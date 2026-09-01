---
id: T3-REPORT-HTML-RENDERER
title: Self-contained accessible HTML renderer from shared report content
depends_on: [T3-REPORT-CONTENT-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-REPORT-HTML-RENDERER
worktree: C:\wt\T3-REPORT-HTML-RENDERER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/html/
  - android/core/src/test/kotlin/nz/myinspection/core/report/html/
forbid:
  - JavaScript, external URLs or resources, raw HTML injection, CSS-only privacy hiding, network, filesystem writes, or DocumentPlan as input
  - Renderer-specific business rules, audience decisions, source paths, vendor metadata, or delivery claims
non_goals:
  - PDF, DOCX import, database receipts, Android chooser UI, or in-app report viewer
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 one ReportContent renders deterministic UTF-8 HTML with semantic heading order, tables or lists, figures, captions, language metadata, disclaimer, and integrity labels"
  - "A2 output is one offline file with embedded bounded images, no script, no event handler, no external reference, and every text or attribute context correctly escaped"
  - "A3 screen CSS is responsive at 320px and 200 percent text; print CSS targets A4 without clipping atomic evidence groups; dark and forced-colour modes remain legible"
  - "A4 meaningful image alternatives and native landmark order survive image failure and non-visual reading"
  - "A5 redaction sentinels removed by ReportContent are absent at byte level and the embedded semantic fingerprint equals the input fingerprint"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.html.*"
dod_exit: 0
dod_assert: exact byte tests prove deterministic self-containment, escaping, accessibility, print and responsive CSS, bounded images, redaction, and fingerprint preservation
review_gate: codex {verdict:pass}
hygiene: escaping, resource, redaction, and accessibility protections each kill a single realistic renderer mutation
doc_sync: requirements + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-HTML-RENDERER

## Deliverable

Serialize the shared semantic report into one portable HTML file suitable for a system browser, save, share, and print. HTML never re-decides audience or privacy.
