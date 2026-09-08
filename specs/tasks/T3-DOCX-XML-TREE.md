---
id: T3-DOCX-XML-TREE
title: Secure in-memory XML tree for validated DOCX parts
depends_on: [T3-DOCX-PACKAGE-READER]
parallelizable_with: [T3-DOCX-EXTRACTION-MANIFEST]
status: todo
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

Implement [the internal contract](../../docs/references/docx-extraction-contract-llms.txt). Direct tests independently construct synthetic DocxPart values, including hostile ones; production uses bounded reader parts. Parent integration stays as specified. No file/network access.
