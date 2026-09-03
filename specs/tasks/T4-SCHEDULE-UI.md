---
id: T4-SCHEDULE-UI
title: 排程 reducer、权限恢复与显式重试行为
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
  - app 启动时请求通知权限或用陈旧 GRANTED 状态排程
  - 修改 MainActivity、根导航、依赖、schema 或后台自动创建巡检
  - 用源码字符串读取代替 reducer、permission、route 或 Compose 行为测试
non_goals:
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
  - 修改 context/DESIGN.md、navigation-bar 标签、根导航徽标或 app shell chrome
  - 视觉呈现层（design token 取值、glyph 化 chrome、动效 token、目标尺寸、对比度、字号）归 T4-SCHEDULE-UI-PRESENTATION
  - 空状态「下一步」动作的具体目标与文案（OD-11，归 T4-SCHEDULE-UI-PRESENTATION）
acceptance:
  - "A1 the reducer renders exactly one declared state among due, no-content-empty, first-inspection, one-off, filtered-empty, loading and error, each carrying its declared badge value, and a due row activation emits exactly one route effect carrying propertyId and inspectionType"
  - "A2 at API 33 and above the presenter re-reads the notification permission on resume and again immediately before the user reminder action, and below API 33 it registers without reading it"
  - "A3 a granted read registers the stored pending occurrence, while a denied or revoked read renders the settings-recovery state with one Open settings action and no startup or repeated system request"
  - "A4 a RETRYABLE_FAILURE cause retains only the pending occurrence whose occurrenceId equals the settled identity, a PERMANENT_FAILURE cause discards it, a SKIPPED cause leaves the rendered state unchanged, and retry re-registers that same occurrenceId without creating a second registration"
  - "A5 runtime acceptance tests invoke compiled reducer/presenter entry points with concrete inputs and production-used injected permission, scheduler and route ports, assert only domain state and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle, while Compose wiring is compile-only"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box reducer/presenter tests pin no startup request, action-time/resume permission refresh, grant/revoke/deny/settings/retry transitions, badges, filters and route effects, with A1-A5 semantic-mutation receipts and no source-derived oracle; assembleDebug is compile-only evidence for Compose wiring and makes no behavioral UI-test claim.
review_gate: codex {verdict:pass}
hygiene: 权限与 pending reducer 的每个事件分支均由单点变异击杀；UI 测试不以源码字符串断言替代行为。
doc_sync: TASK-BOARD 记录合并 OID 并把 T4-SCHEDULE 子链的行为半标记完成；本卡 R5 归档。
---

# T4-SCHEDULE-UI

## Deliverable

Schedule route 的**行为半**：一个把已合并的 cadence / reminder 合同投影成七个互斥状态的 reducer，
一个在 API 33 边界上按时刻重读通知权限的 presenter，以及按 `ReminderRegistrationOutcome` 三分支收口的
pending / retry 机器。`ScheduleScreen` 只作为 typed state/effect 的 Compose 接线存在，由 `assembleDebug`
证明能编译，本卡不声称其 runtime 行为已被测试。

只消费已合并 cadence/reminder 合同，拥有独立 Schedule route 内容；不接管 app shell 或根导航。

Runtime acceptance tests are black-box behavioral tests：测试只调用 compiled reducer/presenter entry point 与 production-used injected ports，并只断言领域状态及 permission/scheduler/route effects；不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。`ScheduleScreen` 只由 `assembleDebug` 证明能与这些 typed state/effect contracts 编译接线，本卡明确不声称 Compose runtime 或像素/语义树行为已被测试。A1–A5 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

## 拆分依据（2026-09-03 用户裁定）

2026-09-03 的需求重写把本卡由 A1–A5 扩为 A1–A9，新增 B 组「最小排程界面」REQ-030..049 与 C 组
「纯图形 chrome」REQ-050..060，并留下 12 条未决决策（OD-1..OD-12）。用户裁定**先按行为 / 呈现两半拆卡**：

- **本卡＝行为半**：A1–A5 / REQ-001..023。**零 `[待定]`、零 `context/DESIGN.md` 冲突**，可立即开工。
- **`T4-SCHEDULE-UI-PRESENTATION`＝呈现半**：A6–A9 / REQ-030..060 与全部 12 条 OD 迁往该卡。
- OD-3（A7 与 `context/DESIGN.md` 抵触）另经用户裁定为**先修订 DESIGN.md**，故新开
  `T4-DESIGN-SYMBOL-CHROME`，它 block 呈现卡、不 block 本卡。

拆分理由与 `T4-SCHEDULE-REMINDER-FLIGHT`（commit `3aba57b6`）同：**自下而上估算超 R3 预算，动手前拆卡**（L266）。
合卡形态估算 ≈ 1600–1900 changed lines（含 11 枚 in-file `ImageVector`、chrome/content 类型分割、
REQ-001..060 的测试面），远超 1000 行 / 60000 字符硬闸。

### 本卡自身的体量估算（L266，写 RED 之前）

| 文件 | 估算 | 依据 |
|---|---|---|
| `ScheduleModels.kt` | 220–280 | 7 个状态 + 事件/效果密封层级 + pending 机器 + filter/restore |
| `ScheduleScreen.kt` | 120–160 | 七状态的 Compose 接线，compile-only |
| `ScheduleUiTest.kt` | 450–550 | REQ-001..023 逐条 + 注入 ports 的 fake + A1–A5 变异收据 |
| **合计** | **790–990** | **贴近 1000 行闸，非宽裕** |

> **已声明的风险与其止损点**：变异收据（钉四个 selector 与前后 SHA-256）历史上占 60–120 行
> （见 `T4-SCHEDULE-REMINDER-DIAGNOSTICS` 的 26 枚收据）。若 RED 写到一半实测越过 900 行，
> **立即按 A1（reducer 状态 / filter / route）与 A2–A4（permission / pending / retry）二次拆卡**，
> 而不是删注释、打包字面量或修剪变异收据——后者正是 L266 与 L270 点名的坏选项。

## 上下文包（实现 agent 的最小指针）

| 事实 | 值 | 来源 |
|---|---|---|
| 平台 | Android，Kotlin + Compose，minSdk 26 / targetSdk 35 / compileSdk 35 | [code:android/app/build.gradle.kts:19-24] |
| 治理规范 | Material 3（平台）+ WCAG 2.2（下限）；无 iOS 面，故不适用 Apple HIG | [code:android/app/build.gradle.kts:19]，`docs/UI-UX-ELEMENTS.md#6` |
| 设计真相源 | `context/DESIGN.md`（唯一规范源，本卡不得修改） | `CLAUDE.md`「权威文档」21 |
| 本页契约 | `SCHEDULE_ROOT` / route `schedule` / `ROOT_STATIC` / 底部导航可见 / owner `T4-SCHEDULE` | [card:context/DESIGN.md:781] |
| 必需 Elements | `app-shell` `top-app-bar` `navigation-bar` `section-header` `result-list-row` `state-badge` `metadata-row` | `docs/UI-UX-ELEMENTS.md:52` |
| 条件 Elements | `empty-state-panel` `filter-chip-group` `feedback-banner` | `docs/UI-UX-ELEMENTS.md:52` |
| 已合并领域合同 | `ScheduleAdvice.{Due,FirstInspection,NoRecurrence}`、`InspectionScheduleType.{ROUTINE,ANNUAL,INGOING,EXIT}` | [code:android/core/src/main/kotlin/nz/myinspection/core/schedule/SchedulePlanner.kt:6-28] |
| 注册结果词汇 | `ReminderRegistrationOutcome.{ADMITTED,RETRYABLE_FAILURE,PERMANENT_FAILURE,SKIPPED}`，25 个 `ReminderRegistrationCause` 各自携带其 outcome | [code:android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt:59-105] |
| 权限端口 | `ReminderPermissionPort.isPostNotificationsGranted()` | [code:android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt:21-23] |
| API 33 分界 | `reminderDeliveryPlan(sdkInt >= 33 && !permissionGranted) → Retry` | [code:android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt:158] |
| 权限已声明 | `POST_NOTIFICATIONS`；`android:supportsRtl="true"` | [code:android/app/src/main/AndroidManifest.xml:6,13] |
| 现有排程 UI | **不存在**：`ScheduleModels.kt` / `ScheduleScreen.kt` 尚未创建，`strings.xml` 只有 `app_name` | [code:android/app/src/main/res/values/strings.xml:1-4] |

## 需求

写法：EARS。`<系统>` 一律是组件（`the schedule reducer` / `the schedule view` / `the schedule presenter`），
不是用户角色；用户动作出现在触发位置。每条一个 shall。需求正文用英文（与本卡 `acceptance` 同语域），
解释性散文用中文（与本卡原有正文同语域）。

**本卡的需求集恰好是 REQ-001..023，其中无一条含 `[待定]`。** 呈现层需求（REQ-030..060）与其 12 条
未决决策整体归 `T4-SCHEDULE-UI-PRESENTATION`。

### A 组 · 行为需求（承接 A1–A5）

| ID | Pattern | Requirement | 归属 / 来源 |
|---|---|---|---|
| REQ-001 | State-Driven | While the schedule state is `Due`, the schedule view shall render, for each due occurrence, the property name, the inspection type name, the absolute due date, and one `state-badge`. | A1 · [card:context/DESIGN.md:52 元素表] |
| REQ-002 | Event-Driven | When the user activates a due row, the schedule view shall emit exactly one route effect carrying `propertyId` and `inspectionType`. | A1 · [code:ReminderContracts.kt:8-11 `ScheduleRoute`] |
| REQ-003 | Unwanted | If a route effect from the same row activation is still unsettled, then the schedule reducer shall discard the subsequent activation without emitting a second effect. | A1 · [card:context/DESIGN.md:966「Only `IDLE` accepts a new navigation intent」] |
| REQ-004 | State-Driven | While the advice for an occurrence is `ScheduleAdvice.FirstInspection`, the schedule view shall render the first-inspection state and shall not render a due date. | A1 · [code:SchedulePlanner.kt:25] |
| REQ-005 | State-Driven | While the advice for an occurrence is `ScheduleAdvice.NoRecurrence`, the schedule view shall render the one-off state and shall not render a due date or a count badge. | A1 · [code:SchedulePlanner.kt:27,50-54] |
| REQ-006 | Event-Driven | When the user selects an inspection-type filter, the schedule reducer shall retain only the occurrences whose `inspectionType` equals the selected value. | A1 · [code:SchedulePlanner.kt:6-11] |
| REQ-007 | Event-Driven | When the schedule state is restored after a configuration change, the schedule reducer shall restore the previously selected filter value and the previous scroll position. | A1 · [card:context/DESIGN.md:812「Restore scroll/filter per tab」] |
| REQ-008 | State-Driven | While a filter selection yields zero occurrences, the schedule view shall render the filtered-empty state and shall not render the no-content empty state. | A1 · [card:context/DESIGN.md:551 `NO_RESULTS` vs `NO_CONTENT`] |
| REQ-009 | State-Driven | While no occurrence is due and no filter is selected, the schedule reducer shall render the no-content empty state carrying exactly one action slot. | A1 · 动作的**目标与文案**归 `T4-SCHEDULE-UI-PRESENTATION`（OD-11） |
| REQ-010 | Complex | While the device API level is 33 or above, when the schedule presenter is resumed, the schedule presenter shall read `isPostNotificationsGranted()` and set its permission state from that read. | A2 · [code:ReminderWorker.kt:21-23] |
| REQ-011 | Complex | While the device API level is 33 or above, when the user activates the reminder action, the schedule presenter shall read `isPostNotificationsGranted()` again before submitting the registration. | A2 · [code:ReminderContracts.kt:158] |
| REQ-012 | State-Driven | While the device API level is below 33, the schedule presenter shall submit the registration without reading `isPostNotificationsGranted()`. | A2 · [code:ReminderContracts.kt:158-165] |
| REQ-013 | Unwanted | If the schedule presenter is created or resumed without a user reminder action, then the schedule presenter shall not emit a permission-request effect. | A2 · [card:context/DESIGN.md:1127]，本卡 `forbid` 第 1 条 |
| REQ-014 | Event-Driven | When a permission read returns granted and a pending occurrence is stored, the schedule presenter shall register that stored occurrence. | A3 |
| REQ-015 | Event-Driven | When a permission read returns denied, the schedule view shall render the `PERMISSION` recovery state carrying one `Open settings` action and the statement that the in-app schedule remains usable. | A3 · [card:context/DESIGN.md:1127「notifications → in-app Schedule」]，`docs/UI-UX-ELEMENTS.md:105` |
| REQ-016 | Unwanted | If a permission previously read as granted now reads denied, then the schedule presenter shall render the `PERMISSION` recovery state and shall not emit a system permission-request effect. | A3 · `docs/UI-UX-ELEMENTS.md:105`「不重复弹系统框」 |
| REQ-017 | Event-Driven | When the presenter returns to the foreground after an `Open settings` effect, the schedule presenter shall read `isPostNotificationsGranted()` before rendering any permission state. | A3 · `docs/UI-UX-ELEMENTS.md:88`「回前台重新检查权限」 |
| REQ-018 | Unwanted | If a registration settles with a cause whose `outcome` is `RETRYABLE_FAILURE`, then the schedule reducer shall retain the pending occurrence whose `occurrenceId` equals the settled identity's and shall discard every other pending occurrence. | A4 · [code:ReminderScheduler.kt:72-105,119] |
| REQ-019 | Unwanted | If a registration settles with a cause whose `outcome` is `PERMANENT_FAILURE`, then the schedule reducer shall discard that pending occurrence and render the error state with exactly one recovery action. | A4 · [code:ReminderScheduler.kt:72-105] |
| REQ-020 | Unwanted | If a registration settles with a cause whose `outcome` is `SKIPPED`, then the schedule reducer shall leave the rendered state unchanged. | A4 · [code:ReminderScheduler.kt:102-104] |
| REQ-021 | Event-Driven | When the user activates retry, the schedule presenter shall register the same `occurrenceId` that failed and shall not derive a new one. | A4 · [code:ReminderContracts.kt:40-54] |
| REQ-022 | Unwanted | If retry is activated while a registration for the same `occurrenceId` is unsettled, then the schedule presenter shall not submit a second registration. | A4 · [code:ReminderScheduler.kt:196-198 flight 合流] |
| REQ-023 | Ubiquitous | The schedule view shall render no `occurrenceId`, generation number, work-request UUID, enum constant name, file path or exception text. | A4 · [card:context/DESIGN.md:1761]，[code:ReminderScheduler.kt:127-133] |

> **REQ-009 是拆分缝的落点**：状态集合的**穷尽性**属行为（reducer 必须能渲染 no-content 而不是崩在
> `when` 的缺支上），故留在本卡并只断言「恰好一个动作槽」这个 arity；该动作**指向哪里、叫什么**取决于
> OD-11，而 OD-11 的两个候选都依赖本卡 `non_goals` 排除的根导航，故整体归呈现卡。

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-001..009 | automated · `ScheduleUiTest` reducer 用例 | 领域状态 + 记录的 route effect |
| A2 | REQ-010..013 | automated · 注入 `ReminderPermissionPort` 的读取计数与时序 | 端口调用序列 |
| A3 | REQ-014..017 | automated · 授权/拒绝/撤销/回前台四条转移 | 状态 + effect |
| A4 | REQ-018..023 | automated · 按 `outcome` 三分支 + 重试合流 | pending 集合 + 注册调用 |
| A5 | 全部 | automated · 变异收据（selector / RED exit / 前后同 SHA-256） | 收据本身 |

> **本卡验收面全自动**。这正是拆分带来的好处：呈现半那些**渲染期**约束（间距、对比度、无渐变、
> 200% 字号）在 A5 的黑盒面内无法机检、只能人工评审，把它们留在同一张卡会让「已验收」这个词
> 在同一张卡内含义分裂。

## 决策记录（Decision log）

| # | 用户原话 / 卡内原文 | 改写形态 | 理由 |
|---|---|---|---|
| 1 | A1「due, empty, first, one-off and type-filter states expose badges」 | 拆成 REQ-001..009，九条各自一个状态或一次转移 | 原句一条含五个状态 + 徽标 + 路由回调，失败时无法定位是哪一项。 |
| 2 | A4「retains only the matching pending occurrence」 | REQ-018/019/020 按 `outcome` 三分支分写 | 「matching」原文未定义匹配依据；已按 `ReminderRegistrationIdentity.occurrenceId` 定死。 |
| 3 | A5 全文 | 保持原意不改 | A5 是验证契约而非产品需求，且措辞经多轮评审收紧。 |
| 4 | 需求语言 | 需求正文英文、解释散文中文 | 与本卡既有形态一致（`acceptance` 英文、正文中文）。 |
| 5 | 「symbols replace visible interface text」「a minimal, top-tier-app schedule interface」 | 整体迁往 `T4-SCHEDULE-UI-PRESENTATION` | 2026-09-03 用户裁定按行为/呈现两半拆卡；该诉求的 DESIGN.md 抵触另由 `T4-DESIGN-SYMBOL-CHROME` 先行修订。 |

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 需求重写：A1–A4 拆为 REQ-001..023（EARS）；新增上下文包、验收与验证方法、决策记录。 |
| 2026-09-03 | **三向拆卡（用户裁定）**：呈现层 A6–A9 / REQ-030..060 与 12 条 OD 迁往 `T4-SCHEDULE-UI-PRESENTATION`；DESIGN.md 抵触迁往 `T4-DESIGN-SYMBOL-CHROME`。本卡回到 A1–A5，新增 REQ-009（no-content 状态 arity）、拆分依据与体量估算，`non_goals` 增两条。`allow_paths`、`dod_command`、`forbid` 未改动。 |
