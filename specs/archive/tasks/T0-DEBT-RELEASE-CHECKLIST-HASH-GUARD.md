---
id: T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD
title: Document and prove the release-checklist hash coupling (repay TD11)
depends_on: []
status: merged
branch: T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD
worktree: C:\wt\T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD
allow_paths:
  - docs/RELEASE-CHECKLIST.md
  - scripts/selftest.ps1
forbid:
  - Changing the canonical Gradle release-blocker row or its rcCanonHash
  - Adding a second unlock path or weakening 17ee
  - Combining another technical-debt item or reusing an existing worktree
non_goals:
  - Implementing T0-LICENSE-SCANNER or repaying TD2
  - Replacing the canonical-row hash contract
diagnosis:
  root_cause: The Gradle release-blocker row is intentionally byte-pinned by selftest 17ee, but the checklist itself does not warn editors that ordinary prose edits require an explicit rcCanonHash update and seeded revalidation.
  same_class: This card covers only the single [GRADLE-LIC-SCANNER-ONLY] row and its adjacent editor warning; the unlock semantics remain unchanged.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: An adjacent checklist comment explicitly names scripts/selftest.ps1 17ee and rcCanonHash, says any edit requires synchronized hash update and seeded rerun, and is machine-checked beside the unique sentinel row. Removing only that warning from a scratch copy fails with a dedicated code while the canonical row/hash remain byte-identical.
review_gate: codex {verdict:pass}
hygiene: Extend the existing 17ee fixture; keep the warning outside the pinned canonical row so rcCanonHash stays unchanged.
doc_sync: After merge, mark TD11 paid with PR/merge SHA, mark this card merged, update TASK-BOARD, and archive only this closed debt/card in R5.
---

# T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD

## Outcome and acceptance

Make the intentional 17ee whole-row hash coupling visible at the editing site and prove the warning cannot silently disappear. The pinned release-blocker wording and its single unlock path do not change.

## File-level implementation plan

1. Extend 17ee with a focused adjacency check for the editor warning and record formal RED while the checklist lacks it.
2. Add one HTML comment immediately above the `[GRADLE-LIC-SCANNER-ONLY]` row, naming `scripts/selftest.ps1`, `17ee`, and `$rcCanonHash` plus the synchronized-update/seeded-rerun rule.
3. Add a scratch-copy deletion mutation for the warning, verify the dedicated failure code, and prove the canonical row/hash are unchanged; then run normal ship gates.

## Key assumptions and exclusions

- The warning is non-rendered checklist metadata for maintainers, not another release unlock path.
- The canonical checklist row remains byte-for-byte identical, so the existing hash and four semantic mutations remain valid.
- T0-LICENSE-SCANNER may later replace this coupling; that separate card is not part of TD11.
