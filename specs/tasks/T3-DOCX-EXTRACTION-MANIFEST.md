---
id: T3-DOCX-EXTRACTION-MANIFEST
title: Immutable DOCX extraction manifest and deterministic evidence digest
depends_on: []
parallelizable_with: [T3-DOCX-XML-TREE]
status: todo
branch: T3-DOCX-EXTRACTION-MANIFEST
worktree: C:\wt\T3-DOCX-EXTRACTION-MANIFEST
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxExtractionManifest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxExtractionManifestTest.kt
forbid:
  - Private samples, writes, network, new runtime dependencies, or deviation from the referenced DOCX-EXTRACT-1 contract
non_goals:
  - XML parsing, package validation, extraction, image qualification, template mapping, persistence or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 direct-constructor tests prove immutable evidence collections and independently calculated deterministic DOCX-EXTRACT-1 digest vectors"
  - "A2 raw text, normalized suggestions, nullable fields, source coordinates and ordering survive without report interpretation or source mutation"
  - "A3 all eight input collections are copied and published read-only; caller mutation cannot change manifest contents or digest"
  - "A4 empty and representative nonempty digest vectors are independent of production serialization; every serialized field, collection order, null versus empty, and Unicode validity are pinned"
  - "A5 package, constructors, normalization and DOCX-EXTRACT-1 bytes match docs/references/docx-extraction-contract-llms.txt, including its independent 151/1472/230-byte vectors"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifestTest"
dod_exit: 0
dod_assert: direct-constructor tests prove immutable evidence collections and independently calculated deterministic DOCX-EXTRACT-1 digest vectors
review_gate: codex {verdict:pass}
hygiene: meaningful field/collection/encoding mutations must fail by assertion; physical candidate-test deletion and source/test restoration hashes are recorded
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-EXTRACTION-MANIFEST

## Approved predecessor (2026-09-06)

Implement [the contract](../../docs/references/docx-extraction-contract-llms.txt). Constructor tests are independent of reader/extractor/image fixtures; parent integration stays as specified.

Pin NFC/trim/six-ASCII-whitespace normalization, raw preservation, interior nonbreaking whitespace and independent vectors. JVM tests do not prove ART behavior.
