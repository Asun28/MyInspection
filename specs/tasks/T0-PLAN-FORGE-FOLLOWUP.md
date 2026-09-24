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
sweep: "2026-09-25 on origin b9a39f36. (1) git grep -n plan-forge over *.md, *.html, *.mjs and .claude/skills, excluding specs/archive, docs/lessons, docs/references, plan-forge.mjs itself and the two plan-forge cards, filtered for 卡|card|拆|Decompose and dropping lines that already name decompose-cards or say plan-forge stops at the verdict. (2) git grep for '4 角度|4 个审计|4 并行卡审|约 5 agent'. Stale faces, all in allow_paths: PLAN-FORGE.md lines 13, 18, 26, 27, 33, 68, 69; PLAN-TEMPLATE.md 7; FRONTEND-FLOW.md 12, 16, 37, 61, 93; frontend-flow SKILL.md 6, 35, 49, 61; the CLAUDE.md frontend-flow index line; HARNESS-REVIEW.md 30, 129; idea-to-plan-diagram.html 441; decompose-cards.mjs 3, 112, 133; plan-forge.mjs 112 (a comment); task-loop SKILL.md 74. Correct and left alone: IDEA-TO-PLAN.md (it already says plan-forge does not project), DELIVERY-CHAINS.md 21, scaffold-architecture.html 615, the grill-design, shape-idea and database-design skills, PLAN-TEMPLATE.md 38 and 44."
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
  - "A2 The card-audit prompt's vague 只报真问题 becomes a concrete single-pass threshold: 只报会让卡开工跑偏、验收判错或返工的问题，每条写出依据的卡字段或仓库文件；纯措辞与风格偏好略过。 Basis: claude-opus-5-prompting-llms.txt, 代码评审 harness (a single-pass self-filter states a concrete threshold, not a qualitative word)."
  - "A3 Every other prompt sentence keeps its wording apart from the removed markers, so what the decompose agent and the auditors check is unchanged. The dod_command anchors 26 criterion phrases plus the A2 sentence, each exactly once in the non-comment text."
  - "A4 Every stated card-audit angle count (N 角度 in decompose-cards.mjs, the plan-forge.mjs comment, PLAN-FORGE.md and task-loop SKILL.md) equals the number of LENSES entries, which the dod_command counts (5 today), and the PLAN-FORGE.md decompose-cards cost row reads 1 拆解 + that number. The comment naming the labels says cardaudit1..cardaudit5."
  - "A5 No doc says plan-forge projects task cards (TD180 moved projection to decompose-cards.mjs): the stale phrases the dod_command lists are gone from PLAN-FORGE.md, PLAN-TEMPLATE.md, FRONTEND-FLOW.md, the frontend-flow skill, the CLAUDE.md frontend-flow index line, HARNESS-REVIEW.md and idea-to-plan-diagram.html, and each of those names decompose-cards (for CLAUDE.md, on that index line)."
  - "A6 PLAN-FORGE.md describes plan-forge as it runs now: the Workflow call lists tier and drops templatePath; the output is the verdict with tier, verify_mode and skipped_lenses, and no cards; the cost row gives T0 = 0, T1 = 4 and T2 up to 81 agents (8 lenses + 8 x 3 x 3 judges + 1 synthesis)."
  - "A7 selftest.ps1 -Only 1,14 passes with no change to selftest.ps1: the .mjs syntax check, 1a and 1i on plan-forge, the 14c lens count in idea-to-plan-diagram.html and 14j on decompose-cards."
  - "A8 The tier-1 acceptance run selftest.ps1 -TaskId T0-PLAN-FORGE-FOLLOWUP passes on the shipped candidate."
dod_command: $f = '.claude/workflows/decompose-cards.mjs'; if (-not (Test-Path -LiteralPath $f)) { Write-Host '[DOD-FAIL] decompose-cards.mjs missing'; exit 1 }; $code = (Get-Content -LiteralPath $f -Encoding utf8 | Where-Object { $_ -notmatch '^\s*//' }) -join "`n"; foreach ($t in @('【', '】', '**', '定义处', '只报真问题')) { if ($code.Contains($t)) { Write-Host "[DOD-FAIL] decompose-cards still present $t"; exit 1 } }; foreach ($k in @('否则就是把旧坑写进卡', '默认档(保守):单卡净改动约 ≤ 200-400 行', '长自主档(受支持模式)', '单一产出:一张卡只交付一个连贯能力', '验收单一:dod_command 是', '评审可判:一个评审者', '自足性(卡可独立开工)', '不可再拆的下限', '反面(过碎)', '冻结点例外:契约/schema 卡可略大', '拆分后必须仍构成无环 DAG', 'check-cards.ps1 机检此正则', 'depends_on 无环; 冻结点', 'import 路径/包根与目录结构一致', 'allow_paths 必须覆盖其 DoD 真正要改/要建的文件', 'allow_paths 超过 5 项时，sweep 必须记录', '路径只经项目约定的 storage/派生层', '构建期 vs 运行期网络要分清', '并行窗口里的卡 allow_paths 互不重叠', 'non_goals 是 forbid(横切硬边界)的能力级对偶', '拓扑/依赖正确性', 'DoD 可机检性:', '硬边界/许可:', 'allow_paths 覆盖与冲突:', '尺寸定义见上 CONSTRAINTS', '允许 Read 仓库真实文件', '纯措辞与风格偏好略过')) { $n = ([regex]::Matches($code, [regex]::Escape($k))).Count; if ($n -ne 1) { Write-Host "[DOD-FAIL] decompose-cards anchor count $n for $k"; exit 1 } }; $stale = [ordered]@{ 'docs/PLAN-FORGE.md' = @('templatePath', '60-85', '初版卡', '裁决 → 拆解 → 卡审'); 'docs/PLAN-TEMPLATE.md' = @('（审计 → 拆卡）'); 'docs/FRONTEND-FLOW.md' = @('plan-forge.mjs` 投影任务卡', 'plan-forge 任务 DAG', '喂 plan-forge**,页面清单投影成', 'plan-forge 负责拆解与卡审', 'plan-forge 投影任务卡'); '.claude/skills/frontend-flow/SKILL.md' = @('plan-forge 投影任务卡', 'plan-forge 的 DAG 拆解', 'plan-forge 任务 DAG', 'plan-forge DAG', '审计拆卡=plan-forge'); 'CLAUDE.md' = @('喂 `plan-forge`** 投影任务卡'); 'docs/HARNESS-REVIEW.md' = @('plan-forge 拆卡', 'plan-forge.mjs` 的 Decompose'); 'docs/idea-to-plan-diagram.html' = @('plan-forge</code> 投卡') }; foreach ($d in $stale.Keys) { $txt = Get-Content -Raw -LiteralPath $d -Encoding utf8; foreach ($t in $stale[$d]) { if ($txt.Contains($t)) { Write-Host "[DOD-FAIL] stale in $d - $t"; exit 1 } }; if (($d -ne 'CLAUDE.md') -and (-not $txt.Contains('decompose-cards'))) { Write-Host "[DOD-FAIL] $d does not name decompose-cards"; exit 1 } }; $ff = @(Get-Content -LiteralPath CLAUDE.md -Encoding utf8 | Where-Object { $_.StartsWith('- **`.claude/skills/frontend-flow`**') }); if (($ff.Count -ne 1) -or (-not $ff[0].Contains('decompose-cards'))) { Write-Host '[DOD-FAIL] CLAUDE.md frontend-flow line does not name decompose-cards'; exit 1 }; $pf = Get-Content -LiteralPath docs/PLAN-FORGE.md -Encoding utf8; $call = @($pf | Where-Object { $_.Contains('plan-forge.mjs", args') }); if (($call.Count -ne 1) -or (-not $call[0].Contains('tier'))) { Write-Host '[DOD-FAIL] PLAN-FORGE.md plan-forge call does not list tier'; exit 1 }; $cost = @($pf | Where-Object { $_.StartsWith('| plan-forge |') }); if (($cost.Count -ne 1) -or (-not $cost[0].Contains('81'))) { Write-Host '[DOD-FAIL] PLAN-FORGE.md plan-forge cost row does not give the T2 maximum of 81'; exit 1 }; $src = Get-Content -Raw -LiteralPath $f -Encoding utf8; $lm = [regex]::Match($src, 'const LENSES = \[(.*?)\r?\n\]', 'Singleline'); $n = ([regex]::Matches($lm.Groups[1].Value, '(?m)^\s+''')).Count; if ($n -lt 1) { Write-Host '[DOD-FAIL] cannot count decompose-cards LENSES'; exit 1 }; foreach ($af in @('.claude/workflows/decompose-cards.mjs', '.claude/workflows/plan-forge.mjs', 'docs/PLAN-FORGE.md', '.claude/skills/task-loop/SKILL.md')) { $am = [regex]::Matches((Get-Content -Raw -LiteralPath $af -Encoding utf8), '(\d+) ?角度'); if ($am.Count -lt 1) { Write-Host "[DOD-FAIL] $af states no card-audit angle count"; exit 1 }; foreach ($x in $am) { if ([int]$x.Groups[1].Value -ne $n) { Write-Host "[DOD-FAIL] $af says $($x.Value), LENSES has $n"; exit 1 } } }; $dc = @(Get-Content -LiteralPath docs/PLAN-FORGE.md -Encoding utf8 | Where-Object { $_.StartsWith('| decompose-cards |') }); if (($dc.Count -ne 1) -or (-not $dc[0].Contains("1 拆解 + $n 并行卡审"))) { Write-Host '[DOD-FAIL] PLAN-FORGE.md decompose-cards cost row does not match LENSES'; exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Only 1,14; if ($LASTEXITCODE -ne 0) { Write-Host '[DOD-FAIL] selftest -Only 1,14'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: decompose-cards.mjs has no markers, maintainer note or vague filter in its non-comment text (A1, A2) and every anchored phrase once (A3); every stated angle count equals LENSES (A4); the stale plan-forge claims are gone and each face names decompose-cards (A5); PLAN-FORGE.md lists tier and the 81-agent T2 maximum (A6); selftest -Only 1,14 exits 0 (A7); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] decompose-cards still present 【.
review_gate: codex {verdict:pass}
budget: 180
hygiene: R4 runs single-statement mutants against the dod_command, each recorded in the R5 note with its [DOD-FAIL] line - put one 【】 pair back in decompose-cards.mjs, restore 只报真问题, put 4 角度 back in PLAN-FORGE.md, put plan-forge 投影任务卡 back in FRONTEND-FLOW.md, drop tier from the PLAN-FORGE.md call, delete the 拓扑/依赖正确性 lens text; every one must exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row, and ask the user whether to add the doc drift to upstream issue #399.
---

# T0-PLAN-FORGE-FOLLOWUP

Opened by user decision on 2026-09-25, after `T0-PLAN-FORGE-PROMPT-TRIM` (PR #360) trimmed the plan-forge
prompts and listed these as non-goals.

## Deliverable

**Part 1: decompose-cards.mjs prompts** (same method as #360; every cut points at a reference entry):

- Remove the emphasis markers: 9 【】 pairs and 6 bold pairs in the model-facing text.
- Move the maintainer note about where the size standard is defined into a code comment.
- Replace 只报真问题 in the card-audit prompt with the concrete threshold in A2.
- Make the stated audit-angle count match `LENSES`. It has 5 entries (right-size, topology, DoD, hard
  boundaries, allow_paths), but the description, the log line and PLAN-FORGE.md say 4.

**Part 2: docs that predate TD180.** plan-forge stops at the verdict and decompose-cards.mjs owns projection,
but seven faces still say plan-forge projects task cards, and PLAN-FORGE.md still lists `templatePath`, the
removed Decompose and Card-Audit stages, and a 60-85 agent cost. Each face gets the minimal correction:
plan-forge audits the plan, decompose-cards projects the cards.

## Found during the sweep, not in scope

`decompose-cards.mjs` dereferences `decomp.cards` right after `agent()` without a null guard, the defect
gate 1a guards against in plan-forge (TD52). It is a separate fix.

## Evidence at registration

- The dod_command was run on base `b9a39f36`: exit 1, `[DOD-FAIL] decompose-cards still present 【`.
- Every stale phrase it bans occurs on base, so none of the bans is vacuous.
- The angle-count arm alone exits 1 on base (`decompose-cards.mjs says 4 角度, LENSES has 5`).
- The PLAN-FORGE.md tier and cost-row arms are both red on base.
- All 26 criterion anchors occur exactly once on base.
- Tier 1 (no TierS path).

## Acceptance

```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: see `dod_assert`; the closed list is `acceptance:` above.
