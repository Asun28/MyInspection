#requires -Version 7
<#
.SYNOPSIS
  Read-only report of the work other sessions hold (T0-LIVE-WORK-GUARD, L218): worktrees with uncommitted or unmerged
  changes, branches named after a card, and handoff files. It never writes to the repository, fetches or pushes, and
  every git call it makes passes --no-optional-locks.
.DESCRIPTION
  live-work.ps1 [-RepoPath <dir>] [-Base master] [-SinceHours 48] [-BudgetSec 10]
      One [LIVE-WORK] line per worktree holding work, one [LIVE-WORK-STALE] line counting those whose newest change
      is older than -SinceHours, one [LIVE-WORK-UNKNOWN] line per worktree whose directory is missing or whose probe
      failed or ran out of time, [LIVE-WORK-NONE] only when every worktree was probed and none holds work, and one
      [LIVE-WORK-HANDOFF] line for the main checkout's progress.md HANDOFF block and for each _local/handoff-*.md.
      Exit 0.
  live-work.ps1 -TaskId <id> [-RepoPath <dir>] [-Base master] [-SinceHours 48] [-BudgetSec 120]
      [LIVE-WORK-OVERLAP] for each worktree whose changed paths fall under the card's allow_paths and whose newest
      change is within -SinceHours, for a worktree whose branch is the card id, and for a branch <id> or r5-<id>
      (local or origin) whose tip is not on the base. An older overlap gets [LIVE-WORK-OVERLAP-STALE], which does not
      count. Exit 3 when any [LIVE-WORK-OVERLAP] line was printed, 0 when none, 2 when a probe fails: an unreadable
      card, a git error, a registered worktree whose directory is missing, or the time budget running out.
  live-work.ps1 -SelfCheck
      Builds temporary Git fixtures and checks both modes and the dirty-worktree hook (.claude/hooks/
      guard-dirty-worktree.ps1); prints [LIVE-WORK-SELF-CHECK-PASS].
  -BudgetSec bounds the whole run: every git call is killed once it passes the deadline. 0 (the default) means 10 s
  for the summary, which the SessionStart hook runs under a 15 s timeout, and 120 s with -TaskId.
  The worktree the caller runs in is left out, except the main checkout, which every session shares. The base is
  origin/<Base> when that ref exists, else <Base>.
#>
[CmdletBinding()]
param(
  [string]$TaskId,
  [string]$RepoPath = (Get-Location).Path,
  [string]$Base = 'master',
  [int]$SinceHours = 48,
  [int]$BudgetSec = 0,
  [switch]$SelfCheck,
  [switch]$AsLibrary
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '_scope.ps1')     # Get-ScaffoldCardAllowPathFromText, Get-ScaffoldOutOfScopePath
. (Join-Path $PSScriptRoot '_context.ps1')   # Get-ScaffoldHandoffBlock

# Every git call runs until this UTC deadline at most and is killed when it passes it; a library caller that sets
# none gets no deadline. --no-optional-locks: git status would otherwise refresh and rewrite the probed index.
# $script:LiveWorkGitConfig adds name=value -c options a library caller wants on every call.
$script:LiveWorkDeadline = [DateTime]::MaxValue; $script:LiveWorkGitConfig = @()
function Invoke-LiveWorkGitRaw([string]$Dir, [string[]]$GitArgs) {
  $what = "git $($GitArgs -join ' ') in $Dir"
  $ms = ($script:LiveWorkDeadline - [DateTime]::UtcNow).TotalMilliseconds
  if ($ms -le 0) { throw "[LIVE-WORK-PROBE-FAIL] the time budget ran out before $what" }
  $psi = [Diagnostics.ProcessStartInfo]::new('git')
  foreach ($a in @('--no-optional-locks', '-C', $Dir, '-c', 'core.quotepath=false') + @($script:LiveWorkGitConfig | ForEach-Object { '-c', $_ }) + $GitArgs) { $psi.ArgumentList.Add($a) }
  $psi.UseShellExecute = $false; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true
  $psi.StandardOutputEncoding = [Text.UTF8Encoding]::new($false)
  $p = [Diagnostics.Process]::Start($psi)
  try {
    $out = $p.StandardOutput.ReadToEndAsync(); $null = $p.StandardError.ReadToEndAsync()
    if (-not $p.WaitForExit([int][Math]::Min($ms, [int]::MaxValue))) {
      try { $p.Kill($true) } catch { }
      throw "[LIVE-WORK-PROBE-FAIL] $what was still running at the deadline and was killed"
    }
    $p.WaitForExit()
    $text = $out.Result.TrimEnd("`r", "`n")
    return [pscustomobject]@{ Code = $p.ExitCode; Lines = @(if ($text) { $text -split "`r?`n" }) }
  } finally { $p.Dispose() }
}

function Invoke-LiveWorkGit([string]$Dir, [string[]]$GitArgs) {
  $r = Invoke-LiveWorkGitRaw $Dir $GitArgs
  if ($r.Code -ne 0) { throw "[LIVE-WORK-PROBE-FAIL] git $($GitArgs -join ' ') exited $($r.Code) in $Dir" }
  return $r.Lines
}

# The tip of a ref, or $null when it does not exist (rev-parse --verify --quiet exits 1); any other exit is a git error.
function Get-LiveWorkRefTip([string]$Dir, [string]$Ref) {
  $r = Invoke-LiveWorkGitRaw $Dir @('rev-parse', '--verify', '--quiet', $Ref)
  if ($r.Code -eq 1) { return $null }
  if ($r.Code -ne 0 -or -not $r.Lines.Count) { throw "[LIVE-WORK-PROBE-FAIL] git rev-parse --verify $Ref exited $($r.Code) in $Dir" }
  return $r.Lines[0]
}

function Get-LiveWorkBaseRef([string]$Dir, [string]$Name) {
  foreach ($r in @("refs/remotes/origin/$Name", "refs/heads/$Name")) { if ($null -ne (Get-LiveWorkRefTip $Dir $r)) { return $r } }
  throw "[LIVE-WORK-PROBE-FAIL] neither origin/$Name nor $Name resolves in $Dir"
}

# Registered worktrees from `git worktree list --porcelain`; the first is the main checkout. Bare entries hold no
# files and are skipped. An entry whose directory is missing is kept with Missing set: what it holds is unknown.
function Get-LiveWorkTrees([string]$Dir) {
  $trees = [System.Collections.Generic.List[object]]::new(); $cur = $null
  foreach ($l in @(Invoke-LiveWorkGit $Dir @('worktree', 'list', '--porcelain')) + @('')) {
    if ($l.StartsWith('worktree ')) { $cur = [pscustomobject]@{ Path = $l.Substring(9); Branch = 'detached'; Bare = $false; Main = ($trees.Count -eq 0); Missing = $false } }
    elseif ($null -ne $cur -and $l.StartsWith('branch refs/heads/')) { $cur.Branch = $l.Substring(18) }
    elseif ($null -ne $cur -and $l -ceq 'bare') { $cur.Bare = $true }
    elseif ($l -eq '' -and $null -ne $cur) { if (-not $cur.Bare) { $cur.Missing = -not (Test-Path -LiteralPath $cur.Path); $trees.Add($cur) }; $cur = $null }
  }
  return $trees.ToArray()
}

function Get-LiveWorkMissingReason($Tree) { return "registered worktree $($Tree.Path) has no directory, so what it holds is unknown; git worktree prune drops the entry once it is gone for good" }

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

function Format-LiveWorkTime($Utc) { if ($null -eq $Utc) { return 'unknown' }; return $Utc.ToString('yyyy-MM-ddTHH:mm:ssZ', [Globalization.CultureInfo]::InvariantCulture) }

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

function Invoke-LiveWorkSummary([string]$Dir, [string]$BaseName, [int]$Hours) {
  '[LIVE-WORK-SUMMARY] work other sessions may hold, read-only; before starting, resuming, splitting or resetting a card run scripts/live-work.ps1 -TaskId <id> and ask the user about any overlap (L218)'
  $baseRef = Get-LiveWorkBaseRef $Dir $BaseName
  $cutoff = [DateTime]::UtcNow.AddHours(-$Hours)
  $held = 0; $stale = 0; $unknown = 0; $trees = @(Get-LiveWorkOthers $Dir)
  foreach ($t in $trees) {
    if ($t.Missing) { $unknown++; "[LIVE-WORK-UNKNOWN] path=$($t.Path) ($(Get-LiveWorkMissingReason $t))"; continue }
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
  $show = Invoke-LiveWorkGitRaw $Dir @('show', "${baseRef}:specs/tasks/$Id.md")
  $cardText = if ($show.Code -eq 0) { $show.Lines -join "`n" }
  elseif (Test-Path -LiteralPath (Join-Path $Dir "specs/tasks/$Id.md")) { [IO.File]::ReadAllText((Join-Path $Dir "specs/tasks/$Id.md")) }
  else { throw "[LIVE-WORK-PROBE-FAIL] no card specs/tasks/$Id.md on $baseRef or in $Dir" }
  $allow = @(Get-ScaffoldCardAllowPathFromText -CardText $cardText)
  if (-not $allow.Count) { throw "[LIVE-WORK-PROBE-FAIL] the card $Id declares no allow_paths" }
  foreach ($t in @(Get-LiveWorkOthers $Dir)) {
    if ($t.Missing) { throw "[LIVE-WORK-PROBE-FAIL] $(Get-LiveWorkMissingReason $t)" }
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
    $tip = Get-LiveWorkRefTip $Dir $r
    if ($null -eq $tip) { continue }
    $anc = (Invoke-LiveWorkGitRaw $Dir @('merge-base', '--is-ancestor', $tip, $baseRef)).Code
    if ($anc -eq 1) { "[LIVE-WORK-OVERLAP] ref=$r tip=$($tip.Substring(0, 8)) is not on $baseRef" }
    elseif ($anc -ne 0) { throw "[LIVE-WORK-PROBE-FAIL] git merge-base --is-ancestor $r $baseRef exited $anc" }
  }
}

# ── Self-check: real Git fixtures (a bare origin, a main checkout, linked worktrees) ──────────────────────────────
function Invoke-LiveWorkSelfCheck {
  $fails = [System.Collections.Generic.List[string]]::new(); $keep = $false
  $root = Join-Path ([IO.Path]::GetTempPath()) "live-work-selfcheck-$PID-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
  $origin = Join-Path $root 'origin.git'; $main = Join-Path $root 'main'
  $wtA = Join-Path $root 'wtA'; $wtB = Join-Path $root 'wtB'; $wtC = Join-Path $root 'wtC'; $wtD = Join-Path $root 'wtD'
  # A failing fixture git command reports git's own output and the directory it ran in.
  $g = { param($d) $a = @($args); $o = @(& git -C $d -c user.email=lw@local -c user.name=lw @a 2>&1 | ForEach-Object { "$_" }); if ($LASTEXITCODE -ne 0) { throw "fixture: git -C $d $($a -join ' ') exited $LASTEXITCODE`: $($o -join ' | ')" } }
  $run = { param([string[]]$a) $o = @(& pwsh -NoProfile -File $PSCommandPath @a 2>&1 | ForEach-Object { "$_" }); [pscustomobject]@{ Code = $LASTEXITCODE; Lines = $o } }
  $has = { param($r, [string]$Prefix, [string]$Part) @($r.Lines | Where-Object { $_.StartsWith($Prefix) -and $_.Contains($Part) }).Count }
  $card = { param($id, $allow) "---`nid: $id`nstatus: todo`nallow_paths:`n  - $allow`n---`n`n# $id`n" }
  function Check([string]$Name, [scriptblock]$Body) { $script:lwCount++; try { if (-not (& $Body)) { $fails.Add($Name) } } catch { $fails.Add("$Name (threw: $($_.Exception.Message))") } }
  $script:lwCount = 0
  try {
    New-Item -ItemType Directory -Force (Join-Path $main 'specs/tasks'), (Join-Path $main 'src'), (Join-Path $main 'docs') | Out-Null
    & $g $root init -q --bare $origin
    & $g $main init -q; & $g $main symbolic-ref HEAD refs/heads/master
    Set-Content (Join-Path $main '.gitignore') "progress.md`n_local/" -Encoding utf8
    Set-Content (Join-Path $main 'src/app.txt') 'app' -Encoding utf8
    Set-Content (Join-Path $main 'docs/free.md') 'free' -Encoding utf8
    foreach ($c in @(@('T9-DEMO', 'src/app.txt'), @('T9-NAME', 'docs/name.md'), @('T9-FREE', 'docs/free.md'), @('T9-REMOTE', 'docs/remote.md'))) { Set-Content (Join-Path $main "specs/tasks/$($c[0]).md") (& $card $c[0] $c[1]) -Encoding utf8 }
    & $g $main add -A; & $g $main commit -q -m base; & $g $main remote add origin $origin; & $g $main push -q origin master; & $g $main fetch -q origin
    & $g $main worktree add -q -b T9-OTHER $wtA origin/master; & $g $main worktree add -q -b T9-NAME $wtB origin/master
    Check 'fixture: a failing git command reports git''s output and the directory it ran in' { try { & $g $main rev-parse --verify refs/heads/no-such; $false } catch { $_.Exception.Message.Contains($main) -and $_.Exception.Message -match 'exited 128: .*\S' } }

    $n = & $run @('-RepoPath', $main)
    Check 'summary: [LIVE-WORK-NONE] when every worktree was probed and none holds work' { $n.Code -eq 0 -and $n.Lines -ccontains '[LIVE-WORK-NONE] no other worktree holds uncommitted or unmerged work' -and -not (& $has $n '[LIVE-WORK] ' '') }
    & $g $main worktree add -q -b T9-GONE $wtD origin/master; Remove-Item -LiteralPath $wtD -Recurse -Force
    $m = & $run @('-RepoPath', $main); $mt = & $run @('-RepoPath', $main, '-TaskId', 'T9-FREE')
    Check 'summary: a registered worktree whose directory is missing is [LIVE-WORK-UNKNOWN] and no [LIVE-WORK-NONE] follows' { $m.Code -eq 0 -and (& $has $m '[LIVE-WORK-UNKNOWN] path=' 'wtD has no directory') -eq 1 -and -not (& $has $m '[LIVE-WORK-NONE]' '') }
    Check 'overlap: a registered worktree whose directory is missing exits 2' { $mt.Code -eq 2 -and (& $has $mt '[LIVE-WORK-PROBE-FAIL]' 'wtD has no directory') -eq 1 }
    & $g $main worktree prune
    # A per-worktree core.fsmonitor hook that sleeps holds only wtB's git status, the way a slow disk would. The 6 s
    # budget leaves the calls before wtB's status room on a loaded machine; killing git can leave the shell's sleep
    # running on Windows, so it sleeps 15 s, past the deadline but not much longer.
    $slow = Join-Path $root 'slow-fsmonitor.sh'; [IO.File]::WriteAllText($slow, "#!/bin/sh`nsleep 15`n"); if (-not $IsWindows) { & chmod +x $slow }
    & $g $main config extensions.worktreeConfig true; & $g $wtB config --worktree core.fsmonitor $slow.Replace('\', '/')
    $clock = [Diagnostics.Stopwatch]::StartNew(); $sl = & $run @('-RepoPath', $main, '-BudgetSec', '6'); $slowSec = $clock.Elapsed.TotalSeconds
    & $g $wtB config --worktree --unset core.fsmonitor
    Check 'summary: a git status a slow fsmonitor hook holds past -BudgetSec is killed at the deadline and reported [LIVE-WORK-UNKNOWN], no [LIVE-WORK-NONE] follows, and the run ends within the budget plus 10 s' { $sl.Code -eq 0 -and @($sl.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-UNKNOWN] path=') -and $_.Contains('wtB') -and $_.Contains('status') -and $_.Contains('was still running at the deadline and was killed') }).Count -eq 1 -and -not (& $has $sl '[LIVE-WORK-NONE]' '') -and $slowSec -lt 16 }

    Set-Content (Join-Path $wtA 'src/app.txt') 'changed by another session' -Encoding utf8
    & $g $main worktree add -q --detach $wtC origin/master; Set-Content (Join-Path $wtC 'docs/c.md') 'c' -Encoding utf8; & $g $wtC add -A; & $g $wtC commit -q -m c
    Set-Content (Join-Path $main 'scratch.txt') 'main' -Encoding utf8
    Set-Content (Join-Path $main 'progress.md') "<!-- HANDOFF:START -->`nTASK: T9-DEMO`nUPDATED: 2026-09-25`n<!-- HANDOFF:END -->" -Encoding utf8
    New-Item -ItemType Directory -Force (Join-Path $main '_local') | Out-Null
    Set-Content (Join-Path $main '_local/handoff-T9-NAME.md') "TASK: T9-NAME`nUPDATED: 2026-09-24" -Encoding utf8
    $s = & $run @('-RepoPath', $main)
    Check 'summary: exit 0 with one [LIVE-WORK] line per worktree holding work (three)' { $s.Code -eq 0 -and (& $has $s '[LIVE-WORK] ' '') -eq 3 }
    Check 'summary: an uncommitted change' { (& $has $s '[LIVE-WORK] ' 'branch=T9-OTHER uncommitted=1 unmerged=0 newest=20') -eq 1 }
    Check 'summary: a commit not on the base in a detached worktree' { (& $has $s '[LIVE-WORK] ' 'branch=detached uncommitted=0 unmerged=1 newest=20') -eq 1 }
    Check 'summary: the main checkout' { (& $has $s '[LIVE-WORK] ' 'branch=master uncommitted=1 unmerged=0 newest=20') -eq 1 }
    Check 'summary: a handoff line for progress.md and for _local/handoff-*.md' { @($s.Lines | Where-Object { $_ -ceq '[LIVE-WORK-HANDOFF] file=progress.md task=T9-DEMO updated=2026-09-25' -or $_ -ceq '[LIVE-WORK-HANDOFF] file=_local/handoff-T9-NAME.md task=T9-NAME updated=2026-09-24' }).Count -eq 2 }
    $st = & $run @('-RepoPath', $main, '-SinceHours', '0')
    Check 'summary: work older than -SinceHours collapses into one [LIVE-WORK-STALE] line' { -not (& $has $st '[LIVE-WORK] ' '') -and (& $has $st '[LIVE-WORK-STALE] count=3 ' '') -eq 1 }

    $o = & $run @('-RepoPath', $main, '-TaskId', 'T9-DEMO')
    Check 'overlap: a worktree changing the card''s allow_paths exits 3' { $o.Code -eq 3 -and @($o.Lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] worktree=') -and $_.Contains('branch=T9-OTHER') -and $_.EndsWith('paths=src/app.txt') }).Count -eq 1 }
    $os = & $run @('-RepoPath', $main, '-TaskId', 'T9-DEMO', '-SinceHours', '0')
    Check 'overlap: an overlap older than -SinceHours is [LIVE-WORK-OVERLAP-STALE] and exits 0' { $os.Code -eq 0 -and (& $has $os '[LIVE-WORK-OVERLAP-STALE] worktree=' 'branch=T9-OTHER') -eq 1 -and -not (& $has $os '[LIVE-WORK-OVERLAP] ' '') }
    $b = & $run @('-RepoPath', $main, '-TaskId', 'T9-NAME')
    Check 'overlap: a clean worktree on a branch named after the card exits 3' { $b.Code -eq 3 -and (& $has $b '[LIVE-WORK-OVERLAP] worktree=' 'branch=T9-NAME uncommitted=0') -eq 1 }
    $f = & $run @('-RepoPath', $main, '-TaskId', 'T9-FREE')
    Check 'overlap: a card nothing overlaps exits 0' { $f.Code -eq 0 -and $f.Lines -ccontains '[LIVE-WORK-NO-OVERLAP] no work another worktree or branch holds within 48 h touches T9-FREE' }
    & $g $main commit -q --allow-empty -m 'r5 left behind'; & $g $main branch r5-T9-FREE; & $g $main reset -q --hard origin/master
    $r = & $run @('-RepoPath', $main, '-TaskId', 'T9-FREE')
    Check 'overlap: a local branch r5-<id> whose tip is not on the base exits 3' { $r.Code -eq 3 -and (& $has $r '[LIVE-WORK-OVERLAP] ref=refs/heads/r5-T9-FREE ' '') -eq 1 }
    & $g $wtC push -q origin HEAD:refs/heads/T9-REMOTE; & $g $main fetch -q origin
    $rr = & $run @('-RepoPath', $main, '-TaskId', 'T9-REMOTE')
    Check 'overlap: a remote-tracking origin/<id> whose tip is not on the base exits 3' { $rr.Code -eq 3 -and (& $has $rr '[LIVE-WORK-OVERLAP] ref=refs/remotes/origin/T9-REMOTE ' '') -eq 1 }
    Set-Content (Join-Path $main 'specs/tasks/T9-LOCAL.md') (& $card 'T9-LOCAL' 'src/app.txt') -Encoding utf8
    $lc = & $run @('-RepoPath', $main, '-TaskId', 'T9-LOCAL')
    Check 'overlap: a card the base lacks is read from the caller''s tree' { $lc.Code -eq 3 -and (& $has $lc '[LIVE-WORK-OVERLAP] worktree=' 'paths=src/app.txt') -eq 1 }
    $p = & $run @('-RepoPath', $main, '-TaskId', 'T9-NONE')
    Check 'overlap: a card that cannot be read exits 2' { $p.Code -eq 2 -and (& $has $p '[LIVE-WORK-PROBE-FAIL]' 'T9-NONE') -eq 1 }

    Remove-Item -LiteralPath (Join-Path $wtB 'docs/free.md')
    $d = & $run @('-RepoPath', $main)
    Check 'summary: a worktree holding only a deletion gets its HEAD commit time as newest' { (& $has $d '[LIVE-WORK] ' 'branch=T9-NAME uncommitted=1 unmerged=0 newest=20') -eq 1 }

    # The dirty-worktree hook (T0-DIRTY-WORKTREE-HOOK), fed event JSON the way Claude Code sends it: wtA is dirty
    # (src/app.txt, branch T9-OTHER); wtC is clean but holds one ignored file. Stderr is merged into the output, so an
    # ask must be exactly one line of hook JSON, "prints nothing" means no line at all, and every case requires exit 0.
    $hook = Join-Path $PSScriptRoot '../.claude/hooks/guard-dirty-worktree.ps1'
    New-Item -ItemType Directory -Force (Join-Path $wtC '_local') | Out-Null; Set-Content (Join-Path $wtC '_local/keep.md') 'keep' -Encoding utf8
    # wtE's index is garbage, so git status fails there while rev-parse still finds the worktree: a git error.
    $wtE = Join-Path $root 'wtE'; & $g $main worktree add -q --detach $wtE origin/master; [IO.File]::WriteAllText((Join-Path @(& git -C $wtE rev-parse --absolute-git-dir)[0] 'index'), 'not an index')
    $ask ={ param([string]$In) $o = @($In | & pwsh -NoProfile -File $hook 2>&1 | ForEach-Object { "$_" }); [pscustomobject]@{ Code = $LASTEXITCODE; Out = $o } }
    $evt = { param($Command, $Cwd, $Tool = 'Bash') & $ask (@{ tool_name = $Tool; tool_input = @{ command = $Command }; cwd = $Cwd } | ConvertTo-Json -Compress) }
    $asksAbout = { param($r, [string]$What) if ($r.Code -ne 0 -or $r.Out.Count -ne 1) { return $false }; $h = ($r.Out[0] | ConvertFrom-Json).hookSpecificOutput
      $h.hookEventName -ceq 'PreToolUse' -and $h.permissionDecision -ceq 'ask' -and $h.permissionDecisionReason.Contains($What) -and $h.permissionDecisionReason.Contains('Another session may hold this worktree (L218)') }
    $asks = { param($r) & $asksAbout $r 'wtA (branch T9-OTHER): 1 uncommitted path(s), newest change 20' }
    $quiet ={ param($r) $r.Code -eq 0 -and $r.Out.Count -eq 0 }
    foreach ($c in @('reset --hard', 'reset --merge', 'reset --keep', 'checkout -f', 'checkout -- src/app.txt', 'checkout .', 'restore src/app.txt',
        'restore --staged --worktree src/app.txt', 'clean -fd', 'stash', 'stash push', 'switch -C lw-x', 'switch --force-create lw-x', 'switch -f T9-OTHER',
        'switch --discard-changes T9-OTHER', 'branch -D lw-x', 'branch -f lw-x', 'branch -M lw-x', 'update-ref refs/heads/lw-x HEAD',
        'reset "--hard"', '"reset" --hard', 'checkout -fq', 'switch -fq T9-OTHER', 'switch -Clw-y', 'stash -m list', 'restore --source HEAD src/app.txt',
        'restore --pathspec-from-file=list.txt', 'update-ref --stdin')) {
      Check "hook: git -C <dirty> $c asks" { & $asks (& $evt "git -C '$wtA' $c" $main) }
      Check "hook: git -C <clean> $c prints nothing" { & $quiet (& $evt "git -C '$wtC' $c" $main) }
    }
    Check 'hook: git worktree remove --force <dirty> asks' { & $asks (& $evt "git worktree remove --force '$wtA'" $main) }
    Check 'hook: git worktree remove --force <clean> prints nothing' { & $quiet (& $evt "git worktree remove --force '$wtC'" $main) }
    Check 'hook: git worktree remove -ff <dirty> asks' { & $asks (& $evt "git worktree remove -ff '$wtA'" $main) }
    Check 'hook: git worktree remove --force -- <dirty> asks about the path after --' { & $asks (& $evt "git worktree remove --force -- '$wtA'" $wtC) }
    Check 'hook: a -c option is not a directory (dirty asks)' { & $asks (& $evt "git -C '$wtA' -c core.quotepath=false reset --hard" $main) }
    Check 'hook: a -c option is not a directory (clean prints nothing)' { & $quiet (& $evt "git -c core.quotepath=false -C '$wtC' reset --hard" $main) }
    Check 'hook: & git.exe is git' { & $asks (& $evt "& git.exe -C '$wtA' reset --hard" $main) }
    Check 'hook: a relative -C joins the event cwd' { & $asks (& $evt 'git -C wtA reset --hard' $root) }
    Check 'hook: a relative -C joins the directory a Set-Location moved to' { & $asks (& $evt "Set-Location '$root'; git -C wtA reset --hard" $wtC) }
    Check 'hook: cd moves the target' { & $asks (& $evt "cd '$wtA' && git reset --hard" $wtC) }
    Check 'hook: Set-Location -LiteralPath moves the target' { & $asks (& $evt "Set-Location -LiteralPath '$wtA'; git reset --hard" $wtC) }
    Check 'hook: Push-Location moves the target' { & $asks (& $evt "Push-Location '$wtA'; git reset --hard" $wtC) }
    Check 'hook: pushd moves the target' { & $asks (& $evt "pushd '$wtA' && git reset --hard" $wtC) }
    Check 'hook: Pop-Location moves it back' { & $quiet (& $evt "Push-Location '$wtA'; Pop-Location; git reset --hard" $wtC) }
    Check 'hook: a cd inside ( ... ) moves the target inside it' { & $asks (& $evt "(cd '$wtA' && git reset --hard)" $wtC) }
    Check 'hook: in PowerShell ( ... ) keeps the location change' { & $asks (& $evt "(Set-Location '$wtA'); git reset --hard" $wtC 'PowerShell') }
    Check 'hook: popd moves it back' { & $quiet (& $evt "pushd '$wtA' && popd && git reset --hard" $wtC) }
    Check 'hook: a ) inside quotes does not close the subshell' { & $asks (& $evt "(cd '$wtA' && echo `")`" && git reset --hard)" $wtC) }
    Check 'hook: a git error in one command leaves the later ones checked' { & $asks (& $evt "git -C '$wtE' reset --hard; git -C '$wtA' reset --hard" $main) }
    Check 'hook: the closing parenthesis of ( ... ) moves it back' { & $quiet (& $evt "(cd '$wtA'); git reset --hard" $wtC) }
    Check 'hook: the event cwd is the target when nothing moves it' { & $asks (& $evt 'git reset --hard' $wtA) }
    if ($IsWindows) {
      Check 'hook: a Git Bash /c/... path is read as C:/...' { & $asks (& $evt "cd '/$($wtA.Substring(0, 1).ToLowerInvariant())$($wtA.Substring(2).Replace('\', '/'))' && git reset --hard" $wtC) }
      $nd = @([char[]](90..68) | Where-Object { -not (Test-Path -LiteralPath "$($_):\") })[0]
      Check 'hook: a command it cannot check leaves the earlier ask in place' { & $asks (& $evt "git -C '$wtA' reset --hard; cd ${nd}:\nope; git -C sub reset --hard" $wtC) }
    }
    foreach ($c in @('git -C <wt> restore --staged src/app.txt', 'git -C <wt> stash list', 'git -C <wt> stash show', 'git -C <wt> status', 'git commit -m "note: git reset --hard asks"', 'git commit -m "wip; git stash pop later"', 'echo git stash',
        'git -C <wt> stash "list"', 'git -C <wt> restore -Sq src/app.txt', 'git -C <wt> restore --source HEAD', 'git -C <wt> checkout --', 'git -C <wt> checkout T9-OTHER --', 'git -C <wt> checkout -bxf',
        'git -C <wt> branch -D', 'git -C <wt> switch -f', 'git -C <wt> switch -C', 'git -C <wt> update-ref', 'git -C <wt> worktree remove --force', 'git -C <wt> RESET --hard',
        'git -C <wt> switch -c lw-z', 'git -C <wt> branch -d lw-x', 'git -C <wt> worktree add -f <wt> T9-OTHER')) {
      Check "hook: $c on a dirty worktree prints nothing" { & $quiet (& $evt $c.Replace('<wt>', "'$wtA'") $wtA) }
    }
    Check 'hook: clean -fdx on a worktree whose only extra file is ignored asks' { & $asksAbout (& $evt "git -C '$wtC' clean -fdx" $main) 'wtC (branch detached): 0 uncommitted path(s) and 1 ignored path(s), newest change unknown' }
    Check 'hook: clean -fX counts ignored files too' { & $asksAbout (& $evt "git -C '$wtC' clean -fX" $main) 'wtC (branch detached): 0 uncommitted path(s) and 1 ignored path(s), newest change unknown' }
    Check 'hook: clean -fd (no -x) there prints nothing' { & $quiet (& $evt "git -C '$wtC' clean -fd" $main) }
    $mark = Join-Path $root 'fsmonitor-ran'; $fsm = Join-Path $root 'mark-fsmonitor.sh'; [IO.File]::WriteAllText($fsm, "#!/bin/sh`necho ran > '$($mark.Replace('\', '/'))'`n"); if (-not $IsWindows) { & chmod +x $fsm }
    & $g $wtA config --worktree core.fsmonitor $fsm.Replace('\', '/'); $fm = & $evt "git -C '$wtA' reset --hard" $main; & $g $wtA config --worktree --unset core.fsmonitor
    Check 'hook: a repository''s core.fsmonitor program does not run (dirty still asks)' { (& $asks $fm) -and -not (Test-Path -LiteralPath $mark) }
    # wtS's src/app.txt keeps its size but not its time, so git status reads it through a clean filter that sleeps past
    # the hook's 10 s deadline (the sleep may outlive the killed git on Windows, as above).
    $wtS = Join-Path $root 'wtS'; & $g $main worktree add -q --detach $wtS origin/master; $attr = Join-Path $root 'slow.attributes'; [IO.File]::WriteAllText($attr, "src/app.txt filter=slow`n")
    & $g $wtS config --worktree core.attributesFile $attr.Replace('\', '/'); & $g $wtS config --worktree filter.slow.clean 'sleep 14; cat'; Set-Content (Join-Path $wtS 'src/app.txt') 'ppa' -Encoding utf8
    $dl = & $evt "git -C '$wtA' reset --hard; git -C '$wtS' reset --hard; git -C '$wtA' clean -fd" $main
    Check 'hook: past the deadline the slow command and every later one are left unasked, and the ask found before it is printed' { (& $asks $dl) -and -not $dl.Out[0].Contains('wtS') -and -not $dl.Out[0].Contains('git clean') }
    Check 'hook: unreadable input prints nothing' { & $quiet (& $ask 'not json') }  } catch { $keep = $true; $fails.Add("fixture: $($_.Exception.Message) (fixture kept at $root)") }
  finally { if (-not $keep) { Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue } }
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
  $budget = if ($BudgetSec -gt 0) { $BudgetSec } elseif ($TaskId) { 120 } else { 10 }
  $script:LiveWorkDeadline = [DateTime]::UtcNow.AddSeconds($budget)
  if ($TaskId) {
    $lines = @(Get-LiveWorkOverlap $RepoPath $TaskId $Base $SinceHours)
    $lines | ForEach-Object { Write-Output $_ }
    if (@($lines | Where-Object { $_.StartsWith('[LIVE-WORK-OVERLAP] ') }).Count) { exit 3 }
    Write-Output "[LIVE-WORK-NO-OVERLAP] no work another worktree or branch holds within $SinceHours h touches $TaskId"
    exit 0
  }
  Invoke-LiveWorkSummary $RepoPath $Base $SinceHours | ForEach-Object { Write-Output $_ }
  exit 0
} catch {
  $m = $_.Exception.Message
  if (-not $m.StartsWith('[LIVE-WORK-PROBE-FAIL]')) { $m = "[LIVE-WORK-PROBE-FAIL] $m" }
  Write-Output $m
  if ($TaskId) { exit 2 }
  exit 0
}
