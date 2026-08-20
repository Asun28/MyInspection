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
dod_command: function Test-Td9SplitContract { param([string]$Corpus, [string[]]$Needles) $cursor = -1; foreach ($needle in $Needles) { $next = $Corpus.IndexOf($needle, $cursor + 1, [System.StringComparison]::Ordinal); if ($next -lt 0) { return $false }; $cursor = $next }; return $true }; $corpus = ((Get-Content docs/TASK-BOARD.md -Raw), (Get-Content specs/tech-debt-tracker.md -Raw), (Get-Content specs/tasks/T0-DEBT-SELFTEST-SKIP-VISIBILITY.md -Raw), (Get-Content specs/tasks/T0-DEBT-SELFTEST-NOGIT-ROUTING.md -Raw), (Get-Content specs/tasks/T0-DEBT-SELFTEST-MUTATION-BUDGET.md -Raw), (Get-Content specs/tasks/T0-DEBT-SELFTEST-LOAD-STABILITY.md -Raw)) -join [Environment]::NewLine; $needles = @('| W0 | T0-DEBT-SELFTEST-FAIL-DIAGNOSTICS | 单分片与 all 汇总以稳定哨兵点名失败 shard/gate（TD9 1/5） | T0-DEBT-SELFTEST-CRITICAL-PATH |', '| W0 | T0-DEBT-SELFTEST-SKIP-VISIBILITY | 有意 skip 与前置失败裁剪进入确定性执行台账（TD9 2/5） | T0-DEBT-SELFTEST-CRITICAL-PATH + T0-LICENSE-SELFTEST-DRIFT |', '| W0 | T0-DEBT-SELFTEST-NOGIT-ROUTING | 有界 fixture mode 证明生产 seeded git-present/absent routing 与 outcome ledger（TD9 3/5） | T0-DEBT-SELFTEST-SKIP-VISIBILITY |', '| W0 | T0-DEBT-SELFTEST-MUTATION-BUDGET | parse-once 紧凑 identity inventory，消除数百份整脚本 mutation 副本（TD9 4/5） | T0-DEBT-SELFTEST-NOGIT-ROUTING |', '| W0 | T0-DEBT-SELFTEST-LOAD-STABILITY | 8.2e 用具名有界预算承受超过五秒的 runner 调度延迟（TD9 5/5） | T0-DEBT-SELFTEST-MUTATION-BUDGET |', '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`', '全部 merged + post-merge core 重放后才可 paid', '  - 在 core 内启动完整 seeded，或证明 seeded 的生产 no-git routing；该行为归 T0-DEBT-SELFTEST-NOGIT-ROUTING', '  - 建立全量 per-gate mutation 矩阵；紧凑身份清单与资源预算归 T0-DEBT-SELFTEST-MUTATION-BUDGET', 'depends_on: [T0-DEBT-SELFTEST-SKIP-VISIBILITY]', 'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture seeded-nogit-routing', 'depends_on: [T0-DEBT-SELFTEST-NOGIT-ROUTING]', 'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture skip-mutation-budget', 'depends_on: [T0-DEBT-SELFTEST-MUTATION-BUDGET]'); if (-not (Test-Td9SplitContract $corpus $needles)) { exit 1 }; foreach ($needle in $needles) { if (Test-Td9SplitContract ($corpus.Replace($needle, '')) $needles) { exit 1 } }; $boardReordered = $corpus.Replace($needles[1], '__TD9_BOARD_SWAP__').Replace($needles[2], $needles[1]).Replace('__TD9_BOARD_SWAP__', $needles[2]); if (Test-Td9SplitContract $boardReordered $needles) { exit 1 }; $trackerReordered = $corpus.Replace($needles[5], '`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-LOAD-STABILITY`'); if (Test-Td9SplitContract $trackerReordered $needles) { exit 1 }
dod_exit: 0
dod_assert: 原 skip 卡明确收回到 bounded helper 协议；生产 no-git routing 与 mutation 资源预算各有独立任务卡；TD9、全部 live 卡与 TASK-BOARD 记录同一串行顺序。
review_gate: codex {verdict:pass}
hygiene: dod_command 对 canonical corpus 做有序精确匹配，并在内存中逐项删除每个必要 needle、交换相邻 TASK-BOARD 行及调换 tracker 链顺序；任一 mutant 存活即非零；两个实现卡共享 selftest 因而必须串行。
doc_sync: 本规划卡合并后标 merged；TD9 保持 carded，直到全部子卡与 post-merge core 重放完成。
---

# T0-DEBT-SELFTEST-SPLIT-PLAN

## 拆分依据

PR #33 的 R3 实测指出两类不同交付单元：生产 no-git 路由的行为证明，以及 mutation harness 的资源确定性。继续塞回原卡会同时扩大行为面与验证成本，因此先把原卡收回到 skip 协议本身，再串行偿还两项余债。

## 串行顺序

`T0-DEBT-SELFTEST-SKIP-VISIBILITY` → `T0-DEBT-SELFTEST-NOGIT-ROUTING` → `T0-DEBT-SELFTEST-MUTATION-BUDGET` → `T0-DEBT-SELFTEST-LOAD-STABILITY`

四卡均修改 `scripts/selftest.ps1`，执行宽度固定为 1。
