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
acceptance:
  # 封闭验收集合：以下即本卡「完成」的全部内容。清单内每条须有可证伪测试。
  - "A1 迁移即版本评审的可执行证据：落新 `1.sqm`（schema 1→2）+ 受审快照 `databases/2.db`，测试用 `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` 建 v1 baseline 后调 `MyInspectionDatabase.Schema.migrate(driver, 1, 2)`，断言迁移前后既有 13 张表（photo/inspection/room_instance/…）的 `PRAGMA table_info` 列集合逐列相等、且 photo 行的 `rel_path`/`content_hash` 逐字段相等；`android/core/build.gradle.kts` 的 `verifyMigrations.set(true)` 保持 true（不改这一行、不加豁免），`verifyMainMyInspectionDatabaseMigration` 随 `:core:check` 全绿"
  - "A2 状态域封闭：`local_asset_state.state` 带 `CHECK (state IN ('PRESENT','ARCHIVED','RESTORING'))`；三个合法值各成功插入一次，`'present'`（小写）与 `'ARCHIVED '`（尾随空格）各抛 SQLite 约束异常——同 `photo.privacy_flag`/`photo.source` 的 CHECK 先例（一个域外值就绕开全部按值过滤的查询）"
  - "A3 转换带时间与原因：每次状态写入落 `changed_at`（epoch 毫秒）+ `reason`（NOT NULL）；用固定 `nz.myinspection.core.db.ClockMs { 1_700_000_000_000L }` 注入，断言 `changed_at` 恰等 1700000000000（不是「大于 0」）；`reason` 传空串 `\"\"` 被拒并带新增状态码 `[ARCHIVE-STATE-REASON-EMPTY]`，传 1 字符 `\"a\"` 通过"
  - "A4 归档不动证据行：把一张 photo 置 ARCHIVED 前后各调一次 `photoQueries.selectById`，断言 `content_hash`/`rel_path`/`deleted_at`/`updated_at` 四列逐字段相等（`deleted_at` 两侧都为 NULL），且其父 `inspection.data_hash` 相等；`local_asset_state` 是按 `photo.rel_path` 追加的独立行，不是对 photo 的 UPDATE"
  - "A5 finalized 面逐字节不变：在 `inspection.status='FINALIZED'` 的巡检上跑完「置 ARCHIVED → 建回执 → 判 archivedEligible」整条链路，对 `photo` 与 `inspection` 两表按主键升序导出全部列拼成 canonical 字符串取 SHA-256，链路前后两个哈希字符串相等；把实现里任一处改成写 photo/inspection 即让该断言变红（配一枚单句变异证明）"
  - "A6 回执六必填 + 一可空：`verified_backup_receipt` 的 `destination_kind`/`destination_ref`/`object_ref`/`exported_at`/`verified_at`/`scope_kind` 六字段必填，`version_ref` 可空；六字段逐一置 NULL 共 6 个用例，各以新增状态码 `[ARCHIVE-RECEIPT-INCOMPLETE]` 拒绝且 `verified_backup_receipt` 行数仍为 0；六字段齐备而 `version_ref = NULL` 的一例创建成功并读回 `version_ref` 为 null"
  - "A7 exact tuple 覆盖：`archivedEligible(relPath, contentHash, byteSize)` 仅在存在 entry 三元组逐字段全等时为 true；正例（三元组全等）为 true；同 rel_path 但 `content_hash` 差 1 个十六进制字符为 false 且理由 `[ARCHIVE-HASH-MISMATCH]`；同 rel_path 同 hash 但 `byte_size` 取 N-1 与 N+1 两例均为 false 且理由 `[ARCHIVE-SIZE-MISMATCH]`（N = 正例的精确字节数，断言精确值而非「不等即可」）"
  - "A8 撤销边界与状态不回退：`revoked_at IS NULL` 的回执判 true；把同一回执 `revoked_at` 置为与 `verified_at` **相同**的毫秒值（最早可撤销点）即 false 且理由 `[ARCHIVE-RECEIPT-REVOKED]`，置为 `verified_at - 1` 同样 false；撤销后再查 `local_asset_state`，该 rel_path 仍读回 `'ARCHIVED'`（行数与值前后相等）——资格翻假但状态不被自动改写"
  - "A9 物业边界同一把尺子：entry 的 rel_path 归属物业（经 photo→room_instance→inspection.property_id 两跳求得）与回执 `scope_kind='property'` 的 `scope_property_id` 相等即 true，不等即 false 且理由 `[ARCHIVE-PROPERTY-MISMATCH]`；`scope_kind='full'` 对上述两个物业均为 true——判定复用已冻结的 `BackupScope.Property.includes` 语义，同测试断言 `BackupFormat.FORMAT_VERSION == 1` 未被改动"
  - "A10 未来时间与时钟注入：`verified_at == clock.nowMs()` 通过（等号边界放行），`verified_at == clock.nowMs() + 1` 被拒且理由 `[ARCHIVE-RECEIPT-FUTURE-TIME]`；时间源恒为注入的 `ClockMs`——同一固定值 `ClockMs` 下同一夹具连跑两次，写入的 `exported_at`/`verified_at`/`changed_at` 三列逐字段相等（直读 `System.currentTimeMillis()` 会让该断言变红）"
  - "A11 双版本 PDF 回执才够清理资格：`report_export_receipt` 对 (`inspection_id`, `audience`, `quality`) 建唯一索引（重复插入抛约束异常）；`audience` 带 CHECK 封闭域（待核实：房东版/房客版两值的字面量由 T3-REPORT-COMPOSER 钉定，本卡以 CHECK 固化）；两版回执齐备时 `cleanupEligible(inspectionId)` 为 true，删房东版一例、删房客版一例共 2 例均为 false 且理由 `[ARCHIVE-EXPORT-RECEIPT-MISSING]`（含缺失的 audience 值）"
  - "A12 表形态闭集（正面清单，非名字黑名单）：对 `local_asset_state`/`report_export_receipt`/`verified_backup_receipt`/`verified_backup_receipt_entry` 四表各跑 `PRAGMA table_info`，断言列名有序集合**逐一等于**本卡声明的清单（多一列即红，故短时签名 URL/token/云凭据这类列加不进来），且四表列 `type` 的取值集合 ⊆ {TEXT, INTEGER}（无 BLOB，照片字节仍只在文件系统）"
  - "A13 回执须经重新打开并解密核验：全链路只注入一个 `ArchiveStore` 测试替身（能力恰为写入 / 重新打开 / 读身份与版本 / 读撤销可见性）+ 固定 `ClockMs`，不注入 SAF/S3/账号/计费类型；替身「重新打开」返回与写入时差 1 字节的内容 → 创建被拒、抛新增状态码 `[ARCHIVE-VERIFY-READBACK-MISMATCH]` 且 `verified_backup_receipt` 与 entries 两表行数均为 0；返回逐字节相同的内容 → 创建成功、entries 行数等于资产数"
  - "A14 目标离线不伪造成功：`ArchiveStore` 替身在「重新打开」时抛 `IOException`，创建以新增状态码 `[ARCHIVE-VERIFY-UNAVAILABLE]` 失败，断言两张回执表行数前后都为 0（不落半条）；同时 `local_asset_state` 里既有的 `'ARCHIVED'` 行数与 `state` 值前后相等，并由公开只读查询 `assetsArchivedWithoutValidReceipt()` 返回该 rel_path（正例——有有效回执时返回空列表）"
  - "A15 provider 信号一律不构成「内容已验证」：构造夹具「有 SAF URI 形态的 `destination_ref` + 有较新的 `exported_at`（模拟『上次备份时间』）+ 状态已置 ARCHIVED，但 entries 表中没有任何行覆盖该 rel_path」，`archivedEligible` 为 false 且理由 `[ARCHIVE-ASSET-NOT-COVERED]`；仅当补上一条逐字段全等的 entry 后同一调用才翻为 true——同一断言即证明 destination_kind 的取值（云盘品牌）不参与资格判定"
  - "A16 确定性与离线：本包全部测试零网络、零 wall-clock、零随机——主键用 `Uuid7Generator(clock = ClockMs { 1_700_000_000_000L }, randomSource = <固定 Uuid7RandomSource>)` 生成，同一夹具连跑两次后对四张新表按主键升序全量导出取 SHA-256，两次哈希字符串相等"
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
`android/core/src/main/sqldelight/` 已冻结。本卡就是该新增表版本的显式评审卡：先获用户放行并确认 schema version，再落新 `.sqm` 和快照；TD4 已偿还，必须沿用其恢复的迁移校验，禁止关掉校验或绕过 `guard-frozen`。备份 manifest 的字段已经足以验证资产，本卡不改冻结的 format_version。

## 不变量
- “记录是否存在”和“本机字节是否存在”分离；归档绝不软删 photo/inspection/report 元数据。
- 回执可失效/撤销，不能因目标暂时离线自动伪造成功；既有 ARCHIVED 状态仍保留并向 UI 暴露风险。
- 时间计算使用注入 Clock；物业边界与 rel_path 归属必须一致。
- 领域层只依赖 `ArchiveStore` 能力（写入、重新打开、读取身份/版本、撤销可见性），不依赖 SAF、S3 SDK、计费或账号类型。v1 只有 SAF adapter；未来 hosted adapter 必须复用同一 exact-content 资格判定。

## 验收
见 front-matter。首选 Opus 5 · max；备选 Sonnet 5 · max。难度 H。
