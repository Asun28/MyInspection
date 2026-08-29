---
id: T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT
title: 从超预算 PR #127 提取 triage 探针 roster 文档同步
depends_on: [T0-LESSONS-CAP-CORE-SPLIT]
status: merged
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
dod_command: pwsh -NoProfile -Command { $expected=@('cards-active','delivery-blocked','effectiveness','handoff-open','harness-refresh','lessons-cap','lessons-demote','lessons-promote','tech-debt-open','worktree-orphan') | Sort-Object; function Test-ExactRoster([string]$raw,[string]$pattern,[string]$mode){ $boundaries=@([regex]::Matches($raw,$pattern)); if($boundaries.Count -ne 1){ return $false }; $body=$boundaries[0].Groups[1].Value; if($mode -eq 'code'){ $tokens=@([regex]::Matches($body,'`([A-Za-z][A-Za-z0-9_-]*)`') | ForEach-Object { $_.Groups[1].Value }) } else { $tokens=@($body -split '\s*/\s*' | ForEach-Object { $_.Trim() }) }; $unique=@($tokens | Sort-Object -Unique); return $tokens.Count -eq 10 -and $unique.Count -eq 10 -and ($unique -join ',') -ceq ($expected -join ',') }; $cases=@(@{ Path='docs/LOOP-ENGINEERING.md'; Pattern='(?s)## 心跳：`scripts/triage\.ps1`\r?\n.*?10 探针：(.*?)(?=\r?\n\r?\n- 退出码)'; Mode='code' },@{ Path='docs/DELIVERY-CHAINS.md'; Pattern='(?m)^\|\s*心跳\s*/\s*triage[^\r\n]*?扫描各子系统（([^）]+)）→[^\r\n]*$'; Mode='plain' },@{ Path='.claude/skills/triage/SKILL.md'; Pattern='(?s)## 回路（DISCOVER → TRIAGE → ACT，单向喂既有链）\r?\n.*?10 探针：(.*?)(?=\r?\n2\. \*\*TRIAGE)'; Mode='code' }); foreach($case in $cases){ $raw=Get-Content $case.Path -Raw; if(-not (Test-ExactRoster $raw $case.Pattern $case.Mode)){ exit 1 }; $match=[regex]::Matches($raw,$case.Pattern)[0]; $group=$match.Groups[1]; $unknownBody=if($case.Mode -eq 'code'){ $group.Value + ' `legacy-probe`' } else { $group.Value + ' / legacy-probe' }; $prefixBody=$group.Value.Replace('cards-active','old_cards-active'); $unknown=$raw.Remove($group.Index,$group.Length).Insert($group.Index,$unknownBody); $prefixed=$raw.Remove($group.Index,$group.Length).Insert($group.Index,$prefixBody); $duplicated=$raw + "`r`n" + $raw; foreach($mutant in @($unknown,$prefixed,$duplicated)){ if(Test-ExactRoster $mutant $case.Pattern $case.Mode){ exit 1 } } } }
dod_exit: 0
dod_assert: 三份权威 roster 各自只有一个边界，完整标识符集合与计数精确等于同一组 10 个探针；unknown、前缀化和重复边界变异均须非零
review_gate: codex {verdict:pass}
hygiene: 三份文件从 PR #127 精确提取；docs-only diff 低于 1000 行 / 60000 字符，先落后令后继代码片的既有 doc-count 闸可绿
doc_sync: 本卡本身就是 triage 探针 roster 的文档同步片
---

# T0-LESSONS-CAP-TRIAGE-DOCS-SPLIT

`triage.ps1` 增至 10 探针后，既有 selftest gate 14 要求三份 roster 同提交更新；但完整代码、
selftest 与文档合计超过 60k。先落 exact #127 docs-only 片，后继代码片即可在预算内恢复一致。
