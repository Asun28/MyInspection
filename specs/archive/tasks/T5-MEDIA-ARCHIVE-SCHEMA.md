---
id: T5-MEDIA-ARCHIVE-SCHEMA
title: 媒体归档 schema v5：四表形态、约束、索引与查询面
depends_on: [T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST, T2-PHOTO-PROPERTY-DEDUPE, T5-BACKUP-FORMAT]
status: merged
branch: T5-MEDIA-ARCHIVE-SCHEMA
worktree: C:\wt\T5-MEDIA-ARCHIVE-SCHEMA
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/
  - android/core/src/test/kotlin/nz/myinspection/core/template/TemplateRoomSchemaTest.kt
  - configs/secrets/tracked-sensitive-allowlist.json
forbid:
  - 关闭 verifyMigrations、绕过 FrozenPaths 或改写既有 1/2/3.sqm 与 1/2/3.db
  - 修改 photo、inspection、备份 FORMAT_VERSION 或新增 provider/账号字段
non_goals:
  - Kotlin 状态/资格服务（T5-MEDIA-ARCHIVE-ELIGIBILITY）
  - ArchiveStore 回读与回执创建编排（T5-MEDIA-ARCHIVE-CONTRACT）
acceptance:
  - "A1 已批准 schema v4→v5：新增 `4.sqm` 与受审 v4 基线 `databases/4.db`；从 4 迁 5 后既有表列集合及 photo/inspection 夹具逐字段不变，Schema.version 恰为 5，`:core:check` 的 verifyMainMyInspectionDatabaseMigration 全绿"
  - "A2 `local_asset_state` 列集合恰为 rel_path/content_hash/byte_size/state/changed_at/reason；state 仅 PRESENT/ARCHIVED/RESTORING，byte_size 非负，reason 非空；小写和尾空格状态均被真实 SQLite 约束拒绝"
  - "A3 `report_export_receipt` 列集合恰为 id/inspection_id/audience/quality/rel_path/content_hash/byte_size/exported_at；audience 仅 LANDLORD/TENANT，quality 仅 LOW/MEDIUM/HIGH/EXTRA_HIGH，(inspection_id,audience,quality) 唯一"
  - "A4 `verified_backup_receipt` 列集合恰为 id/destination_kind/destination_ref/object_ref/version_ref/exported_at/verified_at/scope_kind/scope_property_id/revoked_at；六个核心字段 NOT NULL，version_ref 可空，scope 只允许 full 或带非空 property id 的 property"
  - "A5 `verified_backup_receipt_entry` 列集合恰为 receipt_id/rel_path/content_hash/byte_size；(receipt_id,rel_path) 唯一且 byte_size 非负；四表所有声明列类型仅 TEXT/INTEGER，不持久化 BLOB、token、签名 URL 或 provider 凭据"
  - "A6 SQLDelight 查询面完整且确定：状态 upsert/read、PDF receipt insert/read、backup receipt/entry insert/revoke/read、按 rel_path 的候选回执、资产物业归属、四表主键序全量读取均由真实生成 API 编译执行；任一关键 WHERE/JOIN/ORDER BY 删除变异使对应测试变红"
dod_command: cmd /c "android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests nz.myinspection.core.media.archive.MediaArchiveSchemaTest && android\gradlew.bat -p android --offline --no-daemon -q :core:check"
dod_exit: 0
dod_assert: schema v5 的四表、封闭域、唯一约束、迁移保真、确定查询面与受审 databases/4.db 全部由真实 SQLite/SQLDelight 证明
review_gate: codex {verdict:pass}
version_review: approved 2026-08-29 — schema v4→v5；4.sqm 新增四表，databases/4.db 固定迁移起点；LANDLORD/TENANT 与 LOW/MEDIUM/HIGH/EXTRA_HIGH 为封闭域；backup FORMAT_VERSION 保持 1
boundary_exceptions:
  - "本次版本评审显式批准 A2–A5 的 exact column sets 作为通用 updated_at/deleted_at 规则的窄例外：local_asset_state 是以 rel_path 为自然键的当前物理状态；三张 receipt/entry 表是不可变证据账本，撤销只追加 revoked_at，不更新或软删既有证据，且查询面不得提供硬删除"
  - "本次版本评审显式批准 local_asset_state.rel_path 与 verified_backup_receipt_entry(receipt_id,rel_path) 作为通用 UUIDv7 主键规则的窄例外；report_export_receipt.id 与 verified_backup_receipt.id 仍由 Kotlin 服务生成 UUIDv7"
hygiene: 只交付 schema/query surface；按单点约束/谓词删除做 mutation-survivor 剪枝（R4）
doc_sync: 由最终 T5-MEDIA-ARCHIVE-CONTRACT 在 R5 统一同步 TD132 与 TASK-BOARD
---

# T5-MEDIA-ARCHIVE-SCHEMA

Light Plan Forge 1/3。一次性冻结后续两个实现卡需要的四表与查询面，避免同一 schema 设计被多次迁移。
本卡只证明存储边界，不宣称任何资产已经通过内容回读验证。

## R5

PR #195 / master `902228e1` 已合并；R3 pass。schema v5、v4 迁移基线、四表约束与完整查询面已冻结，`T5-MEDIA-ARCHIVE-ELIGIBILITY` 可开始。
