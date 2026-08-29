---
id: T0-LOCAL-RECONCILE-REGISTER
title: 注册落后本地 master 的十二张可评审文档调和卡
depends_on: []
parallelizable_with: []
status: merged
branch: T0-LOCAL-RECONCILE-REGISTER
worktree: C:\wt\T0-LOCAL-RECONCILE-REGISTER
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tasks/T0-RECONCILE-DATA-AUTHORITY.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-METADATA.md
  - specs/tasks/T0-RECONCILE-LESSONS.md
  - specs/tasks/T0-RECONCILE-ROADMAP-INDEX.md
  - specs/tasks/T0-RECONCILE-T1-SECURITY-CARDS.md
  - specs/tasks/T0-RECONCILE-T5-DIAGNOSTIC-CARDS.md
  - specs/tasks/T0-RECONCILE-UI-CAPTURE.md
  - specs/tasks/T0-RECONCILE-UI-COVERAGE.md
  - specs/tasks/T0-RECONCILE-UI-NOTICE-SCHEDULE.md
  - specs/tasks/T0-RECONCILE-UI-OFFLINE-OPERATIONS.md
forbid:
  - 修改产品代码、冻结契约、脚手架、归档卡或原始脏工作区
  - 在本卡实现设计/数据库/安全/UI 功能；本卡只登记后续调和范围
  - 复活已归档任务、弱化冻结保护或复制已由上游合并的旧 harness 改动
non_goals:
  - 交付十二张子卡的文档内容
  - 清理或快进原始本地 master
acceptance:
  - "A1 精确路径集合：相对本卡基线的净 diff 恰好是 Task Board 与十二张 T0-RECONCILE-* 子卡共 13 个路径；少一个、多一个或路径不同均 RED"
  - "A2 卡片集合：十二张子卡 id 分别为 DATA-AUTHORITY、T1-SECURITY-CARDS、T5-DIAGNOSTIC-CARDS、ROADMAP-INDEX、DESIGN-METADATA、DESIGN-JOURNEYS、DESIGN-COMPONENTS、UI-COVERAGE、UI-CAPTURE、UI-NOTICE-SCHEDULE、UI-OFFLINE-OPERATIONS、LESSONS，全部通过 check-cards"
  - "A3 Task Board 投影：十二个 id 各有且仅有一条 W0 行，作者路由为 Terra/Sonnet，Sol 只作独立 R3；删除任一行或重复任一行均 RED"
  - "A4 评审预算：权威 review.ps1 -SizeOnly 对 exact HEAD 返回 0，changed lines 不超过 1000 且 diff chars 不超过 60000"
  - "A5 边界不扩张：13 个 allow_paths 与 A1 的 exact diff 集合逐项相同，不含 CLAUDE、hook、scripts、产品代码、归档卡或旧 T0-LESSONS-BUMP-PLANE"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 && pwsh -NoProfile -Command "if (((git diff --name-only origin/master...HEAD) -join ',') -cne 'docs/TASK-BOARD.md,specs/tasks/T0-RECONCILE-DATA-AUTHORITY.md,specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md,specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md,specs/tasks/T0-RECONCILE-DESIGN-METADATA.md,specs/tasks/T0-RECONCILE-LESSONS.md,specs/tasks/T0-RECONCILE-ROADMAP-INDEX.md,specs/tasks/T0-RECONCILE-T1-SECURITY-CARDS.md,specs/tasks/T0-RECONCILE-T5-DIAGNOSTIC-CARDS.md,specs/tasks/T0-RECONCILE-UI-CAPTURE.md,specs/tasks/T0-RECONCILE-UI-COVERAGE.md,specs/tasks/T0-RECONCILE-UI-NOTICE-SCHEDULE.md,specs/tasks/T0-RECONCILE-UI-OFFLINE-OPERATIONS.md') { exit 1 }; if ((Select-String -Path 'docs/TASK-BOARD.md' -Pattern '^\| W0 \| T0-RECONCILE-(DATA-AUTHORITY|T1-SECURITY-CARDS|T5-DIAGNOSTIC-CARDS|ROADMAP-INDEX|DESIGN-METADATA|DESIGN-JOURNEYS|DESIGN-COMPONENTS|UI-COVERAGE|UI-CAPTURE|UI-NOTICE-SCHEDULE|UI-OFFLINE-OPERATIONS|LESSONS) \|').Count -ne 12) { exit 1 }" && pwsh -NoProfile -File scripts/review.ps1 -Base master -WorktreePath . -SizeOnly
dod_exit: 0
dod_assert: check-cards 通过；exact diff 只有 13 个 allow_paths；Task Board 恰有十二条对应 W0 行；权威 R3 size-only 预算通过
review_gate: codex {verdict:pass}
hygiene: 注册卡仅建立范围与可证伪验收，不复制子卡设计内容；后续每张卡仍独立 R1–R5
doc_sync: docs/TASK-BOARD.md 由本卡的 reviewed batch 同步，不在基线注册提交预写未合并状态
---

# T0-LOCAL-RECONCILE-REGISTER

## 产出

只为从落后 225 个提交的脏本地 `master` 中安全调和有效文档建立十二张 review-sized 卡。原始修改继续隔离保存；本卡不迁移任何设计内容。

## 基线登记例外

范围闸只读取 pinned baseline 上的卡，因此本卡按仓库既有“卡登记直推 master”约定先落一份卡片元数据。后续十二张卡及 Task Board 投影仍须走本卡范围闸、R3、CI 与 PR 合并，不继承直推例外。

## 验收

执行 front matter 的 `dod_command`。任何额外文件、缺失子卡、重复/缺失 Task Board 行或超预算都会失败。
