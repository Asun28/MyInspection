---
id: T0-DOCS-LIFECYCLE-RECONCILE
title: Reconcile merged-card lifecycle and documentation workflow surfaces
depends_on: []
parallelizable_with: []
status: todo
branch: T0-DOCS-LIFECYCLE-RECONCILE
worktree: C:\wt\T0-DOCS-LIFECYCLE-RECONCILE
allow_paths:
  - specs/tasks/
  - specs/archive/tasks/
  - specs/archive/cards-index.md
  - docs/TASK-BOARD.md
  - docs/DELIVERY-CHAINS.md
  - docs/lessons/LEDGER.md
  - .claude/skills/task-loop/SKILL.md
  - CLAUDE.md
forbid:
  - 改生产代码、schema、依赖或任何闸门行为
  - 改卡片契约；本卡只允许把清单内卡片的 status 从 todo/in-progress 改成 merged 并原样移入 archive
  - 归档本卡清单以外的任何卡片
  - 删除远端分支、worktree 或其他会话资产
non_goals:
  - 新建根 README.md
  - 实现 acceptance 字段机检或修改 T0-CARD-ACCEPTANCE-FIELD
  - 清理远端分支或本地 worktree
  - 重写历史性计划、发现或进度记录
  - 归档 17 张 design-reconciliation 卡与 7 张 lessons-reconciliation 卡，或修复两张 lessons fixture 活动卡中的超长 dod_command 路径（24-card follow-up 另卡处理，避免本卡超过真实 diff 预算）
  - 关闭 T3-REPORT-COMPOSER-R3-CLOSURE 或 TD139（需 focused report DoD 与吸收实现收据，另卡处理）
acceptance:
  - "A1 以下 27 张卡均有可追溯到 master 的实现提交；它们从 specs/tasks 移到 specs/archive/tasks，唯一内容变化是 status: merged"
  - "A2 specs/archive/cards-index.md 由 archive.ps1 重建，-CheckCardsIndex 通过；活动卡计数从 84 加本卡后减 27，得到 58"
  - "A3 docs/TASK-BOARD.md 中 16 个本批次条目与 9 个已归档但仍写等待/评审的条目均改成带 PR 或提交证据的 merged 终态"
  - "A4 CLAUDE.md、docs/DELIVERY-CHAINS.md、.claude/skills/task-loop/SKILL.md 三处 R1-R5/ship 摘要均明确包含 push 前的真实 diff 预算闸"
  - "A5 docs/lessons/LEDGER.md 对 T0-R3-DIFF-BUDGET 的 live path 引用改指 specs/archive/tasks；不得编辑冻结 archive 历史"
  - "A6 check-cards、lessons check、archive cards-index check 与适用的 verify/selftest 分片均通过"
dod_command: pwsh -NoProfile -Command "if ((Get-ChildItem specs/tasks/T*.md).Count -ne 58) { exit 1 }; if ((Test-Path specs/tasks/T0-LESSONS-COLD-RECALL.md) -or (-not (Test-Path specs/archive/tasks/T0-LESSONS-COLD-RECALL.md)) -or (Test-Path specs/tasks/T4-COMPLIANCE-ENGINE.md) -or (-not (Test-Path specs/archive/tasks/T4-COMPLIANCE-ENGINE.md))) { exit 1 }; exit 0"
dod_exit: 0
dod_assert: 27 张有实现证据的卡仅改 status 后归档，活动卡总数为 58；本批权威文档面与入链引用同步完成
review_gate: codex {verdict:pass}
hygiene: 用精确 27-card manifest 与逐卡 base 对比防止越界；保留历史叙述，只追加或改写当前终态
doc_sync: 本卡本体即 docs reconciliation；R5 归档本卡并重建 cards-index
---

# T0-DOCS-LIFECYCLE-RECONCILE

## Why this card exists

The repository-wide documentation audit found 51 cards still marked active after their changes had already landed on `master`.
This first budget-bounded batch reconciles 27 of them. It also fixes stale delivery-state rows, three shortened ship summaries
that omit the enforced real-diff budget, and one non-archive link to a card in this batch. These are one lifecycle repair because
the card moves and their inbound documentation must remain atomic.

## Exact archive manifest

`T0-LESSONS-CAP-CORE-SPLIT`, `T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT`, `T0-LESSONS-CAP-TRIAGE-SPLIT`,
`T0-LESSONS-CAP-UNIT`, `T0-LESSONS-COLD-RECALL`, `T0-LESSONS-TIER1-CUT`, `T0-LICENSE-CI-INTEGRATION`,
`T0-LOCAL-RECONCILE-REGISTER`, `T0-R3-DIFF-BUDGET`, `T0-RECONCILE-DATA-AUTHORITY`,
`T0-RECONCILE-ROADMAP-INDEX`, `T0-RECONCILE-T1-SECURITY-CARDS`, `T0-RECONCILE-T5-DIAGNOSTIC-CARDS`,
`T0-RECONCILE-UI-CAPTURE`, `T0-RECONCILE-UI-COVERAGE`, `T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE`,
`T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE`, `T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE`,
`T0-RECONCILE-UI-NOTICE-SCHEDULE`, `T0-RECONCILE-UI-OFFLINE-OPERATIONS`, `T0-SCAFFOLD-CI-HOTFIX`,
`T0-SCAFFOLD-FLEET-LOOP`, `T0-SCAFFOLD-SYNC-045`, `T0-TRIAGE-EVIDENCE-CASE-REGISTER`,
`T0-TRIAGE-EVIDENCE-SCOPE-REGISTER`, `T3-REPORT-COMPOSER`, `T4-COMPLIANCE-ENGINE`.

## Evidence rule

For 26 cards, the matching implementation PR is merged directly into `master`. `T0-RECONCILE-UI-OFFLINE-OPERATIONS` is the one
documented exception: PR #178 merged into the integration branch, and the identical four-file result reached `master` in
PR #179 (`5235ffe4`). The implementation must compare each archived file with the pre-card `master` version and accept only
the single `status` line change plus the path move.
