---
id: T0-RECONCILE-DESIGN-METADATA-FIXTURE
title: 修正设计元数据 YAML 状态的可执行源夹具
depends_on: []
status: merged
branch: T0-RECONCILE-DESIGN-METADATA-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-METADATA-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-METADATA-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-METADATA.md
forbid:
  - 修改产品代码、设计正文、令牌值、组件集合或已捕获原始提交
  - 放宽设计元数据卡的顶层键、颜色、排版、几何或组件精确集合
non_goals:
  - 执行 T0-RECONCILE-DESIGN-METADATA
  - 合并本地调和源分支
acceptance:
  - "A1 元数据卡只把 source_ref 从 235d40f 切到 c66fd13"
  - "A2 c66fd13 仅将五处状态数组的 ON/OFF 显式引号化"
  - "A3 YAML 1.1 兼容解析不再把组件状态物化为布尔值"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-DESIGN-METADATA.md';$c=Get-Content $p -Raw;$old='235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf';$oid='c66fd137dc2e869a04b0938ce1b41cf14a8f50f1';$needle=$oid+':context/DESIGN.md';if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2-or$c.Contains($old)){throw 'source binding'};$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote ref'};$parent=(&git rev-parse ($oid+'^')).Trim();if($LASTEXITCODE-ne0-or$parent-cne$old){throw 'direct parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($LASTEXITCODE-ne0-or$paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture paths'};$stat=(&git diff-tree --no-commit-id --numstat -r $oid).Trim();if($LASTEXITCODE-ne0-or$stat-cne("5`t5`tcontext/DESIGN.md")){throw 'fixture stat'};$before=&git show ($old+':context/DESIGN.md')|Out-String;$after=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};$a='    states: [OFF, ON, PRESSED, FOCUSED, DISABLED]';$aq="    states: ['OFF', 'ON', PRESSED, FOCUSED, DISABLED]";$b='    states: [UNAVAILABLE, OFF, ON, ADJUSTING, DISABLED]';$bq="    states: [UNAVAILABLE, 'OFF', 'ON', ADJUSTING, DISABLED]";if([regex]::Matches($before,('(?m)^'+[regex]::Escape($a)+'\r?$')).Count-ne4-or[regex]::Matches($before,('(?m)^'+[regex]::Escape($b)+'\r?$')).Count-ne1){throw 'old lines'};function N($x){($x-replace'\r\n',"`n").TrimEnd()};$expected=$before.Replace($a,$aq).Replace($b,$bq);if((N $after)-cne(N $expected)){throw 'exact five replacements'};$f=[regex]::Match($after,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;if([regex]::Matches($f,"(?m)^    states: \[[^\]\r\n]*'OFF'[^\]\r\n]*'ON'[^\]\r\n]*\]\r?$").Count-ne5-or$f-match'(?m)^    states: \[[^\]\r\n]*(?:\[|,)\s*(?:OFF|ON)\s*(?:,|\])'){throw 'yaml state'}
dod_exit: 0
dod_assert: A1–A3 exact source binding；删除任一引号或改回旧 OID 即 RED
review_gate: codex {verdict:pass}
hygiene: 只修不可满足的 fixture binding
doc_sync: 无
---

# T0-RECONCILE-DESIGN-METADATA-FIXTURE

原设计夹具的五组组件状态使用未引号化的 `ON/OFF`。YAML 1.1 兼容解析器会把它们读取为布尔值，与机器状态名契约冲突。本卡只把元数据卡钉到不可合并子夹具 `c66fd13`；该提交仅为十个标量加引号。
