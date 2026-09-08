---
id: T3-DOCX-CUSTOM-PROPERTIES
title: Bounded custom document properties validation and discard
status: todo
depends_on: [T3-DOCX-PACKAGE-READER, T3-DOCX-REPORT-EXTRACTOR]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/package/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/package/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxReportExtractorTest.kt
plan_ref: docs/adr/0007-report-interchange.md
forbid:
  - Exposing custom property names or values as returned parts, extraction fields, errors, or logs
  - Broad metadata allowlists, external relationships, DTD/entities, network, filesystem writes, or new runtime dependencies
non_goals:
  - Full custom-property or VT schema validation, business interpretation, other metadata compatibility, native import commit, or device acceptance
acceptance:
  - "A1 only normalized docprops/custom.xml with the exact transitional custom-properties content type and Properties root is admitted; it requires exactly one internal package-root custom-properties relationship"
  - "A2 the custom part receives existing ZIP/CRC/expansion and XML safety/resource checks before discard; wrong bindings, malformed XML, active content and exceeded budgets reject with safe reason codes"
  - "A3 STORED and DEFLATED packages with inert custom values pass; property names, values and comments are absent from returned part bytes and extraction manifests, whose normalized digest equals the equivalent metadata-free package"
  - "A4 existing package and extraction tests remain green; deleting the new root or relationship guards, bypassing validation, or exposing metadata causes dedicated behaviour failures"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.package.*" --tests "nz.myinspection.core.report.importing.docx.extract.*"
dod_exit: 0
dod_assert: real in-memory ZIP/XML fixtures verify exact binding, inert discard, unchanged semantic digest, all custom-part limits and privacy-safe rejection
review_gate: codex {verdict:pass}
hygiene: directed assertion mutations on final source and actual redundant-test deletion only if all relevant faults remain caught
doc_sync: SECURITY + ADR-0007 + TASK-BOARD; record only the custom-properties boundary delivery, without claiming full private-source import
---

# T3-DOCX-CUSTOM-PROPERTIES

Extend the existing reader. Keep the custom part unclassified by `partKind` until final projection discards it after complete validation.

Exact literals and independent fixtures: [local contract](../../docs/references/docx-extraction-contract-llms.txt). Only this custom part requires one package-root relationship; absence requires none. Core/app metadata, dangling-reference and duplicate-name behaviour stay unchanged.

Inside the correct root, bounded inert children stay opaque: no property-ID or value/type validation. URI-like text is discarded, never fetched. Returned root RELATIONSHIPS may retain the fixed custom target.

Extend existing reader/extractor tests with name/value/comment markers and custom-part/aggregate limits. Private follow-ups remain local and read-only; further unsupported features require separate cards.
