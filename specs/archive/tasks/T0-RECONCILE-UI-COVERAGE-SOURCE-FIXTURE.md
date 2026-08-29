---
id: T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE
title: 登记可完整验收的 UI Elements 覆盖源
depends_on: [T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE]
status: merged
branch: T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE.md
  - specs/tasks/T0-RECONCILE-UI-COVERAGE.md
forbid:
  - 修改产品、设计正文或放宽 UI 覆盖断言
  - 引入未注册 Element 或第二套设计权威
non_goals:
  - 执行 UI 覆盖索引卡
acceptance:
  - "A1 f9d12e7 is the durable four-commit UI-only successor of b3158d1"
  - "A2 every system surface references registered elements"
  - "A3 exclusions and accessibility sentinels are unique"
  - "A4 coverage card pins the final source and dependency"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='f9d12e75f122b26ce42a9fa8b85be8f8649af957';$old='b3158d11c76b02505f71b257159b5e608596a066';$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'remote'};&git merge-base --is-ancestor $old $oid;if($LASTEXITCODE-ne0-or[double](&git rev-list --count ($old+'..'+$oid))-ne4){throw 'chain'};$paths=@(&git diff --name-only $old $oid);if($paths.Count-ne1-or$paths[0]-cne'docs/UI-UX-ELEMENTS.md'){throw 'paths'};if((&git diff --numstat $old $oid).Trim()-cne("9`t7`tdocs/UI-UX-ELEMENTS.md")){throw 'stat'};$src=&git show ($oid+':docs/UI-UX-ELEMENTS.md')|Out-String;foreach($p in @('(?m)^> Normative source: `context/DESIGN\.md`\r?$','(?m)^v1 explicitly excludes FAB, drawer, carousel, charts, global snackbar, and remote telemetry\.\r?$','(?m)^\| Folder/file/create picker \| `SYSTEM_SURFACE` \| `destination-row`;','(?m)^\| Android app settings \| `SYSTEM_SURFACE` \| `recovery-panel`;','(?m)^\| Speech recognizer \| `SYSTEM_SURFACE` \| `input-field`, `phrase-sheet`;','48dp','200%','TalkBack','Reduce Motion','compact / medium / expanded')){if([regex]::Matches($src,$p).Count-ne1){throw ('source '+$p)}};if($src.Contains('destructive `button`')){throw 'generic button'};$p='specs/tasks/T0-RECONCILE-UI-COVERAGE.md';$r=Get-Content $p -Raw;$base=&git show "refs/remotes/origin/master:$p"|Out-String;$e=$base.Replace('depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX, T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE]','depends_on: [T0-RECONCILE-DESIGN-COMPONENTS, T0-RECONCILE-ROADMAP-INDEX, T0-RECONCILE-UI-COVERAGE-ELEMENT-FIXTURE, T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE]').Replace($old,$oid);function N($v){($v-replace'\r\n',"`n").TrimEnd()};if((N $r)-cne(N $e)){throw 'card scope'}
dod_exit: 0
dod_assert: A1–A4；remote chain/path/stat；registered surfaces；unique sentinels；exact card transform
review_gate: codex {verdict:pass}
hygiene: 四个同文件源修复作为一个最终钉源单元
doc_sync: R5 owning card
---

# T0-RECONCILE-UI-COVERAGE-SOURCE-FIXTURE

Pin the preflighted UI Elements source whose complete coverage DoD has already run green.
