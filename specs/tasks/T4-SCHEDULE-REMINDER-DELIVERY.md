---
id: T4-SCHEDULE-REMINDER-DELIVERY
title: 提醒 Worker、通知发布与不重投边界
depends_on: [T4-SCHEDULE-REMINDER-RECEIPTS]
status: todo
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
