---
id: T0-DEBT-TASK-INVENTORY
title: Remove hand-maintained task-card inventory (repay TD21)
depends_on: []
status: todo
branch: T0-DEBT-TASK-INVENTORY
worktree: C:\wt\T0-DEBT-TASK-INVENTORY
allow_paths:
  - CLAUDE.md
  - scripts/selftest.ps1
forbid:
  - Replacing the stale number with another static or dynamically rendered count
  - Editing task cards, archives, tracker rows, TASK-BOARD, or product/runtime code
  - Fixing post-archive card-path references tracked separately as TD22
non_goals:
  - Changing archive semantics or task-card lifecycle
  - Recounting historical cards in prose
diagnosis:
  root_cause: CLAUDE.md copied a point-in-time card count into long-lived authority prose even though card creation and cold-storage archiving change both the live count and its denominator.
  same_class: Scan authoritative project guidance for numeric task-card inventory claims; the stale Current Stage sentence is the only such inventory claim, while historical delivery counts are event records rather than inventory.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
dod_exit: 0
dod_assert: CLAUDE.md Current Stage retains the live task-card authority pointer but contains no static task-card inventory claim; a controlled fixture that inserts a numeric inventory is rejected with [TD21-STATIC-TASK-INVENTORY].
review_gate: codex {verdict:pass}
hygiene: Keep one bounded Current Stage parser and one classified mutation; unrelated historical numbers must not match.
doc_sync: Orchestrator marks TD21 paid and records the merge on master after R3; implementation diff does not edit tracker or task metadata.
---

# T0-DEBT-TASK-INVENTORY

## Outcome and acceptance

Remove the hand-maintained task-card count from `CLAUDE.md`. Keep `specs/tasks/` as the live-card authority pointer and describe archived history without asserting a numeric inventory whose meaning changes after every create/archive cycle.

The regression gate must inspect only the Current Stage task-inventory sentence, reject a synthetic `N 张` / `N cards` claim with the stable TD21 sentinel, and keep unrelated historical delivery counts out of scope.

## File-level implementation plan

1. Add the focused core-shard check and controlled static-count mutation, then capture RED through `task.ps1 -Phase red`.
2. Remove only the stale numeric inventory from the Current Stage sentence while retaining the live/archive truth-source pointers.
3. Run the core shard, normal project verification, and R3 ship.

## Rejected alternatives

- Recomputing the count during R5: remains a second truth source and becomes stale on the next card lifecycle event.
- Counting live plus archived cards: the denominator is still prose-defined and changes whenever archive policy changes.
- Combining the three broken post-archive paths: that is separately registered TD22 and requires its own card/worktree.
