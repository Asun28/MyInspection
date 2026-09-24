---
id: T1-LOCAL-DATA-SECURITY
title: 本地数据安全底座：内外存储分层与 Keystore secret box（依赖安全日志）
depends_on: [T1-SPIKE-PLATFORM, T1-SAFE-MEDIA-LOGGING-REMOTE, T1-STORAGE-PATH-BOUNDARY-REMOTE, T1-APP-STORAGE-POLICY, T1-APP-STORAGE-ANDROID, T1-LOCAL-SECRET-BOX, T1-LOCAL-SECRET-STORE]
status: todo
branch: T1-LOCAL-DATA-SECURITY
worktree: C:\wt\T1-LOCAL-DATA-SECURITY
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/AndroidSecretKeys.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/AndroidSecretKeysTest.kt
  - android/app/src/debug/kotlin/nz/myinspection/app/platform/SecretBoxProbeActivity.kt
  - android/app/src/debug/AndroidManifest.xml
  - docs/local-secret-box-probe.md
forbid:
  - 运行期出站网络；修改冻结 SQLDelight schema/backup format；明文 secret/tenant data 写日志或系统备份
  - device-protected storage 存租客数据；hard-coded 绝对路径；卷不可用时静默写共享相册
  - 禁止未经授权的运行期出站网络、账号/RBAC、遥测；未经本卡 version review 不得改冻结 schema/backup format
  - 修改前置 T1-LOCAL-SECRET-BOX 的信封格式、alias/AAD 隔离与状态映射，或 T1-LOCAL-SECRET-STORE 的信封存储；新增 Gradle/runtime 依赖、Robolectric 或仪器测试框架；源码文本断言冒充平台行为
  - 探针触碰生产 alias 或生产信封文件、输出租客数据/路径/原始异常、卸载或清除既有应用数据、绕过设备锁
non_goals:
  - SAF 备份写入/恢复状态机/口令 UX（T5-BACKUP-IO）；FileProvider/secure-window/network manifest（T1-SHARE-SCREEN-PRIVACY）
  - SQLCipher、账号、同步、遥测、业务 UI
  - 生产装配（T1-APP-BOUNDARY-ASSEMBLY）；锁屏、凭据清除、key invalidation 的真机证据（T7-SMOKE-POLISH A7 清单）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：AppStoragePolicy 把 DB/设置/回执/secret envelope/journal/staging 路由到 credential-encrypted internal/no-backup，把大媒体路由到 app-specific external 并显式返回卷不可用/低空间；Keystore-backed LocalSecretBox 只持久化 version/96-bit nonce/ciphertext+tag、key 不可导出且明文 buffer 尽力清零；同 key/purpose/plaintext 连续加密产生不同 nonce 与 ciphertext，修改 version、nonce、ciphertext 或 tag 均认证失败且不得返回明文，删除随机 nonce 或任一认证检查即 RED；alias/version/purpose 三维隔离夹具证明不同 purpose 或 version 的 envelope 交叉解密必拒绝，删除任一隔离维度即 RED；设备未解锁精确映射可重试 NEEDS_UNLOCK，缺失/失效 key、损坏 envelope、版本不支持或认证失败精确映射需用户重新输入的 NEEDS_PASSPHRASE，均保留旧回执、不降级明文且映射删除变异即 RED；SafeLog API/测试与现有 media 调用不接受/输出绝对路径、SAF URI、地址、姓名、备注、secret、Authorization 或 raw provider body。2026-09-25 三 PR 拆分后：上述 LocalSecretBox 的 JVM 可证部分由前置 T1-LOCAL-SECRET-BOX 与 T1-LOCAL-SECRET-STORE 交付，其测试在本卡 DoD 中继续运行；「Keystore-backed」与「key 不可导出」另须按 docs/local-secret-box-probe.md 在 API33 真机与 API35 模拟器实际执行候选 APK 的自验证探针，保存 APK/源码 pin、每条断言、退出码与对应变异证据。编译成功不代替平台验收。
requirements:
  - "R1 当现有媒体操作记录失败时，系统应仅输出已批准 operation/reason 与 opaque id/count/duration，不得输出完整路径、URI 或原始 Throwable。"
  - "R2 AndroidSecretKeys 实现前置的 SecretKeyPort 与 DeviceUnlockPort：在 AndroidKeyStore 中按前置给出的 alias 生成 AES-256、仅 ENCRYPT|DECRYPT、GCM/NoPadding、要求随机化加密的 key；alias 已存在时只读取、绝不重建（重建会让保留下来的旧 envelope 无法解封）；不要求用户认证、不设 unlocked-device-required，因为 ADR-0006 §3 要求 finalize 后和每周后台备份能解封，可用性与 CE 存储同为首次解锁之后；解锁状态取 UserManager.isUserUnlocked()；普通失败转固定消息且无 cause，致命 Error 按身份传播。"
  - "R3 debug 探针以探针专属 key 版本（不触碰生产 alias 与生产信封）驱动生产 LocalSecretBox 与生产适配器，在真实 Keystore 上自验证，并在结束时删除探针 alias 与文件。"
acceptance:
  - "A1 [R1] MediaFileStore、PhotoImportPipeline、PhotoIngestPendingLease 和 PhotoOrphanCleanupWorker 的失败夹具均不产生路径、原始异常 message/stack 或业务原文。"
  - "A2 [R1] 上述失败日志保留已批准 operation/reason 与 opaque id/count/duration，地址、姓名、备注、URI、secret 和 Authorization 哨兵均不出现在最终日志。"
  - "A3 [R1] 原始异常及其嵌套 cause 含路径或业务原文时，最终日志不含异常 message、stack 或敏感哨兵；删除脱敏边界后该负例必须失败。"
  - "A4 [R2] 真机与模拟器上 KeyInfo 显示 256 位、用途仅加解密、GCM 与 NoPadding、不要求用户认证、非 unlocked-device-required，key.encoded 为 null（不可导出）；安全级别与是否在安全硬件内只记录、不断言（ADR-0006：硬件 Keystore 不是所有设备的保证）。同一 alias 连续两次 seal 后取回的仍是同一把 key：第一次 seal 的 envelope 在第二次 seal 后仍可用该 alias 解封。"
  - "A5 [R3] 探针经生产 LocalSecretBox 完成往返；同明文两次 seal 的 nonce 与密文不同；在真实 Keystore 上翻转 nonce、密文、tag 各一位均为 AUTHENTICATION_FAILED；删除探针 alias 后为 KEY_MISSING；写入损坏 envelope 为 ENVELOPE_CORRUPT；每个失败后 envelope 字节不变。回执只含布尔结果与固定标签，并绑定所测 APK 的 android/ tree。"
  - "A6 [R2] 适配器单点变异（非 AndroidKeyStore provider、错误位长、缺 GCM 或加 padding、加用户认证或 unlocked-device-required、每次 seal 重建 key、异常带 cause 或原始消息）在编译与安装成功后由具名断言检出；恢复最终源 pin 后完整 DoD 与双设备探针通过。"
review_gate: codex {verdict:pass}
budget: 700
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）；适配器变异见 A6，编译/安装失败不算检出
doc_sync: ADR-0006 + SECURITY + TASK-BOARD + docs/local-secret-box-probe.md（R5）
---

# T1-LOCAL-DATA-SECURITY

## 产出

本卡提供 Keystore-backed `LocalSecretBox`，并消费前置卡 `T1-APP-STORAGE-POLICY` 交付的 `AppStoragePolicy`（master `f68d7006`；2026-09 reconcile 以它取代 `T1-APP-STORAGE-POLICY-REMOTE` 的版本，见 ADR-0006）；`SafeLog` 和四处媒体日志接线已由前置卡 `T1-SAFE-MEDIA-LOGGING-REMOTE` 交付。原有安全验收与日志回归完整保留，不重复实现前置能力。

## 契约

- 保留 ADR-0002 的 app-private/SAF 范围；具体落位按 ADR-0006 收紧：DB 等在 internal/no-backup，仅大媒体可在 app-specific external。
- 数据库、settings、receipts、secret envelopes、restore journal 和 staging metadata 只使用 credential-encrypted internal/no-backup；大照片/音频可用 app-specific external，卷缺失/低空间返回结构化状态。
- Keystore alias/version/purpose 分离；每次 AES-GCM 加密使用新的 96-bit nonce，持久化 envelope 仅含 version、nonce、ciphertext+tag，key/明文不可导出。设备尚未解锁返回可重试 `NEEDS_UNLOCK`；key 缺失/失效、版本不支持、envelope 损坏或认证失败返回需重新输入口令的 `NEEDS_PASSPHRASE`，保留旧回执且绝不尝试明文降级。JVM 测状态/codec/篡改；锁屏、凭据清除、key invalidation 与损坏 envelope 的真 Keystore 证据明确交给 `T7-SMOKE-POLISH` 清单，缺任一结果不得发布。
- 日志调用方只传 operation/reason + opaque id/count/duration；现有 media 失败日志移除绝对路径与 raw Throwable（含 message/stack）；覆盖 MediaFileStore、PhotoImportPipeline、PhotoIngestPendingLease、PhotoOrphanCleanupWorker。

## 验收

见 front-matter。首选 GPT-5.6 Terra · high；备选 Sonnet 5 · max。难度 M。

## 安全日志前置拆分

完整首轮实现、测试和变异收据预估 905–1092 changed lines，先拆出单独的安全日志卡。前置卡明确允许修订 PhotoOrphanCleanupWiringTest 的过时原始路径日志断言，保留其余存储、调度和生命周期检查；不改变本卡的完整 DoD、schema、备份格式或既有媒体存储位置。

## 路径前置复用

真实逐段路径解析、验证根快照、checked-child 及直接路径测试由 T1-STORAGE-PATH-BOUNDARY-REMOTE 完整交付。AppStoragePolicy 仍须用黑盒接线测试证明 create 与 resolveChild 都被调用，任一调用旁路由具名断言检出。前置卡不交付 Android getter、媒体状态或 Keystore；本卡原有验收与 DoD 不变。安全日志 PR #304 与路径边界 PR #310 均已通过正式 R3 和 CI 并远端合并；安全日志已归档，路径边界的 R5 归档仍待独立收尾。

历史拆分时的状态记录：“两个远端前置均尚待各自 PR、R3 和 CI 通过后合并。”该记录保留拆分时的判断；当前依赖状态以上述实际远端交付为准。

The storage policy is an additional remote prerequisite after SafeLog and PathBoundary. Consume it only after its functional PR merges. The complete original security acceptance and executable DoD remain unchanged.

## 2026-09 reconcile note

本卡正文取 origin（#301/#313 的远端别名登记）。本地 `a718507f`（2026-09-17）把 Android getter/转换、原始卷状态与目录可写实现拆给 `T1-APP-STORAGE-ANDROID`，并把它加为本卡前置（ADR-0006 的 2026-09-17 交付拆分条目：LocalSecretBox 与生产装配均以后者为前置）。reconcile 据此补入该前置，并把策略前置改指 master 上实际交付 `AppStoragePolicy` 的 `T1-APP-STORAGE-POLICY`。本地 2026-09-17 改动本卡的四个提交（`f372005f`、`21d3c9c9`、`12d96828`、`a718507f`）对标题、allow_paths、forbid、non_goals 与 DoD 的收窄未取入，origin 的完整验收保留。

本次改动使 `T0-REMOTE-ROUND3-CARDS` DoD 对本文件的 SHA-256 钉（`E1C388F0…`，即 #313 登记时的内容）不再成立；该 DoD 在 origin master `70844aae` 上已有四个钉不成立（`T1-APP-STORAGE-POLICY-REMOTE` 卡、ADR 0006、ADR 0007、TASK-BOARD）。它对 TASK-BOARD 本卡行依赖列与本卡 `depends_on` 逐字相等的检查，本次两处同步修改后仍成立。

## 2026-09-25 三 PR 拆分（用户裁定）

SafeLog、存储策略与 Android 存储适配已由前置交付，生产装配归 `T1-APP-BOUNDARY-ASSEMBLY`，本卡余下的只有 Keystore secret box。用户裁定按三个 PR 交付：① 卡片登记（本次修订与 `T1-LOCAL-SECRET-BOX`）；② `T1-LOCAL-SECRET-BOX`：纯 JVM 的信封 codec、AES-GCM、alias/version/purpose 隔离、NEEDS_UNLOCK/NEEDS_PASSPHRASE 映射、明文缓冲清零与原子信封存储，全部经 key/unlock 端口注入；③ 本卡：`AndroidSecretKeys`（AndroidKeyStore key 端口与 UserManager 解锁端口）及 debug 探针，在 SM-A346E（API 33）与 API 35 模拟器上自验证，并以原 DoD 收口。

allow_paths 随之收窄为适配器、其 JVM 测试、debug 探针、debug 清单与探针复现说明；原 `platform/` 与 `media/` 目录级路径及 `build.gradle.kts` 不再需要（SafeLog 与媒体日志接线已由前置交付，本卡不新增依赖）。探针沿用 `T1-APP-STORAGE-ANDROID` 的方式：DUMP 权限限制的 debug Activity、run ID、候选 APK 与源文件 SHA 回执、host 逐条核验。预计 400–550 changed lines；`budget: 700`，超 800 先拆。

不设 unlocked-device-required 是本次拆分写下的设计取舍：设置后，屏幕锁定时 key 不可用，每周后台备份多半在夜间锁屏时运行，会反复落入 NEEDS_UNLOCK，达不到 ADR-0006 §3 的周期保护目的；而首次解锁之后的攻击者若能以本 app 身份执行代码，本就能读取同在 CE 存储中的主库。NEEDS_UNLOCK 因此对应本次开机后用户尚未解锁（`UserManager.isUserUnlocked()` 为 false）；屏幕锁定本身不影响该 key。

## 2026-09-25 预算拆分与待决问题

用户裁定：`T1-LOCAL-SECRET-BOX` 经全新上下文预审补上七处测试缺口后约 836 行，超过其 `budget: 800`，原子信封存储连同其测试与原 R6/A6 移至新卡 `T1-LOCAL-SECRET-STORE`。交付顺序改为：卡片登记 → `T1-LOCAL-SECRET-BOX` → `T1-LOCAL-SECRET-STORE` → 本卡。

待本卡开工时与用户裁定：同一预审指出，alias 下的 key 仍在但已不可用（如 Keystore blob 损坏）时，open 返回 `NeedsPassphrase(KEY_UNUSABLE)`，而用户重新输入口令后，seal 仍拿到同一把坏 key，永远 UNAVAILABLE，达不到 ADR-0006 §3 的重新验证。这与本卡 R2 的「alias 已存在时只读取、绝不重建」冲突；可选做法包括 seal 前对已存在 key 做一次自检、失败才重建，或提升 key 版本换新 alias。须先定取舍再写 RED。
