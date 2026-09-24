---
id: T0-PLAN-FORGE-PROMPT-TRIM
title: Trim the plan-forge prompts for Claude Opus 5.5 (drop emphasis markers, boosts, pipeline mechanics and ticket ids the agents do not need; keep every audit criterion)
status: todo
depends_on: []
parallelizable_with: []
branch: T0-PLAN-FORGE-PROMPT-TRIM
worktree: C:\wt\T0-PLAN-FORGE-PROMPT-TRIM
allow_paths:
  - .claude/workflows/plan-forge.mjs
forbid:
  - Changing what any agent is asked to judge - the severity definitions, the rule-3 specificity threshold, any lens's check items, the judges' refutation default, the synthesis verdict rule
  - Changing the structure - the eight lenses, the T1 lens set, the three judge angles, the 2-of-3 refutation, the per-lens cap of 3, the schemas, the return shape, the tier routing, the log lines
  - Editing scripts/selftest.ps1 or loosening any 1a, 1i or 14c arm
  - Cutting text that no reference entry backs; such text stays and is raised as a question instead
non_goals:
  - docs/PLAN-FORGE.md, which still describes the Decompose and Card-Audit stages TD180 removed
  - The same wording pass on decompose-cards.mjs and scout-options.mjs
  - Judge-count or per-stage effort changes, and a live A/B run of plan-forge (a workflow run needs the user's explicit multi-agent opt-in)
  - Translating the prompts to English
  - Filing the upstream scaffold report for this wording rule (offered to the user at R5)
acceptance:
  - "A1 No line of plan-forge.mjs that is not a // comment carries the 【 】 brackets, ** bold, 铁律, 最高价值, 最重要 or 你必须. Basis: claude-prompting-best-practices-llms.txt, 工具使用 (newer models respond more strongly to the system prompt, so CRITICAL/MUST-style emphasis overtriggers and plain wording is the fix) and 输出与格式 (the prompt's formatting carries into the output)."
  - "A2 Rule 4 of the shared lens prompt keeps its discovery instruction (report every FATAL, HIGH and MEDIUM finding past the rule-3 threshold, uncertain ones included, each with confidence(high/med/low), most severe first), says filtering is a separate later step with the reason the Opus discovery/filter guidance gives (a finding filtered out later is better than a real one dropped), and no longer tells the lens that only the top 3 are verified or which tier verifies. Basis: claude-opus-5-prompting-llms.txt, 代码评审 harness (a filtering signal in a review prompt makes the model report less). The decomposition lens loses the ticket ids T233/TD235, which move to a code comment. Basis: claude-prompting-best-practices-llms.txt, 通用原则, the golden rule (a word a colleague without the context cannot read, the model cannot read either)."
  - "A3 Every other prompt sentence keeps its wording apart from the removed markers (a 的 or a colon fills a removed bracket where the sentence needs one), so what the agents judge by is unchanged: the severity definitions, the rule-3 threshold, the prior-review rule with its reason, rule 5 (the plan stays the source of truth and the output is an audit opinion), the read-first file list, each lens's check items, the judges' 默认怀疑 and refuted=true rule, and the synthesis rules. The dod_command anchors one phrase or more per lens, per shared rule (both branches of rule 1), for the judges and for the synthesis rules, and each anchor occurs exactly once in the non-comment text."
  - "A4 A comment above the shared lens prompt states the wording rule and its reference sources, so the markers are not added back by the next edit."
  - "A5 selftest.ps1 -Only 1,14 passes without any change to selftest.ps1: the 1a synthesis null guard, every 1i arm (tier routing, overflow hand-off, severity sort, skipped lens) and the 14c lens count."
  - "A6 The tier-1 acceptance run selftest.ps1 -TaskId T0-PLAN-FORGE-PROMPT-TRIM passes on the shipped candidate."
dod_command: $p = '.claude/workflows/plan-forge.mjs'; if (-not (Test-Path -LiteralPath $p)) { Write-Host '[DOD-FAIL] plan-forge.mjs missing'; exit 1 }; $code = (Get-Content -LiteralPath $p -Encoding utf8 | Where-Object { $_ -notmatch '^\s*//' }) -join "`n"; foreach ($t in @('【', '】', '**', '铁律', '最高价值', '最重要', '你必须', 'T233', 'T1 档不核验', 'MEDIUM 不参与对抗核验')) { if ($code.Contains($t)) { Write-Host "[DOD-FAIL] still present $t"; exit 1 } }; foreach ($k in @('动手前先 Read 这些文件', '主动避免重复它已发现的问题', '不要重报前次评审已发现的问题 —— 那是浪费', '冻结而变得 load-bearing', 'FATAL = 前期错则后面白干', 'HIGH = 开工早期必须修否则放大', 'MEDIUM = 应改但不阻塞', '且能给出可执行修法的问题', 'confidence(high/med/low)', '好过漏掉一个真问题', '计划的真相源地位不可动摇', '逐字段追问', '冻结后改契约 = 所有下游卡返工', 'openapi-typescript', 'check-budget.ps1', '[CARD-BUDGET-OVER]', '缺失判 MEDIUM', '过碎的卡', 'copyleft', 'PYTHONPATH', '砍掉 50% 留什么', '模块化单体', 'UUIDv7', '唯一索引是否包含 deleted', 'FrozenPaths', 'docs/lessons/database.md', '默认怀疑', 'refuted=true', '核验上限', '都能在开拆前修掉', '逐条自己对着计划核实')) { $n = ([regex]::Matches($code, [regex]::Escape($k))).Count; if ($n -ne 1) { Write-Host "[DOD-FAIL] anchor count $n for $k"; exit 1 } }; & pwsh -NoProfile -File scripts/selftest.ps1 -Only 1,14; if ($LASTEXITCODE -ne 0) { Write-Host '[DOD-FAIL] selftest -Only 1,14'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The non-comment text of plan-forge.mjs has none of the emphasis markers, the ticket ids or the two tier-mechanics phrases (A1, A2), has every anchored criterion phrase exactly once (A3), and selftest -Only 1,14 exits 0 (A5); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] still present 【.
review_gate: codex {verdict:pass}
budget: 120
hygiene: R4 runs single-statement mutants against the dod_command, each recorded in the R5 note with its [DOD-FAIL] line - put one 【】 pair back in a lens, put the 铁律 header back, delete the budget bullet, delete the new filter-step reason, put T233/TD235 back in the decomposition lens, delete the soft-delete unique-index check; every one must exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row, and ask the user whether to file the upstream scaffold report.
---

# T0-PLAN-FORGE-PROMPT-TRIM

## Deliverable

`plan-forge.mjs` sends three kinds of prompt: the shared lens prompt plus one focus per lens, the refutation
judges' prompt, and the synthesis prompt. They were written for earlier models and carry wording that
Claude Opus 5.5 does not need:

- **Emphasis.** 24 non-comment lines use 【】 brackets, `**` bold, 铁律 (iron rules), 你必须 (you must), 最高价值 (highest
  value) or 最重要 (most important), including a "this is the highest-value lens" boost in future-self. The
  best-practices reference says newer models respond more strongly to the system prompt, so CRITICAL/MUST-style
  emphasis overtriggers and plain wording is the fix.
- **Pipeline mechanics in the lens prompt.** Rule 4 tells each lens that only its top 3 findings get verified,
  and at which tier. The Opus 5 reference's review guidance tells the lens only that a separate step filters,
  gives the reason to state, and warns that a filtering signal in a review prompt makes the model report less.
- **Ticket ids** (T233/TD235) inside the decomposition lens. A colleague without the context cannot read them,
  so neither can the model (the best-practices golden rule). They move to a code comment.

Every other sentence keeps its wording apart from the removed markers, so what the agents judge by is unchanged:
severity definitions, the specificity threshold, the prior-review rule and its reason, rule 5, each lens's check
items, the judges' refutation default and the synthesis rules. The structure stays too: lenses, tiers, judge
counts, schemas, return shape and log lines. Each cut has to point at a reference entry (the improve-prompt
rule). Text with no reference entry behind it stays.

Kept: "动手前先 Read" (read these files first). No reference entry backs cutting it, and the Opus 5.5 page's
multi-app section recommends telling the model to read the relevant sources before acting on loosely
specified tasks.

## Amendment (2026-09-25, before ship)

A fresh-context pre-review of the first candidate found two cuts no reference entry backs: the prior-review
rule had been rewritten without its exclusion and its reason (那是浪费), which also let an already-reported
issue back in through the second branch, and rule 5 had lost "计划的真相源地位不可动摇". Both are restored,
minus their markers. A2 and A3 now say exactly what changes and what stays. The dod_command's anchors were
strengthened: each now occurs exactly once in the non-comment text, so each guards one rule. The earlier
anchors "KANO", "具体性门槛", "novel_vs_prior_review" and "动手前先 Read" each appeared more than once or
matched only a label.

## Sources

- https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5-5 (read 2026-09-25), distilled in `docs/references/claude-opus-5-5-prompting-llms.txt`
- `docs/references/claude-opus-5-prompting-llms.txt`, 代码评审 harness (the Opus 5.5 page keeps the Opus 5 patterns as its base)
- `docs/references/claude-prompting-best-practices-llms.txt`, 通用原则, 输出与格式, 工具使用

## Evidence at registration

A draft of the change (31 insertions, 25 deletions, 18 414 diff characters in one file) was checked against
this dod_command before the card was registered:

- base `8cc80f30`: exit 1, `[DOD-FAIL] still present 【`
- draft: exit 0, `[DOD-PASS]`; `selftest.ps1 -Only 1,14` passed (`[SELFTEST-ONLY-PASS] gates=1,14`, 252 s).
  The three anchors for rule 1 and the synthesis verdict rule were added after that run; the static part
  was re-run with them and printed `[DOD-PASS]`.

The card computes tier 1, so the acceptance run is `selftest.ps1 -TaskId`, not the full suite.

## Acceptance

```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: see `dod_assert`; the closed list is `acceptance:` above.
