---
id: T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE
title: 登记 Foundations 目标组件映射与非空洞 DoD
depends_on: [T0-RECONCILE-DESIGN-FOUNDATION-R3-PAIR-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md
forbid:
  - 修改产品代码、主设计正文、源夹具或 R3 预算
  - 扩大到其他组件注册表或组件矩阵
non_goals:
  - 执行 Foundations 或 Components 卡
diagnosis: PR 165 的 R3 发现 Foundations 仅导入基础正文，却从源夹具而非目标文件读取组件颜色字段，导致目标缺少 evidence-rail 五项颜色映射及 inspection-item-card 三项层级/边界映射时 DoD 仍空洞通过。
acceptance:
  - "A1 Foundations 明确只额外导入两项必要组件颜色映射"
  - "A2 exact target 由基线、源基础区及两项源组件 block 唯一重建"
  - "A3 metadata 配对检查从目标文件读取，缺任一字段或 pair 即 RED"
  - "A4 Foundations 卡全文 hash 固定"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='9bdcf966d4beb676e286175158f5b17175ae8f71';$src=(&git show ($oid+':context/DESIGN.md')|Out-String).Replace([string][char]13,'');$base=(&git show '85bdf1df8eeb920bce019739ed49eefe541e4863:context/DESIGN.md'|Out-String).Replace([string][char]13,'');if($LASTEXITCODE-ne0){throw 'sources'};function Block($x,$id){$m=[regex]::Match($x,('(?ms)^  '+[regex]::Escape($id)+':\n.*?(?=^  [a-z0-9-]+:|^---$)'));if(-not$m.Success){throw ('block '+$id)};$m.Value};$region=[regex]::Match($src,'(?ms)^## Colors\n.*?(?=^## Components\n)').Value;$prefix=[regex]::Match($base,'(?ms)\A.*?(?=^## Colors\n)').Value;$suffix=[regex]::Match($base,'(?ms)^## Components\n.*\z').Value;foreach($id in 'evidence-rail','inspection-item-card'){$before=Block $prefix $id;$after=Block $src $id;if([regex]::Matches($prefix,[regex]::Escape($before)).Count-ne1){throw ('block count '+$id)};$prefix=$prefix.Replace($before,$after)};$expected=$prefix+$region+$suffix;$front=[regex]::Match($expected,'(?s)^---\n(.*?)\n---').Groups[1].Value;$cb=[regex]::Match($front,'(?ms)^components:\n(.*)\z').Groups[1].Value;function Fields($id){$b=Block $cb $id;$h=@{};foreach($x in [regex]::Matches($b,'(?m)^    ([A-Za-z0-9-]*Color): "\{colors\.([A-Za-z0-9-]+)\}"$')){$h[$x.Groups[1].Value]=$x.Groups[2].Value};$h};$e=Fields 'evidence-rail';$i=Fields 'inspection-item-card';$ew='backgroundColor,completeColor,missingRequiredColor,blockedColor,optionalColor'-split',';$iw='backgroundColor,textColor,boundaryColor,boundaryAdjacentColor'-split',';if($e.Count-ne5-or(Compare-Object ($e.Keys|sort) ($ew|sort))){throw 'evidence fields'};if($i.Count-ne4-or(Compare-Object ($i.Keys|sort) ($iw|sort))){throw 'item fields'};if($e.backgroundColor-cne'surface-container'-or$e.completeColor-cne'primary'-or$e.missingRequiredColor-cne'tertiary'-or$e.blockedColor-cne'error'-or$e.optionalColor-cne'outline'){throw 'evidence values'};if($i.backgroundColor-cne'surface-container'-or$i.textColor-cne'on-surface'-or$i.boundaryColor-cne'outline'-or$i.boundaryAdjacentColor-cne'surface-container-low'){throw 'item values'};$m=[regex]::Match($region,'(?ms)^### CI contrast gate metadata\n.*?\x60\x60\x60json\n(.*?)\n\x60\x60\x60');$j=$m.Groups[1].Value|ConvertFrom-Json -Depth 20;$decl=@($j.bindings|%{$_.foreground+'|'+$_.background});$want=@();foreach($theme in 'light','dark'){foreach($f in 'completeColor','missingRequiredColor','blockedColor','optionalColor'){$want+=($theme+'.'+$e[$f]+'|'+$theme+'.'+$e.backgroundColor)};$want+=($theme+'.'+$i.textColor+'|'+$theme+'.'+$i.backgroundColor);$want+=($theme+'.'+$i.boundaryColor+'|'+$theme+'.'+$i.boundaryAdjacentColor)};if($want.Count-ne12-or@($want|sort -Unique).Count-ne12){throw 'render set'};foreach($pair in $want){if($decl-cnotcontains$pair){throw ('render pair '+$pair)}};$task=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md' -Raw;if(-not$task.Contains('$fm=[regex]::Match($r')-or-not$task.Contains('$front=[regex]::Match($r')){throw 'target DoD source'};$sha=[Security.Cryptography.SHA256]::Create();$s=$task.Replace([string][char]13,'').TrimEnd();$actual=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($actual-cne'e5ac244ea65e8d5a5d3412d36f333257184f0c714b02bff4974cc5e953b2257f'){throw 'exact card'}
dod_exit: 0
dod_assert: A1–A4；exact target reconstruction、目标 $r 取证、两组件九字段、12 pairs 与 Foundations 卡 hash 任一漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 只修目标取证空洞与两项必要组件映射的任务边界
doc_sync: 无
---

# T0-RECONCILE-DESIGN-FOUNDATION-TARGET-FIXTURE

只登记 PR 165 本轮 R3 所需的目标组件映射与非空洞 DoD，不修改主设计正文。
