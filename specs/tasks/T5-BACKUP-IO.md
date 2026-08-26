---
id: T5-BACKUP-IO
title: 备份落地：SAF 目的地 + 可验证资产回执 + 自动导出 + 恢复「先试跑后落刀」
depends_on: [T5-BACKUP-FORMAT, T2-PHOTO-PROPERTY-DEDUPE, T5-MEDIA-ARCHIVE-CONTRACT, T1-SHARE-SCREEN-PRIVACY, T1-LOCAL-DATA-SECURITY]
parallelizable_with: [T3-PDF-RENDERER, T3-HISTORY-COMPARE, T4-COMPLIANCE-ENGINE]
status: todo
branch: T5-BACKUP-IO
worktree: C:\wt\T5-BACKUP-IO
plan_ref: context/DESIGN.md#offline-and-data-protection-experience
backup_scopes: [full, property]
backup_states: [NOT_CONFIGURED, READY, RUNNING, VERIFIED, PROVIDER_UNAVAILABLE, AUTHORIZATION_REVOKED, NEEDS_UNLOCK, NEEDS_PASSPHRASE, LOW_STORAGE, FAILED]
acceptance:
  - "A1 full and property backup states expose NOT_CONFIGURED through FAILED, PREPARING then ENCRYPTING then WRITING then VERIFYING, and only finish with a verified receipt"
  - "A2 restore expands into staging, supports recovery cleanup and rollback, and uses verify-before-replace so the live data is untouched until verification passes"
  - "A3 provider, authorization, storage, and secret failures are distinct; secrets are referenced from protected storage and never exported or logged"
  - "A4 local/USB export and restore work in flight-mode without an account or network dependency"
  - "A5 the backup format remains the versioned authority for manifest, encryption, compatibility, and restore validation"
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/backup/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/restore/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/restore/
forbid:
  - 明文中间文件；跳过 manifest 校验的恢复路径
non_goals:
  - 合并式恢复（v1 整包替换，ADR-0002）；口令找回（不存在，格式层无后门）
  - Google/OneDrive 账号接入、读取 Google Photos 的“已备份”状态、云端删除；执行本机照片清理（交 T5-LOCAL-MEDIA-RETENTION）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.restore.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 恢复状态机 JVM 测试绿（staging 展开→逐文件哈希校验→全对才 commit 替换；任一败=原库原文件不动；中途杀进程重启后残留 staging 被安全清理）；导出成功只有在目标 .mibk 关闭后重新打开、解密 manifest、逐一核对本次资产 rel_path/hash/size 才写 VerifiedBackupReceipt，失败/授权收回/内容缺失不得留下成功回执；assembleDebug 绿；真机冒烟：SAF 选 Drive/OneDrive 或本地树→手动整包导出→读回验证→「清库」→恢复全回来；自动导出在 finalize 后触发一次，记录附 PR
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T5-BACKUP-IO

## 产出
`app/export/backup`（`ArchiveStore` 的 SAF adapter、授权/流打开、WorkManager 调度、设置页备份区块：目的地/口令设置/上次成功时间常驻显示）+ `core/backup/receipt`（内容特定的验证回执）+ `core/backup/restore`（恢复状态机，纯 JVM 测）。v1 只实现 SAF；不得为未来 S3 引入 SDK、账号或网络端点。

## 上下文包（执行模型必读）
- SAF：`ACTION_OPEN_DOCUMENT_TREE` 选目的地 → `takePersistableUriPermission`（读+写持久）；每次导出经 DocumentFile 建 `myinspection-backup-YYYYMMDD-HHmm.mibk`（时间戳由 Clock 注入）；**授权可被系统收回**——导出 Worker 捕 SecurityException → 通知「备份目的地失效，请重选」+ 设置页红标（ADR-0002：常驻显示上次成功备份时间就是为此）。
- 自动导出：WorkManager——finalize 完成事件触发一次 + 每周期性（PeriodicWorkRequest, 约束：存储非低）；串行唯一队列（KEEP）防并发写同一目的地；导出内容走 T5-BACKUP-FORMAT 写入器（流式，DB 快照用 SQLDelight/SQLite 的在线备份或 checkpoint 后复制——**先 wal_checkpoint(TRUNCATE) 再复制文件**，防半写快照）。
- **上次成功时间不是清理授权**：导出关闭目标流后，必须经 SAF 重新打开该 `.mibk`，解密并核对 manifest 中每个照片的 `rel_path + SHA-256 + byte size`，才落 T5-MEDIA-ARCHIVE-CONTRACT 定义的 `VerifiedBackupReceipt`。目的地品牌、URI 存在或 Worker 成功都不能替代内容回读。
- **恢复「先试跑后落刀」**（3 方一致）：选包 → 口令 → 解密展开到私有 staging 目录 → 逐文件 SHA-256 对 manifest → scope 校验（按物业包拒绝当全量恢复，格式卡语义）→ 全绿才原子替换（旧库改名保底、新库就位、成功后删旧；photos 目录同法）→ 失败任何一步：原数据不动、staging 清理。状态机纯 :core（文件系统抽象注入，JVM 临时目录测试）。
- 口令 UX：设置口令时明示「无找回」（ADR-0002 后果）；口令仅存派生验证哈希用于本机「改口令前验旧」（不存明文）。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Terra。难度 H。
