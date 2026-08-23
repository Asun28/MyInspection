# TASK-BOARD — 任务/模型路由总表（v1 · 2026-08-14）

> **这张表管「谁做哪张卡、用什么档」**；每张卡的完整上下文包/验收在 `specs/tasks/<id>.md`；**状态以卡为准**（本表不追状态，防双源漂移）。
> 计划真相源 `_local/PLAN.md`；设计决策 `docs/adr/0001–0004`；需求 `docs/inspection-app-requirements.md`。
> 执行形态：每卡走 R1–R5（`scripts/task.ps1` start→ship），R3 评审恒 = **GPT-5.6 Sol**（`scripts/_config.ps1` 已钉；Sol 原则上不作同卡作者）。

## 模型席位（性价比路由原则）
| 席位 | 模型 | 用在 | 理由 |
|---|---|---|---|
| 工作马（默认） | **DeepSeek V4 Pro** | 规格清晰、契约已冻结的实现/测试/内容卡 | 最高性价比；卡内上下文包给足即可靠 |
| 设计/新颖单点 | **Opus 5** | 相机、PDF composer、加密格式、法律边界引擎 | 错误代价高或无先例可抄的卡 |
| 中档实现 | **Sonnet 5（max effort）** | 标准 Compose UI、Android 平台适配、本机装环境 | android 细节多但模式成熟 |
| 中档替补/只读消化 | **GPT-5.6 Terra** | WorkManager/通知类中档卡、大体量文档消化 | 分流 + 交叉视角 |
| 轻档内容/交叉复核 | **GPT-5.6 Luna Max** | 双语内容卡的第二双眼睛（抄录错编译不报错） | 便宜的独立复核 |
| 评审席（固定） | **GPT-5.6 Sol** | 全卡 R3 合并闸 + 安全面复核 | 已钉 `_config`；作者≠评审 |

效果档说明：effort 值为执行 harness 通用档（low/medium/high/max）；「难度」= 该卡对模型能力的真实要求（S 机械 / M 中档 / H 难 / H+ 最难）。

## 总表（波次 = 依赖拓扑；同波卡可并行，allow_paths 互不重叠）
| 波 | 卡 id | 产出（一句话） | depends_on | 难度 | 首选模型 · effort | 备选 | 内容交叉复核 |
|---|---|---|---|---|---|---|---|
| W0 | T0-TOOLCHAIN | JDK17+SDK+`android/` Gradle 骨架空编译绿+verify/ci 收紧 | — | M | Sonnet 5 · max | Opus 5 | — |
| W0 | T0-GATE-HARDENING | 许可闸递归发现+verify 确定性+两枚闸门自测（拆自 T0-TOOLCHAIN） | T0-TOOLCHAIN | M | Sonnet 5 · max | DeepSeek V4 Pro | —（事后 R3 block ×2 → 见 T0-GATE-FIXFORWARD） |
| W0 | T0-HARNESS-PERF | 横切优化 selftest 与 CI 墙钟时间（约 300 行 harness 改动） | T0-GATE-HARDENING | M | Sonnet 5 · max | DeepSeek V4 Pro | — |
| W0 | T0-SCAFFOLD-LEAN-CI | 普通产品 PR 不启动 scaffold-only 六分片；脚手架权威面变化仍全跑 | T0-HARNESS-PERF | S | GPT-5.6 Terra · high | DeepSeek V4 Pro | **merged**（master `f976d0f`，PR #22；R3 零发现；基线产品 PR #5–#11 = 60 runs / 360 shard jobs；本次 `.github/**` PR 实测 1 run / 6 jobs 全保留；无新增脚本/job/依赖） |
| W0 | T0-R3-DIFF-BUDGET | pre-push/R3 按真实 changed lines + diff chars fail-closed，超大卡必须拆 | T0-DEBT-R3-CARD-BASELINE,T0-DEBT-SELFTEST-CRITICAL-PATH | M | GPT-5.6 Terra · high | Sonnet 5 max | **已拆为 3 张**（2026-08-23）：本卡只留度量/边界/ship 接线；见下两行。原 PR #53 四轮 R3 均命中真缺陷、修复后 61,674 字符超出自身 60,000 上限 |
| W0 | T0-R3-DIFF-INPUT-TRUST | diff 预算的输入只信 git 自己：ext-diff/textconv/属性二进制均不可缩小体量 | T0-R3-DIFF-BUDGET | S | GPT-5.6 Terra · high | Sonnet 5 max | A5 是**已复现**的真绕过：一行 .gitattributes `-diff` 让 1001 行量成 1 行 |
| W0 | T0-R3-MEASURED-OID-BINDING | 被测量的提交＝被 push/评审/合并的那一个（分支引用与 HEAD 双钉） | T0-R3-DIFF-BUDGET | S | GPT-5.6 Terra · high | Sonnet 5 max | 第 3 轮 finding：只钉分支引用时 detached HEAD 可「审 A 合 B」 |
| W0 | T0-R3-FLOW-ENUM-SYNC | 预算闸补进每一处确定性闸枚举 + 每处一条锚定断言（承接母卡 A13） | T0-R3-DIFF-BUDGET | S | GPT-5.6 Terra · high | Sonnet 5 max | **第二次拆分**（2026-08-23）：母卡 committed 51,882 字符在预算内，加上第 2 轮修复即 63,023、超自身 60,000 上限 3,023；A13 是文本同步契约、与行为度量无共享代码面，整条移出。母卡 A13 槽位留空不重排 |
| W0 | ~~T0-R3-DIFF-BUDGET-R3-CLOSURE~~ | **已退役**（2026-08-23）：人裁选路线 2，第 2 轮两项 finding 已在原卡修完；余下契约由上面两张拆卡承接 | — | — | — | — | 退役理由见 T0-R3-DIFF-BUDGET「拆分依据」 |
| W0 | T0-CI-MERGE-GATE | R3 后等待候选分支 ci.yml 全绿并绑定 head 合并（TD134 1/6） | T0-R3-DIFF-BUDGET | M | GPT-5.6 Terra · high | Sonnet 5 max | 选择性回填 v0.32+v0.37 最终形态；不等待 scaffold matrix。2026-08-23 修正本栏：原写已退役的 closure 卡，与卡内 `depends_on` 不符 |
| W0 | T0-GRADLE-RUNTIME-FILE-INPUTS | 测试运行期读的仓内文件声明为 Gradle 测试输入，消除「改了权威文件仍 UP-TO-DATE」的假绿 | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **实测起因**（2026-08-23，T4-COMPLIANCE-ENGINE 变异批）：改 configs/compliance/nz-rules-v1.json 后直接跑 DoD 得 exit 0 且 UP-TO-DATE，加 --rerun-tasks 才 exit 1——权威法律配置改了却可能测试从未执行。CI 干净检出安全，本地增量与评审者复核路径不安全 |
| W0 | T0-CARD-ACCEPTANCE-FIELD | 把 acceptance 封闭验收集合登记为正式卡片字段 + 形态机检（可选字段、缺失只告警） | T0-R3-DIFF-BUDGET | S | GPT-5.6 Terra · high | Sonnet 5 max | 四张卡已在用该字段而 `specs/README.md` 字段表无此行；机检只判形态（编号 A1..An 连续），不判条目精度。与 5 张在飞卡共用 selftest.ps1，须排在其后 |
| W0 | T0-CI-DOCS-FAST-PATH | 纯文档 PR 保留轻量 verify 状态，跳过 Android/Gradle 重闸（TD159） | — | S | GPT-5.6 Terra · high | Codex R3 | **merged**（PR #114 · master `620fb43`；两轮 R3 后人裁；docs-only lane 保留 cards/archive/secrets） |
| W0 | T0-CI-UNICODE-DEP-FIXTURE | 补齐 license scanner 自检夹具的 Unicode helper 依赖并防止 17p3 假绿 | T0-DEBT-LICENSE-SCALAR-FORMAT | S | GPT-5.6 Terra · high | Codex R3 | **merged**（PR #120 · master `bb0c199`；两处 fixture 依赖齐备，17p3 锚定 `gpl-pkg@1.0.0`，seeded/R3 PASS） |
| W0 | T0-CI-LICENSE-GATE-HASH-SYNC | 同步 docs-only License gate 的 8.2b2 规范块哈希 | T0-CI-DOCS-FAST-PATH | S | GPT-5.6 Terra · high | Codex R3 | **merged**（失败 run `32535429955` 定位到 8.2b2 基线漂移；PR #122 · master `5b2e5a5`；本地 core/verify 与 R3 PASS，合并后 run `32538957860` 的 Windows/Linux core 双绿） |
| W0 | T0-HARNESS-SUBTRACTION-PROTOCOL | 量化、可迁移、按组可回滚的 harness 减负协议（TD134 2/6） | — | S | GPT-5.6 Terra · high | DeepSeek V4 Pro | **merged**（master `e971ef8`，PR #24；R3 零发现；只增 15 行协议文本，无实际删减或脚本行为变化） |
| W0 | T0-LESSONS-COLD-RECALL | 一次性 lesson 归冷、热冷统一检索和 ID 并集（TD134 3/6） | — | M | DeepSeek V4 Pro · high | GPT-5.6 Terra · high | PR #51 两轮 R3 达上限，原 PR 先人裁；剩余 meta 解析转专卡 |
| W0 | T0-LESSONS-CMD-DOCSYNC | lessons.ps1 纳入 doc-drift 机检 + archive 子命令同步三处命令清单 | T0-LESSONS-COLD-RECALL | S | GPT-5.6 Terra · high | Sonnet 5 max | 缺口不在那三行文档而在 `_config.ps1` 的 DocSyncMap——它只有 4 个键、不含 lessons.ps1，故闸 14f/17 对它完全沉默。补文档只修一次，补键才让下一次也红。匹配须排除 `_lessons.ps1`（判定核，改它不该要求动用户文档） |
| W0 | T0-LESSONS-BUMP-PLANE | bump 写主检出账本，复发计数不再随卡片 diff 丢失（含 L226/L106 晋升裁断） | — | S | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `edc2770`，PR #129；R3 第 4 轮 pass 零发现——前 3 轮：1 轮 3 条全是基线陈旧假象、2/3 轮各 1 条真缺陷；另有 R3 前 codex 预审再出 2 条真缺陷，合计 8 枚变异全杀） |
| W0 | T0-LESSONS-COLD-RECALL-R3-CLOSURE | PR #51 round-cap 后规范 meta 行锚定解析（TD144） | T0-LESSONS-COLD-RECALL | S | GPT-5.6 Terra · high | Sonnet 5 max | 原 PR 先人裁；只补正文诱饵/缺失/重复/非法 meta fail-closed |
| W0 | T0-ASCII-SHIP-CODES | ship saga/CI gate 的机器断言改锚 ASCII code（TD134 4/6） | T0-CI-MERGE-GATE | M | GPT-5.6 Terra · high | DeepSeek V4 Pro | 只改观测面，不改控制流 |
| W0 | T0-ASCII-CARD-SECRET-CODES | check-cards/check-secrets 状态码迁移（TD134 5/6） | T0-ASCII-SHIP-CODES | S | GPT-5.6 Terra · high | DeepSeek V4 Pro | 状态码 wave 2a |
| W0 | T0-ASCII-REVIEW-ARCHIVE-CODES | review/archive/init 剩余状态码迁移与 TD134 总验收入口（TD134 6/6） | T0-ASCII-CARD-SECRET-CODES | M | GPT-5.6 Terra · high | Sonnet 5 max | 全六卡 merged + 总验收才可 paid |
| W0 | T0-GATE-FIXFORWARD | 许可闸路径比较改 OS 感知 + 发布清单收敛为单一解锁路径 | T0-GATE-HARDENING | M | Sonnet 5 · max | DeepSeek V4 Pro | —（**三张 T0 卡共用 selftest.ps1，须串行**：HARNESS-PERF → 本卡 → LICENSE-SCANNER） |
| W0 | T0-LICENSE-SCANNER | 产出四张批准 classpath 图的 concrete GAV + offline wrapper 基础（TD2 1/5；PR #20 缩 scope） | T0-GATE-HARDENING | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `035df10`，PR #25；23 个 graph mutation、4 图/150 GAV 真实离线扫描、Sol R3 pass；TD2 仍 carded） |
| W0 | T0-LICENSE-POLICY | POM 安全读取、许可分类与 exact-GAV exception（TD2 2/5） | T0-LICENSE-SCANNER | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `04f80fd`，PR #26；30 个 policy mutation、150 GAV strict 离线扫描、verify/CI 绿；R3 两轮修复后达 cap，经用户人裁批准；TD2 仍 carded） |
| W0 | T0-LICENSE-DIAGNOSTICS | scanner 诊断有界、脱敏、不可注入且不改失败语义（TD2 3/5） | T0-LICENSE-POLICY | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `e3b52c7`，PR #27；18 个 diagnostics mutation、graph/policy 回归、150 GAV strict 离线扫描与 verify 绿；R3 round cap 后按记录人裁；TD2 仍 carded） |
| W0 | T0-LICENSE-GAV-BOUNDS | GAV 每段上界，使 exact accepted coordinate 与有界诊断可同时成立（TD135；TD2 4/5） | T0-LICENSE-DIAGNOSTICS | M | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `f61f586`，PR #28；255/256 共享边界、5 个入口 mutation、graph 23/policy 30 回归、150 GAV strict 离线扫描、R3 pass；TD135 paid，TD2 仍 carded） |
| W0 | T0-LICENSE-CI-INTEGRATION | CI warm-up→offline scan、文档同步与 TD2 总验收（TD2 5/5） | T0-LICENSE-GAV-BOUNDS | S | GPT-5.6 Terra · high | DeepSeek V4 Pro | **ready**；fan-in closure，本卡 merge 后才可 paid |
| W0 | T0-LICENSE-CI-INTEGRATION-R3-CLOSURE | PR #49 round-cap 后活跃接线断言与 cold 聚合语义（TD143） | T0-LICENSE-CI-INTEGRATION | S | GPT-5.6 Terra · high | DeepSeek V4 Pro | 原 PR 先人裁；不恢复旧内联 fixture、不重审 scanner 核心 |
| W0 | T0-DEBT-TASK-INVENTORY | 移除 CLAUDE.md 易漂移的静态任务卡库存数（偿还 TD21） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `53eebf9`，PR #13；当前阶段改用活卡/归档真相源，无静态库存；句级分类器覆盖同栏历史计数、`cardboard` 边界与 `active cards`，Sol R3 pass；TD22 另卡） |
| W0 | T0-DEBT-SEEDED-CLOSURE-SCOPE | 让 17cc 变异闭包显式携带断言器（偿还 TD23；TD21 再审前置） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `39ea794`，PR #14；独立 worktree，真实 A/B mutation 在外层 helper 解绑后仍保持 exact marker/exit/control/SHA，Sol R3 pass；未混入 TD21） |
| W0 | T0-DEBT-CASE-PROBE-CLOSURE-SCOPE | 让 17cc case mutation 闭包显式携带探针函数（偿还 TD25） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `b7efd94`，PR #15；File/Command 两宿主 seeded 全绿，删除 capture 与恢复裸调用均命中专属 TD25 诊断，Sol R3 pass） |
| W0 | T0-DEBT-R3-CARD-BASELINE | 让 R3 与范围闸读取同一 pinned-base 任务卡（偿还 TD3） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `6eec97f`，PR #16；R3 卡片、diff、rubric 与 FrozenPaths 均钉到同一不可变 OID；base 卡非普通 blob、读取/探测失败均 fail-closed；Sol R3 pass） |
| W0 | T0-DEBT-ARCHIVED-CARD-PATHS | 修复三个已归档任务卡的失效入站路径（偿还 TD22） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `4ed2ec7`，PR #17；三处具名来源均改指 archive，17hh 覆盖 TA1 真实移动、非普通文件目标与三枚 old-path 变异；Sol R3 pass） |
| W0 | T0-DEBT-ARCHIVE-CARDS-INDEX-GATE | 让归档卡索引成为 verify 可证的真实投影（TD146） | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `f9775f6`，PR #61；exact-byte/BOM/同长度漂移与 verify 接线均有 12e 行为证据，Sol R3 第二轮 pass） |
| W0 | T0-DEBT-MUTATION-RESTORE-SAFETY | 把 L214 的 mutation 还原防丢纪律晋升为必须层（TD147） | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `8648673`，PR #64；L214 ledger→must，必须层 9→10，DoD/verify/R3 pass） |
| W0 | T0-DEBT-MUTATION-BATCH-COUNT | 把 L177 的 mutation 批次条数自证纪律合并进 L17 铁律（TD148） | T0-DEBT-MUTATION-RESTORE-SAFETY | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `3177e54`，PR #67；L177 ledger→must，与 L17 合并后常驻条目仍为 10，第二轮 R3 pass） |
| W0 | T0-DEBT-MUTATION-EVIDENCE-CLASSIFIER | 把 L167 的 mutation 判据分类器纪律合并进 L165 铁律（TD149） | T0-DEBT-MUTATION-BATCH-COUNT | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `1bc6feb`，PR #70；L167 ledger→must，与 L165 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-POWERSHELL-DETACHED-ENCODING | 把 L172 的 detached pwsh 编码纪律合并进 L17/L177 铁律（TD150） | T0-DEBT-MUTATION-BATCH-COUNT | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `bcaa568`，PR #73；L172 ledger→must，与 L17/L177 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-UNICODE-SANITIZER-CATEGORIES | 把 L181 的 Unicode sanitizer 类别纪律合并进 L193 铁律（TD151） | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `83465fc`，PR #76；L181 ledger→must，与 L193 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-UNICODE-SCALAR-TEXT | scalar-aware Cc/Cf 替换与 malformed UTF-16 fail-closed 单一真相源（TD157 1/3） | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `9572270`，PR #99；全量 1,112,064 scalar oracle + over-replacement mutant，R3 pass；consumer 留后两卡） |
| W0 | T0-DEBT-LICENSE-SCALAR-FORMAT | 许可 metadata/diagnostics 接入 scalar helper（TD157 2/3） | T0-DEBT-UNICODE-SCALAR-TEXT | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `9dce927`，PR #104；POM/exception 与 diagnostic 真实入口覆盖增补 Cc/Cf、malformed UTF-16 和普通 emoji，policy/diagnostics DoD、verify、R3 pass；TD157 保持 carded，待 secrets consumer） |
| W0 | T0-DEBT-SECRETS-SCALAR-FORMAT | tracked-sensitive path/purpose 接入 scalar helper（TD157 3/3） | T0-DEBT-UNICODE-SCALAR-TEXT | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（PR #118 · master `4b308c0`；真实 allowlist path/purpose 覆盖全部增补 `Cf`、malformed UTF-16、emoji 保留与双接线 mutation；seeded/verify/R3 PASS；TD157 paid） |
| W0 | T0-DEBT-WINDOWS-PYTHON-UTF8 | 把 L162 的 Windows Python UTF-8 工具纪律合并进脚本工具铁律（TD152） | T0-DEBT-POWERSHELL-DETACHED-ENCODING | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `89d9c5e`，PR #79；L162 ledger→must，与 L17/L172/L177 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-UNSAFE-PATH-DELEGATION | 把 L171 的不安全路径不得委托下游纪律晋升为必须层（TD153） | T0-DEBT-MUTATION-RESTORE-SAFETY | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `577a456`，PR #82；L171 ledger→must，L196/L214 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-GATE-ENTRY-TRUST-BINDINGS | 把 L164 的 fail-closed 新入口信任绑定纪律合并进 L171 铁律（TD154） | T0-DEBT-UNSAFE-PATH-DELEGATION | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `4c16281`，PR #85；L164 ledger→must，与 L171 合并后常驻条目仍为 10，R3 pass） |
| W0 | T0-DEBT-R3-QUOTA-ROUND-CLASSIFICATION | 把 L21 的 R3 配额/轮次证据分类纪律合并进 L205 铁律（TD155） | — | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `e388def`，PR #88；L21 ledger→must，与 L205 合并后常驻条目仍为 10，第二轮 R3 pass） |
| W0 | T0-DEBT-SELFTEST-SNAPSHOT-BASELINE | 让 selftest all 快照钉住调用者 HEAD 与权威 master（TD156） | — | S | GPT-5.6 Sol · high | Codex R3 | **merged**（master `83d6c54`，PR #91；hostile linked/detached all 三分片 PASS，HEAD/base 删除变异均杀红，R3 pass） |
| W0 | T0-DEBT-TEMPLATE-STORE-IMMUTABILITY | 补 TemplateStore 读回列表不可替换的变异自证（偿还 TD13） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `20d028f`，PR #18；三项 fixture 的 MutableList 索引替换命中 UOE，外层 wrapper 删除变异精确 RED，生产 SHA 恢复；Sol R3 pass） |
| W0 | T0-DEBT-RELEASE-CHECKLIST-HASH-GUARD | merged：17ee 编辑警示与删除变异自证（偿还 TD11） | — | S | GPT-5.6 Terra · max | Sonnet 5 max | PR #19 · master `210513d`；RED/GREEN、Sol R3、canonical row/hash 不变 |
| W0 | T0-DEBT-REFERENCE-INTEGRITY | 修复权威文档／脚本中漂移或失效的 TD 交叉引用（偿还 TD16） | T0-GATE-FIXFORWARD | M | GPT-5.6 Terra · max | Sonnet 5 max | **merged**（master `e8bf550`，PR #12；正式 RED 先证旧 TD14 指向必红，Sol R3 首轮拦下两处未覆盖回退与诊断字段缺口，修为六文件九个 source→target 映射、9 枚 code/path/reference 分类变异后次轮 pass） |
| W0 | T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | 单分片与 all 汇总以稳定哨兵点名失败 shard/gate（TD9 1/5） | T0-DEBT-SELFTEST-CRITICAL-PATH | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `b8dee45`，PR #31；稳定 ASCII gate、协议 fail-closed、hermetic/mutation 覆盖、core/verify/R3 绿；TD9 仍 carded） |
| W0 | T0-LICENSE-SELFTEST-DRIFT | 恢复 Gradle diagnostics 的 selftest 回归覆盖并迁移非 vacuous mutation（TD138） | T0-LICENSE-GAV-BOUNDS | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `d00c062`，PR #36；diagnostics 19 mutations、normal seeded、verify、R3 pass；因并发登记冲突从 TD137 重编号） |
| W0 | T0-DEBT-SELFTEST-SKIP-VISIBILITY | 有意 skip 与前置失败裁剪进入确定性执行台账（TD9 2/5） | T0-DEBT-SELFTEST-CRITICAL-PATH + T0-LICENSE-SELFTEST-DRIFT | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `c745015`，PR #33；机器 skip 台账、汇总与 bounded helper，core/verify/R3 PASS；生产 no-git routing 与 mutation 预算按卡拆分） |
| W0 | T0-DEBT-SELFTEST-NOGIT-ROUTING | 有界 fixture mode 证明生产 seeded git-present/absent routing 与 outcome ledger（TD9 3/5） | T0-DEBT-SELFTEST-SKIP-VISIBILITY | M | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `02425dd`，PR #110；完整 130-gate 身份、nonce 子进程与 route inversion mutation；DoD/verify 绿，两轮 R3 finding 修复后人裁；TD9 仍 carded） |
| W0 | T0-DEBT-SELFTEST-CANARY-HARNESS | 修复 post-merge workflow 静默失败与 seeded cold-Gradle 假红（TD158） | T0-DEBT-SELFTEST-SPLIT-PLAN | S | GPT-5.6 Terra · high | Codex R3 | **merged**（master `3748028`，PR #101；post-merge run #32479728740 六 job 全绿，双平台明确 GRADLE-WRAPPER-OFFLINE skip，R3 pass） |
| W0 | T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER | 让 TD9 split checker 在规划卡冷存后仍验证历史合同 | T0-DEBT-SELFTEST-SPLIT-PLAN | S | GPT-5.6 Terra · high | Codex R3 | **merged**（master `3922dd5`，PR #107；bounded live/archive consumer 夹具、verify/R3 PASS） |
| W0 | T0-HANDOFF-REVALIDATE | 续接旧 HANDOFF 前重验阻塞前提、卡状态与更小方案仍成立 | — | S | GPT-5.6 Terra · high | Codex R3 | **merged**（master `17d6cf2`，PR #100；verify/R3 PASS，真实 init/check/SessionStart 三臂 DoD） |
| W0 | T0-DEBT-SELFTEST-MUTATION-BUDGET | parse-once 紧凑 identity inventory，消除数百份整脚本 mutation 副本（TD9 4/5） | T0-DEBT-SELFTEST-NOGIT-ROUTING | M | GPT-5.6 Terra · high | Sonnet 5 max | R3 实测旧形态约 1.6 GB / 500+ CPU 秒；须有机器预算上界 |
| W0 | T0-DEBT-SELFTEST-LOAD-STABILITY | 8.2e 用具名有界预算承受超过五秒的 runner 调度延迟（TD9 5/5） | T0-DEBT-SELFTEST-MUTATION-BUDGET | M | GPT-5.6 Terra · high | Sonnet 5 max | 五卡全 merged + post-merge core 重放后才可 paid |
| W0 | T0-DEBT-MIGRATION-FIXTURE-CLEANUP | PR #47 round-cap 后收敛 Windows migration fixture 清理（TD145） | T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `19e4646`，PR #93；短路径、有界重试、完整诊断与清理终态通过，解除 TD4 R5 阻塞） |
| W1 | T1-SKELETON-E2E | **一次性走通骨架**：建巡检→加一项→拍一张→导出 PDF（真机可见，用完即弃） | T0 | S–M | Opus 5 | Sonnet 5 max | —（人工真机验收） |
| W1 | T1-SCHEMA-CORE ★ | SQLDelight 全 schema+UUIDv7+基线迁移+JVM 测试 | T0 | H | DeepSeek V4 Pro · high | Sonnet 5 max | 冻结前 Opus 5 抽审 |
| W1 | T1-SPIKE-PLATFORM | 真机可行性 ×4：overlay/离线听写/SAF/80 照 PDF 压力 | T0 | H | Opus 5 · max | Sonnet 5 max | —（人工真机验收） |
| W1 | T1-CANON-HASH ★ | canonical JSON+SHA-256+黄金向量 | T1-SCHEMA-CORE | H | DeepSeek V4 Pro · high | Opus 5 | Terra 对向量复算 |
| W1 | T1-TEMPLATE-ENGINE ★ | 模板 schema+加载器+stable-id/版本对齐+按类型枚举 | T1-SCHEMA-CORE | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W2 | T2-ROUTINE-CONTENT | Routine 双语模板 80–120 项+校验测试 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · medium | Luna Max | **merged**（master `00cb5f0`；deepseek-rescue 替代 Luna Max 复核，卡文已同步登记；9 轮 R3 后合并——烟雾报警器声明组连续 4 轮被拦：内容照抄→许可风险→中性标签改写丢事实→措辞被压缩丢法定 or 替代方案，详见 PR #5） |
| W2 | T2-PHOTO-PIPELINE | 照片存储/EXIF 转正(8 向)/哈希去重/导入 | T1-SCHEMA-CORE | M | Sonnet 5 · max | DeepSeek V4 Pro | **merged**（master `2a3fa6b`，PR #6；R3 第 9 轮触 `ReviewRoundCap` 转人裁，用户裁定选项①：合并+剩余两条登记 TD14（跨 FS+DB 共享临界区式真原子性）/TD15（编码字节上界形式证明），均写入卡 `non_goals`；裁后 3 轮各拦到真缺陷并逐一修复——去重复用漏判存在性校验/位图内存无界/EXIF 亚秒精度缺失、日志缺结构化上下文（round 10），以及本卡收尾时最深的一处：`PhotoAssociationRecorder` 补偿逻辑按内容哈希判活，而同一 rel_path 允许不同哈希并存，导致同 photoId 但哈希不同的重试会误删赢家仍引用的证据文件——改按 `selectById(photoId)` 判活 + 安全性不对称（判不清就不删）、`clock.nowMs()` 移入 try、补偿内部查询失败不再顶掉主异常、导入临时文件改逐次调用唯一命名、SAF 流从函数入口起就纳入所有权（round 11，含两次 L205 独立对抗复核）。78 个 JVM 测试、变异逐一击杀+SHA 复核；L236 登记（变异靶点粒度须与被证明的叙事性主张等宽，同 L165 族）） |
| W2 | T2-PHOTO-STREAMING-ENCODE | 流式 JPEG 编码+同流哈希/落盘，偿还 TD15 | T2-PHOTO-PIPELINE | M | Sonnet 5 · max | GPT-5.6 Terra · high | **merged**（master `c8f3b63`，PR #29；生产共用 workflow、4096² 真实暂存文件夹具、故障/顺序变异；R3 round 2 pass；TD15 paid） |
| W2 | T2-FIELD-LEDGER-THEME-R3-CLOSURE | PR #41 round-cap 后 Material 3 遗漏角色显式映射（TD140） | T2-FIELD-LEDGER-THEME | S | GPT-5.6 Terra · high | Sonnet 5 max | **merged**（master `cc4c67c`，PR #55；Material 3 inverse/fixed/surface 与四个 Typography 遗漏角色已显式映射，逐角色合同、变异、verify/core selftest、R3 全绿；TD140 paid） |
| W2 | T2-PHOTO-PROPERTY-DEDUPE | 去重限定同物业，保证按物业备份资产闭包，偿还 TD24 | T2-PHOTO-PIPELINE,T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `7e6ba4f`，PR #116；同物业 SQL 查询 + 路径二次闸 + 历史跨物业只读审计；R3 round 1 pass；TD24 paid） |
| W2 | T2-CAPTURE-CORE | 采集状态机+房间粒度草稿自动保存(:core) | T1-TEMPLATE-ENGINE | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `89d522e`；6 轮 R3 后合并——round 1-2 拦真缺陷（入参未校验致悬空引用/跨记录归属未核对/wear_or_damage 状态回退未清）；round 3 拦原子性（读-判-写跨事务边界）、登记 TD10（跨连接并发契约债，与 T3-FINALIZE 共享，仲裁后禁止评审再以此 block 单连接卡）；round 4-5 拦基线字段范围误读（人裁维持统一解析）、房间序未定、校验不对称、测试严谨度（DTO-only 断言/无时间戳断言/单物业覆盖）；round 6 拦 AdverseStatuses 可变集合强转泄露（同 T1-TEMPLATE-ENGINE 缺陷类）与草稿态基线语义。全程 76 测试、约 30 个单点变异逐一击杀+SHA 复核；L222 登记 SQLite 无 ORDER BY 返回序坑） |
| W2 | T2-PHRASELIB | 双语短语库种子+数据接口 | T1-TEMPLATE-ENGINE | S | DeepSeek V4 Pro · low | Luna Max | **merged**（master `b4655a1`；deepseek-rescue 替代 Luna Max 复核 66 条短语，卡文已同步登记，复核记录同时留在 PR body 与内容测试类头注——L227：R3 只读 diff 不读 PR body；4 轮 R3——round 1-2 拦真缺陷（sort 漏抄可被默认值 0 静默吞掉/suggestFor 对拼错评级值静默放行/5 处双语译文丢推测语气或语法不全/校验顺序注释与实现不符）后撞 ReviewRoundCap，stableId 是否需参与过滤的争议转人裁：卡文修订裁定 v1 契约=纯按状态过滤、stableId 为消费端预留接口缝，评审据此不得再以此 block；人裁后 round 1 拦下我自行加的分类过滤器（与刚裁定的"纯按状态"契约矛盾，已删）、round 2 pass。34 个 JVM 测试、24 枚单点变异逐一击杀+SHA 复核；新登记 L227，L223 追加一例） |
| W2 | T2-ROOM-REPEATABLE | 房间定义带 repeatable 标记：模板契约 + `.sqm` 迁移 + 入库读回往返 | T1-TEMPLATE-ENGINE,T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST | M | DeepSeek V4 Pro · high | Sonnet 5 max | **ready**（TD4 已 paid）；同一冻结 schema 版本评审窗口并入 TD6（Supplement hash 注释）、TD7（历史模板软删读回）、TD8（模板字节 hash 注释），不另开三张碎卡 |
| W2 | T5-BACKUP-FORMAT ★ | 流式加密归档格式+manifest+防篡改/错口令测试 | T1-CANON-HASH | H+ | Opus 5 · max | Sonnet 5 max | **merged**（`efedcfb`，R3 第 4 轮 pass，两次人裁：分块 AEAD / CD 非规范性，见卡「格式评审记录」）；Terra 未接线 → DeepSeek V4 Pro 独立复读代替（L26），记录在 PR #9 |
| W3 | T2-CAPTURE-UI | Compose 走查界面：大按钮/备注/短语/听写/两级拍照 | T2-CAPTURE-CORE,T2-PHOTO-PIPELINE,T1-SPIKE-PLATFORM | M | Sonnet 5 · max | Terra | — |
| W3 | T2-PHOTO-QUALITY-PROFILES | 新照片 Low/Medium/High/Extra High；默认 Medium | T2-PHOTO-STREAMING-ENCODE | M | Sonnet 5 · max | GPT-5.6 Terra · high | **merged**（master `703af59`，PR #30；四档持久设置、双管线单次快照、转正后按比例缩小、动态位图峰值预算；同设备 Android 生产编码 16 输出总体大小单调；R3 round 2 pass；TD131 paid） |
| W3 | T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER | `.jpg.pending` durable lease + 24h WorkManager 回收无行/软删照片孤儿（TD14） | T2-PHOTO-PIPELINE | M | GPT-5.6 Terra · max | Sonnet 5 · max | **merged**（master `4971f1b`，PR #32；内部 `filesDir/media` + `myinspection.db` 固化为唯一运行时组成；目录级掉电顺序拆至 `T2-PHOTO-DIRECTORY-DURABILITY`） |
| W3 | T2-PHOTO-DIRECTORY-DURABILITY | marker 祖先目录 fsync + JPEG 删除 durable 后再清 sidecar（TD137） | T2-PHOTO-ORPHAN-CLEANUP-SCHEDULER | S | GPT-5.6 Terra · high | Sonnet 5 · high | **merged**（master `e9c56b9`，PR #35；完整祖先目录 fsync、补偿/worker JPEG 删除 durable 后才清 sidecar；TD137 paid） |
| W3 | T5-MEDIA-ARCHIVE-CONTRACT ★ | 本机物理状态+PDF 回执+逐资产已验证备份回执 | T0-DEBT-MIGRATION-SNAPSHOT-ALLOWLIST,T2-PHOTO-PROPERTY-DEDUPE,T5-BACKUP-FORMAT | H | Opus 5 · max | Sonnet 5 max | schema 版本评审；不改备份 format_version |
| W3 | T3-REPORT-COMPOSER ★ | 纯 Kotlin 布局引擎：分页/双语配对/哈希页脚+黄金布局树 | T1-CANON-HASH,T2-CAPTURE-CORE | H+ | Opus 5 · max | Sonnet 5 max | — |
| W3 | T3-REPORT-COMPOSER-R3-CLOSURE | PR #39 round-cap 后六项 renderer-ready 布局收口（TD139） | T3-REPORT-COMPOSER | M | DeepSeek V4 Pro · high | Sonnet 5 max | 原 PR 先人裁；本卡不重审已闭合事项 |
| W3 | T3-FINALIZE | finalize 事务+只读强制+Supplement 哈希链 | T1-CANON-HASH | M | DeepSeek V4 Pro · high | Sonnet 5 max | **merged**（master `a5a71ed`；PR #7，15 轮 R3 后合并，48 测试）——唯一悬点（DbCompletenessChecker 逐项 allowed_statuses 重验，评审三度提出，round 5/12/13 均按 mint-point/L220 驳回）触发两轮争议转人裁，用户裁**选项 A**（实现该检查，防御纵深）：新增 `itemsWithDisallowedStatus`；裁后评审又拦两条真发现——① 删掉自己此前引入的重复权威 `classifyAdverseness`/`Adverseness`（ADVERSE/NOT_ADVERSE 从未被消费），简化为 `isInDomain` 纯域成员判定；② 只读强制此前只证过冻结 SQL 谓词，补一条经真实 `InspectionRepository.setItemStatus`/`setWearOrDamage` 的集成测试。TD5 → paid（本 PR 为偿还指针） |
| W4 | T3-PDF-RENDERER | :app PdfDocument 渲染+CJK+逐页内存+四档导出质量+双版本 | T3-REPORT-COMPOSER | H | Sonnet 5 · max | Opus 5 | 默认 Medium；High 为证据归档建议；不承诺绝对 MB |
| W4 | T3-HISTORY-COMPARE | 历史条(上次状态/滑动)+ghost overlay 集成+双轨基线 | T2-CAPTURE-UI,T1-SPIKE-PLATFORM | H | Sonnet 5 · max | Opus 5 | — |
| W4 | T4-COMPLIANCE-ENGINE ★ | 配置驱动合规引擎+阻断 API+NZ DST 边界测试 | T1-SCHEMA-CORE | H | Opus 5 · high | DeepSeek V4 Pro | Terra 对规则夹具与需求逐条比对 |
| W4 | T4-COMPLIANCE-ENGINE-R3-CLOSURE | PR #43 round-cap 后配置驱动/改期身份/拒绝与不可变证据收口（TD141） | T4-COMPLIANCE-ENGINE | M | GPT-5.6 Terra · high | Sonnet 5 max | 原 PR 先人裁；只接第 2 轮四项 finding |
| W4 | T5-BACKUP-IO | SAF 目的地+内容回读验证回执+自动导出+恢复先试跑后落刀 | T5-BACKUP-FORMAT,T2-PHOTO-PROPERTY-DEDUPE,T5-MEDIA-ARCHIVE-CONTRACT | H | Sonnet 5 · max | Terra | Google Photos 状态不算回执；不接云账号 |
| W5 | T3-E2E-CORE | JVM e2e 闭环接 verify 闸门 2（$gate2Pending=false） | T3-FINALIZE,T3-REPORT-COMPOSER,T2-ROUTINE-CONTENT | M | DeepSeek V4 Pro · high | Sonnet 5 max | — |
| W5 | T4-NOTICES | 48h 通知双语文本+一键复制+送达存档(全文快照) | T4-COMPLIANCE-ENGINE | M | DeepSeek V4 Pro · high | Terra | Luna Max（通知文本） |
| W5 | T4-SCHEDULE | 13 周节奏提醒+本地通知 | T4-COMPLIANCE-ENGINE | S | GPT-5.6 Terra · medium | DeepSeek V4 Pro | — |
| W5 | T5-RETENTION | 租客数据保留期+一键清理 | T1-SCHEMA-CORE | S | DeepSeek V4 Pro · medium | Luna Max | **merged**（master `60cee85`；5 轮 R3（两次撞 ReviewRoundCap=2，均经人裁 reset）——round 1 拦法律措辞混淆（联系方式清理期 12 个月被误述为 RTA s123A 本身规定的数字）+ UI type-to-confirm 对空 tenant_name 永久锁死清理按钮；round 2（撞 cap）拦措辞残留（改写后仍暗示"无限期保留系 RTA 要求"）+ 哈希不变量测试造假（DRAFT 巡检+未持久化照片，未验证真实 finalize 记录）+ purge() 自身到期边界无测试覆盖，人裁：findings 属实且卡内可修 → reset；round 3（reset 后首轮）拦 civil-calendar 时区错用（`ZoneOffset.UTC` 误引"存储用 UTC 入库"规则算日历月，应循 ADR-0004 先例改用 Pacific/Auckland + DST 边界测试），人裁 reset；round 4（再撞 cap）拦 5 处测试盲区（sortedBy 排序/isPurgeable/`Collections.unmodifiableList`/`months` 覆盖参数均无证伪测试、UI "12 个月"字符串未溯源常量），人裁：全部属实 → 定裁修法（删 `months` 参数/补 4 处测试/UI 单源化）+ reset；round 5 pass。20 个 JVM 测试、8 处单点变异逐一击杀+SHA 复核（其一因误用 `.clear()` 而非 `.set()` 产出假证明，识破后重做）；新登记 L231（civil-calendar 计算时区与存储格式规则混淆）、L232（产品策略数值与法条数字巧合相同时的措辞混淆）；TD13（`TemplateStore.read()` 同款 `Collections.unmodifiableList` 缺自证测试） |
| W5 | T5-LOCAL-MEDIA-RETENTION | 每物业保留最近 1/3/5/10/Always 次全尺寸照片；预览确认+安全归档+回填 | T5-BACKUP-IO,T3-PDF-RENDERER,T3-HISTORY-COMPARE,T5-MEDIA-ARCHIVE-CONTRACT | H | Sonnet 5 · max | Opus 5 | 默认 3；30 天宽限；只删本机字节，不删记录/PDF/备份/云端 |
| W6 | T6-TEMPLATES-REST | Ingoing/Exit/Annual 内容+Exit wear/damage+配对约束 | T2-ROUTINE-CONTENT,T3-HISTORY-COMPARE | M | DeepSeek V4 Pro · medium | Luna Max | **Luna Max 全文复核** |
| W6 | T6-HHC | Healthy Homes 五项子模块+合规快照输出 | T3-PDF-RENDERER | M | DeepSeek V4 Pro · high | Terra | — |
| W7 | T7-REMEDIATION | LLM 建议：mock 优先+仅房东版+措辞边界+免责声明 | T3-PDF-RENDERER | M | Sonnet 5 · max | Opus 5 | Sol 安全面重点评审 |
| W7 | T7-SMOKE-POLISH | 真机全流程冒烟+微修捆绑（清单产出 docs/SMOKE-CHECKLIST.md） | 全部 MUST + T7-REMEDIATION（收官卡，不并行） | S | Sonnet 5 · medium | DeepSeek V4 Pro | — |

★ = 冻结点卡：合并后其产出登记 `scripts/_config.ps1` FrozenPaths，改动走版本评审。
并行窗口速查：同波仍须服从 `depends_on` 与 allow_paths；媒体路径关键支线 = STREAMING→QUALITY，DEDUPE→ARCHIVE-CONTRACT→BACKUP-IO→LOCAL-MEDIA-RETENTION；主闭环关键路径仍约为 T0→SCHEMA→CANON→COMPOSER→PDF→E2E。

## Scaffold 0.38 selective backport

本图只表示真实产物依赖；虚线表示共享 `scripts/selftest.ps1` 的写资源冲突。资源冲突要求串行合并或在后合卡重放验收，**不**把独立目标伪造成 `depends_on`。

```mermaid
flowchart LR
  A[T0-R3-DIFF-BUDGET] --> A2[T0-R3-DIFF-INPUT-TRUST]
  A --> A3[T0-R3-MEASURED-OID-BINDING]
  A --> A4[T0-CARD-ACCEPTANCE-FIELD]
  A --> A5[T0-R3-FLOW-ENUM-SYNC]
  A --> B[T0-CI-MERGE-GATE]
  B --> E[T0-ASCII-SHIP-CODES]
  E --> F[T0-ASCII-CARD-SECRET-CODES]
  F --> G[T0-ASCII-REVIEW-ARCHIVE-CODES]
  C[T0-HARNESS-SUBTRACTION-PROTOCOL]
  D[T0-LESSONS-COLD-RECALL]
  D --> H[T0-LESSONS-COLD-RECALL-R3-CLOSURE]
  A -.->|selftest write conflict| A4
  D -.->|selftest write conflict| A
  D -.->|selftest write conflict| B
  D -.->|selftest write conflict| E
```

- 当前状态（2026-08-23）：`T0-R3-DIFF-BUDGET` 已按自身教义拆成 3 张（度量 / 输入可信 / 提交身份），PR #53 的实现分属三张卡，各自独立评审；`T0-LESSONS-COLD-RECALL` PR #51 已修完第 2 轮 finding 待评审。`T0-CI-MERGE-GATE` 的依赖歧义已收口（2026-08-23）：卡内 `depends_on: [T0-R3-DIFF-BUDGET]` 为准（CLAUDE.md「状态以卡为准」），本表该栏与上图边已同步；此前本行建议的 `T0-R3-MEASURED-OID-BINDING` 未被采纳——OID 绑定与合并闸无产物依赖。
- 推荐执行宽度 2：文档协议可与任一实现卡并行；所有写 `scripts/selftest.ps1` 的卡合并宽度 1。
- 上游只提交通用建议，不要求其修本仓：[#163 TD→1–N cards](https://github.com/Asun28/claude-devops-scaffold/issues/163) · [#164 actual diff budget](https://github.com/Asun28/claude-devops-scaffold/issues/164) · [#165 read-only scaffold diff](https://github.com/Asun28/claude-devops-scaffold/issues/165)。

> **调研已回流**（docs/research/synthesis.md + 3 篇深挖）：官方 NZ 巡检表成为 Routine 模板骨架；二值主评级 UI（存储枚举不变）、照片隐私标记、物业级条目抑制、封面卷积/出处页脚等已并入相应卡上下文包；ghost overlay 确认为全品类空白（唯一差异化确认）。

## 用户已定（2026-08-15 签认，下列为**执行契约**，执行模型按此做，勿再问）
1. ✅ **ADR-0002 已签认**：备份 = app 私有存储 + SAF 加密归档导出；需求 §11 那处[定]以 ADR-0002 为准。T5 线解锁。
2. ✅ **房产现状 = 2 套以上，部分在租**。两条硬后果：
   - **既有租约补不回 Ingoing** ⇒ schema 必须支持「把某次 Routine 指定为该 tenancy 的基线」（详见 T1-SCHEMA-CORE 上下文包新增段），Exit 对照 `tenancy.baseline_inspection_id` 而非「必有 Ingoing」的假设；
   - 多物业是**常态不是边缘**：物业切换/按物业筛选是 v1 面（T2-CAPTURE-UI 与 T5-BACKUP-IO 的「按物业导出」照 ADR-0002 已含）。
3. ✅ **租客数据保留期 = 租约结束后 12 个月**（对**联系方式**）：`tenant_name`/`contact` 到期一键清空（置 NULL，**不删行**——证据链要留）。照片记录、报告、哈希和加密备份继续保留；本机全尺寸照片字节另按第 9 项归档。落地卡 = T5-RETENTION。
4. ✅ **年检评级 5 态**（NO_ISSUE/MONITOR/MAINTENANCE_ITEM/SIGNIFICANT_DEFECT/NOT_APPLICABLE）——用户未否决，按 5 态做。
7. ✅ **不做** Condition/Cleanliness 全量双刻度：v1 = 单刻度 + Exit/Ingoing 房间级清洁条目（已在卡内）。
8. ✅ **不做** 缺陷责任方/费用字段：v1 只保留 Exit 的 `wear_or_damage` 三态。
9. ✅ **照片/PDF 空间策略（2026-08-19）**：照片存储质量与 PDF 导出质量各有 Low/Medium/High/Extra High，均默认 Medium；本机全尺寸照片按每物业最近 `1/3/5/10/Always` 次已完成巡检保留，默认 3。只有 exact `.mibk` 内容回读验证、PDF 完成、30 天宽限和保护引用检查全绿后才进入人工预览清理；不删记录/PDF/哈希/音频/备份/云端，Google Photos 状态不算验证回执。落地卡 = T2-PHOTO-QUALITY-PROFILES、T3-PDF-RENDERER、T5-MEDIA-ARCHIVE-CONTRACT、T5-BACKUP-IO、T5-LOCAL-MEDIA-RETENTION。

> **未来云备份（不建卡、不进 v1）**：保留 provider-neutral `ArchiveStore` 与 opaque destination/object/version 回执。若以后做 S3 付费服务，另走账号/订阅/威胁模型 ADR；客户端先加密 `.mibk`，后端签发短时授权，APK 永不持有 AWS 长期凭据。云备份不自动等同多设备同步。

> 3/7/8 是 **T1-SCHEMA-CORE 冻结前**必须落定的项，现已落定 ⇒ 该卡可开工，无待定阻塞。

## 仍待定（不阻塞当前波次）
5. **s48(2)(c) 复检语义**：向 Tenancy Services/持牌人士确认「查验已约定维修」是否占 4 周限额；确认后只改配置（ADR-0004），不改码。
6. **Remediation 用哪家 LLM/key**（T7 前定即可；接口做成 provider 可换）。

## 已由 3 方讨论定稿（原[待] → 已定）
技术栈原生 Kotlin+Compose（ADR-0001）· 租赁评级 4 档 · Exit 独立 wear/damage 三态且仅差异项 · 两级拍照规则（N_A 不逼拍照）· UI 英文单语 + 报告平行双语 · finalize 锁定+哈希页脚 · SQLDelight/自研 UUIDv7/canonical 规范（ADR-0003）。
