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
  - remote/custom-process Worker 或跨进程 SharedPreferences CAS 声称
non_goals:
  - WorkRequest 构造、唯一队列注册、并发 callback fan-out 或 Compose UI
  - 数据库 schema、依赖或 app shell 变更
acceptance:
  - "A1 canonical occurrence identity binds nonblank property, inspection type, and exact Instant seconds+nanos; route fields, all four bilingual copies, private immutable collision-safe intents, lowercase 64-hex shape, and golden vector are independently asserted"
  - "A2 one credential-encrypted private v1 store atomically writes a store sentinel, per-occurrence membership marker, and full receipt; a blank store or valid store with both occurrence keys absent is MISSING, while invalid retained metadata, one-key loss, malformed/future encoding, identity mismatch, or adapter failure fails closed"
  - "A3 worker verifies the actual WorkRequest id, writes DELIVERY_UNCERTAIN before post, and writes DELIVERED only after post returns; post exceptions including SecurityException never repost, attempts 0 and 1 retry explicit pre-post transients, exhausted permission denial becomes PERMISSION_BLOCKED, and exhausted non-permission or permanent/unknown failures become TERMINAL"
  - "A4 exact sanitized JSON diagnostics retain stage, retryable, error_code and cause_code; the source manifest declares POST_NOTIFICATIONS exactly once, Android adapters compile, and alert-once/private immutable intent descriptors are pinned by pure tests"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; $tests = @($kotlin | Where-Object { $_ -like '*src/test*' }); if (Select-String -Path $tests -Pattern 'readText\(|readLines\(|Files\.readString' -Quiet) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderContractsTest" --tests "nz.myinspection.app.feature.schedule.ReminderDiagnosticsTest" --tests "nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest" --tests "nz.myinspection.app.feature.schedule.ReminderWorkerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; [xml]$manifest = Get-Content -Raw 'android/app/src/main/AndroidManifest.xml'; $permissions = @($manifest.manifest.'uses-permission' | ForEach-Object { $_.GetAttribute('name','http://schemas.android.com/apk/res/android') }); if ($permissions.Count -ne 1 -or $permissions[0] -ne 'android.permission.POST_NOTIFICATIONS') { exit 1 }
dod_exit: 0
dod_assert: host JVM tests mutate pure identity, route/copy, fake preference-port, worker-transition and exact JSON contracts; source XML pins one notification permission and assembleDebug compiles the real notification and SharedPreferences adapters without claiming framework execution.
review_gate: codex {verdict:pass}
hygiene: 长自主档但保持单一 delivery 产出；正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行，并保留 mutation-survivor 证据。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，scheduler 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-DELIVERY

从 PR #212 精确 head `a0ed8da4ed2f374a48ddeef9de146f9be2696d7d` 只提取已理解的 delivery 行为并重新 RED-first 实现，不整体 cherry-pick。WorkManager 2.11.2 仍是进程重启后的执行真相源；本卡只拥有应用私有状态与交付适配。

身份 golden vector：property `property-a`、type `ROUTINE`、due `2026-08-03T00:00:00.000000001Z` 必须得到 `c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108`。

## 冻结回执协议

回执保存非负单调 `generationNumber`。`generationId` 必须由 `UUID.nameUUIDFromBytes(UTF8("reminder-work/v1\n" + occurrenceId + "\n" + generationNumber))` 唯一推导，并直接作为 WorkRequest ID；回执另存完整 ReminderSpec、phase 与稳定 cause code。首轮 generationNumber 为 0，只有显式 permission recovery 才加 1；worker 每次转换前比较自身真实 WorkRequest ID，旧 generation 的 callback/worker 不得改写新 generation。Golden generation 0/1 分别为 `40fe7461-9be1-3ce7-8bdf-28b48b76359e` 与 `590ca815-2783-322a-acde-39ab31dafd39`。

同一 credential-encrypted private SharedPreferences 文件固定使用 `store=reminder-receipts/v1`、`seen:{occurrenceId}=v1` 与 `record:{occurrenceId}=strict-v1-envelope`。首次 admission 用同一个 editor 同步 commit 三键；后续转换在默认 app process 的单一 process-wide lock 内做 read-validate-commit，且只声称 in-process linearizable CAS。`commit()==false` 或抛错均视为 write-uncertain：本次不得 enqueue/notify，同进程 guard 保持 fail closed；read-back 不冒充耐久证明，fresh process 只接受完整可解码的 marker+record。

全 preference 文件或同一 occurrence 的 marker+record 同时丢失，与 fresh app data/新 occurrence 不可区分，本卡明确不声称 SharedPreferences 能检测。Exactly-one-key loss、invalid/missing sentinel with retained data、错误版本/字段数/身份/generation 则必须 quarantine。

| 当前 phase | 唯一合法写入/动作 | 后继 phase 或结果 |
|---|---|---|
| 全新空 store，或 valid store 中 seen+record 均不存在 | scheduler 一次 commit sentinel+seen+完整 record + 新 generation | `ADMISSION_PENDING`；写失败/不确定则不得 enqueue |
| `ADMISSION_PENDING` | KEEP 接受且确认 retained WorkRequest ID 等于 generation | `ENQUEUED`；ID 不同即 `QUARANTINED` |
| `ADMISSION_PENDING` | enqueue 明确可重试/永久失败 | `RETRYABLE` / `TERMINAL`，并返回 admission 失败 |
| `ADMISSION_PENDING` | 30 秒单调时钟 watchdog 未收到 operation callback | 保持 `ADMISSION_PENDING`，记录 `ENQUEUE_CALLBACK_TIMEOUT`，全部 waiter 收 `RETRYABLE_FAILURE` 并清 flight；不得二次 enqueue |
| `ENQUEUED` / `RETRYABLE` | post 调用前的权限检查、channel 或输入准备明确 transient，attempt 为 0 或 1 | `RETRYABLE` + WorkManager retry，同 generation |
| `ENQUEUED` / `RETRYABLE` | 权限失败且 attempt >= 2 | `PERMISSION_BLOCKED`；不得自动 repost |
| `ENQUEUED` / `RETRYABLE` | 非权限 pre-post transient 且 attempt >= 2 | `TERMINAL`，保留原稳定 cause；不得自动 repost |
| `ENQUEUED` / `RETRYABLE` | notify 前耐久写成功 | `DELIVERY_UNCERTAIN`，随后才允许调用 notify |
| `DELIVERY_UNCERTAIN` | `notify()` 成功且 final write 成功 | `DELIVERED` |
| `DELIVERY_UNCERTAIN` | `notify()` 已被调用后抛错（含 SecurityException），或 final write/进程死亡 | 保持 `DELIVERY_UNCERTAIN` 并 stop，永不自动 repost；post 调用本身从不归类为明确 pre-post |
| `PERMISSION_BLOCKED` | scheduler 专用 API 重新检查已授权 | 新 generation 的 `ADMISSION_PENDING`；旧 generation 失效 |
| 身份/输入/未知 runtime 永久失败 | 写稳定 cause | `TERMINAL` |
| sentinel/key/编码/ID 损坏或 adapter 读异常 | 隔离并记录稳定 cause | `QUARANTINED` |
| `DELIVERED` / `TERMINAL` / `DELIVERY_UNCERTAIN` / `QUARANTINED` | 重复注册 | skip 或 fail closed；不得 enqueue/notify |

Mutation tests 必须钉住两个 generation golden、第二个新 occurrence 仍为 MISSING、sentinel/seen/record 单键损坏、commit false/throw 零外部副作用、`DELIVERY_UNCERTAIN` 写失败零 notify、post SecurityException，以及 post 后 final-write 失败保持 uncertain 且不重投。Reservation、KEEP callback 与 permission-recovery 交错只由后继 scheduler 卡验证。
