---
id: T1-SCHEMA-CORE
title: SQLDelight 全量 schema + UUIDv7 + 基线迁移 + JVM 测试（★冻结点）
depends_on: [T0-TOOLCHAIN]
parallelizable_with: [T1-SPIKE-PLATFORM]
status: todo
branch: T1-SCHEMA-CORE
worktree: C:\wt\T1-SCHEMA-CORE
allow_paths:
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/model/
  - android/core/src/main/kotlin/nz/myinspection/core/db/
  - android/core/src/test/kotlin/nz/myinspection/core/
forbid:
  - 碰 android/app/（并行卡领地）与构建文件（依赖已由 T0 全量 pin）
  - 自增整数主键（硬边界：UUIDv7）
non_goals:
  - canonical 哈希（T1-CANON-HASH）与模板加载（T1-TEMPLATE-ENGINE）
  - 任何 DAO 之上的业务逻辑（采集状态机归 T2-CAPTURE-CORE）
dod_command: cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.db.*" --tests "nz.myinspection.core.model.*"
dod_exit: 0
dod_assert: 全表建表/查询编译过；UUIDv7 六项测试（固定向量/version 位/variant 位/唯一性/同毫秒非降序/时钟回拨冻结）全绿；verifySqlDelightMigration 挂进 :core:check 且绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后把 android/core/src/main/sqldelight/ 登记进 scripts/_config.ps1 FrozenPaths（R5）
---

# T1-SCHEMA-CORE

## 产出
`.sq` 全量 schema（真相源）+ 生成的类型化 Kotlin 访问层 + 自研 UUIDv7 + 迁移基线 + 纯 JVM 测试（JdbcSqliteDriver 内存库）。

## 上下文包（执行模型必读）
- **表清单与不变量（每表：TEXT 主键存 canonical 小写 UUIDv7 · updated_at UTC epoch 毫秒 · deleted_at 可空软删）**：
  - property（address、kind：RENTAL/OWNER_OCCUPIED、is_boarding_house 影响时段规则）
  - tenancy（property_id、start/end 毫秒、tenant_name/contact **均可空**、baseline_inspection_id 可空）——见下「用户已签认决策」两条
  - template_version（type：ROUTINE/INGOING/EXIT/ANNUAL、version、content_hash）——**被任何巡检引用后不可变**（先例：templateSnapshot 语义；我们以不可变版本行等价实现，报告多年后须可一致重渲）
  - check_item_def（template_version_id、stable_id TEXT ★历史对齐唯一键、area、room、双语文案 en/zh、allowed_statuses）
  - inspection（type、tenancy_id 可空(自住)、scheduled_at、previous_inspection_id、baseline_inspection_id（**双轨：时间前次 ≠ tenancy 的 Ingoing**）、status：DRAFT/FINALIZED、finalized_at、data_hash）
  - room_instance（inspection_id、room_key（模板房间键，如 BEDROOM）、instance_no、display_label（如 "Bedroom 2 / 次卧"））——**调研修正（docs/research/opensource-indie.md 要点 6）**：房间是实例不是模板常量，多卧室同 stable_id 不得冲突；ODK/Fulcrum 先例收敛
  - inspection_item（inspection_id、room_instance_id、stable_id、status TEXT（枚举按模板类型校验在 :core 层）、note、wear_or_damage 可空：FAIR_WEAR/DAMAGE/UNDETERMINED（仅 Exit 且与 Ingoing 有差异））；唯一键 (inspection_id, room_instance_id, stable_id)
  - photo（inspection_item_id 可空 + room_instance_id（room 级全景挂实例）、rel_path、content_hash、exif_time_ms 可空、source：CAMERA/IMPORTED、**privacy_flag**（含租客物品标记——NZ OPC 判例风险，报告可排除；docs/research/synthesis.md「照片隐私」））
  - property_item_override（property_id、stable_id、suppressed）——「本物业不存在此项」永久抑制（zInspector `Ø` 先例：模板现场自愈，免每次 N_A；synthesis 建议 #4）
  - audio（inspection_item_id、rel_path、content_hash）
  - supplement（inspection_id、created_at、text、prev_hash——finalize 哈希链，append-only）
  - notice（inspection_id、full_text 全文快照、generated_at、sent_via、sent_at、lead_hours、validation_snapshot）
  - phrase_entry（en、zh、category、sort）
- 评级枚举（存 TEXT，合法值由模板类型定）：租赁 GOOD/FAIR/POOR/NOT_APPLICABLE；年检 5 态 NO_ISSUE/MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT/NOT_APPLICABLE（ADR-0003/讨论修正）。
- **UUIDv7 自研**（ADR-0003，2-1 决）：RFC 9562——48 位 epoch 毫秒 + ver 0111 + 12 位 rand_a + variant 10 + 62 位 rand_b；SecureRandom；AtomicReference 保同毫秒单调；时钟回拨=沿用上一时间戳（冻结语义）。测试全单照 dod_assert 六项。若评审判不可靠，swap `uuid-creator`(MIT) `getTimeOrderedEpoch()` 为一行级改动。
- 迁移：SQLDelight `.sqm` 显式编号；本卡产出 1.sqm 基线 + `verifySqlDelightMigration` 挂 :core:check。**合并后本目录冻结**——后续加表 = 新 .sqm + 版本评审。
- finalize 只读强制在 SQL 层预埋：所有 UPDATE/DELETE inspection/inspection_item 的 query 恒带 `finalized_at IS NULL`（或 join 到 DRAFT 巡检）；测试写一例「对 FINALIZED 行 update 计 0 行」。
- 照片哈希/EXIF 语义只建列不写逻辑（T2-PHOTO-PIPELINE 实现）。

### 用户已签认决策（2026-08-15 · TASK-BOARD「用户已定」#2/#3/#7/#8 · 冻结前必须落进本卡 schema）
- **既有租约没有 Ingoing，基线要能后指定**（用户实况：2 套以上物业、部分已在租 ⇒ Ingoing 永远补不回来）。落法：
  `tenancy.baseline_inspection_id`（可空，逻辑外键 → inspection.id）= 该 tenancy 的**权威基线指针**，默认在建 Ingoing 时写入，
  也允许把某次 Routine 指定为基线（app 存量租约的唯一出路）。**Exit 的对照方一律读这一列**，不得假设「必有 type=INGOING 的巡检」。
  与 `inspection.baseline_inspection_id`（那一列是**该次巡检当时**用的基线快照）双轨并存、语义不同：tenancy 上的是**当前指针**（可改），
  inspection 上的是**历史事实**（写死不改）。测试须含一例：tenancy 无 Ingoing、指定 Routine 为基线后 Exit 能解析到它。
- **租客联系方式保留期 = 租约结束后 12 个月，到期置 NULL 不删行**。故 `tenant_name`/`contact` 必须**可空**，
  且清理是 `UPDATE ... SET tenant_name=NULL, contact=NULL`，**绝不 DELETE tenancy 行**——照片/报告/哈希是法定证据（Rentals Act s123A
  的 12 个月是**下限**），删行会切断 inspection → tenancy → property 的证据链。本卡只建列 + 保证可空；清理逻辑归 T5-RETENTION。
- **不做**：Condition/Cleanliness 全量双刻度（#7）、缺陷责任方/费用字段（#8）。别自作主张加这两组列——加了就得走版本评审才能删。

## 禁止 / 非目标
见 front-matter。

## 验收
```powershell
cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.db.*" --tests "nz.myinspection.core.model.*"
```
- 退出码 0；断言见 dod_assert。

## 执行建议（TASK-BOARD）
首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max；**冻结前 Opus 5 抽审一遍 schema**（列/索引/软删唯一性），R3 仍 Sol。难度 H（地基卡，宁慢勿错）。
