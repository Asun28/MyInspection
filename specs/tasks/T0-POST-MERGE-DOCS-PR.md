---
id: T0-POST-MERGE-DOCS-PR
title: Automate the post-merge R5 doc-sync PR and the pruning of merged remote branches
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - CLAUDE.md
  - .claude/skills/task-loop/SKILL.md
  - docs/DEVOPS-WORKFLOW.md
  - specs/tasks/T0-POST-MERGE-DOCS-PR.md
forbid:
  - Merging a PR whose diff falls outside the A2 allowlist, or letting this script open, review or merge a card's own ship PR
  - Deleting a remote branch that fails any A4 check, or any local branch or worktree other than the script's own temporary ones
  - Changing scripts/task.ps1, scripts/review.ps1, scripts/_config.ps1 or any existing gate
  - Writing to the main checkout's working tree or to local master
non_goals:
  - Writing the R5 prose; the caller supplies the board status text, the CLAUDE.md entry and the card's R5 section
  - The lessons PR (T0-POST-MERGE-LESSONS)
  - Archiving merged cards (scripts/archive.ps1 already does that)
acceptance:
  - "A1 post-merge.ps1 r5 builds the doc sync in a new worktree cut from origin/<base>: the card's status becomes merged, the card's docs/TASK-BOARD.md row gets the given status cell, the given entry is inserted directly under '## 当前阶段' in CLAUDE.md, and the given R5 section is appended to the card. Each edit fails closed with a named sentinel when its anchor is missing or not unique"
  - "A2 Direct-merge allowlist (user ruling 2026-09-25): before any push the changed paths must be a subset of the card file, docs/TASK-BOARD.md and CLAUDE.md; the TASK-BOARD change must be exactly the card's own row; the CLAUDE.md change must be added lines only, all between '## 当前阶段' and the next '## ' heading. Anything else stops with [POST-MERGE-SCOPE] and nothing is pushed"
  - "A3 The merge happens only after check-cards and check-secrets pass on the new worktree, Assert-PersonalAccount passes, the PR's 'required' check succeeds and the ci.yml run for it has head_sha equal to the pushed head, and the PR base is the expected base; it uses gh pr merge --squash --match-head-commit. No R3 runs for this docs PR. The commit message carries no AI attribution"
  - "A4 post-merge.ps1 prune deletes a remote branch only when exactly one PR has it as head, that PR is MERGED, and the remote tip equals the PR's headRefOid, using git push --force-with-lease=refs/heads/<branch>:<oid>. Every other state (open, closed unmerged, tip moved, no PR, several PRs) is reported and kept. r5 prunes its own branch this way after its merge"
  - "A5 post-merge.ps1 -SelfCheck covers the edit functions, the allowlist judge and the prune decision with positive and negative cases, and prints [POST-MERGE-SELF-CHECK-PASS]; each guard has a single-statement mutation that makes the SelfCheck exit non-zero, recorded in the card"
  - "A6 CLAUDE.md's execution boundary records both standing permissions (the A2 direct-merge scope and the A4 prune rule) with their limits; the task-loop skill's R5 step and docs/DEVOPS-WORKFLOW.md point at post-merge.ps1"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck runs every case against the production functions and prints [POST-MERGE-SELF-CHECK-PASS]; the RED is a SelfCheck whose cases fail against stub functions, not a missing file
review_gate: codex {verdict:pass}
budget: 700
hygiene: single-statement mutations for each guard in A2 and A4 and for each anchor check in A1, run against the SelfCheck with the file restored by SHA-256
doc_sync: TASK-BOARD, CLAUDE.md
---

# T0-POST-MERGE-DOCS-PR

Opened by user request on 2026-09-25, after `T0-REVIEW-GOVERNING-DOCS` was delivered by hand: its R5 doc sync
(#349), the pruning of six merged remote branches, and the lessons PR (#350) were all manual steps that followed
the same pattern.

## User rulings (2026-09-25)

- Post-merge docs PRs merge on CI alone, without R3, but only inside a strict allowlist (A2).
- Remote branches may be deleted without asking only when the PR is merged and the head still matches (A4).
- The automation lives in a new `scripts/post-merge.ps1`; `scripts/task.ps1` is not changed.
- R3 for this card: Codex if its quota is back when the card ships, otherwise Opus 5.5 through `ReviewCommand`.

## Design notes

- The script never touches the main checkout: every edit happens in a temporary worktree cut from
  `origin/<base>`, which is removed at the end, as are its local branch and its remote branch (A4).
- The CI check is lighter than the ship's: the PR's `required` check on the exact head plus the ci.yml run's
  head SHA. That matches what was done by hand for #349 and #350.
- Estimate: about 500 changed lines (script with SelfCheck about 380, docs about 20, this card about 100).

## Implementation record (2026-09-25)

- Change: a new `scripts/post-merge.ps1` with `r5`, `prune` and `-SelfCheck`. `CLAUDE.md`'s R5 bullet names the
  script, and its execution boundary records the prune exception and the direct-merge scope (A6). The task-loop
  skill's R5 step and the post-merge command block in `docs/DEVOPS-WORKFLOW.md` point at it.
- RED (A5): the SelfCheck was written first against one-line stubs. `task.ps1 -Phase red` exited 1, and the
  SelfCheck printed `[POST-MERGE-SELF-CHECK-FAIL] 42 of 42 cases failed`, each on its own assertion. A first
  attempt crashed on a stub called outside a case; that was fixed before the receipt was taken.
- GREEN: `-SelfCheck` passes 60 cases and prints `[POST-MERGE-SELF-CHECK-PASS]`.
- R4 (A5): 42 single-statement mutants, listed below with the line each changes in the final file (SHA-256
  `71A21B8EB65CF3175A122CC51B7A362E037D561DD49DD47250234390E6875128`). Each made `-SelfCheck` exit 1 by failing
  the case named for it, and none failed by a parse error. The file was restored and its SHA-256 checked after
  each. The runner first rewrites the unmutated file through the same write path and requires identical bytes
  and a passing SelfCheck; an earlier batch without that control was void, because it wrote a second BOM.

| id | line | mutation | killing case |
|---|---|---|---|
| M1 | 50 | loop bound `$end` → `$lines.Count` | card: front-matter status becomes merged |
| M2 | 51 | condition → `$false` | card: two status lines |
| M3 | 52 | condition → `$false` | card: already merged |
| M4 | 46 | condition → `$false` | card: a later --- rule is not front matter |
| M5 | 49 | condition → `$false` | card: front matter not closed |
| M6 | 68 | ordinal equality → `StartsWith` | board: a longer id is not the card row |
| M7 | 68 | `Ordinal` → `OrdinalIgnoreCase` | board: the id cell is compared exactly |
| M8 | 72 | condition → `$false` | board: status with a pipe |
| M9 | 76 | condition → `$false` | board: two rows |
| M10 | 91 | condition → `$false` | stage: heading twice |
| M11 | 87 | condition → `$false` | stage: entry with a heading line |
| M12 | 111 | drop `$null -eq $hunk -and` | diff: a removed line starting with -- is content, not a header |
| M13 | 133 | condition → `$false` | scope: nothing changed |
| M14 | 135 | condition → `$false` | scope: a path outside the allowlist |
| M15 | 135 | `Ordinal` → `OrdinalIgnoreCase` | scope: path case is compared exactly |
| M16 | 139 | condition → `$false` | scope: another card's board row |
| M16b | 139 | delete both count checks | scope: an extra board row |
| M17 | 148 | condition → `$false` | scope: a CLAUDE.md line removed |
| M18 | 149 | condition → `$false` | scope: a CLAUDE.md line added outside the section |
| M18b | 149 | delete the `-ge $next` half | scope: a CLAUDE.md line added outside the section |
| M18c | 149 | delete the `-le $heads[0]` half | scope: a line added above the heading |
| M19 | 144 | condition → `$false` | scope: CLAUDE.md without the stage heading |
| M20 | 162 | condition → `$false` | prune: an open PR is kept |
| M21 | 163 | condition → `$false` | prune: a moved tip is kept |
| M22 | 160 | condition → `$false` | prune: two PRs are kept |
| M23 | 159 | condition → `$false` | prune: no PR is kept |
| M24 | 157 | condition → `$false` | prune: no remote branch is kept |
| M25 | 158 | condition → `$false` | prune: a short tip is kept |
| M26 | 58 | condition → `$false` | card: section without a heading |
| M27 | 66 | condition → `$false` | board: a prose line carrying the id is not a row |
| M28 | 68 | delete `$cells.Count -ge 3 -and` | board: a two-cell row is not a row |
| M29 | 139 | delete `$rem.Count -ne 1 -or` | scope: two rows removed and one added |
| M30 | 139 | delete `$add.Count -ne 1 -or` | scope: an extra board row |
| M31 | 139 | delete the `$rem[0]` row check | scope: another card's row rewritten into this card's row |
| M32 | 139 | delete the `$add[0]` row check | scope: this card's row rewritten into another card's row |
| M33 | 149 | `-ge $next` → `-gt $next` | scope: an added line that is the next heading |
| M34 | 191 | also accept NEUTRAL and SKIPPED | ci: a NEUTRAL fan-in check is not success |
| M35 | 201 | condition → `$false` | ci: a failed ci.yml run is failure |
| M36 | 199 | drop the `headSha` filter | ci: a run for another commit is ignored |
| M37 | 200 | drop `status -ceq 'completed'` | ci: an in-progress run is pending |
| M38 | 171 | condition → `$false` | ci: a pending CheckRun is not failure |
| M39 | 177 | state check → always `pending` | ci: a StatusContext ERROR is failure |

- A4 on real GitHub state: `prune` kept a missing branch ("no such remote branch") and the branch of open PR #323
  ("PR #323 is OPEN", tip unchanged). It deleted `register-T0-POST-MERGE-R5-GUARDS` only after PR #366 had
  merged at that tip, and the branch was gone afterwards.
- A1 to A3 before merge: a development build of this script with a preview switch built this card's own R5
  change in a fresh worktree from origin. Its diff added two lines under the current-stage heading, changed only
  this card's board row, and changed the card; the allowlist judge, check-cards and check-secrets passed, and the
  worktree and branch were removed. The same build refused an already merged card with `[POST-MERGE-ANCHOR]` and a
  malformed id with `[POST-MERGE-INPUT]`. The preview switch itself moved to `T0-POST-MERGE-R5-GUARDS`. The push,
  PR, CI-wait and merge path runs for the first time on this card's own R5, recorded in its R5 section.
- Pre-review: DeepSeek V4 Flash round 1 blocked on four points. The user split three of them (the main-table
  board rule, the wiring self-check and a preview switch) into `T0-POST-MERGE-R5-GUARDS`; the fourth, the missing
  mutation record, is this record. Round 2, given those dispositions, passed with no findings.
- R3 round 1 (Opus 5.5 through `ReviewCommand`, Codex out of quota) on `4592f31b` blocked on four points and
  recorded one follow-up; all four were fixed. (1) The mutants named by count only and left guard pieces without a
  killing case: six cases were added (a prose line carrying the id, a two-cell row, two rows removed and one added,
  a row rewritten in each direction, an added line that is the next heading) with M27 to M33, and the table above
  replaced the count. (2) The empty-section guard in `Add-PostMergeCardSection` duplicated the heading check and
  was deleted. (3) The CI wait read the ci.yml run once, so an unfinished run counted as red after the push, and
  NEUTRAL or SKIPPED counted as a successful fan-in: `Test-PostMergeFanInSuccess` (exactly SUCCESS) and
  `Get-PostMergeRunDecision` (pending until a run for the head completes) are now pure and tested (M34 to M39),
  the run list is polled inside the same deadline, and a failure after the push prints
  `[POST-MERGE-LEFT-BEHIND]` with the branch and PR. (4) Cleanup failures in r5's finally block were silent: they
  now print `[POST-MERGE-CLEANUP-FAIL]`, and the comments that claimed every native call is exit-code checked were
  narrowed. The follow-up (A3's live path has no self-verifying test) is the reason for the R5 record above and for
  `T0-POST-MERGE-R5-GUARDS`.
- Tier-1 acceptance on the fixed candidate: `selftest.ps1 -TaskId T0-POST-MERGE-DOCS-PR` from this worktree exited 0
  with `[SELFTEST-TIER-PASS] task=T0-POST-MERGE-DOCS-PR tier=1 gates=1,2,3,4,5,7,8,9,10,11,13,14,15,16` (no gate failed,
  1096.3 s) on `scripts/post-merge.ps1` SHA-256 `71A21B8EB65CF3175A122CC51B7A362E037D561DD49DD47250234390E6875128`. An
  earlier rerun was stopped by the host for low memory before it finished and is not counted. DeepSeek V4 Flash
  round 3 raised one finding (that M33 survives), which the fixture's line numbers and the mutation log refuted;
  round 4, given that evidence, passed. Only this record changed in the card after the run.