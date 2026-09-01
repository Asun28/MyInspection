---
id: T2-ROUTINE-CONTEXT-V2
title: Routine template v2 with Hallway and hash-covered inspection summary
depends_on: [T3-REPORT-CONTENT-CONTRACT, T2-ROUTINE-CONTENT, T2-ROOM-REPEATABLE]
parallelizable_with: []
status: todo
branch: T2-ROUTINE-CONTEXT-V2
worktree: C:\wt\T2-ROUTINE-CONTEXT-V2
allow_paths:
  - data/templates/routine-v2.json
  - data/templates/README.md
  - android/core/build.gradle.kts
  - android/core/src/test/kotlin/nz/myinspection/core/content/RoutineContextV2Test.kt
forbid:
  - Editing routine-v1.json or changing any v1 stable ID, meaning, status domain, or order
  - Source-vendor wording, identifiers, private sample text, or inferred legal conclusions
non_goals:
  - Template editor UI, INGOING/EXIT content, DOCX mapping, schema, or import persistence
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 every routine-v1 room and item retains its stable ID, bilingual meaning, status domain, and relative order in routine-v2"
  - "A2 routine-v2 adds one bilingual Hallway room contract and one General item GEN-SUMMARY-01 without changing historical v1 bytes"
  - "A3 GEN-SUMMARY-01 accepts a normal status and note so imported summary text enters the existing native hash domain"
  - "A4 the template loader reads both versions, resolves routine-v2 as the deterministic current Routine template for new/imported drafts, and rejects duplicate IDs, missing translations, or cross-version drift outside the intended additions"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.content.RoutineContextV2Test"
dod_exit: 0
dod_assert: literal cross-version fixture proves v1 immutability and only the approved Hallway plus GEN-SUMMARY-01 additions
review_gate: codex {verdict:pass}
hygiene: each assertion names a template drift mutation it catches
doc_sync: data/templates/README + TASK-BOARD
---

# T2-ROUTINE-CONTEXT-V2

## Deliverable

Add the deterministic current Routine template version for new and imported drafts so the sample's Hallway rows and Comments / Summary map into ordinary native inspection items. Historical template v1 stays byte-identical and remains available for re-rendering old inspections.
