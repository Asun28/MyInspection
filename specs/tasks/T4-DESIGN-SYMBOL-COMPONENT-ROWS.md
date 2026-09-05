---
id: T4-DESIGN-SYMBOL-COMPONENT-ROWS
title: 组件矩阵各行与 Symbol-only chrome 对齐（含相机面控件的 tooltip 决策）
depends_on: [T4-DESIGN-SYMBOL-CHROME]
parallelizable_with: []
plan_ref: context/DESIGN.md#symbol-only-chrome
status: todo
branch: T4-DESIGN-SYMBOL-COMPONENT-ROWS
worktree: C:\wt\T4-DESIGN-SYMBOL-COMPONENT-ROWS
allow_paths:
  - context/DESIGN.md
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 弱化 Symbol-only chrome 已确立的任一条准入条件
  - 让任何符号化控件失去无障碍名
  - 改动 design token 取值（colors / spacing / typography / motion / iconography 的值）
  - 改动任何 Kotlin、Gradle、schema 或任务卡以外的文件
non_goals:
  - 落地任何 Capture / Camera 界面代码（`T2-CAPTURE-UI` 拥有）
  - 重开 Symbol-only chrome 条件集的动作/状态分岔（前置卡已定，本卡只对齐各行）
  - 底部导航的 icon+label 规则（DESIGN.md:922）
acceptance:
  - "A1 the camera surface is decided one way and stated once: either camera-control and camera-shutter carry a tooltip and their anatomy says so, or the admission set's tooltip requirement is scoped to components whose anatomy declares one; the document states exactly one of these and no row contradicts it"
  - "A2 every component matrix row that governs a symbol-only control resolves to the action-glyph or state-glyph branch by reference and states only component-specific facts, so no row carries a second normative copy of an admission condition"
  - "A3 a control whose accessible name must also carry a persistent on/off state (camera-control, privacy-chip) is covered by the admission set rather than contradicting it"
  - "A4 docs/UI-UX-ELEMENTS.md gains no second copy of any condition; it continues to resolve by reference only"
dod_command: TBD（开卡时按最终措辞钉具名 ASCII 锚点，形态同前置卡：正锚点断言解析式措辞在、负锚点断言被替换的复述措辞不在）
dod_exit: 0
dod_assert: TBD（同上，开卡时与 A1–A4 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；每个 DoD 锚点在被查文件里只出现一次，否则单点删除杀不掉它。
doc_sync: CLAUDE.md「权威文档」21 行与 TASK-BOARD 记录本卡合并 OID。
---

# T4-DESIGN-SYMBOL-COMPONENT-ROWS

## Deliverable

把 `context/DESIGN.md` 组件矩阵里**仍在复述**准入条件的各行，改为解析到前置卡确立的具名条件集
`Symbol-only chrome`；并就相机面控件做出**一次**决策，使该面不再与条件集抵触。

## 拆分依据（2026-09-06 用户裁定）

前置卡 `T4-DESIGN-SYMBOL-CHROME` 的 R3 连续三轮，finding 同属一类——**组件矩阵各行没有解析到新的中心规则**。
第 3 轮点名 `camera-control` 与 `camera-shutter`：二者今天就是纯符号（24dp 图标 / 72dp 圆），
**anatomy 里都没有 tooltip**，而条件集的动作字形分支要求 tooltip。要消解须二选一：

| 选项 | 做法 | 代价 |
|---|---|---|
| (a) 相机控件加 tooltip | 改 `camera-control` / `camera-shutter` 的 anatomy | 相机面长按与拍摄手势存在冲突风险；改的是 `T2-CAPTURE-UI` 将实现的组件合同 |
| (b) tooltip 要求按 anatomy 绑定 | 条件 3 改为「其组件 anatomy 声明 tooltip 者才要求 tooltip」 | 忠于基线文档（`:1622` 对相机动作原本只要求 accessibility text），但削弱统一措辞 |

**两条都是设计决策、不是文档整理**，故不由前置卡顺手决定。用户 2026-09-06 裁定拆出本卡，
前置卡把相机面与逐行规范化写进 `non_goals`、按缩小范围交付 A1。

## 现状盘点（开卡时须重跑，勿照抄）

前置卡合并后，`context/DESIGN.md` 已解析到条件集的条款：`:890` 顶栏尾部图标 · `icon-button` 行 ·
`tooltip` 行 · `state-badge` 行 · Colors 章状态句 · Do's and Don'ts 状态句 ·
`docs/UI-UX-ELEMENTS.md` 的 `icon-button` 规则与主题清单第 5 条。

**本卡要处理的剩余面（开卡时须以「不变量」而非「症状词」重新 grep，L97 + 前置卡 R3 第 1 轮教训）**：

| # | 行 | 现状 | 待办 |
|---|---|---|---|
| 1 | `camera-control` | anatomy `24dp icon, opaque 48dp target`（无 tooltip）；语义 `Label describes action and current state` | A1 决策 + A3（名字里带当前状态） |
| 2 | `camera-shutter` | anatomy `outer 72dp circle, inner state mark`；语义 `label Take photo` | A1 决策 |
| 3 | `camera-overlay-control` | `Switch` + slider，语义含 `Historical photo overlay, 30 percent` | 判定是否属符号化 chrome；属则解析 |
| 4 | `privacy-chip` / `privacy-action` | 有可见标签，语义含当前状态播报 | A3（状态入名的通用写法） |
| 5 | 其余矩阵行的 `Semantics and focus` 列 | 各自陈述无障碍名事实 | A2 逐行判定：组件特有事实留下，条件复述改为解析 |

> **判据（避免把本卡做成整份文档重写）**：一行只有在**复述了准入条件的正文**时才须改；
> 陈述该组件**特有**的事实（角色、焦点键、live region、具体标签值）一律保留原样。

## 需求

写法：EARS。`<系统>` 是文档本身（`the design system document`）。

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-201 | Ubiquitous | The design system document shall state the camera-surface tooltip decision exactly once, and no component row shall contradict it. | A1 · 拆分依据 |
| REQ-202 | Ubiquitous | Every component matrix row governing a symbol-only control shall resolve to the action-glyph or state-glyph branch by reference. | A2 · REQ-103 的承接 |
| REQ-203 | Ubiquitous | No component matrix row shall restate the text of an admission condition. | A2 · 单一真相源 |
| REQ-204 | Ubiquitous | The admission set shall cover a control whose accessible name must also carry a persistent on/off state. | A3 · `camera-control` / `privacy-chip` |
| REQ-205 | Ubiquitous | Every symbol-only control shall keep an accessible name under whichever camera decision is taken. | A1 · 本卡 `forbid` 第 2 条 |
| REQ-206 | Ubiquitous | `docs/UI-UX-ELEMENTS.md` shall continue to resolve by reference and shall gain no copy of any condition. | A4 · L97 |

## 未决决策

| ID | 问题 | 选项 | 影响 |
|---|---|---|---|
| OD-1 | 相机面控件是否携 tooltip？ | (a) 加 tooltip 并改 anatomy (b) tooltip 要求按组件 anatomy 绑定 | REQ-201；(a) 改 `T2-CAPTURE-UI` 的实现合同，(b) 不改任何组件 anatomy |

> **OD-1 必须在写 RED 之前由用户裁定**：它决定 A1 的锚点措辞，也决定本卡是否触及 `T2-CAPTURE-UI` 的合同。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 建卡：承接 `T4-DESIGN-SYMBOL-CHROME` R3 第 3 轮拆出的相机面对齐与逐行规范化（用户裁定拆卡）。 |
