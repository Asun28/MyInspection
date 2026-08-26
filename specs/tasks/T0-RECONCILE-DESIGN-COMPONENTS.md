---
id: T0-RECONCILE-DESIGN-COMPONENTS
title: 补齐 Field Ledger 组件合同、对比度、动效与无障碍规则
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENTS
worktree: C:\wt\T0-RECONCILE-DESIGN-COMPONENTS
source_ref: 34a6b028dda672275bb6162b40cbaa122c6045f8:context/DESIGN.md
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、组件 id 或产品范围
  - 颜色-only、无限动画、layout shift 或危险操作无确认
non_goals:
  - 页面覆盖索引或 Compose 实现
acceptance:
  - "A1 contrast/token source exact"
  - "A2 九族/81 ids exact"
  - "A3 九矩阵全内容 hash；row/machine states exact"
  - "A4 scoped motion + normative bans"
  - "A5 A11y/destructive contracts"
dod_command: $r=Get-Content 'context/DESIGN.md' -Raw;$src=&git show '34a6b028dda672275bb6162b40cbaa122c6045f8:context/DESIGN.md'|Out-String;if($LASTEXITCODE-ne0){throw 'source'};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};$defs=[ordered]@{'Navigation and structure component matrix'='app-shell,detail-scaffold,task-scaffold,inspection-capture-scaffold,camera-capture-scaffold,modal-sheet,alert-dialog,navigation-bar,navigation-destination,top-app-bar,room-progress-strip,room-progress-segment,missing-evidence-strip,bottom-action-dock,divider,property-summary-card';'Evidence and input component matrix'='button-primary,button-secondary,button-destructive,icon-button,inspection-item-card,evidence-rail,status-choice,input-field,phrase-sheet,photo-evidence-tile,privacy-chip,privacy-action';'Feedback and decision component matrix'='save-status,feedback-banner,compliance-block,undo-snackbar,confirmation-dialog,focus-indicator';'Camera component matrix'='camera-control,camera-shutter,camera-overlay-control,camera-review-bar';'Structure, list, and discovery component matrix'='section-header,result-list-row,settings-row,metadata-row,overflow-menu,tooltip,state-badge';'Form and selection component matrix'='search-field,filter-chip-group,switch-row,checkbox-row,radio-group,segmented-control,choice-field,date-time-field,secure-input-field,confirmation-input,slider-field,validation-summary';'State, progress, and recovery component matrix'='empty-state-panel,loading-indicator,task-progress-card,recovery-panel,verification-receipt';'History, evidence, and media component matrix'='history-evidence-strip,review-gap-row,summary-stat,evidence-grid,media-source-sheet,media-assignment-row,audio-evidence-control,media-preview';'Backup, report, health, and compliance component matrix'='backup-health-card,destination-row,task-stepper,preflight-summary,disclosure-list,health-issue-row,share-boundary-callout,notice-delivery-row,compliance-check-row,remediation-suggestion-card,report-action-sheet'};$f=[regex]::Match($r,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;$cb=[regex]::Match($f,'(?ms)^components:\r?\n(.*)\z').Groups[1].Value;$registry=@([regex]::Matches($cb,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$all=@();foreach($h in $defs.Keys){$ms=[regex]::Matches($r,('(?ms)^### '+[regex]::Escape($h)+'\r?\n(.*?)(?=^### |^## |\z)'));if($ms.Count-ne1){throw ('matrix '+$h)};$b=$ms[0].Groups[1].Value;$rows=@([regex]::Matches($b,'(?m)^\| `([a-z0-9-]+)` \|[^\r\n]+$'));$ids=@($rows|%{$_.Groups[1].Value});Exact $ids ($defs[$h]-split',');foreach($row in $rows){$id=$row.Groups[1].Value;$c=@([regex]::Matches($row.Value.Trim('|'),'(?:`[^`]*`|[^|])+')|%{$_.Value.Trim()});if($c.Count-ne6-or@($c|?{-not$_}).Count){throw ('row '+$id)};$all+=$id}};if($all.Count-ne81){throw 'row count'};Exact $all $registry;$tail="Infinite pulse and indefinite ambient animation are forbidden.`nMotion must not cause layout shift.";$e=$src-replace'(?m)^## Accessibility contract\r?$',("$tail`n`n## Accessibility contract");function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N $r)-cne(N $e)){throw 'file scope'}
dod_exit: 0
dod_assert: A1–A5 manifests + matrix；整文件精确目标与非组件区变异 RED
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
