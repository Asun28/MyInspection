---
id: T0-DEBT-SELFTEST-CRITICAL-PATH
title: 阶段性偿还 TD9：完整 scaffold selftest 退出 PR 合并关键路径
depends_on: []
status: todo
branch: T0-DEBT-SELFTEST-CRITICAL-PATH
worktree: C:\wt\T0-DEBT-SELFTEST-CRITICAL-PATH
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - scripts/selftest.ps1
  - CLAUDE.md
  - docs/LOOP-ENGINEERING.md
  - specs/tech-debt-tracker.md
forbid:
  - 弱化或移除 ci.yml 的 push/pull_request 产品验证
  - 删除 scaffold selftest 的任一 shard、OS 或既有断言
  - 新增动态路由器、路径分类器、第三方 Action 或另一层聚合器
  - 改产品代码、R3 裁决语义或任务卡 DoD 语义
non_goals:
  - 重构 scripts/selftest.ps1 或修复 TD9 的全部日志与负载抖动
  - 改写 2 OS × 3 shard 完整矩阵
  - 让 post-merge selftest 失败阻塞已合并提交
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard core
dod_exit: 0
dod_assert: ci.yml 仍在 main/master 的 push 与 pull_request 运行；scaffold-selftest.yml 只在 main/master push 与 workflow_dispatch 运行且明确拒绝 pull_request；完整 2 OS × 3 shard 矩阵保持不变；仅把 pull_request 触发加回 scratch workflow 的单点变异会被 8.2d 指名拒绝
review_gate: codex {verdict:pass}
hygiene: 只改触发契约、对应自测与两处权威流程说明；不引入路由/聚合抽象（R4）
doc_sync: TD9 保持 open，偿还指针记录本卡已消除 PR 关键路径耦合；剩余可诊断性与 post-merge load-flake 继续单独偿还（R5）
---

# T0-DEBT-SELFTEST-CRITICAL-PATH

## 诊断
PR #20 的产品 CI 始终通过，但 16 次 scaffold-selftest 运行中出现 4 次失败和 1 次取消；失败混合了本卡行为、测试夹具及无关历史闸。根因不是缺少更多调度逻辑，而是把完整仓库 harness 的 2 OS × 3 shard 矩阵绑成每次 PR 提交的合并关键路径。

## 产出
PR 只由产品 `ci.yml`、任务卡 DoD 与 R3 守门。完整 scaffold selftest 保留原矩阵，在默认分支合并后自动运行，并可手动运行，用作 harness canary。

## 验收边界
- 先在 `scripts/selftest.ps1` 写触发契约并观察当前 `pull_request` 配置产生目标 RED，再改 workflow。
- `scaffold-selftest.yml` 不得出现 `pull_request`；保留 `push` 的 `main/master` 分支与现有路径过滤，并保留 `workflow_dispatch`。
- 8.2e 的完整矩阵、runner、`-Shard` 与 lint provisioning 契约原样保留。
- 本卡只解除 PR 阻塞；TD9 的失败点名、skip 可见性与负载抖动仍是后续债项。
