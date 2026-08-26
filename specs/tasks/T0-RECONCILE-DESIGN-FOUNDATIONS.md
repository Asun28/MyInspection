---
id: T0-RECONCILE-DESIGN-FOUNDATIONS
title: 拆分 Field Ledger 设计基础合同以满足完整评审预算
depends_on: [T0-RECONCILE-DESIGN-JOURNEYS]
status: todo
branch: T0-RECONCILE-DESIGN-FOUNDATIONS
worktree: C:\\wt\\T0-RECONCILE-DESIGN-FOUNDATIONS
source_ref: ad6b4870bd2ddbacaf950a3ee5412df6446d86d1:context/DESIGN.md
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
  - "A5 R3 修复：算法、37 bindings、namespace/allowlist、相机合成和 scope 语义确定"
dod_command: $r=(Get-Content 'context/DESIGN.md' -Raw)-replace'\r\n',"`n";$src=(&git show 'ad6b4870bd2ddbacaf950a3ee5412df6446d86d1:context/DESIGN.md'|Out-String)-replace'\r\n',"`n";if($LASTEXITCODE-ne0){throw 'source'};$base=(&git show '85bdf1df8eeb920bce019739ed49eefe541e4863:context/DESIGN.md'|Out-String)-replace'\r\n',"`n";if($LASTEXITCODE-ne0){throw 'base'};&git merge-base --is-ancestor 'ad6b4870bd2ddbacaf950a3ee5412df6446d86d1' 'refs/remotes/origin/codex/design-metadata-fixture-v2';if($LASTEXITCODE-ne0){throw 'source unreachable'};function Region($x,$a,$b){$m=[regex]::Match($x,('(?ms)^## '+[regex]::Escape($a)+'\n.*?(?=^## '+[regex]::Escape($b)+'\n)'));if(-not$m.Success){throw ('region '+$a)};$m.Value};$foundation=Region $src 'Colors' 'Components';$prefix=[regex]::Match($base,'(?ms)\A.*?(?=^## Colors\n)').Value;$suffix=[regex]::Match($base,'(?ms)^## Components\n.*\z').Value;if(-not$prefix-or-not$suffix){throw 'base regions'};function N($x){$x.TrimEnd()};if((N $r)-cne(N ($prefix+$foundation+$suffix))){throw 'file scope'};foreach($h in 'Contrast threshold contract','Dark token contrast map','Visual physics contract','CI contrast gate metadata'){if([regex]::Matches($foundation,('(?m)^### '+[regex]::Escape($h)+'$')).Count-ne1){throw ('heading '+$h)}};foreach($h in 'Typography','Layout','Elevation & Depth','Shapes'){if([regex]::Matches($foundation,('(?m)^## '+[regex]::Escape($h)+'$')).Count-ne1){throw ('section '+$h)}};foreach($sentinel in '| Essential icon, focus ring, input/card boundary, evidence segment | Any size | `3.00:1` against adjacent surface | WCAG non-text AA |','Keep primary controls at least `48dp` high','Expanded width constrains prose and forms to a `720dp` column','Shapes are **sturdy and measured**'){if(-not$foundation.Contains($sentinel)){throw ('foundation contract '+$sentinel)}};$m=[regex]::Match($foundation,'(?ms)^### CI contrast gate metadata\n.*?```json\n(.*?)\n```');if(-not$m.Success){throw 'contrast metadata'};$j=$m.Groups[1].Value|ConvertFrom-Json -Depth 20;if($j.schemaVersion-ne2-or$j.bindings.Count-ne37){throw 'contrast metadata shape'};$pairs=@($j.bindings|%{$_.foreground+'|'+$_.background});if(@($pairs|sort -Unique).Count-ne37){throw 'duplicate binding'};$ns=$j.namespaceResolution;if($ns.'light.<role>'-cne'frontmatter.colors.<role>'-or$ns.'dark.<role>'-cne'frontmatter.dark-colors.<role>'-or$ns.'camera.scrim-over-white'-cne'alphaCompositeSrgb(camera.scrim, 0.64, #FFFFFF)'){throw 'namespace'};$allow=@($j.pureColorAllowlist);$want='light.on-primary,light.on-secondary,light.on-tertiary,light.surface-container-low,light.on-error,light.on-privacy,camera.scrim,camera.on-scrim'-split',';if($allow.Count-ne$want.Count-or(Compare-Object ($allow|sort) ($want|sort))){throw 'pure allowlist'};function Lum($hex){$rgb=1,3,5|%{[Convert]::ToInt32($hex.Substring($_,2),16)/255};$lin=$rgb|%{if($_-le.04045){$_/12.92}else{[Math]::Pow(($_+.055)/1.055,2.4)}};.2126*$lin[0]+.7152*$lin[1]+.0722*$lin[2]};foreach($b in $j.bindings){$x=Lum $b.value;$y=Lum $b.backgroundValue;$ratio=([Math]::Max($x,$y)+.05)/([Math]::Min($x,$y)+.05);if($ratio+0.0001-lt$b.minRatio){throw ('binding ratio '+$b.foreground)}};foreach($bad in 'allowedComponents','componentContracts','200% font scale','Landscape is a supported fallback','Room panoramas default to the history overlay','opaque high-contrast scrim'){if($foundation.Contains($bad)){throw ('scope regression '+$bad)}};foreach($s in 'c_srgb = channel / 255','c_srgb <= 0.04045','c_srgb / 12.92','((c_srgb + 0.055) / 1.055) ^ 2.4','Tablet and landscape optimisation are outside the current UI card','Room panoramas may default to the history overlay; item photos do not.','worst case is a white preview composited to `#5C5C5C`, which gives `6.69:1` contrast'){if(-not$foundation.Contains($s)){throw ('review fix '+$s)}}
dod_exit: 0
dod_assert: A1–A5 exact source region；37 bindings/namespace/allowlist/ratio 与 R3 scope/camera 语义漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 以独立区域拆卡，不删减源设计
doc_sync: R5 同步 owning docs
---

# T0-RECONCILE-DESIGN-FOUNDATIONS

仅把颜色、排版、布局、层级与形状基础合同从已审源夹具落入主设计文档，使后续 81 组件矩阵能在 R3 完整读取预算内独立评审。
