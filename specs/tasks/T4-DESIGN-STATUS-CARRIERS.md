---
id: T4-DESIGN-STATUS-CARRIERS
title: 状态的非颜色载体分类学与各组件行的无障碍补齐（承接 T4-DESIGN-SYMBOL-CHROME-V2 拆出）
depends_on: [T4-DESIGN-SYMBOL-CHROME-V2]
parallelizable_with: []
plan_ref: context/DESIGN.md#colors
status: todo
branch: T4-DESIGN-STATUS-CARRIERS
worktree: C:\wt\T4-DESIGN-STATUS-CARRIERS
allow_paths:
  - context/DESIGN.md
forbid:
  - 弱化「颜色不得是状态的唯一通道」这一 WCAG 1.4.1 下限
  - 把无障碍播报当作可见视觉线索的替代项（这正是前卡 R3 第 5 轮拦下的洞）
  - 改动 design token 取值，或新增图标依赖 / 图标资源
  - 改动 `Symbol-only chrome` 节的准入条件与载体规则（前卡已验收，本卡只在其下游收紧状态载体）
  - 在两半（视觉线索 / 播报）同时代入的实例表完成之前动手写规则正文
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 对比度 token 值本身的调整（本卡只登记新增的必带图标需要哪些绑定，不改色值）
acceptance:
  - "A1 the status clause states the non-colour carrier taxonomy as two distinct duties: every status has a visual cue that is not colour, and where a status carries no visible text of its own its owner also announces it, with the announcement never substituting for the visual cue"
  - "A2 the card body carries a completed instance-substitution table evaluating EVERY component row that declares a state axis against BOTH duties at once, each row recording pass or the exact state that fails, with the clause that carries it quoted"
  - "A3 every row that fails either duty is either fixed in this card or explicitly exempted with its reason; no declared state is left with colour as its only visual channel"
  - "A4 the newly mandatory state glyphs are registered against the contrast gate's binding requirement, or the exact missing bindings are named for the implementing card"
dod_command: TBD（措辞定稿后钉具名 ASCII 锚点；每个锚点在被查文件里只许出现一次，见前卡「DoD 锚点设计规矩」）
dod_exit: 0
dod_assert: TBD（同上，与 A1–A4 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256，批中不并行跑第二批（L196/L270）。
doc_sync: TASK-BOARD 记录合并 OID；若补齐改变了任何组件合同，同步 docs/UI-UX-ELEMENTS.md 的覆盖索引。
---

# T4-DESIGN-STATUS-CARRIERS

## 起因：自 `T4-DESIGN-SYMBOL-CHROME-V2` 拆出（2026-09-08 用户裁定）

前卡跑了 **6 轮 R3、10 条 finding，全部属实**，两次经用户裁定 `-ResetRounds`。
**第 4 轮之后的每一条都不再落在前卡的交付物（具名条件集 + 载体规则 + 各条款解析）上，
而是落在「状态由什么承载」这个下游问题上**——它需要把文档里每一个声明了状态轴的组件行
逐个查一遍，是一件独立的、可枚举的活。用户裁定把它拆出来单独做。

**前卡的产出不作废**：`Symbol-only chrome` 具名节、载体规则（领域值永不由字形单独承载）、
五条准入条件、四处条款解析、相机面裁定与计数条款均已收敛并随前卡合并；
`:1420` 保留下限本身（`Color is never the sole state channel`）与对载体规则的解析，
**只把「非颜色载体的完整分类学」留给本卡**。

## 本卡为什么存在：R3 第 5 轮抓到的那个洞

前卡把 owner 的**播报**写成了与可见文字并列的充分载体。**播报服务屏幕阅读器，不是视觉线索**
——色觉障碍的**明眼**用户仍然只剩颜色。WCAG 1.4.1 (Use of Color) 管的正是后者。
前卡当场把句子拆成两半修好了，但**收紧之后立刻暴露出一批既有组件行不满足新下限**，
补齐它们就是本卡的正题。

> **两项义务，不可互相替代**：
> **A 视觉半** — 每个状态都有一个非颜色的视觉线索：自身可见文字、字形、位置、或 owner 给出的可见文字。
> **B 播报半** — 若该状态自身没有可见文字，owner **另外**播报它。
> 一行只有**两半同时满足**才算通过。

## 必做前置：两半同时代入的实例表（`forbid` 第 5 条）

前卡的教训按顺序有三条，本卡起手就要全带上：

1. **代入表要覆盖被改写的周边条款，不只中心规则**（前卡 R3 第 1–4 轮）。
2. **代入表管不了「新句 vs 新句」自相矛盾**，新写的句子之间要交叉核对（前卡本地复核第 3 条）。
3. **规则若有两半，必须两半同时判**（前卡 R3 第 6 轮）——只判一半时，
   「为满足另一半而新增的载体」会系统性制造未覆盖项：前卡第 5 轮每加一个必带字形，
   就在播报半上开一个新口子，第 6 轮原样被拦。

## 已查实的待补齐清单（前卡最后一轮双半扫描的产出，**开卡时重跑勿照抄**）

下表是前卡在两半同时代入下查实的失败行。**每一条都是基线既有缺口，非前卡引入。**

| 组件行 | 失败状态 | 缺什么 | 前卡验证过的修法 |
|---|---|---|---|
| `summary-stat` | complete / attention / blocked | 图标 optional，状态只在语义短语 ⇒ 视觉半可为纯颜色 | 图标改「每个非中性状态必带」＋语义短语含该状态 |
| `metadata-row` | warning / error | 同上；该行自己却承诺 `never encodes state by color alone` 而无载体 | 同上；语义格另需「已授权描述命名该状态」 |
| `evidence-rail` × `UNRATED` | COMPLETE / MISSING_REQUIRED / BLOCKED | **签名组件**：三态仅靠颜色区分（dash 只给 optional），而 `UNRATED`（每项默认态）不声明任何可见文字 | `UNRATED` 可见内容补 `missing-evidence sentence`（照 `ATTENTION_COMPACT` 已有写法）；`SAVE_FAILED` 按 current state 继承 |
| `task-stepper` | complete / failed（另 upcoming 的播报） | 阶段标签只说是哪一步；唯一字形只标 current；位置与成败无关；语义只播报当前阶段。**是 `RESTORE_TASK` / `BACKUP_SETTINGS` / `REPORT_IMPORT` / `LOCAL_DATA_ERASURE` 的必需元素——恢复与清除流程里失败的那一步，色觉障碍用户看不见** | 解剖补「每个 complete 与 failed 阶段带状态标记」；语义补「每个阶段都播报其状态」 |
| `photo-evidence-tile` | FAILED | 无声明载体（其余六态各有条款救） | 确定性格补 `failed names its error` |
| `history-evidence-strip` | archived | 解剖的 date/status 与 previous/baseline marker 都不说媒体已归档；播报只覆盖 relation 与绝对日期 | 确定性格补 `an archived record names its archived state` |
| `media-preview` | error | 解剖与确定性格均未出现该态；语义只播报 pane title 与控件标签 | 确定性格补 `an error names its cause` |
| `remediation-suggestion-card` | failed | 同上（accepted/rejected 骑 include/exclude 的选中态，offline 由 `:1188` 覆盖，generating 由进度指示覆盖） | 确定性格补 `a failed generation names its cause and keeps the on-device suggestion` |
| `report-action-sheet` | preparing / error | 语义只播报 pane title 与焦点返回；`:845` 的 `Generating / Ready / Failed` 属 `REPORT_EXPORT` 另一界面 | 确定性格补 `preparing and error each name their state` |
| `media-assignment-row`（V1.1） | saving | duplicate/invalid 有解释、assigned/unassigned 有目的地，saving 无 | 确定性格补 `a saving row names that it is saving` |

**前卡已在其范围内关掉、本卡不重复**：`:926` `Settings` 健康点的播报（OD-2）·
`room-progress-strip` 的 blocked 播报 · `history-evidence-strip` 的 relation 播报 ·
`state-badge` 语义格由「状态」放宽为「值」。这四条属**载体规则**（字形标记了值、owner 须表达），
是前卡交付物的一部分。

**划过线但未判失败的两处，本卡须复核后再定**：`verification-receipt` 的 `failed` / `unavailable`
（取决于解剖里 `verified/stale state` 是否读作通用状态字段）· `photo-evidence-tile` 的 `TEMPORARY`
（是否由相机复核流的 `Retake` / `Use photo` 作 owner 可见文字）。

## 实现期遗留（A4）

新增的必带状态图标是**essential icon**，落在对比度表 `:1273` 的 3.00:1 档，
而 `:1408` 的对比度闸要求每个渲染前景/背景对都有精确 metadata 条目。
**已查实缺口**：`light.tertiary` 与 `light.error` 目前只登记了对 `surface-container` 的绑定，
缺对 `surface` 的绑定；`metadata-row` / `summary-stat` 的状态图标若渲染在屏幕背景上即会命中。
本卡至少须把缺口点名交给实现卡；若在本卡补绑定，须同时跑对比度闸。

## 未决决策

无。前卡已把「播报不得替代视觉线索」这条裁死（R3 第 5 轮命中 `forbid`），
本卡只在其下游把各行补齐；若代入表查出需要改动组件**解剖**的行超出预期规模，按前卡先例拆卡。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 建卡：自 `T4-DESIGN-SYMBOL-CHROME-V2` 拆出（用户裁定）。前卡 6 轮 R3 / 10 条 finding 全属实，第 4 轮后全部落在「状态由什么承载」这个下游问题上。带入前卡双半扫描已查实的 10 行待补齐清单、三条方法论教训与对比度闸遗留。 |
