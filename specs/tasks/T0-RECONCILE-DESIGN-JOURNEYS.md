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
  - 把联网 provider 伪装为核心离线流程
  - 收窄 ADR-0002 的 full/property backup scope
non_goals:
  - 逐组件视觉合同或 Compose 实现
acceptance:
  - "A1 21 pages/3 roots exact"
  - "A2 9 types/6 transitions exact"
  - "A3 core/support/overlay tables exact"
  - "A4 interaction/focus tables exact"
  - "A5 capability/backup/anchors exact"
dod_command: function Run($id){$p=@("specs/tasks/$id.md","specs/archive/tasks/$id.md")|?{Test-Path $_}|select -First 1;if(-not$p){throw 'predecessor'};$l=Get-Content $p|?{$_-like'dod_command:*'};&([scriptblock]::Create($l.Substring(13)))};Run 'T0-RECONCILE-DESIGN-METADATA';$r=Get-Content 'context/DESIGN.md' -Raw;$board=Get-Content 'docs/TASK-BOARD.md' -Raw;$sha=[Security.Cryptography.SHA256]::Create();function HT($h,$e){$ms=[regex]::Matches($r,('(?ms)^\| '+[regex]::Escape($h)+' \|\r?\n\|[-: |]+\r?\n(?:\|[^\r\n]+\r?\n)+'));if($ms.Count-ne1){throw ('table '+$h)};$m=$ms[0];$s=$m.Value-replace'\r\n',"`n";$a=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($a-ne$e){throw ('table content '+$h)};$m.Value};$defs=@(@('Level | `pageId` | Route | Page type | Parent | Bottom nav | Owner','bf148947b9574e14d46a3ff39ceb06f9eae4596b024880b43583092ab3370a78'),@('Page type | Container | Navigation model | Persistent state | Exit rule','2a773bd512fa4e59dfe474778c9ac6f77b341910599e74dbf5b8115506b6df3a'),@('Operation | Enter | Exit | Duration/easing','ac5459cdc0292675678342d8760fb0be3b3e14267b08709dffda7686078fa260'),@('Trigger source | Preconditions / guard | Navigation action | Target | Transition | Exit and focus return','1a59205e3c93312b5f3c79687bdba9afe34c66d0c1135ccbe3d120ce02f69abe'),@('Trigger source | Action | Target | Close rule | Focus return','aeb7e94b9b67176389faff50c17e0dc37283dfdecb7234ba63c29bdf7e0a5cb1'),@('Overlay or state | Scrim tap | Swipe down | System Back | Explicit action','933e518adfee33e875aac95b8b37af223dac7d7fb8747deada251be60006e019'),@('State/event | Visual response | Input policy | Haptic','279edd35055deb0970650dac3e733e263100d00c81a222f51a344fabdec312b6'),@('Event | Focus destination | Announcement','816ff1e00026815232dd28d0a12830fdc686b93f30cb0623b6f48e4ae2215828'),@('Capability | Offline presentation | Core-flow effect','9c6ef5a239060629820d454280ae0ccd912d45385163281103bbcf61538e56be'),@('State | Required message | Primary action','f36321a03dfa4c2251f0f97fd3298fc3b552bf5277947841321747eb6203a541'));$tables=@{};foreach($d in $defs){$tables[$d[0]]=HT $d[0] $d[1]};$page=$tables[$defs[0][0]];$rows=@([regex]::Matches($page,'(?m)^\| [123] \| `([^`]+)` \|[^\r\n]+$'));if($rows.Count-ne21){throw 'pages'};$roots=@();foreach($row in $rows){$c=@($row.Value.Trim('|').Split('|')|%{$_.Trim()});if($c[4]-eq'—'){$roots+=$c[1].Trim('`')};$owners=@([regex]::Matches($c[6],'T\d-[A-Z0-9-]+')|%{$_.Value});if(-not$owners-and$c[6]-ne'Shared settings shell'){throw 'owner'};foreach($o in $owners){if(-not(Test-Path "specs/tasks/$o.md")-and$board-notmatch('(?m)^\| [^|]+ \| '+[regex]::Escape($o)+' \|')){throw ('owner '+$o)}}};if((Compare-Object ($roots|sort) (@('PROPERTIES_ROOT','SCHEDULE_ROOT','SETTINGS_ROOT')|sort))){throw 'roots'};foreach($a in @(@('Primary inspection journey','primary-inspection-journey'),@('Offline and data-protection experience','offline-and-data-protection-experience'))){$m=[regex]::Matches($r,('(?m)^#{2,3} '+[regex]::Escape($a[0])+'$'));if($m.Count-ne1-or(($a[0].ToLower()-replace'[^a-z0-9 -]',''-replace' +','-')-ne$a[1])){throw 'plan anchor'}};if([regex]::Matches($r,'(?m)^Format v1 offers both `All app data` and `This property` backup scopes\.$').Count-ne1){throw 'backup scope'}
dod_exit: 0
dod_assert: A1–A5 table/reference mutations
review_gate: codex {verdict:pass}
hygiene: 单一权威；无重复
doc_sync: R5 同步 owning docs
---
