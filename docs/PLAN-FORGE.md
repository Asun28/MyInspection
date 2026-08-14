# plan-forge — 想法 → 审过的计划 → 可执行任务卡（可复用规划 harness）

> 一条把"一句话想法 / 初稿计划"打磨成"经多裁判对抗审计、可直接施工的带依赖任务卡"的多 Agent 流水线。
> 两个工作流都在 `.claude/workflows/`，全部 `args` 参数化——**换项目只改 args**。
> **红线**：计划是**唯一真相源**（人拥有 / 人批准）；卡是它的**薄投影**；harness 只做**审计 / 投影 / 校验**，绝不偷偷把计划变成权威（呼应 `specs/README.md`「不引入第二真相源」）。

## 流水线两段（段间都有人工闸）

```
                      计划(真相源, 落 _local/)
                                │
                           plan-forge
                                │ 8 差异化 lens → 多裁判对抗核验 → 裁决 → 拆解 → 卡审
                                ▼
                      裁决(fix-first/ready) + 审计报告 ──[人改计划到 ready]──▶ 修正计划
                                                            │
                        decompose-cards ◀───────────────────┘
                                │ 投影任务卡 + 4 角度对抗卡审
                                ▼
                      任务卡结构化定义 ──[人审, 修 FATAL/HIGH]──▶ 写入 specs/tasks/*.md
```

## 各段怎么跑

### 1. plan-forge（审计现有计划）
`Workflow({ scriptPath: ".claude/workflows/plan-forge.mjs", args: { planPath, priorReviewPath?, claudeMdPath?, specsReadmePath?, templatePath? } })`
- 产出：裁决（`ready-to-decompose` / `fix-first`）+ 经对抗核验存活的发现 + 排序修正项 + 初版卡 + 卡审。
- `priorReviewPath` 可选：给定则 lens 主动避开「前次评审已发现项」，只打遗漏 + 冻结点风险（对已审过的计划仍有信号）。
- **人工闸（默认）/ 自主链式（长自主运行可选）**：`fix-first` 则按修正项把计划改到 ready，再进下一段；机械修正（措辞/字段对齐/遗漏补全类）可自主改并重新过 plan-forge、报证据，但计划裁为 ready（计划签核前奏）仍需人确认。

### 2. decompose-cards（投影成卡）
`Workflow({ scriptPath: ".claude/workflows/decompose-cards.mjs", args: { planPath, reportPath?, templatePath?, specsReadmePath?, claudeMdPath?, decisions?: ["已确认决定1", ...] } })`
- 产出：任务卡结构化定义 + 4 角度对抗卡审（拓扑 / DoD 可机检 / 硬边界许可 / allow_paths 覆盖）。
- `decisions` 可选：把 plan-forge 的修正项浓缩成逐条「已确认决定」喂进去，确保卡吸收、不把旧坑写进卡。
- **人工闸（默认）/ 自主链式（长自主运行可选）**：卡 FATAL/HIGH 修复可自主改并重新卡审、报证据；但写入 `specs/tasks/*.md`（真实任务卡落地）是**计划签核**——只有用户能拍板，必须停下等批准。

## 卡的大小：按「一个可评审/可验证单元」拆（执行者预算分两档）

卡的大小标准是**工具/模型无关**的：一张卡 = **一个可评审、可验证的单元**（单一连贯产出、一条 `dod_command` 二值验收、可一次评审判完）。「够不够小」再叠一层**执行者预算**，分两种执行模式：

- **默认档（fire-and-forget 子代理）**：子代理在一张卡里跑到完才返回、中途不交接重开（见 `docs/LOOP-ENGINEERING.md`「上下文当内存」），所以**卡的上下文预算要在拆卡时预付**——这是保守缺省。
- **长自主档（长命/可续接子代理 · 自主弧）**：长寿子代理可跨子任务保留上下文，或按间隔用独立、新上下文的 verifier 子代理自校验，从而一次推进跨多文件/多子系统的弧（受支持替代，须**显式声明** + 配**间隔 fresh-context 校验**）。声明了长自主执行的卡，超默认档不算过大。

拆解 / 卡审时，用这些可观察的代理指标判「够不够小」（启发式，非硬闸——本仓的闸靠复发挣来）：

- **`allow_paths` 收窄到单一子系统 / 少数文件**：这是「这个 agent 得把多少代码库读进上下文」最直接的代理；`allow_paths` 越宽，子代理越可能坐满预算。
- **单条能独立跑的 `dod_command`**：验收要拉起半个系统才能判，通常意味着卡跨了太多面，该拆。
- **验收标准条数有界、一张卡 = 一个 worktree = 一个体量正常的 PR**：判据是「可一次评审判完」；默认档下＝「一个 agent 一次坐满预算能干完、不需中途重开」，长自主档下＝「长命/可续接子代理带间隔自校验能干完」。
- **拆不动的大活 → 切成带依赖的卡序列**（`T<阶段>-FOO-1` / `-2`），别让单卡膨胀。

`check-cards.ps1` 已守 `allow_paths` / `dod_command` 必填；「大小」目前是**启发式**、不设硬阈值（卡多大算大依赖具体工作，硬闸易误杀合法大卡）。若「卡老拆太大」反复出现，按 `docs/LESSONS.md` 入账 → 复发达门槛再晋升为机械守卫。

## 用在新项目
1. 工作流文件随模板已在 `.claude/workflows/`。
2. 把各 `Workflow` 调用的 `args` 指向新项目的计划 / 模板 / specs / CLAUDE.md（默认值已是相对路径，多数情况无需改）。
3. 没有计划时，先照 `docs/PLAN-TEMPLATE.md` 手工扩写并经用户批准；已有计划则直接从第 1 段开始。
4. 触发：需用户显式开启多 Agent 编排（如 prompt 含 "ultracode"，或直接说"跑 plan-forge 工作流"）。

## 设计要点（为什么这么搭）
- **差异化、不重复**：plan-forge 的 lens 在有 priorReview 时显式避开"前次评审已发现项"，只打**遗漏 + 冻结点风险**。
- **对抗核验**：每条 FATAL/HIGH 由多个裁判从不同角度试图**反驳**，≥多数反驳即枪毙，杀掉似是而非的发现。
- **冻结点意识**：专打"前期错后面白干"——契约 / schema 冻结后改 = 版本评审 + 全下游返工，冻结前改近零成本。
- **真相源单一 + 人工闸**：每段之间人批准，harness 不产生第二真相源。

## 成本（按 effort / 预算缩放 lens 与裁判数）
| 工作流 | 量级 | 备注 |
|---|---|---|
| plan-forge | 约 60-85 agent | 视发现数（8 lens × 多裁判核验 + 汇总 + 拆解 + 卡审） |
| decompose-cards | 约 5 agent | 1 拆解 + 4 并行卡审 |
