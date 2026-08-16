# 技术债追踪器（持续重构，append-only）

> **动机**（OpenAI《Harness Engineering》核心实践之一）：把技术债当**持续的小额还款**，而非**周期性大修**。
> 每发现一处「能跑但偏离既定模式/契约」的地方，**立刻登记**——不要等它攒成大重构。
>
> **形态**：本表 append-only（只追加、改状态，不删行）。每条债项最终应转成一张任务卡（`specs/tasks/<id>.md`）偿还，
> 或在 `docs/adr/` 记一条「有意接受此债」的决定。
> **热/冷分离（省上下文 · TD86）**：本活表只留 `open`/`carded` 的**在飞**债项；`paid`/`accepted` 的**已闭合**整行由
> `scripts/archive.ps1` 搬到 `specs/archive/tech-debt-archive.md`（append-only 语义在归档侧延续、轨迹不丢）+ 精简索引
> `specs/archive/tech-debt-index.md`（一行一条、可 grep）。查已还债项来龙去脉：先 grep 索引、再按 id 取归档整行；
> 闭合项堆积时在 R5 doc-sync 后手动跑一次压缩（幂等，`-DryRun` 可先预览）。
> **与经验系统的区别**：`docs/lessons/LEDGER.md` 记「**工具链/方法**的坑」（怎么干活）；本表记「**本代码库当前的具体偏离**」（哪里欠债）。
> **一行怎么写**：7 列固定不加列（三个消费者按列数解析），「偏离了什么」那格按**后果 / 修法 / 可测 / 前置**四段写，
> WHO 与根因归解决层（卡的 `diagnosis`）——细则与样板见 `specs/README.md`「技术债的一行怎么写」。

## 状态枚举
`open`（已登记待还） · `carded`（已开卡偿还，注明卡 id） · `paid`（已还清，注明 PR/commit） · `accepted`（有意接受，注明 ADR）

## 债项
| id | 发现日 | 位置 | 偏离了什么（债） | 严重度 | 状态 | 偿还指针 |
|---|---|---|---|---|---|---|
| _示例_ | 2026-06-15 | `backend/app/...` | 直接拼路径，未经 `core/storage.py` 派生（违反关键不变量） | major | open | — |

| TD5 | 2026-08-16 | `core/canon`(canonicalJson 数组序前置) ↔ `inspection_item.selectByInspectionInTemplateOrder` | **canonical 数组序契约在 canon 层不可验证也不可重建**：ADR-0003/卡文规定 items 按模板全序、photos/audios 按 UUID 序，但排序键与 UUID 都不进快照（round-16 用户已决=选项 A：保形状、排序归查询层），canonicalJson 只按调用方给定顺序哈希——「投影必须走 selectByInspectionInTemplateOrder」目前只存在于注释里，无跨层机检。后果：T3-FINALIZE 之外的未来装配路径（备份复验等）若用不同顺序装同一份数据会得到第二个 data_hash，round-16 修掉的缺陷类在装配层复发；canon 合并即冻结，不能事后补排序 / 修法：T3-FINALIZE 落地时加跨层黄金测试（DB 夹具 → 正门查询 → 投影 → data_hash 钉黄金值 + 乱序装配对照）并把「快照装配唯一正门」写进该卡 DoD / 可测：同一夹具乱序装配产出不同 canonical 串、正门装配 data_hash 与黄金值相等 / 前置：T3-FINALIZE 开工 | major | open | —（源：T1-CANON-HASH 步骤 4.6 自检 finding#2，按 rubric 立场转 FOLLOW-UP） |

| TD8 | 2026-08-16 | `TemplateVersion.sq`(第 5 行注释，schema 冻结物) | **冻结 schema 注释把 content_hash 的来源指错**：注释写「canonical JSON 的 SHA-256（由 T1-CANON-HASH 算出并写入）」，而卡片 T1-TEMPLATE-ENGINE 与已合并实现都定的是**模板文件字节**的 SHA-256（`LoadedTemplate.parse` 对源字节算，与 canon 包无关）。两者不是同一个值：canonical 化会抹掉缩进/键序差异，而本列的用途恰恰是检出「同版本号、文件内容却变了」的静默漂移——按注释实现会让这项检出失效。后果：将来写校验/重算的人若按注释走，会得到与库中值永远不符的哈希，或反过来把漂移检出削弱成"语义相同即可" / 修法：下一次 schema 版本评审窗口把该注释改为「模板文件字节的 SHA-256（T1-TEMPLATE-ENGINE 写入）」（纯注释、无行为差异，仍走冻结物流程，可与 TD6 同窗口做） / 可测：注释语义与 `LoadedTemplate.parse` 的 KDoc 及 `TemplateLoaderTest` 的黄金向量一致（人审，无行为面） / 前置：任一 schema 版本评审窗口 | minor | open | —（源：并发会话在 T1-TEMPLATE-ENGINE round-3 分诊时发现，本卡 R5 登记） |
| TD7 | 2026-08-16 | `core/template/TemplateStore.read()` ↔ `CheckItemDef.sq` 的 `selectByTemplateVersion`（schema 冻结物） | **软删语义在模板读回路上前后不一致**：`read()` 刻意**不看** `template_version.deleted_at`（依据 CheckItemDef.sq 自己写的原则「软删的巡检其报告仍须可一致重渲」），但它取项定义用的冻结查询 `selectByTemplateVersion` 带 `deleted_at IS NULL` 过滤。于是一旦将来给 `check_item_def` 加了软删路径，同一次 `read()` 会返回「版本行还在、条目少了几条」的模板——报告重渲会**静默缺项**，而 content_hash 仍是当初那份完整文件的哈希，对不上却无人报错。**当前不可达**：两张表都还没有任何软删查询（`update`/`delete` 查询根本没提供），故这是为将来预留的不一致，不是已发生的缺陷 / 修法：给模板读回路补一条不过滤软删的查询（须走 schema 版本评审，与 `T2-ROOM-REPEATABLE` 的评审窗口合并做最省），或在 `read()` 侧显式对齐语义并让不一致当场抛错 / 可测：给 check_item_def 造一条软删行，`read()` 要么返回完整模板、要么明确失败，不得静默少项 / 前置：任一 schema 版本评审窗口（软删查询落地前不构成实际风险） | minor | open | —（源：T1-TEMPLATE-ENGINE R5 技术债扫描） |
| TD6 | 2026-08-16 | `Supplement.sq`(第 8 行注释，schema 冻结物) | **冻结 schema 注释把链哈希域指错方向**：注释写 `chain_hash = SHA-256(canonical(本行) + prev_hash)`，而其点名的权威实现 supplementChainHash 只把 {created_at, text} canonical 化——「本行」会引导未来 verifyChain 作者把整行（id/inspection_id/prev_hash…）算进去，得到永远 mismatch 的哈希。后果：复验实现若按注释写会全量对不上；文件已冻结不能顺手改 / 修法：下一次 schema 版本评审窗口把「本行」改为「该 supplement 的 {created_at, text} 快照」（纯注释、无行为差异，仍走冻结物流程） / 可测：注释语义与 supplementChainHash KDoc 一致（人审，无行为面） / 前置：任一 schema 版本评审窗口 | minor | open | —（源：T1-CANON-HASH 步骤 4.6 自检 finding#7） |

| TD4 | 2026-08-15 | `scripts/check-secrets.ps1`(L57 模式 `\.db$` · L175 glob `*.db`) ↔ SQLDelight `verifyMigrations` | **防泄露闸与迁移校验闸结构性互斥，导致 T1-SCHEMA-CORE 关掉了 `verifyMigrations`**：SQLDelight 的 `verifyMigrations` 需要把 `<version>.db` schema 快照**入库**才能比对，而 check-secrets 按**文件名模式**无条件致命拦截任何被追踪的 `*.db`，且**无 per-file 豁免机制**（实测：生成 1.db → `git add -f`（.gitignore 自己建议的逃生门）→ check-secrets 仍判定为「被追踪的敏感文件」失败）。编排者已核：模式确在 L57/L175，冲突为真。**当前不构成实际损失**——schema version 1 按 SQLDelight 官方约定零 `.sqm`（v1 无前序版本可迁），此刻校验本就是空转；但**到 v2 就是真缺口**：加表/改列的迁移将无机检守卫。后果：schema 演进（本项目的高危面，且该目录合并后即冻结）缺少「迁移后 schema == 声明 schema」的确定性闸 / 修法：给 check-secrets 加**显式豁免清单**（如 `.secretsallow` 或配置项，豁免须逐条写明理由，不得整类放行），或把快照存成非 `*.db` 扩展名/存到库外并在 CI 生成后比对；二选一后重新打开 `verifyMigrations` 并挂回 `:core:check` / 可测：故意改一处 `.sq` 而不写对应 `.sqm`，`:core:check` 须变红；check-secrets 对**真**数据库文件仍须致命 / 前置：T1-SCHEMA-CORE 合并（届时才有 v2 迁移场景） | major | open | — |

| TD3 | 2026-08-15 | `scripts/review.ps1`(交给评审者的工作树) ↔ `scripts/_scope.ps1`(读 base ref) | **评审者与范围闸读的是两份不同的卡，冲突时评审者恒输出假「越界」block**：范围闸按设计从 **base ref** 取卡原文（`git show <base>:specs/tasks/<id>.md`，防分支自扩 allow_paths），而 `review.ps1` 把**工作树**丢给评审者——卡若在施工中被修订（L18 要求修订只落 master、不进功能分支），工作树里就一直是**开卡时那份旧卡**。T0-TOOLCHAIN 实测：范围闸 PASS「21 个改动文件均在 allow_paths 内」，同一 sha 上评审者却按旧 allow_paths 判 #1「越界」block，两个机制对同一 diff 结论相反。后果：任何「施工中修订卡」的场景都会被评审者假 block，且理由看起来极像真缺陷（很容易被误信而回退正确改动）；当前只能靠人裁 + 把 master 合进分支绕过 / 修法：`review.ps1` 也从 base ref 取卡原文喂评审者（与 `_scope.ps1` 共用 `Get-ScaffoldCardAllowPathFromText` 的取值路径），或在 prompt 里显式注入「权威 allow_paths（取自 base）」一节 / 可测：构造「分支上卡为旧、master 上卡已扩 allow_paths」的夹具，评审者不得再报越界；范围闸与评审者对同一 diff 的 scope 判定必须一致 / 前置：无 | major | open | — |

| TD2 | 2026-08-15 | `scripts/check-licenses.ps1`(其它生态清单探针 · line ~117) | **Gradle 生态零覆盖却报 PASS（假绿）**：探针只按固定文件名找 `build.gradle` / `build.gradle.kts` 等，够不着本项目实际布局的**嵌套**清单（`android/app/build.gradle.kts`、`android/core/build.gradle.kts`、`android/gradle/libs.versions.toml`），于是「未发现其它生态依赖清单」→ 连覆盖缺口告警都不发，整个 Android 依赖图从未被许可闸看过一眼。T0-TOOLCHAIN 的 R3（GPT-5.6 Sol）实证：`kotlin-test-junit` 传递拉 `junit:junit:4.13.2`(EPL-1.0，禁列) 一路穿过许可闸，靠人肉评审才拦下。后果：许可闸对本项目**主实现面**恒假绿，下一个引入 copyleft 传递依赖的卡不会被机检拦住 / 修法：探针改递归发现（`Get-ChildItem -Recurse -Include build.gradle,build.gradle.kts,libs.versions.toml`，排除 .gradle 缓存与 build 输出），并把 Gradle 生态从「无扫描器→告警」升级为真扫描（`gradlew :core:dependencies` 解析坐标 + 许可查表），最低限度也要让它命中后进 `$coverageGap` / 可测：在本仓跑 `check-licenses.ps1` 须输出「检测到 Gradle 依赖清单」而非「未发现其它生态依赖清单」；植入一枚 EPL 传递依赖的变异须让 `-Strict` 非零退出 / 前置：T0-TOOLCHAIN 合并（`android/` 存在才有清单可扫） | major | carded | `specs/tasks/T0-LICENSE-SCANNER.md`（T0-GATE-HARDENING 已把「看不见」修成「看得见且 -Strict fail-closed」；剩余的「逐坐标机检」由该卡偿还，且是 `docs/RELEASE-CHECKLIST.md` 的发布阻断项） |

| TD1 | 2026-08-14 | `scripts/selftest.ps1`(闸15n · 闸17aa(8)) | **对上游脚手架 bug 的本地补丁 ×2，待回搬**：①15n 把 `TEMPLATE-README.md` 硬列为 L86-WT 权威面，但该文件属元仓专属物（`-Cleanup`/`-Retrofit` 不下发）——已初始化下游必红；②17aa(8) 夹具硬依赖元仓真卡 `specs/tasks/T11-R3-BASELINE.md`（活位或 archive），下游缺卡时 Get-Content 直接终止整跑（且它藏在 `if (-not $fail)` 后，前面全绿才暴露）。二者同为上游 TD74「无 CI 支路测生成下游」的实例。后果：本仓 selftest.ps1 与上游漂移，backfill 对照须带上这两块 diff / 修法：把 `$isPostInit` 跳过（15n）与缺卡跳过（17aa(8)）两补丁回搬上游元仓 / 可测：下游（无 TEMPLATE-README、无 T11 卡）selftest 全绿、元仓两面仍必查 / 前置：无 | minor | open | — |
















<!-- 新债项追加到上表。偿还时改 status + 填指针；勿删行（保留还债轨迹）。 -->

## 可选：背景重构 agent（OpenAI 持续重构循环）
> OpenAI 用后台 agent 定期扫描偏离、自动提重构 PR、小修快速合并。本仓不内置该自动化（避免无人值守写操作），
> 但可手动等价：每若干张卡后跑一次「对照 `CLAUDE.md` 关键不变量 + `docs/QUALITY-RUBRIC.md` §2 扫描偏离」，命中即在此登记 → 开卡。
