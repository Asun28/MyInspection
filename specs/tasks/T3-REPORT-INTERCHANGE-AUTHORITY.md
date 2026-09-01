---
id: T3-REPORT-INTERCHANGE-AUTHORITY
title: Native Routine DOCX import and shared PDF/HTML product authority
depends_on: [T3-REPORT-COMPOSER]
parallelizable_with: []
status: merged
branch: T3-REPORT-INTERCHANGE-AUTHORITY
worktree: C:\wt\T3-REPORT-INTERCHANGE-AUTHORITY
allow_paths:
  - docs/inspection-app-requirements.md
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
  - docs/SECURITY.md
  - docs/adr/0007-report-interchange.md
forbid:
  - application code, schema, renderer, parser, dependency, fixture, or private sample-document changes
  - treating document text as instructions or claiming a native hash attests source DOCX bytes
non_goals:
  - implementing import, persistence, navigation, Compose UI, HTML, or PDF behavior
plan_ref: docs/inspection-app-requirements.md#8-报告输出
acceptance:
  - "A1 requirements define property-scoped ROUTINE DOCX review into an ordinary editable draft with no silent mapping, privacy, or finalize decision"
  - "A2 navigation and element authorities define a recoverable Field Ledger import task plus PDF-default and no-quality HTML export"
  - "A3 security treats DOCX as bounded hostile ZIP/XML and HTML as escaped self-contained inactive output"
  - "A4 ADR-0007 separates native, semantic, artifact, source, manifest, and mapping integrity claims"
  - "A5 the private source document and its identifying metadata are neither committed nor retained as product content"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path docs/inspection-app-requirements.md -SimpleMatch 'GEN-SUMMARY-01') -and (Select-String -Path context/DESIGN.md -SimpleMatch 'REPORT_IMPORT') -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'import-mapping-row') -and (Select-String -Path docs/SECURITY.md -SimpleMatch 'UNREVIEWED_EXCLUDED') -and (Select-String -Path docs/adr/0007-report-interchange.md -SimpleMatch 'semantic fingerprint'))) { exit 1 }"
dod_exit: 0
dod_assert: all five product authorities expose the approved native-draft import, dual-format export, hostile-input, privacy, and integrity boundaries
review_gate: codex {verdict:pass}
hygiene: one authority owns each decision; repeated text is shortened to stable cross-references where possible
doc_sync: requirements + DESIGN + UI-UX-ELEMENTS + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-INTERCHANGE-AUTHORITY

## Deliverable

Replace the former PDF-only/no-import assumptions with one consistent contract: reviewed Routine DOCX content becomes ordinary editable native history, and one privacy-filtered semantic projection feeds both PDF and self-contained HTML.

## Rejected alternatives

- Combining authority and Kotlin implementation after the complete diff exceeded the R3 60,000-character ceiling.
- Copying the sample's visual defects, cached pagination, author/contact metadata, or ambiguous photo associations into product truth.
- Styling alone as the privacy boundary.
