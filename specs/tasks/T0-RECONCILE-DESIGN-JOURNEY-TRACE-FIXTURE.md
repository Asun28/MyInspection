---
id: T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE
title: 固定完整旅程表面注册与统一动作命名的设计源
depends_on: [T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码或合并设计源夹具分支
  - 更改未由 R3 旅程追溯缺口要求的任务卡内容
non_goals:
  - 执行旅程卡或组件卡
  - 实现 Compose 页面
acceptance:
  - "A1 源夹具新增 PROPERTY_CREATE 与 14 个非路由表面记录"
  - "A2 Start/Finish/Export/Open property 动作命名全局唯一"
  - "A3 旅程卡更新到 22 页面及四张受影响表哈希"
  - "A4 组件卡同步到同一最新设计源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$old='77f9fa9ed2fca2beec295139950098bb94f41d52';$oid='34a6b028dda672275bb6162b40cbaa122c6045f8';if((&git rev-parse ($oid+'^')).Trim()-cne$old){throw 'fixture parent'};$names=@(&git diff-tree --no-commit-id --name-only -r $oid);if($names.Count-ne1-or$names[0]-cne'context/DESIGN.md'){throw 'fixture path'};if((&git diff-tree --no-commit-id --numstat -r $oid|Out-String).Trim()-cne("38`t10`tcontext/DESIGN.md")){throw 'fixture numstat'};$src=(&git show ($oid+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";foreach($bad in 'Begin inspection','Finalize inspection','Save another quality','| Property card |'){if($src.Contains($bad)){throw ('stale action '+$bad)}};$sha=[Security.Cryptography.SHA256]::Create();function TH($h,$e){$p='(?ms)^\| '+[regex]::Escape($h)+' \|\n\|[-: |]+\n(?:\|[^\n]+\n)+';$m=[regex]::Matches($src,$p);if($m.Count-ne1){throw ('table '+$h)};$a=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($m[0].Value)))-replace'-','').ToLower();if($a-ne$e){throw ('hash '+$h)};$m[0].Value};$page=TH 'Level | `pageId` | Route | Page type | Parent | Bottom nav | Owner' '22e9ff9ed05e3ea9bfb8108c4847ac93ee8746399d7b7fc37729d8ae8cdf8f34';if([regex]::Matches($page,'(?m)^\| [123] \|').Count-ne22){throw 'pages'};$main=TH 'Trigger source | Preconditions / guard | Navigation action | Target | Transition | Exit and focus return' '962c328b6fb4019f73d2cb5b528be937c1b447475b7f665d2b4a384594fa14c8';$support=TH 'Trigger source | Action | Target | Close rule | Focus return' 'a8e152cbedfa139d3bf44f8388f1abaca0cc670c99b6da9d8868cfa90962fcd8';$surface=TH 'Target | Page type | Parent / launch context | Owner | Restoration policy | Entry focus key' 'a413c814d58863bdf5e15efcd3ddbf59c68fdff9284a3971604cd73d0d1ca989';if([regex]::Matches($surface,'(?m)^\| `').Count-ne14-or[regex]::Matches($surface,'\| `MODAL_SHEET` \|').Count-ne4-or[regex]::Matches($surface,'\| `ALERT_DIALOG` \|').Count-ne3-or[regex]::Matches($surface,'\| `SYSTEM_SURFACE` \|').Count-ne7){throw 'surface set'};$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote'};$baseCommit='dc84367e53245dbb486ec09a6acf1a2a309a1f2a';function ET($p,$pairs){$before=(&git show ($baseCommit+':'+$p)|Out-String);if($LASTEXITCODE-ne0){throw ('baseline '+$p)};$e=$before;foreach($pair in $pairs){if([regex]::Matches($e,[regex]::Escape($pair[0])).Count-ne$pair[2]){throw ('pair '+$p)};$e=$e.Replace($pair[0],$pair[1])};function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N (Get-Content $p -Raw))-cne(N $e)){throw ('exact '+$p)}};$journey=@(@($old,$oid,2),@('A1 21 pages/3 roots exact','A1 22 pages/3 roots exact',1),@('A3 core/support/overlay tables exact','A3 core/support/overlay/system tables exact',1),@('bf148947b9574e14d46a3ff39ceb06f9eae4596b024880b43583092ab3370a78','22e9ff9ed05e3ea9bfb8108c4847ac93ee8746399d7b7fc37729d8ae8cdf8f34',1),@('1a59205e3c93312b5f3c79687bdba9afe34c66d0c1135ccbe3d120ce02f69abe','962c328b6fb4019f73d2cb5b528be937c1b447475b7f665d2b4a384594fa14c8',1),@("@('Trigger source | Action | Target | Close rule | Focus return','aeb7e94b9b67176389faff50c17e0dc37283dfdecb7234ba63c29bdf7e0a5cb1')","@('Trigger source | Action | Target | Close rule | Focus return','a8e152cbedfa139d3bf44f8388f1abaca0cc670c99b6da9d8868cfa90962fcd8'),@('Target | Page type | Parent / launch context | Owner | Restoration policy | Entry focus key','a413c814d58863bdf5e15efcd3ddbf59c68fdff9284a3971604cd73d0d1ca989')",1),@('$rows.Count-ne21','$rows.Count-ne22',1));ET 'specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md' $journey;ET 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' (,@($old,$oid,2))
dod_exit: 0
dod_assert: A1–A4 exact source/table/card transforms；漏表面、旧命名、旧哈希或任一额外字节即 RED
review_gate: codex {verdict:pass}
hygiene: 同类追溯缺口一次扫全
doc_sync: 无
---

# T0-RECONCILE-DESIGN-JOURNEY-TRACE-FIXTURE

R3 发现 route 清单未覆盖新增物业，非路由弹层/系统面也没有可追溯记录，同时四组核心动作存在多套名称。本卡固定最小设计修订源，并把两个仍待执行的设计消费者同步到该源。
