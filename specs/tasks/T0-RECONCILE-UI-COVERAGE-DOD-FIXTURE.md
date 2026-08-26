---
id: T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE
title: 修正 UI Elements 覆盖卡的 Windows CRLF 表格解析
depends_on: [T0-RECONCILE-DESIGN-COMPONENTS]
status: todo
branch: T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE
worktree: C:\wt\T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE.md
  - specs/tasks/T0-RECONCILE-UI-COVERAGE.md
forbid:
  - 修改产品、设计源或 UI Elements 内容
  - 放宽列数、唯一性或映射断言
non_goals:
  - 实现 UI 覆盖索引
acceptance:
  - "A1 Row parser strips CRLF whitespace before edge delimiters"
  - "A2 source rows resolve to four columns on Windows"
  - "A3 only the parser normalization changes"
dod_command: $p='specs/tasks/T0-RECONCILE-UI-COVERAGE.md';$r=Get-Content $p -Raw;$old=&git show "refs/remotes/origin/master:$p"|Out-String;if($LASTEXITCODE-ne0){throw 'baseline'};$needle='$m[0].Value.Trim(''|'').Split(''|'')';$fixed='$m[0].Value.Trim().Trim(''|'').Split(''|'')';if([regex]::Matches($r,[regex]::Escape($fixed)).Count-ne1-or$r.Contains($needle)){throw 'parser'};function N($v){($v-replace'\r\n',"`n").TrimEnd()};if((N $r)-cne(N ($old.Replace($needle,$fixed)))){throw 'scope'};$src=&git show '235d40fb06ae8afd7675ea1b80e06c1a3a4b43bf:docs/UI-UX-ELEMENTS.md'|Out-String;$row=[regex]::Match($src,'(?m)^\| `PROPERTIES_ROOT` \|[^\r\n]+\r?$').Value;$cols=@($row.Trim().Trim('|').Split('|')|%{$_.Trim()});if($cols.Count-ne4-or@($cols|?{-not$_}).Count){throw 'crlf'};pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-RECONCILE-UI-COVERAGE;if($LASTEXITCODE-ne0){throw 'card'}
dod_exit: 0
dod_assert: A1–A3；CRLF source row exact four columns；single parser-only change
review_gate: codex {verdict:pass}
hygiene: 不放宽既有映射契约
doc_sync: R5 owning card
---

# T0-RECONCILE-UI-COVERAGE-DOD-FIXTURE

Normalize row whitespace before trimming Markdown edge delimiters so native PowerShell `Out-String` CRLF does not create a phantom trailing column.

