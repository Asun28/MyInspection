---
id: T5-BACKUP-FORMAT
title: 加密备份归档格式：流式 ZIP+AES-GCM + manifest + 防篡改/错口令测试（★冻结点）
depends_on: [T1-CANON-HASH]
parallelizable_with: [T2-ROUTINE-CONTENT, T2-PHOTO-PIPELINE, T2-CAPTURE-CORE, T2-PHRASELIB]
status: todo
branch: T5-BACKUP-FORMAT
worktree: C:\wt\T5-BACKUP-FORMAT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/backup/
  - android/core/src/test/kotlin/nz/myinspection/core/backup/
forbid:
  - 自研密码学原语（只组合 javax.crypto 标准件：AES/GCM/NoPadding + PBKDF2WithHmacSHA256 + SecureRandom）
  - 明文落任何中间文件
non_goals:
  - SAF/WorkManager IO 与恢复落刀（T5-BACKUP-IO；本卡纯格式层，流进流出）
  - 合并式恢复（v1 整包替换，ADR-0002）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.backup.*"
dod_exit: 0
dod_assert: 往返测试绿（构造数据集→加密归档→解密展开→逐文件 SHA-256 与 manifest 全对）；错口令报「口令错」不崩溃；篡改任一字节（头/密文/尾）被 GCM tag 或 manifest 校验拒收；按物业过滤导出只含该物业资产；格式头字段（magic/版本/盐/迭代数/kdf_id/nonce 前缀）齐全且测试锚定
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后 core/backup/ 格式部分登记 FrozenPaths（R5）
---

# T5-BACKUP-FORMAT

## 产出
`core/backup`：备份包格式（写入器/读取器，纯流接口）+ manifest 构造/校验 + 口令派生。**格式即长期契约（跨年备份要能被未来版本读回），合并即冻结。**

## 上下文包（执行模型必读）
- **包形态（ADR-0002/0003 · 经 2026-08-16 格式评审修订，见下）**：明文头（magic `MYINSPBK`、format_version、salt、pbkdf2 迭代数、kdf_id、nonce **前缀**）+ 密文体 = **分块 AEAD（STREAM 构造）**包 ZipOutputStream 流：64 KiB 明文分块、每块独立 AES-256-GCM 认证，`nonce = 前缀(7B) ‖ 块序号(4B BE) ‖ 终块标志(1B)`，AAD = 整个明文头——块序、块数、终块均受 tag 认证（防重排/截断/头字段偷换）。**流式**——照片总量 GB 级，全程恒定内存，禁整包入内存。
- **格式评审记录（2026-08-16 · 版本评审，W2 编排会话裁定）**：原设计「AES-256-GCM(CipherOutputStream) 单体包流」与「恒定内存」硬不变量实测互斥——JDK 17 SunJCE 解密态 `update(1 MiB)` 释放 0 字节、全量缓存至 `doFinal`；Android Conscrypt AEAD 走 `EVP_AEAD_CTX_seal/open` 双向单发（实测记录见 PR #9）。且任何「边解密边放明文」的单体 GCM 变体都会在 tag 校验前释放未认证明文，密码学上不可接受。故裁定：恒定内存不变量保留，密文体改分块 STREAM 构造（age/Tink 同款先例），仍只组合 javax.crypto 标准件、零新依赖，不触碰「自研密码学原语」禁线。codex R3 第 1 轮的 Dimension #7 process block（冻结契约变更须显式格式评审）由本记录满足。
- Zip 内容：`manifest.json`（canonical 序列化复用 core/canon；含 format_version、导出时间、app 版本、数据集范围（全量/物业 id）、每文件 rel_path+SHA-256+字节数）+ `db.sqlite`（DB 快照文件）+ `photos/**`、`audio/**`（按 rel_path 原样）+ `configs/`（当前合规规则 override 若有）。
- 口令派生：PBKDF2WithHmacSHA256，迭代数写进头（默认 210_000，可未来上调不破兼容）；盐 16B/nonce 12B SecureRandom。**无口令找回**（无服务端，ADR-0002 后果节）——格式层不留后门。
- 按物业导出：数据集过滤器（该物业的 property/tenancy/inspection/photo/audio/notice 闭包）；DB 快照仍整库（v1 简化，manifest 标记 scope）——**读取器按 manifest.scope 决定恢复语义**，避免「按物业包被当全量恢复」。
- 测试全 JVM：内存流 + 临时目录；GCM 篡改用直接翻字节；口令派生用小迭代数测试常量（头里带迭代数所以合法）。
- swap 备忘：若 R3 判 PBKDF2 迭代数不够档，升 Argon2 需第三方库（licence 审查后）——格式头留 kdf_id 字段（本版恒 1=PBKDF2）。

## 验收 / 执行建议
dod 见 front-matter。首选 Opus 5 · max（加密格式 = 错误代价最高的契约卡）；备选 Sonnet 5 max；Terra 对格式头/manifest 规范复读。难度 H+。
