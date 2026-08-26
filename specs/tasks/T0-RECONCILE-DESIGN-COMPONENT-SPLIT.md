---
id: T0-RECONCILE-DESIGN-COMPONENT-SPLIT
title: 将超预算设计组件卡拆为基础与组件两个完整评审单元
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENT-SPLIT
worktree: C:\\wt\\T0-RECONCILE-DESIGN-COMPONENT-SPLIT
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENT-SPLIT.md
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、设计内容或源夹具
  - 提高 R3 预算或删减设计以绕过闸门
non_goals:
  - 执行基础卡或组件卡
diagnosis: 组件卡的 432 个 changed lines 合格，但完整 unified diff 为 63429 字符，超过 R3 的 60000 字符硬上限；根因是颜色/排版/布局基础与 81 组件矩阵被塞进同一评审单元。
acceptance:
  - "A1 新基础卡只落 Colors 到 Components 前的精确源区域"
  - "A2 原组件卡改为依赖基础卡，最终设计内容不变"
  - "A3 两卡均保留完整自验证 DoD，未放宽预算"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$base='85bdf1df8eeb920bce019739ed49eefe541e4863';$p='specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md';$e=(&git show ($base+':'+$p)|Out-String);if($LASTEXITCODE-ne0){throw 'baseline'};$old='depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]';$new='depends_on: [T0-RECONCILE-DESIGN-FOUNDATIONS]';if([regex]::Matches($e,[regex]::Escape($old)).Count-ne1){throw 'dependency baseline'};$e=$e.Replace($old,$new);function N($x){($x-replace'\r\n',"`n").TrimEnd()};if((N (Get-Content $p -Raw))-cne(N $e)){throw 'component exact'};$f=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md' -Raw;if($f-notmatch'(?m)^depends_on: \[T0-RECONCILE-DESIGN-JOURNEYS\]$'-or$f-notmatch'(?m)^allow_paths:\r?\n  - context/DESIGN\.md$'-or$f-notmatch'63429'-and$f-notmatch'完整评审预算'){throw 'foundation card'};if($f-notmatch'85bdf1df8eeb920bce019739ed49eefe541e4863'-or$f-notmatch'c9a34b314cdf38986b2584c35371b17a73438003'){throw 'pinned refs'}
dod_exit: 0
dod_assert: A1–A3 exact split/card dependency；删内容或放宽预算即 RED
review_gate: codex {verdict:pass}
hygiene: 预算拆分以语义边界为单位
doc_sync: 无
---

# T0-RECONCILE-DESIGN-COMPONENT-SPLIT

只登记预算拆卡，不改变产品设计。

