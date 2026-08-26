---
id: T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE
title: 登记 UI Elements 恢复确认组件修订源
depends_on: [T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE]
status: todo
branch: T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE.md
  - specs/tasks/T0-RECONCILE-UI-COVERAGE.md
forbid:
  - 修改产品、设计正文或放宽 UI Elements 引用校验
  - 保留未注册的通用 button 引用
non_goals:
  - 执行 UI 覆盖索引卡
acceptance:
  - "A1 b3158d1 is the remote direct single-file successor of d041b1c"
  - "A2 restore confirmation uses button-destructive"
  - "A3 generic button reference is absent"
  - "A4 coverage card pins the successor and dependency"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='b3158d11c76b02505f71b257159b5e608596a066';$parent='d041b1c13312591d82891e5de29442028dc441fd';$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'remote'};if((&git rev-parse ($oid+'^')).Trim()-cne$parent){throw 'parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($paths.Count-ne1-or$paths[0]-cne'docs/UI-UX-ELEMENTS.md'){throw 'paths'};if((&git diff-tree --no-commit-id --numstat -r $oid).Trim()-cne("1`t1`tdocs/UI-UX-ELEMENTS.md")){throw 'stat'};$src=&git show ($oid+':docs/UI-UX-ELEMENTS.md')|Out-String;if([regex]::Matches($src,'(?m)^\| restore replacement confirmation \| `ALERT_DIALOG` \| `preflight-summary`, `confirmation-input`, `button-destructive` \| Replace action or first blocker \|\r?$').Count-ne1-or$src.Contains('destructive `button`')){throw 'element'};$p='specs/tasks/T0-RECONCILE-UI-COVERAGE.md';$r=Get-Content $p -Raw;$old=&git show "refs/remotes/origin/master:$p"|Out-String;$e=$old.Replace('depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX]','depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX, T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE]').Replace('235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf',$oid);function N($v){($v-replace'\r\n',"`n").TrimEnd()};if((N $r)-cne(N $e)){throw 'card scope'}
dod_exit: 0
dod_assert: A1–A4；remote tip/parent/stat；registered destructive element；exact coverage card transform
review_gate: codex {verdict:pass}
hygiene: 源提交单文件、单行修复
doc_sync: R5 owning card
---

# T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE

Pin the corrected UI Elements source before the coverage projection is shipped.
