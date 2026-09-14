---
id: T0-PREREVIEW-FACTPACK
title: prereview-facts.ps1 CLI - pack skeleton - temp root, worktree and base through FACTS-LIB, review.ps1 -FactsOut -Tree, snapshot tree export, units.json and facts.json
status: todo
depends_on: [T0-PREREVIEW-FACTS, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-CHECKLISTS]
allow_paths:
  - scripts/prereview-facts.ps1
  - scripts/fixtures/prereview/factpack/
  - specs/tasks/T0-PREREVIEW-FACTPACK.md
dod_command: $t = (& pwsh -NoProfile -File scripts/prereview-facts.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-FACTS-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-FACTS-SELFCHECK-PASS] from the main checkout against a temp worktree: the pack is created under the temp root (never inside the worktree) and is absent from the next snapshot; the tree export equals git ls-tree -r content with core.autocrlf=false; a planted .env and .secrets/ appear nowhere in the pack; the exported base ref and OID equal the FACTS-LIB resolution with and without -Local; facts.json validates; no merge-base yields [PRE-NO-MERGE-BASE]; PRE_LIVE is empty after the non-SelfCheck build path.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 prereview-facts.ps1 -TaskId {id} -Base {name} [-Local] runs from the main checkout, resolves the worktree and base through the FACTS-LIB functions (never recomputing them), and creates the pack under Get-PrereviewTempRoot()/prereview/{id}/{snapshot_tree}/, outside the reviewed worktree."
  - "A2 It calls review.ps1 -WorktreePath -Base -LocalBase (when -Local) -FactsOut {pack} -Tree {snapshot_tree} and asserts the exported base ref and OID equal its own resolution; a mismatch stops with a non-zero exit."
  - "A3 The snapshot tree is exported into {pack}/tree/ with git -c core.autocrlf=false checkout-index --prefix (tracked content only); the fixture proves the export equals git ls-tree -r content and that a planted .env and .secrets/ in the worktree appear nowhere in the pack."
  - "A4 units.json is produced by Get-PrereviewUnits from the exported diff; facts.json carries snapshot_tree, head_sha, base_oid, base_mode, merge_base, policy_hash, rubric_sha, models, risk_class and pack_layout_version and validates as a facts record."
  - "A5 No merge-base yields [PRE-NO-MERGE-BASE] before any file is written; the non-SelfCheck path never sets PRE_LIVE (the fixture asserts $env:PRE_LIVE empty afterwards)."
  - "A6 The CLI has a -AsLibrary early return so the 1b gate can dot-source nothing from it by mistake (the library functions live in scripts/_prereview-facts.ps1); slices, acceptance.json and the secret scan are the next card (T0-PREREVIEW-FACTPACK-SLICES)."
  - "A7 -SelfCheck runs on temp repositories only, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns no worker, never writes _local/, and prints [PREREVIEW-FACTS-SELFCHECK-PASS]."
forbid:
  - Writing anything into the reviewed worktree or its .review/
  - A second diff pipeline (only review.ps1 -FactsOut) or a second base resolution
  - Emitting [PRE-NO-NETWORK-IN-CI] or setting PRE_LIVE
  - A pack root under the worktree or under $env:TEMP
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: place the pack inside the worktree (next-snapshot case red); drop core.autocrlf=false (export byte case red on a CRLF file); drop the base comparison (a mismatching stub passes); drop the merge-base check (no [PRE-NO-MERGE-BASE]).
---

# T0-PREREVIEW-FACTPACK

## Context

First writer of scripts/prereview-facts.ps1 (chain FACTPACK -> FACTPACK-SLICES). Builds the pack skeleton that the workers read: reviewer-identical facts from review.ps1 -FactsOut, the exported snapshot tree (what ship would commit, so gitignored secrets cannot be in it), units.json and facts.json.

## Notes

Split line: ~350 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
