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
  - "A2 registration reports admission only; fresh ADMISSION_PENDING, ENQUEUED, RETRYABLE and recoverable permission states follow explicit resume paths while corrupt, terminal, uncertain, delivered, and retained-work-id mismatch states fail closed or skip exactly as specified"
  - "A3 concurrent registrations coalesce, all waiters resolve even when callbacks throw Throwable, fatal enqueue failures clean active flights, and synchronous or asynchronous callback errors escape with exact identity without ENQUEUE_FAILED relabeling"
  - "A4 permission recovery freshly verifies grant and creates a new work generation; forged identity, rejected null/error, and a 30-second monotonic operation-callback timeout settle exact results, diagnostics, waiters, and active-flight cleanup"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderSchedulerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; $tests = @($kotlin | Where-Object { $_ -like '*src/test*' }); if (Select-String -Path $tests -Pattern 'readText\(|readLines\(|Files\.readString' -Quiet) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderSchedulerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: host JVM captures the scheduler-produced request spec and real OneTimeWorkRequest, mutates timing/state/callback branches, and verifies exact admission diagnostics; assembleDebug compiles the WorkManager adapter.
review_gate: codex {verdict:pass}
hygiene: 正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行；callback 的同线程同步、返回后异步、活动期跨线程与 fatal enqueue 均有 mutation-survivor。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，UI 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-SCHEDULER

消费已合并 delivery 合同，重新 RED-first 实现 scheduler。真实 WorkManager adapter 使用 pinned 2.11.2 API；host JVM seam 只替换平台调用，不复制业务判断。

Scheduler 必须按 delivery 卡的冻结回执表执行。每次 permission recovery 递增 generationNumber 并推导新 WorkRequest UUID；KEEP 完成只在查询到 retained UUID 与当前 generation 完全相等后才承认 admission。

Operation callback 的单调 30 秒 watchdog 到期时，以 `ENQUEUE_CALLBACK_TIMEOUT`/retryable 诊断结束全部 waiter 并清 active flight，回执保持 `ADMISSION_PENDING`，本次不得再次 enqueue。任何迟到 callback 都忽略；下一次显式 register 或进程恢复先 query unique work：retained UUID 相同则只确认 `ENQUEUED`，不存在才以同 generation UUID 重试，ID 不同则 quarantine。所有更新都以 occurrenceId + generationNumber compare-and-set，旧 generation 永不覆盖新状态。

测试必须覆盖 reservation 写失败零 enqueue、fresh persisted recovery、retained-ID mismatch、旧 generation callback、权限耗尽交错、watchdog 全 waiter settle/清 flight/零二次 enqueue、迟到 callback、query-only 恢复、delay `-1ns/0/+1ns/Long.MAX` 与溢出边界。
