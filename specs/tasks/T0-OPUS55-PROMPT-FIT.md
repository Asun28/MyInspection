---
id: T0-OPUS55-PROMPT-FIT
title: Fit improve-prompt, task-loop and plan-forge to Claude Opus 5.5 (new Opus 5.5 prompting reference, refreshed Opus 5 base, Opus 5.5 routing and review effort, turn-end rules, fail-closed lens accounting)
status: merged
depends_on: []
branch: T0-OPUS55-PROMPT-FIT
worktree: C:\wt\T0-OPUS55-PROMPT-FIT
allow_paths:
  - docs/references/claude-opus-5-5-prompting-llms.txt
  - docs/references/claude-opus-5-prompting-llms.txt
  - docs/references/README.md
  - .claude/skills/improve-prompt/SKILL.md
  - .claude/skills/task-loop/SKILL.md
  - .claude/workflows/plan-forge.mjs
  - scripts/selftest.ps1
  - scripts/_config.ps1
  - CLAUDE.md
sweep: "2026-09-24 on origin 1583b4d7: git grep -n -I -e claude-opus-5 -e 'Opus 5' excluding docs/references and specs/archive. Faces that TEACH which model fills the Opus seat or how hard it reviews, all in allow_paths: CLAUDE.md (Opus seat line, improve-prompt index line, effort line, model-reference list), .claude/skills/improve-prompt/SKILL.md (description, step 1, fallback red line), docs/references/README.md index, scripts/_config.ps1 PrereviewRiskyModel. Hits that RECORD past work and stay as they are: CLAUDE.md current-phase logs and the docs/research row, docs/TASK-BOARD.md per-card executor columns with the CLAUDE.md fleet line that defers to it, docs/research bylines, docs/adr/0007, docs/lessons/LEDGER.md, prereview schema fixtures that carry claude-opus-5 as recorded data, and open cards naming their own executor. task-loop line 39 cites the Opus 5 reference for a rule the Opus 5.5 page leaves unchanged. .claude/skills/frontend-design is a non_goal."
dod_command: $ref = 'docs/references/claude-opus-5-5-prompting-llms.txt'; if (-not (Test-Path $ref)) { exit 1 }; $r = Get-Content -Raw $ref; foreach ($k in @('https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5-5', '(c) Anthropic', '2026-09-24', 'claude-opus-5-prompting-llms.txt', 'reasoning_extraction')) { if (-not $r.Contains($k)) { exit 1 } }; if ((Get-Item $ref).Length -gt 20480) { exit 1 }; $o5 = Get-Content -Raw docs/references/claude-opus-5-prompting-llms.txt; if (-not ($o5.Contains('CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS') -and $o5.Contains('2026-09-24'))) { exit 1 }; if (-not (Get-Content -Raw docs/references/README.md).Contains('| `claude-opus-5-5-prompting-llms.txt` |')) { exit 1 }; $ip = Get-Content -Raw .claude/skills/improve-prompt/SKILL.md; if (-not ($ip.Contains('Opus 5.5') -and $ip.Contains('`opus-5-5`'))) { exit 1 }; $opusLine = @(Select-String -Path CLAUDE.md -Pattern '^- \*\*Opus\*\*'); if ($opusLine.Count -ne 1 -or -not $opusLine[0].Line.Contains('`claude-opus-5-5`')) { exit 1 }; if (-not (Get-Content -Raw .claude/skills/task-loop/SKILL.md).Contains('claude-opus-5-5-prompting-llms.txt')) { exit 1 }; if (([regex]::Matches((Get-Content -Raw .claude/workflows/plan-forge.mjs), 'skipped_lenses:')).Count -lt 3) { exit 1 }; $cfg = Get-Content -Raw scripts/_config.ps1; if (-not (($cfg -match "(?m)^\s*PrereviewRiskyModel = 'claude-opus-5-5'") -and ($cfg -match "(?m)^\s*PrereviewRiskyEffort = 'medium'") -and ($cfg -match "(?m)^\s*PrereviewEffort = 'high'"))) { exit 1 }; & pwsh -NoProfile -File scripts/selftest.ps1 -Only 1,14; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: The Opus 5.5 reference exists with its source URL, (c) Anthropic, check date, base-layer pointer and the reasoning_extraction category, within 20480 bytes; the Opus 5 reference carries the Claude Code subagent caps and the 2026-09-24 check date; the references index lists the new file; improve-prompt names Opus 5.5 and the opus-5-5 suffix; the single CLAUDE.md Opus seat line names claude-opus-5-5; task-loop points at the Opus 5.5 reference; plan-forge returns skipped_lenses on all three return paths; _config.ps1 routes risky prereview to claude-opus-5-5 at medium while PrereviewEffort stays high; and selftest -Only 1,14 exits 0, including sub-gate 1i's new arms. The Tier-S full-suite run before ship is A7.
review_gate: codex {verdict:pass}
budget: 500
acceptance:
  - "A1 docs/references/claude-opus-5-5-prompting-llms.txt distils the Opus 5.5 prompting page as a delta over the Opus 5 reference, following the provenance rules in docs/references/README.md (source URL, (c) Anthropic, check date 2026-09-24, functional prompt snippets quoted minimally with their source, no whole-page copy). It covers effort calibration (default medium; medium matching or beating Opus 5 high on coding and knowledge-work evaluations, with early testers reporting stronger code review; more thinking per level; max_tokens room), thinking always on, text-only turn ends in unattended runs and still-running background work, progress updates, safeguard categories including reasoning_extraction, multi-app exploration, multiagent time signals, chat thinking instructions, pasted-content marking, visual inputs and frontend defaults, and names its base file. At most 20480 bytes."
  - "A2 docs/references/claude-opus-5-prompting-llms.txt is re-checked against the live Opus 5 page on 2026-09-24: it gains the paragraph that page added since the last check (the Claude Code / Agent SDK subagent caps CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH, CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS and max_budget_usd, Claude Code 2.1.217 or later) and states its role as the base layer the Opus 5.5 delta builds on. docs/references/README.md indexes the new file and restates the Opus 5 row's role."
  - "A3 improve-prompt routes planning, architecture and review prompts to Opus 5.5 and, for Opus 5.5, reads the opus-5-5 delta together with the opus-5 base, the delta winning where they differ. Its fallback red line no longer presents Opus 4.8 as the current fallback for Opus 5.5, whose page names only the model Anthropic recommends for each category. In CLAUDE.md the Opus seat line names claude-opus-5-5, and the improve-prompt index line and the model-reference list agree with it."
  - "A4 task-loop gains a turn-end section giving the card's completion condition; the four early stops the loop must not take; the stops it must take (CLAUDE.md execution-boundary confirmations, the R3 round cap or a two-round maker-checker disagreement, card changes that need a user ruling, an external failure that needs an authorised fallback, a protected gate refusing); that a phase is not done while a background command or subagent it started is still running; and the Opus 5.5 review effort (start at medium; high for Tier-S or security-surface changes; xhigh or max only after a measured gain). Its prerequisites tell the loop to read the card's neighbourhood before the first edit. CLAUDE.md's effort line gives the same review default. The existing task-loop structure checks stay green."
  - "A5 In plan-forge, lenses report every locatable FATAL, HIGH and MEDIUM finding with a confidence, most severe first. The script sorts FATAL before HIGH itself, sends at most 3 per lens to verification, and logs the rest and hands them to synthesis as unverified instead of dropping them. A lens that returns no result (skipped, refused, or ended without structured output) is listed in skipped_lenses on every return path and turns a ready-to-decompose verdict into fix-first, with one HIGH correction per missing lens. Selftest sub-gate 1i drives both: a T2 run seeding 5 FATAL findings per lens requests exactly 72 judges (8 lenses x 3 verified x 3 angles) and its synthesis prompt carries each lens's 4th and 5th finding; a T2 run whose boundary lens returns null yields fix-first with skipped_lenses equal to [boundary]. Every existing 1i arm keeps its expectation."
  - "A6 scripts/_config.ps1 sets PrereviewRiskyModel = 'claude-opus-5-5' and adds PrereviewRiskyEffort = 'medium' for the risky (Opus) route, leaving PrereviewEffort = 'high' for the standard Sonnet route; its comment says the knob is a value only until the prereview Claude adapter reads it."
  - "A7 selftest.ps1 -TaskId T0-OPUS55-PROMPT-FIT, which for this Tier-S card runs the whole suite, exits 0 in the worktree on the shipped candidate; the DoD itself runs the static arms plus the scoped -Only 1,14 diagnosis."
forbid:
  - Removing or loosening any existing selftest 1i arm, or the 1a synth null guard
  - Changing the Codex R3 reviewer settings (ReviewModel, ReviewEffort, ReviewEffortBySize) or PrereviewModel / PrereviewEffort / the DeepSeek lens knobs
  - Copying whole sections of the Anthropic pages; quote only snippets that must reach a model verbatim
non_goals:
  - The local-master copies of these files (local master has diverged from origin; the change reaches it with the next reconcile)
  - The prereview Claude adapter or runner that will read PrereviewRiskyEffort
  - decompose-cards.mjs, frontend-design and taste-skill frontend defaults, docs/TASK-BOARD.md per-card routing and the CLAUDE.md fleet line
  - Per-stage effort pins inside plan-forge, and docs/PLAN-FORGE.md's older output description
  - Multiagent time-budget signals in the harness, which Claude Code's Agent tool gives no way to inject
hygiene: Each new 1i arm gets a one-line deletion mutant (drop the overflow hand-off; drop the skipped-lens verdict override) that turns 1i red, recorded in the card's R5 note.
doc_sync: CLAUDE.md model routing and effort lines change in-card; at R5 set status merged and add the origin current-phase line.
---

# T0-OPUS55-PROMPT-FIT

## Deliverable

Bring the three agent-facing surfaces that encode prompting habits up to Claude Opus 5.5, which is now the Opus seat:

- **Reference layer.** A new `claude-opus-5-5-prompting-llms.txt` holds the Opus 5.5 page as a delta. The Opus 5 file stays as the base layer: the Opus 5.5 page says the Opus 5 patterns remain a reasonable starting point. The Opus 5 file is re-checked against its live page, which added the Claude Code subagent caps since 2026-07-25.
- **improve-prompt.** Route the Opus seat to Opus 5.5 and read delta plus base.
- **task-loop.** Opus 5.5 ends turns with text-only progress reports on long multi-part work, which stops an autonomous card loop partway. Name the completion condition, the early stops to avoid and the stops to keep. Add the Opus 5.5 review-effort default, and the pre-edit exploration the page recommends for loosely specified work.
- **plan-forge.** Separate discovery from filtering (the Opus 5 review guidance still applies, and Opus 5.5 reviews with fewer false alarms). Make the per-lens verification cap a logged hand-off rather than a silent drop. Treat a lens that produced nothing as a missing audit dimension: Opus 5.5 adds bio and reasoning_extraction to its safeguard classifiers, so a refused lens is more likely than before, and `agent()` returns null for it.
- **Review effort.** In Anthropic's testing Opus 5.5 at medium matches or beats Opus 5 at high on agentic coding and knowledge-work evaluations, and early testers report stronger code review with fewer false alarms. The Opus-backed prereview route moves to claude-opus-5-5 at medium; the guidance for Opus-run reviews starts at medium.

## Sources

- https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5-5 (read 2026-09-24)
- https://platform.claude.com/docs/en/models/opus-5-5/whats-new-opus-5-5 (read 2026-09-24)
- https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/prompting-claude-opus-5 (re-read 2026-09-24)
- https://platform.claude.com/docs/en/build-with-claude/refusals-and-fallback (read 2026-09-24; shared by Fable 5.1, Fable 5, Opus 5.5 and Opus 5)

## Acceptance

```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: see `dod_assert`; the closed list is `acceptance:` above.

## R5 (2026-09-24)

Merged by PR #334 as squash commit `ecdeb4b8` on origin/master, pinned to reviewed head `06b853fc`. The card was registered by PR #331 (`455e260`). The merge was held during the 2026-09 local/origin reconcile at that session's request, and origin/master (`68d38333`) was merged into the branch after its all-clear. That merge was clean; its only follow-up was one same-class line in task-loop naming `[BASE-AHEAD-OF-ORIGIN]` as a stop.

- **R3 route.** Codex was out of quota until 2026-09-25 10:56 (probe and ship stderr). With the user's authorization, each ship's Codex leg failed closed, the round was reset after diagnosis, and a fresh Claude Opus 5.5 instance (`--effort high`, the card being Tier S; read-only tools) reviewed through a temporary `ReviewCommand` in `review.ps1 -PostStatus`. That config change was never committed. The `codex-review` statuses on #334 therefore come from Opus 5.5.
- **Round 1** (`6182f114`) blocked on one spec finding. The 1i fixtures seeded only FATAL findings, so the FATAL-before-HIGH sort was unobserved, and the synthesis-skipped return was never driven. Fixed with the `mixed` and `nullSynth` fixtures plus log and confidence arms.
- **Round 2** (`4b840c1f`) passed spec and blocked on three standards points:
  - The Opus 4.8 fallback example was attributed to the Opus 5 page. It lives on the shared refusals-and-fallback page.
  - The migration table omitted `computer_20251124`. Rows 8 and 9 were added; the table now covers all four breaking changes.
  - The plan-forge overflow log and synthesis header were tier-blind at T1. Both now branch on VERIFY, with a `t1Overflow` fixture.
  
  A fresh-context pre-review of that fix diff found seven same-class points, all fixed. Among them: the effort evidence (the page supports "medium ≥ Opus 5 high" for coding and knowledge work, and reports stronger code review only from early testers, which also corrects A1 and the Deliverable wording above), the fallback exception (no recommended fallback means the refusal stands), and positive T2 arms.
- **Round 3** (`55a4817a`, authorized past the two-round cap) and **Round 4** (`06b853fc`, after the reconcile merge) both passed with 0 findings.
- **R4 hygiene.** Selftest sub-gate 1i, `-Only 1`, on the merged candidate (plan-forge `8263AFAA`, selftest `19487A4B`): 16 of 16 mutants killed, each by its named arm. The set includes dropping the overflow hand-off, the skipped-lens verdict override and both log lines; widening the cap; deleting or inverting the severity sort; emptying `skipped_lenses` on the synthesis-skipped and final returns; dropping both `confidence` prompt mentions; swapping the tier wording both ways; and restoring the exact pre-fix tier-blind texts round 2 reported. One earlier mutant was equivalent: removing one of two prompt mentions of `confidence`. Removing both is killed.
- **Tier-S proof.** `selftest.ps1 -TaskId T0-OPUS55-PROMPT-FIT` gave `selftest: PASS` with 17 of 17 gates on the shipped head. ci.yml run 35991899624 passed `verify` and `required` on `06b853fc`.
- **Carried forward.** `PrereviewRiskyEffort` has no reader yet; `_prereview-facts.ps1` routes only the model. This is registered as TD188. Lessons L165 and L309 recurred and were bumped.