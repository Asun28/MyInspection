---
id: T0-PREREVIEW-CHECKLISTS
title: docs/PREREVIEW-CHECKLISTS.md - worker-facing checklists in four Lens sections that enter the pack and the policy hash
status: todo
depends_on: [T0-PREREVIEW-SCHEMA]
allow_paths:
  - docs/PREREVIEW-CHECKLISTS.md
  - scripts/fixtures/prereview/checklists/
  - specs/tasks/T0-PREREVIEW-CHECKLISTS.md
dod_command: $f = 'docs/PREREVIEW-CHECKLISTS.md'; if (-not (Test-Path $f) -or (Get-Item $f).Length -gt 12288) { exit 1 }; $l = @(Get-Content $f); foreach ($s in @('code', 'tests', 'prose', 'scripts')) { if (@($l | Where-Object { $_ -eq ('## Lens: ' + $s) }).Count -ne 1) { exit 1 } }; $c = @($l | Where-Object { $_ -match '^- C[1-7] ' }); if ($c.Count -lt 8 -or @($c | Where-Object { $_ -notmatch 'L[0-9]+|rubric:[0-9]+' }).Count -ne 0) { exit 1 }
dod_exit: 0
dod_assert: docs/PREREVIEW-CHECKLISTS.md exists, is at most 12288 bytes, has exactly one line each equal to # Lens: code, # Lens: tests, # Lens: prose and # Lens: scripts, and every check line starting with - C{n} (at least eight of them) names a lesson id L{digits} or a rubric dimension rubric:{digits}.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-RECORDS, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 docs/PREREVIEW-CHECKLISTS.md is at most 12288 bytes and has exactly one heading line each for Lens code, tests, prose and scripts in the form Lens: {class}, which Select-PrereviewLensSections (PROMPT) selects by path class."
  - "A2 Every check line has the form - C{n} ... with n in 1..7 and names at least one source token L{digits} or rubric:{digits}, so each check is traceable to a lesson or rubric dimension and is machine-checkable."
  - "A3 The code section covers guards on every entry point, empty/null/overflow, fail-open branches, ordering assumptions and API level versus minSdk; tests covers L165, L225, L282 and L324; prose covers L309, L317, L321 and L224 plus the sibling-clause and universal-claim checks; scripts covers every error branch open or closed, every exit code reachable and every ASCII code unique."
  - "A4 A coverage-rules paragraph states that the discoverer returns one coverage record per unit in units.json with categories_checked covering C1, C2 and C3 (RECORDS synthesises missing otherwise), that candidates use only C1, C2, C3, C4, C5, C7, and that C6 is derived from dispute records only."
  - "A5 The document is written as data: it enters the pack as checklists.md and the policy hash with the record schema (FACTS-LIB), so any later wording change is a policy change by construction; the card adds nothing outside this document."
forbid:
  - Any content outside docs/PREREVIEW-CHECKLISTS.md
  - A separate lens per path class or per-lens A/B (one composed lens only)
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
---

# T0-PREREVIEW-CHECKLISTS

## Context

Worker-facing checklists, written as data: the file enters the pack as checklists.md and the policy hash (FACTS-LIB), so a wording change is a policy change by construction.

## Sections

- Lens code (android/** production): guards on every entry point, empty/null/zero/negative/overflow inputs, fail-open branches, ordering assumptions on collections feeding hashes or receipts, API level of every JDK/Android call against minSdk (rubric 15).
- Lens tests (*Test* files, selftest fixtures, receipts): L165, L225, L282, L324.
- Lens prose (comments, KDoc, docs/**, context/**, specs/**): L309, L317, L321, L224, sibling-clause and universal-claim checks.
- Lens scripts (scripts/**, .github/**, .claude/hooks/**): every error branch open or closed, every exit code reachable, every ASCII code unique.
- Coverage rules: one coverage record per unit with categories_checked covering C1, C2 and C3; candidates use C1, C2, C3, C4, C5, C7; C6 is derived from dispute records only.

## Notes

At most 12288 bytes. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
