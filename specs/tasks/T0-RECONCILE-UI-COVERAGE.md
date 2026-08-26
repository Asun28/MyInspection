---
id: T0-RECONCILE-UI-COVERAGE
title: 建立 UI Elements 覆盖索引
depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX, T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE]
status: todo
branch: T0-RECONCILE-UI-COVERAGE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE
source_ref: b3158d11c76b02505f71b257159b5e608596a066:docs/UI-UX-ELEMENTS.md
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
  - "A1 projection-only"
  - "A2 81-ID hash"
  - "A3 21 page map/owner"
  - "A4 12 overlay/13 state maps"
  - "A5 A11y/responsive/exclusions"
dod_command: function Run($id){$p=@("specs/tasks/$id.md","specs/archive/tasks/$id.md")|?{Test-Path $_}|select -First 1;$l=Get-Content $p|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-COMPONENTS';$u=Get-Content 'docs/UI-UX-ELEMENTS.md' -Raw;$src=&git show 'b3158d11c76b02505f71b257159b5e608596a066:docs/UI-UX-ELEMENTS.md'|Out-String;if($LASTEXITCODE-ne0){throw 'source'};$d=Get-Content 'context/DESIGN.md' -Raw;$idx=Get-Content 'CLAUDE.md' -Raw;$board=Get-Content 'docs/TASK-BOARD.md' -Raw;function TC($r,$h,$i){$b=[regex]::Match($r,('(?ms)^\| '+$h+' \|[^\r\n]*\r?\n\|[-: |]+\r?\n((?:\|[^\r\n]+\r?\n)+)')).Groups[1].Value;@($b-split'\r?\n'|?{$_}|%{($_.Trim('|').Split('|')[$i]).Trim().Trim('`')})};function Exact($a,$e){$a=@($a);$e=@($e);if($a.Count-ne$e.Count-or$a.Count-ne@($a|sort -Unique).Count-or(Compare-Object ($a|sort) ($e|sort))){throw 'exact-set'}};function Row($r,$p){$m=[regex]::Matches($r,$p);if($m.Count-ne1){throw 'row'};@($m[0].Value.Trim().Trim('|').Split('|')|%{$_.Trim()})};function Table($r,$h){$m=[regex]::Matches($r,('(?ms)^\| '+$h+' \|[^\r\n]*\r?\n\|[-: |]+\r?\n(?:\|[^\r\n]+\r?\n)+'));if($m.Count-ne1){throw 'table'};($m[0].Value-replace'\r\n',"`n")};function Must($s,$ps){foreach($p in $ps){$m=[regex]::Match($s,$p);if(-not$m.Success-or[regex]::IsMatch($s.Remove($m.Index,$m.Length),$p)){throw ('missing/non-unique '+$p)}}};$registry=@([regex]::Matches([regex]::Match($d,'(?ms)^components:\r?\n(.*?)(?=^---$)').Groups[1].Value,'(?m)^  ([a-z0-9-]+):')|%{$_.Groups[1].Value});function Refs($s){$a=@([regex]::Matches($s,'`([a-z][a-z0-9-]+)(?::[A-Z_]+)?`')|%{$_.Groups[1].Value});if(-not$a.Count){throw 'missing element'};foreach($x in $a){if($x-notin$registry){throw ('element ref '+$x)}}};$all=@();$iv=@();$sizes=13,14,19,19,16;for($i=1;$i-le5;$i++){$line=[regex]::Match($u,('(?ms)^### 2\.'+$i+'[^\r\n]*\r?\n\r?\n([^\r\n]+)')).Groups[1].Value;$iv+=$line;$ids=@([regex]::Matches($line,'`([a-z0-9-]+)`')|%{$_.Groups[1].Value});if($ids.Count-ne$sizes[$i-1]-or$ids.Count-ne@($ids|sort -Unique).Count){throw 'inventory group'};$all+=$ids};Exact $all $registry;$sv=@();1..5|%{$m=[regex]::Matches($src,('(?ms)^### 2\.'+$_+'[^\r\n]*\r?\n\r?\n([^\r\n]+)'));if($m.Count-ne1){throw 'source inventory'};$sv+=$m[0].Groups[1].Value};$v=(($iv-join"`n")-replace'\r\n',"`n");if(($sv-join"`n")-cne$v){throw 'inventory source'};$sha=[Security.Cryptography.SHA256]::Create();$h=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($v)))-replace'-','').ToLower();if($h-ne'02b1b30375ce2fc52a67d0361ce15350c0672c16170ad949bb560a897c0dc145'){throw 'inventory mutation'};$pages=TC $u 'Page ID' 0;if($pages.Count-ne21){throw 'pages'};foreach($p in $pages){$q=('(?m)^\| `'+$p+'` \|[^\r\n]+\r?$');$x=Row $u $q;$sx=Row $src $q;$dx=Row $d ('(?m)^\| [123] \| `'+$p+'` \|[^\r\n]+\r?$');if($x.Count-ne5-or$sx.Count-ne4-or$dx.Count-ne7-or@($x|?{-not$_}).Count-or(($x[0..3]-join'|')-cne($sx-join'|'))-or$x[4]-cne$dx[6]){throw 'page mapping'};Refs ($x[2]+$x[3]);$os=@([regex]::Matches($x[4],'T\d-[A-Z0-9-]+')|%{$_.Value});if(-not$os-and$x[4]-notmatch'Shared'){throw 'owner sentinel'};foreach($o in $os){if(-not(Test-Path "specs/tasks/$o.md")-and$board-notmatch('(?m)^\| [^|]+ \| '+[regex]::Escape($o)+' \|')){throw 'owner ref'}}};$over=TC $u 'Surface' 0;if($over.Count-ne12-or(Table $u 'Surface')-cne(Table $src 'Surface')){throw 'overlay mapping'};foreach($n in $over){$x=Row $u ('(?m)^\| `?'+[regex]::Escape($n)+'`? \|[^\r\n]+\r?$');if($x.Count-ne4-or@($x|?{-not$_}).Count){throw 'overlay row'};Refs $x[2]};$states=TC $u '状态' 0;if($states.Count-ne13-or(Table $u '状态')-cne(Table $src '状态')){throw 'state mapping'};foreach($n in $states){$x=Row $u ('(?m)^\| '+[regex]::Escape($n)+' \|[^\r\n]+\r?$');if($x.Count-ne3-or@($x|?{-not$_}).Count){throw 'state row'};Refs $x[2]};$heads=@([regex]::Matches($u,'(?m)^#{1,3} (.+)$')|%{$_.Groups[1].Value});Exact $heads @('MyInspection UI/UX Elements 覆盖索引','1. 设计系统服从关系','2. Element 分层','2.1 容器与导航','2.2 内容、列表与状态表达','2.3 表单与选择','2.4 巡检、证据与媒体','2.5 长任务、安全与外部边界','3. 页面 → Elements 覆盖表','4. Overlay 与系统界面覆盖','5. 状态覆盖矩阵','6. 无障碍与响应式验收','7. 明确排除的 Elements');if($u-match'(?mi)^(colors|dark-colors|typography|rounded|spacing|interaction|motion|components|states):\s*$|#[0-9A-F]{6}|^\|[^\r\n]*\btoken\b[^\r\n]*\|'-or[regex]::Matches($u,'(?m)^\|[-: |]+\|$').Count-ne3){throw 'second authority'};$ban=[regex]::Match($u,'(?ms)^## 7\. 明确排除的 Elements\r?\n(.*)\z').Groups[1].Value;if([regex]::Matches($ban,'(?m)^v1 explicitly excludes FAB, drawer, carousel, charts, global snackbar, and remote telemetry\.$').Count-ne1){throw 'element ban'};foreach($n in 'FAB','drawer','carousel','charts','global snackbar','remote telemetry'){if([regex]::Matches($ban,('(?i)'+[regex]::Escape($n))).Count-ne1){throw ('element occurrence '+$n)}};Must $u @('(?m)^> Normative source: `context/DESIGN\.md`','(?m)^## 6\. 无障碍与响应式验收$','48dp','200%','TalkBack','Reduce Motion','compact / medium / expanded');Must $idx @('(?m)^\d+\. `docs/UI-UX-ELEMENTS\.md`')
dod_exit: 0
dod_assert: A1–A5 mapping mutations
review_gate: codex {verdict:pass}
hygiene: 权威唯一
doc_sync: R5
---
