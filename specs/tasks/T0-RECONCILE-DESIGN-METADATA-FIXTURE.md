---
id: T0-RECONCILE-DESIGN-METADATA-FIXTURE
title: 修正设计元数据 YAML 状态的可执行源夹具
depends_on: []
status: todo
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
  - "A1 元数据卡只把 source_ref 从 235d40f 切到 e12ae78"
  - "A2 e12ae78 仅将五处状态数组的 ON/OFF 显式引号化"
  - "A3 YAML 1.1 兼容解析不再把组件状态物化为布尔值"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-DESIGN-METADATA.md';$c=Get-Content $p -Raw;$oid='e12ae7885684a71926d717098092eabfb601d48a';$needle=$oid+':context/DESIGN.md';if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2){throw 'source binding'};if($c.Contains('235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf')){throw 'stale source'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};$f=[regex]::Match($s,'(?s)^---\r?\n(.*?)\r?\n---').Groups[1].Value;$rows=@([regex]::Matches($f,"(?m)^    states: \[[^\]\r\n]*'OFF'[^\]\r\n]*'ON'[^\]\r\n]*\]\r?$")|%{$_.Value});if($rows.Count-ne5){throw 'quoted states'};if($f-match'(?m)^    states: \[[^\]\r\n]*(?:\[|,)\s*(?:OFF|ON)\s*(?:,|\])'){throw 'yaml boolean state'}
dod_exit: 0
dod_assert: A1–A3 exact source binding；删除任一引号或改回旧 OID 即 RED
review_gate: codex {verdict:pass}
hygiene: 只修不可满足的 fixture binding
doc_sync: 无
---

# T0-RECONCILE-DESIGN-METADATA-FIXTURE

原设计夹具的五组组件状态使用未引号化的 `ON/OFF`。YAML 1.1 兼容解析器会把它们读取为布尔值，与机器状态名契约冲突。本卡只把元数据卡钉到不可合并子夹具 `e12ae78`；该提交仅为十个标量加引号。
