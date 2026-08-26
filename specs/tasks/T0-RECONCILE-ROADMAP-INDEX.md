---
id: T0-RECONCILE-ROADMAP-INDEX
title: 将离线安全与诊断卡投影到任务表和技术债索引
depends_on: [T0-RECONCILE-T1-SECURITY-CARDS, T0-RECONCILE-T5-DIAGNOSTIC-CARDS]
status: todo
branch: T0-RECONCILE-ROADMAP-INDEX
worktree: C:\wt\T0-RECONCILE-ROADMAP-INDEX
allow_paths:
  - docs/TASK-BOARD.md
  - specs/tech-debt-tracker.md
forbid:
  - 修改产品代码或把 todo 设计描述为已实现
  - 复活已归档旧卡、TD24，或收窄 ADR-0002 的按物业备份范围
non_goals:
  - 创建或实现七张新卡
  - 修改 UI 设计真相源或 lessons 账本
acceptance:
  - "A1 七卡 board projection exact 且 status todo"
  - "A2 TD160 lifecycle/write authority"
  - "A3 TD161 typed diagnostics closure"
  - "A4 TD12 保留；TD24 archived"
  - "A5 board 不声称 implemented/merged"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$b=Get-Content 'docs/TASK-BOARD.md' -Raw;$t=Get-Content 'specs/tech-debt-tracker.md' -Raw;function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$waves=@{'T1-LOCAL-DATA-SECURITY'='W1';'T1-SHARE-SCREEN-PRIVACY'='W1';'T1-DATABASE-LIFECYCLE-AUTHORITY'='W2';'T5-OPERATION-EVENT-STORE'='W5';'T5-DIAGNOSTIC-EXPORT'='W5';'T5-LOCAL-DATA-ERASURE'='W5';'T7-LOCAL-HEALTH-RELEASE'='W7'};$ids=@([regex]::Matches($b,'(?m)^\| W[1257] \| (T1-LOCAL-DATA-SECURITY|T1-SHARE-SCREEN-PRIVACY|T1-DATABASE-LIFECYCLE-AUTHORITY|T5-OPERATION-EVENT-STORE|T5-DIAGNOSTIC-EXPORT|T5-LOCAL-DATA-ERASURE|T7-LOCAL-HEALTH-RELEASE) \|')|%{$_.Groups[1].Value});if($ids.Count-ne7-or$ids.Count-ne@($ids|sort -Unique).Count-or(Compare-Object ($ids|sort) ($waves.Keys|sort))){throw 'board ids'};foreach($id in $waves.Keys){$c=Get-Content ('specs/tasks/'+$id+'.md') -Raw;$title=[regex]::Match($c,'(?m)^title: (.+)$').Groups[1].Value;$deps=[regex]::Match($c,'(?m)^depends_on: \[(.*)\]$').Groups[1].Value;$row=[regex]::Match($b,('(?m)^\| '+$waves[$id]+' \| '+$id+' \|([^\r\n]+)$')).Value;$x=@($row.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne8-or$x[2]-cne$title-or$x[3]-cne$deps-or$c-notmatch'(?m)^status: todo$'-or$row-match'(?i)\b(merged|implemented|已实现|已合并)\b'){throw ('board projection '+$id)}};Must $t @('(?m)^\| TD160 \|.*active 与 historical.*写权限.*后果.*修法.*可测.*TD4.*major \| carded \| `specs/tasks/T1-DATABASE-LIFECYCLE-AUTHORITY\.md` \|$','(?m)^\| TD161 \|.*typed.*PII.*失败隔离.*90 天/20,000 行.*飞行模式.*T1-LOCAL-DATA-SECURITY.*major \| carded \| `T5-OPERATION-EVENT-STORE` → `T5-DIAGNOSTIC-EXPORT`.*\|$','(?m)^\| TD12 \|.*按物业导出仍带整库.*manifest.*format_version 2.*回读包内 DB.*major \| open \|');if($t-match'(?m)^\| TD24 \|'){throw 'revived TD24'}
dod_exit: 0
dod_assert: A1–A5 projection/status mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
