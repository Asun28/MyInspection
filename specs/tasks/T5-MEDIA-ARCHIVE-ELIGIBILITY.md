---
id: T5-MEDIA-ARCHIVE-ELIGIBILITY
title: 媒体归档账本：本机状态、PDF 完成回执与 exact-content 资格判定
depends_on: [T5-MEDIA-ARCHIVE-SCHEMA]
status: todo
branch: T5-MEDIA-ARCHIVE-ELIGIBILITY
worktree: C:\wt\T5-MEDIA-ARCHIVE-ELIGIBILITY
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/archive/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/
forbid:
  - 修改 frozen schema、photo/inspection 行或备份 FORMAT_VERSION
  - 把 SAF URI、云盘品牌、Worker 成功或最近导出时间当作内容证明
non_goals:
  - 写入/重新打开归档目标与创建 verified receipt（T5-MEDIA-ARCHIVE-CONTRACT）
  - 实际 PDF 渲染、备份导出、清理或恢复照片字节
acceptance:
  - "A1 `recordAssetState` 使用注入 ClockMs 写 exact changed_at；空 reason 以 `[ARCHIVE-STATE-REASON-EMPTY]` 拒绝，单字符通过；状态转换只 upsert local_asset_state，photo/inspection 的所有列与 finalized data_hash 前后不变"
  - "A2 `recordReportExport` 持久化 LANDLORD/TENANT 与四档质量；同 inspection/audience/quality 重复写由唯一约束拒绝；`cleanupEligible(inspectionId)` 仅在两种 audience 都至少有一条回执时为 true，缺失值以 `[ARCHIVE-EXPORT-RECEIPT-MISSING]` 返回"
  - "A3 `archivedEligible(relPath,contentHash,byteSize)` 要求本机状态为 ARCHIVED 且有未撤销回执 entry 精确覆盖三元组；无 entry、hash 差一字符、N-1/N+1 分别返回 `[ARCHIVE-ASSET-NOT-COVERED]`、`[ARCHIVE-HASH-MISMATCH]`、`[ARCHIVE-SIZE-MISMATCH]`"
  - "A4 revoked_at 非空即失效：等于 verified_at 或早 1ms 都返回 `[ARCHIVE-RECEIPT-REVOKED]`，但 local_asset_state 行数、state、changed_at、reason 不回退"
  - "A5 property scope 通过 photo→room_instance→inspection.property_id 求权威归属；相等通过，跨物业返回 `[ARCHIVE-PROPERTY-MISMATCH]`，full 对两个物业均通过；BackupFormat.FORMAT_VERSION 仍为 1"
  - "A6 verified_at 等于注入 clock 通过，未来 1ms 返回 `[ARCHIVE-RECEIPT-FUTURE-TIME]`；相同固定 ClockMs 下状态/PDF 时间逐字段一致，生产实现不直读 System.currentTimeMillis"
  - "A7 仅 destination_ref URI、较新 exported_at、provider kind 与 ARCHIVED 状态而无 exact entry 时仍返回 `[ARCHIVE-ASSET-NOT-COVERED]`；补 exact entry 后才翻 true"
  - "A8 `assetsArchivedWithoutValidReceipt()` 只读、按 rel_path 稳定排序：有效 exact receipt 时为空；回执撤销、scope 不符或 entry 缺失时返回该 rel_path，不修改任何状态"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.archive.MediaArchiveLedgerTest" --tests "nz.myinspection.core.media.archive.MediaArchiveEligibilityTest"
dod_exit: 0
dod_assert: 状态/PDF 回执与 exact tuple、撤销、物业、未来时间、provider 假信号均由真实数据库行为判定，且 finalized 证据不变
review_gate: codex {verdict:pass}
hygiene: 测试只替换注入时钟/UUID，真实 SQLDelight 与领域服务不 mock；删除各资格分支须有 survivor 证据（R4）
doc_sync: 由最终 T5-MEDIA-ARCHIVE-CONTRACT 在 R5 统一同步
---

# T5-MEDIA-ARCHIVE-ELIGIBILITY

Light Plan Forge 2/3。消费已冻结的 v5 查询面，只建立本机物理状态和资格读模型；任何 provider 信号都不能替代 exact bytes 回执。
