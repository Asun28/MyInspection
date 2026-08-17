---
id: T1-SCHEMA-CORE
title: SQLDelight 全量 schema + UUIDv7 + 基线迁移 + JVM 测试（★冻结点）
depends_on: [T0-TOOLCHAIN]
parallelizable_with: [T1-SPIKE-PLATFORM]
status: merged
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
dod_assert: 全表建表/查询编译过（含每表 created_at，actor 字段明确不加）；UUIDv7 七项测试（固定向量全 128 位/version 位/variant 位/唯一性/同毫秒非降序/时钟回拨冻结/计数器耗尽不环绕，固定向量期望值系独立语言算出的字面量）全绿；finalize 只读闸覆盖 update 与 insert 两侧（inspection_item/room_instance/photo/audio）各一例全绿，insert 侧 guard 用 EXISTS 证父行存在且归属正确（非「标量子查询 IS NULL」，那对不存在的父行会误判通过）、缺失/错配父行各至少一例；inspection 的 status/finalized_at/data_hash 三态一致性由 CHECK 约束保证、非法组合与未知 status 各一例必报错；EXIT 巡检经 tenancy 基线指针解析（非假设 INGOING 存在）一例全绿；tenancy.purgeContactInfo 清联系方式一例全绿、传 NULL purged_at 不落地不腐坏行一例全绿；软删除唯一性用部分唯一索引（非表级 UNIQUE+deleted_at，SQLite NULL 不互等）、每条各一例重复插入必报错；core/model/ 的 InspectionSnapshot/SupplementSnapshot 每一个嵌套类型逐字段参与相等性各一例全绿（不是只测顶层）；计数器耗尽回归断言前推**恰好** 1ms（解码时间戳比较，非只判"更大"）；四条下游查询（wear_or_damage 更新受 finalize 守卫、property_item_override 同行切换抑制/恢复、notice.recordDelivery 一次性锁定且 sent_via/sent_at 任一传 NULL 均不落地不锁行、photo.softDelete 受 finalize 守卫且传 NULL deleted_at 不落地 + orphanedAssets **按 rel_path 判活并只返回 rel_path**，且同一路径被活跃行引用时永不上报——含「活跃行属 FINALIZED 巡检且哈希不同」一例）各至少一例全绿；notice 的 sent_via/sent_at CHECK 约束两种错配组合各一例必报错，scheduled_at 快照独立存储一例全绿；supplement 的 prev_hash 非空锚定 inspection.data_hash + chain_hash 落库一例全绿，同 created_at 两行按 id 兜底排序确定一例全绿。verifyMigrations 本卡未开——见下方「验收」说明的实测原因（与 check-secrets 防泄露闸冲突），已登记 TD4，非本卡实现质量问题
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: CLAUDE.md 当前阶段；合并后把 android/core/src/main/sqldelight/ 登记进 scripts/_config.ps1 FrozenPaths（R5）
---

# T1-SCHEMA-CORE

## 产出
`.sq` 全量 schema（真相源）+ 生成的类型化 Kotlin 访问层 + 自研 UUIDv7 + 迁移基线 + `core/model/` 不可变快照类型
（`InspectionSnapshot`/`SupplementSnapshot`，T1-CANON-HASH 哈希域的输入形状）+ 纯 JVM 测试（JdbcSqliteDriver 内存库）。

## 上下文包（执行模型必读）
- **表清单与不变量（每表：TEXT 主键存 canonical 小写 UUIDv7 · created_at + updated_at UTC epoch 毫秒 ·
  deleted_at 可空软删）**：`created_at` 是本卡在 CLAUDE.md「关键不变量」显式列的 updated_at+deleted_at
  之上**额外补的**——updated_at 会被后续修改覆盖，没有 created_at 单靠它恢复不出创建历史。**明确不加**
  `created_by`/`updated_by` 等 actor 字段：本 app 单用户、无账号体系（CLAUDE.md 硬边界「永不做…任何账号
  体系」），记录"是谁改的"在只有一个用户的设备上没有意义，加了就是死重量。supplement 已有 created_at
  （领域字段本就是这个含义，不重复加）；notice.generated_at 同理身兼两职（一行 notice 恒在生成的那一刻
  插入），也不重复加。
  - property（address、kind：RENTAL/OWNER_OCCUPIED、is_boarding_house 影响时段规则）
  - tenancy（property_id、start/end 毫秒、tenant_name/contact **均可空**、baseline_inspection_id 可空、
    **purged_at** 可空——T5-RETENTION「一键清理=置空联系方式+标记 purged_at」需要，其 allow_paths 不含
    core/db/，只能调用本卡已生成的 API，故本卡建列+机械查询 `purgeContactInfo`，何时/是否清理的判断逻辑仍归它）——见下「用户已签认决策」两条
  - template_version（type：ROUTINE/INGOING/EXIT/ANNUAL、version、content_hash）——**被任何巡检引用后不可变**（先例：templateSnapshot 语义；我们以不可变版本行等价实现，报告多年后须可一致重渲）
  - check_item_def（template_version_id、stable_id TEXT ★历史对齐唯一键、area、room、双语文案 en/zh、allowed_statuses、
    **photo_rule** 可空（T1-TEMPLATE-ENGINE 模板 JSON 同名字段：ROOM_PANORAMA/ADVERSE_ONLY，NULL=无强制拍照）、
    **sort** 不可空（T1-CANON-HASH 哈希域要求 items[] 按"模板序"排列，不能靠插入顺序/行 id））
  - inspection（type、**property_id**（每次巡检恒属于某处物业，tenancy_id 可空时没有其它路径关联 property）、
    tenancy_id 可空(自住)、**template_version_id**（逻辑外键→template_version.id，该次巡检当时用的具体模板版本，
    缺了它多年后无法确定当时是哪版模板内容）、scheduled_at、previous_inspection_id、baseline_inspection_id
    （**双轨：时间前次 ≠ tenancy 的 Ingoing**）、status：DRAFT/FINALIZED、finalized_at、data_hash）——
    **status/finalized_at/data_hash 三态一致性由 CHECK 约束保证**（DRAFT⇔两者皆 NULL、FINALIZED⇔两者皆非
    NULL，其余组合含未知 status 值一律拒绝插入）
  - room_instance（inspection_id、room_key（模板房间键，如 BEDROOM）、instance_no、display_label（如 "Bedroom 2 / 次卧"））——**调研修正（docs/research/opensource-indie.md 要点 6）**：房间是实例不是模板常量，多卧室同 stable_id 不得冲突；ODK/Fulcrum 先例收敛
  - inspection_item（inspection_id、room_instance_id、stable_id、status TEXT（枚举按模板类型校验在 :core 层）、note、wear_or_damage 可空：FAIR_WEAR/DAMAGE/UNDETERMINED（仅 Exit 且与 Ingoing 有差异））；唯一键 (inspection_id, room_instance_id, stable_id)
  - photo（inspection_item_id 可空 + room_instance_id（room 级全景挂实例）、rel_path、content_hash、exif_time_ms 可空、source：CAMERA/IMPORTED、**privacy_flag**（含租客物品标记——NZ OPC 判例风险，报告可排除；docs/research/synthesis.md「照片隐私」））
  - property_item_override（property_id、stable_id、suppressed）——「本物业不存在此项」永久抑制（zInspector `Ø` 先例：模板现场自愈，免每次 N_A；synthesis 建议 #4）；`setSuppressed` 可逆切换（T2-CAPTURE-CORE「抑制/恢复用例本卡提供」，其 allow_paths 不含 core/db/）
  - audio（inspection_item_id、rel_path、content_hash）
  - supplement（inspection_id、created_at、text、prev_hash **不可空**（链首锚 inspection.data_hash，不是
    "没有上一条"）、**chain_hash** 不可空（T3-FINALIZE 用 T1-CANON-HASH 的 supplementChainHash 算出、写入；
    没有这一列，链尾/单条记录的哈希无处核对）——finalize 哈希链，append-only
  - notice（inspection_id、full_text 全文快照、generated_at、**scheduled_at** 快照（T4-NOTICES dod_assert
    明文「存档记录含…预定巡检时间…」）、sent_via、sent_at（两者 CHECK 同生共死）、lead_hours、
    validation_snapshot）——`recordDelivery` 回记送达且一次性锁定（T4-NOTICES「回记送达后提前量重算并锁定」，其 allow_paths 不含 core/db/）
  - phrase_entry（en、zh、category、sort、**applies_to_statuses** 可空（如 wear 类只在 FAIR 时推荐）、
    **shortcut** 可空（如 "FWT" 快捷展开））——两个可空字段为 T2-PHRASELIB 所需；其 allow_paths 也不含
    core/db/，同 tenancy.purged_at 一样的理由由本卡建列
- 评级枚举（存 TEXT，合法值由模板类型定）：租赁 GOOD/FAIR/POOR/NOT_APPLICABLE；年检 5 态 NO_ISSUE/MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT/NOT_APPLICABLE（ADR-0003/讨论修正）。
- **UUIDv7 自研**（ADR-0003，2-1 决）：RFC 9562——48 位 epoch 毫秒 + ver 0111 + 12 位 rand_a + variant 10 + 62 位 rand_b；SecureRandom；42 位单调计数器保同毫秒单调，**耗尽时前推 1ms 换种子而非环绕**（环绕会把计数器绕回更小的值、产出的 UUID 反而变小，破坏单调性）；时钟回拨=沿用上一时间戳（冻结语义）。测试照 dod_assert 七项（含计数器耗尽回归、固定向量全 128 位断言）；固定向量的期望值用 Python（与 Kotlin
生产代码无关的独立语言/路径）算出后硬编码成字面量，不是同一套位布局公式在 Kotlin 里抄两遍再自证——那样两处
共享同一个理解错误（如字节序搞反）会一起测过。若评审判不可靠，swap `uuid-creator`(MIT) `getTimeOrderedEpoch()` 为一行级改动。
- 迁移：SQLDelight `.sqm` 显式编号；version 1（本卡）按 SQLDelight 官方约定零 .sqm 文件（"first schema version is 1"，`.sqm` 命名的是"迁移起点版本号"，v1 无前序版本可迁）。`verifyMigrations` 本卡**未开**——见「验收」说明，已登记 TD4。**合并后本目录冻结**——后续加表 = 新 .sqm + 版本评审，届时须先还清 TD4。
- finalize 只读强制在 SQL 层预埋，**update 与 insert 两侧都要守**（否则 FINALIZED 后仍能悄悄插入新
  inspection_item/room_instance/photo/audio，一样会破坏快照）：所有 UPDATE query 恒带 `finalized_at IS NULL`
  （或 join 到 DRAFT 巡检）；这四张挂在巡检下的表的 `insert` 改写成 `INSERT…SELECT…WHERE …`
  （`INSERT…VALUES` 语法本身带不了 WHERE），guard 用 **EXISTS** 而非「标量子查询 IS NULL」——父行若不存在，
  标量子查询返回 NULL、`NULL IS NULL` 为真会误判通过，EXISTS 要求父行真的存在；`inspection_item`/`photo`
  的 guard 还要多验一层归属（room_instance 真属于该 inspection、inspection_item 真属于该 room_instance），
  防止借用别的巡检/房间的 id 拼出跨链路数据。`supplement` 是唯一的 append-only 例外（本就设计成 finalize
  后仍可写）。每条守卫至少各一例「对 FINALIZED 行/缺失父行/错配父行操作计 0 行」。同一条 finalize 守卫
  也套在两条后补的写路径上：`inspection_item.updateWearOrDamageIfDraft`（T2-CAPTURE-CORE 差异判定后写入）
  与 `photo.softDelete`（T2-PHOTO-PIPELINE 去关联/孤儿清理链路前半步）。
  **`orphanedAssets` 按 `rel_path` 判活、只返回 `rel_path`**（改名 + 改判据，见「修订之十」）：清理任务删的是
  一个路径，而 schema 不保证「一路径一哈希」，故判定粒度必须与删除粒度一致。
  由此才有那条推论——FINALIZED 巡检的照片不能软删，其 `rel_path` 恒有活跃行、不会被判成孤儿，故无需另写
  "排除 FINALIZED"的特判。**注意这条推论此前是按 `(content_hash, rel_path)` 说的，那样是错的**：
  finalized 的若是 `(H2, P)`、被报孤儿的是 `(H1, P)`，删的仍是同一个物理文件。
- **软删除唯一性一律用部分唯一索引**（`CREATE UNIQUE INDEX … WHERE deleted_at IS NULL`），**不用表级
  `UNIQUE(业务键, deleted_at)`**：SQLite 的 `UNIQUE` 把 `NULL` 视为互不相等，`deleted_at` 恒为 `NULL` 的
  活跃行之间表级约束形同虚设、根本拦不住重复。
  涉及：template_version(type,version)、check_item_def(template_version_id,stable_id)、
  room_instance(inspection_id,room_key,instance_no)、inspection_item(inspection_id,room_instance_id,stable_id)、
  photo(room_instance_id,content_hash)、property_item_override(property_id,stable_id)。每条至少一例证明
  重复插入两行活跃记录会报约束错误。
- 每个逻辑外键列都要有可用的最左索引（哪怕不建物理 `REFERENCES`），包括
  `inspection.{property_id,template_version_id,previous_inspection_id,baseline_inspection_id}`、
  `inspection_item.room_instance_id`、`tenancy.baseline_inspection_id`。
- 照片哈希/EXIF 语义只建列不写逻辑（T2-PHOTO-PIPELINE 实现）。

### 用户已签认决策（2026-08-15 · TASK-BOARD「用户已定」#2/#3/#7/#8 · 冻结前必须落进本卡 schema）
- **既有租约没有 Ingoing，基线要能后指定**（用户实况：2 套以上物业、部分已在租 ⇒ Ingoing 永远补不回来）。落法：
  `tenancy.baseline_inspection_id`（可空，逻辑外键 → inspection.id）= 该 tenancy 的**权威基线指针**，默认在建 Ingoing 时写入，
  也允许把某次 Routine 指定为基线（app 存量租约的唯一出路）。**Exit 的对照方一律读这一列**，不得假设「必有 type=INGOING 的巡检」。
  与 `inspection.baseline_inspection_id`（那一列是**该次巡检当时**用的基线快照）双轨并存、语义不同：tenancy 上的是**当前指针**（可改），
  inspection 上的是**历史事实**（写死不改）。测试须含一例：tenancy 无 Ingoing、指定 Routine 为基线后，
  **真的建一个 EXIT 巡检**、把它的 `baseline_inspection_id` 从 tenancy 指针解析写入，断言解析到的是该 Routine
  （不能只测 tenancy 指针本身被设对了——那样测不出 Exit 侧是否仍在假设"必有 INGOING"）。
- **租客联系方式保留期 = 租约结束后 12 个月，到期置 NULL 不删行**。故 `tenant_name`/`contact` 必须**可空**，
  且清理是 `UPDATE ... SET tenant_name=NULL, contact=NULL`，**绝不 DELETE tenancy 行**——照片/报告/哈希是法定证据（Rentals Act s123A
  的 12 个月是**下限**），删行会切断 inspection → tenancy → property 的证据链。本卡建列（含 `purged_at`）+
  机械查询 `purgeContactInfo`（置空联系方式 + 打时间戳，不判断该不该清）；何时/是否清理的判断与确认 UI 归 T5-RETENTION。
  `purged_at` 复用 `:updated_at` 而非单独的可空参数——单独参数能传 NULL，造出「联系方式已清空但 purged_at
  仍是 NULL」这种自相矛盾、无法区分"从未清理"的状态；updated_at 非空，复用它令这条路径类型层面不可能传 NULL。
- **不做**：Condition/Cleanliness 全量双刻度（#7）、缺陷责任方/费用字段（#8）。别自作主张加这两组列——加了就得走版本评审才能删。
- **`notice.recordDelivery` 同样有 purgeContactInfo 那类"可空参数能骗过一次性锁"的坑**（R3 五轮评审指出，
  已改正）：`sent_via`/`sent_at` 两列本身可空，若守卫只看现有行的 `sent_at IS NULL`、不管调用方**传入的值**，
  一次误传 `NULL` 的调用会通过守卫、把行标成"已处理"却没真送达，之后想补录真值反被同一条守卫拦死。改法：
  `sent_at` 复用 `:updated_at`（同 purgeContactInfo 的手法，类型层面不可能传 NULL）；`sent_via` 额外加
  `WHERE :sent_via IS NOT NULL`，传 NULL 时整条 UPDATE 是 0 行、不落地、不锁行，之后仍可补录真值。

## 卡片修订 2026-08-15（编排者裁决 · R3 两轮同一争点 · model 层类型归属）
争点：R3 两轮要求本卡在 `core/model/` 定义 `InspectionSnapshot`/`SupplementSnapshot`；执行者以「本卡卡文没点名要它、
且 T1-CANON-HASH 是本卡 non_goal」两次拒绝。按「同一争点两轮不认可即人裁」，**编排者裁决：R3 对，本卡建这两个类型**。
依据不是卡文措辞，而是**两张卡的 allow_paths**：
- 本卡 allow_paths **含** `android/core/src/main/kotlin/nz/myinspection/core/model/`（且至今未被本卡任何产出使用——
  该路径当初就是为此分配的）；
- `T1-CANON-HASH` 的 allow_paths **只有** `core/canon/`（main+test），并在 forbid 明写「碰 db/template 包」，
  它**无权**在 `core/model/` 建类型；其卡文亦写明输入是「model 层不可变数据类（T1-SCHEMA-CORE 已定义）」。
⇒ 若本卡不建，CANON-HASH 开工即撞墙，只能再破例扩 allow_paths（正是 L206 要避免的）。
**形状不是猜**：字段 = `T1-CANON-HASH` 的**哈希域**（ADR-0003 明确排除 `updated_at` / 文件路径 / UI 态 / LLM 建议）。
按那份清单一一对应，**不多加一个字段**——多出来的字段进了哈希域就是错，没进就是死代码。
`core/model/` **不进** FrozenPaths（本卡只冻 `sqldelight/`），故类型日后可随 CANON-HASH 实测调整，不必一次到位到完美。

## 卡片修订 2026-08-15 之二（编排者裁决 · 下游卡要的 db 查询归本卡提供）
R3 第四轮指出四处缺失的机械查询。**裁决：补，但每条须能指到**要求它的那张卡的原文**——**
理由是结构性的：`core/db/` 归本卡，而要用这些查询的下游卡 allow_paths **不含** `core/db/`
（已核：`T2-PHOTO-PIPELINE` 只有 `core/media/` + `app/media/`，其卡文却明写「提供 `orphanedAssets()` 查询 + 清理用例」）。
本卡不提供 ⇒ 下游卡要么被卡死、要么被迫破例写进**已冻结**目录。四条：
`inspection_item.wear_or_damage` 更新 · `property_item_override` 取消抑制/恢复 · `notice` 记录 `sent_via`/`sent_at` ·
`photo` 软删 + `orphanedAssets()`。**无出处的不要加**——臆想的查询是死代码，且冻结后删不掉。

## 卡片修订 2026-08-15 之三（R3 第六轮 · 六项发现，四项实现两项按裁决驳回）
逐条按"能否指到具体要求它的卡文"核验（同上一条裁决的同一标准），而非照单全收：
- **实现（均已指到出处）**：① `supplement` 加 `chain_hash`（T3-FINALIZE 原文"prev_hash = 上一条 chain_hash"——
  没有这一列，"上一条 chain_hash"就无处可读；顺带把 `prev_hash` 改回 NOT NULL——T1-CANON-HASH 原文
  "prev_hash(1) = inspection.data_hash"，链首**有**值，不是没有上一条）；② `notice` 加 `scheduled_at` 快照列
  （T4-NOTICES dod_assert 原文明文列"存档记录含…预定巡检时间…"）；③ `notice` 加 CHECK 约束令
  `sent_via`/`sent_at` 必须同为 NULL 或同为非 NULL（insert 路径原本能绕过 recordDelivery 的一次性锁，直接
  插入不一致状态——结构性缺口，不需要额外卡文出处，是本卡自己该守的完整性）；④ `check_item_def`/
  `phrase_entry`/`supplement` 三处 `ORDER BY` 加 `id` 兜底排序键（sort/created_at 都不保证唯一，同值时
  SQLite 不保证顺序稳定——T1-CANON-HASH 的整个存在理由就是确定性序列化，这条不需要额外出处，属于
  本卡对自己已经声明的"确定性"承诺的基本兑现）；`photo` 加 `content_hash` 打头索引（`orphanedContentHashes`
  自身查询模式要求，不需要外部卡文佐证）。
- **驳回（评审给的理由是真实关切，但没有可指认的卡文出处，且第一条直接抵触编排者已下的裁决）**：
  ① `InspectionSnapshot` 加房间/检查项/照片的归属标识与 `privacy_flag`——**编排者已就这同一个类型明确裁决
  "字段 = T1-CANON-HASH 的哈希域…按那份清单一一对应，不多加一个字段"**，而 T1-CANON-HASH 那份哈希域清单
  原文就是 `items[]（stable_id/status/note/wear_or_damage）`/`photos[]（content_hash/source/exif_time_ms/
  room 级标记）`/`audios[]（content_hash）`，没有归属 id、没有 `privacy_flag`。评审的关切（重新分配证据能否
  在保持 data_hash 不变的情况下篡改报告）是否成立，取决于 T1-CANON-HASH 打算怎么序列化这些嵌套列表——那是
  它自己的设计决策，不该由本卡越权替它把哈希域改大；`core/model/` 已明确不冻结，正是留给 T1-CANON-HASH
  落地时按实测校正的余地。② `orphanedContentHashes` 返回 `rel_path`——T2-PHOTO-PIPELINE 原文只说"提供
  `orphanedAssets()` 查询 + 清理用例"，没有指定返回形状；而它自己的"去重"机制细节（"复用既有 photo 资产、
  只建新关联"是否意味着多行共享同一 `rel_path`）本卡尚未读到足够文字来确定，猜返回形状与猜 model 字段是
  同一种风险。已按 T2-PHOTO-PIPELINE 自己已生成的 API（`content_hash` → 各活跃行的 `rel_path`）留一条现成
  退路：它拿到孤儿 `content_hash` 后自己 `selectByRoomInstance`/新增窄查询即可取 `rel_path`，不必本卡代劳。

## 卡片修订 2026-08-15 之四（R3 第七轮 · 两项：一项证明上条驳回的退路是假的，一项是本卡自己的事实错误）
- **`orphanedContentHashes` 撤回上条驳回，改名 `orphanedAssets` 直接返回 `content_hash, rel_path`**：
  上条给的"退路"（拿到孤儿 content_hash 后自己 `selectByRoomInstance` 查 rel_path）经评审指出**根本不成立**——
  `selectByRoomInstance` 需要已知 `room_instance_id`（调用方从一个孤儿 content_hash 并不知道它曾挂在哪个
  room_instance 下），且该查询天生排除软删行，而孤儿行恰恰全部是软删的，两条理由任一条就足以让这条"退路"
  查不到任何东西。这不是"评审偏好 vs 卡文出处"的分歧，是**我自己提的替代方案有真实缺陷**，故直接采纳：
  返回值改 `DISTINCT content_hash, rel_path`（同一哈希曾落到不同 rel_path 时逐条列出，全部要能被清理）。
- **修正一处事实错误：SQLDelight 2.3.2 不支持"复用非空列参数让另一处可空列绑定也变非空"这个技巧**——
  `notice.recordDelivery` 与 `tenancy.purgeContactInfo` 先前都写了"复用 `:updated_at` 令 sent_at/purged_at
  在类型层面不可能传 NULL"，**实测生成代码证明这是错的**：SQLDelight 按参数名在整条语句里出现的**所有**
  绑定位置推断可空性，只要其中一次绑到可空列，生成的 Kotlin 参数类型就是 `Long?`，不会因为同一参数**也**
  绑过非空列而变成 `Long`。已改正：两条查询都改回各自独立的可空参数 + 运行期 WHERE 三/二重拒绝（`:sent_at
  IS NOT NULL`/`:purged_at IS NOT NULL`），如实反映"这是运行期守卫、不是编译期保证"，不再声称类型层面的
  保证。这是本卡自己没有"按实测核验、不凭记忆"这条纪律执行到位的一处，R5.5 复盘时入账 lessons。

## 卡片修订 2026-08-15 之五（R3 第八轮 · orphanedAssets 判活粒度错了，是上条改动自己引入的真 bug）
`orphanedAssets` 的 `NOT EXISTS` 只按 `content_hash` 判断"是否还有活跃引用"，但同一哈希允许落在多个不同
`rel_path`（上条改动自己承认的前提）——`(hash H, 路径 A)` 软删、`(hash H, 路径 B)` 仍活跃时，旧写法看到
"哈希 H 还有活跃行"就整条放过，路径 A 那份物理文件永远判不成孤儿、永久漏删。这不是卡文出处问题，是上条
修 rel_path 时没有把资产身份的定义（`content_hash` + `rel_path` 一对，不是单独 `content_hash`）贯彻到
`NOT EXISTS` 的匹配条件里——只改了 SELECT 的返回列，没改 WHERE 的判活逻辑，两处对"什么算同一份资产"的
理解不一致。改法：`NOT EXISTS` 子查询同时匹配 `content_hash` 和 `rel_path`。新增用例：同一哈希两个不同
路径、一个软删一个活跃，断言只有被软删的那个路径出现在孤儿列表里。

## 卡片修订 2026-08-16 之六（R3 第九轮 · 「引用后不可变」只做了一半，insert 侧全无守卫）
`CheckItemDef.sq` 的注释写「被任何巡检引用后不可变：本卡故意不提供 update 查询」，但 **不提供 update 拦不住
insert**。check_item_def 是 `template_version` 的**子行**：巡检引用某版本之后，仍可往该版本 insert 一条新
`stable_id`，于是 `version` 与 `content_hash` 都不变、那一版的**实际内容**却变了。这破的是 CLAUDE.md 关键
不变量里的「历史对齐只靠 ID + 模板版本」——多年后按「当时那一版模板」重渲报告会与原件不符。卡是 ★冻结点，
这个洞冻进去以后再改代价极高，故本轮修掉。

改法沿用 `InspectionItem.sq` / `Audio.sq` 已有的 `INSERT … SELECT … WHERE EXISTS` 形态，不新造写法：父版本须
存在且未软删，且该版本**尚未被任何巡检引用**。引用判定不过滤 `inspection.deleted_at`（软删巡检的报告仍须可
一致重渲）——但**本卡未提供 inspection 软删查询，该分支不可达也测不到**，卡文与代码注释都已如实标注它是
「为将来预留的默认」而非已证明的保证。

**三例成组**（缺一即可被假守卫蒙混）：父行缺失 → 0 行 · 已被引用 → 0 行 · **未被引用 → 1 行**。第三例排除
「恒返回 0 行的坏守卫」也能让前两例全绿的假通过（L165）。

**单句删除变异证明**（L165：守卫要配一枚能让它翻红的变异，且须带判据分类器）：摘掉 `AND NOT EXISTS (…)`
那一行后重跑 `DbReferentialIntegrityTest` —— `tests=9 failures=1 errors=0`，失败的**恰好**是「already
referenced → 0 行」那一例，消息为 `java.lang.AssertionError: … expected [0] but found [1]`（真断言失败，
非 SQL 语法坏、非运行时异常），其余 8 例含另两条 check_item_def 用例全绿（证明变异是外科式的）。变异后按
`git checkout --` 还原，SHA256 与变异前一致（L196）。

**关于 RED-first**：本卡原始 TDD 周期的 RED 证据在 sha `9b590c9`，本轮是**已绿代码上的 remediation**，
造不出新的 RED 相，故 ship 用 `-SkipRed` 并记账。上面那枚变异证明比 RED 相更强：它证明的是「新断言在守卫
缺席时确实红，且红在指定断言上」，而不只是「某次运行退出码非零」。

## 卡片修订 2026-08-16 之七（R3 第十轮 · 封闭域只写在注释里，没落成 CHECK）
七张表的列注释声明了封闭枚举（`property.kind` / `is_boarding_house`、`template_version.type`、
`inspection.type`、`check_item_def.photo_rule`、`inspection_item.wear_or_damage`、`photo.source` /
`privacy_flag`、`property_item_override.suppressed`），但生成的 API 收任意 String/Long——注释拦不住任何东西。
而 `inspection.status` **早就有** CHECK：同一份 schema 里两套标准。卡是 ★冻结点，事后补约束要走迁移，故落齐。

**这些列都载重，不是形式主义**：`is_boarding_house` 决定巡检时段上限（19:00 / 18:00），是**不可关闭的合规闸**
的输入；`inspection.type` 决定「4 周内不得重复 Routine，Ingoing/Exit 不计入」的分流，未知 type 从这条法律上限
的两侧同时溜走；`privacy_flag` 决定报告是否排除该照片，一个 `2` 就能让含租客物品的照片绕过所有 `= 1` 的排除
查询进入报告（NZ OPC 判例风险）；`photo_rule` / `suppressed` 的未知值分别被静默读成「无拍照要求」「未抑制」。

**两处刻意不加**（代码注释里同样写明）：`inspection_item.status`——合法评级随模板类型而变，校验归 `:core`，
卡文明确如此（评审者本人亦如此要求）；`allowed_statuses`——JSON 编码的集合，不是标量域。

**测试写法上的坑（差点假绿）**：`check_item_def` / `inspection_item` / `photo` 的 insert 现为
`INSERT…SELECT…WHERE EXISTS`，**守卫滤掉的行根本走不到 CHECK**——父行没备齐时插入只是 0 行、不抛异常，
`assertFailsWith` 会以「没抛＝约束不存在」的**相反理由**变红（或在别的写法下假绿）。故每例先备齐合法父行，
只把被测那一列换成非法值；两个可空域（`photo_rule` / `wear_or_damage`）各配一条 **NULL 正例**，
防约束把合法的空值一并挡掉。

**单句删除变异证明**（取风险最高的 `privacy_flag`）：摘掉 `CHECK (privacy_flag IN (0, 1))` 后重跑
`DbInvariantsTest` —— `tests=18 failures=1 errors=0`，失败的**恰好**是 `photo rejects an unknown source and a
non-boolean privacy_flag`，消息 `Expected an exception … but was completed successfully`（即「摘掉约束就放行」
这条契约本身），其余 17 例全绿。`git checkout --` 还原后 SHA256 与变异前一致（L196）。

DoD 全量：`tests` 合计 62（db 45 + model 10 + uuid 7），`failures=0 errors=0`。

## 卡片修订 2026-08-16 之八（R3 第十一轮 · 去重复用没有 DB 入口）+ 编排者人裁记录
**发现成立**：T2-PHOTO-PIPELINE 的去重链路（「哈希已存在就复用该资产、只建新关联」）在本卡的查询面上**没有入口**——
`selectByRoomInstance` 要求已知 room_instance_id（去重时恰恰还不知道），`orphanedAssets` 只回软删行。而该卡
`allow_paths` 不含 `core/db/`，本目录合并后又进 `FrozenPaths`，届时它连加一条查询都要走版本评审。
本卡「修订之二」早已裁决「下游卡要的 db 查询归本卡提供」，`dod_assert` 里也已有「四条下游查询」——这是第五条，同一口径。
补 `selectActiveAssetsByContentHash`（返回 `rel_path`，按 `rel_path` 升序）。

**排序键取 `rel_path` 而非 `created_at`**：DISTINCT 之后 rel_path 两两不等、顺序恒定；`created_at` 会撞值，
撞值时同一输入两次运行可能复用到**不同的物理文件**，下游 canonical 哈希要的确定性就没了。

**写测试时浮出来的真实去重模型（值得记进卡）**：`idx_photo_active` 的唯一性**有意收窄到单个 room_instance**
（见 Photo.sq 该索引注释：同一照片内容跨巡检合法出现——同一缺陷在 Ingoing 与 Exit 各拍一次、同一张参考图
导入两个巡检）。所以「一个哈希、多份物理路径」**只在跨房间成立**，而那正是本查询要服务的跨巡检复用场景。
测试遂用三个房间，贴住真实用法。存活粒度与 `orphanedAssets` 对齐：只要还有活跃关联指向某路径，它就仍可复用；
最后一条关联被软删的那一刻它退出复用池，而 `orphanedAssets` 必须在同一刻报告它——测试把这两条一起断言，
**两条查询互为对照，不各说各话**。

### 编排者人裁（R3 轮次闸首次触发）
本轮结束时 `.review/T1-SCHEMA-CORE.rounds` 计数为 **2**，达 `ReviewRoundCap`——下一次 ship 将不再唤起评审者、
直接 `[R3-ROUND-CAP]` 转人裁。人裁在此完成，走 rubric §5 的**路由 (2)「发现属实 → 修，然后 -ResetRounds 重跑」**：
上述发现在 allow_paths 内、不在 non_goals 内、且与本卡既有裁决同口径，故认下并已修。

**但十一轮的真正结论是这张卡太大了**（路由 (3) 的证据，只是现在不宜再拆）：「全量 schema、13 张表、还是 ★冻结点」
本就该按表族拆成 2–3 张。近三轮发现（引用后可变、封闭域无 CHECK、去重无入口）**条条属实且互不相关**——
这不是评审者挑刺，是一张卡里塞了太多可独立评审的单元。**后续冻结点卡按表族拆**，别再出现单卡十一轮。

## 卡片修订 2026-08-16 之九（R3 第十二轮 · 两条都是**同类第二处**，根因是没做类扫描）
两条发现——`notice.sent_via` 封闭域无 CHECK、`photo.softDelete` 收 NULL 撞 NOT NULL——**都不是新类**：
前者与「之七」刚修的九条同类，后者与本卡早已为 `tenancy.purgeContactInfo` 修过的 NULL 守卫同类（测试都还在）。
**根因是执行者按评审者点名的文件逐一改，没有自己先扫全类**，违反 L97。多打的这一轮账要记在这里。

**本轮改为先扫全类再动手，且扫描口径是「读生成的 Kotlin」而非推理**（Notice.sq recordDelivery 注释里
记着一次实测教训：曾以为参数在类型层面不可空，读生成代码才发现假设是错的）。两类的扫描结论：

- **封闭域**：全库只剩 `notice.sent_via` 一处。它上一轮漏网是因为**域定义不在列注释里、在需求方卡片里**
  （`T4-NOTICES.md`「记录送达方式（SMS/EMAIL/LETTER 枚举）」）——按列注释写的扫描天然看不见它。
  原有 CHECK 只保证 sent_via/sent_at 同生共死，从不约束值本身，而这个值落在**送达审计记录**里，
  正是 48h 提前量合规的举证材料。NULL 仍合法（已生成未发送）。
- **可空参数写坏 NOT NULL 列**：全库只有 `photo.softDelete` 一处真实例。其参数同时绑可空的 `deleted_at`
  与 NOT NULL 的 `updated_at`，故生成为 `Long?`；传 NULL 时 WHERE 全过、UPDATE 真执行、写 `updated_at`
  时抛**未受控异常**，而本族约定是「守卫不过＝0 行、可重试」。按 `purgeContactInfo` 同款守卫修正。
  **其余为何安全（记下来，免得下次重推）**：`setSuppressed` / `updateWearOrDamageIfDraft` 形参本就非空；
  `recordDelivery` / `purgeContactInfo` 已有 `IS NOT NULL` 守卫且有测试；`finalizeIfDraft` 传 NULL 撞 CHECK
  抛错是**卡里明文规定的行为**，不是缺陷。

两条修复各配 NULL 正例（未发送的通知、被拒后仍可重试的软删），防约束把合法空值一并挡掉。65 测试全绿。

## 卡片修订 2026-08-16 之十（R3 第十二轮 · orphanedAssets 判活粒度与删除粒度不一致 = 数据丢失）
`orphanedAssets` 按 `(content_hash, rel_path)` 判死活，但**清理任务删的是一个路径**。schema 里没有任何约束
保证「一个 rel_path 只对应一个 content_hash」——唯一索引管的是 `(room_instance_id, content_hash)`，不管路径。
于是软删的 `(H1, P)` 与活跃的 `(H2, P)` 并存时，`P` 被报成孤儿，**而它仍被活跃行引用**，照报告删下去即数据丢失。

**这同时推翻了本文件既有的一段论证**：原注释称「finalize 过的照片不能软删，故其 (hash, path) 组合恒有活跃行、
不会进孤儿列表」。在 H1/H2 形状下，finalized 的是 `(H2, P)`、被报孤儿的是 `(H1, P)`，删的是同一个物理文件——
巡检证据照样没。改为**按 `rel_path` 判活**后该论证重新成立，且这次是**因为判定粒度与删除粒度对齐**，
不是因为哈希碰巧不撞。返回值一并收敛为 `rel_path`（清理只需路径；哈希侧问题由 `selectActiveAssetsByContentHash`
回答，各司其职），三处调用点同步更新。

**单句变异证明**：把旧的 `p2.content_hash = p1.content_hash AND` 判活条件塞回去后重跑
`DbDownstreamQueriesTest` —— `tests=15 failures=1 errors=0`，失败的**恰好**是新增的
`orphanedAssets never reports a path that a finalized inspection still references under a different hash`，
消息为该用例自己的断言原文（非语法坏、非运行时异常）。`git checkout --` 还原后 SHA256 与变异前一致。
新回归刻意把后果顶到最严重：活跃行属于 **FINALIZED 巡检**且哈希不同。66 测试全绿。

## 卡片修订 2026-08-16 之十一（R3 第十三轮 · 上一轮改动的两处未竟收尾）
两条发现**都是「修订之十」自身的 fallout，不是新地盘**，故不计入「本卡该拆」的证据：
1. **索引没跟上访问模式**：判活从 `content_hash` 改成 `rel_path` 后，`idx_photo_content_hash`（其注释原文
   即写明「专为 orphanedAssets 的 content_hash 自关联而加」）不再服务这个查询，而没有 rel_path 打头的索引，
   SQLite 只能对**每一条软删行**扫一遍活跃行 = O(软删 × 活跃)，且是在冻结边界上。
   补部分索引 `idx_photo_active_rel_path ON photo (rel_path) WHERE deleted_at IS NULL`——谓词与子查询完全吻合；
   同时把 content_hash 索引的注释改指它**现在**的消费者 `selectActiveAssetsByContentHash`。
2. **注释与实现矛盾**：三处仍在描述旧契约，其中一处是执行者**有意保留的「历史」叙述**。
   **教训**：「当前契约的面要对齐、历史记录留原样」这条区分**对卡片成立、对源码注释不成立**——
   卡片有「带日期的修订小节」这一体裁，源码注释没有，读到它的人只会当成现状。整段删除，只留当前规则。

**扫描纪律的实测数据**（L97 的具体做法）：本轮改完后连扫三遍，**每遍都还有收获**（卡内 4 处 → Photo.sq 1 处 →
测试 2 处）。「一次性扫齐」在实践中等于「改完立刻扫、扫到空为止」，不是扫一遍就完。

66 测试全绿。

## 卡片修订 2026-08-16 之十二（R3 第十四轮 · 两条又都是「类」，执行者连续三轮只修点名处）
1. **守卫缺正例**：评审点名 audio，实扫发现四张守卫子表里**三张**只有拒绝用例
   （room_instance / inspection_item / audio；photo 是上一轮新助手顺带覆盖到的）。
   **一个恒返回 0 行的坏守卫能让全部拒绝断言通过**——正路径不钉住，整组拒绝证明不了任何东西（L165）。
   新增一例覆四张表：合法 DRAFT 链路各插一行，断言影响 1 行**且能读回来**（只断行数排除不了「写进去但落到别处」）。
2. **源码/测试里的流程史**：评审点名 5 个文件，实扫 **12 个文件 23 处**，且**已开始过期**——
   注释里写的评审轮号与卡里记的对不上，评审者正是拿这个当证据。
   **执行口径（非无脑删）**：读代码的人需要的理由**留**（「这是机械写路径，判断逻辑在消费方模块」——没有它，
   一条光秃秃的 UPDATE 看不出动机）；**卡片流程框架删**（「其 allow_paths 不含 core/db/」「R3 第 N 轮」
   「编排者裁决」）。历史理由归任务卡——卡有「带日期的修订小节」这一体裁，源码注释没有，读到即当现状。

### 执行者自评：连续三轮同一个失败
第十二、十三、十四轮的根因都是**「修评审点名的那一处，不扫同类」**（L97）。前两轮已把教训写进本卡，第三轮照犯。
本轮起改做法：**先 grep 统计规模、再动手**，而不是边改边想着"顺便扫一下"。本轮两次统计都证明点名处只是冰山一角
（1 张表 → 3 张；5 个文件 → 12 个）。这条比"记得扫类"可执行，已按此办。

67 测试全绿。

## 卡片修订 2026-08-16 之十三（R3 第十五轮 · 相等性测不出形状，而形状就是哈希域）
八个快照类型只有「逐字段参与相等性」的测试——**给任何一个加一个带默认值的字段，全部相等性断言照样绿，
而 T1-CANON-HASH 的哈希域已经悄悄变了**。最尖锐的是租客联系方式那条：它声称验证「联系方式不在快照里」，
实际只验证了「三参数构造成功」，而**多出一个带默认值的第四参数时三参数构造同样成功**——证明不了缺席。
该测试的注释自己写着「这里没有反射黑魔法能测某字段不存在」；有的，`declaredFields` 就是。

八个类型现各自钉死 `name:JavaType` 有序列表。**边界写明而非默认**：引用类型的可空性**不覆盖**
（`String?` 与 `String` 在 Java 侧同为 `String`），可空基本类型**覆盖**（装箱成 `Long`/`Boolean`，
与 `long`/`boolean` 可区分）——实测结论，不是遗漏。

两处排除项就地附理由（因为它们最可能被将来的改动无意撤销）：
租客联系方式必须留在外面，否则 Privacy Act 的保留期清理一执行，**历史报告的哈希将永远无法复验**；
`SupplementSnapshot` 必须只有 `createdAt`+`text`——`id` 是随机 UUID 与内容无关、`inspectionId` 在
「某次巡检下的链」里冗余、`prevHash` 是 `supplementChainHash(prev, s)` 的另一个入参而非本条内容。

**变异证明**：给 `AudioSnapshot` 加 `sneakyExtra: String = "default"` 后，
`every snapshot type has exactly the declared hash-domain shape` 变红，消息为
`expected [contentHash:String] but found [contentHash:String, sneakyExtra:String]`，
**而全部相等性用例保持绿**——这正是本轮要补的那个洞。还原后 SHA256 一致。68 测试全绿。

## 卡片修订 2026-08-16 之十四（R3 第十六轮 · items[] 无全序＝data_hash 不确定；**停止条件首次触发并奏效**）
**发现（真·schema 设计缺陷，非卫生问题）**：`check_item_def.sort` 不是全序键——同一 `stable_id` 可合法落在
多个 `room_instance` 上（活跃唯一索引 = `(inspection_id, room_instance_id, stable_id)`），两个不同 `stable_id`
也允许共用一个 `sort`。并列项顺序无定义 ⇒ 主卧 `wall.paint`=GOOD、次卧 `wall.paint`=POOR 两种序列都合法 ⇒
**同样的数据算出两个 data_hash**。而该哈希写进 PDF 页脚、作用是「自证未事后修改」——它不确定，自证即为空。

**编排者按既定规则停手、交用户裁决**（本卡此前记录的停止条件：「出现 schema 设计层面的新缺陷时停下来问」）。
两条修法都会改动冻结点契约，故不自行选择：
- **A**：不改快照形状，把排序契约收紧为全序 `(sort, room_instance_id, stable_id)` + 提供有序查询。
- **B**：给 `InspectionItemSnapshot` 加房间标识——改哈希域形状，牵动 T1-CANON-HASH 黄金向量。

**用户选 A**（2026-08-16）。已按 A 实现：新增 `inspection_item.selectByInspectionInTemplateOrder`；
排序键留在投影之外，与 `photos[]`/`audios[]` 用外部 UUID 定序同一做法，**快照形状不变**，「修订之十三」
钉下的形状断言继续有效。

**LEFT JOIN 而非 JOIN**（本轮自行加固，非评审要求）：内连接会把「`stable_id` 在该模板版本无定义」的条目
静默丢出结果集——**删掉一条 `check_item_def` 就能让某个条目从哈希里消失**，那是可利用的漏洞而非数据清洁。
外连接保留之（`sort` 为 NULL，SQLite ASC 排最前，位置仍确定），配独立回归。

**第二条发现是执行者上一轮自己的 bug**：形状断言依赖 `Class.getDeclaredFields()` 的返回顺序，而 JVM 规范
**不保证**该顺序——合规但重排字段的工具链会让它在源码未变时变红（假确定性）。改为比较排序后的集合；
字段顺序本也不该是契约（canonical JSON 由 T1-CANON-HASH 自行定序），要守的是「哪些字段在」。

**变异证明**：从 `ORDER BY` 摘掉 `ii.room_instance_id` 后，顺序由 `A/a, A/b, B/a, B/b` 变成
`A/a, B/a, A/b, B/b`——兜底键确实承重。70 测试全绿。

### 本轮两条操作教训（已进 lessons 候选）
1. **变异证明前必须先提交**：`git checkout -- <file>` 恢复到 HEAD，会把该文件里**未提交的新工作**一并抹掉，
   不只是变异。本轮即因此丢失整条新查询，靠 L196 的「还原后核 SHA256」当场发现（`restored=False`）。
2. **守卫返回 0 行而不抛异常 ⇒ 不看返回值的调用点静默失败**：夹具先建巡检、后定义模板项，被
   `check_item_def` 的引用后不可变守卫连拒三次，症状却在几十行外伪装成「排序实现有问题」。
   夹具的每一步前提都要断言影响行数，让前提坏时报「夹具坏了」。

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
  禁止提交任何 `*.db` 文件——两者在当前配置下不可兼容，不是本卡实现或配置错误。**已登记为 TD4**
  （`specs/tech-debt-tracker.md`，编排者核验后登记，覆盖两条互斥事实、实测复现步骤、两条候选修法——
  check-secrets 加按文件豁免 / 快照改存非 `.db` 后缀——与证明用的变异：改一处 `.sq` 却不配对写 `.sqm`，
  `:core:check` 必须转红）；影响面小——version 1 零 .sqm 本就无迁移可验，真正开始起作用是从第一次
  加表/改列（第一份 .sqm）起，届时需先还清 TD4 才能开工。
- **`core/model/` 已按上方两条编排者裁决落地**：`InspectionSnapshot`（+ 嵌套的 `PropertySnapshot`/
  `TenancySnapshot`/`TemplateSnapshot`/`InspectionItemSnapshot`/`PhotoSnapshot`/`AudioSnapshot`）与
  `SupplementSnapshot`，字段逐一对齐 T1-CANON-HASH 的哈希域清单，一个不多——排序（items 按模板序、
  photos/audios 按 UUID 序）留给调用方，本卡只定形状不做排序。四条下游查询（`inspection_item.
  updateWearOrDamageIfDraft`、`property_item_override.{setSuppressed,selectByPropertyAndStableId}`、
  `notice.recordDelivery`、`photo.{softDelete,orphanedAssets}`，另加第五条 `photo.selectActiveAssetsByContentHash`
  （去重复用，修订之八））已加到对应 .sq 文件，均带
  finalize 守卫（`property_item_override` 除外——它跨巡检生效，不属于任何单次巡检的只读快照）+
  自证测试。

## 执行建议（TASK-BOARD）
首选 DeepSeek V4 Pro · high；备选 Sonnet 5 max；**冻结前 Opus 5 抽审一遍 schema**（列/索引/软删唯一性），R3 仍 Sol。难度 H（地基卡，宁慢勿错）。
