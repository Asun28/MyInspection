---
id: T0-PREREVIEW-UNIT-ID-REVISION
title: Frozen schema revision 1 - hunk ordinal identities and coordinated fixture migration (cross-surface contract change)
status: merged
depends_on: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROTOCOL-DOC]
allow_paths:
  - specs/prereview-record.schema.json
  - scripts/check-prereview-schema.ps1
  - scripts/fixtures/prereview/schema/
  - scripts/_prereview-records.ps1
  - scripts/fixtures/prereview/records/
  - docs/PREREVIEW-PROTOCOL.md
  - specs/tasks/T0-PREREVIEW-UNIT-ID-REVISION.md
dod_command: $s = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $s.Contains('[PREREVIEW-SCHEMA-OK]') -or -not $s.Contains('[PREREVIEW-UNIT-ID-REVISION-PASS]')) { exit 1 }; $r = (& pwsh -NoProfile -File scripts/_prereview-records.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $r.Contains('[PREREVIEW-RECORDS-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: The schema checker and RECORDS self-check both pass; revision 1 envelopes are accepted and revision 0 envelopes rejected by both the source schema and projection; hunk IDs with ordinals 1 and 2 validate, missing/zero/negative/non-integer ordinals fail, file IDs remain valid; the existing RECORDS duplicate-unit and unknown-unit membership checks retain their original failure causes.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
acceptance:
  - "A1 This card is the user-approved version review of specs/prereview-record.schema.json: schema_version remains 1, schema_revision becomes const 1, and hunk unit_id becomes {path}#{body_sha256[0:12]}-{ordinal}, where ordinal is the positive 1-based hunk order in that file in the supplied diff; {path}#file is unchanged. The unit_id and body_sha256 descriptions and protocol table use this definition."
  - "A2 Existing source-schema samples, worker-envelope projection and mini samples migrate to revision 1, preserving all existing rejection classes. The existing wrong-revision sample uses revision 0. No second $defs source is introduced."
  - "A3 The existing schema checker adds in-memory behavior cases for two distinct hunk IDs with identical body_sha256, missing/zero/negative/non-integer ordinals, file IDs, and revision 0/1 envelopes against both source and projection. It emits [PREREVIEW-UNIT-ID-REVISION-PASS] only when these cases pass."
  - "A4 RECORDS fixtures and self-check literals use revised hunk IDs and revision 1 provenance. No production RECORDS algorithm changes. Duplicate-unit and unknown-unit membership regressions separately assert their own reason, using otherwise schema-valid inputs."
forbid:
  - Changing scripts/_config.ps1, scripts/task.ps1, scripts/review.ps1 or scripts/selftest.ps1
  - Changing the RECORDS production algorithm, relaxing any existing rejection class, or accepting revision 0 envelopes
non_goals:
  - FACTS-LIB production implementation, packet generation, worker execution, Phase 1b, or origin/master reconciliation
hygiene: Mutate the ordinal pattern to omit/permit an invalid ordinal, remove the file alternative, restore revision 0 in either source or projection, remove duplicate-unit detection or unknown-unit membership detection; each must fail its targeted behavioral assertion.
---

# T0-PREREVIEW-UNIT-ID-REVISION

User approved the preflight recommendation on 2026-09-16. TD176 identified that two byte-identical hunk bodies in one file have the same old identity. Frozen schema revision 0 prevents correcting this solely in FACTS-LIB A5; this prerequisite revises the contract and immediate consumers first.

FACTS-LIB owns deterministic ordinal minting, not this card. The schema checks ID shape; RECORDS checks unit uniqueness and membership. The projection remains a schema projection with its existing keyword restrictions; WORKERS owns its projection-fidelity checks.

Planned diff budget: under 700 changed lines / 50000 characters including fixtures and receipt; measure with review.ps1 before invoking R3. Local ship targets master; no remote reconciliation.

## Implementation evidence

Local ship merged `71895645` (reviewed tip `269d5268`): first formal R3 round passed with zero findings; DoD, verify, scope, license, secrets and hard diff budget passed. Routed selftest from this worktree passed all three shards (seeded, workflow, core; aggregate exit 0). Normal cleanup passed and removed the worktree and branch with the matching merge token; review evidence was preserved under the session's local evidence directory.

RED recorded at `1220f108`: new hunk IDs and revision 1 envelopes failed against revision 0; missing ordinals and revision 0 envelopes incorrectly passed. GREEN: schema checker (including projection samples and revision cases) and RECORDS self-check pass. Fixture migration preserves the eleven original JSON rejection classes; unknown-unit checks now require their specific membership reason. Actual diff before this receipt: 128 changed lines / 53035 characters, above the planning estimate but below the 1000 / 60000 hard limit.

R4: 10/10 mutants killed by their named behavior assertion, with byte-identical restoration: legacy ID pattern; zero, negative and fractional ordinal acceptance; removal of file IDs; source revision 0; projection revision 0; removed duplicate-unit detection; removed candidate membership; removed coverage membership. No compile/parse failures count as kills. Schema-pattern/revision mutants fail the specific unit or envelope case; RECORDS mutants fail duplicate-unit or the exact membership-reason check.

SHA-256 at mutation time (uppercase hex):

| File | SHA-256 |
|---|---|
| `specs/prereview-record.schema.json` | `2F662F50303C5372FE85E3664FAA38F870FAF335F7C1AA1B414F77BBC511308A` |
| `scripts/fixtures/prereview/schema/worker-envelope.min.json` | `C9242D7C4CD0616E19498C38C2AF5EEB6C8B97FD1FA0197C6E228F909248C414` |
| `scripts/check-prereview-schema.ps1` | `2F45125C8AB5765CF766CACD579ABA73F3CAA0EEA057CAC53111B6DB1A6D25E6` |
| `scripts/_prereview-records.ps1` | `1DDCECAE82CAF9B56F1E1A7FB3EBD23D4B774FA291FD27B4B5B26EC3E5CE5339` |
