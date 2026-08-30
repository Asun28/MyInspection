---
id: T0-CI-MERGE-GATE
title: 在所有远端合并路径上等待候选分支 ci.yml 检查全绿
depends_on: [T0-R3-DIFF-BUDGET]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-CI-MERGE-GATE
worktree: C:\wt\T0-CI-MERGE-GATE
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - .github/workflows/ci.yml
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
forbid:
  - 把 scaffold-selftest.yml 的分片重新放回 PR 合并关键路径
  - 取消本仓 mandatory R3、降低现有确定性闸或依赖服务端规则集兜底
  - 从基线树而非合并候选树派生期望检查集
  - 在本卡塞入完整 API/身份/分页 mutation 矩阵；该证明属于后继 T0-CI-HARDENING-MATRIX
non_goals:
  - 修改 ci.yml 的业务验收内容或增加新 job
  - 收口 receipt-loss 恢复旁路；该行为属于 T0-RECEIPT-LOSS-FAIL-CLOSED
  - 自动修复失败的 GitHub Actions run
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/task.ps1 -SimpleMatch '[CI-GATE-PASS]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '--match-head-commit') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'basic-red') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'ci-jobs-consumed'))) { exit 1 }"
dod_exit: 0
dod_assert: 远端 ship 在 R3 后从候选树解析 ci.yml jobs，钉定 reviewed PR head，候选 check 红灯须在 merge 前 fail-closed；绿灯路径必须消费候选 workflow run/jobs，最终 merge 使用 --match-head-commit 绑定过闸 head。
review_gate: codex {verdict:pass}
hygiene: 每个失败面只保留一枚最小 hermetic gh 夹具；CI gate 的期望检查解析与运行时共用单一实现
doc_sync: 本卡只同步 task.ps1 可执行帮助；DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 的完整 CI/恢复契约由后继 T0-CI-HARDENING-MATRIX 与 T0-RECEIPT-LOSS-FAIL-CLOSED 分段承接
---

# T0-CI-MERGE-GATE

## 产出

选择性回填上游 v0.32/v0.37 的 CI gate 运行时，而不是旧的 scaffold matrix 版本。最终远端顺序为：真实 diff 预算 → push/PR → mandatory R3 → pinned-head `ci.yml` checks → base/head 复核 → merge。本卡只交付运行时和一红一绿的最小行为证明；完整 mutation 矩阵由后继卡承接，避免超过 R3 的 60000 字符预算。

## 验收重点

- `ci.yml` job 名从 `$Wt` 指向的候选树解析；PR 若改 job 名，以新树为准。
- 红 candidate check 必须在 workflow/jobs/merge 之前阻断；绿路径必须真实消费候选 workflow run/jobs 后才触达 merge。
- 基本绿路的合并调用必须带当前 reviewed head 的 `--match-head-commit`，并绑定 fixture 实际创建/复用的 PR。
- R3 前后 local/PR head mismatch、完整 timeout、分页、API shape、workflow identity、base/head TOCTOU 与 `-NoAutoMerge` 证明属于 `T0-CI-HARDENING-MATRIX`。
- 本卡不复制上游 advisory R3，也不重新等待已退出 PR 触发的 scaffold-selftest 分片。

## 上游依据

- v0.37 的 workflow/task 成对迁移；只拿 task-side `ci.yml` 最终形态。
- 通用回写建议另见上游 issue #165；本卡只修本仓。
