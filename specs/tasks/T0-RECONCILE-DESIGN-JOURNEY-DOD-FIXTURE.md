---
id: T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE
title: 规范化设计旅程卡源文本换行后再执行区域验收
depends_on: [T0-RECONCILE-DESIGN-JOURNEY-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md
forbid:
  - 修改产品代码、设计正文、验收集合、固定源 OID 或表格哈希
  - 更改旅程卡中除两处 git show 换行规范化以外的内容
non_goals:
  - 执行旅程卡
  - 修改组件卡
acceptance:
  - "A1 source/base 两个 git show 结果均先规范化 CRLF"
  - "A2 旅程卡其余内容逐字不变"
  - "A3 规范化后的区域正则可从固定提交解析三段"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$p='specs/tasks/T0-RECONCILE-DESIGN-JOURNEYS.md';$c=Get-Content $p -Raw;$baseCommit='fcceb5995c688b124e281f68d5875146f227c7da';$before=(&git show ($baseCommit+':'+$p)|Out-String);if($LASTEXITCODE-ne0){throw 'baseline'};$oid='77f9fa9ed2fca2beec295139950098bb94f41d52';$baseOid='13f6e809b345c01aa69d4d7090a52f404b96e1df';$d=[char]36;$nlq=[string][char]34+[char]96+'n'+[char]34;$oldSrc=$d+"src=&git show '"+$oid+":context/DESIGN.md'|Out-String;";$newSrc=$d+"src=(&git show '"+$oid+":context/DESIGN.md'|Out-String)-replace'\r\n',"+$nlq+";";$oldBase=$d+"base=&git show '"+$baseOid+":context/DESIGN.md'|Out-String;";$newBase=$d+"base=(&git show '"+$baseOid+":context/DESIGN.md'|Out-String)-replace'\r\n',"+$nlq+";";if([regex]::Matches($before,[regex]::Escape($oldSrc)).Count-ne1-or[regex]::Matches($before,[regex]::Escape($oldBase)).Count-ne1){throw 'baseline bindings'};$expected=$before.Replace($oldSrc,$newSrc).Replace($oldBase,$newBase);function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N $c)-cne(N $expected)){throw 'exact card'};$src=(&git show ($oid+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";$base=(&git show ($baseOid+':context/DESIGN.md')|Out-String)-replace'\r\n',"`n";$ef=[regex]::Match($src,'(?s)^---\n.*?\n---').Value;$pre=[regex]::Match($src,'(?ms)^# .*?(?=^## Colors$)').Value;$post=[regex]::Match($base,'(?ms)^## Colors\n.*\z').Value;if(-not$ef-or-not$pre-or-not$post){throw 'regions'}
dod_exit: 0
dod_assert: A1–A3 exact two normalizers；删除任一规范化或改动固定源即 RED
review_gate: codex {verdict:pass}
hygiene: 只修跨平台验收机械差异
doc_sync: 无
---

# T0-RECONCILE-DESIGN-JOURNEY-DOD-FIXTURE

旅程卡的固定源读取在 Windows PowerShell 下产生 CRLF，而区域正则以 LF 行尾为边界。本卡仅在两个 `git show` 结果进入验收逻辑前统一换行，不改变任何产品或设计契约。
