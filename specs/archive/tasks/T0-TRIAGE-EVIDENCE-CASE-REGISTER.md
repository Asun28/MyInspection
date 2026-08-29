---
id: T0-TRIAGE-EVIDENCE-CASE-REGISTER
title: 登记 triage 裁决证据目录大小写语义修复卡
depends_on: []
status: merged
branch: T0-TRIAGE-EVIDENCE-CASE-REGISTER
worktree: C:\wt\T0-TRIAGE-EVIDENCE-CASE-REGISTER
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-CASE-REGISTER.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md
forbid:
  - 修改生产脚本或把 fix-forward 塞回 exact extraction PR #137
  - 提高 R3 diff 预算或改写任何既有分支历史
non_goals:
  - 实现大小写语义修复
  - 改变 PR #127 / #137 的探针行为
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-TRIAGE-EVIDENCE-CASE
dod_exit: 0
dod_assert: 独立实现卡通过任务卡 schema 校验，且 TASK-BOARD 明确登记其依赖与根因范围
review_gate: codex {verdict:pass}
hygiene: docs-only 登记片；不触碰脚本、不消费完整 scaffold selftest
doc_sync: docs/TASK-BOARD.md 同提交登记目标卡
---

# T0-TRIAGE-EVIDENCE-CASE-REGISTER

PR #137 的 R3 发现属于真实 fix-forward，但其 exact-extraction 卡禁止改变 PR #127 的已评审运行语义。
本卡只登记独立承接卡，让 #137 恢复精确提取并保持发现不丢失。
