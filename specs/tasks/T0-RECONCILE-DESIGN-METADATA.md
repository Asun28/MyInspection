---
id: T0-RECONCILE-DESIGN-METADATA
title: 建立 Field Ledger 可机读设计令牌与组件注册表
depends_on: []
parallelizable_with: [T0-RECONCILE-DATA-AUTHORITY, T0-RECONCILE-LESSONS]
status: todo
branch: T0-RECONCILE-DESIGN-METADATA
worktree: C:\wt\T0-RECONCILE-DESIGN-METADATA
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、生成资源或把 skeleton 当成生产视觉先例
  - 引入需要联网才能解析的字体、图标或设计依赖
non_goals:
  - 页面旅程、导航/恢复语义和完整性矩阵
  - 实现 Compose 组件
acceptance:
  - "A1 顶层 schema：front matter 恰有 version/name/description 与 colors/dark-colors/typography/rounded/spacing/iconography/interaction/motion/components 共 12 个顶层键"
  - "A2 颜色和文字：light/dark 各 28 个同名语义颜色角色（共 56 行），并完整登记 12 个 typography roles；状态语义不得只靠颜色"
  - "A3 几何和触控：rounded 6 项、spacing 11 项（共 17 个 px token），明确 48px touch、56px action 与 16px screen gutter"
  - "A4 图标/交互/动效：iconography 5 键、interaction 7 键、motion 8 键共 20 键，含 focus ring、camera scrim、reduced-motion translation 和确定时长"
  - "A5 组件注册表：81 个批准 component id 各出现且仅出现一次；删除、重命名、重复任一 id 均使 exact-count/allowlist 断言 RED"
dod_command: $r=Get-Content 'context/DESIGN.md' -Raw; function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count -ne $e.Count -or $a.Count -ne @($a|Sort-Object -Unique).Count -or (Compare-Object ($a|Sort-Object) ($e|Sort-Object))){throw 'exact-set mismatch'}}; function Keys($n,$next){$m=[regex]::Match($r,('(?ms)^'+[regex]::Escape($n)+':\r?\n(.*?)(?=^'+[regex]::Escape($next)+':?$)'));@([regex]::Matches($m.Groups[1].Value,'(?m)^  ([A-Za-z0-9-]+):')|ForEach-Object{$_.Groups[1].Value})}; $colors='primary','on-primary','primary-container','on-primary-container','secondary','on-secondary','secondary-container','on-secondary-container','tertiary','on-tertiary','tertiary-container','on-tertiary-container','surface','surface-container-low','surface-container','surface-container-high','on-surface','on-surface-variant','outline','outline-variant','error','on-error','error-container','on-error-container','privacy','on-privacy','privacy-container','on-privacy-container'; Exact @([regex]::Matches($r,'(?m)^([a-z][a-z-]*):')|%{$_.Groups[1].Value}) @('version','name','description','colors','dark-colors','typography','rounded','spacing','iconography','interaction','motion','components'); Exact (Keys 'colors' 'dark-colors') $colors; Exact (Keys 'dark-colors' 'typography') $colors; Exact (Keys 'typography' 'rounded') @('display-md','headline-lg','headline-md','title-lg','title-md','body-lg','body-md','body-sm','label-lg','label-md','label-sm','data-lg'); Exact (Keys 'rounded' 'spacing') @('none','sm','md','lg','xl','full'); Exact (Keys 'spacing' 'iconography') @('base','xs','sm','md','lg','xl','2xl','3xl','touch','action','screen-gutter'); Exact (Keys 'iconography' 'interaction') @('family','defaultStyle','selectedStyle','sizes','strokeWeight'); Exact (Keys 'interaction' 'motion') @('minTouchTarget','adjacentTargetGap','stateLayers','focusRingWidth','cameraScrim','cameraScrimOpacity','onCameraScrim'); Exact (Keys 'motion' 'components') @('pressFeedbackMs','stateChangeMs','expandMs','sheetEnterMs','exitMs','easingEnter','easingExit','reducedMotionTranslation'); Exact (Keys 'components' '---') @('app-shell','detail-scaffold','task-scaffold','inspection-capture-scaffold','camera-capture-scaffold','modal-sheet','alert-dialog','navigation-bar','navigation-destination','top-app-bar','room-progress-strip','room-progress-segment','missing-evidence-strip','button-primary','button-secondary','button-destructive','icon-button','status-choice','privacy-chip','evidence-rail','inspection-item-card','property-summary-card','photo-evidence-tile','save-status','feedback-banner','compliance-block','input-field','phrase-sheet','confirmation-dialog','undo-snackbar','bottom-action-dock','camera-control','camera-shutter','camera-review-bar','privacy-action','divider','focus-indicator','camera-overlay-control','section-header','result-list-row','settings-row','metadata-row','overflow-menu','tooltip','state-badge','search-field','filter-chip-group','switch-row','checkbox-row','radio-group','segmented-control','choice-field','date-time-field','secure-input-field','confirmation-input','slider-field','empty-state-panel','loading-indicator','task-progress-card','validation-summary','recovery-panel','verification-receipt','history-evidence-strip','review-gap-row','summary-stat','evidence-grid','media-source-sheet','media-assignment-row','audio-evidence-control','media-preview','backup-health-card','destination-row','task-stepper','preflight-summary','disclosure-list','health-issue-row','share-boundary-callout','notice-delivery-row','compliance-check-row','remediation-suggestion-card','report-action-sheet');foreach($p in @('(?m)^  touch: 48px$','(?m)^  action: 56px$','(?m)^  screen-gutter: 16px$','(?m)^  focusRingWidth: 3px$','(?m)^  cameraScrim: \"#000000\"$','(?m)^  cameraScrimOpacity: 0\.64$','(?m)^  reducedMotionTranslation: 0px$','(?m)^  pressFeedbackMs: 100$','(?m)^  stateChangeMs: 180$','(?m)^  expandMs: 200$','(?m)^  sheetEnterMs: 250$','(?m)^  exitMs: 150$')){$m=[regex]::Match($r,$p);if(-not$m.Success-or[regex]::IsMatch($r.Remove($m.Index,$m.Length),$p)){throw 'token value'}}
dod_exit: 0
dod_assert: A1–A5 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: 元数据键稳定、无同义重复；视觉状态同时具备非颜色语义
doc_sync: 本卡只建立机读层，后续两卡补充行为合同（R5）
---

# T0-RECONCILE-DESIGN-METADATA

## 产出

在既有 `context/DESIGN.md` 前加入可机读的 Field Ledger 设计系统：颜色、排版、间距、形状、动效、触控/无障碍和具名组件状态。保持单文件真相源，不在本卡扩写页面旅程。

## 验收

执行 front matter 的 `dod_command`，并确认本卡净 diff 低于 R3 预算。
