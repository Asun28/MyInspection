---
id: T0-DECOMPOSE-NULL-GUARDS
title: Stop decompose-cards.mjs from crashing on a null Decompose result and from reading a missing card-audit angle as a clean one
status: todo
depends_on:
  - T0-PLAN-FORGE-FOLLOWUP
parallelizable_with: []
branch: T0-DECOMPOSE-NULL-GUARDS
worktree: C:\wt\T0-DECOMPOSE-NULL-GUARDS
allow_paths:
  - .claude/workflows/decompose-cards.mjs
  - scripts/selftest.ps1
  - docs/PLAN-FORGE.md
forbid:
  - Changing any prompt text in decompose-cards.mjs (T0-PLAN-FORGE-FOLLOWUP owns it), the LENSES set, the schemas or the cardaudit labels
  - Loosening, removing or renumbering any existing gate-1 sub-gate, or changing plan-forge.mjs
  - Treating a missing agent result as a clean one on any path
non_goals:
  - The same guards in scout-options.mjs
  - Retrying a null agent, or adding a verdict field to decompose-cards
  - Any other PLAN-FORGE.md rewording (T0-PLAN-FORGE-FOLLOWUP owns it)
acceptance:
  - "A1 When the Decompose agent returns null (skipped, refused, or no structured output), decompose-cards.mjs does not throw: it logs that the decomposition returned no result, requests no Card-Audit agent, and returns every usual key with cards [], freeze_point null, topo_valid false, parallel_window null, audit_issues [], fatal_count 0 and high_count 0, plus decompose_skipped true and skipped_audits []."
  - "A2 A Card-Audit agent that returns null is listed by its label (cardaudit<N>) in skipped_audits, gets one HIGH audit issue naming that label (the angle did not run; re-run decompose-cards), is counted in high_count, and is named in one log line. It is never read as an angle that found nothing."
  - "A3 A run where every agent returns a result is unchanged: one Decompose and one Card-Audit per LENSES entry, the same values for the existing keys, and decompose_skipped false with skipped_audits []. decompose_skipped and skipped_audits are present on every return path, never omitted."
  - "A4 New sub-gate 1j in scripts/selftest.ps1 drives decompose-cards.mjs under stub agent/parallel/log, the way 1i drives plan-forge.mjs, through the three runs in A1-A3. It reads the LENSES length from the file rather than a literal, accumulates every failed arm, and prints the ASCII sentinel [DECOMPOSE-NULL-GUARD-OK] only when all arms hold."
  - "A5 docs/PLAN-FORGE.md says in one sentence that a Decompose or card-audit agent returning nothing is reported through decompose_skipped and skipped_audits, never read as a clean result."
  - "A6 Each hygiene mutant turns 1j red, and the Tier-S acceptance run selftest.ps1 -TaskId T0-DECOMPOSE-NULL-GUARDS (the full suite) passes on the shipped candidate."
dod_command: $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 1 2>&1 | Out-String); $code = $LASTEXITCODE; if ($code -ne 0) { Write-Host (@($o -split "`n" | Select-Object -Last 20) -join "`n"); Write-Host "[DOD-FAIL] selftest -Only 1 exit $code"; exit 1 }; if (-not $o.Contains('[DECOMPOSE-NULL-GUARD-OK]')) { Write-Host '[DOD-FAIL] sub-gate 1j did not print [DECOMPOSE-NULL-GUARD-OK]'; exit 1 }; $pf = Get-Content -Raw -LiteralPath docs/PLAN-FORGE.md -Encoding utf8; foreach ($k in @('decompose_skipped', 'skipped_audits')) { if (-not $pf.Contains($k)) { Write-Host "[DOD-FAIL] docs/PLAN-FORGE.md does not name $k"; exit 1 } }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: selftest -Only 1 exits 0 and prints [DECOMPOSE-NULL-GUARD-OK] from sub-gate 1j (A1-A4), and docs/PLAN-FORGE.md names decompose_skipped and skipped_audits (A5); prints [DOD-PASS]. On base 1j does not exist, so it exits 1 with [DOD-FAIL] sub-gate 1j did not print [DECOMPOSE-NULL-GUARD-OK].
review_gate: codex {verdict:pass}
budget: 250
hygiene: R4 runs single-statement mutants, each recorded in the R5 note with its 1j failure - delete the Decompose null guard, let the null-Decompose path still request audits, drop the HIGH issue for a missing audit angle, drop skipped_audits from the normal return; every one must turn 1j red and the dod_command exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row, and ask the user whether to report the decompose-cards defect upstream.
---

# T0-DECOMPOSE-NULL-GUARDS

Opened by user decision on 2026-09-25. Found during the sweep for `T0-PLAN-FORGE-FOLLOWUP`.

## Problem (read on master `e064b828`)

- `const decomp = await agent(...)` is followed straight away by `decomp.cards`, `decomp.freeze_point` and
  `decomp.topo_valid` in the log line and in the return. `agent()` returns null for a skipped or refused agent,
  or one that ended without structured output, so one such result throws and loses the whole run. Gate 1a
  guards the same pattern in plan-forge (TD52); nothing guards it here.
- `audits.filter(Boolean)` drops a null card-audit result before counting issues. A missing angle then reads
  as an angle that found nothing: the return carries no field saying it did not run, and fatal_count and
  high_count are lower than they should be. `T0-OPUS55-PROMPT-FIT` (#334) fixed the same fail-open for plan-forge
  lenses with skipped_lenses and a HIGH correction per missing lens.

## Order

Depends on `T0-PLAN-FORGE-FOLLOWUP`, which edits the prompt text of the same file. Ship that first, then this.

## Evidence at registration

- Card tier S (`scripts/selftest.ps1` is in TierSPaths), so the acceptance run is the full suite.
- The dod_command exits 1 on base `e064b828` in 107 s: `selftest -Only 1` passes there, and the DoD then
  stops at `[DOD-FAIL] sub-gate 1j did not print [DECOMPOSE-NULL-GUARD-OK]`. Sub-gate id 1j is unused on base.

## Acceptance

```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: see `dod_assert`; the closed list is `acceptance:` above.
