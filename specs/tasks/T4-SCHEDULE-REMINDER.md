---
id: T4-SCHEDULE-REMINDER
title: WorkManager 本地提醒与通知交付边界
depends_on: [T4-SCHEDULE-CADENCE]
status: todo
branch: T4-SCHEDULE-REMINDER
worktree: C:\wt\T4-SCHEDULE-REMINDER
allow_paths:
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderScheduler.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderFeatureTest.kt
forbid:
  - 精确闹钟、BOOT_COMPLETED receiver、运行期网络或合规决策
  - 静默吞掉损坏回执、持久化失败或异常原因
non_goals:
  - Compose 排程列表、根导航、依赖/schema 变更
acceptance:
  - "A1 production builds one real unique KEEP OneTimeWorkRequest per property/type/due occurrence with exact delay and route data"
  - "A2 receipt states make repeated registration and delivery idempotent while corrupt persistence fails closed"
  - "A3 transient failures retry, permanent failures stop, and sanitized structured logs retain diagnostic cause codes"
  - "A4 all four inspection types map to private bilingual notifications with stable collision-safe content intents"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderFeatureTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: host JVM captures the production-built OneTimeWorkRequest and exhaustively verifies identity/data/receipt/failure/log/copy contracts; assembleDebug compiles the real WorkManager, Notification and PendingIntent adapter. Durable DB/restart recovery remains the pinned WorkManager 2.11.2 platform contract; this card does not claim an unavailable host-JVM framework recreation test.
review_gate: codex {verdict:pass}
hygiene: 真实 OneTimeWorkRequest 输入与失败分类均有 mutation-survivor；不以自写磁盘队列伪装 WorkManager。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档。
---

# T4-SCHEDULE-REMINDER

只交付 Android 提醒薄壳与纯可测合同。平台对象由 debug assembly 验证接线；JVM 测试只声称其真实执行的 request/data/identity/state 行为。
