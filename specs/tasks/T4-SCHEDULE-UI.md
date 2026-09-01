---
id: T4-SCHEDULE-UI
title: 排程列表、权限恢复与显式重试界面
depends_on: [T4-SCHEDULE-REMINDER-FLIGHT]
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
  - 用源码字符串读取代替 reducer、permission、route 或 Compose 行为测试
non_goals:
  - 日历集成、自定义节奏、精确闹钟、T2-CAPTURE-UI 接线
acceptance:
  - "A1 due, empty, first, one-off and type-filter states expose badges and stable property/type route callbacks"
  - "A2 API 33 permission is refreshed on resume and immediately before the user reminder action; pre-33 schedules directly"
  - "A3 grant schedules the saved occurrence while denial and revoked permission show settings recovery without startup prompts"
  - "A4 enqueue or tracking failure retains only the matching pending occurrence and exposes a non-crashing retry"
  - "A5 runtime acceptance tests invoke compiled reducer/presenter entry points with concrete inputs and production-used injected permission, scheduler and route ports, assert only domain state and recorded effects, and carry executable semantic mutation receipts; source, resources, and inspected compiled artifacts are never an oracle, while Compose wiring is compile-only"
dod_command: $kotlin = @('android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleModels.kt','android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/ScheduleScreen.kt','android/app/src/test/kotlin/nz/myinspection/app/feature/schedule/ScheduleUiTest.kt'); if ($kotlin | Where-Object { -not (Test-Path $_) }) { exit 1 }; if (Select-String -Path $kotlin -Pattern '\btypealias\b|;' -Quiet) { exit 1 }; if ($kotlin | ForEach-Object { Get-Content $_ | Where-Object { $_.Length -gt 120 } }) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:testDebugUnitTest --tests "nz.myinspection.app.feature.schedule.ScheduleUiTest"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :app:assembleDebug
dod_exit: 0
dod_assert: black-box reducer/presenter tests pin no startup request, action-time/resume permission refresh, grant/revoke/deny/settings/retry transitions, badges, filters and route effects, with A1-A5 semantic-mutation receipts and no source-derived oracle; assembleDebug is compile-only evidence for Compose wiring and makes no behavioral UI-test claim.
review_gate: codex {verdict:pass}
hygiene: 权限与 pending reducer 的每个事件分支均由单点变异击杀；UI 测试不以源码字符串断言替代行为。
doc_sync: TASK-BOARD 记录合并 OID并把 T4-SCHEDULE 子链标记完成；本卡 R5 归档。
---

# T4-SCHEDULE-UI

只消费已合并 cadence/reminder 合同，拥有独立 Schedule route 内容；不接管 app shell 或根导航。

Runtime acceptance tests are black-box behavioral tests：测试只调用 compiled reducer/presenter entry point 与 production-used injected ports，并只断言领域状态及 permission/scheduler/route effects；不得读取 repository/generated source、source-derived resource 或反射/反编译 compiled artifact 作为 oracle。`ScheduleScreen` 只由 `assembleDebug` 证明能与这些 typed state/effect contracts 编译接线，本卡明确不声称 Compose runtime 或像素/语义树行为已被测试。A1–A4 各至少一个 production semantic mutation 必须在测试不变时让具名 selector nonzero；receipt 记录 acceptance、selector、变异 branch/port effect、RED exit 与 mutation 前/还原后相同 SHA-256，源码文本、测试期望值或注释 mutation 无效。
