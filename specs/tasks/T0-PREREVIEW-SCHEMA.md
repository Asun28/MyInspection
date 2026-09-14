---
id: T0-PREREVIEW-SCHEMA
title: Frozen record schema, worker-envelope projection, check-prereview-schema.ps1 and the Prereview* config knobs
status: todo
depends_on: []
allow_paths:
  - specs/prereview-record.schema.json
  - scripts/check-prereview-schema.ps1
  - scripts/fixtures/prereview/schema/
  - scripts/_config.ps1
  - specs/tasks/T0-PREREVIEW-SCHEMA.md
dod_command: $t = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-SCHEMA-OK]')) { exit 1 }; $c = (Get-Content scripts/_config.ps1 | Where-Object { $_ -notmatch '^\s*#' }) -join ' '; if (-not ($c.Contains('PrereviewEnabled = $true') -and $c.Contains('PrereviewGateEnforced = $false') -and $c.Contains('PrereviewBatchCap = 2'))) { exit 1 }
dod_exit: 0
dod_assert: Default mode exits 0 and prints [PREREVIEW-SCHEMA-OK] after the record schema passes hygiene and the contract walk, every records/valid sample validates (facts.*.json and units.*.json against their $defs), every records/reject sample of the eleven Test-Json classes declared in the script inventory (verdict field, status pass, unknown field, missing schema_version, wrong schema_revision, worker-emitted id, worker-emitted missing, provenance inside records[], blocked without missing_context, symbol null without line_start, facts base_mode outside the enum) returns Test-Json $false under -ErrorAction SilentlyContinue with the inventory equal to the directory, every records/reject/*.schema.json (cross-file $ref; one hidden under patternProperties; one not exactly #/$defs/{name}) fails -Schema, -Anchors on anchors/ok.md passes while missing-row, extra-code and duplicate-row each fail, -Schema worker-envelope.min.json -Samples mini/ passes; the non-comment lines of scripts/_config.ps1 contain PrereviewEnabled = $true, PrereviewGateEnforced = $false and PrereviewBatchCap = 2.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS]
acceptance:
  - "A1 specs/prereview-record.schema.json is one self-contained file (only same-document /$defs references) whose worker_output envelope {schema_version, schema_revision, records[]} and candidate, coverage, facts and units $defs use additionalProperties false, lower_snake enum values, and a closed status_code enum of exactly the 20 codes of plan §6.8."
  - "A2 No schema path admits a field named verdict, a status value pass or block, or a worker-emitted id, missing status or provenance field inside records[]; records/reject holds one sample per class plus one schema with a cross-file $ref that -Schema mode rejects with [PREREVIEW-SCHEMA-FAIL]."
  - "A3 scripts/fixtures/prereview/schema/worker-envelope.min.json is the model-facing projection on one line of at most 4096 bytes: content fields only, no provenance, no numeric constraints, and only the keywords type, properties, required, additionalProperties, enum, const, items, anyOf, $ref, $defs, description."
  - "A4 scripts/check-prereview-schema.ps1 offers -Schema {file} -Samples {dir} (valid/ must pass, reject/ must fail) and -Anchors {doc} (prints [PREREVIEW-ANCHORS-OK]); its default mode self-exercises both on scripts/fixtures/prereview/schema/{records,anchors,mini} and prints [PREREVIEW-SCHEMA-OK] or [PREREVIEW-SCHEMA-FAIL]."
  - "A5 -Anchors reads the first table under the heading Status codes, takes the first [PRE-...] token of column 1 as the code, and fails on a missing enum row, a code outside the enum, or a duplicate row, proven by anchors/missing-row.md, extra-code.md and duplicate-row.md against anchors/ok.md."
  - "A6 The only Test-Json claim made anywhere in the card is that under -ErrorAction SilentlyContinue every violation class (including one additionalProperties sample and one if/then sample) returns $false without throwing."
  - "A7 scripts/_config.ps1 gains the Prereview* knobs with the plan §6.1 defaults as values only (PrereviewEnabled $true, PrereviewGateEnforced $false, PrereviewDiscoverCommand and PrereviewLensCommand empty, PrereviewModel claude-sonnet-5, PrereviewRiskyModel claude-opus-5, PrereviewEffort high, PrereviewLensEnabled $true, PrereviewLensModel deepseek-v4-flash, PrereviewLensDocModel deepseek-v4-pro, PrereviewLensEffort high, PrereviewBatchCap 2, PrereviewTimeoutSec 900, PrereviewMaxFileBytes 200000, PrereviewMaxDocBytes 1000000, PrereviewMaxPackBytes 20971520) and PrereviewRiskyExtraPaths holds only the extra prefixes scripts/, .claude/, .github/, configs/compliance/ (drop configs/compliance/ if FrozenPaths already lists it at RED)."
forbid:
  - Any cross-file or URL $ref in any schema file, or copying $defs into a second file
  - Implementing the four projection checks here (they are WORKERS' -SelfCheck)
  - Editing FrozenPaths or DocSyncMap (LOOP-DOCS, 1b) or any file outside allow_paths
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: One single-line-deletion mutant in the check script per reject class and per anchors failure mode (missing-row, extra-code, duplicate-row); each must turn the default mode red; a mutant that adds a 21st code to the enum must turn anchors/ok.md red.
---

# T0-PREREVIEW-SCHEMA

## Contract carried by this card (the plan is gitignored; this body is the source for implementers)

### Envelope (what a worker returns; one JSON object)

- worker_output: schema_version (1), schema_revision (integer, bumped by any in-window patch), records[] (candidate and coverage records only).

### candidate

| column | fields |
|---|---|
| model-authored | local_id, kind (defect/question/suggestion), severity_guess (critical/high/medium/low/null), category (C1/C2/C3/C4/C5/C7), anchor (hunk/file/absent), file (any path of the snapshot tree), symbol (non-empty, or null with line_start required), line_start, line_end, unit_ids[] (may be empty; membership checked by RECORDS), trigger, expected, actual, impact, contract_ref (lesson:L[0-9]+ or rubric:[0-9]+ or acceptance:[AR][0-9]+ or frozen:path or none), evidence_refs[], evidence_needed[], introduced_or_worsened (introduced/worsened/pre_existing), suggested_fix_direction |
| adapter-stamped | snapshot_tree, worker_id, model_id, lens |
| core-added (RECORDS) | id (C-[0-9]+), fingerprint, root_group, related_to[] |

### coverage (one per changed unit)

- unit_id (must exist in units.json), categories_checked[] (subset of C1/C2/C3/C4/C5/C7), status (checked_no_finding/finding/not_applicable/blocked), candidate_local_ids[], missing_context[] (non-empty when blocked). The status value missing is state-only (synthesised by RECORDS) and is rejected when a worker emits it.

### facts (facts.json) and units (units.json)

- facts: snapshot_tree, head_sha, base_oid, base_mode (local/remote), merge_base, policy_hash (SHA-256 over docs/PREREVIEW-CHECKLISTS.md plus specs/prereview-record.schema.json at the merge-base commit), rubric_sha, models, risk_class, pack_layout_version.
- units: array of {unit_id (path plus hash-sign plus first 12 hex of sha256 of the normalised hunk body; binary or over-size files degrade to path plus hash-sign plus file), file, hunk_header, body_sha256}.

### Forbidden by schema (reject samples)

- any field named verdict; any status value pass or block; a worker-emitted id, missing status or provenance field inside records[]; unknown fields anywhere (additionalProperties false); lower_snake enum values only.

### Status codes (20; enum in the schema; the protocol doc's table is anchor-tested against it)

| code | owner | phase | meaning | counts as |
|---|---|---|---|---|
| [PRE-NO-MERGE-BASE] | FACTPACK | 1a | base and HEAD share no merge-base | stop, no worker |
| [PRE-SECRETS] | FACTPACK-SLICES | 1a | pack-wide secret scan hit | stop, no worker |
| [PRE-NO-NETWORK-IN-CI] | RUN | 1a | CI or GITHUB_ACTIONS set and a built-in adapter would be needed | that worker not spawned (discoverer incomplete, lens skipped) |
| [PRE-LIVE-REFUSED] | runner/adapters | 1a | built-in command requested without PRE_LIVE=1 | discoverer incomplete, lens skipped |
| [PRE-WORKER-MISSING] | runner/adapters | 1a | configured CLI or key not found | discoverer incomplete, lens skipped |
| [PRE-NO-OUTPUT] | runner/adapters | 1a | worker wrote nothing | discoverer incomplete, lens skipped |
| [PRE-TIMEOUT] | runner | 1a | process tree killed at PRE_TIMEOUT_SEC, exit 124 | discoverer incomplete, lens skipped |
| [PRE-BAD-RECORD] | RECORDS | 1a | a record failed validation or references an unknown unit or id | discoverer incomplete, lens skipped |
| [PRE-LENS-SKIPPED] | WORKERS | 1a | lens disabled | lens skipped |
| [PRE-PACKET-READY] | RUN | 1a | state and packet written | informational |
| [PRE-RUN-DISABLED] | RUN | 1a | PrereviewEnabled=false; nothing spawned | stop (kill switch) |
| [PRE-BUDGET-AFTER-FIX] | RUN (1b BATCH) | 1b | review.ps1 -SizeOnly -Tree over budget on the new snapshot | stop; split before any push |
| [PRE-BATCH-CAP] | RUN (1b BATCH) | 1b | the (cap+1)-th run since the last merge | stop; user |
| [PRE-MISSING] | ship gate | 1b | no state for the task | ship refused |
| [PRE-STALE] | ship gate | 1b | tree, policy or base_mode mismatch | ship refused |
| [PRE-OPEN] | ship gate | 1b | a candidate without disposition | ship refused |
| [PRE-INCOMPLETE] | ship gate | 1b | review_status incomplete (worker, units, needs_human) | ship refused |
| [PRE-SKIPPED] | ship gate | 1b | -SkipPrereview | ship continues; ledger |
| [PRE-DISABLED] | ship gate | 1b | PrereviewGateEnforced=false | ship continues; ledger |
| [PRE-GATE-PASS] | ship gate | 1b | fresh, dispositioned, covered | ship continues; ledger |

### Knobs written into scripts/_config.ps1 (values only)

- PrereviewEnabled $true, PrereviewGateEnforced $false, PrereviewDiscoverCommand '', PrereviewLensCommand '', PrereviewModel claude-sonnet-5, PrereviewRiskyModel claude-opus-5, PrereviewRiskyExtraPaths (scripts/, .claude/, .github/, configs/compliance/ unless FrozenPaths already lists it at RED), PrereviewEffort high, PrereviewLensEnabled $true, PrereviewLensModel deepseek-v4-flash, PrereviewLensDocModel deepseek-v4-pro, PrereviewLensEffort high, PrereviewBatchCap 2, PrereviewTimeoutSec 900, PrereviewMaxFileBytes 200000, PrereviewMaxDocBytes 1000000, PrereviewMaxPackBytes 20971520.

## Implementation record (2026-09-15)

- Gaps the plan left, closed in the schema and pinned by the default mode: `base_mode` = `local` | `remote` (FACTS-LIB A2 still says `origin`; align that one word on master before it starts); `risk_class` = `standard` | `risky`; `models` = `{discoverer: {model, effort}, lens: {enabled, model, doc_model, effort}}`; `pack_layout_version` integer >= 1; `unit.hunk_header` null for a `path#file` unit; `severity_guess` has `null` as an enum member; `schema_revision` is `const 0` so a stale fixture really fails; the closed object behind A1's "units" is `$defs/unit` (`$defs/units` is its array). Enum values are lower_snake except the identifier enums `category` and `status_code`; the contract walk admits exactly those two shapes and compares case-sensitively.
- Projection rule (3791 bytes, one line, UTF-8 without BOM, no trailing newline): keep the root and the `$defs` reachable from it, keep only the A3 keywords, drop the rest (`$schema`, `title`, `$comment`, `pattern`, `minLength`, `minimum`, `minItems`, `uniqueItems`, `if`, `then`). Re-derive after any in-window patch.
- Samples: `<def>.<name>.json` validates against `$defs/<def>` through an in-memory `{$ref, $defs}` wrapper, anything else against the whole schema; `*.schema.json` is a schema for -Schema, never a sample. `$ref` hygiene scans every JSON node and accepts exactly `#/$defs/<name>` with an existing target; the exactness and no-target checks overlap on every fixture the forbid allows (a fixture only the first catches needs a copied `$defs`), so that class is pinned by its inventory line and F01, not by a check-deletion mutant.
- Size at ship: about 650 changed lines / 58K diff chars. The Notes' split rule was measured too late (L266); the task owner withdrew it for this card (amendment at the end of Notes) because moving -Anchors out would not reach 500 lines and would orphan PROTOCOL-DOC's DoD; the budget was met by trimming.
- R4: 37/37 single-point mutants killed by a behavioural failure line, every script mutant still parsing. Script S01-S11, S18-S20 one inventory line each (inventory drift); S12/S13/S14 duplicate, extra-code, missing-row check (that negative doc passes); S15 `$expect = $true`; S16 def routing off. Fixture F01 cross-file-ref.schema.json made clean (it passes). Schema M01/M02 `additionalProperties` off candidate/coverage; M03 `missing`; M04 `pass`; M05/M06 an `if` removed; M07 required; M08 21st status code (anchors/ok.md red); M09 `origin`; M10 `verdict` property in facts; M11 `Risky`; M12 cross-file `$ref`; M13 `const: 2`; M14 `Medium`; M15 `schema_revision` unpinned (wrong-revision sample validates). Config C01 `PrereviewGateEnforced = $true`, C02 `PrereviewBatchCap = 3`. SHA-256 of the final bytes: schema d784fc5233b2a3191ceb9cdadc2d38090f7c6a09aadee15c02b93016e4dbd7e6, script ab638f0074fbddc707359724e92a8ff200a4702abc7b00d5b7cbb5044311ae3a, _config 4ba25af9ce60427ef7806c619c6f896f4c09e2d5c7f6c3d67ccd68849bc240d4, cross-file fixture 6a1d872f1a8bdd3606622e9701e70a744f0bf95fb7750b1752f127a46ba451bd.
- `-Anchors specs/tasks/T0-PREREVIEW-SCHEMA.md` (the table below) prints [PREREVIEW-ANCHORS-OK]. Eight in-flight branches modify scripts/_config.ps1 (collision rule); this card appends one block and keeps the BOM; retiring them stays a user decision.
## Notes

Freeze point: the record schema is the contract from this merge; in-window patches bump schema_revision and go through a follow-up card on these files only; the mechanical FrozenPaths registration is done by the 1b LOOP-DOCS card after live use. The -Schema/-Samples mode prints [PREREVIEW-SCHEMA-OK] or [PREREVIEW-SCHEMA-FAIL] like the default mode (the 1b ledger-schema card relies on it). Split line: ~450 lines; over 500 before RED means the -Anchors mode moves to a follow-up card. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
