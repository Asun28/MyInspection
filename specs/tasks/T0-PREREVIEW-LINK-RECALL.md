---
id: T0-PREREVIEW-LINK-RECALL
title: prereview.ps1 link-r3 and recall - link R3 block reasons to packet candidates and compute pooled and per-worker recall for the 1a checkpoint
status: todo
depends_on: [T0-PREREVIEW-RUN]
allow_paths:
  - scripts/prereview.ps1
  - scripts/fixtures/prereview/link-recall/
  - specs/tasks/T0-PREREVIEW-LINK-RECALL.md
dod_command: pwsh -NoProfile -File scripts/prereview.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; $t = (& pwsh -NoProfile -File scripts/prereview.ps1 -SelfCheck -Fixture link-recall *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-LINK-RECALL-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck -Fixture link-recall exits 0 and prints [PREREVIEW-LINK-RECALL-SELFCHECK-PASS] after link-r3 links reasons from a fixture verdict file, rejects an unknown candidate id and an out-of-range reason index without writing, records reason_sha256 per dispute, and recall -Out {temp} reproduces the golden pooled and per-worker numbers without writing _local/; the plain RUN -SelfCheck stays green.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
acceptance:
  - "A1 link-r3 -TaskId {id} -Round {n} reads the current verdict file .review/{branch}.json of the task's worktree (1a: no per-round archive), lists reasons[i] with their sha256, and records one dispute per reason with relation in same|related|new and candidate_id (null for new) through Add-PrereviewDispute; an unknown candidate id or an out-of-range reason index is rejected without writing."
  - "A2 Each dispute carries r3_round, r3_sha (the verdict's sha), reason_index, reason_sha256, by and at; linking the same round twice replaces that round's disputes instead of duplicating them, and the state still validates against state.schema.json."
  - "A3 recall -Out {path} reads every state under the common-dir plane, computes per card linked reasons with relation same or related over all linked reasons, pools over cards, reports per worker (a reason linked to candidates from both workers counts for both; unique marks reasons only one worker had), and writes one JSON snapshot to -Out."
  - "A4 The fixture reproduces a golden pooled number and golden per-worker numbers from fixture states plus a fixture verdict file, and the self-check writes only to a temp path, never to _local/review-metrics/."
  - "A5 -SelfCheck -Fixture link-recall clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns no worker, and prints [PREREVIEW-LINK-RECALL-SELFCHECK-PASS]; the plain -SelfCheck of RUN stays green."
  - "A6 The card body states the 1a checkpoint rule: at least 5 product cards run the packet before their first ship and link-r3 after every block; pooled recall of at least 50 percent (relation same or related) opens 1b, otherwise the packet stays advisory or is retired; packet wall time P50 and [PRE-BAD-RECORD] counts are recorded alongside."
forbid:
  - Reading a per-round archive (1b ROUND-ACCOUNTING) or writing dispositions
  - Any change to state.schema.json or the record schema
  - Writing _local/ from -SelfCheck
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop the unknown-id rejection => case red; count relation new as matched => golden pooled number red; drop the per-worker double count => per-worker golden red; drop the same-round replacement => duplicate-dispute case red.
doc_sync: After merge: TASK-BOARD status for the 1a checkpoint (recall snapshots under _local/review-metrics/), and the scaffold-sync report per docs/SCAFFOLD-SYNC.md once the checkpoint has numbers.
---

# T0-PREREVIEW-LINK-RECALL

## Context

Second writer of scripts/prereview.ps1. Adds link-r3 (link every R3 block reason to a packet candidate right after the block, before the next round overwrites the verdict file) and recall (pooled and per-worker recall over the state plane).

## The Phase-1a checkpoint

- At least 5 product cards run the packet before their first ship and link-r3 after every block; pooled recall (relation same or related) of at least 50 percent opens Phase 1b; below that the packet stays advisory or is retired. Snapshots go to _local/review-metrics/recall-{date}.json via recall -Out.

## Notes

Split line: ~250 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
