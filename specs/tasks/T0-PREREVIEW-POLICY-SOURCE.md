---
id: T0-PREREVIEW-POLICY-SOURCE
title: Publish the two byte-exact historical prereview policy source files
status: todo
branch: T0-PREREVIEW-POLICY-SOURCE
worktree: C:\wt\T0-PREREVIEW-POLICY-SOURCE
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA]
allow_paths:
  - scripts/fixtures/prereview/policy-source/raw/PREREVIEW-PROTOCOL.txt
  - scripts/fixtures/prereview/policy-source/raw/PREREVIEW-CHECKLISTS.txt
dod_command: $r='scripts/fixtures/prereview/policy-source/raw/'; $expected=@{'PREREVIEW-PROTOCOL.txt'=@(29016,'1CBF12A29E6CCC67BCFAEEEAE93C0CE65E00D151CCDCC67F885421BE5F744457','0bbfb4e51b08624c23f38a8e388aad485e05b1b8');'PREREVIEW-CHECKLISTS.txt'=@(12238,'C0C8ADACC6F77EF8D067F2987E69398E1ABDEF40E10C46B1AD32BF511B6181F1','836a99e8a4b756fb1f78e92159e86eb4e48a06c3')}; if(@(Get-ChildItem -LiteralPath $r -File -Recurse -Force).Count -ne 2) { exit 1 }; foreach($name in $expected.Keys) { $p=$r+$name; if((Get-Item -LiteralPath $p).Length -ne $expected[$name][0] -or (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash -cne $expected[$name][1]) { exit 1 }; $blob=(& git hash-object --no-filters -- $p | Out-String).Trim(); if($LASTEXITCODE -ne 0 -or $blob -cne $expected[$name][2]) { exit 1 } }; Write-Output '[POLICY-SOURCE-BYTES-PASS]'; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 The two published files preserve all raw bytes of original Git commit 4c9735ef113e2a778d628de490bfc0554d9defa9, including historical wording and references. Each actual byte length, SHA-256 and unfiltered Git blob ID matches independent literals in the base-card DoD. [source identity]"
  - "A2 The raw directory contains exactly the two fixed source paths and no extra file. This inventory remains valid when SOURCE-CHECK later adds its manifest and scripts outside raw. No unpublished checker or recipe is needed to run this card's DoD. [inventory and independent DoD]"
  - "A3 The txt payload is historical fixture data under the existing prereview fixture tree, not an active policy or instruction. No source text is edited to remove obsolete references. Its boundary is stated in this card and the adoption plan. [placement review]"
budget: 650
non_goals: [Policy activation, replacement recipe publication, comparison script implementation, worker or runner execution, delivery gate changes]
hygiene: Data publication only. Verify the original Git bytes and pinned hashes independently; no behavior change or new mutation suite is claimed.
---

# T0-PREREVIEW-POLICY-SOURCE

The source evidence is currently unavailable from committed remote history, which caused both real POLICY R3 blocks. Publish only these two reviewed raw files; do not publish the divergent local history. The subsequent SOURCE-CHECK card supplies the complete recipe, executable replay and negative probes before POLICY resumes. The two POLICY verdicts and its round counter remain preserved.

Compute the actual base, full diff size and selftest tier/routing before this card's authorized R1. The fixture txt paths currently have no route and are expected to escalate computed Tier 1 to full acceptance; this expectation is not acceptance evidence.

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. The source documents that `scripts/fixtures/prereview/policy-source/raw/` copies are now on master, and each raw copy is byte-identical to its document (`git hash-object` equal on 2026-09-24). Whether this card is closed, narrowed or kept is a user decision.
