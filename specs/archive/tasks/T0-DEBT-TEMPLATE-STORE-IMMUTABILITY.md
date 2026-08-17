---
id: T0-DEBT-TEMPLATE-STORE-IMMUTABILITY
title: Prove TemplateStore read results reject element replacement (repay TD13)
depends_on: []
status: merged
branch: T0-DEBT-TEMPLATE-STORE-IMMUTABILITY
worktree: C:\wt\T0-DEBT-TEMPLATE-STORE-IMMUTABILITY
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/template/TemplateStoreTest.kt
forbid:
  - Changing TemplateStore production behavior or schema
  - Combining another technical-debt item or reusing an existing worktree
non_goals:
  - Testing add, remove, or clear as a substitute for element replacement
  - Changing TemplateLoader or nested allowedStatuses immutability
diagnosis:
  root_cause: TemplateStore.read wraps its returned items with Collections.unmodifiableList, but the existing round-trip assertions do not exercise mutation through a MutableList cast, so deleting the wrapper survives the suite.
  same_class: The existing ContactRetentionServiceTest demonstrates the required two-element set mutation; this card applies that exact discriminating shape only to TemplateStore.read items.
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon :core:test --tests nz.myinspection.core.template.TemplateStoreTest
dod_exit: 0
dod_assert: A persisted template with at least two items is read back; replacing item 0 through a MutableList cast with item 1 throws UnsupportedOperationException and leaves the returned list unchanged. Temporarily deleting only the outer Collections.unmodifiableList wrapper in TemplateStore.read makes this new test fail, then exact restoration returns it to GREEN.
review_gate: codex {verdict:pass}
hygiene: Extend TemplateStoreTest.kt only; use set/index assignment, not add/remove/clear, because fixed-size lists can reject structural changes without the wrapper.
doc_sync: After merge, mark TD13 paid with PR/merge SHA, mark this card merged, update TASK-BOARD, and archive only this closed debt/card in R5.
---

# T0-DEBT-TEMPLATE-STORE-IMMUTABILITY

## Outcome and acceptance

Add the missing regression proof for the outer immutability boundary of `TemplateStore.read().items`. The test must distinguish the production wrapper from Kotlin/JVM fixed-size list behavior.

## File-level implementation plan

1. Add one focused test to the existing `TemplateStoreTest.kt` with at least two returned items.
2. Record RED by temporarily removing only the outer `Collections.unmodifiableList` wrapper from `TemplateStore.read`, run the card DoD, then restore the production file byte-for-byte.
3. Run the targeted test GREEN and the normal ship gates; the final implementation diff contains only the test file.

## Key assumptions and exclusions

- The existing Routine fixture has at least two items.
- Index assignment is the discriminating operation: without the wrapper, the mapped list permits replacement even when structural mutations are unsupported.
- Production code and frozen schema remain unchanged in the final diff.
