[CmdletBinding()]
param(
    [switch]$SelfTest,
    [string]$EventName = '',
    [string]$BaseSha = '',
    [string]$HeadSha = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-CiDocsOnlyPaths {
    param([AllowEmptyCollection()][string[]]$Paths)

    $items = @($Paths)
    if ($items.Count -eq 0) { return $false }

    foreach ($path in $items) {
        if ([string]::IsNullOrWhiteSpace($path)) { return $false }
        if ($path.Contains('\') -or $path.StartsWith('/') -or $path -match '^[A-Za-z]:') { return $false }
        if ($path.StartsWith('"') -and $path.EndsWith('"')) { return $false }

        $segments = @($path.Split('/'))
        if ($segments.Count -eq 0 -or $segments -contains '' -or $segments -contains '.' -or $segments -contains '..') {
            return $false
        }

        $isDocsTree = $path.StartsWith('docs/', [StringComparison]::Ordinal)
        $isSpecsTree = $path.StartsWith('specs/', [StringComparison]::Ordinal)
        $isMarkdown = $path.EndsWith('.md', [StringComparison]::OrdinalIgnoreCase)
        if (-not ($isDocsTree -or $isSpecsTree -or $isMarkdown)) { return $false }
    }

    return $true
}

function Get-CiChangedPaths {
    param(
        [Parameter(Mandatory)][string]$Base,
        [Parameter(Mandatory)][string]$Head
    )

    if ($Base -notmatch '^[0-9a-fA-F]{40}$' -or $Head -notmatch '^[0-9a-fA-F]{40}$') {
        throw 'base/head SHA must be full 40-hex commit IDs'
    }

    $paths = @(& git -c core.quotepath=false diff --name-only --no-renames $Base $Head --)
    if ($LASTEXITCODE -ne 0) { throw "git diff failed (exit $LASTEXITCODE)" }
    return @($paths)
}

function Write-CiScopeOutput {
    param([bool]$DocsOnly)

    $value = if ($DocsOnly) { 'true' } else { 'false' }
    $mode = if ($DocsOnly) { 'docs' } else { 'full' }
    if (-not [string]::IsNullOrWhiteSpace($env:GITHUB_OUTPUT)) {
        Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value "docs_only=$value" -Encoding utf8
    }
    Write-Host "[CI-DOCS-SCOPE] mode=$mode"
}

function Get-WorkflowContractErrors {
    param([Parameter(Mandatory)][string]$WorkflowText)

    $errors = [Collections.Generic.List[string]]::new()
    $pullRequest = [regex]::Match($WorkflowText, '(?ms)^  pull_request:\s*\r?\n(?<body>.*?)(?=^  [a-zA-Z_]+:|\z)')
    if (-not $pullRequest.Success) {
        $errors.Add('pull_request trigger missing')
    } elseif ($pullRequest.Groups['body'].Value -match '(?m)^\s+paths(?:-ignore)?:') {
        $errors.Add('pull_request must not use path filters')
    }

    foreach ($required in @(
        'id: docs_scope',
        'scripts/ci-docs-scope.ps1',
        'github.event.pull_request.base.sha',
        'github.event.pull_request.head.sha',
        'fetch-depth: 0',
        '- name: Validate task cards',
        '- name: Validate archive card index',
        '-CheckCardsIndex -Quiet',
        '- name: Secret-leak gate'
    )) {
        if (-not $WorkflowText.Contains($required, [StringComparison]::Ordinal)) {
            $errors.Add("missing workflow contract: $required")
        }
    }

    $heavySteps = @(
        'Setup Python',
        'Install uv',
        'Sync deps',
        'Pytest (no-network)',
        'Setup Java (Temurin 17)',
        'Setup Android SDK',
        'Setup Gradle (dependency cache across CI runs)',
        'Gradle online build (warms cache for verify.ps1''s --offline gate)',
        'License gate',
        'E2E verify gate'
    )
    foreach ($name in $heavySteps) {
        $escaped = [regex]::Escape($name)
        $step = [regex]::Match($WorkflowText, "(?ms)^      - name: $escaped\r?\n(?<body>.*?)(?=^      - (?:name:|uses:)|\z)")
        if (-not $step.Success) {
            $errors.Add("heavy step missing: $name")
        } elseif ($step.Groups['body'].Value -notmatch "steps\.docs_scope\.outputs\.docs_only != 'true'") {
            $errors.Add("heavy step lacks docs-only guard: $name")
        }
    }

    return @($errors)
}

function Assert-SelfTest {
    param(
        [bool]$Condition,
        [string]$Message
    )

    if (-not $Condition) { throw "ci-docs-scope selftest: $Message" }
}

function Invoke-SelfTest {
    $cases = @(
        @{ Name = 'docs'; Paths = @('docs/guide.txt'); Expected = $true },
        @{ Name = 'specs'; Paths = @('specs/tasks/T0-X.md'); Expected = $true },
        @{ Name = 'root-markdown'; Paths = @('README.md'); Expected = $true },
        @{ Name = 'nested-markdown'; Paths = @('.github/CONTRIBUTING.MD'); Expected = $true },
        @{ Name = 'source'; Paths = @('android/app/src/main/Main.kt'); Expected = $false },
        @{ Name = 'script'; Paths = @('scripts/check.ps1'); Expected = $false },
        @{ Name = 'workflow'; Paths = @('.github/workflows/ci.yml'); Expected = $false },
        @{ Name = 'mixed'; Paths = @('docs/guide.md', 'scripts/check.ps1'); Expected = $false },
        @{ Name = 'empty'; Paths = @(); Expected = $false },
        @{ Name = 'unsafe-parent'; Paths = @('docs/../scripts/check.ps1'); Expected = $false },
        @{ Name = 'unsafe-backslash'; Paths = @('docs\guide.md'); Expected = $false }
    )
    foreach ($case in $cases) {
        $actual = Test-CiDocsOnlyPaths -Paths $case.Paths
        Assert-SelfTest -Condition ($actual -eq $case.Expected) -Message "case '$($case.Name)' expected $($case.Expected), got $actual"
    }

    $workflowPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.github/workflows/ci.yml'
    $workflow = Get-Content -Raw -LiteralPath $workflowPath
    $errors = @(Get-WorkflowContractErrors -WorkflowText $workflow)
    Assert-SelfTest -Condition ($errors.Count -eq 0) -Message ($errors -join '; ')

    $withoutArchive = $workflow.Replace('      - name: Validate archive card index', '      - name: MUTATED archive gate')
    Assert-SelfTest -Condition (@(Get-WorkflowContractErrors -WorkflowText $withoutArchive).Count -gt 0) -Message 'archive gate deletion mutation survived'
    $guardText = 'if: ${{ steps.docs_scope.outputs.docs_only != ''true'' }}'
    $withoutGuard = $workflow.Replace($guardText, 'if: ${{ true }}')
    Assert-SelfTest -Condition (@(Get-WorkflowContractErrors -WorkflowText $withoutGuard).Count -gt 0) -Message 'docs-only guard deletion mutation survived'

    Write-Host 'ci-docs-scope: PASS'
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

$docsOnly = $false
if ($EventName -ceq 'pull_request') {
    try {
        $docsOnly = Test-CiDocsOnlyPaths -Paths @(Get-CiChangedPaths -Base $BaseSha -Head $HeadSha)
    } catch {
        Write-Warning "[CI-DOCS-SCOPE] classification failed; using full CI: $($_.Exception.Message)"
        $docsOnly = $false
    }
}
Write-CiScopeOutput -DocsOnly $docsOnly
