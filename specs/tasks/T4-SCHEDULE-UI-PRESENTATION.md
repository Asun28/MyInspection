---
id: T4-SCHEDULE-UI-PRESENTATION
title: 排程界面的最小呈现契约与符号化 chrome
depends_on: [T4-SCHEDULE-UI-REMINDER-ACTIONS, T4-DESIGN-SYMBOL-CHROME]
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
  - 修改 context/DESIGN.md（其修订归 T4-DESIGN-SYMBOL-CHROME）
  - 修改 MainActivity、根导航、依赖、schema、navigation-bar 标签或 app shell chrome
  - 改动 T4-SCHEDULE-UI 已钉住的 reducer 状态集、权限时序或 pending/retry 转移语义
  - 用源码字符串读取或反编译产物代替 typed 值断言
non_goals:
  - reducer 状态机与路由效果（T4-SCHEDULE-UI 已拥有并已验收）
  - 权限时序、授权恢复、pending/retry 行为（T4-SCHEDULE-UI-REMINDER-ACTIONS 已拥有并已验收）
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
  - 新增图标依赖（OD-2 若裁定扩 res/，仍不得引入 material-icons 依赖）
acceptance:
  - "A1 every schedule state exposes exactly one primary action, declares at most two top-app-bar actions, draws spacing, type, shape and colour only from context/DESIGN.md token names, and declares no gradient, drop shadow or illustration"
  - "A2 every chrome control value is a typed glyph carrying a non-null accessible-name key and no visible-text field, while every content value keeps its text and numerals"
  - "A3 no declared state is blank: empty, filtered-empty, loading, error and permission-blocked each carry non-empty content and exactly one named next or recovery action"
  - "A4 every interactive control declares at least 48dp target size with at least 8dp separation, status is carried by glyph and position as well as colour, directional glyphs declare RTL mirroring, and every animation duration is a declared motion token with a reduced-motion variant"
  - "A5 runtime acceptance tests assert typed declaration values through the compiled reducer entry points only; source, resources and inspected compiled artifacts are never an oracle, Compose wiring stays compile-only, and every automated requirement carries an executable semantic mutation receipt"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: reducer tests pin the typed chrome/content split, declared token names, action arity, declared target sizes, declared motion tokens and mirror flags, with A1-A5 semantic-mutation receipts and no source-derived oracle; render-time constraints named in the verification table are manual design review and make no automated claim.
review_gate: codex {verdict:pass}
hygiene: 每个 typed 声明值由单点变异击杀；人工评审项不冒充自动验收。
doc_sync: TASK-BOARD 记录合并 OID 并把 T4-SCHEDULE 子链标记完成；本卡与 T4-SCHEDULE-UI 一并 R5 归档。
---

# T4-SCHEDULE-UI-PRESENTATION

## Deliverable

在 `T4-SCHEDULE-UI` 已钉住的行为骨架上，加一层**可断言的呈现契约**：每个状态的动作数、
token 取名来源、目标尺寸、动效 token、RTL 镜像、以及把「chrome 无可见文字」做进类型的
chrome/content 分割。渲染期约束（间距、对比度、200% 字号、无渐变）在本卡的黑盒测试面内
**无法机检**，一律走人工设计评审并在下方逐条标注——不冒充自动验收。

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

## 前置：OD-3 已由 `T4-DESIGN-SYMBOL-CHROME` 收口

原卡列出三处与 `context/DESIGN.md` 的抵触。开卡前重新 grep 四处权威面后，结论修正如下：

| 冲突点 | 原判 | 复核结论 | 处置 |
|---|---|---|---|
| 纯图标操作按钮 | 视为抵触 | **本就允许**：`docs/UI-UX-ELEMENTS.md:35`「纯图标操作必须使用 `icon-button` 并同时提供 tooltip 和无障碍名称」，`context/DESIGN.md:1622` 同构 | 无需修订 |
| 计数徽标不得纯图标 | 抵触 | 属实（[card:context/DESIGN.md:1763]），且与同文档 `state-badge` 的 `DOT` 变体自相矛盾 | `T4-DESIGN-SYMBOL-CHROME` REQ-104/105 |
| 状态色须配 label **和** icon | 抵触 | 属实（[card:context/DESIGN.md:1812]） | `T4-DESIGN-SYMBOL-CHROME` REQ-106/107 |
| 底部导航须带标签 | 视为抵触 | **不在范围内**：根导航在两张 Schedule 卡的 `non_goals` 内 | 不触碰 |

**本卡不得自行修订 `context/DESIGN.md`**（见 `forbid` 第 1 条）：依赖卡合并后，本卡按修订后的
具名条件集 `Symbol-only chrome` 落地。

## 范围边界：chrome 与 content

- **chrome（界面文字）**＝控件与容器自身的说明性文字：按钮/图标按钮标签、filter chip 标签、
  `section-header` 文本、提示与占位符、默认可见 tooltip、状态消息标题。**A2 只作用于 chrome。**
- **content（内容）**＝数据驱动或用户可读事实：物业名、巡检类型名、到期日期与相对时间、计数短语、
  失败的自然语言说明。**content 一律保持文字与数字，A2 不适用。**
- 判据（供实现与测试共用）：一个值若在**没有领域数据**时仍要出现，它是 chrome；若它的存在与取值由
  `ScheduleAdvice` / `ReminderRegistrationCause` / 物业记录决定，它是 content。
- 边界不清的具体串一律进 §未决决策，不由实现自行归类（当前：四个巡检类型 filter 标签、
  `section-header` 分组名 → OD-1）。

## 需求

写法：EARS，同 `T4-SCHEDULE-UI`。`[待定：X]` 表示该数字/规则源不存在，已进 §未决决策。

### B 组 · 最小排程界面（承接 A1、A3）

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-030 | Ubiquitous | The schedule view shall expose exactly one primary action per state, and shall place every secondary action outside the row, in the `feedback-banner` or the overflow menu. | [card:context/DESIGN.md:1618「never contains a second nested button」] |
| REQ-031 | Ubiquitous | The schedule view shall declare at most two top-app-bar actions. | [card:context/DESIGN.md:`components.top-app-bar.actionsMax: 2`] |
| REQ-032 | Ubiquitous | The schedule view shall draw every spacing value from `spacing.{xs,sm,md,lg,xl,2xl,3xl,touch,action,screen-gutter}` and shall use no other spacing literal. | [card:context/DESIGN.md:`spacing`]，`4dp` 基础节奏 |
| REQ-033 | Ubiquitous | The schedule view shall use at most `[待定：N]` distinct typography tokens, drawn only from the declared `typography` set. | OD-6 · [card:context/DESIGN.md:`typography`] |
| REQ-034 | Ubiquitous | The schedule view shall use `colors.primary` as its only interactive accent role, and shall use the status roles only to carry inspection or registration state. | [card:context/DESIGN.md:1811] |
| REQ-035 | Ubiquitous | The schedule view shall declare no gradient, no drop shadow, no glass effect and no decorative illustration, and shall express layering through tonal surface levels only. | [card:context/DESIGN.md:1462-1469,1815,1779] |
| REQ-036 | Ubiquitous | The schedule view shall render an absolute date for every due occurrence, and shall render relative time only in addition to that absolute date. | [card:context/DESIGN.md:1766]（例：`3 months ago · 19 May 2026`） |
| REQ-037 | Ubiquitous | The schedule view shall render every count as a complete plural-aware phrase. | [card:context/DESIGN.md:1762] |
| REQ-038 | State-Driven | While the schedule data is being read from local storage, the schedule view shall render the loading state only after `300ms` have elapsed, and shall render no network-style indeterminate spinner. | A3 · [card:context/DESIGN.md:1777,1783]，`docs/UI-UX-ELEMENTS.md:99` |
| REQ-039 | State-Driven | While the schedule reducer renders the no-content empty state, the schedule view shall fill that state's single action slot with a named next action targeting `[待定：目标]`. | A3 · OD-11 · 承接 `T4-SCHEDULE-UI` REQ-009 |
| REQ-040 | Ubiquitous | The schedule view shall render dates and times using `[待定：locale 与 12h/24h 规则]`. | OD-8 |
| REQ-041 | Ubiquitous | The schedule view shall render today and now as `[待定：规则]`, and shall render an occurrence conflict as `[待定：规则]`. | OD-9 |
| REQ-042 | Ubiquitous | The schedule view shall present at most `[待定：N]` tappable controls simultaneously per state. | OD-7 |
| REQ-043 | Ubiquitous | The schedule view shall complete first render within `[待定：ms]` and shall hold scroll frames within `[待定：ms]`. | OD-10 |
| REQ-044 | Ubiquitous | The schedule view shall resolve every colour through a semantic token name so that the light and dark schemes carry identical semantics. | A4 · `docs/UI-UX-ELEMENTS.md:119` |
| REQ-045 | Ubiquitous | The schedule view shall wrap rather than truncate every due date, status, count and failure reason at `200%` system font scale. | A4 · [card:context/DESIGN.md:1802]，`docs/UI-UX-ELEMENTS.md:118` |
| REQ-046 | Ubiquitous | The schedule view shall draw every animation duration from `motion.{pressFeedbackMs,stateChangeMs,expandMs,sheetEnterMs,exitMs}`. | A4 · [card:context/DESIGN.md:`motion`] |
| REQ-047 | State-Driven | While the operating system reports reduce-motion, the schedule view shall emit no translation, scale, pulse or repeating animation, and shall limit any remaining transition to a `100ms` opacity change. | A4 · `docs/UI-UX-ELEMENTS.md:120`，[card:context/DESIGN.md:1794] |
| REQ-048 | Ubiquitous | The schedule view shall carry non-blocking feedback in a `feedback-banner` and shall use no modal dialog for it. | [card:context/DESIGN.md:1787] |
| REQ-049 | Ubiquitous | The schedule view shall expose no destructive action, and therefore declares no undo or confirm path. | 本卡 `non_goals`；[card:context/DESIGN.md:1787] |

### C 组 · 纯图形界面 chrome（承接 A2）

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-050 | Ubiquitous | The schedule models shall classify every rendered value as either chrome or content according to §范围边界, and shall carry the two in distinct types. | 用户指令 · §范围边界 |
| REQ-051 | Ubiquitous | The chrome control type shall declare no visible-text field, so that a chrome control carrying a visible label cannot be constructed. | A2 · 仓内既有做法（`ReportContent` 私有构造器 · `LoadedTemplate.parse`） |
| REQ-052 | Ubiquitous | Every chrome control value shall carry a non-null accessible-name key. | A2 · [SOURCE: WCAG 2.2, SC 4.1.2 Name, Role, Value (Level A), https://www.w3.org/WAI/WCAG22/Understanding/name-role-value.html]，[SOURCE: WCAG 2.2, SC 1.1.1 Non-text Content (Level A), https://www.w3.org/WAI/WCAG22/Understanding/non-text-content.html] |
| REQ-053 | Ubiquitous | Every content value shall keep its text and numerals unchanged, and shall not be replaced by a glyph. | §范围边界 · [card:context/DESIGN.md:1762] |
| REQ-054 | State-Driven | While a chrome control's glyph is classified `novel` in §图形对照表, that control shall carry a fallback disclosure of the class named in that row. | OD-4 · [SOURCE: NN/g, Icon Usability, https://www.nngroup.com/articles/icon-usability/] |
| REQ-055 | Ubiquitous | Glyph artwork shall contain no letter and no word, and shall contain a numeral only where §图形对照表 declares that numeral to be content. | OD-5 |
| REQ-056 | Ubiquitous | The schedule view shall carry every state through a glyph and a position in addition to colour, and shall carry no state through colour alone. | A4 · `T4-DESIGN-SYMBOL-CHROME` REQ-106/107 · `docs/UI-UX-ELEMENTS.md:119` |
| REQ-057 | Ubiquitous | Every chrome control value whose glyph is directional shall declare that it mirrors under a right-to-left layout direction. | A4 · [code:android/app/src/main/AndroidManifest.xml:13 `supportsRtl="true"`] |
| REQ-058 | Ubiquitous | Every glyph shall be declared as one in-file Compose `ImageVector`, tinted from a theme colour role, and sized from `iconography.sizes.{sm,md,lg}` (`18` / `24` / `32`). | OD-2 · [code:android/gradle/libs.versions.toml:28-32]（无 icon 依赖，本卡 `forbid` 禁改依赖） |
| REQ-059 | Ubiquitous | Every chrome control shall declare a target of at least `48dp` by `48dp` with at least `8dp` to the adjacent target. | A4 · [SOURCE: Android Developers, Compose accessibility API defaults, https://developer.android.com/develop/ui/compose/accessibility/api-defaults]（`48dp`）· [card:context/DESIGN.md:`interaction.minTouchTarget`,`adjacentTargetGap`] |
| REQ-060 | Ubiquitous | Every chrome control shall meet at least `24` by `24` CSS px target size and `3:1` non-text contrast against the adjacent surface. | [SOURCE: WCAG 2.2, SC 2.5.8 Target Size (Minimum), Level AA, https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html]，[SOURCE: WCAG 2.2, SC 1.4.11 Non-text Contrast, Level AA, https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html] |

> **REQ-051 的写法是本组的关键**：A5 禁止以源码字符串或编译产物作 oracle，且 Compose runtime 不在测试面内，
> 所以「chrome 里没有可见文字」不能靠扫描断言，只能**做进类型**——chrome 控件类型不含可见文本字段，
> 于是「带标签的 chrome 控件」不是被规则禁止，而是**写不出来**，reducer 测试断言的是该 typed 值本身。
> 这与 `T3-REPORT-CONTENT-ADAPTER`（`compose(content)` 签名里没有 `Audience`）同一手法。

## 图形对照表（Glyph legend）

图标集：`context/DESIGN.md` 声明 `iconography.family: Material Symbols`（`outlined` 默认 / `filled` 选中）。
**但本仓不存在任何图标依赖或图标资源**，故本表「glyph」列给的是**形态概念**，落地形态由 REQ-058 规定为
in-file `ImageVector`；是否改为 `res/drawable/` 矢量资源见 OD-2。
`accessible name` 列是 REQ-052 的 key，**不是可见文字**，因此不违反 A2。

| # | 当前/应有 chrome 文字 | glyph（概念） | 可识别性类 | fallback 披露 | accessible name | 备注 |
|---|---|---|---|---|---|---|
| 1 | Retry | 循环箭头 refresh | universal | 无 | `Retry registering this reminder` | 承接 `T4-SCHEDULE-UI` REQ-021 |
| 2 | Open settings | 齿轮 settings | universal | 无 | `Open notification settings` | 承接 REQ-015；离开 app 边界 |
| 3 | Notifications blocked | 铃铛加斜杠 notifications-off | conventional | 长按 tooltip | `Notifications are turned off` | 承接 REQ-015/016 状态标 |
| 4 | Filter | 漏斗 filter | universal | 无 | `Filter by inspection type` | filter-chip-group 入口 |
| 5 | Routine | `[待定]`（OD-1） | novel | 长按 tooltip | `Routine inspections` | 类型名是否属 content 未定 |
| 6 | Annual | `[待定]`（OD-1） | novel | 长按 tooltip | `Annual home checks` | 同上 |
| 7 | Ingoing | `[待定]`（OD-1） | novel | 长按 tooltip | `Ingoing inspections` | 同上 |
| 8 | Exit | `[待定]`（OD-1） | novel | 长按 tooltip | `Exit inspections` | 同上 |
| 9 | Due（`state-badge`） | 时钟 schedule | conventional | 长按 tooltip | `Due` | 计数数字属 content（OD-5） |
| 10 | Needs attention（`state-badge`） | 三角感叹号 warning | universal | 无 | `Needs attention` | REQ-056 双重编码 |
| 11 | `section-header` 分组名 | `[待定]`（OD-1） | novel | `[待定]` | `[待定]` | 分组名是否属 content 未定 |

> **已记录的风险，不是静默选择**：NN/g 的结论是「多数图标没有通用含义，文字标签是必需的」，且明确反对用
> hover 揭示标签（「在触摸设备上无法奏效」）[SOURCE: NN/g, Icon Usability,
> https://www.nngroup.com/articles/icon-usability/]。第 5–8、11 行属 `novel` 类，正是该研究点名的高风险区。
> 因此 REQ-054 要求逐条 fallback 披露，并由 **OD-4** 交由用户确认是否接受这些条目纯图标。**本卡不代为决定。**

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-030..032, 034..037, 048, 049 | **REQ-030/031/032/034 automated**（token 名与动作数是 typed 值）；REQ-035/036/037/048/049 manual · 对照 `context/DESIGN.md` 的设计评审 | typed 值 / 人工评审 |
| A2 | REQ-050..053, 055 | automated · 类型层断言（chrome 类型无可见文本字段、accessible-name key 非 null） | typed 值 |
| A3 | REQ-038, 039 | automated · 五个状态各断言非空内容与恰好一个动作 | 领域状态 |
| A4 | REQ-044..047, 056..060 | **REQ-046/047/056/057/058/059 automated**（皆为 typed 声明值）；REQ-044/045/060 manual · 对比度与字号走设计评审 | typed 值 / 人工评审 |
| A5 | 全部 automated 项 | automated · 变异收据（selector / RED exit / 前后同 SHA-256） | 收据本身 |
| C 组风险 | REQ-054 | manual · 图形五秒识别测试，通过阈值 `[待定：N/参与人数]` | OD-12 |

> **诚实说明**：A5 把 Compose runtime 排除在测试面外，故「间距/对比度/无渐变/200% 字号」这类**渲染期**约束
> 在本卡内**无法机检**，只能人工评审。可机检的部分之所以可机检，是因为 REQ 把它们写成了
> `ScheduleModels.kt` 里的 typed 声明值（token 名、动作数、目标尺寸、motion token、mirror 标志），
> 而不是渲染结果。实现时若把某条从 typed 值退化成散落的字面量，该条即失去 oracle。

## 未决决策（Open decisions）

**开工前须全部收口**（OD-3 已由用户裁定并交由 `T4-DESIGN-SYMBOL-CHROME` 执行）。

| # | 问题（闭合式） | 候选 | 阻塞 |
|---|---|---|---|
| OD-1 | 四个巡检类型的 filter 标签与 `section-header` 分组名，算 chrome 还是 content？ | (a) 算 content，保留文字 (b) 算 chrome，纯 glyph + accessible name (c) 算 chrome，但保留文字直到用户确认 | REQ-050, REQ-054, 图形对照表 5–8/11 行 |
| OD-2 | 是否为本卡扩 `allow_paths` 至 `res/drawable/` 与 `values/strings.xml`（姊妹卡 `T3-REPORT-EXPORT-UI` 二者皆有）？ | (a) 不扩，glyph 用 in-file `ImageVector`，accessible name 用内联字面量 (b) 扩两条，glyph 用矢量 drawable、文案入 strings.xml | REQ-058, REQ-052 |
| ~~OD-3~~ | ~~与 `context/DESIGN.md` 的抵触如何收口？~~ | **已裁定（2026-09-03）：选项 (b) 先修订 DESIGN.md** | → `T4-DESIGN-SYMBOL-CHROME` |
| OD-4 | `novel` 类 glyph 接受纯图标吗？ | (a) 接受，配长按 tooltip (b) 仅 `novel` 类保留文字标签 (c) 首次运行一次性提示后纯图标 | REQ-054 |
| OD-5 | glyph 内允许出现数字吗（如 due 计数）？ | (a) 一律禁止，计数作为独立 content 文本 (b) 仅 due 计数徽标允许 | REQ-055, 图形对照表第 9 行 |
| OD-6 | 排程界面允许几种 typography token？ | (a) 4 种 (b) 5 种 | REQ-033 |
| OD-7 | 单一状态下同时可见的可点控件上限？ | (a) 5 (b) 7 (c) 不设上限，只受 REQ-030 约束 | REQ-042 |
| OD-8 | 日期/时间格式与 12h/24h 取值来源？ | (a) 跟随系统 locale 与系统 12/24h 设置 (b) 固定 NZ 形态（`19 May 2026`，与 DESIGN 示例一致） | REQ-040 |
| OD-9 | 「今天/现在」与「冲突」如何呈现？（排程列表无重叠事件概念，最接近的是合规 4 周上限，归 `T4-COMPLIANCE-ENGINE`） | (a) 本卡不呈现冲突，只呈现 due/overdue (b) 预留 blocked 徽标，待合规引擎接线 | REQ-041 |
| OD-10 | 首屏渲染与滚动帧预算数值？ | (a) 首屏 300ms / 帧 16.7ms (b) 本卡不设数值，只守 REQ-038 的「本地读取不显示网络式 spinner」 | REQ-043 |
| OD-11 | 无内容空状态的「下一步」指向何处？（根导航在 `non_goals` 内，本卡无法跳转 Properties） | (a) 只陈述事实、不给动作（需同时放宽 A3） (b) 给一个由宿主注入的回调，本卡不实现目标页 | REQ-039, A3 |
| OD-12 | `novel` glyph 五秒识别测试的通过阈值与参与人数？ | (a) 4/5 人正确 (b) 不做该测试，改由 OD-4 直接裁定 | REQ-054 验证方法 |

## 决策记录（Decision log）

| # | 用户原话 / 卡内原文 | 改写形态 | 理由 |
|---|---|---|---|
| 1 | 「symbols replace visible interface text」 | §范围边界：chrome（控件说明文字）纯 glyph；content（数据驱动事实）保留文字与数字 | 原话未区分「界面文字」与「数据」。若字面执行，物业名与到期日期也会被符号取代，界面即失去信息。 |
| 2 | 「a minimal, top-tier-app schedule interface」 | B 组 REQ-030..049 的可检查约束（动作数、token 来源、装饰禁令、状态渲染、动效时长） | 「minimal」「top-tier」是目标不是需求；测试无法对形容词取值。 |
| 3 | 「symbols replace visible interface text」 | REQ-051：chrome 类型不含可见文本字段 | A5 禁止源码字符串 oracle 且不测 Compose runtime，故只能把该保证做进类型，否则该需求在本卡内不可验证。 |
| 4 | 「accessibility name」 | REQ-052 的 accessible-name **key**，渲染期才解析为文本 | 可访问名不是可见文字，故不破坏 A2；同时满足 WCAG 1.1.1/4.1.2。 |
| 5 | 图标资产格式（下发指令里该项为空） | REQ-058 判定为 vector（in-file `ImageVector`） | 仓内无图标依赖、无 `res/drawable/`，且本卡 `forbid` 禁改依赖。改用 bitmap 会需要每密度资产与新目录，理由在卡内与仓内都不存在。 |
| 6 | OD-3 三选一 | 用户 2026-09-03 裁定选项 (b)：先修订 `context/DESIGN.md` | `CLAUDE.md`「权威文档」21 规定 UI 规范细节唯一服从该文件，不得由 UI 卡就地绕过。 |

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 拆出的呈现半（原 A6–A9 → 本卡 A1–A4，新增 A5 验证契约）、REQ-030..060、图形对照表与 11 条 OD。开卡前复核四处权威面，修正原「三处抵触」为「两处真抵触 + 一处本就允许 + 一处不在范围」。 |
