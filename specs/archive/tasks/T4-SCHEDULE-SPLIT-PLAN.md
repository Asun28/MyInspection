---
id: T4-SCHEDULE-SPLIT-PLAN
title: 将 T4-SCHEDULE 拆成可读且可独立评审的三张串行卡
depends_on: [T4-COMPLIANCE-ENGINE]
status: merged
branch: T4-SCHEDULE-SPLIT-PLAN
worktree: C:\wt\T4-SCHEDULE-SPLIT-PLAN
allow_paths:
  - specs/tasks/T4-SCHEDULE.md
  - specs/tasks/T4-SCHEDULE-SPLIT-PLAN.md
  - specs/tasks/T4-SCHEDULE-CADENCE.md
  - specs/tasks/T4-SCHEDULE-REMINDER.md
  - specs/tasks/T4-SCHEDULE-UI.md
  - docs/TASK-BOARD.md
forbid:
  - 修改任何 Android 生产代码、测试、依赖或 schema
  - 提高 R3 的 1000 行或 60000 字符预算
  - 把原 PR #208 的压缩代码原样合并
non_goals:
  - 实现 cadence、WorkManager、通知或 Compose UI
  - 删除 T4-SCHEDULE 分支或工作树中的可复用实现
acceptance:
  - "A1 original T4-SCHEDULE is retired after PR #208 proved normal formatting cannot fit the R3 budget"
  - "A2 CADENCE then REMINDER then UI form one dependency chain with disjoint exact-file allowlists"
  - "A3 each child card has an executable focused DoD and carries only its own A1-A4 acceptance subset"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; if ((Test-Path specs/tasks/T4-SCHEDULE.md) -or -not ((Test-Path specs/tasks/T4-SCHEDULE-CADENCE.md) -and (Test-Path specs/tasks/T4-SCHEDULE-REMINDER.md) -and (Test-Path specs/tasks/T4-SCHEDULE-UI.md))) { exit 1 }
dod_exit: 0
dod_assert: 原单卡退出活目录；三张子卡按 CADENCE→REMINDER→UI 串行，精确文件 allowlist 互不重叠，TASK-BOARD 同步记录 PR #208 的拆分原因。
review_gate: codex {verdict:pass}
hygiene: check-cards 校验全部活卡，DoD 额外钉住原卡退役与三张子卡同时存在。
doc_sync: TASK-BOARD 记录 split-plan 合并 OID；本规划卡 R5 归档，三张实现卡保持 todo。
---

# T4-SCHEDULE-SPLIT-PLAN

## 拆分依据

PR #208 的 R3 实测确认单卡必须靠 279–504 字符的压缩行才能进入 1000/60000 预算；恢复正常 Kotlin 格式并补齐权限、持久化、错误分类与日志测试会再次超限。按 R3 指令沿 core cadence、Android reminder boundary、Compose UI 三条文件缝拆分，不提高预算、不继续压缩。

## 串行顺序

`T4-SCHEDULE-CADENCE` → `T4-SCHEDULE-REMINDER` → `T4-SCHEDULE-UI`

三卡 exact-file allowlist 互不重叠；后卡显式依赖前卡，避免尚未合并的类型契约被复制。
