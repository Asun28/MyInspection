---
id: T0-RECONCILE-UI-CAPTURE
title: 对齐采集、历史与 PDF 实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-CAPTURE
worktree: C:\wt\T0-RECONCILE-UI-CAPTURE
allow_paths:
  - specs/tasks/T2-CAPTURE-UI.md
  - specs/tasks/T3-FIELD-UX-ACCEPTANCE.md
  - specs/tasks/T3-HISTORY-COMPARE.md
  - specs/tasks/T3-PDF-RENDERER.md
forbid:
  - 复制 DESIGN.md 的完整规格或修改产品代码
  - 编辑已归档的 theme/photo-property-dedupe 卡
non_goals:
  - 通知、日程、备份、清除、remediation 与 smoke 卡同步
  - 实现 Compose/PDF/相机功能
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md' -Pattern '^plan_ref: context/DESIGN.md#primary-inspection-journey$') -and (Select-String -Path 'specs/tasks/T3-FIELD-UX-ACCEPTANCE.md' -Pattern '^plan_ref: context/DESIGN.md#accessibility-contract$') -and (Select-String -Path 'specs/tasks/T3-HISTORY-COMPARE.md' -Pattern '^plan_ref: context/DESIGN.md#history-evidence-and-media-component-matrix$') -and (Select-String -Path 'specs/tasks/T3-PDF-RENDERER.md' -Pattern '^plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix$'))) { exit 1 }"
dod_exit: 0
dod_assert: 四张卡以最小 plan_ref/acceptance 指针覆盖采集、相机、历史、报告页面和关键状态，不复制 canonical prose
review_gate: codex {verdict:pass}
hygiene: 每条新增验收都能指向 DESIGN.md 或 UI-UX-ELEMENTS 的唯一条目
doc_sync: 四张卡与 UI 覆盖索引一致（R5）
---

# T0-RECONCILE-UI-CAPTURE

## 产出

把采集主流程、现场验收、历史比较和 PDF 出口四张实现卡对齐到 Field Ledger 页面与组件合同，删除本地重复的长篇设计说明。

## 验收

执行 front matter 的 `dod_command`，并确认净 diff 保持评审预算内。
