---
id: T4-SCHEDULE-REMINDER-FLIGHT
title: 注册合流、异步 callback flight 与单调 watchdog
depends_on: [T4-SCHEDULE-REMINDER-SCHEDULER]
status: todo
branch: T4-SCHEDULE-REMINDER-FLIGHT
worktree: C:\wt\T4-SCHEDULE-REMINDER-FLIGHT
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、依赖/schema 或 UI 变更
  - 把 callback 异常重标为 enqueue 失败，或因一个 waiter 抛错而饿死其余 waiter
  - watchdog 二次 enqueue，或在 deadline 未真正到达时就 settle
non_goals:
  - 改写 delivery 卡已冻结的身份、回执序列化、worker 或通知文案合同
  - 重做 scheduler 卡已合并的 WorkRequest 构造、保留工作查询表与权限恢复判定
  - 根导航与启动时权限请求
acceptance:
  - "A1 concurrent registrations of one occurrence coalesce into a single reservation, query and enqueue, and every waiter of that flight settles exactly once with the same exact cause"
  - "A2 the enqueue seam becomes asynchronous: registration returns after submission, the operation callback settles the flight with its exact class and cause, and a waiter that throws Throwable never starves the remaining waiters or the diagnostic"
  - "A3 a matching worker that leaves ADMISSION_PENDING before the callback settles every waiter as admitted, later callback failures record ENQUEUE_CALLBACK_AFTER_WORKER_STARTED with their original class without downgrading a proved admission, and a callback naming a superseded generation changes no waiter or receipt"
  - "A4 one monotonic 30 second watchdog per flight settles all waiters as a retryable timeout only while the under-lock receipt is still ADMISSION_PENDING with no worker proof, keeps the receipt pending, clears the flight, never enqueues a second time, and reschedules rather than settling when the monotonic deadline has not actually passed"
  - "A5 a callback arriving after settlement changes no waiter or receipt yet is still recorded exactly, and fatal enqueue failures clear the active flight so the next registration re-coordinates instead of joining a dead one"
  - "A6 registration diagnostics render in the delivery card's exact JSON field vocabulary, carrying an irreversible occurrence_id, a non negative generation_number, the derived work_request_id, the conflicting retained_work_request_id and the failure class, and the bounded re-read exhaustion the scheduler card pre-declared as a survivor is settled and proved here"
  - "A7 runtime acceptance tests invoke the compiled scheduler and the production delivery runner over one shared receipt store and scheduler-owned flight with concrete inputs and production-used injected enqueue, query, permission, deadline and clock ports, assert only domain results and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM drives coalesced registrations,每种 callback 结局、worker-before-callback、迟到与跨代 callback 及 watchdog 提前/到期两态，asserts waiter results, receipt phases and recorded effects only, and records A1-A5 semantic mutations without a source-derived oracle; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；callback 的同线程同步、返回后异步、活动期跨线程、waiter 抛错与 watchdog 两态均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，T4-SCHEDULE-UI 转为 ready。
---

# T4-SCHEDULE-REMINDER-FLIGHT

承接 `T4-SCHEDULE-REMINDER-SCHEDULER` 已合并的同步注册路径，把 enqueue seam 改成**异步 callback**并补上并发边界。查询恢复表、WorkRequest 构造、权限恢复判定与回执写法均已冻结在上一张卡里，本卡只改「谁在等、谁来 settle、什么时候超时」。

上一张卡按 R3 diff 预算把两项留给本卡，本卡必须一并交付：① **诊断渲染**——上一张卡只落 typed settlement 记录，本卡补 delivery 精确 JSON 字段的渲染，并加上 `retained_work_request_id` 与失败类别 `cause_code`（迟到/跨代/超时诊断正需要这两项）；② **重读上限耗尽**（`RECEIPT_CONTENDED`）——单次注册构造不出，故上一张卡事前声明为 survivor，本卡的并发合流用例必须让它成为可证伪的行为并配 mutation。

`register` 提交后即返回，结果经 waiter callback 交付。同一 occurrence 的并发注册**合流**到一个 flight：只做一次 reservation、一次 query、一次 enqueue，全部 waiter 拿到同一个 cause。waiter 在 flight 之外被调用（settle 时先在锁内取快照并清 flight，再逐个 invoke），任一 waiter 抛出的 Throwable 都被隔离，不影响其余 waiter 与诊断——这条边界必须是**结构性**的，不是调用方需要记得的约定。

Callback 与 watchdog 都在 store 的原子结果上线性化：仍 pending 的 `SUCCESS` CAS `phase=ENQUEUED` 并以 `CALLBACK_CONFIRMED_ADMISSION` settle 全 waiter；仍 pending 的 null、operation 报错或读取结果时抛出的 Throwable 保持精确 class/cause，CAS `RETRYABLE` 并以 `ENQUEUE_CALLBACK_NULL/ERROR/THROWABLE` settle，不得统称 `ENQUEUE_FAILED`。

Matching worker 若先看到 `ADMISSION_PENDING`，以其 `actualId == generationId` 证明 WorkManager 已 admission 并自行 CAS `ENQUEUED` 后再 delivery；随后 callback success、callback Throwable/null/error 或 watchdog 都重读回执：若当前 generation 已离开 pending，全部 waiter 得 `ADMITTED`（`WORKER_CONFIRMED_ADMISSION`）并清 flight，callback 异常只以原始 class/cause 记 `ENQUEUE_CALLBACK_AFTER_WORKER_STARTED`，**不得把已证实的 admission 改成失败**。Worker、callback、watchdog 三者第一个成功的 transition 决定 flight；已经 settle 后的迟到 callback 不改 waiter/receipt，仍须记录精确 late 诊断。

单调 30 秒 watchdog 每个 flight 一枚：唤醒时先用**同一个单调源**核对 deadline 是否真的到达，未到即按剩余量重新排期而不 settle；到达且回执仍为 `ADMISSION_PENDING`、无 worker proof 时，才以 `ENQUEUE_CALLBACK_TIMEOUT/RETRYABLE_FAILURE` 结束全部 waiter、清 flight 并保持 pending，本次不得再次 enqueue。deadline 与其核对必须来自同一个注入的时间源，否则排期与判定会各按一把钟走。

Runtime acceptance tests are black-box behavioral tests：测试调用 compiled scheduler entry point，并在同一 fake receipt store/flight 上调用 delivery 卡的 compiled production delivery runner 来制造真实 worker-admission transition；production-used injected ports 覆盖 enqueue/query/permission/deadline/clock，只断言领域结果与记录的边界 effects。不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。A1–A5 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

测试必须覆盖并发注册只 enqueue 一次且全 waiter 同 cause、waiter 抛 Throwable 后其余 waiter 与诊断仍完成、四种 callback 结局、worker-before-callback 的 success/error/Throwable/null/watchdog 五种 settle、迟到 callback、跨代 callback、watchdog 提前唤醒重排与到期 settle、watchdog 后 flight 已清故下一次 register 重新协调、以及 fatal enqueue 失败清 flight。
