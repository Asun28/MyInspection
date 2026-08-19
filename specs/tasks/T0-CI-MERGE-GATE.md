---
id: T0-CI-MERGE-GATE
title: 在所有远端合并路径上等待候选分支 ci.yml 检查全绿
depends_on: [T0-R3-DIFF-BUDGET]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
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
non_goals:
  - 修改 ci.yml 的业务验收内容或增加新 job
  - 实现真实 diff 预算、lessons 冷热分层或状态码迁移
  - 自动修复失败的 GitHub Actions run
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/task.ps1 -SimpleMatch '[CI-GATE-PASS]') -and (Select-String -Path scripts/task.ps1 -SimpleMatch '--match-head-commit') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'CI-GATE-HEAD-MOVED'))) { exit 1 }"
dod_exit: 0
dod_assert: 自动合并与 NoAutoMerge 两条远端路径都在 R3 后、merge 或宣告人工可合并前等待候选分支 ci.yml 的全部 job；期望项必须 completed+success，missing/skipped/neutral/red/API 失败/timeout/head moved/base retarget 均 fail-closed，最终 merge 绑定过闸 head。
review_gate: codex {verdict:pass}
hygiene: 每个失败面只保留一枚最小 hermetic gh 夹具；CI gate 的期望检查解析与运行时共用单一实现
doc_sync: DEVOPS-WORKFLOW 与 DELIVERY-CHAINS 同步远端 ship 顺序、人工恢复步骤和 free/private 仓客户端硬闸
---

# T0-CI-MERGE-GATE

## 产出

选择性回填上游 v0.32/v0.37 的最终 CI gate，而不是旧的 scaffold matrix 版本。最终远端顺序为：真实 diff 预算 → push/PR → mandatory R3 → pinned-head `ci.yml` checks → base/head 复核 → merge。

## 验收重点

- `ci.yml` job 名从 `$Wt` 指向的候选树解析；PR 若改 job 名，以新树为准。
- API 分页取齐；非期望检查也不得处于失败状态。
- `-NoAutoMerge` 只能在 CI gate 通过后宣告 ready，不能把红 PR 交给人工。
- `SCAFFOLD_CI_TIMEOUT_SEC` 只作为可测试的等待上限，不是绕过开关。
- 本卡不复制上游 advisory R3，也不重新等待已退出 PR 触发的 scaffold-selftest 分片。

## 上游依据

- v0.37 的 workflow/task 成对迁移；只拿 task-side `ci.yml` 最终形态。
- 通用回写建议另见上游 issue #165；本卡只修本仓。
