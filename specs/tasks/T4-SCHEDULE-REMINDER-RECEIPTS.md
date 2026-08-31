---
id: T4-SCHEDULE-REMINDER-RECEIPTS
title: 提醒耐久回执、损坏隔离与 generation CAS
depends_on: [T4-SCHEDULE-REMINDER-CONTRACTS]
status: todo
branch: T4-SCHEDULE-REMINDER-RECEIPTS
worktree: C:\wt\T4-SCHEDULE-REMINDER-RECEIPTS
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt
forbid:
  - WorkManager 注册、通知发布、UI、依赖、数据库 schema 或跨进程 CAS 声称
  - 把保留 sentinel/admission evidence 的损坏 store 当作全新 occurrence
  - read-back 冒充 commit durability 或持久化失败后继续外部副作用
non_goals:
  - Worker delivery、scheduler callback/flight 与 Android notification adapter
  - 检测完整 preference 文件或某 occurrence 三键同时被彻底删除
acceptance:
  - "A1 a credential-encrypted private v1 store atomically commits sentinel, immutable admitted marker, seen marker and full canonical ReminderSpec receipt for fresh generation zero only"
  - "A2 only a blank store or a valid store with all three queried occurrence keys absent is MISSING; a second occurrence stays MISSING while another complete occurrence remains present"
  - "A3 every retained partial key set, missing/invalid sentinel, malformed or noncanonical UTF-8/base64, future envelope, identity/generation mismatch, unknown cause or invalid phase/cause pair becomes typed QUARANTINED"
  - "A4 false/throwing commits become write-uncertain, poison later same-process mutations, preserve the prior durable receipt when one exists and never use read-back as durability proof"
  - "A5 one process-wide lock linearizes exact occurrence+generation+work-id+phase CAS, old generations cannot overwrite new permission recovery, and final-snapshot semantic mutations prove A1-A4"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStore.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderReceiptStoreTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box JVM tests exercise the production preference port seam, strict codec, partial-key matrix, generation recovery and CAS effects without source inspection; assembleDebug compiles the private SharedPreferences adapter.
review_gate: codex {verdict:pass}
hygiene: normal Kotlin only; no typealias, semicolon packing or line over 120 characters; final mutation receipts include selector, production effect, RED exit and identical before/after SHA-256.
doc_sync: TASK-BOARD records merge OID; archive this card and make T4-SCHEDULE-REMINDER-DELIVERY ready.
---

# T4-SCHEDULE-REMINDER-RECEIPTS

## Light Plan Forge 2/3

Consume the merged contract types and own the only durable application-private receipt protocol. The store sentinel is exactly `store=reminder-receipts/v1`; occurrence evidence uses immutable `admitted:{occurrenceId}=v1`, `seen:{occurrenceId}=v1` and `record:{occurrenceId}=strict-v1-envelope` keys.

First admission accepts generation zero only. The sole generation increment API accepts a fully validated `PERMISSION_BLOCKED` receipt, freshly derives generation `n+1`, and writes `ADMISSION_PENDING`; overflow, identity drift and every other transition fail closed. All operations share one process-wide lock. The implementation claims only in-process linearizable CAS in the default app process.

`commit()==false` or any thrown write is uncertain. The current action stops; a same-process guard prevents later mutation/notification. If a prior durable receipt exists, read-only lookup may expose it with a `writeUncertain` flag; if no prior receipt exists, lookup is quarantined. Complete deletion of all evidence remains explicitly indistinguishable from fresh app data.

Runtime acceptance tests are black-box behavioral tests. They inject the production-used preference port, assert only lookup/transition results and recorded commits, and never inspect source/resources/compiled artifacts. A1-A4 each require a production semantic mutation on the final snapshot with a named failing selector and byte-identical SHA-256 restoration.
