# Harness 评审：随模型变强而**做减法**

> **动机**（Anthropic《Harness Design for Long-Running Agentic Applications》）：*"Every component in a harness encodes an assumption about what the model can't do on its own, and those assumptions are worth stress testing."*
>
> 每一道闸门（worktree / TDD / Codex 评审 / guard-frozen / handoff / lessons / CI …）都**编码了一个假设**：「模型自己做不好 X，所以加这道闸」。模型变强后部分假设会失效，届时**多余的脚手架反而降低产出**（过度规划/分解会让强模型更**保守、更少特性**）。故需要一个**定期做减法**的仪式，而不是只增不减。

## 双向：减法（删旧闸） + 主动刷新（纳新）
> self-improvement 不是只往下删。本评审是**双向**的——既给失效闸门**做减法**，也主动**搜更优工具/方法做加法/替换**（L26：方法论优先于工具，工具可随时换更优的）。两极同属一个仪式，别只做一半。
- **减法极**：闸门假设失效 / 长期零命中 → 删/降级（下方主流程）。
- **刷新极**：定期问「实现某能力的更优工具/方法出现了吗？」→ web 搜当前最优 → 按本仓硬规则（许可/离线/确定性/Windows）评估 → 纳入即记经验库 + 把被替代者记一条 ADR。**只换实现、不破坏方法论定义**（定义层工具无关）。心跳 `harness-refresh` 探针（judgment 经验累积达门槛）是它的自动触发点。

## 何时触发本评审
- 升级了主力模型（如 Opus 4.x → 5.x）后；
- 某道闸门连续 N 张卡都「零命中」（从不 block / 从不拦截）时；
- **心跳 `harness-refresh` 探针报信**（judgment 经验累积达门槛——该双向复审了）；
- 季度例行（与 `scripts/lessons.ps1 check` 的必须层封顶一起做）。

## 方法：一次只动一个，量化前后
> Anthropic：*"Remove one component at a time, measuring impact rather than radical restructuring."*

逐闸门走一遍下表；**一次只移除/弱化一道**，用真实卡量化影响，再决定保留/删除/降级。

| 闸门 | 它假设模型做不到 | 该假设现在还成立吗？（证据） | 命中率 / 价值 | 处置 |
|---|---|---|---|---|
| R1 worktree 隔离 | 并行/隔离改动不互相污染 | | | 保留 |
| R2 TDD（先写失败测试） | 不写测试就过度自信 | | | 保留 |
| R3 Codex 第二评审 | 自评会自我夸奖（见 QUALITY-RUBRIC §0） | | | 保留 |
| guard-frozen | 会顺手改冻结契约 | | | 保留 |
| handoff 三件套 | 跨 session 会丢上下文 | | | 保留 |
| 规划 harness（plan-forge 拆卡） | 一句话需求会跑偏 | 强模型可独立扩写规格？ | | **重点复审**：强模型下可简化分解粒度 |
| lessons 必须层（Tier1） | 会重导工具链坑 | 坑是否已被机械守卫覆盖（见 enforced_by） | | 有机械守卫的可降回按需层 |

## Quantified subtraction protocol（量化减负协议）
> 上表评的是**闸门**；本协议管的是每轮常驻的**指令文本**，包括 `CLAUDE.md` 铁律、硬规则、命令清单和其他常驻负载。一次只处理一组，量化且可回滚；减负只能是迁移，不能是丢失。

1. **枚举候选组与 backing。** 一组只含一条常驻指令、一段 lesson 或一个小节。逐组记录支撑它的机器闸（selftest 子闸 id / 代码内 fail-closed guard）和/或承载细节的权威文档；无法指出 backing 的内容不是候选，因为它可能是唯一防线。
2. **删除前验证迁移目标，并先盘点机器锚点。** 打开每个指针目标，确认被减内容确实存在于对应章节/锚点；目标未核实就删除属于丢失。动手前列出文件内被机器断言的字面量：计数值、`.Contains` 检查、标题、逐字同步段和 lesson id 引用；这些锚点保持原样。
3. **One group per commit。** 每组一个 commit，独立 revert 就是回滚机制；禁止把无关组捆在同一提交。
4. **完整重验。** 至少在最终树跑完整 gate suite；filtered selftest 只能用于中途诊断，不能充当最终验收。若动了 Tier-1 文本，额外跑 lessons checker。再选 2–3 个近期真实 ask/card 回放，在 PR/card 记录 TaskId，并确认 agent 仍能沿指针到达迁移后的细节。
5. **裁决并记账。** 全绿且无内容丢失，才保留该组删减，并在 PR/card 记录逐组 byte/line delta（行数作为 token proxy）。一旦质量退化，立即 revert 该组并标记为 **load-bearing**，同时记录原因，避免下轮重复尝试。

不可放宽的边界：
- 不删没有机器闸兜底的行为红线（如「执行边界」中的「绝对禁止」）；此时文本本身就是唯一 enforcement。
- 上一步盘点出的机器锚点必须保留。
- 目标形态是**索引而非零文本**：每项压缩后仍保留一行规则和一个指针，指针目标是正文的单一真相源。
- 这是带量化核心的判断仪式，不建自动删文脚本；可复现性来自逐组 delta 与 revert 记录。

## 判据
- **命中率**：一道闸门若长期零命中，要么假设已失效（删/降级），要么它在**沉默地防护**（保留）——用证据区分，别凭感觉。
  证据来源 = **效果账本**（`_local/effectiveness-ledger.jsonl`，TD2）：`task.ps1 ship` 各确定性闸（DoD/许可/密钥/R3）真拦截即记一行，心跳 `effectiveness` 探针汇总**各闸拦截计数**并标出「0 拦截」候选。把本判据从「凭感觉」升级为「凭账本」——但拦截数只是**分子**：denominator（ship 次数）靠 git/PR history 人工判（TD9 曾试把分母也记进账本算逐闸比率，被 max-effort review 否决——逐闸比率会因「下游闸只在前闸放行时才到达 / 旧账本无分母致比率 >1 / 重跑稀释」而误导，见 ADR `0003`）；0 拦截仍须判断：可能是**沉默防护**（如没人去碰冻结物，正说明 guard-frozen 在威慑），不是自动删除信号。
- **机械化优先**：能被确定性脚本（selftest 闸 / lint / check）覆盖的「软约束」，应从「上下文里的提醒」升级为「机器闸门」；反之，已被机器覆盖的提醒可从必须层删除（见 `docs/LESSONS.md` 的 `enforced_by` 机制）。
- **做减法要留痕**：移除/降级任一闸门，记一条 ADR（`docs/adr/`）说明「基于什么证据移除了什么假设」，便于回滚。

## judgment-feed：方向/决策类经验汇入本评审
> Anthropic《Recursive Self-Improvement》的 judgment scaffolding：**记录决策本可更优的时刻、随模型变强检验方向品味是否提升**。

**judgment 类经验**（`scripts/lessons.ps1 add -Kind judgment`，见 `docs/LOOP-ENGINEERING.md`）不走 `enforced_by` 机械守卫（方向启发式难机检），而是汇入本评审：升级模型后逐条复审「这个方向失手现在还会发生吗？」——

| judgment 经验 id | 当时的次优方向 | 更好的启发式（rule） | 升级模型后仍会犯？（证据） | 处置 |
|---|---|---|---|---|
| _（promote judgment 经验时登记到此）_ | | | | |

- **仍会犯** → 保留该经验（方向品味未达），考虑加一道轻量提醒或在 rubric 补一维。
- **不再犯** → 模型方向品味已提升，可把该经验降回总账层（做减法）——与下方闸门减法同理。

## skill 减法/纠偏：never-fired 与 undertrigger
> 对应 Anthropic skills 实践的「度量」一环:发现热门 skill 与**从不触发**的 skill。本仓刻意**不接** PreToolUse 遥测钩子(避免全局钩子的破坏面),故走**人工周期复审**,与本评审同频。

逐 skill(`.claude/skills/*`)过一遍:
- **从不触发(unused)**:这一周期你本该用、却没被唤起的 skill → 多半是 **description 触发契约写弱了**(undertrigger):补触发词 / 「何时用」,走 `skill-creator` 改;或确属冗余 → 退役(删卡 + 同步三处索引,见 `.claude/skills/skill-creator/SKILL.md`)。
- **过度触发(overtrigger)**:抢了不该它管的活 → 收紧 description 的「何时不用」负向边界。
- **机制说明**:可靠遥测需要一个 PreToolUse 钩子记录每次 skill 调用(Anthropic 官方做法);本仓默认不引钩子,改为此处人工复审。若日后愿接,只需一个**只读**记录钩子(append 一行到 `_local/skill-usage.log`,gitignored),即可把本节从「凭记忆」升级为「凭日志」——是纯加法、不改任何现有闸。

## 语义一致复审（semantic coherence — 结构机检之外的判断）
> selftest 闸 11/14/16 只机检**结构**（交叉链接存在、计数一致、L-id 存在），丰富的结构检查恰好遮住**语义从不被检查**这一缺口。对单人外化心智模型摊在多份文档/多个 skill/数十条经验上的元层，**语义漂移**（各处说法悄悄互相矛盾）是主要的维护半衰期失效面，且**无任何机器闸**覆盖（机检语义本就是过度设计，本仓会拒）。
> 故做成与本评审同频的**人工判断仪式**——不是闸门，发现矛盾即记一条 judgment 经验 / 开卡修，不强求当场全改。

逐项扫一遍：
- **rubric ↔ 失败类**：`QUALITY-RUBRIC.md` 的判定维度，是否仍覆盖 `LEDGER` 近期累积的失败类？新坑反复出现却无对应维度 → 补一维。
- **铁律/硬规则互不矛盾**：`CLAUDE.md` 经验铁律（Tier1）与「改动时的硬规则」两两之间，有没有给出**冲突指令**的（如一处要「极简/删」、另一处要「必加某结构」）。
- **交付链无缝无重叠**：交付链表是否仍**干净划分**工作（无两链争同一职责、无该有的环节落空）？增删链后尤其要看。
- **命名/接口口径不漂移**：`CLAUDE.template.md`「命名约定 / 代码与接口命名」与各 linter、各 skill 指针说的是否一致（结构由闸守，**口径**靠这里）。
- **单一真相源未被双写**：同一约定是否在两处各写了一份**正文**（而非一处正文 + 一处指针）？双源即漂移温床。

> 与结构闸的分工：闸 11/16 保证「指针指向的文件/经验**存在**」；本仪式保证「指针指向的内容**对得上**」——存在性可机检，语义对不对只能人/强模型判断。

## 每轮上下文预算（per-turn context budget）
> 另一类该做减法的对象是 harness **每轮常驻塞给 agent 的指令负载**（`CLAUDE.md` + skill 描述 + Tier1 经验 + 钩子 banner——各部分单看都「才几百 token」但求和从未被预算）；注意力稀缺论证见 `docs/LOOP-ENGINEERING.md`「上下文当内存」。

本评审周期量一次、设个软预算：
- **量/减**：粗估常驻 token（`CLAUDE.md` 字数 + 注入 skill 描述总量 + Tier1 条数 + 钩子每轮输出）记一条 judgment 经验逐周期对比；
  **非每轮必需**的材料移到 `paths:` 懒加载 `.claude/rules/`（只在 Read 到匹配文件时注入）或按需 skill——skill 目录速览、交付链大表是首选候选。
- **钩子噪音（TD88 W2 已收窄，2→1 议题仍待决）**：原第三个 Stop 钩子 `comprehension-reminder.ps1` 只复述 `CLAUDE.md` 执行边界「完成与词义」+ task-loop 4.7 fresh-context 证据审计，属纯重复，已随 TD88 W2 删除；现存 2 个 Stop 提醒（lessons / handoff）各有不重叠的节律语义（策展 vs 离场校验），暂不合并。若这 2 个的噪音仍扰，再议合并为一个轮转调度（2→1），评审时权衡节律损失，不在常规改动里顺手做。

## 安全税周期复审（security-cadence — 无人值守回路的攻击面）
> 来源：loop-engineering「security tax」（`docs/LOOP-ENGINEERING.md` 安全税节，TD10 → ADR `0002`）。无人值守的回路也是无人值守的攻击面，其防护**会随时间腐烂**；故把周期重审**挂在本仪式既有的人工 cadence**，而非建无人值守写操作自动化或需要新状态基建的探针（守边界 + YAGNI）。

每周期过一遍（纯人工/agent 判断、非闸；命中即开卡或记 ADR）：
- **权限范围重审**：回路/agent 的写权限是否仍是最小集？被「就加一个」临时加上的写权限，距上次复审是否已 >~30 天没再看？
- **灰区权限疲劳（上下文感知权限分类器）**：无人值守回路的 ask 清单会累积「permission fatigue」——问得太频，放行就退化成不看内容的机械点头（本身即攻击面）。评估一道**上下文感知的权限分类器**（当前工具 = Claude Code `auto` mode）：一个独立分类器模型在动作执行前复核，拦截「越出你请求范围 / 指向未识别基础设施 / 由 Claude 读到的敌意内容驱动」的调用（官方并明确 *"auto mode reduces permission prompts but does not guarantee safety"*——故它只是**补**层，不是安全保证）。定位是**补** fail-closed 守卫覆盖不到的**灰区**，**绝不替代**确定性 fail-closed 守卫（`_guard.ps1` / 账号守卫 / 冻结路径——硬红线仍走确定性闸，比分类器强）。工具无关（L26）：标准是「灰区加一层上下文感知分类」，auto mode 只是当前默认实现；换/去掉记一条 lesson/ADR。**可用性/启用条件因 provider·版本·模型而异且随版本演进——一律以官方 `permission-modes` 文档 <https://code.claude.com/docs/en/permission-modes.md> 为准，别在此处钉死会过期的启用矩阵，也别假设分类器一定在场**：不可用时回路仍只有 fail-closed 守卫兜底（这正是「补而非替代」的另一面）。
- **skill 来源/注入审**：本周期新 vendor/装的第三方 skill，是否审过来源？skill 描述里的 prompt 注入是回路的注入向量——走 `skill-creator` 的许可/来源洁净约定。
- **闸会腐烂，抽检它**：挑几个回路开的 PR，核对当初放行的测试**真**能抓你在乎的失败类（与理解债闸同源——别盲信绿灯）。
- **既有机械兜底**：`scripts/check-secrets.ps1`（防泄露）· 安全评审（`security-review` skill）· `scripts/check-licenses.ps1`（许可）。本节只补**周期性重审**这一无机检维；最可机械化的子片（vendor 期 skill 来源溯源）留待复发再开卡。

## 评审者须在自改回路之外（evaluator outside the self-improvement loop）
> 来源：Lilian Weng《Harness Engineering for Self-Improvement》(2026-07)——系统瓶颈 #5「reward hacking」：
> > *"Self-improvement loops optimize the given signals … evaluators need to sit outside the optimization loop."*
> 及其设计原则「permission control and security layers should live outside self-improvement loops」。

本仓自身就跑一条**自改回路**（`triage` 心跳 `harness-refresh`/`effectiveness` 探针触发、乃至无人值守的 regression-sweep 回路）——**它改的正是脚手架自己**。于是有一类特殊风险：回路为让某改动「过闸」，去**弱化它自己的成功判据**（`selftest` 闸 / `QUALITY-RUBRIC` 维度 / `check-cards` 规则 / `review.ps1` 裁决逻辑）。这时绿灯是**被改出来的、不是挣来的**（Goodhart / reward hacking），且弱化往往看着人畜无害、能静默随功能卡 ship。

- **判别**：一次改动**同时**动了「被改物」和「判它对错的评审者」（闸/规则/rubric/裁决器），即 evaluator 落进了它评判的 optimization loop 内。
- **非对称守则（核心）**：
  - **加严判据（GROW）可随手做**——补闸、加维、把软约束升成机器闸，随时欢迎（`enforced_by` 机械化本就是方向）。
  - **放宽/删闸/降维（PRUNE）只走做减法正门**：本评审「一次只动一个 + 真卡量化 + ADR 留痕」（见上「方法」「判据」节），**绝不**由自改回路顺手塞进一张功能卡。让闸更松要审，让闸更严不用。
- **别把评审者变更静默捆进它要放行的卡**：对成功判据本身的任何修改，**显式**标给回路之外的第二独立模型（codex R3）+ 人复核；`selftest` 绿 ≠ 已评审（L50）；别在同一 PR 自写例外放行自己的改动（循环自批准，R3 必拦，L53/L74 已实证）。
- **衔接**：本节是 L50（selftest≠评审）/ L53（循环自批准）两个具体实例的**一般化**——把它们升成通则，与下方「安全税周期复审」同属**回路完整性**一类：心跳负责「发现该复审了」，本节保证「复审自己时别被自己糊弄」。

## cookbook agent 模式：已落地在何处（provenance / 纳新对照表）
> 来源：Anthropic `claude-cookbooks`（MIT）的 `patterns/agents`（五种基础 agent 模式）与 `managed_agents/CMA_plan_big_execute_small.ipynb`；后者两个非显然坑已入账 **L107**（核验闸只覆盖闸内对象，分解/前提在闸外仍未验证）/ **L108**（按自然工作单元拆、别按最小事实粒度；协调者零 spawn = 本不该编排）。

本节是上文「刷新极/纳新」的**对照清单**：上游出现新模式时先核这张表——**已落地的别重复搭**（YAGNI），真需要的按**链 / 工作流**接（非常驻角色班子）。五种基础模式本仓**皆已收敛实现**，形态是链与工作流而非 `.claude/agents/` 命名代理 roster：

| cookbook agent 模式 | 本仓已落地锚点 |
|---|---|
| prompt-chaining（提示串联） | R1–R5 单卡闭环（`scripts/task.ps1`）· 想法→计划漏斗（1-brief→2-options→3-plan） |
| routing（路由） | 模型档位分工（Opus 想 / Sonnet 做 / Fable 长自主，见 `CLAUDE.md`——按任务类型择模型档）· `route-new-work` 钩子命中启动语（`根据脚手架`）后提示先与用户确定 T0/T1/T2 档位再按深度走 |
| parallelization（并行·分段/投票） | `Workflow` 工具 `parallel()`/`pipeline()` 对**预定固定**清单并行 · `plan-forge.mjs` 的**固定** 8-lens 并行审计 + 每条发现派 3 固定裁判投票 · task-loop 并行窗口（每卡一 worktree） |
| orchestrator-workers（编排者—工人·动态委派） | `Workflow` 工具 `agent()` 对**运行时才发现**的工作清单动态扇出（先侦察得工作项、再逐项派 worker，非预设集）· `plan-forge.mjs` 的 Decompose 据计划**动态**投影可变规模卡图 → task-loop 逐卡派 worker 子代理 |
| evaluator-optimizer（评审者—优化者） | R2 TDD（失败测试=评估者 ↔ 实现=优化者，迭代至绿）· R3 codex 第二评审 → 按裁决 `reasons` 修 → 重评审的 fix 循环（本卡此刻正在此循环内） |

**结论（判断，非闸）**：不建**常驻命名代理 roster**——角色按 **phase / 模型档位**路由、按需派**临时**子代理（`Agent` 工具 / 工作流内 `agent()`）用完即弃，依据 L26（能力按方法论定义、工具无关）与 L108（常驻班子 = 固定 overhead，拆过头反更贵）。唯一**长驻**角色是第二独立评审者（当前 = codex R3），因它是方法论不变量且须**在自改回路之外**（见上节），属结构而非便利。
**哨兵**：想加一整套常驻班子前先问这张表——多半已被某条链覆盖；真缺的先小规模试跑、比对 token 账单（L108）再放大。

## 与其它系统的衔接
- 必须层封顶（`LessonsMustCap`）已是一种「强制做减法」——本评审是其在**整条 harness** 层面的推广。
- **心跳的发现信号**：`scripts/triage.ps1` 的十枚探针里，**五枚的下一步直接指回本文**，即本评审的**自动触发点**——
  `lessons-cap`（必须层驻留 id 达/超封顶）· `lessons-demote`（某条铁律已被确定性守卫覆盖，该降回按需层）·
  `lessons-promote`（无守卫经验达晋升门槛：候选 `<=5` 时逐 id 走 lessons 动作；`>5` 时只建一个 HARNESS-REVIEW 批次）· `harness-refresh`（judgment 累积达门槛）·
  `effectiveness`（各闸拦截计数 / 0 拦截，TD2）。
  另五枚不进本仪式、走交付链：`tech-debt-open` · `cards-active` · `handoff-open` · `worktree-orphan` · `delivery-blocked`（交付停摆 → task-loop）。
  某闸门长期零命中由 `effectiveness` 探针 + 效果账本捕获——心跳负责「发现该复审了」，本仪式负责「逐闸量化、双向增删」。
- 移除闸门后跑 `scripts/selftest.ps1` 确认自洽；涉及评审维度的改动同步 `docs/QUALITY-RUBRIC.md`。
