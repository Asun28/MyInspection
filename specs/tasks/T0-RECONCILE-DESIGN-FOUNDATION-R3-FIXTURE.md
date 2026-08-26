---
id: T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE
title: 登记 Foundations R3 修订源与两张下游卡的新钉点
depends_on: [T0-RECONCILE-DESIGN-COMPONENT-SPLIT]
status: todo
branch: T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、主设计正文或 R3 预算
  - 改写既有源提交历史或删除组件合同
non_goals:
  - 执行 Foundations 或 Components 卡
diagnosis: PR 165 的首轮 R3 发现固定源 c9a34b3 在基础区域混入组件/无障碍合同，并缺少完整 WCAG 算法、完整 token bindings 与一致的相机 scrim 合成语义；旧钉点无法在原卡范围内修复。
acceptance:
  - "A1 ad6b487 是远端夹具分支上 c9a34b3 的直接单文件后继"
  - "A2 源含确定 WCAG 算法、37 个唯一 bindings、完整 namespace 与 pure-color allowlist"
  - "A3 源移除越界组件/无障碍规则并恢复 landscape 与 panorama 既有语义"
  - "A4 Foundations 与 Components 两卡全文固定并共同改钉新源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='ad6b4870bd2ddbacaf950a3ee5412df6446d86d1';$old='c9a34b314cdf38986b2584c35371b17a73438003';$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote ref'};$parent=(&git rev-parse ($oid+'^')).Trim();if($LASTEXITCODE-ne0-or$parent-cne$old){throw 'direct parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($LASTEXITCODE-ne0-or$paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture paths'};$stat=(&git diff-tree --no-commit-id --numstat -r $oid).Trim();if($LASTEXITCODE-ne0-or$stat-cne("69`t54`tcontext/DESIGN.md")){throw ('fixture stat '+$stat)};$src=(&git show ($oid+':context/DESIGN.md')|Out-String).Replace([string][char]13,'');if($LASTEXITCODE-ne0){throw 'source'};$region=[regex]::Match($src,'(?ms)^## Colors\n.*?(?=^## Components\n)').Value;$m=[regex]::Match($region,'(?ms)^### CI contrast gate metadata\n.*?```json\n(.*?)\n```');if(-not$m.Success){throw 'metadata'};$j=$m.Groups[1].Value|ConvertFrom-Json -Depth 20;if($j.schemaVersion-ne2-or$j.bindings.Count-ne37){throw 'metadata shape'};$pairs=@($j.bindings|%{$_.foreground+'|'+$_.background});if(@($pairs|sort -Unique).Count-ne37-or@($j.bindings|?{$_.foreground-like'light.*'}).Count-ne18-or@($j.bindings|?{$_.foreground-like'dark.*'}).Count-ne18-or@($j.bindings|?{$_.foreground-like'camera.*'}).Count-ne1){throw 'binding set'};$ns=$j.namespaceResolution;if($ns.'light.<role>'-cne'frontmatter.colors.<role>'-or$ns.'dark.<role>'-cne'frontmatter.dark-colors.<role>'-or$ns.'camera.scrim-over-white'-cne'alphaCompositeSrgb(camera.scrim, 0.64, #FFFFFF)'){throw 'namespace'};$allow=@($j.pureColorAllowlist);$want='light.on-primary,light.on-secondary,light.on-tertiary,light.surface-container-low,light.on-error,light.on-privacy,camera.scrim,camera.on-scrim'-split',';if($allow.Count-ne$want.Count-or(Compare-Object ($allow|sort) ($want|sort))){throw 'pure allowlist'};function Lum($hex){$rgb=1,3,5|%{[Convert]::ToInt32($hex.Substring($_,2),16)/255};$lin=$rgb|%{if($_-le.04045){$_/12.92}else{[Math]::Pow(($_+.055)/1.055,2.4)}};.2126*$lin[0]+.7152*$lin[1]+.0722*$lin[2]};foreach($b in $j.bindings){$x=Lum $b.value;$y=Lum $b.backgroundValue;$ratio=([Math]::Max($x,$y)+.05)/([Math]::Min($x,$y)+.05);if($ratio+0.0001-lt$b.minRatio){throw ('ratio '+$b.foreground)}};foreach($bad in 'allowedComponents','componentContracts','200% font scale','Landscape is a supported fallback','Room panoramas default to the history overlay','opaque high-contrast scrim'){if($region.Contains($bad)){throw ('scope regression '+$bad)}};foreach($s in 'c_srgb = channel / 255','c_srgb <= 0.04045','c_srgb / 12.92','((c_srgb + 0.055) / 1.055) ^ 2.4','Tablet and landscape optimisation are outside the current UI card','Room panoramas may default to the history overlay; item photos do not.','worst case is a white preview composited to `#5C5C5C`, which gives `6.69:1` contrast'){if(-not$region.Contains($s)){throw ('review fix '+$s)}};$sha=[Security.Cryptography.SHA256]::Create();function ExactHash($p,$e){$s=(Get-Content $p -Raw).Replace([string][char]13,'').TrimEnd();$a=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($a-cne$e){throw ('exact card '+$p)}};ExactHash 'specs/tasks/T0-RECONCILE-DESIGN-FOUNDATIONS.md' '19032f68fe984f2d19804f1a8296f8b724f673e364476c7c8bf8025bbfab2f3e';ExactHash 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' '2380d4612f5805470b1cf3ddbae9fd27bce69e1d90eb54191aadc98c4c693986'
dod_exit: 0
dod_assert: A1–A4；远端 tip/直接父提交/单文件 stat、37 bindings/算法/namespace/allowlist/ratio/scope 语义及两卡全文 hash 任一漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 仅登记经 R3 反馈修订的不可变源与下游钉点
doc_sync: 无
---

# T0-RECONCILE-DESIGN-FOUNDATION-R3-FIXTURE

只登记 PR 165 首轮 R3 所需的源修订与两张现有执行卡的新钉点，不改变主设计正文。
