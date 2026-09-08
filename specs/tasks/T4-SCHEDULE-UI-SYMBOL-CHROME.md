---
id: T4-SCHEDULE-UI-SYMBOL-CHROME
title: 排程界面的符号化 chrome 与无障碍声明（chrome/content 类型分割 · 8 枚字形 · RTL · 目标尺寸 · 动效）
depends_on: [T4-SCHEDULE-UI-PRESENTATION]
parallelizable_with: []
plan_ref: context/DESIGN.md#page-inventory
status: todo
branch: T4-SCHEDULE-UI-SYMBOL-CHROME
worktree: C:\wt\T4-SCHEDULE-UI-SYMBOL-CHROME
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt
forbid:
  - 修改 context/DESIGN.md（其修订归 T4-DESIGN-SYMBOL-CHROME-V2，已合并；遗留项归 T4-DESIGN-STATUS-CARRIERS）
  - 修改 MainActivity、根导航、依赖、schema、navigation-bar 标签或 app shell chrome
  - 改动 T4-SCHEDULE-UI 已钉住的 reducer 状态集、权限时序或 pending/retry 转移语义
  - 改动 T4-SCHEDULE-UI-PRESENTATION 已钉住的 token 词汇、动作数、无空状态契约与日期/计数形态
  - 用源码字符串读取或反编译产物代替 typed 值断言
  - 重新解释 T4-SCHEDULE-UI-PRESENTATION 已收口的 OD-1..OD-12 任一条
non_goals:
  - reducer 状态机与路由效果（T4-SCHEDULE-UI 已拥有并已验收）
  - 权限时序、授权恢复、pending/retry 行为（T4-SCHEDULE-UI-REMINDER-ACTIONS 已拥有并已验收）
  - token 词汇、每状态动作数、无空状态契约、日期与计数形态（T4-SCHEDULE-UI-PRESENTATION 已拥有）
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
  - 新增图标依赖或 res/drawable 资源（OD-2 已裁定不扩，且一律不得引入 material-icons 依赖）
acceptance:
  - "A1 every chrome control value is a typed glyph carrying a non-null accessible-name key and no visible-text field, no chrome control rests on a glyph classified novel, and glyph artwork carries no letter, word or numeral"
  - "A2 every chrome control declares at least 48dp target size with at least 8dp separation, every state is carried by a glyph and a position as well as colour, every directional glyph declares that it mirrors under a right-to-left layout, every colour resolves through a semantic token name, and every animation duration is a declared motion token with a reduced-motion variant"
  - "A3 runtime acceptance tests assert typed declaration values through the compiled reducer entry points only; source, resources and inspected compiled artifacts are never an oracle, Compose wiring stays compile-only, and every automated requirement carries an executable semantic mutation receipt"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: reducer tests pin the typed chrome/content split, the non-null accessible-name key, the absence of any visible-text field on the chrome control type, declared target sizes and gaps, declared motion tokens with their reduced-motion variants, and the mirror flag of every directional glyph, with A1-A3 semantic-mutation receipts and no source-derived oracle; contrast, font scale and render-time constraints named in the verification table are manual design review and make no automated claim.
review_gate: codex {verdict:pass}
hygiene: 每个 typed 声明值由单点变异击杀；反射证明 chrome 控件类型无可见文本成员；人工评审项不冒充自动验收。
doc_sync: TASK-BOARD 记录合并 OID 并把 T4-SCHEDULE 子链标记完成；本卡与 T4-SCHEDULE-UI、T4-SCHEDULE-UI-PRESENTATION 一并 R5 归档。
---

# T4-SCHEDULE-UI-SYMBOL-CHROME

## Deliverable

把排程界面的 **chrome**（控件与容器自身的说明性文字）从可见文字换成字形，并把「chrome 里
没有可见文字」这条保证**做进类型**；同时补齐随之而来的无障碍声明：目标尺寸与间距、
状态的非颜色载体、方向性字形的 RTL 镜像、语义颜色 token、动效 token 与 reduced-motion 变体。

**content 一侧不动**：物业名、巡检类型名、`section-header` 分组名、到期日期、计数短语与失败说明
一律保留文字与数字，由前置卡 `T4-SCHEDULE-UI-PRESENTATION` 的 REQ-053 拥有并已验收——
本卡引用它作为分割的 content 一侧，**不重复拥有、不重新解释**。

## 拆分依据（2026-09-08 用户裁定）

`T4-SCHEDULE-UI-PRESENTATION` 在 OD 全部收口后按 L266 重算体量，落在 ≈990 changed lines：
已越该卡自己写下的 900 止损点，且距 R3 的 1000 行 / 60000 字符硬闸仅剩十行余量——
任何一条 R3 finding 的修复都将无处安放（这正是 `T3-REPORT-HTML-RENDERER` 三次被 1000 行硬闸
逼着拆卡的同一形态，教训是**越早拆越省轮次**）。用户裁定按该卡 §止损点早已写下的那条线拆，
本卡承接其中的 A2/A4 半。

**为何前置卡的 OD 收口砍掉 5 枚字形后总量反而回升**：收口本身加回了两枚——
`CLEAR_FILTER` 动作槽需要自己的字形（原表漏列），空状态的 `NEXT` 动作需要一枚**方向性**字形，
而后者是 REQ-057 唯一的非空实例（其余 7 枚皆非方向性）。故字形由 11 → 6 → **8**。

### 体量估算（L266，写 RED 之前）

| 项 | 估算 | 依据 |
|---|---|---|
| 8 枚 in-file `ImageVector` | 120–200 | 仓内无图标依赖（REQ-058），每枚 path 约 15–25 行 |
| chrome 控件类型 + 字形/可识别性/方向性枚举 | 120–160 | REQ-050..052、054..058 的 typed 值 |
| 徽标非颜色载体 + 目标尺寸 + 动效/reduced-motion | 100–140 | REQ-056、059、046、047 |
| Compose 接线改写 | 80–120 | 七状态由文字标签换成字形 |
| 测试 + 变异收据 | 280–360 | 可机检的 REQ 约 12 条 + 一条反射证明 |
| **合计** | **700–980** | 止损点 900；越过则按「字形与类型分割」/「无障碍声明」再拆 |

> **止损点**：实测越过 900 行，**按 A1（chrome/content 类型分割 + 字形）与 A2（无障碍声明：
> 目标尺寸 / 非颜色载体 / RTL / 动效）二次拆卡**，不删注释、不打包字面量、不修剪变异收据。

## 继承的裁定（不得重开）

`T4-SCHEDULE-UI-PRESENTATION` 的 §未决决策已于 2026-09-08 全部收口，**二次拆卡发生在收口之后**，
故本卡直接继承该表。与本卡直接相关的四条：

| # | 裁定 | 对本卡的约束 |
|---|---|---|
| OD-1 | 巡检类型标签与 `section-header` 分组名判为 **content** | 这两类值不得成为 chrome 控件，也不得配字形 |
| OD-2 | **不扩** `allow_paths` 至 `res/drawable/` 与 `values/strings.xml` | 字形一律 in-file `ImageVector`，accessible name 用内联字面量 |
| OD-4 | `novel` 类一律保留文字标签 | 本卡的 8 枚字形须全部为 `universal` 或 `conventional` |
| OD-5 | 字形内一律禁止数字 | due 计数是独立 content 文本，不入字形 |

## 需求

写法：EARS，同前置卡。**REQ 编号沿用前置卡的号段，不重排**——编号是稳定标识，
跨卡同号同义（同 `CLAUDE.md`「检查项 ID 稳定」的同一条理由）。

| ID | Pattern | Requirement | 来源 |
|---|---|---|---|
| REQ-044 | Ubiquitous | The schedule view shall resolve every colour through a semantic token name so that the light and dark schemes carry identical semantics. | A2 · `docs/UI-UX-ELEMENTS.md:119` |
| REQ-045 | Ubiquitous | The schedule view shall wrap rather than truncate every due date, status, count and failure reason at `200%` system font scale. | A2 · [card:context/DESIGN.md:1802]，`docs/UI-UX-ELEMENTS.md:118` |
| REQ-046 | Ubiquitous | The schedule view shall draw every animation duration from `motion.{pressFeedbackMs,stateChangeMs,expandMs,sheetEnterMs,exitMs}`. | A2 · [card:context/DESIGN.md:`motion`] |
| REQ-047 | State-Driven | While the operating system reports reduce-motion, the schedule view shall emit no translation, scale, pulse or repeating animation, and shall limit any remaining transition to a `100ms` opacity change. | A2 · `docs/UI-UX-ELEMENTS.md:120`，[card:context/DESIGN.md:1794] |
| REQ-050 | Ubiquitous | The schedule models shall classify every rendered value as either chrome or content according to the predecessor card's scope boundary, and shall carry the two in distinct types. | 用户指令 · `T4-SCHEDULE-UI-PRESENTATION` §范围边界 |
| REQ-051 | Ubiquitous | The chrome control type shall declare no visible-text field, so that a chrome control carrying a visible label cannot be constructed. | A1 · 仓内既有做法（`ReportContent` 私有构造器 · `LoadedTemplate.parse`） |
| REQ-052 | Ubiquitous | Every chrome control value shall carry a non-null accessible-name key. | A1 · [SOURCE: WCAG 2.2, SC 4.1.2 Name, Role, Value (Level A), https://www.w3.org/WAI/WCAG22/Understanding/name-role-value.html]，[SOURCE: WCAG 2.2, SC 1.1.1 Non-text Content (Level A), https://www.w3.org/WAI/WCAG22/Understanding/non-text-content.html] |
| REQ-054 | Ubiquitous | Every value whose glyph would be classified `novel` in §图形对照表 shall keep its visible text and shall not be constructed as a chrome control, so that no symbol-only control in this view rests on a novel glyph. | OD-4（已裁定：`novel` 类保留文字）· [SOURCE: NN/g, Icon Usability, https://www.nngroup.com/articles/icon-usability/]（该研究明确反对以 hover/长按揭示标签，触摸设备上不奏效） |
| REQ-055 | Ubiquitous | Glyph artwork shall contain no letter, no word and no numeral. | OD-5（已裁定：一律禁止；计数是独立 content，见前置卡 REQ-037）· [card:context/DESIGN.md:1563「Domain values are never carried by a glyph alone」] |
| REQ-056 | Ubiquitous | The schedule view shall carry every state through a glyph and a position in addition to colour, and shall carry no state through colour alone. | A2 · [card:context/DESIGN.md:1561 `Symbol-only chrome` 准入条件 5] · `docs/UI-UX-ELEMENTS.md:119` |
| REQ-057 | Ubiquitous | Every chrome control value whose glyph is directional shall declare that it mirrors under a right-to-left layout direction. | A2 · [code:android/app/src/main/AndroidManifest.xml:13 `supportsRtl="true"`] |
| REQ-058 | Ubiquitous | Every glyph shall be declared as one in-file Compose `ImageVector`, tinted from a theme colour role, and sized from `iconography.sizes.{sm,md,lg}` (`18` / `24` / `32`). | OD-2 · [code:android/gradle/libs.versions.toml:28-32]（无 icon 依赖，本卡 `forbid` 禁改依赖） |
| REQ-059 | Ubiquitous | Every chrome control shall declare a target of at least `48dp` by `48dp` with at least `8dp` to the adjacent target. | A2 · [SOURCE: Android Developers, Compose accessibility API defaults, https://developer.android.com/develop/ui/compose/accessibility/api-defaults]（`48dp`）· [card:context/DESIGN.md:`interaction.minTouchTarget`,`adjacentTargetGap`] |
| REQ-060 | Ubiquitous | Every chrome control shall meet at least `24` by `24` CSS px target size and `3:1` non-text contrast against the adjacent surface. | [SOURCE: WCAG 2.2, SC 2.5.8 Target Size (Minimum), Level AA, https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum.html]，[SOURCE: WCAG 2.2, SC 1.4.11 Non-text Contrast, Level AA, https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html] |

> **REQ-051 的写法是本卡的关键**：A3 禁止以源码字符串或编译产物作 oracle，且 Compose runtime
> 不在测试面内，所以「chrome 里没有可见文字」不能靠扫描断言，只能**做进类型**——chrome 控件类型
> 不含可见文本字段，于是「带标签的 chrome 控件」不是被规则禁止，而是**写不出来**，
> 测试断言的是该 typed 值本身，并配一条反射证明（该类型无任何 `CharSequence` 可达成员）。
> 这与 `T3-REPORT-CONTENT-ADAPTER`（`compose(content)` 签名里没有 `Audience`）、
> `T3-REPORT-HTML-EVIDENCE-PORT`（只暴露谓词、不暴露集合）同一手法。

> **REQ-054 不配运行期守卫**：8 枚字形无一属 `novel`，故任何「拒绝 novel 字形」的运行期分支都是
> **没有变异能杀死的死守卫**——仓内已为此吃过教训（`T3-REPORT-CONTENT-ADAPTER` 明确不补
> 「任何变异都杀不掉的死守卫」）。本卡改为直接断言该枚举属性：`ScheduleGlyph.entries` 中
> 无一声明 `NOVEL`，并以一枚「把某枚改成 `NOVEL`」的变异证明该断言在测。

## 图形对照表（Glyph legend）

图标集：`context/DESIGN.md` 声明 `iconography.family: Material Symbols`（`outlined` 默认 / `filled` 选中）。
**但本仓不存在任何图标依赖或图标资源**，故本表「glyph」列给的是**形态概念**，落地形态由 REQ-058
规定为 in-file `ImageVector`（OD-2 已裁定不扩 `res/`）。
`accessible name` 列是 REQ-052 的 key，**不是可见文字**，因此不违反 A1。

| # | chrome 值 | glyph（概念） | 可识别性类 | 方向性 | accessible name | 备注 |
|---|---|---|---|---|---|---|
| 1 | Retry | 循环箭头 refresh | universal | 非方向性 | `Retry registering this reminder` | 承接 `T4-SCHEDULE-UI` REQ-021；`Error` 态的 `RETRY` 槽 |
| 2 | Open settings | 齿轮 settings | universal | 非方向性 | `Open notification settings` | 承接 REQ-015；`OPEN_SETTINGS` 槽，离开 app 边界 |
| 3 | Notifications blocked | 铃铛加斜杠 notifications-off | conventional | 非方向性 | `Notifications are turned off` | 承接 REQ-015/016 状态标；REQ-056 双重编码 |
| 4 | Filter | 漏斗 filter | universal | 非方向性 | `Filter by inspection type` | top-app-bar 动作（REQ-031 的 2 个上限内） |
| 5 | Clear filter | 漏斗加斜杠 filter-off | conventional | 非方向性 | `Clear the inspection type filter` | `FilteredEmpty` 态的 `CLEAR_FILTER` 槽 |
| 6 | Next | 前向箭头 arrow-forward | universal | **方向性 → RTL 镜像** | `Add a property` | `NoContentEmpty` 态的 `NEXT` 槽；目标由宿主注入（OD-11），名字只说做什么、不说去哪 |
| 7 | Due（`state-badge`） | 时钟 schedule | conventional | 非方向性 | `Due` | 计数是独立 content 文本，不入字形（OD-5） |
| 8 | First inspection（`state-badge`） | 三角感叹号 warning | universal | 非方向性 | `First inspection` | REQ-056 双重编码 |

> **第 8 行的名字是一处更正**：前置卡原表写的是 `Needs attention`，但 reducer 钉住的徽标枚举是
> `ScheduleBadge.{DUE, FIRST_INSPECTION, NONE}`（`ScheduleModels.kt`），并无 needs-attention 态。
> 名字按**代码里真实存在的状态**改写，不按原表措辞照抄——否则会造出一个 accessible name 指向
> 一个不存在的状态。`ScheduleBadge.NONE` 不配字形也不配名字：它是声明出来的「无徽标」，
> 行本身仍由 content 文字承载，不构成 REQ-056 所禁的「仅靠颜色」。

> **本表 8 枚无一属 `novel` 类**。NN/g 的结论是「多数图标没有通用含义，文字标签是必需的」，
> 且明确反对用 hover 揭示标签（「在触摸设备上无法奏效」）
> [SOURCE: NN/g, Icon Usability, https://www.nngroup.com/articles/icon-usability/]。
> 该研究点名的高风险区（四个巡检类型标签与分组名）已由 OD-1 判为 content、OD-4 判 `novel` 类
> 保留文字——**这一风险不是被接受，而是被移除**。第 3、7 行按 `icon-button` anatomy 自身
> 声明的 tooltip 配长按披露，触发 `context/DESIGN.md:1561` 准入条件 3。

## 组件选择：本视图只用 `icon-button`

`context/DESIGN.md:1561` 的准入段末句写明：**准入永不覆盖更严的组件合同**，
且点名 Buttons and selection controls 属于那些更严合同。因此本卡的做法是——
**排程视图不声明任何 `button-primary` / `button-secondary`**，每个状态的那一个动作一律是
`icon-button`（该组件行自己写着「Admitted by symbol-only chrome」）。
于是「符号化把按钮的文字标签吃掉了」这件事不是被规则禁止，而是**不存在**：
本视图里根本没有要求可见文字的按钮组件。

四个巡检类型的 filter chip 属 selection control，按同一条更严合同保留可见文字，
这与 OD-1 判它们为 content 是同一结论的两条独立理由。

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-050..052, 054, 055, 058 | automated · 类型层断言（chrome 类型无可见文本字段 + 反射证明、accessible-name key 非 null、无字形声明 `NOVEL`、字形形态无字母/文字/数字） | typed 值 |
| A2 | REQ-044, 046, 047, 056, 057, 059 | automated · 皆为 typed 声明值（语义颜色 token、motion token 与 reduced-motion 变体、徽标的字形与位置、方向性字形的 mirror 标志、目标尺寸与间距） | typed 值 |
| A2（人工） | REQ-045, 060 | manual · 200% 字号换行与 `3:1` 非文本对比度走设计评审 | 人工评审 |
| A3 | 全部 automated 项 | automated · 变异收据（selector / RED exit / 前后同 SHA-256） | 收据本身 |
| 前置卡拥有 | REQ-030..043, 048, 049, 053 | 见 `T4-SCHEDULE-UI-PRESENTATION`；本卡不重复验收 | — |

> **诚实说明**：A3 把 Compose runtime 排除在测试面外，故对比度与 200% 字号这类**渲染期**约束
> 在本卡内**无法机检**，只能人工评审。可机检的部分之所以可机检，是因为 REQ 把它们写成了
> `ScheduleModels.kt` 里的 typed 声明值（字形、可识别性、方向性、目标尺寸、motion token、
> mirror 标志），而不是渲染结果。实现时若把某条从 typed 值退化成散落的字面量，该条即失去 oracle。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-08 | 建卡：由 `T4-SCHEDULE-UI-PRESENTATION` 按其 §止损点二次拆卡而来（用户裁定），承接原 A2/A4 半。移入 REQ-044..047 与 REQ-050..052、054..060 及图形对照表；REQ 编号不重排。图形对照表补齐 `CLEAR_FILTER` 与方向性 `NEXT` 两枚（8 枚），并把原第 10 行 `Needs attention` 按 reducer 实有的 `ScheduleBadge.FIRST_INSPECTION` 更正。新增「本视图只用 `icon-button`」一节，解释符号化准入与「按钮须有可见文字」这条更严合同如何并存。REQ-054 明确不配运行期死守卫，改断言枚举属性。 |
