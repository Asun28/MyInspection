---
id: T0-PREREVIEW-STATE-1A
title: Add prereview state v1, atomic persistence, dispute append, packet rendering, and common-dir views
status: todo
branch: T0-PREREVIEW-STATE-1A
worktree: C:\wt\T0-PREREVIEW-STATE-1A
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-RECORDS, T0-PREREVIEW-REMOTE-FACTS]
allow_paths:
  - scripts/_prereview-state.ps1
  - scripts/fixtures/prereview/state/
  - specs/tasks/T0-PREREVIEW-STATE-1A.md
budget: 999
doc_sync: After merge, update this card's status and record its PR and R3 receipt in this card during R5; archive through the normal R5 path.
dod_command: $t = (& pwsh -NoProfile -File scripts/_prereview-state.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-STATE-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck prints [PREREVIEW-STATE-SELFCHECK-PASS] after validating v1 state, atomic recovery, disputes, packet prose, invalid verdict and pass/block rejection, and common-dir-only views.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 scripts/fixtures/prereview/state/state.schema.json is one self-contained file with no cross-file $ref, describing state_version 1: task and snapshot metadata; workers with complete|incomplete|skipped status; units, candidates, coverage including missing, disputes, and stop_reason. It has no verdict field and no pass or block status value."
  - "A2 Write-PrereviewState writes {git-common-dir}/scaffold-prereview/{id}/state.json via a temporary file and atomic rename; a simulated crash between them preserves the earlier state.json and leaves no partial file at that name."
  - "A3 A run-written state contains an empty disputes[] array. Add-PrereviewDispute atomically appends candidate_id or null, r3_round, r3_sha, reason_index, reason_sha256, relation same|related|new, by, and at."
  - "A4 Write-PrereviewPacket lists candidates, coverage, and missing units without emitting a gate verdict, while preserving model-authored prose containing pass or block. The self-check rejects verdict fields and pass/block status values. Write-PrereviewView writes packet.md and worker views beside state.json in the common-dir plane and rejects reviewed-worktree and .review targets."
  - "A5 Resolve-PrereviewStatePlane derives its location from git rev-parse --git-common-dir of the main checkout; deleting a worktree preserves state and no .review/{id}/prereview path is created."
  - "A6 -SelfCheck uses a temporary repository and fixtures, clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, runs only Git needed for that repository, starts no worker, makes no network request, and prints [PREREVIEW-STATE-SELFCHECK-PASS]."
forbid:
  - Dispositions, transitions, review_status fields or derivation, gate predicates, batch behavior, ledger rows, or metrics
  - Writing views or logs inside a worktree or .review/
  - A second state schema or a copy of record schema definitions
non_goals:
  - Any Phase-1b extension, including review_status
  - Board updates; those are separate R5 work
hygiene: Replacing rename with direct write, removing path refusal, omitting disputes[], admitting verdict or pass/block status, or removing candidate prose must each make -SelfCheck fail. No `.psd1` mutation registry change is required.
---

# T0-PREREVIEW-STATE-1A

## Scope

This card begins only after `T0-PREREVIEW-REMOTE-SCHEMA`, `T0-PREREVIEW-RECORDS`, and `T0-PREREVIEW-REMOTE-FACTS` have actually merged. It consumes the published schema_version 1 / schema_revision 1, record normalization, and facts/Git helper contracts. It does not republish schema, policy, configuration, or facts-library files. It owns state v1 and the common-dir plane; common-dir placement is persistence isolation, not a sandbox guarantee. It stores a supplied policy_hash as state metadata but does not compute or consume policy hashes at runtime. The implementation stays below 1,000 changed lines and 60,000 changed characters.

## Source evidence

The archived local delivery used 86 self-check assertions and 25 behavioral mutants. It repaired the acceptance so legal finding prose containing `pass` or `block` survives packet rendering, while structural verdict fields and pass/block status values fail validation. `review_status` remains exclusively a later 1b concern.
