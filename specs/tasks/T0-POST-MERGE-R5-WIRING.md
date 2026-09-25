---
id: T0-POST-MERGE-R5-WIRING
title: post-merge.ps1 -SelfCheck checks the commands, parameters and variables its plumbing uses
status: todo
depends_on: [T0-POST-MERGE-R5-BOARD-TABLE]
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - specs/tasks/T0-POST-MERGE-R5-WIRING.md
forbid:
  - Loosening any guard T0-POST-MERGE-DOCS-PR or T0-POST-MERGE-R5-BOARD-TABLE delivered, or widening the direct-merge allowlist
  - Changing scripts/_guard.ps1, scripts/_ci.ps1, scripts/task.ps1 or any existing gate
non_goals:
  - -DryRun (T0-POST-MERGE-R5-GUARDS)
  - Running the git or gh plumbing itself inside -SelfCheck
acceptance:
  - "A1 post-merge.ps1 -SelfCheck also checks the wiring: after loading _guard.ps1 and _ci.ps1, every Verb-Noun command in post-merge.ps1 resolves, every named parameter it passes exists on that command, and every Scaffold* variable it reads is defined"
  - "A2 The check reports each of the three kinds on a small script text that has one defect of each kind, so each arm has a single-statement mutation that fails a named SelfCheck case; one more mutation renames a helper post-merge.ps1 calls and fails the check on the file itself. All are recorded in this card with their killing cases, the file restored by SHA-256"
  - "A3 A failing check names every unresolved command, command parameter and variable in one message, so one run shows every break"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck passes against the production functions, including the wiring check on post-merge.ps1 itself and on the defect script text, and prints [POST-MERGE-SELF-CHECK-PASS]
review_gate: codex {verdict:pass}
budget: 120
hygiene: single-statement mutations for the wiring check's command, parameter and variable arms, and one renamed helper, file restored by SHA-256
doc_sync: TASK-BOARD
---

# T0-POST-MERGE-R5-WIRING

Second of three PRs split from `T0-POST-MERGE-R5-GUARDS` (its A2) on 2026-09-25, when the user asked for that
card's three fixes to be delivered as separate PRs.

`-SelfCheck` covers the pure functions only, so a renamed helper or parameter in `_guard.ps1` or `_ci.ps1`
leaves the DoD green while every live r5 or prune fails. This card makes the SelfCheck resolve what the plumbing
calls, without running it.

R3: Opus 5.5 through `ReviewCommand` instead of Codex (user ruling 2026-09-25).
