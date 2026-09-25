---
id: T0-POST-MERGE-R5-BOARD-TABLE
title: post-merge.ps1 r5 edits a card's board row only in a main card table
status: todo
depends_on: [T0-POST-MERGE-DOCS-PR]
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - specs/tasks/T0-POST-MERGE-R5-BOARD-TABLE.md
forbid:
  - Loosening any guard T0-POST-MERGE-DOCS-PR delivered (its A1, A2 and A4) or widening the direct-merge allowlist
  - Changing scripts/_guard.ps1, scripts/_ci.ps1, scripts/task.ps1 or any existing gate
non_goals:
  - The wiring self-check (T0-POST-MERGE-R5-WIRING) and -DryRun (T0-POST-MERGE-R5-GUARDS)
  - Reading the board's column layout from anywhere but the table header the row sits under
acceptance:
  - "A1 r5 edits a docs/TASK-BOARD.md row only in a main card table: the header of the table the row sits in has '卡 id' as its second cell and '卡片状态 / 备注' as its last, a '---' separator line follows that header, and the row has the header's number of cells. The board has two tables with that header; a row in either counts. A card id in the second column of another board table (such as the '原产品卡' or 'Card' tables) does not count as the card's row. Each case fails closed with [POST-MERGE-ANCHOR]"
  - "A2 Each A1 guard (second cell, last cell, separator, cell count, and the rule's use in the row search) has a single-statement mutation that fails a named SelfCheck case, recorded in this card with that case, the file restored by SHA-256"
  - "A3 The rule changes no result on today's board: on origin/master's docs/TASK-BOARD.md, for every card id in the second column of a main card table, the new row search finds the same single row as the old one, checked with the production functions and recorded here"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck passes against the production functions, including the main-table cases, and prints [POST-MERGE-SELF-CHECK-PASS]
review_gate: codex {verdict:pass}
budget: 150
hygiene: single-statement mutations for each main-table guard (second cell, last cell, separator, cell count, the rule itself), file restored by SHA-256
doc_sync: TASK-BOARD
---

# T0-POST-MERGE-R5-BOARD-TABLE

First of three PRs split from `T0-POST-MERGE-R5-GUARDS` (its A1) on 2026-09-25, when the user asked for that
card's three fixes to be delivered as separate PRs. The next two are `T0-POST-MERGE-R5-WIRING` and the narrowed
`T0-POST-MERGE-R5-GUARDS`; they touch the same file, so they run one after another.

The board editor rewrites the last cell of the one row whose second cell is the card id. The last column of the
main card tables is the status, but other board tables also carry card ids in their second column, where the last
column means something else. The allowlist judge reads `git diff -U0` hunks, which carry no table context, so it
keeps identifying the row by its second cell; the editor is the only writer of the board line.

R3: Opus 5.5 through `ReviewCommand` instead of Codex (user ruling 2026-09-25).
