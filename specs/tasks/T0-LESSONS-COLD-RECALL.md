---
id: T0-LESSONS-COLD-RECALL
title: 让一次性 lessons 可安全归冷且仍能统一检索
depends_on: []
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: [T0-HARNESS-SUBTRACTION-PROTOCOL]
status: todo
branch: T0-LESSONS-COLD-RECALL
worktree: C:\wt\T0-LESSONS-COLD-RECALL
allow_paths:
  - scripts/lessons.ps1
  - scripts/archive.ps1
  - scripts/selftest.ps1
  - docs/LESSONS.md
  - specs/archive/README.md
forbid:
  - 复制上游具体 lesson 内容、归档结果或按固定数量搬运本仓条目
  - 自动归档 must/ondemand、当前最大 ID 或被常驻 CLAUDE 文件引用的条目
  - 新写第二套搬运器绕过 archive.ps1 的幂等和 fail-closed 保护
non_goals:
  - 降级 L165/L196
  - 删除任何 lesson 或改变 Next-Id 单调性
  - 同卡修改 task/review/CI merge 行为
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-ARCHIVE-DRYRUN]') -and (Select-String -Path scripts/lessons.ps1 -SimpleMatch '[archived]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'lessons-archive.md'))) { exit 1 }"
dod_exit: 0
dod_assert: archive -DryRun 只选择 tier=ledger、recurrence=1、非最大 ID、且未被 CLAUDE.md/CLAUDE.template.md 引用的条目；真实执行复用 archive.ps1 -LessonIds；search 横跨热账本与冷库并标记 archived；check/gate16 的定义 ID 集合为两库并集，bump/promote 命中冷项时给出移回热区的明确修法。
review_gate: codex {verdict:pass}
hygiene: 用 hermetic RepoRoot 夹具证明候选、排除、dry-run 零写入、幂等、冷检索和 ID 并集；不把当前约 160 个候选数写死进测试
doc_sync: LESSONS 与 archive README 同步热/冷职责、检索保证和人工归档边界
---

# T0-LESSONS-COLD-RECALL

## 产出

选择性回填上游 v0.35 的 selector、cold search 和 ID union，复用本仓已经存在的 `archive.ps1 -LessonIds` 搬运器。

## 当前基线

只读审计时本仓有 213 条 lesson、LEDGER 约 273 KB；按候选规则约 160 条可进入冷库。该数字只是容量证据，不是稳定验收值，实施时必须重新计算。

## 资源冲突

本卡与 `T0-R3-DIFF-BUDGET`、`T0-CI-MERGE-GATE`、后续状态码卡都写 `scripts/selftest.ps1`。它没有业务硬依赖，但这些卡不得同时向同一基线合并；执行器须串行占用该文件或在合并前重放完整验收。
