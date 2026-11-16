---
id: T0-SCRIPTS-ORPHAN-CLEANUP
title: Delete scripts/_validation.ps1, the selftest risk router nothing calls since PR 297
status: todo
depends_on: []
parallelizable_with: [T2-PHOTO-INVARIANT-CLEANUP]
allow_paths:
  - scripts/_validation.ps1
  - scripts/selftest.ps1
  - specs/tasks/T0-SCRIPTS-ORPHAN-CLEANUP.md
sweep: "2026-10-14 audit on 01a2e801: an AST call graph over every tracked .ps1, then git grep -w for each removed name over scripts, .github, .claude and specs/tasks. scripts/_validation.ps1: its 21 functions are called only from inside the file; the one outside reference is selftest.ps1:1240-1243 (dot-source plus Test-ScaffoldValidationExamples in gate 1h). Invoke-SelftestTaskSelection calls Invoke-SelftestAll and New-SelftestSnapshot, which no tracked file defines. No doc teaches [SELFTEST-TASK-ROUTE] or [SELFTEST-NOT-APPLICABLE]. The two uncalled functions and scripts/fixtures/gate2/ moved to T0-SCRIPTS-ORPHAN-FUNCS on 2026-11-16."
forbid:
  - Changing any function that stays, any gate's behaviour other than gate 1h no longer loading _validation.ps1, or any message a remaining check prints
  - Deleting code that came from the upstream scaffold in PR 297 (reported upstream instead; see docs/SCAFFOLD-SYNC.md)
  - Deleting _validation.ps1 in parts; the file goes in one change (see Split)
non_goals:
  - Add-GradleLicenseFinding, Get-WorkflowActiveStepIndices and scripts/fixtures/gate2/ (T0-SCRIPTS-ORPHAN-FUNCS)
  - The 13 unread Prereview* knobs (TD192) and the card checks check-cards.ps1 keeps off (TD193)
  - Historical mentions in CLAUDE.md, docs/TASK-BOARD.md, the ledger and archived cards
acceptance:
  - "A1 scripts/_validation.ps1 is deleted. selftest.ps1 gate 1h no longer dot-sources it or calls Test-ScaffoldValidationExamples, and still runs the examples of the two other local cores it loads there (_symbol-markdown.ps1, _unicode.ps1)."
  - "A2 No tracked file under scripts, .github or .claude names _validation.ps1, Invoke-SelftestTaskSelection, Resolve-SelftestTaskRoute, Resolve-SelftestRiskRoute or Test-ScaffoldValidationExamples, and selftest.ps1 parses with no errors."
  - "A3 selftest -Only 1 passes, and the Tier-S full selftest passes on the shipped candidate. Evidence form: the implementation record carries a full run on a commit C (launch and end HEAD, an empty git status --porcelain --untracked-files=all, each shard's exit and [GATE-RESULT-SUMMARY] failed=none, selftest: PASS), and git diff --stat C HEAD lists only this card; a commit cannot carry the output of a run on itself, and the reviewer reads only the diff (L227)."
dod_command: $ErrorActionPreference = 'Stop'; $e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath 'scripts/selftest.ps1').Path, [ref]$null, [ref]$e); if ($e -and $e.Count) { Write-Host '[DOD-FAIL] scripts/selftest.ps1 does not parse'; exit 1 }; if (Test-Path -LiteralPath 'scripts/_validation.ps1') { Write-Host '[DOD-FAIL] scripts/_validation.ps1 still exists'; exit 1 }; $hits = @(& git grep -n -e '_validation\.ps1' -e 'Invoke-SelftestTaskSelection' -e 'Resolve-SelftestTaskRoute' -e 'Resolve-SelftestRiskRoute' -e 'Test-ScaffoldValidationExamples' -- scripts .github .claude); if ($hits.Count) { $hits | ForEach-Object { Write-Host $_ }; Write-Host '[DOD-FAIL] a removed name is still referenced'; exit 1 }; $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 1 2>&1 | Out-String); if ($LASTEXITCODE -ne 0) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 3000))); Write-Host '[DOD-FAIL] selftest -Only 1'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: selftest.ps1 parses, the module is gone, no removed name is referenced under scripts, .github or .claude, and selftest gate 1 passes; prints [DOD-PASS]. On base it exits 1 because scripts/_validation.ps1 exists.
review_gate: codex {verdict:pass}
budget: 560
hygiene: Deletion only, so R4 has no new assertion to prove. R4 instead restores the module with its gate 1h hook, and the gate 1h hook alone, and records in the R5 note that the DoD fails on each restore, which shows the DoD checks the deletion.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-SCRIPTS-ORPHAN-CLEANUP

From the 2026-10-14 unused-code audit (plan: `docs/plans/PLAN-AUDIT-CLEANUP-20261014.md`, aidlc goal
`g-20261014091214-314b46`). The user asked that day to fix every audit finding and delegated the decisions.

## Why it goes

`scripts/_validation.ps1` is the selftest risk router `T0-SELFTEST-RISK-ROUTING` added in PR #273. PR #297
replaced `selftest -TaskId` with the upstream tier routing (`[SELFTEST-TIER-PASS]`), and since then nothing
outside the file calls any of its 21 functions. Gate 1h still loads it to run its examples, so the module was
tested but never used. Its entry point calls two functions that no tracked file defines, so it could not run.

## Split (2026-11-16)

On 2026-11-16 the user set a rule: a card changes fewer than 200 lines, or it is broken into two cards. The
first implementation of this card (branch head 7bfcb2c3) changed 608 lines, 502 of them `_validation.ps1`.
The two uncalled functions and `scripts/fixtures/gate2/` (75 deleted lines) moved to `T0-SCRIPTS-ORPHAN-FUNCS`.

This card stays at about 504 deleted lines, above 200, as a stated exception. Keeping every card under 200 would
take three cards for this one file: the `-SelfCheck` block and its fixture helpers (lines 274 to 485 and 502),
the functions `Test-ScaffoldValidationExamples` does not reach, then the rest with the gate 1h hook. Each would be
Tier S (full selftest plus R3) and each review would check the same fact, that nothing outside the file calls it,
while the file stays dead between them. The user delegated the choice the same day ("ask Fable 5.2 for consults
or pick answers"); a Fable 5.2 consult also recommended two cards.

## Tier

`scripts/selftest.ps1` is a TierSPaths entry, so the card is Tier S and needs the full selftest on the shipped
candidate (A3).
