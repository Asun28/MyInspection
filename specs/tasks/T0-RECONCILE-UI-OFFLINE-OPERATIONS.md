---
id: T0-RECONCILE-UI-OFFLINE-OPERATIONS
title: 对齐备份、媒体、remediation 与收官 smoke 的离线体验指针
depends_on: [T0-RECONCILE-UI-COVERAGE, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-OFFLINE-OPERATIONS
worktree: C:\wt\T0-RECONCILE-UI-OFFLINE-OPERATIONS
allow_paths:
  - specs/tasks/T5-BACKUP-IO.md
  - specs/tasks/T5-LOCAL-MEDIA-RETENTION.md
  - specs/tasks/T7-REMEDIATION.md
  - specs/tasks/T7-SMOKE-POLISH.md
forbid:
  - 修改产品代码、备份格式或运行期网络硬边界
  - 把 provider/LLM 不可用显示为核心 app 离线失败
non_goals:
  - 通知、日程、采集、历史与 PDF 卡同步
  - 实现备份、清除、诊断或 remediation
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T5-BACKUP-IO.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and (Select-String -Path 'specs/tasks/T5-LOCAL-MEDIA-RETENTION.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and (Select-String -Path 'specs/tasks/T7-REMEDIATION.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and (Select-String -Path 'specs/tasks/T7-SMOKE-POLISH.md' -Pattern '^plan_ref: context/DESIGN.md#primary-inspection-journey$'))) { exit 1 }"
dod_exit: 0
dod_assert: 备份/恢复/清理/remediation/smoke 的 ready、running、blocked、failed、verified、offline 状态及恢复动作均有唯一设计指针
review_gate: codex {verdict:pass}
hygiene: 卡内只保留可验收状态和链接，不重复页面或组件规格
doc_sync: 四张卡与 UI 覆盖索引、数据库/安全权威一致（R5）
---

# T0-RECONCILE-UI-OFFLINE-OPERATIONS

## 产出

把备份、恢复、媒体清理、可选 remediation 和最终 smoke 验收对齐到离线优先体验合同，明确核心 app 无网可用，只有显式远程 provider 在离线时降级。

## 验收

执行 front matter 的 `dod_command`，并确认没有扩大网络能力。
