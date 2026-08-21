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

    $range = "$Base...$Head"
    $paths = @(& git -c core.quotepath=false diff --name-only --no-renames $range --)
    if ($LASTEXITCODE -ne 0) { throw "git diff failed (exit $LASTEXITCODE)" }
    return @($paths)
}

function Write-CiScopeOutput {
    param(
        [bool]$DocsOnly,
        [string]$OutputPath = $env:GITHUB_OUTPUT
    )

    $value = if ($DocsOnly) { 'true' } else { 'false' }
    $mode = if ($DocsOnly) { 'docs' } else { 'full' }
    if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
        Add-Content -LiteralPath $OutputPath -Value "docs_only=$value" -Encoding utf8
    }
    Write-Host "[CI-DOCS-SCOPE] mode=$mode"
}

function Invoke-CiDocsScope {
    param(
        [Parameter(Mandatory)][string]$Event,
        [string]$Base = '',
        [string]$Head = '',
        [scriptblock]$ChangedPathsProvider = { param($b, $h) Get-CiChangedPaths -Base $b -Head $h },
        [string]$OutputPath = $env:GITHUB_OUTPUT
    )

    $docsOnly = $false
    if ($Event -ceq 'pull_request') {
        try {
            $paths = @(& $ChangedPathsProvider $Base $Head)
            $docsOnly = Test-CiDocsOnlyPaths -Paths $paths
        } catch {
            Write-Warning "[CI-DOCS-SCOPE] classification failed; using full CI: $($_.Exception.Message)"
            $docsOnly = $false
        }
    }
    Write-CiScopeOutput -DocsOnly $docsOnly -OutputPath $OutputPath
    return $docsOnly
}

function Get-WorkflowStepMatch {
    param(
        [Parameter(Mandatory)][string]$WorkflowText,
        [Parameter(Mandatory)][string]$Name
    )

    $escaped = [regex]::Escape($Name)
    return [regex]::Match($WorkflowText, "(?ms)^      - name: $escaped\r?\n(?<body>.*?)(?=^      - (?:name:|uses:)|\z)")
}

function Set-WorkflowStepBodyForTest {
    param(
        [Parameter(Mandatory)][string]$WorkflowText,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][scriptblock]$MutateBody
    )

    $step = Get-WorkflowStepMatch -WorkflowText $WorkflowText -Name $Name
    if (-not $step.Success) { throw "selftest setup: workflow step missing: $Name" }
    $bodyGroup = $step.Groups['body']
    $mutatedBody = & $MutateBody $bodyGroup.Value
    return $WorkflowText.Substring(0, $bodyGroup.Index) + $mutatedBody + $WorkflowText.Substring($bodyGroup.Index + $bodyGroup.Length)
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
        'github.event_name'
    )) {
        if (-not $WorkflowText.Contains($required, [StringComparison]::Ordinal)) {
            $errors.Add("missing workflow contract: $required")
        }
    }

    $gateContracts = @(
        @{ Name = 'Validate task cards'; Needles = @("`$f = 'scripts/check-cards.ps1'", 'pwsh -NoProfile -File $f', 'check-cards.ps1 missing (gate removed?)') },
        @{ Name = 'Validate archive card index'; Needles = @("`$f = 'scripts/archive.ps1'", 'pwsh -NoProfile -File $f -CheckCardsIndex -Quiet', 'archive.ps1 missing (gate removed?)') },
        @{ Name = 'Secret-leak gate'; Needles = @("`$f = 'scripts/check-secrets.ps1'", 'pwsh -NoProfile -File $f', 'check-secrets.ps1 missing (gate removed?)') }
    )
    foreach ($gate in $gateContracts) {
        $step = Get-WorkflowStepMatch -WorkflowText $WorkflowText -Name $gate.Name
        if (-not $step.Success) {
            $errors.Add("retained gate missing: $($gate.Name)")
            continue
        }
        foreach ($needle in $gate.Needles) {
            if (-not $step.Groups['body'].Value.Contains($needle, [StringComparison]::Ordinal)) {
                $errors.Add("retained gate body missing [$($gate.Name)]: $needle")
            }
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
        $step = Get-WorkflowStepMatch -WorkflowText $WorkflowText -Name $name
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

    $outputFile = New-TemporaryFile
    try {
        $entryCases = @(
            @{ Name = 'pr-docs'; Event = 'pull_request'; Base = 'x'; Head = 'y'; Provider = { param($b, $h) @('docs/guide.md') }; Expected = $true },
            @{ Name = 'pr-mixed'; Event = 'pull_request'; Base = 'x'; Head = 'y'; Provider = { param($b, $h) @('docs/guide.md', 'scripts/check.ps1') }; Expected = $false },
            @{ Name = 'invalid-sha'; Event = 'pull_request'; Base = 'bad'; Head = ('0' * 40); Provider = { param($b, $h) Get-CiChangedPaths -Base $b -Head $h }; Expected = $false },
            @{ Name = 'missing-commit'; Event = 'pull_request'; Base = ('f' * 40); Head = ('e' * 40); Provider = { param($b, $h) Get-CiChangedPaths -Base $b -Head $h }; Expected = $false },
            @{ Name = 'push'; Event = 'push'; Base = ''; Head = ''; Provider = { throw 'provider must not run for push' }; Expected = $false },
            @{ Name = 'manual'; Event = 'workflow_dispatch'; Base = ''; Head = ''; Provider = { throw 'provider must not run for workflow_dispatch' }; Expected = $false }
        )
        foreach ($case in $entryCases) {
            Clear-Content -LiteralPath $outputFile.FullName
            $actual = Invoke-CiDocsScope -Event $case.Event -Base $case.Base -Head $case.Head -ChangedPathsProvider $case.Provider -OutputPath $outputFile.FullName
            $expectedOutput = if ($case.Expected) { 'docs_only=true' } else { 'docs_only=false' }
            $output = @(Get-Content -LiteralPath $outputFile.FullName)
            Assert-SelfTest -Condition ($actual -eq $case.Expected) -Message "entry '$($case.Name)' expected $($case.Expected), got $actual"
            Assert-SelfTest -Condition ($output.Count -eq 1 -and $output[0] -ceq $expectedOutput) -Message "entry '$($case.Name)' output was '$($output -join ',')'"
        }

        $head = (& git rev-parse HEAD 2>$null | Out-String).Trim()
        Assert-SelfTest -Condition ($LASTEXITCODE -eq 0 -and $head -match '^[0-9a-f]{40}$') -Message 'git fixture HEAD unavailable'
        Clear-Content -LiteralPath $outputFile.FullName
        $actualEmpty = Invoke-CiDocsScope -Event pull_request -Base $head -Head $head -OutputPath $outputFile.FullName
        Assert-SelfTest -Condition (-not $actualEmpty) -Message 'real empty git diff did not fail closed to full CI'
        Assert-SelfTest -Condition ((Get-Content -Raw -LiteralPath $outputFile.FullName).Trim() -ceq 'docs_only=false') -Message 'real empty git diff output mismatch'

        $fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) "ci-docs-scope-$PID-$([guid]::NewGuid().ToString('N'))"
        New-Item -ItemType Directory -Path $fixtureRoot | Out-Null
        Push-Location $fixtureRoot
        try {
            & git init -q
            & git config user.name ci-docs-scope
            & git config user.email ci-docs-scope@example.invalid
            Set-Content -LiteralPath base.txt -Value base -Encoding utf8
            & git add -- base.txt
            & git commit -q -m base
            $common = (& git rev-parse HEAD).Trim()

            & git switch -q -c docs-feature
            New-Item -ItemType Directory -Path docs | Out-Null
            Set-Content -LiteralPath docs/feature.md -Value docs -Encoding utf8
            & git add -- docs/feature.md
            & git commit -q -m docs
            $featureHead = (& git rev-parse HEAD).Trim()

            & git switch -q --detach $common
            New-Item -ItemType Directory -Path scripts | Out-Null
            Set-Content -LiteralPath scripts/target-change.ps1 -Value code -Encoding utf8
            & git add -- scripts/target-change.ps1
            & git commit -q -m target-code
            $targetHead = (& git rev-parse HEAD).Trim()

            $divergedPaths = @(Get-CiChangedPaths -Base $targetHead -Head $featureHead)
            Assert-SelfTest -Condition ($divergedPaths.Count -eq 1 -and $divergedPaths[0] -ceq 'docs/feature.md') -Message "diverged PR diff included target-only paths: $($divergedPaths -join ',')"
            Assert-SelfTest -Condition (Test-CiDocsOnlyPaths -Paths $divergedPaths) -Message 'diverged Markdown-only PR was not docs-only'
        } finally {
            Pop-Location
            Remove-Item -LiteralPath $fixtureRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    } finally {
        Remove-Item -LiteralPath $outputFile.FullName -Force -ErrorAction SilentlyContinue
    }

    $workflowPath = Join-Path (Split-Path -Parent $PSScriptRoot) '.github/workflows/ci.yml'
    $workflow = Get-Content -Raw -LiteralPath $workflowPath
    $errors = @(Get-WorkflowContractErrors -WorkflowText $workflow)
    Assert-SelfTest -Condition ($errors.Count -eq 0) -Message ($errors -join '; ')

    $gateMutations = @(
        @{ Name = 'Validate task cards'; Command = 'pwsh -NoProfile -File $f' },
        @{ Name = 'Validate archive card index'; Command = 'pwsh -NoProfile -File $f -CheckCardsIndex -Quiet' },
        @{ Name = 'Secret-leak gate'; Command = 'pwsh -NoProfile -File $f' }
    )
    foreach ($mutation in $gateMutations) {
        $command = $mutation.Command
        $mutant = Set-WorkflowStepBodyForTest -WorkflowText $workflow -Name $mutation.Name -MutateBody { param($body) $body.Replace($command, '') }
        $expectedError = "retained gate body missing [$($mutation.Name)]: $command"
        Assert-SelfTest -Condition (@(Get-WorkflowContractErrors -WorkflowText $mutant) -contains $expectedError) -Message "gate command deletion survived: $($mutation.Name)"
    }

    $heavySteps = @(
        'Setup Python', 'Install uv', 'Sync deps', 'Pytest (no-network)',
        'Setup Java (Temurin 17)', 'Setup Android SDK', 'Setup Gradle (dependency cache across CI runs)',
        'Gradle online build (warms cache for verify.ps1''s --offline gate)', 'License gate', 'E2E verify gate'
    )
    foreach ($name in $heavySteps) {
        $mutant = Set-WorkflowStepBodyForTest -WorkflowText $workflow -Name $name -MutateBody {
            param($body)
            $body.Replace("steps.docs_scope.outputs.docs_only != 'true'", 'true')
        }
        $expectedError = "heavy step lacks docs-only guard: $name"
        Assert-SelfTest -Condition (@(Get-WorkflowContractErrors -WorkflowText $mutant) -contains $expectedError) -Message "heavy-step guard deletion survived: $name"
    }

    Write-Host 'ci-docs-scope: PASS'
}

if ($SelfTest) {
    Invoke-SelfTest
    exit 0
}

$null = Invoke-CiDocsScope -Event $EventName -Base $BaseSha -Head $HeadSha
