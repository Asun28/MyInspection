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

> 以下为原工作树的本地设计验证与评审历史，原文保留；不是当前远端验收结论，也不代表设计已发布。当前有效状态仍由本卡 frontmatter 与后续远端交付决定。

## R4 变异收据（2026-09-08 · R3 第 8 轮修复后重跑 · 25/25 KILLED）

被测基线 SHA-256（收据钉这两个文件的确切字节，此后任何改动即作废本批）：
`context/DESIGN.md` = `D637672CF820E1BFD407FA51940D62DE1E7D499975053D8B282C59DD45DCF82F` ·
`docs/UI-UX-ELEMENTS.md` = `F1364E4823FA7E3ED41CCC8E7F5C4D51FE27E4822EC1EEF6E60AF4CDBF497BA8`。
基线 `dod_command` 退出 **0**（GREEN）；25 枚跑完后两文件 SHA 逐一回到上列基线、退出仍 0。
每枚植入前断言靶串在文件内**恰好出现 1 次**（不符即作废该枚，L190）、断言渲染后文本与 SHA 均已改变
（防 no-op 冒充击杀，L297）、断言植入前文件仍等于基线（L196）。**无一枚 VOID。**

RED 证据取自真实基线：`-Phase red` 首次运行读到的是主检出卡片里尚未定稿的 `TBD` 字面量，
非零退出由 PowerShell 解析错误产生、**不构成 RED 证据**（L282）；卡片 `dod_command` 定稿后重跑，
19 条锚点逐条点名失败、3 条「保留项」锚点在基线即为绿，才是真 RED。

| # | 断言 | 文件 | 单点变异 | 判定 |
|---|---|---|---|---|
| M1 | A1 | DESIGN | 改掉声明具名条件集的标题行形态 | KILLED |
| M2 | A1 | DESIGN | 准入开场句去掉 only-when 限定 | KILLED |
| M3 | A2 | DESIGN | 载体规则 never → not | KILLED |
| M4 | A5 | DESIGN | 状态条款 sole → only | KILLED |
| M5 | A5 | DESIGN | 把该串再加回 `state-badge` 行（令其不再单点） | KILLED |
| M6 | A6 | DESIGN | 条件 3 去掉 component-anatomy 限定 | KILLED |
| M7 | OD-2 | DESIGN | 删掉 Settings 的健康状态播报 | KILLED |
| M8 | A4 | DESIGN | 计数保证 its → the | KILLED |
| M9 | A4 | DESIGN | 计数条款去掉 `plural-aware` | KILLED |
| M10 | A4 | DESIGN | `state-badge` 去掉 `full` | KILLED |
| M11 | A6 | DESIGN | 删掉 `:1748` 的具名相机例外 | KILLED |
| M12 | A1 | DESIGN | `state-badge` 去掉 owner 表达义务 | KILLED |
| M13 | A5 | DESIGN | Do 条 besides → beyond | KILLED |
| M14 | A4 | DESIGN | 还原无条件禁令 `or icon-only badges` | KILLED |
| M15 | A6 | DESIGN | 还原断言相机动作带 tooltip 的旧 `tooltip` 行 | KILLED |
| M16 | A1 | DESIGN | 还原 `:894` 第 5 条的条件复述 | KILLED |
| M17 | A1 | DESIGN | 还原 `icon-button` 行的条件复述 | KILLED |
| M18 | A5 | DESIGN | 还原「Pair every status with a label」无条件要求 | KILLED |
| M19 | A5 | DESIGN | 还原旧 Do 条 | KILLED |
| M20 | A1 | ELEMENTS | `:35` 去掉跨文档具名锚点 | KILLED |
| M21 | A1 | ELEMENTS | `:123` 去掉解析引用 | KILLED |
| M22 | A1 | ELEMENTS | 还原 `:35` 未按相机合同收窄的旧选型句 | KILLED |
| M23 | A3 | DESIGN | 还原「无障碍名须为 verb-object 短语」这条全称（`More options` / `Save and exit` 正违反它） | KILLED |
| M24 | forbid#1 | DESIGN | 状态条款删掉 `visual` 一词（播报即可满足下限——正是 R3 第 5 轮拦下的那个洞） | KILLED |
| M25 | forbid#1 | DESIGN | 删掉「必带状态字形只渲染在对比度闸已登记的配对上」这条约束 | KILLED |

M1/M20/M21 证明具名锚点由定义处与两处跨文档引用各自独占承载；M14/M15/M16/M17/M18/M19/M22
证明七处被替换的复述措辞两侧都在测；M5 与 M12 成对证明「唯一承载」而非「存在即可」。
变异脚本不入库（跑在 scratchpad，不属 `allow_paths`）。


## R3 第 1 轮（2026-09-07 · block · 3 条 finding 全部属实、全部当场修）

三条同属一类，也正是退役卡的病根：**中心规则里的全称词，被文档既有实例证伪**。
我的实例代入表按组件行代入过，却漏了两层——**具体已声明的标签取值**，以及**选型类条款**。

| # | finding | 属实性 | 修法 |
|---|---|---|---|
| 1 | 条件 2 要求 verb-object 短语，而 `More options`（`:1643`）与 `Save and exit`（`:880`）都不是 | 属实。二者在**基线里就已违反** `:894` 原有的「verb + object」措辞，非本卡引入 | 条件 2 改为「名字须命名它执行的动作」并明写不设短语语法；`tooltip` 行同步去掉 verb-object 字样。新增 DoD 锚点 `names the action it performs` + M23 钉住 |
| 2 | `UI-UX-ELEMENTS.md:35` 要求纯图标操作一律用 `icon-button`，而相机两个组件是独立合同、`camera-shutter` 的 base 是 Custom `Button`，且本卡刚写明 `CAMERA` 变体不外扩准入 | 属实，且是本卡自己制造的新缝：写下「变体不外扩」的同时没回头看选型句 | `:35` 把选型收窄到普通界面，点名相机两个合同不套用，三者准入统一路由到本节 |
| 3 | 状态条款写「glyph、position 与 text 三者**together**」，与载体规则允许的「owner 以文字**或播报**给出」及 Do 条允许的「载体是字形而非文字」互相矛盾，令 `Settings` 点与 evidence rail 不可判定 | 属实。ship 前自审已改掉尾句与 Do 条两处，**却漏了主句里的合取** | 主句改为列举允许的载体：字形 + 位置，加上「自己的可见标签 **或** owner 给出的文字或播报」，并显式解析到载体规则 |

**教训（比三条 finding 本身更值钱）**：实例代入表若只代入到**组件行**，会漏掉两类实例——
① 文档在别处**已声明的具体取值**（一个无障碍名、一个标签字面量）；
② **选型/归属类条款**（「必须用哪个组件」），它同样在管辖符号化控件。
下次建表时这两层要单独扫一遍：grep 已声明的标签字面量，grep「必须使用 / 一律用 / 归 X 承载」这类选型措辞。

## R3 第 2 轮 + 本地对抗复核（2026-09-07）

### R3 第 2 轮（block · 1 条 finding · 属实）

状态条款仍要求**每个状态都有字形**，而九个合同声明状态时没有强制字形
（`metadata-row` 与 `summary-stat` 的图标原文就写着 optional，另有 `property-summary-card`、
`result-list-row`、`verification-receipt`、`history-evidence-strip`、`destination-row`、
`health-issue-row`、`compliance-check-row`）。**这条 finding 由第 1 轮的修法引入**。

**该条款至此已被两种不同形态各证伪一次**（第 1 轮：合取里要求文字；第 2 轮：全称要求字形）——
按 L309 自己的识别信号，**停手别补第三次措辞**，改为把 17 个承载状态的合同逐个列出、
从实例反推出析取式，而不是再猜一句。

### 本地对抗复核（fresh-context 子代理，L205；不烧 R3 轮次）

第 2 轮修法落地后**先在本地跑一次对抗复核**再 ship。两轮复核共出 9 条，7 条属实：

| 条 | 内容 | 处置 |
|---|---|---|
| 1 | `room-progress-segment` 的 `BLOCKED` 只由 state mark 字形承载，而 owner 的播报串只给 label/计数/current | 属实（与 Settings 点同类的基线缺口）。`room-progress-strip` 播报补 blocked |
| 2 | 条件 2 指向「文档已声明的那些名字」，而 capture 的 Back 有 `Save and exit`(`:880`) 与 `Back to {parent}`(`:1590`) 两个声明 | 属实。删掉该指代句；**两名之争属基线既有矛盾、非本卡引入**，且被替换的旧 `icon-button` 行本就带同一依赖 → 记 `[FOLLOW-UP]`，不在本卡收口 |
| 3 | 状态条款把「a declared glyph」列为独立充分通道，而同一 diff 的载体规则写着「领域值永不由字形单独承载」——**两条新句自相矛盾** | 属实且最重。改为「颜色不是唯一通道，且状态不由字形单独承载」，通道列表去掉裸字形 |
| 4 | `camera-control` 等行仍在复述条件 2/5，而新句声称「一律解析、不复述」 | 属实。声称收窄为「就这些条件解析到本节，组件行仍陈述自己特有的事实」 |
| 5 | 称 `camera-control` 的 `IMPORT` 变体使「唯一具名例外」少算 | **驳回**：`:1748` 的七个词是 capture/compliance/privacy/delete/finalize/backup/restore，import 不在其中，且文档通篇把 Camera 与 Imported 当两种来源（`:1133` `:1585` `:1688` `:1774`）。复核者查证后撤回该条 |
| 6 | 更严合同的三例读起来像封闭全集，而另有四个合同独立强制可见文字 | 属实。改成「规则 + 举例」，并在第二轮复核指出**规则只护「组件行」而 `:1764` 是散文**后再放宽为「组件行或其他条款」 |
| 7 | `:691` 原句只许 icon 或 label 作第二通道，而 rail 的实际规格是颜色 + owner 播报 | 属实。首轮改成指向本节，**第二轮复核指出这个指向落空**（rail 不是控件，三个段态也无字形，真正管它的是 `:1420`）→ 最终只保留「不由颜色单独承载」，删掉空指针 |
| 8 | `history-evidence-strip` 的 previous/baseline 标记是记录持有的值，而「announces relation」只作用于**被选中**的那条 | 属实（两轮复核都未先发现，第二轮才抓到）。改为每条都播报其 relation |
| 9 | `summary-stat` 图标 optional 且合并短语未声明含状态，图标缺席时只剩颜色 | 属实。合并短语改为含「任何已表达的状态」，与 Settings 点、blocked 房间同法收口 |

**复核者最终覆盖**：五条准入条件 × 6 个控件族；载体规则 × 14 个合同；解析声称 × 9 处条款；
更严合同 × 8 行；计数保证 × 5 处；唯一例外 × 9 个候选。末轮除两条已记 `[FOLLOW-UP]` 的
基线残留（capture Back 双名、`summary-stat` 已修）外无剩余证伪者。

### 方法论补记（本卡premise 的两处缺口，比 finding 本身更值钱）

L309 说「给成熟文档加中心规则前先做实例代入表」。本卡照做了，仍连吃 10 条。病因有二，
**都不是实例代入表能覆盖的**：

1. **只把中心规则代入了实例，没把被改写的周边条款也代入**。状态条款、`:1748`、`:691`、
   更严合同那句，每一句都自带全称词，它们同样在对整份文档做断言。
2. **实例代入表比对的是「新句 vs 旧实例」，管不了「新句 vs 新句」**。第 3 条（裸字形通道
   与载体规则互相矛盾）就是两条新句打架，表里任何一行都发现不了它。

故本卡把方法补成三步：**建表 → 逐句代入（含被改写的周边条款）→ 新句之间交叉核对**，
并在 ship 前跑一次 fresh-context 对抗复核（L205）——本卡两轮复核抓到 7 条真缺陷，
**全部在本地免费循环里吃掉，没有烧掉任何一轮 R3**。

### 遗留 `[FOLLOW-UP]`（不在本卡收口，理由见上表第 2 条）

`STREAM_CAPTURE` 的 Back 同时被 `:880` 的 `Save and exit` 与 `:1590` 的 `Back to {parent}`
声明，条件 3「tooltip 携同一短语」因此没有唯一指代。该矛盾在基线即存在，且被本卡替换的
旧 `icon-button` 行本就带同一依赖；收口它属于 Back 标签合同，超出本卡范围。

## R3 第 3 轮（2026-09-08 · block · 1 条 finding · 属实）

`state-badge` 的语义格被我改写成「owner 也表达该徽标所标记的**状态**」，但同一行还声明了
`SOURCE` 变体——**来源是记录持有的值、不是状态**，于是该变体落在保证之外，可以违反
载体规则「领域值永不由字形单独承载」。

**属实，且是本卡自己制造的窄口**：我为给 A5 腾出唯一锚点而改写这一行时，只想着 `DOT`
标记状态这一种用法，没回头看该行自己声明的四个变体（`COUNT / DOT / STATUS / SOURCE`）。

修法两处：语义格改为「owner 也表达该徽标所标记的**值**」（严格放宽，四个变体全覆盖）；
载体规则的举例补上 `a source`，让覆盖面在定义处就看得见。DoD 锚点与 M5/M12 同步改到
`the owner also expresses the value the badge marks`。

**又一次同型**：这已是第四次「新写的句子比它要覆盖的实例窄」。前三次的对象是整份文档的实例，
这次的对象是**被改写那一行自己声明的变体清单**——代入表连这一层也要扫：
改写任何一行时，先把该行自己的 variants 逐个代入新措辞。

## R3 第 4 轮 + 第三/四轮本地对抗复核（2026-09-08）

### R3 第 4 轮（block · 2 条 · 均属实、均由我前两轮修法引入）

| # | finding | 修法 |
|---|---|---|
| 1 | 状态条款把字形词汇写成**闭集**（check/exclamation/cross/dash/shield），而同一 diff 明确放行的 `Settings` `DOT` 徽标正是一个标记状态的字形、且不在集内 | 见下（最终改为「映射 + 按行例外」） |
| 2 | `summary-stat` 的修法「any expressed state 并入短语」是**条件句**，纯颜色实现仍合规 | 改为「every non-neutral state 并入短语」，四个状态逐个可判 |

### 本地对抗复核第三、四轮（不烧 R3 轮次，共出 5 条，全部属实）

| # | finding | 修法 |
|---|---|---|
| 1 | 闭集比文档声明的状态图标窄：`status-choice` 的 `CRITICAL`、`feedback-banner` 的 `INFO`、`notice-delivery-row` 四态、`save-status` 五态都无成员 | **删掉全称**：该句由「闭集」改写为**只对五个具名状态的映射**，集外状态不再被断言 |
| 2 | 我为救 `DOT` 而加的 `state-badge` 整行豁免**过宽**：它把最需要词汇约束的 `STATUS` 变体一起免掉，却指向一行根本不谈符号的合同 | 豁免改为**按行声明的标记形态**（`except where a component row declares a different marker form, as state-badge does for its dot`），只放行 dot 这一形态 |
| 3 | 计数条款仍按「状态 vs 计数」二分，`SOURCE` 落在两支之外——**第 3 轮那条缺陷在同一 diff 的第二处条款里原样幸存** | 改为「a badge that marks a state or another value rather than a count」 |
| 4 | 载体规则的领域值举例被当作闭集用（本轮刚为 source 扩过一次），而 `history-evidence-strip` 标记的 baseline/previous **relation** 无成员 | 举例补 `a relation` |
| 5 | `these statuses` 是**前指**（先行词在冒号之后），可被读回第二句的 `Every status` 而复活全称 | 句首直接列出五个状态名 |

复核者另独立核实：**没有任何条款依赖那个闭集**（唯一候选 `:1175` 的四个完成标签本就不在五个状态内），
故删掉全称不丢任何保证。

### 结论：字形词汇那一句是独立子问题，其余已收敛

复核者按位置统计 17 条持续 finding：**8 条落在状态载体一族，其中 5 条落在 `:1420` 这一句，
再其中 4 条落在它末尾的字形词汇分句**；7 条落在本节的载体规则与定义/范围段，**已收敛**
（最近两轮零新发现）；**只有 1 条落在五条准入条件**，且是本卡继承而非制造的歧义。

病根：文档同时持有两个事实，一句话调和不了——**五个状态之外的状态也带图标**，
且**五个状态之内有一个组件用非词汇形态（dot）标记**。前五次改写都在「太窄」与「太宽」之间翻烙饼。
最终解法不是第六次改措辞，而是**同时消掉两个方向的全称**：作用域缩到五个具名状态（治太宽），
例外按「行声明的标记形态」给（治太窄），二者都不再对整份文档做断言。

## R3 第 5 轮：WCAG 1.4.1 下限（2026-09-08 · block · 1 条 · 属实且最重）

**这是全卡唯一一条命中卡片 `forbid` 的 finding，也是唯一一条本地五轮对抗复核都没抓到的。**

评审指出：我的规则把 owner 的**播报**当作与颜色并列的充分载体。播报服务屏幕阅读器，
**不是视觉线索**——色觉障碍的**明眼**用户仍然只剩颜色。`forbid` 第 1 条禁止弱化的正是
WCAG 1.4.1 这条下限，而我把它写松了。点名实例 `summary-stat`：图标 optional，
三个非中性状态只活在合并语义短语里。

**病根是我把两件事合并成了一件**：WCAG 1.4.1 要的是**明眼可见的非颜色线索**；
无障碍播报要的是**屏幕阅读器可达**。二者是两项义务，我却把播报写成了可见文字的**替代项**。

修法：句 2 拆成两句——「每个状态都有一个非颜色的**视觉**线索：自身可见文字、字形、位置、
或 owner 给出的可见文字」+「若该状态自身没有可见文字，owner **另外**播报它」。
播报从此只补充、永不替代。新增 DoD 锚点 `has a visual cue that is not color` + M24 钉住。

### 收紧后暴露的既有无障碍缺口（本卡因此关掉六处，全部是基线问题、非本卡引入）

| 组件 | 缺口 | 修法 |
|---|---|---|
| `summary-stat` | 图标 optional，非中性状态只在语义短语 | 图标改为「每个非中性状态必带」 |
| `metadata-row` | 同上形态；该行自己却承诺「never encodes state by color alone」却无载体 | 同上；语义格补「装饰图标隐藏、状态图标不隐藏」 |
| `evidence-rail` × `UNRATED` | **签名组件**：complete / missing-required / blocked 三态仅靠颜色区分（dash 只给 optional），而 `UNRATED`（每个项的默认态）不声明任何可见文字 | `UNRATED` 可见内容补 `missing-evidence sentence`（照 `ATTENTION_COMPACT` 已有的写法）；`SAVE_FAILED` 按「current state」继承 |
| `photo-evidence-tile` | `FAILED` 无声明载体（其余六态各有自己的条款救） | 确定性格补 `failed names its error` |
| `task-stepper` | `complete` / `failed` 无载体：阶段标签只说是哪一步、唯一字形只标 `current`、位置与成败无关、语义只播报当前阶段。**它是 `RESTORE_TASK` / `BACKUP_SETTINGS` / `REPORT_IMPORT` / `LOCAL_DATA_ERASURE` 的必需元素——恢复或清除流程里失败的那一步，色觉障碍用户看不见** | 解剖补「每个 complete 与 failed 阶段带状态标记」；语义补「失败阶段也播报」 |
| （另见 `:926` `Settings` 点、`room-progress` `BLOCKED`、`history-evidence-strip` relation，本卡前几轮已关） | | |

复核者逐条核实这六处不与既有条款冲突：`metadata-row` 的状态图标属对比度表 `:1273` 的
`Essential icon`（3.00:1）而非 `:1274` 的装饰豁免，与 `:1824`「装饰性 rail 与 divider 无单独描述」
不重叠。**实现期遗留**：新增的必带图标会产出新的前景/背景对，`:1408` 的对比度闸要求逐对登记；
`light.tertiary` / `light.error` 目前只登记了对 `surface-container`、缺对 `surface` 的绑定。

### 遗留 `[FOLLOW-UP]`（同类但更弱，本卡不扩范围）

`media-preview` 的 `error` 与 `remediation-suggestion-card` 的 `failed`：载体由邻近条款**隐含**
而非声明（前者靠「archived media offers recovery」、后者靠 `:1188` 的离线表），比上表五处弱一档。
另有 capture Back 的 `Save and exit` / `Back to {parent}` 双名（基线既有，见第 2 轮记录）。

### 本卡真正的产出，比它原定范围更值钱

原定范围是「给符号化 chrome 命名一组准入条件」。但要把这条规则**说成真的**，文档就必须为
它声明的每一个状态回答「除颜色之外由什么承载」——于是查出**六处既有的 WCAG 1.4.1 缺口**，
其中一处在签名组件 evidence rail 的默认态上。这些缺口在规则含糊（播报可替代视觉）时是看不见的。

## R3 第 6 轮 + 双半扫描（2026-09-08）

### R3 第 6 轮（block · 2 条 · 均属实 · 均由第 5 轮修法引入）

两条都落在**播报半**，而非视觉半：`task-stepper` 声明四态却只播报 current 与 failed；
`metadata-row` 把图标改成必带、只说「状态图标不隐藏」，却没要求任何**已授权的描述**命名该状态，
于是 warning/error 对辅助技术仍是裸字形。

**我自己的盲点**：规则有两半（视觉线索 + 播报），我把视觉半扫得很彻底，却默认播报半自动成立。
第 5 轮我给若干行加了**必带字形**——字形恰恰没有文字，正是最需要播报的那一类。

### 双半扫描（本地第六轮复核 · 按两半同时代入每个带状态轴的合同）

改用「A 非颜色视觉线索 / B 无自身可见文字时 owner 另外播报」**两半同时判**，
再查出 5 处「语义格播报的范围窄于该行自己的状态清单」——与第 6 轮同型：

| 行 | 未覆盖状态 | 修法（统一句式：在确定性格追加一句声明载体） |
|---|---|---|
| `history-evidence-strip` | `archived` | `; an archived record names its archived state` |
| `media-preview` | `error` | `; an error names its cause` |
| `remediation-suggestion-card` | `failed` | `; a failed generation names its cause and keeps the on-device suggestion` |
| `report-action-sheet` | `preparing` / `error` | `; preparing and error each name their state` |
| `media-assignment-row` (V1.1) | `saving` | `; a saving row names that it is saving` |

复核者同时给出**逐行通过清单**（自带可见文字者约 28 行；靠字形/位置但播报已显式声明者 10 行，
逐条引用救它的那一句），并明确划线说明两处**不**判为失败的理由（`verification-receipt` 的
`verified/stale state` 按通用状态字段读；`photo-evidence-tile` 的 `TEMPORARY` 由相机复核流的
`Retake` / `Use photo` 作为 owner 可见文字）。

**方法论第三处补记**：规则若有两半，代入表必须**两半同时判**。只判一半时，
「为满足另一半而新增的载体」会系统性地制造出未覆盖项——第 5 轮加的每一个必带字形，
都在播报半上开了一个新口子。

## R3 第 7–8 轮：拆卡切口不可分割，以及「线索须可感知」（2026-09-08）

### 第 7 轮（block · 2 条 · 均属实）——拆卡当轮即被证伪

用户裁定把「非颜色载体分类学 + 组件行补齐」拆给承接卡后，我照办撤回了十一处补齐、
只在本卡保留下限声明。**R3 当轮就拦下**：

1. `:691`「rail never carries state by color alone」与 `:1736`（complete/missing/blocked
   仅靠颜色区分、`UNRATED` 无可见文字）**自相矛盾**——撤回补齐后下限声明立刻变成假话。
2. 我改了产线文件却**没更新卡内 R4 收据**：收据仍钉旧 SHA、仍列着已被删除措辞的 M24。
   L270 纪律整场都在守，最后一改漏了。

**结论：下限声明与行补齐不可分割**。A5 要求修订状态条款，`forbid` 第 1 条禁止弱化 WCAG 1.4.1，
而下限只有在各行满足它时才是真的。补齐已全部还原进本卡（还原后 SHA 回到 `64E20D38`，
与收据所钉一致，第 2 条自解）；承接卡收窄为三处真正独立的尾巴。

> **可复用教训：拆卡的切口必须落在「声称」与「证据」之间不产生断裂的地方。**
> 我这一刀切在了「规则」与「使规则为真的实例」之间，于是规则当场变成假话。

### 第 8 轮（block · 1 条 · 属实）——线索须有可感知的证据

前一轮把若干状态图标改为**必带**，却把「这些必带图标的对比度绑定」推给了承接卡。
评审指出：**把线索定为必带、却没有证据它可被感知，并不能建立下限**——而本卡自己
就记着 `light.tertiary` / `light.error` 只对 `surface-container` 验证过、缺对 `surface` 的绑定。

修法取评审给的第二条出路（**约束到已验证配对**，不新造绑定、不改色值）：状态条款补一句
「组件行定为必带的状态字形，只渲染在对比度闸已登记的前景/背景配对上」。
承接卡的 A1 相应改为「补登记更多配对以放宽该约束」，而非「补齐后闸才能过」。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 建卡：承接被退役的 `T4-DESIGN-SYMBOL-CHROME`（6 轮 R3 / 11 条 finding 全属实但未收敛，用户裁定关 PR #236 重做）。合并原 `T4-DESIGN-SYMBOL-COMPONENT-ROWS` 的相机面与逐行对齐范围（该卡同日撤销，未曾开工）。新增「必做前置：实例代入表」为本卡与前卡的唯一方法论差别（L300）。 |
