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

## 决策记录（Decision log）

1. **`pending` 与 `submission` 是两个问题，不是一个槽。** REQ-019 要求 PERMANENT_FAILURE **丢弃**
   pending，REQ-021 又要求重试重投**失败的那个** `occurrenceId`——只有一个槽时，
   `ScheduleScreenState.Error` 唯一的恢复动作就是一个什么也做不了的按钮。故 `pending` 只表示
   「下一次读到授权时可以**不再问用户**就注册的那个 occurrence」（REQ-014 的自动路径），
   `ScheduleSubmission` 表示「这次提交的是什么、结算了没有」，显式重试重放后者。于是永久失败
   清掉自动路径（resume 永不静默重投一个已永久失败的 occurrence）而重试仍指名同一个 occurrence。

2. **结算只由它点名的那次注册应用。** waiter 携带的是它被提交时的 `occurrenceId`；若较早
   occurrence 的回调在 presenter 已转向另一个 occurrence 之后到达，原草图会清掉**不属于它**的在途
   标记、并画出**不属于它**的错误状态，随后一次重试就能重复注册。这是实现期发现的 fail-open，卡片
   未写；`settle` 入口先按 `occurrenceId` 认领 submission，认不下就整体不变（REQ-022 的同类保护）。

3. **权限恢复面画在屏幕状态旁边，不取代它。** `ScheduleScreenState` 是互斥层；把 PERMISSION 做成
   一个屏幕状态会让日程表本身不可读，与 REQ-015 自己要求的「the in-app schedule remains usable」
   （亦即 `context/DESIGN.md` 给通知权限指定的 fallback）自相矛盾。故新增派生值
   `ScheduleUiState.permissionRecovery`（单值、可为空，`ScheduleActionSlot` 增 `OPEN_SETTINGS`），
   与 `actionSlot` 同样「一个或没有、永不是集合」。`ScheduleRecovery` **刻意不**增 `OPEN_SETTINGS`
   ——那会造出一个 `actionSlot` 永不返回的死枚举值。

4. **提交守卫放在唯一提交点。** REQ-022 只点名重试，但 resume 在未结算时同样会二次注册。守卫落在
   `submit()` 这一个出生点，于是重试、resume 与重复动作三条路一体生效，没有哪个调用方是「忘了加
   守卫的那个」。

5. **SKIPPED 不改渲染面与效果，但仍把 submission 结算掉。**（R3 第 1 轮后修正：初版把在途标记
   一起留着，理由是「无实义的注册没什么可重试」。这是错的——两个 SKIPPED cause
   （`OCCURRENCE_CLOSED` / `GENERATION_SUPERSEDED`）都是这次注册的**终态答复**，飞行确实结束了；
   留着标记等于把重试永久挂起，而屏幕上若已有 Error，它那唯一的恢复动作就永远按不动。REQ-020 管的
   是「rendered state 与 effects」，`submission.settled` 两者都不是，故按字面仍然成立。）

7. **权限读取下沉到唯一提交点。** REQ-011 说的是「submitting the registration 之前再读一次」，而
   注册的入口不止用户那一个动作：`onRetry` 会提交，resume 释放 pending 会提交，`dispatch` 是公开的
   因而 `ReminderRequested` 也能直接提交。读取只放在 `onReminderAction` 里时，另外三条路都能拿着被
   撤销的权限完成注册——这正是本卡 `forbid` 第 1 条要禁的「用陈旧 GRANTED 状态排程」。故 API 33 的
   读取与「在途不重投」的守卫一起落在 `submit()`：两条规则同在唯一出生点，谁都当不成「忘了加守卫的
   那个调用方」。`onResume` 仍自己读一次，因为那一次是**决定渲染什么**的读（REQ-010），与紧贴提交的
   那一次目的不同。

8. **Error 只在还有可重放的已结算 submission 时渲染。** 永久失败画出 Error 之后，一次被 ADMITTED
   的重试会清空 submission；若不同时撤下 Error，屏幕上就留着一个按下去什么也不发生的恢复动作。故
   ADMITTED 分支在当前屏幕是 Error 时重新投影，其余屏幕原样不动（例如仍在 Loading 时不会被提前
   投影成空态）。这条与决策 5 合起来把「Error 配着按不动的按钮」这一类缺陷整类关掉。

6. **A5 的变异靶在行投影上。** 渲染面之所以不含 id，是因为 `ScheduleScreenState` 的任何分支都不带
   带 id 的字段——这条性质无法被单点变异**违反**而仍编译。可被违反的只有「名字来自哪个字段」，故
   M12 / M37 分别打 Due 与 FirstInspection 两条分支的 `propertyName`，由逐行名字的字面量断言击杀。

## 变更记录（Change log）

| 日期 | 变更 |
|---|---|
| 2026-09-03 | 建卡：承接 `T4-SCHEDULE-UI` 第二次拆卡（用户裁定）拆出的 presenter 半，原 A2–A4 / REQ-010..023，acceptance 重编号为 A1–A6 并按实现收紧（撤销时机、遍历全部 cause、occurrenceId 而非半个 identity）。 |
| 2026-09-04 | **R3 第 1 轮 block，2 条 finding 全部属实**，均已修：① 注册的权限闸只装在
`onReminderAction` 上，而 `onRetry` 与公开的 `dispatch(ReminderRequested)` 都能绕过它完成注册
（见决策 7，修法是把读取下沉到唯一提交点，并补两条测试：API 33 重试遇撤销、直接派发的请求）；
② 永久失败后的重试被 ADMITTED 时 Error 不撤，留下按不动的恢复动作（见决策 8 与决策 5 的修正，
补「永久失败→重试→ADMITTED」与「SKIPPED 后重试仍可用」两条测试）。**改产线即作废整批收据（L270）**，
37 枚重跑为 40 枚。为把 R3 修复挤回 1000 行硬闸内，合并了两对同一 REQ 的重复用例（REQ-010 的授权/
拒绝两半、REQ-018 的保留/丢弃两半，覆盖面不变），并把已合并卡的收据块**留在原处只改其两行哈希**
（而非整块替换）——保住逐枚具名击杀证据的同时省下约 119 行 diff。 |
| 2026-09-04 | 实现：`_local/` 里那份 presenter 草稿按上面 6 条决策重写后落地，非原样移植。草稿有三处与本卡验收对不上——① 没有任何「settings 恢复态」的领域值（A2 的「one Open settings action」只是一个未被任何测试看见的 Compose 按钮）；② `ScheduleEffect.OpenSettings` 从未被任何 reducer 分支发出（死分支），已删；③ `ScheduleRecovery.OPEN_SETTINGS` 是 `actionSlot` 永不返回的死枚举值，未采用。另修两处 fail-open（决策 2、4）。**M1–M15 随本卡整批重跑**：收据钉的是生产文件的确切 SHA-256，`ScheduleModels.kt` 一改即作废前一批（L270），故 15 枚旧变异与 22 枚新变异同批执行、同一份基线。 |
