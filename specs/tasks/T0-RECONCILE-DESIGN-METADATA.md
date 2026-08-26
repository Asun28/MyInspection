---
id: T0-RECONCILE-DESIGN-METADATA
title: 建立 Field Ledger 可机读设计令牌与组件注册表
depends_on: []
parallelizable_with: [T0-RECONCILE-DATA-AUTHORITY, T0-RECONCILE-LESSONS]
status: todo
branch: T0-RECONCILE-DESIGN-METADATA
worktree: C:\wt\T0-RECONCILE-DESIGN-METADATA
source_ref: 235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf:context/DESIGN.md
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、生成资源或把 skeleton 当成生产视觉先例
  - 引入联网字体、图标或设计依赖
non_goals:
  - 页面旅程与 Compose 实现
acceptance:
  - "A1 12 顶层键；非组件 metadata 全内容 hash"
  - "A2 56 colors、12 typography；组件非颜色语义"
  - "A3 17 geometry 与 48/56/16px 固定值"
  - "A4 icon/interaction/motion 嵌套 schema exact"
  - "A5 81 component blocks 全内容固定且含 compose/codeName/states"
dod_command: $r=Get-Content 'context/DESIGN.md' -Raw;$src=&git show '235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf:context/DESIGN.md'|Out-String;if($LASTEXITCODE-ne0){throw 'source'};$base=&git show '13f6e809b345c01aa69d4d7090a52f404b96e1df:context/DESIGN.md'|Out-String;if($LASTEXITCODE-ne0){throw 'base'};$f=[regex]::Match($r,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;function B($n,$x){$p=if($x){'(?ms)^'+[regex]::Escape($n)+':\r?\n(.*?)(?=^'+[regex]::Escape($x)+':$)'}else{'(?ms)^'+[regex]::Escape($n)+':\r?\n(.*)\z'};[regex]::Match($f,$p).Groups[1].Value};function K($n,$x){@([regex]::Matches((B $n $x),'(?m)^  ([^ :\r\n][^:\r\n]*):')|%{$_.Groups[1].Value})};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};$top=@([regex]::Matches($f,'(?m)^([^ \r\n][^:\r\n]*):')|%{$_.Groups[1].Value});Exact $top ('version,name,description,colors,dark-colors,typography,rounded,spacing,iconography,interaction,motion,components'-split',');$c='primary,on-primary,primary-container,on-primary-container,secondary,on-secondary,secondary-container,on-secondary-container,tertiary,on-tertiary,tertiary-container,on-tertiary-container,surface,surface-container-low,surface-container,surface-container-high,on-surface,on-surface-variant,outline,outline-variant,error,on-error,error-container,on-error-container,privacy,on-privacy,privacy-container,on-privacy-container'-split',';foreach($n in 'colors','dark-colors'){$x=if($n-eq'colors'){'dark-colors'}else{'typography'};Exact (K $n $x) $c};$roles='display-md,headline-lg,headline-md,title-lg,title-md,body-lg,body-md,body-sm,label-lg,label-md,label-sm,data-lg'-split',';Exact (K typography rounded) $roles;foreach($z in @(@('rounded','spacing','none,sm,md,lg,xl,full'),@('spacing','iconography','base,xs,sm,md,lg,xl,2xl,3xl,touch,action,screen-gutter'))){$e=$z[2]-split',';Exact (K $z[0] $z[1]) $e};Exact (K iconography interaction) ('family,defaultStyle,selectedStyle,sizes,strokeWeight'-split',');$ib=B iconography interaction;$sz=[regex]::Match($ib,'(?ms)^  sizes:\r?\n(.*?)(?=^  [^ ]+:|\z)').Groups[1].Value;Exact (@([regex]::Matches($sz,'(?m)^    ([^ :]+):')|%{$_.Groups[1].Value})) ('sm,md,lg'-split',');Exact (K interaction motion) ('minTouchTarget,adjacentTargetGap,stateLayers,focusRingWidth,cameraScrim,cameraScrimOpacity,onCameraScrim'-split',');$ib=B interaction motion;$sl=[regex]::Match($ib,'(?ms)^  stateLayers:\r?\n(.*?)(?=^  [^ ]+:|\z)').Groups[1].Value;Exact (@([regex]::Matches($sl,'(?m)^    ([^ :]+):')|%{$_.Groups[1].Value})) ('pressedOpacity,focusedOpacity,draggedOpacity,disabledContentOpacity,disabledContainerOpacity'-split',');Exact (K motion components) ('pressFeedbackMs,stateChangeMs,expandMs,sheetEnterMs,exitMs,easingEnter,easingExit,reducedMotionTranslation'-split',');$ids='app-shell,detail-scaffold,task-scaffold,inspection-capture-scaffold,camera-capture-scaffold,modal-sheet,alert-dialog,navigation-bar,navigation-destination,top-app-bar,room-progress-strip,room-progress-segment,missing-evidence-strip,button-primary,button-secondary,button-destructive,icon-button,status-choice,privacy-chip,evidence-rail,inspection-item-card,property-summary-card,photo-evidence-tile,save-status,feedback-banner,compliance-block,input-field,phrase-sheet,confirmation-dialog,undo-snackbar,bottom-action-dock,camera-control,camera-shutter,camera-review-bar,privacy-action,divider,focus-indicator,camera-overlay-control,section-header,result-list-row,settings-row,metadata-row,overflow-menu,tooltip,state-badge,search-field,filter-chip-group,switch-row,checkbox-row,radio-group,segmented-control,choice-field,date-time-field,secure-input-field,confirmation-input,slider-field,empty-state-panel,loading-indicator,task-progress-card,validation-summary,recovery-panel,verification-receipt,history-evidence-strip,review-gap-row,summary-stat,evidence-grid,media-source-sheet,media-assignment-row,audio-evidence-control,media-preview,backup-health-card,destination-row,task-stepper,preflight-summary,disclosure-list,health-issue-row,share-boundary-callout,notice-delivery-row,compliance-check-row,remediation-suggestion-card,report-action-sheet'-split',';Exact (K components $null) $ids;$cb=B components $null;foreach($id in $ids){$b=[regex]::Match($cb,('(?ms)^  '+[regex]::Escape($id)+':\r?\n(.*?)(?=^  [^ ]+:|\z)')).Groups[1].Value;foreach($k in 'compose','codeName','states'){if($b-notmatch(('(?m)^    '+$k+':\s*\S'))){throw ('component schema '+$id)}}};function N($x){($x-replace'\r\n',"`n").TrimEnd()};$ef=[regex]::Match($src,'(?s)^---\r?\n.*?\r?\n---').Value;$body=[regex]::Match($base,'(?s)^---\r?\n.*?\r?\n---\r?\n(.*)\z').Groups[1].Value;$e=$ef+"`n"+$body;if((N $r)-cne(N $e)){throw 'file scope'}
dod_exit: 0
dod_assert: A1–A5 schema manifests + content hashes；整文件精确阶段与值变异 RED
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
