---
id: T0-PREREVIEW-RECORDS
title: _prereview-records.ps1 record core - boolean validation, unit membership, C-{n} id minting, exact-duplicate rule, fingerprint hint and missing-coverage synthesis
status: todo
depends_on: [T0-PREREVIEW-SCHEMA]
allow_paths:
  - scripts/_prereview-records.ps1
  - scripts/fixtures/prereview/records/
  - specs/tasks/T0-PREREVIEW-RECORDS.md
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-records.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-RECORDS-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-RECORDS-SELFCHECK-PASS] after every violation class returns [PRE-BAD-RECORD] without a thrown error, unknown unit_id and unknown local_id references return [PRE-BAD-RECORD], local_id maps to C-{n} monotonically across two normalisations, exact duplicates merge while near duplicates keep one root_group, and a missing row is synthesised for a unit whose coverage lacks C2.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK]
acceptance:
  - "A1 Test-PrereviewRecord validates a candidate, coverage, facts or units document against specs/prereview-record.schema.json by wrapping the $defs sub-shape in memory ({$ref: /$defs/{name}, $defs: ...}) and returns a boolean; every violation class in the fixture yields [PRE-BAD-RECORD] without a thrown error."
  - "A2 Unit membership: a candidate unit_ids[] entry or a coverage unit_id absent from units.json, and a coverage candidate_local_ids[] entry naming no record of the same batch, each yield [PRE-BAD-RECORD]."
  - "A3 Id minting maps local_id to C-{n} monotonically within one state's life across two normalisation calls, never reuses a number, and stamps fingerprint (file|category|symbol|contract_ref) as a grouping hint that is never used as identity."
  - "A4 Exact duplicates (same batch and same file|symbol|category|contract_ref|expected|actual after NFC and whitespace normalisation; the key carries no line or anchor, so the same statement about the same symbol at different lines merges) merge into one record that keeps every contributing worker_id and local_id in provenance and the sorted union of unit_ids, evidence_refs and evidence_needed, so no location is lost; near duplicates (same fingerprint, different key) are kept and share one root_group."
  - "A5 Missing-coverage synthesis adds a state-only missing row for every unit whose discoverer coverage lacks C1, C2 or C3, while a worker-emitted missing status or a worker-emitted id is rejected as [PRE-BAD-RECORD]."
  - "A6 -SelfCheck reads only its fixture folder and temp, clears PRE_LIVE and PRE_LENS_ENDPOINT inherited from the parent, spawns no process, and prints [PREREVIEW-RECORDS-SELFCHECK-PASS]."
forbid:
  - Any state shape, disposition or transition logic (STATE-1A and 1b STATE)
  - A prompt builder or fence helper here (PROMPT)
  - Throwing on invalid input instead of returning [PRE-BAD-RECORD]
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: One single-line-deletion mutant per violation class and per rule (unit membership, monotonic ids, duplicate merge, missing synthesis); each must turn -SelfCheck red.
---

# T0-PREREVIEW-RECORDS

## Context

Records core: the only place that validates worker records, checks unit membership, mints canonical ids, applies the exact-duplicate rule and synthesises missing coverage.

## Interfaces carried by this card

- Test-PrereviewRecord -Kind candidate|coverage|facts|units -Json string -> boolean (in-memory wrapper around the $defs sub-shape; Test-Json -ErrorAction SilentlyContinue).
- ConvertTo-PrereviewCandidates -Records -Units -NextId / ConvertTo-PrereviewCoverage -Records -Units -Candidates -DiscovererWorkerId: input is the adapter-stamped record list of one batch (WORKERS A3 stamps snapshot_tree, worker_id, model_id, lens, schema_version, schema_revision; the six keys are separated before schema validation, worker_id is required and local_id is worker-local); local_id to C-n minting (monotonic within one state's life: the returned NextId feeds the next call), fingerprint = file|category|symbol|contract_ref (grouping hint only), exact duplicate = same batch and same file|symbol|category|contract_ref|expected|actual after NFC and whitespace normalisation (line-free: locality is kept by the union of unit_ids and evidence_refs, decided on master before start per TD176), near duplicate = same fingerprint with a different key, root_group = the lowest id of the fingerprint group, missing rows for units whose discoverer coverage lacks C1, C2 or C3 (categories_checked only, whatever the status). Every function returns a result object with Ok, Code ('' or [PRE-BAD-RECORD]) and Reasons instead of throwing; a batch with any violation mints nothing.

## Notes

Split line: ~350 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.

## Implementation record (2026-09-15)

- Delivered `scripts/_prereview-records.ps1` (`-AsLibrary` / `-SelfCheck`, 70 assertions) and `scripts/fixtures/prereview/records/` (units.json, facts.json, valid/batch.jsonl with 11 stamped records from two workers, reject/ with 12 pinned violation classes). Input is the adapter-stamped JSONL of one batch; the six provenance keys are separated before the closed `$defs` validation, `worker_id` is the only one this core needs (local_id namespace, provenance list), the other five ride along verbatim.
- Identity is ordinal everywhere (`New-OrdinalMap`, ordinal HashSets, ordinal `OrderedDictionary` for the split content: `[ordered]@{}` is case-insensitive and would fold a worker's `Expected` into `expected`, so that class is a reject fixture). The exact-duplicate key and the `(worker_id, local_id)` / `(worker_id, unit_id)` keys are JSON-array encodings of their tuples, so a separator inside a value cannot merge two different tuples; the self-check proves it with `(x|y, z)` vs `(x, y|z)`. `fingerprint` keeps the card's `|` form as a grouping hint; `root_group` is the lowest id of its group.
- Both normalisers run the same whole-batch validation first, so any violation in either record kind mints nothing and produces no row (NextId untouched). The candidates result carries `BatchId` (SHA-256 of the serialised records) and coverage refuses any result not minted from its own batch, including a failed result (BatchId empty) and a rewritten batch whose map still resolves every local id; coverage also re-validates units on its own call. One coverage row per `(worker, unit)`; `[{...}]` lines are arrays, not records (`-NoEnumerate`). Merged scalars come from the first contributor (RUN passes the discoverer first); each contributor's own `kind`, `severity_guess`, `anchor`, `line_start`, `line_end` sit in its provenance entry, so the merge discards nothing. Missing rows come from the discoverer's `categories_checked` only: the fixture's lens row carrying C2 on U2 does not rescue it.
- Local pre-review before R4 (fresh-context `/code-review high` + a second pass): 7 findings, all fixed above (case-insensitive `[ordered]` content, unbound candidates result, units as PSCustomObject throwing, duplicate `(worker, unit)` rows, first-contributor scalars lost, 0-based record numbers, `[{...}]` unrolling).
- R4: 36/36 single-point mutants killed, each by a named behavioural FAIL line of `-SelfCheck` (the runner flags parse errors and unnamed exits as survivors; two earlier survivors, a redundant `Ok` guard and a crash-kill, were removed or retargeted). Script S01-S03 unit membership / local-id reference, S04 duplicate local_id, S05 worker_id, S06 unknown shape, S07 schema validation, S08 malformed line, S09/S10 monotonic NextId, S11 exact merge, S12 NFC, S13 whitespace, S14 union, S15 provenance, S16 fingerprint, S17 root_group, S18 related_to, S19 missing synthesis, S20 discoverer-only, S21 candidate_ids, S23/S24 fail-closed batch in each normaliser, S25 ordinal maps, S26/S27 tuple keys, S28 env clearing, S29 units schema, S30 ordinal content, S31 BatchId binding, S32 units dictionaries, S33 one row per (worker, unit), S34 `-NoEnumerate`, S35 contributor scalars; fixture F01 reject made clean, F02 lens l1 no longer a duplicate. SHA-256 of the final script bytes: 4f6e424a8d9d26f98e31b0e332f417e3d39e4832627d847831a6a92a0f3e1636.
