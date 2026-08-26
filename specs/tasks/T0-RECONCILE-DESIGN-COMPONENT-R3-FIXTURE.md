---
id: T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE
title: 登记 Components 动效与 Compose 语义修订源
depends_on: [T0-RECONCILE-DESIGN-FOUNDATIONS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、主设计正文、R3 预算或既有提交历史
  - 扩大组件能力或更改产品范围
non_goals:
  - 执行 Components 卡
diagnosis: PR 169 的 R3 发现正文标准状态动效 200ms 与权威 token 180ms 冲突，且 group、tablist、search、list、collection 被误写为固定 Compose 版本不存在的 Role。
acceptance:
  - "A1 97f2fb2 是远端夹具分支上 c384947 的直接单文件后继"
  - "A2 动效正文唯一引用 stateChangeMs 180ms"
  - "A3 所有不受支持的 Role 均改为可实现的 Compose 语义映射"
  - "A4 81 个组件与 49 个对比度 bindings 保持不变"
  - "A5 Components 卡全文固定并改钉新源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='97f2fb2db443575e38e0b321ae8b22f087122fe0';$old='c384947cb448e57b0192b0987c9b7ee263423a56';$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote ref'};$parent=(&git rev-parse ($oid+'^')).Trim();if($LASTEXITCODE-ne0-or$parent-cne$old){throw 'direct parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($LASTEXITCODE-ne0-or$paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture paths'};$stat=(&git diff-tree --no-commit-id --numstat -r $oid).Trim();if($LASTEXITCODE-ne0-or$stat-cne("2`t2`tcontext/DESIGN.md")){throw ('fixture stat '+$stat)};$src=(&git show ($oid+':context/DESIGN.md')|Out-String).Replace([string][char]13,'');if($LASTEXITCODE-ne0){throw 'source'};if($src-match'(?m)standard state transitions use 200ms|Role \x60(?:group|tablist|search|list|collection)\x60|"semanticsRole": "group"|\x60collectionInfo\('){throw 'stale contract'};foreach($p in 'standard state transitions use \x60motion\.stateChangeMs\x60 \(\x60180ms\x60\)','"semanticsRole": "merged traversal group \(isTraversalGroup=true; no Role\)"','SearchBar supplies editable-text semantics; no Role','\x60Modifier\.semantics \{ collectionInfo = CollectionInfo\(rowCount = 1, columnCount = itemCount\) \}\x60; no Role','\x60Modifier\.semantics \{ collectionInfo = CollectionInfo\(rowCount = resolvedRowCount, columnCount = resolvedColumnCount\) \}\x60; no Role'){if([regex]::Matches($src,$p).Count-ne1){throw ('mapping '+$p)}};if([regex]::Matches($src,'\x60selectableGroup\(\)\x60 \+ \x60isTraversalGroup=true\x60; no Role').Count-ne2){throw 'selectable groups'};$fm=[regex]::Match($src,'(?s)^---\n(.*?)\n---').Groups[1].Value;$registry=@([regex]::Matches([regex]::Match($fm,'(?ms)^components:\n(.*)\z').Groups[1].Value,'(?m)^  ([a-z0-9-]+):'));if($registry.Count-ne81){throw 'component count'};$m=[regex]::Match($src,'(?ms)^### CI contrast gate metadata\n.*?\x60\x60\x60json\n(.*?)\n\x60\x60\x60');if(-not$m.Success-or($m.Groups[1].Value|ConvertFrom-Json -Depth 20).bindings.Count-ne49){throw 'binding count'};$task=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' -Raw;$sha=[Security.Cryptography.SHA256]::Create();$s=$task.Replace([string][char]13,'').TrimEnd();$actual=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($actual-cne'7ccf65fe58722791d3025d0900ae524fcd895a4e95db96d60dc061fb3e25d61a'){throw 'exact card'}
dod_exit: 0
dod_assert: A1–A5；远端 tip/parent/stat、过时 Role/时长、精确 Compose 映射、81/49 数量与 Components 卡 hash 任一漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 仅登记本轮 R3 动效与 Compose 语义修订源及下游钉点
doc_sync: 无
---

# T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE

只登记 PR 169 首轮 R3 所需的动效一致性与可实现 Compose 语义映射，不修改主设计正文。
