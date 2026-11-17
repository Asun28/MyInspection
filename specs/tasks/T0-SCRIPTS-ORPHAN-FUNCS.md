---
id: T0-SCRIPTS-ORPHAN-FUNCS
title: Delete Add-GradleLicenseFinding, Get-WorkflowActiveStepIndices and the gate2 fixture nothing reads
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
  - scripts/fixtures/gate2/
  - specs/tasks/T0-SCRIPTS-ORPHAN-FUNCS.md
sweep: "2026-10-14 audit on 01a2e801 (carried over from T0-SCRIPTS-ORPHAN-CLEANUP on 2026-11-16): an AST call graph over every tracked .ps1, then git grep -w for each removed name over scripts, .github, .claude and specs/tasks. check-licenses.ps1:638 Add-GradleLicenseFinding and license-scanner-check.ps1:436 Get-WorkflowActiveStepIndices: definition only; Get-WorkflowActiveStepRecords keeps its callers at :383 and :761. scripts/fixtures/gate2/: no script names it; only archived T3-E2E-* cards and LEDGER L-entry refs, which stay as history."
forbid:
  - Changing any function that stays or any message a remaining check prints
  - Deleting code that came from the upstream scaffold in PR 297 (reported upstream instead; see docs/SCAFFOLD-SYNC.md)
  - Editing docs/LICENSE-POLICY.md; the check-licenses.ps1 change removes an uncalled function only, so its commit carries [doc-sync:none]
non_goals:
  - scripts/_validation.ps1 and its gate 1h hook (T0-SCRIPTS-ORPHAN-CLEANUP)
  - The 13 unread Prereview* knobs (TD192) and the card checks check-cards.ps1 keeps off (TD193)
  - Historical mentions in CLAUDE.md, docs/TASK-BOARD.md, the ledger and archived cards
acceptance:
  - "A1 check-licenses.ps1 no longer defines Add-GradleLicenseFinding and license-scanner-check.ps1 no longer defines Get-WorkflowActiveStepIndices; nothing else in either file changes."
  - "A2 scripts/fixtures/gate2/ is deleted, and no tracked file under scripts, .github or .claude names fixtures/gate2, Add-GradleLicenseFinding or Get-WorkflowActiveStepIndices."
  - "A3 Both edited scripts parse with no errors, and the Tier-S full selftest passes on the shipped candidate. Evidence form: the implementation record carries a full run on a commit C (launch and end HEAD, an empty git status --porcelain --untracked-files=all, each shard's exit and [GATE-RESULT-SUMMARY] failed=none, selftest: PASS), and git diff --stat C HEAD lists only this card; a commit cannot carry the output of a run on itself, and the reviewer reads only the diff (L227)."
dod_command: $ErrorActionPreference = 'Stop'; foreach ($f in @('scripts/check-licenses.ps1', 'scripts/license-scanner-check.ps1')) { $e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $f).Path, [ref]$null, [ref]$e); if ($e -and $e.Count) { Write-Host "[DOD-FAIL] $f does not parse"; exit 1 } }; if (Test-Path -LiteralPath 'scripts/fixtures/gate2') { Write-Host '[DOD-FAIL] scripts/fixtures/gate2 still exists'; exit 1 }; $PSNativeCommandUseErrorActionPreference = $false; $hits = @(& git grep -n -e 'fixtures/gate2' -e 'Add-GradleLicenseFinding' -e 'Get-WorkflowActiveStepIndices' -- scripts .github .claude); if ($LASTEXITCODE -gt 1) { Write-Host "[DOD-FAIL] git grep exit $LASTEXITCODE"; exit 1 }; if ($hits.Count) { $hits | ForEach-Object { Write-Host $_ }; Write-Host '[DOD-FAIL] a removed name is still referenced'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: Both scripts parse, the fixture is gone and no removed name is referenced under scripts, .github or .claude; prints [DOD-PASS]. On base it exits 1 because scripts/fixtures/gate2 exists. git grep exits 1 when nothing matches, which is the pass case, so the DoD turns off $PSNativeCommandUseErrorActionPreference (the ship sets it) before git grep and fails on an exit above 1.
review_gate: codex {verdict:pass}
budget: 120
hygiene: Deletion only, so R4 has no new assertion to prove. R4 instead restores each deleted unit one at a time (each function, the fixture) and records in the R5 note that the DoD fails on each restore, which shows the DoD checks every deletion.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-SCRIPTS-ORPHAN-FUNCS

Split from `T0-SCRIPTS-ORPHAN-CLEANUP` on 2026-11-16 under the user's rule that a card changes fewer than 200
lines (see that card's Split section). From the 2026-10-14 unused-code audit (plan:
`docs/plans/PLAN-AUDIT-CLEANUP-20261014.md`, aidlc goal `g-20261014091214-314b46`).

## Why each piece goes

- `Add-GradleLicenseFinding` lost its caller in #297, and the nested `Get-WorkflowActiveStepIndices` lost its
  caller in a later local change. Both are local code.
- `scripts/fixtures/gate2/` held the fake Gradle wrappers for the gate 2 fixture. Selftest now writes its own stubs
  (`Set-ProjectGate2Stub15`), and no script reads this directory.

The deletions were first made on the `T0-SCRIPTS-ORPHAN-CLEANUP` branch (c0dc420a); this card takes the same
hunks for these three paths.

## Tier

`scripts/check-licenses.ps1` is a TierSPaths entry, so the card is Tier S and needs the full selftest on the
shipped candidate (A3).

## Implementation record (2026-11-16)

- Change: the hunks of c0dc420a (`T0-SCRIPTS-ORPHAN-CLEANUP` branch) for this card's three paths, applied with
  `git apply` on bd9db075: `Add-GradleLicenseFinding` (47 lines) and `Get-WorkflowActiveStepIndices` (5 lines)
  removed, `scripts/fixtures/gate2/android/gradlew{,.bat}` (23 lines) deleted. 75 deletions outside this card.
- RED: `task.ps1 -Phase red` on bd9db075 printed `[DOD-FAIL] scripts/fixtures/gate2 still exists` and exited 1.
- DoD: `[DOD-PASS]`, exit 0.
- R4 (hygiene): the runner first rewrote both scripts through its own byte path (SHA-256 unchanged) and saw the
  DoD pass. Putting back check-licenses.ps1 from base: DoD exit 1, `[DOD-FAIL] a removed name is still
  referenced`. Putting back license-scanner-check.ps1 from base: DoD exit 1, same line. Putting back the two
  fixture files: DoD exit 1, `[DOD-FAIL] scripts/fixtures/gate2 still exists`. 3/3 killed; both scripts restored
  to SHA-256 `249D256A...A1FEE2DD` (check-licenses.ps1) and `817817D5...1344CE76` (license-scanner-check.ps1).
- Tier S (A3): `selftest.ps1` with no arguments (one process runs all 17 gates in turn, so there are no shards)
  from this worktree on C = `69b19802`, the merge of origin/master `2b01826f`; the DoD on C prints `[DOD-PASS]`,
  exit 0. An earlier `-Parallel` run on C failed only 17post `T37-CIGATE/DEADLINE` (git-fetch-hang over its time
  bound under load, L386), and this serial run replaces it. Wrapper lines and the summary (cut after `wall=`):
  ```text
  head=69b19802257010a3aa2cb7bbdc423feac651cb16 status=[] start=2026-11-17T11:38:36.0720363+13:00
  [GATE-RESULT-SUMMARY] failed=none ran=17 wall=3807.6s
  selftest: PASS
  exit=0 end=2026-11-17T12:42:06.0187084+13:00 headAfter=69b19802257010a3aa2cb7bbdc423feac651cb16 statusAfter=[]
  ```
  `git diff --stat 69b19802 HEAD` lists only this card.

## Attempt evidence

| Item | Proof | Result |
|---|---|---|
| A1 | `git diff origin/master HEAD` on the two scripts removes only `Add-GradleLicenseFinding` (47 lines) and `Get-WorkflowActiveStepIndices` (5 lines); R4 puts back each script and the DoD exits 1 | pass |
| A2 | DoD: `scripts/fixtures/gate2` is absent and `git grep` over scripts, .github and .claude finds none of the three names; R4 puts back the fixture and the DoD exits 1 | pass |
| A3 | DoD parses both scripts; the serial full selftest on C = `69b19802` (record above) ends `failed=none ran=17`, `selftest: PASS` | pass |
