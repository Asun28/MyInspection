---
id: T4-DESIGN-PAIR-USAGE-DUTIES
title: 一个配对可以同时承担多种职责，而 `usage` 是单值：40 个状态字形配对里 14 个被别的职责占了标签
depends_on: [T4-DESIGN-NOT-APPLICABLE-COLOR]
parallelizable_with: []
plan_ref: context/DESIGN.md#colors
status: todo
branch: T4-DESIGN-PAIR-USAGE-DUTIES
worktree: C:\wt\T4-DESIGN-PAIR-USAGE-DUTIES
allow_paths:
  - context/DESIGN.md
forbid:
  - 改动 design token 的色值，或新增一个调色板 role
  - 增删任何前景/背景配对（本卡只改职责标注，不动配对集合，也不动任何实测比值或 `minRatio`）
  - 删掉某个配对已有的职责标注（职责集合只许加、不许减）
  - 弱化「每个被渲染的配对必须有精确条目、缺则构建失败」这条 fail-closed 下限
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 重开 `OPTIONAL` 与 `NOT_APPLICABLE` 共用 `outline` 这一判断（`T4-DESIGN-NOT-APPLICABLE-COLOR` 已由用户裁定）
  - 改动对比度阈值本身（`3.00:1` 非文本档 / `4.50:1` 文本档均不动）
  - 给尚未登记的配对补登记（配对集合在本卡是**只读输入**）
acceptance:
  - "A1 a consumer that asks the metadata which pairs carry a given duty gets the same set the prose elsewhere declares for that duty, so the state-glyph duty answers with all forty pairs the state glyph contrast map names rather than only the twenty-six that happen to be labelled state-icon, and the evidence-segment duty answers with all eight rail segment pairs rather than only six"
  - "A2 the one-entry-per-pair invariant survives the change: multiplicity is expressed by making one entry's duty field multi-valued, not by admitting a second entry for the same pair, so the existing duplicate-pair detection keeps working unchanged"
  - "A3 every duty a pair carries is derived from a statement the document already makes, and no pair loses a duty it was already labelled with, so the change is purely additive and each added duty can be traced to the clause that assigns it"
dod_command: TBD（措辞与 schema 形态定稿后钉具名 ASCII 锚点，并复用前卡 DoD 的机检骨架：从 frontmatter 重算 token/绑定/配对覆盖与比值，证明本卡未增删任何配对、未动任何比值；再逐职责重算「散文声明的集合 == 元数据答出的集合」）
dod_exit: 0
dod_assert: TBD（同上，与 A1–A3 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256，且每枚植入后须断言 mutant != baseline、条目元数自检（L196/L270/L319）。另须有一枚变异证明「职责集合只许加不许减」这条守卫活着。
doc_sync: TASK-BOARD 记录合并 OID；`specs/tech-debt-tracker.md` 的 TD175 转 paid（本卡是其偿还卡）。
---

# T4-DESIGN-PAIR-USAGE-DUTIES

## 起因：`T4-DESIGN-NOT-APPLICABLE-COLOR` 的 `[FOLLOW-UP]`（TD175，2026-09-08）

前卡把 `NOT_APPLICABLE` 定为与 `OPTIONAL` 共用 `outline` 做**段色**。ship 前的全新上下文复核指出：
`outline` on `surface-container` 这个配对的 CI 条目标 `"usage":"evidence-boundary"`，而同底的另三个
段色（`primary`/`tertiary`/`error`）标 `"usage":"evidence-segment"`——同一条 rail 上的五个段态，
四个 role，标签却分属两类。前卡按 `[FOLLOW-UP]` 记账（TD175）而未就地收口，理由是：
`context/DESIGN.md` 的「Every rendered foreground/background pair … has one metadata entry」**只许一条条目**，
而一个 `usage` 字段只能命名一种职责——改标签会让另一种职责失准，真正的收口需要 schema 决定。

**开卡时重新测量，发现缺口比 TD175 写的那一处大得多**（下节），故本卡不叫「改一个标签」，
而是「让职责标注能表达一个配对同时承担多种职责」。

## 缺口的确切形态（测量口径见下节，勿照抄、实施前须复跑）

`State glyph contrast map` 一节明写：五个状态色 × 四个内容底 = **40 个配对全部**在两个主题上登记于
`3.00:1` essential-icon 档。但元数据里只有 **26** 条标着 `"usage":"state-icon"`。差额 14 条并非漏登记
——它们都在，只是**标签被另一种它同样承担的职责占了**：

| 配对 | 现有标签 | 它同时承担的状态字形职责来自 |
|---|---|---|
| `light.primary` / `dark.primary` on `surface` | `focus` | `State glyph contrast map`（`primary` × `surface`） |
| `light.primary` / `dark.primary` on `surface-container` | `evidence-segment` | 同上 |
| `light.tertiary` / `dark.tertiary` on `surface-container` | `evidence-segment` | 同上 |
| `light.error` / `dark.error` on `surface-container` | `evidence-segment` | 同上 |
| `light.outline` / `dark.outline` on `surface` | `boundary` | 同上 |
| `light.outline` / `dark.outline` on `surface-container-low` | `card-boundary` | 同上 |
| `light.outline` / `dark.outline` on `surface-container` | `evidence-boundary` | 同上 |

**反方向同样漏**：`evidence-rail` 的段色是四个 role 落在 rail 自己的底 `surface-container` 上 =
每主题 4 对、共 **8** 对；但标 `evidence-segment` 的只有 **6** 条——缺的正是 `outline` 那两条
（它们被 `evidence-boundary` 占了）。于是：

- 问「哪些配对是状态字形？」→ 元数据答 **26**，文档散文答 **40**（少 35%）。
- 问「哪些配对是证据段？」→ 元数据答 **6**，rail 契约答 **8**。

**当前无行为缺陷**：闸只要求「该配对存在一条精确条目」，从不比对 `usage` 的语义，故构建不会因此失败。
风险是**读元数据判职责的人（或将来任何据 usage 分流的检查）会系统性漏掉重叠的那一半**。

## 测量口径（必须声明，否则本卡的数字对不上它落地的那棵树）

上表的 40 / 26 / 8 / 6 全部测自**本地 master `d6b369ee`**（`T4-DESIGN-STATUS-CARRIERS` 与
`T4-DESIGN-NOT-APPLICABLE-COLOR` 均已合并的那棵树）。**`origin/master` 落后**：截至本卡建卡时它
只有 49 条绑定、**`state-icon` 这个 usage 值一条都没有**、40 个状态字形配对里只登记了 14 个
——因为上述两张卡尚未推到远端。

**推论（也是 `depends_on` 的实义）**：本卡**不能**在缺少那两张卡的树上实施——那里根本还没有
「40 对全登记」这个声明，也就无从谈「标签漏了其中 14 对」。实施前第一步是确认工作树里
`State glyph contrast map` 一节存在且 `"usage":"state-icon"` 计数为 26；不符即停、先同步。

## 未决决策（开工前须收口）

**OD-1 · 多职责怎么表达？**

- **推荐 A：把单值 `usage` 换成多值 `usages`（数组），`schemaVersion` 2 → 3。**
  一个配对仍**恰好一条**条目，故 A2 要求的「重复配对检测不受影响」自动成立；职责变成集合后，
  「问某职责要哪些配对」是一次过滤，两个方向的漏计一起消失。代价：所有读 `usage` 的地方要改，
  且**已合并卡的 DoD 收据里那些数 `"usage":"state-icon"` 的锚点会失配**（见下「迁移面」）。
- 备选 B：保留 `usage` 作「主职责」，另加一个派生的职责索引表。**不取**：造出第二个权威，
  正是本仓反复吃亏的形态（单一真相源）。
- 备选 C：允许同一配对多条条目、每条一个 usage。**不取**：直接违反「每配对恰好一条」，
  且会让现成的重复配对检测（本仓多张卡的 DoD 都在用）从守卫变成噪声源。

**OD-2 · 职责集合从哪里派生，谁来兜底？**
每条职责必须能指回文档里赋予它的那一句（A3）。开工前要定：是**逐条手写**职责集合并由 DoD 逐条
比对散文，还是让文档声明「职责 → 配对集合」的规则、由 DoD 从规则重算。**倾向后者**：前卡的经验是
「从 frontmatter 重算」比「比对字面量」结实得多，而 `State glyph contrast map` 与 rail 的段色契约
本来就已经是可重算的规则。

## 迁移面（开工前 grep 一次，别等 R3 逐轮外溢 · L97）

改 `usage` 的形态会波及**引用过该字段的已合并卡收据**。已知至少：
`T4-DESIGN-NOT-APPLICABLE-COLOR`（锚点 `"usage":"state-icon"` 计数 26）与
`T4-DESIGN-STATUS-CARRIERS`（同一锚点）。二者都已归档，其 `dod_command` **不再被任何闸重跑**
（`check-cards.ps1` 只非递归扫 `specs/tasks/*.md`），故这不是「会变红的闸」，而是
**历史证据与当前文件不再对应**的记账问题。开工前须确认这份清单是否完整，并决定记账方式
（倾向：在本卡交付记录里点名列出受影响的收据与其失配原因，不回改已归档卡）。

## 方法

1. 沿用前三张设计卡验证有效的三条：代入表要覆盖被改写的**周边**条款；新句与新句之间交叉核对；
   规则有两半就两半同时判。
2. **不写全称句**（L309 已复发三次，L321 是它的姊妹条）：要说的是「`State glyph contrast map`
   声明的那 40 对，每一对的职责集合都含状态字形」这种**有界**断言，不是「文档里每个配对的职责都完整」
   这种对整份元数据的断言——后者对 `text` 那 30 条立刻不可证。
3. **镜像别处的句子时把限定语一起带上**（L321）：本卡会大量改写职责相关措辞，最容易犯的错就是
   把某处有范围的陈述抄成全称句。
4. DoD 除锚点外**复用前卡的机检骨架**：从 frontmatter 重算 token 表、绑定集、配对覆盖与比值，
   证明本卡**没有增删任何配对、没有动任何比值**（选 A 时这些数应当逐字不变）。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-09 | 建卡：承接 `T4-DESIGN-NOT-APPLICABLE-COLOR` 的 `[FOLLOW-UP]`（TD175）。开卡时复测发现缺口比 TD175 记的那一处大：40 个状态字形配对里 14 个的标签被另一种同样承担的职责占了，反方向 rail 段色 8 对里只有 6 对标着 `evidence-segment`。测量口径（本地 master `d6b369ee`）与 `origin/master` 的落后状态已显式声明。 |
