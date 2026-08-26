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
  - android/core/src/main/kotlin/nz/myinspection/core/diagnostics/
  - android/core/src/test/kotlin/nz/myinspection/core/diagnostics/
forbid:
  - 把诊断日志写入主证据库、canonical hash、PDF、通知或加密备份
  - 记录地址、姓名、联系方式、备注/转写、媒体/hash、路径/URI、文件名、口令/key/token/header 或原始异常体
  - 联网发送、远程管理、账号/RBAC、通用自由文本 logger
non_goals:
  - 面向支持人员的导出 UI（交 T5-DIAGNOSTIC-EXPORT）
  - 崩溃上报 SaaS、远程告警、跨设备日志聚合、法定不可否认审计
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.diagnostics.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:check
dod_exit: 0
dod_assert: 独立 diagnostics DB 仅含设计基线的 diagnostic_run/operation_event 字段与索引，run-stable app/schema/device context 不在 event 重复；typed recorder 拒绝未知 code/key、CRLF、超 2KiB context 与禁记字段；90 天或 20000 event 行先到即小批物理裁剪，最后事件删除后才清 orphan run；注入 DB 损坏/空间满/写异常时业务调用结果不变；数据库不进入 .mibk/Android backup；飞行模式全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: specs/tech-debt-tracker.md 更新 TD136 子卡进度；docs/SECURITY.md 与 TASK-BOARD 记录证据（R5）
---

# T5-OPERATION-EVENT-STORE

## 产出

新增独立 `MyInspectionDiagnosticsDatabase`：`diagnostic_run` 归一化一次进程内不变的 app/schema/device context，append-only `operation_event` 只持有 `run_id`。事件只能由 sealed/typed `OperationEventRecorder` 产生；字段、约束、索引、保留期和敏感数据禁列以 `docs/DATABASE-DESIGN.md` 为准。

首批只覆盖低频关键路径：app start/slow start、previous-crash recovery marker、inspection create/finalize、supplement、media ingest、notice、contact purge、backup、restore、media cleanup/rehydration、diagnostics export、integrity check。不要记录每次文字输入、条目 autosave、逐帧或逐图片性能事件；性能只存有界聚合与越阈 reason code。

## 失败语义

- 业务事务先按自身真相完成；事件写入失败只返回给内部 health 状态，不改变业务成功/失败。
- 非成功事件使用稳定 reason code；原始异常只在内存映射，绝不落库。
- 裁剪只由 maintenance owner 执行；表没有 update/soft-delete API。
- 不做伪安全哈希链。诊断库不是 finalized evidence，root/恶意 OS 仍在既定不保护边界内。

## 验收

见 front-matter。每个敏感字段禁记规则至少有一枚可证伪测试；logger failure 必须在一个真实领域用例旁证明业务结果与主库状态不变。
