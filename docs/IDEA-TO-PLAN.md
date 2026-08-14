# 从想法到计划：3 步（Idea → Plan front funnel）

> **给谁看**：任何人——包括非技术同事。这页用大白话讲清楚「一个想法怎么一步步变成可以开工的计划」。
> 三步、三个编号文件，顺序一目了然。每步都有人把关，机器只做搜集、打分、投影，**不替你拍板**。
>
> **这页之后**：开工执行（worktree + TDD + Codex 评审 + CI）见 `docs/DEVOPS-WORKFLOW.md`；
> 规划引擎细节见 `docs/PLAN-FORGE.md`。方法来源：obra/Superpowers（brainstorm/写计划纪律）+ garrytan/gstack
> （六个逼问/选型评审）的精华，去掉它们各自的运行时耦合，融进本仓的确定性闸门。

## 总图（想法 → 上线 · 全程 AI，人只在 ⛳ 闸口定方向）

```
                          一句话想法
                              │
  ┌───────────────────────── 第1步 Shape (1-brief) ─────────────────────────┐
  │  ① 发散 DIVERGE (做加法/探索)        ② 收敛 CONVERGE (做减法/定义)        │
  │  AI 并行调研:痛点·JTBD·竞品·         AI: KANO/MoSCoW 排序 → 砍伪需求 →    │
  │  跨域·What-if → 机会地图(≥5角度)      MVP → 用户故事+验收(G/W/T)            │
  │            ⛳人选方向                          ⛳人批 brief                  │
  └────────────────────────────────┬────────────────────────────────────────┘
                                    ▼  _local/1-brief.md
  ┌───────────────────── 第2步 Scout (2-options) ───────────────────────────┐
  │  scout-options.mjs:5 角度并行搜(品类/关键词/生态/前人工程/顶会论文SOTA)   │
  │  → 逐候选按硬规则(许可/离线/确定性)对抗核验 → 决策矩阵 + 推荐             │
  │                            ⛳人批 ADR                                      │
  └────────────────────────────────┬────────────────────────────────────────┘
                          ▼  _local/2-options.md + docs/adr/NNNN-*.md
  ┌───────────────────── 第3步 Plan (3-plan) ───────────────────────────────┐
  │  (设计先拷问) grill-design 沿决策树一次一问·给推荐·消解依赖 → 敲定设计    │
  │  PLAN-TEMPLATE 扩写 → plan-forge.mjs 8 lens 审计(含 ③ module-design:      │
  │  模块化/扁平化/去中心化「做乘法」+ 反过度工程右尺寸) + 多裁判对抗核验     │
  │  → 裁决 → 投影任务卡 → decompose-cards 卡审                               │
  │                          ⛳人批计划/卡                                     │
  └────────────────────────────────┬────────────────────────────────────────┘
                                    ▼  specs/tasks/*.md (带 MoSCoW 优先级)
  ┌───────────────────── 开工执行 (task-loop · R1–R5) ──────────────────────┐
  │  并行窗口:每卡一个 AI agent、各占一棵 worktree 并行跑闭环                  │
  │  R1 worktree → R2 TDD(先测) → ponytail/simplify → R4 剪枝 →               │
  │  安全闸 → R3 Codex 评审 → PR → 合并 → R5 文档同步   (冻结点卡先合)        │
  │                                                                           │
  │  ┄┄ 前端分支(T2·复杂多页前端) ┄ frontend-flow 串现有件:                  │
  │     流程卡(页面地图)→plan-forge 投卡 │ 意图卡(单页)→grill-design 拷问     │
  │     生成→pencil MCP/Claude Design 高保真 │ 验证过区块→回流 context/       │
  └─────────────────────────────────────────────────────────────────────────┘
                                    ▼
                              可运行产品
                                    │
  ┌───────────────── 合并之后 · 验收→收口→发布 ─────────────────────────────┐
  │  【发布前收口】RELEASE-CHECKLIST 可勾清单:整合已有闸(防泄露/verify)        │
  │     + 授权安全自查(IDOR/会话/token/CSRF) + 可观测 + 灰度/回滚            │
  │     (能力完成度自评见 eval 方法论 docs/EVAL.md, opt-in·下游自接)          │
  │                              │                                            │
  │  【变 public 前防泄露】check-secrets.ps1 -Strict 须全绿                    │
  │     (机密既被 gitignore、又未被 git 追踪)                                 │
  │                          ⛳人批发布                                        │
  └────────────────────────────────┬────────────────────────────────────────┘
                                    ▼
                                 上线
                  (合并之后的交付/运维方法论见 docs/DELIVERY-OPS.md;
                   脚手架永不自动发布,CD 下游接线)
```
> **自主链式（长自主运行可选；机械环节报证据不阻塞，硬人工闸不因它而跳）**：⛳人选方向、⛳人批 brief 是**brief 批准**这一硬人工闸的两半——需求方向/范围只有用户能给，自主链式下始终停，不因用户预先委托而跳过。
> ⛳人批 ADR：归档 ADR、落 `_local/2-options.md` 等机械步骤可自主完成并报证据；启用自主链式本身即用户对本段的委托——recommendation 与已批 brief 的范围/约束一致时，即按推荐自主进入第 3 步并报证据；仅当推荐会改变已批范围（如引入新许可类别、成本量级、超出 brief 的架构含义）或出现只有用户能答的问题时才停。
> ⛳人批计划/卡 = **计划签核**，另一处硬人工闸，自主链式下始终停：`plan-forge` 的 `fix-first` 修正、`decompose-cards` 的卡 `FATAL/HIGH` 修复这两段机械环节可自主改并重新过审、报证据，但计划裁为 ready、拆解结果写入 `specs/tasks/*.md` 前必须停下等用户签核，不得替用户拍板。
> 检查点边界（不是「每个 ⛳ 都停」，而是碰到才停）：难逆/破坏性动作、真实范围变更、或只有用户能给的输入——见 `docs/references/claude-fable-5-prompting-llms.txt`。落地见 `.claude/hooks/route-new-work.ps1`、`.claude/workflows/scout-options.mjs` 的 next_step。
> **三段方法论**:① 想法**发散**(加法,找真痛点,禁过早收敛) → ② 需求**收敛**(减法,KANO/MoSCoW 砍到 MVP) → ③ 设计**做乘法**(模块化/扁平化/去中心化,但小 MVP 右尺寸优先、反过度工程)。
> **适配 AI**:全程 AI 执行,人只在 ⛳ 闸口定方向(RSI:perspiration 自动化、direction 归人)。开工细节见 `docs/DEVOPS-WORKFLOW.md`。
> **产品之后**:验收靠 eval 方法论自评(`docs/EVAL.md`,opt-in、下游自接 CI)、收口靠发布前清单(`docs/RELEASE-CHECKLIST.md`)、变 public 前过防泄露闸(`docs/SECURITY.md`);**档位决定走多长**——T0 只到「可运行产品」即可,T1 加防泄露,T2 全套(含前端 frontend-flow、post-merge `docs/DELIVERY-OPS.md`)。
> **精致版总图(浏览器打开)**:`docs/idea-to-plan-diagram.html`(自包含、内联 CSS,用本仓设计技能做的「控制塔」风格)。想看**分层子系统架构**(盒子+关系连线,非线性管线)另见 `docs/scaffold-architecture.html`(架构图 + 流程图两视图)。

## 按规模档位（治「小项目被全套流程拖慢」）

不是每个项目都要走全套链。按项目规模选档，**建议跳过哪些链**——AI/人按规模裁，`scripts/_config.ps1` 的 `ProjectTier` 是软提示（默认 `T1`）。

| 档位 | 适用 | 走哪些链（在上一档基础上叠加） | 总图走到哪 |
|---|---|---|---|
| **T0 极简** | 脚本 / 玩具 / 一次性 | 只 `task.ps1` R1–R5 + `check-cards` + `lessons` + CI；**跳过整个想法→计划漏斗**，直接写卡开干。 | 只「开工执行 → 可运行产品」 |
| **T1 标准** | 多数项目 | + 想法→计划漏斗（shape-idea →[scout-options]→[grill-design]→ plan-forge → decompose）+ 发布前清单 + 变 public 前防泄露闸。 | 全链：3 步漏斗 → 开工 → 产品 → 收口/防泄露 → 上线 |
| **T2 完整** | 大 / 长周期 / 团队 / 合规 | + 前端闭环（frontend-flow 串 frontend-design / taste-skill / pencil）+ post-merge（DELIVERY-OPS：集成/e2e·可观测·灰度·CD）+ 心跳（triage）+ 全套对抗。 | 全链 + 前端分支 + 合并之后交付/运维层 |

> 档位是「**建议跳过哪些链**」，AI/人按项目规模裁；`_config.ProjectTier` 只是软提示，不做强制机制、不做物理裁剪。
> **注**：T2 的「团队 / 合规」指**项目复杂度**（更重的流程 / 审计需求），**不**代表脚手架提供多人组织治理——git 层账号守卫仍锁**单个人账号**、R3 状态可被任何写权限者伪造（见 `docs/SECURITY.md` §4 与 `specs/tech-debt-tracker.md` TD14）。org/team 治理是范围外、须 ADR 扩展。

## 一眼看懂

| 步 | 大白话名字 | 回答什么问题 | 谁来做 | 产出文件（非技术也读得懂） |
|---|---|---|---|---|
| 1 | **Shape（发散→收敛）** | 我们要做什么、为什么？（先发散找痛点，再收敛定 MVP） | `shape-idea` skill（AI 自驱，人把关） | `_local/1-brief.md` |
| 2 | **Scout（搜现成方案）** | 已经有什么能用？拿什么当地基？ | `scout-options.mjs`（多 agent 搜+评） | `_local/2-options.md` + `docs/adr/NNNN-*.md` |
| 3 | **Plan（写成计划）** | 具体怎么做？拆成哪些任务？ | `PLAN-TEMPLATE` + `plan-forge.mjs` | `_local/3-plan.md`→ `specs/tasks/*.md` |

> `1-` `2-` `3-` 的编号就是顺序。这三个 `_local/` 文件不入库（是你的工作草稿）；只有第 2 步的 ADR 和第 3 步的任务卡会进仓库。

---

## 第 1 步 · Shape —— 发散找痛点 → 收敛定 MVP（AI 自驱，你只把关）
**做什么**：AI 替你把模糊想法先**发散**（做加法：找真痛点/创新/商业价值），再**收敛**（做减法：砍到 MVP），落成一页「做什么/为什么」的简报。
**怎么开始**：直接说"我有个想法…"或"帮我理一下需求"，或点名 `shape-idea`。
**会发生什么**（全程 AI 干活，你只在两个闸口定方向；缺真实信号的判断 AI 会标 `needs-signal`）：
- **发散（加法）**：AI 并行调研——挖真实痛点（web 搜 + 社交/论坛/评论如 Reddit/X 的抱怨与 workaround）、扫竞品+邻域、跨域类比、反共识 What-if——给你一张「机会地图」（≥5 个迥异角度，**先不筛可行性**）。⛳**闸**：你挑哪些方向有感觉。
- **收敛（减法）**：AI 用 KANO/MoSCoW 排优先级、砍伪需求、提炼 MVP，把每条转成「作为…我想…以便…」的用户故事 + 验收标准（Given/When/Then）。⛳**闸**：你批准简报。
**红线**：简报没批准前不写代码、不进第 2 步；**发散期严禁用"技术难/没资源"提前收敛**；简报只谈做什么/为什么。
**模板**：`docs/PROJECT-BRIEF-TEMPLATE.md`。

## 第 2 步 · Scout —— 搜现成方案、定地基
**做什么**：别从零造轮子之前，先看世界上已经有什么能用。多 agent 从 4 个角度并行搜开源/库，逐个按本仓硬规则（许可、离线、确定性、目标系统）打分核验，给出**决策矩阵 + 推荐**。
**怎么开始**：`Workflow({ scriptPath: ".claude/workflows/scout-options.mjs", args: { briefPath: "_local/1-brief.md" } })`（或直接说"跑 scout-options"）。
**会发生什么**：
- 搜候选 → 去重 → 每个候选打分（许可过不过？要 adopt 还是 fork 还是只借思路？可行性 0–10）→ 汇总成一页选型说明 + 一条 ADR（永久决策记录）。
- 所有候选都不合适时，它会**明确建议从零做**并说清原因。
**产出**：`_local/2-options.md`（选型说明）+ `docs/adr/NNNN-<名字>.md`（入库的「为什么选它」）。
**细节**：`docs/SCOUT-OPTIONS.md`。

## 第 3 步 · Plan —— 写成可开工的计划
**做什么**：按第 2 步选定的 base，把简报扩写成计划，再由规划引擎审计、投影成带依赖关系的任务卡。
**怎么开始**：
0. （可选但推荐）填 PLAN 前先说「**grill / 拷问我的设计**」触发 `grill-design`：AI 沿设计决策树**一次一问、每问给推荐、消解依赖**，把数据模型/契约/状态机/模块边界/错误路径敲定——设计先 grill 清楚，下一步 plan-forge 少返工。
1. `Copy-Item docs\PLAN-TEMPLATE.md _local\PLAN.md`（或叫 `_local/3-plan.md`），按第 1 步简报 + 第 2 步 ADR（+ grill 敲定的设计决策）填各节。
2. `Workflow({ scriptPath: ".claude/workflows/plan-forge.mjs", args: { planPath: "_local/PLAN.md" } })` —— 8 个 lens 审计（含 `module-design`：模块化/扁平化/去中心化「做乘法」+ 反过度工程右尺寸）+ 多裁判对抗核验 → 裁决 → 投影任务卡 + 卡审。
3. 裁决 `fix-first` 就按修正项改计划到 `ready`；然后 `decompose-cards.mjs` 投影成 `specs/tasks/*.md`。
**会发生什么**：计划是**唯一真相源**（你拥有/你批准），任务卡是它的薄投影。写计划的纪律（不留占位、任务右尺寸、文件结构即拆解）借自 Superpowers `writing-plans`；审计/拆卡引擎是本仓自有、更强。
**细节**：`docs/PLAN-FORGE.md`（引擎）·`specs/README.md`（任务卡投影约定）。

---

## 接上开工
任务卡写好后，每张卡走单卡闭环 R1–R5（worktree → TDD → DoD → Codex 评审 → PR → 合并 → 文档同步），见 `docs/DEVOPS-WORKFLOW.md`。
至此漏斗闭合：**想法 → 1-brief → 2-options(+ADR) → 3-plan → 任务卡 → 开工**。
