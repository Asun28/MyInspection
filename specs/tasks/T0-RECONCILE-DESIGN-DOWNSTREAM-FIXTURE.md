---
id: T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE
title: 同步旅程与组件卡到可移植的设计元数据源夹具
depends_on: [T0-RECONCILE-DESIGN-METADATA-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、设计正文、任务卡验收集合或哈希
  - 更改除 source_ref 与对应 git show OID 以外的目标卡内容
non_goals:
  - 执行旅程卡或组件卡
  - 合并设计源夹具分支
acceptance:
  - "A1 两张目标卡各有两处 c66fd13 源绑定"
  - "A2 两张目标卡均不再引用 235d40f"
  - "A3 c66fd13 可从 origin 专用分支解析"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$old='235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf';$oid='c66fd137dc2e869a04b0938ce1b41cf14a8f50f1';$needle=$oid+':context/DESIGN.md';foreach($p in 'specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md','specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md'){$c=Get-Content $p -Raw;if([regex]::Matches($c,[regex]::Escape($needle)).Count-ne2-or$c.Contains($old)){throw ('source binding '+$p)}};$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote ref'}
dod_exit: 0
dod_assert: A1–A3 exact two-card source binding；改回任一旧 OID 即 RED
review_gate: codex {verdict:pass}
hygiene: 同类源绑定一次扫全
doc_sync: 无
---

# T0-RECONCILE-DESIGN-DOWNSTREAM-FIXTURE

元数据卡已切到可移植的 YAML 安全夹具，但旅程卡和组件卡仍引用旧源，执行时会重新引入未引号化的 `ON/OFF`。本卡只同步两张下游卡的源提交 OID。
