---
id: T7-LOCAL-HEALTH-RELEASE
title: 本机健康与发布证据：秒级可操作提示 + 脱敏崩溃恢复 + release mapping 回执
depends_on: [T5-OPERATION-EVENT-STORE, T5-DIAGNOSTIC-EXPORT, T5-BACKUP-IO, T1-LOCAL-DATA-SECURITY]
status: todo
branch: T7-LOCAL-HEALTH-RELEASE
worktree: C:\wt\T7-LOCAL-HEALTH-RELEASE
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/kotlin/nz/myinspection/app/health/
  - android/app/src/test/kotlin/nz/myinspection/app/health/
  - docs/RELEASE-CHECKLIST.md
forbid:
  - 遥测/崩溃上传 SDK、后台/Wi-Fi 自动上报、远程告警、账号/设备指纹、原始 stack message 或业务字段落盘
  - 让诊断/健康写入失败改变 capture/finalize/PDF/backup/restore 结果；把本机提示描述为服务端实时监控
  - mapping/符号表入 APK、公开仓库或诊断包；无映射回执仍宣称 release 可反混淆
  - 禁止遥测/自动上传、远程 admin；诊断/健康不得写 finalized evidence；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - 云端日志平台、飞书/钉钉告警、远程 admin、自动修复、数据库编辑器
  - 性能优化实现（由各 owning card 修；本卡只消费越阈事件和发布证据）
health_states: [BACKUP_STALE_7D, BACKUP_FAILED_3X, INTEGRITY_FAILED, RESTORE_ROLLED_BACK, PREVIOUS_CRASH, STARTUP_SLOW]
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleRelease
dod_exit: 0
dod_assert: HealthStateReducer 只从 typed events/authoritative receipts 产生 BACKUP_STALE_7D、BACKUP_FAILED_3X、INTEGRITY_FAILED、RESTORE_ROLLED_BACK、PREVIOUS_CRASH 与 STARTUP_SLOW 状态，跨 retention 保持 latch，跨不同 occurrence 清除全局状态，事件到可见提示 ≤1s 且每状态只有一个明确恢复动作；崩溃边界只保存 source kind/exception class/build id/allowlisted frame identifiers/reason code，不存 message/业务内容，Android 11+ 只映射明确异常 ApplicationExitInfo、pre-30 只认原子 uncaught marker，同一 source 只提示一次；诊断/健康写入失败不改变任何业务结果；每个 release 生成受保护符号回执：minified 构建记录 build id/mapping SHA-256/受控存放确认并以固定 obfuscated fixture 本地反混淆，错 build 拒绝，未混淆构建显式 NOT_MINIFIED 且不得宣称存在 mapping；app unit tests、assembleRelease 与 release checklist 断言全绿
review_gate: codex {verdict:pass}
hygiene: 每个阈值、脱敏字段和 mapping 回执条件各有单点变异，任何删除/放宽均命中具名失败（R4）
doc_sync: SECURITY、DATABASE-DESIGN、TASK-BOARD 与 T7-SMOKE-POLISH 记录 release 证据（R5）
---

# T7-LOCAL-HEALTH-RELEASE

## 本机健康状态

健康提示是本机派生视图，不是另一份业务真相。上次 verified backup 时间来自回执，连续失败/回滚来自 typed events 与 materialized latch，完整性按 MAIN_DB/BACKUP/RESTORE source 隔离。每状态在来源变化后 1 秒内更新并只有一个恢复动作。普通单次失败留在所属页面，不升级全局告警。

## 崩溃边界

Android 11+ 仅把 CRASH/CRASH_NATIVE/ANR/INITIALIZATION_FAILURE/EXCESSIVE_RESOURCE 映射为 actionable reason；低内存、用户停止、更新等不提示，source ID 只由 immutable exit fields 派生且不读取 description/trace/summary。Android 10 及以下只认 no-backup 原子 uncaught-Java marker，不从缺少 clean shutdown 推断。durable claim ledger 保证同一 source 至多一次提示，即使诊断写入失败也不重复。

## Release mapping 证据

每个 release 构建生成不入 APK/仓库/诊断包的本地受保护回执。minified 构建记录 build ID、version、mapping SHA-256、生成时间与受控存放确认；固定 obfuscated fixture 必须以同 build mapping 还原，错 build 拒绝。未混淆构建只记录 `NOT_MINIFIED`。

## 诚实边界

v1 没有遥测、后台上传或远程秒级告警。“秒级”只指本机事件到 UI 的延迟。用户主动导出的诊断包仍由 `T5-DIAGNOSTIC-EXPORT` 控制。
