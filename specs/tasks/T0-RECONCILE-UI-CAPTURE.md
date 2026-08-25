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
acceptance:
  - "A1 T2-CAPTURE-UI：plan_ref 主旅程且恰有 A1–A5，覆盖 21-page ownership 中的 setup/capture/review/camera、resume/save/focus、evidence states、permission/offline fallback、48dp/200%/TalkBack/performance"
  - "A2 T3-FIELD-UX-ACCEPTANCE：plan_ref accessibility 且恰有 A1–A5，具名设备/构建、日光单手、TalkBack/200%、Reduce Motion、process death、offline/provider、P0/P1 debt closure"
  - "A3 T3-HISTORY-COMPARE：plan_ref history matrix 且恰有 A1–A4，覆盖 previous/baseline/empty/archived、visible controls、overlay preview-only、focus return 与离线读"
  - "A4 T3-PDF-RENDERER：plan_ref backup/report matrix 且恰有 A1–A4，覆盖质量/进度/验证回执、Open/Share/Export actions、temporary content URI、CJK/内存/离线与失败恢复"
  - "A5 去重与范围：四卡不出现 DESIGN 的 component-matrix 章节或第二套 token；只增 plan_ref/acceptance/最小 dependency，不修改已归档 theme/photo dedupe 卡或产品代码"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md' -Pattern '^plan_ref: context/DESIGN.md#primary-inspection-journey$') -and ((Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md' -Pattern '^  - .A[1-5] ').Count -eq 5) -and (Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md' -Pattern '^  - .A[1-5] .*resume.*save.*focus') -and (Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md' -Pattern '^  - .A[1-5] .*48dp.*200%.*TalkBack') -and (Select-String -Path 'specs/tasks/T3-FIELD-UX-ACCEPTANCE.md' -Pattern '^plan_ref: context/DESIGN.md#accessibility-contract$') -and ((Select-String -Path 'specs/tasks/T3-FIELD-UX-ACCEPTANCE.md' -Pattern '^  - .A[1-5] ').Count -eq 5) -and (Select-String -Path 'specs/tasks/T3-FIELD-UX-ACCEPTANCE.md' -Pattern '^  - .A[1-5] .*process death.*offline.*P0/P1') -and (Select-String -Path 'specs/tasks/T3-HISTORY-COMPARE.md' -Pattern '^plan_ref: context/DESIGN.md#history-evidence-and-media-component-matrix$') -and ((Select-String -Path 'specs/tasks/T3-HISTORY-COMPARE.md' -Pattern '^  - .A[1-4] ').Count -eq 4) -and (Select-String -Path 'specs/tasks/T3-HISTORY-COMPARE.md' -Pattern '^  - .A[1-4] .*previous.*baseline.*archived') -and (Select-String -Path 'specs/tasks/T3-PDF-RENDERER.md' -Pattern '^plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix$') -and ((Select-String -Path 'specs/tasks/T3-PDF-RENDERER.md' -Pattern '^  - .A[1-4] ').Count -eq 4) -and (Select-String -Path 'specs/tasks/T3-PDF-RENDERER.md' -Pattern '^  - .A[1-4] .*Open PDF.*Share.*Export another quality') -and (-not (Select-String -Path 'specs/tasks/T2-CAPTURE-UI.md','specs/tasks/T3-FIELD-UX-ACCEPTANCE.md','specs/tasks/T3-HISTORY-COMPARE.md','specs/tasks/T3-PDF-RENDERER.md' -Pattern '^### .*component matrix$')))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 通过：四个 exact plan_ref、5/5/4/4 acceptance counts 与关键状态锚点完整，且 component-matrix headings 显式不存在
review_gate: codex {verdict:pass}
hygiene: 每条新增验收都能指向 DESIGN.md 或 UI-UX-ELEMENTS 的唯一条目
doc_sync: 四张卡与 UI 覆盖索引一致（R5）
---

# T0-RECONCILE-UI-CAPTURE

## 产出

把采集主流程、现场验收、历史比较和 PDF 出口四张实现卡对齐到 Field Ledger 页面与组件合同，删除本地重复的长篇设计说明。

## 验收

执行 front matter 的 `dod_command`，并确认净 diff 保持评审预算内。
