---
id: T0-RECONCILE-UI-NOTICE-SCHEDULE
title: 对齐通知与日程实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-NOTICE-SCHEDULE
worktree: C:\wt\T0-RECONCILE-UI-NOTICE-SCHEDULE
allow_paths:
  - specs/tasks/T4-NOTICES.md
  - specs/tasks/T4-SCHEDULE.md
forbid:
  - 修改产品代码、法定规则或通知送达语义
  - 复制 DESIGN.md 的完整组件规格
non_goals:
  - 采集、报告、备份、健康或清除卡同步
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T4-NOTICES.md' -Pattern '^plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix$') -and (Select-String -Path 'specs/tasks/T4-SCHEDULE.md' -Pattern '^plan_ref: context/DESIGN.md#structure-list-and-discovery-component-matrix$'))) { exit 1 }"
dod_exit: 0
dod_assert: 通知草稿/复制/记录与日程提醒状态引用唯一设计合同，离线可读且不虚构系统送达
review_gate: codex {verdict:pass}
hygiene: 卡内只留 owning acceptance，不复制组件矩阵
doc_sync: 两张卡与 UI 覆盖索引一致（R5）
---

# T0-RECONCILE-UI-NOTICE-SCHEDULE

## 产出

给通知和日程两张卡补充最小设计系统指针，覆盖列表、状态、空态、复制确认与本地提醒，不改变既有合规语义。

## 验收

执行 front matter 的 `dod_command`。
