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
  - "A6 -SelfCheck reads only the frozen schema (the A1 contract), its fixture folder and temp, clears PRE_LIVE and PRE_LENS_ENDPOINT inherited from the parent, spawns no process, and prints [PREREVIEW-RECORDS-SELFCHECK-PASS]."
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

- `scripts/_prereview-records.ps1` (`-AsLibrary` / `-SelfCheck`, 67 assertions; the header comment states the rules) + `scripts/fixtures/prereview/records/` (four units, 11 stamped records from two workers, 7 reject files, 5 reject cases derived in memory). Identity is ordinal in library and harness (`[string]::Equals(..., Ordinal)`, ordinal sets; PowerShell's comparison operators and `Select-Object -Unique` are culture comparisons that equate soft hyphens, zero-width characters and NFC/NFD variants); tuple keys are JSON-array encodings; `[long]` counter, exhaustion pre-checked on unique keys; bad caller input is a failed result or `$false`, never a throw; coverage re-mints from `StartId` and requires the whole result object (seven properties, canonical JSON, exact property-name set) to match; state shapes are pinned key by key; `-SelfCheck` dot-sources nothing.
- R3 rounds 1-6 (two user-ruled `ResetRounds`; 13 findings, every one accepted and fixed): binding dereference and altered / coordinated / padded / status-only forgeries (now recomputation), `[int]` counter and exhaustion on raw records, `-SelfCheck` dot-sourcing, untested unions, missing-row cases per category, culture `Sort-Object` and `#requires` floor, throwing on bad NextId, culture operators in the harness. Two fresh-context sweeps between rounds closed ten more (culture `-ceq`, unrolling, extra properties, array map values, bad-Kind / bad-Path / null throws, unpinned shapes, inventory filter, a dead guard).
- R4: 58/58 single-point mutants killed by a named behavioural FAIL line (parse errors and unnamed exits count as survivors; redundant guards S22 / S50 and an unreachable branch were deleted; S28 plants PRE_LIVE / PRE_LENS_ENDPOINT before spawning). S01-S08 reject classes; S09/S10 NextId; S11-S13 merge key; S14/S15 union, provenance; S16-S18 fingerprint, root_group, related_to; S19/S20 missing; S21 candidate_ids; S23/S24 fail-closed; S25-S27 ordinal maps, tuple keys; S28 env; S29 units schema; S30 ordinal content; S31 binding; S32 units dictionaries; S33 one row per (worker, unit); S34 `-NoEnumerate`; S35 contributor scalars; S36 key covers `actual`; S37 missing without rows; S38 sort; S39 long; S40/S41/S46 recompute equality; S42 duplicate unit_id; S43/S44/S52 exhaustion, unique keys, invalid NextId; S45 key sort; S47 ordinal equality; S48 no unrolling; S49 property names; S51 ordinal discoverer; S53 provenance anchor; S54 `-DiscovererWorkerId`; S55 categories_checked; S56 only local_id dropped; S57/S58 C1 and C3 required; F01/F02 fixtures. Final script SHA-256: 7f75c86d21284291fc7d7d91e7ba7c947353b0ef2545397620abb2c400ffbf3a.
