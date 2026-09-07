---
id: T3-REPORT-EXPORT-CORE
title: Verified dual-format export workflow with semantic parity receipts
depends_on: [T3-REPORT-INTERCHANGE-SCHEMA, T3-REPORT-HTML-RENDERER, T3-REPORT-HTML-PRESENTATION, T3-PDF-RENDER-DEVICE, T5-MEDIA-ARCHIVE-CONTRACT]
parallelizable_with: []
status: todo
branch: T3-REPORT-EXPORT-CORE
worktree: C:\wt\T3-REPORT-EXPORT-CORE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/exporting/
  - android/core/src/test/kotlin/nz/myinspection/core/report/exporting/
  - android/app/src/main/kotlin/nz/myinspection/app/export/core/
  - android/app/src/test/kotlin/nz/myinspection/app/export/core/
forbid:
  - Receipt before close and reopen verification, renderer-specific semantic projection, delivery claims, network, cloud accounts, or deletion of the last verified artifact on failure
  - HTML quality masquerading as PDF quality or HTML receipts unlocking PDF-only media archive eligibility
non_goals:
  - Compose, navigation, system chooser presentation, DOCX import, cloud upload, or in-app viewing
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 one audience and privacy projection is reused for PDF and HTML, and both verified artifacts carry the same semantic fingerprint and native integrity labels"
  - "A2 each artifact writes to private temporary storage, closes, reopens, verifies exact bytes, size, SHA-256, and fingerprint, then atomically publishes and writes its format-aware receipt"
  - "A3 PDF selects one of four qualities while HTML always records NONE; receipt identity permits both formats and both audiences to coexist"
  - "A4 retry is idempotent for an exact tuple, stale temporary files are recoverable, and a failed sibling export preserves every prior verified artifact and receipt"
  - "A5 redaction sentinels absent from ReportContent are absent from both reopened files and neither success state claims delivery or backup"
  - "A6 [R1] 受众/内容/照片集合切换后旧确认不能授权新导出；保持现有任一受众可显式纳入的合同。"
  - "A7 [R2] 双格式真实重开文件验证内容一致与排除哨兵缺失，不仅比较内存模型。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.exporting.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: real temporary-file and SQLite fixtures prove parity, atomic publication, byte verification, format identity, idempotent retry, and last-good preservation
requirements:
  - "R1 当用户显式纳入私密照片时，系统应把确认绑定受众、内容版本及明确照片集合；任何绑定变化应要求重新确认。"
  - "R2 当生成 PDF 和 HTML 时，系统应向两种 renderer 传入同一次受众/隐私投影生成的 ReportContent。"
review_gate: codex {verdict:pass}
hygiene: close, reopen, hash, receipt, retry, and preservation branches each kill a named mutation
doc_sync: DATABASE-DESIGN + SECURITY + ADR-0007 + TASK-BOARD
---

# T3-REPORT-EXPORT-CORE

## Deliverable

Coordinate both renderers behind one verified artifact protocol. Completion means the app reopened and checked the artifact; it never means another app received, stored, or backed it up.

## 2026-09-06 接口补充

复用现有 ReportOptions/ReportContent，在 exporting 边界创建不可由 UI 任意构造的确认值；不就地改变已归档投影合同。签名草案与安全边界见 `specs/android-module-boundaries.md`。
