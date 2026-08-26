---
id: T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE
title: 登记 Components 原始音频保留修订源
depends_on: [T0-RECONCILE-DESIGN-COMPONENT-R3-FIXTURE]
status: todo
branch: T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE
worktree: C:\\wt\\T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE
allow_paths:
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE.md
  - specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md
forbid:
  - 修改产品代码、主设计正文、R3 预算或既有提交历史
  - 提供任何已保存音频删除路径
non_goals:
  - 执行 Components 卡或设计音频存储 schema
diagnosis: PR 169 的第二轮 R3 发现 audio-evidence-control 允许 finalize 前删除已保存音频，违反原始音频永远保留并可恢复、可重处理的硬不变量。
acceptance:
  - "A1 d041b1c 是远端夹具分支上 97f2fb2 的直接单文件后继"
  - "A2 已保存音频只能追加并保持可播放，不得删除或解除审计关联"
  - "A3 finalize 后只读，原始字节及审计关联持续可恢复、可重处理"
  - "A4 81 个组件与 49 个对比度 bindings 保持不变"
  - "A5 Components 卡全文固定并改钉新源"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1;if($LASTEXITCODE-ne0){exit 1};$oid='d041b1c13312591d82891e5de29442028dc441fd';$old='97f2fb2db443575e38e0b321ae8b22f087122fe0';$remote=&git ls-remote --exit-code origin 'refs/heads/codex/design-metadata-fixture-v2'|Out-String;if($LASTEXITCODE-ne0-or[regex]::Match($remote,'^[0-9a-f]{40}').Value-cne$oid){throw 'durable remote ref'};$parent=(&git rev-parse ($oid+'^')).Trim();if($LASTEXITCODE-ne0-or$parent-cne$old){throw 'direct parent'};$paths=@(&git diff-tree --no-commit-id --name-only -r $oid);if($LASTEXITCODE-ne0-or$paths.Count-ne1-or$paths[0]-cne'context/DESIGN.md'){throw 'fixture paths'};$stat=(&git diff-tree --no-commit-id --numstat -r $oid).Trim();if($LASTEXITCODE-ne0-or$stat-cne("1`t1`tcontext/DESIGN.md")){throw ('fixture stat '+$stat)};$src=(&git show ($oid+':context/DESIGN.md')|Out-String).Replace([string][char]13,'');if($LASTEXITCODE-ne0){throw 'source'};foreach($stale in 'playback/delete after save','delete is confirmed when allowed before finalize'){if($src.Contains($stale)){throw ('stale audio '+$stale)}};foreach($s in 'saved-recording playback/history','Each successful recording appends','saved recordings remain playable and are never deleted or detached','Finalized audio is read-only','original bytes and audit associations remain available for recovery and future reprocessing'){if([regex]::Matches($src,[regex]::Escape($s)).Count-ne1){throw ('audio contract '+$s)}};if(-not(Get-Content 'CLAUDE.md' -Raw).Contains('原始音频永远保留')){throw 'hard invariant'};$audio=Get-Content 'android/core/src/main/sqldelight/nz/myinspection/core/db/Audio.sq' -Raw;if(-not$audio.Contains('故意不提供 delete 查询')-or[regex]::IsMatch($audio,'(?mi)^\s*(delete|remove)[A-Za-z0-9_]*:\s*$')){throw 'persistence invariant'};$fm=[regex]::Match($src,'(?s)^---\n(.*?)\n---').Groups[1].Value;$registry=@([regex]::Matches([regex]::Match($fm,'(?ms)^components:\n(.*)\z').Groups[1].Value,'(?m)^  ([a-z0-9-]+):'));if($registry.Count-ne81){throw 'component count'};$m=[regex]::Match($src,'(?ms)^### CI contrast gate metadata\n.*?\x60\x60\x60json\n(.*?)\n\x60\x60\x60');if(-not$m.Success-or($m.Groups[1].Value|ConvertFrom-Json -Depth 20).bindings.Count-ne49){throw 'binding count'};$task=Get-Content 'specs/tasks/T0-RECONCILE-DESIGN-COMPONENTS.md' -Raw;$sha=[Security.Cryptography.SHA256]::Create();$s=$task.Replace([string][char]13,'').TrimEnd();$actual=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($s)))-replace'-','').ToLower();if($actual-cne'25452364ea3361b9ddffc82f4d6fd4c541354cdb3f7cd9cceee9d883489df826'){throw 'exact card'}
dod_exit: 0
dod_assert: A1–A5；远端 tip/parent/stat、音频保留硬不变量、持久层无删除入口、81/49 数量与 Components 卡 hash 任一漂移即 RED
review_gate: codex {verdict:pass}
hygiene: 仅登记本轮 R3 原始音频保留修订源及下游钉点
doc_sync: 无
---

# T0-RECONCILE-DESIGN-COMPONENT-AUDIO-FIXTURE

只登记 PR 169 第二轮 R3 所需的原始音频保留合同，不修改主设计正文。
