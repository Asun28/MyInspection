---
id: T0-PREREVIEW-FACTS
title: review.ps1 -FactsOut and -SizeOnly -Tree read-only modes exporting reviewer-identical facts with the pinned base ref and OID
status: todo
depends_on: [T0-PREREVIEW-FACTS-EXTRACT]
allow_paths:
  - scripts/review.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
  - scripts/fixtures/prereview/facts/
  - specs/tasks/T0-PREREVIEW-FACTS.md
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git -Fixture review-facts *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[SELFTEST-FIXTURE] review-facts PASS')) { exit 1 }
dod_exit: 0
dod_assert: The seeded-git shard stays green through 17gg and 17a..17y, then the review-facts fixture (after review-runner and review-extract, before the seeded:17-git-main record) early-exits with [SELFTEST-FIXTURE] review-facts PASS after proving: two prompts for one tree are byte-equal after nonce normalisation with six fence lines each and different nonces; every -FactsOut file byte-equals the live value and is fence-hardened; the exported base ref and OID equal what the live path pinned, with and without -LocalBase; -Tree diffs against the merge-base; -SizeOnly -Tree counts uncommitted lines; .rounds untouched; no backend invoked; -FactsOut with -SizeOnly or -ResetRounds prints [R3-DIFF-ARGS-INVALID].
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 review.ps1 -FactsOut {dir} writes one file per fact (diff.patch, stat.txt, numstat.txt, card.md, rubric.md, the FrozenPaths clause, base.json) from the same value functions the live prompt uses, after Protect-FenceMarkers, and never invokes a backend, consumes a round or touches .rounds."
  - "A2 base.json carries the pinned base ref and OID exactly as the live path resolved them through Resolve-ScaffoldBaseRef -PreferLocal:$LocalBase; -Base still takes a branch name and no fully qualified ref form is added (scripts/_gitbase.ps1 untouched)."
  - "A3 With -Tree {oid} the diff, stat and numstat are computed as git diff merge-base(base, HEAD) {tree}, so a snapshot tree holding uncommitted changes is what gets measured; -SizeOnly -Tree measures that tree against the same 1000 changed-lines and 60000 diffChars thresholds."
  - "A4 -FactsOut combined with -SizeOnly or -ResetRounds exits non-zero with [R3-DIFF-ARGS-INVALID] before any git call, and that code keeps its QUALITY-RUBRIC §5 row so 17t(doc) stays green."
  - "A5 The fixture review-facts (ValidateSet entry; body after review-extract and before the seeded:17-git-main record; early exit) proves two prompts for one tree are byte-equal after normalising DATA-[0-9a-f]{12}, each has exactly six fence lines with one nonce, and the nonces differ."
  - "A6 Every -FactsOut file is asserted byte-equal to the value the live path computed for the same tree, the base ref and OID export is asserted with and without -LocalBase, and a dirty tree passed as -Tree is reported by -SizeOnly -Tree with its uncommitted lines counted."
  - "A7 docs/QUALITY-RUBRIC.md gains one pointer paragraph for -FactsOut and -SizeOnly -Tree (DocSyncMap 14f); no other document changes."
  - "A8 -FactsOut and -SizeOnly -Tree return before review.ps1 creates the worktree .review directory: git status --porcelain --ignored of the reviewed worktree is byte-identical before and after either mode, and the fixture asserts it."
forbid:
  - A second base-resolution implementation or a fully qualified -Base form (TD68; _gitbase.ps1 is not edited)
  - Changing the live prompt bytes modulo nonce or any [R3-...] emission text
  - A nonce override parameter
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop Protect-FenceMarkers on one -FactsOut file => byte-equality red; diff against HEAD instead of the merge-base under -Tree => diff case red; drop the -FactsOut/-SizeOnly conflict guard => [R3-DIFF-ARGS-INVALID] case red; export the unpinned base name => base.json case red.
doc_sync: docs/QUALITY-RUBRIC.md pointer paragraph in the same commit (DocSyncMap 14f).
---

# T0-PREREVIEW-FACTS

## Context

Third card of the review.ps1 chain. It adds the read-only fact export that the pack builder (T0-PREREVIEW-FACTPACK) consumes, so the packet and the codex reviewer see identical, fence-hardened facts from one assembly function.

## Interfaces carried by this card

- review.ps1 -WorktreePath -Base name [-LocalBase] -FactsOut dir [-Tree oid]: writes diff.patch, stat.txt, numstat.txt, card.md, rubric.md, frozen.txt, base.json (pinned base ref and OID) from the live value functions; no backend, no .rounds, no .review directory.
- review.ps1 -SizeOnly -Tree oid: measures git diff merge-base(base, HEAD) tree against the 1000-line and 60000-char thresholds.

## Notes

Split line: ~350 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
