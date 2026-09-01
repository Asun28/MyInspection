---
id: T3-REPORT-IMPORT-COMMIT
title: Atomic staged-media commit of a reviewed legacy report as a native draft
depends_on: [T3-REPORT-INTERCHANGE-SCHEMA, T3-REPORT-IMPORT-PLANNER, T2-REPEATABLE-ROOM-RUNTIME, T2-CAPTURE-CORE, T2-PHOTO-QUALITY-PROFILES, T3-FINALIZE]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-COMMIT
worktree: C:\wt\T3-REPORT-IMPORT-COMMIT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/commit/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/commit/
  - android/core/src/main/kotlin/nz/myinspection/core/capture/InspectionRepository.kt
  - android/core/src/main/kotlin/nz/myinspection/core/CoreModule.kt
  - android/app/src/main/kotlin/nz/myinspection/app/importing/
  - android/app/src/test/kotlin/nz/myinspection/app/importing/
forbid:
  - Partial visible inspections, orphan published media, source DOCX retention, auto-finalize, or mutation of finalized previous or baseline links
  - Non-Routine import, overwrite of an active draft, duplicate source commit, network, or filesystem paths in durable provenance
non_goals:
  - DOCX extraction, mapping UI, export rendering, history comparison UI, or backup format v2
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 zero-blocker review stages all media and a durable recovery marker before one database transaction creates a normal editable Routine DRAFT plus immutable provenance"
  - "A2 failure or process interruption before commit exposes no inspection and recovery removes unpublished media; failure after commit preserves the complete draft and clears only its marker"
  - "A3 source SHA idempotency prevents duplicate native drafts while allowing a prior abandoned extraction with no committed receipt to restart"
  - "A4 imported items use current template and room-instance identities; only photos with an explicit reviewed privacy classification can reach commit, each retains its source digest, and missing native items remain unrated"
  - "A5 only ordinary completeness and finalize paths can place the record in history; no existing finalized previous_inspection_id or baseline_inspection_id changes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.commit.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: real SQLite and temporary-file fixtures prove atomic visibility, crash recovery, idempotency, reviewed privacy classification, normal draft editability, and unchanged finalized links
review_gate: codex {verdict:pass}
hygiene: transaction, marker, idempotency, and link-protection branches each kill a named mutation
doc_sync: DATABASE-DESIGN + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-COMMIT

## Deliverable

Commit a fully reviewed plan as the same native DRAFT shape created by ordinary capture. The external source file is never moved or modified; v1 retains only its bounded immutable provenance and the native media selected for the draft.
