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
dod_command: pwsh -NoProfile -Command { $expected=@('cards-active','delivery-blocked','effectiveness','handoff-open','harness-refresh','lessons-cap','lessons-demote','lessons-promote','tech-debt-open','worktree-orphan') | Sort-Object; $sections=@(([regex]::Match((Get-Content docs/LOOP-ENGINEERING.md -Raw),'(?s)## 心跳：`scripts/triage\.ps1`(?<body>.*?)(?=\r?\n\r?\n- 退出码)')).Groups['body'].Value,([regex]::Match((Get-Content docs/DELIVERY-CHAINS.md -Raw),'(?m)^\|\s*心跳\s*/\s*triage.*$')).Value,([regex]::Match((Get-Content .claude/skills/triage/SKILL.md -Raw),'(?s)10 探针：(?<body>.*?)(?=\r?\n2\. \*\*TRIAGE)')).Groups['body'].Value); foreach($section in $sections){ $matches=@([regex]::Matches($section,'(?<![a-z0-9-])(?:cards-active|delivery-blocked|effectiveness|handoff-open|harness-refresh|lessons-cap|lessons-demote|lessons-promote|tech-debt-open|worktree-orphan)(?![a-z0-9-])') | ForEach-Object Value); $actual=@($matches | Sort-Object -Unique); if((-not $section) -or $matches.Count -ne 10 -or ($actual -join ',') -cne ($expected -join ',')){ exit 1 } } }
dod_exit: 0
dod_assert: 唯一解析三份权威 roster 边界；每份恰含一次同一组 10 个探针名，缺失、重复或陈旧集合均非零
review_gate: codex {verdict:pass}
hygiene: 三份文件从 PR #127 精确提取；docs-only diff 低于 1000 行 / 60000 字符，先落后令后继代码片的既有 doc-count 闸可绿
doc_sync: 本卡本身就是 triage 探针 roster 的文档同步片
---

# T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT

`triage.ps1` 增至 10 探针后，既有 selftest gate 14 要求三份 roster 同提交更新；但完整代码、
selftest 与文档合计超过 60k。先落 exact #127 docs-only 片，后继代码片即可在预算内恢复一致。
