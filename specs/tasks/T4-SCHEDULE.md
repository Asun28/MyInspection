---
id: T4-SCHEDULE
title: 巡检排程：13 周节奏提醒 + 本地通知
depends_on: [T4-COMPLIANCE-ENGINE]
parallelizable_with: [T4-NOTICES, T5-RETENTION]
status: todo
branch: T4-SCHEDULE
worktree: C:\wt\T4-SCHEDULE
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/schedule/
  - android/core/src/test/kotlin/nz/myinspection/core/schedule/
  - android/app/src/main/kotlin/nz/myinspection/app/feature/schedule/
forbid:
  - 把提醒节奏当法律限制展示（13 周是提醒节奏；法律上限在合规引擎——两层别混，需求 §3）
non_goals:
  - 日历集成/外部日历写入（v1.1）；自定义节奏 UI（默认 13 周，常量配置即可）
dod_command: cmd /c android\gradlew.bat --offline --no-daemon -q :core:test --tests "nz.myinspection.core.schedule.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat --offline --no-daemon -q :app:assembleDebug
dod_exit: 0
dod_assert: 下次应巡建议 = 上次同类型 finalize 后 +13 周（按物业/类型算，测试绿）；建议时刻先过合规引擎再入提醒；到期本地通知（AlarmManager/WorkManager 任一，重启后存活）；assembleDebug 绿
review_gate: codex {verdict:pass}
hygiene: 冗余测试经 mutation-survivor 剪枝（R4）
doc_sync: TASK-BOARD 备注（R5）
---

# T4-SCHEDULE

## 产出
`core/schedule`（下次应巡计算，纯函数）+ 提醒调度薄壳 + 物业列表页「距下次 Routine」徽标。

## 上下文包（执行模型必读）
- 计算：每物业每类型独立（ROUTINE 13 周默认；ANNUAL 12 个月）；无历史→「建议排首检」空态。建议值只是提醒——用户真正建巡检时合规引擎才是闸。
- 通知：POST_NOTIFICATIONS 运行时权限（API 33+）；提醒文案双语 key 复用 notice 文案表机制；点通知深链到该物业。
- 重启存活：BOOT_COMPLETED 重挂或用 WorkManager 周期扫描（实现者二选一，卡不锁实现，测试锚定「重启后提醒仍会来」的调度登记态）。

## 验收 / 执行建议
dod 见 front-matter。首选 GPT-5.6 Terra · medium；备选 DeepSeek V4 Pro。难度 S。
