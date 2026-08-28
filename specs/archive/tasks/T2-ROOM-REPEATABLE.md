---
id: T2-ROOM-REPEATABLE
title: 房间 repeatable 契约与同窗口 schema 语义债收口（TD6/TD7/TD8）
depends_on: [T1-TEMPLATE-ENGINE, T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST]
status: merged
branch: T2-ROOM-REPEATABLE
worktree: C:\wt\T2-ROOM-REPEATABLE
allow_paths:
  - configs/secrets/tracked-sensitive-allowlist.json
  - android/core/build.gradle.kts
  - android/core/src/main/sqldelight/
  - android/core/src/main/kotlin/nz/myinspection/core/template/
  - android/core/src/test/kotlin/nz/myinspection/core/template/
  - data/templates/README.md
forbid:
  - 未授权的运行期出站网络 / 写登录态 / 自动发布
  - 落 `.sqm` 却不同时更新受审 schema 快照，或绕过已恢复的 verifyMigrations / 防泄露闸
non_goals:
  - 采集期真正实例化 room_instance 的状态机（T2-CAPTURE-CORE 消费本卡产出）
  - 真实模板内容里逐房间标注 repeatable（T2-ROUTINE-CONTENT / T6-TEMPLATES-REST）
acceptance:
  # 作者声明的验收清单：以下是本卡认为「完成」所需的事实，每条应有可证伪测试。
  # **这是一份声明，不改变任何评审语义**——裁决仍完全按 docs/QUALITY-RUBRIC.md 现行 rubric 判，
  # 清单未列到的问题照常按现行 rubric 处理（含其现行的 [FOLLOW-UP] 适用条件）。
  # 「把清单当排他性判据、清单外一律 FOLLOW-UP」是上游提案 Asun28/claude-devops-scaffold#203
  # 的内容，**上游落地前本仓不采用**。
  - "A1 版本评审即迁移闸的可执行证据：落新 `1.sqm`（schema 1→2）+ 同步更新受审快照 `databases/2.db`，`android/core/build.gradle.kts` 的 `verifyMigrations.set(true)` 与 `schemaOutputDirectory.set(file(\"src/main/sqldelight/databases\"))` 两行逐字不变（不加豁免、不改开关），`verifyMainMyInspectionDatabaseMigration` 随 `:core:check` 全绿；测试用 `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` 建 v1 baseline 后调 `MyInspectionDatabase.Schema.migrate(driver, 1, 2)`，断言迁移后 `check_item_def` 的 `PRAGMA table_info` 列名有序集合与迁移前**逐列相等**（房间定义走新表、绝不给 check_item_def 加列），且既有 3 行的 `stable_id`/`room`/`sort` 逐字段相等"
  - "A2 房间定义表形态闭集：新表 `template_room_def`，`PRAGMA table_info` 列名有序集合逐一等于 (`id`, `template_version_id`, `room_key`, `repeatable`, `sort`, `created_at`, `updated_at`, `deleted_at`)；`repeatable INTEGER NOT NULL` 带 `CHECK (repeatable IN (0, 1))`（同 `photo.privacy_flag` 先例）——写 0 与 1 各成功一次，写 2 与 -1 各抛 SQLite 约束异常；建部分唯一索引 `ON template_room_def (template_version_id, room_key) WHERE deleted_at IS NULL`（同 `idx_check_item_def_active` 先例），同版本重复 room_key 抛约束异常、不同版本同 room_key 两行均插入成功"
  - "A3 JSON 形态向后兼容（这是 non_goals 第 2 条的可证伪面）：`Template` 增 `rooms: List<TemplateRoom> = emptyList()`，新增类型 `TemplateRoom(key: String, repeatable: Boolean = false)` 落 `Template.kt`；对**缺 `rooms` 键**的既有字节 `data/templates/routine-v1.json`（经 `resources.srcDir(\"../../data/templates\")` 从测试类路径读原字节），`LoadedTemplate.parse` 成功且 `rooms.isEmpty()` 为 true、`TemplateLoader.validate` 返回**空列表**；含 `rooms` 键的字节解析出等长列表；键名拼错的 `roomsX` 仍抛 `kotlinx.serialization.SerializationException`（严格未知字段模式未被放宽）"
  - "A4 声明校验两侧边界 + 点名 + 一次报全：`rooms` 非空时，`item.room` 与某个 `rooms[].key` **精确相等**（`\"KITCHEN\"` = `\"KITCHEN\"`）即通过；`\"KITCHEN\"` 对声明 `\"KITCHE\"`（差 1 字符）、`\"kitchen\"` 对声明 `\"KITCHEN\"`（仅大小写差）两例均被拒，错误串为全 ASCII 的新增形态 `<stableId>: room <值> is not declared in rooms`（沿用既有 `<stableId>: <what>` 契约，L165 只认 ASCII 哨兵）；两条不同 stableId 各带未声明房间时 `validate` 一次返回 **2** 条错误（精确计数，不是「≥1」），证明「一次报全」承诺未被提前返回破坏"
  - "A5 rooms 自身校验：`rooms[i].key` 为空串 `\"\"` 被拒，错误串 `template: rooms[<index>].key is blank`（点位置，同既有 `item[<index>]` 先例）；`rooms` 内两个相同 key 被拒，错误串 `template: duplicate room key <值>`；`rooms` 为**空列表**时上述两条都不报错且 `validate` 返回空列表（正例，与 A3 同一保证）"
  - "A6 往返不丢失（本卡核心）：夹具 = 2 个房间（BEDROOM repeatable=true / KITCHEN repeatable=false）+ 3 条 item，`TemplateStore.persist(loaded)` 后 `TemplateStore.read(versionId)` 返回的 `Template` 与 `loaded.template` **整体 `==` 相等**（`type`/`version`/`items`/`rooms` 四段全等，不是逐字段抽查）；删掉实现里写 `template_room_def` 的那一句（单句删除变异）后该断言变红，且变异脚本按判据分类器核到失败文本"
  - "A7 repeatable 与单例在读回结果里可区分：读回的 `rooms.single { it.key == \"BEDROOM\" }.repeatable` 恰为 `true`、`rooms.single { it.key == \"KITCHEN\" }.repeatable` 恰为 `false`（断言两个精确布尔值，不是「至少有一个 true」），且两个 `TemplateRoom` 元素 `!=`；DB 侧 `template_room_def.repeatable` 两行分别读回 `1L` 与 `0L`"
  - "A8 房间顺序确定性：`rooms` 数组下标即 `template_room_def.sort`（0 起，同 `check_item_def.sort` 先例），读回查询排序为 `ORDER BY sort ASC, id ASC`（`sort` 撞值时有次级键，同 `selectByTemplateVersion` 的 `, id` tie-breaker 之理）；断言读回的 `rooms` 逐位置等于写入顺序；再把两行 `sort` 人为改成同一值后连读两次，两次返回的 `rooms` 列表相等（不依赖插入顺序或行 id 的偶然分布）"
  - "A9 TD7 历史读回不静默少项：新增查询 `selectByTemplateVersionIncludingDeleted`（**不过滤** `deleted_at`，排序仍 `sort ASC, id ASC`），`TemplateStore.read` 改用它；软删夹具——3 条 item 中 1 条置 `deleted_at` 非空后，`read()` 返回的 `items.size` 恰为 **3**，而既有冻结查询 `selectByTemplateVersion` 对同一夹具返回恰为 **2**（两个精确计数都断言，两侧都钉住）；房间侧同样不过滤，软删 1 个房间后 `read()` 的 `rooms.size` 恰为 **2**"
  - "A10 TD8 注释与权威实现一致（只改注释、不改存量值）：`TemplateVersion.sq` 的 `content_hash` 行注释携带 ASCII 哨兵 `SHA-256(template file raw bytes)`；测试以 Gradle `:core` 项目目录为工作目录读 `src/main/sqldelight/nz/myinspection/core/db/TemplateVersion.sq`，断言该哨兵在该行**存在**、且旧文案哨兵 `canonical JSON` 在该行**不存在**；同测试钉住权威实现——同一模板文档的两份字节（一份多两个空格缩进）经 `LoadedTemplate.parse(...).contentHash` 得到**不相等**的两值（证明哈希域是原始字节而非 canonical JSON），且各自等于对那份字节 `MessageDigest.getInstance(\"SHA-256\")` 的小写十六进制"
  - "A11 TD6 注释与权威实现一致，且哈希行为逐字节不变：`Supplement.sq` 的 `chain_hash` 行注释携带 ASCII 哨兵 `SHA-256(canonical({created_at, text}) + prev_hash)`（同 A10 的文件读法断言存在，且旧文案 `canonical(本行)` 不存在）；同测试用固定黄金向量钉住行为——`supplementChainHash(prev = \"0\" × 64, SupplementSnapshot(createdAt = 1_700_000_000_000L, text = \"x\"))` 等于本卡记录的那一个精确 64 位小写十六进制串；只改 `createdAt`（+1ms）与只改 `text`（`\"y\"`）两例返回值各自**不等于**该串，而同一 `SupplementSnapshot` 配不同 `supplement.id` / `inspection_id` 行值时返回值**相等**（证明哈希域恰为 {created_at, text} 两字段，不多不少）"
  - "A12 README 房间段与实现同源：`data/templates/README.md` 新增房间段，内含一个最小 `rooms` 片段；`android/core/build.gradle.kts` 的 test resources 精确 include 集合须加入 `README.md`（不恢复整个目录通配），测试从测试类路径读该资源，抽出其中标为 json 的代码块字节喂给 `LoadedTemplate.parse`，断言解析成功且 `TemplateLoader.validate` 返回**空列表**；删除 `README.md` 这一枚 include 后该测试因资源缺失精确翻红——文档示例因此不会随实现漂移成假"
  - "A13 非目标边界可证：本卡全部测试的夹具**从不插入 `room_instance` 行**，在此前提下 A1–A12 全绿（不做采集期实例化状态机，`room_instance` 的写入路径留给 T2-CAPTURE-CORE）；并断言 `data/templates/routine-v1.json` 与 `data/templates/phrases-v1.json` 的原始字节 SHA-256 各等于本卡开工前记录的那两个精确 64 位串（真实模板内容未被本卡改动）"
  - "A14 离线确定性与两道既有闸：全部测试零出站网络、零 wall-clock、零随机——`TemplateStore(db, uuid = Uuid7Generator(clock = ClockMs { 1_700_000_000_000L }, randomSource = FixedUuid7RandomSource(seed = 0x1234L)), clock = ClockMs { 1_700_000_000_000L })`，同一夹具连跑两次后对 `template_version` + `check_item_def` + `template_room_def` 三表按主键升序全量导出，两次字符串逐字节相等；`configs/secrets/tracked-sensitive-allowlist.json` 只新增 `android/core/src/main/sqldelight/databases/2.db` 这一条精确路径（不新增目录/glob/扩展名豁免），`pwsh -File scripts\\check-secrets.ps1 -Strict` 退出 0；本卡唯一验收出口是 `dod_command` 的退出码 0，清单内无任何发布/推送/写登录态动作"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.template.*"
dod_exit: 0
dod_assert: 模板 JSON 的房间定义带 repeatable 标记，经加载→校验→**入库→读回**往返不丢失；每条 item.room 必须在房间定义里声明（未声明即拒，错误点名条目）；repeatable 房间与单例房间在读回结果里可区分；模板历史读回不得因 check_item_def 软删过滤而静默缺项；两处冻结 schema 哈希注释与权威实现一致
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；data/templates/README.md 的房间段；TD6/TD7/TD8 状态与 TASK-BOARD 备注（R5）
---

# T2-ROOM-REPEATABLE

## 产出
模板契约里的**房间定义**（房间键 + `repeatable` 标记）+ 对应 schema 迁移（新 `.sqm` + 版本评审）+ 加载/校验/入库/读回往返 + README 的房间段。

## 拆分依据（为什么不在 T1-TEMPLATE-ENGINE 里做）
T1-TEMPLATE-ENGINE 的 R3 评审两轮都点到这一项：第 1 轮该评审者自己判为 `[FOLLOW-UP]`（理由正是"可能需要改动本卡 allow_paths 之外、且已冻结的持久层 schema"），第 2 轮升级为 block。仲裁结论 = 维持第 1 轮的判断，独立成卡，理由三条：

1. **存不下**：`check_item_def` 只有 item 级的 `room` 列，没有房间定义表，也没有 repeatable 列。`android/core/src/main/sqldelight/` 已在 `fcdc88d` 起登记为 FrozenPaths，加列/加表必须走新 `.sqm` + 版本评审——这在 T1-TEMPLATE-ENGINE 的 `allow_paths` 之外。
2. **半落地更坏**：只往模板 JSON 加 `rooms[]` 而不持久化，会造出「入库时静默丢字段」的路径——正是 T1-SCHEMA-CORE 用 17 轮评审清掉的那一类缺陷，不该在一张**冻结点卡**里新造一个。
3. **迁移闸已就绪**：TD4 已由 PR #47 与后续清理 PR #93 偿还；逐路径 schema 快照豁免和 `verifyMigrations` 已恢复。本卡现在可以在版本评审后提交第一份 `.sqm`，但不得绕过这两道闸。

## 禁止
见 front-matter。特别地：动冻结的 `sqldelight/` 目录**必须**以「本卡 = 该版本评审」的形式显式声明，并通过 TD4 已恢复的 schema 快照与迁移校验闸。

## 两处冻结物都要过版本评审（开卡第一步）
本卡要改的两个面**都已冻结**（`scripts/_config.ps1` FrozenPaths，`guard-frozen` 钩子会当场拒绝就地编辑）：
- `android/core/src/main/sqldelight/`（T1-SCHEMA-CORE 起）——房间定义表/列走**新 `.sqm`**，同步更新受审快照并通过 `verifyMigrations`；
- `android/core/src/main/kotlin/nz/myinspection/core/template/Template.kt`（T1-TEMPLATE-ENGINE 起）——模板 JSON 形态即契约，加 `rooms[]` 段就是改它。

故本卡第一步是**版本评审本身**：把变更提案连同两处影响面报给用户，取得放行后再从 FrozenPaths 临时摘除对应条目、落改动、合并后重新登记。**不要绕过 `guard-frozen`**。

## 同一版本评审窗口并入的既有债

为避免对同一冻结目录反复开窗，本卡同时偿还三个已登记的小债，不另拆实现卡：

- TD6：把 `Supplement.sq` 的链哈希注释改为 `{created_at, text}` 快照域，与 `supplementChainHash` 一致；只改注释，不改哈希行为。
- TD7：模板历史读回使用不过滤 `check_item_def.deleted_at` 的专用查询，或在发现不完整定义时明确失败；不得静默少项，并以软删夹具验证。
- TD8：把 `TemplateVersion.sq` 的 `content_hash` 注释改为模板文件原始字节 SHA-256，与 `LoadedTemplate.parse` 一致；只改注释，不改存量值。

三项都服从本卡相同的 FrozenPaths 临时摘除、版本评审、合并后重新登记与 R5 证据要求。

## 非目标（本卡刻意不做的能力）
见 front-matter：不做采集期的实例化状态机，不写真实模板内容。

## 验收（DoD = 命令 + 退出码 + 断言）
```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.template.*"
```
- 期望退出码：0
- 断言：见 front-matter `dod_assert`。关键是**往返**——只加 JSON 字段而读回时丢掉，等于没做（见「拆分依据」第 2 条）。
