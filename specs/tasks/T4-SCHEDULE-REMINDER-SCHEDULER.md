---
id: T4-SCHEDULE-REMINDER-SCHEDULER
title: WorkManager 唯一注册、恢复与 callback 并发边界
depends_on: [T4-SCHEDULE-REMINDER-DELIVERY]
status: todo
branch: T4-SCHEDULE-REMINDER-SCHEDULER
worktree: C:\wt\T4-SCHEDULE-REMINDER-SCHEDULER
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、依赖/schema 或 UI 变更
  - 把 callback 异常重标为 enqueue 失败，或因一个 waiter 抛错而饿死其余 waiter
  - 用源码字符串断言、自写磁盘队列或陈旧权限快照替代行为测试
non_goals:
  - 改写 delivery 卡已冻结的身份、回执序列化、worker 或通知文案合同
  - 根导航与启动时权限请求
acceptance:
  - "A1 production scheduler feeds one real unique KEEP OneTimeWorkRequest per canonical occurrence with exact route data, deterministic generation id, and enqueue-time Clock delay rounded upward below one millisecond without overflow"
  - "A2 registration reports admission only; fresh ADMISSION_PENDING, ENQUEUED, RETRYABLE and recoverable permission states follow explicit resume paths, all six WorkInfo states reconcile without false admission, and corrupt, terminal, uncertain, delivered, or active retained-work-id mismatch states fail closed or skip exactly as specified"
  - "A3 concurrent registrations coalesce, all waiters resolve even when callbacks throw Throwable, fatal enqueue failures clean active flights, and synchronous or asynchronous callback errors escape with exact identity without ENQUEUE_FAILED relabeling"
  - "A4 permission recovery freshly verifies grant and creates a new work generation; forged identity, rejected null/error, and a 30-second monotonic operation-callback timeout settle exact results, correlated diagnostics, waiters, and active-flight cleanup"
  - "A5 runtime acceptance tests invoke the compiled scheduler with concrete inputs and production-used injected enqueue, query, callback and clock ports, assert only domain results and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box host JVM captures the scheduler-produced request spec and real OneTimeWorkRequest, mutates timing/state/callback branches, verifies exact correlated admission diagnostics, and records A1-A5 semantic mutations without a source-derived oracle; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；callback 的同线程同步、返回后异步、活动期跨线程与 fatal enqueue 均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，UI 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-SCHEDULER

消费已合并 delivery 合同，重新 RED-first 实现 scheduler。真实 WorkManager adapter 使用 pinned 2.11.2 API；host JVM seam 只替换平台调用，不复制业务判断。

Scheduler 必须按 delivery 卡的冻结回执表执行。每次 permission recovery 递增 generationNumber 并推导新 WorkRequest UUID；KEEP 完成只在查询到 retained UUID 与当前 generation 完全相等后才承认 admission。

Operation callback 的单调 30 秒 watchdog 到期时，以 `ENQUEUE_CALLBACK_TIMEOUT`/retryable 诊断结束全部 waiter 并清 active flight，回执保持 `ADMISSION_PENDING`，本次不得再次 enqueue。任何迟到 callback 都忽略；下一次显式 register 或进程恢复先 query unique work，并按下表协调。所有更新都以 occurrenceId + generationNumber + expected phase compare-and-set；CAS 输给 worker/callback 时在 process-wide lock 内重读。`DELIVERED/TERMINAL/PERMISSION_BLOCKED/DELIVERY_UNCERTAIN/QUARANTINED` 才是可原样返回的 closed phase；WorkInfo 已 terminal 时绝不返回 `ADMISSION_PENDING/ENQUEUED/RETRYABLE`，旧 generation 永不覆盖新状态。

| query 结果 | 唯一合法恢复 |
|---|---|
| 当前 UUID 恰一条 `ENQUEUED` 或 `RUNNING` | CAS receipt 为 `ENQUEUED`，承认 admission |
| 当前 UUID 恰一条 `BLOCKED` | 本卡 request 无 prerequisite，故 quarantine `RETAINED_WORK_BLOCKED`，不得承认 admission |
| 当前 UUID 恰一条 `SUCCEEDED` | under lock 重读同 generation receipt；closed phase 原样返回，任一 active phase CAS quarantine `RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT`，竞争后仍 active 则重试协调 |
| 当前 UUID 恰一条 `FAILED` 或 `CANCELLED` | under lock 把任一 active phase CAS `TERMINAL`，分别记录 `RETAINED_WORK_FAILED` / `RETAINED_WORK_CANCELLED`；竞争后 closed phase 可返回，仍 active 则重试协调 |
| 无当前 UUID，且无其它 active UUID | 只有此情形才以同 generation UUID 重试 enqueue；旧 generation 的 terminal history 可忽略 |
| 任一其它 UUID 为 `ENQUEUED`/`RUNNING`/`BLOCKED`，或当前 UUID 重复 | quarantine `RETAINED_WORK_ID_MISMATCH` / `RETAINED_WORK_DUPLICATE`；不得 enqueue |

所有 admission/query/callback/watchdog 诊断沿用 delivery 的精确 JSON 字段，并带不可逆 `occurrence_id`、非负 `generation_number` 与 intended/retained `work_request_id`；retained-ID mismatch 同时用 `retained_work_request_id` 记录实际冲突 UUID，绝不记录 propertyId。

Runtime acceptance tests are black-box behavioral tests：测试只调用 compiled scheduler entry point 与 production-used injected ports，并只断言领域结果及 enqueue/query/callback/clock effects；不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。A1–A4 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。

测试必须覆盖 reservation 写失败零 enqueue、fresh persisted recovery、其它 active retained-ID mismatch、当前 UUID duplicate、旧 generation callback、权限耗尽交错、watchdog 全 waiter settle/清 flight/零二次 enqueue、迟到 callback、query-only 恢复、delay `-1ns/0/+1ns/Long.MAX` 与溢出边界。当前 UUID 的 `ENQUEUED/RUNNING/BLOCKED/SUCCEEDED/FAILED/CANCELLED` 六态各有黑盒结果、稳定 cause、CAS-loss 交错与把该态误标 `ENQUEUED` 的 semantic mutation receipt。
