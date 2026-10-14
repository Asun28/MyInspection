---
id: T0-SCRIPTS-ORPHAN-CLEANUP
title: Delete the local scaffold code that nothing calls since PR #297, and the gate2 fixture nothing reads
status: todo
depends_on: []
parallelizable_with: [T2-PHOTO-INVARIANT-CLEANUP]
allow_paths:
  - scripts/_validation.ps1
  - scripts/selftest.ps1
  - scripts/check-licenses.ps1
  - scripts/license-scanner-check.ps1
  - scripts/fixtures/gate2/
  - specs/tasks/T0-SCRIPTS-ORPHAN-CLEANUP.md
sweep: "2026-10-14 audit on 01a2e801: an AST call graph over every tracked .ps1, then git grep -w for each removed name over scripts, .github, .claude and specs/tasks. scripts/_validation.ps1: its 21 functions are called only from inside the file; the one outside reference is selftest.ps1:1240-1243 (dot-source plus Test-ScaffoldValidationExamples in gate 1h). Invoke-SelftestTaskSelection calls Invoke-SelftestAll and New-SelftestSnapshot, which no tracked file defines. check-licenses.ps1:638 Add-GradleLicenseFinding and license-scanner-check.ps1:436 Get-WorkflowActiveStepIndices: definition only; Get-WorkflowActiveStepRecords keeps its callers at :383 and :761. scripts/fixtures/gate2/: no script names it; only archived T3-E2E-* cards and LEDGER L-entry refs, which stay as history. No doc teaches [SELFTEST-TASK-ROUTE] or [SELFTEST-NOT-APPLICABLE]."
forbid:
  - Changing any function that stays, any gate's behaviour other than gate 1h no longer loading _validation.ps1, or any message a remaining check prints
  - Deleting code that came from the upstream scaffold in PR #297 (reported upstream instead; see docs/SCAFFOLD-SYNC.md)
  - Editing docs/LICENSE-POLICY.md; the check-licenses.ps1 change removes an uncalled function only, so its commit carries [doc-sync:none]
non_goals:
  - The 13 unread Prereview* knobs (TD192) and the card checks check-cards.ps1 keeps off (TD193)
  - Historical mentions in CLAUDE.md, docs/TASK-BOARD.md, the ledger and archived cards
acceptance:
  - "A1 scripts/_validation.ps1 is deleted. selftest.ps1 gate 1h no longer dot-sources it or calls Test-ScaffoldValidationExamples, and still runs the examples of the two other local cores it loads there (_symbol-markdown.ps1, _unicode.ps1)."
  - "A2 check-licenses.ps1 no longer defines Add-GradleLicenseFinding and license-scanner-check.ps1 no longer defines Get-WorkflowActiveStepIndices; nothing else in either file changes."
  - "A3 scripts/fixtures/gate2/ is deleted."
  - "A4 No tracked file under scripts, .github or .claude names _validation.ps1, fixtures/gate2, Invoke-SelftestTaskSelection, Resolve-SelftestTaskRoute, Resolve-SelftestRiskRoute, Test-ScaffoldValidationExamples, Add-GradleLicenseFinding or Get-WorkflowActiveStepIndices, and the three edited scripts parse with no errors."
  - "A5 selftest -Only 1 passes, and the Tier-S full selftest passes on the shipped candidate."
dod_command: $ErrorActionPreference = 'Stop'; foreach ($f in @('scripts/selftest.ps1', 'scripts/check-licenses.ps1', 'scripts/license-scanner-check.ps1')) { $e = $null; [void][System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $f).Path, [ref]$null, [ref]$e); if ($e -and $e.Count) { Write-Host "[DOD-FAIL] $f does not parse"; exit 1 } }; foreach ($p in @('scripts/_validation.ps1', 'scripts/fixtures/gate2')) { if (Test-Path -LiteralPath $p) { Write-Host "[DOD-FAIL] $p still exists"; exit 1 } }; $hits = @(& git grep -n -e '_validation\.ps1' -e 'fixtures/gate2' -e 'Invoke-SelftestTaskSelection' -e 'Resolve-SelftestTaskRoute' -e 'Resolve-SelftestRiskRoute' -e 'Test-ScaffoldValidationExamples' -e 'Add-GradleLicenseFinding' -e 'Get-WorkflowActiveStepIndices' -- scripts .github .claude); if ($hits.Count) { $hits | ForEach-Object { Write-Host $_ }; Write-Host '[DOD-FAIL] a removed name is still referenced'; exit 1 }; $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 1 2>&1 | Out-String); if ($LASTEXITCODE -ne 0) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 3000))); Write-Host '[DOD-FAIL] selftest -Only 1'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The three edited scripts parse, the module and the fixture are gone, no removed name is referenced under scripts, .github or .claude, and selftest gate 1 passes; prints [DOD-PASS]. On base it exits 1 because scripts/_validation.ps1 exists.
review_gate: codex {verdict:pass}
budget: 700
hygiene: Deletion only, so R4 has no new assertion to prove. R4 instead restores each deleted unit one at a time (the module with its gate 1h hook, each function, the fixture) and records in the R5 note that the DoD fails on each restore, which shows the DoD checks every deletion.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-SCRIPTS-ORPHAN-CLEANUP

From the 2026-10-14 unused-code audit (plan: `docs/plans/PLAN-AUDIT-CLEANUP-20261014.md`, aidlc goal
`g-20261014091214-314b46`). The user asked that day to fix every audit finding and delegated the decisions.

## Why each piece goes

- `scripts/_validation.ps1` is the selftest risk router `T0-SELFTEST-RISK-ROUTING` added in PR #273. PR #297
  replaced `selftest -TaskId` with the upstream tier routing (`[SELFTEST-TIER-PASS]`), and since then nothing
  outside the file calls any of its 21 functions. Gate 1h still loads it to run its examples, so the module was
  tested but never used. Its entry point calls two functions that no tracked file defines, so it could not run.
- `Add-GradleLicenseFinding` lost its caller in #297, and the nested `Get-WorkflowActiveStepIndices` lost its
  caller in a later local change. Both are local code.
- `scripts/fixtures/gate2/` held the fake Gradle wrappers for the gate 2 fixture. Selftest now writes its own stubs
  (`Set-ProjectGate2Stub15`), and no script reads this directory.

## Tier

`scripts/check-licenses.ps1` and `scripts/selftest.ps1` are TierSPaths, so the card is Tier S and needs the full
selftest on the shipped candidate (A5).
