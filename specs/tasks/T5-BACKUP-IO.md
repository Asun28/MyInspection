---
id: T5-BACKUP-IO
title: 备份落地：SAF 目的地 + 可验证资产回执 + 自动导出 + 恢复「先试跑后落刀」
depends_on: [T5-BACKUP-FORMAT, T2-PHOTO-PROPERTY-DEDUPE, T5-MEDIA-ARCHIVE-CONTRACT, T1-SHARE-SCREEN-PRIVACY]
parallelizable_with: [T3-PDF-RENDERER, T3-HISTORY-COMPARE, T4-COMPLIANCE-ENGINE]
status: todo
branch: T5-BACKUP-IO
worktree: C:\wt\T5-BACKUP-IO
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/backup/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/backup/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/restore/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/restore/
forbid:
  - 明文中间文件；跳过 manifest 校验的恢复路径；format v1 按物业导出；明文落盘口令/可导出固定密钥
non_goals:
  - 合并式恢复（v1 整包替换，ADR-0002）；口令找回（不存在，格式层无后门）
  - Google/OneDrive 账号接入、读取 Google Photos 的“已备份”状态、云端删除；执行本机照片清理（交 T5-LOCAL-MEDIA-RETENTION）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.restore.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 恢复状态机 JVM 测试绿（staging 展开→manifest/path/count/declared-size/free-space/逐文件哈希校验→预检摘要→journal 两阶段替换；任一败=原库原文件不动；各 commit 点杀进程均确定性回滚/完成）；UI 区分未配置/运行中/已验证/失败/授权失效/provider 不可用/低空间/需解锁/需口令，未配置不冒充失败，>1s 操作分阶段且 TalkBack 有界播报，页面/进程恢复按 operation id 解析；恢复确认明确全量替换；format v1 UI/写侧只允许 Full，Property scope fail closed；Keystore 信封不入包且 key 失效只暂停自动备份；同一备份意图串行幂等，瞬时 provider I/O 只按 15/30/60 分钟最多 3 次退避，授权/口令/格式/空间/完整性失败不自动重试；导出成功只有在最终目标 .mibk 关闭后重新打开、解密 manifest、逐一核对本次资产 rel_path/hash/size 才写 VerifiedBackupReceipt，失败/授权收回/内容缺失不得留下成功回执；assembleDebug 绿；真机飞行模式本地/USB手动备份恢复、云 provider 离线/授权收回、低空间、错误口令、损坏包、finalize 自动导出各有记录
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T5-BACKUP-IO

## 产出
`app/export/backup`（`ArchiveStore` 的 SAF adapter、授权/流打开、WorkManager 调度、设置页备份区块：目的地/口令设置/上次成功时间常驻显示）+ `core/backup/receipt`（内容特定的验证回执）+ `core/backup/restore`（恢复状态机，纯 JVM 测）。v1 只实现 SAF；不得为未来 S3 引入 SDK、账号或网络端点。

## Backup experience contract

Backup is strongly recommended but never inserted between first launch and first inspection. After the first finalize with no destination configured, the finalized summary shows one non-blocking panel:

`Protect this inspection — create an encrypted backup in a folder you choose.`

Actions are `Set up backup` and `Not now`. Dismissal lasts until the next finalize; the app does not nag on every launch or mislabel an unconfigured backup as a failure.

| UI state | Required copy/facts | Primary action |
| --- | --- | --- |
| `NOT_CONFIGURED` | `Encrypted backup not set up`; local data remains available | Set up backup |
| `READY` | Destination display name and last verified time, or `No verified backup yet` | Back up now |
| `RUNNING` | Current phase: Encrypting → Writing → Verifying; preserve last verified receipt separately | None; duplicate starts rejected |
| `VERIFIED` | `Encrypted backup verified`, absolute date/time, scope, inspection/photo counts | Back up now |
| `FAILED` | Exact retryable reason; retain and continue showing prior verified receipt | Try again |
| `AUTHORIZATION_REVOKED` | `Backup folder access was removed`; local data and prior destination files are unchanged | Choose folder again |
| `PROVIDER_UNAVAILABLE` | `Backup folder is unavailable offline`; capture/finalize/local reports remain available | Try again |
| `NEEDS_UNLOCK` | Device must be unlocked before the Keystore envelope can be used | None; retry after unlock |
| `NEEDS_PASSPHRASE` | Device-local envelope is missing/invalid; prior archives remain valid | Verify password |
| `LOW_STORAGE` | Name required additional space and whether source staging or destination is full | Manage storage |

`Worker succeeded`, a provider brand, URI existence, or bytes written never produces the word Verified. The status component always distinguishes the latest attempt from the last verified backup.

Every operation persists an opaque operation ID and phase checkpoint. UI/process restoration resolves that ID before enabling another start. Work over one second announces phase changes and coarse progress without replacing the last verified receipt. Cancellation is available while producing an unverified temporary object; verification/restore commit boundaries block cancellation and state the reason. Any cancelled/failed attempt removes only its own temporary bytes.

### Passphrase setup

Passphrase setup explains `There is no password recovery` before entry, permits password-manager paste, provides Show/Hide, and requires confirmation. Strength guidance is advisory and must not invent a server recovery path. The passphrase itself never reappears in settings or notifications.

For automatic backups, the app generates a non-exportable Android Keystore AES-GCM wrapping key and persists only an encrypted passphrase envelope in credential-encrypted `noBackupFilesDir`. The envelope never enters `.mibk` or Android system backup. Plaintext uses `CharArray`/wipeable buffers and is cleared best-effort after each derivation; it is never logged. Keystore invalidation, restore to another phone, corruption, or pre-unlock execution transitions to `NEEDS_UNLOCK`/`NEEDS_PASSPHRASE`; it never falls back to an empty/default password or unencrypted export.

### Restore journey

Restore is a staged full-screen task:

1. Choose encrypted backup.
2. Enter passphrase.
3. Verify header, manifest, scope, and every asset in staging.
4. Show a preflight summary: backup date, scope, properties, inspections, photos, and any incompatibility.
5. Confirm `Replace all data on this device` by entering `RESTORE`.
6. Commit atomically, relaunch the app root, and show the restored backup date once.

Wrong passphrase, corrupt data, unsupported version, property-scoped package, insufficient space, or process interruption leaves current data untouched and names the next safe action. The destructive confirmation is unavailable until preflight passes; it never uses a generic `Continue` label. Offer `Back up current data first` before replacement.

### Offline and scope contract

- v1 exposes **All app data** only. `BackupScope.Property` is read only for diagnostics/legacy rejection; neither UI nor writer may create it. A whole `db.sqlite` inside a property-labelled package is a confidentiality failure, not a harmless extra.
- Local folder/USB through SAF works without internet while the volume is present. A cloud DocumentsProvider may be unavailable offline; that state affects only backup/restore and never blocks capture, finalize, history, schedule, or local PDF.
- The app never gates a local screen on a connectivity probe. Provider errors are interpreted at operation time and mapped to user actions without persisting raw URI/token/error bodies.

## 上下文包（执行模型必读）
- SAF：`ACTION_OPEN_DOCUMENT_TREE` 选目的地 → `takePersistableUriPermission`（读+写持久）；每次导出经 DocumentFile 建 `myinspection-backup-YYYYMMDD-HHmm.mibk`（时间戳由 Clock 注入）；**授权可被系统收回**——导出 Worker 捕 SecurityException → 通知「备份目的地失效，请重选」+ 设置页红标（ADR-0002：常驻显示上次成功备份时间就是为此）。
- 自动导出：WorkManager——finalize 完成事件触发一次 + 每周期性（PeriodicWorkRequest, 约束：存储非低）；串行唯一队列（KEEP）防并发写同一目的地；只有 Keystore 信封可解时运行。相同内容快照/目的地的重复意图合并为同一 operation id。仅 provider 暂时 I/O/忙碌状态使用 15/30/60 分钟指数退避且最多 3 次；授权收回、口令/Keystore、格式、空间与完整性错误等待用户动作，不自动重试。导出内容走 T5-BACKUP-FORMAT 写入器（流式）；DB 快照优先 SQLite online backup/一致性快照，checkpoint + 文件复制仅允许在 app DB 写屏障内，禁止与 WAL 写并发。
- **不覆盖旧备份**：新对象先以 `.partial` 写入，关闭并回读验证，再形成最终名称并再次确认可打开。provider 无安全 rename 时复制到最终对象并复核，之后才删 partial。任何残留 partial、Worker success、字节写完或 provider 品牌都不是成功回执。
- **上次成功时间不是清理授权**：导出关闭目标流后，必须经 SAF 重新打开该 `.mibk`，解密并核对 manifest 中每个照片的 `rel_path + SHA-256 + byte size`，才落 T5-MEDIA-ARCHIVE-CONTRACT 定义的 `VerifiedBackupReceipt`。目的地品牌、URI 存在或 Worker 成功都不能替代内容回读。
- **恢复「先试跑后落刀」**：选包 → 口令 → 私有 internal staging → 校验 format/schema/scope/path/duplicate/count/declared size/总量/可用空间 → 流式解密且每项不得超过声明大小 → 逐 SHA-256 → 预检摘要 → maintenance mode + 独立 journal → 旧 roots 改名保底 → 新 roots 就位 → 重开 DB/抽查资产 → 完成后删旧。任一点失败或杀进程都由 journal 确定性回滚/完成，禁止混合新旧数据。
- 空间预检保留 `max(512 MiB, usableSpace 的 10%)`；manifest/file-count 等敌意输入上限由 core 常量与 hostile tests 固定。加总使用 checked arithmetic，溢出即拒。
- 结构化日志仅用 operation/reason code/opaque id/count/duration；禁完整路径、SAF URI、地址、备注、口令、key、Authorization、provider 原始错误体。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Terra。难度 H。
