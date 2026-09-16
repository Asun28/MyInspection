---
id: T1-APP-STORAGE-POLICY
title: Pure app-private storage policy over a typed environment
depends_on: [T1-SPIKE-PLATFORM, T1-SAFE-MEDIA-LOGGING]
status: todo
branch: T1-APP-STORAGE-POLICY
worktree: C:\wt\T1-APP-STORAGE-POLICY
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/
forbid:
  - 运行期出站网络；修改冻结 SQLDelight schema/backup format；明文 secret/tenant data 写日志或系统备份
  - device-protected storage 存租客数据；hard-coded 绝对路径；卷不可用时静默写共享相册
  - 任何 Keystore、加密 envelope、alias/purpose/version 策略或生产装配（分别归 T1-LOCAL-DATA-SECURITY 与 T1-APP-BOUNDARY-ASSEMBLY）
  - AndroidAppStorageEnvironment、Android 原始卷状态映射和目录可写探针（整体归 T1-APP-STORAGE-ANDROID）
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI；迁移既有 PhotoRuntimeStorage 的媒体或数据库位置
  - SafeLog 或媒体日志接线的重复实现；前置卡保有该能力
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：AppStoragePolicy 消费 AppStorageEnvironment 的具名根与封闭卷状态，把 DB/设置/回执/secret envelope/journal/staging 路由到经 canonical File 边界检查的 credential-encrypted internal/no-backup；媒体仅消费端口提供的 app-specific external，不回退共享目录。拒绝空根、不可写、未挂载和低空间，等值可用；普通异常脱敏，致命 Error 原样传播。删除或错误替换任一策略路由、根边界或失败状态后，对应行为测试必须失败。前置 SafeLog 回归继续绿；Android getter/原始卷状态/目录可写性由 T1-APP-STORAGE-ANDROID 独立验收，本卡不声称已交付实际平台适配、业务装配或 Keystore。
requirements:
  - "R1 DB、settings、receipts、secret envelope、restore journal 与 staging metadata 必须路由至 credential-encrypted internal/no-backup；仅大媒体可路由至 app-specific external。"
  - "R2 端口提供的 external 卷必须为 MOUNTED 且可写；空目录或端口判定不可写（包括非空不存在路径）、未挂载、只读返回 Unavailable，usableBytes < requestedBytes 返回 InsufficientSpace，等于边界可用；不得暴露绝对路径或选择共享回退。Android MEDIA_MOUNTED 和实际文件状态的映射由 T1-APP-STORAGE-ANDROID 提供。"
acceptance:
  - "A1 各数据类别以显式类别到不同内部子目录的映射夹具证明 protected 类别绝不落 device-protected/external；标记为 DP 的环境先请求 CE 环境，转换后仍标记 DP 则拒绝；媒体仅消费具名 app-specific 端口根，不自行选择 shared/public。删除、交换或错误替换任一策略路由后，对应行为测试必须失败。实际 Android getter 来源和转换由后继直接验收。"
  - "A2 空目录、非空不存在路径且端口报告不可写、未挂载、只读、低空间与正常可用夹具返回闭合状态，明确验证 usableBytes 等于 requestedBytes 的边界；注入敏感绝对路径后，policy 结果的文本表示、失败状态和普通异常信息不得包含它。真实 File canonical 根检查覆盖等于根、同前缀兄弟、归一化越界和实际 DP 根；致命 Error 按身份传播。删除或错误替换任一策略失败映射后，对应行为测试必须失败。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；路由与卷状态各保留一枚具名单点变异
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-APP-STORAGE-POLICY

## 产出与边界

本卡只提供 `AppStorageEnvironment` 端口、`AppStoragePolicy` 和结构化 `StorageRoot/State`。策略对受保护根执行真实 File canonical 边界检查；外部媒体根的 app-specific 来源由端口契约及后继 Android 适配器保证，类型名本身不证明平台来源。数据库、settings、receipts、secret envelopes、restore journal 与 staging metadata 只消费 credential-encrypted internal/no-backup；媒体仅消费具名 external 根。卷缺失或低空间返回闭合状态，不泄露普通失败信息，也不选择共享回退。

不做加密、KeyStore、信封 codec 或 alias/version/purpose 隔离。后继 `T1-LOCAL-DATA-SECURITY` 只消费这一 primitive 并交付 `LocalSecretBox`；`T1-APP-BOUNDARY-ASSEMBLY` 才把两者接入生产入口。现有媒体日志脱敏仅作为已交付前置的回归，不在本卡重写。

## RED、DoD 与预算

先用纯 JVM 测试固定每种数据类别的根目录、卷缺失、低空间和禁止回退；以窄环境端口提供平台事实，真实 canonical File 检查仍执行。不得新增 Gradle/runtime 依赖。DoD 见 front-matter，R4 覆盖类别、状态、根边界和异常语义。

2026-09-17：第三次 R3 指出实际 Android 映射缺直接测试；原差异 613 行 / 29,691 字符，补齐平台执行预计达 813–923 行，因此将整个 Android 适配实现、原始状态映射、可写 helper 及专属测试移至 `T1-APP-STORAGE-ANDROID`。保留三次失败裁决和原提交；不得仅推迟测试而保留未验证适配器。当前卡拆后预计 528–558 行 / 25k–27k，另有约 90 行修复空间；650 行或 45k 提前闸不变。纯策略重新跑完整 DoD、所有剩余最终源 pin 的 R4 和正式 R3。作者修复提升 GPT-5.6 Terra · high；独立 GPT-5.6 Sol · high 正式评审。
