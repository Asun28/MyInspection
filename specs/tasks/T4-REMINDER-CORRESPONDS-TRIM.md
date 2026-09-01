---
id: T4-REMINDER-CORRESPONDS-TRIM
title: 删掉 corresponds 中两个被 store 不变量蕴含的比较
depends_on: [T4-SCHEDULE-REMINDER-DELIVERY]
status: todo
branch: T4-REMINDER-CORRESPONDS-TRIM
worktree: C:\wt\T4-REMINDER-CORRESPONDS-TRIM
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt
forbid:
  - 任何行为改变、任何新增依赖或 schema 改动
  - 削弱、删除或跳过 ReminderWorkerTest 既有任何一个用例
  - 保留任何「事前声明的幸存变异」条目作为覆盖缺口的托词
non_goals:
  - 触碰 ReminderReceiptStore 及其不变量
  - TD166（损坏读不重试）的任何修法
acceptance:
  - "A1 corresponds 及其两个被 store 不变量蕴含的比较（work id、spec）全部消失，回执对应性只比 generation，且判定点就地记录为何只比这一项"
  - "A2 行为不变：ReminderWorkerTest 15 个用例一个不删、全部通过，assembleDebug 编译通过"
  - "A3 变异收据恰好 16 行、不含任何 SURVIVED 条目，且其钉住的 SHA-256 等于当前 ReminderWorker.kt 的真实哈希"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ReminderWorker.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ReminderWorkerTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; $main = $kotlin[0]; $test = $kotlin[1]; if (Select-String -Path $main -Pattern 'fun corresponds|workRequestId == valid\.workRequestId|receipt\.spec == valid\.spec' -Quiet) { exit 1 }; if (Select-String -Path $test -Pattern 'SURVIVED' -Quiet) { exit 1 }; if (@(Select-String -Path $test -Pattern '^ \* A[1-4] M\d{2} ').Count -ne 16) { exit 1 }; $sha = (Get-FileHash -Algorithm SHA256 $main).Hash.ToLower(); if (-not (Select-String -Path $test -Pattern $sha -SimpleMatch -Quiet)) { exit 1 }; if (@(Select-String -Path $test -Pattern '@Test').Count -ne 15) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ReminderWorkerTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: 两个被蕴含的比较与 corresponds 本身在源码中不存在；测试用例数仍为 15、全绿；assembleDebug 通过；收据恰好 16 行、无 SURVIVED，且收据里钉的 SHA-256 与 ReminderWorker.kt 的真实哈希逐字相等（收据遂在 DoD 时自证新鲜，L270）。
review_gate: codex {verdict:pass}
hygiene: 纯删除 + 就地说明，无新增测试；16 枚既有语义变异在删除后重跑全部仍击杀，收据按新 SHA 重钉。
doc_sync: TASK-BOARD 记录 merge OID 并更新 DELIVERY 行的「事前声明幸存」表述；归档本卡。
---

# T4-REMINDER-CORRESPONDS-TRIM

## 依据

`T4-SCHEDULE-REMINDER-DELIVERY`（master `41793005`）的 `corresponds` 比三项：generation、work id、spec。
R4 事前声明其中两项无法被任何输入证伪并如实记为幸存变异（S01/S02）。用户裁定：**删除**，不保留为信任边界。

蕴含关系（删除的正确性依据，非猜测）：`ReminderReceiptStore.lookupLocked` 只返回
`receipt.occurrenceId == occurrenceId` 的回执；其 `isValidReceipt` 强制
`workRequestId == reminderGenerationId(occurrenceId, generationNumber)` 且 spec 是该 occurrence 的规范 spec。
而 `validate` 已强制 `valid.workRequestId == reminderGenerationId(occurrenceId, valid.generationNumber)`、
`valid.spec` 由同一 route/dueAt 派生。故 generation 相等 ⇒ 另两项必然相等，删之不改变任何可观测行为。

## 非 TDD 卡（显式声明）

本卡是**行为不变的删除**，写不出一个「删除前红、删除后绿」的测试——那正是 S01/S02 幸存的定义。
故 `-SkipRed`。不变性由两条既有证据承担：15 个用例一个不删全绿，且 16 枚既有语义变异重跑后仍逐一击杀。

## 收据纪律

删除改动了 `ReminderWorker.kt` 的字节，**旧收据当场作废**（L270）。必须重跑整批 16 枚、按新 SHA-256 重钉，
并删除 S01/S02 两行与「事前声明幸存」整段。DoD 会把收据里的 SHA 与文件真实哈希逐字比对，钉错即闸红。
