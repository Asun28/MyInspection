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
  - "A1 post-merge.ps1 r5 builds the doc sync in a new worktree cut from origin/<base>: the card's status becomes merged, the card's docs/TASK-BOARD.md row gets the given status cell, the given entry is inserted directly under '## 当前阶段' in CLAUDE.md, and the given R5 section is appended to the card. The board row must sit in the main card table (header second cell '卡 id', last cell '卡片状态 / 备注') and have that header's number of cells. Each edit fails closed with a named sentinel when its anchor is missing or not unique. -DryRun builds and checks the same change, prints its diff and pushes nothing"
  - "A2 Direct-merge allowlist (user ruling 2026-09-25): before any push the changed paths must be a subset of the card file, docs/TASK-BOARD.md and CLAUDE.md; the TASK-BOARD change must be exactly the card's own row; the CLAUDE.md change must be added lines only, all between '## 当前阶段' and the next '## ' heading. Anything else stops with [POST-MERGE-SCOPE] and nothing is pushed"
  - "A3 The merge happens only after check-cards and check-secrets pass on the new worktree, Assert-PersonalAccount passes, the PR's 'required' check succeeds and the ci.yml run for it has head_sha equal to the pushed head, and the PR base is the expected base; it uses gh pr merge --squash --match-head-commit. No R3 runs for this docs PR. The commit message carries no AI attribution"
  - "A4 post-merge.ps1 prune deletes a remote branch only when exactly one PR has it as head, that PR is MERGED, and the remote tip equals the PR's headRefOid, using git push --force-with-lease=refs/heads/<branch>:<oid>. Every other state (open, closed unmerged, tip moved, no PR, several PRs) is reported and kept. r5 prunes its own branch this way after its merge"
  - "A5 post-merge.ps1 -SelfCheck covers the edit functions, the allowlist judge and the prune decision with positive and negative cases, and prints [POST-MERGE-SELF-CHECK-PASS]; it also checks the wiring: every command, named parameter and Scaffold* variable the git/gh plumbing uses resolves after loading _guard.ps1 and _ci.ps1; each guard has a single-statement mutation that makes the SelfCheck exit non-zero, recorded in the card"
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
- A3's network path (push, PR, CI wait, merge, and prune's delete) cannot run before this card merges. It is covered by the -DryRun run, the SelfCheck's wiring check and real prune keep-path runs, and its first live run is this card's own R5, recorded in that R5 section.
- Amended 2026-09-25 after DeepSeek pre-review round 1: -DryRun and the main-table rule in A1, the wiring check in A5.
- Estimate: about 500 changed lines (script with SelfCheck about 380, docs about 20, this card about 100).
