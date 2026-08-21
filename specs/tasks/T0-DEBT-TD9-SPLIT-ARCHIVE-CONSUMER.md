---
id: T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER
title: 让 TD9 split checker 读取已归档规划卡
depends_on: [T0-DEBT-SELFTEST-SPLIT-PLAN]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
status: todo
branch: T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER
worktree: C:\wt\T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER
allow_paths:
  - scripts/check-td9-split.ps1
  - specs/tasks/T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER.md
  - docs/TASK-BOARD.md
forbid:
  - 修改 scripts/selftest.ps1、TD9 子卡语义或归档生命周期
  - 放宽卡片字段、allow_paths、串行顺序或 mutation 断言
  - 为历史卡保留 live 副本或静默选择重复副本
non_goals:
  - 修改 skip ledger、no-git routing、mutation budget 或 load stability 实现
  - 执行任何 selftest 分片
diagnosis:
  root_cause: check-td9-split.ps1 把已合并规划卡路径写死为 specs/tasks/，但权威归档流程会将 merged 卡移入 specs/archive/tasks/，导致正常 R5 归档后 checker 在断言前因缺文件退出。
  same_class: checker 仅 Plan 卡已 merged，其余四张 TD9 实施卡仍为 live todo；只将共享解析用于 Plan，保留其历史 allow_paths 的逐字验证。
dod_command: pwsh -NoProfile -File scripts/check-td9-split.ps1
dod_exit: 0
dod_assert: 有界生命周期夹具证明 live-only 与 archive-only 均解析唯一卡，live+archive 双份 fail-closed；真实 TD9 split 合同及既有 deletion/reorder/weakening/decoy mutations 全部通过。
review_gate: codex {verdict:pass}
hygiene: 夹具只创建两个有界临时卡文件并逐个清理；不启动 selftest，不以源码 grep 代替真实 checker 行为。
doc_sync: 合并后归档本卡并在 TASK-BOARD 记录 PR/commit；随后 T0-HANDOFF-REVALIDATE R5 归档 PR 在新 master 上重放。
---

# T0-DEBT-TD9-SPLIT-ARCHIVE-CONSUMER

## 产出

让 TD9 split checker 遵循 live/archive 卡片生命周期，不再因正常冷存而假红。

## 验收边界

- 同一 id 只允许 live 或 archive 一份；缺失或双份均拒绝。
- 归档后仍读取历史卡原文，不把 allow_paths 改写为 archive 路径。
- 既有 TD9 字段、顺序与 mutation 合同不变。
