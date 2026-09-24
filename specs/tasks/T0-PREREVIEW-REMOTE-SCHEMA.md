---
id: T0-PREREVIEW-REMOTE-SCHEMA
title: Adopt approved prereview schema revision 1 and configuration on remote master
status: todo
branch: T0-PREREVIEW-REMOTE-SCHEMA
worktree: C:\wt\T0-PREREVIEW-REMOTE-SCHEMA
depends_on: []
allow_paths:
  - specs/prereview-record.schema.json
  - scripts/check-prereview-schema.ps1
  - scripts/fixtures/prereview/schema/
  - scripts/_config.ps1
dod_command: $t = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-SCHEMA-OK]') -or -not $t.Contains('[PREREVIEW-UNIT-ID-REVISION-PASS]')) { exit 1 }; . ./scripts/_config.ps1; if ($ScaffoldConfig.PrereviewEnabled -ne $true -or $ScaffoldConfig.PrereviewGateEnforced -ne $false -or $ScaffoldConfig.PrereviewBatchCap -ne 2 -or $ScaffoldConfig.PrereviewMaxFileBytes -ne 200000) { exit 1 }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 The self-contained closed record schema and its valid/reject inventory retain all source contract cases, including forbidden verdict/provenance and malformed references. [schema checker default]"
  - "A2 Schema version 1 revision 1 accepts positive decimal hunk ordinals and file units; omitted, zero, negative, fractional and leading-zero ordinals fail. Equal bodies may have distinct ordinal IDs. Revision 0 envelopes fail in both source and projection. [revision cases]"
  - "A3 Anchor checking accepts exactly the status-code inventory and rejects missing, extra and duplicate rows; mini samples validate against the existing projection. [anchors and mini modes]"
  - "A4 The approved Prereview defaults are adopted as values only, including Enabled true, GateEnforced false, BatchCap 2 and MaxFileBytes 200000. Existing FrozenPaths, delivery harness and gates remain unchanged. [config arm and source comparison]"
budget: 760
non_goals: [RECORDS implementation, worker or runner activation, Phase 1b integration]
hygiene: Preserve source bytes except the explicit PR 302 review corrections below; retain historical mutation evidence and rerun all behavioral schema cases on the repaired remote candidate.
---

# T0-PREREVIEW-REMOTE-SCHEMA

Remote adoption of the already approved final local artifact; no original local card is relabelled as remotely merged. Source and excluded consumers: docs/plans/PREREVIEW-REMOTE-ADOPTION.md.

The original tests and mutation receipts are historical evidence only. Run the DoD and the current remote delivery gates on this candidate. R5 records the new PR/review/CI identity and archives this adoption card.

## Initial candidate evidence (historical)

Candidate `13f2cbfb516f54523bec42f90a00b5e86dbb9da1` is clean and unchanged after its checks. The default schema checker passed both `[PREREVIEW-SCHEMA-OK]` and `[PREREVIEW-UNIT-ID-REVISION-PASS]`; the config DoD arm passed. An independent byte comparison matched all 26 imported schema/checker/fixture files to local source tree `4c9735ef`. All 17 Prereview config literal lines match that source exactly; deleting only the added block restores the entire pre-adoption config byte content (after text newline normalization).

`pwsh -NoProfile -File scripts/selftest.ps1 -Parallel` passed on that frozen candidate: light, e2e, seed-pre, seed-post and seed-b each exited 0, aggregate exit 0, 1216 seconds parallel wall time. This is ordinary full acceptance, without nightly `-IncludeMeta`. The candidate's ignored `.review/remote-adoption/` contains `proof.json`, `schema-selftest.log`, `schema-dod.log`, `schema-source-identity.json` and `config-identity.json`; the full log SHA-256 is `5038E845167E3C5964368B006F17DEF171B199114861CC48C77BE74D0FC49476`. At that historical checkpoint, formal R3 and required CI were still pending; the prior local verdict was not reused.

## PR 302 review correction

The first formal R3 on `13f2cbfb516f54523bec42f90a00b5e86dbb9da1` returned spec pass and standards block: the schema comment referenced a missing source card, and the hygiene checker rejected typed conditional fragments. Neither the old verdict nor the old full selftest authorizes merging the repaired candidate.

Repair candidate `22c9b8e7f2b9e5c5ae83abab6c9e2dcb3291a533` changes only the schema comment locator and the checker with its regression cases. Schema validation keywords, version/revision constants and unit identities are unchanged. The checker now records whether a node is the direct value of an if/then/else keyword; ordinary properties with those names and nested object schemas still require closure. All 18 new behavioral cases and the original default DoD pass. RED explicitly failed the three typed conditional cases before production changed; three semantic mutants were then killed by the typed-fragment, same-name-object and nested-child assertions. Checker SHA-256: `093A1B5E0D18BD15CD2B6B55E8F7A6CFDB9BD358E9DB723C8E70E2C1C9B1DC87`.

The original byte-identity statement applies to the initial candidate only. The repaired checker and the schema comment are deliberate review fixes; all other imported files and all 17 configuration literals remain unchanged. Final acceptance requires a new ordinary full selftest on the repaired candidate, another formal R3 and required CI. Before reship, write ignored `.review/remote-adoption/current-proof.json` with the new full selftest logs, actually tested candidate and source hashes; an old candidate's proof is historical only.
