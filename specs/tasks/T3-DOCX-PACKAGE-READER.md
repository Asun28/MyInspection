---
id: T3-DOCX-PACKAGE-READER
title: Bounded no-write OOXML package reader for hostile legacy reports
depends_on: [T3-REPORT-CONTENT-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-DOCX-PACKAGE-READER
worktree: C:\wt\T3-DOCX-PACKAGE-READER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/package/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/package/
forbid:
  - Network, filesystem writes, external relationships, DTD or entity expansion, macros, OLE, ActiveX, encryption, or third-party DOCX libraries
  - Trusting ZIP filenames, relationship targets, declared sizes, compression ratios, content types, or source metadata
non_goals:
  - Report semantics, item/photo mapping, persistence, renderer output, UI, OCR, or PDF import
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 a synthetic valid package exposes bounded document, header, footer, relationship, and image parts in deterministic package order without writing files"
  - "A2 traversal, duplicate normalized names, encrypted entries, unsupported active content, external relationships, DTD or XXE, and malformed XML are rejected before semantic extraction"
  - "A3 entry count, per-entry bytes, total expanded bytes, compression ratio, XML depth, and text-node limits each have an independent hostile fixture"
  - "A4 rejection errors expose safe reason codes and counts but never raw paths, document text, relationship URLs, or image bytes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.package.*"
dod_exit: 0
dod_assert: in-memory valid and hostile ZIP/XML fixtures prove deterministic reads, every bound, no writes, and privacy-safe failures
review_gate: codex {verdict:pass}
hygiene: every hostile branch is killed by its own real parser fixture
doc_sync: SECURITY + ADR-0007 + TASK-BOARD
---

# T3-DOCX-PACKAGE-READER

## Deliverable

Provide a pure JVM package boundary that treats DOCX as untrusted ZIP/XML data. It emits only bounded parts for the extractor and performs no business or storage write.
