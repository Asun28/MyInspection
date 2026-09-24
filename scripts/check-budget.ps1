#requires -Version 7
<#
.SYNOPSIS
  Card budget meter: how much of the budget a task card DECLARED has its live diff already spent?

.DESCRIPTION
  A second ENTRY POINT over the shared decision core `Get-ScaffoldCardBudgetDecision` (scripts/_guard.ps1),
  not a second implementation - the same relationship `check-scope.ps1` has with `_scope.ps1`. Every
  threshold, the default budget and the under/trip/over judgement live in the core and in `_config.ps1`;
  nothing here decides anything, which is why the card's DoD asserts that this file names the core AND
  that it assigns no trip fraction of its own. A private copy of a threshold is precisely how the meter
  and the ship gate would come to disagree without either being obviously wrong.

  WHY THIS EXISTS (TD235). A card's size is estimated once, by a planner, before a line of code exists,
  and is then never measured again until the diff reaches review. The only machine signal in between that
  is keyed on size is a non-blocking warning about `allow_paths > 5`. So growth is invisible during
  exactly the window in which splitting is still cheap, and the first real feedback arrives when
  splitting costs a rebase of everything already written.

  WHAT IT IS NOT. It is not a ceiling. A fixed ceiling of N makes N-1 a pass, and the work then optimises
  for fitting under the limit rather than for being reviewable. This measures a card against ITS OWN
  declared budget, and the event that matters is the TRIP - crossing a fraction of that budget early
  enough that splitting is still a cheap decision.

  REPORT ONLY. This script is diagnosis: it prints, and its exit code states what it found. It is not a
  gate and nothing in the delivery chain fails because of it - the ship-side block is T233. Exit codes:

    0 = under, or no declared budget (reporting only), or undecidable-but-harmless (see below).
    1 = TRIP or OVER. Something to decide, not necessarily something wrong.
    2 = the measurement itself could not be made (no card, no git, unresolvable base). Distinguished from
        1 on purpose: "I could not measure" must never read as "you are over budget", and a caller that
        conflated them would report a phantom overrun every time a worktree was missing.

  BASE-CARD BINDING. The budget is read from the card as it exists on the BASE ref, never from the branch
  copy. If a card could raise its own budget inside the diff the budget is judging, the budget judges
  nothing - the same reason the ship scope gate reads `allow_paths` from the base card. Raising a budget
  is allowed and is meant to be VISIBLE: it has to land on the base branch as its own commit, with the
  reason in that commit, which is the rule DocBudgets already states for its own ceilings.

  BASE SIDE: origin/<branch> by default, local only with -Local. This mirrors check-scope.ps1's mode split
  and it is not a stylistic echo - it was measured. On a shared checkout LOCAL master is whatever the last
  session to commit left there: stale, or diverged onto a sibling. Read live on 2026-08-31, local master sat
  on a peer's unpushed commit while origin carried this card's raised budget, and resolving to local made the
  meter report `625 of 400` where the truth was `534 of 550` - BOTH numbers wrong from one cause, since the
  stale base also moved the merge-base and swept in three unrelated sessions' commits. A meter that
  misreports in a shared checkout misreports in this repo's normal mode.

  NEVER FETCHES. Like `check-scope.ps1`, this is a read-only diagnosis port and does not touch the
  network. It prints the base ref and sha it used; if you want a fresher base, fetch first.

.PARAMETER TaskId  Card id, e.g. T1-FOO. Defaults to the current branch name when that names a card.
.PARAMETER Path    Repository or worktree to measure. Defaults to <WorktreeRoot>\<TaskId>, then the
                   current directory when that does not exist.
.PARAMETER Base    Base branch NAME (master / origin/master - the prefix is normalised away). Defaults to
                   the base ref resolver's answer.
.PARAMETER Local   Resolve the base to the LOCAL branch instead of origin/<branch>. Default is origin, and
                   that default is load-bearing on a shared checkout - see BASE SIDE below.
.PARAMETER Quiet   Print one line instead of the block. For the Stop hook (T232), which speaks every turn.

.EXAMPLE
  pwsh -NoProfile -File scripts\check-budget.ps1 -TaskId T1-FOO
  pwsh -NoProfile -File scripts\check-budget.ps1 -TaskId T1-FOO -Base master -Quiet
#>
[CmdletBinding()]
param(
  [string]$TaskId,
  [string]$Path,
  [string]$Base,
  [switch]$Local,
  [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $PSCommandPath
. (Join-Path $here '_config.ps1')
. (Join-Path $here '_guard.ps1')

function Write-Meter([string]$Text, [string]$Colour) {
  if ($Colour) { Write-Host $Text -ForegroundColor $Colour } else { Write-Host $Text }
}

# -- 1. which card? --------------------------------------------------------------------------------
if (-not $TaskId) {
  try {
    $probe = if ($Path) { $Path } else { (Get-Location).Path }
    $branch = (& git -C $probe rev-parse --abbrev-ref HEAD 2>$null | Out-String).Trim()
    if ($branch -and $branch -ne 'HEAD') { $TaskId = $branch }
  }
  catch { $TaskId = '' }
}
if (-not $TaskId) {
  if (-not $Quiet) { Write-Meter '[CARD-BUDGET] no card id given and the current branch does not name one - nothing to measure.' 'DarkGray' }
  exit 2
}

# -- 2. which tree? --------------------------------------------------------------------------------
if (-not $Path) {
  $wtRoot = Get-ScaffoldWorktreeRoot
  $candidate = if ($wtRoot) { Join-Path $wtRoot $TaskId } else { $null }
  $Path = if ($candidate -and (Test-Path $candidate)) { $candidate } else { (Get-Location).Path }
}
if (-not (Test-Path $Path)) {
  if (-not $Quiet) { Write-Meter "[CARD-BUDGET] cannot measure: '$Path' does not exist." 'DarkGray' }
  exit 2
}

# -- 3. which base? --------------------------------------------------------------------------------
$baseRef = ''
try {
  if ($Base) { $baseRef = ($Base -replace '^origin/', '') }
  else {
    . (Join-Path $here '_gitbase.ps1')
    $baseRef = Resolve-ScaffoldBaseRef -GitDir $Path
  }
}
catch { $baseRef = '' }
if (-not $baseRef) { $baseRef = 'master' }
$baseRef = ($baseRef -replace '^origin/', '')

# -- 4. the two card texts. The BASE one carries the budget; the branch one is read only so the core can
#       be handed both and the binding stays visible at the call site rather than implied here. --------
$cardRel = "specs/tasks/$TaskId.md"
$baseCard = ''
$branchCard = ''
$baseSides = if ($Local) { @($baseRef, "origin/$baseRef") } else { @("origin/$baseRef", $baseRef) }
$baseUsed = ''
foreach ($side in $baseSides) {
  if ($baseCard) { break }
  try { $baseCard = (& git -C $Path show "${side}:$cardRel" 2>$null | Out-String) } catch { $baseCard = '' }
  if ($baseCard) { $baseUsed = $side }
}
if (-not $baseUsed) { $baseUsed = $baseSides[0] }
try {
  $branchPath = Join-Path $Path $cardRel
  if (Test-Path $branchPath) { $branchCard = (Get-Content $branchPath -Raw) }
}
catch { $branchCard = '' }

if (-not $baseCard -and -not $branchCard) {
  if (-not $Quiet) { Write-Meter "[CARD-BUDGET] cannot measure: $cardRel is on neither '$baseRef' nor the working tree." 'DarkGray' }
  exit 2
}

# -- 5. the diff ------------------------------------------------------------------------------------
$changed = 0
$touched = @()
$measured = $false
try {
  # The SAME ref the card text came from. Reading the budget from one side and the diff from another is
  # how the two halves of one judgement come to describe different trees.
  $numstat = (& git -C $Path diff "$baseUsed...HEAD" --numstat 2>$null | Out-String)
  foreach ($ln in ($numstat -split "`r?`n")) {
    if ($ln -match '^(\d+)\s+(\d+)\s+(.+)$') {
      $changed += [int]$Matches[1] + [int]$Matches[2]
      $touched += $Matches[3].Trim()
      $measured = $true
    }
  }
  # Uncommitted work counts. The whole point is feedback WHILE the card is being written, and at that
  # moment most of the diff is not committed yet - measuring only committed lines would go quiet during
  # exactly the stretch this exists to speak in.
  $wipstat = (& git -C $Path diff HEAD --numstat 2>$null | Out-String)
  foreach ($ln in ($wipstat -split "`r?`n")) {
    if ($ln -match '^(\d+)\s+(\d+)\s+(.+)$') {
      $changed += [int]$Matches[1] + [int]$Matches[2]
      $touched += $Matches[3].Trim()
      $measured = $true
    }
  }
  # UNTRACKED files count too, and leaving them out was a real defect this meter found in ITSELF on its
  # first use (T231): `git diff` - in every form, staged or not - is blind to a file git has never seen,
  # so a card that CREATES a script reads as small right up until the moment it commits. This meter read
  # its own card at 309 of 400 while the truth was 515, because its own 218-line entry point was still
  # untracked. That is the worst possible direction for the error: the cards most at risk of growing are
  # exactly the ones that add new files, and they are the ones that got the reassuring number.
  # --exclude-standard so ignored files (.venv, node_modules, _local) are not counted as card work.
  foreach ($u in ((& git -C $Path ls-files --others --exclude-standard 2>$null | Out-String) -split "`r?`n")) {
    $rel = $u.Trim()
    if (-not $rel) { continue }
    try {
      $full = Join-Path $Path $rel
      if (-not (Test-Path $full -PathType Leaf)) { continue }
      $changed += @(Get-Content $full -ErrorAction Stop).Count
      $touched += $rel
      $measured = $true
    }
    catch { continue }   # unreadable file: skip it rather than abandon the whole measurement
  }
}
catch { $measured = $false }

if (-not $measured -and $changed -eq 0) {
  # A genuinely empty diff and a failed measurement look identical from here, so say so rather than
  # reporting a confident zero. Exit 0: an unmeasurable tree is not an overrun.
  if (-not $Quiet) { Write-Meter "[CARD-BUDGET] $TaskId - no measurable diff against '$baseRef' (empty, or git could not answer)." 'DarkGray' }
  exit 0
}

# -- 6. the decision, made in the core -------------------------------------------------------------
$decision = Get-ScaffoldCardBudgetDecision `
  -BaseCardText $baseCard `
  -BranchCardText $branchCard `
  -ChangedLines $changed `
  -DefaultBudget (Get-ScaffoldCardBudgetDefault) `
  -TripFraction (Get-ScaffoldCardBudgetTripFraction)

# allow_paths coverage, reported beside the line count because the two answer different questions: the
# count says how much has been spent, the coverage says how much of the card's declared surface has been
# entered at all. A card at 80% of budget having touched 2 of 9 declared paths is a different situation
# from one having touched 9 of 9, and the split advice differs accordingly.
$declaredPaths = @()
try {
  # _cards.ps1 FIRST and not by taste: the allow_paths reader in _scope.ps1 calls Get-FrontMatter and
  # Get-YamlBlockListItems, both of which live in _cards.ps1. Sourcing _scope.ps1 alone leaves them
  # undefined, the reader throws, and the catch below turns that into a silent "allow_paths unreadable"
  # - a coverage report that quietly reports nothing rather than failing. Measured live on this card.
  . (Join-Path $here '_cards.ps1')
  . (Join-Path $here '_scope.ps1')
  $cardForPaths = if ($baseCard) { $baseCard } else { $branchCard }
  $declaredPaths = @(Get-ScaffoldCardAllowPathFromText -CardText $cardForPaths)
}
catch { $declaredPaths = @() }
$touchedUnique = @($touched | Sort-Object -Unique)
$pathsEntered = 0
foreach ($d in $declaredPaths) {
  $norm = ($d -replace '\\', '/').TrimEnd('/')
  if ($touchedUnique | Where-Object { ($_ -replace '\\', '/') -eq $norm -or ($_ -replace '\\', '/').StartsWith($norm + '/') }) { $pathsEntered++ }
}

$colour = switch ($decision.State) { 'over' { 'Red' } 'trip' { 'Yellow' } default { 'DarkGray' } }
$pathNote = if ($declaredPaths.Count -gt 0) { "$pathsEntered of $($declaredPaths.Count) allow_paths entered" } else { 'allow_paths unreadable' }

# WHOSE number (TD246). The one-line form is what the T232 Stop hook prints EVERY TURN, and it carried
# a state with no source, so a card that declares nothing read exactly like a card over its own budget.
$srcTag = if ($decision.Source -eq 'default') { ', config default - ship does not block' } else { '' }

if ($Quiet) {
  Write-Meter "[CARD-BUDGET] $TaskId $($decision.Used)/$($decision.Budget) ($($decision.State)$srcTag) - $pathNote" $colour
}
else {
  Write-Meter "" $null
  Write-Meter "[CARD-BUDGET] $TaskId  vs $baseUsed  (budget source: $($decision.Source))" $colour
  Write-Meter "  $($decision.Message)" $colour
  Write-Meter "  $pathNote; $($touchedUnique.Count) file(s) touched" 'DarkGray'
  if ($decision.State -eq 'trip' -or $decision.State -eq 'over') {
    # The two ways out are the moves that clear a BLOCK. There is no block to clear when the number came
    # from config, so offering them there sends the author to edit a base card for no reason.
    if ($decision.Source -eq 'default') {
      Write-Meter "  Nothing is blocked here: ship judges this card at DefaultBudget 0 and passes it." $colour
      Write-Meter "    declare    - add budget: to the card on $baseRef if you want a number that binds" $colour
    }
    else {
      Write-Meter "  Two ways out, and only these two:" $colour
      Write-Meter "    split      - name the cards and the order, then carve this one down" $colour
      Write-Meter "    raise      - edit budget: on the BASE card ($baseRef), as its own commit, reason in that commit." $colour
      Write-Meter "                 Raising it in this branch is inert by design - the base card is what is read." $colour
    }
  }
  Write-Meter "" $null
}

if ($decision.State -eq 'trip' -or $decision.State -eq 'over') { exit 1 }
exit 0
