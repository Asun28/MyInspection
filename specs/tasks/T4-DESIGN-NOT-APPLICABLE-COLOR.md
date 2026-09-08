---
id: T4-DESIGN-NOT-APPLICABLE-COLOR
title: NOT_APPLICABLE 的破折号没有声明前景色：evidence-rail 五个段态只有四个色
depends_on: [T4-DESIGN-STATUS-CARRIERS]
parallelizable_with: []
plan_ref: context/DESIGN.md#colors
status: todo
branch: T4-DESIGN-NOT-APPLICABLE-COLOR
worktree: C:\wt\T4-DESIGN-NOT-APPLICABLE-COLOR
allow_paths:
  - context/DESIGN.md
forbid:
  - 改动 design token 的色值，或新增一个调色板 role
  - 改动 `Symbol-only chrome` 节的准入条件与载体规则（前前卡已验收）
  - 改动 `State glyph contrast map` 已登记的 20 个 状态色 × 内容底 配对或其实测比值（前卡已验收）
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 给 `status-choice` / `compliance-check-row` 的各变体补色（它们**所有**变体都不声明颜色，NOT_APPLICABLE 在那里并不特殊，属另一类问题）
  - 重开 `OPTIONAL` 与 `NOT_APPLICABLE` 是否应当同色这一设计判断以外的任何 rail 语义
acceptance:
  - "A1 every state that `evidence-rail` declares in `segmentStates` resolves to exactly one declared color, so the component no longer names five segment states and four segment colors"
  - "A2 the not-applicable dash the status clause makes mandatory has one declared foreground stated once, and that foreground is one the contrast gate already registers on every content surface, so the amendment introduces no new pair"
  - "A3 the rail prose and the component row agree with the frontmatter on which states share a color, with no state left described only by a word that is not one of its declared state names"
dod_command: TBD（措辞定稿后钉具名 ASCII 锚点，并复用前卡 DoD 的机检骨架：从 frontmatter 重算/核对绑定与配对覆盖，证明本卡未引入未登记配对；每个锚点在被查文件里的出现次数逐条钉死）
dod_exit: 0
dod_assert: TBD（同上，与 A1–A3 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256，且每枚植入后须断言 mutant != baseline、条目元数自检（L196/L270/L319）。
doc_sync: TASK-BOARD 记录合并 OID。
---

# T4-DESIGN-NOT-APPLICABLE-COLOR

## 起因：`T4-DESIGN-STATUS-CARRIERS` ship 前本地对抗复核记下的 `[FOLLOW-UP]`（2026-09-08）

前卡把 状态色 × 内容底 的 40 个配对全部登记进对比度闸，于是「必带状态字形落在哪个中性底上」
不再是问题。ship 前的全新上下文复核指出了它的**对偶缺口**：那个字形**用哪个前景色**，对
`NOT_APPLICABLE` 而言全文没有答案。该发现被判为 `[FOLLOW-UP]` 而非阻断，理由是给一个态指派
role 颜色属**调色决定**，落在前卡「只补登记、不调色」的范围之外、且贴着其 `forbid` 第三条。

## 缺口的确切形态（开卡时已逐处核实，勿照抄、仍须复跑）

**这不是 WCAG 1.4.1 缺口。** 每一处渲染 not-applicable 的地方都已有非颜色载体：item card 的
`NOT_APPLICABLE` 行是「Dash, explicit label, `Change`」（破折号**加**显式文字标签），rail 段的
破折号是形状而非颜色。下限不受影响，本卡也不得借此松动它。

缺的是**颜色绑定的完整性**，而它现在有了机检后果：前卡把状态条款改成「必带状态字形渲染在
**对比度闸已登记的**前景/背景配对上」——要判一个配对是否已登记，就得先知道前景是哪个 token。
对 `NOT_APPLICABLE` 这一步答不出来。

| 处 | 原文事实 |
|---|---|
| `evidence-rail` frontmatter | 声明 `completeColor` / `missingRequiredColor` / `blockedColor` / `optionalColor` **四个**颜色，而 `segmentStates` 是 `COMPLETE, MISSING_REQUIRED, BLOCKED, OPTIONAL, NOT_APPLICABLE` **五个**态 |
| rail 散文段 | 「A complete segment uses primary; missing-required evidence uses amber; a compliance-blocked segment uses red; **optional/irrelevant** evidence uses neutral with a dash.」——`irrelevant` **不是**任何一个声明过的状态名，故它是否指 `NOT_APPLICABLE` 靠读者推断 |
| 组件行 `evidence-rail` | 变体列逐字列出五个态，行内不提颜色 |
| 状态条款 | 「Where a glyph marks OK, attention, blocked, **not applicable** or privacy, it uses the declared symbol: … **dash for not applicable** …」——钉死了字形，没钉前景 |
| item card 状态表 | `NOT_APPLICABLE` 行「Dash, explicit label, `Change`」——有载体，无颜色 |

**范围外的近邻，别顺手改**：`status-choice` 的变体含 `NOT_APPLICABLE`、`compliance-check-row`
的状态含 `not applicable`，但这两个组件对**所有**变体都不声明颜色，`NOT_APPLICABLE` 在那里
并不比 `OK` 更缺——那是「组件不声明配色」的另一类问题，不在本卡（见 `non_goals`）。
另注：`:1770` 的「If a state cannot occur, the page contract marks it `NOT_APPLICABLE`」讲的是
**页面契约的元标注**，不是被渲染的状态，别把它一起卷进来。

## 未决决策（开工前须收口）

**OD-1 · `NOT_APPLICABLE` 用哪个前景？**

- **推荐 A：明说它与 `OPTIONAL` 共用 `optionalColor`（即 `outline`）。** 与 rail 散文
  「optional/**irrelevant** evidence uses neutral with a dash」的既有意图一致；`outline` 已由前卡
  在两个主题的四个内容底上全部登记，故**零新绑定、零新配对、零调色**，A2 自动满足。落地形态可以是
  把 `optionalColor` 的语义写清（它服务哪些段态），而不是新增一个 `notApplicableColor` 键。
- 备选 B：给它自己的 role 与色值。需要新 token + 新绑定 + 新实测比值，直接撞 `forbid` 第一条，
  且没有任何证据说明「可选」与「不适用」在 6dp 宽的 rail 段上需要被颜色区分（二者都已带破折号，
  且 rail 整条合并成一个 TalkBack 节点播报）。**除非用户明确要求区分，否则不取。**

选 A 时仍须**显式写下来**：本卡的价值正是把「靠读者把 `irrelevant` 推断成 `NOT_APPLICABLE`」
换成一句可被机检的声明。

## 方法

1. 沿用前卡三条：代入表要覆盖被改写的**周边**条款；新句与新句之间交叉核对；规则有两半就两半同时判。
2. **不写全称句**（L309，前卡在这条上栽了三次、其中两次靠复核才抓到）：要说的是
   「`evidence-rail` 的每个 `segmentStates` 都解析到一个已声明颜色」这种**有界**断言，
   不是「文档里每个状态都有颜色」这种对整份文档的断言——后者对 `status-choice` 立刻为假。
3. DoD 除锚点外**复用前卡的机检骨架**：从 frontmatter 重算绑定/配对覆盖，证明本卡没有引入
   任何未登记的前景/背景配对（选 A 时该数应当逐字不变）。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 建卡：承接 `T4-DESIGN-STATUS-CARRIERS` 的 `[FOLLOW-UP]`（其 ship 前本地对抗复核发现）。缺口已逐处核实并写明「这不是 WCAG 缺口、是颜色绑定完整性缺口」，近邻的 `status-choice` / `compliance-check-row` 显式划出范围外。 |
