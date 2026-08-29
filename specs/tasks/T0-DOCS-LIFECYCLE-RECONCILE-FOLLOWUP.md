---
id: T0-DOCS-LIFECYCLE-RECONCILE-FOLLOWUP
title: Reconcile remaining design and lessons card lifecycles
depends_on: [T0-DOCS-LIFECYCLE-RECONCILE]
parallelizable_with: []
status: todo
branch: T0-DOCS-LIFECYCLE-RECONCILE-FOLLOWUP
worktree: C:\wt\T0-DOCS-LIFECYCLE-RECONCILE-FOLLOWUP
allow_paths:
  - specs/tasks/
  - specs/archive/tasks/
  - specs/archive/cards-index.md
  - docs/TASK-BOARD.md
forbid:
  - 改生产代码、schema、依赖、权威设计/经验正文或任何闸门行为
  - 改清单卡契约；除 status: merged 外不得改变归档卡内容
  - 归档本卡清单之外的任何卡片
  - 删除远端分支、其他 worktree 或其他会话资产
non_goals:
  - 关闭 T3-REPORT-COMPOSER-R3-CLOSURE 或 TD139
  - 新建根 README.md 或改写历史计划记录
  - 修改 lessons/design 源内容；本卡只同步已落地工作的生命周期与入站路径
acceptance:
  - "A1 清单内 24 张卡均有可追溯到 master 的实现提交；它们从 specs/tasks 移到 specs/archive/tasks，唯一内容变化是 status: merged"
  - "A2 specs/archive/cards-index.md 由 archive.ps1 重建且 -CheckCardsIndex 通过；登记本卡后的活动卡数 58 减 24 得 34"
  - "A3 docs/TASK-BOARD.md 的 design metadata/journeys/components 与 lessons 四行改为带 master SHA 和 PR 的 merged 终态"
  - "A4 两张仍活动的 lessons fixture 卡将对 T0-RECONCILE-LESSONS 的 live path 引用改指 archive path；每张正好两处且仅路径改变"
  - "A5 check-cards、lessons check、archive cards-index check、verify 与适用 selftest 分片通过；真实 diff 预算不超过 60000 字符"
dod_command: pwsh -NoProfile -Command "if ((Get-ChildItem specs/tasks/T*.md).Count -ne 34) { exit 1 }; if ((Test-Path specs/tasks/T0-RECONCILE-DESIGN-METADATA.md) -or (-not (Test-Path specs/archive/tasks/T0-RECONCILE-DESIGN-METADATA.md)) -or (Test-Path specs/tasks/T0-RECONCILE-LESSONS.md) -or (-not (Test-Path specs/archive/tasks/T0-RECONCILE-LESSONS.md))) { exit 1 }; exit 0"
dod_exit: 0
dod_assert: 24 张已实现 design/lessons 卡仅改 status 后归档，活动卡总数为 34，四个 board 终态与两张活动 fixture 入链同步
review_gate: codex {verdict:pass}
hygiene: 用精确 24-card manifest 与逐卡基线比较防止越界；archive.ps1 机械重建索引
doc_sync: R5 把本卡标 merged 后归档并重建 cards-index
---

# T0-DOCS-LIFECYCLE-RECONCILE-FOLLOWUP

## Why this card exists

The first lifecycle reconciliation was split at the repository's enforced 60,000-character real-diff budget. This bounded
follow-up closes the remaining verified design and lessons families, repairs their four current Task Board projections, and
retargets the only two still-active fixture cards that directly reference the lessons card's live path.

## Exact archive manifest

Design family (17): `T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE`,
`T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE`, `T0-RECONCILE-DESIGN-COMPONENT-SPLIT`,
`T0-RECONCILE-DESIGN-COMPONENTS`, `T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE`,
`T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE`, `T0-RECONCILE-DESIGN-FOUNDATION-R3-PAIR-FIXTURE`,
`T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE`, `T0-RECONCILE-DESIGN-FOUNDATIONS`,
`T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE`, `T0-RECONCILE-DESIGN-JOURNEY-FIXTURE`,
`T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE`, `T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE`,
`T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE`, `T0-RECONCILE-DESIGN-JOURNEYS`,
`T0-RECONCILE-DESIGN-METADATA`, `T0-RECONCILE-DESIGN-METADATA-FIXTURE`.

Lessons family (7): `T0-RECONCILE-LESSONS`, `T0-RECONCILE-LESSONS-FINAL-FIXTURE`,
`T0-RECONCILE-LESSONS-FIXTURE`, `T0-RECONCILE-LESSONS-R3-FIXTURE`,
`T0-RECONCILE-LESSONS-R3-PATTERN-FIXTURE`, `T0-RECONCILE-LESSONS-VALIDATOR-DOD-FIXTURE`,
`T0-RECONCILE-LESSONS-VALIDATOR-FIXTURE`.

## Evidence rule

Each archived card must compare with its pre-card `master` form as an exact path move plus one `status` line change. Current
Task Board receipts use the substantive family implementation commits: metadata PR #156 / `281dff2e`, journeys PR #163 /
`94b92146`, components PR #171 / `e7ce52cc`, and lessons PR #150 / `3bcd1cc9`.
