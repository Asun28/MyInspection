---
id: T0-DEBT-R3-CARD-BASELINE
title: Align R3 task-card authority with the pinned base (repay TD3)
depends_on: []
status: todo
branch: T0-DEBT-R3-CARD-BASELINE
worktree: C:\wt\T0-DEBT-R3-CARD-BASELINE
allow_paths:
  - scripts/review.ps1
  - scripts/selftest.ps1
forbid:
  - Changing scope-checker policy, task-card schema, review rubric, FrozenPaths, or base-ref resolution
  - Reading an edited review-branch card when the pinned base contains the card
  - Editing tracker, task-card, TASK-BOARD, or unrelated harness code in the implementation worktree
non_goals:
  - Fixing TD9, TD22, or another selftest/review debt
  - Making review.ps1 logic itself immutable under manual Local execution
  - Changing prompt-injection redaction or verdict parsing
diagnosis:
  root_cause: The deterministic scope gate reads the card from the pinned base ref, but review.ps1 injects the worktree copy, so a master-side card amendment can make the two gates judge the same diff against different scope contracts.
  same_class: Rubric and FrozenPaths already use the pinned base; the injected task card is the remaining R3 authority input read from the reviewee worktree.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: In a repository where base and worktree cards disagree, the captured R3 prompt contains the complete base card and excludes the stale worktree contract; when the card is genuinely absent from base, the worktree fallback remains usable.
review_gate: codex {verdict:pass}
hygiene: Keep one data-driven base-wins/fallback fixture and a mutation that restores worktree-first behavior; reuse the already resolved pinned baseRef.
doc_sync: Orchestrator marks TD3 paid and records the merge on master after R3; implementation diff does not edit tracker or task metadata.
---

# T0-DEBT-R3-CARD-BASELINE

## Outcome and acceptance

Make R3 and the deterministic scope gate consume the same task-card authority. `review.ps1` must load the complete card from the already pinned `$baseRef`; only a genuinely absent or empty base card may fall back to the review worktree for compatibility with branch-created cards.

## File-level implementation plan

1. Add a seeded, hermetic repository fixture whose base card and worktree card deliberately disagree; capture the R3 prompt through a stub backend and record formal RED when the stale worktree card wins.
2. Load `specs/tasks/$branchSafe.md` with `git show $baseRef:<path>`, preserve the existing worktree fallback, and emit a diagnostic source label without changing prompt sanitization.
3. Prove base-wins, base-absent fallback, full-card delivery, and mutation death when worktree-first behavior is restored; then run normal ship gates.

## Key assumptions and exclusions

- The exact `$baseRef` already selected for diff, rubric, and FrozenPaths is the only acceptable base for the card.
- The full card—not only `allow_paths`—remains prompt input because `forbid`, `non_goals`, diagnosis, and declared acceptance also constrain review.
- TD22’s archived-card references and TD9’s selftest diagnostics remain separate cards and worktrees.

## Rejected alternatives

- Injecting only a separately parsed `allow_paths` list would discard the card’s other review-relevant boundaries.
- Always requiring a base card would break legitimate review of a card first introduced on the branch, so an explicit worktree fallback is retained.
