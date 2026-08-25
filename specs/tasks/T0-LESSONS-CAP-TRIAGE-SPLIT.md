---
id: T0-LESSONS-CAP-TRIAGE-SPLIT
title: 从超预算 PR #127 提取 lessons triage 探针与 hermetic 夹具
depends_on: [T0-LESSONS-CAP-CORE-SPLIT]
status: todo
branch: T0-LESSONS-CAP-TRIAGE-SPLIT
worktree: C:\wt\T0-LESSONS-CAP-TRIAGE-SPLIT
allow_paths:
  - scripts/triage.ps1
  - specs/tasks/T0-LESSONS-CAP-TRIAGE-SPLIT.md
forbid:
  - 改变 PR #127 已评审树里的探针语义；本卡只提取同一文件内容
  - fetch、gh 或任何运行时网络访问；心跳保持只读、离线、确定性
  - 提高 R3 diff 预算或改写 PR #127 历史
non_goals:
  - lessons 共享判定核（依赖卡已提取）
  - 教学文档与任务卡收尾（仍由原 PR #127 承担）
dod_command: pwsh -NoProfile -File scripts/triage.ps1 selfcheck
dod_exit: 0
dod_assert: triage selfcheck 打印 PASS，覆盖 cap 两侧、promote/demote、delivery-blocked 与 resident Markdown marker/boundary/fence
review_gate: codex {verdict:pass}
hygiene: 文件内容从 PR #127 精确提取；提取前后 SHA-256 相等，且本卡完整 diff 低于 1000 行 / 60000 字符
doc_sync: 原 PR #127 的文档尾卡统一收口，避免拆分过程中制造双源
---

# T0-LESSONS-CAP-TRIAGE-SPLIT

依赖共享核落地后，本卡只提取 PR #127 的 `triage.ps1` 完整消费者与 hermetic 夹具。
原 PR #127 随后只剩小体量教学面与配置收尾，可由完整 R3 一次读完。

