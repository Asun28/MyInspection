---
id: T3-DOCX-EXTRACTION-MANIFEST
title: Immutable DOCX extraction manifest and deterministic evidence digest
depends_on: []
parallelizable_with: [T3-DOCX-XML-TREE]
status: in-progress
branch: T3-DOCX-EXTRACTION-MANIFEST
worktree: C:\wt\T3-DOCX-EXTRACTION-MANIFEST
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxExtractionManifest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxExtractionManifestTest.kt
forbid:
  - Private samples, file or database writes, network, new runtime dependencies, or changes to the existing DOCX-EXTRACT-1 format
non_goals:
  - XML parsing, package validation, extraction, image qualification, template mapping, persistence or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 direct-constructor tests prove immutable evidence collections and independently calculated deterministic DOCX-EXTRACT-1 digest vectors"
  - "A2 raw text, normalized suggestions, nullable fields, source coordinates and ordering survive without report interpretation or source mutation"
  - "A3 all eight input collections are copied and published read-only; caller mutation cannot change manifest contents or digest"
  - "A4 empty and representative nonempty digest vectors are independent of production serialization; every serialized field, collection order, null versus empty, and Unicode validity are pinned"
  - "A5 the existing package, constructors and DOCX-EXTRACT-1 encoding remain byte-compatible with the approved extractor implementation"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.extract.DocxExtractionManifestTest"
dod_exit: 0
dod_assert: direct-constructor tests prove immutable evidence collections and independently calculated deterministic DOCX-EXTRACT-1 digest vectors
review_gate: codex {verdict:pass}
hygiene: meaningful field/collection/encoding mutations must fail by assertion; physical candidate-test deletion and source/test restoration hashes are recorded
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-EXTRACTION-MANIFEST

## Approved predecessor (2026-09-06)

The user approved migrating the existing Manifest from T3-DOCX-REPORT-EXTRACTOR into an independently verified predecessor. Direct-constructor unit tests do not depend on the reader, extractor or image fixtures. The original extractor integration tests remain in their parent card. No version, public API or digest-format redesign is included.

## Compatibility correction found before first ship

Fresh preflight found that Android uses Unicode regex character classes while the JDK default whitespace class is ASCII. An interior nonbreaking space therefore has different normalized evidence and digest bytes under the original platform-default pattern. The one-line correction explicitly names the same six ASCII whitespace characters, preserving existing JDK behavior, NFC/trim, raw evidence, API and DOCX-EXTRACT-1 field encoding. An independent JDK Pattern probe verified identical matching sets across all 1,112,064 Unicode scalars; original digest vectors remain unchanged. Android behavior is derived from the installed official SDK source, not claimed as an ART execution result. New direct-constructor whitespace regressions, actual dependency-classpath verification and a fresh complete final-source R4 epoch cover the correction. The new regression is already GREEN on the old JDK behavior and is not represented as a reproduced Android RED. The initial official tests-first RED and all historical evidence remain preserved.
