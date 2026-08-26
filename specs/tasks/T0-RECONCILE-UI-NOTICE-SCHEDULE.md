---
id: T0-RECONCILE-UI-NOTICE-SCHEDULE
title: 对齐通知与日程实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-NOTICE-SCHEDULE
worktree: C:\wt\T0-RECONCILE-UI-NOTICE-SCHEDULE
allow_paths:
  - specs/tasks/T4-NOTICES.md
  - specs/tasks/T4-SCHEDULE.md
forbid:
  - 修改产品代码、法定规则或通知送达语义
  - 复制 DESIGN.md 完整组件规格
non_goals:
  - 采集、报告、备份、健康或清除卡
acceptance:
  - "A1 Notices plan+A1–A4+states"
  - "A2 Copy≠delivery；record method/time"
  - "A3 Schedule plan+A1–A4+route"
  - "A4 only plan/acceptance"
dod_command: function ExactIds($r){foreach($k in 'acceptance','plan_ref'){if([regex]::Matches($r,('(?m)^'+$k+':')).Count-ne1){throw ('key '+$k)}};$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - "?([^ "\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne4-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) @('A1','A2','A3','A4'))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$base=(& git merge-base HEAD refs/remotes/origin/master).Trim();if($LASTEXITCODE-ne0-or$base-notmatch'^[0-9a-f]{40}$'){throw 'baseline oid'};$np='specs/tasks/T4-NOTICES.md';$sp='specs/tasks/T4-SCHEDULE.md';$n=Get-Content $np -Raw;$s=Get-Content $sp -Raw;ExactIds $n;ExactIds $s;Must $n @('(?m)^plan_ref: context/DESIGN\.md#backup-report-health-and-compliance-component-matrix$','(?m)^  - "A1 (?=[^"\r\n]*center)(?=[^"\r\n]*compose)(?=[^"\r\n]*draft)(?=[^"\r\n]*valid)(?=[^"\r\n]*blocked)(?=[^"\r\n]*copied)(?=[^"\r\n]*recorded)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*Copy)(?=[^"\r\n]*Record delivery)(?=[^"\r\n]*method)(?=[^"\r\n]*time)(?=[^"\r\n]*not sent)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*compliance correction)(?=[^"\r\n]*share boundary)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*focus return)(?=[^"\r\n]*lock-screen address)(?=[^"\r\n]*no background send)[^"\r\n]+"$');Must $s @('(?m)^plan_ref: context/DESIGN\.md#structure-list-and-discovery-component-matrix$','(?m)^  - "A1 (?=[^"\r\n]*due)(?=[^"\r\n]*empty)(?=[^"\r\n]*filter)(?=[^"\r\n]*state badge)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*property)(?=[^"\r\n]*inspection route)[^"\r\n]+"$','(?m)^  - "A3 [^"\r\n]*13-week local reminder[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*offline)(?=[^"\r\n]*permission failure)[^"\r\n]+"$');if(($n+$s)-match'(?m)^### .*component matrix$'){throw 'copied matrix'};function Clean($v){$v=$v-replace'\r\n',"`n";$v=$v-replace'(?m)^plan_ref:.*\n','' -replace'(?ms)^acceptance:\n(?:  - [^\n]+\n)+','';$v.Trim()};foreach($q in @($np,$sp)){$old=& git show ("$base`:$q")|Out-String;if($LASTEXITCODE-ne0-or(Clean (Get-Content $q -Raw))-cne(Clean $old)){throw 'unrelated rewrite'}}
dod_exit: 0
dod_assert: exact semantics；pinned base；only plan/acceptance
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 owning docs
---
