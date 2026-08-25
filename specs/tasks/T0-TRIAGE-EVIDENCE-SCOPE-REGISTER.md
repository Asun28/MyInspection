---
id: T0-TRIAGE-EVIDENCE-SCOPE-REGISTER
title: 把 PR #137 其余裁决证据 finding 并入独立修复卡
depends_on: []
status: todo
branch: T0-TRIAGE-EVIDENCE-SCOPE-REGISTER
worktree: C:\wt\T0-TRIAGE-EVIDENCE-SCOPE-REGISTER
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-SCOPE-REGISTER.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md
forbid:
  - 修改生产脚本或把 fix-forward 塞回 exact extraction PR #137
  - 删除既有大小写语义 finding，或提高 R3 diff 预算
non_goals:
  - 实现裁决证据修复
  - 改变 PR #127 / #137 的探针行为
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-TRIAGE-EVIDENCE-CASE
dod_exit: 0
dod_assert: 独立实现卡同时封闭 actual-root identity、per-root HEAD 绑定和 unreadable/unknown 可观测失败路径
review_gate: codex {verdict:pass}
hygiene: docs-only 范围修订；不运行 scaffold selftest
doc_sync: docs/TASK-BOARD.md 同提交更新目标卡说明
---

# T0-TRIAGE-EVIDENCE-SCOPE-REGISTER

PR #137 恢复 exact extraction 后，R3 又点出同一 delivery-blocked 证据链的 SHA 夹具空洞与静默失败。
两者继续由独立实现卡承接，不扩大 #137。
