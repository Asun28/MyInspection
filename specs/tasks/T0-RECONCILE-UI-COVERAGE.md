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
  - "A2 81 Elements：五个分层清单按 13/14/19/19/16 精确列出全部 81 component ids，无遗漏、重复或未注册 id"
  - "A3 21 页面：全部 production pageId 各有目标、必需 Elements、条件/异常 Elements；owner 可追到 Task Board/card，删除任一页即 RED"
  - "A4 12 overlays + 13 states：系统/模态界面各有类型、elements/约束和焦点返回；empty/loading/long-running/validation/failure/compliance/offline/permission/storage/restore/success/destructive/handoff 构成 13 条闭包"
  - "A5 索引、可访问/响应与排除：CLAUDE 权威索引登记 UI-UX-ELEMENTS；48dp、200%、TalkBack/focus、Reduce Motion、compact/medium/expanded 完整；明确排除 FAB/drawer/carousel/charts/global snackbar/remote telemetry"
dod_command: $u=Get-Content 'docs/UI-UX-ELEMENTS.md' -Raw;$d=Get-Content 'context/DESIGN.md' -Raw;$idx=Get-Content 'CLAUDE.md' -Raw;function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count -ne $e.Count -or $a.Count -ne @($a|Sort-Object -Unique).Count -or (Compare-Object ($a|Sort-Object) ($e|Sort-Object))){throw 'exact-set mismatch'}};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not $m.Success -or [regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$registry=@([regex]::Matches([regex]::Match($d,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});$all=@();$sizes=13,14,19,19,16;for($i=1;$i-le5;$i++){$line=[regex]::Match($u,('(?ms)^### 2\.'+$i+'[^\r\n]*\r?\n\r?\n([^\r\n]+)')).Groups[1].Value;$ids=@([regex]::Matches($line,'`([a-z0-9-]+)`')|%{$_.Groups[1].Value});if($ids.Count-ne$sizes[$i-1]-or $ids.Count-ne@($ids|sort -Unique).Count){throw 'inventory group'};$all+=$ids};Exact $all $registry;$pages=@([regex]::Matches($u,'(?m)^\| `([A-Z_]+)` \|')|%{$_.Groups[1].Value});Exact $pages @('PROPERTIES_ROOT','SCHEDULE_ROOT','SETTINGS_ROOT','PROPERTY_HUB','INSPECTION_SETUP','INSPECTION_CAPTURE','INSPECTION_REVIEW','REPORT_EXPORT','NOTICE_CENTER','NOTICE_COMPOSE','HHC_CAPTURE','BACKUP_SETTINGS','RESTORE_TASK','QUALITY_SETTINGS','LOCAL_MEDIA_SETTINGS','HEALTH_STATUS','DIAGNOSTIC_EXPORT','LOCAL_DATA_ERASURE','REMEDIATION_SETTINGS','CAMERA_CAPTURE','CAMERA_REVIEW');foreach($p in $pages){$row=[regex]::Match($u,('(?m)^\| `'+$p+'` \|[^\r\n]+$')).Value;if(($row.Trim('|').Split('|')).Count-ne5-or ($row.Trim('|').Split('|')|?{-not $_.Trim()}).Count){throw 'page row fields'}};$over=@([regex]::Matches($u,'(?m)^\| (`?(?:REPORT_ACTION_SHEET|THEME_MODE_SHEET|STATUS_SHEET|PHRASE_SHEET|MEDIA_SOURCE_SHEET)`?|finalize / discard / clear / remove confirmation|restore replacement confirmation|Android permission dialog|Folder/file/create picker|PDF viewer / Sharesheet|Android app settings|Speech recognizer) \|')|%{$_.Groups[1].Value.Trim('`')});Exact $over @('REPORT_ACTION_SHEET','THEME_MODE_SHEET','STATUS_SHEET','PHRASE_SHEET','MEDIA_SOURCE_SHEET','finalize / discard / clear / remove confirmation','restore replacement confirmation','Android permission dialog','Folder/file/create picker','PDF viewer / Sharesheet','Android app settings','Speech recognizer');$states=@([regex]::Matches($u,'(?m)^\| (Empty|Local loading|Long-running|Inline validation|Recoverable failure|Blocking compliance/integrity|Offline|Permission denied|Low storage|Restored after interruption|Success|Destructive|External handoff) \|')|%{$_.Groups[1].Value});Exact $states @('Empty','Local loading','Long-running','Inline validation','Recoverable failure','Blocking compliance/integrity','Offline','Permission denied','Low storage','Restored after interruption','Success','Destructive','External handoff');Must $u @('(?m)^> Normative source: `context/DESIGN\.md`','(?m)^## 6\. 无障碍与响应式验收$','48dp','200%','TalkBack','Reduce Motion','compact / medium / expanded','(?m)^## 7\. 明确排除的 Elements$','FAB','drawer','carousel','charts','global snackbar','remote telemetry');Must $idx @('(?m)^\d+\. `docs/UI-UX-ELEMENTS\.md`');foreach($p in $pages){$x=@([regex]::Match($u,('(?m)^\| `'+$p+'` \|[^\r\n]+$')).Value.Trim('|').Split('|')|%{$_.Trim()});if($x.Count-ne5-or$x[4]-notmatch'(`T[0-9]-|Shared)'){throw 'page owner'}};foreach($n in $over){$row=[regex]::Match($u,('(?m)^\| `?'+[regex]::Escape($n)+'`? \|[^\r\n]+$')).Value;if(($row.Trim('|').Split('|')).Count-ne4-or($row.Trim('|').Split('|')|?{-not$_.Trim()}).Count){throw 'overlay schema'}};foreach($n in $states){$row=[regex]::Match($u,('(?m)^\| '+[regex]::Escape($n)+' \|[^\r\n]+$')).Value;if(($row.Trim('|').Split('|')).Count-ne3-or($row.Trim('|').Split('|')|?{-not$_.Trim()}).Count){throw 'state schema'}}
dod_exit: 0
dod_assert: A1–A5 的 exact sets、逐块语义和删除变异全绿；删改任一要求即 RED
review_gate: codex {verdict:pass}
hygiene: 组件索引是覆盖投影而非真相源；删除重复 prose 后仍可从卡定位到设计合同
doc_sync: DESIGN.md、UI-UX-ELEMENTS 与下游卡指针闭环（R5）
---

# T0-RECONCILE-UI-COVERAGE

## 产出

增加一份完整但非规范性的 UI/UX elements 覆盖索引，将页面、overlay、状态、组件 id 与 owning card 连成可检查的投影；设计细节仍只由 `context/DESIGN.md` 定义。

## 验收

执行 front matter 的 `dod_command`，并确认索引不复制 canonical token/行为正文。
