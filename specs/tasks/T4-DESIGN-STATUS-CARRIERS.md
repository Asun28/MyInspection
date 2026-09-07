---
id: T4-DESIGN-STATUS-CARRIERS
title: 状态载体收口的三处遗留：对比度绑定、两处边界行、capture Back 双名
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
  - 把无障碍播报当作可见视觉线索的替代项（前卡 R3 第 5 轮已裁死）
  - 改动 design token 的色值（本卡只补登记缺失的对比度绑定，不调色）
  - 改动 `Symbol-only chrome` 节的准入条件与载体规则（前卡已验收）
non_goals:
  - 落地任何界面代码（各 UI 卡拥有）
  - 重做前卡已完成的十一处组件行补齐（已随前卡合并）
acceptance:
  - "A1 the additional foreground/background pairs are registered with the contrast gate so the predecessor's constraint confining mandatory state glyphs to already-registered pairs can be relaxed, with each new pair carrying its audited light and dark ratio"
  - "A2 verification-receipt and photo-evidence-tile are each judged explicitly against both duties, and every declared state is recorded as carried, or fixed, or exempted with its reason"
  - "A3 the capture Back control has exactly one declared accessible name across the whole document, so the tooltip condition has a single referent"
dod_command: TBD（措辞定稿后钉具名 ASCII 锚点；每个锚点在被查文件里只许出现一次，见前卡「DoD 锚点设计规矩」）
dod_exit: 0
dod_assert: TBD（同上，与 A1–A3 逐条对齐后填）
review_gate: codex {verdict:pass}
hygiene: 每条断言由「删掉被改写的那一句即变红」的单点变异证明；变异批钉生产文件 SHA-256（L196/L270）。
doc_sync: TASK-BOARD 记录合并 OID。
---

# T4-DESIGN-STATUS-CARRIERS

## 起因：`T4-DESIGN-SYMBOL-CHROME-V2` 的三处显式遗留（2026-09-08）

前卡把「状态由什么承载」整个收口了：非颜色载体的两项义务（**视觉半**：每个状态都有非颜色的
视觉线索；**播报半**：无自身可见文字时 owner 另外播报，且**播报永不替代视觉线索**）已写进
`:1420`，十一处既有组件行的无障碍缺口已逐个补齐并随前卡合并。

> **本卡一度被裁定承接「整个载体分类学 + 组件行补齐」，随即证明不可分割**：把补齐撤回、
> 只留下限声明后，`:691`「rail never carries state by color alone」立刻与 `:1736`
> （complete/missing/blocked 仅靠颜色区分）自相矛盾，R3 当轮即拦。**下限声明与行补齐必须同卡**，
> 故补齐已还原进前卡，本卡只承接下面三处真正独立的尾巴。这条经验值得记住：
> **拆卡的切口必须落在「声称」与「证据」之间不产生断裂的地方**。

## A1 · 放宽前卡的「已登记配对」约束

前卡把若干状态图标从 optional 改为**必带**（`metadata-row` 的 warning/error、`summary-stat` 的
complete/attention/blocked、`task-stepper` 的 complete/failed 状态标记）。必带状态图标是
**essential icon**，落对比度表的 3.00:1 档，而对比度闸要求每个渲染前景/背景对都有精确 metadata 条目。

**前卡 R3 第 8 轮的裁定**：把线索定为必带、却没有证据它可被感知，并不能建立下限。
前卡取了评审给的第二条出路——**约束到已验证配对**，在状态条款写下
「组件行定为必带的状态字形，只渲染在对比度闸已登记的前景/背景配对上」，不新造绑定、不改色值。

**本卡的活是放宽它**：补登记缺失的配对（已查实 `light.tertiary` / `light.error` 只对
`surface-container` 有绑定、缺对 `surface`；dark 侧同理须复核），每个新配对带其实测 light/dark 比值，
之后该约束才可放宽。开卡时先重跑一遍实际缺口，勿照抄本段。

## A2 · 两处边界行（前卡划过线、明确未判失败）

前卡的双半扫描对这两行**给出了理由但未下最终判定**，留给本卡按同一方法收口：

| 行 | 状态 | 前卡的读法（须复核） |
|---|---|---|
| `verification-receipt` | `failed` / `unavailable` | 仅当解剖里的 `verified/stale state` 读作**通用状态字段**（而非二值字段）时才通过。若读作二值，两态无载体 |
| `photo-evidence-tile` | `TEMPORARY` | 该行自身无载体，但它总渲染在相机复核流内，`Retake` / `Use photo` 可作 owner 可见文字。须确认该流程是否为其**唯一**渲染场合 |

## A3 · capture Back 的双名（基线既有矛盾）

`STREAM_CAPTURE` 的 Back 同时被两处声明：一处给 `Save and exit`，另一处的 `top-app-bar` 行给
`Back to {parent}`。前卡的准入条件 3 要求「anatomy 声明了 tooltip 时，tooltip 携**同一短语**」,
而「同一短语」在此没有唯一指代。

**该矛盾在基线即存在**，且前卡替换掉的旧 `icon-button` 行本就带同一依赖，故前卡按
`[FOLLOW-UP]` 记账而未就地收口（收口它属于 Back 标签合同，超出前卡范围）。本卡定一个名。

## 方法（沿用前卡验证有效的三条）

1. 代入表要覆盖**被改写的周边条款**，不只中心规则。
2. 代入表管不了**新句与新句**自相矛盾，新写的句子之间要交叉核对。
3. 规则若有两半，**两半必须同时判**——只判一半时，为满足另一半而新增的载体会系统性制造未覆盖项。

## 未决决策

无。A3 的选名与 A2 的两处读法均属实现自由度，由 A1–A3 的性质断言与 R3 评审约束。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 建卡：自 `T4-DESIGN-SYMBOL-CHROME-V2` 拆出。**初版曾承接整个载体分类学与十一处组件行补齐，但 R3 当轮证明该切口不可分割**（撤回补齐后下限声明立即与 rail 规格矛盾），补齐已还原进前卡；本卡收窄为三处真正独立的遗留：对比度绑定、两处边界行、capture Back 双名。 |
