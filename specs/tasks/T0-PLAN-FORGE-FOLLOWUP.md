---
id: T0-PLAN-FORGE-FOLLOWUP
title: Trim the decompose-cards.mjs prompts for Claude Opus 5.5 and correct the docs that still say plan-forge projects task cards or that the card audit has 4 angles
status: todo
depends_on: []
parallelizable_with: []
branch: T0-PLAN-FORGE-FOLLOWUP
worktree: C:\wt\T0-PLAN-FORGE-FOLLOWUP
allow_paths:
  - .claude/workflows/decompose-cards.mjs
  - .claude/workflows/plan-forge.mjs
  - docs/PLAN-FORGE.md
  - docs/PLAN-TEMPLATE.md
  - docs/FRONTEND-FLOW.md
  - .claude/skills/frontend-flow/SKILL.md
  - .claude/skills/task-loop/SKILL.md
  - CLAUDE.md
  - docs/HARNESS-REVIEW.md
  - docs/idea-to-plan-diagram.html
  - docs/DELIVERY-CHAINS.md
  - docs/IDEA-TO-PLAN.md
sweep: "2026-09-25 on origin b9a39f36. (1) git grep -n plan-forge over *.md, *.html, *.mjs and .claude/skills, excluding specs/archive, docs/lessons, docs/references, plan-forge.mjs itself and the two plan-forge cards, filtered for 卡|card|拆|Decompose and dropping lines that already name decompose-cards or say plan-forge stops at the verdict. (2) git grep for '4 角度|4 个审计|4 并行卡审|约 5 agent'. Stale faces, all in allow_paths: PLAN-FORGE.md lines 13, 18, 26, 27, 33, 68, 69; PLAN-TEMPLATE.md 7; FRONTEND-FLOW.md 12, 16, 37, 61, 93; frontend-flow SKILL.md 6, 35, 49, 61; the CLAUDE.md frontend-flow index line; HARNESS-REVIEW.md 30, 129; idea-to-plan-diagram.html 441; decompose-cards.mjs 3, 112, 133; plan-forge.mjs 112 (a comment); task-loop SKILL.md 74. Correct and left alone: IDEA-TO-PLAN.md (it already says plan-forge does not project), DELIVERY-CHAINS.md 21, scaffold-architecture.html 615, the grill-design, shape-idea and database-design skills, PLAN-TEMPLATE.md 38 and 44. Found after registration, during implementation and a fresh-context pre-review, and added to allow_paths by the Amendment: decompose-cards.mjs 6 and 132 (base numbering), PLAN-FORGE.md 3 and 28, CLAUDE.md 668, DELIVERY-CHAINS.md 19 (line 21 stays correct), IDEA-TO-PLAN.md 109, HARNESS-REVIEW.md 128 and idea-to-plan-diagram.html 400. DELIVERY-CHAINS.md 19, IDEA-TO-PLAN.md 109 and HARNESS-REVIEW.md 128 were in the output of step (1) and were misread as correct; step (2) missed a count phrased as 4 并行 lens."
forbid:
  - Changing what the decompose agent or the card auditors are asked to check, the LENSES set, the schemas, the return shape or the cardaudit labels
  - Changing plan-forge.mjs beyond the angle count in its one comment line
  - Editing scripts/selftest.ps1
  - Cutting prompt text that no reference entry backs; such text stays and is raised as a question instead
non_goals:
  - A null guard for decomp in decompose-cards.mjs (agent() can return null and decomp.cards then throws); found during the sweep, a separate defect
  - The same wording pass on scout-options.mjs (12 marked non-comment lines)
  - Rewording any doc beyond the stale claims named here
  - Adding the doc drift to upstream issue Asun28/claude-devops-scaffold#399 (offered to the user at R5)
acceptance:
  - "A1 No non-comment line of decompose-cards.mjs carries the 【 】 brackets or ** bold, and the maintainer note (本仓尺寸标准的定义处,他处引用此定义、勿另立) moves from the prompt into a code comment. Basis: claude-prompting-best-practices-llms.txt, 工具使用 (CRITICAL/MUST-style emphasis overtriggers on newer models) and 输出与格式 (prompt formatting carries into the output); 通用原则, the golden rule, for the maintainer note."
  - "A2 The card-audit prompt's vague 只报真问题 becomes a coverage instruction with one concrete exclusion: 能在卡字段或仓库文件里指出依据的问题都报，拿不准的也报并在 problem 里注明，每条按 severity 分级；纯措辞与风格偏好略过。 Basis: claude-opus-5-prompting-llms.txt, 代码评审 harness (the discovery step reports everything with a severity and a confidence, and a separate step filters; here the human review of FATAL and HIGH issues is that step; the audit schema has no confidence field and the schemas stay as they are, so an uncertain finding says so in problem). The single-pass threshold registered first would have suppressed the right-size lens's own MEDIUM over-fragmentation findings."
  - "A3 Every other prompt sentence keeps its wording apart from the removed markers (a colon or round brackets take a removed bracket's place where the sentence needs one), so what the decompose agent and the auditors check is unchanged. The one other prompt edit: the right-size lens pointed at 见上 CONSTRAINTS, a variable name the model never sees and, in the audit prompt, the wrong direction; it now names 见下方通用硬要求的右尺寸一条 (best-practices 通用原则, the golden rule). The dod_command anchors 26 criterion phrases plus the whole A2 sentence, each exactly once in the non-comment text."
  - "A4 Every stated card-audit angle count (N 角度 in decompose-cards.mjs, the plan-forge.mjs comment, PLAN-FORGE.md and task-loop SKILL.md) equals the number of LENSES entries, which the dod_command counts (5 today), and the PLAN-FORGE.md decompose-cards cost row reads 1 拆解 + that number. The comment naming the labels says cardaudit1..cardaudit5."
  - "A5 No doc says plan-forge projects task cards (TD180 moved projection to decompose-cards.mjs) or describes the two workflows as they ran before: the stale phrases the dod_command lists are gone from PLAN-FORGE.md, PLAN-TEMPLATE.md, FRONTEND-FLOW.md, DELIVERY-CHAINS.md, IDEA-TO-PLAN.md, the frontend-flow skill, two CLAUDE.md lines (the frontend-flow skill index and doc index item 17), HARNESS-REVIEW.md and idea-to-plan-diagram.html, and each of those names decompose-cards (for CLAUDE.md, on the frontend-flow skill index line)."
  - "A6 PLAN-FORGE.md describes plan-forge as it runs now: the Workflow call lists tier and drops templatePath (decompose-cards keeps its own templatePath argument); the output names tier, verify_mode, skipped_lenses and every verdict value (ready-to-decompose, fix-first, tier-skipped, synthesis-skipped), and no cards; the cost row gives T0 = 0, T1 = 4 and T2 up to 81 agents (8 lenses + 8 x 3 x 3 judges + 1 synthesis)."
  - "A7 selftest.ps1 -Only 1,14 passes with no change to selftest.ps1: the .mjs syntax check, 1a and 1i on plan-forge, the 14c lens count in idea-to-plan-diagram.html and 14j on decompose-cards."
  - "A8 The tier-1 acceptance run selftest.ps1 -TaskId T0-PLAN-FORGE-FOLLOWUP passes on the shipped candidate."
  - "A9 The dod_command pins the reviewed text, so the checks above cannot be met by a different edit: decompose-cards.mjs must equal the reviewed file (the SHA-256 of its LF-normalized text), which fixes the maintainer comment, the A2 sentence in the audit prompt, every unanchored prompt sentence and the cardaudit label comment; and every line the change adds to the other eleven files must occur exactly once in its file (the SHA-256 of the full line), which fixes each corrected doc line and the whole documented output and cost contract. The Pinned lines section lists each pinned line."
dod_command: $f = '.claude/workflows/decompose-cards.mjs'; if (-not (Test-Path -LiteralPath $f)) { Write-Host '[DOD-FAIL] decompose-cards.mjs missing'; exit 1 }; $code = (Get-Content -LiteralPath $f -Encoding utf8 | Where-Object { $_ -notmatch '^\s*//' }) -join "`n"; foreach ($t in @('【', '】', '**', '定义处', '只报真问题')) { if ($code.Contains($t)) { Write-Host "[DOD-FAIL] decompose-cards still present $t"; exit 1 } }; foreach ($k in @('否则就是把旧坑写进卡', '默认档(保守):单卡净改动约 ≤ 200-400 行', '长自主档(受支持模式)', '单一产出:一张卡只交付一个连贯能力', '验收单一:dod_command 是', '评审可判:一个评审者', '自足性(卡可独立开工)', '不可再拆的下限', '反面(过碎)', '冻结点例外:契约/schema 卡可略大', '拆分后必须仍构成无环 DAG', 'check-cards.ps1 机检此正则', 'depends_on 无环; 冻结点', 'import 路径/包根与目录结构一致', 'allow_paths 必须覆盖其 DoD 真正要改/要建的文件', 'allow_paths 超过 5 项时，sweep 必须记录', '路径只经项目约定的 storage/派生层', '构建期 vs 运行期网络要分清', '并行窗口里的卡 allow_paths 互不重叠', 'non_goals 是 forbid(横切硬边界)的能力级对偶', '拓扑/依赖正确性', 'DoD 可机检性:', '硬边界/许可:', 'allow_paths 覆盖与冲突:', '尺寸定义见下方通用硬要求', '允许 Read 仓库真实文件', '能在卡字段或仓库文件里指出依据的问题都报，拿不准的也报并在 problem 里注明，每条按 severity 分级；纯措辞与风格偏好略过。')) { $n = ([regex]::Matches($code, [regex]::Escape($k))).Count; if ($n -ne 1) { Write-Host "[DOD-FAIL] decompose-cards anchor count $n for $k"; exit 1 } }; $stale = [ordered]@{ 'docs/PLAN-FORGE.md' = @('经多裁判对抗审计、可直接施工', '60-85', '初版卡', '裁决 → 拆解 → 卡审'); 'docs/PLAN-TEMPLATE.md' = @('（审计 → 拆卡）'); 'docs/FRONTEND-FLOW.md' = @('plan-forge.mjs` 投影任务卡', 'plan-forge 任务 DAG', '喂 plan-forge**,页面清单投影成', 'plan-forge 负责拆解与卡审', 'plan-forge 投影任务卡'); '.claude/skills/frontend-flow/SKILL.md' = @('plan-forge 投影任务卡', 'plan-forge 的 DAG 拆解', 'plan-forge 任务 DAG', 'plan-forge DAG', '审计拆卡=plan-forge'); 'CLAUDE.md' = @('喂 `plan-forge`** 投影任务卡', '流程卡→喂 `plan-forge`、'); 'docs/DELIVERY-CHAINS.md' = @('喂 `plan-forge`** 投影任务卡'); 'docs/IDEA-TO-PLAN.md' = @('| `PLAN-TEMPLATE` + `plan-forge.mjs` | `_local/3-plan.md`'); 'docs/HARNESS-REVIEW.md' = @('plan-forge 拆卡', 'plan-forge.mjs` 的 Decompose', '每条发现派 3 固定裁判'); 'docs/idea-to-plan-diagram.html' = @('plan-forge</code> 投卡', '守 id、依赖、DoD、allow-paths') }; foreach ($d in $stale.Keys) { $txt = Get-Content -Raw -LiteralPath $d -Encoding utf8; foreach ($t in $stale[$d]) { if ($txt.Contains($t)) { Write-Host "[DOD-FAIL] stale in $d - $t"; exit 1 } }; if (($d -ne 'CLAUDE.md') -and (-not $txt.Contains('decompose-cards'))) { Write-Host "[DOD-FAIL] $d does not name decompose-cards"; exit 1 } }; $ff = @(Get-Content -LiteralPath CLAUDE.md -Encoding utf8 | Where-Object { $_.StartsWith('- **`.claude/skills/frontend-flow`**') }); if (($ff.Count -ne 1) -or (-not $ff[0].Contains('decompose-cards'))) { Write-Host '[DOD-FAIL] CLAUDE.md frontend-flow line does not name decompose-cards'; exit 1 }; $pf = Get-Content -LiteralPath docs/PLAN-FORGE.md -Encoding utf8; $call = @($pf | Where-Object { $_.Contains('plan-forge.mjs", args') }); if (($call.Count -ne 1) -or (-not $call[0].Contains('tier')) -or $call[0].Contains('templatePath')) { Write-Host '[DOD-FAIL] PLAN-FORGE.md plan-forge call does not list tier or still lists templatePath'; exit 1 }; $pfRaw = $pf -join "`n"; foreach ($v in @('tier-skipped', 'synthesis-skipped')) { if (-not $pfRaw.Contains($v)) { Write-Host "[DOD-FAIL] PLAN-FORGE.md does not name the verdict $v"; exit 1 } }; $cost = @($pf | Where-Object { $_.StartsWith('| plan-forge |') }); if (($cost.Count -ne 1) -or (-not $cost[0].Contains('81'))) { Write-Host '[DOD-FAIL] PLAN-FORGE.md plan-forge cost row does not give the T2 maximum of 81'; exit 1 }; $src = Get-Content -Raw -LiteralPath $f -Encoding utf8; $lm = [regex]::Match($src, 'const LENSES = \[(.*?)\r?\n\]', 'Singleline'); $n = ([regex]::Matches($lm.Groups[1].Value, '(?m)^\s+''')).Count; if ($n -lt 1) { Write-Host '[DOD-FAIL] cannot count decompose-cards LENSES'; exit 1 }; foreach ($af in @('.claude/workflows/decompose-cards.mjs', '.claude/workflows/plan-forge.mjs', 'docs/PLAN-FORGE.md', '.claude/skills/task-loop/SKILL.md')) { $am = [regex]::Matches((Get-Content -Raw -LiteralPath $af -Encoding utf8), '(\d+) ?角度'); if ($am.Count -lt 1) { Write-Host "[DOD-FAIL] $af states no card-audit angle count"; exit 1 }; foreach ($x in $am) { if ([int]$x.Groups[1].Value -ne $n) { Write-Host "[DOD-FAIL] $af says $($x.Value), LENSES has $n"; exit 1 } } }; $dc = @(Get-Content -LiteralPath docs/PLAN-FORGE.md -Encoding utf8 | Where-Object { $_.StartsWith('| decompose-cards |') }); if (($dc.Count -ne 1) -or (-not $dc[0].Contains("1 拆解 + $n 并行卡审"))) { Write-Host '[DOD-FAIL] PLAN-FORGE.md decompose-cards cost row does not match LENSES'; exit 1 }; $sha = [Security.Cryptography.SHA256]::Create(); $H = { param($t) -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($t)) | ForEach-Object { $_.ToString('x2') }) }; if ((& $H ((Get-Content -Raw -LiteralPath $f -Encoding utf8).Replace("`r`n", "`n"))) -ne '35d22adbd2fce1ac210a723ef4aaa321c416895a140390f3cad1ff9539462530') { Write-Host '[DOD-FAIL] decompose-cards.mjs differs from the pinned snapshot'; exit 1 }; $pins = [ordered]@{ '.claude/skills/frontend-flow/SKILL.md' = @('8ffe04faf463205ffb10a599369a76c9401a66c46f1ad052ab5f5d2ca7df8965', '8e437998c27e17a953bba19d0109c168d9eba8e74fa0d07c989d831b6c89c384', '1b91b67f53b11bd5f05382957939a67ec60c6d180484b34e0bba011810b8c2e3', '8929f6709ec81244f7f0155a6f963d4c41ff985325633cebfffaa3ef7e1434c4'); '.claude/skills/task-loop/SKILL.md' = @('1c0c6651a4eb7ee435ed51d0826af85d08984ecad820ac5d51611e8bdc73e0e2'); '.claude/workflows/plan-forge.mjs' = @('149aa6743591efee9f5124edf5684b8a3f3ade4ada6d6093cda43f4f907b1cad'); 'CLAUDE.md' = @('643b77d49a0c5de95c1790992394ee2cbcf007cdef3daa14682da9acf81a1eca', '8291d39bf1dc5bc389f55976a9b25ae1f110568a6c3be66c26d1a55170c6966b'); 'docs/DELIVERY-CHAINS.md' = @('63892297be93272357bc3151a98533b2fa707057c13dcc617e8ef05ac1186410'); 'docs/FRONTEND-FLOW.md' = @('1905f609cedd3223bf97299612dd733eb70447238ddf74751890c40e3f59675c', 'f7673875f8d92bbbf691430e788bbab712d0b01504f61a0c417c02b054d16c8c', '84a94d7ff343c87319d1853bf5fb8e7c39bce9802f13eeca7c40b494c774d564', '555756e0853f9254709a2435b87dd7bfbc0d3e48e265af208444b5278ed17a48', 'f19667bdc2891de4f7a8b3d7c0840199a3508d4462b3358259b5842773111538'); 'docs/HARNESS-REVIEW.md' = @('541b318a4d5398abc4712478f64b87fe3ceaea6e5cd5053fd772e6d3c181b91a', '14a5c94402f3f6a4aede175f45ba7985a122821228618a95a89a974aed7b60d5', 'e2af4256abf68169c5f9becdfe6779fb0924dd6bdf337bec6a8e75818430219e'); 'docs/IDEA-TO-PLAN.md' = @('dba733a1af663a33183efbeabc2a14f79d6e0c24fd66988e2bde668fd70c6b1a'); 'docs/PLAN-FORGE.md' = @('d88dc7eb06102d700aee88feee28c796d8b7cde65da766bbfaca8556b48cd96a', '1b4f5d6e09fb99d766165a9f9c866f7b0daa55b230d5f6722bdb27c08f4970a5', '8ba3803f09650c206932c81d78cba4740a547d1c6ca1317b0b6cbc79eee63e51', 'e1efe56d62dde1a33378c803d0e029ebe79b900019a3ea8ef05d35eae4b2d9ba', '9e3c92bf37202e794856bea5e58186f4fe2b569a0f185b5a2fd65b926f084ef7', '682815742ea9ca7766a064f912d1c8e20e97a13965cf7141ce81e2c1515a8ede', '401092c7e725af1c92dc3f860f83f2d1fa5e2837e5333792d468a30eb1cfc898', 'ecc8d1070c1a9b1628edc641cf8648a2673f92c5f305bf8de5dc3306d2ee0d27', 'c2c932685c25a18de172cef24ad708f92baf6ef66c4472cdda70e2e80307c91b', '0959bdb90ece3c3861023a265cbec29eb4d3d0ce73150fcfdd30a5a90aa4fe98', '56a8ffcbff185b826b86cad6ac35d483878491a3568e172652f8b5b3b8220257'); 'docs/PLAN-TEMPLATE.md' = @('f35c3ef3440e6464c3dc4f7adaec04319d19d72080b387d433e2067e8825f124'); 'docs/idea-to-plan-diagram.html' = @('48d515ddab9e6038fa5adb1ec71d580ee2116ed8edaa9b2242ab76634314018f', '73ac9ecc3eaa7c6124a02a3ee49306ca132c12a22ecb4a7f4fd440d398d38747') }; foreach ($pk in $pins.Keys) { $hs = @(Get-Content -LiteralPath $pk -Encoding utf8 | ForEach-Object { & $H $_ }); foreach ($ph in $pins[$pk]) { $pc = @($hs | Where-Object { $_ -eq $ph }).Count; if ($pc -ne 1) { Write-Host "[DOD-FAIL] $pk pinned line $($ph.Substring(0, 12)) occurs $pc times"; exit 1 } } }; & pwsh -NoProfile -File scripts/selftest.ps1 -Only 1,14; if ($LASTEXITCODE -ne 0) { Write-Host '[DOD-FAIL] selftest -Only 1,14'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: decompose-cards.mjs has no markers, maintainer note or vague filter in its non-comment text (A1, A2) and every anchored phrase once (A3); every stated angle count equals LENSES (A4); the stale claims are gone and each face names decompose-cards (A5); the PLAN-FORGE.md plan-forge call lists tier and not templatePath, the doc names tier-skipped and synthesis-skipped, and the cost row gives the 81-agent T2 maximum (A6); decompose-cards.mjs equals the pinned snapshot and each pinned line occurs exactly once (A9); selftest -Only 1,14 exits 0 (A7); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] decompose-cards still present 【.
review_gate: codex {verdict:pass}
budget: 180
hygiene: R4 runs single-statement mutants against the dod_command, each recorded in the R5 note with its [DOD-FAIL] line - put one 【】 pair back in decompose-cards.mjs, restore 只报真问题, put 4 角度 back in PLAN-FORGE.md, put plan-forge 投影任务卡 back in FRONTEND-FLOW.md, drop tier from the PLAN-FORGE.md call, delete the 拓扑/依赖正确性 lens text; and the eight mutations R3 round 1 named - delete the maintainer comment line, move the A2 sentence into an unused constant, reword an unanchored prompt sentence, put the label comment back to cardaudit1..cardaudit4, put a false plan-forge claim in HARNESS-REVIEW row 30, corrupt verify_mode, T1 = 4 or the no-cards statement in PLAN-FORGE.md; every one must exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row, and ask the user whether to add the doc drift to upstream issue #399.
---

# T0-PLAN-FORGE-FOLLOWUP

Opened by user decision on 2026-09-25, after `T0-PLAN-FORGE-PROMPT-TRIM` (PR #360) trimmed the plan-forge
prompts and listed these as non-goals.

## Deliverable

**Part 1: decompose-cards.mjs prompts** (same method as #360; every cut points at a reference entry):

- Remove the emphasis markers: 9 【】 pairs and 6 bold pairs in the model-facing text.
- Move the maintainer note about where the size standard is defined into a code comment.
- Replace 只报真问题 in the card-audit prompt with the coverage instruction in A2.
- Make the stated audit-angle count match `LENSES`. It has 5 entries (right-size, topology, DoD, hard
  boundaries, allow_paths), but the description, the log line and PLAN-FORGE.md say 4.

**Part 2: docs that predate TD180.** plan-forge stops at the verdict and decompose-cards.mjs owns projection,
but nine doc files (seven at registration, see the Amendment) still say plan-forge projects task cards, and PLAN-FORGE.md still lists `templatePath`, the
removed Decompose and Card-Audit stages, and a 60-85 agent cost. Each face gets the minimal correction:
plan-forge audits the plan, decompose-cards projects the cards.

## Found during the sweep, not in scope

`decompose-cards.mjs` dereferences `decomp.cards` right after `agent()` without a null guard, the defect
gate 1a guards against in plan-forge (TD52). It is a separate fix.

## Amendment (2026-09-25, before ship)

Implementation and a fresh-context pre-review of the first candidate changed the card in four ways:

- The dod_command banned the word templatePath anywhere in PLAN-FORGE.md, but decompose-cards.mjs really takes
  a templatePath argument, so the correct decompose-cards call failed it. The check now applies to the plan-forge
  call line only, which is what A6 always said.
- Five more stale faces were found (see the sweep field) and joined allow_paths and the dod_command's bans:
  DELIVERY-CHAINS.md 19 and CLAUDE.md doc index item 17 say plan-forge projects cards; IDEA-TO-PLAN.md 109 gives
  plan-forge as the tool that outputs specs/tasks; HARNESS-REVIEW.md 128 says every finding gets 3 judges; the
  diagram's card-audit line lists the wrong angles. PLAN-FORGE.md 3 and 28 also needed T2 and the missing
  verdict values.
- A2 changed from a single-pass threshold to a coverage instruction, because the threshold would have silenced
  the right-size lens's MEDIUM over-fragmentation findings, and the reference prefers coverage when a separate
  filter step exists (here, the human review).
- A3 records the one other prompt edit: the right-size lens's pointer to CONSTRAINTS, a name the model never sees.

A second pre-review, of those fixes, confirmed them and found that A2's 拿不准的也报 gave a reviewer no way to tell an
uncertain finding from a sure one, because the audit schema has no confidence field. The A2 sentence now asks for
the uncertainty to be stated in problem, and the dod_command anchors that whole sentence. It also corrected two
line numbers and the stated reason for the sweep's misses.

Codex R3 round 1 on PR #378 (`d2e51ad3`) blocked on dimension 6: the file-wide token checks still passed after
deleting the maintainer comment, moving the A2 sentence into an unused constant, rewording an unanchored prompt
sentence or putting the label comment back to 4, and after replacing a corrected doc line with a different false
claim or corrupting the PLAN-FORGE.md output and cost lines. A9 adds the pins. All eight named mutations now make
the dod_command exit 1: the four in decompose-cards.mjs at the snapshot check, the four doc ones at the line pins.

## Pinned lines (A9)

decompose-cards.mjs snapshot: SHA-256 of the LF-normalized file = `35d22adbd2fce1ac210a723ef4aaa321c416895a140390f3cad1ff9539462530`.

Every line the reviewed candidate (`d2e51ad3`) adds to the other eleven files, with its line number there:

| file | line | SHA-256 (first 12) | opens with |
|---|---|---|---|
| `.claude/skills/frontend-flow/SKILL.md` | 6 | 8ffe04faf463 | 【流程卡(页面地图)】写进计划经 plan-forge 审计后由 d |
| `.claude/skills/frontend-flow/SKILL.md` | 35 | 8e437998c27e | → **流程卡的页面清单写进计划、经 `plan-forge` 审计 |
| `.claude/skills/frontend-flow/SKILL.md` | 49 | 1b91b67f53b1 | - **流程卡 ≠ decompose-cards 任务 DAG** |
| `.claude/skills/frontend-flow/SKILL.md` | 61 | 8929f6709ec8 | - 不重造任何现有引擎(拷问=grill-design / 审计=p |
| `.claude/skills/task-loop/SKILL.md` | 74 | 1c0c6651a4eb | - **每卡一个 agent、各占一棵 worktree**：窗口内 |
| `.claude/workflows/plan-forge.mjs` | 112 | 149aa6743591 | // freeze_point / topo_valid / par |
| `CLAUDE.md` | 668 | 643b77d49a0c | 17. `docs/FRONTEND-FLOW.md` — **前端 |
| `CLAUDE.md` | 713 | 8291d39bf1dc | - **`.claude/skills/frontend-flow` |
| `docs/DELIVERY-CHAINS.md` | 19 | 63892297be93 | \| 前端生成闭环（T2 · 串联非新引擎） \| `.claude/s |
| `docs/FRONTEND-FLOW.md` | 12 | 1905f609cedd | \| **2 生成中** \| 产出**流程卡(页面地图)** + ** |
| `docs/FRONTEND-FLOW.md` | 16 | f7673875f8d9 | > **边界(复用非重复)**:流程卡 ≠ decompose-ca |
| `docs/FRONTEND-FLOW.md` | 37 | 84a94d7ff343 | > 用途:把整个前端的页面与关系画清楚。填完→写进计划**喂 pla |
| `docs/FRONTEND-FLOW.md` | 61 | 555756e0853f | > **流程卡 → plan-forge → decompose-c |
| `docs/FRONTEND-FLOW.md` | 93 | f19667bdc289 | `想法 → 1-brief(+前端补充) → 流程卡(页面地图) → |
| `docs/HARNESS-REVIEW.md` | 30 | 541b318a4d53 | \| 规划 harness（plan-forge 审计 → decom |
| `docs/HARNESS-REVIEW.md` | 128 | 14a5c94402f3 | \| parallelization（并行·分段/投票） \| `Wor |
| `docs/HARNESS-REVIEW.md` | 129 | e2af4256abf6 | \| orchestrator-workers（编排者—工人·动态委派 |
| `docs/IDEA-TO-PLAN.md` | 109 | dba733a1af66 | \| 3 \| **Plan（写成计划）** \| 具体怎么做？拆成哪些任 |
| `docs/PLAN-FORGE.md` | 3 | d88dc7eb0610 | > 一条把"一句话想法 / 初稿计划"打磨成"经审计（T2 为多裁判 |
| `docs/PLAN-FORGE.md` | 13 | 1b4f5d6e09fb | │ 按 tier 选 lens（T2 全 8 个）→ T2 多裁判对 |
| `docs/PLAN-FORGE.md` | 18 | 8ba3803f0965 | │ 投影任务卡 + 5 角度对抗卡审 |
| `docs/PLAN-FORGE.md` | 26 | e1efe56d62dd | `Workflow({ scriptPath: ".claude/w |
| `docs/PLAN-FORGE.md` | 27 | 9e3c92bf3720 | - `tier` 传字面量 `T0` / `T1` / `T2`，缺 |
| `docs/PLAN-FORGE.md` | 28 | 682815742ea9 | - 产出：`verdict`（`ready-to-decompose |
| `docs/PLAN-FORGE.md` | 34 | 401092c7e725 | - 产出：任务卡结构化定义 + 5 角度对抗卡审（右尺寸 / 拓扑  |
| `docs/PLAN-FORGE.md` | 62 | ecc8d1070c1a | - **对抗核验（T2）**：每个 lens 最重的 3 条 FAT |
| `docs/PLAN-FORGE.md` | 66 | c2c932685c25 | ## 成本（按 tier 路由 lens 与裁判数） |
| `docs/PLAN-FORGE.md` | 69 | 0959bdb90ece | \| plan-forge \| T0 = 0；T1 = 4；T2 最多 |
| `docs/PLAN-FORGE.md` | 70 | 56a8ffcbff18 | \| decompose-cards \| 约 6 agent \| 1  |
| `docs/PLAN-TEMPLATE.md` | 7 | f35c3ef3440e | > 填好后用 `.claude/workflows/plan-for |
| `docs/idea-to-plan-diagram.html` | 400 | 48d515ddab9e | <p>裁决通过后投影成任务卡 <span class="arrow" |
| `docs/idea-to-plan-diagram.html` | 441 | 73ac9ecc3eaa | <p>复杂多页前端走串联驱动卡，把现有件串成四段：<b style= |

## Evidence at registration

- The dod_command was run on base `b9a39f36`: exit 1, `[DOD-FAIL] decompose-cards still present 【`.
- Every stale phrase it bans occurs on base, so none of the bans is vacuous.
- The angle-count arm alone exits 1 on base (`decompose-cards.mjs says 4 角度, LENSES has 5`).
- The PLAN-FORGE.md tier and cost-row arms are both red on base.
- All 26 criterion anchors registered then occurred exactly once on base. The Amendment replaced one of them with the new-text anchor 尺寸定义见下方通用硬要求 (the A3 pointer fix), which base does not have.
- Tier 1 (no TierS path).

## Acceptance

```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: see `dod_assert`; the closed list is `acceptance:` above.
