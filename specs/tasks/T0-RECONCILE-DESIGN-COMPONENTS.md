---
id: T0-RECONCILE-DESIGN-COMPONENTS
title: 补齐 Field Ledger 组件合同、对比度、动效与无障碍规则
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENTS
worktree: C:\wt\T0-RECONCILE-DESIGN-COMPONENTS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、组件 id 或产品范围
  - 颜色-only、无限动画、layout shift 或危险操作无确认
non_goals:
  - 页面覆盖索引或 Compose 实现
acceptance:
  - "A1 4.50:1/7.00:1/3.00:1；正文不定义第二套 token"
  - "A2 九个 matrix 分族 exact sets，恰好覆盖 81 registry ids"
  - "A3 每行 states 覆盖对应机读 states，含 empty/loading/error/disabled/read-only/commit"
  - "A4 100/150/180/200/250ms、reduced-motion；明确禁止 infinite pulse/layout shift"
  - "A5 48dp、200%、TalkBack、焦点回退和危险操作硬合同"
dod_command: function Run($id){$p=@("specs/tasks/$id.md","specs/archive/tasks/$id.md")|?{Test-Path $_}|select -First 1;if(-not$p){throw 'predecessor'};$l=Get-Content $p|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-JOURNEYS';$r=Get-Content 'context/DESIGN.md' -Raw;function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};$defs=[ordered]@{'Navigation and structure component matrix'='app-shell,detail-scaffold,task-scaffold,inspection-capture-scaffold,camera-capture-scaffold,modal-sheet,alert-dialog,navigation-bar,navigation-destination,top-app-bar,room-progress-strip,room-progress-segment,missing-evidence-strip,bottom-action-dock,divider,property-summary-card';'Evidence and input component matrix'='button-primary,button-secondary,button-destructive,icon-button,inspection-item-card,evidence-rail,status-choice,input-field,phrase-sheet,photo-evidence-tile,privacy-chip,privacy-action';'Feedback and decision component matrix'='save-status,feedback-banner,compliance-block,undo-snackbar,confirmation-dialog,focus-indicator';'Camera component matrix'='camera-control,camera-shutter,camera-overlay-control,camera-review-bar';'Structure, list, and discovery component matrix'='section-header,result-list-row,settings-row,metadata-row,overflow-menu,tooltip,state-badge';'Form and selection component matrix'='search-field,filter-chip-group,switch-row,checkbox-row,radio-group,segmented-control,choice-field,date-time-field,secure-input-field,confirmation-input,slider-field,validation-summary';'State, progress, and recovery component matrix'='empty-state-panel,loading-indicator,task-progress-card,recovery-panel,verification-receipt';'History, evidence, and media component matrix'='history-evidence-strip,review-gap-row,summary-stat,evidence-grid,media-source-sheet,media-assignment-row,audio-evidence-control,media-preview';'Backup, report, health, and compliance component matrix'='backup-health-card,destination-row,task-stepper,preflight-summary,disclosure-list,health-issue-row,share-boundary-callout,notice-delivery-row,compliance-check-row,remediation-suggestion-card,report-action-sheet'};$f=[regex]::Match($r,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;$cb=[regex]::Match($f,'(?ms)^components:\r?\n(.*)\z').Groups[1].Value;$registry=@([regex]::Matches($cb,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$all=@();foreach($h in $defs.Keys){$b=[regex]::Match($r,('(?ms)^### '+[regex]::Escape($h)+'\r?\n(.*?)(?=^### |^## |\z)')).Groups[1].Value;$rows=@([regex]::Matches($b,'(?m)^\| `([a-z0-9-]+)` \|[^\r\n]+$'));$ids=@($rows|%{$_.Groups[1].Value});Exact $ids ($defs[$h]-split',');foreach($row in $rows){$id=$row.Groups[1].Value;$c=@([regex]::Matches($row.Value.Trim('|'),'(?:`[^`]*`|[^|])+')|%{$_.Value.Trim()});if($c.Count-ne6-or@($c|?{-not$_}).Count){throw ('row '+$id)};$mb=[regex]::Match($cb,('(?ms)^  '+[regex]::Escape($id)+':\r?\n(.*?)(?=^  [^ ]+:|\z)')).Groups[1].Value;$sm=[regex]::Match($mb,'(?m)^    states: \[([^\]]+)\]$');if(-not$sm.Success){throw ('registered states '+$id)};foreach($state in $sm.Groups[1].Value.Split(',')){$p=[regex]::Escape($state.Trim())-replace'_','[ _/-]';if(($c[2])-notmatch('(?i)'+$p)){throw ('matrix state '+$id+' '+$state)}};$all+=$id}};if($all.Count-ne81){throw 'row count'};Exact $all $registry;$body=$r.Substring([regex]::Match($r,'(?s)^---\r?\n.*?\r?\n---').Length);if([regex]::IsMatch($body,'(?m)^(colors|dark-colors|typography|rounded|spacing|iconography|interaction|motion|components):\s*$')-or[regex]::Matches($r,'(?m)^---$').Count-ne2){throw 'second token source'};foreach($term in 'infinite pulse','layout shift'){$m=@([regex]::Matches($body,('(?im)^[^\r\n]*\b'+[regex]::Escape($term)+'\b[^\r\n]*$')));if($m.Count-ne1-or$m[0].Value-notmatch'(?i)(forbidden|must not|never)'){throw ('normative ban '+$term)}};foreach($p in @('(?m)^### Contrast threshold contract$','`4\.50:1`','`7\.00:1`','`3\.00:1`','(?m)^### Element completeness gate$','(?m)^## Motion and haptics$','reduced motion','(?m)^## Accessibility contract$','`48dp`','`200%`','TalkBack','100ms','150ms','180ms','200ms','250ms','先预览影响，再明确动词/输入确认，执行中禁止重复/Back','Status, save, camera, and compliance changes are announced as state changes')){if(-not[regex]::IsMatch($r,$p)){throw ('contract '+$p)}}
dod_exit: 0
dod_assert: 九族 exact sets、81 行逐项机读 states、单一 token 源、规范性禁令与 A11y 合同全绿
review_gate: codex {verdict:pass}
hygiene: prose 只引用机读 token/组件 id
doc_sync: 与前两张设计卡组成完整 DESIGN.md（R5）
---
