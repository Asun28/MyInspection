---
id: T3-REPORT-CONTENT-CONTRACT
title: Shared privacy-filtered report content for native PDF and HTML parity
depends_on: [T3-REPORT-COMPOSER]
parallelizable_with: []
status: todo
branch: T3-REPORT-CONTENT-CONTRACT
worktree: C:\wt\T3-REPORT-CONTENT-CONTRACT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/content/
  - android/core/src/test/kotlin/nz/myinspection/core/report/content/
  - docs/inspection-app-requirements.md
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
  - docs/SECURITY.md
  - docs/adr/0007-report-interchange.md
forbid:
  - Android, HTML, PDF, DOCX, database, filesystem, or network dependencies in the semantic contract
  - CSS-only audience/privacy hiding or claims that the native data hash attests the source DOCX
non_goals:
  - DOCX parsing, schema migration, renderer bytes, persistence, navigation, or Compose UI
plan_ref: docs/inspection-app-requirements.md#8-报告输出
acceptance:
  - "A1 one literal fixture projects ordered identity, glossary, rooms, items, statuses, native notes, photos, supplements, disclaimer, tenant agreement, and separately labelled provenance"
  - "A2 audience and privacy filtering happens before ReportContent exists: tenant content has no landlord remediation and private photos are absent unless explicitly included"
  - "A3 the semantic fingerprint is deterministic across repeated projection and changes when one included semantic field changes"
  - "A4 ReportContent has no A4 geometry, DocumentPlan, Android, file path, URI, or renderer-specific field"
  - "A5 product, navigation, element, security, and ADR authorities consistently define editable ROUTINE DOCX import plus self-contained HTML and PDF export"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.content.*"
dod_exit: 0
dod_assert: focused tests prove ordered semantic projection, pre-serialization audience/privacy removal, separate integrity labels, and deterministic format-neutral fingerprinting
review_gate: codex {verdict:pass}
hygiene: redundant tests removed only when a named mutation remains killed by another real-behaviour assertion
doc_sync: requirements + DESIGN + UI-UX-ELEMENTS + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-CONTENT-CONTRACT

## Deliverable

Introduce a pure ReportContent projection upstream of pagination and serialization. It carries the reviewed native meaning once, after audience and privacy policy, so PDF and HTML cannot drift or reintroduce removed bytes.

The existing native data hash remains unchanged. A legacy source hash and mapping-receipt hash are optional, immutable provenance labels; they never replace or broaden the native hash claim. Source summary content is represented by the mapped current-template item note, not a new mutable narrative field.

## Rejected alternatives

- HTML reverse-engineered from DocumentPlan.
- Separate audience/privacy filtering in each renderer.
- Canonical hash v2 without a mutable field outside the existing hash domain.
- Copying the sample report's layout defects or private metadata into authority or fixtures.
