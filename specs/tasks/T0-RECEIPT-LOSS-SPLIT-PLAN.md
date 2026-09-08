---
id: T0-RECEIPT-LOSS-SPLIT-PLAN
title: 将 receipt-loss 交付拆为授权位、运行时停止与源码合同三张串行卡
depends_on: [T0-CI-IDENTITY-DEADLINE]
plan_ref: docs/TASK-BOARD.md#scaffold-038-selective-backport
parallelizable_with: []
acceptance:
  - "A1 既有 T0-RECEIPT-LOSS-FAIL-CLOSED 保留原 id/path 并改为依赖 T0-RECEIPT-AUTHORIZATION-BIT"
  - "A2 T0-RECEIPT-AUTHORIZATION-BIT -> T0-RECEIPT-LOSS-FAIL-CLOSED -> T0-RECEIPT-LOSS-SOURCE-CONTRACT -> T0-ASCII-SHIP-CODES 是唯一串行链"
  - "A3 A/B/C 均有编号验收、可执行单行 DoD、空 parallelizable_with 与包含自身卡路径的精确 allow_paths"
  - "A4 TASK-BOARD 只把 c53ec489 超限 WIP、session 54615 旧行为 RED 与 4495fae8 prototype 记为只读设计证据，不冒充正式 RED/GREEN"
  - "A5 注册 diff 只含本卡声明的六个 metadata/card/board 路径，不修改 scripts、运行时 docs 或 tech-debt"
status: merged
branch: T0-RECEIPT-LOSS-SPLIT-PLAN
worktree: C:\wt\T0-RECEIPT-LOSS-SPLIT-PLAN
allow_paths:
  - specs/tasks/T0-RECEIPT-LOSS-SPLIT-PLAN.md
  - specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md
  - specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md
  - specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md
  - specs/tasks/T0-ASCII-SHIP-CODES.md
  - docs/TASK-BOARD.md
forbid:
  - 修改 scripts、运行时 docs、质量预算或任何 gate 行为
  - 删除或改名既有 T0-RECEIPT-LOSS-FAIL-CLOSED
  - 把超限 WIP、prototype 或旧行为 RED 当成正式实现/终态验证
non_goals:
  - 实现 A/B/C 的 runtime、测试或文档行为
  - 清理只读 WIP/RED 设计证据
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; $ErrorActionPreference='Stop'; . ./scripts/_cards.ps1; function Get-M([string]$path) { $fm = Get-FrontMatter (Get-Content -Raw $path); [ordered]@{ id=(Get-Scalar $fm 'id'); status=(Get-Scalar $fm 'status'); depends_on=@(Get-YamlListItems $fm 'depends_on'); parallelizable_with=@(Get-YamlListItems $fm 'parallelizable_with'); allow_paths=@(Get-YamlListItems $fm 'allow_paths'); acceptance=@(Get-YamlListItems $fm 'acceptance'); dod_command=(Get-Scalar $fm 'dod_command'); dod_assert=(Get-Scalar $fm 'dod_assert') } }; function Get-N([object]$source) { $n=[ordered]@{}; foreach ($key in $source.Keys) { if ($key -ne 'dod_command') { $n[$key]=$source[$key] } }; $n }; function Mutate-Raw([string]$raw,[object]$model,[string]$key) { if ($key -in @('id','status','depends_on','parallelizable_with','dod_command','dod_assert')) { if ($key -in @('depends_on','parallelizable_with')) { return ($raw -replace "(?m)^$([regex]::Escape($key))\:.*$", "${key}: [__mutation__]") }; return ($raw -replace "(?m)^$([regex]::Escape($key))\:.*$", "${key}: __mutation__") }; if ($key -eq 'allow_paths') { return $raw.Replace('  - '+[string]$model.allow_paths[0],'  - '+[string]$model.allow_paths[0]+'__mutation__') }; if ($key -eq 'acceptance') { return $raw.Replace('  - "'+[string]$model.acceptance[0]+'"','  - "'+[string]$model.acceptance[0]+'__mutation__"') }; return $raw }; function Get-S([object]$value) { $json = $value | ConvertTo-Json -Compress -Depth 20; [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($json))) }; $expected = [ordered]@{ A='6C7BF304557B4CF83AFA7DB3320549152794FA7F67BBDFDFB6D2EBB02BB5F94D'; B='CF91190DF2CA38E740C7B890F38E86419417F34E8D16E547D0974048F9D0D980'; C='D5DA001B9B5EBB5EDE9C648FB75BC69C265DF57756DD0AE0870C8B8B3A5BB2BD'; Z='C90AE3B3AEC9FAA0979A461460BF609B07FC0966F115CBA068FFF33AA64A7F06'; P='A96AB94D7922731F06B739707FB129740A559E04249FF22F2EAA500C249B4D11' }; $a=Get-M 'specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md'; $b=Get-M 'specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md'; $c=Get-M 'specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md'; $z=Get-M 'specs/tasks/T0-ASCII-SHIP-CODES.md'; $p=Get-M 'specs/tasks/T0-RECEIPT-LOSS-SPLIT-PLAN.md'; foreach ($x in @(@('A',$a),@('B',$b),@('C',$c),@('Z',$z))) { if ((Get-S $x[1]) -cne $expected[$x[0]]) { exit 1 } }; $pNo=Get-N $p; if ((Get-S $pNo) -cne $expected.P) { exit 1 }; if (@($a,$b,$c,$z | Where-Object { $_.status -cne 'todo' }).Count -ne 0 -or $p.status -ne 'merged') { exit 1 }; $tmp=[IO.Path]::Combine([IO.Path]::GetTempPath(),'receipt-card-negative-'+[guid]::NewGuid().ToString('N')); New-Item -ItemType Directory -Path $tmp -Force | Out-Null; try { foreach ($pair in @(@('A',$a),@('B',$b),@('C',$c),@('Z',$z),@('P',$p))) { $keys=@('id','status','depends_on','parallelizable_with','allow_paths','acceptance','dod_assert'); if ($pair[0] -ne 'P') { $keys += 'dod_command' }; foreach ($key in $keys) { if ($key -eq 'acceptance' -and @($pair[1].acceptance).Count -eq 0) { continue }; $src = switch ($pair[0]) { 'A' { 'specs/tasks/T0-RECEIPT-AUTHORIZATION-BIT.md' } 'B' { 'specs/tasks/T0-RECEIPT-LOSS-FAIL-CLOSED.md' } 'C' { 'specs/tasks/T0-RECEIPT-LOSS-SOURCE-CONTRACT.md' } 'Z' { 'specs/tasks/T0-ASCII-SHIP-CODES.md' } default { 'specs/tasks/T0-RECEIPT-LOSS-SPLIT-PLAN.md' } }; $raw=Get-Content -Raw $src; $mut=Mutate-Raw $raw $pair[1] $key; if ($mut -eq $raw) { exit 1 }; $file=Join-Path $tmp ($pair[0]+'-'+$key+'.md'); Set-Content -LiteralPath $file -Value $mut -NoNewline -Encoding utf8; $parsed=Get-M $file; $canon=if ($pair[0] -eq 'P') { Get-N $parsed } else { $parsed }; if ((Get-S $canon) -eq $expected[$pair[0]]) { exit 1 } } }; $evidence=@(Get-Content 'docs/TASK-BOARD.md' | Where-Object { $_ -match '^\- receipt-loss 拆分证据' }); if ($evidence.Count -ne 1 -or $evidence[0] -notmatch '仅设计输入，不冒充正式验证' -or $evidence[0] -notmatch '正式执行必须依次 fresh RED、实现、GREEN、R3、merge') { exit 1 }; $boardModel=[ordered]@{line=$evidence}; if ((Get-S $boardModel) -cne '37273E8E2F319838B88017175C1570411BA573D80B51AA40F684A4E038487C36') { exit 1 }; $boardFile=Join-Path $tmp 'board.md'; $boardRaw=Get-Content -Raw 'docs/TASK-BOARD.md'; Set-Content -LiteralPath $boardFile -Value $boardRaw.Replace($evidence[0],$evidence[0]+'__mutation__') -NoNewline -Encoding utf8; $boardMutEvidence=@(Get-Content $boardFile | Where-Object { $_ -match '^\- receipt-loss 拆分证据' }); if ($boardMutEvidence.Count -ne 1 -or (Get-S ([ordered]@{line=$boardMutEvidence})) -eq '37273E8E2F319838B88017175C1570411BA573D80B51AA40F684A4E038487C36') { exit 1 } } finally { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
dod_exit: 0
dod_assert: check-cards 通过；A、既有 B、C、ASCII-SHIP 构成唯一串行链；A/B/C 的验收与 DoD 已冻结；看板钉住三份只读证据且未宣称正式 GREEN。
review_gate: codex {verdict:pass}
hygiene: 逐卡核对唯一 id、exact depends_on、空 parallelizable_with、实际 allow_paths 与可执行单行 DoD；只记录已验证证据。
doc_sync: 本登记 PR 同步 TASK-BOARD 与本卡 status=merged，作为合并后状态投影；不预填未来 squash SHA，不在六路径外自归档。actual ship 后只做 official cleanup，卡片由未来常规归档批次搬迁。
---

# T0-RECEIPT-LOSS-SPLIT-PLAN

## Light Plan Forge 结论

串行链固定为：

`T0-RECEIPT-AUTHORIZATION-BIT -> T0-RECEIPT-LOSS-FAIL-CLOSED -> T0-RECEIPT-LOSS-SOURCE-CONTRACT -> T0-ASCII-SHIP-CODES`

A 修复同一轮已验证/已铸 receipt 的内存授权；既有 B 承担已发布 receipt 四类失效态的运行时 fail-closed、真实 T37 与文档；C 只承担源码合同、enum/discovery 和 mutation 防回归。三卡共享 `scripts/selftest.ps1`，A/B 还共享 `scripts/task.ps1`，因此必须串行。

## 已拒绝方案

- 不交付 117k 超限 WIP；它只作为覆盖映射来源。
- 不按生产码/测试拆分；每张行为卡都必须携带能先红后绿的可证伪测试。
- 不并行 A/B/C；共享写路径会破坏精确基线和中间 GREEN。
- 不让 C 修 B 的运行时或文档；否则职责与字符预算重新合并。

## 证据边界

- `c53ec489f7bb4b89dbe81ec7273deb037bd2e65d`：超 60,000 字符的 reviewed WIP，只读。
- `session 54615`：旧代码真实 receipt 删除后的行为 RED；raw SHA256 `A6388DA76D404F6292E2905F5E5E7C3E7D904F67B2889CE6ACDB8139EC801637`，exit 1，HEAD PRE=POST `ceb2685e9e3ada76a503377584d512c0c6d2af4d`，唯一失败 `15r(e)B`。它只证明缺陷可观测；A 仍须在正式工作树 fresh RED。
- `afced255 -> 309a51a1 -> 4495fae854777eb4592d0b5223a9981de13f0ac4`：A prototype 的实现、授权测试加固、真实 scope-core 删除夹具修正；最终设计证据钉在末个提交，不可 cherry-pick 代替 TDD。

## 验收

见 front matter。注册卡只改六个 metadata/card/board 路径；A/B/C 均保持仓库正式 `1000/60000` 硬闸，C 再自限 `300/35000`。
