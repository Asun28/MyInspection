---
id: T3-DOCX-REPORT-EXTRACTOR
title: Sample-shaped DOCX report extractor with explicit ambiguity
depends_on: [T3-DOCX-PACKAGE-READER, T3-DOCX-IMAGE-QUALIFICATION, T3-DOCX-EXTRACTION-MANIFEST, T3-DOCX-XML-TREE]
parallelizable_with: []
status: todo
branch: T3-DOCX-REPORT-EXTRACTOR
worktree: C:\wt\T3-DOCX-REPORT-EXTRACTOR
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxReportExtractor.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxExtractorFixture.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxReportExtractorTest.kt
forbid:
  - Private sample bytes or text in git, vendor-specific execution, writes, network, OCR, or automatic native mapping
  - Treating cached page fields, Word anchors, styles, author metadata, URLs, or tiny layout-shim images as report truth
non_goals:
  - Template mapping, status confirmation, persistence, finalization, rendering, or Compose UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 a synthetic sample-shaped package yields report identity, ordered room and item rows, raw nullable statuses, comments, summary narrative, captions, and substantive images"
  - "A2 paragraphs, the inspection table, headers, footers, inline drawings, and anchored drawings are all visited; content cannot disappear because it is outside the main document body"
  - "A3 a fixture with 64 items, 89 captions, 67 larger photos and 15 small images retains all 82 images and 83 placements (one repeated header placement), remains ambiguous and never invents 22 photo pairs"
  - "A4 raw spelling and ordering survive alongside normalized suggestions; page counters, source URLs, authors and sensitivity labels are excluded with safe warnings; every accepted image, including a valid small substantive PNG, retains IMAGE_REVIEW_REQUIRED and all its placements; no LAYOUT_IMAGE_EXCLUDED is emitted"
  - "A5 normalized manifest ordering and digest are deterministic for equivalent package input"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.extract.*"
dod_exit: 0
dod_assert: synthetic multi-story fixture proves complete ordered extraction, nullable raw fields, ambiguity preservation, metadata scrubbing, and deterministic digest
review_gate: codex {verdict:pass}
hygiene: each visited story part and ambiguity branch has a named deletion mutation
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-REPORT-EXTRACTOR

## Deliverable

Transform bounded OOXML parts into a no-write extraction manifest. The [extraction contract](../../docs/references/docx-extraction-contract-llms.txt) specifies the entry API and named synthetic integration cases; tests implement those cases without private source material or assumptions about code already existing remotely.

## Approved split scope

Consume the image validation, immutable manifest and internal XML-tree predecessors. Test malicious XML, drawing loss and adjacent same-parent identity labels against the reference contract. Unsupported drawings reject; empty inline/anchor frames remain unresolved evidence; expired identity labels produce warnings. The user-approved conservative image rule supersedes local exclusion assertions: the synthetic 67 larger and 15 small images all remain (82 review warnings, 83 placements, zero layout exclusions), while 64 items and 89 captions stay unchanged. These registrations grant no gate exception.
