---
id: T4-SCHEDULE-CADENCE
title: 巡检类型的本地民历提醒节奏
depends_on: [T4-COMPLIANCE-ENGINE, T4-SCHEDULE-SPLIT-PLAN]
status: todo
branch: T4-SCHEDULE-CADENCE
worktree: C:\wt\T4-SCHEDULE-CADENCE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/schedule/SchedulePlanner.kt
  - android/core/src/test/kotlin/nz/myinspection/core/schedule/SchedulePlannerTest.kt
forbid:
  - 把 13 周提醒节奏展示为法律限制或绕过 ComplianceEngine
  - 运行期网络、schema、依赖或 Android UI 改动
non_goals:
  - WorkManager、通知、权限、Compose 排程界面
acceptance:
  - "A1 ROUTINE uses last same-property same-type finalized local date plus 13 weeks and ANNUAL plus 12 calendar months"
  - "A2 INGOING and EXIT never recur while missing same-type history yields first-inspection advice"
  - "A3 property/type isolation plus month-end and Pacific/Auckland DST boundaries are deterministic"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.schedule.*"
dod_exit: 0
dod_assert: focused JVM tests pin type cadence, first/one-off states, property/type isolation, leap-month end, and NZ DST.
review_gate: codex {verdict:pass}
hygiene: 每条 cadence/隔离边界均有单点变异会令 focused suite 翻红；无 Android fake。
doc_sync: TASK-BOARD 记录合并 OID；本卡 R5 归档。
---

# T4-SCHEDULE-CADENCE

只交付纯 core 日历算法。建议日期不创建巡检，也不替代真实创建/改期时的 ComplianceEngine 判断。
