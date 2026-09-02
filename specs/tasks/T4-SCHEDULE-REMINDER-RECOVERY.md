---
id: T4-SCHEDULE-REMINDER-RECOVERY
title: 权限恢复、注册诊断渲染与确定性 admission 重读
depends_on: [T4-SCHEDULE-REMINDER-FLIGHT]
status: todo
branch: T4-SCHEDULE-REMINDER-RECOVERY
worktree: C:\wt\T4-SCHEDULE-REMINDER-RECOVERY
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、依赖/schema 或 UI 变更
  - 未授权时触碰 WorkManager 或改写回执
  - 在注册诊断里发布 property/date/path/异常文本，或发布关联不上的 occurrence/generation/work id
non_goals:
  - 重做 FLIGHT 卡已合并的 flight 协调、waiter 隔离、callback settle 与 watchdog
  - 改写 delivery 卡已冻结的身份、回执序列化、worker 或通知文案合同
  - 启动时权限请求、权限申请 UI 与根导航（归 T4-SCHEDULE-UI）
acceptance:
  - "A1 permission recovery freshly verifies the grant at the moment of recovery, lets the store derive the next generation and its work request id, and re-registers under that new generation; while the grant is missing it touches neither WorkManager nor the receipt and reports a distinct cause"
  - "A2 registration diagnostics render in the delivery card's exact JSON field vocabulary, carrying an irreversible occurrence_id, a non negative generation_number, the derived work_request_id, the conflicting retained_work_request_id and the failure class cause_code, and drop any of those they cannot correlate rather than publishing them"
  - "A3 definitive admission survives a lost compare and set: a call site already holding admission evidence (the callback confirmed it, or retained work showed this generation ENQUEUED or RUNNING) re-reads only to decide superseded or closed, and never downgrades that evidence to contention (TD167)"
  - "A4 the bounded re-read exhaustion the scheduler card pre-declared as a survivor is settled here: the re-read is bounded, exhausting that bound reports its own cause rather than looping or reporting contention, and a mutation of the bound turns a named test red"
  - "A5 a failure callback that arrives after a worker-proved admission was already settled by the watchdog is recorded as ENQUEUE_CALLBACK_AFTER_WORKER_STARTED carrying its original callback class, exactly as a callback that wins that race already is, and it still changes no waiter and no receipt (TD168)"
  - "A6 runtime acceptance tests invoke the compiled scheduler and the production delivery runner over one shared receipt store with concrete inputs and production-used injected enqueue, query, permission, deadline and clock ports, assert only domain results, rendered diagnostic strings and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM drives permission recovery 的授予/未授予两态、诊断渲染的逐字段关联与丢弃、confirm 路径在 CAS 失败后的 admission 保全、以及重读上界耗尽，asserts domain results, rendered strings and recorded effects only, and records A1-A5 semantic mutations without a source-derived oracle; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；权限两态、每个被丢弃的诊断字段、confirm 与非 confirm 两类调用点的重读分歧、重读上界均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID + TD167/TD168 置 paid；本卡 R5 归档，T4-SCHEDULE-UI 转为 ready。
---

# T4-SCHEDULE-REMINDER-RECOVERY

承接 `T4-SCHEDULE-REMINDER-FLIGHT`，收掉提醒串行链在注册侧的最后三项——它们各自独立于 flight 协调器，落点分别是 `coordinate()` 的 PERMISSION_BLOCKED 分支、注册诊断的渲染，以及 `reread()` 的判定策略。

## 拆分依据（承接 FLIGHT 的预算拆分，L266/L276）

`T4-SCHEDULE-REMINDER-SCHEDULER` 顶到 996 行 / 51k 字符时把三项留给了 FLIGHT；FLIGHT 开工前按「一条验收一个黑盒用例」重估，连同这三项合计约 950 行 / 66k 字符，**超出 `review.ps1` 的 60000 字符硬闸**（fail-closed，顶破即评审者不会被唤起）。用户裁定二分：flight 机制留在 FLIGHT，这三项归本卡。两卡 `allow_paths` 相同、故**严格串行**，FLIGHT 合并后本卡才开工。

## 权限恢复

`PERMISSION_BLOCKED` 是 delivery runner 在权限耗尽时写下的终点，注册侧此前按 closed phase 一律跳过。本卡补上恢复路径：**在恢复的那一刻**读一次新鲜授权（不得复用任何缓存或早先读到的 GRANTED），授权在则交给 store 自己递增 generation 并派生新的 WorkRequest UUID（`recoverPermissionBlocked` 已是唯一的 generation 递增点，身份漂移在那里已不可表达），再以新 generation 走一次注册；授权不在则**既不碰 WorkManager 也不改回执**，以自己的 cause 如实回报，等下一次注册或 UI 重试。

## 诊断渲染

上一张卡只落了 typed settlement 记录（`ReminderRegistrationRecord`），日志侧尚无渲染。本卡按 delivery 卡 `reminderLogMessage` 已冻结的字段词汇渲染注册诊断，并补两项本链诊断真正需要的字段：`retained_work_request_id`（与本代 work id 冲突的那一个，迟到/跨代诊断正靠它定位）与失败类别 `cause_code`。**关联不上就丢弃**这一纪律照搬 contracts 卡：occurrence_id 须过不可逆摘要形态、generation_number 须非负、work_request_id 须恰是二者派生出的那一个，任一不成立即该字段为 null，绝不把一个合法但无关的 UUID 发布成本 occurrence 的。property/date/path 与异常文本永不出现。

## 确定性 admission 与有界重读（TD167）

`confirm` 路径的调用点本身已握有 admission 证据——callback 已确认，或 retained 查询看到本代 `ENQUEUED`/`RUNNING`。此前一次 CAS 失败后的重读只认同代 `ENQUEUED`，于是 worker 抢在该 CAS 之前把同代推到 `RETRYABLE` 时，本次注册记 `RECEIPT_CONTENDED` 而非 admission（方向是**低报**：下次注册的 retained 查询会以证据自愈，故无信息丢失，只延后一次）。修法是把「调用点自带的确定性证据」与「靠相位反推」分开：confirm 路径的重读**只用来判定 superseded/closed**，其余一律沿用调用点已有的结论。

重读须有界：耗尽上界时报自己的 cause，既不在循环里空转，也不退化成 contention——上一张卡把这条记为事前声明的幸存变异，本卡把它变成可证伪的行为并配一枚让上界变红的变异。

## 测试纪律

Runtime acceptance tests are black-box behavioral tests：测试调用 compiled scheduler entry point，并在同一 fake receipt store 上调用 delivery 卡的 compiled production delivery runner 制造真实的 worker 竞速；production-used injected ports 覆盖 enqueue/query/permission/deadline/clock，只断言领域结果、渲染出的诊断字符串与记录的边界 effects。不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。A1–A4 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

测试必须覆盖权限授予与未授予两态（未授予时零 WorkManager 调用、零回执写入）、恢复后以 n+1 代与派生 UUID 提交、诊断渲染的每个字段在关联成立与不成立两侧、confirm 路径被 worker 抢先 CAS 到 `RETRYABLE` 后仍报 ADMITTED 家族、非 confirm 调用点同样情形仍报 contention（两类调用点的分歧必须都被钉住），以及重读上界耗尽。
