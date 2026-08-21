---
id: T0-LESSONS-COLD-RECALL-R3-CLOSURE
title: 让 lesson 冷存选择器只信规范 meta 行
depends_on: [T0-LESSONS-COLD-RECALL]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-LESSONS-COLD-RECALL-R3-CLOSURE
worktree: C:\wt\T0-LESSONS-COLD-RECALL-R3-CLOSURE
allow_paths:
  - scripts/lessons.ps1
  - scripts/selftest.ps1
forbid:
  - 在原 PR #51 继续第 3 轮 R3
  - 重开已闭合的 LessonsOnly 旁域隔离、冷检索、ID 并集或幂等证明
  - 复制真实 lesson 内容、实际归档结果或当前候选数量
non_goals:
  - 改 archive.ps1 搬运协议
  - 归档真实 LEDGER 条目
  - 改 task、review 或 CI
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path scripts/lessons.ps1 -SimpleMatch '[LSN-META-INVALID]') -and (Select-String -Path scripts/selftest.ps1 -SimpleMatch 'lesson-meta-body-bait'))) { exit 1 }"
dod_exit: 0
dod_assert: 选择器只从唯一、锚定且完整的规范 meta 行读取 tier 与 recurrence；缺失、重复或非法字段令 check fail-closed 且永不入选，即使正文含误导性的 tier=ledger 或 recurrence=1 文本；既有合法条目行为不变
review_gate: codex {verdict:pass}
hygiene: 使用 hermetic RepoRoot hostile fixtures 覆盖缺 tier、缺 recurrence、非法值、重复 meta 与正文诱饵；不读取或改写真实 lesson
doc_sync: none；除非行为契约改变，否则不扩文档范围
---

# T0-LESSONS-COLD-RECALL-R3-CLOSURE

## 来源与执行前置

PR #51 在两轮 R3 后达到硬上限。第 1 轮的组合归档旁域副作用、夹具幂等和文档漂移已经闭合；第 2 轮只剩规范元数据解析缺口。不得在原 PR 继续第 3 轮。

原卡必须先由人裁决定是否合并；本卡在该决定及原产物落入基线前不得开始实现。

## 单一产出

把 lesson 的 `tier` 与 `recurrence` 解析收敛到唯一、锚定、完整的规范 meta 行。正文 prose、缺字段、重复字段或非法值都不能补齐元数据或让条目进入自动归档候选；`check` 对这些状态 fail-closed。

## RED-first

先在 hermetic fixture 中加入正文伪造 `tier=ledger` / `recurrence=1`、缺失字段、重复 meta 与非法值用例，证明 PR #51 当前实现会误选或漏报；记录 RED 后才修改选择器与检查器。

## 边界

只接住 PR #51 第 2 轮点名的一个 finding。既有 `archive.ps1 -LessonsOnly` 搬运隔离、hot/cold search、定义 ID 并集、冷项 bump/promote 拒绝和归档幂等不重新设计。
