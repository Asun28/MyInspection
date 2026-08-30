---
id: T0-CI-HARDENING-MATRIX
title: 用可证伪矩阵锁死候选 CI 的身份、分页、deadline 与最终快照
depends_on: [T0-CI-MERGE-GATE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-CI-HARDENING-MATRIX
worktree: C:\wt\T0-CI-HARDENING-MATRIX
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 修改 ci.yml job 集或业务验收内容
  - 降低 mandatory R3、真实 diff 预算或现有本地确定性闸
  - 用固定 PR 号、run id、head SHA 或只搜错误文本的 vacuous fixture
non_goals:
  - receipt-loss 恢复策略；由后继 T0-RECEIPT-LOSS-FAIL-CLOSED 承接
  - 自动重跑、取消或修复 GitHub Actions
  - 把 scaffold-selftest.yml 放回 PR 关键路径
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/selftest.ps1 -SimpleMatch 'T37-CIGATE/API-CONTRACT') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'T37-CIGATE/WORKFLOW-BINDING') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'T37-CIGATE/JOBS-DRIFT') -and (Select-String -Path docs/DEVOPS-WORKFLOW.md -SimpleMatch 'candidate CI'))) { exit 1 }"
dod_exit: 0
dod_assert: 自动合并与 NoAutoMerge 都只接受候选树 job 集、当前 PR、reviewed head、候选 ci.yml 的唯一 workflow run 与其返回 run id；missing/skipped/neutral/red、未知 job key、分页/shape/total 漂移、API/tree timeout、base/head 移动均在任何 merge 前 fail-closed，并由命中目标 endpoint/sentinel 的正反夹具证明。
review_gate: codex {verdict:pass}
hygiene: 数据驱动 fixture 必须记录 endpoint、PR、head、run id、jobs 与事件顺序；每个负例有对应正例或变异，禁止更早的通用 API 错误冒充命中
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 同步候选 CI 身份、deadline、最终 exact-head/base 快照和 NoAutoMerge 契约
---

# T0-CI-HARDENING-MATRIX

## 轻量计划

1. 注册 CI gate IDs 与无 git/非 Windows 路由，扩展共享远端 fixture 的可观测哨兵。
2. 对 check-runs、workflow-runs、jobs 三个 endpoint 覆盖数组形态、严格 total_count、有效分页与 target-reaching 失败。
3. 证明 reviewed local SHA、PR number/head、workflow path/event/PR association、返回 run id 与最终 merge 参数逐层绑定。
4. 证明单一 wall-clock deadline 杀进程树，重试 sleep 只用剩余预算，最终 exact-head/base 快照后才决策。
5. 同步两份权威运维文档并跑 workflow、seeded-remote、mutation 与 SizeOnly。

## 可证伪验收

- 正常与 alternate identity 两条绿路使用不同 PR/run id；stub 拒绝硬编码。
- workflow pending/failure 在 jobs 前阻断；晚到 unrelated red 在最终扫描阻断。
- 空数组配 missing/null total、对象冒充数组、跨页 total 漂移分别命中实际 endpoint 哨兵。
- review 中移动本地 HEAD、CI 中前移 base、最终快照 retarget/head moved 均不触发 merge。
- 超时夹具必须证明 API 已启动、子进程树未留下完成哨兵，且墙钟小于配置上限加明确清理余量。
