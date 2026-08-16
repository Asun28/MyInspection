# 0002 — 数据落位与备份：app 私有存储 + SAF 加密归档导出（推翻「数据目录指向云盘同步文件夹」）

日期：2026-08-14 · 状态：**accepted（用户已签认 2026-08-15）**——本条推翻需求 §11 一处 [定]，签认后 §11 该 [定] 以本 ADR 为准 · 决策方式：3 方一致 + 用户签认

## 背景
需求 §11 [定]：「数据目录可自选，指向 Google Drive / OneDrive 同步文件夹 = 穷人版备份+同步」。这是桌面心智模型：Android 上 Drive/OneDrive 不做任意本地目录同步；SAF 树是 URI 文档提供者，不具备 SQLite 需要的锁/WAL/原子重命名/随机访问语义——**活数据库放云盘目录不可行**（3 方一致判定）。

## 决策
1. 活数据（SQLite + 照片/音频）落 **app 私有外部存储**（`getExternalFilesDir`；不进相册，含租客照片的隐私面收紧）。
2. 备份 = **加密归档导出**，满足 MVP 必需：
   - 手动：整包 + 按物业导出（`ACTION_CREATE_DOCUMENT`）；恢复（`ACTION_OPEN_DOCUMENT`）先入 staging 解密、逐 SHA-256 校验 manifest，全部通过才原子替换（先试跑后落刀）。
   - 自动：用户选定 SAF 目录树（`takePersistableUriPermission`）后，WorkManager 在每次 finalize 后 + 每周导出；目的地可以是 Drive/OneDrive 的 DocumentsProvider（写入即上传）。授权可能被系统收回——界面常驻显示「上次成功备份时间」，失败即提醒。
   - 格式：ZipOutputStream → **分块 AES-256-GCM（STREAM 构造）**流式加密（2026-08-16 格式评审修订：单体 GCM 实测无法流式解密——JCE 全量缓存至 doFinal、Conscrypt 双向单发，改逐块认证；理由与 nonce/AAD 方案见 `specs/tasks/T5-BACKUP-FORMAT.md`「格式评审记录」）；PBKDF2WithHmacSHA256 派生口令密钥；盐/迭代数/kdf_id/nonce 前缀/格式版本入明文头，GCM tag 逐块随密文（T5-BACKUP-FORMAT 冻结）。
3. 「数据目录可自选」改述为「**备份目的地可自选**」。

## 备选方案
- 活库直落 SAF/云盘目录：不可行（上文语义缺口）。
- 第三方 FolderSync 类工具带走私有目录：不可控、越权面大，弃。
- 数据库整体加密（SQLCipher）：威胁模型（设备丢失）已由备份包加密+系统锁屏覆盖，SQLCipher 社区版许可与复杂度不划算，v1 不做（需求 §11 安全基线一致）。

## 后果
- 换机/丢机恢复路径 = 最新备份包 + 口令。**口令找回不存在**（无服务端）——界面在设置口令时明示。
- 恢复 = 整包替换，不做合并（合并属未来「真同步」弧）。
