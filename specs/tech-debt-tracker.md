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
| TD9 | 2026-08-16 | `scripts/selftest.ps1`（8.2e stub 夹具 + 分片汇总器） | **selftest 可诊断性 + post-merge 稳定性残债**（W2 编排会话实证）：①PR #6/#9 的 `selftest (ubuntu-latest, core)` FAIL 但全日志无一行点名失败闸——汇总器只打 `selftest: FAIL`；②与通过运行的日志 diff 显示约 45 项检查静默未执行、也无 skip 行；③同一 merge commit 重跑即全绿（run 31941736470），8.2e 并发 stub 在 CI 高负载下会 flake。`T0-DEBT-SELFTEST-CRITICAL-PATH` 已把完整矩阵移出 PR 关键路径，不再阻塞产品推进；剩余修法只针对合并后 canary：FAIL 点名闸 id、skip 可见、8.2e 负载容差。 | major | carded | 五张 TD9 子卡均已 merged，末卡为 `T0-DEBT-SELFTEST-LOAD-STABILITY`（PR #189 `95f74222`）；仅余一次 post-merge core 重放，稳定后才可把 TD9 置 paid。 |






| TD2 | 2026-08-15 | `scripts/check-licenses.ps1`(其它生态清单探针 · line ~117) | **Gradle 生态零覆盖却报 PASS（假绿）**：探针只按固定文件名找 `build.gradle` / `build.gradle.kts` 等，够不着本项目实际布局的**嵌套**清单（`android/app/build.gradle.kts`、`android/core/build.gradle.kts`、`android/gradle/libs.versions.toml`），于是「未发现其它生态依赖清单」→ 连覆盖缺口告警都不发，整个 Android 依赖图从未被许可闸看过一眼。T0-TOOLCHAIN 的 R3（GPT-5.6 Sol）实证：`kotlin-test-junit` 传递拉 `junit:junit:4.13.2`(EPL-1.0，禁列) 一路穿过许可闸，靠人肉评审才拦下。后果：许可闸对本项目**主实现面**恒假绿，下一个引入 copyleft 传递依赖的卡不会被机检拦住 / 修法：探针改递归发现（`Get-ChildItem -Recurse -Include build.gradle,build.gradle.kts,libs.versions.toml`，排除 .gradle 缓存与 build 输出），并把 Gradle 生态从「无扫描器→告警」升级为真扫描（`gradlew :core:dependencies` 解析坐标 + 许可查表），最低限度也要让它命中后进 `$coverageGap` / 可测：在本仓跑 `check-licenses.ps1` 须输出「检测到 Gradle 依赖清单」而非「未发现其它生态依赖清单」；植入一枚 EPL 传递依赖的变异须让 `-Strict` 非零退出 / 前置：T0-TOOLCHAIN 合并（`android/` 存在才有清单可扫） | major | carded | `T0-LICENSE-SCANNER`（PR #25 · master `035df10` · merged）→ `T0-LICENSE-POLICY`（PR #26 · master `04f80fd` · merged）→ `T0-LICENSE-DIAGNOSTICS`（PR #27 · master `e3b52c7` · merged；18 个 diagnostics mutation，经 round-cap 人裁）→ `T0-LICENSE-GAV-BOUNDS`（PR #28 · master `f61f586` · merged；TD135 paid，5 个 boundary mutation）→ `T0-LICENSE-CI-INTEGRATION`（ready；TD2 5/5；五卡串行；分别冻结图/GAV、POM/分类、finding/CLI、GAV 边界与 CI/文档合同；本卡 merge 且总证据齐全后 TD2 才可 paid） |





























| TD129 | 2026-08-18 | Field Ledger capture/history UI 发布前验收 | **现有卡只要求功能冒烟，没有一张卡统一证明现场可用性**：日光、单手拇指、TalkBack、200% 字号、减少动态效果、保存失败反馈与相机取证若各自零散验收，功能可编译却仍可能在真实巡检中不可读、不可达或丢失上下文。后果：关键无障碍/现场缺陷到发布后才暴露，且没有证据与偿还指针 / 修法：T2 capture 与 T3 history 合并后跑专属真机验收矩阵，验收卡只收集证据，发现的 P0/P1 各开独立 TD，不在验收卡顺手改 UI / 可测：报告八个固定 evidence 段均有设备、步骤、截图/录屏、结果；无待验证标记；所有 P0/P1 finding 都有债项指针 / 前置：T2-CAPTURE-UI 与 T3-HISTORY-COMPARE | major | carded | `specs/tasks/T3-FIELD-UX-ACCEPTANCE.md` |

| TD130 | 2026-08-19 | `T3-PDF-RENDERER` 固定图片采样参数 | **PDF 没有面向用途的导出质量契约**：原卡只计划内联约 150dpi、附录约 200dpi、长边 2048px。后果：几十张照片的分享报告可能远大于需要，而用户无法用低体积档换取可接受清晰度；反向也没有铭牌小字所需的高档 / 修法：在同一渲染卡增加 Low/Medium/High/Extra High 纯 core 参数与每次导出选择，设置固定夹具的相对大小和可读性验收，不承诺任意内容的绝对 MB / 可测：四档参数、默认 Medium、固定 80 照夹具总体大小单调、High 铭牌可读、报告内容和 data_hash 不随档位改变 / 前置：T3-REPORT-COMPOSER | major | carded | `specs/tasks/T3-PDF-RENDERER.md` |
| TD133 | 2026-08-19 | app 私有照片目录长期增长 | **没有按物业巡检代数限制本机全尺寸照片，也没有安全预览、确认和回填路径**。后果：数年后占用可达 GB；若直接删除又会破坏 previous/baseline、历史查看及证据恢复 / 修法：以每物业最近 N 次已完成巡检控制本机字节，选项 1/3/5/10/Always、默认 3；仅在 finalize、PDF、30 天宽限、非保护引用且 exact verified receipt 全满足时列候选，默认人工确认，并从加密包按 hash/size 回填 / 可测：所有保护条件逐一做单点变异；预览释放量准确；中断不伪报；回填失败不改状态，成功原子置 PRESENT / 前置：T5-MEDIA-ARCHIVE-CONTRACT、T5-BACKUP-IO、T3-PDF-RENDERER、T3-HISTORY-COMPARE | critical | carded | `specs/tasks/T5-LOCAL-MEDIA-RETENTION.md` |
| TD134 | 2026-08-19 | devops-scaffold v0.29.0 下游 ↔ 上游 v0.38.0 | **本仓已局部吸收上游变化但缺少选择性回填闭环**：scaffold-selftest 已退出 PR 关键路径，task 却仍把 ci.yml 明写成信息性并在 R3 后直接 merge；lessons 已有冷库 mover 但 search/check 不跨热冷；常驻文本减负缺量化协议；机器测试仍大量锚本地化 prose。后果：free/private 仓可能在 CI 未完成时合并，lesson 常驻上下文持续膨胀，状态断言受编码/措辞变化影响；整包覆盖又会错误删除本仓 mandatory R3、RED/waterline 和 reviewer hardening / 修法：原 CI 实现因 R3 60000 字符预算拆为 `T0-CI-MERGE-GATE → T0-CI-HARDENING-SPLIT-PLAN → T0-CI-PAGED-CONTRACT → T0-CI-IDENTITY-DEADLINE → T0-RECEIPT-LOSS-FAIL-CLOSED`，再接 `T0-ASCII-SHIP-CODES → T0-ASCII-CARD-SECRET-CODES → T0-ASCII-REVIEW-ARCHIVE-CODES`；另有独立 `T0-HARNESS-SUBTRACTION-PROTOCOL` 与 `T0-LESSONS-COLD-RECALL`，共享 selftest 时串行但不伪造业务依赖 / 可测：各卡 DoD；全部 merged 后跑 full selftest + lessons check + 一张真实小卡 ship 回放，确认 CI/head 绑定、receipt-loss fail-closed、cold recall、code roster 和本仓保留差异 / 前置：CI 链须先等 `T0-R3-DIFF-BUDGET` 合并；不得直接把 ScaffoldVersion 改成 0.38.0 | major | carded | `T0-CI-MERGE-GATE` → `T0-CI-HARDENING-SPLIT-PLAN` → `T0-CI-PAGED-CONTRACT` → `T0-CI-IDENTITY-DEADLINE` → `T0-RECEIPT-LOSS-FAIL-CLOSED` → `T0-ASCII-SHIP-CODES` → `T0-ASCII-CARD-SECRET-CODES` → `T0-ASCII-REVIEW-ARCHIVE-CODES`；并行独立卡 `T0-HARNESS-SUBTRACTION-PROTOCOL`、`T0-LESSONS-COLD-RECALL`；全链 merge + 总验收后才可 paid |


| TD141 | 2026-08-20 | `T4-COMPLIANCE-ENGINE` PR #43 R3 round cap | 原卡两轮 R3 后仍缺四类可证伪证据：非默认规则夹具未跨默认常量边界，改期输入不能排除自身记录，配置拒绝分支负例不完整，公开集合不可变包装无 mutation-sensitive 证明。继续在同 PR 追加会越过轮次上限并放大法律边界卡。修法：人裁原 PR 后，用专卡补非默认边界矩阵、记录身份/自排除、表驱动拒绝夹具与集合写入拒绝；不重开 DST/checksum/timezone/reason 合同 / 可测：四组测试先在 PR #43 当前实现上 RED；删除配置读取、自排除、任一拒绝或不可变包装均翻红 / 前置：T4-COMPLIANCE-ENGINE | major | carded | `specs/tasks/T4-COMPLIANCE-ENGINE-R3-CLOSURE.md` |
| TD142 | 2026-08-21 | `T0-R3-DIFF-BUDGET` PR #53 R3 round cap | 原卡第 2 轮仍有两条同一权威链缺口：预算 diff 未显式禁用仓库可控的 external diff/textconv，且 SizeOnly 捕获的提交 OID 未贯穿后续 push/R3/merge。后果：成功 helper 可隐藏大正文，或 ref 在测量后移动，令 pre-push 闸测 A 却发布 B。继续原 PR 第 3 轮违反 ReviewRoundCap 并重复放大 harness 变更 / 修法：人裁原 PR后，用专卡禁用 diff helper，并把 exact measured OID 贯穿 ship 的每个副作用与 PR head 校验 / 可测：成功 spoof 仍阻断；SizeOnly 后 ref 移动时 push/reviewer/merge 均未触发；正常本地/远端 hermetic ship 仍通过 / 前置：T0-R3-DIFF-BUDGET | major | carded | `specs/tasks/T0-R3-DIFF-BUDGET-R3-CLOSURE.md` |
| TD143 | 2026-08-21 | `T0-LICENSE-CI-INTEGRATION` PR #49 R3 round cap | 原卡两轮 R3 后仍有两类 harness 合同缺口：selftest/workflow/scanner/docs 的 raw-text 断言可被注释满足；cold seeded 跳过真实 scan，却仍输出真实 Strict scan PASS，且 integration 静默忽略 SkipMutations。继续在同 PR 追评会违反两轮上限并扩大 fan-in closure / 修法：人裁原 PR 后，用专卡把四类接线改为活跃且唯一的结构断言并加 comment/delete mutation；明确传播或拒绝 SkipMutations，按 cold/full 模式输出真实证据 / 可测：四类注释/删除变异命中专属码；cold 无 Gradle cache 绿且不声称真实 scan，默认 DoD 仍含 Strict/TestNG / 前置：T0-LICENSE-CI-INTEGRATION | major | carded | `specs/tasks/T0-LICENSE-CI-INTEGRATION-R3-CLOSURE.md` |
| TD144 | 2026-08-21 | `T0-LESSONS-COLD-RECALL` PR #51 R3 round cap | 原卡两轮 R3 后仍有一个选择器 fail-open 缺口：`tier` 与 `recurrence` 由未锚定正则读取，正文 prose 可伪造缺失值，使 malformed lesson 看起来满足自动归冷条件。继续在原 PR 追加属于第 3 轮并违反评审硬上限。修法：先人裁 PR #51，再用专卡把元数据解析钉到唯一、完整、锚定的规范 meta 行；缺失、重复、非法字段与正文诱饵均 fail-closed / 可测：hermetic RepoRoot hostile fixtures 先在 PR #51 当前实现上 RED；合法条目、既有 hot/cold 行为不变 / 前置：T0-LESSONS-COLD-RECALL | major | carded | `specs/tasks/T0-LESSONS-COLD-RECALL-R3-CLOSURE.md` |
| TD161 | 2026-08-26 | 运行时 structured logs 与本机支持诊断 | **现有日志没有持久、有界、可由用户授权导出的 typed 诊断证据，普通日志扩展会放大 PII 风险且缺少失败隔离**。后果：admin/support 无法离线重建 backup/restore/finalize 失败，同时 logger 故障可能影响主事务。修法：独立 no-backup diagnostics DB、typed event recorder 与用户确认的只读脱敏导出；90 天/20,000 行裁剪，不进主库、备份、网络或证据哈希。可测：敏感字段、CRLF、未知 key 与超限全部拒绝，满盘/损坏不改变业务结果，飞行模式导出 manifest/hash 可复验且禁项零命中；实现前置为 T1-LOCAL-DATA-SECURITY | major | carded | `T5-OPERATION-EVENT-STORE` → `T5-DIAGNOSTIC-EXPORT`；两卡 merged + 总验收后才可 paid |
| TD162 | 2026-08-28 | `scripts/selftest.ps1` skip mutation budget fixture（PR #187 的并发 post-reset R3） | **mutation oracle 与 inventory 完整性未独立闭合**：四个语义 mutant 由各自的“坏输出特征”分类器判 killed，而不是由 baseline 同一份 ledger contract oracle 拒绝；identity inventory 只统计命中的 command AST，未独立证明三类命令均在、名字非空且合法、`command@offset` 唯一，以及分项计数之和等于总数。后果：专用分类器与真实合同可一起漂移，或 decoy/重复/空 identity 仍满足资源预算。修法：提取 baseline/mutant 共用的 mode contract oracle；预算前验证大小写不敏感的三类集合、逐类计数、规范 identity 与唯一性。可测：删除任一 mode 合同、注入 unknown/空/重复 identity、移除任一命令族或伪造总数均命中专属红灯，现有四 mutant 继续逐一被同一 oracle 杀死 / 前置：PR #187 已 merged；不得重开 TD9 load-stability 范围 | major | open | — |
| TD163 | 2026-09-01 | `scripts/selftest.ps1` 的 `seeded-remote` 分片墙钟（`T37-CIGATE/API-CONTRACT`，PR #218） | **分页契约闸的每条用例都是一次完整 ship**（建仓→red→ship→被 CI 闸拦下），单例约 12s；27 条用例把 `seeded-remote` 分片本机墙钟从约 2.5 min 推到约 9.6 min。后果：canary 分片逼近 `docs/DEVOPS-WORKFLOW.md` 的「单片 20 分钟」上限，CI 机器更慢时余量很薄；继续往该闸加用例会先撞墙。注：`scaffold-selftest.yml` 只在默认分支 push/手动跑，故不阻塞 PR 路径。修法（择一，均不减覆盖）：把阻断类用例改为直接驱动 `Get-GhPagedCollectionBeforeDeadline` 的函数级夹具、只保留少量端到端「target-reaching」证明；或按 8.2d/8.2e 既有做法把 `seeded-remote` 再拆一片。可测：拆分/改造后 `T37-CIGATE/API-CONTRACT` 仍打印成功哨兵、A4 两枚变异仍各自变红，且分片墙钟回到 5 min 以内 / 前置：T0-CI-PAGED-CONTRACT 已 merged | minor | open | — |
| TD164 | 2026-09-01 | `ReminderReceiptStore` 的失败路径没有诊断记录（`T4-SCHEDULE-REMINDER-RECEIPTS`，PR #217） | **偏好读失败与提交失败只转成带类型的结果，本文件不发任何结构化日志**（R3 第 1 轮维度 #12 提出，本卡以「持有 `ReminderDiagnosticPort` 的调用方才有 stage/generation/work id，两边都记会把同一次失败记两遍且这边更少信息」为由只做了 `Throwable`→`Exception` 收窄那一半）。后果：若 delivery 层不消费`PREFERENCE_READ_FAILED` / `WriteUncertain` / `Quarantined(reason)` 并落 typed event，则一次耐久性失败在现场完全不可见——而这正是本 store 唯一会静默的一类事件。修法：`T4-SCHEDULE-REMINDER-DELIVERY` 在Worker 边界把这三类结果映射成 `LogRecord`（复用 `ReminderDiagnostics` 的 `LogStage.RECEIPT` 与`LogError.RECEIPT_*`），不在 store 内新增依赖。可测：注入读失败与提交失败各一次，断言 delivery 侧恰好记录一条对应 `error_code` 的结构化行，且行内不含 property id、日期或异常原文 / 前置：本卡已 merged | minor | open | — |
| TD165 | 2026-09-01 | 写不确定毒化在进程内无恢复路径（`T4-SCHEDULE-REMINDER-RECEIPTS`，PR #217） | **一次未确认的 `commit()` 之后，该偏好文件上所有 occurrence 的 admit/CAS/recover 永久返回 `WriteUncertain`，直到进程重启**（R3 第 2 轮维度 #9 判定：`SharedPreferences.commit()` 重写整个文件，故失败后整份文件都不再是证据）。这是有意的 fail-closed，但代价是一次瞬时磁盘错误会让本进程此后完全无法记录提醒回执。后果：用户在该次运行内不会再收到任何新提醒的准入，且 UI 无从区分「暂时不可写」与「坏了」。修法（择一）：① 提供显式重建信任的入口——重读整份文件、逐 occurrence 重算规范信封、全部自洽才清毒化；② 在 `docs/adr/` 记一条「有意接受重启前不恢复」的决定，并要求 UI 对该态给出可操作文案。可测：走法①时，毒化后调用重建入口且文件自洽则后续写入恢复、文件不自洽则维持毒化，两条各有专属红灯 / 前置：本卡已 merged | minor | open | — |
| TD166 | 2026-09-01 | 损坏或不可读的回执让提醒静默永久丢失（`T4-SCHEDULE-REMINDER-DELIVERY`，PR #219） | **`ReminderDeliveryRunner` 对 `Missing` / `Quarantined(PREFERENCE_READ_FAILED)` / 写不确定一律返回 `FAILURE` 且不重试**，因此一次瞬时的偏好读失败会让该 occurrence 的提醒永远不再发出（用户表现：到期了但没收到通知，且无任何用户可见线索）。本卡刻意不扩大范围：A3 只授权「权限 + 显式 pre-post 瞬时故障」重试，读失败不在该词表内，故当前行为是 fail-closed 而非疏忽。修法（择一）：① 把「存储读失败」并入可重试词表，与 TD165 的信任重建入口一起做（两者同源：都需要先能重新信任存储）；② 在 `docs/adr/` 记一条「有意接受损坏即丢失」的决定，并要求 scheduler/UI 卡对该态给出可操作文案。可测：注入一次抛异常的 `readAll()`，走法①时 attempt 0/1 必须 `RETRY` 且回执不前移、attempt 2 关闭，三条各有专属红灯 / 前置：`T4-SCHEDULE-REMINDER-SCHEDULER`（恢复面归它） | minor | open | — |

| TD167 | 2026-09-02 | confirm 路径在 CAS 失败后低报 admission（`T4-SCHEDULE-REMINDER-SCHEDULER`，PR #222） | **`confirm`（enqueue 已确认或 retained 查到本代 `ENQUEUED`/`RUNNING`）本身已握有 admission 证据，但重读只认同代 `ENQUEUED`**：若 worker 抢在该 CAS 之前把同代推到 `RETRYABLE`，本次注册记 `RECEIPT_CONTENDED` 而非 admission（R3 第 5 轮维度 #6/A4）。方向是**低报**、不是高报：调用方重试后，下一次注册的 retained-work 查询会以证据给出 `RETAINED_WORK_ENQUEUED`（ADMITTED），故无信息丢失，只延后一次注册。修法：把「调用点自带的确定性 admission 证据」与「靠相位反推」分开——confirm 路径的重读只用来判定 superseded/closed，其余一律沿用调用点已有的结论。可测：用 compiled production runner 在 confirm 的 CAS 之前把回执推到 `RETRYABLE`，断言结果为 ADMITTED 家族而非 contention，并配一枚让该分支变红的变异 / 前置：`T4-SCHEDULE-REMINDER-FLIGHT`（worker 与 callback 的真实竞速在该卡） / **偿还卡改为 `T4-SCHEDULE-REMINDER-RECOVERY`**（2026-09-02：FLIGHT 开工前按预算二分，本条与权限恢复、诊断渲染一同移入承接卡） | minor | open | — |
| TD168 | 2026-09-02 | 迟到失败 callback 在 watchdog 先结算时丢失 admission 分类（`T4-SCHEDULE-REMINDER-FLIGHT`，R3 第 4 轮） | **同一事实按谁赢得竞速而有两种分类**：worker 证实 admission 后，若 **watchdog** 先 settle 掉 flight（`WORKER_CONFIRMED_ADMISSION`，waiter 得 ADMITTED），随后到达的失败 callback 走 late 分支，被记成原始的 `ENQUEUE_CALLBACK_NULL/ERROR/THROWABLE` 且 `callbackCause` 为空；而当 **callback 自己**赢得竞速时同一情形正确记为 `ENQUEUE_CALLBACK_AFTER_WORKER_STARTED` + 原始类别。**影响仅限日志**：waiter 与回执均已由 worker proof 正确结算，late 记录本身已带 `late=true`、不改任何状态，故无决策受影响。修法：在 flight 上留下它结算时的 cause（或在 late 分支只读重取等价证据），late 分支据此对失败 callback 发 `ENQUEUE_CALLBACK_AFTER_WORKER_STARTED` + `callbackCause`，并补一条 worker → watchdog → 迟到失败 callback 的黑盒用例。/ 偿还卡：`T4-SCHEDULE-REMINDER-RECOVERY`（该卡已拥有注册诊断渲染与失败类别 `cause_code`，同一处落点）| minor | open | — |
| TD169 | 2026-09-02 | 边缘路径诊断记的 generation 为空或记成了新的那一代（`T4-SCHEDULE-REMINDER-FLIGHT`，R3 第 6 轮） | `expire` 的 Missing/Quarantined 与 `proved`/`reread` 的不可读分支构造 `Settlement(RECEIPT_QUARANTINED)` **不带 generation**，于是 `Flight.record()` 记出 `identity=null`——而这条 flight 明明知道自己是以第 N 代提交的；两处 generation 不匹配分支又用 `fresh.settle(GENERATION_SUPERSEDED)`，把记录记在**第 N+1 代**名下，读起来成了「新的那一代被取代了」。**影响仅限日志**：waiter 与回执均正确。修法是这些 Settlement 一律带上**本 flight 自己的** generation（约 5 处单行改动）+ 逐条断言精确诊断记录。/ 偿还卡：`T4-SCHEDULE-REMINDER-RECOVERY`——该卡 A2 本就要求诊断渲染带 **non negative generation_number**，必然经手此处 | minor | open | — |
| TD170 | 2026-09-02 | waiter 抛出的 Throwable 被静默吞掉、无任何诊断（`T4-SCHEDULE-REMINDER-FLIGHT`，R3 第 6 轮） | `publish` 逐个隔离 waiter 是 A2 的硬要求（一个 waiter 抛错不得饿死其余），但当前 `catch (_: Throwable)` **不记录任何东西**，于是调用方的 bug 完全消失、无从排查。修法：继续调用后续 waiter 不变，另发一条**结构化且脱敏**的 waiter-failure 诊断（带注册 identity 与 cause，不带异常文本——`ReminderRegistrationRecord` 的隐私约定不收异常文本），并给抛错用例补一条自证断言。**需要新的诊断词汇**（现有 `ReminderRegistrationCause` 无「waiter 抛错」这一类），故落点在拥有诊断渲染的承接卡。/ 偿还卡：`T4-SCHEDULE-REMINDER-RECOVERY` | minor | open | — |

<!-- 新债项追加到上表。偿还时改 status + 填指针；勿删行（保留还债轨迹）。 -->

## 可选：背景重构 agent（OpenAI 持续重构循环）
> OpenAI 用后台 agent 定期扫描偏离、自动提重构 PR、小修快速合并。本仓不内置该自动化（避免无人值守写操作），
> 但可手动等价：每若干张卡后跑一次「对照 `CLAUDE.md` 关键不变量 + `docs/QUALITY-RUBRIC.md` §2 扫描偏离」，命中即在此登记 → 开卡。
- **TD166** seeded-remote 分片耗时在本卡窗口内实测 11m31s–22m25s 波动（基线 8m08s），且**更少**夹具的一次反而更慢 ⇒ 主因是并行会话的 Gradle/Android 负载抢机器，不是 T37-CIGATE 夹具本身；但 20 分钟单片上限已被触碰。**待办**：在安静机器上重测三次取中位数，确认后再决定是否按 8.2d/8.2e 惯例拆片。与 TD163（分页夹具把该片推到 9.6m）同源，宜合并处置。status=open · 提出=T0-CI-IDENTITY-DEADLINE R5
- **TD168** `PdfRenderProgramBuilder.build(plan, inspectionId, semanticFingerprint, quality)` 把 inspectionId 与 semanticFingerprint 收作**自由入参**：`dataHash`/`audience` 读自 plan、无法与 plan 分歧（M6 已钉），但**没有任何东西阻止调用方把 plan A 配上 fingerprint B**——两者本应同出一个 `ReportContent`。本卡刻意不引入该耦合（卡内契约：唯一输入 = DocumentPlan + identity），配对属 `T3-REPORT-EXPORT-CORE` A1「一次投影复用于 PDF/HTML、两份产物携带同一 fingerprint」。**待办**：EXPORT-CORE 落地时须有一条测试证明「plan 与 fingerprint 出自同一次投影」，否则这条缝会一直敞着；若届时发现更自然的做法是让 builder 直接收 `ReportContent`，则改这里、并同步 T3-PDF-RENDERER 卡的输入契约。status=open · 提出=T3-PDF-RENDERER R5
| TD171 | 2026-09-02 | 「安全路径段」的判据在两个派生点各写了一份（`T3-PDF-ARTIFACT-PATHS`，PR #228） | `PdfArtifactPaths.isSafeSegment` 与 `MediaPaths.isSafeSegment` 是**逐字相同**的私有实现（非空白 · 不含 `/` · 不含反斜杠 · 非 `.` · 非 `..`），于是「什么算安全路径段」这条全仓规则有了**两个权威**。当前两份一致，故无行为缺陷；风险是单侧演进——若将来一侧收紧（例如禁止段内嵌换行、或加长度上限）而另一侧不动，照片与报告两个命名空间会对同一个损坏值给出不同答案，且没有任何闸能发现。刻意未在本卡合并：抽出共用判据要动 `core/media/`，在本卡 `allow_paths` 之外。修法：把判据提到两者共用的一处（如 `core/storage/RelPathSegments`），两个派生点各自的形状判定仍保留在自己文件里；可测=一处改判据、两个命名空间的负例同时变红 / 偿还卡：`T3-REPORT-EXPORT-CORE`（它是首个同时消费两个命名空间的调用方） | minor | open | — |
