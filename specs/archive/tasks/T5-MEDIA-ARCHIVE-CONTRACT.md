---
id: T5-MEDIA-ARCHIVE-CONTRACT
title: 媒体归档收口：重新打开逐字节核验、原子回执与 finalized 不变性
depends_on: [T5-MEDIA-ARCHIVE-ELIGIBILITY]
status: merged
branch: T5-MEDIA-ARCHIVE-CONTRACT
worktree: C:\wt\T5-MEDIA-ARCHIVE-CONTRACT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/archive/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/
forbid:
  - 修改 frozen schema、既有 photo/inspection/finalized 行或备份 FORMAT_VERSION
  - 注入 SAF/S3/账号/计费类型，或持久化 token、签名 URL、云凭据
non_goals:
  - 实际导出/恢复（T5-BACKUP-IO）与照片字节清理/回填（T5-LOCAL-MEDIA-RETENTION）
  - 备份格式 v2、provider adapter、S3、账号或订阅实现
acceptance:
  - "A1 创建输入的 destination_kind/destination_ref/object_ref/exported_at/verified_at/scope 六项逐一缺失时均以 `[ARCHIVE-RECEIPT-INCOMPLETE]` 拒绝且 receipt/entry 行数为 0；version_ref=null 成功并读回 null"
  - "A2 全链路只注入 provider-neutral ArchiveStore（写入、重新打开、读 identity/version/revocation）+ ClockMs + Uuid7Generator；重新打开内容差 1 字节时以 `[ARCHIVE-VERIFY-READBACK-MISMATCH]` 拒绝且两表为 0，逐字节相同时原子写入 receipt 与全部 entries"
  - "A3 ArchiveStore 重新打开抛 IOException 时以 `[ARCHIVE-VERIFY-UNAVAILABLE]` 失败且 receipt/entry 均不落半条；既有 ARCHIVED 状态不变，并由 `assetsArchivedWithoutValidReceipt()` 暴露风险"
  - "A4 重新打开返回的 destination/object/version identity 必须与写入结果逐字段相等，revocation 可见性落 revoked_at；身份不符不得创建可用回执"
  - "A5 FINALIZED 巡检执行 ARCHIVED→写入→重新打开→建回执→资格判断全链前后，photo 与 inspection 按主键导出的全部列 SHA-256 逐字节相等；任一实现写入证据表的单句变异使测试变红"
  - "A6 固定 ClockMs 与固定 Uuid7RandomSource 的两套新数据库运行相同夹具，四张归档表按主键全量导出的 SHA-256 完全相等；全部测试零网络、零 wall-clock、零随机"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.media.archive.*"
dod_exit: 0
dod_assert: 只有重新打开逐字节一致且 identity/version/revocation 可见的 provider-neutral 归档才能原子创建 exact entries；离线/错字节/缺字段均 fail-closed，finalized 证据不变且结果确定
review_gate: codex {verdict:pass}
version_review: approved 2026-08-29 via T5-MEDIA-ARCHIVE-SCHEMA — schema v4→v5，backup FORMAT_VERSION 保持 1
hygiene: ArchiveStore fake 只替代外部存储边界；真实服务/数据库/哈希路径不 mock；关键失败分支逐一 mutation-survivor 剪枝（R4）
doc_sync: TD132 置 paid；同步 docs/DATABASE-DESIGN.md 与 docs/TASK-BOARD.md，并归档三个串行卡（R5）
---

# T5-MEDIA-ARCHIVE-CONTRACT

Light Plan Forge 3/3，也是对下游保持稳定的最终卡 ID。只有本卡合并后，T5-BACKUP-IO 与 T5-LOCAL-MEDIA-RETENTION 才可消费归档资格合同。

## 已批准版本评审

2026-08-29 用户批准 schema v4→v5：第一张子卡用 `4.sqm` 与受审 `databases/4.db` 一次性冻结四表和完整查询面；后两张卡不得静默增表、改列或提升备份 format_version。
