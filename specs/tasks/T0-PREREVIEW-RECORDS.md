---
id: T0-PREREVIEW-RECORDS
title: Add prereview record validation, membership, normalization, ID minting, duplicate merging, and missing coverage synthesis
status: todo
branch: T0-PREREVIEW-RECORDS
worktree: C:\wt\T0-PREREVIEW-RECORDS
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA]
allow_paths:
  - scripts/_prereview-records.ps1
  - scripts/fixtures/prereview/records/
  - specs/tasks/T0-PREREVIEW-RECORDS.md
budget: 999
doc_sync: After merge, update this card's status and record its PR and R3 receipt in this card during R5; archive through the normal R5 path.
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-records.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-RECORDS-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck prints [PREREVIEW-RECORDS-SELFCHECK-PASS] after rejected records return [PRE-BAD-RECORD], normalisation mints IDs and merges exact duplicates correctly, and missing coverage is synthesized.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 Test-PrereviewRecord validates candidate, coverage, facts, and units documents against specs/prereview-record.schema.json by wrapping the $defs sub-shape in memory, returns a boolean, and makes each fixture violation return [PRE-BAD-RECORD] without throwing."
  - "A2 A candidate unit_ids[] or coverage unit_id absent from units.json, and a coverage candidate_local_ids[] naming no record from its batch, each return [PRE-BAD-RECORD]."
  - "A3 Normalization maps local_id to monotonically increasing C-{n} values across two calls during one state lifetime, never reuses a number, and sets fingerprint file|category|symbol|contract_ref solely as a grouping hint."
  - "A4 Exact duplicates in one batch share file|symbol|category|contract_ref|expected|actual after NFC and whitespace normalization, with line and anchor excluded from the key; they merge provenance and sorted unions of unit_ids, evidence_refs, and evidence_needed. Near duplicates retain separate records sharing root_group."
  - "A5 Missing-coverage synthesis adds a state-only missing row for each unit whose discoverer coverage lacks C1, C2, or C3. A worker-emitted missing status or id returns [PRE-BAD-RECORD]."
  - "A6 -SelfCheck reads only the published schema, its fixtures, and temp; clears inherited PRE_LIVE and PRE_LENS_ENDPOINT; spawns no process; and prints [PREREVIEW-RECORDS-SELFCHECK-PASS]."
forbid:
  - State shape, dispositions, transitions, review_status, packet rendering, or Phase-1b predicates
  - Prompt construction or worker execution
  - Throwing on invalid records rather than returning [PRE-BAD-RECORD]
non_goals:
  - Board updates; those are separate R5 work
  - Any capability beyond record normalization and coverage synthesis
hygiene: A single-line deletion mutant for each validation class and for membership, monotonic IDs, duplicate merge, and missing synthesis must turn -SelfCheck red. No `.psd1` mutation registry change is required.
---

# T0-PREREVIEW-RECORDS

## Scope

This card starts after `T0-PREREVIEW-REMOTE-SCHEMA` has actually merged and consumes that delivery's schema_version 1 / schema_revision 1 contract. It provides the shared record core used later by state handling. It does not republish the schema, configuration, policy documents, or facts library. Keep the implementation below 1,000 changed lines and 60,000 changed characters; the local source delivery was close to the character limit, so this card has no room for unrelated features.

## Source evidence

The archived local implementation used 67 self-check assertions, worker and coverage fixtures, and 58 behavioral mutants. Its final binding rule re-minted coverage from the result and compared the full canonical object, rather than only checking selected fields. That evidence describes the required behavior; no `.psd1` mutation registry was changed or is required here.
