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
  - "A3 On origin/master's docs/TASK-BOARD.md the new row search finds exactly one row for every card id in the second column of a main card table: the old search's row wherever the old search found one, and the main-table row for each id the old search found more than once (listed here). Checked with the production functions and recorded here"
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

## Implementation record

- RED: the rebuilt and new board cases were written first. Against the unchanged functions 6 of 73 SelfCheck cases
  failed on their own assertions: the positive case threw `[POST-MERGE-ANCHOR] ... has 2 rows for T9-DEMO` and the
  five negative cases did not throw. GREEN: 73 of 73.
- `Test-PostMergeMainTableRow` takes the run of `|` lines around the row as its table and checks the run's first
  line as the header. It compares the cell counts first, so `$head[1]` stays in range under StrictMode.
- R4 (A2): 9 single-statement mutants, B8 and B9 added after the pre-review (below). Each made `-SelfCheck` exit 1
  with its named case failing and no parse error; the file was restored after each and ended at SHA-256
  `4A3E0C111153EA7A2EF7F873435FEBFE8B21DC3B56DC7FA41760ED61ECF9E989`.

| id | statement | mutation | killing case |
|---|---|---|---|
| B1 | `$head[1] -ceq '卡 id' -and ` | (deleted) | board: a table whose second header cell is not 卡 id |
| B2 | ` -and $head[-1] -ceq '卡片状态 / 备注'` | (deleted) | board: a table whose last header cell is not the status |
| B3 | ` -and $Lines[$top + 1] -cmatch '^\\|(\s*:?-{3,}:?\s*\\|)+\s*$'` | (deleted) | board: a table without a separator line |
| B4 | `$head.Count -eq @(Get-PostMergeCells $Lines[$Index]).Count -and ` | (deleted) | board: a row whose cell count differs from its header |
| B5 | ` -and (Test-PostMergeMainTableRow $lines $i)` | (deleted) | board: a row in another table is not the card row |
| B6 | `$Lines[$top - 1].StartsWith('\|')` | `$true` | board: only the card row changes |
| B7 | `$Lines[$top - 1].StartsWith('\|')` | `$false` | board: only the card row changes |
| B8 | `return ($head.Count -eq @(Get-PostMergeCells $Lines[$Index]).Count -and $head[1] -ceq '卡 id' -and $head[-1] -ceq '卡片状态 / 备注' -and` | `return ($head[1] -ceq '卡 id' -and $head[-1] -ceq '卡片状态 / 备注' -and $head.Count -eq @(Get-PostMergeCells $Lines[$Index]).Count -and` | board: a header narrower than the row |
| B9 | `$top -gt 0 -and ` | (deleted) | board: a board that opens with its table and has no final newline |

- A3: on origin/master `354a45d4` (the board is unchanged at `ae731205`), 221 main-table cells equal a card id. For
  217 of them both searches find the same single row. The other four ids are also in the second column of another
  table, so the old search found two rows and r5 would have stopped; the new search finds the main-table row:
  `T1-STORAGE-PATH-BOUNDARY` (line 157; old also 431), `T3-PDF-TYPOGRAPHY-CONTRACT` (212; 432) and
  `T3-PDF-PAGINATION-FIXTURES` (213; 433) under the `远端交付卡 | 原产品卡` header at line 429, and
  `T3-PDF-MEASUREMENT-REQUESTS` (214; 442) under `Round | Card` at line 439. One edit through
  `Set-PostMergeBoardStatus` on that board (`T0-TOOLCHAIN`) changed line 34 only.
- [FOLLOW-UP] 34 more main-table cells hold a card id with decoration: a `★` suffix (10), strikethrough (5), or a
  markdown link (19, including every row of the second main table at lines 382-399). Both searches find no row for
  these, before and after this change, so r5 stops with `[POST-MERGE-ANCHOR]` for them; 11 are live
  `T0-PREREVIEW-*` cards. Accepting decorated ids would change `Test-PostMergeBoardRow`, which the allowlist judge
  shares, so it is left to its own card. Today only the SelfCheck fixture exercises a row in the second main table.
- After a fresh-context pre-review, two cases were added to pin behaviour the first batch did not reach: a header
  narrower than the row (B8, the count comparison must come first under StrictMode) and a board that opens with its
  table and has no final newline (B9, the walk up must stop at line 0). SelfCheck: 75 of 75.
