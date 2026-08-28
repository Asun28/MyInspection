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
| TD9 | 2026-08-16 | `scripts/selftest.ps1`（8.2e stub 夹具 + 分片汇总器） | **selftest 可诊断性 + post-merge 稳定性残债**（W2 编排会话实证）：①PR #6/#9 的 `selftest (ubuntu-latest, core)` FAIL 但全日志无一行点名失败闸——汇总器只打 `selftest: FAIL`；②与通过运行的日志 diff 显示约 45 项检查静默未执行、也无 skip 行；③同一 merge commit 重跑即全绿（run 31941736470），8.2e 并发 stub 在 CI 高负载下会 flake。`T0-DEBT-SELFTEST-CRITICAL-PATH` 已把完整矩阵移出 PR 关键路径，不再阻塞产品推进；剩余修法只针对合并后 canary：FAIL 点名闸 id、skip 可见、8.2e 负载容差。 | major | carded | 关键路径、失败点名、skip 可见、生产 no-git 路由与 mutation 预算已偿还：PR #31 `b8dee45`、PR #33 `c745015`、PR #110 `02425dd`、PR #187 `86a895a9`。唯一余项为 `T0-DEBT-SELFTEST-LOAD-STABILITY`；该卡 merged 后再做一次 post-merge core 重放，才可把 TD9 置 paid。 |






| TD2 | 2026-08-15 | `scripts/check-licenses.ps1`(其它生态清单探针 · line ~117) | **Gradle 生态零覆盖却报 PASS（假绿）**：探针只按固定文件名找 `build.gradle` / `build.gradle.kts` 等，够不着本项目实际布局的**嵌套**清单（`android/app/build.gradle.kts`、`android/core/build.gradle.kts`、`android/gradle/libs.versions.toml`），于是「未发现其它生态依赖清单」→ 连覆盖缺口告警都不发，整个 Android 依赖图从未被许可闸看过一眼。T0-TOOLCHAIN 的 R3（GPT-5.6 Sol）实证：`kotlin-test-junit` 传递拉 `junit:junit:4.13.2`(EPL-1.0，禁列) 一路穿过许可闸，靠人肉评审才拦下。后果：许可闸对本项目**主实现面**恒假绿，下一个引入 copyleft 传递依赖的卡不会被机检拦住 / 修法：探针改递归发现（`Get-ChildItem -Recurse -Include build.gradle,build.gradle.kts,libs.versions.toml`，排除 .gradle 缓存与 build 输出），并把 Gradle 生态从「无扫描器→告警」升级为真扫描（`gradlew :core:dependencies` 解析坐标 + 许可查表），最低限度也要让它命中后进 `$coverageGap` / 可测：在本仓跑 `check-licenses.ps1` 须输出「检测到 Gradle 依赖清单」而非「未发现其它生态依赖清单」；植入一枚 EPL 传递依赖的变异须让 `-Strict` 非零退出 / 前置：T0-TOOLCHAIN 合并（`android/` 存在才有清单可扫） | major | carded | `T0-LICENSE-SCANNER`（PR #25 · master `035df10` · merged）→ `T0-LICENSE-POLICY`（PR #26 · master `04f80fd` · merged）→ `T0-LICENSE-DIAGNOSTICS`（PR #27 · master `e3b52c7` · merged；18 个 diagnostics mutation，经 round-cap 人裁）→ `T0-LICENSE-GAV-BOUNDS`（PR #28 · master `f61f586` · merged；TD135 paid，5 个 boundary mutation）→ `T0-LICENSE-CI-INTEGRATION`（ready；TD2 5/5；五卡串行；分别冻结图/GAV、POM/分类、finding/CLI、GAV 边界与 CI/文档合同；本卡 merge 且总证据齐全后 TD2 才可 paid） |










| TD26 | 2026-08-17 | `T2-ROOM-REPEATABLE` ↔ `InspectionRepository` / `CompletenessPort` / history | **重复房间 schema 卡明确排除了运行时状态机，却没有后续卡接住实例维度**：当前 capture 只为每个 `room_key` 建 `instance_no=1`，baseline 写入又只按 `stable_id` 取首项；schema 已允许同一巡检的 BEDROOM #1/#2 各有同一 `stable_id`，相关查询无顺序保证。后果：重复房间落地后，Exit 的 #2 可能误拿 Ingoing #1 作基线，分类随未定义行序变化；progress/finalize 也无法按声明数量验证缺失实例 / 修法：在专属运行时交接卡中定义并持久化或派生物业的重复房间数量，所有基线匹配改用 `(room_key, instance_no, stable_id)`，走查按模板房间序再按 `instance_no`，finalize 验证所需实例数，并同步后续 history/Exit 卡的旧前提 / 可测：B1/B2 同 stable_id 不同状态时 Exit B2 必与 B2 比；交换 baseline 插入顺序结果不变；progress 顺序稳定；声明两间却缺 B2 时 finalize 拒绝 / 前置：TD4 已 paid；`T2-ROOM-REPEATABLE` 已由 PR #190 完成 schema/版本评审；不得与该 schema 卡合卡，必须全新 worktree，并在 capture UI/history/Exit 内容上线前偿还 | major | carded | `specs/tasks/T2-REPEATABLE-ROOM-RUNTIME.md` |



















| TD129 | 2026-08-18 | Field Ledger capture/history UI 发布前验收 | **现有卡只要求功能冒烟，没有一张卡统一证明现场可用性**：日光、单手拇指、TalkBack、200% 字号、减少动态效果、保存失败反馈与相机取证若各自零散验收，功能可编译却仍可能在真实巡检中不可读、不可达或丢失上下文。后果：关键无障碍/现场缺陷到发布后才暴露，且没有证据与偿还指针 / 修法：T2 capture 与 T3 history 合并后跑专属真机验收矩阵，验收卡只收集证据，发现的 P0/P1 各开独立 TD，不在验收卡顺手改 UI / 可测：报告八个固定 evidence 段均有设备、步骤、截图/录屏、结果；无待验证标记；所有 P0/P1 finding 都有债项指针 / 前置：T2-CAPTURE-UI 与 T3-HISTORY-COMPARE | major | carded | `specs/tasks/T3-FIELD-UX-ACCEPTANCE.md` |

| TD130 | 2026-08-19 | `T3-PDF-RENDERER` 固定图片采样参数 | **PDF 没有面向用途的导出质量契约**：原卡只计划内联约 150dpi、附录约 200dpi、长边 2048px。后果：几十张照片的分享报告可能远大于需要，而用户无法用低体积档换取可接受清晰度；反向也没有铭牌小字所需的高档 / 修法：在同一渲染卡增加 Low/Medium/High/Extra High 纯 core 参数与每次导出选择，设置固定夹具的相对大小和可读性验收，不承诺任意内容的绝对 MB / 可测：四档参数、默认 Medium、固定 80 照夹具总体大小单调、High 铭牌可读、报告内容和 data_hash 不随档位改变 / 前置：T3-REPORT-COMPOSER | major | carded | `specs/tasks/T3-PDF-RENDERER.md` |
| TD132 | 2026-08-19 | T5 备份成功状态 ↔ 本机照片删除资格 | **现有计划只有目的地和上次成功时间，没有“某个照片 exact bytes 已在某包回读验证”的持久证明**。后果：若仅凭 Worker 成功、SAF URI 或云盘品牌清理本机字节，授权撤销、短写、缺文件或错误 scope 会把唯一证据删除 / 修法：新增不改 finalized photo 行的本机物理状态、PDF 回执、verified backup receipt 及逐资产 rel_path/hash/size 条目；实际回读由 T5-BACKUP-IO 消费 / 可测：缺字段、哈希/大小不符、回执撤销、跨物业、未来时间均不可产生归档资格，schema 升级/读回全绿 / 前置：TD4、TD24、T5-BACKUP-FORMAT | critical | carded | `specs/tasks/T5-MEDIA-ARCHIVE-CONTRACT.md` |
| TD133 | 2026-08-19 | app 私有照片目录长期增长 | **没有按物业巡检代数限制本机全尺寸照片，也没有安全预览、确认和回填路径**。后果：数年后占用可达 GB；若直接删除又会破坏 previous/baseline、历史查看及证据恢复 / 修法：以每物业最近 N 次已完成巡检控制本机字节，选项 1/3/5/10/Always、默认 3；仅在 finalize、PDF、30 天宽限、非保护引用且 exact verified receipt 全满足时列候选，默认人工确认，并从加密包按 hash/size 回填 / 可测：所有保护条件逐一做单点变异；预览释放量准确；中断不伪报；回填失败不改状态，成功原子置 PRESENT / 前置：T5-MEDIA-ARCHIVE-CONTRACT、T5-BACKUP-IO、T3-PDF-RENDERER、T3-HISTORY-COMPARE | critical | carded | `specs/tasks/T5-LOCAL-MEDIA-RETENTION.md` |
| TD134 | 2026-08-19 | devops-scaffold v0.29.0 下游 ↔ 上游 v0.38.0 | **本仓已局部吸收上游变化但缺少选择性回填闭环**：scaffold-selftest 已退出 PR 关键路径，task 却仍把 ci.yml 明写成信息性并在 R3 后直接 merge；lessons 已有冷库 mover 但 search/check 不跨热冷；常驻文本减负缺量化协议；机器测试仍大量锚本地化 prose。后果：free/private 仓可能在 CI 未完成时合并，lesson 常驻上下文持续膨胀，状态断言受编码/措辞变化影响；整包覆盖又会错误删除本仓 mandatory R3、RED/waterline 和 reviewer hardening / 修法：按六张独立卡选择性回填，真实产物链为 `T0-CI-MERGE-GATE → T0-ASCII-SHIP-CODES → T0-ASCII-CARD-SECRET-CODES → T0-ASCII-REVIEW-ARCHIVE-CODES`，另有独立 `T0-HARNESS-SUBTRACTION-PROTOCOL` 与 `T0-LESSONS-COLD-RECALL`；后者和主链共享 selftest 时只作资源冲突串行，不伪造业务依赖 / 可测：六卡各自 DoD；全部 merged 后跑 full selftest + lessons check + 一张真实小卡 ship 回放，确认 CI/head 绑定、cold recall、code roster 和本仓保留差异 / 前置：CI 卡须先等 `T0-R3-DIFF-BUDGET` 合并；不得直接把 ScaffoldVersion 改成 0.38.0 | major | carded | `T0-CI-MERGE-GATE` → `T0-ASCII-SHIP-CODES` → `T0-ASCII-CARD-SECRET-CODES` → `T0-ASCII-REVIEW-ARCHIVE-CODES`；并行独立卡 `T0-HARNESS-SUBTRACTION-PROTOCOL`、`T0-LESSONS-COLD-RECALL`；全六卡 merge + 总验收后才可 paid |


| TD139 | 2026-08-20 | `T3-REPORT-COMPOSER` PR #39 R3 round cap | 原卡两轮 R3 后仍剩六项 renderer-ready 布局证据缺口：项目内联缩略图未落 40mm 表格列几何、长 caption 可能拆图槽、实际页脚绘了完整哈希、空房标题可孤行、引用/照片层级校验不足、封面/照片实际文本仍绘 epoch 且漏 totals。继续在同 PR 追评会重复扩大 diff 并消耗大额评审 token。修法：人裁原 PR后，用专卡逐项补 exact geometry/不可拆/短哈希/分组/输入校验/ISO-8601 可见文本；不重开已闭合状态域和长文本分页 / 可测：六组行为断言与单句删除变异，报告专属 DoD 绿 / 前置：T3-REPORT-COMPOSER | major | carded | `specs/tasks/T3-REPORT-COMPOSER-R3-CLOSURE.md` |
| TD141 | 2026-08-20 | `T4-COMPLIANCE-ENGINE` PR #43 R3 round cap | 原卡两轮 R3 后仍缺四类可证伪证据：非默认规则夹具未跨默认常量边界，改期输入不能排除自身记录，配置拒绝分支负例不完整，公开集合不可变包装无 mutation-sensitive 证明。继续在同 PR 追加会越过轮次上限并放大法律边界卡。修法：人裁原 PR 后，用专卡补非默认边界矩阵、记录身份/自排除、表驱动拒绝夹具与集合写入拒绝；不重开 DST/checksum/timezone/reason 合同 / 可测：四组测试先在 PR #43 当前实现上 RED；删除配置读取、自排除、任一拒绝或不可变包装均翻红 / 前置：T4-COMPLIANCE-ENGINE | major | carded | `specs/tasks/T4-COMPLIANCE-ENGINE-R3-CLOSURE.md` |
| TD142 | 2026-08-21 | `T0-R3-DIFF-BUDGET` PR #53 R3 round cap | 原卡第 2 轮仍有两条同一权威链缺口：预算 diff 未显式禁用仓库可控的 external diff/textconv，且 SizeOnly 捕获的提交 OID 未贯穿后续 push/R3/merge。后果：成功 helper 可隐藏大正文，或 ref 在测量后移动，令 pre-push 闸测 A 却发布 B。继续原 PR 第 3 轮违反 ReviewRoundCap 并重复放大 harness 变更 / 修法：人裁原 PR后，用专卡禁用 diff helper，并把 exact measured OID 贯穿 ship 的每个副作用与 PR head 校验 / 可测：成功 spoof 仍阻断；SizeOnly 后 ref 移动时 push/reviewer/merge 均未触发；正常本地/远端 hermetic ship 仍通过 / 前置：T0-R3-DIFF-BUDGET | major | carded | `specs/tasks/T0-R3-DIFF-BUDGET-R3-CLOSURE.md` |
| TD143 | 2026-08-21 | `T0-LICENSE-CI-INTEGRATION` PR #49 R3 round cap | 原卡两轮 R3 后仍有两类 harness 合同缺口：selftest/workflow/scanner/docs 的 raw-text 断言可被注释满足；cold seeded 跳过真实 scan，却仍输出真实 Strict scan PASS，且 integration 静默忽略 SkipMutations。继续在同 PR 追评会违反两轮上限并扩大 fan-in closure / 修法：人裁原 PR 后，用专卡把四类接线改为活跃且唯一的结构断言并加 comment/delete mutation；明确传播或拒绝 SkipMutations，按 cold/full 模式输出真实证据 / 可测：四类注释/删除变异命中专属码；cold 无 Gradle cache 绿且不声称真实 scan，默认 DoD 仍含 Strict/TestNG / 前置：T0-LICENSE-CI-INTEGRATION | major | carded | `specs/tasks/T0-LICENSE-CI-INTEGRATION-R3-CLOSURE.md` |
| TD144 | 2026-08-21 | `T0-LESSONS-COLD-RECALL` PR #51 R3 round cap | 原卡两轮 R3 后仍有一个选择器 fail-open 缺口：`tier` 与 `recurrence` 由未锚定正则读取，正文 prose 可伪造缺失值，使 malformed lesson 看起来满足自动归冷条件。继续在原 PR 追加属于第 3 轮并违反评审硬上限。修法：先人裁 PR #51，再用专卡把元数据解析钉到唯一、完整、锚定的规范 meta 行；缺失、重复、非法字段与正文诱饵均 fail-closed / 可测：hermetic RepoRoot hostile fixtures 先在 PR #51 当前实现上 RED；合法条目、既有 hot/cold 行为不变 / 前置：T0-LESSONS-COLD-RECALL | major | carded | `specs/tasks/T0-LESSONS-COLD-RECALL-R3-CLOSURE.md` |
| TD145 | 2026-08-23 | `scripts/lessons.ps1` Resolve-BumpLedger | bump 的主检出解析取 `--git-common-dir` 的父级作为检出根。本仓被当作 submodule 使用时 git 返回 `<super>/.git/modules/<path>`，其父级并非检出根，函数会 fail-closed 抛 [LSN-PLANE-UNRESOLVED] 而非误写——安全但不可用。R3 前独立预审（codex）第 2 条，当时判定不在原卡修：本仓不以 submodule 形式使用，且干净修法（`git worktree list --porcelain` 取首条=主工作树）与卡片钉在 master 的 dod_command（断言源码含 `--git-common-dir`）冲突 / 修法：改用 worktree list 取主工作树并同步改 DoD / 可测：submodule 夹具下 bump 正确写入子模块检出的账本；既有 2d(a)-(e) 全绿 / 前置：无 | minor | open | — |
| TD160 | 2026-08-26 | SQLDelight 点查与 capture/retention 写入口 | **active 与 historical 生命周期语义未分流，写权限依赖调用方自律**。后果：软删父记录可能被新业务引用，通用基线 setter 可绕过同物业、租约、类型与 finalized 守卫，清理终态字段也可能被回填。修法：active/any 点查分流、具名基线操作、purge 终态 CHECK 与 deleted override guard。可测：逐条删除 active predicate、关系守卫或终态约束均命中专属红灯；保留历史报告与已删除租约隐私清理正例。前置：TD4 已 paid；冻结 schema 变更需版本评审 | major | carded | `specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY.md` |
| TD161 | 2026-08-26 | 运行时 structured logs 与本机支持诊断 | **现有日志没有持久、有界、可由用户授权导出的 typed 诊断证据，普通日志扩展会放大 PII 风险且缺少失败隔离**。后果：admin/support 无法离线重建 backup/restore/finalize 失败，同时 logger 故障可能影响主事务。修法：独立 no-backup diagnostics DB、typed event recorder 与用户确认的只读脱敏导出；90 天/20,000 行裁剪，不进主库、备份、网络或证据哈希。可测：敏感字段、CRLF、未知 key 与超限全部拒绝，满盘/损坏不改变业务结果，飞行模式导出 manifest/hash 可复验且禁项零命中；实现前置为 T1-LOCAL-DATA-SECURITY | major | carded | `T5-OPERATION-EVENT-STORE` → `T5-DIAGNOSTIC-EXPORT`；两卡 merged + 总验收后才可 paid |
| TD162 | 2026-08-28 | `scripts/selftest.ps1` skip mutation budget fixture（PR #187 的并发 post-reset R3） | **mutation oracle 与 inventory 完整性未独立闭合**：四个语义 mutant 由各自的“坏输出特征”分类器判 killed，而不是由 baseline 同一份 ledger contract oracle 拒绝；identity inventory 只统计命中的 command AST，未独立证明三类命令均在、名字非空且合法、`command@offset` 唯一，以及分项计数之和等于总数。后果：专用分类器与真实合同可一起漂移，或 decoy/重复/空 identity 仍满足资源预算。修法：提取 baseline/mutant 共用的 mode contract oracle；预算前验证大小写不敏感的三类集合、逐类计数、规范 identity 与唯一性。可测：删除任一 mode 合同、注入 unknown/空/重复 identity、移除任一命令族或伪造总数均命中专属红灯，现有四 mutant 继续逐一被同一 oracle 杀死 / 前置：PR #187 已 merged；不得重开 TD9 load-stability 范围 | major | open | — |


<!-- 新债项追加到上表。偿还时改 status + 填指针；勿删行（保留还债轨迹）。 -->

## 可选：背景重构 agent（OpenAI 持续重构循环）
> OpenAI 用后台 agent 定期扫描偏离、自动提重构 PR、小修快速合并。本仓不内置该自动化（避免无人值守写操作），
> 但可手动等价：每若干张卡后跑一次「对照 `CLAUDE.md` 关键不变量 + `docs/QUALITY-RUBRIC.md` §2 扫描偏离」，命中即在此登记 → 开卡。
