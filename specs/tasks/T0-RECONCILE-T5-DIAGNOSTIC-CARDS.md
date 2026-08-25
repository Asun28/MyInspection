---
id: T0-RECONCILE-T5-DIAGNOSTIC-CARDS
title: 登记本机事件、诊断导出、数据清除与发布健康四张实现卡
depends_on: [T0-RECONCILE-T1-SECURITY-CARDS]
status: todo
branch: T0-RECONCILE-T5-DIAGNOSTIC-CARDS
worktree: C:\wt\T0-RECONCILE-T5-DIAGNOSTIC-CARDS
allow_paths:
  - specs/tasks/T5-OPERATION-EVENT-STORE.md
  - specs/tasks/T5-DIAGNOSTIC-EXPORT.md
  - specs/tasks/T5-LOCAL-DATA-ERASURE.md
  - specs/tasks/T7-LOCAL-HEALTH-RELEASE.md
forbid:
  - 修改产品代码或建立远程 admin、遥测、自动上传
  - 把诊断包当备份、把事件库放进证据哈希域或主备份
non_goals:
  - 实现四张卡或同步 Task Board/技术债
  - 修改 T1 安全卡
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 && pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T5-OPERATION-EVENT-STORE.md' -Pattern '^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T1-LOCAL-DATA-SECURITY\]$') -and (Select-String -Path 'specs/tasks/T5-OPERATION-EVENT-STORE.md' -Pattern '^dod_assert: .*独立 diagnostics DB.*飞行模式全绿$') -and (Select-String -Path 'specs/tasks/T5-DIAGNOSTIC-EXPORT.md' -Pattern '^depends_on: \[T5-OPERATION-EVENT-STORE, T1-SHARE-SCREEN-PRIVACY\]$') -and (Select-String -Path 'specs/tasks/T5-DIAGNOSTIC-EXPORT.md' -Pattern '^dod_assert: .*admin/support 无写入口$') -and (Select-String -Path 'specs/tasks/T5-LOCAL-DATA-ERASURE.md' -Pattern '^dod_assert: .*精确 `ERASE`.*first-run') -and (Select-String -Path 'specs/tasks/T7-LOCAL-HEALTH-RELEASE.md' -Pattern '^  - 遥测/崩溃上传 SDK'))) { exit 1 }"
dod_exit: 0
dod_assert: 四张卡按 typed event store→用户授权导出/物理清除→本机健康与发布证据拆分，失败隔离、脱敏、保留上限和离线验收均明确
review_gate: codex {verdict:pass}
hygiene: 事件、导出、清除、健康各有单一 owner；无通用自由文本日志或远程控制面
doc_sync: 卡片引用已合并的数据库/安全权威与既有备份卡（R5）
---

# T0-RECONCILE-T5-DIAGNOSTIC-CARDS

## 产出

登记四张后续卡，把 admin/debug 需要转化为用户授权、离线、脱敏、只读且不影响业务结果的本机诊断能力，同时提供无账号场景的真实本机数据清除与发布证据。

## 验收

执行 front matter 的 `dod_command` 和 `scripts/check-cards.ps1`。
