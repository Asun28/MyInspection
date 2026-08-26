---
id: T1-SHARE-SCREEN-PRIVACY
title: Android 隐私出口：安全文件分享 + 敏感窗口分级 + cleartext/系统备份清单闸
depends_on: [T1-LOCAL-DATA-SECURITY]
status: todo
branch: T1-SHARE-SCREEN-PRIVACY
worktree: C:\wt\T1-SHARE-SCREEN-PRIVACY
allow_paths:
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/res/xml/
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/
forbid:
  - 运行期出站网络；修改冻结 schema/backup format；exported FileProvider、file:// URI、宽目录/永久 URI grant
  - 全局 FLAG_SECURE；把照片/DB/backup staging/secret root 暴露给分享 provider
  - 后台/启动时读取剪贴板；申请 READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE 只为导入照片；绕过系统 Photo Picker/SAF 扫描相册
non_goals:
  - PDF 渲染/chooser 产品流程（T3-PDF-RENDERER）；remediation HTTP（T7-REMEDIATION）；SAF 备份/恢复（T5-BACKUP-IO）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM 测试与 assemble 绿：合并 manifest 保持 allowBackup=false、旧版/Android12+ 全域 backup+D2D 排除、cleartext=false，且无全相册读取权限；SelectedMediaPolicy 只允许系统 Photo Picker/SAF 返回的单项授权，ClipboardPolicy 禁止任何读取并只允许显式通知复制写入；FileProvider exported=false+grantUriPermissions=true 且 paths XML 只暴露 internal reports/export 子树；ShareGrant 只生成 content:// + temporary read grant；SensitiveSurfacePolicy 仅把 backup password、restore preflight、tenant contact、full sensitive photo 标 secure，普通 capture/list/report action 不受全局截图封锁
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-SHARE-SCREEN-PRIVACY

## 产出

提供 PDF-only `FileProvider`/临时授权、`SensitiveSurfacePolicy`、`SelectedMediaPolicy`、`ClipboardPolicy` 与合并 manifest 的系统备份/cleartext 硬闸。它定义隐私出口，不实现 PDF、备份或网络业务。

## 契约

- FileProvider 只映射专用 internal `reports/export/`，不映射目录根；provider 不导出，每次 intent 只给临时读权限。
- secure-window 采用敏感页面枚举，不全局禁截图；导出后的 PDF 明示离开 app 信任边界。
- 继续逐域排除系统 Auto Backup/D2D；即使未来加 `INTERNET`，cleartext 也默认 fail closed。
- 照片导入只消费系统 Photo Picker/SAF 返回的用户选中 URI，不申请或推导全相册可见性；剪贴板永不读，只有用户显式 `Copy notice` 时可写通知正文。

## 验收

见 front-matter。首选 GPT-5.6 Terra · high；备选 Sonnet 5 · max。难度 S–M。
