#requires -Version 7
<#
.SYNOPSIS
  Shared, fail-closed routing for task-scoped scaffold selftest runs.

.DESCRIPTION
  The task card and FrozenPaths are read from one pinned local baseline commit.
  A task branch cannot edit either authority to select a cheaper selftest shard.
#>
[CmdletBinding()]
param([switch]$SelfCheck)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-ValidationGit {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string[]]$Arguments)

  $start = [Diagnostics.ProcessStartInfo]::new()
  $start.FileName = 'git'; $start.WorkingDirectory = $RepoRoot; $start.UseShellExecute = $false
  $start.CreateNoWindow = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
  foreach ($argument in $Arguments) { [void]$start.ArgumentList.Add($argument) }
  $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
  try {
    if (-not $process.Start()) { throw '[SELFTEST-ROUTE-GIT-START] git could not start.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync(); $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.WaitForExit(); $stdout = $stdoutTask.GetAwaiter().GetResult(); $stderr = $stderrTask.GetAwaiter().GetResult()
    return [pscustomobject]@{ ExitCode = $process.ExitCode; StdOut = $stdout; StdErr = $stderr }
  } finally { $process.Dispose() }
}

function Assert-ValidationGit {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string[]]$Arguments, [Parameter(Mandatory)][string]$Code)

  $result = Invoke-ValidationGit -RepoRoot $RepoRoot -Arguments $Arguments
  if ($result.ExitCode -ne 0) { throw "[$Code] git $($Arguments -join ' ') failed (exit $($result.ExitCode))." }
  return $result.StdOut
}

function Test-ValidationRelativePath {
  param([string]$Path)

  if ([string]::IsNullOrWhiteSpace($Path) -or $Path.Contains([char]0) -or $Path.Contains('\') -or
      $Path.StartsWith('/') -or $Path -match '^[A-Za-z]:|[\r\n]') { return $false }
  $segments = @($Path.Split('/'))
  return $segments.Count -gt 0 -and -not ($segments -contains '') -and -not ($segments -contains '.') -and -not ($segments -contains '..')
}

function Add-ValidationPath {
  param([Parameter(Mandatory)][AllowEmptyCollection()][Collections.Generic.HashSet[string]]$Set, [Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Source)

  if (-not (Test-ValidationRelativePath $Path)) { throw "[SELFTEST-ROUTE-PATH-INVALID] $Source returned an unsafe or unreadable path." }
  [void]$Set.Add($Path)
}

function Add-ValidationNameStatusPaths {
  param([Parameter(Mandatory)][AllowEmptyCollection()][Collections.Generic.HashSet[string]]$Set, [Parameter(Mandatory)][AllowEmptyString()][string]$NulText, [Parameter(Mandatory)][string]$Source)

  $tokens = [Collections.Generic.List[string]]::new()
  foreach ($token in $NulText.Split([char]0)) { if ($token.Length -gt 0) { $tokens.Add($token) } }
  for ($index = 0; $index -lt $tokens.Count;) {
    $status = $tokens[$index]; $index++
    if ($status -notmatch '^(?:[ACDMRTUXB]|[RC][0-9]+)$') { throw "[SELFTEST-ROUTE-NAMESTATUS-INVALID] $Source returned status '$status'." }
    $pathCount = if ($status -match '^[RC]') { 2 } else { 1 }
    if ($index + $pathCount -gt $tokens.Count) { throw "[SELFTEST-ROUTE-NAMESTATUS-INVALID] $Source ended before all paths for '$status'." }
    for ($pathIndex = 0; $pathIndex -lt $pathCount; $pathIndex++) { Add-ValidationPath -Set $Set -Path $tokens[$index + $pathIndex] -Source $Source }
    $index += $pathCount
  }
}

function Get-ValidationChangedPaths {
  param([Parameter(Mandatory)][string]$WorktreePath, [Parameter(Mandatory)][string]$BaseOid)

  $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $committed = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--name-status', '-z', '--find-renames', "$BaseOid...HEAD", '--') -Code 'SELFTEST-ROUTE-COMMITTED-DIFF'
  $dirty = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--name-status', '-z', '--find-renames', '--') -Code 'SELFTEST-ROUTE-DIRTY-DIFF'
  $staged = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--cached', '--name-status', '-z', '--find-renames', '--') -Code 'SELFTEST-ROUTE-STAGED-DIFF'
  Add-ValidationNameStatusPaths -Set $paths -NulText $committed -Source 'committed diff'
  Add-ValidationNameStatusPaths -Set $paths -NulText $dirty -Source 'dirty diff'
  Add-ValidationNameStatusPaths -Set $paths -NulText $staged -Source 'staged diff'
  $untracked = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('ls-files', '--others', '--exclude-standard', '-z') -Code 'SELFTEST-ROUTE-UNTRACKED'
  foreach ($path in $untracked.Split([char]0)) { if ($path.Length -gt 0) { Add-ValidationPath -Set $paths -Path $path -Source 'untracked files' } }
  return @($paths | Sort-Object)
}

function Get-ValidationGitCommonDir {
  param([Parameter(Mandatory)][string]$RepoRoot)

  $commonDir = (Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('rev-parse', '--path-format=absolute', '--git-common-dir') -Code 'SELFTEST-ROUTE-COMMONDIR').Trim()
  if ([string]::IsNullOrWhiteSpace($commonDir) -or -not (Test-Path -LiteralPath $commonDir -PathType Container)) { throw '[SELFTEST-ROUTE-COMMONDIR] git returned an unreadable common directory.' }
  return (Resolve-Path -LiteralPath $commonDir).Path
}

function Assert-ValidationRegularBlob {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$BaseOid, [Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][string]$Code)

  $type = (Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('cat-file', '-t', "${BaseOid}:$Path") -Code $Code).Trim()
  if ($type -cne 'blob') { throw "[$Code] baseline '$Path' is not a regular blob." }
}

function Convert-ValidationFrozenPaths {
  param([Parameter(Mandatory)][string]$Config)

  $tokens = $null; $errors = $null
  $ast = [Management.Automation.Language.Parser]::ParseInput($Config, [ref]$tokens, [ref]$errors)
  if ($errors -and $errors.Count -gt 0) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline _config.ps1 does not parse.' }
  $owners = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and
      $node.Left -is [Management.Automation.Language.VariableExpressionAst] -and $node.Left.VariablePath.UserPath -ceq 'script:ScaffoldConfig' }, $true))
  if ($owners.Count -ne 1 -or $owners[0].Right -isnot [Management.Automation.Language.CommandExpressionAst] -or
      $owners[0].Right.Expression -isnot [Management.Automation.Language.HashtableAst]) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline must assign one static $script:ScaffoldConfig hashtable.' }
  $pairs = @($owners[0].Right.Expression.KeyValuePairs | Where-Object {
      ($_.Item1 -is [Management.Automation.Language.StringConstantExpressionAst]) -and $_.Item1.Value -ceq 'FrozenPaths' })
  if ($pairs.Count -ne 1) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline _config.ps1 must contain exactly one static FrozenPaths value.' }
  $blocks = @($pairs[0].Item2.FindAll({ param($node) $node -is [Management.Automation.Language.StatementBlockAst] }, $true))
  if ($blocks.Count -ne 1) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths is not a static literal.' }
  try { $value = $blocks[0].SafeGetValue() } catch { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths is not a static literal.' }
  if ($null -eq $value -or (($value -isnot [Array]) -and ($value -isnot [string]))) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths must be a static string array.' }
  $frozenPaths = @($value)
  foreach ($frozen in $frozenPaths) {
    if ($frozen -isnot [string] -or [string]::IsNullOrWhiteSpace($frozen)) { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths must contain only non-empty strings.' }
    try { [void][regex]::new("^(?:$frozen)") } catch { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths contains an invalid pattern.' }
  }
  return $frozenPaths
}

function Get-ValidationFrozenPaths {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$BaseOid)

  Assert-ValidationRegularBlob -RepoRoot $RepoRoot -BaseOid $BaseOid -Path 'scripts/_config.ps1' -Code 'SELFTEST-ROUTE-BASE-CONFIG'
  $config = Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('show', "${BaseOid}:scripts/_config.ps1") -Code 'SELFTEST-ROUTE-BASE-CONFIG'
  return @(Convert-ValidationFrozenPaths -Config $config)
}

function Test-ValidationFrozenPath {
  param([Parameter(Mandatory)][string]$Path, [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$FrozenPaths)

  foreach ($frozen in $FrozenPaths) {
    try { if ($Path -match ("^(?:" + $frozen + ")")) { return $true } }
    catch { throw '[SELFTEST-ROUTE-FROZEN-INVALID] baseline FrozenPaths contains an invalid pattern.' }
  }
  return $false
}

function Resolve-SelftestRiskRoute {
  param([Parameter(Mandatory)][string[]]$ChangedPath, [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$FrozenPath)

  if ($ChangedPath.Count -eq 0) { return [pscustomobject]@{ Mode = 'all'; Reason = 'empty-change-set' } }
  $classes = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  $criticalDocs = @('docs/SECURITY.md', 'docs/QUALITY-RUBRIC.md', 'docs/DEVOPS-WORKFLOW.md', 'docs/RELEASE-CHECKLIST.md', 'docs/DELIVERY-CHAINS.md', 'docs/DELIVERY-OPS.md')
  foreach ($path in $ChangedPath) {
    if (-not (Test-ValidationRelativePath $path)) { return [pscustomobject]@{ Mode = 'all'; Reason = 'invalid-path' } }
    if ((Test-ValidationFrozenPath -Path $path -FrozenPaths $FrozenPath) -or $criticalDocs -ccontains $path -or $path -clike 'scripts/*' -or
        $path -clike '.github/*' -or $path -clike '.claude/*' -or $path -ceq 'CLAUDE.md' -or $path -ceq 'AGENTS.md' -or $path -ceq 'specs/verdict.schema.json') {
      return [pscustomobject]@{ Mode = 'all'; Reason = 'critical-or-frozen' }
    }
    if ($path -clike 'android/*' -or $path -clike 'configs/compliance/*') { [void]$classes.Add('product'); continue }
    if (($path -clike 'docs/*.md') -or ($path -clike 'specs/*.md')) { [void]$classes.Add('docs'); continue }
    return [pscustomobject]@{ Mode = 'all'; Reason = 'unknown-path' }
  }
  if ($classes.Count -ne 1) { return [pscustomobject]@{ Mode = 'all'; Reason = 'mixed-risk' } }
  if ($classes.Contains('product')) { return [pscustomobject]@{ Mode = 'not-applicable'; Reason = 'ordinary-product' } }
  return [pscustomobject]@{ Mode = 'core'; Reason = 'ordinary-docs' }
}

function Get-ValidationCardScalar {
  param([Parameter(Mandatory)][string]$FrontMatter, [Parameter(Mandatory)][string]$Name, [switch]$Optional)

  $matches = @([regex]::Matches($FrontMatter, "(?m)^$([regex]::Escape($Name))\s*:\s*(.*?)\s*$"))
  if ($matches.Count -eq 0 -and $Optional) { return $null }
  if ($matches.Count -ne 1) { throw "[SELFTEST-ROUTE-CARD-INVALID] baseline card must contain exactly one '$Name' scalar." }
  $value = Get-UncommentedValue $matches[0].Groups[1].Value
  if ([string]::IsNullOrWhiteSpace($value)) { throw "[SELFTEST-ROUTE-CARD-INVALID] baseline card '$Name' scalar is empty." }
  return $value
}

function Get-ValidationRegisteredWorktree {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$TaskId)

  $raw = Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('worktree', 'list', '--porcelain', '-z') -Code 'SELFTEST-ROUTE-WORKTREE-LIST'
  $candidates = [Collections.Generic.List[string]]::new(); $fields = @{}
  foreach ($token in $raw.Split([char]0)) {
    if ($token.Length -eq 0) {
      if ($fields.Count -eq 0) { continue }
      if ($fields.ContainsKey('worktree') -and $fields['branch'] -ceq "refs/heads/$TaskId") { $candidates.Add($fields['worktree']) }
      $fields = @{}; continue
    }
    if ($token -match '^(worktree|branch) (.+)$') {
      if ($fields.ContainsKey($Matches[1])) { throw '[SELFTEST-ROUTE-WORKTREE-LIST] git worktree output has duplicate fields.' }
      $fields[$Matches[1]] = $Matches[2]
    }
  }
  if ($fields.Count -ne 0) { throw '[SELFTEST-ROUTE-WORKTREE-LIST] git worktree output ended mid-record.' }
  if ($candidates.Count -ne 1) { throw "[SELFTEST-ROUTE-WORKTREE-AMBIGUOUS] expected one registered worktree for '$TaskId', found $($candidates.Count)." }
  if (-not (Test-Path -LiteralPath $candidates[0] -PathType Container)) { throw "[SELFTEST-ROUTE-WORKTREE-MISSING] '$($candidates[0])' is unavailable." }
  return (Resolve-Path -LiteralPath $candidates[0]).Path
}

function Test-ValidationStatusOnlyCardText {
  param([Parameter(Mandatory)][string]$BaselineCard, [Parameter(Mandatory)][string]$CurrentCard)

  $frontMatterPattern = '(?s)\A\uFEFF?---\r?\n.*?\r?\n---[ \t]*(?:\r?\n|\z)'
  if ($BaselineCard -notmatch $frontMatterPattern -or $CurrentCard -notmatch $frontMatterPattern) { return $false }
  $statusPattern = '(?m)^status\s*:\s*.*$'
  if ([regex]::Matches($BaselineCard, $statusPattern).Count -ne 1 -or [regex]::Matches($CurrentCard, $statusPattern).Count -ne 1) { return $false }
  $baselineWithoutStatus = [regex]::Replace($BaselineCard, $statusPattern, 'status: <selftest-routing-status>', 1).TrimEnd([char[]]"`r`n")
  $currentWithoutStatus = [regex]::Replace($CurrentCard, $statusPattern, 'status: <selftest-routing-status>', 1).TrimEnd([char[]]"`r`n")
  return $baselineWithoutStatus -ceq $currentWithoutStatus
}
function Test-ValidationStatusOnlyCardChange { param([string]$BaselineCard,[string]$CurrentCardPath); $item=Get-Item -LiteralPath $CurrentCardPath -Force -ErrorAction SilentlyContinue; if($null -eq $item -or $item -isnot [IO.FileInfo] -or $item.LinkType){return $false}; return (Test-ValidationStatusOnlyCardText $BaselineCard ([IO.File]::ReadAllText($item.FullName))) }

function Resolve-SelftestTaskRoute {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$TaskId, [Parameter(Mandatory)][string]$Base)

  if ($TaskId -cnotmatch '^T\d+-[A-Z0-9]+(?:-[A-Z0-9]+)*$') { throw "[SELFTEST-TASKID-BADID] '$TaskId' is not a task-card ID." }
  if ($Base -notmatch '^[A-Za-z0-9][A-Za-z0-9._/-]*$') { throw "[SELFTEST-ROUTE-BASE-INVALID] '$Base' is not a local branch name." }
  if ($Base -match '(?:^|/)\.{1,2}(?:/|$)|//|/$') { throw "[SELFTEST-ROUTE-BASE-INVALID] '$Base' is not a local branch name." }
  $baseOid = (Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('rev-parse', '--verify', "refs/heads/$Base^{commit}") -Code 'SELFTEST-ROUTE-BASE-MISSING').Trim().ToLowerInvariant()
  if ($baseOid -notmatch '^[0-9a-f]{40}$') { throw "[SELFTEST-ROUTE-BASE-INVALID] '$Base' did not resolve to a commit." }
  . (Join-Path $PSScriptRoot '_cards.ps1')
  $cardPath = "specs/tasks/$TaskId.md"
  Assert-ValidationRegularBlob -RepoRoot $RepoRoot -BaseOid $baseOid -Path $cardPath -Code 'SELFTEST-ROUTE-BASE-CARD'
  $card = Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('show', "${baseOid}:$cardPath") -Code 'SELFTEST-ROUTE-BASE-CARD'
  $frontMatter = Get-FrontMatter $card
  if (-not $frontMatter) { throw "[SELFTEST-ROUTE-CARD-INVALID] baseline card '$cardPath' has no readable front matter." }
  $cardId = Get-ValidationCardScalar -FrontMatter $frontMatter -Name 'id'
  if ($cardId -cne $TaskId) { throw "[SELFTEST-ROUTE-CARD-INVALID] baseline card '$cardPath' does not bind the requested ID." }
  $worktreePath = Get-ValidationRegisteredWorktree -RepoRoot $RepoRoot -TaskId $TaskId
  $declaredWorktree = Get-ValidationCardScalar -FrontMatter $frontMatter -Name 'worktree' -Optional
  if ($declaredWorktree) {
    if (-not [IO.Path]::IsPathRooted($declaredWorktree) -or -not (Test-Path -LiteralPath $declaredWorktree -PathType Container) -or
        ((Resolve-Path -LiteralPath $declaredWorktree).Path -ine $worktreePath)) { throw "[SELFTEST-ROUTE-CARD-INVALID] baseline card worktree does not match the registered task worktree." }
  }
  if ((Get-ValidationGitCommonDir -RepoRoot $RepoRoot) -ine (Get-ValidationGitCommonDir -RepoRoot $worktreePath)) { throw '[SELFTEST-ROUTE-WORKTREE-UNRELATED] baseline card worktree is not in this repository.' }
  $branch = (Assert-ValidationGit -RepoRoot $worktreePath -Arguments @('branch', '--show-current') -Code 'SELFTEST-ROUTE-BRANCH').Trim()
  if ($branch -cne $TaskId) { throw "[SELFTEST-ROUTE-BRANCH-MISMATCH] expected '$TaskId', got '$branch'." }
  $allPaths = @(Get-ValidationChangedPaths -WorktreePath $worktreePath -BaseOid $baseOid)
  if ($cardPath -in $allPaths) {
    # The aggregated diff spans committed, index, and worktree layers. A
    # status-only checkout must therefore be status-only in every layer;
    # otherwise a contract edit in HEAD or index could be hidden by the final
    # worktree content.
    $headCard = Assert-ValidationGit -RepoRoot $worktreePath -Arguments @('show', "HEAD:$cardPath") -Code 'SELFTEST-ROUTE-CARD-HEAD'
    $indexCard = Assert-ValidationGit -RepoRoot $worktreePath -Arguments @('show', ":$cardPath") -Code 'SELFTEST-ROUTE-CARD-INDEX'
    if (-not ((Test-ValidationStatusOnlyCardText $card $headCard) -and (Test-ValidationStatusOnlyCardText $card $indexCard) -and
        (Test-ValidationStatusOnlyCardChange -BaselineCard $card -CurrentCardPath (Join-Path $worktreePath $cardPath)))) {
      return [pscustomobject]@{ Mode = 'all'; Reason = 'card-contract-changed'; BaseOid = $baseOid; WorktreePath = $worktreePath; Paths = $allPaths }
    }
  }
  $paths = @($allPaths | Where-Object { $_ -cne $cardPath })
  $frozen = Get-ValidationFrozenPaths -RepoRoot $RepoRoot -BaseOid $baseOid
  $route = Resolve-SelftestRiskRoute -ChangedPath $paths -FrozenPath $frozen
  return [pscustomobject]@{ Mode = $route.Mode; Reason = $route.Reason; BaseOid = $baseOid; WorktreePath = $worktreePath; Paths = $paths }
}

function Assert-ValidationSelfCheck { param([bool]$Condition, [string]$Message); if (-not $Condition) { throw "[SELFTEST-RISK-ROUTING-SELFCHECK] $Message" } }

function Invoke-ValidationTaskEntrypointFixture {
  param([Parameter(Mandatory)][string]$Directory, [Parameter(Mandatory)][ValidateSet('not-applicable', 'core', 'all', 'failure')][string]$Mode,
    [Parameter(Mandatory)][string[]]$Arguments)

  $selftestPath = Join-Path $PSScriptRoot 'selftest.ps1'
  $source = [IO.File]::ReadAllText($selftestPath)
  $start = $source.IndexOf('# An explicit task run selects only existing scaffold coverage.', [StringComparison]::Ordinal)
  $end = $source.IndexOf('# TD15：', $start, [StringComparison]::Ordinal)
  if ($start -lt 0 -or $end -le $start) { throw '[SELFTEST-RISK-ROUTING-SELFCHECK] exact selftest task-entry source block was not found.' }
  $entry = $source.Substring($start, $end - $start)
  $scripts = Join-Path $Directory 'scripts'; New-Item -ItemType Directory -Path $scripts -Force | Out-Null
  $escapedRoot = (Split-Path -Parent $PSScriptRoot).Replace("'", "''")
  $fixtureValidation = @"
param([switch]`$SelfCheck)
function Resolve-SelftestTaskRoute {
  param([string]`$RepoRoot, [string]`$TaskId, [string]`$Base)
  if ('$Mode' -ceq 'failure') { throw '[ENTRYPOINT-PROOF-FAILURE]' }
  return [pscustomobject]@{ Mode = '$Mode'; Reason = 'fixture'; BaseOid = '0000000000000000000000000000000000000000'; Paths = @() }
}
"@
  $prefix = @"
[CmdletBinding()]
param(
  [string]`$TaskId,
  [string]`$Base,
  [string]`$Shard = 'all',
  [string]`$Fixture = '',
  [string]`$GateIdMutation = '',
  [string]`$NoGitFixtureCase = '',
  [string]`$NoGitFixtureNonce = '',
  [string]`$NoGitMutationNonce = '',
  [switch]`$StrictLint
)
`$RepoRoot = '$escapedRoot'
function Test-SelftestCiWiringContract { param([string]`$Source); return (`$Source -match '(?m)^\s*shard:\s*core\s*$') }
function Invoke-SelftestAll { param([string]`$SourceRoot, [bool]`$ForwardStrictLint, [bool]`$StrictLintValue); Write-Host '[ENTRYPOINT-DISPATCH] shard=all'; return 0 }
"@
  $suffix = "`nWrite-Output ('[ENTRYPOINT-DISPATCH] shard=' + `$Shard)`nexit 0`n"
  Set-Content -LiteralPath (Join-Path $scripts '_validation.ps1') -Value $fixtureValidation -Encoding utf8NoBOM
  $entryPath = Join-Path $scripts 'selftest-entryproof.ps1'
  Set-Content -LiteralPath $entryPath -Value ($prefix + $entry + $suffix) -Encoding utf8NoBOM
  $output = (& pwsh -NoProfile -File $entryPath @Arguments 2>&1 | Out-String)
  return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = $output }
}

function Invoke-ValidationSelfCheck {
  Assert-ValidationSelfCheck -Condition ([bool](Get-Command Resolve-SelftestTaskRoute -CommandType Function -ErrorAction SilentlyContinue)) -Message 'Resolve-SelftestTaskRoute is missing.'
  $frozen = @('android/core/src/main/sqldelight/')
  $cases = @(
    @{ Name='product'; Paths=@('android/app/src/main/Main.kt', 'configs/compliance/rules.json'); Mode='not-applicable' }, @{ Name='docs'; Paths=@('docs/guide.md', 'specs/tasks/T0-X.md'); Mode='core' },
    @{ Name='frozen'; Paths=@('android/core/src/main/sqldelight/foo.sq'); Mode='all' }, @{ Name='critical'; Paths=@('scripts/task.ps1'); Mode='all' },
    @{ Name='unknown'; Paths=@('README.md'); Mode='all' }, @{ Name='mixed'; Paths=@('docs/guide.md', 'android/app/src/main/Main.kt'); Mode='all' },
    @{ Name='case-variant-product'; Paths=@('Android/app/Main.kt'); Mode='all' }, @{ Name='case-variant-docs'; Paths=@('Docs/guide.md'); Mode='all' }
  )
  foreach ($case in $cases) { $actual = Resolve-SelftestRiskRoute -ChangedPath $case.Paths -FrozenPath $frozen; Assert-ValidationSelfCheck -Condition ($actual.Mode -ceq $case.Mode) -Message "pure case '$($case.Name)' expected $($case.Mode), got $($actual.Mode)." }
  foreach ($badConfig in @(
    "`$script:ScaffoldConfig = @{}",
    "`$decoy = @{ FrozenPaths = @('android/') }; `$script:ScaffoldConfig = @{}",
    "`$script:ScaffoldConfig = @{ Nested = @{ FrozenPaths = @('android/') } }",
    "`$script:ScaffoldConfig = @{ FrozenPaths = @(`$env:USERPROFILE) }",
    "`$script:ScaffoldConfig = @{ FrozenPaths = @('a'); FrozenPaths = @('b') }",
    "`$script:ScaffoldConfig = @{ FrozenPaths = @('[') }"
  )) {
    $rejected = $false; try { [void](Convert-ValidationFrozenPaths -Config $badConfig) } catch { $rejected = $true }
    Assert-ValidationSelfCheck -Condition $rejected -Message 'missing, dynamic, or duplicate FrozenPaths was accepted.'
  }
  $duplicateCardRejected = $false; try { [void](Get-ValidationCardScalar -FrontMatter "id: T0-ROUTE`nid: T0-ROUTE" -Name 'id') } catch { $duplicateCardRejected = $true }
  Assert-ValidationSelfCheck -Condition $duplicateCardRejected -Message 'duplicate card scalar was accepted.'
  $duplicateWorktreeRejected = $false; try { [void](Get-ValidationCardScalar -FrontMatter "worktree: C:\wt\a`nworktree: C:\wt\b" -Name 'worktree') } catch { $duplicateWorktreeRejected = $true }
  Assert-ValidationSelfCheck -Condition $duplicateWorktreeRejected -Message 'duplicate card worktree scalar was accepted.'
  $root = Join-Path ([IO.Path]::GetTempPath()) "selftest-risk-routing-$PID-$([guid]::NewGuid().ToString('N'))"
  try {
    New-Item -ItemType Directory -Path (Join-Path $root 'scripts'), (Join-Path $root 'specs/tasks'), (Join-Path $root 'docs') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '_cards.ps1') -Destination (Join-Path $root 'scripts/_cards.ps1') -Force
    Set-Content -LiteralPath (Join-Path $root 'scripts/_config.ps1') -Encoding utf8 -Value "`$script:ScaffoldConfig = @{ FrozenPaths = @('android/frozen/') }"
    & git -C $root init -q; & git -C $root config user.name selftest; & git -C $root config user.email selftest@example.invalid; & git -C $root config core.autocrlf false
    $card = @('---', 'id: T0-ROUTE', 'status: todo', 'allow_paths:', '  - docs/', '---') -join "`n"
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $card; Set-Content -LiteralPath (Join-Path $root 'docs/base.md') -Encoding utf8 -Value base; Set-Content -LiteralPath (Join-Path $root 'docs/rename-from.md') -Encoding utf8 -Value rename
    & git -C $root add --all; & git -C $root commit -q -m base; & git -C $root branch -M master; & git -C $root switch -q -c T0-ROUTE
    $fixtureBase = (Assert-ValidationGit -RepoRoot $root -Arguments @('rev-parse', 'master^{commit}') -Code 'SELFTEST-ROUTE-SELFCHECK-BASE').Trim()
    $fixtureFrozen = @(Get-ValidationFrozenPaths -RepoRoot $root -BaseOid $fixtureBase)
    Assert-ValidationSelfCheck -Condition ($fixtureFrozen.Count -eq 1 -and $fixtureFrozen[0] -ceq 'android/frozen/') -Message 'static baseline FrozenPaths was not read without execution.'
    Set-Content -LiteralPath (Join-Path $root 'docs/committed.md') -Encoding utf8 -Value committed; & git -C $root add docs/committed.md; & git -C $root commit -q -m committed
    $committedPaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition ((Resolve-SelftestRiskRoute -ChangedPath $committedPaths -FrozenPath $fixtureFrozen).Mode -ceq 'core') -Message 'committed docs did not route core.'
    $committedRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($committedRoute.Mode -ceq 'core') -Message 'committed docs did not retain core through pinned task routing.'
    & git -C $root reset --hard -q $fixtureBase
    & git -C $root mv docs/rename-from.md scripts/rename-to.ps1; & git -C $root add -A
    $stagedPaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition (('docs/rename-from.md' -in $stagedPaths) -and ('scripts/rename-to.ps1' -in $stagedPaths)) -Message 'staged R100 rename did not retain both paths.'
    & git -C $root reset --hard -q $fixtureBase
    Set-Content -LiteralPath (Join-Path $root 'docs/dirty.md') -Encoding utf8 -Value dirty
    $dirtyPaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition ((Resolve-SelftestRiskRoute -ChangedPath $dirtyPaths -FrozenPath $fixtureFrozen).Mode -ceq 'core') -Message 'dirty docs did not route core.'
    $dirtyRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($dirtyRoute.Mode -ceq 'core') -Message 'dirty docs did not retain core through pinned task routing.'
    Remove-Item -LiteralPath (Join-Path $root 'docs/dirty.md') -Force
    Set-Content -LiteralPath (Join-Path $root 'scripts/untracked.ps1') -Encoding utf8 -Value untracked
    $untrackedPaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition ((Resolve-SelftestRiskRoute -ChangedPath $untrackedPaths -FrozenPath $fixtureFrozen).Mode -ceq 'all') -Message 'untracked critical path did not route all.'
    $untrackedRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($untrackedRoute.Mode -ceq 'all') -Message 'untracked critical path did not retain all through pinned task routing.'
    Remove-Item -LiteralPath (Join-Path $root 'scripts/untracked.ps1') -Force
    Set-Content -LiteralPath (Join-Path $root 'scripts/_config.ps1') -Encoding utf8 -Value "`$script:ScaffoldConfig = @{ FrozenPaths = @() }"
    Assert-ValidationSelfCheck -Condition ((@(Get-ValidationFrozenPaths -RepoRoot $root -BaseOid $fixtureBase))[0] -ceq 'android/frozen/') -Message 'branch-edited config became routing authority.'
    & git -C $root checkout -- scripts/_config.ps1
    Move-Item -LiteralPath (Join-Path $root 'docs/rename-from.md') -Destination (Join-Path $root 'scripts/rename-to.ps1')
    $renamePaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition (('docs/rename-from.md' -in $renamePaths) -and ('scripts/rename-to.ps1' -in $renamePaths)) -Message 'an actual rename did not retain both old and new paths.'
    Move-Item -LiteralPath (Join-Path $root 'scripts/rename-to.ps1') -Destination (Join-Path $root 'docs/rename-from.md')
    New-Item -ItemType Directory -Force (Join-Path $root 'android/app') | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'android/app/Main.kt') -Encoding utf8 -Value product
    $statusCard = $card -replace 'status: todo', 'status: doing'
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $statusCard
    Assert-ValidationSelfCheck -Condition (Test-ValidationStatusOnlyCardChange -BaselineCard $card -CurrentCardPath (Join-Path $root 'specs/tasks/T0-ROUTE.md')) -Message 'status-only card bookkeeping was not recognized separately from contract changes.'
    $productRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($productRoute.Mode -ceq 'not-applicable') -Message 'status-only task bookkeeping forced core for ordinary product changes.'
    $contractCard = $statusCard -replace 'allow_paths:', 'allow_paths_changed:'
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $contractCard
    & git -C $root add specs/tasks/T0-ROUTE.md
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $statusCard
    $stagedMaskedRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($stagedMaskedRoute.Mode -ceq 'all' -and $stagedMaskedRoute.Reason -ceq 'card-contract-changed') -Message 'a staged card contract edit was masked by final status-only content.'
    & git -C $root reset --hard -q $fixtureBase; Set-Content -LiteralPath (Join-Path $root 'android/app/Main.kt') -Encoding utf8 -Value product
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $contractCard; & git -C $root add specs/tasks/T0-ROUTE.md; & git -C $root commit -q -m card-contract
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $statusCard
    $committedMaskedRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($committedMaskedRoute.Mode -ceq 'all' -and $committedMaskedRoute.Reason -ceq 'card-contract-changed') -Message 'a committed card contract edit was masked by final status-only content.'
    & git -C $root reset --hard -q $fixtureBase
    $entryRoot = Join-Path $root 'entrypoint-proof'
    $notApplicableEntry = Invoke-ValidationTaskEntrypointFixture -Directory $entryRoot -Mode not-applicable -Arguments @('-TaskId', 'T0-ROUTE', '-Base', 'master')
    Assert-ValidationSelfCheck -Condition ($notApplicableEntry.ExitCode -eq 0 -and $notApplicableEntry.Output -match '\[SELFTEST-NOT-APPLICABLE\]' -and $notApplicableEntry.Output -notmatch '\[ENTRYPOINT-DISPATCH\]') -Message 'task entrypoint did not stop successfully for not-applicable routing.'
    $coreEntry = Invoke-ValidationTaskEntrypointFixture -Directory $entryRoot -Mode core -Arguments @('-TaskId', 'T0-ROUTE', '-Base', 'master')
    Assert-ValidationSelfCheck -Condition ($coreEntry.ExitCode -eq 0 -and $coreEntry.Output -match '\[ENTRYPOINT-DISPATCH\] shard=core') -Message 'task entrypoint did not select the core shard.'
    $allEntry = Invoke-ValidationTaskEntrypointFixture -Directory $entryRoot -Mode all -Arguments @('-TaskId', 'T0-ROUTE', '-Base', 'master')
    Assert-ValidationSelfCheck -Condition ($allEntry.ExitCode -eq 0 -and $allEntry.Output -match '\[ENTRYPOINT-DISPATCH\] shard=all') -Message 'task entrypoint did not preserve all routing.'
    $conflictEntry = Invoke-ValidationTaskEntrypointFixture -Directory $entryRoot -Mode core -Arguments @('-TaskId', 'T0-ROUTE', '-Base', 'master', '-Shard', 'core')
    Assert-ValidationSelfCheck -Condition ($conflictEntry.ExitCode -ne 0 -and $conflictEntry.Output -match '\[SELFTEST-TASKID-CONFLICT\]') -Message 'task entrypoint accepted an explicit shard conflict.'
    $failureEntry = Invoke-ValidationTaskEntrypointFixture -Directory $entryRoot -Mode failure -Arguments @('-TaskId', 'T0-ROUTE', '-Base', 'master')
    Assert-ValidationSelfCheck -Condition ($failureEntry.ExitCode -ne 0 -and $failureEntry.Output -match '\[ENTRYPOINT-PROOF-FAILURE\]') -Message 'task entrypoint did not propagate routing failure.'
    foreach ($invalid in @('t0-route', 'T0-MISSING')) {
      $rejected = $false; try { [void](Resolve-SelftestTaskRoute -RepoRoot $root -TaskId $invalid -Base master) } catch { $rejected = $true }
      Assert-ValidationSelfCheck -Condition $rejected -Message "invalid or missing task authority '$invalid' was accepted."
    }
    $unreadable = $false; try { [void](Get-ValidationChangedPaths -WorktreePath ([IO.Path]::GetTempPath()) -BaseOid $fixtureBase) } catch { $unreadable = $true }
    Assert-ValidationSelfCheck -Condition $unreadable -Message 'unreadable repository state was accepted.'
  } finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
  Write-Host 'selftest-risk-routing: PASS'
}

if ($SelfCheck) { Invoke-ValidationSelfCheck }
