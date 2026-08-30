---
id: T4-SCHEDULE-UI
title: 排程列表、权限恢复与显式重试界面
depends_on: [T4-SCHEDULE-REMINDER]
status: todo
branch: T4-SCHEDULE-UI
worktree: C:\wt\T4-SCHEDULE-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt
  - android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt
forbid:
  - app 启动时请求通知权限或用陈旧 GRANTED 状态排程
  - 修改 MainActivity、根导航、依赖、schema 或后台自动创建巡检
non_goals:
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
acceptance:
  - "A1 due, empty, first, one-off and type-filter states expose badges and stable property/type route callbacks"
  - "A2 API 33 permission is refreshed on resume and immediately before the user reminder action; pre-33 schedules directly"
  - "A3 grant schedules the saved occurrence while denial and revoked permission show settings recovery without startup prompts"
  - "A4 enqueue or tracking failure retains only the matching pending occurrence and exposes a non-crashing retry"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: focused production-state tests pin no startup request, action-time/resume permission refresh, grant/revoke/deny/settings/retry transitions, badges, filters and route callbacks; assembleDebug compiles Compose wiring.
review_gate: codex {verdict:pass}
hygiene: 权限与 pending reducer 的每个事件分支均由单点变异击杀；UI 测试不以源码字符串断言替代行为。
doc_sync: TASK-BOARD 记录合并 OID并把 T4-SCHEDULE 子链标记完成；本卡 R5 归档。
---

# T4-SCHEDULE-UI

只消费已合并 cadence/reminder 合同，拥有独立 Schedule route 内容；不接管 app shell 或根导航。
