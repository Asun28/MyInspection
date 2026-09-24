---
id: T0-POST-MERGE-R5-GUARDS
title: Harden post-merge.ps1 r5 with a main-table board rule, a wiring self-check and -DryRun
status: todo
depends_on: [T0-POST-MERGE-DOCS-PR]
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - .claude/skills/task-loop/SKILL.md
  - specs/tasks/T0-POST-MERGE-R5-GUARDS.md
forbid:
  - Loosening any guard T0-POST-MERGE-DOCS-PR delivered (its A1, A2 and A4) or widening the direct-merge allowlist
  - Changing scripts/_guard.ps1, scripts/_ci.ps1, scripts/task.ps1 or any existing gate
non_goals:
  - The lessons PR (T0-POST-MERGE-LESSONS)
  - Reading the board's column layout from anywhere but the table header the row sits under
acceptance:
  - "A1 r5 edits a docs/TASK-BOARD.md row only in the main card table: the header of the table the row sits in has '卡 id' as its second cell and '卡片状态 / 备注' as its last, a '---' separator line follows that header, and the row has the header's number of cells. A card id in the second column of another board table (such as the '原产品卡' or 'Card' tables) does not count as the card's row. Each case fails closed with [POST-MERGE-ANCHOR]"
  - "A2 post-merge.ps1 -SelfCheck also checks the wiring: after loading _guard.ps1 and _ci.ps1, every Verb-Noun command in post-merge.ps1 resolves, every named parameter it passes exists on that command, and every Scaffold* variable it reads is defined"
  - "A3 r5 -DryRun builds and checks the same change as a real run (edits, allowlist judge, check-cards, check-secrets), prints its diff, pushes nothing, and removes its worktree and local branch; the task-loop skill's R5 step says to preview with -DryRun"
  - "A4 Each new guard has a single-statement mutation that fails a named SelfCheck case, recorded in this card, and one real -DryRun on a card that is not yet merged is recorded here with its diff summary"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck passes against the production functions, including the main-table cases and the wiring check, and prints [POST-MERGE-SELF-CHECK-PASS]
review_gate: codex {verdict:pass}
budget: 250
hygiene: single-statement mutations for each main-table guard (second cell, last cell, separator, cell count, the rule itself) and for the wiring check's command, parameter and variable arms, file restored by SHA-256
doc_sync: TASK-BOARD
---

# T0-POST-MERGE-R5-GUARDS

Split out of `T0-POST-MERGE-DOCS-PR` by user decision on 2026-09-25. DeepSeek V4 Flash pre-review round 1 of that
card raised three points the user chose to ship as a follow-up rather than fold into it:

1. The board editor rewrites the last cell of the one row whose second cell is the card id. The main table's last
   column is the status, but two other board tables also carry card ids in their second column, where the last
   column means something else (A1).
2. `-SelfCheck` never exercised the git/gh plumbing's dependencies, so a renamed helper in `_guard.ps1` or
   `_ci.ps1` would leave the DoD green while every live run failed (A2).
3. A preview mode that builds and checks the change without pushing makes the prose placement visible before a
   PR exists (A3).

A draft of all three was written and passed its self-check while `T0-POST-MERGE-DOCS-PR` was in progress; it is
reapplied here on top of that card once it has merged. Estimate: about 150 changed lines.
