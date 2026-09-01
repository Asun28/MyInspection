---
id: T3-REPORT-CONTENT-ADAPTER
title: Adapt shared semantic report content into the existing A4 layout plan
depends_on: [T3-REPORT-CONTENT-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-REPORT-CONTENT-ADAPTER
worktree: C:\wt\T3-REPORT-CONTENT-ADAPTER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportModel.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportContentAdapter.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/DocumentPlan.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/
forbid:
  - Audience or privacy filtering after ReportContent, HTML or PDF bytes, Android imports, schema changes, or geometry owned by a renderer
  - Recomputing native data hash from a filtered snapshot or relabelling provenance as native integrity
non_goals:
  - DOCX import, HTML serialization, PDF drawing, export receipts, navigation, or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 ReportComposer consumes only ReportContent semantics through the adapter and no longer owns audience or privacy decisions"
  - "A2 the existing fixed report fixture retains its exact page count, block sequence, geometry, full native data hash, disclaimer, and photo back-references"
  - "A3 summary and provenance blocks come from the shared content in stable order and remain separately labelled"
  - "A4 tenant and landlord layout plans cannot contain a field absent from their input ReportContent"
  - "A5 all existing A1 through A18 report-composer guards remain green without weakening literal expectations"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.*"
dod_exit: 0
dod_assert: hardened composer suite proves semantic adapter ownership while preserving every existing golden layout and integrity invariant
review_gate: codex {verdict:pass}
hygiene: no existing behaviour test is deleted unless a stronger real-output assertion kills the same mutation
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-CONTENT-ADAPTER

## Deliverable

Move semantic projection upstream and keep the mature A4 paginator focused on layout. This is the compatibility bridge that lets the PDF renderer consume the same reviewed content as HTML without changing report meaning.
