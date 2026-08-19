# 技术债追踪器（持续重构，append-only）

> **动机**（OpenAI《Harness Engineering》核心实践之一）：把技术债当**持续的小额还款**，而非**周期性大修**。
> 每发现一处「能跑但偏离既定模式/契约」的地方，**立刻登记**——不要等它攒成大重构。
>
> **形态**：本表 append-only（只追加、改状态，不删行）。**TD 是登记/验收单元，不是 PR 大小单位**：每条债项进入
> `carded` 前必须先 decomposition，按依赖投影成 **1–N 张**各自可一次评审、一次验证的任务卡（`specs/tasks/<id>.md`）。
> 只有整项债本来就满足「一个可评审/可验证单元」时才允许 N=1；不得为了“一债一卡”把多个解析器、策略、平台、
> CI/文档面塞进一个 PR。偿还指针在同一格列 card set/依赖顺序；**全部子卡 merge 且 TD 总验收通过后**才置 `paid`。
> 另一条闭合路线是在 `docs/adr/` 记一条「有意接受此债」的决定。
> **热/冷分离（省上下文 · TD86）**：本活表只留 `open`/`carded` 的**在飞**债项；`paid`/`accepted` 的**已闭合**整行由
> `scripts/archive.ps1` 搬到 `specs/archive/tech-debt-archive.md`（append-only 语义在归档侧延续、轨迹不丢）+ 精简索引
> `specs/archive/tech-debt-index.md`（一行一条、可 grep）。查已还债项来龙去脉：先 grep 索引、再按 id 取归档整行；
> 闭合项堆积时在 R5 doc-sync 后手动跑一次压缩（幂等，`-DryRun` 可先预览）。
> **与经验系统的区别**：`docs/lessons/LEDGER.md` 记「**工具链/方法**的坑」（怎么干活）；本表记「**本代码库当前的具体偏离**」（哪里欠债）。
> **一行怎么写**：7 列固定不加列（三个消费者按列数解析），「偏离了什么」那格按**后果 / 修法 / 可测 / 前置**四段写，
> WHO 与根因归解决层（卡的 `diagnosis`）——细则与样板见 `specs/README.md`「技术债的一行怎么写」。

## 状态枚举
`open`（已登记待还，或 decomposition 尚未完成） · `carded`（已完成 decomposition，注明 1–N 张卡及依赖顺序） · `paid`（全部子卡已合并且 TD 总验收通过，注明各 PR/commit） · `accepted`（有意接受，注明 ADR）

## 债项
| id | 发现日 | 位置 | 偏离了什么（债） | 严重度 | 状态 | 偿还指针 |
|---|---|---|---|---|---|---|
| _示例_ | 2026-06-15 | `backend/app/...` | 直接拼路径，未经 `core/storage.py` 派生（违反关键不变量） | major | open | — |
| TD12 | 2026-08-16 | `core/backup/format/BackupManifest.kt`(BackupScope，冻结物) ↔ `T5-BACKUP-IO` | **按物业导出仍带整库 `db.sqlite`，且 manifest 不记每文件属主**：v1 简化（卡片上下文包明写），范围只由 `manifest.scope` 标记。后果：按物业包若被当作「只含该物业数据」对外交付，会连带其它 tenancy 的全部 DB 行（Privacy Act 2020 面）；且将来做「按物业恢复」时，读侧无法只凭 manifest 判断某文件属于哪个物业（须回读包内 DB 的 照片→巡检→物业 链） / 修法：做按物业恢复那张卡时二选一——① manifest 加 `owner_property_id`（**改冻结格式 = format_version 2 + 版本评审**）；② 恢复期回读包内 DB 推导归属（不动格式） / 可测：按物业包恢复后，别的物业的数据不出现在结果库里 / 前置：T5-BACKUP-IO | major | open | — |

| TD10 | 2026-08-16 | `core/finalize`（CompletenessPort）+ `core/capture`（InspectionRepository 事务面）跨卡共享 | **多连接 DB 契约未定**（R3 于 T3-FINALIZE 与 T2-CAPTURE-CORE 反复要求、经编排仲裁转债）：本 app 单用户单进程、生产单连接（SQLDelight AndroidSqliteDriver），事务原子性/端口 enlistment 在单连接下已由确定性测试钉住（fake-port 写后抛/拒 → 回滚判别）；**跨连接**（未来 WorkManager 备份并发读等）的 enlistment 证明与竞争语义无处安放——xerial sqlite-jdbc shared-cache 实测 `SQLITE_LOCKED_SHAREDCACHE`（L221）致确定性双连接测试不可行。修法方向：若未来引入第二连接，先在一处定义并发契约（连接归属/重试语义），配可行驱动的确定性测试；在此之前**任何评审不得再以「证明多连接性质」block 单连接卡**（仲裁记录见两卡 PR） | major | open | — |
| TD9 | 2026-08-16 | `scripts/selftest.ps1`（8.2e stub 夹具 + 分片汇总器） | **selftest 可诊断性 + post-merge 稳定性残债**（W2 编排会话实证）：①PR #6/#9 的 `selftest (ubuntu-latest, core)` FAIL 但全日志无一行点名失败闸——汇总器只打 `selftest: FAIL`；②与通过运行的日志 diff 显示约 45 项检查静默未执行、也无 skip 行；③同一 merge commit 重跑即全绿（run 31941736470），8.2e 并发 stub 在 CI 高负载下会 flake。`T0-DEBT-SELFTEST-CRITICAL-PATH` 已把完整矩阵移出 PR 关键路径，不再阻塞产品推进；剩余修法只针对合并后 canary：FAIL 点名闸 id、skip 可见、8.2e 负载容差。 | major | carded | `T0-DEBT-SELFTEST-CRITICAL-PATH` 已偿还 PR 关键路径耦合；残债拆为 `T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS`、`T0-DEBT-SELFTEST-SKIP-VISIBILITY`、`T0-DEBT-SELFTEST-LOAD-STABILITY`（无业务互依但共享 selftest，执行/合并宽度 1；三卡全 merged + post-merge core 重放后才可 paid） |


| TD8 | 2026-08-16 | `TemplateVersion.sq`(第 5 行注释，schema 冻结物) | **冻结 schema 注释把 content_hash 的来源指错**：注释写「canonical JSON 的 SHA-256（由 T1-CANON-HASH 算出并写入）」，而卡片 T1-TEMPLATE-ENGINE 与已合并实现都定的是**模板文件字节**的 SHA-256（`LoadedTemplate.parse` 对源字节算，与 canon 包无关）。两者不是同一个值：canonical 化会抹掉缩进/键序差异，而本列的用途恰恰是检出「同版本号、文件内容却变了」的静默漂移——按注释实现会让这项检出失效。后果：将来写校验/重算的人若按注释走，会得到与库中值永远不符的哈希，或反过来把漂移检出削弱成"语义相同即可" / 修法：下一次 schema 版本评审窗口把该注释改为「模板文件字节的 SHA-256（T1-TEMPLATE-ENGINE 写入）」（纯注释、无行为差异，仍走冻结物流程，可与 TD6 同窗口做） / 可测：注释语义与 `LoadedTemplate.parse` 的 KDoc 及 `TemplateLoaderTest` 的黄金向量一致（人审，无行为面） / 前置：任一 schema 版本评审窗口 | minor | open | —（源：并发会话在 T1-TEMPLATE-ENGINE round-3 分诊时发现，本卡 R5 登记） |
| TD7 | 2026-08-16 | `core/template/TemplateStore.read()` ↔ `CheckItemDef.sq` 的 `selectByTemplateVersion`（schema 冻结物） | **软删语义在模板读回路上前后不一致**：`read()` 刻意**不看** `template_version.deleted_at`（依据 CheckItemDef.sq 自己写的原则「软删的巡检其报告仍须可一致重渲」），但它取项定义用的冻结查询 `selectByTemplateVersion` 带 `deleted_at IS NULL` 过滤。于是一旦将来给 `check_item_def` 加了软删路径，同一次 `read()` 会返回「版本行还在、条目少了几条」的模板——报告重渲会**静默缺项**，而 content_hash 仍是当初那份完整文件的哈希，对不上却无人报错。**当前不可达**：两张表都还没有任何软删查询（`update`/`delete` 查询根本没提供），故这是为将来预留的不一致，不是已发生的缺陷 / 修法：给模板读回路补一条不过滤软删的查询（须走 schema 版本评审，与 `T2-ROOM-REPEATABLE` 的评审窗口合并做最省），或在 `read()` 侧显式对齐语义并让不一致当场抛错 / 可测：给 check_item_def 造一条软删行，`read()` 要么返回完整模板、要么明确失败，不得静默少项 / 前置：任一 schema 版本评审窗口（软删查询落地前不构成实际风险） | minor | open | —（源：T1-TEMPLATE-ENGINE R5 技术债扫描） |
| TD6 | 2026-08-16 | `Supplement.sq`(第 8 行注释，schema 冻结物) | **冻结 schema 注释把链哈希域指错方向**：注释写 `chain_hash = SHA-256(canonical(本行) + prev_hash)`，而其点名的权威实现 supplementChainHash 只把 {created_at, text} canonical 化——「本行」会引导未来 verifyChain 作者把整行（id/inspection_id/prev_hash…）算进去，得到永远 mismatch 的哈希。后果：复验实现若按注释写会全量对不上；文件已冻结不能顺手改 / 修法：下一次 schema 版本评审窗口把「本行」改为「该 supplement 的 {created_at, text} 快照」（纯注释、无行为差异，仍走冻结物流程） / 可测：注释语义与 supplementChainHash KDoc 一致（人审，无行为面） / 前置：任一 schema 版本评审窗口 | minor | open | —（源：T1-CANON-HASH 步骤 4.6 自检 finding#7） |

| TD4 | 2026-08-15 | `scripts/check-secrets.ps1`(L57 模式 `\.db$` · L175 glob `*.db`) ↔ SQLDelight `verifyMigrations` | **防泄露闸与迁移校验闸结构性互斥，导致 T1-SCHEMA-CORE 关掉了 `verifyMigrations`**：SQLDelight 的 `verifyMigrations` 需要把 `<version>.db` schema 快照**入库**才能比对，而 check-secrets 按**文件名模式**无条件致命拦截任何被追踪的 `*.db`，且**无 per-file 豁免机制**（实测：生成 1.db → `git add -f`（.gitignore 自己建议的逃生门）→ check-secrets 仍判定为「被追踪的敏感文件」失败）。编排者已核：模式确在 L57/L175，冲突为真。**当前不构成实际损失**——schema version 1 按 SQLDelight 官方约定零 `.sqm`（v1 无前序版本可迁），此刻校验本就是空转；但**到 v2 就是真缺口**：加表/改列的迁移将无机检守卫。后果：schema 演进（本项目的高危面，且该目录合并后即冻结）缺少「迁移后 schema == 声明 schema」的确定性闸 / 修法：给 check-secrets 加**显式豁免清单**（如 `.secretsallow` 或配置项，豁免须逐条写明理由，不得整类放行），或把快照存成非 `*.db` 扩展名/存到库外并在 CI 生成后比对；二选一后重新打开 `verifyMigrations` 并挂回 `:core:check` / 可测：故意改一处 `.sq` 而不写对应 `.sqm`，`:core:check` 须变红；check-secrets 对**真**数据库文件仍须致命 / 前置：T1-SCHEMA-CORE 合并（届时才有 v2 迁移场景） | major | carded | `specs/tasks/T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST.md` |


| TD2 | 2026-08-15 | `scripts/check-licenses.ps1`(其它生态清单探针 · line ~117) | **Gradle 生态零覆盖却报 PASS（假绿）**：探针只按固定文件名找 `build.gradle` / `build.gradle.kts` 等，够不着本项目实际布局的**嵌套**清单（`android/app/build.gradle.kts`、`android/core/build.gradle.kts`、`android/gradle/libs.versions.toml`），于是「未发现其它生态依赖清单」→ 连覆盖缺口告警都不发，整个 Android 依赖图从未被许可闸看过一眼。T0-TOOLCHAIN 的 R3（GPT-5.6 Sol）实证：`kotlin-test-junit` 传递拉 `junit:junit:4.13.2`(EPL-1.0，禁列) 一路穿过许可闸，靠人肉评审才拦下。后果：许可闸对本项目**主实现面**恒假绿，下一个引入 copyleft 传递依赖的卡不会被机检拦住 / 修法：探针改递归发现（`Get-ChildItem -Recurse -Include build.gradle,build.gradle.kts,libs.versions.toml`，排除 .gradle 缓存与 build 输出），并把 Gradle 生态从「无扫描器→告警」升级为真扫描（`gradlew :core:dependencies` 解析坐标 + 许可查表），最低限度也要让它命中后进 `$coverageGap` / 可测：在本仓跑 `check-licenses.ps1` 须输出「检测到 Gradle 依赖清单」而非「未发现其它生态依赖清单」；植入一枚 EPL 传递依赖的变异须让 `-Strict` 非零退出 / 前置：T0-TOOLCHAIN 合并（`android/` 存在才有清单可扫） | major | carded | `T0-LICENSE-SCANNER`（PR #25 · master `035df10` · merged）→ `T0-LICENSE-POLICY`（PR #26 · master `04f80fd` · merged）→ `T0-LICENSE-DIAGNOSTICS`（PR #27 · master `e3b52c7` · merged；18 个 diagnostics mutation，经 round-cap 人裁）→ `T0-LICENSE-GAV-BOUNDS`（PR #28 · master `f61f586` · merged；TD135 paid，5 个 boundary mutation）→ `T0-LICENSE-CI-INTEGRATION`（ready；TD2 5/5；五卡串行；分别冻结图/GAV、POM/分类、finding/CLI、GAV 边界与 CI/文档合同；本卡 merge 且总证据齐全后 TD2 才可 paid） |

| TD1 | 2026-08-14 | `scripts/selftest.ps1`(闸15n · 闸17aa(8)) | **对上游脚手架 bug 的本地补丁 ×2，待回搬**：①15n 把 `TEMPLATE-README.md` 硬列为 L86-WT 权威面，但该文件属元仓专属物（`-Cleanup`/`-Retrofit` 不下发）——已初始化下游必红；②17aa(8) 夹具硬依赖元仓真卡 `specs/tasks/T11-R3-BASELINE.md`（活位或 archive），下游缺卡时 Get-Content 直接终止整跑（且它藏在 `if (-not $fail)` 后，前面全绿才暴露）。二者同为上游 TD74「无 CI 支路测生成下游」的实例。后果：本仓 selftest.ps1 与上游漂移，backfill 对照须带上这两块 diff / 修法：把 `$isPostInit` 跳过（15n）与缺卡跳过（17aa(8)）两补丁回搬上游元仓 / 可测：下游（无 TEMPLATE-README、无 T11 卡）selftest 全绿、元仓两面仍必查 / 前置：无 | minor | open | — |

| TD14 | 2026-08-17 | `core/media/`(落盘↔入库) + `app/media/`(清理调度) | **跨 FS+DB 的真原子性未做**：落盘在前、入库在后，入库失败靠补偿撤销刚写的字节。两条具体丢数据路径已在 T2-PHOTO-PIPELINE 内各自封死（补偿绝不删仍被活跃行引用的路径——同 photoId 重试时赢家那行正引用它，判据为已冻结的 `selectActiveAssetsByContentHash`；复用路径本次不写字节故永不补偿），但**共享临界区**这个架构级解法未做。后果：极端交错下仍可能出现「字节在盘、行不在库」的孤儿（由 `OrphanedAssetCleanup` 兜底，而它至今**没有生产调度器**）/ 修法：要么给两侧共享临界区（须动**已冻结**的 `sqldelight/`，走版本评审），要么把 `OrphanedAssetCleanup` 接上 WorkManager 定期跑（`app/` 调度接线不在本卡 allow_paths 内）。**注意 TD10 仲裁：不得再以多连接场景 block 单连接卡** / 可测：接线后须有「入库失败→字节被撤销 或 被清理任务回收」的端到端用例 / 前置：无（但动 sqldelight 须先还 TD4） | major | open | — |

| TD15 | 2026-08-17 | `core/media/`(JPEG 编码内存预算) | **编码字节上界是「有依据的余量」而非可证明上界**：预算余量已从 2 B/px 提到 4 B/px 以覆盖 `ByteArrayOutputStream` 底层数组 + `toByteArray()` 复制，注释也已如实改口（不再自称 upper bound）。后果：病理输入（超高熵图像）理论上仍可能超出余量而 OOM，`RejectedTooLarge` 可重试语义能兜住一部分但非证明 / 修法：**不在内存攒整份 JPEG**——边编码边写盘、边摘要，上界随之消失；属两条管线的字节流向重构，跨 `core/media` 与 `app/media` 两侧 / 可测：重构后以合成高熵图跑，堆占用不随图像尺寸线性攒整份字节 / 前置：无 | minor | carded | `specs/tasks/T2-PHOTO-STREAMING-ENCODE.md` |





| TD24 | 2026-08-17 | `Photo.sq` `selectActiveAssetsByContentHash` → `PhotoIngest.plan()` ↔ `BackupSourceFile.ownerPropertyId` | **照片按内容哈希全局复用，却要求备份源为单一物业 owner**：查询不按物业过滤，`PhotoIngest` 也只校验路径形状；现有 recorder 测试已允许 B 物业照片复用 A 物业的同一 `rel_path`。但 `BackupSourceFile.ownerPropertyId` 是标量：该物理文件标 A 会从 B 的物业包排除，标 B 会从 A 排除，重复列出同一路径又被拒，因而无法忠实表达两个物业的资产闭包。后果：未来 `T5-BACKUP-IO` 可能漏导一方照片或伪造 owner 语义 / 修法：专属媒体债卡把物理去重限制为同一物业；跨物业同 hash 走现有 `WriteNewAsset`，保留同物业复用；先只读检测历史共享路径并明确兼容决策，不改冻结备份格式 / 可测：A 路径作为 B 候选必须写入 B 新路径；同物业仍复用；recorder 后不同物业不得共享 `rel_path`；删除物业成员过滤时测试翻红 / 前置：已合并 `T2-PHOTO-PIPELINE`；必须在 `T5-BACKUP-IO` 前独立偿还，不得与 TD12/TD14/TD15 合卡；全新 worktree | major | carded | `specs/tasks/T2-PHOTO-PROPERTY-DEDUPE.md` |


| TD26 | 2026-08-17 | `T2-ROOM-REPEATABLE` ↔ `InspectionRepository` / `CompletenessPort` / history | **重复房间 schema 卡明确排除了运行时状态机，却没有后续卡接住实例维度**：当前 capture 只为每个 `room_key` 建 `instance_no=1`，baseline 写入又只按 `stable_id` 取首项；schema 已允许同一巡检的 BEDROOM #1/#2 各有同一 `stable_id`，相关查询无顺序保证。后果：重复房间落地后，Exit 的 #2 可能误拿 Ingoing #1 作基线，分类随未定义行序变化；progress/finalize 也无法按声明数量验证缺失实例 / 修法：在专属运行时交接卡中定义并持久化或派生物业的重复房间数量，所有基线匹配改用 `(room_key, instance_no, stable_id)`，走查按模板房间序再按 `instance_no`，finalize 验证所需实例数，并同步后续 history/Exit 卡的旧前提 / 可测：B1/B2 同 stable_id 不同状态时 Exit B2 必与 B2 比；交换 baseline 插入顺序结果不变；progress 顺序稳定；声明两间却缺 B2 时 finalize 拒绝 / 前置：先还 TD4，再完成 `T2-ROOM-REPEATABLE` 的 schema/版本评审；不得与该 schema 卡合卡，必须全新 worktree，并在 capture UI/history/Exit 内容上线前偿还 | major | carded | `specs/tasks/T2-REPEATABLE-ROOM-RUNTIME.md` |



















| TD129 | 2026-08-18 | Field Ledger capture/history UI 发布前验收 | **现有卡只要求功能冒烟，没有一张卡统一证明现场可用性**：日光、单手拇指、TalkBack、200% 字号、减少动态效果、保存失败反馈与相机取证若各自零散验收，功能可编译却仍可能在真实巡检中不可读、不可达或丢失上下文。后果：关键无障碍/现场缺陷到发布后才暴露，且没有证据与偿还指针 / 修法：T2 capture 与 T3 history 合并后跑专属真机验收矩阵，验收卡只收集证据，发现的 P0/P1 各开独立 TD，不在验收卡顺手改 UI / 可测：报告八个固定 evidence 段均有设备、步骤、截图/录屏、结果；无待验证标记；所有 P0/P1 finding 都有债项指针 / 前置：T2-CAPTURE-UI 与 T3-HISTORY-COMPARE | major | carded | `specs/tasks/T3-FIELD-UX-ACCEPTANCE.md` |

| TD130 | 2026-08-19 | `T3-PDF-RENDERER` 固定图片采样参数 | **PDF 没有面向用途的导出质量契约**：原卡只计划内联约 150dpi、附录约 200dpi、长边 2048px。后果：几十张照片的分享报告可能远大于需要，而用户无法用低体积档换取可接受清晰度；反向也没有铭牌小字所需的高档 / 修法：在同一渲染卡增加 Low/Medium/High/Extra High 纯 core 参数与每次导出选择，设置固定夹具的相对大小和可读性验收，不承诺任意内容的绝对 MB / 可测：四档参数、默认 Medium、固定 80 照夹具总体大小单调、High 铭牌可读、报告内容和 data_hash 不随档位改变 / 前置：T3-REPORT-COMPOSER | major | carded | `specs/tasks/T3-PDF-RENDERER.md` |
| TD131 | 2026-08-19 | `app/media/PhotoJpegEncoder.kt` 固定 q92 且无尺寸档 | **所有新照片固定以 q92 编码且没有长边上限/用户设置**。后果：多物业、多年巡检的本机照片增长不可控，Extra High 输入还会放大 TD15 内存峰值；用户也无法区分日常记录与小字证据需求 / 修法：先以流式编码偿还 TD15，再引入 Low/Medium/High/Extra High 的长边与质量提示，默认 Medium，只作用于未来捕获/导入 / 可测：两条 ingest 管线共同消费同一档位；不放大小图；切换不改历史 hash；固定全景、小字、低光、高熵夹具验证像素帽、可读性和总体大小单调 / 前置：T2-PHOTO-STREAMING-ENCODE | major | carded | `specs/tasks/T2-PHOTO-QUALITY-PROFILES.md` |
| TD132 | 2026-08-19 | T5 备份成功状态 ↔ 本机照片删除资格 | **现有计划只有目的地和上次成功时间，没有“某个照片 exact bytes 已在某包回读验证”的持久证明**。后果：若仅凭 Worker 成功、SAF URI 或云盘品牌清理本机字节，授权撤销、短写、缺文件或错误 scope 会把唯一证据删除 / 修法：新增不改 finalized photo 行的本机物理状态、PDF 回执、verified backup receipt 及逐资产 rel_path/hash/size 条目；实际回读由 T5-BACKUP-IO 消费 / 可测：缺字段、哈希/大小不符、回执撤销、跨物业、未来时间均不可产生归档资格，schema 升级/读回全绿 / 前置：TD4、TD24、T5-BACKUP-FORMAT | critical | carded | `specs/tasks/T5-MEDIA-ARCHIVE-CONTRACT.md` |
| TD133 | 2026-08-19 | app 私有照片目录长期增长 | **没有按物业巡检代数限制本机全尺寸照片，也没有安全预览、确认和回填路径**。后果：数年后占用可达 GB；若直接删除又会破坏 previous/baseline、历史查看及证据恢复 / 修法：以每物业最近 N 次已完成巡检控制本机字节，选项 1/3/5/10/Always、默认 3；仅在 finalize、PDF、30 天宽限、非保护引用且 exact verified receipt 全满足时列候选，默认人工确认，并从加密包按 hash/size 回填 / 可测：所有保护条件逐一做单点变异；预览释放量准确；中断不伪报；回填失败不改状态，成功原子置 PRESENT / 前置：T5-MEDIA-ARCHIVE-CONTRACT、T5-BACKUP-IO、T3-PDF-RENDERER、T3-HISTORY-COMPARE | critical | carded | `specs/tasks/T5-LOCAL-MEDIA-RETENTION.md` |
| TD134 | 2026-08-19 | devops-scaffold v0.29.0 下游 ↔ 上游 v0.38.0 | **本仓已局部吸收上游变化但缺少选择性回填闭环**：scaffold-selftest 已退出 PR 关键路径，task 却仍把 ci.yml 明写成信息性并在 R3 后直接 merge；lessons 已有冷库 mover 但 search/check 不跨热冷；常驻文本减负缺量化协议；机器测试仍大量锚本地化 prose。后果：free/private 仓可能在 CI 未完成时合并，lesson 常驻上下文持续膨胀，状态断言受编码/措辞变化影响；整包覆盖又会错误删除本仓 mandatory R3、RED/waterline 和 reviewer hardening / 修法：按六张独立卡选择性回填，真实产物链为 `T0-CI-MERGE-GATE → T0-ASCII-SHIP-CODES → T0-ASCII-CARD-SECRET-CODES → T0-ASCII-REVIEW-ARCHIVE-CODES`，另有独立 `T0-HARNESS-SUBTRACTION-PROTOCOL` 与 `T0-LESSONS-COLD-RECALL`；后者和主链共享 selftest 时只作资源冲突串行，不伪造业务依赖 / 可测：六卡各自 DoD；全部 merged 后跑 full selftest + lessons check + 一张真实小卡 ship 回放，确认 CI/head 绑定、cold recall、code roster 和本仓保留差异 / 前置：CI 卡须先等 `T0-R3-DIFF-BUDGET` 合并；不得直接把 ScaffoldVersion 改成 0.38.0 | major | carded | `T0-CI-MERGE-GATE` → `T0-ASCII-SHIP-CODES` → `T0-ASCII-CARD-SECRET-CODES` → `T0-ASCII-REVIEW-ARCHIVE-CODES`；并行独立卡 `T0-HARNESS-SUBTRACTION-PROTOCOL`、`T0-LESSONS-COLD-RECALL`；全六卡 merge + 总验收后才可 paid |


<!-- 新债项追加到上表。偿还时改 status + 填指针；勿删行（保留还债轨迹）。 -->

## 可选：背景重构 agent（OpenAI 持续重构循环）
> OpenAI 用后台 agent 定期扫描偏离、自动提重构 PR、小修快速合并。本仓不内置该自动化（避免无人值守写操作），
> 但可手动等价：每若干张卡后跑一次「对照 `CLAUDE.md` 关键不变量 + `docs/QUALITY-RUBRIC.md` §2 扫描偏离」，命中即在此登记 → 开卡。
