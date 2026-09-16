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
hygiene: Reuse the exact approved schema/checker bytes and original mutation evidence; rerun all behavioral schema cases on the remote candidate.
---

# T0-PREREVIEW-REMOTE-SCHEMA

Remote adoption of the already approved final local artifact; no original local card is relabelled as remotely merged. Source and excluded consumers: docs/plans/PREREVIEW-REMOTE-ADOPTION.md.

The original tests and mutation receipts are historical evidence only. Run the DoD and the current remote delivery gates on this candidate. R5 records the new PR/review/CI identity and archives this adoption card.

## Current remote-candidate evidence

Candidate `13f2cbfb516f54523bec42f90a00b5e86dbb9da1` is clean and unchanged after its checks. The default schema checker passed both `[PREREVIEW-SCHEMA-OK]` and `[PREREVIEW-UNIT-ID-REVISION-PASS]`; the config DoD arm passed. An independent byte comparison matched all 26 imported schema/checker/fixture files to local source tree `4c9735ef`. All 17 Prereview config literal lines match that source exactly; deleting only the added block restores the entire pre-adoption config byte content (after text newline normalization).

`pwsh -NoProfile -File scripts/selftest.ps1 -Parallel` passed on that frozen candidate: light, e2e, seed-pre, seed-post and seed-b each exited 0, aggregate exit 0, 1216 seconds parallel wall time. This is ordinary full acceptance, without nightly `-IncludeMeta`. The candidate's ignored `.review/remote-adoption/` contains `proof.json`, `schema-selftest.log`, `schema-dod.log`, `schema-source-identity.json` and `config-identity.json`; the full log SHA-256 is `5038E845167E3C5964368B006F17DEF171B199114861CC48C77BE74D0FC49476`. Formal R3 and required CI are still pending and must run on this candidate; the prior local verdict is not reused.
