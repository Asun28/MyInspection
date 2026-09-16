---
id: T1-SAFE-MEDIA-LOGGING
title: 安全日志底座与四处媒体接线（跨层日志断言同步）
depends_on: [T1-SPIKE-PLATFORM]
status: merged
branch: T1-SAFE-MEDIA-LOGGING
worktree: C:\wt\T1-SAFE-MEDIA-LOGGING
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/SafeLog.kt
  - android/app/src/main/kotlin/nz/myinspection/app/media/MediaFileStore.kt
  - android/app/src/main/kotlin/nz/myinspection/app/media/PhotoImportPipeline.kt
  - android/app/src/main/kotlin/nz/myinspection/app/media/PhotoIngestPendingLease.kt
  - android/app/src/main/kotlin/nz/myinspection/app/media/PhotoOrphanCleanupWorker.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/SafeLogTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/media/PhotoOrphanCleanupWiringTest.kt
forbid:
  - 运行期出站网络；修改冻结 schema/backup format；原始路径、业务数据、secret、Throwable/message/stack 写日志
  - 修改现有媒体根、数据库落位、调度/删除/发布/回滚语义；新增运行时依赖
non_goals:
  - AppStoragePolicy、LocalSecretBox（由 T1-LOCAL-DATA-SECURITY 交付）
  - 持久诊断库、账号、遥测、UI、存储迁移
requirements:
  - "R1 当现有媒体操作记录失败时，仅输出已批准 operation/reason 与 opaque id/count/duration，不输出路径、URI、业务原文或原始 Throwable。"
  - "R2 当日志接收端失败时，不改变媒体清理/导入操作原本的结果。"
acceptance:
  - "A1 [R1] MediaFileStore、PhotoImportPipeline、PhotoIngestPendingLease 和 PhotoOrphanCleanupWorker 的真实失败日志边界夹具均保留精确的批准 operation/reason；Worker 的 pending/soft-delete 与 rejected/failed 区别由封闭 reason 保留。"
  - "A2 [R1] 最终日志字节不含绝对路径、SAF URI、地址、姓名、备注、secret、Authorization、raw provider body；原始 Throwable、嵌套 cause/suppressed 的 message/stack 哨兵均不泄露。删除脱敏边界后对应负例失败。"
  - "A3 [R1] SafeLog API 不接收业务自由文本或 Throwable；opaque id 受格式约束，count/duration 受值域约束；四处生产日志接线均通过真实调用验证，不能只测孤立模板。"
  - "A4 [R2] 注入抛异常的 sink 后，原失败边界仍保持既有返回/异常与后续清理行为。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug :core:test --tests "nz.myinspection.core.media.PhotoOrphanCleanupWiringTest"
dod_exit: 0
dod_assert: app 平台日志测试与 assemble 通过；core 原有媒体存储/调度/生命周期接线检查保留并通过，仅替换迫使原始路径/异常日志的过时断言；A1-A4 均有真实输出与失败负例，R4 具名单点变异证明各泄露边界和日志存在性有效
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: SECURITY + TASK-BOARD（R5）
---

# T1-SAFE-MEDIA-LOGGING

## 来源与范围

从 T1-LOCAL-DATA-SECURITY 拆出安全日志这一独立前置，原因是 RED 前自然可读实现/测试/R4 合计预估 905–1092 行，超过首轮 800 行预算且可能超过 R3 硬上限。用户已批准 PhotoOrphanCleanupWiringTest.kt 最小范围修订，并授权按推荐方案自主修卡。

本卡只交付 SafeLog 和四处媒体失败日志接线。允许路径跨 platform/media/core-test 是同一日志边界的完整覆盖；保留 core 测试所有无关存储、调度、生命周期断言。禁止为维持源码子串检查在注释或死代码中保留旧日志。

## 上下文包

必读 ADR-0006 第2节、SECURITY 第2.4节、四个允许的 media 源码及 PhotoOrphanCleanupWiringTest。SafeLog 采用封闭 operation/reason 与受约束 opaque id/count/duration；Android Log 仅为末端薄适配。JVM 用注入 sink 捕获生产边界的最终字符串，不引入 Robolectric 或测试默认返回值开关。

R4 点名删除日志/绕过字段边界/输出 Throwable 或路径后相应测试必须失败，且每枚变异须编译通过、还原源文件 SHA 一致。首轮按真实 additions+deletions 估计 427–620 changed lines，目标 400–650，达到约 800 前重新评估；正式脚本预算上限不变。

## 纯 JVM 的真实失败边界

不宣称 JVM 执行 Android Bitmap 解码或 Worker 构造。只提取并执行生产正在调用的故障控制流，SafeLogTest 直接调用这些 app 内边界，捕获同一 SafeLog 最终 sink 字符串；不另建 formatter 替身。

- MediaFileStore：实际 copyInto 的复制、发布及 finally 都运行在真实临时目录；包内重载注入临时删除结果和安全 sink。删除返回 false、sink 抛 RuntimeException 时，已发布字节、返回 File 与主异常保持原语义。
- PhotoImportPipeline：仅抽取原 finally 中 delete、日志和 primary/suppressed 小段为内部 cleanupImportTemp；原 finally 直接调用。测试实际删除失败和异常抑制分支，含主异常存在/不存在及 sink 异常；不重排 Bitmap ingest 大段代码。
- PhotoIngestPendingLease：app 内构造接收窄 close 动作与安全 sink；正式 acquire 仍捕获真实 PendingPhotoLease 并调用 closeAfterAssetDeletion，core 接口/可见性不变。JVM 注入确定性的 close=false 或带多层异常的 close，直接执行本类 finish/close，或经真实 VerifiedAssetWorkflow 的完成后清理回调，验证结果、disposition 与日志。不得依赖 Windows 文件锁偶发拒删制造 false。
- PhotoOrphanCleanupWorker：将现有 issues/execution.failure 的遍历与日志输出收为本类内部 reportFailures，由 doWork 调用。JVM 用真实 PhotoOrphanCleanupExecution.run 的结果及公开 PhotoOrphanCleanupIssue 夹具集合驱动所有四种 bucket/result 与 execution failure；生产集合仍取自 cleanupReport.issues()；断言 retry/failure 决策、资源关闭状态、最终日志及 sink 异常下不变。

接线的静态检查只证明生产调用关系，不替代上述真实控制流输出断言。R4 分别摘掉这些生产边界里的日志调用、改变封闭 reason、引入路径/异常泄露、取消值域守卫或 sink 异常隔离时，具名测试必须失败。有效边界负例不得因预算缩减。

## 交付记录

2026-09-17 本地合并：master b58eeb4add351991cba4077009d9478e7a1d81a4，feature a5d4dd83dc6d663243df8996f49f0ada6cb9bd90。正式 Sol/high R3 第二次评审 pass、零 finding；首轮缺少不可重试 FAILURE 的测试，已补同一真实边界的 RETRY/FAILURE、抛错 sink 成功及资源关闭断言。DoD、完整 verify/E2E、范围、许可、防泄露闸通过；24 项语义变异均独立编译成功并由具名断言检出，原 M19 注入 Error 逃逸另存、M19b 已显式断言重验。最终差异 647 行、38419 字符；现有存储/调度/删除语义不变。
