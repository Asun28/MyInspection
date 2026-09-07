---
id: T2-ROUTINE-CONTEXT-V2
title: Routine template v2 with Hallway and hash-covered inspection summary
depends_on: [T3-REPORT-CONTENT-CONTRACT, T2-ROUTINE-CONTENT, T2-ROOM-REPEATABLE]
parallelizable_with: []
status: merged
branch: T2-ROUTINE-CONTEXT-V2
worktree: C:\wt\T2-ROUTINE-CONTEXT-V2
allow_paths:
  - data/templates/routine-v2.json
  - data/templates/README.md
  - android/core/build.gradle.kts
  - android/core/src/test/kotlin/nz/myinspection/core/content/RoutineContextV2Test.kt
  - android/core/src/main/kotlin/nz/myinspection/core/template/TemplateStore.kt
  - android/core/src/test/kotlin/nz/myinspection/core/template/TemplateStoreTest.kt
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
  - "A4 the existing loader reads both versions and rejects duplicate IDs or missing translations; the literal cross-version fixture rejects drift outside the intended additions. TemplateStore selects only active ROUTINE version 2 for new/imported drafts, returns unavailable when absent, and preserves historical read-by-ID; no fallback to v1 or unapproved newer versions"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.content.RoutineContextV2Test" --tests "nz.myinspection.core.template.TemplateStoreTest"
dod_exit: 0
dod_assert: literal cross-version fixture proves v1 immutability and only the approved Hallway plus GEN-SUMMARY-01 additions; real SQLite proves active Routine v2 selection without fallback and preserves historical read-by-ID
review_gate: codex {verdict:pass}
hygiene: each assertion names a template drift mutation it catches
doc_sync: data/templates/README + TASK-BOARD
---

# T2-ROUTINE-CONTEXT-V2

## Deliverable

Add the deterministic current Routine template version for new and imported drafts so the sample's Hallway rows and Comments / Summary map into ordinary native inspection items. Historical template v1 stays byte-identical and remains available for re-rendering old inspections.

## Scope clarification 2026-09-07

A4 already requires production version selection; its original allow_paths omitted the production store and its mirrored tests. The exact paths and DoD selector now cover that existing requirement. ADR-0007 fixes current Routine at version 2, not the greatest installed version. Reuse the existing active-version query without changing SQL or the frozen template schema.

The selector only identifies an installed active version. Packaged-asset hash verification, installation into an empty database, recovery readiness and application wiring remain owned by T1-APP-BOUNDARY-ASSEMBLY. Existing duplicate active-version insertion errors are not swallowed, and historical read-by-ID is unchanged. Single-template structure validation remains in TemplateLoader; cross-version content preservation is proved by this card's literal fixture.

## Delivery record — 2026-09-07

Locally merged as `0603b022`; reviewed feature `68779d13c0d3159c099159183bfd4b85afb32159` received formal R3 pass with no findings. Routine v2 contains 92 items: all 83 historical items unchanged, eight Hallway items and one General summary. The installed active Routine v2 selector has no v1/newer-version fallback and preserves historical reads.

The integrated DoD passed 21 tests with no failures/errors/skips. Nine final-production mutations were caught by the specified real assertions; exact source bytes were restored. Default full selftest passed in 1850.423 seconds with seeded/workflow/core all passing, 24 explicitly recorded skips (including three deferred nightly meta checks), and all 650 tracked file hashes unchanged. Normal ship passed verify, scope, licence, secrets and size checks (352 changed lines, 37737 diff characters). Evidence is retained under `_local/android-spike-2026-09-06/routine-*`. These results do not attest APK bootstrap, UI integration or Android device acceptance; assembly remains with T1-APP-BOUNDARY-ASSEMBLY. No new technical debt was found; existing exact-byte evidence rules cover the line-ending verification pitfall.
