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
acceptance:
  - "A1 Backup IO：offline/data-protection plan_ref + A1–A5，覆盖 v1 full-only、10 states/phases、verified receipt、staging/rollback、provider/authorization/storage/secret failure 与本地/USB 飞行模式"
  - "A2 Media retention：同 plan_ref + A1–A4，覆盖 1/3/5/10/Always、preflight/protected refs/30天、confirmation/progress/recovery、hash/size 回填与不删 DB/PDF/backup/cloud"
  - "A3 Remediation：同 plan_ref + A1–A4，覆盖 on-device first、remote explicit/cancellable、offline fallback、safe payload/source/disclaimer、永不阻断 finalize/report"
  - "A4 Smoke：primary journey plan_ref + A1–A5，覆盖 flight-mode end-to-end、provider/permission/storage/process-death、backup/restore/erase/health/share、TalkBack/200%/theme/performance 和 exact evidence"
  - "A5 去重与边界：四卡无 component-matrix headings，不复制 DESIGN；runtime network 仍只在 remediation 用户显式动作，任务卡不实现功能或修改冻结备份格式"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T5-BACKUP-IO.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and ((Select-String -Path 'specs/tasks/T5-BACKUP-IO.md' -Pattern '^  - .A[1-5] ').Count -eq 5) -and (Select-String -Path 'specs/tasks/T5-BACKUP-IO.md' -Pattern '^  - .A[1-5] .*full-only.*NOT_CONFIGURED.*FAILED.*verified receipt') -and (Select-String -Path 'specs/tasks/T5-LOCAL-MEDIA-RETENTION.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and ((Select-String -Path 'specs/tasks/T5-LOCAL-MEDIA-RETENTION.md' -Pattern '^  - .A[1-4] ').Count -eq 4) -and (Select-String -Path 'specs/tasks/T5-LOCAL-MEDIA-RETENTION.md' -Pattern '^  - .A[1-4] .*1/3/5/10/Always.*30.*hash/size') -and (Select-String -Path 'specs/tasks/T7-REMEDIATION.md' -Pattern '^plan_ref: context/DESIGN.md#offline-and-data-protection-experience$') -and ((Select-String -Path 'specs/tasks/T7-REMEDIATION.md' -Pattern '^  - .A[1-4] ').Count -eq 4) -and (Select-String -Path 'specs/tasks/T7-REMEDIATION.md' -Pattern '^  - .A[1-4] .*on-device.*remote.*offline.*finalize.*report') -and (Select-String -Path 'specs/tasks/T7-SMOKE-POLISH.md' -Pattern '^plan_ref: context/DESIGN.md#primary-inspection-journey$') -and ((Select-String -Path 'specs/tasks/T7-SMOKE-POLISH.md' -Pattern '^  - .A[1-5] ').Count -eq 5) -and (Select-String -Path 'specs/tasks/T7-SMOKE-POLISH.md' -Pattern '^  - .A[1-5] .*flight-mode.*backup.*restore.*erase.*health.*TalkBack.*200%') -and (-not (Select-String -Path 'specs/tasks/T5-BACKUP-IO.md','specs/tasks/T5-LOCAL-MEDIA-RETENTION.md','specs/tasks/T7-REMEDIATION.md','specs/tasks/T7-SMOKE-POLISH.md' -Pattern '^### .*component matrix$')))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 通过：四个 plan_ref、5/4/4/5 acceptance counts、全量备份状态/媒体保护/remediation 降级/飞行模式 smoke 关键锚点完整，且无复制 matrix
review_gate: codex {verdict:pass}
hygiene: 卡内只保留可验收状态和链接，不重复页面或组件规格
doc_sync: 四张卡与 UI 覆盖索引、数据库/安全权威一致（R5）
---

# T0-RECONCILE-UI-OFFLINE-OPERATIONS

## 产出

把备份、恢复、媒体清理、可选 remediation 和最终 smoke 验收对齐到离线优先体验合同，明确核心 app 无网可用，只有显式远程 provider 在离线时降级。

## 验收

执行 front matter 的 `dod_command`，并确认没有扩大网络能力。
