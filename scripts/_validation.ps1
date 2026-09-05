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
  $pairs = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.HashtableAst] }, $true) | ForEach-Object { $_.KeyValuePairs } |
    Where-Object { ($_.Item1 -is [Management.Automation.Language.StringConstantExpressionAst]) -and $_.Item1.Value -ceq 'FrozenPaths' })
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
    if ((Test-ValidationFrozenPath -Path $path -FrozenPaths $FrozenPath) -or $path -in $criticalDocs -or $path -like 'scripts/*' -or
        $path -like '.github/*' -or $path -like '.claude/*' -or $path -eq 'CLAUDE.md' -or $path -eq 'AGENTS.md' -or $path -eq 'specs/verdict.schema.json') {
      return [pscustomobject]@{ Mode = 'all'; Reason = 'critical-or-frozen' }
    }
    if ($path -like 'android/*' -or $path -like 'configs/compliance/*') { [void]$classes.Add('product'); continue }
    if (($path -like 'docs/*.md') -or ($path -like 'specs/*.md')) { [void]$classes.Add('docs'); continue }
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

function Test-ValidationStatusOnlyCardChange {
  param([Parameter(Mandatory)][string]$BaselineCard, [Parameter(Mandatory)][string]$CurrentCardPath)

  $item = Get-Item -LiteralPath $CurrentCardPath -Force -ErrorAction SilentlyContinue
  if ($null -eq $item -or $item -isnot [IO.FileInfo] -or $item.LinkType) { return $false }
  $currentCard = [IO.File]::ReadAllText($item.FullName)
  $frontMatterPattern = '(?s)\A\uFEFF?---\r?\n.*?\r?\n---[ \t]*(?:\r?\n|\z)'
  if ($BaselineCard -notmatch $frontMatterPattern -or $currentCard -notmatch $frontMatterPattern) { return $false }
  $statusPattern = '(?m)^status\s*:\s*.*$'
  if ([regex]::Matches($BaselineCard, $statusPattern).Count -ne 1 -or [regex]::Matches($currentCard, $statusPattern).Count -ne 1) { return $false }
  $baselineWithoutStatus = [regex]::Replace($BaselineCard, $statusPattern, 'status: <selftest-routing-status>', 1).TrimEnd([char[]]"`r`n")
  $currentWithoutStatus = [regex]::Replace($currentCard, $statusPattern, 'status: <selftest-routing-status>', 1).TrimEnd([char[]]"`r`n")
  return $baselineWithoutStatus -ceq $currentWithoutStatus
}

function Resolve-SelftestTaskRoute {
  param([Parameter(Mandatory)][string]$RepoRoot, [Parameter(Mandatory)][string]$TaskId, [Parameter(Mandatory)][string]$Base)

  if ($TaskId -notmatch '^T\d+-[A-Z0-9]+(?:-[A-Z0-9]+)*$') { throw "[SELFTEST-TASKID-BADID] '$TaskId' is not a task-card ID." }
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
  if ($cardPath -in $allPaths -and -not (Test-ValidationStatusOnlyCardChange -BaselineCard $card -CurrentCardPath (Join-Path $worktreePath $cardPath))) {
    return [pscustomobject]@{ Mode = 'all'; Reason = 'card-contract-changed'; BaseOid = $baseOid; WorktreePath = $worktreePath; Paths = $allPaths }
  }
  $paths = @($allPaths | Where-Object { $_ -cne $cardPath })
  $frozen = Get-ValidationFrozenPaths -RepoRoot $RepoRoot -BaseOid $baseOid
  $route = Resolve-SelftestRiskRoute -ChangedPath $paths -FrozenPath $frozen
  return [pscustomobject]@{ Mode = $route.Mode; Reason = $route.Reason; BaseOid = $baseOid; WorktreePath = $worktreePath; Paths = $paths }
}

function Assert-ValidationSelfCheck { param([bool]$Condition, [string]$Message); if (-not $Condition) { throw "[SELFTEST-RISK-ROUTING-SELFCHECK] $Message" } }

function Invoke-ValidationSelfCheck {
  Assert-ValidationSelfCheck -Condition ([bool](Get-Command Resolve-SelftestTaskRoute -CommandType Function -ErrorAction SilentlyContinue)) -Message 'Resolve-SelftestTaskRoute is missing.'
  $frozen = @('android/core/src/main/sqldelight/')
  $cases = @(
    @{ Name='product'; Paths=@('android/app/src/main/Main.kt', 'configs/compliance/rules.json'); Mode='not-applicable' }, @{ Name='docs'; Paths=@('docs/guide.md', 'specs/tasks/T0-X.md'); Mode='core' },
    @{ Name='frozen'; Paths=@('android/core/src/main/sqldelight/foo.sq'); Mode='all' }, @{ Name='critical'; Paths=@('scripts/task.ps1'); Mode='all' },
    @{ Name='unknown'; Paths=@('README.md'); Mode='all' }, @{ Name='mixed'; Paths=@('docs/guide.md', 'android/app/src/main/Main.kt'); Mode='all' }
  )
  foreach ($case in $cases) { $actual = Resolve-SelftestRiskRoute -ChangedPath $case.Paths -FrozenPath $frozen; Assert-ValidationSelfCheck -Condition ($actual.Mode -ceq $case.Mode) -Message "pure case '$($case.Name)' expected $($case.Mode), got $($actual.Mode)." }
  foreach ($badConfig in @(
    "`$script:ScaffoldConfig = @{}",
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
    Move-Item -LiteralPath (Join-Path $root 'docs/rename-from.md') -Destination (Join-Path $root 'scripts/rename-to.ps1')
    $renamePaths = Get-ValidationChangedPaths -WorktreePath $root -BaseOid $fixtureBase
    Assert-ValidationSelfCheck -Condition (('docs/rename-from.md' -in $renamePaths) -and ('scripts/rename-to.ps1' -in $renamePaths)) -Message 'an actual rename did not retain both old and new paths.'
    Move-Item -LiteralPath (Join-Path $root 'scripts/rename-to.ps1') -Destination (Join-Path $root 'docs/rename-from.md')
    New-Item -ItemType Directory -Force (Join-Path $root 'android/app') | Out-Null
    Set-Content -LiteralPath (Join-Path $root 'android/app/Main.kt') -Encoding utf8 -Value product
    $statusCard = $card -replace 'status: todo', 'status: doing'
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value ($statusCard -replace 'allow_paths:', 'allow_paths_changed:')
    $authorityRoute = Resolve-SelftestTaskRoute -RepoRoot $root -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck -Condition ($authorityRoute.Mode -ceq 'all' -and $authorityRoute.Reason -ceq 'card-contract-changed' -and $authorityRoute.WorktreePath -ieq $root) -Message 'a branch-side card contract change did not refuse cheaper routing from the registered slim-card worktree.'
    Set-Content -LiteralPath (Join-Path $root 'specs/tasks/T0-ROUTE.md') -Encoding utf8 -Value $statusCard
    Assert-ValidationSelfCheck -Condition (Test-ValidationStatusOnlyCardChange -BaselineCard $card -CurrentCardPath (Join-Path $root 'specs/tasks/T0-ROUTE.md')) -Message 'status-only card bookkeeping was not recognized separately from contract changes.'
  } finally { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue }
  Write-Host 'selftest-risk-routing: PASS'
}

if ($SelfCheck) { Invoke-ValidationSelfCheck }
