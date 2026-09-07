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

This card registers bounded custom document properties compatibility; implementation remains pending remote delivery. Extend the existing reader rather than adding a second parser or metadata model. The custom part remains unclassified by `partKind`, so the existing final projection discards it only after complete validation.

The exact package path, MIME, relationship type and root namespace follow transitional OOXML. Reference: Microsoft Open XML SDK `CustomFilePropertiesPart` and custom `Properties`. Only the newly supported custom part requires one package-root relationship; existing core/app metadata behaviour is unchanged. An absent custom part needs no relationship. Dangling references and duplicate normalized ZIP names retain existing rejection behaviour.

Inside the correct root, inert child XML remains opaque and bounded. Do not validate property IDs, value types or value semantics. URI-like text is data to discard, never a relationship or a resource to fetch. Root relationship XML remains an existing returned RELATIONSHIPS part and may contain the fixed custom part target; no promise is made to remove that structural reference.

Tests extend the existing reader and extractor test files. Synthetic markers cover property names, values and comments. Explicit limits target the custom part, including package-wide accumulation. Private input may only be used for a local read-only follow-up; a later unsupported feature must be reported separately, never admitted by widening this card.
