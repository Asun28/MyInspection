---
id: T0-RECONCILE-DESIGN-JOURNEY-FIXTURE
title: 修正设计旅程备份范围断言的可执行源夹具
depends_on: [T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEY-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-JOURNEY-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEY-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、设计正文语义、任务卡验收集合或表格哈希
  - 更改除 source_ref 与对应 git show OID 以外的目标卡内容
non_goals:
  - 执行旅程卡或组件卡
  - 合并设计源夹具分支
acceptance:
  - "A1 源夹具只把备份范围句拆成独立段落"
  - "A2 两张目标卡各有两处 77f9fa9 源绑定且无 c66fd13"
  - "A3 origin 专用分支精确解析到新夹具"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$old='c66fd137dc2e869a04b0938ce1b41cf14a8f50f1';$oid='77f9fa9';$full=&git rev-parse ($oid+'^{commit}');if($LASTEXITCODE-ne0){throw 'local fixture'};$full=$full.Trim();if((&git rev-parse ($full+'^')).Trim()-cne$old){throw 'fixture parent'};$names=@(&git diff-tree --no-commit-id --name-only -r $full);if($names.Count-ne1-or$names[0]-cne'context/DESIGN.md'){throw 'fixture path'};$num=(&git diff-tree --no-commit-id --numstat -r $full|Out-String).Trim();if($num-cne("3`t1`tcontext/DESIGN.md")){throw 'fixture numstat'};$src=(&git show ($full+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";if([regex]::Matches($src,'(?m)^Format v1 offers both `All app data` and `This property` backup scopes\.$').Count-ne1-or$src.Contains('receipt. Format v1 offers both')){throw 'fixture text'};$needle=$full+':context/DESIGN.md';foreach($p in 'specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md','specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md'){$c=Get-Content $p -Raw;if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2-or$c.Contains($old)){throw ('source binding '+$p)}};$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$full){throw 'durable remote ref'}
dod_exit: 0
dod_assert: A1–A3 fixture delta/source binding；合并句、旧 OID、非单路径或非 3/1 变异即 RED
review_gate: codex {verdict:pass}
hygiene: 夹具提交最小且可追溯
doc_sync: 无
---

# T0-RECONCILE-DESIGN-JOURNEY-FIXTURE

旅程卡要求备份范围断言独占一行，同时要求正文与源夹具逐字一致；旧夹具把断言接在上一句后，形成不可满足契约。本卡只固定最小排版修复并同步两个仍待执行的消费者。
