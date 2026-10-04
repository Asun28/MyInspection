---
id: T0-POST-MERGE-LESSONS
title: Automate the post-merge lessons PR, with lessons.ps1 bump able to target the current checkout
status: todo
depends_on: [T0-POST-MERGE-DOCS-PR, T0-POST-MERGE-R5-GUARDS]
parallelizable_with: []
allow_paths:
  - scripts/lessons.ps1
  - scripts/post-merge.ps1
  - docs/LESSONS.md
  - .claude/skills/task-loop/SKILL.md
  - specs/tasks/T0-POST-MERGE-LESSONS.md
forbid:
  - Changing what lessons.ps1 bump does without -Here (it keeps writing the main checkout's ledger)
  - Merging a lessons PR whose diff falls outside the A3 allowlist
  - Writing to the main checkout's working tree or to local master
non_goals:
  - Promoting lessons to the must tier (still a user decision)
  - Deciding which lessons to record; the caller supplies the new entries and the ids to bump
acceptance:
  - "A1 lessons.ps1 bump <id> -Here increments the recurrence on the entry's meta line in the current checkout's docs/lessons/LEDGER.md, through the same anchored edit and the same fail-closed checks as today's bump; without -Here the target is unchanged (the main checkout). lessons.ps1's self-checks cover -Here with a positive and a negative case"
  - "A2 post-merge.ps1 lessons takes a spec of new entries (tags, severity, symptom, root cause, rule) and ids to bump, and in a new worktree cut from origin/<base> runs lessons.ps1 add for each entry and lessons.ps1 bump -Here for each id, then lessons.ps1 check; new ids are therefore allocated from origin's ledger"
  - "A3 Direct-merge allowlist (user ruling 2026-09-25): only docs/lessons/LEDGER.md may change, as added lines plus recurrence-value changes on meta lines of the bumped ids; anything else stops with [POST-MERGE-SCOPE] and nothing is pushed. The merge and prune path is the one T0-POST-MERGE-DOCS-PR built (its A3 and A4)"
  - "A4 post-merge.ps1 -SelfCheck gains cases for the lessons allowlist judge, and lessons.ps1's self-check for -Here; each new guard has a single-statement mutation that makes its self-check exit non-zero, recorded in the card"
  - "A5 docs/LESSONS.md documents -Here and the lessons PR; the task-loop skill's R5.5 step points at post-merge.ps1 lessons"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: both self-checks pass against the production code, including the new lessons allowlist and -Here cases
review_gate: codex {verdict:pass}
budget: 400
hygiene: single-statement mutations for the -Here plane switch and each lessons allowlist guard, file restored by SHA-256
doc_sync: TASK-BOARD, docs/LESSONS.md
---

# T0-POST-MERGE-LESSONS

Second half of the post-merge automation the user asked for on 2026-09-25 (see `T0-POST-MERGE-DOCS-PR`). The
lessons PR for `T0-REVIEW-GOVERNING-DOCS` (#350) had to hand-copy `bump`'s one-line edit, because `bump` always
writes the main checkout's ledger (T0-LESSONS-BUMP-PLANE) and the main checkout held another session's work.
`-Here` makes the same edit in the checkout the command runs in, so the lessons PR can be built in a clean worktree
cut from origin, where `add` also allocates ids that cannot collide with ones already on origin.

Estimate: about 250 changed lines.
