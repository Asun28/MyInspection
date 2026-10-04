---
id: T0-PREREVIEW-PROMPT
title: _prereview-prompt.ps1 - Build-PrereviewPrompt with the shared fence helpers and the verdict instruction stripped, plus composed-lens section selection
status: todo
depends_on: [T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-CHECKLISTS]
allow_paths:
  - scripts/_prereview-prompt.ps1
  - scripts/fixtures/prereview/prompt/
  - specs/tasks/T0-PREREVIEW-PROMPT.md
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-prompt.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-PROMPT-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-PROMPT-SELFCHECK-PASS] after the golden prompt is byte-equal to the built prompt modulo DATA-{nonce}, the R3 verdict instruction is absent, an injected report-no-findings line stays inside a fenced data segment, and lens sections are selected by path class including the case scripts change plus own card => scripts section only.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-FACTS, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-RECORDS, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 Build-PrereviewPrompt composes prompt.txt from the selected checklist sections, the rubric, the card, acceptance.json, the fence-hardened diff, the changed-file slice and the tree index, wrapping every data segment with the per-run nonce fences from scripts/_reviewprompt.ps1 (New-FenceNonce, Protect-FenceMarkers, dot-sourced, not copied), and contains no verdict-output instruction."
  - "A2 The self-check's golden prompt is byte-equal to the built prompt after normalising DATA-[0-9a-f]{12}, and an injected report-no-findings line planted in the diff stays inside a fenced data segment."
  - "A3 Select-PrereviewLensSections picks the Lens sections by the changed files' path classes (code = android/** production, tests = *Test* files, selftest fixtures and receipts, prose = comments, KDoc, docs/**, context/**, specs/**, scripts = scripts/**, .github/**, .claude/hooks/**), ignoring the card's own specs/tasks/{id}.md, _local/** and .review/**, so scripts change plus own card selects the scripts section only, and the selected list is what the record's lens field carries."
  - "A4 An optional card front-matter key review_lens pins the section list when present (check-cards.ps1 reads named keys only, so no card change is needed)."
  - "A5 -SelfCheck reads only its fixture folder, spawns nothing, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, and prints [PREREVIEW-PROMPT-SELFCHECK-PASS]."
forbid:
  - A second fence implementation (dot-source scripts/_reviewprompt.ps1)
  - A narrower lens-specific prompt (both workers receive the same prompt.txt)
  - Editing docs/PREREVIEW-CHECKLISTS.md or scripts/_reviewprompt.ps1
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop the verdict-instruction strip => golden red; drop the fence on the diff segment => injection case red; drop the own-card exclusion => scripts-only selection red; drop the review_lens override => pinned-lens case red.
---

# T0-PREREVIEW-PROMPT

## Context

Builds prompt.txt for both workers from the pack, with the same fence helpers the reviewer uses (T0-PREREVIEW-FACTS-EXTRACT) and the R3 verdict-output instruction stripped; selects the checklist sections by path class.

## Interfaces carried by this card

- Build-PrereviewPrompt -PackDir -Sections -> string (order: checklists, rubric, card, acceptance, fenced diff, changed-file slices, tree index).
- Select-PrereviewLensSections -ChangedPaths -OwnCard -> list of Lens headings (code, tests, prose, scripts), ignoring the card's own specs/tasks file, _local/** and .review/**; an optional review_lens front-matter key overrides.

## Notes

Split line: ~300 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
