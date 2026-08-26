---
id: T1-LOCAL-DATA-SECURITY
title: 本地数据安全底座：内外存储分层 + Keystore secret box + 脱敏日志
depends_on: [T1-SPIKE-PLATFORM]
status: todo
branch: T1-LOCAL-DATA-SECURITY
worktree: C:\wt\T1-LOCAL-DATA-SECURITY
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/kotlin/nz/myinspection/app/platform/
  - android/app/src/main/kotlin/nz/myinspection/app/media/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/
forbid:
  - 运行期出站网络；修改冻结 SQLDelight schema/backup format；明文 secret/tenant data 写日志或系统备份
  - device-protected storage 存租客数据；hard-coded 绝对路径；卷不可用时静默写共享相册
  - 禁止未经授权的运行期出站网络、账号/RBAC、遥测；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：AppStoragePolicy 把 DB/设置/回执/secret envelope/journal/staging 路由到 credential-encrypted internal/no-backup，把大媒体路由到 app-specific external 并显式返回卷不可用/低空间；Keystore-backed LocalSecretBox 只持久化 version/96-bit nonce/ciphertext+tag、key 不可导出且明文 buffer 尽力清零；同 key/purpose/plaintext 连续加密产生不同 nonce 与 ciphertext，修改 version、nonce、ciphertext 或 tag 均认证失败且不得返回明文，删除随机 nonce 或任一认证检查即 RED；alias/version/purpose 三维隔离夹具证明不同 purpose 或 version 的 envelope 交叉解密必拒绝，删除任一隔离维度即 RED；设备未解锁精确映射可重试 NEEDS_UNLOCK，缺失/失效 key、损坏 envelope、版本不支持或认证失败精确映射需用户重新输入的 NEEDS_PASSPHRASE，均保留旧回执、不降级明文且映射删除变异即 RED；SafeLog API/测试与现有 media 调用不接受/输出绝对路径、SAF URI、地址、姓名、备注、secret、Authorization 或 raw provider body
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-LOCAL-DATA-SECURITY

## 产出

提供 `AppStoragePolicy`、Keystore-backed `LocalSecretBox` 和 `SafeLog` 三个 app 平台 primitive，供 capture、backup、restore 和 remediation 复用；不实现业务功能。

## 契约

- 保留 ADR-0002 的 app-specific external/SAF 存储与导出范围。
- 数据库、settings、receipts、secret envelopes、restore journal 和 staging metadata 只使用 credential-encrypted internal/no-backup；大照片/音频可用 app-specific external，卷缺失/低空间返回结构化状态。
- Keystore alias/version/purpose 分离；每次 AES-GCM 加密使用新的 96-bit nonce，持久化 envelope 仅含 version、nonce、ciphertext+tag，key/明文不可导出。设备尚未解锁返回可重试 `NEEDS_UNLOCK`；key 缺失/失效、版本不支持、envelope 损坏或认证失败返回需重新输入口令的 `NEEDS_PASSPHRASE`，保留旧回执且绝不尝试明文降级。JVM 测状态/codec/篡改；锁屏、凭据清除、key invalidation 与损坏 envelope 的真 Keystore 证据明确交给 `T7-SMOKE-POLISH` 清单，缺任一结果不得发布。
- 日志调用方只传 operation/reason + opaque id/count/duration；现有 media 删除失败日志移除绝对路径。

## 验收

见 front-matter。首选 GPT-5.6 Terra · high；备选 Sonnet 5 · max。难度 M。
