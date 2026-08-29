---
id: T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE
title: 固定备份范围矩阵与完整旅程栈不变量
depends_on: [T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE]
status: merged
branch: T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码或合并设计源夹具分支
  - 更改 R3 所列备份/路由不变量以外的卡片内容
non_goals:
  - 执行旅程卡或组件卡
  - 实现备份格式 v2 或 Compose 页面
diagnosis: 正式旅程评审证明源设计把 v1 property 兼容导出误写成可恢复范围，且同一逻辑页面混用多个父栈/退出规则；根因是 DoD 只固定表面集合，没有固定版本范围矩阵与每个入口的完整父栈。
acceptance:
  - "A1 v1/v2 full/property 导出、回执、拒绝和替换语义形成精确矩阵"
  - "A2 23 个 route-backed 页面为 post-finalize 与 re-export 建立独立路由"
  - "A3 first-run、Schedule、Capture/Review/Finalize 的完整栈与退出焦点唯一"
  - "A4 旅程卡和组件卡同步到同一最新设计源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$parent='f53c7f0f6222d79a77e3d3556822780a885423ff';$oid='3595e53a8bf315d8f03312bb845c38973636bc34';if((&git rev-parse ($oid+'^')).Trim()-cne$parent){throw 'fixture parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture path'};if((&git diff-tree --no-commit-id --numstat -r $oid|Out-String).Trim()-cne("24`t10`tcontext/DESIGN.md")){throw 'fixture numstat'};&git merge-base --is-ancestor $oid 'refs/remotes/origin/codex/design-metadata-fixture-v2';if($LASTEXITCODE-ne0){throw 'fixture not reachable from remote-tracking ref'};$src=(&git show ($oid+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";foreach($bad in 'S --> IS','Switch to Properties stack, then `PUSH`','Dock shows only `Next room`, `Review missing`, or `Finish inspection`','| `Finish inspection` | Missing count `0`; state `READY`','| Report action `Export another quality` | Inspection finalized | `PUSH` | `REPORT_EXPORT','format compatibility, `All app data`','T5-CONTACT-PURGE'){if($src.Contains($bad)){throw ('stale invariant '+$bad)}};$sha=[Security.Cryptography.SHA256]::Create();function TH($h,$e){$p='(?ms)^\| '+[regex]::Escape($h)+' \|\n\|[-: |]+\n(?:\|[^\n]+\n)+';$m=[regex]::Matches($src,$p);if($m.Count-ne1){throw ('table '+$h)};$a=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($m[0].Value)))-replace'-','').ToLower();if($a-ne$e){throw ('hash '+$h)};$m[0].Value};$page=TH 'Level | `pageId` | Route | Page type | Parent | Bottom nav | Owner' 'a6cfe799552f8b4e4294a6ce710324048a9551d9fcfc336d6ee15cf3321d804d';if([regex]::Matches($page,'(?m)^\| [123] \|').Count-ne23-or[regex]::Matches($page,'(?m)^\| 2 \| `REPORT_(?:EXPORT|REEXPORT)` ').Count-ne2){throw 'page set'};$screen=TH 'Screen | First viewport priority | Main content | Persistent action | Empty / exceptional state' '1beaaa0c70725ce67639b229cbd023f7b852f989f9465407b511b3e0326ce810';$main=TH 'Trigger source | Preconditions / guard | Navigation action | Target | Transition | Exit and focus return' '53c0a1e3910d721e0b17112f8d60e2cd79b63aed4dea5a5042c64352755c5a3a';$support=TH 'Trigger source | Action | Target | Close rule | Focus return' '279452801a9cc9022f9f60a3410e3bb6fd52439eaf724233b9a0027dcb412450';$matrix=TH 'Package | Export disclosure | Recoverable verified receipt | Restore preflight and result' 'dabd6e80b5e83a0d21b144e9c6807b8de20e9d5e1637f4819a307da10055466c';if([regex]::Matches($matrix,'(?m)^\| v[12] `(?:full|property)`').Count-ne4-or-not$matrix.Contains('Compatibility export created — not restorable')-or-not$matrix.Contains('Replace current data with this property')){throw 'backup matrix'};$base='5cbdcf57912292b11e27291681f564d67d16b96e';$old='f53c7f0f6222d79a77e3d3556822780a885423ff';function ET($p,$pairs){$e=(&git show ($base+':'+$p)|Out-String);if($LASTEXITCODE-ne0){throw ('baseline '+$p)};foreach($pair in $pairs){if([regex]::Matches($e,[regex]::Escape($pair[0])).Count-ne$pair[2]){throw ('pair '+$p)};$e=$e.Replace($pair[0],$pair[1])};function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N (Get-Content $p -Raw))-cne(N $e)){throw ('exact '+$p)}};$bar="@('Page type | Bar | Leading action | Title | Trailing actions | Primary action placement','815b7795916e74cbafb09fee7bff6d8fe97c00892d8e92a5257c9cda0be665ac'),@('Operation | Enter | Exit | Duration/easing','ac5459cdc0292675678342d8760fb0be3b3e14267b08709dffda7686078fa260')";$bar2="@('Page type | Bar | Leading action | Title | Trailing actions | Primary action placement','815b7795916e74cbafb09fee7bff6d8fe97c00892d8e92a5257c9cda0be665ac'),@('Screen | First viewport priority | Main content | Persistent action | Empty / exceptional state','1beaaa0c70725ce67639b229cbd023f7b852f989f9465407b511b3e0326ce810'),@('Operation | Enter | Exit | Duration/easing','ac5459cdc0292675678342d8760fb0be3b3e14267b08709dffda7686078fa260')";$cap="@('Capability | Offline presentation | Core-flow effect','9c6ef5a239060629820d454280ae0ccd912d45385163281103bbcf61538e56be'),@('State | Required message | Primary action','f36321a03dfa4c2251f0f97fd3298fc3b552bf5277947841321747eb6203a541')";$cap2="@('Capability | Offline presentation | Core-flow effect','9c6ef5a239060629820d454280ae0ccd912d45385163281103bbcf61538e56be'),@('Package | Export disclosure | Recoverable verified receipt | Restore preflight and result','dabd6e80b5e83a0d21b144e9c6807b8de20e9d5e1637f4819a307da10055466c'),@('State | Required message | Primary action','f36321a03dfa4c2251f0f97fd3298fc3b552bf5277947841321747eb6203a541')";$journey=@(@($old,$oid,2),@('A1 22 pages/3 roots exact','A1 23 pages/3 roots exact',1),@('22e9ff9ed05e3ea9bfb8108c4847ac93ee8746399d7b7fc37729d8ae8cdf8f34','a6cfe799552f8b4e4294a6ce710324048a9551d9fcfc336d6ee15cf3321d804d',1),@($bar,$bar2,1),@('962c328b6fb4019f73d2cb5b528be937c1b447475b7f665d2b4a384594fa14c8','53c0a1e3910d721e0b17112f8d60e2cd79b63aed4dea5a5042c64352755c5a3a',1),@('a238a19944197cf61d1af7c8f40dc67b63108ed9b27dd73b2c674c9f69d0b339','279452801a9cc9022f9f60a3410e3bb6fd52439eaf724233b9a0027dcb412450',1),@($cap,$cap2,1),@('$rows.Count-ne22','$rows.Count-ne23',1));ET 'specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md' $journey;ET 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' (,@($old,$oid,2))
dod_exit: 0
dod_assert: A1–A4 exact source/table/card transforms；版本范围、父栈、退出或哈希漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 备份范围与 route entry/parent/exit 同类缺口一次扫全
doc_sync: 无
---

# T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE

仅登记经 R3 证明的 journey 源不变量修复，不执行下游设计卡。
