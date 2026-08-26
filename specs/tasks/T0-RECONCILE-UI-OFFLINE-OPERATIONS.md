---
id: T0-RECONCILE-UI-OFFLINE-OPERATIONS
title: 对齐备份、媒体、remediation 与收官 smoke 的离线体验指针
depends_on: [T0-RECONCILE-UI-COVERAGE, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-OFFLINE-OPERATIONS
worktree: C:\wt\T0-RECONCILE-UI-OFFLINE-OPERATIONS
allow_paths:
  - specs/tasks/T5-BACKUP-IO.md
  - specs/tasks/T5-LOCAL-MEDIA-RETENTION.md
  - specs/tasks/T7-REMEDIATION.md
  - specs/tasks/T7-SMOKE-POLISH.md
forbid:
  - 修改产品代码、备份格式或运行期网络硬边界
  - 收窄 ADR-0002 的 full/property scope
non_goals:
  - 通知、日程、采集、历史与 PDF 卡
acceptance:
  - "A1 backup fields/states exact"
  - "A2 media acceptance exact"
  - "A3 remediation/network exact"
  - "A4 smoke acceptance exact"
  - "A5 unique fields + pinned baseline"
dod_command: function ExactIds($r,$e){if([regex]::Matches($r,'(?m)^acceptance:').Count-ne1){throw 'acceptance key'};$q=[regex]::Match($r,'(?ms)^acceptance:\r?\n((?:  - [^\r\n]+\r?\n)+)').Groups[1].Value;$a=@([regex]::Matches($q,'(?m)^  - "?([^ "\r\n]+) ')|%{$_.Groups[1].Value});if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'acceptance ids'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Matches($s,$p);if($m.Count-ne1){throw ('missing/non-unique '+$p)}}};$base=(& git merge-base HEAD refs/remotes/origin/master).Trim();if($LASTEXITCODE-ne0-or$base-notmatch'^[0-9a-f]{40}$'){throw 'baseline oid'};$bp='specs/tasks/T5-BACKUP-IO.md';$mp='specs/tasks/T5-LOCAL-MEDIA-RETENTION.md';$rp='specs/tasks/T7-REMEDIATION.md';$sp='specs/tasks/T7-SMOKE-POLISH.md';$b=Get-Content $bp -Raw;$m=Get-Content $mp -Raw;$r=Get-Content $rp -Raw;$s=Get-Content $sp -Raw;ExactIds $b @('A1','A2','A3','A4','A5');ExactIds $m @('A1','A2','A3','A4');ExactIds $r @('A1','A2','A3','A4');ExactIds $s @('A1','A2','A3','A4','A5');Must $b @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^backup_scopes: \[full, property\]$','(?m)^  - "A1 (?=[^"\r\n]*full)(?=[^"\r\n]*property)(?=[^"\r\n]*NOT_CONFIGURED)(?=[^"\r\n]*FAILED)(?=[^"\r\n]*verified receipt)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*staging)(?=[^"\r\n]*rollback)(?=[^"\r\n]*verify-before-replace)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*provider)(?=[^"\r\n]*authorization)(?=[^"\r\n]*storage)(?=[^"\r\n]*secret)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*local/USB)(?=[^"\r\n]*flight-mode)[^"\r\n]+"$','(?m)^  - "A5 [^"\r\n]*backup format[^"\r\n]+"$','PREPARING.*ENCRYPTING.*WRITING.*VERIFYING');Must $m @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^  - "A1 (?=[^"\r\n]*1/3/5/10/Always)(?=[^"\r\n]*30-day)[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*preflight)(?=[^"\r\n]*protected refs)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*confirmation)(?=[^"\r\n]*progress)(?=[^"\r\n]*recovery)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*hash/size)(?=[^"\r\n]*never delete DB/PDF/backup/cloud)[^"\r\n]+"$');Must $r @('(?m)^plan_ref: context/DESIGN\.md#offline-and-data-protection-experience$','(?m)^  - "A1 [^"\r\n]*on-device first[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*remote explicit)(?=[^"\r\n]*cancellable)(?=[^"\r\n]*runtime network)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*offline fallback)(?=[^"\r\n]*safe payload)(?=[^"\r\n]*source)(?=[^"\r\n]*disclaimer)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*never block)(?=[^"\r\n]*finalize)(?=[^"\r\n]*report)[^"\r\n]+"$');Must $s @('(?m)^plan_ref: context/DESIGN\.md#primary-inspection-journey$','(?m)^  - "A1 [^"\r\n]*flight-mode end-to-end[^"\r\n]+"$','(?m)^  - "A2 (?=[^"\r\n]*provider)(?=[^"\r\n]*permission)(?=[^"\r\n]*storage)(?=[^"\r\n]*process-death)[^"\r\n]+"$','(?m)^  - "A3 (?=[^"\r\n]*backup)(?=[^"\r\n]*restore)(?=[^"\r\n]*erase)(?=[^"\r\n]*health)(?=[^"\r\n]*share)[^"\r\n]+"$','(?m)^  - "A4 (?=[^"\r\n]*TalkBack)(?=[^"\r\n]*200%)(?=[^"\r\n]*theme)(?=[^"\r\n]*performance)[^"\r\n]+"$','(?m)^  - "A5 [^"\r\n]*exact evidence[^"\r\n]+"$');$sm=[regex]::Matches($b,'(?m)^backup_states: \[(.*)\]$');if($sm.Count-ne1-or($m+$r+$s)-match'(?m)^backup_(scopes|states):'){throw 'backup fields'};$sd=$sm[0].Groups[1].Value;$st=@($sd.Split(',')|%{$_.Trim()});$ex='NOT_CONFIGURED,READY,RUNNING,VERIFIED,PROVIDER_UNAVAILABLE,AUTHORIZATION_REVOKED,NEEDS_UNLOCK,NEEDS_PASSPHRASE,LOW_STORAGE,FAILED'-split',';if($st.Count-ne$ex.Count-or$st.Count-ne@($st|sort -Unique).Count-or(Compare-Object ($st|sort) ($ex|sort))){throw 'backup states'};if([regex]::Matches($r,'(?i)runtime network').Count-ne1-or($b+$m+$s)-match'(?i)runtime network'){throw 'network exclusivity'};if(($b+$m+$r+$s)-match'(?m)^### .*component matrix$'){throw 'copied matrix'};function Clean($v){$v=$v-replace'\r\n',"`n";$v=$v-replace'(?m)^plan_ref:.*\n','' -replace'(?ms)^acceptance:\n(?:  - [^\n]+\n)+','' -replace'(?m)^backup_scopes:.*\n','' -replace'(?m)^backup_states:.*\n','';$v.Trim()};foreach($q in @($bp,$mp,$rp,$sp)){$old=& git show ("$base`:$q")|Out-String;if($LASTEXITCODE-ne0-or(Clean (Get-Content $q -Raw))-cne(Clean $old)){throw 'function/format rewrite'}}
dod_exit: 0
dod_assert: A1–A5 unique-field mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
