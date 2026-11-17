---
id: T0-PREREVIEW-FACTPACK-SLICES
title: prereview-facts.ps1 second writer - changed-file slices, acceptance.json, the content and path secret scan of the pack, and the pack size limit
status: todo
depends_on: [T0-PREREVIEW-FACTPACK]
allow_paths:
  - scripts/prereview-facts.ps1
  - scripts/fixtures/prereview/slices/
  - specs/tasks/T0-PREREVIEW-FACTPACK-SLICES.md
dod_command: pwsh -NoProfile -File scripts/prereview-facts.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; $t = (& pwsh -NoProfile -File scripts/prereview-facts.ps1 -SelfCheck -Fixture slices *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-SLICES-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: The plain prereview-facts.ps1 -SelfCheck (T0-PREREVIEW-FACTPACK) stays green, then -SelfCheck -Fixture slices exits 0 and prints [PREREVIEW-SLICES-SELFCHECK-PASS] after slices respect both byte caps, acceptance.json reproduces a golden projection of a fixture card, a planted content secret and a planted .env.local path each yield [PRE-SECRETS] with no pack left behind, and a pack over PrereviewMaxPackBytes stops with a non-zero exit.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 files/** holds the full text of every changed file up to PrereviewMaxFileBytes (200000) and, for docs/** and context/** files, up to PrereviewMaxDocBytes (1000000); larger files are listed in facts.json as truncated with their size and are not copied."
  - "A2 acceptance.json is the projection of the card's front-matter acceptance list (A-ids and text) read from the card at the merge-base, matching a golden file for the fixture card."
  - "A3 The secret scan applies both Find-LineSecret (content patterns) and Test-Sensitive (path patterns) from scripts/check-secrets.ps1 -AsLibrary to diff.patch, files/**, card.md and the path list of units.json; a hit prints [PRE-SECRETS], deletes the pack directory and exits non-zero before any worker could be started."
  - "A4 A pack whose total size exceeds PrereviewMaxPackBytes (20971520) stops with a non-zero exit and a message naming the largest entries; the fixture proves it with an oversized planted file."
  - "A5 -SelfCheck -Fixture slices runs on a temp repository only, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns no worker, never writes _local/, and prints [PREREVIEW-SLICES-SELFCHECK-PASS]; the plain -SelfCheck of T0-PREREVIEW-FACTPACK stays green and runs first in the DoD."
forbid:
  - Duplicating the secret patterns instead of dot-sourcing check-secrets.ps1 -AsLibrary
  - Writing anything into the reviewed worktree
  - Setting PRE_LIVE
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop Test-Sensitive (the .env.local path case passes); drop Find-LineSecret (the content case passes); drop the pack deletion on a hit (a pack survives); drop the PrereviewMaxDocBytes branch (a 300 KB doc is truncated instead of copied).
doc_sync: TASK-BOARD status after merge.
---

# T0-PREREVIEW-FACTPACK-SLICES

## Context

Second writer of scripts/prereview-facts.ps1 (chain FACTPACK -> FACTPACK-SLICES). T0-PREREVIEW-FACTPACK delivers the pack skeleton (temp root, base, review.ps1 -FactsOut -Tree, tree export, units.json, facts.json); this card adds what leaves the machine beyond the diff and must therefore be scanned: the changed-file slices, the acceptance projection and the pack-wide secret scan.

## Notes

Split line: ~300 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
