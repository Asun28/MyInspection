---
id: T4-SCHEDULE-REMINDER-DELIVERY
title: 提醒身份、耐久回执、诊断与通知交付边界
depends_on: [T4-SCHEDULE-CADENCE, T4-SCHEDULE-REMINDER-SPLIT-PLAN]
status: todo
branch: T4-SCHEDULE-REMINDER-DELIVERY
worktree: C:\wt\T4-SCHEDULE-REMINDER-DELIVERY
allow_paths:
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络、根导航或合规决策
  - 把全新空 store 与曾初始化后丢键的损坏 store 混为同一状态
  - 静默吞掉持久化失败、模糊交付、未知异常或诊断原因
non_goals:
  - WorkRequest 构造、唯一队列注册、并发 callback fan-out 或 Compose UI
  - 数据库 schema、依赖或 app shell 变更
acceptance:
  - "A1 canonical occurrence identity binds nonblank property, inspection type, and exact Instant seconds+nanos; route fields, all four bilingual copies, private immutable collision-safe intents, lowercase 64-hex shape, and golden vector are independently asserted"
  - "A2 one versioned private receipt stores the full reminder spec, work generation id, phase, and cause; a genuinely new store is MISSING while lost sentinel/key, literal MISSING, invalid data, read/write failure, or mismatched ids fail closed"
  - "A3 worker writes DELIVERY_UNCERTAIN before notify and DELIVERED after success; ambiguous post never reposts, transient pre-post failures retry within bounds, permission exhaustion becomes PERMISSION_BLOCKED, and permanent/unknown failures become TERMINAL"
  - "A4 exact sanitized JSON diagnostics retain stage, retryable, error_code and cause_code; notification permission is the only manifest addition and alert-once behavior is pinned"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; $tests = @($kotlin | Where-Object { $_ -like '*src/test*' }); if (Select-String -Path $tests -Pattern 'readText\(|readLines\(|Files\.readString' -Quiet) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderContractsTest" --tests "nz.myinspection.app.feature.schedule.ReminderDiagnosticsTest" --tests "nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest" --tests "nz.myinspection.app.feature.schedule.ReminderWorkerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: focused JVM tests mutate identity, route/copy, versioned persistence, worker transition and exact JSON fields; assembleDebug compiles the real notification and SharedPreferences adapters.
review_gate: codex {verdict:pass}
hygiene: 长自主档但保持单一 delivery 产出；正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行，并保留 mutation-survivor 证据。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，scheduler 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-DELIVERY

从 PR #212 精确 head `a0ed8da4ed2f374a48ddeef9de146f9be2696d7d` 只提取已理解的 delivery 行为并重新 RED-first 实现，不整体 cherry-pick。WorkManager 2.11.2 仍是进程重启后的执行真相源；本卡只拥有应用私有状态与交付适配。

身份 golden vector：property `property-a`、type `ROUTINE`、due `2026-08-03T00:00:00.000000001Z` 必须得到 `c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108`。

## 冻结回执协议

回执保存非负单调 `generationNumber`。`generationId` 必须由 `UUID.nameUUIDFromBytes(UTF8("reminder-work/v1\n" + occurrenceId + "\n" + generationNumber))` 唯一推导，并直接作为 WorkRequest ID；回执另存完整 ReminderSpec、phase 与稳定 cause code。首轮 generationNumber 为 0，只有显式 permission recovery 才加 1；旧 generation 的 callback/worker 不得改写新 generation。

| 当前 phase | 唯一合法写入/动作 | 后继 phase 或结果 |
|---|---|---|
| 全新且无初始化 sentinel | scheduler 先耐久写完整 spec + 新 generation | `ADMISSION_PENDING`；写失败则不得 enqueue |
| `ADMISSION_PENDING` | KEEP 接受且确认 retained WorkRequest ID 等于 generation | `ENQUEUED`；ID 不同即 `QUARANTINED` |
| `ADMISSION_PENDING` | enqueue 明确可重试/永久失败 | `RETRYABLE` / `TERMINAL`，并返回 admission 失败 |
| `ADMISSION_PENDING` | 30 秒单调时钟 watchdog 未收到 operation callback | 保持 `ADMISSION_PENDING`，记录 `ENQUEUE_CALLBACK_TIMEOUT`，全部 waiter 收 `RETRYABLE_FAILURE` 并清 flight；不得二次 enqueue |
| `ENQUEUED` / `RETRYABLE` | notify 调用前的权限检查、channel 或输入准备明确 transient 且仍有次数 | `RETRYABLE` + WorkManager retry，同 generation |
| `ENQUEUED` / `RETRYABLE` | 权限失败且次数耗尽 | `PERMISSION_BLOCKED`；不得自动 repost |
| `ENQUEUED` / `RETRYABLE` | notify 前耐久写成功 | `DELIVERY_UNCERTAIN`，随后才允许调用 notify |
| `DELIVERY_UNCERTAIN` | `notify()` 成功且 final write 成功 | `DELIVERED` |
| `DELIVERY_UNCERTAIN` | `notify()` 已被调用后抛错，或 final write/进程死亡 | 保持 `DELIVERY_UNCERTAIN` 并 stop，永不自动 repost；notify 调用本身从不归类为明确 pre-post |
| `PERMISSION_BLOCKED` | scheduler 专用 API 重新检查已授权 | 新 generation 的 `ADMISSION_PENDING`；旧 generation 失效 |
| 身份/输入/未知 runtime 永久失败 | 写稳定 cause | `TERMINAL` |
| sentinel/key/编码/ID 损坏或 adapter 读异常 | 隔离并记录稳定 cause | `QUARANTINED` |
| `DELIVERED` / `TERMINAL` / `DELIVERY_UNCERTAIN` / `QUARANTINED` | 重复注册 | skip 或 fail closed；不得 enqueue/notify |

Mutation tests 必须钉住 generation 算法与递增、reservation 写失败零 enqueue、`DELIVERY_UNCERTAIN` 写失败零 notify、notify 后 final-write 失败不重投、权限耗尽与旧 KEEP callback 交错时旧 generation 不得覆盖新状态。
