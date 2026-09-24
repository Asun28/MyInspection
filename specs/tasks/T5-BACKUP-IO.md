---
id: T5-BACKUP-IO
title: 备份落地：SAF 目的地 + 可验证资产回执 + 自动导出 + 恢复「先试跑后落刀」
depends_on: [T5-BACKUP-FORMAT, T2-PHOTO-PROPERTY-DEDUPE, T5-MEDIA-ARCHIVE-CONTRACT, T1-SHARE-SCREEN-PRIVACY, T1-LOCAL-DATA-SECURITY, T1-APP-BOUNDARY-ASSEMBLY]
parallelizable_with: [T3-PDF-RENDERER, T3-HISTORY-COMPARE, T4-COMPLIANCE-ENGINE]
status: todo
branch: T5-BACKUP-IO
worktree: C:\wt\T5-BACKUP-IO
plan_ref: context/DESIGN.md#offline-and-data-protection-experience
backup_scopes: [full]
backup_states: [NOT_CONFIGURED, READY, RUNNING, VERIFIED, PROVIDER_UNAVAILABLE, AUTHORIZATION_REVOKED, NEEDS_UNLOCK, NEEDS_PASSPHRASE, LOW_STORAGE, FAILED]
acceptance:
  - "A1 format v1 full backup exposes NOT_CONFIGURED through FAILED and PREPARING then ENCRYPTING then WRITING then VERIFYING, finishing only with a verified receipt; no new v1 property export is offered, legacy v1 property restore is rejected, and production v2 property export/restore belongs to T5-PROPERTY-RESTORE-INTEGRATION"
  - "A2 restore expands into staging, supports recovery cleanup and rollback, and uses verify-before-replace so the live data is untouched until verification passes"
  - "A3 provider, authorization, storage, and secret failures are distinct; secrets are referenced from protected storage and never exported or logged"
  - "A4 local/USB export and restore work in flight-mode without an account or network dependency"
  - "A5 the backup format remains the versioned authority for manifest, encryption, compatibility, and restore validation"
  - "A6 [R1] 后台任务/失效信封/未解锁夹具及真机 Keystore 证据对应到状态，旧 Verified 不被失败覆盖。"
  - "A7 [R2] 快照期间注入各 writer，重开 SQLite 后证明是一致时点，删除屏障会使负例失败。"
  - "A8 [R3] 树 URI 的 partial 发布与单文档原 URI 重开分别测试；改写/短读/撤权/低空间不签回执。"
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/backup/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/receipt/
  - android/core/src/main/kotlin/nz/myinspection/core/backup/restore/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/restore/
  - android/app/src/test/kotlin/nz/myinspection/app/export/backup/
  - android/app/src/main/kotlin/nz/myinspection/app/platform/composition/
  - android/app/src/test/kotlin/nz/myinspection/app/platform/composition/
forbid:
  - 向 app-private 之外写明文中间文件或持久化明文口令；跳过 manifest 校验的恢复路径（受保护私有恢复 staging 按 ADR-0006 允许）
non_goals:
  - 合并式恢复（v1 整包替换，ADR-0002）；口令找回（不存在，格式层无后门）
  - Google/OneDrive 账号接入、读取 Google Photos 的“已备份”状态、云端删除；执行本机照片清理（交 T5-LOCAL-MEDIA-RETENTION）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.restore.*" --tests "nz.myinspection.core.backup.receipt.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: 恢复状态机 JVM 测试绿（staging 展开→逐文件哈希校验→全对才 commit 替换；任一败=原库原文件不动；中途杀进程重启后残留 staging 被安全清理）；导出成功只有在目标 .mibk 关闭后重新打开、解密 manifest、逐一核对本次资产 rel_path/hash/size 才写 VerifiedBackupReceipt，失败/授权收回/内容缺失不得留下成功回执；assembleDebug 绿；真机冒烟：SAF 选 Drive/OneDrive 或本地树→手动整包导出→读回验证→「清库」→恢复全回来；自动导出在 finalize 后触发一次，记录附 PR
requirements:
  - "R1 当后台导出需要口令时，系统应使用 LocalSecretBox 解封，按已定状态区分未解锁与密钥失效，不得持久化明文或以口令哈希代替加密材料。"
  - "R2 当系统生成 DB 快照时，系统应保持一致性写入屏障或等价经验证快照保证，不能混入并发写入的一部分。"
  - "R3 当 SAF 导出完成时，系统应关闭并重开最终授权对象全验后再记录 Verified，树 URI 与单文档 URI 各按 ADR-0006 的发布协议执行。"
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T5-BACKUP-IO

## 产出
`app/export/backup`（`ArchiveStore` 的 SAF adapter、授权/流打开、WorkManager 调度、设置页备份区块：目的地/口令设置/上次成功时间常驻显示）+ `core/backup/receipt`（内容特定的验证回执）+ `core/backup/restore`（恢复状态机，纯 JVM 测）。v1 只实现 SAF；不得为未来 S3 引入 SDK、账号或网络端点。

## 上下文包（执行模型必读）
- SAF：`ACTION_OPEN_DOCUMENT_TREE` 选目的地 → `takePersistableUriPermission`（读+写持久）；每次导出经 DocumentFile 建 `myinspection-backup-YYYYMMDD-HHmm.mibk`（时间戳由 Clock 注入）；**授权可被系统收回**——导出 Worker 捕 SecurityException → 通知「备份目的地失效，请重选」+ 设置页红标（ADR-0002：常驻显示上次成功备份时间就是为此）。
- 自动导出：WorkManager——finalize 完成事件触发一次 + 每周期性（PeriodicWorkRequest, 约束：存储非低）；串行唯一队列（KEEP）防并发写同一目的地；导出内容走 T5-BACKUP-FORMAT 写入器（流式，DB 快照采用经验证的一致性在线快照，或在统一写入屏障内完成 checkpoint + 文件复制；屏障覆盖 capture/finalize/保留期等所有 DB writer，复制完成前不得释放。禁止无屏障的 checkpoint 后裸复制；provider 写入不占用 DB 屏障）。
- **上次成功时间不是清理授权**：导出关闭目标流后，必须经 SAF 重新打开该 `.mibk`，解密并核对 manifest 中每个照片的 `rel_path + SHA-256 + byte size`，才落 T5-MEDIA-ARCHIVE-CONTRACT 定义的 `VerifiedBackupReceipt`。目的地品牌、URI 存在或 Worker 成功都不能替代内容回读。
- **恢复「先试跑后落刀」**（3 方一致）：选包 → 口令 → 解密展开到私有 staging 目录 → 逐文件 SHA-256 对 manifest → scope 校验（按物业包拒绝当全量恢复，格式卡语义）→ 全绿才原子替换（旧库改名保底、新库就位、成功后删旧；photos 目录同法）→ 失败任何一步：原数据不动、staging 清理。状态机纯 :core（文件系统抽象注入，JVM 临时目录测试）。
- 口令 UX：设置口令时明示「无找回」（ADR-0002 后果）；用户口令是跨设备恢复根；本机只持久化 LocalSecretBox/Android Keystore 加密口令信封，供自动任务解封，信封不进入备份。未解锁返回 NEEDS_UNLOCK；key 失效/信封损坏返回 NEEDS_PASSPHRASE 并暂停自动备份，保留旧回执，不降级明文。单存验证哈希不能满足后台加密，不作为本卡实现。

## 验收 / 执行建议
dod 见 front-matter。首选 Sonnet 5 · max；备选 Terra。难度 H。

## 2026-09-06 范围与接口

本卡只负责 format v1 full 的导出/恢复，不提供 v1 property 导出；旧 v1 property 包拒绝恢复。format v2 与逐表闭包归各自前置卡；T5-PROPERTY-RESTORE-INTEGRATION 拥有 v2 property 的生产导出、SAF 关闭重开全验、回执及恢复接线。复用 ArchiveStore 与启动装配的写入/恢复协调能力；命令和确认边界见 `specs/android-module-boundaries.md`。

接线范围说明：新增 composition 两条路径用于注册备份维护/启动恢复与写入协调；只实现本卡恢复/快照的接入，不迁移其他功能。总路径较多但属于同一备份端到端交付，format v2 的物业导出/恢复由后续整合卡交付，仍属产品 V1。
