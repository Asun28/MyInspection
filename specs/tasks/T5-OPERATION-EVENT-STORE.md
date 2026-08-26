---
id: T5-OPERATION-EVENT-STORE
title: 独立本机诊断库：有界脱敏 operation_event 与失败隔离
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T1-LOCAL-DATA-SECURITY]
plan_ref: docs/DATABASE-DESIGN.md#7-diagnostics-database-schema
status: todo
branch: T5-OPERATION-EVENT-STORE
worktree: C:\wt\T5-OPERATION-EVENT-STORE
allow_paths:
  - android/core/build.gradle.kts
  - android/core/src/main/sqldelight/nz/myinspection/core/diagnostics/
  - android/core/src/main/kotlin/nz/myinspection/core/diagnostics/store/
  - android/core/src/test/kotlin/nz/myinspection/core/diagnostics/store/
forbid:
  - 把诊断日志写入主证据库、canonical hash、PDF、通知或加密备份
  - 记录地址、姓名、联系方式、备注/转写、媒体/hash、路径/URI、文件名、口令/key/token/header 或原始异常体
  - 联网发送、远程管理、账号/RBAC、通用自由文本 logger
  - 禁止遥测/自动上传、远程 admin；诊断/健康不得写 finalized evidence；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - 面向支持人员的导出 UI（交 T5-DIAGNOSTIC-EXPORT）
  - 崩溃上报 SaaS、远程告警、跨设备日志聚合、法定不可否认审计
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.diagnostics.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
dod_exit: 0
dod_assert: 独立 diagnostics DB 的 diagnostic_run/operation_event/diagnostic_counter/diagnostic_health 与 registry v1 精确匹配设计权威，所有表 UUIDv7 PK+updated_at/deleted_at，sequence_no 由事务 counter 唯一分配且回滚不泄漏；run-stable context 不在 event 重复；typed recorder 拒绝未知 code/reason/outcome/scope/key、CRLF、超 2KiB context 与禁记字段；同毫秒/时钟回拨只按 sequence 排序，health latch 跨 90 天或 20000 event 裁剪仍保持且只由声明 transition 清除；首个 run/APP_START 原子、零事件遗留可裁剪；注入 DB 损坏/空间满/写异常时业务调用结果不变；数据库不进入 .mibk/Android backup；飞行模式全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 更新 TD136 子卡进度；docs/SECURITY.md 与 TASK-BOARD 记录证据（R5）
version_review: this card = the version review
---

# T5-OPERATION-EVENT-STORE

## 产出

新增独立 `MyInspectionDiagnosticsDatabase`。本卡以 version review 建立独立 diagnostics schema version、对应 `.sqm` 与 schema snapshot。`diagnostic_run` 归一化进程稳定环境，`operation_event` 保存 UUIDv7 事件 ID 与数据库分配的因果 `sequence_no`，`diagnostic_counter` 在同事务分配序号，`diagnostic_health` 保存有界 latch。普通读取只见 `deleted_at IS NULL`。retention owner 只能软删/分批物理裁剪过期 event 及已无 active event 的 orphan run；counter 行永不删除，active/global health 永不裁剪，只有超过 90 天的 inactive scoped health 行可软删后分批物理裁剪。

事件只能由 sealed/typed `OperationEventRecorder` 产生。operation、outcome、reason、scope、context 和健康转移必须逐项实现 `docs/DATABASE-DESIGN.md` 的 registry v1；未知值 fail closed。备份只记 terminal `BACKUP_RESULT`，不记 start；周期触发与每次逻辑 occurrence 的 UUIDv7 correlation ID 分离，retry 复用同一 occurrence。

## 失败与隐私语义

- 业务事务先按自身真相完成；事件写入或健康投影失败只返回内部状态，不改变 capture/finalize/PDF/backup/restore 的结果。
- 原始异常只在内存映射为闭集 reason/context；无自由文本、路径、URI、hash、业务字段或凭据。
- run + APP_START + 已确定的 previous-crash/slow latch 在单一事务完成；失败不留下故意的 run-only 行。
- `sequence_no` 是唯一因果顺序；时间戳只用于显示和保留期。
- 诊断库不是 finalized evidence，不加入主库哈希、PDF 或备份。

## 验收

闭集 registry 的每个 operation/reason/context/scope/health transition 都有正例、非法组合与单点删除变异。测试覆盖并发/回滚 counter、时钟回拨、两个 retention 边界、每个 active latch 的保留/清除、两个 weekly occurrence 与一次 process-death retry、receipt revision source isolation、crash dedup、PDF variant isolation，以及真实领域用例中的 logger failure isolation。
