---
id: T1-APP-STORAGE-POLICY
title: App-private storage routing policy and structured volume state
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
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI；迁移既有 PhotoRuntimeStorage 的媒体或数据库位置
  - SafeLog 或媒体日志接线的重复实现；前置卡保有该能力
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：AppStoragePolicy 把 DB/设置/回执/secret envelope/journal/staging 路由到 credential-encrypted internal/no-backup，把大媒体路由到 app-specific external；卷不可用/低空间返回结构化状态而非路径或共享相册回退。前置 SafeLog API/测试与现有 media 调用继续证明不接受/输出绝对路径、SAF URI、地址、姓名、备注、secret、Authorization 或 raw provider body。删除或错误替换任一生产路由或状态映射后，对应行为测试必须失败；测试不声称已装配到业务入口或证明 Keystore 行为。
requirements:
  - "R1 DB、settings、receipts、secret envelope、restore journal 与 staging metadata 必须路由至 credential-encrypted internal/no-backup；仅大媒体可路由至 app-specific external。"
  - "R2 external 卷必须为已挂载且可写的 app-specific external（Android MEDIA_MOUNTED）；目录缺失、未挂载或只读返回结构化 Unavailable，usableBytes < requestedBytes 返回 InsufficientSpace，等于边界可用；不得暴露绝对路径、转写共享相册或静默成功。"
acceptance:
  - "A1 各数据类别以显式类别到不同内部子目录的映射夹具证明 protected 类别绝不落 device-protected/external；device-protected context 先转 CE，转换后仍受 device-protected 标记则拒绝；媒体绝不落 shared/public。删除、交换或错误替换任一生产路由后，对应行为测试必须失败。"
  - "A2 目录缺失、未挂载、只读、低空间与正常可用夹具返回闭合状态，明确验证 usableBytes 等于 requestedBytes 的边界；注入敏感绝对路径后，policy 结果的文本表示、失败状态和抛出信息不得包含它。删除或错误替换任一生产失败映射后，对应行为测试必须失败。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；路由与卷状态各保留一枚具名单点变异
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-APP-STORAGE-POLICY

## 产出与边界

本卡只提供 `AppStoragePolicy` 及其结构化 `StorageRoot/State`：不含租客数据的 device-protected 或共享目录从类型上不可作为路由结果。数据库、settings、receipts、secret envelopes、restore journal 与 staging metadata 只能落 credential-encrypted internal/no-backup；大照片/音频只能落 app-specific external。卷缺失或低空间返回闭合、可调用方处理的状态，不泄露绝对路径，也不静默转到共享相册。

不做加密、KeyStore、信封 codec 或 alias/version/purpose 隔离。后继 `T1-LOCAL-DATA-SECURITY` 只消费这一 primitive 并交付 `LocalSecretBox`；`T1-APP-BOUNDARY-ASSEMBLY` 才把两者接入生产入口。现有媒体日志脱敏仅作为已交付前置的回归，不在本卡重写。

## RED、DoD 与预算

先用纯 JVM 测试固定每种数据类别的根目录、卷缺失、低空间和禁止回退；只允许以窄环境端口替换 Android 文件系统状态。不得新增 Gradle/runtime 依赖。DoD 见 front-matter，R4 以删除一种受保护类别路由和删除一种不可用卷映射的变异证明断言不是模板。

完整 diff 目标 400–520 changed lines / 28k–40k characters：预期实现 285–340（生产 135–180、直接测试约 95、R4 55–65），另留 review 修复余量 115–180；余量不是额外功能范围。NOTICE 不属本卡。RED 前按 `review.ps1 -SizeOnly` 的完整差异重算；预估达到 650 行或 45k 字符即先报告拆分，不削弱类别或失败状态验收。首选 GPT-5.6 Terra · medium；仅端口形状超出该预算时提升为 high。
