---
id: T0-R3-INPUT-DOD-CONTRACT
title: Bind INPUT-TRUST acceptance to a shared real A5 behavior fixture
depends_on: []
status: merged
branch: T0-R3-INPUT-DOD-CONTRACT
worktree: C:\wt\T0-R3-INPUT-DOD-CONTRACT
allow_paths:
  - specs/tasks/T0-R3-DIFF-INPUT-TRUST.md
  - specs/tasks/T0-R3-INPUT-DOD-CONTRACT.md
forbid:
  - Changing runtime code, tests, workflows, configuration, budgets, RED receipts, review counters, or any path outside these two cards
  - Weakening or changing INPUT acceptance A1-A10, forbid, non_goals, dependencies, status, hygiene, or doc_sync
  - Treating metadata acceptance as capability implementation, A5 RED/GREEN, complete seeded verification, or remote delivery evidence
non_goals:
  - Implementing the focused fixture or INPUT-TRUST production defenses
  - Repairing receipt-loss machinery, replaying old receipts, or changing OID-BINDING scope
  - Extending the completed T0-SCAFFOLD-CARD-CONTRACT-REPAIR change
diagnosis: The INPUT card searches source text for three strings, so its DoD cannot establish the required real A5 RED; its historical claim that external-diff and textconv defenses were already present disagrees with the fixed aa0 baseline.
acceptance:
  - "A1 INPUT equals fixed aa0 source after exactly three unique edits: focused dod_command, explicit shared A5 RED and unchanged complete A1-A10/seeded obligations, and the historical-defense correction. All other target content remains exact."
  - "A2 This card equals its pinned complete contract hash excluding only its unique dod_command line; its merged status is the explicit post-merge projection, while INPUT remains todo."
  - "A3 The candidate changes only these two cards. Metadata checks do not execute or claim the future focused fixture, and normal verify, scope, license, secrets, diff budget, independent R3 and exact-candidate CI remain required."
dod_command: $ErrorActionPreference='Stop'; $PSNativeCommandUseErrorActionPreference=$false; $b='aa0c79b302aa6b3bf37de37eac9af0648ebee7fb'; $p='specs/tasks/T0-R3-DIFF-INPUT-TRUST.md'; $s='specs/tasks/T0-R3-INPUT-DOD-CONTRACT.md'; $read={param($path) [IO.File]::ReadAllText((Join-Path (Get-Location) $path)).Replace("`r`n","`n")}; $x=((& git show ($b+':'+$p)) -join "`n")+"`n"; if($LASTEXITCODE){throw '[INPUT-DOD-SOURCE] fixed source unavailable'}; $edit={param($text,$pattern,$value) if([regex]::Matches($text,$pattern).Count -ne 1){throw '[INPUT-DOD-ANCHOR] nonunique fixed-source edit'}; [regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m) $value})}; $x=& $edit $x '(?m)^dod_command: [^\n]*$' 'dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture attr-binary-bypass'; $x=& $edit $x '(?m)^dod_assert: [^\n]*$' 'dod_assert: focused attr-binary-bypass 与完整 seeded 的 17ai 必须调用同一 A5 行为夹具，直接执行实际候选 review.ps1，真实 Git 下 1001 行纯文本被 .gitattributes 标为 -diff 时仍须以 [R3-DIFF-TOO-LARGE] 阻断；实现前须先观察该实际基线绕过导致的命名 A5 断言非零，再由 task.ps1 -Phase red 留下真实 RED 收据及其原始输出。缺参数、缺变异靶点、语法或 setup 失败不算 A5 RED。验收集合 A1–A10 每条仍须有可证伪测试，focused GREEN 不替代完整验收；CI 与 ship 跑 selftest.ps1 -Shard seeded 须 exit 0。'; $x=& $edit $x '前两条已在本卡拆出前修掉（`--no-ext-diff`\ /\ `--no-textconv`），第三条未修——它是同一个病在低一层的再现。' '原卡记录前两条已在拆出前修掉（`--no-ext-diff` / `--no-textconv`）；但核对基线 `aa0c79b302aa6b3bf37de37eac9af0648ebee7fb` 时，三处权威 diff 调用实际仍缺这两个参数，二进制条目也仍只计文件数而不计体量。保留上述历史复现记录；恢复实施时必须针对实际整合基线重新取得 A5 行为 RED，并完成 A1–A10。'; if((& $read $p) -cne $x){throw '[INPUT-DOD-TARGET] target differs from exact three-edit projection'}; $own=& $read $s; if([regex]::Matches($own,'(?m)^dod_command: [^\n]*\n').Count -ne 1){throw '[INPUT-DOD-SELF] expected one complete DoD line'}; $body=[regex]::Replace($own,'(?m)^dod_command: [^\n]*\n',''); $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body))); if($hash -cne 'FFE6B5759A1025D74A649740CC69ED34B32FE0C9651AA01653087A2C27A05E71'){throw '[INPUT-DOD-SELF] complete contract hash mismatch'}; $m=(& git merge-base refs/heads/master HEAD); if($LASTEXITCODE -or @($m).Count -ne 1 -or $m -cnotmatch '^[0-9a-f]{40}$'){throw '[INPUT-DOD-BASE] master merge-base unavailable'}; $paths=@(& git -c core.quotepath=false diff --name-only --no-renames $m --); if($LASTEXITCODE){throw '[INPUT-DOD-SCOPE] diff failed'}; $paths+=@(& git -c core.quotepath=false ls-files --others --exclude-standard); if($LASTEXITCODE){throw '[INPUT-DOD-SCOPE] untracked scan failed'}; if(@($paths|Where-Object{$_ -cne $p -and $_ -cne $s}).Count){throw '[INPUT-DOD-SCOPE] unexpected candidate path'}; Write-Host 'INPUT-DOD-CONTRACT-PASS'
dod_exit: 0
dod_assert: The complete target card equals its fixed-source three-edit projection, the complete self contract matches its pinned hash excluding only dod_command, and no other tracked or untracked candidate path changes relative to the master merge-base.
review_gate: codex {verdict:pass}
hygiene: Metadata-only SkipRed; run exact positive and isolated negative contract checks, then all normal non-RED delivery gates. Do not fabricate capability failure evidence.
doc_sync: This self-contained merged-status projection is valid only after this administrative PR actually merges; keep INPUT todo and retain this card for official cleanup/archive. No runtime or product completion statement is added.
---

# Bounded administrative contract

The user authorized the necessary prerequisite correction within the normal task-loop. Author: GPT-6 Astra, high effort. Independent R3: configured GPT-5.6 Sol, high effort. Use the primary task.ps1 entry, metadata SkipRed and every other normal gate.

The status above is an explicit post-merge projection prepared for this administrative change. It is not a claim that this candidate has already passed R3, CI or merged. Actual delivery requires the corresponding tool evidence; the INPUT capability remains todo.

The future `-Fixture attr-binary-bypass` option does not exist at the fixed source baseline. This card validates a strengthened acceptance contract only. INPUT implementation must add that option and share its actual A5 behavior assertion with default seeded 17ai before obtaining RED. Missing options, source flags, mutation targets or setup cannot stand in for an observed A5 bypass. All original A1-A10 acceptance and full seeded obligations remain in force.

The fixed reconstruction source is `aa0c79b302aa6b3bf37de37eac9af0648ebee7fb`. The target's historical R3 reproduction is retained; only the contradicted statement about already-present flags gains the explicit baseline correction. The self-contract hash covers every other line, including these boundaries, status projection and exact allow_paths. The inline DoD is separately reviewable executable code, not authenticated by its own excluded hash.
