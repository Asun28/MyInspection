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
  - "A3 a 64-row, 89-caption, 67-substantive-image fixture remains explicitly ambiguous and never invents 22 photo pairs"
  - "A4 raw spelling and ordering survive alongside normalized suggestions, while page counters, qualified layout shims, source URLs, authors and sensitivity labels are excluded with safe warnings; images without verified payload qualification remain retained with IMAGE_REVIEW_REQUIRED"
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

Transform bounded OOXML parts into a no-write extraction manifest shaped by the supplied report's real structure. The committed fixture is synthetic and non-private; it preserves the adversarial counts and fragmented story layout without copying the user's document.

## Approved split scope

Consume the independently verified image qualification, immutable manifest and internal XML-tree predecessors. Retain the original extraction acceptance and all integration coverage, including malicious XML, drawing loss and adjacent same-parent identity labels. Unsupported drawing structures reject; empty inline/anchor frames remain unresolved evidence; expired identity labels produce warnings. Only qualified layout shims may be excluded. The synthetic 64-item / 89-caption / 67-substantive-image ambiguity contract remains unchanged. These registrations grant no new review-counter reset or gate exception.
