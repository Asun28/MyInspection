---
id: T4-SCHEDULE-UI-REMINDER-ACTIONS
title: 排程 presenter：权限时序、授权恢复、注册结果分支与显式重试
depends_on: [T4-SCHEDULE-UI]
parallelizable_with: []
plan_ref: context/DESIGN.md#page-inventory
status: todo
branch: T4-SCHEDULE-UI-REMINDER-ACTIONS
worktree: C:\wt\T4-SCHEDULE-UI-REMINDER-ACTIONS
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt
forbid:
  - app 启动时请求通知权限或用陈旧 GRANTED 状态排程
  - 修改 MainActivity、根导航、依赖、schema 或后台自动创建巡检
  - 用源码字符串读取代替 permission、pending 或 retry 行为测试
  - 改动 T4-SCHEDULE-UI 已钉住的行种类、屏幕状态投影、筛选或路由效果语义
non_goals:
  - reducer 的行种类、屏幕状态、筛选与路由效果（T4-SCHEDULE-UI 已拥有并已验收）
  - 视觉呈现层（token 取值、glyph 化 chrome、动效、目标尺寸）归 T4-SCHEDULE-UI-PRESENTATION
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
acceptance:
  - "A1 at API 33 and above the presenter reads the notification permission on resume and reads it again immediately before submitting the user's reminder action, so a grant revoked between the two stops that same action rather than the next one, and below API 33 it submits without reading at all"
  - "A2 a granted read registers the stored pending occurrence, while a denied or revoked read renders the settings-recovery state with one Open settings action, and neither creation, resume nor a repeated denial ever emits a system permission request"
  - "A3 a RETRYABLE_FAILURE cause retains only the pending occurrence whose occurrenceId equals the settled one, a PERMANENT_FAILURE cause discards it and renders the error state with exactly one recovery action, and a SKIPPED cause leaves the rendered state and the effects unchanged, each proved across every cause carrying that outcome rather than one sampled cause"
  - "A4 retry re-registers the occurrenceId that failed rather than deriving a new one, and retry while that same occurrenceId is unsettled submits no second registration"
  - "A5 no occurrenceId, generation number, work-request UUID, cause constant name, property id, file path or exception text reaches the rendered screen"
  - "A6 runtime acceptance tests invoke compiled presenter entry points with concrete inputs and production-used injected permission, registration, route and settings ports, assert only domain state and recorded port traffic, and carry executable semantic mutation receipts; source, resources and inspected compiled artifacts are never an oracle, while Compose wiring is compile-only"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box presenter tests pin no startup request, resume and action-time permission reads, the revoked-in-between case, the pre-33 no-read path, grant/deny/revoke/settings-return transitions, the three outcome branches proved across every cause carrying each outcome, retry identity reuse and in-flight suppression, and screen redaction, with A1-A6 semantic-mutation receipts and no source-derived oracle; assembleDebug is compile-only evidence for Compose wiring.
review_gate: codex {verdict:pass}
hygiene: 权限与 pending reducer 的每个事件分支均由单点变异击杀；三个 outcome 分支各由「遍历该 outcome 全部 cause」的用例覆盖，而非抽样一个。
doc_sync: TASK-BOARD 记录合并 OID 并把 T4-SCHEDULE-UI 的 presenter 半标记完成；本卡 R5 归档。
---

# T4-SCHEDULE-UI-REMINDER-ACTIONS

## Deliverable

Schedule route 的 **presenter**：拥有 reducer 刻意不拥有的东西——**读什么、什么时候读**。
API 33 边界上的通知权限时序、授权/拒绝/撤销/回前台四条转移、按
`ReminderRegistrationOutcome` 三分支收口的 pending 机器，以及复用同一 `occurrenceId` 的显式重试。

Runtime acceptance tests are black-box behavioral tests：测试只调用 compiled presenter entry point
与 production-used injected ports，并只断言领域状态及 permission/registration/route/settings port
traffic；不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled
artifact 作为 oracle。A1–A6 各至少一个 production semantic mutation 必须在测试不变时让具名 selector
nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后
相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

## 拆分依据（2026-09-03 用户裁定 · 第二次拆卡）

`T4-SCHEDULE-UI` 的行为半实测 **1057 changed lines**，越过 R3 的 1000 行硬闸（diffChars 41547 未越
60000），且 R4 变异收据尚未写入。该卡第一版已自行声明止损点「越过 900 行即按 A1 与 A2–A4 二次拆卡，
而不是删注释、打包字面量或修剪变异收据」，用户裁定按原声明的缝执行，遂开本卡承接原 A2–A4 /
REQ-010..023。**代码在拆卡时已写好并 GREEN**（31 测试全绿），故本卡是把已验证的 presenter 半
重新落到自己的卡上，不是从零重写。

## 上下文包（实现 agent 的最小指针）

| 事实 | 值 | 来源 |
|---|---|---|
| 平台 | Android，Kotlin + Compose，minSdk 26 / targetSdk 35 / compileSdk 35 | [code:android/app/build.gradle.kts:19-24] |
| 前置卡已钉住 | `ScheduleUiState` / `ScheduleScreenState` / `ScheduleRow` / `ScheduleReducer` / `ScheduleEffect.Navigate` | `T4-SCHEDULE-UI`（本卡不得改其语义） |
| 注册入口 | `ReminderScheduler.register(reminder, waiter: (ReminderRegistrationCause) -> Unit)` | [code:ReminderScheduler.kt:218] |
| 注册结果词汇 | `ReminderRegistrationOutcome.{ADMITTED,RETRYABLE_FAILURE,PERMANENT_FAILURE,SKIPPED}`，25 个 `ReminderRegistrationCause` 各自携带其 outcome | [code:ReminderScheduler.kt:59-105] |
| 权限端口 | `ReminderPermissionPort.isPostNotificationsGranted()` | [code:ReminderWorker.kt:21-23] |
| API 33 分界 | `reminderDeliveryPlan(sdkInt >= 33 && !permissionGranted) → Retry` | [code:ReminderContracts.kt:158] |
| 权限已声明 | `POST_NOTIFICATIONS`；`android:supportsRtl="true"` | [code:android/app/src/main/AndroidManifest.xml:6,13] |
| 测试落点已验 | `:app:testDebugUnitTest` exit 0 且产出该包的 `TEST-*.xml` | L280 实测 discharge |

## 需求

写法：EARS，同前置卡。**本卡的需求集恰好是 REQ-010..023，无一条含 `[待定]`。**

| ID | Pattern | Requirement | 归属 / 来源 |
|---|---|---|---|
| REQ-010 | Complex | While the device API level is 33 or above, when the schedule presenter is resumed, the schedule presenter shall read `isPostNotificationsGranted()` and set its permission state from that read. | A1 · [code:ReminderWorker.kt:21-23] |
| REQ-011 | Complex | While the device API level is 33 or above, when the user activates the reminder action, the schedule presenter shall read `isPostNotificationsGranted()` again before submitting the registration. | A1 · [code:ReminderContracts.kt:158] |
| REQ-012 | State-Driven | While the device API level is below 33, the schedule presenter shall submit the registration without reading `isPostNotificationsGranted()`. | A1 · [code:ReminderContracts.kt:158-165] |
| REQ-013 | Unwanted | If the schedule presenter is created or resumed without a user reminder action, then the schedule presenter shall not emit a permission-request effect. | A2 · [card:context/DESIGN.md:1127]，本卡 `forbid` 第 1 条 |
| REQ-014 | Event-Driven | When a permission read returns granted and a pending occurrence is stored, the schedule presenter shall register that stored occurrence. | A2 |
| REQ-015 | Event-Driven | When a permission read returns denied, the schedule view shall render the `PERMISSION` recovery state carrying one `Open settings` action and the statement that the in-app schedule remains usable. | A2 · [card:context/DESIGN.md:1127]，`docs/UI-UX-ELEMENTS.md:105` |
| REQ-016 | Unwanted | If a permission previously read as granted now reads denied, then the schedule presenter shall render the `PERMISSION` recovery state and shall not emit a system permission-request effect. | A2 · `docs/UI-UX-ELEMENTS.md:105`「不重复弹系统框」 |
| REQ-017 | Event-Driven | When the presenter returns to the foreground after an `Open settings` effect, the schedule presenter shall read `isPostNotificationsGranted()` before rendering any permission state. | A2 · `docs/UI-UX-ELEMENTS.md:88`「回前台重新检查权限」 |
| REQ-018 | Unwanted | If a registration settles with a cause whose `outcome` is `RETRYABLE_FAILURE`, then the schedule reducer shall retain the pending occurrence whose `occurrenceId` equals the settled one and shall discard every other pending occurrence. | A3 · [code:ReminderScheduler.kt:72-105,119] |
| REQ-019 | Unwanted | If a registration settles with a cause whose `outcome` is `PERMANENT_FAILURE`, then the schedule reducer shall discard that pending occurrence and render the error state with exactly one recovery action. | A3 · [code:ReminderScheduler.kt:72-105] |
| REQ-020 | Unwanted | If a registration settles with a cause whose `outcome` is `SKIPPED`, then the schedule reducer shall leave the rendered state and the emitted effects unchanged. | A3 · [code:ReminderScheduler.kt:102-104] |
| REQ-021 | Event-Driven | When the user activates retry, the schedule presenter shall register the same `occurrenceId` that failed and shall not derive a new one. | A4 · [code:ReminderContracts.kt:40-54] |
| REQ-022 | Unwanted | If retry is activated while a registration for the same `occurrenceId` is unsettled, then the schedule presenter shall not submit a second registration. | A4 · [code:ReminderScheduler.kt:196-198 flight 合流] |
| REQ-023 | Ubiquitous | The schedule view shall render no `occurrenceId`, generation number, work-request UUID, cause constant name, property id, file path or exception text. | A5 · [card:context/DESIGN.md:1761]，[code:ReminderScheduler.kt:127-133] |

> **三个 outcome 分支各须遍历该 outcome 的全部 cause**（`hygiene` 已写死）：25 个 cause 分属四个
> outcome，抽样一个只证明那一个。实现按 `cause.outcome` 分支而非按 cause 分支，于是上游新增 cause
> 会自动落进正确分支——这条性质只有遍历式用例能证明。
>
> **settlement 携带的是 `occurrenceId` 而不是完整 identity**：waiter 的签名只交出
> `ReminderRegistrationCause`，presenter 知道的是它自己提交的那个 occurrence。若在此构造一个
> `ReminderRegistrationIdentity`，其 generation 半边只能是编造的——而
> `T4-SCHEDULE-REMINDER-DIAGNOSTICS` 的结论正是「身份是一个值，半个身份关联不到任何东西」。
> 故本卡按 occurrenceId 匹配，并在类型上不去假装拥有一个 identity。

## 验收与验证方法

| 验收集 | REQ | 验证方法 | oracle |
|---|---|---|---|
| A1 | REQ-010, 011, 012 | automated · 注入 `ReminderPermissionPort` 的读取计数与时序，含 resume 与 action 之间被撤销 | 端口调用序列 |
| A2 | REQ-013..017 | automated · 授权/拒绝/撤销/回前台四条转移 + 零 settings 调用 | 状态 + port traffic |
| A3 | REQ-018, 019, 020 | automated · 按 `outcome` 三分支，各遍历该 outcome 全部 cause | pending 集合 + screen |
| A4 | REQ-021, 022 | automated · 重试身份复用与在途抑制 | 注册调用序列 |
| A5 | REQ-023 | automated · 渲染面不含 id / cause 名 / property id | 领域状态 |
| A6 | 全部 | automated · 变异收据（selector / RED exit / 前后同 SHA-256） | 收据本身 |

> **本卡验收面全自动**，无人工评审项。

## 未决决策

无。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 第二次拆卡（用户裁定）拆出的 presenter 半，原 A2–A4 / REQ-010..023，acceptance 重编号为 A1–A6 并按实现收紧（撤销时机、遍历全部 cause、occurrenceId 而非半个 identity）。 |
