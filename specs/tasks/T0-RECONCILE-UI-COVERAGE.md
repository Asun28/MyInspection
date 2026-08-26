---
id: T0-RECONCILE-UI-COVERAGE
title: 建立 UI/UX elements 页面与组件覆盖索引
depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX]
status: todo
branch: T0-RECONCILE-UI-COVERAGE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE
allow_paths:
  - CLAUDE.md
  - docs/UI-UX-ELEMENTS.md
forbid:
  - 在任务卡复制 DESIGN.md 的完整组件规格
  - 修改产品代码、已归档任务卡或已合并主题/照片去重卡
non_goals:
  - 修改下游实现卡、实现 UI、生成截图或建立第二套设计 token
  - 改动数据库/安全权威文档
acceptance:
  - "A1 projection-only schema；无第二 token/state authority"
  - "A2 五清单 exact 覆盖 81 ids"
  - "A3 21 pages Elements/owner 完整"
  - "A4 12 overlays + 13 states 完整"
  - "A5 A11y/响应式；六类未批准 Element 规范禁用"
dod_command: function Run($id){$p=@("specs/tasks/$id.md","specs/archive/tasks/$id.md")|?{Test-Path $_}|select -First 1;$l=Get-Content $p|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-COMPONENTS';$u=Get-Content 'docs/UI-UX-ELEMENTS.md' -Raw;$d=Get-Content 'context/DESIGN.md' -Raw;$idx=Get-Content 'CLAUDE.md' -Raw;$board=Get-Content 'docs/TASK-BOARD.md' -Raw;function TC($h,$i){$b=[regex]::Match($u,('(?ms)^\| '+$h+' \|[^\r\n]*\r?\n\|[-: |]+\r?\n((?:\|[^\r\n]+\r?\n)+)')).Groups[1].Value;@($b-split'\r?\n'|?{$_}|%{($_.Trim('|').Split('|')[$i]).Trim().Trim('`')})};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$registry=@([regex]::Matches([regex]::Match($d,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});function Refs($s){$a=@([regex]::Matches($s,'`([a-z][a-z0-9-]+)(?::[A-Z_]+)?`')|%{$_.Groups[1].Value});if(-not$a.Count){throw 'missing element'};foreach($x in $a){if($x-notin$registry){throw ('element ref '+$x)}}};$all=@();$sizes=13,14,19,19,16;for($i=1;$i-le5;$i++){$line=[regex]::Match($u,('(?ms)^### 2\.'+$i+'[^\r\n]*\r?\n\r?\n([^\r\n]+)')).Groups[1].Value;$ids=@([regex]::Matches($line,'`([a-z0-9-]+)`')|%{$_.Groups[1].Value});if($ids.Count-ne$sizes[$i-1]-or$ids.Count-ne@($ids|sort -Unique).Count){throw 'inventory group'};$all+=$ids};Exact $all $registry;$pages=TC 'Page ID' 0;Exact $pages @('PROPERTIES_ROOT','SCHEDULE_ROOT','SETTINGS_ROOT','PROPERTY_HUB','INSPECTION_SETUP','INSPECTION_CAPTURE','INSPECTION_REVIEW','REPORT_EXPORT','NOTICE_CENTER','NOTICE_COMPOSE','HHC_CAPTURE','BACKUP_SETTINGS','RESTORE_TASK','QUALITY_SETTINGS','LOCAL_MEDIA_SETTINGS','HEALTH_STATUS','DIAGNOSTIC_EXPORT','LOCAL_DATA_ERASURE','REMEDIATION_SETTINGS','CAMERA_CAPTURE','CAMERA_REVIEW');foreach($p in $pages){$x=@([regex]::Match($u,('(?m)^\| `'+$p+'` \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne5-or@($x|?{-not$_}).Count){throw 'page row'};Refs ($x[2]+$x[3]);$os=@([regex]::Matches($x[4],'T\d-[A-Z0-9-]+')|%{$_.Value});if(-not$os-and$x[4]-notmatch'Shared'){throw 'owner sentinel'};foreach($o in $os){if(-not(Test-Path "specs/tasks/$o.md")-and$board-notmatch('(?m)^\| [^|]+ \| '+[regex]::Escape($o)+' \|')){throw 'owner ref'}}};$over=TC 'Surface' 0;Exact $over @('REPORT_ACTION_SHEET','THEME_MODE_SHEET','STATUS_SHEET','PHRASE_SHEET','MEDIA_SOURCE_SHEET','finalize / discard / clear / remove confirmation','restore replacement confirmation','Android permission dialog','Folder/file/create picker','PDF viewer / Sharesheet','Android app settings','Speech recognizer');foreach($n in $over){$x=@([regex]::Match($u,('(?m)^\| `?'+[regex]::Escape($n)+'`? \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne4-or@($x|?{-not$_}).Count){throw 'overlay row'};Refs $x[2]};$states=TC '状态' 0;Exact $states @('Empty','Local loading','Long-running','Inline validation','Recoverable failure','Blocking compliance/integrity','Offline','Permission denied','Low storage','Restored after interruption','Success','Destructive','External handoff');foreach($n in $states){$x=@([regex]::Match($u,('(?m)^\| '+[regex]::Escape($n)+' \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne3-or@($x|?{-not$_}).Count){throw 'state row'};Refs $x[2]};$heads=@([regex]::Matches($u,'(?m)^#{1,3} (.+)$')|%{$_.Groups[1].Value});Exact $heads @('MyInspection UI/UX Elements 覆盖索引','1. 设计系统服从关系','2. Element 分层','2.1 容器与导航','2.2 内容、列表与状态表达','2.3 表单与选择','2.4 巡检、证据与媒体','2.5 长任务、安全与外部边界','3. 页面 → Elements 覆盖表','4. Overlay 与系统界面覆盖','5. 状态覆盖矩阵','6. 无障碍与响应式验收','7. 明确排除的 Elements');if($u-match'(?mi)^(colors|dark-colors|typography|rounded|spacing|interaction|motion|components|states):\s*$|#[0-9A-F]{6}|^\|[^\r\n]*\btoken\b[^\r\n]*\|'-or[regex]::Matches($u,'(?m)^\|[-: |]+\|$').Count-ne3){throw 'second authority'};$ban=[regex]::Match($u,'(?ms)^## 7\. 明确排除的 Elements\r?\n(.*)\z').Groups[1].Value;foreach($n in 'FAB','drawer','carousel','charts','global snackbar','remote telemetry'){if($ban-notmatch(('(?i)(不设计|禁止|不得)[^\r\n]*'+[regex]::Escape($n)))-or$ban-match(('(?i)(允许|支持|预留)[^\r\n]*'+[regex]::Escape($n)))){throw ('element ban '+$n)}};Must $u @('(?m)^> Normative source: `context/DESIGN\.md`','(?m)^## 6\. 无障碍与响应式验收$','48dp','200%','TalkBack','Reduce Motion','compact / medium / expanded','(?m)^## 7\. 明确排除的 Elements$','FAB','drawer','carousel','charts','global snackbar','remote telemetry');Must $idx @('(?m)^\d+\. `docs/UI-UX-ELEMENTS\.md`')
dod_exit: 0
dod_assert: A1–A5 schema/ban mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
