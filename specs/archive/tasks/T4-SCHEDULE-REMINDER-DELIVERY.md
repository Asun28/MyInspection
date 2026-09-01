---
id: T4-SCHEDULE-REMINDER-DELIVERY
title: 提醒 Worker、通知发布与不重投边界
depends_on: [T4-SCHEDULE-REMINDER-RECEIPTS]
status: merged
branch: T4-SCHEDULE-REMINDER-DELIVERY
worktree: C:\wt\T4-SCHEDULE-REMINDER-DELIVERY
allow_paths:
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt
forbid:
  - WorkManager registration/query/callback flights, UI, exact alarms, boot receivers, dependencies or schema
  - notification before a uniquely won durable DELIVERY_UNCERTAIN transition
  - raw property/date/contact/exception/path/URI logging or swallowed state/diagnostic failures
non_goals:
  - receipt codec changes, scheduler permission recovery and root navigation
  - remote/custom-process Worker or cross-process SharedPreferences CAS claims
acceptance:
  - "A1 ReminderWorker is a thin Android adapter over compiled ReminderDeliveryRunner and validates exact WorkData, actual WorkRequest UUID, occurrence, generation and persisted ReminderSpec before any delivery write"
  - "A2 a matching pending worker uniquely confirms admission, only the winner of ENQUEUED or RETRYABLE to DELIVERY_UNCERTAIN may notify, and concurrent runners plus any uncertain/post/final-write failure never repost"
  - "A3 permission and explicit pre-post transients retry attempts zero and one only; exhausted permission becomes PERMISSION_BLOCKED while exhausted non-permission, permanent, invalid or unknown failures become TERMINAL with stable causes"
  - "A4 exact correlated diagnostics are emitted for corrupt lookup, input, permission, preparation, transition write uncertainty and post failures; Android manifest/notification/private immutable explicit alert-once adapters compile"
  - "A5 black-box tests invoke the compiled runner with production-used preference, permission, preparation, notifier and diagnostic ports, assert effects/results only, and carry final-snapshot semantic mutation receipts"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderWorkerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; [xml]$manifest = Get-Content -Raw 'android/app/src/main/AndroidManifest.xml'; $permissions = @($manifest.manifest.'uses-permission' | ForEach-Object { $_.GetAttribute('name','http://schemas.android.com/apk/res/android') }); if (@($permissions | Where-Object { $_ -eq 'android.permission.POST_NOTIFICATIONS' }).Count -ne 1) { exit 1 }
dod_exit: 0
dod_assert: black-box JVM tests drive the compiled runner and injected runtime ports through concurrent and failure branches; assembleDebug compiles the real Worker/notification adapter and source XML contains exactly one matching permission.
review_gate: codex {verdict:pass}
hygiene: normal Kotlin only; no typealias, semicolon packing or line over 120 characters; final mutation receipts include selector, production effect, RED exit and identical before/after SHA-256.
doc_sync: TASK-BOARD records merge OID; archive this card and make T4-SCHEDULE-REMINDER-SCHEDULER ready.
---

# T4-SCHEDULE-REMINDER-DELIVERY

## Light Plan Forge 3/3

Consume the merged contract and receipt cards. This card owns only the actual Worker/notification delivery boundary and the single source-manifest permission declaration. Scheduler construction, unique KEEP work and callback/watchdog recovery remain in the next card.

The compiled `ReminderDeliveryRunner` and `ReminderWorker` use the same `ReminderReceiptStore`. A matching `ADMISSION_PENDING` worker may win the shared CAS to `ENQUEUED`, but only a caller that itself applies `DELIVERY_UNCERTAIN` may invoke the notifier. Observing another caller's already-uncertain phase is a stop, never permission to post.

Before post, explicit permission/transient failures at attempts zero and one persist `RETRYABLE`. Attempt two closes as `PERMISSION_BLOCKED` for permission or `TERMINAL` otherwise. A durable `DELIVERY_UNCERTAIN` write must precede the notifier call. Once the call is invoked, any Throwable or final receipt-write uncertainty leaves delivery uncertain and no automatic path may repost.

Runtime acceptance tests are black-box behavioral tests. They call the compiled runner with concrete receipt/UUID/input and production-used injected permission, preparation, notifier and diagnostic ports. Concurrent-runner tests must prove one post. Tests assert only domain results and boundary effects, never source/resources/compiled artifacts. A1-A4 each require a production semantic mutation on the final snapshot with a named failing selector and identical before/after SHA-256.

## 交付记录（R5）

**merged**：master `41793005`，PR #219，R3 第 **2** 轮 pass（首轮 1 条 finding，属实且已修）。

### 落地形态
`ReminderWorker.kt` 一个文件承载两层：`ReminderDeliveryRunner` 是纯 JVM 决策核，`ReminderWorker` 是薄 Android
适配（只读 WorkData、装配四个运行时端口、翻译 outcome，无任何交付判断）。四个端口 = 权限 / 通知构建
（preparation）/ 通知发布（notifier）/ 诊断，外加已合并的 `ReminderReceiptStore`。

**关键设计（与退役 seed 的分歧点）**：seed 用 `PrePostNotificationException` 包装器区分「发布前 / 发布后」失败，
任何调用方都可能忘记套上它。本卡把这条边界**做进类型**——preparation 与 notifier 是两个独立端口，于是
「失败发生在发布之前」由**哪个端口抛的**决定，写不出「忘了包装」这条路径。A3 的重试规则与 A2 的不重投规则
因此是结构性的，而非约定性的。

### 不变量
- 两次写夹住通知：`ENQUEUED`/`RETRYABLE` → `DELIVERY_UNCERTAIN` 的 CAS **只有自己 applied 的调用方**可发布；
  观察到别人已置 uncertain 是**停止**，不是发布许可。发布成功后再写 `DELIVERED`。
- 一旦调用 notifier，任何 Throwable 或末次写不确定都只能停在 `DELIVERY_UNCERTAIN`（store 的迁移表本身也只
  允许它去 `DELIVERED`），**无任何自动路径可重投**。
- 身份三重绑定：occurrence 摘要重算绑定 property/type/dueAt；**平台实际运行的 WorkRequest UUID** 绑定
  generation；落库回执再核一次。任一环不符则**在读取存储之前**就被拒（测试断言 `reads == 0`）。
- 诊断只带 occurrence 摘要 / type / generation / 派生 work id——不含 property、日期、URI、异常文本。

### 证据
15 个 JVM 测试（含 40 轮并发竞态，证明恰好一次发布）。**16 枚语义变异逐一击杀 + 2 枚事前声明的幸存**；
收据写在 `ReminderWorkerTest.kt` 本体（allow_paths 只有三个文件，卡片不在其内），含 selector / 生产效应 /
RED 退出码 / 前后同一 SHA-256 `3f2be2b3ea684e1cabc9b597f3a7a88bfe46be65af82432e5a4d7277894e8ef7`。
两枚幸存是 `corresponds` 中位于 generation 比较之下的两个条件：store 自身的回执不变量已保证它们恒真，
外部无法构造使其为假的输入，故**事前声明**而非事后发现——理由记在 `corresponds` 的 KDoc 与收据表。

### R3 首轮 finding（属实，已修）
两张重试阶梯表**每个 attempt 都新建 `ENQUEUED` 夹具**，于是 `RETRYABLE→RETRYABLE`（attempt 1）与耗尽时的
`RETRYABLE→PERMISSION_BLOCKED` / `RETRYABLE→TERMINAL` 从未被真正走过——表面覆盖了整条阶梯，实际只测了第一级。
改为同一个 occurrence 顺序走完 attempt 0→1→2，逐步断言 outcome、phase、诊断条数与内容、以及零发布。
修完重跑全部 18 枚变异复验（生产文件未动，SHA 不变）。

### 技术债
TD166（损坏/不可读回执使提醒静默永久丢失，fail-closed 是刻意选择，恢复面归 scheduler 卡）。