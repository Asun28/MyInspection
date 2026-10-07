#requires -Version 7
<#
.SYNOPSIS
  Read-only report of the work other sessions hold (T0-LIVE-WORK-GUARD, L218): worktrees with uncommitted or unmerged
  changes, branches named after a card, and handoff files. It never writes to the repository, fetches or pushes.
.DESCRIPTION
  live-work.ps1 [-RepoPath <dir>] [-Base master] [-SinceHours 48] [-BudgetSec 10]
      One [LIVE-WORK] line per worktree holding work, one [LIVE-WORK-STALE] line counting those whose newest change
      is older than -SinceHours, [LIVE-WORK-NONE] when no worktree holds work, and one [LIVE-WORK-HANDOFF] line for
      the main checkout's progress.md HANDOFF block and for each _local/handoff-*.md. Exit 0.
  live-work.ps1 -TaskId <id> [-RepoPath <dir>] [-Base master] [-SinceHours 48]
      [LIVE-WORK-OVERLAP] for each worktree whose changed paths fall under the card's allow_paths and whose newest
      change is within -SinceHours, for a worktree whose branch is the card id, and for a branch <id> or r5-<id>
      (local or origin) whose tip is not on the base. An older overlap gets [LIVE-WORK-OVERLAP-STALE], which does not
      count. Exit 3 when any [LIVE-WORK-OVERLAP] line was printed, 0 when none, 2 when a probe fails.
  live-work.ps1 -SelfCheck
      Builds temporary Git fixtures and checks both modes and the dirty-worktree hook; prints [LIVE-WORK-SELF-CHECK-PASS].
  The worktree the caller runs in is left out, except the main checkout, which every session shares. The base is
  origin/<Base> when that ref exists, else <Base>.
#>
[CmdletBinding()]
param(
  [string]$TaskId,
  [string]$RepoPath = (Get-Location).Path,
  [string]$Base = 'master',
  [int]$SinceHours = 48,
  [int]$BudgetSec = 10,
  [switch]$SelfCheck,
  [switch]$AsLibrary
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_scope.ps1')     # Get-ScaffoldCardAllowPathFromText, Get-ScaffoldOutOfScopePath
. (Join-Path $PSScriptRoot '_context.ps1')   # Get-ScaffoldHandoffBlock

# --no-optional-locks: git status would otherwise refresh and rewrite the probed worktree's index.
function Invoke-LiveWorkGit([string]$Dir, [string[]]$GitArgs) {
  $out = @(& git --no-optional-locks -C $Dir -c core.quotepath=false @GitArgs 2>$null)
  if ($LASTEXITCODE -ne 0) { throw "[LIVE-WORK-PROBE-FAIL] git $($GitArgs -join ' ') exited $LASTEXITCODE in $Dir" }
  return $out
}

function Get-LiveWorkBaseRef([string]$Dir, [string]$Name) {
  foreach ($r in @("refs/remotes/origin/$Name", "refs/heads/$Name")) {
    & git --no-optional-locks -C $Dir rev-parse --verify --quiet $r *> $null
    if ($LASTEXITCODE -eq 0) { return $r }
  }
  throw "[LIVE-WORK-PROBE-FAIL] neither origin/$Name nor $Name resolves in $Dir"
}

# Registered worktrees from `git worktree list --porcelain`; the first is the main checkout. Missing directories
# (prunable entries) and bare entries hold no files and are skipped.
function Get-LiveWorkTrees([string]$Dir) {
  $trees = [System.Collections.Generic.List[object]]::new(); $cur = $null
  foreach ($l in @(Invoke-LiveWorkGit $Dir @('worktree', 'list', '--porcelain')) + @('')) {
    if ($l.StartsWith('worktree ')) { $cur = [pscustomobject]@{ Path = $l.Substring(9); Branch = 'detached'; Bare = $false; Main = ($trees.Count -eq 0) } }
    elseif ($null -ne $cur -and $l.StartsWith('branch refs/heads/')) { $cur.Branch = $l.Substring(18) }
    elseif ($null -ne $cur -and $l -ceq 'bare') { $cur.Bare = $true }
    elseif ($l -eq '' -and $null -ne $cur) { if (-not $cur.Bare -and (Test-Path -LiteralPath $cur.Path)) { $trees.Add($cur) }; $cur = $null }
  }
  return $trees.ToArray()
}

# Uncommitted paths of one worktree, untracked files included.
function Get-LiveWorkUncommitted([string]$Path) {
  return @(Invoke-LiveWorkGit $Path @('status', '--porcelain=v1', '--untracked-files=all') | Where-Object { $_.Length -gt 3 } | ForEach-Object {
      $p = $_.Substring(3); if ($p -match ' -> (.+)$') { $p = $Matches[1] }; $p.Trim('"') })
}

# The newest last-write time (UTC) among the given worktree-relative paths that exist, or $null.
function Get-LiveWorkNewest([string]$Path, [string[]]$Rel) {
  $newest = $null
  foreach ($r in @($Rel | Select-Object -Unique)) {
    $fi = [IO.FileInfo]::new((Join-Path $Path $r))
    if ($fi.Exists -and ($null -eq $newest -or $fi.LastWriteTimeUtc -gt $newest)) { $newest = $fi.LastWriteTimeUtc }
  }
  return $newest
}

# What one worktree holds: uncommitted paths, paths changed by its commits that are not on the base, and the newest
# last-write time among those paths that exist, or the HEAD commit time when none exists (deletions only).
function Get-LiveWorkHolding([string]$Path, [string]$BaseRef) {
  $unc = @(Get-LiveWorkUncommitted $Path)
  $unm = @(Invoke-LiveWorkGit $Path @('-c', 'diff.renames=false', 'diff', '--name-only', "$BaseRef...HEAD") | Where-Object { $_ })
  $newest = Get-LiveWorkNewest $Path (@($unc) + @($unm))
  if ($null -eq $newest -and (@($unc).Count + @($unm).Count)) { $newest = [DateTimeOffset]::FromUnixTimeSeconds([long]@(Invoke-LiveWorkGit $Path @('log', '-1', '--format=%ct', 'HEAD'))[0]).UtcDateTime }
  return [pscustomobject]@{ Uncommitted = $unc; Unmerged = $unm; Newest = $newest }
}

function Format-LiveWorkTime($Utc) { if ($null -eq $Utc) { return 'unknown' }; return $Utc.ToString('yyyy-MM-ddTHH:mm:ssZ') }

function Test-LiveWorkSamePath([string]$A, [string]$B) {
  $n = { param($p) [IO.Path]::GetFullPath($p).TrimEnd('\', '/') }
  return [string]::Equals((& $n $A), (& $n $B), [StringComparison]::OrdinalIgnoreCase)
}

# The worktrees to report: every registered one except the caller's own linked worktree.
function Get-LiveWorkOthers([string]$Dir) {
  $top = @(Invoke-LiveWorkGit $Dir @('rev-parse', '--show-toplevel'))[0]
  return @(Get-LiveWorkTrees $Dir | Where-Object { $_.Main -or -not (Test-LiveWorkSamePath $_.Path $top) })
}

function Get-LiveWorkHandoffLines([string]$MainRoot) {
  $files = @(Join-Path $MainRoot 'progress.md') + @(Get-ChildItem -LiteralPath (Join-Path $MainRoot '_local') -Filter 'handoff-*.md' -File -ErrorAction SilentlyContinue | ForEach-Object FullName)
  foreach ($f in $files) {
    if (-not (Test-Path -LiteralPath $f -PathType Leaf)) { continue }
    $text = [IO.File]::ReadAllText($f)
    $block = Get-ScaffoldHandoffBlock -Text $text
    if (-not $block.Trim()) { $block = $text }
    $field = { param($k) if ($block -cmatch "(?m)^$($k):[ \t]*(.+)$") { $v = $Matches[1].Trim(); if ($v.Length -gt 100) { $v = $v.Substring(0, 100) + '...' }; $v } else { '?' } }
    "[LIVE-WORK-HANDOFF] file=$([IO.Path]::GetRelativePath($MainRoot, $f).Replace('\', '/')) task=$(& $field 'TASK') updated=$(& $field 'UPDATED')"
  }
}

function Invoke-LiveWorkSummary([string]$Dir, [string]$BaseName, [int]$Hours, [int]$Budget) {
  '[LIVE-WORK-SUMMARY] work other sessions may hold, read-only; before starting, resuming, splitting or resetting a card run scripts/live-work.ps1 -TaskId <id> and ask the user about any overlap (L218)'
  $baseRef = Get-LiveWorkBaseRef $Dir $BaseName
  $clock = [Diagnostics.Stopwatch]::StartNew(); $cutoff = [DateTime]::UtcNow.AddHours(-$Hours)
  $held = 0; $stale = 0; $unknown = 0; $trees = @(Get-LiveWorkOthers $Dir)
  foreach ($t in $trees) {
    if ($clock.Elapsed.TotalSeconds -gt $Budget) { $unknown++; "[LIVE-WORK-UNKNOWN] path=$($t.Path) (time budget of $Budget s spent)"; continue }
    try { $h = Get-LiveWorkHolding $t.Path $baseRef } catch { $unknown++; "[LIVE-WORK-UNKNOWN] path=$($t.Path) ($($_.Exception.Message))"; continue }
    if (-not (@($h.Uncommitted).Count + @($h.Unmerged).Count)) { continue }
    if ($null -ne $h.Newest -and $h.Newest -lt $cutoff) { $stale++; continue }
    $held++
    "[LIVE-WORK] path=$($t.Path) branch=$($t.Branch) uncommitted=$(@($h.Uncommitted).Count) unmerged=$(@($h.Unmerged).Count) newest=$(Format-LiveWorkTime $h.Newest)"
  }
  if ($stale) { "[LIVE-WORK-STALE] count=$stale (worktrees whose newest change is older than $Hours h)" }
  if (-not $held -and -not $stale -and -not $unknown) { '[LIVE-WORK-NONE] no other worktree holds uncommitted or unmerged work' }
  Get-LiveWorkHandoffLines @($trees | Where-Object Main)[0].Path
}

# Returns the overlap lines for one card. A worktree whose overlapping work is older than $Hours gets a
# [LIVE-WORK-OVERLAP-STALE] line, which does not count; a worktree or branch named after the card always counts.
function Get-LiveWorkOverlap([string]$Dir, [string]$Id, [string]$BaseName, [int]$Hours) {
  $baseRef = Get-LiveWorkBaseRef $Dir $BaseName
  $cutoff = [DateTime]::UtcNow.AddHours(-$Hours)
  & git --no-optional-locks -C $Dir show "${baseRef}:specs/tasks/$Id.md" *> $null
  $cardText = if ($LASTEXITCODE -eq 0) { (Invoke-LiveWorkGit $Dir @('show', "${baseRef}:specs/tasks/$Id.md")) -join "`n" }
  elseif (Test-Path -LiteralPath (Join-Path $Dir "specs/tasks/$Id.md")) { [IO.File]::ReadAllText((Join-Path $Dir "specs/tasks/$Id.md")) }
  else { throw "[LIVE-WORK-PROBE-FAIL] no card specs/tasks/$Id.md on $baseRef or in $Dir" }
  $allow = @(Get-ScaffoldCardAllowPathFromText -CardText $cardText)
  if (-not $allow.Count) { throw "[LIVE-WORK-PROBE-FAIL] the card $Id declares no allow_paths" }
  foreach ($t in @(Get-LiveWorkOthers $Dir)) {
    $h = Get-LiveWorkHolding $t.Path $baseRef
    $changed = @(@($h.Uncommitted) + @($h.Unmerged) | Select-Object -Unique)
    $outside = @(Get-ScaffoldOutOfScopePath -ChangedPath $changed -AllowPath $allow)
    $inside = @($changed | Where-Object { $outside -notcontains $_ })
    $named = [string]::Equals($t.Branch, $Id, [StringComparison]::Ordinal)
    if ($inside.Count -or $named) {
      $shown = @($inside | Select-Object -First 5) -join ','; if ($inside.Count -gt 5) { $shown += ",+$($inside.Count - 5)" }
      $tag = if ($named -or $null -eq $h.Newest -or $h.Newest -ge $cutoff) { '[LIVE-WORK-OVERLAP]' } else { '[LIVE-WORK-OVERLAP-STALE]' }
      "$tag worktree=$($t.Path) branch=$($t.Branch) uncommitted=$(@($h.Uncommitted).Count) newest=$(Format-LiveWorkTime $h.Newest) paths=$shown"
    }
  }
  foreach ($r in @("refs/heads/$Id", "refs/heads/r5-$Id", "refs/remotes/origin/$Id", "refs/remotes/origin/r5-$Id")) {
    $tip = @(& git --no-optional-locks -C $Dir rev-parse --verify --quiet $r 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not $tip.Count) { continue }
    & git --no-optional-locks -C $Dir merge-base --is-ancestor $tip[0] $baseRef *> $null
    if ($LASTEXITCODE -eq 1) { "[LIVE-WORK-OVERLAP] ref=$r tip=$($tip[0].Substring(0, 8)) is not on $baseRef" }
    elseif ($LASTEXITCODE -ne 0) { throw "[LIVE-WORK-PROBE-FAIL] git merge-base --is-ancestor $r $baseRef exited $LASTEXITCODE" }
  }
}

# ── Self-check: real Git fixtures (a bare origin, a main checkout, two linked worktrees) and the real hook ──────────
function Invoke-LiveWorkSelfCheck {
  $fails = [System.Collections.Generic.List[string]]::new(); $count = 0
  $root = Join-Path ([IO.Path]::GetTempPath()) "live-work-selfcheck-$PID-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
  $origin = Join-Path $root 'origin.git'; $main = Join-Path $root 'main'; $wtA = Join-Path $root 'wtA'; $wtB = Join-Path $root 'wtB'
  $hook = Join-Path $PSScriptRoot '../.claude/hooks/guard-dirty-worktree.ps1'
  $g = { param($d) $a = @($args); & git -C $d -c user.email=lw@local -c user.name=lw @a *> $null; if ($LASTEXITCODE -ne 0) { throw "fixture: git $($a -join ' ') exited $LASTEXITCODE" } }
  $run = { param([string[]]$a) $o = @(& pwsh -NoProfile -File $PSCommandPath @a 2>&1 | ForEach-Object { "$_" }); [pscustomobject]@{ Code = $LASTEXITCODE; Lines = $o } }
  $ask = { param($command, $cwd) $j = @{ tool_name = 'Bash'; tool_input = @{ command = $command }; cwd = $cwd } | ConvertTo-Json -Compress; (@($j | & pwsh -NoProfile -File $hook 2>&1) -join "`n") }
  $card = { param($id, $allow) "---`nid: $id`nstatus: todo`nallow_paths:`n  - $allow`n---`n`n# $id`n" }
  function Check([string]$Name, [scriptblock]$Body) { $script:lwCount++; try { if (-not (& $Body)) { $fails.Add($Name) } } catch { $fails.Add("$Name (threw: $($_.Exception.Message))") } }
  $script:lwCount = 0
  try {
    New-Item -ItemType Directory -Force (Join-Path $main 'specs/tasks'), (Join-Path $main 'src'), (Join-Path $main 'docs') | Out-Null
    & git init -q --bare $origin *> $null
    & $g $main init -q; & $g $main symbolic-ref HEAD refs/heads/master
    Set-Content (Join-Path $main '.gitignore') "progress.md`n_local/" -Encoding utf8
    Set-Content (Join-Path $main 'src/app.txt') 'app' -Encoding utf8
    Set-Content (Join-Path $main 'docs/free.md') 'free' -Encoding utf8
    foreach ($c in @(@('T9-DEMO', 'src/app.txt'), @('T9-NAME', 'docs/name.md'), @('T9-FREE', 'docs/free.md'))) { Set-Content (Join-Path $main "specs/tasks/$($c[0]).md") (& $card $c[0] $c[1]) -Encoding utf8 }
    & $g $main add -A; & $g $main commit -q -m base; & $g $main remote add origin $origin; & $g $main push -q origin master; & $g $main fetch -q origin
    & $g $main worktree add -q -b T9-OTHER $wtA origin/master; & $g $main worktree add -q -b T9-NAME $wtB origin/master
    Set-Content (Join-Path $wtA 'src/app.txt') 'changed by another session' -Encoding utf8
    Set-Content (Join-Path $main 'progress.md') "<!-- HANDOFF:START -->`nTASK: T9-DEMO`nUPDATED: 2026-09-25`n<!-- HANDOFF:END -->" -Encoding utf8
    New-Item -ItemType Directory -Force (Join-Path $main '_local') | Out-Null
    Set-Content (Join-Path $main '_local/handoff-T9-NAME.md') "TASK: T9-NAME`nUPDATED: 2026-09-24" -Encoding utf8

    $s = & $run @('-RepoPath', $main)
    Check 'summary: exit 0' { $s.Code -eq 0 }
    Check 'summary: one [LIVE-WORK] line, for the dirty worktree' { $l = @($s.Lines | Where-Object { $_.StartsWith('[LIVE-WORK] ') }); $l.Count -eq 1 -and $l[0].Contains('branch=T9-OTHER uncommitted=1 unmerged=0 newest=20') }
    Check 'summary: a handoff line for progress.md and for _local/handoff-*.md' { @($s.Lines | Where-Object { $_ -ceq '[LIVE-WORK-HANDOFF] file=progress.md task=T9-DEMO updated=2026-09-25' -or $_ -ceq '[LIVE-WORK-HANDOFF] file=_local/handoff-T9-NAME.md task=T9-NAME updated=2026-09-24' }).Count -eq 2 }
    $st = & $run @('-RepoPath', $main, '-SinceHours', '0')
    Check 'summary: work older than -SinceHours collapses into one [LIVE-WORK-STALE] line' { @($st.Lines | Where-Object { $_.StartsWith('[LIVE-WORK] ') }).Count -eq 0 -and @($st.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-STALE] count=1 ') }).Count -eq 1 }
    Check 'summary: worktrees not probed within -BudgetSec are [LIVE-WORK-UNKNOWN] and no [LIVE-WORK-NONE] follows' { $u = & $run @('-RepoPath', $main, '-BudgetSec', '0'); $u.Code -eq 0 -and @($u.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-UNKNOWN] path=') }).Count -eq 3 -and -not @($u.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-NONE]') }).Count }
    $o = & $run @('-RepoPath', $main, '-TaskId', 'T9-DEMO')
    Check 'overlap: a worktree changing the card''s allow_paths exits 3' { $o.Code -eq 3 -and @($o.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] worktree=') -and $_.Contains('branch=T9-OTHER') -and $_.EndsWith('paths=src/app.txt') }).Count -eq 1 }
    $os = & $run @('-RepoPath', $main, '-TaskId', 'T9-DEMO', '-SinceHours', '0')
    Check 'overlap: an overlap older than -SinceHours is [LIVE-WORK-OVERLAP-STALE] and exits 0' { $os.Code -eq 0 -and @($os.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP-STALE] worktree=') -and $_.Contains('branch=T9-OTHER') }).Count -eq 1 -and -not @($os.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] ') }).Count }
    $b = & $run @('-RepoPath', $main, '-TaskId', 'T9-NAME')
    Check 'overlap: a clean worktree on a branch named after the card exits 3' { $b.Code -eq 3 -and @($b.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] worktree=') -and $_.Contains('branch=T9-NAME uncommitted=0') }).Count -eq 1 }
    $f = & $run @('-RepoPath', $main, '-TaskId', 'T9-FREE')
    Check 'overlap: a card nothing overlaps exits 0' { $f.Code -eq 0 -and $f.Lines -ccontains '[LIVE-WORK-NO-OVERLAP] no work another worktree or branch holds within 48 h touches T9-FREE' }
    & $g $main commit -q --allow-empty -m 'r5 left behind'; & $g $main branch r5-T9-FREE; & $g $main reset -q --hard origin/master
    $r = & $run @('-RepoPath', $main, '-TaskId', 'T9-FREE')
    Check 'overlap: a branch r5-<id> whose tip is not on the base exits 3' { $r.Code -eq 3 -and @($r.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] ref=refs/heads/r5-T9-FREE ') }).Count -eq 1 }
    $p = & $run @('-RepoPath', $main, '-TaskId', 'T9-NONE')
    Check 'overlap: a card that cannot be read exits 2' { $p.Code -eq 2 -and @($p.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-PROBE-FAIL]') }).Count -eq 1 }

    $isAsk = { param($out) $out.Contains('"permissionDecision":"ask"') -and $out.Contains('T9-OTHER') }
    Check 'hook: git -C <dirty> reset --hard asks' { & $isAsk (& $ask "git -C '$wtA' reset --hard" $main) }
    Check 'hook: git checkout -- . in a dirty cwd asks' { & $isAsk (& $ask 'git checkout -- .' $wtA) }
    Check 'hook: git clean -fd in a dirty cwd asks' { & $isAsk (& $ask 'git clean -fd' $wtA) }
    Check 'hook: git worktree remove --force <dirty> asks' { & $isAsk (& $ask "git worktree remove --force '$wtA'" $main) }
    Check 'hook: cd <dirty> && git reset --hard asks' { & $isAsk (& $ask "cd '$wtA' && git reset --hard" $main) }
    Check 'hook: the same commands on a clean worktree print nothing' { -not (& $ask "git -C '$wtB' reset --hard" $main) -and -not (& $ask 'git checkout -- .' $wtB) }
    Check 'hook: a command that discards nothing prints nothing' { -not (& $ask 'git status' $wtA) }
    Check 'hook: unreadable input prints nothing' { -not (@('not json' | & pwsh -NoProfile -File $hook 2>&1) -join '') }
    Remove-Item -LiteralPath (Join-Path $wtB 'docs/free.md')
    $d = & $run @('-RepoPath', $main)
    Check 'summary: a worktree holding only a deletion gets its HEAD commit time as newest' { @($d.Lines | Where-Object { $_.StartsWith('[LIVE-WORK] ') -and $_.Contains('branch=T9-NAME uncommitted=1 unmerged=0 newest=20') }).Count -eq 1 }
  } catch { $fails.Add("fixture: $($_.Exception.Message)") }
  finally {
    foreach ($w in @($wtA, $wtB)) { & git -C $main worktree remove --force $w *> $null }
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
  }
  if ($fails.Count) {
    foreach ($x in $fails) { Write-Host "LIVE-WORK-FAIL: $x" }
    Write-Host "[LIVE-WORK-SELF-CHECK-FAIL] $($fails.Count) of $script:lwCount cases failed"
    return 1
  }
  Write-Host "[LIVE-WORK-SELF-CHECK-PASS] $script:lwCount cases"
  return 0
}

if ($AsLibrary) { return }
if ($SelfCheck) { exit (Invoke-LiveWorkSelfCheck) }
try {
  if ($TaskId) {
    $lines = @(Get-LiveWorkOverlap $RepoPath $TaskId $Base $SinceHours)
    $lines | ForEach-Object { Write-Output $_ }
    if (@($lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] ') }).Count) { exit 3 }
    Write-Output "[LIVE-WORK-NO-OVERLAP] no work another worktree or branch holds within $SinceHours h touches $TaskId"
    exit 0
  }
  Invoke-LiveWorkSummary $RepoPath $Base $SinceHours $BudgetSec | ForEach-Object { Write-Output $_ }
  exit 0
} catch {
  $m = $_.Exception.Message
  if (-not $m.StartsWith('[LIVE-WORK-PROBE-FAIL]')) { $m = "[LIVE-WORK-PROBE-FAIL] $m" }
  Write-Output $m
  if ($TaskId) { exit 2 }
  exit 0
}
