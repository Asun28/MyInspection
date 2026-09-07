---
id: T4-DESIGN-SYMBOL-CHROME-V2
title: 「符号化 chrome」准入条件收口（承接 T4-DESIGN-SYMBOL-CHROME，含相机面决策与逐行对齐）
depends_on: []
parallelizable_with: []
plan_ref: context/DESIGN.md#components
status: todo
branch: T4-DESIGN-SYMBOL-CHROME-V2
worktree: C:\wt\T4-DESIGN-SYMBOL-CHROME-V2
allow_paths:
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
  - 删除计数的完整数值或其完整播报，或允许把数值折成纯符号
  - 让任何符号化控件失去无障碍名
  - 改动 design token 取值（colors / spacing / typography / motion / iconography 的值）
  - 新增图标依赖或图标资源
  - 改动任何 Kotlin、Gradle、schema 或任务卡以外的文件
  - 在实例代入表（见「必做前置」）完成之前动手写规则正文
non_goals:
  - 落地任何 Schedule / Capture / Report / Camera 界面代码（各自的 UI 卡拥有）
  - 底部导航「每个目的地同时显示 icon 与 label」这条规则本身（DESIGN.md:922，只作为实例被代入验证，不改写）
acceptance:
  - "A1 DESIGN.md carries one named condition set under which a chrome control may omit visible text, stated once, and every clause in DESIGN.md and UI-UX-ELEMENTS.md that governs a symbol-only control resolves to it by reference rather than restating its conditions"
  - "A2 the card body carries a completed instance-substitution table: every existing instance the rule governs is listed, each is evaluated against the final wording, and each row records compliant / requires-restatement / explicitly-exempt with its reason"
  - "A3 no instance in that table is left indeterminate: no instance satisfies two mutually exclusive branches of the rule, and no instance the document already permits is judged non-compliant by the rule"
  - "A4 the amended count clause still requires every count to carry its numeral and to announce its full value, and no longer forbids a symbol-only badge in terms that contradict the state-badge DOT variant"
  - "A5 the amended status clause still forbids colour from being the sole carrier of state, and states its satisfying carriers rather than requiring a visible text label unconditionally"
  - "A6 the camera surface is decided one way and stated once: either the camera rows carry a tooltip and their anatomy says so, or the tooltip requirement is scoped so those rows satisfy it as written; no camera row is left contradicting the rule"
dod_command: TBD（措辞定稿后钉具名 ASCII 锚点；设计规矩见本卡「DoD 锚点设计规矩」，**每个锚点在被查文件里只许出现一次**）
dod_exit: 0
dod_assert: TBD（同上，与 A1–A6 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；每个 DoD 锚点在被查文件里只出现一次，否则单点删除杀不掉它；变异批钉生产文件 SHA-256，批中不并行跑第二批或独立复核（L196）。
doc_sync: CLAUDE.md「权威文档」21 行与 TASK-BOARD 记录本卡合并 OID；解锁 T4-SCHEDULE-UI-PRESENTATION。
---

# T4-DESIGN-SYMBOL-CHROME-V2

## 起因：承接被退役的 `T4-DESIGN-SYMBOL-CHROME`（2026-09-06 用户裁定）

前卡跑了 **6 轮 R3、11 条 finding，全部属实**，两次经用户裁定 `-ResetRounds`，仍未收敛。
**11 条里没有一条是实现缺陷**——全是同一类：**新写的中心规则与文档里既有实例不符**，
每轮修完措辞、下一轮就在另一处冒出新缝。用户裁定关闭 PR #236、把整件事折进本卡重做。
经验已入账 **L300**（成熟文档加中心规则须先做实例代入表）。

**前卡的产出不作废**：分支 `T4-DESIGN-SYMBOL-CHROME`（PR #236，已关闭但分支保留）里有一份
经 6 轮打磨的草案，其**条件集骨架与各条款的解析式改写可直接复用**，只有「chrome / domain content
边界」那一段是坏的（见下）。前卡卡片记录了逐轮 finding 与修法，是本卡最有价值的输入。

## 必做前置：实例代入表（写规则正文之前，`forbid` 第 7 条）

**这是本卡与前卡唯一的方法论差别，也是它存在的理由。** 前卡的病根是：规则的每一句声称都在对整份
文档做全称断言，而我只对着「开卡时盘点出的那几处冲突」验证过它。故本卡**先建表、后写规则**：

1. 用**不变量**而非症状词 grep（前卡第 1 轮的教训：搜 `icon-only` / `纯图标` 只找到 4 处，
   真正该找的是「status + label + icon 的合取」「计数 + 播报」「字形 + 无障碍名」这些**不变量**）。
2. 列出**每一个**受该规则管辖的既有实例，逐个代入拟定措辞算一遍：它合规吗？按规则它该长什么样？
   与它自己那一行冲突吗？
3. 冲突的当场消解或**显式豁免并写明理由**；不留「以后再说」。
4. 规则里每出现一次全称词（every / never / all / 一律），回头核一遍该全称在文档里是否真成立。

**表至少须覆盖下列实例**（开卡时重跑 grep 补全，勿照抄本清单当全集）：

| 实例 | 现状要点 | 前卡在这里栽过 |
|---|---|---|
| `:922` `Settings` 未标注错误点 | 记录派生的二元状态，文档明写 `unlabelled` | 是（第 6 轮） |
| `:922` 底部导航 icon+label | 更严的合同，压在准入之上 | — |
| `:922` `Schedule` 计数徽标 `1`–`99+` | 计数 + 视觉截断 | — |
| `state-badge` `COUNT / DOT / STATUS / SOURCE` | `announce the full count`；`dots require an owning row label`；`Merged into owner` | 是（第 2/3/6 轮） |
| 计数条款（产品语言合同） | 复数短语 + 数值 + 全额播报 | 是（第 5 轮：播报保证被缩到 `state-badge`） |
| `camera-control` | 24dp 图标，anatomy **无 tooltip**；语义含 current state | 是（第 3/4 轮） |
| `camera-shutter` | 72dp 圆，无障碍名 `Take photo`，**无 tooltip** | 是（第 3/4 轮） |
| `camera-overlay-control` | `Switch` + slider | 是（第 4 轮） |
| `icon-button` | anatomy **含** tooltip；变体含 `CAMERA` | 是（第 4 轮：`CAMERA` 变体被读成替相机组件背书） |
| `tooltip` 行 | 曾三度复述条件正文 | 是（第 2/3/4 轮） |
| `:1414` Colors 章状态句 | 原文「Pair every status with a label and stable symbol」+ 符号词汇表 | 是（第 1 轮漏掉） |
| `:1826` Do's and Don'ts 状态句 | 颜色不得唯一承载状态 | — |
| `:1756` 「图标不得替代这些动作的标签」 | capture/compliance/privacy/delete/finalize/backup/restore | 是（第 3 轮：原样搬进规则 ⇒ 与 `camera-shutter` 抵触） |
| `:1707` 房间进度段「不得缩成无标签的点」 | 更严的合同 | — |
| `notice-delivery-row` | `Status is text + icon` | — |
| `status-choice` / `privacy-chip` / `privacy-action` | 有可见标签；无障碍名含当前状态 | 是（第 4 轮：条件 3 与「名字里带状态」抵触） |
| `metadata-row` / `summary-stat` | 不得单以颜色编码状态 | — |
| `docs/UI-UX-ELEMENTS.md:35` 纯图标操作 | 已给准入条件（tooltip + 无障碍名） | — |
| `docs/UI-UX-ELEMENTS.md:122` 主题清单第 5 条 | 原文「仍有文本和图标，不靠颜色」 | 是（第 1 轮漏掉） |

## 已知陷阱（前卡 11 条 finding 的浓缩，勿重犯）

1. **边界判据别自造**。前卡第 4 轮我发明了「取值读数是否超过两种」的基数判据，第 6 轮就被证伪：
   一个**记录派生的二元状态**同时满足「领域内容保留文字」与「二元标记可为字形」两边 ⇒ 规则不可判定。
2. **别把某处的清单原样搬进中心规则**。第 3 轮把 `:1756` 的动作清单搬进准入段，于是规则自称
   「capture 动作保留可见标签」，而 `camera-shutter` 正是纯符号的 capture 控件。
3. **延期必须显式豁免**。第 4 轮：相机面已进 `non_goals`，但条件 3 仍然绑着它们——
   一边说「不管」一边判它们不合规。延期只有在规则点名豁免时才自洽。
4. **矩阵行只许引用、不许复述**。`tooltip` / `state-badge` 两行连着三轮因复述条件正文被拦。
   判据：一行只有在**复述了准入条件的正文**时才须改；组件**特有**事实（角色、焦点键、live region、
   具体标签值）一律保留。
5. **收窄一句声称，就回头查所有引用该声称的句子**。ship 前自审抓到过一次：加了相机豁免段之后，
   「所有条款都解析到本集」那句立刻自相矛盾。
6. **全称词是风险点**。every / never / all 每出现一次，就是一次对整份文档的断言。

## 诊断与建议正解（前卡第 6 轮留下，**本卡须先验证再采用**）

文档里真正成立的规则**不是基数，而是载体**：`:922` 的错误点之所以可以无标签，是因为它
`Merged into owner`、拥有者本身有可见标签；`state-badge` 的「dots require an owning row label」
是同一件事。故拟定正解：

> **领域值永不由字形单独承载；字形可以额外标记它，只要拥有者以文字或播报给出该值。**

一条规则同时覆盖计数、巡检项状态、`Settings` 错误点与 `DOT`，且删掉「基数」这个自造判据。

> **未验证的一步（务必先查）**：该正解要求「拥有者以文字或播报给出该值」。文档目前**没有明说**
> `Settings` 目的地的无障碍名会陈述本机健康状态——它的可见标签只是 `Settings`。若确实没有，
> 本卡要么补上这一句（属本卡范围），要么换措辞。**这正是实例代入表该抓出来的东西，别跳过。**

> **评审者在前卡给的备选（「所有记录派生值一律保留可见文字」）在本文档里不成立**：
> 它会判 `:922` 的 unlabelled error dot 违规。已实测该行未被前卡 diff 触碰、原文确写 `unlabelled`。

## DoD 锚点设计规矩（前卡验证有效，沿用）

- 锚点一律 **ASCII**（L165）；**每个锚点在被查文件里只许出现一次**，否则「删掉被改写的那一句」
  杀不掉它、变异必然存活。前卡靠这条把 9 枚变异全部做成可击杀。
- 大小写敏感可用来给两个形态各留一个坑位（前卡：标题形态 `Symbol-only chrome` 在 DESIGN.md 只出现在
  标题上，正文与跨文档引用各用不同形态）。**但这属于权宜**：本卡若能用天然唯一的整句作锚点更好。
- 正锚点断言「解析式措辞在」，负锚点断言「被替换的复述措辞不在」，两侧都要有。
- 收紧 `dod_command` 只许加、不许减；每次加完实测「master 基线仍 RED、本卡工作树 GREEN」。

## 未决决策

| ID | 问题 | 选项 | 影响 |
|---|---|---|---|
| OD-1 | 相机面控件是否携 tooltip？ | (a) 加 tooltip 并改 `camera-control` / `camera-shutter` anatomy (b) tooltip 要求按组件 anatomy 是否声明而绑定 | A6；(a) 改 `T2-CAPTURE-UI` 将实现的合同、且相机面长按与拍摄手势有冲突风险，(b) 不改任何 anatomy 且忠于基线 `:1622`（对相机动作原本只要求 accessibility text） |
| OD-2 | 「载体」正解里，`Settings` 目的地是否须在无障碍名中陈述本机健康状态？ | (a) 是，本卡补这一句 (b) 否，改用别的措辞让 `:922` 天然合规 | 决定正解能否原样采用；须由实例代入表先给出事实 |

> **OD-1 与 OD-2 必须在写 RED 之前由用户裁定**：二者共同决定规则的最终措辞，也决定 DoD 锚点选哪几句。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 建卡：承接被退役的 `T4-DESIGN-SYMBOL-CHROME`（6 轮 R3 / 11 条 finding 全属实但未收敛，用户裁定关 PR #236 重做）。合并原 `T4-DESIGN-SYMBOL-COMPONENT-ROWS` 的相机面与逐行对齐范围（该卡同日撤销，未曾开工）。新增「必做前置：实例代入表」为本卡与前卡的唯一方法论差别（L300）。 |
