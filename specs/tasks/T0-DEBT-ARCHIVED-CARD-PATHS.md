---
id: T0-DEBT-ARCHIVED-CARD-PATHS
title: Repair inbound references to archived task cards (repay TD22)
depends_on: []
status: todo
branch: T0-DEBT-ARCHIVED-CARD-PATHS
worktree: C:\wt\T0-DEBT-ARCHIVED-CARD-PATHS
allow_paths:
  - CLAUDE.md
  - docs/adr/0002-local-data-saf-encrypted-backup.md
  - android/core/src/test/kotlin/nz/myinspection/core/capture/InspectionRepositoryTest.kt
  - scripts/selftest.ps1
forbid:
  - Editing scripts/archive.ps1 or any file under specs/archive/
  - Scanning or rewriting arbitrary historical references in frozen archive content
  - Combining another technical-debt item or reusing an existing worktree
non_goals:
  - A repository-wide Markdown link checker
  - Automatic inbound-reference rewriting during archival
  - Changing runtime Android behavior; the Kotlin edit is comment-only
diagnosis:
  root_cause: The archive step moves merged cards from specs/tasks/ to specs/archive/tasks/ but three non-archive authoritative sources retained their old concrete live-card paths.
  same_class: The bounded set is exactly the T0-TOOLCHAIN, T5-BACKUP-FORMAT, and T2-CAPTURE-CORE inbound references named by TD22; archive content is frozen and excluded.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: Seeded 17hh verifies all three uniquely anchored source segments contain the expected archive-card path, reject the former live-card path, and resolve to a regular-file archive target; a hermetic TA1 archive lifecycle fixture proves the same post-move state, and each one-at-a-time mutation back to the old path fails with stable code/path/reference/target evidence and restores scratch bytes exactly.
review_gate: codex {verdict:pass}
hygiene: Extend the existing seeded shard under the unused 17hh label; keep the checker data-driven and bounded to the three named mappings.
doc_sync: After merge, mark TD22 paid with PR/merge SHA, mark this card merged, update TASK-BOARD, and archive only this closed debt/card in R5.
---

# T0-DEBT-ARCHIVED-CARD-PATHS

## Outcome and acceptance

Repair the three known inbound references that still point to live task-card paths after those merged cards were archived. Add a deterministic seeded regression that proves the exact source-to-archive mapping and rejects regression to the old paths.

## File-level implementation plan

1. Add a bounded three-rule `17hh` checker and mutation fixtures to `scripts/selftest.ps1`; run formal RED while the real sources still contain the old paths. Include a hermetic merged-card `TA1` archive lifecycle fixture without editing the archive tool.
2. Change only the three named references to `specs/archive/tasks/<id>.md` in `CLAUDE.md`, the backup-format ADR, and the capture test comment.
3. Run seeded GREEN, verify every one-at-a-time old-path mutation is classified and scratch SHA restoration is exact, then run the normal ship gates.

## Key assumptions and exclusions

- All three archive targets already exist as regular files and are merged cards; the checker does not inspect unrelated archive content.
- The regression checker is deliberately bounded; it is not a generic historical-link policy.
- `scripts/archive.ps1` remains unchanged because silently rewriting arbitrary inbound prose is outside its conservative move-only contract.
