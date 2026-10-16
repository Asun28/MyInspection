---
id: T1-APP-STORAGE-ANDROID
title: Android app-private storage adapter and self-verifying device probe
depends_on: [T1-APP-STORAGE-POLICY, T1-SPIKE-PLATFORM]
status: todo
branch: T1-APP-STORAGE-ANDROID
worktree: C:\wt\T1-APP-STORAGE-ANDROID
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/AndroidAppStorageEnvironment.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/AndroidAppStorageEnvironmentTest.kt
  - android/app/src/debug/kotlin/nz/myinspection/app/platform/AppStorageProbeActivity.kt
  - android/app/src/debug/AndroidManifest.xml
  - docs/storage-android-probe.md
forbid:
  - 新增 Gradle/runtime 依赖、Android JVM 替身、源码文本断言冒充平台行为、修改生产 manifest 或冻结 schema
  - 修改前置 AppStoragePolicy 的类别、状态、canonical 根边界或异常语义；Keystore、业务装配、数据迁移
  - 共享目录写入、用户数据清除、卸载既有应用、修改系统应用或绕过设备锁；输出租客数据或原始异常
non_goals:
  - 业务入口装配、加密或备份；已有媒体位置迁移
  - 普通 APK 未生效的 system-only defaultToDeviceProtectedStorage 配置不冒充真实复现
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: JVM 映射/helper 测试及 debug APK 通过；另须按 docs/storage-android-probe.md 在 API33 真机与 API35 模拟器实际执行候选 APK 的自验证探针，保存 APK/源码 pin、每条断言、退出码和对应变异证据。编译成功不代替平台验收，旧 ignored probe 仅为设计输入。实际根、转换、状态、可写性、空间及拒绝路径必须直接调用本卡生产适配器并与独立 Android getter/受控夹具比较。
requirements:
  - "R1 AndroidAppStorageEnvironment 实现已交付端口：noBackupFilesDir、dataDir、DP dataDir 与 getExternalFilesDir(null) 精确转交；显式 DP context 转 CE 后才供策略使用，不选 filesDir、shared/public 或 DP 作为受保护根。"
  - "R2 原始卷状态仅 MEDIA_MOUNTED 映为 MOUNTED，MEDIA_MOUNTED_READ_ONLY 映为只读，其余为 UNMOUNTED；目录须同时 isDirectory 与 canWrite；usableSpace 精确转交，普通转换失败消息固定且不携带 cause。"
acceptance:
  - "A1 双设备真实 Context getter 独立对照所有根，noBackup 与 filesDir 必须可区分；explicit DP 转换为真实 CE，合成 false-marker wrapper 保留实际 DP 根并被前置策略拒绝，明确标注合成条件。"
  - "A2 实际 app-specific external 与 internal 两种目录对照带目录参数的 Environment 状态；前置状态先记录并证明能区分错误全局/恒定映射。真实自有目录、普通文件和先断言不存在的非空路径验证可写性；wrapper 仅提供该不存在 external 路径，真实适配器和策略必须返回 Unavailable。只读及未知 raw-state 由直接 JVM mapper/helper 测试覆盖。"
  - "A3 实际 usableSpace 非负且与原生 getter 对照，受控 File.getUsableSpace 哨兵验证精确转交；受控 NameNotFoundException 验证固定信息与无 cause，明确区分受控元数据与系统事实。"
  - "A4 对 noBackup→filesDir、external→public、CE转换退回DP、DP根→CE根、marker错误、状态忽略目录、可写恒true及空间错误转交做单点变异；对应编译/安装成功后须由具名 java.lang.AssertionError 检出。原始 mounted/readonly/unknown 与目录类型/权限 helper 变异也须检出；恢复最终源 pin 后完整 DoD 和双设备探针通过。"
review_gate: codex {verdict:pass}
hygiene: 平台行为与受控夹具分别标注，具名单点变异；无编译/安装失败冒充检出
doc_sync: ADR-0006 + SECURITY + TASK-BOARD + probe recipe（R5）
---

# T1-APP-STORAGE-ANDROID

承接 `T1-APP-STORAGE-POLICY` 第三次 R3 后完整移出的平台能力，未交付前不声称 Android 存储保证已完成。复用已存在的 debug Activity 探针方式，不建设通用 instrumentation 框架。探针只写自身固定诊断目录，用不可混淆的 run ID、候选 APK/源文件 SHA 和具名断言回执防止读到旧结果；host 命令逐一核验并对失败返回非零。实际 canonical 路径仅用于内存比较，回执保留布尔结果/固定标签，不输出租客路径或内容。

设备安装前核验 applicationId 与签名，既有 app 只允许 `install -r` 保留数据；仅启动本卡 debug Activity，结束时 force-stop 并核空 PID，只清理本次自有测试目录。编译 SDK 35 的平台 API 先按 pinned 源码/文档核验。既有双 diagnostic package 的旧证据保留，不替代新卡候选 APK。

预计完整交付 300–450 changed lines / 18k–30k 字符，包含设备检查、JVM 测试、短复现说明、R4 收据与约 25% 修复空间；RED 前核实际方案，达到 650 行或 45k 先拆，不压缩断言。作者 GPT-6 Astra · high（平台测试设计与实现）；独立 GPT-5.6 Sol · high 正式 R3。R1–R5 及真实设备验收全部通过才闭环。
