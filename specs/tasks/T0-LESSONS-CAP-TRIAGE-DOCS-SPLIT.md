---
id: T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT
title: 从超预算 PR #127 提取 triage 探针 roster 文档同步
depends_on: [T0-LESSONS-CAP-CORE-SPLIT]
status: todo
branch: T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT
worktree: C:\wt\T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT
allow_paths:
  - docs/LOOP-ENGINEERING.md
  - docs/DELIVERY-CHAINS.md
  - .claude/skills/triage/SKILL.md
  - specs/tasks/T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT.md
forbid:
  - 改变 PR #127 已评审文案语义；本卡只提取同一文档内容
  - 修改脚本、提高 R3 diff 预算或改写 PR #127 历史
non_goals:
  - triage 生产脚本与 selftest（后继 T0-LESSONS-CAP-TRIAGE-SPLIT）
  - 其它 lessons 教学面与原任务卡收尾（仍由原 PR #127 承担）
dod_command: pwsh -NoProfile -Command "if (-not ((Select-String -Path docs/LOOP-ENGINEERING.md -Pattern '10 探针') -and (Select-String -Path docs/DELIVERY-CHAINS.md -Pattern 'delivery-blocked') -and (Select-String -Path .claude/skills/triage/SKILL.md -Pattern 'lessons-demote'))) { exit 1 }"
dod_exit: 0
dod_assert: 三份权威 roster 同步到 10 探针并明确包含 delivery-blocked 与 lessons-demote
review_gate: codex {verdict:pass}
hygiene: 三份文件从 PR #127 精确提取；docs-only diff 低于 1000 行 / 60000 字符，先落后令后继代码片的既有 doc-count 闸可绿
doc_sync: 本卡本身就是 triage 探针 roster 的文档同步片
---

# T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT

`triage.ps1` 增至 10 探针后，既有 selftest gate 14 要求三份 roster 同提交更新；但完整代码、
selftest 与文档合计超过 60k。先落 exact #127 docs-only 片，后继代码片即可在预算内恢复一致。
