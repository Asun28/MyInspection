---
id: T0-POST-MERGE-R5-GUARDS
title: Add -DryRun to post-merge.ps1 r5, the last of the three hardening PRs
status: todo
depends_on: [T0-POST-MERGE-R5-WIRING]
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - .claude/skills/task-loop/SKILL.md
  - specs/tasks/T0-POST-MERGE-R5-GUARDS.md
forbid:
  - Loosening any guard T0-POST-MERGE-DOCS-PR, T0-POST-MERGE-R5-BOARD-TABLE or T0-POST-MERGE-R5-WIRING delivered, or widening the direct-merge allowlist
  - Changing scripts/_guard.ps1, scripts/_ci.ps1, scripts/task.ps1 or any existing gate
non_goals:
  - The lessons PR (T0-POST-MERGE-LESSONS)
  - The main-table board rule (T0-POST-MERGE-R5-BOARD-TABLE) and the wiring self-check (T0-POST-MERGE-R5-WIRING)
acceptance:
  - "A1 r5 -DryRun builds and checks the same change as a real run (edits, allowlist judge, check-cards, check-secrets), prints its diff, pushes nothing, and removes its worktree and local branch; the task-loop skill's R5 step says to preview with -DryRun"
  - "A2 One real -DryRun on a card that is not yet merged is recorded here with its diff summary, together with a check that origin then has no r5-<id> branch and no PR for it. The stop before the push is plumbing that -SelfCheck cannot run, so this run is its evidence in place of a mutation"
  - "A3 The synopsis and the usage line name -DryRun, and prune given -DryRun is refused with [POST-MERGE-INPUT] before any git or gh call instead of deleting branches, shown by one real run recorded here"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck passes against the production functions, including the main-table cases and the wiring check (which also resolves the -DryRun code), and prints [POST-MERGE-SELF-CHECK-PASS]
review_gate: codex {verdict:pass}
budget: 120
hygiene: no new SelfCheck case for -DryRun (see A2); the cases added by the two earlier PRs stay
doc_sync: TASK-BOARD
---

# T0-POST-MERGE-R5-GUARDS

Split out of `T0-POST-MERGE-DOCS-PR` by user decision on 2026-09-25. DeepSeek V4 Flash pre-review round 1 of that
card raised three points the user chose to ship as a follow-up rather than fold into it:

1. The board editor rewrites the last cell of the one row whose second cell is the card id. The main table's last
   column is the status, but two other board tables also carry card ids in their second column, where the last
   column means something else.
2. `-SelfCheck` never exercised the git/gh plumbing's dependencies, so a renamed helper in `_guard.ps1` or
   `_ci.ps1` would leave the DoD green while every live run failed.
3. A preview mode that builds and checks the change without pushing makes the prose placement visible before a
   PR exists.

Later on 2026-09-25 the user asked for the three to be delivered as separate PRs. Point 1 went to
`T0-POST-MERGE-R5-BOARD-TABLE`, point 2 to `T0-POST-MERGE-R5-WIRING`, and this card keeps point 3; the three touch
the same file, so they run one after another. A draft of all three, written while `T0-POST-MERGE-DOCS-PR` was in
progress, is reapplied on top of the merged code.

R3: Opus 5.5 through `ReviewCommand` instead of Codex (user ruling 2026-09-25).
