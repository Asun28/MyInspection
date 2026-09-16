---
id: T1-LOCAL-DATA-SECURITY
title: Keystore-backed LocalSecretBox（消费已交付存储策略）
depends_on: [T1-SPIKE-PLATFORM, T1-SAFE-MEDIA-LOGGING, T1-APP-STORAGE-POLICY]
status: todo
branch: T1-LOCAL-DATA-SECURITY
worktree: C:\wt\T1-LOCAL-DATA-SECURITY
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/
forbid:
  - 运行期出站网络；修改冻结 SQLDelight schema/backup format；明文 secret/tenant data 写日志或系统备份
  - device-protected storage 存租客数据；hard-coded 绝对路径；卷不可用时静默写共享相册
  - 重写 AppStoragePolicy、StorageRoot/State、媒体日志接线或生产装配
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI；迁移既有 PhotoRuntimeStorage 的媒体或数据库位置
  - AppStoragePolicy（T1-APP-STORAGE-POLICY）及 SafeLog/四处媒体调用改造（T1-SAFE-MEDIA-LOGGING）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：Keystore-backed LocalSecretBox 只持久化 version/96-bit nonce/ciphertext+tag、key 不可导出且明文 buffer 尽力清零；同 key/purpose/plaintext 连续加密产生不同 nonce 与 ciphertext，修改 version、nonce、ciphertext 或 tag 均认证失败且不得返回明文，删除随机 nonce 或任一认证检查即 RED；alias/version/purpose 三维隔离夹具证明不同 purpose 或 version 的 envelope 交叉解密必拒绝，删除任一隔离维度即 RED；设备未解锁精确映射可重试 NEEDS_UNLOCK，缺失/失效 key、损坏 envelope、版本不支持或认证失败精确映射需用户重新输入的 NEEDS_PASSPHRASE，本层失败不调用明文 consumer、不产出成功信封或降级明文且映射删除变异即 RED；完整旧 VerifiedBackupReceipt 不被失败覆盖由 T5-BACKUP-IO A6/R1 验收，不以本卡局部测试冒充。前置 StoragePolicy 与 SafeLog 测试继续随基线绿，但不作为本卡实现。
requirements:
  - "R1 LocalSecretBox 每次 AES-GCM 加密必须生成新的 96-bit nonce；持久化 envelope 仅含 version、nonce、ciphertext+tag，key 与明文不可导出。"
  - "R2 alias/version/purpose 三维必须隔离；version、nonce、ciphertext 或 tag 任一改变均认证失败且不得返回明文。"
  - "R3 NEEDS_UNLOCK 仅表示可重试的未解锁设备；缺失/失效 key、损坏 envelope、版本不支持或认证失败必须闭合映射 NEEDS_PASSPHRASE，本层失败不调用明文 consumer、不产出成功信封且不得明文降级；完整旧回执保留由 T5-BACKUP-IO A6/R1 验收。"
acceptance:
  - "A1 同 key/purpose/plaintext 连续加密产生不同 nonce/ciphertext；删除随机 nonce 或任一认证检查即 RED。"
  - "A2 不同 purpose 或 version 的 envelope 交叉解密必拒绝；删除任一隔离维度即 RED。"
  - "A3 锁定、缺失/失效、损坏、版本不支持与篡改夹具分别断言精确闭合状态、失败不调用明文 consumer 且不产出成功信封；删除任一状态映射即 RED。完整旧 VerifiedBackupReceipt 不覆盖与状态持久化仍由 T5-BACKUP-IO A6/R1 验收。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；随机 nonce、任一认证检查、任一隔离维度及错误状态映射各保留一枚具名单点变异
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-LOCAL-DATA-SECURITY

## 产出与边界

本卡保留原 ID，只交付 Keystore-backed `LocalSecretBox`。它消费 `T1-APP-STORAGE-POLICY` 已固定的内部/no-backup 路由来放置信封；绝不重新定义 `AppStoragePolicy`、目录、卷状态或媒体存储。SafeLog 与四处媒体失败脱敏由 `T1-SAFE-MEDIA-LOGGING` 保有，本卡只要求其基线回归继续绿。

alias/version/purpose 必须三维隔离。每次 AES-GCM 加密使用新的 96-bit nonce；持久化 envelope 仅含 version、nonce、ciphertext+tag，key/明文不可导出。设备尚未解锁返回可重试 `NEEDS_UNLOCK`；key 缺失/失效、版本不支持、envelope 损坏或认证失败返回 `NEEDS_PASSPHRASE`，失败不调用明文 consumer、不产出成功信封且绝不明文降级。完整旧 VerifiedBackupReceipt 不覆盖与备份状态持久化明确由 T5-BACKUP-IO A6/R1 验收；本卡不拥有 receipt 模型，不把局部 callback 测试当作整条备份接线证据。信封通过已交付 SECRET_ENVELOPE 内部/no-backup 路由保存和读回的义务仍在本卡，不因该验收边界澄清移除。JVM 验证 codec、状态、信封读写和篡改；锁屏、凭据清除、key invalidation 与损坏 envelope 的真 Keystore 证据仍由 `T7-SMOKE-POLISH` 清单承担，缺任一结果不得发布。

## RED、DoD 与预算

先以可替换的 `KeyMaterialPort` fake 固定 nonce、认证、三维隔离及错误映射，再接 Android Keystore thin adapter；不新增 Gradle/runtime 依赖。R4 的具名变异与 DoD 见 front-matter。

完整 diff 目标 520–640 changed lines / 35k–48k characters：生产 220–300、直接测试约 197、R4 与修复余量 100–145。NOTICE 不属本卡。RED 前按完整 diff 重算；预估达到 650 行或 50k 字符即再拆 codec/port 与 Android adapter，但不删任何安全验收。首选 GPT-5.6 Terra · high；Keystore 分层、错误闭合和证伪测试均需要高推理。
