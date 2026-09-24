---
id: T0-PREREVIEW-FACTS-EXTRACT
title: Pure move of the reviewer prompt assembly and fence helpers from review.ps1 into scripts/_reviewprompt.ps1 (same prompt modulo nonce)
status: todo
depends_on: [T0-PREREVIEW-RUNNER]
allow_paths:
  - scripts/_reviewprompt.ps1
  - scripts/review.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
  - scripts/fixtures/prereview/extract/
  - specs/tasks/T0-PREREVIEW-FACTS-EXTRACT.md
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git -Fixture review-extract *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[SELFTEST-FIXTURE] review-extract PASS')) { exit 1 }
dod_exit: 0
dod_assert: The seeded-git shard runs 17gg and 17a..17y unchanged and green, then the review-extract fixture (after review-runner, before the seeded:17-git-main record) early-exits with [SELFTEST-FIXTURE] review-extract PASS after proving that the same tree yields the same reviewer prompt modulo DATA-{nonce} before and after the move, that the diff/numstat/budget block and every [R3-...] emission are still in review.ps1, and that Protect-FenceMarkers and New-FenceNonce are exported by scripts/_reviewprompt.ps1.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 scripts/_reviewprompt.ps1 exports Build-ReviewPrompt (inputs: base OID, rubric text at base, card text at base, FrozenPaths clause, stat text, unified diff, nonce) plus Protect-FenceMarkers and New-FenceNonce, moved verbatim from review.ps1; review.ps1 dot-sources it and calls Build-ReviewPrompt where the inline assembly was."
  - "A2 The diff, numstat and budget block, every [R3-...] emission, the first verdict = hashtable literal (gate 6), model_reasoning_effort= and REVIEW_EFFORT (17z) and the 17ff anchors stay in review.ps1; the fixture greps all of them."
  - "A3 The fixture captures the reviewer prompt for one tree through a stub ReviewCommand before and after the move (both from the fixture's own copies) and asserts byte equality after normalising DATA-[0-9a-f]{12}, six fence lines per prompt, and two different nonces."
  - "A4 No behaviour changes: the reviewer stdin, verdict parsing, .rounds accounting and error codes are untouched; the review-runner fixture and the 17 family stay green."
  - "A5 The fixture review-extract is a ValidateSet entry placed after review-runner and before the seeded:17-git-main record with an early exit; docs/QUALITY-RUBRIC.md gains one pointer sentence naming scripts/_reviewprompt.ps1 (DocSyncMap 14f)."
  - "A6 The diff measured with review.ps1 -SizeOnly before RED stays under about 500 changed lines (a move counts twice); nothing else is in the card."
forbid:
  - Adding -FactsOut, -SizeOnly -Tree or any new mode (T0-PREREVIEW-FACTS)
  - Touching the launcher or scripts/_subprocess.ps1 (T0-PREREVIEW-RUNNER)
  - Adding a nonce override parameter or any way for two runs to share a nonce
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants that must turn the fixture red: drop the Protect-FenceMarkers call on the diff segment (fence count changes); reuse a fixed nonce (the two-nonces assertion); move one [R3-...] emission into the helper (17t(doc) grep); drop the QUALITY-RUBRIC sentence (14f on the real run).
doc_sync: docs/QUALITY-RUBRIC.md pointer sentence in the same commit; TASK-BOARD status after merge.
---

# T0-PREREVIEW-FACTS-EXTRACT

## Context

Second card of the review.ps1 chain. After it, Build-PrereviewPrompt (T0-PREREVIEW-PROMPT) can reuse the same fence helpers, so the packet's data segments are hardened exactly like the reviewer's (TD48).

## Interfaces carried by this card

- Build-ReviewPrompt(baseOid, rubricText, cardText, frozenClause, statText, diffText, nonce) -> string; identical bytes to the previous inline assembly for the same inputs.
- Protect-FenceMarkers(text) and New-FenceNonce() moved verbatim.

## Notes

Split line: ~450 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
