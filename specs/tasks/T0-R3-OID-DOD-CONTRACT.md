---
id: T0-R3-OID-DOD-CONTRACT
title: Bind measured-OID acceptance to real detached-HEAD behavior and its declared documentation scope
depends_on: []
status: merged
branch: T0-R3-OID-DOD-CONTRACT
worktree: C:\wt\T0-R3-OID-DOD-CONTRACT
allow_paths:
  - specs/tasks/T0-R3-MEASURED-OID-BINDING.md
  - specs/tasks/T0-R3-OID-DOD-CONTRACT.md
forbid:
  - Changing runtime scripts, tests, workflows, configuration, budgets, receipts, review counters, or any path outside these two cards
  - Changing original OID acceptance A1-A12, forbid, non_goals, dependencies, status, hygiene, doc_sync, or historical narrative
  - Treating metadata checks, a missing fixture or setup failure as capability implementation, valid RED/GREEN, full verification, or remote delivery evidence
non_goals:
  - Implementing OID defenses, the A4 fixture or 17t state-code producer checks
  - Changing INPUT-TRUST, budget semantics, PR-base policy or concurrency locks
  - Reopening completed PR275 or altering another metadata card
diagnosis: The OID DoD only searches three strings, which cannot establish a detached-HEAD behavior failure; doc_sync names DEVOPS-WORKFLOW although allow_paths omits it.
acceptance:
  - "A1 The original OID card equals fixed aa0 after exactly three unique edits: add the already-declared DEVOPS path, replace the textual DoD with the focused behavior contract, and strengthen dod_assert without changing any other content."
  - "A2 This entire administrative contract matches its pinned hash excluding only its unique dod_command line; its merged status is an explicit post-merge projection while the capability card stays todo."
  - "A3 Metadata validation does not execute the future fixture. The coordinator must pre-run and identify real A4 failure before official RED because the current driver accepts any nonzero exit; after focused PASS the functional DoD runs complete workflow and seeded suites with exit and PASS checks. All A1-A12 and normal delivery gates remain mandatory."
dod_command: $ErrorActionPreference='Stop'; $PSNativeCommandUseErrorActionPreference=$false; $b='aa0c79b302aa6b3bf37de37eac9af0648ebee7fb'; $p='specs/tasks/T0-R3-MEASURED-OID-BINDING.md'; $s='specs/tasks/T0-R3-OID-DOD-CONTRACT.md'; $read={param($path) [IO.File]::ReadAllText((Join-Path (Get-Location) $path)).Replace("`r`n","`n")}; $x=((& git show ($b+':'+$p)) -join "`n")+"`n"; if($LASTEXITCODE){throw '[OID-DOD-SOURCE] fixed source unavailable'}; $edit={param($text,$pattern,$value) if([regex]::Matches($text,$pattern).Count -ne 1){throw '[OID-DOD-ANCHOR] fixed-source anchor must occur once'}; [regex]::Replace($text,$pattern,[Text.RegularExpressions.MatchEvaluator]{param($m)$value})}; $x=& $edit $x '(?m)^  - docs/QUALITY-RUBRIC\.md$' ('  - docs/QUALITY-RUBRIC.md'+[char]10+'  - docs/DEVOPS-WORKFLOW.md'); $x=& $edit $x '(?m)^dod_command: [^\n]*$' 'dod_command: $o=(& pwsh -NoProfile -File scripts/selftest.ps1 -Fixture head-detach-not-ref 2>&1 | Out-String); $x=$LASTEXITCODE; Write-Host $o; $p=[regex]::Matches($o,''(?m)^\[SELFTEST-OID-A4\] head-detach-not-ref PASS\r?$'').Count; $f=[regex]::Matches($o,''(?m)^\[SELFTEST-OID-A4\] head-detach-not-ref FAIL\r?$'').Count; if($o -cmatch ''\[SELFTEST-OID-A4-SETUP\]'' -or ($p+$f) -ne 1){throw ''[SELFTEST-OID-A4-SETUP] Missing fixture or invalid result; not RED evidence.''}; if($x -ne 0 -and $f -eq 1){exit 1}; if($x -ne 0 -or $p -ne 1){throw ''[SELFTEST-OID-A4-SETUP] Exit/result mismatch; not RED evidence.''}; foreach($shard in @(''workflow'',''seeded'')){$r=(& pwsh -NoProfile -File scripts/selftest.ps1 -Shard $shard 2>&1 | Out-String); $rx=$LASTEXITCODE; Write-Host $r; if($rx -ne 0 -or [regex]::Matches($r,(''(?m)^selftest\(''+[regex]::Escape($shard)+''\): PASS\r?$'')).Count -ne 1){throw (''[OID-DOD-SUITE] ''+$shard+'' did not pass'')}}; exit 0'; $x=& $edit $x '(?m)^dod_assert: [^\n]*$' 'dod_assert: focused head-detach-not-ref 与完整 workflow 必须调用同一真实 A4 行为夹具；实际候选 task.ps1 测量后，只 checkout --detach 到另一预建 OID，证明任务分支引用未动且 HEAD 已变，下一动作前须以 [R3-DIFF-TIP-MOVED] 阻断。夹具仅在 setup 成功且真实 A4 断言通过或失败时分别输出独占行 [SELFTEST-OID-A4] head-detach-not-ref PASS 或 FAIL；缺入口、无效输出、参数/语法/环境/setup 错误为 [SELFTEST-OID-A4-SETUP]。现行 task.ps1 red 只按非零退出铸收据，并不识别 SETUP；本卡不改通用 RED 协议。协调者必须在调用官方 -Phase red 前先原生预跑 focused，核对实际基线、唯一命名 A4 FAIL 与相符非零及无 SETUP；缺件或 SETUP 则停在准备阶段，不调用 phase，不能把已有任意非零收据当作真实 RED。验收集合 A1–A12 每条仍须有可证伪测试；功能 dod_command 在真实 A4 FAIL 时短路，在 focused PASS 后实际串行运行完整 selftest.ps1 -Shard workflow 与覆盖受影响 17ai/17t(doc) 的完整 -Shard seeded，都须 exit 0 且各有唯一 shard PASS，focused GREEN 不替代完整验收。'; if((& $read $p) -cne $x){throw '[OID-DOD-TARGET] whole target differs from exact projection'}; $own=& $read $s; if([regex]::Matches($own,'(?m)^dod_command: [^\n]*\n').Count -ne 1){throw '[OID-DOD-SELF] one complete DoD line required'}; $body=[regex]::Replace($own,'(?m)^dod_command: [^\n]*\n',''); $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($body))); if($hash -cne '77FFFDACA0A0E81AB788DBF405EA4703B447F40437B49A8C30C21C11740A30EC'){throw '[OID-DOD-SELF] complete contract hash mismatch'}; Write-Host 'OID-DOD-CONTRACT-PASS'
dod_exit: 0
dod_assert: The whole capability card equals its fixed-source three-edit projection and the whole self contract matches its exact pinned hash excluding only the unique DoD line; normal scope checks separately enforce the two allowed paths.
review_gate: codex {verdict:pass}
hygiene: Metadata-only SkipRed; use exact positive and isolated negative card-content checks, then every normal non-RED delivery gate. Never label missing-fixture or setup exit as capability RED.
doc_sync: This self-contained merged-status projection becomes true only after this administrative PR actually merges; keep OID todo and retain this card for official cleanup/archive. No runtime or capability completion claim is added.
---

# Bounded administrative correction

The user authorized necessary prerequisite corrections through the normal task-loop. Author: GPT-6 Astra, high effort. Independent R3: configured GPT-5.6 Sol, high effort. Run the primary task.ps1 entry with metadata SkipRed and all other normal gates.

The merged status above is an explicit post-merge projection, not evidence that this candidate has already passed review, CI or merged. Actual delivery remains conditional on the corresponding tool results.

The focused head-detach-not-ref fixture does not exist at the fixed source baseline. This card only repairs its acceptance contract. Capability implementation must add a shared focused/workflow A4 fixture using real task.ps1 and real Git, and establish that only HEAD changed. The current task.ps1 red phase accepts any nonzero exit and does not recognize SETUP: no machine-level prevention of a false RED receipt is claimed or added here. The coordinator must first run the focused command natively, inspect the actual baseline and unique named A4 FAIL with matching nonzero exit and no SETUP, and only then invoke official red to record that real failure. Missing fixtures or setup errors keep execution in preparation without calling the phase. A nonzero receipt alone proves none of this. After focused PASS, the executable functional DoD runs full workflow and seeded, requiring each native exit zero and its unique shard PASS.

Adding docs/DEVOPS-WORKFLOW.md fulfills the original doc_sync; it does not authorize unrelated workflow policy. Updating 17t(doc) to account for actual review/task state-code producers remains feature work, without fake unused state strings. Existing A1-A12, original history, forbidden behavior and non-goals remain exact. After INPUT actually merges, rebind feature evidence to the resulting real source rather than replaying aa0 assumptions.

The reconstruction source is aa0c79b302aa6b3bf37de37eac9af0648ebee7fb. The self hash covers every other line, including status, paths, documentation scope and these limits. The excluded inline DoD is separately reviewable executable code; its own hash does not authenticate it.
