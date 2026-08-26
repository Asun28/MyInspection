---
id: T0-RECONCILE-DESIGN-FOUNDATIONS
title: 拆分 Field Ledger 设计基础合同以满足完整评审预算
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-FOUNDATIONS
worktree: C:\\wt\\T0-RECONCILE-DESIGN-FOUNDATIONS
source_ref: c9a34b314cdf38986b2584c35371b17a73438003:context/DESIGN.md
allow_paths:
  - context/DESIGN.md
forbid:
  - 修改产品代码、组件 id、行程合同或产品范围
  - 降低对比度、触控尺寸、布局密度或形状语义要求
non_goals:
  - 组件注册表、组件矩阵、动效、无障碍或 Compose 实现
acceptance:
  - "A1 WCAG 文本与非文本对比度算法和阈值完整"
  - "A2 light/dark token 映射及 machine-readable tokens 完整"
  - "A3 typography、layout、elevation、shape 合同完整"
  - "A4 仅替换 Colors 到 Components 前的基础设计区域"
dod_command: $r=(Get-Content 'context/DESIGN.md' -Raw)-replace'\r\n',"`n";$src=(&git show 'c9a34b314cdf38986b2584c35371b17a73438003:context/DESIGN.md'|Out-String)-replace'\r\n',"`n";if($LASTEXITCODE-ne0){throw 'source'};$base=(&git show '85bdf1df8eeb920bce019739ed49eefe541e4863:context/DESIGN.md'|Out-String)-replace'\r\n',"`n";if($LASTEXITCODE-ne0){throw 'base'};&git merge-base --is-ancestor 'c9a34b314cdf38986b2584c35371b17a73438003' 'refs/remotes/origin/codex/design-metadata-fixture-v2';if($LASTEXITCODE-ne0){throw 'source unreachable'};function Region($x,$a,$b){$m=[regex]::Match($x,('(?ms)^## '+[regex]::Escape($a)+'\n.*?(?=^## '+[regex]::Escape($b)+'\n)'));if(-not$m.Success){throw ('region '+$a)};$m.Value};$foundation=Region $src 'Colors' 'Components';$prefix=[regex]::Match($base,'(?ms)\A.*?(?=^## Colors\n)').Value;$suffix=[regex]::Match($base,'(?ms)^## Components\n.*\z').Value;if(-not$prefix-or-not$suffix){throw 'base regions'};function N($x){$x.TrimEnd()};if((N $r)-cne(N ($prefix+$foundation+$suffix))){throw 'file scope'};foreach($h in 'Contrast threshold contract','Dark token contrast map','Visual physics contract','CI contrast gate metadata'){if([regex]::Matches($foundation,('(?m)^### '+[regex]::Escape($h)+'$')).Count-ne1){throw ('heading '+$h)}};foreach($h in 'Typography','Layout','Elevation & Depth','Shapes'){if([regex]::Matches($foundation,('(?m)^## '+[regex]::Escape($h)+'$')).Count-ne1){throw ('section '+$h)}};foreach($sentinel in '| Essential icon, focus ring, input/card boundary, evidence segment | Any size | `3.00:1` against adjacent surface | WCAG non-text AA |','Keep primary controls at least `48dp` high','Expanded width constrains prose and forms to a `720dp` column','Shapes are **sturdy and measured**'){if(-not$foundation.Contains($sentinel)){throw ('foundation contract '+$sentinel)}}
dod_exit: 0
dod_assert: A1–A4 exact source region；基础区、边界或阈值漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 以独立区域拆卡，不删减源设计
doc_sync: R5 同步 owning docs
---

# T0-RECONCILE-DESIGN-FOUNDATIONS

仅把颜色、排版、布局、层级与形状基础合同从已审源夹具落入主设计文档，使后续 81 组件矩阵能在 R3 完整读取预算内独立评审。
