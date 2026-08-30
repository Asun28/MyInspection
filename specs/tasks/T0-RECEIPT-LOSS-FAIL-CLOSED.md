---
id: T0-RECEIPT-LOSS-FAIL-CLOSED
title: receipt 丢失或不自洽时停止第二套 review/CI/merge 恢复旁路
depends_on: [T0-CI-HARDENING-MATRIX]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-RECEIPT-LOSS-FAIL-CLOSED
worktree: C:\wt\T0-RECEIPT-LOSS-FAIL-CLOSED
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 在 receipt-loss 分支手抄 review.ps1 -PostStatus、GitHub API 轮询或 gh pr merge 管线
  - 使用 -SkipRed、cleanup、rebase 或历史改写绕过 T35 receipt 四谓词
  - 改动候选 CI 正常 ship 控制流或降低既有闸
non_goals:
  - 自动重建或伪造丢失的 receipt
  - 修改 check-scope.ps1 的范围判定算法
  - 改 CI job、R3 配置、预算阈值或状态码
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/task.ps1 -SimpleMatch '人工升级') -and (Select-String -Path scripts/task.ps1 -SimpleMatch 'T35 receipt') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'receipt-loss 已 push fail-closed') -and (Select-String -Path docs/DEVOPS-WORKFLOW.md -SimpleMatch '无法恢复 receipt 就保持 PR 未合并'))) { exit 1 }"
dod_exit: 0
dod_assert: watershed 后 receipt 缺失、损坏或不自洽时，重跑 ship 在 RED/receipt 边界非零退出，只保留 worktree/branch/PR/evidence 并指向人工升级；review、status、candidate CI、merge、cleanup 哨兵全缺。只有恢复一枚通过 T35 四谓词的 receipt 后才能重跑同一条 ship；否则 PR 保持未合并。
review_gate: codex {verdict:pass}
hygiene: 保留一枚真实已 push receipt-loss fixture 与文档 source-contract 变异；诊断命令可读状态但不得拥有合并授权
doc_sync: DEVOPS-WORKFLOW 的 S2/S9/TD85 与 DELIVERY-CHAINS 只描述诊断、人工升级和 normal ship-only 恢复
---

# T0-RECEIPT-LOSS-FAIL-CLOSED

## 轻量计划

1. 将 task.ps1 的 post-watershed receipt-loss 指引改为 fail-closed 人工升级，保留未推送 reset-safe 路由。
2. 把 15q/15r/15g/15s 与 T37-REMOTEMX/4 从“手工直连合并”反转为“零下游消费”。
3. 删除运维文档中的第二套 review/API/merge 配方，只保留诊断性确定闸与状态采集。
4. 对陈旧措辞、可执行旁路、缺少未合并结局与 receipt 恢复条件做删除/插入变异。
5. 跑 workflow、seeded-remote、mutation、SizeOnly 与最终总验收。

## 可证伪验收

- 真 receipt 先存在，再删除；已 push 重跑必须命中 TD85-RESUME/人工升级且非零。
- `review-invoked`、`status-posted`、candidate CI、merge-attempted、merge-reached 与 cleanup token 全部缺失。
- 文档 receipt-loss 块含人工升级、恢复可验证 T35 receipt、否则保持未合并；出现可执行 PostStatus/API/merge 即失败。
- 未 push 且 reset-safe 的既有精确 evidence.redSha 路由不回归；remote-ahead/diverged 不执行危险 reset。
