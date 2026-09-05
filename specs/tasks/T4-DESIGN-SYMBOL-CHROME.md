---
id: T4-DESIGN-SYMBOL-CHROME
title: DESIGN.md 收口「符号化 chrome」的准入条件，消解与纯图标界面的两处抵触
depends_on: []
parallelizable_with: [T4-SCHEDULE-UI]
plan_ref: context/DESIGN.md#dos-and-donts
status: todo
branch: T4-DESIGN-SYMBOL-CHROME
worktree: C:\wt\T4-DESIGN-SYMBOL-CHROME
allow_paths:
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
  - 删除计数的完整数值或其完整播报，或允许把数值折成纯符号
  - 改动 design token 取值（colors / spacing / typography / motion / iconography 的值）
  - 改动任何 Kotlin、Gradle、schema 或任务卡以外的文件
non_goals:
  - 落地任何 Schedule / Capture / Report 界面代码（各自的 UI 卡拥有）
  - 底部导航的 icon+label 规则（DESIGN.md:922，本卡不触碰）
  - 新增图标依赖或图标资源
  - 相机面控件（camera-control / camera-shutter / camera-overlay-control）与具名条件集的对齐——两者今天即纯符号且无 tooltip，对齐须改其 anatomy 或改 tooltip 条件，属设计决策（2026-09-06 用户裁定拆给 T4-DESIGN-SYMBOL-COMPONENT-ROWS）
  - 其余组件矩阵行「Semantics and focus」列的逐行规范化（同上，归 T4-DESIGN-SYMBOL-COMPONENT-ROWS）
acceptance:
  - "A1 DESIGN.md carries one named condition set under which a chrome control may be symbol-only, and that condition set is stated once and referenced from every clause that governs symbol-only chrome, so the document holds one rule rather than three"
  - "A2 the amended count clause still requires every count to carry its numeral and to announce the full count, and it no longer forbids a symbol-only badge in terms that contradict the state-badge DOT variant the same document declares"
  - "A3 the amended status clause still forbids colour from being the sole carrier of state, and states the carriers that satisfy it in terms of glyph, position and text rather than requiring a visible text label unconditionally"
  - "A4 docs/UI-UX-ELEMENTS.md resolves its icon-button rule to the same named condition set, so the two authority surfaces cannot drift apart"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path context/DESIGN.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'plural-aware' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'announce the full count' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'never the sole state channel' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'Every status uses its stable symbol' -CaseSensitive -Quiet) -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'Required by every symbol-only action glyph' -CaseSensitive -Quiet) -and -not (Select-String -Path context/DESIGN.md -SimpleMatch 'or icon-only badges' -CaseSensitive -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: DESIGN.md declares a single named 'Symbol-only chrome' condition set, keeps the plural-aware count rule and the full-count announcement, keeps 'never the sole state channel', no longer carries the unconditional icon-only-badge prohibition, and UI-UX-ELEMENTS.md resolves its icon-button rule to that same named set. Two anchors added after R3 round 1 lock the other two clauses of the same class: the Colors-chapter status clause now resolves instead of mandating label+icon ('Every status uses its stable symbol'), and the UI-UX theme checklist resolves to the admission set in lowercase ('symbol-only chrome', which is case-distinct from the title-case anchor above so each stays singly sited).
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；不以整份文档 diff 作断言面。
doc_sync: CLAUDE.md「权威文档」21 行与 TASK-BOARD 记录本卡合并 OID；解锁 T4-SCHEDULE-UI-PRESENTATION。
---

# T4-DESIGN-SYMBOL-CHROME

## Deliverable

在 `context/DESIGN.md` 里把「一个 chrome 控件在什么条件下可以是纯符号」写成**一处具名条件集**，
并让今天分散在四处、彼此不一致的相关条款全部解析到它。产出是文档修订，不是界面代码。

## 拆分依据（2026-09-03 用户裁定）

`T4-SCHEDULE-UI` 的需求重写提出 A7「chrome 控件一律为 typed glyph、不含可见文本字段」，与
`context/DESIGN.md` 抵触。`CLAUDE.md`「权威文档」21 规定 UI 规范细节**唯一服从** `context/DESIGN.md`，
故不得由 UI 卡就地绕过。用户在三个候选中裁定 **「先按 DESIGN 自身流程修订该文件，再落呈现卡」**，
遂开本卡；本卡 block `T4-SCHEDULE-UI-PRESENTATION`，**不** block 行为半 `T4-SCHEDULE-UI`。

## 现状盘点（开卡前已 grep，L97）

教「图标与标签」规则的权威面共四处，**其中两处已经允许纯图标**，只有两处真抵触：

| # | 位置 | 原文要点 | 与「纯符号 chrome」的关系 |
|---|---|---|---|
| 1 | [card:docs/UI-UX-ELEMENTS.md:35] | 「纯图标操作必须使用 `icon-button` 并同时提供 tooltip 和无障碍名称」 | **已允许**，且已给出准入条件（tooltip + 无障碍名称） |
| 2 | [card:context/DESIGN.md:1622] `tooltip` 行 | 「Every icon-only toolbar/camera action exposes the same verb-object label as accessibility text」 | **已允许**，条件与 #1 同构 |
| 3 | [card:context/DESIGN.md:1763] | 「Counts use complete, plural-aware phrases … never `1 items` or icon-only badges」 | **抵触**（无条件禁令） |
| 4 | [card:context/DESIGN.md:1812] | 「Do pair every status color with a **label** and icon」 | **抵触**（无条件要求可见 label） |

另有一处**看似抵触实则不是**：`context/DESIGN.md:922` 要求底部导航每个目的地同时显示 icon 与 label。
根导航不在任何 Schedule UI 卡的范围内（两张卡的 `non_goals` 均已排除），本卡亦不触碰。

> **结论决定了本卡的尺寸**：需要改的是 #3 与 #4 两句，加一处新的具名条件集，再让 #1 #2 指向它。
> 这不是「放宽无障碍」——#3 #4 各自保护着一条真实不变量（计数的数值必须可读可播报；
> 颜色不得是状态的唯一通道），本卡要求把这两条不变量**原样保留**，只把它们与
> 「必须有可见文字」这个**实现手段**解耦。

## 需求

写法：EARS。`<系统>` 是文档本身（`the design system document`）。

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-101 | Ubiquitous | The design system document shall declare one named `Symbol-only chrome` condition set stating what a chrome control must carry in order to omit visible text. | A1 · 本卡 Deliverable |
| REQ-102 | Ubiquitous | The `Symbol-only chrome` condition set shall require a tooltip and an accessible name, matching the conditions already stated at [card:docs/UI-UX-ELEMENTS.md:35] and [card:context/DESIGN.md:1622]. | A1 · 现状盘点 #1 #2 |
| REQ-103 | Ubiquitous | Every clause governing symbol-only chrome shall resolve to the `Symbol-only chrome` condition set rather than restating its conditions. | A1 · 单一真相源 |
| REQ-104 | Ubiquitous | The count clause shall continue to require every count to carry its numeral and to announce the full count. | A2 · [card:context/DESIGN.md:1763,1622 `state-badge`] |
| REQ-105 | Ubiquitous | The count clause shall no longer state an unconditional prohibition on symbol-only badges, because the same document declares a `state-badge` `DOT` variant that such a prohibition contradicts. | A2 · [card:context/DESIGN.md:1622 `state-badge` 行] |
| REQ-106 | Ubiquitous | The status clause shall continue to forbid colour from being the sole carrier of state. | A3 · [SOURCE: WCAG 2.2, SC 1.4.1 Use of Color (Level A), https://www.w3.org/WAI/WCAG22/Understanding/use-of-color.html] |
| REQ-107 | Ubiquitous | The status clause shall state its satisfying carriers as glyph, position and text, and shall require a visible text label only where the `Symbol-only chrome` condition set is not met. | A3 · 现状盘点 #4 |
| REQ-108 | Ubiquitous | `docs/UI-UX-ELEMENTS.md` shall resolve its icon-button rule to the `Symbol-only chrome` condition set by name. | A4 · L97（横切纪律须一次纳入全部权威面） |
| REQ-109 | Ubiquitous | The design system document shall change no design token value. | 本卡 `forbid` 第 3 条 |

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-101..103 | automated · `dod_command` 的具名锚点断言 | 文档文本 |
| A2 | REQ-104, 105 | automated · 保留串在、旧禁令串不在 | 文档文本 |
| A3 | REQ-106, 107 | automated · `never the sole state channel` 仍在；人工复核载体表述 | 文档文本 + 评审 |
| A4 | REQ-108 | automated · 两份文档同时含具名锚点 | 文档文本 |
| — | REQ-109 | automated · `git diff` 内无 token 值行变动（R3 范围闸 + 评审） | diff |

> **本卡的 oracle 只能是文档文本**，这与 `A5 不得以源码字符串作 oracle` 的纪律不冲突：那条纪律针对的是
> **用源码扫描冒充行为测试**，而本卡的交付物**本身就是文本**，文本即契约本体（同
> `T5-BACKUP-FORMAT` 的黄金向量、`T1-CANON-HASH` 的键序测试）。

## 未决决策

无。本卡的唯一决策（三选一）已由用户于 2026-09-03 裁定为「先修订 DESIGN.md」。
条件集的**具体措辞**属实现自由度，由 A1–A4 的性质断言与 R3 评审约束，不另开 OD。

## 落地形态（2026-09-05）

新增 `context/DESIGN.md` §`Symbol-only chrome`（置于 `Component contract schema` 之后、各组件矩阵之前）：
先划 chrome / domain content 的界（计数、日期、地址、物业与房间名、用户须读的状态**不是 chrome**，
永不折成字形），再列 5 条准入条件——**按字形种类分岔**：字形取自既有 iconography 且只代表**一件事**（一个动作
**或**一个状态）、命名不得由图标资源名派生 · 触控区不缩到可见字形边界 · **动作字形**携该动作的 verb-object
tooltip 并以同一短语作无障碍名 · **状态字形**不携动作措辞，改由拥有它的控件在自己的无障碍名里陈述该状态、
其变化以 state change 播报 · 两类都不依赖颜色（去掉颜色后动作仍可辨、状态仍可读）。并明写**准入不覆盖更严的
合同**——带标签的底部导航、带标签的房间进度段、`notice-delivery-row`，以及拍照/合规/隐私/删除/finalize/备份/
恢复这几个动作，都在准入之上保留可见标签（据此不触碰 `non_goals` 排除的 `DESIGN.md:922`，也与 `:1756`
「图标不得替代这些动作的标签」相容）。七处条款改为解析到该集：`:890` 顶栏尾部图标 · `icon-button` 行 ·
`tooltip` 行（**只对动作字形**）· `state-badge` 行（纯符号徽标即状态字形，由拥有者陈述状态）· Colors 章状态句 ·
`docs/UI-UX-ELEMENTS.md:35` 与 `:122`。计数条款与状态条款按 A2/A3 重写。

**两处判断记账（均为让每条断言拿到可击杀的单点变异）**：

1. **每个 DoD 锚点在被查文件里刻意只出现一次**。锚点若出现两处，「删掉被改写的那一句」删一处仍绿，
   变异必然存活。故 `Symbol-only chrome` 的精确大小写形态在 `DESIGN.md` 内**只出现在标题**上，文内各条款以
   小写 `symbol-only chrome` 解析到它（定义在标题、正文小写引用是常规英文写法）；跨文档引用
   （`docs/UI-UX-ELEMENTS.md`）才用标题形态，那里同样只出现一次。
2. **`never the sole state channel` 归位到状态条款**。该串原在 `state-badge` 行，其义是「徽标不得是状态的
   唯一通道」；A3 要的却是**状态条款**承载「颜色不得是状态的唯一通道」——同一串给两个主语会让 A3 无可击杀变异。
   故该串移至状态条款作颜色规则的唯一权威，`state-badge` 行以
   `the owner also expresses the state the badge marks` 原义留存（同义改写，非弱化；该行的
   `announce the full count` 一字未动，仍是 A2 那半的唯一锚点）。

## R3 第 1 轮：两条 finding 均属实，已修，并**加两条锚点把修复钉进 DoD**

1. **同类条款还剩两处，我的开卡盘点漏了**（`context/DESIGN.md:1414` Colors 章「Pair every status with a
   label and stable symbol」· `docs/UI-UX-ELEMENTS.md:122` 主题清单「仍有文本和图标」）。两句都在无条件要求
   **可见文字 + 图标**，与重写后的状态条款直接抵触，等于把「一条具名规则」又变回多个权威。
   **病根是我按「症状词」grep（`icon-only` / `纯图标`），没按「不变量」grep（status + label + icon 的合取）**
   ——盘点表因此只列了四处。改法：`:1414` 保留其真正独有的贡献（符号词汇表：check/exclamation/cross-octagon/
   dash/shield）并把载体问题解析给状态条款；`:122` 同理解析给状态条款与准入条件。
   顺带扫清同类：`:688`「pair color with an icon **or** label」是**析取**、不抵触；`:1695`
   `notice-delivery-row`「Status is text + icon」与 `:1756`「图标不得替代这几个动作的标签」是**更严的
   组件/动作合同**，已由准入段那句「不覆盖更严的合同」显式收纳并点名。
2. **准入条件集自身不自洽**：条件 1 允许字形只代表**一个状态**，条件 2/3 却要求它有「该**动作**的
   verb-object」tooltip 与无障碍名——纯状态字形（如 `:922` Settings 的 unlabelled error dot、
   `state-badge` 的 `DOT`）因此**无法确定性地满足**该集。属实。改法即上文「按字形种类分岔」：动作字形走
   tooltip + 同名，状态字形由**拥有它的控件**在自己的无障碍名里陈述并按 state change 播报。

**DoD 已按修复收紧（只加不减，原六项一字未动）**：新增两条 ASCII 正锚点
`Every status uses its stable symbol`（DESIGN.md）与小写 `symbol-only chrome`（UI-UX-ELEMENTS.md），
使这两处修复此后由机检守住、而非仅靠一次评审。实测该收紧后的命令在 **master 基线仍退出 1（RED）**、
在本卡工作树退出 0（GREEN），故原 RED 证据在新命令下依然成立，不存在「改标准换绿灯」。
小写形态与标题形态**大小写不同**，故两个锚点各自仍只出现一次（见下方判断 1）。

## R3 第 2 轮：一条 finding，属实，且是我自己第 1 轮修复的回归

`context/DESIGN.md:1636` 的 `tooltip` 行写「Required by **symbol-only chrome**」——把 tooltip 说成**整个**
准入集的要求；但第 1 轮把条件集按字形种类分岔后，tooltip + 同名无障碍名**只对动作字形**成立，状态字形走的是
「由拥有者在自己的无障碍名里陈述」。于是同一份文档对状态字形给了两条互斥指令。**改法**：该行收窄为
`Required by every symbol-only action glyph`。

顺着同一透镜扫同类（L292：一条规则装在一个入口上等于没装），发现姊妹处 `state-badge` 行的语义列
（我在第 1 轮为腾出 `never the sole state channel` 而改写的那句）仍在**复述**条件 4 而非解析到它，遂一并改为
`Merged into owner; a symbol-only badge is a state glyph, so the owner names the state`。
改完复扫 `DESIGN.md` 全部 7 处 `symbol-only` 提及，无一再对状态字形过度声称。

**DoD 第三次收紧（仍只加不减）**：新增 ASCII 正锚点 `Required by every symbol-only action glyph`，
把这次收窄钉住。实测收紧后命令在 **master 基线仍退出 1（RED）**、本卡工作树退出 0（GREEN）。

## R3 第 3 轮：两条 finding，一条当场修，一条经用户裁定拆卡

1. **`tooltip` 与 `state-badge` 两行仍在复述条件 3/4，而非解析到它**（`:1636-1637`）。属实：
   「supplies the phrase that control also exposes as its accessible name」就是条件 3 的正文，
   「so the owner names the state」就是条件 4 的正文。已改为只指向对应分支
   （`that branch fixes its wording` / `follows that branch`），组件特有事实留在各自列。
2. **相机面控件未解析到该集，且我引入的枚举与它们抵触**（`:1559` 与 `:1622-1623`）。
   两半分开处理：
   - **我引入的那半当场修**：第 1 轮我把 `:1756` 的动作清单（capture/compliance/privacy/delete/
     finalize/backup/restore）原样搬进准入段，于是准入段自己宣称「capture 动作保留可见标签」——
     而同文档的 `camera-shutter` 正是纯符号的 capture 控件。**这是本次 diff 引入的抵触**（L113），
     已删掉该枚举，改为「凡组件合同要求可见标签者，该标签压在准入之上」并只举三个确有此要求的例子。
     `:1756` 恢复为本卡未触碰的既有条款。
   - **相机行的对齐拆出去**：`camera-control`（24dp 图标、anatomy 无 tooltip）与 `camera-shutter`
     （72dp 圆、无障碍名 `Take photo`、无 tooltip）今天就是纯符号；让它们解析到本集，要么给相机控件加
     tooltip（改 anatomy，且相机面长按与拍摄手势有冲突风险），要么把「动作字形必带 tooltip」改成
     「按组件 anatomy 是否声明 tooltip 而定」。**两条都是设计决策、且改动 `T2-CAPTURE-UI` 将要实现的
     组件合同**，不是文档整理。**2026-09-06 用户裁定拆给 `T4-DESIGN-SYMBOL-COMPONENT-ROWS`**，
     并进本卡 `non_goals`。

> **拆卡依据（不是回避 finding）**：连续三轮的 finding 同属一类——组件矩阵各行没有解析到新的中心规则。
> 本仓已记过这条信号（`T0-CI-IDENTITY-DEADLINE` / `T3-REPORT-HTML-RENDERER`：一处反复吃 finding 的
> 子问题通常是独立子问题，越早拆越省轮次）。据此，**A1「referenced from every clause」在本卡按缩小
> 范围交付**：条件集本身 + 本卡盘点的那批条款已全部解析，组件矩阵各行归承接卡。此事实在此明记，
> 不以「已全部解析」措辞蒙混。

## R3 第 4 轮：三条 finding 全部属实，全部当场修

1. **拆卡后规则仍在声称相机控件已对齐**（#14 范围过度声称）。第 3 轮把相机面写进 `non_goals`，
   但条件 3「每个动作字形都要 tooltip」仍然**绑住**了 `camera-control` / `camera-shutter` /
   `camera-overlay-control`——它们的合同里根本没有 tooltip。**延期只有在规则显式豁免它们时才自洽**，
   否则等于一边说「不管」一边把它们判为不合规。已在条件集末尾加一段显式豁免：点名这三个组件，
   写明本节**不决定**相机面是否加 tooltip，在该决定落地前由它们各自的行管辖、本集不认领它们。
   同时把 `icon-button` 行的「Every instance is admitted…no variant relaxes that set」收窄为
   「Admitted as symbol-only chrome on the action-glyph branch」——原措辞的 `CAMERA` 变体读起来
   像是替相机组件背书。
2. **`Required by every symbol-only action glyph` 仍在 `tooltip` 行复述条件 3**（#7）。属实。
   已把这句**移进条件 3 本身**（DoD 锚点随之搬家，仍只出现一次、`dod_command` 一字未改），
   `tooltip` 行变成纯引用 `Governed by the action-glyph branch of symbol-only chrome`。
3. **chrome/content 边界没有可判定的判据**（#7）。我写的「a status the user must read 是 domain content、
   永不折成字形」与条件 4/5 允许状态字形、状态条款允许 glyph 承载状态，三者之间没有任何判据能分开
   「受保护的领域内容」与「控件自身状态」。属实，是我留的洞。已补**可判定的判据**：**看取值有几种读数**
   ——超过两种读数的值保留文字；只有二元存在与否、且拥有者已点明它标记什么的标记，才可以是字形。
   并给出三个落在判据两边的实例（计数与巡检项状态保留文字；`Settings` 错误点不需要文字），
   与同文档 `:922` 的 unlabelled error dot 和 `state-badge` 的「Merged into owner」自洽。

> **一处过程事故记账**：改完后我在**上一批变异尚未跑完时**又起了一批，两批重叠，于是第二批读到的
> 「基线」其实是第一批 M6 的变异态（实测该态 SHA = `EB9324…`，正是本批 M6 行记录的那一枚），
> 遂 fail-closed 报「baseline is not GREEN」并**在任何植入之前中止**——守卫按设计生效，没有污染任何字节。
> 已确认文件回到基线且 DoD 绿后，重跑单批得下列收据。**L196 又中一次**（其原文已写明「变异批进行中
> 勿并行跑独立交叉复核」），R5 计数。

> **ship 前自审又吃掉一条**（R3 第 4 轮修完、送审之前自查条件集的每句声称是否在文档里为真）：
> 「the clauses elsewhere … resolve here」与刚加的相机豁免段**自相矛盾**——相机行确实治理符号化控件、
> 却按豁免不解析到本集。已把该句收窄为 `… except the three camera rows named at the end of this section`。
> 这条本会是第 5 轮的 finding；**声称一旦收窄，就要回头检查所有引用该声称的句子**。

## R3 第 5 轮：两条 finding 均属实，均是第 4 轮措辞造成的自伤

1. **全额播报的保证被我缩到了 `state-badge` 身上**（A2/REQ-104）。我写的是「计数永远带数字，
   而 `state-badge` 可以在视觉上截断但不能截断播报」——于是**非徽标的计数**并没有被要求播报完整数值，
   而 A2 要的是「every count … announce the full count」。已改为「it always carries its numeral
   **and announces its full value**」，徽标那半作为视觉截断的例外保留在后。
2. **条件 1 会把我自己举的两个例子判为不合规**（A1/A2 内部一致性）。条件 1 要求字形取自既有
   iconography set，而我举的 `Settings` 错误点与 `state-badge` 的 `DOT` 都是**几何点标记**、
   并非该 set 的成员——按条件 1 它们不合规 ⇒ 需要可见标签 ⇒ 正好把 A2 要消除的 DOT 抵触又造回来。
   已在条件 1 显式接纳**组件自定义的二元状态标记**（`or is a component-defined binary state marker
   such as a badge dot`），不引入任何图标资源（守本卡 `non_goals` 第 3 条）。

## R4 变异收据（2026-09-06 · 9/9 KILLED · 对应 R3 第 5 轮修复后的字节）

被测基线 SHA-256（收据钉这两个文件的确切字节，此后任何改动即作废本批，L270）：
`context/DESIGN.md` = `2970A04E081D0EEA8CC01217B7B742A27EBD479CD2B3F40BE6861195B00F6C4A` ·
`docs/UI-UX-ELEMENTS.md` = `F0B69A3148BD9202D0578FCAE8CFAEE8EE4DA094193F48653E6CB5F225FF7EC4`。
基线 `dod_command` 退出 **0**（GREEN）；9 枚跑完后两文件 SHA 逐一回到上列基线、退出仍 0。
每枚植入前断言靶串在文件内**恰好出现 1 次**（不符即作废该枚并中止，L190）、断言渲染后文本与 SHA 均已改变
（防 no-op 变异冒充击杀，L297）、断言植入前文件仍等于基线（L196）。

| # | 断言 | 文件 | 单点变异（删/还原被改写的那一句） | dod_exit | 判定 |
|---|---|---|---|---|---|
| M1 | A1 | DESIGN.md | 删掉声明具名条件集的 `### Symbol-only chrome` 标题行 | 1 | KILLED |
| M2 | A2 | DESIGN.md | 计数条款去掉 `plural-aware`（`Counts use complete phrases`） | 1 | KILLED |
| M3 | A2 | DESIGN.md | 把无条件禁令 `or icon-only badges` 还原进计数条款 | 1 | KILLED |
| M4 | A3 | DESIGN.md | 状态条款去掉 `never the sole state channel`（换成 `is not the only carrier`） | 1 | KILLED |
| M5 | A4 | UI-UX-ELEMENTS.md | 删掉 `icon-button` 规则里解析到 `Symbol-only chrome` 的那半句 | 1 | KILLED |
| M6 | A2 | DESIGN.md | `state-badge` 行去掉 `announce the full count` | 1 | KILLED |
| M7 | A1/A3（R3 r1 #1） | DESIGN.md | 把 Colors 章的 label+icon 强制令还原、盖掉解析式状态句 | 1 | KILLED |
| M8 | A1/A4（R3 r1 #1） | UI-UX-ELEMENTS.md | 删掉主题清单里小写 `symbol-only chrome` 那处解析 | 1 | KILLED |
| M9 | A1（R3 r2/r4） | DESIGN.md | 把**条件 3** 放宽回「所有 symbol-only chrome」，等于重新给状态字形强加 tooltip | 1 | KILLED |

M1/M5 证明 A1/A4 的具名锚点由**定义处与跨文档引用处**各自独占承载；M2/M3 证明 A2 的保留项与被删禁令两侧都在测；
M4 证明 A3 的颜色不变量确由重写后的状态条款承载（而非借 `state-badge` 行蒙混）；M6 证明 DoD 仍在守
`state-badge` 的全额播报；M7/M8/M9 证明 R3 两轮的三处修复此后**改不回去**——还原任一处即 RED。
变异脚本不入库（跑在 scratchpad，不属 `allow_paths`）。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 拆出的 OD-3（用户裁定选项 b）。开卡前已 grep 四处权威面，确认真抵触只有两句。 |
| 2026-09-05 | 实现：新增具名条件集，四处条款解析到它，计数/状态两句按 A2/A3 重写。R4 6/6 击杀。记两处判断（锚点唯一化、`never the sole state channel` 归位）。 |
| 2026-09-05 | R3 第 1 轮 block（PR #236），两条 finding 均属实：同类条款漏两处 · 条件集对纯状态字形不自洽。已修；`dod_command` 加两条正锚点把修复钉住（只加不减，master 仍 RED）。R4 重跑 8/8 击杀。 |
| 2026-09-05 | R3 第 2 轮 block，一条 finding 属实且是第 1 轮修复的回归：`tooltip` 行仍对**全部**准入集声称。已收窄为动作字形，并顺同一透镜修 `state-badge` 姊妹处；`dod_command` 第三次收紧（仍只加不减）。R4 重跑 9/9 击杀。轮次达上限 2/2，**用户裁定 `-ResetRounds` 重跑**（三轮各为互不相同的真缺陷、逐条接受并修复，不属该闸要止住的同一争点拉锯）。 |
| 2026-09-06 | R3 第 3 轮 block，两条 finding 均属实：两行仍复述条件 3/4（已改为解析）· 相机行未解析且与我引入的枚举抵触（枚举已删；相机行对齐**经用户裁定拆给 `T4-DESIGN-SYMBOL-COMPONENT-ROWS`** 并进 `non_goals`）。R4 重跑 9/9 击杀。 |
| 2026-09-06 | R3 第 4 轮 block，三条 finding 全部属实且全部当场修：延期的相机控件仍被规则绑住（补显式豁免 + 收窄 `icon-button` 行）· 锚点句仍在 `tooltip` 行复述条件 3（移进条件 3 本体，`dod_command` 未改）· chrome/content 边界无可判定判据（补「取值读数是否超过两种」）。R4 重跑 9/9 击杀。**轮次再次达上限 2/2。** |
| 2026-09-06 | R3 第 5 轮 block，两条 finding 均属实、均是第 4 轮措辞的自伤：全额播报保证被缩到 `state-badge`（已改回「每个计数都播报完整数值」）· 条件 1 会把我自己举的点标记例子判为不合规（已显式接纳组件自定义二元状态标记）。R4 重跑 9/9 击杀。**五轮共 10 条 finding 全部属实；编排者建议就此转人裁，不再请求第三次 reset。** |
