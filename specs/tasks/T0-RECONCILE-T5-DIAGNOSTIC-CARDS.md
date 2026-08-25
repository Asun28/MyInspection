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
acceptance:
  - "A1 EVENT-STORE 卡：depends migration+local security；恰有 core build/diagnostics SQL/main/test 4 paths；独立 DB、typed allowlist、2KiB、90天/20000行、orphan run、满盘/损坏失败隔离和飞行模式均在 DoD"
  - "A2 DIAGNOSTIC-EXPORT 卡：depends event store+share privacy；恰有 core export main/test 与 app feature main/test/androidTest 5 paths；7/30/90天、manifest/hash、SAF/content URI、取消清理、禁记字段与 admin 只读均在 DoD"
  - "A3 LOCAL-DATA-ERASURE 卡：depends backup+两安全卡；恰有 core/app erasure main/test 4 paths；完整类别计划、外部 mibk 保留、ERASE、失败不伪成功、first-run 重启均在 DoD"
  - "A4 LOCAL-HEALTH-RELEASE 卡：depends event/export/backup/security；恰有 app build/health main/test/release checklist 4 paths；六状态、1秒动作、脱敏 crash、mapping SHA/反混淆与 NOT_MINIFIED 均在 DoD"
  - "A5 隔离和顺序：四卡 allow_paths 不重叠；event→export/health 依赖明确；诊断失败永不改变业务，所有卡禁止遥测/自动上传/远程 admin/证据写入口并通过 check-cards"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 && pwsh -NoProfile -Command "if (-not ((Select-String -Path 'specs/tasks/T5-OPERATION-EVENT-STORE.md' -Pattern '^depends_on: \[T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T1-LOCAL-DATA-SECURITY\]$') -and ((Select-String -Path 'specs/tasks/T5-OPERATION-EVENT-STORE.md' -Pattern '^  - android/core/(build\.gradle\.kts|src/main/sqldelight/nz/myinspection/core/diagnostics/|src/main/kotlin/nz/myinspection/core/diagnostics/|src/test/kotlin/nz/myinspection/core/diagnostics/)$').Count -eq 4) -and (Select-String -Path 'specs/tasks/T5-OPERATION-EVENT-STORE.md' -Pattern '^dod_assert: .*diagnostic_run/operation_event.*2KiB.*90 天.*20000.*业务调用结果不变.*飞行模式全绿$') -and (Select-String -Path 'specs/tasks/T5-DIAGNOSTIC-EXPORT.md' -Pattern '^depends_on: \[T5-OPERATION-EVENT-STORE, T1-SHARE-SCREEN-PRIVACY\]$') -and ((Select-String -Path 'specs/tasks/T5-DIAGNOSTIC-EXPORT.md' -Pattern '^  - android/(core/src/(main|test)/kotlin/nz/myinspection/core/diagnostics/export/|app/src/(main|test|androidTest)/kotlin/nz/myinspection/app/feature/diagnostics/)$').Count -eq 5) -and (Select-String -Path 'specs/tasks/T5-DIAGNOSTIC-EXPORT.md' -Pattern '^dod_assert: .*7 天.*90 天.*manifest.*SAF.*content://.*取消/失败.*admin/support 无写入口$') -and ((Select-String -Path 'specs/tasks/T5-LOCAL-DATA-ERASURE.md' -Pattern '^  - android/(core/src/(main|test)/kotlin/nz/myinspection/core/privacy/erasure/|app/src/(main|test)/kotlin/nz/myinspection/app/feature/settings/erasure/)$').Count -eq 4) -and (Select-String -Path 'specs/tasks/T5-LOCAL-DATA-ERASURE.md' -Pattern '^dod_assert: .*主/诊断 DB.*外部 `.mibk` 保留.*`ERASE`.*不得显示成功.*first-run') -and ((Select-String -Path 'specs/tasks/T7-LOCAL-HEALTH-RELEASE.md' -Pattern '^  - (android/app/build\.gradle\.kts|android/app/src/main/kotlin/nz/myinspection/app/health/|android/app/src/test/kotlin/nz/myinspection/app/health/|docs/RELEASE-CHECKLIST\.md)$').Count -eq 4) -and (Select-String -Path 'specs/tasks/T7-LOCAL-HEALTH-RELEASE.md' -Pattern '^dod_assert: .*BACKUP_STALE_7D.*STARTUP_SLOW.*≤1s.*mapping SHA-256.*NOT_MINIFIED') -and (Select-String -Path 'specs/tasks/T7-LOCAL-HEALTH-RELEASE.md' -SimpleMatch '遥测/崩溃上传 SDK'))) { exit 1 }"
dod_exit: 0
dod_assert: A1–A5 完整矩阵通过：4/5/4/4 exact allow paths、依赖、typed schema/retention/redaction/failure isolation/erasure/release 全 DoD 关键字及 check-cards 均成立
review_gate: codex {verdict:pass}
hygiene: 事件、导出、清除、健康各有单一 owner；无通用自由文本日志或远程控制面
doc_sync: 卡片引用已合并的数据库/安全权威与既有备份卡（R5）
---

# T0-RECONCILE-T5-DIAGNOSTIC-CARDS

## 产出

登记四张后续卡，把 admin/debug 需要转化为用户授权、离线、脱敏、只读且不影响业务结果的本机诊断能力，同时提供无账号场景的真实本机数据清除与发布证据。

## 验收

执行 front matter 的 `dod_command` 和 `scripts/check-cards.ps1`。
