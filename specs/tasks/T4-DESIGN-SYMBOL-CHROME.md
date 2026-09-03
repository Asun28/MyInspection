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
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path context/DESIGN.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path docs/UI-UX-ELEMENTS.md -SimpleMatch 'Symbol-only chrome' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'plural-aware' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'announce the full count' -CaseSensitive -Quiet) -and (Select-String -Path context/DESIGN.md -SimpleMatch 'never the sole state channel' -CaseSensitive -Quiet) -and -not (Select-String -Path context/DESIGN.md -SimpleMatch 'or icon-only badges' -CaseSensitive -Quiet))) { exit 1 }"
dod_exit: 0
dod_assert: DESIGN.md declares a single named 'Symbol-only chrome' condition set, keeps the plural-aware count rule and the full-count announcement, keeps 'never the sole state channel', no longer carries the unconditional icon-only-badge prohibition, and UI-UX-ELEMENTS.md resolves its icon-button rule to that same named set.
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

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 拆出的 OD-3（用户裁定选项 b）。开卡前已 grep 四处权威面，确认真抵触只有两句。 |
