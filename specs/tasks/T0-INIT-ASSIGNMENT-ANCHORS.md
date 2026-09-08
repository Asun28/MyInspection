---
id: T0-INIT-ASSIGNMENT-ANCHORS
title: Restrict initializer configuration rewrites to their actual assignment fields
status: todo
depends_on: [T0-SHIP-REVIEW-BASE-BUNDLE]
allow_paths:
  - init-scaffold.ps1
  - scripts/selftest.ps1
  - scripts/_config.ps1
  - scripts/scaffold-sync.ps1
  - docs/SCAFFOLD-SYNC.md
  - CLAUDE.md
  - specs/tasks/T0-INIT-ASSIGNMENT-ANCHORS.md
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture init-assignments
dod_exit: 0
dod_assert: The real initializer rewrites intended fields in an isolated fixture while preserving comments, longer keys, indentation and valid quoted values; scaffold sync validates the adopted version ledger and origin/current split.
review_gate: codex {verdict:pass}
diagnosis: Unanchored regex replacements match field-name suffixes in longer keys and commented examples; the same class affects configured scalar fields and FrozenPaths replacement.
acceptance:
  - "A1 Isolated execution covers all local initializer config rewrites, including GhAccount, PythonVersion, LessonsMustCap, ReviewModel, ReviewEffort, ScaffoldOriginVersion, ProjectName and FrozenPaths"
  - "A2 Matching fields change correctly while commented examples and longer-name lookalikes remain byte-identical; indentation and trailing comments are preserved"
  - "A3 Apostrophes and literal dollar replacement tokens remain literal and the generated config parses; an already matching value remains stable"
  - "A4 The focused fixture runs from the existing selftest entry and full affected coverage, and a restored unanchored rewrite yields a named semantic failure"
  - "A5 Record v0.47.0 as partially adopted after the selected fixes, keeping immutable origin v0.29.0 and synchronizing current-version configuration, authority references and the existing real-ledger SelfCheck"
forbid:
  - Running init on the live project, adding PlanDir or adding a DocSyncMap target for absent TEMPLATE-README.md
hygiene: Extend the existing init regression surface with an isolated fixture; do not create a parallel initialization implementation.
doc_sync: Keep the initializer comments accurate; synchronize the v0.47 partial-adoption ledger and current-version references; record completion in this card.
---

# T0-INIT-ASSIGNMENT-ANCHORS

Adapt the root cause of upstream PR #377 across the local field set. The audit reproduced
ReviewEffort rewriting a comment and LegacyReviewEffort. The current application is already initialized.

Version-closeout coupling (confirmed before R1/RED): scaffold-sync's existing real-ledger SelfCheck
pins current v0.46.0. Include its expected v0.47 partial row and the matching config/authority metadata
in this final adoption card so the last selected repair and its adoption record land together.
The focused fixture also invokes the existing scaffold-sync SelfCheck. Historical ledger rows and
immutable origin remain unchanged; unselected advisory/budget policies remain declined.


## Remote delivery authorization and provenance

The user's 2026-09-08 instruction authorizes remote delivery through an independent worktree and PR. The remaining acceptance, forbid and non-goals stay in force. This todo registration does not represent local historical results as remote implementation or acceptance. Use task-loop with GPT-6 Astra, high effort and the configured independent GPT-5.6 Sol high R3. Establish current-source behavior evidence and preserve existing remote product changes.
