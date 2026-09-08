---
id: T4-SCHEDULE-UI-PRESENTATION
title: 排程界面的最小呈现契约（动作数与反馈 banner · 无空状态 · 日期与计数形态）
depends_on: [T4-SCHEDULE-UI-REMINDER-ACTIONS, T4-DESIGN-SYMBOL-CHROME-V2]
parallelizable_with: []
plan_ref: context/DESIGN.md#page-inventory
status: todo
branch: T4-SCHEDULE-UI-PRESENTATION
worktree: C:\wt\T4-SCHEDULE-UI-PRESENTATION
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt
forbid:
  - 修改 context/DESIGN.md（其修订归 T4-DESIGN-SYMBOL-CHROME-V2，已合并；遗留项归 T4-DESIGN-STATUS-CARRIERS）
  - 修改 MainActivity、根导航、依赖、schema、navigation-bar 标签或 app shell chrome
  - 改动 T4-SCHEDULE-UI 已钉住的 reducer 状态集、权限时序或 pending/retry 转移语义
  - 用源码字符串读取或反编译产物代替 typed 值断言
non_goals:
  - reducer 状态机与路由效果（T4-SCHEDULE-UI 已拥有并已验收）
  - 权限时序、授权恢复、pending/retry 行为（T4-SCHEDULE-UI-REMINDER-ACTIONS 已拥有并已验收）
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
  - 新增图标依赖（OD-2 已裁定不扩 res/，且一律不得引入 material-icons 依赖）
  - "符号化 chrome 与无障碍声明整半（chrome/content 类型分割、字形、RTL 镜像、目标尺寸、动效 token、reduced-motion）——归 T4-SCHEDULE-UI-SYMBOL-CHROME"
acceptance:
  - "A1 every schedule state declares at most one primary action and declares that count explicitly, so that a state offering none declares the absence rather than merely lacking one; a feedback banner's recovery is a secondary action by type, so no combination of screen state and blocked permission can present two primary actions; the view declares at most two top-app-bar actions, declares its visible-control count unbounded rather than leaving the question unanswered, and declares no gradient, drop shadow or illustration"
  - "A2 no declared state is blank: loading, no-content-empty, filtered-empty, error and permission-blocked each carry non-empty content; each state that can act carries exactly one named action, loading and content declare that they offer none, and the no-content target is a host-supplied callback this card does not resolve"
  - "A3 every rendered domain value keeps its text and numerals: absolute dates render as a spelled month-name form derived from neither the system locale nor its numeral system, this view renders no clock time at all, and every count renders as a complete plural-aware phrase carrying the whole count"
  - "A4 runtime acceptance tests assert typed declaration values through the compiled reducer entry points only; source, resources and inspected compiled artifacts are never an oracle, Compose wiring stays compile-only, and every automated requirement carries an executable semantic mutation receipt"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: reducer tests pin the per-state action arity, the secondary type of a feedback banner recovery so no state can present two primary actions, the declared visible-control policy, the non-blank content and single named action of every state, and the fixed date and plural-aware count forms, with A1-A4 semantic-mutation receipts and no source-derived oracle; render-time constraints named in the verification table are manual design review and make no automated claim.
review_gate: codex {verdict:pass}
hygiene: 每个 typed 声明值由单点变异击杀；人工评审项不冒充自动验收。
doc_sync: TASK-BOARD 记录合并 OID 并登记后继卡 T4-SCHEDULE-UI-SYMBOL-CHROME；T4-SCHEDULE 子链待该后继卡合并后才标记完成，本卡与 T4-SCHEDULE-UI 一并 R5 归档。
---

# T4-SCHEDULE-UI-PRESENTATION

## Deliverable

在 `T4-SCHEDULE-UI` 已钉住的行为骨架上，加一层**可断言的最小界面契约**：每个状态的动作数、
反馈 banner 与它的 secondary recovery、「没有一个状态是空白的」、以及领域值的日期与计数形态。渲染期约束
（间距、对比度、200% 字号、无渐变）在本卡的黑盒测试面内**无法机检**，一律走人工设计评审
并在下方逐条标注——不冒充自动验收。

**符号化 chrome 那一半不在本卡**：chrome/content 类型分割、in-file 字形、RTL 镜像、
目标尺寸、动效 token 与 reduced-motion 归后继卡 `T4-SCHEDULE-UI-SYMBOL-CHROME`（见下）。
本卡的动作仍按现状携带可见文字标签，由后继卡换成字形——这是一条诚实的演进：
本卡先把「每个状态恰好一个具名动作」钉死，后继卡再把那个名字从可见文字换成 accessible-name key。

## 拆分依据（2026-09-03 用户裁定）

2026-09-03 的需求重写把 `T4-SCHEDULE-UI` 由 A1–A5 扩为 A1–A9 并留下 12 条 OD。用户裁定按
**行为 / 呈现两半拆卡**，本卡承接呈现半：原 A6–A9（本卡重编号为 A1–A4）、REQ-030..060、
图形对照表，以及除 OD-3 外的全部 11 条未决决策。

合卡形态自下而上估算 ≈ 1600–1900 changed lines，远超 R3 的 1000 行 / 60000 字符硬闸（L266，
同 `T4-SCHEDULE-REMINDER-FLIGHT` commit `3aba57b6` 的做法：动手前拆，而非 ship 时压）。

### 本卡自身的体量估算（L266，写 RED 之前）

| 项 | 估算 | 依据 |
|---|---|---|
| 11 枚 in-file `ImageVector` | 150–250 | 仓内无图标依赖（REQ-058），每枚 path 约 10–25 行 |
| chrome/content 类型分割 + token 声明 | 150–200 | REQ-050..060 与 REQ-030..034 的 typed 值 |
| Compose 接线增量 | 80–120 | 七状态套用上述 typed 值 |
| 测试 + 变异收据 | 300–400 | 可机检的 REQ 约 20 条 |
| **合计** | **680–970** | **贴近 1000 行闸** |

> **止损点**：若 OD-2 裁定为「不扩 `res/`、glyph 用 in-file `ImageVector`」且实测越过 900 行，
> **按 A1/A3（最小界面契约）与 A2/A4（符号化 chrome + 无障碍声明）二次拆卡**，
> 不删注释、不打包字面量、不修剪变异收据。

### 二次拆卡（2026-09-08 用户裁定，止损点触发）

OD 收口后按 §止损点重新自下而上计数，**落在 ≈990 changed lines**——已越 900 止损点、
且距 R3 的 1000 行硬闸只剩十行余量，任何一条 R3 finding 的修复都将无处安放。
用户裁定**按卡内早已写下的那条线拆**：

| 卡 | 承接 | 估算 |
|---|---|---|
| `T4-SCHEDULE-UI-PRESENTATION`（本卡） | 原 A1/A3 → 现 A1–A4：token 词汇、动作数、无空状态、日期与计数形态 | ≈610 |
| `T4-SCHEDULE-UI-SYMBOL-CHROME`（后继） | 原 A2/A4：chrome/content 类型分割、8 枚字形、RTL 镜像、目标尺寸、动效 token、reduced-motion | ≈750 |

> **该表是二次拆卡当时的划分，已被后续两次移交取代**：token 词汇先交出取值绑定、R3 第 2 轮后
> 连同声明整条交给后继卡（见 §R3 第 2 轮的四条 finding）。本卡最终承接的是**动作数与反馈 banner、
> 无空状态、日期与计数形态**三块。

**为何 OD 收口砍掉 5 枚字形后总量反而回升**：收口本身**加回了两枚**——
`CLEAR_FILTER` 动作槽需要自己的字形（原表漏列），空状态的 `NEXT` 动作需要一枚**方向性**字形
（它同时是 REQ-057「方向性字形须声明 RTL 镜像」不落空的唯一实例；其余 7 枚皆非方向性）。
故字形由 6 枚回到 8 枚，模型层的 token 与 content 值分类也比原估更宽。
**这正是 L266 要的那次「写 RED 之前重算」**：估算在需求变化后必须重做，不能沿用建卡时的数字。

### 收口后的重新估算（2026-09-08，OD 全部裁定之后 · 拆卡前的合卡口径）

OD-1 判四个巡检类型标签与 `section-header` 分组名为 **content**、OD-4 判 `novel` 类一律保留文字标签，
于是图形对照表第 5–8、11 行**不再需要 glyph**，in-file `ImageVector` 由 11 枚降为 **6 枚**
（refresh / settings / notifications-off / filter / schedule / warning）。

| 项 | 原估 | 收口后 | 变化依据 |
|---|---|---|---|
| in-file `ImageVector` | 150–250 | **90–150** | 6 枚而非 11 枚（OD-1 + OD-4） |
| chrome/content 类型分割 + token 声明 | 150–200 | 150–200 | 不变 |
| Compose 接线增量 | 80–120 | 80–120 | 不变 |
| 测试 + 变异收据 | 300–400 | 300–400 | 不变（REQ-042/043 转为否定声明，不减可机检项） |
| **合计** | 680–970 | **620–870** | **低于 900 止损点，本卡不二次拆分** |

实测超过 900 行仍按上述止损点拆卡，不以删注释或修剪变异收据压预算。

## 前置：OD-3 已由 `T4-DESIGN-SYMBOL-CHROME-V2` 收口（已合并）

原卡列出三处与 `context/DESIGN.md` 的抵触。开卡前重新 grep 四处权威面后，结论修正如下：

| 冲突点 | 原判 | 复核结论 | 处置 |
|---|---|---|---|
| 纯图标操作按钮 | 视为抵触 | **本就允许**：`docs/UI-UX-ELEMENTS.md:35`「纯图标操作必须使用 `icon-button` 并同时提供 tooltip 和无障碍名称」，`context/DESIGN.md:1622` 同构 | 无需修订 |
| 计数徽标不得纯图标 | 抵触 | 属实（[card:context/DESIGN.md:1763]），且与同文档 `state-badge` 的 `DOT` 变体自相矛盾 | **已收口**：`context/DESIGN.md:1563`「Domain values are never carried by a glyph alone」+ 计数条款重写（master `53673571`） |
| 状态色须配 label **和** icon | 抵触 | 属实（[card:context/DESIGN.md:1812]） | **已收口**：`context/DESIGN.md:1561` `Symbol-only chrome` 准入条件 5（状态须作为状态变更播报且脱离颜色仍可辨）（master `53673571`） |
| 底部导航须带标签 | 视为抵触 | **不在范围内**：根导航在两张 Schedule 卡的 `non_goals` 内 | 不触碰 |

**本卡不得自行修订 `context/DESIGN.md`**（见 `forbid` 第 1 条）：依赖卡合并后，本卡按修订后的
具名条件集 `Symbol-only chrome` 落地。

## 范围边界：chrome 与 content

- **chrome（界面文字）**＝控件与容器自身的说明性文字：按钮/图标按钮标签、filter chip 标签、
  `section-header` 文本、提示与占位符、默认可见 tooltip、状态消息标题。
  **符号化只作用于 chrome，且整半归后继卡 `T4-SCHEDULE-UI-SYMBOL-CHROME`。**
- **content（内容）**＝数据驱动或用户可读事实：物业名、巡检类型名、到期日期与相对时间、计数短语、
  失败的自然语言说明。**content 一律保持文字与数字，符号化永不适用——这是本卡 A3 的断言面。**
- 判据（供实现与测试共用）：一个值若在**没有领域数据**时仍要出现，它是 chrome；若它的存在与取值由
  `ScheduleAdvice` / `ReminderRegistrationCause` / 物业记录决定，它是 content。
- 边界不清的具体串一律进 §未决决策，不由实现自行归类。**OD-1 已于 2026-09-08 裁定**：
  四个巡检类型 filter 标签与 `section-header` 分组名**皆为 content，保留文字**——
  `context/DESIGN.md:1561` 的 `Symbol-only chrome` 把 status 与 relation 点名为 domain value，
  巡检类型正是其一，而 domain value 永不由字形单独承载。

## 与已合并 reducer 的对齐（2026-09-08，写 RED 之前发现）

把 A1/A2 复述成可测形式时撞上一处**卡片与已合并代码的真实抵触**，在写任何测试之前先改卡：

原 A1 写「exactly one primary action」、原 A2 把 `loading` 列进「各带恰好一个具名动作」的状态里。
但 `T4-SCHEDULE-UI` 已合并的 `actionSlot`（`ScheduleModels.kt`）明确给 `Loading` 与 `Content`
**零个**动作，并在 KDoc 写下理由：loading 是本地磁盘读取上的 300ms 阈值态、**此刻无事可做**，
而内容屏的动作属于它的行。本卡 `forbid` 又禁止改动该 reducer 已钉住的状态集。

**两条路只有一条是诚实的**：改 reducer 被本卡明令禁止；把 loading 的动作断言悄悄放宽成
「有就断言、没有就跳过」则是 vacuous pass（L19/L20/L47）。故取第三条——
**把「恰好一个」改写成「至多一个，且每个状态都必须显式声明这个数目」**：
提供动作的状态声明它的那一个，不提供的**声明这个缺席**，于是「某状态忘了声明动作」
不是靠人记得检查，而是**类型上不成立**。这与同文件 `ScheduleBadge.NONE` 的既有做法同源
（「[NONE] 是被声明出来的值而非缺失的值」），也与 `ScheduleScreenState` 把互斥层做成 sealed 的
理由一致。REQ-030 与 A1/A2 已按此重写，标准是**收紧**（多了「必须显式声明缺席」这一条）
而非放宽。

## 需求

写法：EARS，同 `T4-SCHEDULE-UI`。`[待定：X]` 表示该数字/规则源不存在，已进 §未决决策。

### B 组 · 最小排程界面（承接 A1–A3）

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-030 | Ubiquitous | The schedule view shall expose at most one primary action per state and shall declare that count for every state, so that a state offering none declares the absence; it shall place every secondary action outside the row, in the `feedback-banner` or the overflow menu. | [card:context/DESIGN.md:1618「never contains a second nested button」]；「at most」而非「exactly」的理由见 §与已合并 reducer 的对齐 |
| REQ-031 | Ubiquitous | The schedule view shall declare at most two top-app-bar actions. | [card:context/DESIGN.md:`components.top-app-bar.actionsMax: 2`] |
| REQ-035 | Ubiquitous | The schedule view shall declare no gradient, no drop shadow, no glass effect and no decorative illustration, and shall express layering through tonal surface levels only. | [card:context/DESIGN.md:1462-1469,1815,1779] |
| REQ-036 | Ubiquitous | The schedule view shall render an absolute date for every due occurrence, and shall render relative time only in addition to that absolute date. | [card:context/DESIGN.md:1766]（例：`3 months ago · 19 May 2026`） |
| REQ-037 | Ubiquitous | The schedule view shall render every count as a complete plural-aware phrase. | [card:context/DESIGN.md:1762] |
| REQ-038 | State-Driven | While the schedule data is being read from local storage, the schedule view shall render the loading state only after `300ms` have elapsed, and shall render no network-style indeterminate spinner. | A2 · [card:context/DESIGN.md:1777,1783]，`docs/UI-UX-ELEMENTS.md:99` |
| REQ-039 | State-Driven | While the schedule reducer renders the no-content empty state, the schedule view shall fill that state's single action slot with a named next action whose target is a host-supplied callback, and shall not itself resolve that target to a destination. | A2 · OD-11（已裁定：宿主注入回调）· 承接 `T4-SCHEDULE-UI` REQ-009 |
| REQ-040 | Ubiquitous | The schedule view shall render every absolute date as a spelled month-name form (`19 May 2026`) and shall derive it from neither the system locale nor its numeral system, and it shall render no clock time at all. | OD-8（已裁定：固定 NZ 形态）· [card:context/DESIGN.md:1766]；**无时刻**：排程只呈现到期**日期**，24h 时刻形态在本视图无实例，故不保留无调用者的格式化入口（见 §体量收口） |
| REQ-041 | Ubiquitous | The schedule view shall render today and now as the same absolute date form required by REQ-040 with the relative phrase added, and shall render no occurrence conflict at all. | OD-9（已裁定：不呈现冲突）· 4 周上限归 `T4-COMPLIANCE-ENGINE` |
| REQ-042 | Ubiquitous | The schedule view shall declare its visible tappable-control count unbounded as a typed value chosen among alternatives, and REQ-030 and REQ-031 shall be its only control-count constraints. | OD-7（已裁定：不设上限——内容态是滚动列表，任何固定 N 一旦行数足够即为假）；**R3 第 2 轮指出原文只是散文、无 typed 值也无断言**，遂落为 `ScheduleControlCountPolicy` |
| REQ-043 | Ubiquitous | This card shall declare no first-render budget and no scroll-frame budget, because A4 places the Compose runtime outside its test surface and such a budget would carry no oracle. | OD-10（已裁定：不设数值）· REQ-038 的 `300ms` 仍成立，它是 typed 声明而非渲染实测 |
| REQ-048 | Ubiquitous | The schedule view shall carry non-blocking feedback in a `feedback-banner` holding both its copy and its one secondary recovery, and shall use no modal dialog for it. | [card:context/DESIGN.md:1787]；R3 第 2 轮前为散落的 `Text` + 通用 `Button` |
| REQ-049 | Ubiquitous | The schedule view shall expose no destructive action, and therefore declares no undo or confirm path. | 本卡 `non_goals`；[card:context/DESIGN.md:1787] |
| REQ-053 | Ubiquitous | Every content value shall keep its text and numerals unchanged, and shall not be replaced by a glyph. | A3 · §范围边界 · [card:context/DESIGN.md:1762] |

> **REQ-032/033/034 与 REQ-044..047、REQ-050..052、054..060 已移交 `T4-SCHEDULE-UI-SYMBOL-CHROME`**
> （token 词汇于 2026-09-08 R3 第 2 轮后**整条移交**：既然取值绑定归后继卡，词汇声明也随之过去，
> 由一张卡端到端拥有 token，本卡遂不再声明任何自己不应用的东西）
> （语义颜色解析、200% 字号换行、动效 token 与 reduced-motion、chrome/content 类型分割、字形、
> RTL 镜像、目标尺寸与非文本对比度）。**REQ 编号不重排**：编号是稳定标识，跨卡沿用同一号，
> 避免两卡出现同号异义（同 CLAUDE.md「检查项 ID 稳定」的同一条理由）。
> **REQ-053 留在本卡**：它是「领域值保留文字与数字」，正是本卡 A3 的核心断言；
> 后继卡引用它作为 chrome/content 分割的 content 一侧，不重复拥有。

## 体量收口（2026-09-08，R3 第 1 轮之后）

R3 第 1 轮拦下一条**属实**的 finding：`contentOf` 把行只投影成物业名，`ContentText` 只画
`value.text`，于是**到期日与相对短语根本没到过屏幕**，而所有日期测试都直接调 `dueLine` 因而全绿——
REQ-036/040/041 是被声明、没被交付。这与本仓反复出现的同一形态一致：**写下的保证超出断言真正兑现的东西**，
且六轮本地自查全部漏掉、只有第二模型抓到。修法是把行投影收进 `rowContentOf`（到期行当且仅当该行携带
`dueAt`），`contentOf` 由它组合，屏幕两个字段都画。

修完体量达 **1040 changed lines**，越 R3 的 1000 行硬闸。按 rubric §4.1「超限必须拆成可独立验证的
有序子卡」，用户裁定作**第三次范围移交**：

| 移交项 | 去向 | 理由 |
|---|---|---|
| token → Compose 取值绑定（spacing→dp / typography→TextStyle / shape / colour） | `T4-SCHEDULE-UI-SYMBOL-CHROME` | 后继卡本就要为字形重写该屏；**REQ-032/033/034 的「draw」子句随之移交**，故本卡不留「声明了却没应用」的缺口——那正是第 1 轮 finding 的形态 |
| `absoluteDateTime` 与 `twoDigits` | 删除 | 排程只呈现到期**日期**，无任何调用者渲染时刻；留着就是又一处「声明了不交付」。REQ-040 同步收窄 |
| 三项冗余测试 | 删除 | R4 mutation-survivor 剪枝（卡内 `hygiene` 要求）：无任何变异**单独**杀死它们 |

收口后 **923 changed lines / 47774 chars**，留约 77 行余量。**没有删注释、没有打包字面量、
没有修剪任何变异收据**——这三条正是卡内止损点明令禁止的压预算手法。

## R3 第 2 轮的四条 finding（2026-09-08，全部属实并已修）

轮次上限达顶（`ReviewRoundCap=2`），用户裁定 `-ResetRounds`：两轮提的是**互不相同**的真缺陷、
每条都被接受并修复、每次修复都带来新的击杀变异，不属该上限要止住的「同一争点拉锯」
（同 `T3-REPORT-HTML-RENDERER` / `T3-REPORT-HTML-CHARACTER-POLICY` 的先例）。

| # | finding | 判定 | 修法 |
|---|---|---|---|
| 1 | 权限恢复与状态动作各自渲染成同一个 primary `ActionButton`，故 BLOCKED × 任一可动作状态会同时出现**两个 primary**（违反 REQ-030），而测试只组合过 BLOCKED × `Loading` | 属实 | 反馈改为 `ScheduleFeedbackBanner`，其 recovery 是 **`ScheduleSecondaryAction`（另一个类型）**——于是「一屏两个 primary」不是被规则禁止，而是**写不出来**；新增测试逐一组合**每个**屏幕状态 × BLOCKED |
| 2 | REQ-048 要求非阻断反馈进 `feedback-banner`，实际是根 `Column` 里散落的 `Text` + 通用 `Button` | 属实 | banner 成为一个类型与一个渲染区域，copy 与 recovery 同属其中；`perform()` 抽出，两个调用点共用同一套 slot 路由 |
| 3 | REQ-042 在验收表里标着 automated，但 diff 里**既无 typed 值、也无断言、也无变异收据** | 属实，且是**我自己的验收表在说假话**——与第 1 轮同一类错误（写下的声明超出证据） | 落为 `ScheduleControlCountPolicy.{Unbounded, AtMost}`，`Unbounded` 是在备选中被选中的答案而非唯一可表达值；配断言与变异 M32 |
| 4 | `monthNames` 是 12 条各自手写的字符串，测试只覆盖 5 月与 9 月，其余十个月的错字会静默通过 | 属实 | 新增按表驱动的十二个月断言，配变异 M33（把 `August` 写错一个字母），该变异只被这条测试杀死 |

**为腾出空间，token 词汇（`ScheduleTypographyToken` / `ScheduleSpacingToken` / `ScheduleShapeToken` /
`ScheduleColorRole` 及两个谓词）连同 REQ-032/033/034 整条移交后继卡**——后继卡本就要绑定它们，
由一张卡端到端拥有 token，比「这张声明、那张应用」更内聚，也让本卡不再声明任何自己不应用的东西。

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-030, 031, 035, 042, 048, 049 | **REQ-030/031/042 automated**（动作数、top-app-bar 动作上限、控件数策略皆为 typed 值），**REQ-048 的「recovery 属于 banner 且为 secondary」亦 automated**（类型层）；REQ-035/049 与 REQ-048 的视觉形态 manual · 对照 `context/DESIGN.md` 的设计评审 | typed 值 / 人工评审 |
| A2 | REQ-038, 039 | automated · 五个状态各断言非空内容；能动作的四个各断言恰好一个具名动作，`Loading` 与 `Content` 各断言**声明出来的缺席**（见 §与已合并 reducer 的对齐）；空状态的目标是宿主注入的回调，本卡不解析目的地 | 领域状态 |
| A3 | REQ-036, 037, 040, 041, 053 | **REQ-036/037/040/053 automated**（日期形态、复数感知计数短语与「领域值保留文字数字」皆为 typed 值）；REQ-041 的「今天/现在」相对短语 automated，「不呈现冲突」由本卡不存在该类型证明 | typed 值 |
| A4 | 全部 automated 项 + REQ-043 | automated · 变异收据（selector / RED exit / 前后同 SHA-256）；REQ-043 是本卡对自身测试面的否定声明，不产生断言 | 收据本身 |
| 移交后继卡 | REQ-032/033/034, 044..047, 050..052, 054..060 | 见 `T4-SCHEDULE-UI-SYMBOL-CHROME`；token 词汇的**声明与绑定**均归它，本卡对 token 不作任何验收声明 | — |

> **诚实说明**：A4 把 Compose runtime 排除在测试面外，故「间距/无渐变」这类**渲染期**约束
> 在本卡内**无法机检**，只能人工评审。可机检的部分之所以可机检，是因为 REQ 把它们写成了
> `ScheduleModels.kt` 里的 typed 声明值（token 名、动作数、日期形态、计数短语），
> 而不是渲染结果。实现时若把某条从 typed 值退化成散落的字面量，该条即失去 oracle。

## 未决决策（Open decisions）

**开工前须全部收口** —— **2026-09-08 已全部收口**：OD-3 于 2026-09-03 裁定并由
`T4-DESIGN-SYMBOL-CHROME-V2` 执行完毕（master `53673571`），其余 11 条于 2026-09-08 由用户逐条裁定。
下表「裁定」列即本卡开工依据；此后实现不得重开其中任一条，需变更走新一次用户裁定并记进 §变更记录。

**裁定同时约束后继卡**：二次拆卡发生在收口之后，故 `T4-SCHEDULE-UI-SYMBOL-CHROME` 直接继承下表
——OD-1/4/5（字形该不该出现、`novel` 类保留文字、字形内无数字）与 OD-2（不扩 `res/`）主要落在后继卡，
本卡受 OD-6/7/8/9/10/11 约束。后继卡不得重新解释任一条。

| # | 问题（闭合式） | 候选 | **裁定（2026-09-08）** | 阻塞 |
|---|---|---|---|---|
| OD-1 | 四个巡检类型的 filter 标签与 `section-header` 分组名，算 chrome 还是 content？ | (a) 算 content，保留文字 (b) 算 chrome，纯 glyph + accessible name (c) 算 chrome，但保留文字直到用户确认 | **(a) content，保留文字** | REQ-050, REQ-054, 图形对照表 5–8/11 行 |
| OD-2 | 是否为本卡扩 `allow_paths` 至 `res/drawable/` 与 `values/strings.xml`（姊妹卡 `T3-REPORT-EXPORT-UI` 二者皆有）？ | (a) 不扩，glyph 用 in-file `ImageVector`，accessible name 用内联字面量 (b) 扩两条，glyph 用矢量 drawable、文案入 strings.xml | **(a) 不扩，allow_paths 维持三条** | REQ-058, REQ-052 |
| ~~OD-3~~ | ~~与 `context/DESIGN.md` 的抵触如何收口？~~ | **已裁定（2026-09-03）：选项 (b) 先修订 DESIGN.md** | 已执行完毕 → `T4-DESIGN-SYMBOL-CHROME-V2` | → 依赖卡 |
| OD-4 | `novel` 类 glyph 接受纯图标吗？ | (a) 接受，配长按 tooltip (b) 仅 `novel` 类保留文字标签 (c) 首次运行一次性提示后纯图标 | **(b) `novel` 类保留文字标签** | REQ-054 |
| OD-5 | glyph 内允许出现数字吗（如 due 计数）？ | (a) 一律禁止，计数作为独立 content 文本 (b) 仅 due 计数徽标允许 | **(a) 一律禁止** | REQ-055, 图形对照表第 9 行 |
| OD-6 | 排程界面允许几种 typography token？ | (a) 4 种 (b) 5 种 | **(b) 5 种** | REQ-033 |
| OD-7 | 单一状态下同时可见的可点控件上限？ | (a) 5 (b) 7 (c) 不设上限，只受 REQ-030 约束 | **(c) 不设上限** | REQ-042 |
| OD-8 | 日期/时间格式与 12h/24h 取值来源？ | (a) 跟随系统 locale 与系统 12/24h 设置 (b) 固定 NZ 形态（`19 May 2026`，与 DESIGN 示例一致） | **(b) 固定 NZ 形态 + 24h** | REQ-040 |
| OD-9 | 「今天/现在」与「冲突」如何呈现？（排程列表无重叠事件概念，最接近的是合规 4 周上限，归 `T4-COMPLIANCE-ENGINE`） | (a) 本卡不呈现冲突，只呈现 due/overdue (b) 预留 blocked 徽标，待合规引擎接线 | **(a) 不呈现冲突** | REQ-041 |
| OD-10 | 首屏渲染与滚动帧预算数值？ | (a) 首屏 300ms / 帧 16.7ms (b) 本卡不设数值，只守 REQ-038 的「本地读取不显示网络式 spinner」 | **(b) 不设数值** | REQ-043 |
| OD-11 | 无内容空状态的「下一步」指向何处？（根导航在 `non_goals` 内，本卡无法跳转 Properties） | (a) 只陈述事实、不给动作（需同时放宽 A3） (b) 给一个由宿主注入的回调，本卡不实现目标页 | **(b) 宿主注入回调** | REQ-039, A3 |
| OD-12 | `novel` glyph 五秒识别测试的通过阈值与参与人数？ | (a) 4/5 人正确 (b) 不做该测试，改由 OD-4 直接裁定 | **(b) 不做该测试**（OD-4 已使 `novel` 类无纯图标实例） | REQ-054 验证方法 |

## 决策记录（Decision log）

| # | 用户原话 / 卡内原文 | 改写形态 | 理由 |
|---|---|---|---|
| 1 | 「symbols replace visible interface text」 | §范围边界：chrome（控件说明文字）纯 glyph；content（数据驱动事实）保留文字与数字 | 原话未区分「界面文字」与「数据」。若字面执行，物业名与到期日期也会被符号取代，界面即失去信息。 |
| 2 | 「a minimal, top-tier-app schedule interface」 | B 组 REQ-030..049 的可检查约束（动作数、token 来源、装饰禁令、状态渲染、动效时长） | 「minimal」「top-tier」是目标不是需求；测试无法对形容词取值。 |
| 3 | 「symbols replace visible interface text」 | REQ-051：chrome 类型不含可见文本字段 | A5 禁止源码字符串 oracle 且不测 Compose runtime，故只能把该保证做进类型，否则该需求在本卡内不可验证。 |
| 4 | 「accessibility name」 | REQ-052 的 accessible-name **key**，渲染期才解析为文本 | 可访问名不是可见文字，故不破坏 A2；同时满足 WCAG 1.1.1/4.1.2。 |
| 5 | 图标资产格式（下发指令里该项为空） | REQ-058 判定为 vector（in-file `ImageVector`） | 仓内无图标依赖、无 `res/drawable/`，且本卡 `forbid` 禁改依赖。改用 bitmap 会需要每密度资产与新目录，理由在卡内与仓内都不存在。 |
| 6 | OD-3 三选一 | 用户 2026-09-03 裁定选项 (b)：先修订 `context/DESIGN.md` | `CLAUDE.md`「权威文档」21 规定 UI 规范细节唯一服从该文件，不得由 UI 卡就地绕过。 |
| 7 | OD-1（chrome 还是 content） | 用户 2026-09-08 裁定 (a)：巡检类型标签与分组名为 content，保留文字 | 依据是已合并的 `context/DESIGN.md:1561`：domain value 明确含 status 与 relation，巡检类型即其一，而 domain value 永不由字形单独承载。副作用是图形对照表 5–8/11 行不再需要 glyph。 |
| 8 | OD-4（`novel` 类是否纯图标） | 用户 2026-09-08 裁定 (b)：`novel` 类保留文字标签 | NN/g 明确反对以 hover/长按揭示标签（触摸设备上不奏效）。OD-1 之后 `novel` 类只剩分组名一处，保留文字的代价接近零而风险归零。 |
| 9 | OD-7（可点控件上限） | 用户 2026-09-08 裁定 (c)：不设上限 | 内容态是滚动列表，可见控件数无界；任何固定 N 在行数足够时即为假陈述。真正成立的约束是 REQ-030 与 REQ-031。 |
| 10 | OD-10（渲染性能预算） | 用户 2026-09-08 裁定 (b)：不设数值 | A5 把 Compose runtime 排除在测试面外，写下的 ms 预算将没有任何 oracle——那正是本卡声明不做的「冒充自动验收」。REQ-038 的 `300ms` 不受影响，它是 typed 声明。 |
| 11 | OD-12（五秒识别测试） | 用户 2026-09-08 裁定 (b)：不做 | 非独立判断，而是 OD-4 的推论：`novel` 类已无纯图标实例，该测试没有可测对象。 |

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 拆出的呈现半（原 A6–A9 → 本卡 A1–A4，新增 A5 验证契约）、REQ-030..060、图形对照表与 11 条 OD。开卡前复核四处权威面，修正原「三处抵触」为「两处真抵触 + 一处本就允许 + 一处不在范围」。 |
| 2026-09-08 | **开工前收口 OD-1/2/4..12**（用户逐条裁定，见 §未决决策「裁定」列与 §决策记录 7–11）。据此改写 REQ-033/039/040/041/042/043/054/055，重写图形对照表（11 枚 glyph → **6 枚，且无一属 `novel`**），A2 覆盖面由 REQ-050..053+055 扩为 REQ-050..055，删去已消解的「C 组风险」行。体量重估 **620–870 changed lines**（低于 900 止损点，不二次拆分）。前置依赖复核：`context/DESIGN.md:1561` `Symbol-only chrome` 具名节已随 `T4-DESIGN-SYMBOL-CHROME-V2`（master `53673571`）落地，OD-3 的执行方由退役卡 `T4-DESIGN-SYMBOL-CHROME` 更正为 V2。 |
| 2026-09-08 | **二次拆卡（用户裁定，止损点触发）**：收口后重算落在 ≈990 changed lines，越过卡内 900 止损点且距 1000 硬闸仅十行，遂按 §止损点早已写下的那条线拆——本卡留 A1/A3（现重编号 A1–A4：token 词汇、动作数、无空状态、日期与计数形态，≈610 行），符号化 chrome 与无障碍声明整半移交新卡 `T4-SCHEDULE-UI-SYMBOL-CHROME`（≈750 行）。移交 REQ-044..047 与 REQ-050..052、054..060 及整张图形对照表；**REQ 编号不重排**（跨卡稳定标识）；REQ-053 留本卡作 A3 断言面。acceptance 由 A1–A5 重编为 A1–A4，验证表、`dod_assert`、`doc_sync` 与 `non_goals` 同步。**为何砍掉 5 枚字形后总量反升**：收口加回 `CLEAR_FILTER` 与方向性 `NEXT` 两枚，后者是 REQ-057 不落空的唯一实例。 |
| 2026-09-08 | **R3 第 2 轮四条 finding 全部属实并已修**（见 §R3 第 2 轮的四条 finding）：两个 primary 动作并存、缺 `feedback-banner`、REQ-042 有声明无证据、十二个月只测两个月。轮次上限达顶，用户裁定 `-ResetRounds`。为腾空间把 token 词汇连同 REQ-032/033/034 **整条**移交后继卡，收至 911 行。 |
| 2026-09-08 | **R3 第 1 轮 finding 属实并已修**（到期日从未到达屏幕，见 §体量收口），修后体量 1040 行越闸，用户裁定第三次范围移交：token 取值绑定连同 REQ-032/033/034 的 draw 子句移交后继卡、删除无调用者的 `absoluteDateTime`、按 R4 剪枝三项冗余测试，收至 923 行。 |
| 2026-09-08 | **写 RED 之前对齐验收契约，发现卡片与已合并 reducer 抵触**：原 A1「exactly one primary action」与原 A2 把 `loading` 列入「各带恰好一个具名动作」，同 `T4-SCHEDULE-UI` 已合并的 `actionSlot`（`Loading` 与 `Content` 各零个，KDoc 写明理由）直接冲突，而本卡 `forbid` 禁止改动该 reducer。既不改 reducer、也不把断言放宽成 vacuous pass，改取**收紧**写法：「至多一个，且每个状态必须显式声明这个数目，不提供者声明缺席」——同文件 `ScheduleBadge.NONE`「声明出来的值而非缺失的值」的既有做法。REQ-030、A1、A2 与验证表同步重写，新增 §与已合并 reducer 的对齐 记录理由。 |
