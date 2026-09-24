---
id: T0-PREREVIEW-FACTS-LIB
title: _prereview-facts.ps1 -AsLibrary - worktree, base (via _gitbase.ps1), snapshot tree, policy hash, units, live_allowed, model route and temp root as pure functions
status: merged
depends_on: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-UNIT-ID-REVISION]
allow_paths:
  - scripts/_prereview-facts.ps1
  - scripts/fixtures/prereview/facts-lib/
  - specs/tasks/T0-PREREVIEW-FACTS-LIB.md
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-facts.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-FACTS-LIB-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-FACTS-LIB-SELFCHECK-PASS] after: -AsLibrary returns without side effects and never sets PRE_LIVE; the snapshot tree equals git write-tree of a dirty temp tree; the policy hash matches its golden value at the merge-base commit; -Local resolves through Resolve-ScaffoldBaseRef -PreferLocal recording base_mode local and the OID, otherwise the origin ref; no merge-base => [PRE-NO-MERGE-BASE]; units are deterministic with body_sha256 and degrade to {path}#file for binary or over-size files; live_allowed is false under CI; a FrozenPaths hit and an extra-path hit both route to PrereviewRiskyModel; the temp root derives from [IO.Path]::GetTempPath().
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-WORKERS, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK]
acceptance:
  - "A1 Dot-sourcing with -AsLibrary returns before any side effect (the check-secrets.ps1 pattern), defines Resolve-PrereviewWorktree, Resolve-PrereviewBase, Get-PrereviewSnapshotTree, Get-PrereviewPolicyHash, Get-PrereviewUnits, Get-PrereviewLiveAllowed, Get-PrereviewModelRoute and Get-PrereviewTempRoot, and never sets PRE_LIVE."
  - "A2 Resolve-PrereviewBase -Local resolves the base name through Resolve-ScaffoldBaseRef -PreferLocal from scripts/_gitbase.ps1 (dot-sourced, not mirrored) and returns base_mode local with the pinned OID; without -Local it returns the origin ref and base_mode remote (the frozen enum of specs/prereview-record.schema.json); no merge-base with HEAD yields [PRE-NO-MERGE-BASE]."
  - "A3 Get-PrereviewSnapshotTree equals git write-tree of a temporary index built from read-tree HEAD plus add -A, so dirty tracked and untracked non-ignored files are included, untracked ignored files are excluded, and tracked files remain even when matched by an ignore rule; the fixture proves these cases with a planted untracked .env, an untracked non-ignored file and a tracked file subsequently matched by an ignore rule, and the same tree twice yields the same oid."
  - "A4 Get-PrereviewPolicyHash is the SHA-256 over the concatenated raw bytes of docs/PREREVIEW-CHECKLISTS.md then specs/prereview-record.schema.json at the merge-base commit (git show, not the working tree, with no inserted separator or text conversion) and matches a golden value in the fixture."
  - "A5 Get-PrereviewUnits -DiffPath -RepoRoot -SnapshotTree -MergeBase mints deterministic unit_id values {path}#{sha256(normalised hunk body)[:12]}-{ordinal}, where ordinal is the positive 1-based hunk order within that file in the supplied diff. Hunk normalization converts CRLF to LF, preserves all other body bytes and excludes the @@ header. Identical bodies at different headers have equal body_sha256 but distinct IDs, and repeated input yields identical units. Binary, metadata-only or over-PrereviewMaxFileBytes files degrade to {path}#file with the SHA-256 of the full raw blob; blob bytes and size come from SnapshotTree (MergeBase for a deleted file), never from patch length."
  - "A6 Get-PrereviewLiveAllowed returns false when CI or GITHUB_ACTIONS is set and true after the self-check clears both in-process; it only reports and never writes PRE_LIVE."
  - "A7 Get-PrereviewModelRoute returns PrereviewRiskyModel for a FrozenPaths hit and for a PrereviewRiskyExtraPaths hit (the fixture uses scripts/ or .claude/, never configs/compliance/), reading FrozenPaths at runtime instead of copying it, and PrereviewModel otherwise; Get-PrereviewTempRoot derives from [IO.Path]::GetTempPath()."
  - "A8 -SelfCheck runs against a temp repository only, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns no worker, and prints [PREREVIEW-FACTS-LIB-SELFCHECK-PASS]."
forbid:
  - Editing scripts/_gitbase.ps1 or reimplementing base resolution
  - Building the pack here (FACTPACK); writing outside temp except Git content-addressed objects needed for the snapshot in the selected repository object database; modifying its real index, worktree files, refs or configuration
  - Setting PRE_LIVE anywhere in this file
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: skip add -A before write-tree => untracked-file case red; hash the working tree instead of the merge-base for the policy => golden red; drop the FrozenPaths union => frozen-hit route red; ignore GITHUB_ACTIONS => live_allowed case red.
---

# T0-PREREVIEW-FACTS-LIB

## Context

Pure-function library consumed by FACTPACK, STATE-1A, RUN and the 1b gate; the functions other cards call and never recompute.

## Functions carried by this card

- Resolve-PrereviewWorktree -TaskId [-RepoRoot]; Resolve-PrereviewBase -RepoRoot -Base -Local (through Resolve-ScaffoldBaseRef -PreferLocal, returning base_ref, base_mode, base_oid and merge_base); Get-PrereviewSnapshotTree -WorktreePath (git write-tree on a temporary index after read-tree HEAD and add -A); Get-PrereviewPolicyHash -RepoRoot -MergeBase; Get-PrereviewUnits -DiffPath -RepoRoot -SnapshotTree -MergeBase; Get-PrereviewLiveAllowed (false under CI or GITHUB_ACTIONS; never sets PRE_LIVE); Get-PrereviewModelRoute -AllowPaths (FrozenPaths union PrereviewRiskyExtraPaths at runtime); Get-PrereviewTempRoot ([IO.Path]::GetTempPath()).
- Dot-source with -AsLibrary (early return like check-secrets.ps1).

## Notes

2026-09-16 user-approved TD176 correction: A3 applies ignore exclusion only to untracked files; A5 depends on the explicit schema revision card above. Snapshot creation may write Git content-addressed objects, while the temporary index stays under temp and the real index/worktree/refs/config are unchanged. Unit generation requires pinned blob context so over-size and binary units hash actual content. These corrections were registered on master before R1/RED.

Split line: ~350 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.

## Implementation evidence

Local merge `b675d6a6` reviewed tip `91bf3cbe`: first formal R3 passed with zero findings. DoD, project verify, scope, license, secrets and hard diff budget passed. Actual reviewed diff: 517 added lines / 31867 characters, above the sizing estimate but below 1000 / 60000. The eight public functions reuse the existing base resolver and Git; no new dependency or worker execution was added.

RED at `580d078a` rejected the missing eight exports. Final GREEN: 31 checks cover A1-A8 with real temporary Git repositories, raw BOM/CRLF/Unicode policy bytes, dirty/untracked/ignored paths and unchanged real index, local/remote merge bases, raw non-UTF-8 hunk bytes, repeated hunk ordinals, no-newline markers, full-blob fallback and runtime routing. The Unix-only literal-quote filename case was not exercised on Windows; the C-quoted Unicode/space path case passed. Routed selftest from this worktree passed seeded, workflow and core (aggregate exit 0; ordinary mode, nightly meta fixtures deferred). Normal cleanup removed the worktree and branch using the matching merge token; review/RED receipts were preserved in the local evidence directory.

R4: 18/18 behavioral mutants were killed by named checks, without counting parser failures; each restored the exact production bytes. Covered faults: skipped add/read-tree; working-copy policy hash; missing/stale FrozenPaths; ignored GITHUB_ACTIONS; ordinal collision; header included in body; UTF-8 recoding; ignored size; wrong blob hash/file ID/metadata hash; real-index writes; missing PreferLocal; inherited live environment; missing CRLF normalization; dropped no-newline marker. One duplicate frozen-route assertion was removed only after the frozen-union mutant remained caught by the runtime-config assertion; GREEN then passed again.

Mutation source SHA-256: `23A38171AAAF97303EDA973586041A07F8500EE7BF38275AA01CCFE156F7ACC2`. Independent fresh-context pre-review found no issues and reran all 31 checks. Formal R3 remains the acceptance verdict above.

R5 follow-up: TD179 records the existing schema/protocol candidate-file and anchor wording for deleted paths, to resolve in a separate frozen-contract review before worker/prompt integration. This library follows its approved deleted-unit contract. L331 records the fixture helper/native Git name collision and its prevention.
