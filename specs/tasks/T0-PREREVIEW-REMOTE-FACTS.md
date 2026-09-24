---
id: T0-PREREVIEW-REMOTE-FACTS
title: Publish reviewed prereview facts library against the remote foundation
status: todo
branch: T0-PREREVIEW-REMOTE-FACTS
worktree: C:\wt\T0-PREREVIEW-REMOTE-FACTS
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-REMOTE-POLICY]
allow_paths:
  - scripts/_prereview-facts.ps1
  - scripts/fixtures/prereview/facts-lib/
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-facts.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-FACTS-LIB-SELFCHECK-PASS]')) { exit 1 }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 Dot-sourcing with -AsLibrary returns before any side effect (the check-secrets.ps1 pattern), defines Resolve-PrereviewWorktree, Resolve-PrereviewBase, Get-PrereviewSnapshotTree, Get-PrereviewPolicyHash, Get-PrereviewUnits, Get-PrereviewLiveAllowed, Get-PrereviewModelRoute and Get-PrereviewTempRoot, and never sets PRE_LIVE."
  - "A2 Resolve-PrereviewBase -Local resolves the base name through Resolve-ScaffoldBaseRef -PreferLocal from scripts/_gitbase.ps1 (dot-sourced, not mirrored) and returns base_mode local with the pinned OID; without -Local it returns the origin ref and base_mode remote (the frozen enum of specs/prereview-record.schema.json); no merge-base with HEAD yields [PRE-NO-MERGE-BASE]."
  - "A3 Get-PrereviewSnapshotTree equals git write-tree of a temporary index built from read-tree HEAD plus add -A, so dirty tracked and untracked non-ignored files are included, untracked ignored files are excluded, and tracked files remain even when matched by an ignore rule; the fixture proves these cases with a planted untracked .env, an untracked non-ignored file and a tracked file subsequently matched by an ignore rule, and the same tree twice yields the same oid."
  - "A4 Get-PrereviewPolicyHash is the SHA-256 over the concatenated raw bytes of docs/PREREVIEW-CHECKLISTS.md then specs/prereview-record.schema.json at the merge-base commit (git show, not the working tree, with no inserted separator or text conversion) and matches a golden value in the fixture."
  - "A5 Get-PrereviewUnits -DiffPath -RepoRoot -SnapshotTree -MergeBase mints deterministic unit_id values {path}#{sha256(normalised hunk body)[:12]}-{ordinal}, where ordinal is the positive 1-based hunk order within that file in the supplied diff. Hunk normalization converts CRLF to LF, preserves all other body bytes and excludes the @@ header. Identical bodies at different headers have equal body_sha256 but distinct IDs, and repeated input yields identical units. Binary, metadata-only or over-PrereviewMaxFileBytes files degrade to {path}#file with the SHA-256 of the full raw blob; blob bytes and size come from SnapshotTree (MergeBase for a deleted file), never from patch length."
  - "A6 Get-PrereviewLiveAllowed returns false when CI or GITHUB_ACTIONS is set and true after the self-check clears both in-process; it only reports and never writes PRE_LIVE."
  - "A7 Get-PrereviewModelRoute returns PrereviewRiskyModel for a FrozenPaths hit and for a PrereviewRiskyExtraPaths hit (the fixture uses scripts/ or .claude/, never configs/compliance/), reading FrozenPaths at runtime instead of copying it, and PrereviewModel otherwise; Get-PrereviewTempRoot derives from [IO.Path]::GetTempPath()."
  - "A8 -SelfCheck runs against a temp repository only, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns no worker, and prints [PREREVIEW-FACTS-LIB-SELFCHECK-PASS]."
budget: 600
non_goals: [RECORDS implementation, packet generation, workers, Phase 1b, R3 changes]
hygiene: Retain exact production bytes and 18 historical killed-mutant evidence; run the 31 existing Windows checks and actual adopted-base config/hash smoke checks.
---

# T0-PREREVIEW-REMOTE-FACTS

Remote adoption of the already approved final local artifact; no original local card is relabelled as remotely merged. Source and excluded consumers: docs/plans/PREREVIEW-REMOTE-ADOPTION.md.

The original tests and mutation receipts are historical evidence only. Run the DoD and the current remote delivery gates on this candidate. R5 records the new PR/review/CI identity and archives this adoption card.

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. `scripts/_prereview-facts.ps1` and `scripts/fixtures/prereview/facts-lib/` are therefore on master at their local merged bytes, plus the gate 1h self-check entry the reconcile added, which makes the file SHA-256 `FD50745DA618EF4A...`; this card's `23A38171...` pin and its 18/18 receipt describe the pre-reconcile file. Whether this card is closed, narrowed or kept is a user decision.
