#requires -Version 7
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
  $committed = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--no-ext-diff', '--no-textconv', '--name-status', '-z', '--find-renames', "$BaseOid...HEAD", '--') -Code 'SELFTEST-ROUTE-COMMITTED-DIFF'
  $dirty = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--no-ext-diff', '--no-textconv', '--name-status', '-z', '--find-renames', '--') -Code 'SELFTEST-ROUTE-DIRTY-DIFF'
  $staged = Assert-ValidationGit -RepoRoot $WorktreePath -Arguments @('diff', '--no-ext-diff', '--no-textconv', '--cached', '--name-status', '-z', '--find-renames', '--') -Code 'SELFTEST-ROUTE-STAGED-DIFF'
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

  $entry = (Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('ls-tree', $BaseOid, '--', $Path) -Code $Code).Trim()
  if ($entry -notmatch '^100(?:644|755) blob [0-9a-f]+\t') { throw "[$Code] baseline '$Path' is not a regular blob." }
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
  param([Parameter(Mandatory)][AllowEmptyCollection()][string[]]$ChangedPath, [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$FrozenPath)

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
  $baseRef = if ($Base.StartsWith('origin/', [StringComparison]::Ordinal)) { "refs/remotes/$Base" } else { "refs/heads/$Base" }
  $baseOid = (Assert-ValidationGit -RepoRoot $RepoRoot -Arguments @('rev-parse', '--verify', "$baseRef^{commit}") -Code 'SELFTEST-ROUTE-BASE-MISSING').Trim().ToLowerInvariant()
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

function Invoke-SelftestTaskSelection {
  param([string]$RepoRoot, [string]$TaskId, [string]$Base, [bool]$ForwardStrictLint, [bool]$StrictLintValue)
  $route = Resolve-SelftestTaskRoute -RepoRoot $RepoRoot -TaskId $TaskId -Base $Base
  Write-Host "[SELFTEST-TASK-ROUTE] task=$TaskId mode=$($route.Mode) base=$($route.BaseOid) reason=$($route.Reason)"
  if ($route.Mode -eq 'not-applicable') {
    Write-Host '[SELFTEST-NOT-APPLICABLE] No scaffold checks selected; product verify is still required.'
    return 0
  }
  if ($route.Mode -eq 'all') {
    return (Invoke-SelftestAll -SourceRoot $route.WorktreePath -ForwardStrictLint $ForwardStrictLint -StrictLintValue $StrictLintValue)
  }
  if ($route.Mode -ne 'core') { throw '[SELFTEST-ROUTE-MODE-INVALID] unknown selected coverage' }
  $root = Join-Path ([IO.Path]::GetTempPath()) "scaffold-selftest-task-$PID-$([guid]::NewGuid().ToString('N'))"
  try {
    $snapshot = New-SelftestSnapshot -SourceRoot $route.WorktreePath -SnapshotRoot $root -Name core -GitExe (Get-Command git -ErrorAction Stop)
    $arguments = @('-NoProfile', '-File', (Join-Path $snapshot 'scripts/selftest.ps1'), '-Shard', 'core')
    if ($ForwardStrictLint) { $arguments += "-StrictLint:`$$($StrictLintValue.ToString().ToLowerInvariant())" }
    & pwsh @arguments | Out-Host
    return $LASTEXITCODE
  } finally {
    $resolved = [IO.Path]::GetFullPath($root)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($temp,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $resolved) -notlike 'scaffold-selftest-task-*') { throw 'unsafe task snapshot cleanup target' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
  }
}

function Assert-ValidationSelfCheck([bool]$Condition, [string]$Message) {
  if (-not $Condition) { throw "[SELFTEST-RISK-ROUTING-SELFCHECK] $Message" }
}
function Invoke-ValidationSelfCheck {
  Assert-ValidationSelfCheck ([bool](Get-Command Resolve-SelftestTaskRoute -ErrorAction SilentlyContinue)) 'routing capability missing'
  $cases = @(
    @{ Name='product'; Paths=@('android/app/Main.kt','configs/compliance/a.json'); Mode='not-applicable' },
    @{ Name='docs'; Paths=@('docs/guide.md','specs/tasks/T0-X.md'); Mode='core' },
    @{ Name='frozen'; Paths=@('android/frozen/model.kt'); Mode='all' },
    @{ Name='critical'; Paths=@('scripts/task.ps1'); Mode='all' },
    @{ Name='critical-doc'; Paths=@('docs/SECURITY.md'); Mode='all' },
    @{ Name='unknown-config'; Paths=@('configs/other.json'); Mode='all' },
    @{ Name='unknown'; Paths=@('README.md'); Mode='all' },
    @{ Name='mixed'; Paths=@('android/a.kt','docs/a.md'); Mode='all' },
    @{ Name='case-product'; Paths=@('Android/a.kt'); Mode='all' },
    @{ Name='case-docs'; Paths=@('Docs/a.md'); Mode='all' },
    @{ Name='empty'; Paths=@(); Mode='all' }
  )
  foreach ($case in $cases) {
    $actual = Resolve-SelftestRiskRoute -ChangedPath $case.Paths -FrozenPath @('android/frozen/')
    Assert-ValidationSelfCheck ($actual.Mode -ceq $case.Mode) "policy $($case.Name)"
  }
  foreach ($config in @('$script:ScaffoldConfig = @{}', '$decoy = @{ FrozenPaths = @() }; $script:ScaffoldConfig = @{}',
    '$script:ScaffoldConfig = @{ FrozenPaths = @($env:USERPROFILE) }', '$script:ScaffoldConfig = @{ FrozenPaths = @(''['') }')) {
    $rejected = $false
    try { [void](Convert-ValidationFrozenPaths $config) } catch { $rejected = $true }
    Assert-ValidationSelfCheck $rejected 'invalid frozen authority'
  }
  $ownedRoot = Join-Path ([IO.Path]::GetTempPath()) "selftest-risk-routing-$PID-$([guid]::NewGuid().ToString('N'))"
  function Write-FixtureFile($Root, $Path, $Text) {
    $target = Join-Path $Root $Path
    New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
    [IO.File]::WriteAllText($target, $Text)
  }
  function Fixture-Git($Root, [string[]]$Arguments) {
    $result = & git -C $Root @Arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw "fixture git failed: $result" }
    return $result.Trim()
  }
  function New-RouteFixture($Name) {
    $primary = Join-Path $ownedRoot "$Name/primary"; $wt = Join-Path $ownedRoot "$Name/task"
    New-Item -ItemType Directory -Force $primary | Out-Null
    [void](Fixture-Git $primary @('init','-q','-b','master'))
    [void](Fixture-Git $primary @('config','user.name','selftest'))
    [void](Fixture-Git $primary @('config','user.email','selftest@example.invalid'))
    [void](Fixture-Git $primary @('config','core.autocrlf','false'))
    $card = "---`nid: T0-ROUTE`nstatus: todo`nallow_paths:`n  - docs/`n---`n"
    Write-FixtureFile $primary 'specs/tasks/T0-ROUTE.md' $card
    Write-FixtureFile $primary 'scripts/_config.ps1' '$script:ScaffoldConfig = @{ FrozenPaths = @(''android/frozen/'') }'
    Write-FixtureFile $primary 'docs/base.md' "base`n"
    Write-FixtureFile $primary 'android/rename.kt' "rename`n"
    [void](Fixture-Git $primary @('add','-A')); [void](Fixture-Git $primary @('commit','-q','-m','base'))
    [void](Fixture-Git $primary @('worktree','add','-q','-b','T0-ROUTE',$wt,'master'))
    return @{ Primary=$primary; Worktree=$wt; Card=$card; Base=(Fixture-Git $primary @('rev-parse','master')) }
  }
  function Route($Fixture) { Resolve-SelftestTaskRoute -RepoRoot $Fixture.Primary -TaskId T0-ROUTE -Base master }
  function Invoke-EntryFixture($Fixture, [int]$ChildExit, [string[]]$Arguments = @('-TaskId','T0-ROUTE','-Base','master')) {
    $scripts = Join-Path $Fixture.Primary 'scripts'
    New-Item -ItemType Directory -Force $scripts | Out-Null
    foreach ($file in @('selftest.ps1','_gitbase.ps1','_encoding.ps1')) {
      Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination (Join-Path $scripts $file) -Force
    }
    Assert-ValidationSelfCheck ((Get-FileHash (Join-Path $scripts 'selftest.ps1')).Hash -ceq (Get-FileHash (Join-Path $PSScriptRoot 'selftest.ps1')).Hash) 'actual entry source bytes'
    Write-FixtureFile $Fixture.Primary '.github/workflows/scaffold-selftest.yml' ([IO.File]::ReadAllText((Join-Path (Split-Path -Parent $PSScriptRoot) '.github/workflows/scaffold-selftest.yml')))
    Write-FixtureFile $Fixture.Primary 'init-scaffold.ps1' '# bounded entry fixture'
    New-Item -ItemType Directory -Force (Join-Path $Fixture.Primary '.claude/hooks') | Out-Null
    # Dependency aliases bound only expensive execution and the post-hook filesystem boundary.
    # The launched selftest file, parameters, initialization and statement order stay byte-identical.
    $boundaries = @'
function Invoke-RouteFixtureAll {
  param($SourceRoot,$ForwardStrictLint,$StrictLintValue)
  Write-Host "[DISPATCH] all=$SourceRoot strict=$StrictLintValue"
  if (__DEFAULT__) {
    Write-Host '[DEFAULT-BOUNDARY] launching actual core'
    & pwsh -NoProfile -File (Join-Path $SourceRoot 'scripts/selftest.ps1') -Shard core | Out-Host
    return $LASTEXITCODE
  }
  return __EXIT__
}
function New-RouteFixtureSnapshot {
  param($SourceRoot,$SnapshotRoot,$Name,$GitExe)
  Write-Host "[DISPATCH] core=$SourceRoot"
  New-Item -ItemType Directory -Force (Join-Path $SnapshotRoot 'scripts') | Out-Null
  Set-Content (Join-Path $SnapshotRoot 'scripts/selftest.ps1') 'param($Shard,[switch]$StrictLint); Write-Host "[CHILD] shard=$Shard strict=$StrictLint"; exit __EXIT__'
  return $SnapshotRoot
}
function Get-RouteFixtureChildItem {
  [CmdletBinding()]param([string]$Path,[string]$Filter,[switch]$Recurse)
  if ($Path -eq (Join-Path $RepoRoot '.claude/workflows')) {
    Write-Host "[BOUNDED-CORE] fail=$script:fail"
    if ($script:fail) { exit 37 }
    exit 0
  }
  Microsoft.PowerShell.Management\Get-ChildItem @PSBoundParameters
}
Set-Alias Invoke-SelftestAll Invoke-RouteFixtureAll
Set-Alias New-SelftestSnapshot New-RouteFixtureSnapshot
Set-Alias Get-ChildItem Get-RouteFixtureChildItem
'@
    $boundaries = $boundaries.Replace('__EXIT__',[string]$ChildExit).Replace('__DEFAULT__',('$' + (-not ($Arguments -contains '-TaskId')).ToString().ToLowerInvariant()))
    [IO.File]::AppendAllText((Join-Path $scripts '_gitbase.ps1'), "`n" + $boundaries)
    $validation = "param([switch]`$SelfCheck)`nif (`$SelfCheck) { Write-Host '[ROUTE-SELFCHECK] requested=True'; exit $ChildExit }`n. '" + (Join-Path $PSScriptRoot '_validation.ps1').Replace("'","''") + "'`n"
    Write-FixtureFile $Fixture.Primary 'scripts/_validation.ps1' $validation
    $path = Join-Path $scripts 'selftest.ps1'
    $output = & pwsh -NoProfile -File $path @Arguments 2>&1 | Out-String
    $code = $LASTEXITCODE
    Write-Verbose ("[ACTUAL-ENTRY] args=$($Arguments -join ' ') exit=$code`n$output")
    return @{ Exit=$code; Text=$output }
  }
  try {
    foreach ($state in @('committed','staged','dirty','untracked')) {
      $f = New-RouteFixture $state
      $path = if ($state -eq 'dirty') { 'docs/base.md' } else { 'docs/new.md' }
      Write-FixtureFile $f.Worktree $path 'changed'
      if ($state -in @('committed','staged')) { [void](Fixture-Git $f.Worktree @('add','-A')) }
      if ($state -eq 'committed') { [void](Fixture-Git $f.Worktree @('commit','-q','-m','change')) }
      $r = Route $f
      Assert-ValidationSelfCheck ($r.Mode -ceq 'core' -and $r.BaseOid -ceq $f.Base -and $r.WorktreePath -ieq $f.Worktree) "state $state identity and route"
    }
    foreach ($state in @('staged','committed','working')) {
      $f = New-RouteFixture "rename-$state"
      if ($state -eq 'working') { Move-Item -LiteralPath (Join-Path $f.Worktree 'android/rename.kt') -Destination (Join-Path $f.Worktree 'docs/renamed.md') }
      else { [void](Fixture-Git $f.Worktree @('mv','android/rename.kt','docs/renamed.md')) }
      if ($state -eq 'committed') { [void](Fixture-Git $f.Worktree @('commit','-q','-am','rename')) }
      $r = Route $f
      Assert-ValidationSelfCheck ($r.Mode -ceq 'all' -and $r.Paths -ccontains 'android/rename.kt' -and $r.Paths -ccontains 'docs/renamed.md') "rename $state both endpoints"
    }
    foreach ($state in @('working','staged','committed')) {
      $f = New-RouteFixture "card-$state"
      Write-FixtureFile $f.Worktree 'android/new.kt' 'product'
      $status = $f.Card.Replace('status: todo','status: doing')
      Write-FixtureFile $f.Worktree 'specs/tasks/T0-ROUTE.md' $status
      Assert-ValidationSelfCheck ((Route $f).Mode -ceq 'not-applicable') "status only $state"
      Write-FixtureFile $f.Worktree 'specs/tasks/T0-ROUTE.md' $status.Replace('allow_paths:','changed_paths:')
      if ($state -ne 'working') {
        [void](Fixture-Git $f.Worktree @('add','specs/tasks/T0-ROUTE.md'))
        if ($state -eq 'committed') { [void](Fixture-Git $f.Worktree @('commit','-q','-m','contract')) }
        Write-FixtureFile $f.Worktree 'specs/tasks/T0-ROUTE.md' $status
        if ($state -eq 'committed') { [void](Fixture-Git $f.Worktree @('add','specs/tasks/T0-ROUTE.md')) }
      }
      Assert-ValidationSelfCheck ((Route $f).Mode -ceq 'all') "card authority $state"
    }
    $f = New-RouteFixture 'frozen'
    Write-FixtureFile $f.Worktree 'scripts/_config.ps1' '$script:ScaffoldConfig = @{ FrozenPaths = @() }'
    $frozen = @(Get-ValidationFrozenPaths -RepoRoot $f.Primary -BaseOid $f.Base)
    Assert-ValidationSelfCheck ($frozen.Count -eq 1 -and $frozen[0] -ceq 'android/frozen/' -and (Route $f).Mode -ceq 'all') 'baseline frozen ownership'
    [void](Fixture-Git $f.Worktree @('commit','-q','-am','branch-config'))
    $branchFrozen = @(Get-ValidationFrozenPaths -RepoRoot $f.Worktree -BaseOid $f.Base)
    Assert-ValidationSelfCheck ($branchFrozen.Count -eq 1 -and $branchFrozen[0] -ceq 'android/frozen/') 'branch HEAD config is not authority'
    Write-FixtureFile $f.Worktree 'specs/tasks/T0-ROUTE.md' $f.Card.Replace('id: T0-ROUTE','id: T0-OTHER')
    [void](Fixture-Git $f.Worktree @('commit','-q','-am','branch-card'))
    $branchCardRoute = Resolve-SelftestTaskRoute -RepoRoot $f.Worktree -TaskId T0-ROUTE -Base master
    Assert-ValidationSelfCheck ($branchCardRoute.Mode -ceq 'all' -and $branchCardRoute.Reason -ceq 'card-contract-changed') 'branch HEAD card is not authority'
    foreach ($mode in @('product','core','all')) {
      $f = New-RouteFixture "entry-$mode"
      $path = switch ($mode) { product {'android/new.kt'} core {'docs/new.md'} all {'unknown.bin'} }
      Write-FixtureFile $f.Worktree $path 'change'
      foreach ($code in @(0,37)) {
        $r = Invoke-EntryFixture $f $code @('-TaskId','T0-ROUTE','-Base','master','-StrictLint')
        if ($mode -eq 'product') {
          Assert-ValidationSelfCheck ($r.Exit -eq 0 -and $r.Text -match '\[SELFTEST-NOT-APPLICABLE\]' -and $r.Text -match 'product verify' -and $r.Text -notmatch 'PASS|\[DISPATCH\]') 'entry product honesty'
        } else {
          Assert-ValidationSelfCheck ($r.Exit -eq $code -and $r.Text.Contains("[DISPATCH] $mode=$($f.Worktree)")) "entry $mode exit $code and resolved source"
          if ($mode -eq 'core') { Assert-ValidationSelfCheck ($r.Text -match '\[CHILD\] shard=core strict=True') 'core child arguments' }
        }
      }
      $default = Invoke-EntryFixture $f 37 @()
      Assert-ValidationSelfCheck ($default.Exit -eq 37 -and $default.Text.Contains("[DISPATCH] all=$($f.Primary)") -and
        $default.Text -match '\[ROUTE-SELFCHECK\] requested=True' -and $default.Text -match '闸1\(validation\)' -and
        $default.Text -match '\[BOUNDED-CORE\] fail=True') ("actual default dispatch reaches core selfcheck and consumes failure (exit=$($default.Exit)): $($default.Text)")
      $conflict = Invoke-EntryFixture $f 0 @('-TaskId','T0-ROUTE','-Base','master','-Shard','core')
      Assert-ValidationSelfCheck ($conflict.Exit -ne 0 -and $conflict.Text -match '\[SELFTEST-TASKID-CONFLICT\]') 'entry conflict'
    }
    $coreGreen = Invoke-EntryFixture $f 0 @('-Shard','core')
    Assert-ValidationSelfCheck ($coreGreen.Exit -eq 0 -and $coreGreen.Text -match '\[ROUTE-SELFCHECK\] requested=True' -and
      $coreGreen.Text -match '\[BOUNDED-CORE\] fail=False' -and $coreGreen.Text -notmatch '闸1\(validation\)') 'actual core selfcheck success'
    $badBase = Invoke-EntryFixture $f 0 @('-TaskId','T0-ROUTE','-Base','absent')
    Assert-ValidationSelfCheck ($badBase.Exit -ne 0 -and $badBase.Text -match '\[SELFTEST-ROUTE-BASE-MISSING\]' -and
      $badBase.Text -notmatch '\[DISPATCH\]') 'actual entry Base binding'
    foreach ($bad in @(@{Id='bad';Base='master';Code='SELFTEST-TASKID-BADID'},@{Id='T0-MISSING';Base='master';Code='SELFTEST-ROUTE-BASE-CARD'},@{Id='T0-ROUTE';Base='absent';Code='SELFTEST-ROUTE-BASE-MISSING'})) {
      $failure = ''
      try { [void](Resolve-SelftestTaskRoute -RepoRoot $f.Primary -TaskId $bad.Id -Base $bad.Base) } catch { $failure = $_.Exception.Message }
      Assert-ValidationSelfCheck ($failure.Contains("[$($bad.Code)]")) "authority refusal $($bad.Id)/$($bad.Base)"
    }
    $f = New-RouteFixture 'remote-base'
    [void](Fixture-Git $f.Primary @('update-ref','refs/remotes/origin/master',$f.Base))
    Write-FixtureFile $f.Worktree 'docs/new.md' 'docs'
    $remote = Resolve-SelftestTaskRoute -RepoRoot $f.Primary -TaskId T0-ROUTE -Base origin/master
    Assert-ValidationSelfCheck ($remote.Mode -ceq 'core' -and $remote.BaseOid -ceq $f.Base) 'explicit remote tracking base'
    $f = New-RouteFixture 'wrong-baseline-id'
    Write-FixtureFile $f.Primary 'specs/tasks/T0-ROUTE.md' $f.Card.Replace('id: T0-ROUTE','id: T0-OTHER')
    [void](Fixture-Git $f.Primary @('commit','-q','-am','wrong-id'))
    $failure = ''; try { [void](Route $f) } catch { $failure = $_.Exception.Message }
    Assert-ValidationSelfCheck ($failure.Contains('[SELFTEST-ROUTE-CARD-INVALID]')) 'baseline card id match'
    $f = New-RouteFixture 'missing-task-tree'
    [void](Fixture-Git $f.Worktree @('branch','-m','T0-OTHER'))
    $failure = ''; try { [void](Route $f) } catch { $failure = $_.Exception.Message }
    Assert-ValidationSelfCheck ($failure.Contains('[SELFTEST-ROUTE-WORKTREE-AMBIGUOUS]')) 'matching registered tree required'
    $entryFailure = Invoke-EntryFixture $f 0
    Assert-ValidationSelfCheck ($entryFailure.Exit -ne 0 -and $entryFailure.Text -match '\[SELFTEST-ROUTE-WORKTREE-AMBIGUOUS\]' -and $entryFailure.Text -notmatch '\[DISPATCH\]') 'entry authority failure propagation'
    $rejected = $false
    try { [void](Resolve-SelftestTaskRoute -RepoRoot $ownedRoot -TaskId T0-ROUTE -Base master) } catch { $rejected = $true }
    Assert-ValidationSelfCheck $rejected 'unreadable repository refusal'
  } finally {
    $resolved = [IO.Path]::GetFullPath($ownedRoot)
    $parent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    if (-not $resolved.StartsWith($parent,[StringComparison]::OrdinalIgnoreCase) -or (Split-Path -Leaf $resolved) -notlike 'selftest-risk-routing-*') { throw 'unsafe fixture cleanup target' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
  }
  Write-Host 'selftest-risk-routing: PASS'
}
if ($SelfCheck) { Invoke-ValidationSelfCheck }
