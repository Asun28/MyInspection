---
id: T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER
title: 照片孤儿清理：durable sidecar lease + WorkManager 生产调度（偿还 TD14）
depends_on: [T2-PHOTO-PIPELINE]
status: todo
branch: T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER
worktree: C:\wt\T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/
  - android/core/src/test/kotlin/nz/myinspection/core/media/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
forbid:
  - 改动 sqldelight、FrozenPaths、照片路径/哈希/finalized 只读语义，或新增运行时依赖
  - 使用 externalFilesDir、扫描 mediaRoot 以外路径，或无验证地删除任意 .jpg
  - 改动 scripts/selftest.ps1、引入网络/账号/云端或把 TD10 多连接债当作单连接功能的阻断理由
non_goals:
  - 以 schema 版本评审实现 FS+DB 共享临界区；本卡只用文件侧 lease + 既有 selectById
  - 修复历史或损坏的 cross-ID rel_path alias；全局 rel_path 活跃性查询须等 TD4 后另开 follow-up
  - 照片保留策略、备份、导入 UI、跨物业去重、重压已存/已 finalize 照片
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 目标 JPEG 的同目录 .jpg.pending lease 在 publish 前 durable 创建、跨 publish 到 record 全程独占且只在 Recorded 或已补偿的 RejectedByGuard 后移除；worker 只枚举受限的 photos 三层 sidecar、过 MediaPaths 形状闸并以 selectById(photoId)+精确 rel_path 复核活跃引用，活跃引用只移 marker，非活跃才删 asset 再删 marker；marker/旧 soft-deleted 清理的 rejected=failure、任一 IO/锁失败=retry、deleted/readopted=success；AndroidSqliteDriver 每径关闭且 close/marker cleanup 不替换主异常；唯一 24 小时 KEEP+StorageNotLow 调度、目标 core media 测试与 assembleDebug 全绿
review_gate: codex {verdict:pass}
hygiene: 仅保留能分别杀掉 marker 早建、lease 缺失、活跃 rel_path 复核、结果映射、driver close 与两条管线接线的单点变异（R4）
doc_sync: TD14 状态、TASK-BOARD 备注与 runtime storage 约定（R5）
---

# T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER

## 产出

把现有 `OrphanedAssetCleanup` / `PhotoAssetCleanupExecutor` 接入真实 Android 周期任务，并为“落盘成功、入库前进程死亡或补偿失败”的无行 JPEG 增加一个最小的文件侧恢复记录。它偿还 TD14 的正常 ingest 路径，不新增 schema、查询或依赖。

## 运行时存储组成

新增一个 app/media 内的唯一运行时存储组成点：SQLite 名固定为 `myinspection.db`，媒体根固定为
`File(context.filesDir, "media")`。`PhotoOrphanCleanupWorker` 用这两个值构造 `AndroidSqliteDriver`、
`MyInspectionDatabase` 与 `PhotoAssetCleanupExecutor`；未来 ingest 的 composition 也必须消费同一来源，不能各自拼 DB 名或根目录。

这里故意不用 `externalFilesDir`：外部存储可缺席或未挂载，而后台回收必须仍能运行；内部 `file` 域已在现有备份规则中整体排除。

## sidecar lease 生命周期

只在 `PhotoIngestPlan.WriteNewAsset` 路径执行，复用既有物理文件不创建 marker。

1. 目标 JPEG 已 staged、计划已得出后，在同目录原子创建空的 `<photoId>.jpg.pending`；关闭/同步成功后取得其独占文件锁。取得 marker 或锁失败时不得 publish。
2. 保持该 lock 贯穿 `publish`、`PhotoAssociationRecorder.record` 与结果判定；任何主失败仍由既有 stager/recorder 的清理和异常语义主导，marker 留给恢复任务。
3. 仅在 `Recorded`，或 `RejectedByGuard(orphanedFileRemains=false)` 已确认补偿撤掉 JPEG 时移除 marker；marker 移除失败只记录并留给 worker，不得伪造“入库失败”或覆盖已有主异常。lock 总在 finally 释放，close/cleanup 异常须作为主异常的 suppressed 原因保留。
4. 进程若死在 marker 前，不会 publish；死在 marker 后 publish 前，worker 只清 marker；死在 publish 后 record 前，worker 删除无行 JPEG；DB 已提交但 marker 未删时，worker 只删 marker；补偿失败时，worker 回收 JPEG。

同一 `<photoId>.jpg.pending` 也是跨 ingest 与 worker 的 lease：worker 拿不到锁就 retry，绝不在一次仍可能 record 的操作旁删除 JPEG。现有 canonical 路径由 `photoId` 派生，故恢复只接受 `selectById(photoId)` 返回的活跃行且 `rel_path` 精确相同；历史/损坏的 cross-ID alias 不在本卡冒险处理。

## Worker 与调度

- 从 `MainActivity` 的 app 启动路径调用 scheduler；使用一个稳定唯一名、24 小时 `PeriodicWorkRequest`、`ExistingPeriodicWorkPolicy.KEEP` 与 `setRequiresStorageNotLow(true)`。
- worker 只逐层列举 `mediaRoot/photos/<property>/<inspection>/<photo>.jpg.pending`，不做全盘递归；去掉 `.pending` 后必须通过 `MediaPaths.isPhotoRelPathShape`，否则标为 rejected，既不删 marker 也不删 JPEG。
- 取得 marker lock 后，先用既有 `selectById(photoId)` + 活跃/精确路径复核：已被采用的资产只删除 marker，计为 readopted；否则经 `PhotoAssetCleanupExecutor` 删除 JPEG，再删除 marker。目标已不存在仍算删除成功。
- 同一 run 再执行现有 `OrphanedAssetCleanup`，覆盖已 soft-delete 且无活跃引用的旧行资产。合并结果中 rejected 为 `Result.failure()`，任一文件/锁/环境性失败为 `Result.retry()`，其余 deleted/readopted 为 `Result.success()`；未知契约/程序错误 fail closed。`AndroidSqliteDriver` 必须在每条返回路径关闭，关闭失败不可把失败伪装成成功。

## 验收与资源边界

目标测试必须用真实临时目录覆盖上述 crash/recovery 顺序、lease 竞争、活跃重采用、缺失 JPEG、形状拒绝、删除失败和 driver close；同时以 source/wiring 断言两条 ingest 都在 publish 前建 marker、record 后才清 marker，并走既有清理器。`assembleDebug` 是 Android/WorkManager API 接线证明；不跑 full selftest。

`T1-SPIKE-PLATFORM` 当前占用宽泛 app 资源，故它是开始/合并时的资源串行前提，不是本卡业务 `depends_on`；本卡不等待 spike 的产品结论。

## 执行建议

首选 GPT-5.6 Terra · max；备选 Sonnet 5 · max。难度 M。
