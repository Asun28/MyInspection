---
id: T4-SCHEDULE-UI
title: 排程 reducer：行种类、屏幕状态、筛选与路由效果
depends_on: [T4-SCHEDULE-REMINDER-RECOVERY]
plan_ref: context/DESIGN.md#page-inventory
status: todo
branch: T4-SCHEDULE-UI
worktree: C:\wt\T4-SCHEDULE-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt
forbid:
  - 修改 MainActivity、根导航、依赖、schema 或后台自动创建巡检
  - 用源码字符串读取代替 reducer 与 route 行为测试
  - 在本卡引入通知权限读取、注册、pending 或 retry（归 T4-SCHEDULE-UI-REMINDER-ACTIONS）
non_goals:
  - 通知权限时序、授权恢复、注册结果分支与显式重试（T4-SCHEDULE-UI-REMINDER-ACTIONS 拥有）
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
  - 修改 context/DESIGN.md、navigation-bar 标签、根导航徽标或 app shell chrome
  - 视觉呈现层（token 取值、glyph 化 chrome、动效、目标尺寸、对比度、字号）归 T4-SCHEDULE-UI-PRESENTATION
  - 空状态「下一步」动作的具体目标与文案（OD-11，归 T4-SCHEDULE-UI-PRESENTATION）
acceptance:
  - "A1 the reducer renders exactly one screen state among content, no-content-empty, filtered-empty, loading and error, and every row inside a content state carries exactly one row kind among due, first-inspection and one-off together with that kind's declared badge value and its declared due date or absence of one"
  - "A2 a row activation emits exactly one route effect carrying propertyId and inspectionType, a further activation while that route is unsettled emits none, and an activation after it settles emits again"
  - "A3 a type filter retains only the occurrences whose inspectionType equals the selection, a selection matching nothing renders filtered-empty rather than no-content, an empty list with no filter renders no-content, and a reload restores the previously selected filter and scroll position"
  - "A4 the action a screen state offers is a single optional value rather than a collection, so no state can express two: the no-content, filtered-empty and error states each declare exactly one slot, while loading and content declare none by design"
  - "A5 runtime acceptance tests invoke the compiled reducer entry points with concrete inputs and assert only domain state and recorded effects, and carry executable semantic mutation receipts; source, resources and inspected compiled artifacts are never an oracle, while Compose wiring is compile-only"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box reducer tests pin the two-level state model, per-kind badges and due-date absence, single-effect route activation with duplicate suppression, filter projection, filtered-empty versus no-content, scroll and filter restore, and single-slot action arity, with A1-A5 semantic-mutation receipts and no source-derived oracle; assembleDebug is compile-only evidence for Compose wiring and makes no behavioral UI-test claim.
review_gate: codex {verdict:pass}
hygiene: reducer 的每个事件分支与每条投影判据均由单点变异击杀；测试不以源码字符串断言替代行为。
doc_sync: TASK-BOARD 记录合并 OID 并把 T4-SCHEDULE-UI 行为半标记完成；本卡 R5 归档。
---

# T4-SCHEDULE-UI

## Deliverable

Schedule route 的 **reducer**：把已合并的 `ScheduleAdvice` 投影成两层状态（互斥的**屏幕状态** ×
逐行的**行种类**），加筛选、滚动/筛选恢复与单发路由效果。`ScheduleScreen` 只作为 typed state/event
的 Compose 接线存在，由 `assembleDebug` 证明能编译，本卡不声称其 runtime 行为已被测试。

Runtime acceptance tests are black-box behavioral tests：测试只调用 compiled reducer entry point，
并只断言领域状态及记录的 route effects；不得读取 repository/generated source、source-derived resource
或反射/反编译 compiled artifact 作为 oracle。A1–A5 各至少一个 production semantic mutation 必须在测试
不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/effect、RED exit 与
mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

## 拆分依据（2026-09-03 用户裁定 · 第二次）

第一次拆卡（行为 / 呈现）后，本卡的行为半实测 **1057 changed lines**，越过 R3 的 1000 行硬闸
（diffChars 41547 未越 60000），且 R4 变异收据尚未写入。这正是本卡第一版自己声明的止损点
（「越过 900 行即按 A1 与 A2–A4 二次拆卡，而不是删注释、打包字面量或修剪变异收据」），
经用户裁定按**原声明的缝**执行：

- **本卡＝reducer 半**：A1–A5 / REQ-001..009 + `ScheduleScreen`（约 600 行、13 测试）。
- **`T4-SCHEDULE-UI-REMINDER-ACTIONS`＝presenter 半**：权限时序、授权恢复、注册结果分支与显式重试
  （原 A2–A4 / REQ-010..023，约 500 行、18 测试）。

**两次拆卡的代价与收益都记在账上**：代价是本条产品能力从 1 张卡变成 3 张（再加 DESIGN 修订卡共 4 张）；
收益是每张都能被一次 R3 完整读完，而不是在 ship 压力下开始删注释或修剪证据（L266 / L270）。

## 上下文包（实现 agent 的最小指针）

| 事实 | 值 | 来源 |
|---|---|---|
| 平台 | Android，Kotlin + Compose，minSdk 26 / targetSdk 35 / compileSdk 35 | [code:android/app/build.gradle.kts:19-24] |
| 治理规范 | Material 3（平台）+ WCAG 2.2（下限）；无 iOS 面，故不适用 Apple HIG | [code:android/app/build.gradle.kts:19]，`docs/UI-UX-ELEMENTS.md#6` |
| 设计真相源 | `context/DESIGN.md`（唯一规范源，本卡不得修改） | `CLAUDE.md`「权威文档」21 |
| 本页契约 | `SCHEDULE_ROOT` / route `schedule` / `ROOT_STATIC` / 底部导航可见 / owner `T4-SCHEDULE` | [card:context/DESIGN.md:781] |
| 必需 Elements | `app-shell` `top-app-bar` `navigation-bar` `section-header` `result-list-row` `state-badge` `metadata-row` | `docs/UI-UX-ELEMENTS.md:52` |
| 已合并领域合同 | `ScheduleAdvice.{Due,FirstInspection,NoRecurrence}`、`InspectionScheduleType.{ROUTINE,ANNUAL,INGOING,EXIT}` | [code:android/core/src/main/kotlin/nz/myinspection/core/schedule/SchedulePlanner.kt:6-28] |
| 路由值 | `ScheduleRoute(propertyId, inspectionType)` | [code:ReminderContracts.kt:8-11] |
| 测试落点已验 | `:app:testDebugUnitTest` exit 0 且产出 `TEST-nz.myinspection.app.feature.schedule.*.xml` | L280 实测 discharge |
| 现有排程 UI | **不存在**：`ScheduleModels.kt` / `ScheduleScreen.kt` 尚未创建 | [code:android/app/src/main/res/values/strings.xml:1-4] |

## 需求

写法：EARS。`<系统>` 一律是组件（`the schedule reducer` / `the schedule view`），不是用户角色；
用户动作出现在触发位置。每条一个 shall。需求正文用英文，解释性散文用中文。
**本卡的需求集恰好是 REQ-001..009，无一条含 `[待定]`。**

| ID | Pattern | Requirement | 归属 / 来源 |
|---|---|---|---|
| REQ-001 | State-Driven | While a row's advice is `ScheduleAdvice.Due`, the schedule view shall render, for that row, the property name, the inspection type name, the absolute due date, and one `state-badge`. | A1 · [card:context/DESIGN.md:52 元素表] |
| REQ-002 | Event-Driven | When the user activates a due row, the schedule view shall emit exactly one route effect carrying `propertyId` and `inspectionType`. | A2 · [code:ReminderContracts.kt:8-11 `ScheduleRoute`] |
| REQ-003 | Unwanted | If a route effect from the same row activation is still unsettled, then the schedule reducer shall discard the subsequent activation without emitting a second effect. | A2 · [card:context/DESIGN.md:966「Only `IDLE` accepts a new navigation intent」] |
| REQ-004 | State-Driven | While the advice for an occurrence is `ScheduleAdvice.FirstInspection`, the schedule view shall render the first-inspection row kind and shall not render a due date. | A1 · [code:SchedulePlanner.kt:25] |
| REQ-005 | State-Driven | While the advice for an occurrence is `ScheduleAdvice.NoRecurrence`, the schedule view shall render the one-off row kind and shall not render a due date or a count badge. | A1 · [code:SchedulePlanner.kt:27,50-54] |
| REQ-006 | Event-Driven | When the user selects an inspection-type filter, the schedule reducer shall retain only the occurrences whose `inspectionType` equals the selected value. | A3 · [code:SchedulePlanner.kt:6-11] |
| REQ-007 | Event-Driven | When the schedule state is restored after a configuration change, the schedule reducer shall restore the previously selected filter value and the previous scroll position. | A3 · [card:context/DESIGN.md:812「Restore scroll/filter per tab」] |
| REQ-008 | State-Driven | While a filter selection yields zero occurrences, the schedule view shall render the filtered-empty state and shall not render the no-content empty state. | A3 · [card:context/DESIGN.md:551 `NO_RESULTS` vs `NO_CONTENT`] |
| REQ-009 | State-Driven | While no occurrence is due and no filter is selected, the schedule reducer shall render the no-content empty state carrying exactly one action slot. | A4 · 动作的**目标与文案**归 `T4-SCHEDULE-UI-PRESENTATION`（OD-11） |

> **两层状态，不是一层**：`no-content-empty` / `filtered-empty` / `loading` / `error` / `content` 是
> **屏幕状态**（互斥，恰好一个）；`due` / `first-inspection` / `one-off` 是 **行种类**，由该行的
> `ScheduleAdvice` 决定（一个 content 屏可同时含三种行）。原 A1 把两层平铺成一个「恰好一个」的枚举，
> 于是「三条 due 行 + 一条 one-off 行」在任何读法下都不满足它——该措辞不可测，已于 RED 前收紧（非放宽）。
>
> **`error` 屏幕状态在本卡只被声明、不被产生**：唯一产生它的转移是注册的
> `PERMANENT_FAILURE` 分支，归 `T4-SCHEDULE-UI-REMINDER-ACTIONS`。本卡断言它的动作槽 arity，
> 不断言进入它的路径——那条路径在本卡里根本不存在，若在此假装测过就是 vacuous。

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-001, 004, 005 | automated · 逐行种类的 badge 与 due-date 断言 | 领域状态 |
| A2 | REQ-002, 003 | automated · 单发效果 + 未结算抑制 + 结算后重放 | 记录的 route effect |
| A3 | REQ-006, 007, 008 | automated · 筛选投影与恢复 | 领域状态 |
| A4 | REQ-009 | automated · 动作槽为单值而非列表 | typed 值 |
| A5 | 全部 | automated · 变异收据（selector / RED exit / 前后同 SHA-256） | 收据本身 |

> **本卡验收面全自动**，无人工评审项。

## 决策记录（Decision log）

| # | 用户原话 / 卡内原文 | 改写形态 | 理由 |
|---|---|---|---|
| 1 | A1「due, empty, first, one-off and type-filter states expose badges」 | 拆成 REQ-001..009 并分作两层 | 原句一条含五个状态 + 徽标 + 路由回调，失败时无法定位是哪一项；且把行种类与屏幕状态平铺后不可测。 |
| 2 | 「symbols replace visible interface text」「minimal, top-tier-app schedule interface」 | 整体迁往 `T4-SCHEDULE-UI-PRESENTATION` | 2026-09-03 用户裁定按行为/呈现两半拆卡。 |
| 3 | 原 A2–A4（权限/pending/retry） | 迁往 `T4-SCHEDULE-UI-REMINDER-ACTIONS` | 2026-09-03 第二次拆卡：实测 1057 行越 R3 1000 行硬闸，按本卡自己声明的止损点执行。 |
| 4 | A4「every non-content screen state declares exactly one action slot」 | 改为「单值而非集合，故无一状态能表达两个；no-content / filtered-empty / error 各恰好一个，loading 与 content 刻意为零」 | **R3 第 1 轮 finding #1 属实**：`Loading` 是 non-content 却返回 null，实现与该措辞直接矛盾，而测试 `assertNull` 把这处违反**写成了期望值**。两条路：给 Loading 编一个动作，或改正过宽的措辞。前者是凭空发明产品行为（loading 是本地读盘 300ms 阈值态，无可操作对象，且动作文案归呈现卡 OD-11），故选后者。**arity 不变量未放宽**：「不能有两个」仍由单值类型结构性保证，只是把「每个非 content 状态都恰好一个」这句从未成立的过度概括收回。 |

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 需求重写：A1–A4 拆为 REQ-001..023（EARS）；新增上下文包、验收与验证方法、决策记录。 |
| 2026-09-03 | **三向拆卡（用户裁定）**：呈现层 A6–A9 / REQ-030..060 与 12 条 OD 迁往 `T4-SCHEDULE-UI-PRESENTATION`；DESIGN.md 抵触迁往 `T4-DESIGN-SYMBOL-CHROME`。 |
| 2026-09-03 | **RED 前收紧 A1 与 REQ-001（不可测 → 可测，非放宽）**：屏幕状态与行种类分作两层。L280 已实测 discharge：`:app:testDebugUnitTest` exit 0 且产出该包的 `TEST-*.xml`。 |
| 2026-09-03 | **R3 第 1 轮 block，4 条 finding 全部属实**，逐条修：① A4 措辞与 `Loading` 矛盾（见决策记录 4）；② `ScheduleScreen` 的 `onRetry` 回调与可点重试按钮**违反本卡自己的 `forbid`**（retry 归 ACTIONS 卡），改为不可操作的纯文本 Error 分支并删掉该参数；③ `hygiene` 承诺「每个事件分支与每条投影判据均由单点变异击杀」，而收据缺 `OccurrencesLoaded`、`FilterSelected` 与 `visible.isNotEmpty()` 三处，补 M13–M15 并在收据头写明具名 selector；④ 四处注释在拆卡后失效（声称每个分支都带 rows 或 recovery、声称动作槽由状态携带、声称测试含 presenter 断言、引用已迁出的 REQ-023），逐条改正。②③④ 均属实且无争议，①经权衡后按「收回过度概括、不发明产品行为」处置。**改产线即作废整批收据（L270），故 12 枚重跑为 15 枚。** |
| 2026-09-03 | **第二次拆卡（用户裁定）**：实测 1057 changed lines 越 R3 1000 行闸，按本卡声明的止损点把 REQ-010..023（权限/pending/retry）迁往 `T4-SCHEDULE-UI-REMINDER-ACTIONS`。本卡收为 REQ-001..009 + `ScheduleScreen`，acceptance 重编号为 A1–A5。 |
