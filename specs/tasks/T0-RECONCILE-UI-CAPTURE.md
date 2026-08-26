---
id: T0-RECONCILE-UI-CAPTURE
title: 对齐采集、历史与 PDF 实现卡的设计系统指针
depends_on: [T0-RECONCILE-UI-COVERAGE]
status: todo
branch: T0-RECONCILE-UI-CAPTURE
worktree: C:\wt\T0-RECONCILE-UI-CAPTURE
allow_paths:
  - specs/tasks/T2-CAPTURE-UI.md
  - specs/tasks/T3-FIELD-UX-ACCEPTANCE.md
  - specs/tasks/T3-HISTORY-COMPARE.md
  - specs/tasks/T3-PDF-RENDERER.md
forbid:
  - 复制 DESIGN.md 规格或修改产品代码
  - 编辑已归档 theme/photo-property-dedupe 卡
non_goals:
  - 通知、日程、备份、清除、remediation、smoke
acceptance:
  - "A1 Capture plan + A1–A5"
  - "A2 Field UX plan + A1–A5"
  - "A3 History plan + A1–A4"
  - "A4 PDF plan + A1–A4"
  - "A5 exact depends_on；其余仅 plan/acceptance"
dod_command: function ExactIds($r,$e){$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - "?([^ "\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$base=(& git merge-base HEAD refs/remotes/origin/master).Trim();if($LASTEXITCODE-ne0-or$base-notmatch'^[0-9a-f]{40}$'){throw 'baseline oid'};$cp='specs/tasks/T2-CAPTURE-UI.md';$fp='specs/tasks/T3-FIELD-UX-ACCEPTANCE.md';$hp='specs/tasks/T3-HISTORY-COMPARE.md';$pp='specs/tasks/T3-PDF-RENDERER.md';$c=Get-Content $cp -Raw;$f=Get-Content $fp -Raw;$h=Get-Content $hp -Raw;$p=Get-Content $pp -Raw;ExactIds $c @('A1','A2','A3','A4','A5');ExactIds $f @('A1','A2','A3','A4','A5');ExactIds $h @('A1','A2','A3','A4');ExactIds $p @('A1','A2','A3','A4');Must $c @('(?m)^depends_on: \[T2-CAPTURE-CORE, T2-PHOTO-PIPELINE, T1-SPIKE-PLATFORM, T1-SHARE-SCREEN-PRIVACY, T2-FIELD-LEDGER-THEME-R3-CLOSURE, T2-REPEATABLE-ROOM-RUNTIME\]$','(?m)^plan_ref: context/DESIGN\.md#primary-inspection-journey$','(?m)^  - "A1 (?=[^"\r\n]*setup)(?=[^"\r\n]*capture)(?=[^"\r\n]*review)(?=[^"\r\n]*camera)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*resume)(?=[^"\r\n]*save)(?=[^"\r\n]*focus)(?=[^"\r\n]*evidence)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*permission)(?=[^"\r\n]*offline)(?=[^"\r\n]*fallback)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*48dp)(?=[^"\r\n]*200%)(?=[^"\r\n]*TalkBack)[^"\r\n]+"$','(?m)^  - "A5 (?=[^"\r\n]*main-thread)(?=[^"\r\n]*LRU)(?=[^"\r\n]*performance)[^"\r\n]+"$');Must $f @('(?m)^depends_on: \[T2-CAPTURE-UI, T3-HISTORY-COMPARE\]$','(?m)^plan_ref: context/DESIGN\.md#accessibility-contract$','(?m)^  - "A1 (?=[^"\r\n]*device)(?=[^"\r\n]*build)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*daylight)(?=[^"\r\n]*one-hand)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*TalkBack)(?=[^"\r\n]*200%)(?=[^"\r\n]*Reduce Motion)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*process death)(?=[^"\r\n]*offline)(?=[^"\r\n]*provider)[^"\r\n]+"$','(?m)^  - "A5 (?=[^"\r\n]*P0/P1)(?=[^"\r\n]*closure)[^"\r\n]+"$');Must $h @('(?m)^depends_on: \[T2-CAPTURE-UI, T1-SPIKE-PLATFORM, T2-REPEATABLE-ROOM-RUNTIME\]$','(?m)^plan_ref: context/DESIGN\.md#history-evidence-and-media-component-matrix$','(?m)^  - "A1 (?=[^"\r\n]*previous)(?=[^"\r\n]*baseline)(?=[^"\r\n]*empty)(?=[^"\r\n]*archived)[^"\r\n]+"$','(?m)^  - "A2 [^"\r\n]*visible controls[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*preview-only)(?=[^"\r\n]*focus return)[^"\r\n]+"$','(?m)^  - "A4 [^"\r\n]*offline read[^"\r\n]+"$');Must $p @('(?m)^depends_on: \[T3-REPORT-COMPOSER, T1-SHARE-SCREEN-PRIVACY\]$','(?m)^plan_ref: context/DESIGN\.md#backup-report-health-and-compliance-component-matrix$','(?m)^  - "A1 (?=[^"\r\n]*quality)(?=[^"\r\n]*progress)(?=[^"\r\n]*verified receipt)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*Open PDF)(?=[^"\r\n]*Share)(?=[^"\r\n]*Export another quality)[^"\r\n]+"$','(?m)^  - "A3 [^"\r\n]*temporary content URI[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*CJK)(?=[^"\r\n]*memory)(?=[^"\r\n]*offline)(?=[^"\r\n]*failure recovery)[^"\r\n]+"$');if(($c+$f+$h+$p)-match'(?m)^### .*component matrix$'){throw 'copied matrix'};function Clean($v){$v=$v-replace'\r\n',"`n";$v=$v-replace'(?m)^plan_ref:.*\n','' -replace'(?ms)^acceptance:\n(?:  - [^\n]+\n)+','' -replace'(?m)^depends_on:.*\n','';$v.Trim()};foreach($q in @($cp,$fp,$hp,$pp)){$old=& git show ("$base`:$q")|Out-String;if($LASTEXITCODE-ne0-or(Clean (Get-Content $q -Raw))-cne(Clean $old)){throw 'unrelated rewrite'}}
dod_exit: 0
dod_assert: A1–A5 field/dependency mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
