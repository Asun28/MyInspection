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
non_goals:
  - 云端日志平台、飞书/钉钉告警、远程 admin、自动修复、数据库编辑器
  - 性能优化实现（由各 owning card 修；本卡只消费越阈事件和发布证据）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleRelease
dod_exit: 0
dod_assert: HealthStateReducer 只从 typed events/authoritative receipts 产生 BACKUP_STALE_7D、BACKUP_FAILED_3X、INTEGRITY_FAILED、RESTORE_ROLLED_BACK、PREVIOUS_CRASH 与 STARTUP_SLOW 状态，事件到可见提示 ≤1s 且每状态只有一个明确恢复动作；崩溃边界只保存 exception class/build id/allowlisted frame identifiers/reason code，不存 message/业务内容并在下次启动一次性提示；diagnostics 写坏/满不影响业务；每个 release 生成受保护符号回执：minified 构建记录 build id/mapping SHA-256/受控存放确认并以固定 obfuscated fixture 本地反混淆，未混淆构建显式 NOT_MINIFIED 且不得宣称存在 mapping；app unit tests、assembleRelease 与 release checklist 断言全绿
review_gate: codex {verdict:pass}
hygiene: 每个阈值、脱敏字段和 mapping 回执条件各有单点变异，任何删除/放宽均命中具名失败（R4）
doc_sync: SECURITY、DATABASE-DESIGN、TASK-BOARD 与 T7-SMOKE-POLISH 记录 release 证据（R5）
---

# T7-LOCAL-HEALTH-RELEASE

## 本机健康状态

健康提示是本机派生视图，不是另一份业务真相：上次 verified backup 时间来自回执，连续失败和恢复回滚来自 typed operation events，完整性来自只读检查。每个状态在来源变化后 1 秒内更新，并只提供一个恢复动作：`Back up now`、`Open backup settings`、`Export diagnostics`、`Review restore result` 或 `Restart safely`。普通单次失败仍留在所属页面，不升级成全局告警。

## 崩溃边界

崩溃处理器只落一个 no-backup 的最小 marker：构建 ID、异常类、allowlisted/有界帧标识、时间和稳定 reason code。禁止异常 message、地址、姓名、备注、路径、URI、payload、token 或内存转储。下次启动先恢复业务真相，再显示一次 `The app closed unexpectedly` 与 `Export diagnostics`；marker 转成脱敏事件后删除。marker/事件写入失败不得制造启动循环。

## Release mapping 证据

每个 release 构建都生成一份不入 APK/仓库/诊断包的本地受保护回执。minified 构建记录 build ID、version、mapping 文件 SHA-256、生成时间和受控存放确认；固定混淆 fixture 必须能用同一 build ID 的 mapping 本地还原，错版本 mapping 必须拒绝。未混淆构建只记录 build ID/version/`NOT_MINIFIED`，不得伪造 mapping 路径或反混淆成功。

## 诚实边界

v1 没有遥测、后台上传或远程秒级告警。本卡所称“秒级”只指本机事件到可操作 UI 状态的延迟。用户主动导出的诊断包仍由 `T5-DIAGNOSTIC-EXPORT` 控制包含/排除与临时分享授权。
