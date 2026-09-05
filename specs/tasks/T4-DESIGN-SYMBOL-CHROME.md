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
acceptance:
  - "A1 DESIGN.md carries one named condition set under which a chrome control may be symbol-only, and that condition set is stated once and referenced from every clause that governs symbol-only chrome, so the document holds one rule rather than three"
  - "A2 the amended count clause still requires every count to carry its numeral and to announce the full count, and it no longer forbids a symbol-only badge in terms that contradict the state-badge DOT variant the same document declares"
  - "A3 the amended status clause still forbids colour from being the sole carrier of state, and states the carriers that satisfy it in terms of glyph, position and text rather than requiring a visible text label unconditionally"
  - "A4 docs/UI-UX-ELEMENTS.md resolves its icon-button rule to the same named condition set, so the two authority surfaces cannot drift apart"
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path context/DESIGN.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'plural-aware' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'announce the full count' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'never the sole state channel' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'Every status uses its stable symbol' -CaseSensitive -Quiet) -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'symbol-only chrome' -CaseSensitive -Quiet) -and -not (Select-String -Path context/DESIGN.md -SimpleMatch 'or icon-only badges' -CaseSensitive -Quiet))) { exit 1 }"
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
「图标不得替代这些动作的标签」相容）。六处条款改为解析到该集：`:890` 顶栏尾部图标 · `icon-button` 行 ·
`tooltip` 行 · Colors 章状态句 · `docs/UI-UX-ELEMENTS.md:35` 与 `:122`。计数条款与状态条款按 A2/A3 重写。

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

## R4 变异收据（2026-09-05 · 8/8 KILLED · 对应 R3 第 1 轮修复后的字节）

被测基线 SHA-256（收据钉这两个文件的确切字节，此后任何改动即作废本批，L270）：
`context/DESIGN.md` = `7EFFEA3CF4DD12719E3359144F70866004E4BFBE678887E5D80E17FDA01FEA53` ·
`docs/UI-UX-ELEMENTS.md` = `F0B69A3148BD9202D0578FCAE8CFAEE8EE4DA094193F48653E6CB5F225FF7EC4`。
基线 `dod_command` 退出 **0**（GREEN）；8 枚跑完后两文件 SHA 逐一回到上列基线、退出仍 0。
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

M1/M5 证明 A1/A4 的具名锚点由**定义处与跨文档引用处**各自独占承载；M2/M3 证明 A2 的保留项与被删禁令两侧都在测；
M4 证明 A3 的颜色不变量确由重写后的状态条款承载（而非借 `state-badge` 行蒙混）；M6 证明 DoD 仍在守
`state-badge` 的全额播报；M7/M8 证明 R3 第 1 轮那两处修复此后**改不回去**——还原任一处即 RED。
变异脚本不入库（跑在 scratchpad，不属 `allow_paths`）。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 拆出的 OD-3（用户裁定选项 b）。开卡前已 grep 四处权威面，确认真抵触只有两句。 |
| 2026-09-05 | 实现：新增具名条件集，四处条款解析到它，计数/状态两句按 A2/A3 重写。R4 6/6 击杀。记两处判断（锚点唯一化、`never the sole state channel` 归位）。 |
| 2026-09-05 | R3 第 1 轮 block（PR #236），两条 finding 均属实：同类条款漏两处 · 条件集对纯状态字形不自洽。已修；`dod_command` 加两条正锚点把修复钉住（只加不减，master 仍 RED）。R4 重跑 8/8 击杀。 |
