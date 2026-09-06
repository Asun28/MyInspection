---
id: T0-INIT-ASSIGNMENT-ANCHORS
title: Restrict initializer configuration rewrites to their actual assignment fields
status: todo
depends_on: [T0-SHIP-REVIEW-BASE-BUNDLE]
allow_paths:
  - init-scaffold.ps1
  - scripts/selftest.ps1
  - specs/tasks/T0-INIT-ASSIGNMENT-ANCHORS.md
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture init-assignments
dod_exit: 0
dod_assert: The real initializer rewrites intended fields in an isolated fixture while preserving comments, longer keys, indentation and valid quoted values.
review_gate: codex {verdict:pass}
diagnosis: Unanchored regex replacements match field-name suffixes in longer keys and commented examples; the same class affects configured scalar fields and FrozenPaths replacement.
acceptance:
  - "A1 Isolated execution covers all local initializer config rewrites, including GhAccount, PythonVersion, LessonsMustCap, ReviewModel, ReviewEffort, ScaffoldOriginVersion, ProjectName and FrozenPaths"
  - "A2 Matching fields change correctly while commented examples and longer-name lookalikes remain byte-identical; indentation and trailing comments are preserved"
  - "A3 Apostrophes and literal dollar replacement tokens remain literal and the generated config parses; an already matching value remains stable"
  - "A4 The focused fixture runs from the existing selftest entry and full affected coverage, and a restored unanchored rewrite yields a named semantic failure"
forbid:
  - Running init on the live project, adding PlanDir or adding a DocSyncMap target for absent TEMPLATE-README.md
hygiene: Extend the existing init regression surface with an isolated fixture; do not create a parallel initialization implementation.
doc_sync: Keep the initializer comments accurate; record completion in this card.
---

# T0-INIT-ASSIGNMENT-ANCHORS

Adapt the root cause of upstream PR #377 across the local field set. The audit reproduced
ReviewEffort rewriting a comment and LegacyReviewEffort. The current application is already initialized.

