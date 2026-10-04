---
id: T0-PREREVIEW-RUN
title: prereview.ps1 run [-Local] - kill switch, two-phase parallel launch of discoverer and lens, record normalisation, state and packet under the common-dir plane
status: todo
depends_on: [T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-WORKERS-CLAUDE, T0-PREREVIEW-WORKERS-DEEPSEEK, T0-PREREVIEW-STATE-1A]
allow_paths:
  - scripts/prereview.ps1
  - scripts/fixtures/prereview/run/
  - specs/tasks/T0-PREREVIEW-RUN.md
dod_command: $t = (& pwsh -NoProfile -File scripts/prereview.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-SELFCHECK-PASS] on a temp repo with the fake claude first on PATH, PRE_LIVE unset and CI=1 throughout: the canonical flow (RED, uncommitted implementation, run -Local, simulated ship commit) gives HEAD^{tree} equal to state.snapshot_tree and one more edited line gives a mismatch; PrereviewEnabled=false gives [PRE-RUN-DISABLED] with nothing spawned; the state validates; rendezvous markers prove two concurrent workers (sequential mutant red); lens timeout => skipped with the packet ready; discoverer timeout => incomplete; empty knobs under CI=1 => [PRE-NO-NETWORK-IN-CI] with the fake claude never invoked; a parent-planted PRE_LIVE=1 is absent in the shim's env; nothing is written into the worktree.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
acceptance:
  - "A1 run -TaskId {id} -Base {name} [-Local] executes from the main checkout: PrereviewEnabled=false exits [PRE-RUN-DISABLED] before building a pack or spawning anything; otherwise it builds the pack through prereview-facts.ps1 (-Local passed through to base resolution), writes one prompt.txt with Build-PrereviewPrompt, and is the only writer of PRE_LIVE, set to 1 only on this non-SelfCheck path when CI and GITHUB_ACTIONS are unset."
  - "A2 Under CI or GITHUB_ACTIONS a built-in adapter is not spawned and run prints [PRE-NO-NETWORK-IN-CI] (discoverer incomplete, lens skipped) while custom commands and shims still run; the fixture observes this with empty knobs, CI=1, fake key values and the fake claude first on PATH, asserting the fake claude was never invoked (no marker file)."
  - "A3 Both workers are started through Start-BoundedWorker before either Wait-BoundedWorker; rendezvous markers written by two shims prove concurrent execution and a sequential-call mutant turns the fixture red; a lens failure (timeout, bad record, missing) makes the lens skipped with skip_code and the packet still ready; a discoverer failure makes the run incomplete."
  - "A4 Records pass through _prereview-records.ps1 (validation, id minting, duplicate rule, missing synthesis) and the state (state_version 1, disputes[] empty, base_mode, pinned base OID, policy_hash, workers[] with duration_s and exit_code) is written through _prereview-state.ps1 into {git-common-dir}/scaffold-prereview/{id}/ with packet.md and workers/{worker}-{batch}.* beside it; the pack is removed after the run; nothing is written into the reviewed worktree; run prints [PRE-PACKET-READY] with the paths."
  - "A5 The canonical flow fixture (temp repo: RED at branch start, uncommitted implementation, run -Local, simulated ship commit) proves HEAD^{tree} equals state.snapshot_tree, one more edited line yields a mismatch, the state validates against state.schema.json, and the same tree twice yields the same snapshot oid."
  - "A6 -SelfCheck clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, plants PRE_LIVE=1 in the parent for one case and proves the shim's echoed env has no PRE_LIVE, sets CI=1 and the private fake-claude PATH for every child, never sets PRE_LIVE itself, accepts -Fixture {name} for later cards, and prints [PREREVIEW-SELFCHECK-PASS]."
  - "A7 run emits exactly [PRE-RUN-DISABLED], [PRE-NO-NETWORK-IN-CI] and [PRE-PACKET-READY]; every code the run prints or stores is a member of the schema's status_code enum, and the first-live-run checklist results (OAuth without ANTHROPIC_API_KEY, reads outside the pack denied, DeepSeek key absent => lens skipped with [PRE-WORKER-MISSING], output token counts) are recorded as card evidence, never as DoD."
  - "A8 The CI-with-empty-knobs case is observed positively: the adapter's resolved executable path equals the fake claude and the fake's marker file is absent, and the loopback HTTPS_PROXY sink records zero connections; a fail-open CI mutant turns this case red by either signal."
forbid:
  - Setting PRE_LIVE in -SelfCheck or in any fixture
  - A non-SelfCheck run with empty command knobs inside a fixture, except the [PRE-NO-NETWORK-IN-CI] case (CI=1, fake key values, fake claude first on PATH, marker assertion)
  - Writing anything into the reviewed worktree or its .review/
  - A manager model, streaming, per-finding subagents, a Claude review subagent
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: ignore PrereviewEnabled => the shim is spawned in the kill-switch case; sequential launch => rendezvous red; CI check made fail-open => the fake claude marker appears; drop the pack cleanup => pack-absent case red; each a single-line change.
doc_sync: After merge record the first live run (OAuth without ANTHROPIC_API_KEY, denied reads outside the pack, lens skipped without a key, token counts, wall time) in the card body and TASK-BOARD status; no protocol prose changes (PROTOCOL-DOC owns them).
---

# T0-PREREVIEW-RUN

## Context

The 1a controller: builds the pack (FACTPACK and FACTPACK-SLICES), the prompt (PROMPT), launches the discoverer (WORKERS-CLAUDE) and the lens (WORKERS-DEEPSEEK) in parallel through the runner, normalises records (RECORDS), writes the state and packet (STATE-1A). Runs from the main checkout with -TaskId, like task.ps1.

## Codes emitted

- [PRE-RUN-DISABLED] (PrereviewEnabled=false, nothing spawned), [PRE-NO-NETWORK-IN-CI] (built-in adapter under CI, not spawned), [PRE-PACKET-READY].

## First live run (evidence, not DoD)

- OAuth succeeds without ANTHROPIC_API_KEY; reads outside the pack are denied; the lens is skipped with [PRE-WORKER-MISSING] when DEEPSEEK_API_KEY is absent; output token counts and wall time recorded here after merge.

## Notes

Split line: ~350 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
