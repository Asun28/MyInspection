---
id: T5-MEDIA-ARCHIVE-CONTRACT
title: 媒体归档契约：本机物理状态 + 内容特定的已验证备份回执
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T2-PHOTO-PROPERTY-DEDUPE, T5-BACKUP-FORMAT]
status: todo
branch: T5-MEDIA-ARCHIVE-CONTRACT
worktree: C:\wt\T5-MEDIA-ARCHIVE-CONTRACT
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/media/archive/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/
forbid:
  - 绕过 FrozenPaths；修改既有 photo 内容哈希或 finalized 行
  - 把 SAF URI、云盘品牌、Worker 成功或“上次备份时间”当成内容已验证
non_goals:
  - 实际导出/回读（T5-BACKUP-IO）；清理/恢复照片字节（T5-LOCAL-MEDIA-RETENTION）；备份格式 v2；S3/账号/订阅实现
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.archive.*"
dod_exit: 0
dod_assert: 新 schema 以追加表表达 PRESENT/ARCHIVED/RESTORING 状态、PDF 生成回执、VerifiedBackupReceipt 及逐资产 rel_path/hash/size；finalized photo 行和 canonical hash 不被更新；只有完整且未撤销的回执覆盖 exact tuple 才可判 archivedEligible；缺一字段、哈希/大小变化、回执撤销、跨物业或未来时间均拒绝；迁移升级/读回测试与版本评审全绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TD132 状态、schema 契约与 TASK-BOARD 备注（R5）
---

# T5-MEDIA-ARCHIVE-CONTRACT

## 产出
不改 `photo` 证据行，新增独立的物理资产状态与归档证明：

- `local_asset_state`：照片逻辑记录仍在时，其本机字节为 PRESENT / ARCHIVED / RESTORING；状态转换带时间与原因。
- `report_export_receipt`：某 inspection/audience/quality 的 PDF 已成功原子落盘；清理资格至少要求房东版和房客版按产品契约生成完成。
- `verified_backup_receipt` + entries：provider-neutral 的 `destination_kind + opaque destination_ref + opaque object_ref + optional version_ref`、导出/验证时间、scope，以及每个资产的 `rel_path + content_hash + byte_size`；只有目标重新打开并解密核验后才能创建。短时签名 URL、token 和云凭据不得持久化。

## 冻结物版本评审
`android/core/src/main/sqldelight/` 已冻结。本卡就是该新增表版本的显式评审卡：先获用户放行并确认 schema version，再落新 `.sqm` 和快照；先还 TD4，禁止关掉迁移校验或绕过 `guard-frozen`。备份 manifest 的字段已经足以验证资产，本卡不改冻结的 format_version。

## 不变量
- “记录是否存在”和“本机字节是否存在”分离；归档绝不软删 photo/inspection/report 元数据。
- 回执可失效/撤销，不能因目标暂时离线自动伪造成功；既有 ARCHIVED 状态仍保留并向 UI 暴露风险。
- 时间计算使用注入 Clock；物业边界与 rel_path 归属必须一致。
- 领域层只依赖 `ArchiveStore` 能力（写入、重新打开、读取身份/版本、撤销可见性），不依赖 SAF、S3 SDK、计费或账号类型。v1 只有 SAF adapter；未来 hosted adapter 必须复用同一 exact-content 资格判定。

## 验收
见 front-matter。首选 Opus 5 · max；备选 Sonnet 5 · max。难度 H。
