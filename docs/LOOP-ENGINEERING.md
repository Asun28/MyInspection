# Loop Engineering：心跳 + 理解债闸 + 把发现自动化

> **来源**：addy osmani《Loop Engineering》(https://addyosmani.com/blog/loop-engineering/) ·
> Anthropic《Recursive Self-Improvement》(https://www.anthropic.com/institute/recursive-self-improvement) ·
> 吴恩达「三层 loop」（0→1 产品语境：https://x.com/AndrewYNg/status/2071988145667928442 ·
> 综述 https://adtmag.com/articles/2026/07/01/loop-engineering-emerges-as-developers-put-ai-coding-agents-on-repeat.aspx）。
> 本文把这几篇里**脚手架尚缺**的部分落地，并标注脚手架**已有**的部分（多数已覆盖，勿重造）。

## 一句话
脚手架原本全是**按需触发**（`task start/ship/cleanup`）；loop-engineering 的第一组件「the heartbeat」要求一个
**定期发现待办**的回路。本仓为此加了 `scripts/triage.ps1`（心跳）+ `triage` skill（回路）+ 下方「理解债闸」纪律（防盲信）。
其余四个组件脚手架早已具备——见下表，**不要重复造**。

## loop-engineering 五组件 → 脚手架映射
| 组件（osmani） | 脚手架落地 | 状态 |
|---|---|---|
| ① Automations（心跳：定期发现+分诊） | `scripts/triage.ps1` + `.claude/skills/triage` | **本次新增** |
| ② Worktrees（并行隔离） | `scripts/task.ps1` R1（每卡 `<WorktreeRoot>\<id>`） | 早已有 |
| ③ Skills（固化知识） | `.claude/skills/{task-loop,lessons,planning-with-files,triage}` | 早已有（+triage） |
| ④ Plugins/Connectors（接外部世界） | codex 评审插件 · `gh`（建仓/PR/合并） | 早已有 |
| ⑤ Sub-agents（写/验分离） | R3 Codex 第二评审（`scripts/review.ps1` 按 `docs/QUALITY-RUBRIC.md` 判） | 早已有 |
| State/Spine（外部记忆） | handoff 三件套 + `scripts/handoff.ps1`（`docs/HANDOFF.md`） | 早已有 |

## 三层回路（Ng）：上表五组件几乎全住在第 ① 层
> 与 osmani 五组件**正交**——五组件说「回路由什么**零件**构成」，三层说「回路以什么**节律**转、**谁**主导、做错了**多久**才知道」。
> 上表除心跳（属②）外全是①的零件。**此处只做分层与接线，不新增任何机器**。

| 层 | 节律 | 主导 | 判据 | 脚手架落地 | 状态 |
|---|---|---|---|---|---|
| ① agentic coding（AI 自写自测自修到符合规格） | 分钟 | AI | 确定性 exit 0/1（**R3 是唯一 carve-out**，见下） | `task.ps1` R1–R5 · TDD · `verify.ps1` · CI · R3 codex | **重装**（本仓另有 17 闸 selftest 守脚手架自身） |
| ② developer feedback（人看当前产品、指挥方向：加什么特性 / UI 哪不行 / 流程怎么改） | 数十分钟–小时 | 人 | 判断，非闸 | 想法→计划漏斗 · 心跳 `triage` · 理解债闸 · `docs/HARNESS-REVIEW.md` · handoff | 齐备，但**散在五份文档**、此前从未被命名为「一层」 |
| ③ external feedback（几个朋友试用 → alpha 用户 → 产线 A/B） | 天–周 | 真实用户 | 线上信号 | `docs/DELIVERY-OPS.md`（opt-in 占位）· `docs/EVAL.md` GROW | **薄，且是有意的**——见下 |

**别越层套零件**：①的**完成判据**必须是客观信号——测试/编译/lint 的 exit 0/1；模型的**主观质量分**永不当完成闸（**L25**，见下「软完成失败」）。
**唯一 carve-out 是 R3**：它代的是**人工审批**、不是 LLM 质量分，故被显式接受为①层里唯一非确定的闸（裁决跑跑可能不同，误 block 走重审路径；
见 `docs/QUALITY-RUBRIC.md` §0 与 `specs/tech-debt-tracker.md` TD1）——**别把这条 carve-out 读成「模型可以判完成」**。
②的产出是**方向与上下文**、不是又一道闸——
人在②的稀缺贡献是 taste + 只有他看得见的用户/业务上下文，**不是替机器复核 diff**（复核归①的确定性闸；理解债闸问的是「你懂不懂」，不是「你验没验」）；
③的产出是**信号**，回流走下面两条既有正门，不靠加 build-time 闸。

### 闸门密度与后果严重性成反比——自觉的取舍，不是疏忽
①最快、最可逆，却压着**最多**的闸；③最慢、且是**唯一**能证伪「这东西真有人要」的层，闸门数 = **0**。两条理由撑这个倒挂：
**(a) 闸该花在可逆性低处**——①的错误几分钟内被测试抓住，闸便宜、立刻兑现；而③的「错误」（造了没人要的东西）根本不是任何 build-time 断言**能表达**的命题。
**(b) 元层没有产线**——脚手架**永不自动发布**（`docs/SECURITY.md` 硬边界），埋点/放量/A-B 由**下游**按自己平台接线，元层只能给方法论 + 接缝（`docs/DELIVERY-OPS.md` a–d 节）。

代价必须说破：**build-time 全绿 ≠ 这东西值得存在**。①证明「代码对」，②证明「方向对」，③才证明「用户要」。
把①的绿灯读成③的答案，是理解债在**产品层**的同构——*"the comfortable posture is the dangerous one"*。

### ③ 怎么回流：两条既有正门，都不需要第十个探针
Ng 的链是「外部数据 → 修正开发者愿景 → 改产品规格 → 喂编码 agent」。本仓早有这条链，按信号类型分两口进：
- **缺陷类**（bug 逃逸到产线）→ **③直连①**：补一条回归 eval（`docs/EVAL.md` GROW）+ 复发的工具链坑记一条经验（`scripts/lessons.ps1` → `enforced_by` 机械守卫）。回流的**产物**受机检（eval 条目 exit 0/1、守卫脚本），**触发**靠人。
- **方向类**（用户不要它 / 要的是别的）→ **③经②回①**：从漏斗的 **`1-brief` 重新进**（`docs/IDEA-TO-PLAN.md`：1-brief→2-options→3-plan），修愿景 → 改规格 → 重新拆卡。这正是 Ng 那条链——**早已存在，只是从没被标成「外部信号的入口」**。

> **心跳不接外部信号，是刻意的**：`triage.ps1` 8 探针全部只读、离线、扫本地子系统（见下节）。生产遥测要网络、要状态、要凭据，
> 会把一个「只读离线确定性」的 reporter 变成有攻击面的守护进程（对照下方「安全税」节）。③的入口是**人把外部信号写成一条 eval 或一条 brief**，从既有正门进。

## 心跳：`scripts/triage.ps1`
**只读、离线、确定性**地扫描既有子系统的本地信号，汇成收件箱 `_local/triage-inbox.md`（gitignored）。8 探针：
`lessons-promote`（经验达晋升门槛却仍在总账层）· `tech-debt-open`（债未还）· `cards-active`（卡在飞）·
`handoff-open`（cwd 交接未收口）· `lessons-cap`（必须层达封顶该做减法）·
`harness-refresh`（judgment 经验累积达门槛——该双向自我改进：删旧闸 + 主动搜更优工具/方法纳新）·
`effectiveness`（效果账本：各 ship 闸真实拦截数——喂 HARNESS-REVIEW 据拦截数 + ship 次数做减法）·
`worktree-orphan`（卡已 merged 却没拆的残留 worktree——cleanup 漏跑 / 半合并遗留）。

- 退出码恒 0——它是 **reporter，不是闸门**。闸门仍是 worktree/TDD/Codex/CI。
- **只发现、不行动**：绝不写仓内被跟踪文件、不做 git/gh 写操作。act 走既有交付链。
- 自检：`pwsh -File scripts\triage.ps1 selfcheck`——探针 4 的 hermetic 自测（临时夹具 + 输出断言，末行 `triage selfcheck: PASS` 即绿；PR #26 引入，selftest 闸 12c 常设接线，改探针即回归）。
- 节律（主模式）：一次自主运行内**自步进 N 个 triage→act 周期**——扫描发现 → 挑一件 act → 到间隔用 fresh-context 校验（独立、新上下文的 verifier 子代理对着规格核，优于自我批评）→ 再扫下一轮，如此推进数小时。session 开场跑一次当 work-list、或 Claude Code `/loop` 定时重跑 `triage` skill，是**回退节律**（用户不在场自步进、或跨运行接力时用）。

> **发现 vs 行动，按可逆性分界**（Anthropic RSI）：把「perspiration」（巡检、发现）自动化，心跳本体只发现；act 则按**可逆性**分两档。
> **难逆/方向性**（合并、推送、设定「做哪件、值不值得做」的方向）仍归人/agent 确认——这类一旦错难以回退；**可逆且属既定意图**（开卡、promote 达门槛的 lesson、清 orphan worktree）可在自主运行里直接做，但须**证据锚定上报**（每条声明指向本 run 的工具结果——命令+退出码/文件路径/diff，见 `docs/HANDOFF.md` 异步上报节）。RSI 把「research taste / 选对方向」列为最后的人类瓶颈——分界正落在这条可逆性线上，而非「模型什么都还不能自主」。

## 理解债闸（comprehension-debt）
osmani 的核心警告：同一套回路既能**加速有把握的工作**，也能**助长对产出的不理解**——*"the comfortable posture is the
dangerous one"*。脚手架优化吞吐（worktree/TDD/Codex/CI/心跳），但**吞吐 ≠ 理解**。

落地 = `CLAUDE.md` 执行边界「完成与词义」（完成只有一个定义 = 机检闸通过，自评「看起来好了」不算数）+ task-loop 步骤 4.7
fresh-context 证据审计（重大改动派独立子代理核验，不由做改动的同一上下文自证）。原 Stop 钩子 `comprehension-reminder.ps1`
仅复述这两处，属纯重复，已于 TD88 W2 删除；它治的是**人/agent 侧**的债，不是又一道机器闸。

## 上下文当内存（预算纪律）

> 回路、心跳、子代理都在**花上下文**。和理解债闸同源——*"the comfortable posture is the dangerous one"*：
> 上下文越满，模型越容易在你没察觉时变笨。把上下文当**有限内存**来管，别当无限磁带。

- **指令有上限**：大模型同时能稳守的指令数有上限；塞进太多铁律/规则，反而开始漏守。这正是 `LessonsMustCap`（必须层封顶）和 `.claude/rules/` 懒加载存在的原因——见下。
- **单次长自主运行是默认**：Fable 5 一轮可自主延续数小时——把上下文当有限内存管好（噪音委派出去、脏了就回退，见下两条），但**别因预算数值而收工**。别把剩余 token 倒计时暴露给模型，也别据此建议开新会话/交接/自行删减——那诱发预算焦虑、易在活没干完时静默退出。真要收束靠**结论沉淀**（过程噪音留在子代理、只把结论带回主线），而非按用量到点定时截断（源：`docs/references/claude-fable-5-prompting-llms.txt`「更长的单回合」「罕见的上下文预算焦虑」）。
- **拆会话 + 交接是例外路径**：一个长任务默认在单次自主运行里推到底；把它拆成多段、每段开新会话接力**只在真边界触发**——进程崩溃/换机器、真被只有用户能答的问题阻塞、或用户叫停。触发时介质仍是已有的 handoff 三件套（`task_plan.md`/`findings.md`/`progress.md` + 末尾 HANDOFF 块，见 `docs/HANDOFF.md`）；`scripts/handoff.ps1 check` 守交接不得模糊。它是**崩溃保险 + 真边界检查点**，不是每隔一段就该做的常规动作。
- **`CLAUDE.md` 是上下文预算表，不是文档库**：它每轮全量进上下文，是**最稀缺**的预算。只放索引 + 每轮必须的铁律；分语言/分目录的细则拆进 `.claude/rules/` 懒加载（`paths:` 只在 Read 到匹配文件时注入），给主文件瘦身——见「文件放置约定」与 `.claude/rules/README.md`。
- **回退优于在被污染的上下文里纠正**：一旦上下文被错误方向/失败尝试污染，继续在里面「掰回来」往往越掰越歪。更省的是**回退**——丢弃这段、从干净状态（或交接总结）重开，比在脏上下文里反复纠正划算。
- **子代理的价值在上下文隔离**：把噪音大的活（大范围搜索、读一堆文件、试错）丢给子代理，**只把结论带回主会话**，原始噪音留在子代理的上下文里。主会话的预算因此只承载结论，不承载过程。
- **预算纪律是 fractal 的——子代理同样适用，但按形态分两种**：上面几条（结论沉淀、回退、隔离）对**每个** agent 都成立，不只主会话。关键区分是子代理的两种形态：**长命可续接子代理**（harness 已支持——如 `SendMessage` 按 id/名字带原上下文续接；跨子任务保留上下文、省时省钱走缓存读）能像主会话一样中途接力、被纠偏、再往下委派；**一次性即抛（fire-and-forget）子代理**跑到返回就丢掉原上下文，逃生口更少，其上下文预算几乎只能**开工前预付**——靠把任务卡拆到「一个 agent 一次坐满预算能干完」。所以拆卡对即抛子代理仍是**承重机制**（不只整洁）；能续接时它退化为一种便利。sizing 启发式见 `docs/PLAN-FORGE.md`「卡的大小」。

## 把「发现」喂回自我改进（Anthropic RSI 的 judgment scaffolding）
脚手架对应物 = 经验系统的 **judgment 类经验**（`scripts/lessons.ps1 add -Kind judgment`）：记「当时选了次优方向、更好的下一步是什么」，
不升级为机械守卫（方向启发式难机检），而是汇入 `docs/HARNESS-REVIEW.md`「judgment-feed」节（真相源：随模型变强复审方向品味、双向增删仪式）。

## 瓶颈会迁移（Amdahl）
RSI 文：某环节一旦提速，瓶颈就**迁移**到别处（他们发现自动化生成代码后，**人工评审**成了新瓶颈——脚手架已用 R3 Codex 评审
把这环也自动化）。推论：**别假设当前闸门永远是对的**。心跳让待办可见、`docs/HARNESS-REVIEW.md` 让闸门可被减除——
两者合起来，是脚手架对「瓶颈会迁移」的结构性回应：持续发现新瓶颈、持续给旧闸门做减法。

## 软完成失败（Ralph Wiggum loop）：闸必须是客观信号，不是模型意见
> 命名：Geoffrey Huntley「Ralph Wiggum loop」；同源警告见 osmani「maker 太宽容地给自己批作业」。

回路最隐蔽的失败 = **完成条件是「软」的**：靠模型（或只会「review」的第二个 agent）的意见判 done，而非测试/编译/lint 的 exit 0/1。
于是 agent 提早吐完成 token、在半成品上**静默退出**、还继续烧钱。三个触发：无真验证器（俩乐观主义互相点头）、软完成条件（done 由 agent 判断）、无硬停（靠 rate-limit 或人注意到才停）。
- **修法 = 既有确定性闸**：DoD 走 `scripts/verify.ps1` / 测试 / 编译 / lint 的 exit 0/1；**L25** 是其前端特例（模型只在上游写断言、绝不进闸）。第二意见走 R3 codex（写/验分离，maker≠checker），但 R3 代**人工审批**、非 LLM 质量分（carve-out 见 `specs/tech-debt-tracker.md` TD1）。
- **节律语义别混**：「定时重跑」（cadence，无论状态都查）≠「跑到某客观条件为真才停」（goal，停条件交由另一个模型实例核）。心跳 `triage` 是前者——退出码恒 0、只发现、非闸；真正的「干到完成」必须把停条件钉成客观信号，**别让写代码的 agent 自己判完成**（呼应 **L24**：发现≠已决定）。两种节律**别混用**：定时重跑不会让你到达终点，停条件不客观则永远到不了。
- **「独立」在实例 vs 在证据——只有后者算闸**：凡**停条件交由另一个模型读会话记录来判**的机制，其独立性只在**实例**（maker≠checker 确是两个模型），**不在证据**（评委读到的仍是写者自己的叙述）。写者漏跑一个文件、或把失败讲成成功，只读会话的评委原则上无从发现。故这类机制的产出**仍是模型意见、不是客观信号**，绝不可当 DoD 闸（**L25**）。判据不是「评委是不是另一个实例」，而是「**评委的证据是否独立于写者的自述**」。对照 R3：`review.ps1` 喂的是**真实 diff hunk**（`--unified=3`，不止 `--stat`），rubric 从**基线分支**读（`git show $Base:docs/QUALITY-RUBRIC.md`）——证据与标准都不取自写者的自述，故它够格代人工审批。
  > 某个**具体工具**（如 Claude Code 的 `/goal`）落在哪一侧，**以其当时的官方文档为准，本节不断言**（**L26**：定义层工具无关；仓外文档未 vendor 进 `docs/references/`，对 agent 即不存在）。要把它钉死在某一侧，先按 `docs/references/` 的规矩 vendor 其文档再引。
- **但 R3 的独立性同样有边界，别把它读成绝对**：diff 正文超 6 万字符即按字符截断（尾部 hunk 按 git 路径序被丢），余下退回「评审者自行只读核对」的荣誉制；基线无 rubric 时回退工作树副本；且 `_config.ps1`（FrozenPaths）与 `review.ps1` 本体按 `$PSScriptRoot` 载入——标准 ship 路径从**主检出**调用（`task.ps1:399`）不受影响，但 `ship -Local`（`task.ps1:362`）与手动在**被审检出内**跑评审时，这部分判定标准由被审分支自己提供（登记 `specs/tech-debt-tracker.md` TD66）。
  **把「另一个模型读会话来判完成」读成「写者骗不过的验证器」，是本节警告的原样复现；把 R3 的独立性读成无边界，是同一个错误换了个方向。**
- **硬停判据是「无进展」，不是「花了多少」**：第三个触发（无硬停）的修法——**同一步骤连续失败 N 次（默认 2）即停：记下失败签名、跳过该步、显式上报**，绝不静默重试到 rate-limit 或等人来发现。判据是**进展停滞**（同一失败重复出现），**不是 token 用量**：按剩余预算收工会诱发预算焦虑与半成品静默退出（见上「上下文当内存」：别因预算数值而收工）。二者不冲突——**预算不该让你停，无进展应该让你停**。`CLAUDE.md`「模型分工与交接」的「两次没修好的 bug → 升 Opus」是本规则在**模型路由**上的特例；回路层通则是**停 + 上报 + 换目标**，别在同一堵墙上撞到耗尽。

## 安全税：无人值守的回路 = 无人值守的攻击面
回路开 PR 比人读得快、且常带写权限跑，其防护**会随时间腐烂**——权限范围、skill 来源、闸有效性都要周期性抽检，不是建一次就永久。
周期复审清单（权限重审、灰区权限分类器、skill 来源审、闸腐烂抽检）真相源见 `docs/HARNESS-REVIEW.md`「安全税周期复审」（TD10 → ADR `0002`）。

## 边界：什么**没**自动化（有意为之）
- 心跳本体（`triage.ps1`）只发现、只读，不内置无人值守的 git/gh 写自动化。**运行中 agent 的 act 按可逆性分界**：难逆/方向性（合并、推送、设方向）留人/agent 确认；可逆且属既定意图（开卡、promote 达门槛的 lesson、清 orphan worktree）可自主做并证据锚定上报（见上「发现 vs 行动，按可逆性分界」）。这是**行为边界**，不是新增自动化。
- 收件箱是**运行时态**（`_local/`，每次覆盖），**不是**真相源；真相源仍是 LEDGER / tech-debt-tracker / specs/tasks。
- 一次只挑**一件**做完再扫，别把收件箱当批处理无脑平推（见 LEDGER 的 judgment 经验）。
