---
id: T3-DOCX-REPORT-EXTRACTOR
title: Sample-shaped DOCX report extractor with explicit ambiguity
depends_on: [T3-DOCX-PACKAGE-READER, T3-DOCX-IMAGE-QUALIFICATION, T3-DOCX-EXTRACTION-MANIFEST, T3-DOCX-XML-TREE]
parallelizable_with: []
status: merged
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
  - "A4 raw spelling and ordering survive alongside normalized suggestions; page counters, qualified layout shims, source URLs, authors and sensitivity labels are excluded with safe warnings; images without verified payload qualification remain retained with IMAGE_REVIEW_REQUIRED"
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

## Approved split (2026-09-06)

After two real R3 blocks, the user approved T3-DOCX-IMAGE-QUALIFICATION as a predecessor. Replace the local image-dimensions helper with that boundary after it merges; only qualified layout shims may be excluded. Unverified PNG/JPEG media remain image evidence with IMAGE_REVIEW_REQUIRED, including complete headers without valid payloads. Preserve both verdicts and the original implementation; restore review rounds only under this explicit split adjudication and rerun every gate. The 64 logical-item / 89-caption / 67-substantive-image synthetic ambiguity contract remains unchanged.

## Second approved split (2026-09-06)

The user approved T3-DOCX-EXTRACTION-MANIFEST and T3-DOCX-XML-TREE as independent predecessors after the complete repair measured 66,391 characters and exceeded the unchanged 60,000-character review cap. Both complete their own R1-R5 before the parent resumes. Preserve all 36 existing integration tests and add the five drawing/identity regressions plus the malicious-XML integration test. The repair rejects unsupported drawing structure, retains empty inline/anchor frames as unresolved evidence, and confines pending identity values to an adjacent paragraph with the same parent while warning on expired labels. Merge the updated master into the preserved extractor branch without rewriting its history. After implementation and real tests-first verification, the user authorizes exactly one additional official review-counter restoration; preserve the preceding verdicts, count, RED and shipped receipts and rerun every gate. This is not permission to raise size limits, skip review or widen the package reader.

## Delivery evidence

2026-09-07: local master `71e2b8e5`, implementation `89f5724b15715cfeb477bad0a79273dabe1c1f64`; 42 extractor integration tests passed; the card DoD also covers 11 Manifest and 6 XML-tree tests; 99 current full-source mutation cases produced assertion failures. Current R4 compiles all three parent source/test files and the two delivered baseline units: 96 parent-source faults plus 3 predecessor integration sensitivity faults, all 42 integration methods, 100 receipts, 107 actual XML runs, and seven physical candidate deletions. Historical 51 story/guard, 18 local image-header and 12 image-integration mutation epochs remain separate preserved records. The image-qualification predecessor and the second user-approved Manifest/XML-tree split were delivered first. The original branch history and prior verdicts were preserved. Drawing-loss, identity-scope, field-phase and URL-boundary repairs received real behavioral RED checkpoints and complete final-source R4; the historical extra official counter restoration approved with the second split was consumed only after implementation. The later character-data finding received its own final behavioral RED (59 tests, one assertion failure), GREEN (all 59 passed), and independent public Reader probes (48 rejected inputs plus 12 compatible controls). One universal guard now rejects nonblank direct character data unless it belongs to Word t or instrText inside an active paragraph; whitespace-only formatting remains accepted. The subsequent user continue instruction separately authorized one character-data review-counter restoration; it did not reuse the exhausted historical authorization. Every normal ship gate then ran through official R3 pass and local merge. The original supplied package still stops upstream at UNSUPPORTED_PART for custom document properties; this synthetic extraction delivery does not claim original-package import compatibility. Follow-up: TD174.
