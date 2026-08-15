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
  - android/core/build.gradle.kts   # 窄幅修订：仅应用/配置 T0 已 pin 的 sqldelight 插件块，不新增依赖，见下方「验收」说明
forbid:
  - 碰 android/app/（并行卡领地）；build.gradle.kts 里新增/升级依赖版本（依赖已由 T0 全量 pin，本卡只挂 sqldelight{} 配置块——core/build.gradle.kts 自身注释即预留此项）
  - 自增整数主键（硬边界：UUIDv7）
non_goals:
  - canonical 哈希（T1-CANON-HASH）与模板加载（T1-TEMPLATE-ENGINE）
  - 任何 DAO 之上的业务逻辑（采集状态机归 T2-CAPTURE-CORE）
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.db.*" --tests "nz.myinspection.core.model.*"
dod_exit: 0
dod_assert: 全表建表/查询编译过；UUIDv7 六项测试（固定向量/version 位/variant 位/唯一性/同毫秒非降序/时钟回拨冻结）全绿；finalize 只读闸 + tenancy 基线指定各一例全绿。verifyMigrations 本卡未开——见下方「验收」说明的实测原因（与 check-secrets 防泄露闸冲突），TD 登记，非本卡实现质量问题
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
- 迁移：SQLDelight `.sqm` 显式编号；version 1（本卡）按 SQLDelight 官方约定零 .sqm 文件（"first schema version is 1"，`.sqm` 命名的是"迁移起点版本号"，v1 无前序版本可迁）。`verifyMigrations` 本卡**未开**——见「验收」说明。**合并后本目录冻结**——后续加表 = 新 .sqm + 版本评审，届时一并解决 verifyMigrations 的快照落库问题。
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
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.db.*" --tests "nz.myinspection.core.model.*"
```
- 退出码 0；断言见 dod_assert。
- **`-p android` 是本卡对原 dod_command 的必要修正**：`gradlew.bat` 不 `cd` 进自身目录，`task.ps1` 相位命令固定在
  worktree 根跑（`Push-Location $Wt`）——若不显式传 `-p android`，Gradle 会把 worktree 根当项目目录、报
  "does not contain a Gradle build"——实测复现（从主检出/worktree 根两处均如此），与实现质量无关，任何卡都会撞。
  加 `-p android` 显式指定项目目录后修复，已验证 RED 与 GREEN 均可达。
- **`android/core/build.gradle.kts` 窄幅纳入 allow_paths**：该文件现有注释原文写明"数据库 schema 由后续卡
  T1-SCHEMA-CORE 挂 sqldelight{} 配置块，本卡只 pin 依赖"——应用 `alias(libs.plugins.sqldelight)` + 配置
  `sqldelight { databases { create(...) { packageName.set(...) } } }` 是本卡产出（.sq 文件）能编译、DoD 能跑的
  必要前提，且不引入任何新依赖版本（libs.versions.toml 不动）。原卡遗漏此文件属疏漏，非范围蔓延。
- **`verifyMigrations` 本卡未开，dod_assert 已同步改写**：实测（非凭记忆）过程——① 设 `verifyMigrations.set(true)`
  但不设 `schemaOutputDirectory` → `verifyMainMyInspectionDatabaseMigration` 任务存在但运行期报
  "Verifying a migration requires a database file to be present"；② 同时设 `schemaOutputDirectory` 后
  `generateMainMyInspectionDatabaseSchema` 任务出现，跑出 `1.db`（167KB 空 schema 快照），此时 `:core:check`
  真的全绿；③ 但 `1.db` 一旦 `git add -f`（`.gitignore` 自身注释建议的路数）纳入，`scripts/check-secrets.ps1`
  的防泄露闸按文件名模式 `\.db$`（硬编码、无按文件豁免机制）判定「已追踪的敏感文件」**无条件** fatal——
  与 gitignore 无关（已追踪文件 gitignore 救不了），且 `scripts/` 是并行卡领地本卡不可改动加白名单。三步实测
  结论：SQLDelight 的 verifyMigrations 机制**结构性依赖一份已提交的 .db 快照**，而本项目的防泄露闸**无条件**
  禁止提交任何 `*.db` 文件——两者在当前配置下不可兼容，不是本卡实现或配置错误。已把此改动登记进
  `specs/tech-debt-tracker.md`；影响面小——version 1 零 .sqm 本就无迁移可验，真正开始起作用是从第一次
  加表/改列（第一份 .sqm）起，届时需一并解决快照落库问题（如按文件名例外、或换非 `.db` 后缀存储）。

## 执行建议（TASK-BOARD）
首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max；**冻结前 Opus 5 抽审一遍 schema**（列/索引/软删唯一性），R3 仍 Sol。难度 H（地基卡，宁慢勿错）。
