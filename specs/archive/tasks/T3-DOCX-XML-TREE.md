---
id: T3-DOCX-XML-TREE
title: Secure in-memory XML tree for validated DOCX parts
depends_on: [T3-DOCX-PACKAGE-READER]
parallelizable_with: [T3-DOCX-EXTRACTION-MANIFEST]
status: merged
branch: T3-DOCX-XML-TREE
worktree: C:\wt\T3-DOCX-XML-TREE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxXmlTree.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/extract/DocxXmlTreeTest.kt
forbid:
  - Private samples, production file or database writes, runtime network, new runtime dependencies, or reader allowlist changes
non_goals:
  - A public XML API, independent package resource limits, provider abstractions, report extraction, image handling, persistence or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 direct parse tests prove namespace-aware ordered trees and closed rejection of malformed XML, DTDs and entities before external access"
  - "A2 namespace/local names, attribute precedence, direct text including CDATA and UTF-8, child ordering, parent links, traversal and nearest Word ancestor queries match the referenced extraction contract"
  - "A3 plain DOCTYPE, internal general and expansion entities, external general and parameter entities and external subsets are rejected as DOCX_XML with no exposed input, path or nested cause"
  - "A4 a test-only JDK17 I/O guard is calibrated through real file and URL entry points; each malicious case makes zero target-file or network attempts and the previous guard is restored"
  - "A5 implement the internal Element, parse and W signatures in docs/references/docx-extraction-contract-llms.txt; production callers use validated bounded DocxPart input"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.extract.DocxXmlTreeTest"
dod_exit: 0
dod_assert: direct parse tests prove namespace-aware ordered trees and closed rejection of malformed XML, DTDs and entities before external access
review_gate: codex {verdict:pass}
hygiene: named parser/namespace/traversal/DTD mutations fail by assertion; physical candidate-test deletion and exact source/test restoration hashes are recorded
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-XML-TREE

## Approved predecessor (2026-09-06)

Implement [the internal contract](../../../docs/references/docx-extraction-contract-llms.txt). Direct tests independently construct synthetic DocxPart values, including hostile ones; production uses bounded reader parts. Parent integration stays as specified. No file/network access.

## Remote delivery — 2026-09-08

Squash-merged by [PR #261](https://github.com/Asun28/MyInspection/pull/261) as `94dfbe58ee4c70e13581ae3eab957ad7f1e7f87c`; reviewed head `de12cd0931c208e11086b5836da78287f5437ad2` received formal R3 pass with empty reasons and exact-candidate CI `verify` SUCCESS ([run](https://github.com/Asun28/MyInspection/actions/runs/34187288526)). Official non-local task-loop ship passed RED, DoD, project verify, scope, licence, secrets and complete-diff budget gates.

Fresh RED had 6 behavioral failures; final DoD passed all 6 without skips. Eleven production mutations and two I/O counter mutations failed their named assertions. Physical removal of the ancestor test let its mutation survive the full core suite (895 tests, 4 existing skips), so that unique test was restored. Source SHA-256 `73bfa6f6a26732ba7da5b1774ea47f86011e8584e507bda3054ece02dcff17de`; final test SHA-256 `9cd98fce9b2c03b9eda90009d879f696ad60e010fbe701a39a62b61649c4759e`. Preserved local evidence: `_local/projection-20260908/remote-xml-delivery/` and `remote-xml-evidence/`.

Official cleanup completed after merge and evidence preservation. R5 debt scan found no new concrete divergence. R5.5 skips a duplicate lesson: L310 already records the local-only versus remote-delivery recovery. Parser I/O calibration is JDK 17 evidence, not ART acceptance; package resource limits remain the reader's responsibility. Extraction and complete import are separate deliveries.
