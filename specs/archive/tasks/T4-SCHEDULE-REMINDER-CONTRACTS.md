---
id: T4-SCHEDULE-REMINDER-CONTRACTS
title: 提醒身份、路由文案与精确诊断合同
depends_on: [T4-SCHEDULE-CADENCE, T4-SCHEDULE-REMINDER-SPLIT-PLAN]
status: merged
branch: T4-SCHEDULE-REMINDER-CONTRACTS
worktree: C:\wt\T4-SCHEDULE-REMINDER-CONTRACTS
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt
forbid:
  - WorkManager 注册、SharedPreferences、通知发布、UI、依赖或 schema 变更
  - 原始 property、日期、地址、联系人、异常消息、路径或 URI 进入诊断
  - 源码字符串、反射、反编译产物或 generated resource 充当验收 oracle
non_goals:
  - Android Notification/Worker adapter 与持久化状态机
  - 根导航、权限请求与用户入口
acceptance:
  - "A1 canonical occurrence identity binds nonblank property, inspection type and exact Instant seconds+nanos; lowercase 64-hex shape and the frozen golden vector are black-box asserted"
  - "A2 deterministic non-negative generation IDs pin generation zero and one golden UUIDs and reject invalid identity or negative generations"
  - "A3 route data URI, notification identity, collision-safe request descriptor, explicit immutable private alert-once policy and all four exact bilingual copies are pure production descriptors"
  - "A4 exact sanitized JSON retains occurrence_id, generation_number, work_request_id, stage, retryable, error_code and cause_code, while corrupt generation correlation becomes null and raw sensitive/error text never appears"
  - "A5 runtime tests invoke named compiled production entry points, assert only domain results, and carry final-snapshot semantic mutation receipts for A1-A4 without any source-derived oracle"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderContracts.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnostics.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderContractsTest.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderDiagnosticsTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderContractsTest" --tests "nz.myinspection.app.feature.schedule.ReminderDiagnosticsTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box JVM tests pin identity, route/copy/privacy descriptors and exact correlated diagnostics through compiled entry points; assembleDebug compiles the Android logger without source inspection.
review_gate: codex {verdict:pass}
hygiene: normal Kotlin only; no typealias, semicolon packing or line over 120 characters; final mutation receipts include selector, production effect, RED exit and identical before/after SHA-256.
doc_sync: TASK-BOARD records merge OID; archive this card and make T4-SCHEDULE-REMINDER-RECEIPTS ready.
---

# T4-SCHEDULE-REMINDER-CONTRACTS

## Light Plan Forge 1/3

The first Delivery candidate added 1,929 lines and cannot enter the 1,000-line R3 gate. The dependency path is:

`T4-SCHEDULE-REMINDER-CONTRACTS` → `T4-SCHEDULE-REMINDER-RECEIPTS` →
`T4-SCHEDULE-REMINDER-DELIVERY` → `T4-SCHEDULE-REMINDER-SCHEDULER` → `T4-SCHEDULE-UI`.

The preserved seed is local branch `codex/t4-reminder-delivery-split-preserve` at exact tip
`46217539dfc1344118daa88cc70d7a33ed196fd6`. Measured with `git diff --no-ext-diff --no-color`
and `--numstat` before projection:

| Card | Exact preserved range | Added/deleted | Unified diff chars |
|---|---|---:|---:|
| Contracts | `56089bb016ec9c183c171f4e786da1860c200734..e4f12f9c9f4a5daa396c703fc0a5dc54e585585a` | 497/0 | 19,416 |
| Receipts | `e4f12f9c9f4a5daa396c703fc0a5dc54e585585a..296b782c6c2be72b8d788ed227daf0000e94a553` | 613/0 | 25,783 |
| Delivery | `296b782c6c2be72b8d788ed227daf0000e94a553..46217539dfc1344118daa88cc70d7a33ed196fd6` | 780/0 | 30,880 |

Each seed is below the R3 ceilings of 1,000 changed lines and 60,000 unified-diff characters. Each card
must rerun the real size gate after its review fixes; this table is evidence for the split, not a waiver.

This card owns only value-level identity, route/copy/privacy descriptors and sanitized diagnostics. The occurrence golden for property `property-a`, type `ROUTINE` and due instant `2026-08-03T00:00:00.000000001Z` is `c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108`. Generation zero and one are `40fe7461-9be1-3ce7-8bdf-28b48b76359e` and `590ca815-2783-322a-acde-39ab31dafd39`.

The route descriptor must be consumed by the later Android adapter rather than duplicated there. Diagnostics expose only validated occurrence/generation/work correlation plus enums; invalid generation data becomes JSON null and no raw exception detail is serialized.

Runtime acceptance tests are black-box behavioral tests. They call named compiled production entry points with concrete values and assert domain outputs only. Repository/generated source, source-derived resources and inspected compiled artifacts are never an oracle. A1-A4 each require a production semantic mutation on the final snapshot; unchanged tests must fail and restoration must return the production file to the recorded SHA-256.
