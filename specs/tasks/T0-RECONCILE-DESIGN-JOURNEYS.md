---
id: T0-RECONCILE-DESIGN-JOURNEYS
title: 补齐 Field Ledger 信息架构、导航、恢复与离线隐私旅程
depends_on: [T0-RECONCILE-DESIGN-METADATA]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEYS
worktree: C:\wt\T0-RECONCILE-DESIGN-JOURNEYS
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码或机读组件 id
  - 把需要网络的 provider 行为伪装为核心离线流程
non_goals:
  - 逐组件视觉合同、motion token 或 Compose 实现
  - 新增 Dashboard、Reports 顶级页或导航抽屉
acceptance:
  - "A1 页面闭包：21 个 production pageId 各有 route、page type、parent、bottom-nav 可见性与可解析 owner；顶级仅 Properties/Schedule/Settings"
  - "A2 容器与导航：9 个 page type、三栈 bottom navigation、inset owner 与 PUSH/POP/sheet/dialog/camera transitions 均有确定行为"
  - "A3 路由与 overlay：核心和支持触发声明 precondition/action/target/transition/退出/焦点；目标引用可解析，6 类 overlay 拦截无缺口"
  - "A4 恢复与焦点：interaction state 表、10 个 focus lifecycle 事件及 resume/save barrier/missing-item/finalize/report handoff 有唯一恢复动作"
  - "A5 离线与数据保护：6 类 capability 与 10 个备份状态完整；两个下游 plan_ref 锚点唯一，外部失败不阻断巡检/finalize/历史/PDF"
dod_command: function Run($id){$l=Get-Content "specs/tasks/$id.md"|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-METADATA';$r=Get-Content 'context/DESIGN.md' -Raw;$board=Get-Content 'docs/TASK-BOARD.md' -Raw;function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function T($h,$i,$n){$b=[regex]::Match($r,('(?ms)^\| '+$h+' \|\r?\n\|[-: |]+\r?\n((?:\|[^\r\n]+\r?\n)+)')).Groups[1].Value;$rows=@($b-split'\r?\n'|?{$_});foreach($row in $rows){$c=@($row.Trim('|').Split('|')|%{$_.Trim()});if($c.Count-ne$n-or@($c|?{-not$_}).Count){throw 'table schema'}};@($rows|%{$v=($_.Trim('|').Split('|')[$i]).Trim();if($v-match'^`([^`]*)`$'){$Matches[1]}else{$v}})};$p=T 'Level \| `pageId` \| Route \| Page type \| Parent \| Bottom nav \| Owner' 1 7;Exact $p @('PROPERTIES_ROOT','SCHEDULE_ROOT','SETTINGS_ROOT','PROPERTY_HUB','INSPECTION_SETUP','INSPECTION_CAPTURE','INSPECTION_REVIEW','REPORT_EXPORT','NOTICE_CENTER','NOTICE_COMPOSE','HHC_CAPTURE','BACKUP_SETTINGS','RESTORE_TASK','QUALITY_SETTINGS','LOCAL_MEDIA_SETTINGS','HEALTH_STATUS','DIAGNOSTIC_EXPORT','LOCAL_DATA_ERASURE','REMEDIATION_SETTINGS','CAMERA_CAPTURE','CAMERA_REVIEW');$types=@('ROOT_STATIC','HUB_STATIC','PUSH_DETAIL','STREAM_CAPTURE','FULLSCREEN_TASK','CAMERA_TASK','MODAL_SHEET','ALERT_DIALOG','SYSTEM_SURFACE');Exact (T 'Page type \| Container \| Navigation model \| Persistent state \| Exit rule' 0 5) $types;Exact (T 'Overlay or state \| Scrim tap \| Swipe down \| System Back \| Explicit action' 0 5) @('Choice/action sheet','Phrase sheet','Destructive confirmation','Finalize confirmation','Camera with uncommitted photo','Restore while `COMMITTING`');Exact (T 'Event \| Focus destination \| Announcement' 0 3) @('Push / deep-link entry','Pop','Top-level switch','Active destination reselect','Sheet/dialog opens','Sheet/dialog closes','Camera Use photo','Missing-item jump','Dynamic insertion/removal','Save failure blocks exit');Exact (T 'Capability \| Offline presentation \| Core-flow effect' 0 3) @('Local inspection, history, rules, finalize, PDF','Voice without an installed offline recognizer','Local/USB backup','Cloud SAF backup/restore','Offline remediation seed match','Remote remediation');Exact (T 'State \| Required message \| Primary action' 0 3) @('NOT_CONFIGURED','READY','RUNNING','VERIFIED','PROVIDER_UNAVAILABLE','AUTHORIZATION_REVOKED','NEEDS_UNLOCK','NEEDS_PASSPHRASE','LOW_STORAGE','FAILED');foreach($id in $p){$c=@([regex]::Match($r,('(?m)^\| [123] \| `'+$id+'` \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});$pt=$c[3].Trim('`');$pa=$c[4].Trim('`');if($pt-notin$types-or($pa-ne'—'-and$pa-notin$p)){throw 'page ref'};$owners=@([regex]::Matches($c[6],'T\d-[A-Z0-9-]+')|%{$_.Value});if(-not$owners-and$c[6]-ne'Shared settings shell'){throw 'owner sentinel'};foreach($o in $owners){if(-not(Test-Path "specs/tasks/$o.md")-and$board-notmatch('(?m)^\| [^|]+ \| '+[regex]::Escape($o)+' \|')){throw 'owner ref'}}};$allowed=@($p)+@('FINALIZE_CONFIRMATION','REPORT_ACTION_SHEET','THEME_MODE_SHEET','STATUS_SHEET','PHRASE_SHEET');foreach($z in @(@('Core routes','Supporting routes and overlays',6,15,3),@('Supporting routes and overlays','Overlay dismissal and interception',5,15,2))){$b=[regex]::Match($r,('(?ms)^### '+$z[0]+'\r?\n(.*?)(?=^### '+$z[1]+')')).Groups[1].Value;$q=@([regex]::Matches($b,'(?m)^\| (?!-{3})[^\r\n]+\|$')|?{$_.Value-notmatch'^\| Trigger source'});$bad=@($q|?{($_.Value.Trim('|').Split('|')).Count-ne$z[2]-or@($_.Value.Trim('|').Split('|')|?{-not$_.Trim()}).Count});if($q.Count-ne$z[3]-or$bad.Count){throw 'route schema'};foreach($row in $q){$target=$row.Value.Trim('|').Split('|')[$z[4]];foreach($m in [regex]::Matches($target,'`([A-Z][A-Z0-9_]+)(?:\([^`]*\))?`')){if($m.Groups[1].Value-notin$allowed){throw 'target ref'}}}};foreach($h in @('(?m)^## Primary inspection journey$','(?m)^### Offline and data-protection experience$')){if([regex]::Matches($r,$h).Count-ne1){throw 'plan anchor'}}
dod_exit: 0
dod_assert: 前置 metadata、A1–A5 exact sets、全列 schema、引用解析与唯一锚点全绿；任一增删伪造即 RED
review_gate: codex {verdict:pass}
hygiene: 同一页面只有一个路由/返回合同；离线正常态不使用持续错误横幅
doc_sync: 与机读组件 id 和当前产品需求保持一致（R5）
---

# T0-RECONCILE-DESIGN-JOURNEYS

## 产出

把 Field Ledger 从视觉语言补全为可实现的应用体验合同：页面类型、导航栈、触发入口、焦点恢复、证据采集主流程，以及离线安全/备份/恢复/清除的用户旅程。

## 验收

执行 front matter 的 `dod_command`，并确认所有路线都有返回语义、所有失败状态都有恢复动作。
