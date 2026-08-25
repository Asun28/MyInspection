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
  - "A1 单向服从：索引明确 context/DESIGN.md 是唯一 normative source，本文件只投影 coverage/owner，不得定义第二套 token、状态或行为"
  - "A2 81 Elements：五个分层清单按 13/14/19/19/16 精确列出全部 81 component ids，无遗漏、重复或未注册引用"
  - "A3 21 页面：全部 production pageId 各有目标、必需/异常 Elements；所有 Element 与 owner 引用可解析，删除任一页即 RED"
  - "A4 12 overlays + 13 states：系统/模态界面有类型、elements/约束和焦点返回；13 类状态构成闭包"
  - "A5 CLAUDE 登记索引；48dp、200%、TalkBack/focus、Reduce Motion、三档响应完整；排除 FAB/drawer/carousel/charts/global snackbar/remote telemetry"
dod_command: function Run($id){$l=Get-Content "specs/tasks/$id.md"|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-COMPONENTS';$u=Get-Content 'docs/UI-UX-ELEMENTS.md' -Raw;$cov=[regex]::Match($u,'(?ms)^## 3\..*?(?=^## 6\.)').Value;$d=Get-Content 'context/DESIGN.md' -Raw;$idx=Get-Content 'CLAUDE.md' -Raw;$board=Get-Content 'docs/TASK-BOARD.md' -Raw;function TC($h,$i){$b=[regex]::Match($u,('(?ms)^\| '+$h+' \|[^\r\n]*\r?\n\|[-: |]+\r?\n((?:\|[^\r\n]+\r?\n)+)')).Groups[1].Value;@($b-split'\r?\n'|?{$_}|%{($_.Trim('|').Split('|')[$i]).Trim().Trim('`')})};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$registry=@([regex]::Matches([regex]::Match($d,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$all=@();$sizes=13,14,19,19,16;for($i=1;$i-le5;$i++){$line=[regex]::Match($u,('(?ms)^### 2\.'+$i+'[^\r\n]*\r?\n\r?\n([^\r\n]+)')).Groups[1].Value;$ids=@([regex]::Matches($line,'`([a-z0-9-]+)`')|%{$_.Groups[1].Value});if($ids.Count-ne$sizes[$i-1]-or$ids.Count-ne@($ids|sort -Unique).Count){throw 'inventory group'};$all+=$ids};Exact $all $registry;$refs=@([regex]::Matches($cov,'`([a-z][a-z0-9-]+)(?::[A-Z_]+)?`')|%{$_.Groups[1].Value});foreach($ref in $refs){if($ref-notin$registry){throw ('element ref '+$ref)}};$pages=TC 'Page ID' 0;Exact $pages @('PROPERTIES_ROOT','SCHEDULE_ROOT','SETTINGS_ROOT','PROPERTY_HUB','INSPECTION_SETUP','INSPECTION_CAPTURE','INSPECTION_REVIEW','REPORT_EXPORT','NOTICE_CENTER','NOTICE_COMPOSE','HHC_CAPTURE','BACKUP_SETTINGS','RESTORE_TASK','QUALITY_SETTINGS','LOCAL_MEDIA_SETTINGS','HEALTH_STATUS','DIAGNOSTIC_EXPORT','LOCAL_DATA_ERASURE','REMEDIATION_SETTINGS','CAMERA_CAPTURE','CAMERA_REVIEW');foreach($p in $pages){$x=@([regex]::Match($u,('(?m)^\| `'+$p+'` \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne5-or@($x|?{-not$_}).Count){throw 'page row'};$os=@([regex]::Matches($x[4],'T\d-[A-Z0-9-]+')|%{$_.Value});if(-not$os-and$x[4]-notmatch'Shared'){throw 'owner sentinel'};foreach($o in $os){if(-not(Test-Path "specs/tasks/$o.md")-and$board-notmatch('(?m)^\| [^|]+ \| '+[regex]::Escape($o)+' \|')){throw 'owner ref'}}};$over=TC 'Surface' 0;Exact $over @('REPORT_ACTION_SHEET','THEME_MODE_SHEET','STATUS_SHEET','PHRASE_SHEET','MEDIA_SOURCE_SHEET','finalize / discard / clear / remove confirmation','restore replacement confirmation','Android permission dialog','Folder/file/create picker','PDF viewer / Sharesheet','Android app settings','Speech recognizer');foreach($n in $over){$row=[regex]::Match($u,('(?m)^\| `?'+[regex]::Escape($n)+'`? \|[^\r\n]+$')).Value;if(($row.Trim('|').Split('|')).Count-ne4-or@($row.Trim('|').Split('|')|?{-not$_.Trim()}).Count){throw 'overlay schema'}};$states=TC '状态' 0;Exact $states @('Empty','Local loading','Long-running','Inline validation','Recoverable failure','Blocking compliance/integrity','Offline','Permission denied','Low storage','Restored after interruption','Success','Destructive','External handoff');foreach($n in $states){$row=[regex]::Match($u,('(?m)^\| '+[regex]::Escape($n)+' \|[^\r\n]+$')).Value;if(($row.Trim('|').Split('|')).Count-ne3-or@($row.Trim('|').Split('|')|?{-not$_.Trim()}).Count){throw 'state schema'}};Must $u @('(?m)^> Normative source: `context/DESIGN\.md`','(?m)^## 6\. 无障碍与响应式验收$','48dp','200%','TalkBack','Reduce Motion','compact / medium / expanded','(?m)^## 7\. 明确排除的 Elements$','FAB','drawer','carousel','charts','global snackbar','remote telemetry');Must $idx @('(?m)^\d+\. `docs/UI-UX-ELEMENTS\.md`')
dod_exit: 0
dod_assert: 前置 DESIGN、81-ID 注册表、全部引用/owner、页面/overlay/state exact sets 与逐行 schema 全绿
review_gate: codex {verdict:pass}
hygiene: 组件索引是覆盖投影而非真相源；删除重复 prose 后仍可从卡定位到设计合同
doc_sync: DESIGN.md、UI-UX-ELEMENTS 与下游卡指针闭环（R5）
---

# T0-RECONCILE-UI-COVERAGE

## 产出

增加一份完整但非规范性的 UI/UX elements 覆盖索引，将页面、overlay、状态、组件 id 与 owning card 连成可检查的投影；设计细节仍只由 `context/DESIGN.md` 定义。

## 验收

执行 front matter 的 `dod_command`，并确认索引不复制 canonical token/行为正文。
