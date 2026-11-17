---
id: T1-SHARE-SCREEN-PRIVACY
title: Verified report ShareStaging, narrow FileProvider, and temporary grants
depends_on: [T1-LOCAL-DATA-SECURITY, T1-PRIVACY-MANIFEST-POLICY-GATE, T3-REPORT-EXPORT-CORE]
status: todo
branch: T1-SHARE-SCREEN-PRIVACY
worktree: C:\wt\T1-SHARE-SCREEN-PRIVACY
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/res/xml/
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/share/
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/share/
forbid:
  - 运行期出站网络；修改冻结 schema/backup format；exported FileProvider、file:// URI、宽目录/永久 URI grant
  - 全局 FLAG_SECURE；把照片/DB/backup staging/secret root 暴露给分享 provider
  - 后台/启动时读取剪贴板；申请 READ_MEDIA_IMAGES/READ_EXTERNAL_STORAGE 只为导入照片；绕过系统 Photo Picker/SAF 扫描相册
  - 直接接受 File/String/path/MIME/hash 组合，或在本卡自造已验证报告信封；只接受 T3-REPORT-EXPORT-CORE 的不可伪造 verified-artifact primitive
  - 禁止未经授权的运行期出站网络、账号/RBAC、遥测；未经本卡 version review 不得改冻结 schema/backup format
non_goals:
  - SelectedMediaPolicy、ClipboardPolicy、SensitiveSurfacePolicy、backup/D2D/cleartext/读取权限闸（T1-PRIVACY-MANIFEST-POLICY-GATE）
  - PDF/HTML 渲染、验证回执、chooser 产品流程、UI 导航（T3-REPORT-EXPORT-CORE/UI）；remediation HTTP；SAF 备份/恢复
requirements:
  - "R1 分享只消费 T3-REPORT-EXPORT-CORE 的已关闭、重开验证且带类型/大小/SHA-256/内部相对位置的 typed verified artifact；不得用路径、扩展名或调用方 Boolean 替代。"
  - "R2 provider 仅映射 internal reports/export 子树；每次 ShareGrant 仅产生 content:// 和 temporary read grant。"
acceptance:
  - "A1 FileProvider merged-manifest 检查证明 exported=false、grantUriPermissions=true 且 paths XML 仅映射 internal reports/export；删除/错误替换任一生产配置后失败。"
  - "A2 ShareStaging 对 verified PDF/HTML 原子复制并关闭后复核 size/SHA-256/type，到随机 export 副本；拒绝任何未验证、越界、symlink、扩展名/MIME/签名字节不一致或仍写入来源。"
  - "A3 真实临时目录夹具证明 source mutation/concurrent writer、复制中断、路径穿越和 symlink 均不发布副本；启动/下次分享只删除超过24小时副本，不删源报告；ShareGrant 无 file:// 或持久授权。"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:check :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: :app:check 的真实 merged-manifest 解析证明 FileProvider exported=false、grantUriPermissions=true 与唯一 export 路径映射；app JVM 与 assemble 绿；真实临时目录测试证明 typed verified-artifact gate、原子 staging/reopen hash/type、symlink/concurrent mutation 拒绝、24小时清理与临时 content URI grant；本卡不声称 chooser 或 delivery。
review_gate: codex {verdict:pass}
hygiene: R4 对 artifact provenance、每个 type/hash/path 校验、provider exported/grant/path 配置、mutation/copy failure、过期清理及 grant flags 各保留生产语义变异
doc_sync: ADR-0006 + SECURITY + TASK-BOARD（R5）
---

# T1-SHARE-SCREEN-PRIVACY

## 产出与真实前置

本卡仅实现 `ShareStaging`、窄 `FileProvider` 与临时 `ShareGrant`。它必须消费 `T3-REPORT-EXPORT-CORE` 交付的不可由 UI/调用方构造的 verified-artifact primitive；该 primitive 是 export core 在关闭、重开、字节/size/SHA-256/fingerprint 验证并写格式回执后才可得到的证明。当前仓库尚无该类型或实现，故不能在 Export Core 前开始，也不能以 raw `File`、相对路径、MIME、hash 或 Boolean 自造替代品。

staging 在明确分享时将该已验证 artifact 从 internal `reports/{propertyId}/` 原子复制到 `reports/export/` 的不可预测名称；关闭后复核声明的 type/size/SHA-256。provider 只暴露 export 子树，grant 只有本次 read 的 `content://`。源报告永不删除；旧副本只在启动/下次分享时清理且年龄超过 24h。

## 体量结论

此卡保留原 staging/provider/grant 的完整验收，预估生产 380–520、真实文件系统/merged-manifest 测试 420–580、R4 与修复余量 160–230，合计 **960–1,330 changed lines / 62k–88k characters**。它不满足单卡 800行/50k 预警，不能按当前 ID 直接实施。

最小后续切点是先交付 `T1-VERIFIED-REPORT-STAGING`：只消费 Export Core primitive，完成 atomic copy/reopen/type/hash、symlink/concurrent mutation、24h cleanup 和真实文件系统测试；其后本卡仅完成 FileProvider merged config 与 temporary grant。两卡都必须依赖 Export Core，且不可互相伪造 artifact。首选 GPT-5.6 Terra · high；即使提高 effort 也不改变该拆分结论。
