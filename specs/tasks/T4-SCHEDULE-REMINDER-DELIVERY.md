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
  - 把仍保留 sentinel 或 admission evidence 的损坏 store 当成全新状态
  - 静默吞掉持久化失败、模糊交付、未知异常或诊断原因
  - remote/custom-process Worker 或跨进程 SharedPreferences CAS 声称
non_goals:
  - WorkRequest 构造、唯一队列注册、并发 callback fan-out 或 Compose UI
  - 数据库 schema、依赖或 app shell 变更
acceptance:
  - "A1 canonical occurrence identity binds nonblank property, inspection type, and exact Instant seconds+nanos; route fields, all four bilingual copies, private immutable collision-safe intents, lowercase 64-hex shape, and golden vector are independently asserted"
  - "A2 one credential-encrypted private v1 store atomically writes a sentinel, immutable admitted marker, seen marker, and full receipt; only a blank store or a valid store with all three occurrence keys absent is MISSING, while retained admission with missing receipt keys, invalid metadata, malformed/future encoding, identity mismatch, or adapter failure fails closed"
  - "A3 worker verifies the actual WorkRequest id, treats a matching ADMISSION_PENDING start as authoritative admission before delivery, writes DELIVERY_UNCERTAIN before post, and writes DELIVERED only after post returns; post exceptions including SecurityException never repost, attempts 0 and 1 retry explicit pre-post transients, exhausted permission denial becomes PERMISSION_BLOCKED, and exhausted non-permission or permanent/unknown failures become TERMINAL"
  - "A4 exact sanitized JSON diagnostics retain occurrence_id, generation_number, work_request_id, stage, retryable, error_code and cause_code; the source manifest declares POST_NOTIFICATIONS exactly once without excluding unrelated permissions, Android adapters compile, and alert-once/private immutable intent descriptors are pinned by pure tests"
  - "A5 runtime acceptance tests invoke named compiled production entry points with concrete inputs and production-used injected ports, assert only domain results and recorded effects, and carry executable semantic mutation receipts; repository/generated source, source-derived resources, and inspected compiled artifacts are never an oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderContractsTest" --tests "nz.myinspection.app.feature.schedule.ReminderDiagnosticsTest" --tests "nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest" --tests "nz.myinspection.app.feature.schedule.ReminderWorkerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; [xml]$manifest = Get-Content -Raw 'android/app/src/main/AndroidManifest.xml'; $permissions = @($manifest.manifest.'uses-permission' | ForEach-Object { $_.GetAttribute('name','http://schemas.android.com/apk/res/android') }); $notificationPermissions = @($permissions | Where-Object { $_ -eq 'android.permission.POST_NOTIFICATIONS' }); if ($notificationPermissions.Count -ne 1) { exit 1 }
dod_exit: 0
dod_assert: black-box host JVM tests cover pure identity, route/copy, fake preference-port, worker-transition and exact correlated JSON contracts, with A1-A5 semantic-mutation receipts and no source-derived oracle; source XML pins exactly one matching notification-permission declaration while allowing unrelated permissions, and assembleDebug compiles the real notification and SharedPreferences adapters without claiming framework execution.
review_gate: codex {verdict:pass}
hygiene: 长自主档但保持单一 delivery 产出；正常 Kotlin 格式，零 typealias/分号拼接/超 120 字符行，并保留 mutation-survivor 证据。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档，scheduler 卡转为 ready。
---

# T4-SCHEDULE-REMINDER-DELIVERY

从 PR #212 精确 head `a0ed8da4ed2f374a48ddeef9de146f9be2696d7d` 只提取已理解的 delivery 行为并重新 RED-first 实现，不整体 cherry-pick。WorkManager 2.11.2 仍是进程重启后的执行真相源；本卡只拥有应用私有状态与交付适配。

身份 golden vector：property `property-a`、type `ROUTINE`、due `2026-08-03T00:00:00.000000001Z` 必须得到 `c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108`。

`ReminderWorker` 只是 Android adapter；同文件的 compiled production `ReminderDeliveryRunner` 拥有 actual-ID 校验、shared receipt-store lock 与 delivery transitions。Worker 必须委托它，delivery 单测与后继 scheduler 的 worker-before-callback 黑盒交错也调用同一个 runner，不得在测试复制 transition。

## 冻结回执协议

回执保存非负单调 `generationNumber`。`generationId` 必须由 `UUID.nameUUIDFromBytes(UTF8("reminder-work/v1\n" + occurrenceId + "\n" + generationNumber))` 唯一推导，并直接作为 WorkRequest ID；回执另存完整 ReminderSpec、phase 与稳定 cause code。首轮 generationNumber 为 0，只有显式 permission recovery 才加 1；worker 每次转换前比较自身真实 WorkRequest ID，旧 generation 的 callback/worker 不得改写新 generation。Golden generation 0/1 分别为 `40fe7461-9be1-3ce7-8bdf-28b48b76359e` 与 `590ca815-2783-322a-acde-39ab31dafd39`。

同一 credential-encrypted private SharedPreferences 文件固定使用 `store=reminder-receipts/v1`、不可删除的 `admitted:{occurrenceId}=v1`、`seen:{occurrenceId}=v1` 与 `record:{occurrenceId}=strict-v1-envelope`。首次 admission 用同一个 editor 同步 commit sentinel、admitted、seen 与完整 record；后续转换在默认 app process 的单一 process-wide lock 内做 read-validate-commit，且只声称 in-process linearizable CAS。`commit()==false` 或抛错均视为 write-uncertain：本次不得 enqueue/notify，同进程 guard 保持 fail closed；read-back 不冒充耐久证明，fresh process 只接受完整可解码且相互一致的 sentinel+admitted+seen+record。

全 preference 文件或某 occurrence 的 admitted+seen+record 全部被删除仍与 fresh app data/新 occurrence 不可区分，本卡明确不声称 SharedPreferences 能检测。非空文件缺 sentinel、admitted 已保留而 seen/record 任一或全部丢失、admitted 未保留却出现 seen/record、错误版本/字段数/身份/generation 都必须 quarantine；只有完整 valid store 的三个 occurrence keys 均不存在才是新 occurrence `MISSING`。

| 当前 phase | 唯一合法写入/动作 | 后继 phase 或结果 |
|---|---|---|
| 全新空 store，或 valid store 中 admitted+seen+record 均不存在 | scheduler 一次 commit sentinel+admitted+seen+完整 record + 新 generation | `ADMISSION_PENDING`；写失败/不确定则不得 enqueue |
| `ADMISSION_PENDING` | query 唯一命中当前 generation UUID，state=`ENQUEUED`/`RUNNING` | CAS `ENQUEUED`；CAS 竞争失败则重读较新 phase |
| `ADMISSION_PENDING` | `worker.actualId == generationId` 的 worker 先于 Operation callback 启动 | shared lock 下 CAS `phase=ENQUEUED,causeCode=WORKER_CONFIRMED_ADMISSION` 后继续同次 delivery；CAS 输则重读，写失败/不确定则零 notify 并 stop |
| `ADMISSION_PENDING` | 当前 UUID state=`BLOCKED` | `QUARANTINED/RETAINED_WORK_BLOCKED`；本请求无 prerequisite，不得假称 admission |
| 当前 generation active phase | 当前 UUID state=`SUCCEEDED` | under lock 重读；只有 closed phase 原样返回，否则 CAS `QUARANTINED/RETAINED_WORK_SUCCEEDED_WITHOUT_RECEIPT`，绝不返回/回写 active admission |
| 当前 generation active phase | 当前 UUID state=`FAILED`/`CANCELLED` | under lock CAS `TERMINAL/RETAINED_WORK_FAILED` 或 `TERMINAL/RETAINED_WORK_CANCELLED`；竞争后重读仍 active 就重试协调，绝不返回 active admission |
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

所有结构化诊断必须带不可逆 occurrence hash `occurrence_id`、非负 `generation_number` 与该 generation 的 `work_request_id`；损坏到无法可信解码 generation 的 lookup 诊断仍带查询 occurrence id，并把后二者设为 JSON null，不得回填未经验证的值。

Runtime acceptance tests are black-box behavioral tests：每个测试调用具名 production entry point，并用实际 production seam 注入 preference、WorkRequest ID、notifier 或 diagnostic port；只断言返回领域结果与 port 记录的边界效果。测试不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 acceptance oracle；本卡唯一 static 例外是具名 AndroidManifest.xml 的 namespace-aware declaration count，且不冒充运行期行为。

Mutation tests 必须钉住两个 generation golden、第二个新 occurrence 三键全 absent 时仍为 MISSING、retained admitted + seen/record 双丢失以及 admitted/seen/record 其它 partial 组合全部 quarantine、commit false/throw 零外部副作用、matching worker-before-callback 的 pending→enqueued transition 与错误 WorkRequest ID 拒绝、`DELIVERY_UNCERTAIN` 写失败零 notify、post SecurityException，以及 post 后 final-write 失败保持 uncertain 且不重投。每项 acceptance 在 GREEN 后至少做一个 production semantic mutation，未改测试地重跑对应 selector 必须 nonzero；receipt 记录 acceptance、selector、变异的 production branch/port effect、RED exit 与 mutation 前/还原后逐字节相同的 SHA-256。源码文本、测试期望值或仅改注释的 mutation 无效。Reservation、full WorkInfo-state reconciliation、KEEP callback 与 permission-recovery 交错只由后继 scheduler 卡验证。
