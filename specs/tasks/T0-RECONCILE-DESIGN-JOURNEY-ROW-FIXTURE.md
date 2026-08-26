---
id: T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE
title: 固定支持路由列形与通知选择失败路径
depends_on: [T0-RECONCILE-DESIGN-JOURNEY-INVARIANT-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码或合并设计源夹具分支
  - 修改 UI coverage 后续卡负责的文档
non_goals:
  - 执行旅程卡、组件卡或 UI coverage 卡
  - 实现通知页面
diagnosis: 支持路由表新增首启恢复时把 guard 当成第六列，且 Notice compose 的 inspectionId 没有选择守卫；根因是 DoD 只哈希表文本，未验证逐行列数与参数化路由的输入失败路径。
acceptance:
  - "A1 支持路由表每行严格 5 列"
  - "A2 Notice compose 只接受同物业 eligible 选择，缺失或失效不改栈并回焦 selector"
  - "A3 first-run restore 保留完整 stack、取消和焦点合同"
  - "A4 旅程卡和组件卡同步同一源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$parent='3595e53a8bf315d8f03312bb845c38973636bc34';$oid='c9a34b314cdf38986b2584c35371b17a73438003';if((&git rev-parse ($oid+'^')).Trim()-cne$parent){throw 'fixture parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture path'};if((&git diff-tree --no-commit-id --numstat -r $oid|Out-String).Trim()-cne("2`t2`tcontext/DESIGN.md")){throw 'fixture numstat'};&git merge-base --is-ancestor $oid 'refs/remotes/origin/codex/design-metadata-fixture-v2';if($LASTEXITCODE-ne0){throw 'fixture not reachable'};$src=(&git show ($oid+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";$h='Trigger source | Action | Target | Close rule | Focus return';$p='(?ms)^\| '+[regex]::Escape($h)+' \|\n\|[-: |]+\n(?:\|[^\n]+\n)+';$m=[regex]::Matches($src,$p);if($m.Count-ne1){throw 'support table'};$table=$m[0].Value;foreach($line in ($table -split"`n")|Select-Object -Skip 2){if($line-and$line.Split('|').Count-ne7){throw ('support columns '+$line)}};$sha=[Security.Cryptography.SHA256]::Create();$hash=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($table)))-replace'-','').ToLower();if($hash-cne'3c312eb0c7f1c12a66ba8c2378b2884587f6ad81c884b58c0bfd69f1513f2cb0'){throw 'support hash'};$notice='| Notice center `New notice` | If one selected eligible inspection belongs to this property, `PUSH`; otherwise keep the stack unchanged, show `Choose an inspection`, and focus the required inspection selector | `NOTICE_COMPOSE(inspectionId)` | Dirty-state guard after Push; a deleted or ineligible selection returns to the selector without creating a notice | New notice button, or inspection selector on guard failure |';$restore='| First-run `Restore encrypted backup` | Select Settings; reset its stack to `SETTINGS_ROOT → BACKUP_SETTINGS → RESTORE_TASK`; then `LAUNCH_SYSTEM` | `BACKUP_FILE_PICKER` | Cancel restores `PROPERTIES_ROOT` first-run state; successful restore relaunches | Restore encrypted backup action |';if([regex]::Matches($table,[regex]::Escape($notice)).Count-ne1-or[regex]::Matches($table,[regex]::Escape($restore)).Count-ne1){throw 'route rows'};$base='d9ea7f163be651532af18dae71ccc63d1988d7c0';$old='3595e53a8bf315d8f03312bb845c38973636bc34';function ET($p,$pairs){$e=(&git show ($base+':'+$p)|Out-String);if($LASTEXITCODE-ne0){throw ('baseline '+$p)};foreach($pair in $pairs){if([regex]::Matches($e,[regex]::Escape($pair[0])).Count-ne$pair[2]){throw ('pair '+$p)};$e=$e.Replace($pair[0],$pair[1])};function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N (Get-Content $p -Raw))-cne(N $e)){throw ('exact '+$p)}};ET 'specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md' @(@($old,$oid,2),@('279452801a9cc9022f9f60a3410e3bb6fd52439eaf724233b9a0027dcb412450','3c312eb0c7f1c12a66ba8c2378b2884587f6ad81c884b58c0bfd69f1513f2cb0',1));ET 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' (,@($old,$oid,2))
dod_exit: 0
dod_assert: A1–A4 exact row shape/failure path/source/card transforms；多列、无 guard 或漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 参数化路由输入与失败焦点同类扫全
doc_sync: 无；UI 元素索引由已登记 T0-RECONCILE-UI-COVERAGE 落地
---

# T0-RECONCILE-DESIGN-JOURNEY-ROW-FIXTURE

仅登记支持路由表列形与参数输入失败路径修复。

