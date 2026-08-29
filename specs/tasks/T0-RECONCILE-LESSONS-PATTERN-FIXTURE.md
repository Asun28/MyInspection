---
id: T0-RECONCILE-LESSONS-PATTERN-FIXTURE
title: 补齐 lessons 精确源的变异守卫语义
depends_on: [T0-RECONCILE-LESSONS-FIXTURE]
status: todo
branch: T0-RECONCILE-LESSONS-PATTERN-FIXTURE
worktree: C:\wt\T0-RECONCILE-LESSONS-PATTERN-FIXTURE
allow_paths:
  - specs/archive/tasks/T0-RECONCILE-LESSONS.md
forbid:
  - 修改产品代码、lessons 正文、CLAUDE 或其他调和卡
  - 放宽六块精确构造、语义模式或排除源检查
non_goals:
  - 执行 T0-RECONCILE-LESSONS
  - 改写原始本地快照提交
acceptance:
  - "A1 LESSONS 的 ledger_source_ref 与构造命令唯一指向 8d16b11668732faa4d1f78d43870426fb91b5ca5"
  - "A2 新夹具在 d141f3d 的六个非空 refs 基础上，只为 L242 补齐 mutation guard 可证伪词组"
  - "A3 六块全部既有模式对精确构造目标通过；删除 mutation guard 或改回 d141f3d 即 RED"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/archive/tasks/T0-RECONCILE-LESSONS.md';$c=Get-Content $p -Raw;$oid='8d16b11668732faa4d1f78d43870426fb91b5ca5';$needle=$oid+':docs/lessons/LEDGER.md';if([regex]::Matches($c,('(?m)^ledger_source_ref: '+[regex]::Escape($needle)+'\r?$')).Count-ne1-or[regex]::Matches($c,[regex]::Escape($needle)).Count-ne2){throw 'binding'};if($c.Contains('d141f3d58ba386887aafb17a3893db50fcde814f:docs/lessons/LEDGER.md')){throw 'stale'};$s=&git show $needle|Out-String;if($LASTEXITCODE-ne0){throw 'fixture'};$b=[regex]::Match($s,'(?ms)^## L242\r?\n(?:(?!^## L[0-9]+).)*').Value;if([regex]::Matches($b,'mutation.*guard').Count-ne1-or[regex]::Matches($b,'(?m)^- refs: .+\r?$').Count-ne1){throw 'L242'};foreach($id in 'L238','L243','L244','L245','L248'){$q=[regex]::Match($s,('(?ms)^## '+$id+'\r?\n(?:(?!^## L[0-9]+).)*')).Value;if([regex]::Matches($q,'(?m)^- refs: .+\r?$').Count-ne1){throw ('refs '+$id)}}
dod_exit: 0
dod_assert: A1–A3 exact binding；删除 mutation guard 或改回 d141f3d 即 RED
review_gate: codex {verdict:pass}
hygiene: 单一语义缺口，零旁改
doc_sync: 无
---

# T0-RECONCILE-LESSONS-PATTERN-FIXTURE

前一修复补齐了六个空 `refs:`，随后的真实 DoD 证明 L242 还缺注册卡要求的英文 `mutation … guard` 语义。本卡只把 ledger 源前移到不可合并子夹具 `8d16b11`；其相对前一夹具仅修改 L242 的 refs 行。
