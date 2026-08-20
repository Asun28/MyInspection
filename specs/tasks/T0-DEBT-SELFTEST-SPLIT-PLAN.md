---
id: T0-DEBT-SELFTEST-SPLIT-PLAN
title: 将 TD9 skip 可见性余项拆成有界串行卡
depends_on: []
status: todo
branch: T0-DEBT-SELFTEST-SPLIT-PLAN
worktree: C:\wt\T0-DEBT-SELFTEST-SPLIT-PLAN
allow_paths:
  - specs/tasks/T0-DEBT-SELFTEST-SPLIT-PLAN.md
  - specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md
  - specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md
  - specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md
  - specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md
  - specs/tech-debt-tracker.md
  - docs/TASK-BOARD.md
forbid:
  - 修改 scripts/selftest.ps1 或启动任何 selftest 分片
  - 把后续卡标成并行，或宣称 TD9 已偿还
  - 用缩窄卡片掩盖 PR #33 已引入且仍可达的缺陷
non_goals:
  - 实现 no-git routing 夹具或 mutation 预算收敛
  - 合并或关闭 PR #33
dod_command: pwsh -NoProfile -Command "if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md -SimpleMatch 'depends_on: [T0-DEBT-SELFTEST-SKIP-VISIBILITY]')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md -SimpleMatch 'depends_on: [T0-DEBT-SELFTEST-NOGIT-ROUTING]')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md -SimpleMatch 'depends_on: [T0-DEBT-SELFTEST-MUTATION-BUDGET]')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md -SimpleMatch '本卡只保留 skip primitive、机器台账、摘要与 bounded helper 互斥证明')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md -SimpleMatch '生产 seeded git-present/absent routing')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md -SimpleMatch 'parse-once 的紧凑身份清单')) { exit 1 }; if (-not (Select-String -LiteralPath docs/TASK-BOARD.md -SimpleMatch 'T0-DEBT-SELFTEST-NOGIT-ROUTING')) { exit 1 }; if (-not (Select-String -LiteralPath docs/TASK-BOARD.md -SimpleMatch 'T0-DEBT-SELFTEST-MUTATION-BUDGET')) { exit 1 }; if (-not (Select-String -LiteralPath specs/tech-debt-tracker.md -SimpleMatch 'T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY')) { exit 1 }"
dod_exit: 0
dod_assert: 原 skip 卡明确收回到 bounded helper 协议；生产 no-git routing 与 mutation 资源预算各有独立任务卡；TD9、全部 live 卡与 TASK-BOARD 记录同一串行顺序。
review_gate: codex {verdict:pass}
hygiene: dod_command 每条 Select-String 对应一项声明；删除任一依赖、carve-out、owner、TASK-BOARD 行或 tracker 顺序即非零；两个实现卡共享 selftest 因而必须串行。
doc_sync: 本规划卡合并后标 merged；TD9 保持 carded，直到全部子卡与 post-merge core 重放完成。
---

# T0-DEBT-SELFTEST-SPLIT-PLAN

## 拆分依据

PR #33 的 R3 实测指出两类不同交付单元：生产 no-git 路由的行为证明，以及 mutation harness 的资源确定性。继续塞回原卡会同时扩大行为面与验证成本，因此先把原卡收回到 skip 协议本身，再串行偿还两项余债。

## 串行顺序

`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`

四卡均修改 `scripts/selftest.ps1`，执行宽度固定为 1。
