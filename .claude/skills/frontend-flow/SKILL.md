---
name: frontend-flow
description: >-
  前端生成闭环的【串联驱动卡】(T2 档·复杂多页前端才用)。把现有件串成「生成前→生成中→生成后→资产回流」
  四段:PRD 复用 shape-idea/PROJECT-BRIEF + frontend/README 5 闸 + design tokens 真相源;生成中产出
  【流程卡(页面地图)】喂 plan-forge 投影任务卡、【意图卡(单页目标)】用 grill-design 拷问敲定;生成后路由
  pencil MCP / Claude Design 可视化高保真、frontend-design/taste-skill 局部改;验证过的区块回流
  context/frontend-assets/。它不是新引擎——所有重活复用现有件,只补「前端生成」这个串联视角。
  Triggers on: "前端生成", "做前端页面", "页面地图", "意图卡", "流程卡", "前端闭环", "frontend flow".
  不用于:后端/通用任务(用 plan-forge)、纯需求(shape-idea)、纯视觉矫正(taste-skill)、简单单页前端(直接 frontend-design + pencil)。
---

# frontend-flow — 前端生成闭环驱动卡（T2 档 · 串联现有件，不重造引擎）

> **这是一张「串联驱动卡」,不是新引擎。** 前端从需求到上线的链路本仓已有零件(shape-idea / frontend/README 5 闸 /
> design tokens / grill-design / plan-forge / frontend-design / taste-skill / pencil MCP / context/),
> 但缺一个**「前端生成」视角的串联**。本卡只补这条线——把现有件按四段闭环串起来,每段都**复用**对应的现有件,
> **绝不另造拷问/审计/编辑器引擎**。每件的边界见下「边界(复用非重复)」节。

## 档位:仅 T2(复杂/多页前端才用)
> 呼应 `docs/IDEA-TO-PLAN.md` 档位表 + 反过度工程。
- **T2 完整**(大 / 多页 / 长周期 / 团队):才上这套四段闭环——页面多、共享状态复杂、要先画**页面地图**再逐页敲**意图**,值得这套结构。
- **T0/T1 简单前端**(单页 / 落地页 / 玩具):**别上这套**,直接 `frontend-design` + `pencil` 画完即走,流程卡/意图卡是过度结构。
- 自检:页面 ≤ 2 或没有跨页共享状态 ⇒ 你不需要 frontend-flow。

## 四段闭环（每段复用哪个现有件）

### 1 · 生成前（PRD + 生成规则 + 组件底座）
- **PRD（产品需求）**：复用 `shape-idea`(漏斗第一步)产出的 `_local/1-brief.md` / `docs/PROJECT-BRIEF-TEMPLATE.md`。前端项目可加**前端 PRD 补充节**(目标用户的设备/场景、核心页面假设、品牌/视觉约束)——模板见 `docs/FRONTEND-FLOW.md`。
- **生成规则（不可漂移的硬约束）**：复用 `frontend/README.md` 的 **5 道闸**(命名 eslint+tsc / 接口单一真相源 / plan-forge consistency / webapp-testing 契约断言 / **design tokens 视觉真相源**)。生成出来的页面**必须**过这 5 闸,尤其颜色/间距走 token、字段从后端契约生成类型——这是「AI 生成的前端不漂移」的治本招。
- **组件底座**：复用 `frontend/` 骨架 + design tokens(下游填)。**业务组件实现不进元层**,本卡只约定底座位置与 token 真相源。

### 2 · 生成中（流程卡 + 意图卡）—— 本卡的核心产物
- **流程卡(页面地图)**：列**页面清单**(路由+用途) | **导航跳转关系** | **跨页共享数据/状态** | **入口/默认页**。模板见 `docs/FRONTEND-FLOW.md`。
  → **流程卡的页面清单喂 `plan-forge`**：每个页面/页面群投影成带 `depends_on` 的任务卡(复用 plan-forge 的 DAG 拆解 + 卡审,**不另造拆解机制**)。流程卡是 UX 视角的页面关系,plan-forge 把它转成施工依赖。
- **意图卡(单页目标,每页一张)**：**目标**(这页让用户能做什么) | **职责边界** | **关键交互** | **数据需求**(读/写哪些 API/字段,接 frontend 闸 2 类型真相源) | **复用哪些 `context/frontend-assets/` 区块** | **验收**(点击→路由,接 eval frontend-behavior)。模板见 `docs/FRONTEND-FLOW.md`。
  → **意图卡用 `grill-design` 拷问产出**：沿设计决策树一次一问、每问给推荐——把每页的目标/数据需求/交互敲定(**复用 grill-design 的交互式拷问机制,不另造拷问**)。意图卡 = grill-design 在前端的产物模板。

### 3 · 生成后（可视化高保真 + 局部 AI 改 + 导出）
- **可视化编辑 / 高保真**：路由到 **pencil MCP**(本环境现成的 `.pen` 可视化设计编辑器)**或 Claude Design / v0** 等生成型工具。**脚手架只做路由,不自造编辑器**(L26:工具可换、举例不绑死单一;选了哪个记一条 lesson 或 ADR)。
- **局部 AI 改 / 视觉矫正**：路由到 `frontend-design`(UI 设计路由卡)/ `taste-skill`(反 slop)。
- **导出**：正常 React(或所选框架)工程——不引入私有产物格式锁定。

### 4 · 资产回流（验证过就沉淀）
- 验证过的**区块/页面模式** → 沉淀到 `context/frontend-assets/`(含元信息:名/用途/依赖哪些 tokens/出处/截图或代码位置/注意事项)。下次做相似页先查这里复用。
- **红线**：沉淀**模式/约定/元信息**,具体业务组件实现仍在 `frontend/`(`context/` = 给 agent 的领域知识,不是组件仓)。呼应 CLAUDE.md「项目内复用资产沉淀归位」。

## 边界（复用非重复 · 必读，防与现有卡重叠膨胀）
- **流程卡 ≠ plan-forge 任务 DAG**：流程卡是**UX 视角的页面关系**(谁跳谁、共享什么状态);plan-forge DAG 是**施工依赖**(谁先建)。前者**喂**后者,不替代。本卡不重造拆解/卡审。
- **意图卡 = grill-design 的前端产物模板**：拷问机制是 grill-design 的,意图卡只是它在「单页设计决策」上的产出形态。本卡不重造拷问引擎。
- **资产库 = `context/` 的前端子集**：`context/frontend-assets/` 是 `context/`(领域知识/约定,入库共享)按前端切的一块,遵 context/ 的一切红线。本卡不另起资产体系。
- **可视化编辑 = 路由到 pencil/Claude Design/v0**：本卡不自造编辑器(同 frontend-design 之于 taste-skill/ui-ux-pro-max:只路由)。
- **生成规则 = frontend/README 5 闸 + tokens 真相源**：本卡不另立前端验收标准,直接指向那份真相源。
- **PRD = shape-idea / PROJECT-BRIEF**:本卡不另起需求收集流程,只加可选的前端补充节。

## 何时用 / 不用
- **用**:复杂多页前端,要先画页面地图、逐页敲意图、再投影成任务卡施工(T2)。触发词见 frontmatter。
- **不用**:后端/通用任务(走 `plan-forge`)、纯需求阶段(走 `shape-idea`)、纯视觉矫正(走 `taste-skill`/`frontend-design`)、简单单页前端(直接 `frontend-design` + `pencil`,别上闭环)。

## 红线
- 不重造任何现有引擎(拷问=grill-design / 审计拆卡=plan-forge / 编辑器=pencil 等 / 验收标准=frontend/README 5 闸 / 资产=context)。新需求先认领既有件,认领不了再说。
- 流程卡/意图卡模板的**唯一真相源是 `docs/FRONTEND-FLOW.md`**,本卡只引不抄(免双源漂移)。
- 业务组件实现不进元层;资产回流只沉淀模式/约定/元信息(见上红线)。
- 工具(pencil/Claude Design/v0)作举例,可换(L26),换了记 lesson/ADR。

> 方法论 + 串联视角在此卡;模板字段与方法论详述见 `docs/FRONTEND-FLOW.md`。
