---
id: T0-PREREVIEW-STATE-1A
title: _prereview-state.ps1 v1 - state schema, atomic state write, dispute append, 1a packet renderer and view writer under the git common-dir plane
status: merged
depends_on: [T0-PREREVIEW-RECORDS, T0-PREREVIEW-FACTS-LIB]
allow_paths:
  - scripts/_prereview-state.ps1
  - scripts/fixtures/prereview/state/
  - specs/tasks/T0-PREREVIEW-STATE-1A.md
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-state.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-STATE-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-STATE-SELFCHECK-PASS] after a v1 state validates against the single-file state.schema.json, the atomic write leaves no partial file on a simulated crash, disputes[] is present and empty on a run-written state and grows through Add-PrereviewDispute, the 1a packet lists candidates, coverage and missing units without emitting a gate verdict, preserves candidate prose containing pass or block, and the state validator rejects a verdict field and pass/block status values, and view files land under the common-dir plane and never inside the worktree.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK]
acceptance:
  - "A1 scripts/fixtures/prereview/state/state.schema.json is one self-contained file (no cross-file $ref) describing state_version 1: task_id, snapshot_tree, head_sha, base_oid, base_mode, merge_base, policy_hash, rubric_sha, workers[] (id, model, effort, lens, required, status in complete|incomplete|skipped, skip_code, duration_s, exit_code, usage), units[], candidates[], coverage[] (worker statuses plus missing), disputes[], stop_reason; no field named verdict and no status value pass or block."
  - "A2 Write-PrereviewState writes {git-common-dir}/scaffold-prereview/{id}/state.json by temp file plus atomic rename; a simulated crash between the temp write and the rename leaves the previous state.json intact and no partial file under that name."
  - "A3 A state written by the 1a path carries disputes[] present and empty; Add-PrereviewDispute appends {candidate_id or null, r3_round, r3_sha, reason_index, reason_sha256, relation in same|related|new, by, at} through the same atomic writer and is the entry LINK-RECALL calls."
  - "A4 Write-PrereviewPacket renders packet.md from the state listing candidates, coverage and missing units without emitting a gate verdict; model-authored prose, including the words pass and block, is preserved. The self-check rejects states containing a verdict field or a pass/block status value, and verifies that valid candidate prose containing those words survives rendering. Write-PrereviewView writes packet.md and workers/{worker}-{batch}.* next to state.json under the common-dir plane and refuses any target inside the reviewed worktree or its .review/."
  - "A5 Resolve-PrereviewStatePlane derives the plane from git rev-parse --git-common-dir of the main checkout, so deleting the worktree keeps the state; nothing under .review/{id}/prereview/ is ever created."
  - "A6 -SelfCheck runs on a temp repository and fixture states, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, invokes only Git commands needed for the temporary repository, starts no worker and makes no network request, and prints [PREREVIEW-STATE-SELFCHECK-PASS]."
forbid:
  - Dispositions, transitions, review_status fields or derivation, gate or batch predicates (1b STATE extends this file in place)
  - Writing views or logs into the worktree or .review/
  - A second state schema file or a copy of the record schema's $defs
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: rename replaced by a direct write => crash case red; drop the worktree-path refusal => view-location case red; drop disputes[] from the run-written state => schema case red; allow a verdict field or a pass/block status value => invalid-state cases red; remove candidate prose from the renderer => prose-preservation case red.
---

# T0-PREREVIEW-STATE-1A

## Context

Owns the 1a state document and its schema (never frozen), the atomic writer, the dispute append used by link-r3, the 1a packet renderer and the view writer. All of it lives under the git common dir, outside the reviewed worktree; this placement does not establish a sandbox guarantee that the reviewer cannot read the packet.

## State v1 fields

- state_version 1, task_id, snapshot_tree, head_sha, base_oid, base_mode, merge_base, policy_hash, rubric_sha, workers[] {id, model, effort, lens, required, status (complete/incomplete/skipped), skip_code, duration_s, exit_code, usage or null}, units[], candidates[] (with provenance and core fields, no disposition), coverage[] (worker statuses plus missing), disputes[] (empty on run), stop_reason.

## Notes

Split line: ~200 lines; the 1b STATE card extends this file in place. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.

## Acceptance revision (2026-09-17)

User approved repairing the card before execution: TD176 is resolved here by structural no-verdict/status assertions and preservation of legal finding prose; review_status remains 1b-only, as in the merged protocol. Common-dir placement is not a sandbox guarantee. A6 permits the Git processes required to exercise the real repository boundary while still forbidding workers and network access. The DoD command and all delivery gates are unchanged.

## Implementation evidence

Local merge `62ec5f3b`, reviewed tip `9346969d`: first formal R3 passed with zero findings. DoD, project verify, scope, license, secrets and hard diff budget passed. Reviewed diff: 547 added lines / 43649 characters, below 1000 / 60000. No new dependency, worker execution, frozen schema change or Phase-1b predicate was added.

RED at `01c7cc25` failed the valid-state assertion before implementation. Final GREEN has 86 checks: frozen RECORDS replay, worker/candidate/coverage consistency, malformed metadata, prose round-trip, real temporary Git common-dir resolution, interrupted atomic replacement, append preservation, path traversal and reparse rejection, and persistence after worktree removal. Windows junction cases ran; Unix symbolic-link behavior was not exercised on this host.

R4: 25/25 behavioral mutants failed at their named checks; parse failures and unrelated exceptions were not counted. The runner uses disposable copies and checks source hashes after completion. A redundant root-verdict assertion was removed only after closed-root and nested-verdict mutants remained detected. The task-id newline mutant first survived because a generic rejection assertion accepted a downstream exception; the assertion now requires parameter-binding rejection. Additional RED cases exposed permissive hash end anchors, now covered by hash-boundary mutants.

Final SHA-256: library `88E64050EF9322454304A5771D8199ED756D38A5A4A258E67AF7F97FAB14E67B`; schema `5FBA3B71A265D2E0F8A90267392267DE81A7C8B11D0FDD535B4B91CD2ABBFAD2`; selfcheck `099250665DF5E7053D482D05830EFB2B756C029239E96671008726582B38EDA2`. Local evidence is retained under `_local/task-loop-state-1a-20260917/`.
