---
id: T0-RECONCILE-LESSONS-FIXTURE
title: 修正本地调和 lessons 的可执行源夹具
depends_on: []
status: todo
branch: T0-RECONCILE-LESSONS-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 放宽 L242–L247 的精确目标、schema、排除项或变异检查
non_goals:
  - 执行 T0-RECONCILE-LESSONS
  - 改写原始本地快照提交 44d3e13
acceptance:
  - "A1 LESSONS 卡显式登记 ledger_source_ref=d141f3d58ba386887aafb17a3893db50fcde814f:docs/lessons/LEDGER.md"
  - "A2 ledger 构造只从 d141f3d 读取六个映射源块；CLAUDE L17 仍从不可变原始快照 44d3e13 读取"
  - "A3 d141f3d 的 L242/L238/L243/L244/L245/L248 每块都有唯一非空 refs；删除任一 refs 或改回旧 ledger OID 即 RED"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='d141f3d58ba386887aafb17a3893db50fcde814f';$needle=$oid+':docs/lessons/LEDGER.md';if([regex]::Matches($c,('(?m)^ledger_source_ref: '+[regex]::Escape($needle)+'\r?$')).Count-ne1){throw 'source ref'};if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2){throw 'ledger binding'};$stale='44d3e13a9742fe17fb8df6170d7499fff8835dc0:docs/lessons/LEDGER.md';if($c.Contains($stale)){throw 'stale ledger'};$raw='44d3e13a9742fe17fb8df6170d7499fff8835dc0:CLAUDE.md';if([regex]::Matches($c,[regex]::Escape($raw)).Count-ne1){throw 'raw CLAUDE'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};foreach($id in 'L242','L238','L243','L244','L245','L248'){$b=[regex]::Match($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*')).Value;if([regex]::Matches($b,'(?m)^- refs: .+\r?$').Count-ne1){throw ('refs '+$id)}}
dod_exit: 0
dod_assert: A1–A3 exact source binding；删除任一 refs 或改回旧 ledger OID 即 RED
review_gate: codex {verdict:pass}
hygiene: 只修不可满足的 fixture binding
doc_sync: 无
---

# T0-RECONCILE-LESSONS-FIXTURE

注册批次把六个 lessons 块精确绑定到原始脏快照，但该快照的六个 `refs:` 均为空，与同卡的非空 schema 闸矛盾。本卡只把 ledger 源切到原始快照的不可合并子夹具 `d141f3d`；该子夹具仅补齐六个可追溯引用。
