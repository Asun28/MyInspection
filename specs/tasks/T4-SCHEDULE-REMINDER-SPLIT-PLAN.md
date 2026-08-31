---
id: T4-SCHEDULE-REMINDER-SPLIT-PLAN
title: 将超限提醒卡拆为 delivery 与 scheduler 两张可读串行卡
depends_on: [T4-SCHEDULE-CADENCE]
status: todo
branch: T4-SCHEDULE-REMINDER-SPLIT-PLAN
worktree: C:\wt\T4-SCHEDULE-REMINDER-SPLIT-PLAN
allow_paths:
  - specs/tasks/T4-SCHEDULE-REMINDER.md
  - specs/tasks/T4-SCHEDULE-REMINDER-SPLIT-PLAN.md
  - specs/tasks/T4-SCHEDULE-REMINDER-DELIVERY.md
  - specs/tasks/T4-SCHEDULE-REMINDER-SCHEDULER.md
  - specs/tasks/T4-SCHEDULE-UI.md
  - docs/TASK-BOARD.md
forbid:
  - 修改任何 Android 生产代码、测试、依赖或 schema
  - 提高 R3 的 1000 行或 60000 字符预算
  - 整体 cherry-pick、force-push 或合并 PR #212 的压缩提交链
non_goals:
  - 实现 delivery、scheduler、权限恢复 UI 或根导航
  - 在两张实现卡完成抽取前删除 PR #212 的只读证据
acceptance:
  - "A1 retire unmerged T4-SCHEDULE-REMINDER and record PR #212 exact head a0ed8da4ed2f374a48ddeef9de146f9be2696d7d, green CI, and three-angle Sol R3 blockers"
  - "A2 DELIVERY then SCHEDULER then UI form one dependency chain with disjoint exact-file allowlists"
  - "A3 successor DoDs keep the 1000/60000 gates and reject typealiases, semicolon packing, long lines, dependency/schema changes, and source-string substitute tests"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; $required = @('specs/tasks/T4-SCHEDULE-REMINDER-DELIVERY.md','specs/tasks/T4-SCHEDULE-REMINDER-SCHEDULER.md','specs/tasks/T4-SCHEDULE-UI.md'); if ((Test-Path 'specs/tasks/T4-SCHEDULE-REMINDER.md') -or @($required | Where-Object { -not (Test-Path $_) }).Count -ne 0) { exit 1 }; $ui = Get-Content -Raw 'specs/tasks/T4-SCHEDULE-UI.md'; $board = Get-Content -Raw 'docs/TASK-BOARD.md'; if ($ui -notmatch 'depends_on:\s*\[T4-SCHEDULE-REMINDER-SCHEDULER\]' -or $board -notmatch '#212' -or $board -notmatch 'a0ed8da4ed2f374a48ddeef9de146f9be2696d7d') { exit 1 }
dod_exit: 0
dod_assert: 原 reminder 卡退出活目录；DELIVERY→SCHEDULER→UI 串行且 exact-file allowlist 互斥；TASK-BOARD 钉住 PR #212 的精确未合并证据。
review_gate: codex {verdict:pass}
hygiene: check-cards 校验全部活卡；拆分卡只改元数据，不借拆分放宽任何质量闸。
doc_sync: TASK-BOARD 记录 split-plan 合并 OID；本规划卡 R5 归档，两张实现卡保持 todo。
---

# T4-SCHEDULE-REMINDER-SPLIT-PLAN

## Light Plan Forge 结论

PR #212 在精确 head `a0ed8da4ed2f374a48ddeef9de146f9be2696d7d` 已通过 focused DoD、verify 与 CI，但代码靠 12 个不透明 typealias、39 行分号拼接和精确 1000 行才入闸。三次独立 Sol Max 预审进一步发现 callback 清理、活动交付状态、损坏存储、极值 delay 与 mutation-survivor 缺口；继续压缩会重复违反原拆卡目的。

## 冻结决策

`T4-SCHEDULE-REMINDER-DELIVERY` → `T4-SCHEDULE-REMINDER-SCHEDULER` → `T4-SCHEDULE-UI`

Delivery 独占身份、持久化、诊断、worker 与通知边界；Scheduler 独占 WorkRequest、注册并发与 callback 边界。两卡都从已合并 master 重新 TDD，不整体搬运 PR #212。任意持久化协议变化留在应用私有 SharedPreferences，不修改数据库 schema、依赖、启动导航、精确闹钟或 boot receiver。

## 可证伪假设与非目标

- WorkManager 2.11.2 继续是进程重启后的执行真相源；host JVM 只验证本仓拥有的适配与状态合同。
- 全新 app-data store 是合法 `MISSING`；曾初始化却丢失/损坏的 store 必须 fail closed。
- 本链不开放用户入口；UI 卡仅在 scheduler 合并后启动，因此中间卡不会暴露半成品路径。
