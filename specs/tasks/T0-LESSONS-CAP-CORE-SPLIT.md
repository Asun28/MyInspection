---
id: T0-LESSONS-CAP-CORE-SPLIT
title: 从超预算 PR #127 提取 resident-id 共享判定核与 lessons 消费者
depends_on: []
status: todo
branch: T0-LESSONS-CAP-CORE-SPLIT
worktree: C:\wt\T0-LESSONS-CAP-CORE-SPLIT
allow_paths:
  - scripts/_lessons.ps1
  - scripts/lessons.ps1
  - scripts/_cards.ps1
  - scripts/selftest.ps1
  - specs/tasks/T0-LESSONS-CAP-CORE-SPLIT.md
forbid:
  - 改变 PR #127 已评审树里的业务语义；本卡只提取同一文件内容
  - 提高 R3 diff 预算或改写 PR #127 历史
non_goals:
  - triage 探针与其夹具（下一张 T0-LESSONS-CAP-TRIAGE-SPLIT）
  - 教学文档与任务卡收尾（仍由原 PR #127 承担）
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check
dod_exit: 0
dod_assert: lessons.ps1 check 在生产路径打印 PASS 与 resident id=9；共享解析器边界与 `_cards.ps1` BOM 兼容另由现有 selftest 常设夹具覆盖
review_gate: codex {verdict:pass}
hygiene: 文件内容从 PR #127 精确提取；提取前后逐文件 SHA-256 相等，且本卡完整 diff 低于 1000 行 / 60000 字符
doc_sync: 原 PR #127 的文档尾卡统一收口，避免拆分过程中制造双源
---

# T0-LESSONS-CAP-CORE-SPLIT

PR #127 在 #128 合并后被真实 diff 预算拦下。本卡只提取共享判定核、`lessons.ps1` 消费者、
对应 selftest 与既有 BOM 兼容改动；内容不重写，合并后由下一张提取 triage 面。
