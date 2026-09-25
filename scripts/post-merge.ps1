<#
.SYNOPSIS
  Post-merge automation (T0-POST-MERGE-DOCS-PR): the R5 doc-sync PR and the pruning of merged remote branches.
.DESCRIPTION
  r5     Builds a card's R5 doc sync in a new worktree cut from origin/<Base>: the card's status becomes merged,
         its docs/TASK-BOARD.md row gets the given status cell, the given entry goes directly under
         '## 当前阶段' in CLAUDE.md, and the given section is appended to the card. The PR merges on CI alone,
         without R3, and only inside the allowlist the user set on 2026-09-25: the card file, the card's own
         board row, and lines ADDED to the current-stage section of CLAUDE.md. Anything else stops with
         [POST-MERGE-SCOPE] before a push. The prose is the caller's; this script only places it.
  prune  Deletes a remote branch only when exactly one PR has it as head, that PR is MERGED, and the remote tip
         still equals the PR's head, using a lease on that tip. Every other state is reported and kept.
  The main checkout's working tree and local master are never touched: r5 works in its own temporary worktree
  and removes it, with its local branch, at the end; a removal that fails is reported as [POST-MERGE-CLEANUP-FAIL].
.EXAMPLE
  pwsh -NoProfile -File scripts\post-merge.ps1 r5 -TaskId T0-FOO -BoardStatusFile s.txt -StageEntryFile e.md -CardNoteFile n.md
  pwsh -NoProfile -File scripts\post-merge.ps1 prune -Branch T0-FOO
  pwsh -NoProfile -File scripts\post-merge.ps1 -SelfCheck
#>
[CmdletBinding()]
param(
  [Parameter(Position = 0)][ValidateSet('r5', 'prune')][string]$Command,
  [string]$TaskId,
  [string]$BoardStatusFile,
  [string]$StageEntryFile,
  [string]$CardNoteFile,
  [string[]]$Branch = @(),
  [string]$Base = 'master',
  [int]$CiTimeoutSec = 1800,
  [switch]$SelfCheck
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$script:StageHeading = '## 当前阶段'

# ── Pure decisions (covered by -SelfCheck; no git, gh or file access) ────────────────────────────────

# Edits keep the file's own line ending, so a CRLF file is not rewritten as LF.
function Get-PostMergeNewline([string]$Text) { if ($Text.Contains("`r`n")) { return "`r`n" } return "`n" }

# The status line is looked for only inside the front matter: a body line reading 'status: todo' is prose.
function Set-PostMergeCardStatus([string]$CardText) {
  $nl = Get-PostMergeNewline $CardText
  $lines = $CardText -split '\r?\n'
  if ($lines[0] -cne '---') { throw '[POST-MERGE-ANCHOR] the card has no front matter' }
  $end = -1
  for ($i = 1; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq '---') { $end = $i; break } }
  if ($end -lt 0) { throw '[POST-MERGE-ANCHOR] the card front matter is not closed' }
  $hits = @(for ($i = 1; $i -lt $end; $i++) { if ($lines[$i] -cmatch '^status:') { $i } })
  if ($hits.Count -ne 1) { throw "[POST-MERGE-ANCHOR] the card front matter has $($hits.Count) status lines, expected 1" }
  if ($lines[$hits[0]] -cmatch '^status:\s*merged\b') { throw '[POST-MERGE-ANCHOR] the card is already merged' }
  $lines[$hits[0]] = 'status: merged'
  return ($lines -join $nl)
}

function Add-PostMergeCardSection([string]$CardText, [string]$Section) {
  if ($Section.Trim() -cnotmatch '^## \S') { throw '[POST-MERGE-INPUT] the R5 section must start with a ''## '' heading' }
  $nl = Get-PostMergeNewline $CardText
  return ($CardText.TrimEnd() + $nl + $nl + ($Section.Trim() -replace '\r?\n', $nl) + $nl)
}

# A board row is the card's when its second cell is the card id, compared ordinally: a longer id sharing the
# prefix, or the same id in another case, is a different card.
function Test-PostMergeBoardRow([string]$Line, [string]$TaskId) {
  if (-not $Line.StartsWith('|')) { return $false }
  $cells = $Line.Trim().Trim('|').Split('|')
  return ($cells.Count -ge 3 -and [string]::Equals($cells[1].Trim(), $TaskId, [StringComparison]::Ordinal))
}

function Get-PostMergeCells([string]$Line) { return @($Line.Trim().Trim('|').Split('|') | ForEach-Object { $_.Trim() }) }

# The editor also requires the row to sit in a main card table, since other board tables hold card ids in their
# second column too while their last column is not the status. The table is the run of '|' lines around the row;
# its first line is the header, which must have '卡 id' second and '卡片状态 / 备注' last, with a '---' line under it
# and as many cells as the row. The cell counts are compared first, which keeps $head[1] in range (the row has at
# least three cells). The allowlist judge reads -U0 hunks with no table around them and keeps the second-cell test.
function Test-PostMergeMainTableRow([string[]]$Lines, [int]$Index) {
  $top = $Index
  while ($top -gt 0 -and $Lines[$top - 1].StartsWith('|')) { $top-- }
  $head = @(Get-PostMergeCells $Lines[$top])
  return ($head.Count -eq @(Get-PostMergeCells $Lines[$Index]).Count -and $head[1] -ceq '卡 id' -and $head[-1] -ceq '卡片状态 / 备注' -and $Lines[$top + 1] -cmatch '^\|(\s*:?-{3,}:?\s*\|)+\s*$')
}

function Set-PostMergeBoardStatus([string]$BoardText, [string]$TaskId, [string]$Status) {
  if ([string]::IsNullOrWhiteSpace($Status) -or ($Status -match '[|\r\n]')) { throw '[POST-MERGE-INPUT] the board status must be one non-empty line without ''|''' }
  $nl = Get-PostMergeNewline $BoardText
  $lines = $BoardText -split '\r?\n'
  $hits = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ((Test-PostMergeBoardRow $lines[$i] $TaskId) -and (Test-PostMergeMainTableRow $lines $i)) { $i } })
  if ($hits.Count -ne 1) { throw "[POST-MERGE-ANCHOR] docs/TASK-BOARD.md has $($hits.Count) main-card-table rows for $TaskId, expected 1" }
  $row = $lines[$hits[0]].TrimEnd()
  $cut = $row.TrimEnd('|').LastIndexOf('|')
  $lines[$hits[0]] = $row.Substring(0, $cut + 1) + ' ' + $Status.Trim() + ' |'
  return ($lines -join $nl)
}

# The entry may not carry a heading line of its own: the allowlist judge bounds the section by the next heading,
# so an injected heading would move that bound.
function Add-PostMergeStageEntry([string]$ClaudeText, [string]$Entry) {
  if ([string]::IsNullOrWhiteSpace($Entry)) { throw '[POST-MERGE-INPUT] the CLAUDE.md entry is empty' }
  if ($Entry -match '(?m)^\s*#') { throw '[POST-MERGE-INPUT] the CLAUDE.md entry must not contain a heading line' }
  $nl = Get-PostMergeNewline $ClaudeText
  $lines = $ClaudeText -split '\r?\n'
  $hits = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $script:StageHeading) { $i } })
  if ($hits.Count -ne 1) { throw "[POST-MERGE-ANCHOR] CLAUDE.md has $($hits.Count) '$($script:StageHeading)' headings, expected 1" }
  $h = $hits[0]
  $rest = [System.Collections.Generic.List[string]]::new()
  for ($i = $h + 1; $i -lt $lines.Count; $i++) { $rest.Add($lines[$i]) }
  while ($rest.Count -gt 0 -and $rest[0] -eq '') { $rest.RemoveAt(0) }
  $out = @($lines[0..$h]) + @('') + @($Entry.Trim() -split '\r?\n') + @('') + @($rest)
  return ($out -join $nl)
}

# Parses `git diff -U0` output. '---'/'+++' are file headers only before a file's first hunk: inside a hunk, a
# removed line whose text starts with '--' also begins with '---'.
function ConvertFrom-PostMergeDiff([string]$DiffText) {
  $files = [System.Collections.Generic.List[object]]::new()
  $cur = $null; $hunk = $null
  foreach ($line in ($DiffText -split '\r?\n')) {
    if ($line.StartsWith('diff --git ')) {
      $cur = [pscustomobject]@{ Path = ''; Hunks = [System.Collections.Generic.List[object]]::new() }
      $files.Add($cur); $hunk = $null; continue
    }
    if ($null -eq $cur) { continue }
    if ($null -eq $hunk -and $line.StartsWith('--- ')) { if ($line -cne '--- /dev/null') { $cur.Path = $line.Substring(4) -replace '^a/', '' }; continue }
    if ($null -eq $hunk -and $line.StartsWith('+++ ')) { if ($line -cne '+++ /dev/null') { $cur.Path = $line.Substring(4) -replace '^b/', '' }; continue }
    if ($line -match '^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@') {
      $oc = 1; if ($Matches[2]) { $oc = [int]$Matches[2] }
      $ncount = 1; if ($Matches[4]) { $ncount = [int]$Matches[4] }
      $hunk = [pscustomobject]@{ OldStart = [int]$Matches[1]; OldCount = $oc; NewStart = [int]$Matches[3]; NewCount = $ncount
        Removed = [System.Collections.Generic.List[string]]::new(); Added = [System.Collections.Generic.List[string]]::new() }
      $cur.Hunks.Add($hunk); continue
    }
    if ($null -eq $hunk) { continue }
    if ($line.StartsWith('+')) { $hunk.Added.Add($line.Substring(1)) }
    elseif ($line.StartsWith('-')) { $hunk.Removed.Add($line.Substring(1)) }
  }
  return $files.ToArray()
}

# The direct-merge allowlist (user ruling 2026-09-25): the card file; exactly the card's own board row; and
# lines ADDED to CLAUDE.md strictly between the current-stage heading and the next '## ' heading.
function Get-PostMergeScopeIssues([string]$TaskId, [string[]]$ChangedPaths, [object[]]$Files, [string]$NewClaudeText) {
  $issues = [System.Collections.Generic.List[string]]::new()
  $cardPath = "specs/tasks/$TaskId.md"; $boardPath = 'docs/TASK-BOARD.md'; $claudePath = 'CLAUDE.md'
  $allowed = @($cardPath, $boardPath, $claudePath)
  if (@($ChangedPaths).Count -eq 0) { $issues.Add('nothing changed'); return $issues.ToArray() }
  foreach ($p in $ChangedPaths) {
    if (-not @($allowed | Where-Object { [string]::Equals($_, $p, [StringComparison]::Ordinal) }).Count) { $issues.Add("path outside the allowlist: $p") }
  }
  foreach ($f in @($Files | Where-Object { [string]::Equals($_.Path, $boardPath, [StringComparison]::Ordinal) })) {
    $rem = @($f.Hunks | ForEach-Object { $_.Removed }); $add = @($f.Hunks | ForEach-Object { $_.Added })
    if ($rem.Count -ne 1 -or $add.Count -ne 1 -or -not (Test-PostMergeBoardRow $rem[0] $TaskId) -or -not (Test-PostMergeBoardRow $add[0] $TaskId)) { $issues.Add("docs/TASK-BOARD.md may change only the $TaskId row") }
  }
  foreach ($f in @($Files | Where-Object { [string]::Equals($_.Path, $claudePath, [StringComparison]::Ordinal) })) {
    $lines = $NewClaudeText -split '\r?\n'
    $heads = @(for ($i = 0; $i -lt $lines.Count; $i++) { if ($lines[$i] -ceq $script:StageHeading) { $i + 1 } })
    if ($heads.Count -ne 1) { $issues.Add("CLAUDE.md must keep exactly one '$($script:StageHeading)' heading"); continue }
    $next = $lines.Count + 1
    for ($i = $heads[0]; $i -lt $lines.Count; $i++) { if ($lines[$i] -cmatch '^## ') { $next = $i + 1; break } }
    foreach ($h in $f.Hunks) {
      if ($h.OldCount -gt 0) { $issues.Add("CLAUDE.md may only gain lines; a hunk at old line $($h.OldStart) removes or changes $($h.OldCount)") }
      if ($h.NewCount -gt 0 -and ($h.NewStart -le $heads[0] -or ($h.NewStart + $h.NewCount - 1) -ge $next)) { $issues.Add("CLAUDE.md lines $($h.NewStart)-$($h.NewStart + $h.NewCount - 1) are outside '$($script:StageHeading)' (lines $($heads[0] + 1)-$($next - 1))") }
    }
  }
  return $issues.ToArray()
}

function Get-PostMergePruneDecision([string]$RemoteTip, [object[]]$Prs) {
  $prs = @($Prs)
  if (-not $RemoteTip) { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = 'no such remote branch' } }
  if ($RemoteTip -notmatch '^[0-9a-fA-F]{40}$') { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = "remote tip '$RemoteTip' is not a full SHA" } }
  if ($prs.Count -eq 0) { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = 'no PR has this branch as head' } }
  if ($prs.Count -gt 1) { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = "$($prs.Count) PRs have this branch as head" } }
  $p = $prs[0]
  if ([string]$p.state -cne 'MERGED') { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = "PR #$($p.number) is $($p.state)" } }
  if (-not [string]::Equals(([string]$p.headRefOid).Trim(), $RemoteTip.Trim(), [StringComparison]::OrdinalIgnoreCase)) { return [pscustomobject]@{ Delete = $false; Oid = ''; Reason = "remote tip $RemoteTip is not PR #$($p.number)'s head $($p.headRefOid)" } }
  return [pscustomobject]@{ Delete = $true; Oid = $RemoteTip.Trim().ToLowerInvariant(); Reason = "PR #$($p.number) is MERGED at this tip" }
}

# A CheckRun reports status + conclusion, a commit status reports state; both reduce to success/pending/failure.
function Get-PostMergeCheckState($Check) {
  $p = $Check.PSObject.Properties
  if ($p['status']) {
    if ([string]$Check.status -cne 'COMPLETED') { return 'pending' }
    if (@('SUCCESS', 'NEUTRAL', 'SKIPPED') -ccontains [string]$Check.conclusion) { return 'success' }
    return 'failure'
  }
  if ($p['state']) {
    if ([string]$Check.state -ceq 'SUCCESS') { return 'success' }
    if (@('PENDING', 'EXPECTED') -ccontains [string]$Check.state) { return 'pending' }
  }
  return 'failure'
}

function Get-PostMergeCheckName($Check) {
  foreach ($n in @('name', 'context')) { if ($Check.PSObject.Properties[$n]) { return [string]$Check.$n } }
  return '?'
}

# The fan-in check must end in exactly SUCCESS: NEUTRAL or SKIPPED is not a pass for the one check that stands
# for the whole CI run (the ship's CI gate accepts only 'success' too).
function Test-PostMergeFanInSuccess($Check) {
  $p = $Check.PSObject.Properties
  if ($p['status']) { return ([string]$Check.status -ceq 'COMPLETED' -and [string]$Check.conclusion -ceq 'SUCCESS') }
  if ($p['state']) { return ([string]$Check.state -ceq 'SUCCESS') }
  return $false
}

# The ci.yml runs for one head: any completed run that did not succeed is a failure, a succeeded one is success,
# and anything else (not listed yet, queued, in progress) is still pending. Runs for other commits are ignored.
function Get-PostMergeRunDecision([object[]]$Runs, [string]$Head) {
  $mine = @($Runs | Where-Object { [string]$_.headSha -ceq $Head })
  $bad = @($mine | Where-Object { [string]$_.status -ceq 'completed' -and [string]$_.conclusion -cne 'success' })
  if ($bad.Count) { return [pscustomobject]@{ State = 'failure'; Id = [string]$bad[0].databaseId } }
  $ok = @($mine | Where-Object { [string]$_.conclusion -ceq 'success' })
  if ($ok.Count) { return [pscustomobject]@{ State = 'success'; Id = [string]$ok[0].databaseId } }
  return [pscustomobject]@{ State = 'pending'; Id = '' }
}
# What a failed r5 left on the remote, as probed facts only. The failure path takes no action and does not judge
# which PR is this R5, because a push, PR or merge that errored locally may still have taken effect remotely. The
# guidance is the same in every state and only says to inspect: it never says to delete, prune or merge by hand.
function Format-PostMergeRecoveryReport([string]$Branch, [string]$Head, [string]$Base, [bool]$TipKnown, [string]$Tip, [bool]$PrsKnown, [object[]]$Prs) {
  $tipText = 'none'
  if ($Tip) { $tipText = $Tip }
  if (-not $TipKnown) { $tipText = 'unknown (probe failed)' }
  $prText = 'none'
  if (@($Prs).Count) { $prText = (@($Prs) | ForEach-Object { "#$($_.number) $($_.state) head $($_.headRefOid) base $($_.baseRefName)" }) -join '; ' }
  if (-not $PrsKnown) { $prText = 'unknown (probe failed)' }
  return "[POST-MERGE-LEFT-BEHIND] r5 failed after pushing $Branch (head $Head, base $Base). Remote branch tip: $tipText. PRs: $prText. Nothing was pruned and nothing is decided here: inspect the branch and PRs above before acting, never merge a PR by hand (only r5 re-checks head, base and CI), and rerun r5 only after the branch is gone and no PR for it is open."
}
# ── Self-check ────────────────────────────────────────────────────────────────────────────────────────

function Invoke-PostMergeSelfCheck {
  $fails = [System.Collections.Generic.List[string]]::new()
  function Check([string]$Name, [scriptblock]$Body) {
    $script:pmCount++
    try { if (-not (& $Body)) { $fails.Add($Name) } } catch { $fails.Add("$Name (threw: $($_.Exception.Message))") }
  }
  function Throws([string]$Name, [string]$Sentinel, [scriptblock]$Body) {
    $script:pmCount++
    try { & $Body | Out-Null; $fails.Add("$Name (did not throw)") }
    catch { if (-not $_.Exception.Message.Contains($Sentinel)) { $fails.Add("$Name (threw '$($_.Exception.Message)', expected $Sentinel)") } }
  }
  $script:pmCount = 0

  $card = "---`nid: T9-DEMO`ntitle: demo`nstatus: todo`nallow_paths:`n  - x`n---`n`n# T9-DEMO`n`nstatus: todo`n"
  Check 'card: front-matter status becomes merged' { (Set-PostMergeCardStatus $card) -ceq "---`nid: T9-DEMO`ntitle: demo`nstatus: merged`nallow_paths:`n  - x`n---`n`n# T9-DEMO`n`nstatus: todo`n" }
  Throws 'card: no front matter' '[POST-MERGE-ANCHOR]' { Set-PostMergeCardStatus "# T9-DEMO`nstatus: todo`n" }
  Throws 'card: a later --- rule is not front matter' '[POST-MERGE-ANCHOR]' { Set-PostMergeCardStatus "# T9-DEMO`n`nstatus: todo`n`n---`n" }
  Throws 'card: front matter not closed' '[POST-MERGE-ANCHOR] the card front matter is not closed' { Set-PostMergeCardStatus "---`nstatus: todo`n" }
  Throws 'card: two status lines' '[POST-MERGE-ANCHOR]' { Set-PostMergeCardStatus "---`nstatus: todo`nstatus: todo`n---`n" }
  Throws 'card: already merged' '[POST-MERGE-ANCHOR]' { Set-PostMergeCardStatus "---`nid: T9-DEMO`nstatus: merged`n---`n" }
  Check 'card: CRLF card keeps CRLF' { (Set-PostMergeCardStatus "---`r`nstatus: todo`r`n---`r`n") -ceq "---`r`nstatus: merged`r`n---`r`n" }
  Check 'card: section appended after one blank line' { (Add-PostMergeCardSection "---`n---`nbody`n`n" "## R5`n`ndone") -ceq "---`n---`nbody`n`n## R5`n`ndone`n" }
  Throws 'card: empty section' '[POST-MERGE-INPUT]' { Add-PostMergeCardSection "body`n" "  " }
  Throws 'card: section without a heading' '[POST-MERGE-INPUT]' { Add-PostMergeCardSection "body`n" "done" }

  # Two main card tables, and between them another table whose second column also holds T9-DEMO.
  $mainHead = "| 波 | 卡 id | 产出（一句话） | depends_on | 难度 | 首选模型 · effort | 备选 | 卡片状态 / 备注 |`n|---|---|---|---|---|---|---|---|`n"
  $rowX = "| W0 | T9-DEMO-X | other | — | S | a | b | **todo** |`n"
  $remote = "`n| 远端交付卡 | 原产品卡 | 依赖 | 作者 | a | b | c | 状态 |`n|---|---|---|---|---|---|---|---|`n| T9-DEMO-REMOTE | T9-DEMO | — | x | a | b | c | done |`n`n"
  $board = $mainHead + $rowX + $remote + $mainHead + "| W0 | T9-DEMO | demo | — | S | a | b | **todo** |`n"
  Check 'board: only the card row changes' { (Set-PostMergeBoardStatus $board 'T9-DEMO' '**merged**: done') -ceq ($mainHead + $rowX + $remote + $mainHead + "| W0 | T9-DEMO | demo | — | S | a | b | **merged**: done |`n") }
  Throws 'board: no row' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus $board 'T9-NONE' 'x' }
  Throws 'board: two rows' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus ($board + "| W1 | T9-DEMO | again | — | S | a | b | x |`n") 'T9-DEMO' 'x' }
  Throws 'board: a row in another table is not the card row' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus $remote 'T9-DEMO' 'x' }
  Throws 'board: a table whose second header cell is not 卡 id' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus "| 波 | 原产品卡 | x | 卡片状态 / 备注 |`n|---|---|---|---|`n| W0 | T9-DEMO | d | s |`n" 'T9-DEMO' 'x' }
  Throws 'board: a table whose last header cell is not the status' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus "| 波 | 卡 id | x | 状态 |`n|---|---|---|---|`n| W0 | T9-DEMO | d | s |`n" 'T9-DEMO' 'x' }
  Throws 'board: a table without a separator line' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus "| 波 | 卡 id | x | 卡片状态 / 备注 |`n| W0 | T9-DEMO | d | s |`n" 'T9-DEMO' 'x' }
  Throws 'board: a row whose cell count differs from its header' '[POST-MERGE-ANCHOR]' { Set-PostMergeBoardStatus ($mainHead + "| W0 | T9-DEMO | demo | S | **todo** |`n") 'T9-DEMO' 'x' }
  Check 'board: a longer id is not the card row' { -not (Test-PostMergeBoardRow '| W0 | T9-DEMO-X | other | x |' 'T9-DEMO') }
  Check 'board: the id cell is compared exactly' { -not (Test-PostMergeBoardRow '| W0 | t9-demo | other | x |' 'T9-DEMO') }
  Check 'board: a prose line carrying the id is not a row' { -not (Test-PostMergeBoardRow 'x | T9-DEMO | y' 'T9-DEMO') }
  Check 'board: a two-cell row is not a row' { -not (Test-PostMergeBoardRow '| W0 | T9-DEMO |' 'T9-DEMO') }
  Throws 'board: status with a pipe' '[POST-MERGE-INPUT]' { Set-PostMergeBoardStatus $board 'T9-DEMO' 'a | b' }
  Throws 'board: empty status' '[POST-MERGE-INPUT]' { Set-PostMergeBoardStatus $board 'T9-DEMO' ' ' }

  $claude = "# CLAUDE`n`n## 文件放置`n`nx`n`n## 当前阶段`n`n**old entry**`n`n## 权威文档`n`n1. y`n"
  Check 'stage: entry goes directly under the heading' { (Add-PostMergeStageEntry $claude '**new entry**') -ceq "# CLAUDE`n`n## 文件放置`n`nx`n`n## 当前阶段`n`n**new entry**`n`n**old entry**`n`n## 权威文档`n`n1. y`n" }
  Throws 'stage: heading missing' '[POST-MERGE-ANCHOR]' { Add-PostMergeStageEntry "# CLAUDE`n`nx`n" 'e' }
  Throws 'stage: heading twice' '[POST-MERGE-ANCHOR]' { Add-PostMergeStageEntry ($claude + "## 当前阶段`n") 'e' }
  Throws 'stage: entry with a heading line' '[POST-MERGE-INPUT]' { Add-PostMergeStageEntry $claude "e`n## 硬边界`nx" }
  Throws 'stage: empty entry' '[POST-MERGE-INPUT]' { Add-PostMergeStageEntry $claude "`n" }

  $diff = @(
    'diff --git a/CLAUDE.md b/CLAUDE.md', 'index 1111111..2222222 100644', '--- a/CLAUDE.md', '+++ b/CLAUDE.md',
    '@@ -8,0 +9,2 @@ ## 当前阶段', '+**new entry**', '+',
    'diff --git a/docs/TASK-BOARD.md b/docs/TASK-BOARD.md', '--- a/docs/TASK-BOARD.md', '+++ b/docs/TASK-BOARD.md',
    '@@ -1 +1 @@', '-| W0 | T9-DEMO | demo | **todo** |', '+| W0 | T9-DEMO | demo | **merged** |',
    '@@ -5,2 +5,0 @@', '--- a removed markdown rule line', '-plain removed line'
  ) -join "`n"
  $parsed = @()
  Check 'diff: the parser runs' { $script:pmParsed = @(ConvertFrom-PostMergeDiff $diff); $true }
  if (Test-Path variable:script:pmParsed) { $parsed = @($script:pmParsed) }
  Check 'diff: two files' { $parsed.Count -eq 2 -and $parsed[0].Path -ceq 'CLAUDE.md' -and $parsed[1].Path -ceq 'docs/TASK-BOARD.md' }
  Check 'diff: counts and starts' { $h = $parsed[0].Hunks[0]; $h.OldStart -eq 8 -and $h.OldCount -eq 0 -and $h.NewStart -eq 9 -and $h.NewCount -eq 2 -and $h.Added.Count -eq 2 }
  Check 'diff: an omitted count means 1' { $h = $parsed[1].Hunks[0]; $h.OldCount -eq 1 -and $h.NewCount -eq 1 -and $h.Removed[0] -ceq '| W0 | T9-DEMO | demo | **todo** |' }
  Check 'diff: a removed line starting with -- is content, not a header' { $parsed[1].Hunks.Count -eq 2 -and $parsed[1].Hunks[1].Removed.Count -eq 2 -and $parsed[1].Path -ceq 'docs/TASK-BOARD.md' }

  $newClaude = "# CLAUDE`n`n## 文件放置`n`nx`n`n## 当前阶段`n`n**new entry**`n`n**old entry**`n`n## 权威文档`n`n1. y`n"
  function Hunk($os, $oc, $ns, $nc, $rem, $add) { [pscustomobject]@{ OldStart = $os; OldCount = $oc; NewStart = $ns; NewCount = $nc; Removed = @($rem); Added = @($add) } }
  function FileOf($path, $hunks) { [pscustomobject]@{ Path = $path; Hunks = @($hunks) } }
  $okBoard = FileOf 'docs/TASK-BOARD.md' @(Hunk 1 1 1 1 @('| W0 | T9-DEMO | d | **todo** |') @('| W0 | T9-DEMO | d | **merged** |'))
  $okClaude = FileOf 'CLAUDE.md' @(Hunk 8 0 9 2 @() @('**new entry**', ''))
  $okCard = FileOf 'specs/tasks/T9-DEMO.md' @(Hunk 3 1 3 1 @('status: todo') @('status: merged'))
  $okPaths = @('CLAUDE.md', 'docs/TASK-BOARD.md', 'specs/tasks/T9-DEMO.md')
  Check 'scope: the allowed change has no issues' { @(Get-PostMergeScopeIssues 'T9-DEMO' $okPaths @($okClaude, $okBoard, $okCard) $newClaude).Count -eq 0 }
  Check 'scope: a path outside the allowlist' { @(Get-PostMergeScopeIssues 'T9-DEMO' ($okPaths + 'scripts/x.ps1') @($okClaude, $okBoard, $okCard, (FileOf 'scripts/x.ps1' @())) $newClaude).Count -gt 0 }
  Check 'scope: path case is compared exactly' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('claude.md') @((FileOf 'claude.md' @(Hunk 8 0 9 1 @() @('x')))) $newClaude).Count -gt 0 }
  Check 'scope: another card''s board row' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('docs/TASK-BOARD.md') @((FileOf 'docs/TASK-BOARD.md' @(Hunk 2 1 2 1 @('| W0 | T9-OTHER | d | x |') @('| W0 | T9-OTHER | d | y |')))) $newClaude).Count -gt 0 }
  Check 'scope: an extra board row' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('docs/TASK-BOARD.md') @((FileOf 'docs/TASK-BOARD.md' @(Hunk 1 1 1 2 @('| W0 | T9-DEMO | d | x |') @('| W0 | T9-DEMO | d | y |', '| W0 | T9-DEMO | d | z |')))) $newClaude).Count -gt 0 }
  Check 'scope: a CLAUDE.md line removed' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('CLAUDE.md') @((FileOf 'CLAUDE.md' @(Hunk 11 1 11 0 @('**old entry**') @()))) $newClaude).Count -gt 0 }
  Check 'scope: a CLAUDE.md line added outside the section' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('CLAUDE.md') @((FileOf 'CLAUDE.md' @(Hunk 14 0 15 1 @() @('x')))) $newClaude).Count -gt 0 }
  Check 'scope: a line added above the heading' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('CLAUDE.md') @((FileOf 'CLAUDE.md' @(Hunk 5 0 6 1 @() @('x')))) $newClaude).Count -gt 0 }
  Check 'scope: nothing changed' { @(Get-PostMergeScopeIssues 'T9-DEMO' @() @() $newClaude).Count -gt 0 }
  Check 'scope: two rows removed and one added' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('docs/TASK-BOARD.md') @((FileOf 'docs/TASK-BOARD.md' @(Hunk 1 2 1 1 @('| W0 | T9-DEMO | d | x |', '| W0 | T9-OTHER | d | x |') @('| W0 | T9-DEMO | d | y |')))) $newClaude).Count -gt 0 }
  Check 'scope: another card''s row rewritten into this card''s row' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('docs/TASK-BOARD.md') @((FileOf 'docs/TASK-BOARD.md' @(Hunk 2 1 2 1 @('| W0 | T9-OTHER | d | x |') @('| W0 | T9-DEMO | d | y |')))) $newClaude).Count -gt 0 }
  Check 'scope: this card''s row rewritten into another card''s row' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('docs/TASK-BOARD.md') @((FileOf 'docs/TASK-BOARD.md' @(Hunk 1 1 1 1 @('| W0 | T9-DEMO | d | x |') @('| W0 | T9-OTHER | d | y |')))) $newClaude).Count -gt 0 }
  Check 'scope: an added line that is the next heading' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('CLAUDE.md') @((FileOf 'CLAUDE.md' @(Hunk 11 0 12 2 @() @('', '## 权威文档')))) $newClaude).Count -gt 0 }
  Check 'scope: CLAUDE.md without the stage heading' { @(Get-PostMergeScopeIssues 'T9-DEMO' @('CLAUDE.md') @($okClaude) "# CLAUDE`n`nx`n").Count -gt 0 }

  $tip = 'a' * 40
  function Pr($n, $s, $h) { [pscustomobject]@{ number = $n; state = $s; headRefOid = $h } }
  Check 'prune: merged PR at the tip is deleted' { $d = Get-PostMergePruneDecision $tip @((Pr 1 'MERGED' $tip)); $d.Delete -and $d.Oid -ceq $tip }
  Check 'prune: no remote branch is kept' { $d = Get-PostMergePruneDecision '' @((Pr 1 'MERGED' $tip)); -not $d.Delete -and $d.Reason -ceq 'no such remote branch' }
  Check 'prune: no PR is kept' { -not (Get-PostMergePruneDecision $tip @()).Delete }
  Check 'prune: two PRs are kept' { -not (Get-PostMergePruneDecision $tip @((Pr 1 'MERGED' $tip), (Pr 2 'MERGED' $tip))).Delete }
  Check 'prune: an open PR is kept' { -not (Get-PostMergePruneDecision $tip @((Pr 1 'OPEN' $tip))).Delete }
  Check 'prune: a closed unmerged PR is kept' { -not (Get-PostMergePruneDecision $tip @((Pr 1 'CLOSED' $tip))).Delete }
  Check 'prune: a moved tip is kept' { -not (Get-PostMergePruneDecision $tip @((Pr 1 'MERGED' ('b' * 40)))).Delete }
  Check 'prune: a short tip is kept' { -not (Get-PostMergePruneDecision 'abc' @((Pr 1 'MERGED' 'abc'))).Delete }

  function Run($id, $sha, $status, $conclusion) { [pscustomobject]@{ databaseId = $id; headSha = $sha; status = $status; conclusion = $conclusion } }
  $cr = { param($s, $c) [pscustomobject]@{ name = 'required'; status = $s; conclusion = $c } }
  Check 'ci: a SUCCESS fan-in check is success' { Test-PostMergeFanInSuccess (& $cr 'COMPLETED' 'SUCCESS') }
  Check 'ci: a NEUTRAL fan-in check is not success' { -not (Test-PostMergeFanInSuccess (& $cr 'COMPLETED' 'NEUTRAL')) }
  Check 'ci: a pending CheckRun is not failure' { (Get-PostMergeCheckState (& $cr 'IN_PROGRESS' '')) -ceq 'pending' }
  Check 'ci: a FAILURE CheckRun is failure' { (Get-PostMergeCheckState (& $cr 'COMPLETED' 'FAILURE')) -ceq 'failure' }
  Check 'ci: a StatusContext ERROR is failure' { (Get-PostMergeCheckState ([pscustomobject]@{ context = 'x'; state = 'ERROR' })) -ceq 'failure' }
  Check 'ci: an in-progress run is pending' { (Get-PostMergeRunDecision @((Run 7 $tip 'in_progress' '')) $tip).State -ceq 'pending' }
  Check 'ci: a completed success run is success' { $r = Get-PostMergeRunDecision @((Run 7 $tip 'completed' 'success')) $tip; $r.State -ceq 'success' -and $r.Id -ceq '7' }
  Check 'ci: a failed ci.yml run is failure' { (Get-PostMergeRunDecision @((Run 7 $tip 'completed' 'failure')) $tip).State -ceq 'failure' }
  $rec = { param($tk, $tp, $pk, $p) Format-PostMergeRecoveryReport 'r5-T9-DEMO' $tip 'master' $tk $tp $pk @($p) }
  Check 'recovery: the report gives the tip and each PR with state, head and base' { $r = & $rec $true $tip $true @([pscustomobject]@{ number = 5; state = 'MERGED'; headRefOid = $tip; baseRefName = 'main' }); $r.StartsWith('[POST-MERGE-LEFT-BEHIND]') -and $r.Contains("tip: $tip.") -and $r.Contains("#5 MERGED head $tip base main") }
  Check 'recovery: an unreadable PR list is reported as unknown' { (& $rec $true $tip $false @()).Contains('PRs: unknown') }
  Check 'recovery: an unreadable branch tip is reported as unknown' { (& $rec $false '' $true @()).Contains('tip: unknown') }
  Check 'recovery: no branch and no PR are reported as none' { $r = & $rec $true '' $true @(); $r.Contains('tip: none') -and $r.Contains('PRs: none') }
  $inspectOnly = { param($r) $r.Contains('Nothing was pruned') -and $r.Contains('inspect the branch and PRs above before acting') -and $r.Contains('never merge a PR by hand') -and -not $r.Contains('delete') -and -not $r.Contains('prune -Branch') }
  Check 'recovery: the advice is inspection-only when nothing is known' { & $inspectOnly (& $rec $false '' $false @()) }
  Check 'recovery: the advice is inspection-only for a PR with another head' { $r = & $rec $true $tip $true @([pscustomobject]@{ number = 5; state = 'MERGED'; headRefOid = ('b' * 40); baseRefName = 'master' }); (& $inspectOnly $r) -and $r.Contains('head ' + ('b' * 40)) }
  Check 'recovery: the advice is inspection-only for several PRs' { $r = & $rec $true $tip $true @([pscustomobject]@{ number = 5; state = 'MERGED'; headRefOid = $tip; baseRefName = 'master' }, [pscustomobject]@{ number = 6; state = 'OPEN'; headRefOid = $tip; baseRefName = 'master' }); (& $inspectOnly $r) -and $r.Contains('#5 MERGED') -and $r.Contains('#6 OPEN') }
  Check 'recovery: the advice is inspection-only when nothing is left' { & $inspectOnly (& $rec $true '' $true @()) }
  Check 'ci: a run for another commit is ignored' { (Get-PostMergeRunDecision @((Run 7 ('b' * 40) 'completed' 'success')) $tip).State -ceq 'pending' }
  if ($fails.Count) {
    foreach ($f in $fails) { Write-Host "POST-MERGE-FAIL: $f" -ForegroundColor Red }
    Write-Host "[POST-MERGE-SELF-CHECK-FAIL] $($fails.Count) of $script:pmCount cases failed" -ForegroundColor Red
    return 1
  }
  Write-Host "[POST-MERGE-SELF-CHECK-PASS] $script:pmCount cases" -ForegroundColor Green
  return 0
}

# ── Git and gh plumbing (not covered by -SelfCheck). Native calls go through Invoke-PostMergeNative, which throws on a
# non-zero exit. The exceptions are the branch probe in r5 (git rev-parse --verify --quiet exits 1 for a missing
# branch, so its output is what counts) and the cleanup calls in r5's finally block, which report a failure
# as [POST-MERGE-CLEANUP-FAIL] instead of throwing over the error that got there first.

# Runs a native command and returns its stdout lines; a non-zero exit throws with the command's own stderr.
function Invoke-PostMergeNative([string]$What, [scriptblock]$Call) {
  $ErrorActionPreference = 'Continue'
  $raw = @(& $Call 2>&1)
  $code = $LASTEXITCODE
  $err = @($raw | Where-Object { $_ -is [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
  $out = @($raw | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] } | ForEach-Object { "$_" })
  if ($code -ne 0) { throw "[POST-MERGE-TOOL] $What failed (exit $code): $((@($err) + @($out)) -join ' ')" }
  return $out
}

# Rewrites a file through a pure edit, keeping its BOM (or absence of one) byte for byte.
function Update-PostMergeFile([string]$Path, [scriptblock]$Edit) {
  $bytes = [IO.File]::ReadAllBytes($Path)
  $bom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
  $start = 0; if ($bom) { $start = 3 }
  $text = [Text.UTF8Encoding]::new($false).GetString($bytes, $start, $bytes.Length - $start)
  [IO.File]::WriteAllText($Path, (& $Edit $text), [Text.UTF8Encoding]::new($bom))
}

# Waits, within one deadline, until the PR's fan-in check on the exact pushed head has ended in SUCCESS and a
# ci.yml pull_request run for that head has succeeded. Any failing check, a fan-in that ends in anything but
# SUCCESS, or a completed ci.yml run that did not succeed is [POST-MERGE-CI-RED]. Returns the run id.
function Wait-PostMergeCi([int]$Pr, [string]$Head) {
  $deadline = (Get-Date).AddSeconds($CiTimeoutSec)
  while ($true) {
    $view = (Invoke-PostMergeNative "gh pr view $Pr" { gh pr view $Pr --json headRefOid,statusCheckRollup }) -join "`n" | ConvertFrom-Json
    if ([string]$view.headRefOid -cne $Head) { throw "[POST-MERGE-HEAD-MOVED] PR #$Pr head is $($view.headRefOid), not the pushed $Head" }
    $checks = @($view.statusCheckRollup)
    $red = @($checks | Where-Object { (Get-PostMergeCheckState $_) -ceq 'failure' } | ForEach-Object { Get-PostMergeCheckName $_ })
    if ($red.Count) { throw "[POST-MERGE-CI-RED] PR #$Pr failing checks: $($red -join ', ')" }
    $fanIn = @($checks | Where-Object { (Get-PostMergeCheckName $_) -ceq $ScaffoldCiFanInJob })
    if (@($fanIn | Where-Object { (Get-PostMergeCheckState $_) -cne 'pending' -and -not (Test-PostMergeFanInSuccess $_) }).Count) { throw "[POST-MERGE-CI-RED] PR #${Pr}: '$ScaffoldCiFanInJob' finished without SUCCESS" }
    if ($fanIn.Count -and -not @($fanIn | Where-Object { -not (Test-PostMergeFanInSuccess $_) }).Count) {
      $runs = @((Invoke-PostMergeNative 'gh run list' { gh run list --commit $Head --workflow ci.yml --event pull_request --json databaseId,headSha,status,conclusion }) -join "`n" | ConvertFrom-Json)
      $run = Get-PostMergeRunDecision $runs $Head
      if ($run.State -ceq 'failure') { throw "[POST-MERGE-CI-RED] ci.yml run $($run.Id) for $Head did not succeed" }
      if ($run.State -ceq 'success') { return $run.Id }
    }
    if ((Get-Date) -gt $deadline) { throw "[POST-MERGE-CI-TIMEOUT] PR #${Pr}: '$ScaffoldCiFanInJob' and a successful ci.yml run for $Head did not both appear within $CiTimeoutSec s" }
    Start-Sleep -Seconds 20
  }
}
function Invoke-PostMergePrune([string[]]$Names) {
  # Through `pwsh -File` a list arrives as one string ("a,b", quotes included), so split and unquote here.
  $Names = @($Names | ForEach-Object { "$_" -split ',' } | ForEach-Object { $_.Trim().Trim("'", '"') } | Where-Object { $_ })
  if (-not $Names.Count) { throw '[POST-MERGE-INPUT] prune needs -Branch' }
  $failed = 0
  foreach ($b in $Names) {
    if ($b -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._-]*$') { throw "[POST-MERGE-INPUT] '$b' is not a branch name this script prunes" }
    $tip = ''
    $line = @(Invoke-PostMergeNative "git ls-remote $b" { git -C $RepoRoot ls-remote origin "refs/heads/$b" }) | Select-Object -First 1
    if ($line) { $tip = ($line -split "`t")[0].Trim() }
    $prs = @((Invoke-PostMergeNative "gh pr list --head $b" { gh pr list --state all --head $b --json number,state,headRefOid }) -join "`n" | ConvertFrom-Json)
    $d = Get-PostMergePruneDecision $tip $prs
    if (-not $d.Delete) { Write-Host "[POST-MERGE-PRUNE-KEEP] ${b}: $($d.Reason)" -ForegroundColor Yellow; continue }
    try {
      Invoke-PostMergeNative "delete $b" { git -C $RepoRoot push "--force-with-lease=refs/heads/${b}:$($d.Oid)" origin ":refs/heads/$b" } | Out-Null
      Write-Host "[POST-MERGE-PRUNED] ${b}: $($d.Reason)" -ForegroundColor Green
    } catch { $failed++; Write-Host "[POST-MERGE-PRUNE-FAIL] ${b}: $($_.Exception.Message)" -ForegroundColor Red }
  }
  if ($failed) { throw "[POST-MERGE-PRUNE-FAIL] $failed branch(es) could not be deleted" }
}

function Invoke-PostMergeR5 {
  if ($TaskId -cnotmatch '^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$') { throw "[POST-MERGE-INPUT] -TaskId '$TaskId' is not a card id" }
  $in = @{}
  foreach ($name in @('BoardStatusFile', 'StageEntryFile', 'CardNoteFile')) {
    $p = [string](Get-Variable -Name $name -ValueOnly)
    if (-not $p -or -not (Test-Path -LiteralPath $p -PathType Leaf)) { throw "[POST-MERGE-INPUT] -$name must name an existing file" }
    $in[$name] = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $p).Path)
  }
  Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote
  $branchName = "r5-$TaskId"
  Invoke-PostMergeNative "git fetch origin $Base" { git -C $RepoRoot fetch origin $Base } | Out-Null
  $baseOid = @(Invoke-PostMergeNative 'git rev-parse' { git -C $RepoRoot rev-parse "refs/remotes/origin/$Base" })[-1].Trim()
  if (& git -C $RepoRoot rev-parse --verify --quiet "refs/heads/$branchName") { throw "[POST-MERGE-BRANCH-EXISTS] local branch $branchName already exists" }
  if (@(Invoke-PostMergeNative 'git ls-remote' { git -C $RepoRoot ls-remote origin "refs/heads/$branchName" }).Count) { throw "[POST-MERGE-BRANCH-EXISTS] remote branch $branchName already exists" }
  $wt = Join-Path (Get-ScaffoldWorktreeRoot) $branchName
  if (Test-Path -LiteralPath $wt) { throw "[POST-MERGE-BRANCH-EXISTS] $wt already exists" }
  Invoke-PostMergeNative 'git worktree add' { git -C $RepoRoot worktree add -b $branchName $wt $baseOid } | Out-Null
  $merged = $false; $pushAttempted = $false; $failure = $null; $head = ''
  try {
    $cardFile = Join-Path $wt "specs/tasks/$TaskId.md"
    if (-not (Test-Path -LiteralPath $cardFile)) { throw "[POST-MERGE-ANCHOR] specs/tasks/$TaskId.md is not on origin/$Base" }
    Update-PostMergeFile $cardFile { param($t) Add-PostMergeCardSection (Set-PostMergeCardStatus $t) $in.CardNoteFile }
    Update-PostMergeFile (Join-Path $wt 'docs/TASK-BOARD.md') { param($t) Set-PostMergeBoardStatus $t $TaskId $in.BoardStatusFile.Trim() }
    Update-PostMergeFile (Join-Path $wt 'CLAUDE.md') { param($t) Add-PostMergeStageEntry $t $in.StageEntryFile }
    $msg = Join-Path $wt '.git-post-merge-msg'
    [IO.File]::WriteAllText($msg, "docs: R5 sync for $TaskId`n`nCard status to merged, its docs/TASK-BOARD.md row, and a CLAUDE.md current-stage entry.`nOpened by scripts/post-merge.ps1 r5 and merged on CI alone inside the direct-merge`nallowlist (T0-POST-MERGE-DOCS-PR).`n", [Text.UTF8Encoding]::new($false))
    try {
      Invoke-PostMergeNative 'git add' { git -C $wt add -- "specs/tasks/$TaskId.md" 'docs/TASK-BOARD.md' 'CLAUDE.md' } | Out-Null
      Invoke-PostMergeNative 'git commit' { git -C $wt commit -q -F $msg } | Out-Null
    } finally { Remove-Item -LiteralPath $msg -ErrorAction SilentlyContinue }
    $paths = @(Invoke-PostMergeNative 'git diff --name-only' { git -C $wt -c core.quotepath=false diff --name-only --no-renames $baseOid HEAD })
    $diff = (Invoke-PostMergeNative 'git diff' { git -C $wt -c core.quotepath=false diff -U0 --no-renames --no-color $baseOid HEAD }) -join "`n"
    $issues = @(Get-PostMergeScopeIssues $TaskId $paths @(ConvertFrom-PostMergeDiff $diff) ([IO.File]::ReadAllText((Join-Path $wt 'CLAUDE.md'))))
    if ($issues.Count) { throw "[POST-MERGE-SCOPE] $($issues -join '; ')" }
    foreach ($gate in @(@('check-cards.ps1', '-TaskId', $TaskId), @('check-secrets.ps1'))) {
      $gateArgs = @($gate | Select-Object -Skip 1)
      & pwsh -NoProfile -File (Join-Path $wt "scripts/$($gate[0])") @gateArgs | Out-Host
      if ($LASTEXITCODE -ne 0) { throw "[POST-MERGE-GATE] $($gate[0]) exited $LASTEXITCODE on the doc-sync worktree" }
    }
    $head = @(Invoke-PostMergeNative 'git rev-parse HEAD' { git -C $wt rev-parse HEAD })[-1].Trim()
    Write-Host "[POST-MERGE-SCOPE-OK] $($paths -join ', ') on base $baseOid, head $head" -ForegroundColor DarkGray
    $pushAttempted = $true
    Invoke-PostMergeNative 'git push' { git -C $wt push origin "refs/heads/${branchName}:refs/heads/$branchName" } | Out-Null
    $body = Join-Path ([IO.Path]::GetTempPath()) "post-merge-$branchName-$PID.md"
    [IO.File]::WriteAllText($body, "R5 doc sync for ``$TaskId``, opened by ``scripts/post-merge.ps1 r5``.`n`nIt changes only what the direct-merge allowlist permits (user ruling 2026-09-25, ``T0-POST-MERGE-DOCS-PR``): the card file, the card's ``docs/TASK-BOARD.md`` row, and lines added under ``$($script:StageHeading)`` in ``CLAUDE.md``. It merges once CI passes, without R3.`n", [Text.UTF8Encoding]::new($false))
    try { $url = @(Invoke-PostMergeNative 'gh pr create' { gh pr create --base $Base --head $branchName --title "docs: R5 sync for $TaskId" --body-file $body })[-1].Trim() }
    finally { Remove-Item -LiteralPath $body -ErrorAction SilentlyContinue }
    if ($url -notmatch '/pull/(\d+)$') { throw "[POST-MERGE-TOOL] gh pr create did not return a PR URL: $url" }
    $pr = [int]$Matches[1]
    Write-Host "[POST-MERGE-PR] $url" -ForegroundColor DarkGray
    $runId = Wait-PostMergeCi $pr $head
    $view = (Invoke-PostMergeNative "gh pr view $pr" { gh pr view $pr --json headRefOid,baseRefName }) -join "`n" | ConvertFrom-Json
    if ([string]$view.headRefOid -cne $head -or [string]$view.baseRefName -cne $Base) { throw "[POST-MERGE-HEAD-MOVED] PR #$pr is $($view.headRefOid) on $($view.baseRefName), expected $head on $Base" }
    Invoke-PostMergeNative "gh pr merge $pr" { gh pr merge $pr --squash --match-head-commit $head } | Out-Null
    $after = (Invoke-PostMergeNative "gh pr view $pr" { gh pr view $pr --json state,mergeCommit }) -join "`n" | ConvertFrom-Json
    if ([string]$after.state -cne 'MERGED') { throw "[POST-MERGE-TOOL] PR #$pr is $($after.state) after the merge call" }
    $merged = $true
    Write-Host "[POST-MERGE-MERGED] PR #$pr merge=$($after.mergeCommit.oid) head=$head ci-run=$runId" -ForegroundColor Green
  } catch {
    $failure = $_
    if ($pushAttempted -and -not $merged) {
      $tipKnown = $true; $tip = ''; $prsKnown = $true; $prs = @()
      try { $line = @(Invoke-PostMergeNative "git ls-remote $branchName" { git -C $RepoRoot ls-remote origin "refs/heads/$branchName" }) | Select-Object -First 1; if ($line) { $tip = ($line -split "`t")[0].Trim() } } catch { $tipKnown = $false }
      try { $prs = @((Invoke-PostMergeNative "gh pr list --head $branchName" { gh pr list --state all --head $branchName --json number,state,headRefOid,baseRefName }) -join "`n" | ConvertFrom-Json) } catch { $prsKnown = $false }
      Write-Host (Format-PostMergeRecoveryReport $branchName $head $Base $tipKnown $tip $prsKnown $prs) -ForegroundColor Yellow
    }
  } finally {
    & git -C $RepoRoot worktree remove --force $wt 2>$null | Out-Null
    if (($LASTEXITCODE -ne 0) -or (Test-Path -LiteralPath $wt)) { Write-Host "[POST-MERGE-CLEANUP-FAIL] worktree $wt was not removed; remove it with: git worktree remove --force $wt" -ForegroundColor Red }
    & git -C $RepoRoot branch -D $branchName 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host "[POST-MERGE-CLEANUP-FAIL] local branch $branchName was not deleted; delete it with: git branch -D $branchName" -ForegroundColor Red }
  }
  if ($merged) { Invoke-PostMergePrune @($branchName); return }
  throw $failure
}

if ($SelfCheck) { exit (Invoke-PostMergeSelfCheck) }
. (Join-Path $PSScriptRoot '_guard.ps1')   # loads _config.ps1 too: Assert-PersonalAccount, Get-ScaffoldWorktreeRoot
. (Join-Path $PSScriptRoot '_ci.ps1')      # $ScaffoldCiFanInJob, the single name of the CI fan-in check
try {
  switch ($Command) {
    'r5' { Invoke-PostMergeR5 }
    'prune' { Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote; Invoke-PostMergePrune $Branch }
    default { Write-Host 'usage: post-merge.ps1 r5 -TaskId <id> -BoardStatusFile <f> -StageEntryFile <f> -CardNoteFile <f> | prune -Branch <b>[,<b>...] | -SelfCheck' -ForegroundColor Yellow; exit 2 }
  }
} catch {
  Write-Host $_.Exception.Message -ForegroundColor Red
  exit 1
}
exit 0
