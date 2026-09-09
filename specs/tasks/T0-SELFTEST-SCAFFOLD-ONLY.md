---
id: T0-SELFTEST-SCAFFOLD-ONLY
title: Keep scaffold selftest off product-only changes
depends_on: []
parallelizable_with: []
status: todo
branch: T0-SELFTEST-SCAFFOLD-ONLY
worktree: C:\wt\T0-SELFTEST-SCAFFOLD-ONLY
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - scripts/selftest.ps1
  - scripts/task.ps1
  - CLAUDE.md
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - docs/TASK-BOARD.md
  - specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md
forbid:
  - Removing or weakening any selftest assertion, shard, operating system, failure, or manual-dispatch path
  - Changing product tests, scripts/verify.ps1, R3, timeouts, dependencies, release, or deployment behavior
  - Excluding scaffold-owned license or secret configuration from the post-merge canary
non_goals:
  - Upstream tiered acceptance, nightly meta-gate routing, or further selftest performance work
  - Changing GitHub service rules
diagnosis:
  root_cause: The broad configs/** workflow selector treats product compliance rules as scaffold authority, so a product-only compliance push launches all ten scaffold selftest jobs.
  same_class: Product code and ordinary product configuration are already outside the trigger; configs/licenses/** and configs/secrets/** remain scaffold-owned. Aggregate validation also exposed one scaffold fixture that mutated product compliance config to force a test failure; it must use an isolated temporary test instead.
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard core -Fixture through-gate8 *>&1 | Out-String); $s = Get-Content scripts/selftest.ps1 -Raw; if ($LASTEXITCODE -ne 0 -or $t -cnotmatch '(?m)^\[SELFTEST-FIXTURE\] through-gate8 PASS\s*$' -or $s.Contains('configs/compliance/nz-rules-v1.json') -or $s -notmatch 'Td4ContinueProbeTest') { exit 1 }
dod_exit: 0
dod_assert: Gate 8.2d accepts only the exact scaffold trigger boundary and rejects removal of the configs/compliance exclusion; the bounded fixture emits PASS; gate 17a3 names its isolated test and no longer names the product compliance file.
acceptance:
  - "A1 Changes limited to product paths, including configs/compliance/**, do not trigger scaffold-selftest"
  - "A2 Scripts, hooks, workflows, configs/licenses/**, and configs/secrets/** remain covered by default-branch push; workflow_dispatch remains available"
  - "A3 Removing the product-config exclusion makes gate 8.2d and the card DoD fail"
  - "A4 Both operating systems, all five shards, every assertion, and normal product verification remain unchanged"
  - "A5 Gate 17a3 manufactures its test-first failure without reading or modifying product compliance configuration"
review_gate: codex {verdict:pass}
hygiene: Extend the existing exact trigger contract and one discriminating mutation; add no dispatcher, job, dependency, or copied test logic.
doc_sync: Keep CLAUDE.md, DEVOPS-WORKFLOW.md, DELIVERY-CHAINS.md, and TASK-BOARD aligned with the scaffold-only boundary.
---

# T0-SELFTEST-SCAFFOLD-ONLY

The user's 2026-09-05 instruction to apply the useful findings authorizes local integration and
supersedes this card's earlier audit-only merge restriction. Final validation also restores the
missing note classification for the existing task-help continuation in gate 17ai; every ordered
gate-enumeration assertion remains intact. Restore the deleted local-gate summary comment in
task.ps1, and remove obsolete note-only discovery entries; no production workflow behavior changes.

Product work uses its relevant tests plus `scripts/verify.ps1`. The full scaffold selftest is reserved for
changes to scaffold/harness authority, whether run by the post-merge workflow or explicitly by an operator.

## Acceptance

1. `configs/compliance/**` is excluded from the workflow trigger without excluding scaffold-owned config.
2. Gate 8.2d rejects deletion of that exclusion.
3. Gate 17a3 uses a temporary scaffold-owned failing test rather than product compliance configuration.
4. The existing matrix, assertions, manual trigger, product verification, and R3 behavior are untouched.


## Remote delivery authorization and provenance

The user's 2026-09-08 instruction authorizes remote delivery through an independent worktree and PR. The remaining acceptance, forbid and non-goals stay in force. This todo registration does not represent local historical results as remote implementation or acceptance. Use task-loop with GPT-5.6 Terra, high effort and the configured independent GPT-5.6 Sol high R3. Establish current-source behavior evidence and preserve existing remote product changes.

Trigger isolation already has remote provenance from T0-SCAFFOLD-TRIGGER-ADOPTION. Verify all remaining assertions and fixture isolation on the current remote candidate; that prerequisite work alone does not mark this card complete.

## Prior reviewed-candidate verification (2026-09-08)

The prior reviewed candidate changed only the 17a3 fixture and its shared cleanup helper. Trigger, matrix, task-help classification and operating-contract requirements already exist in the remote baseline and remain unchanged. Primary RED was recorded on ae40d6b7 before the scoped implementation.

Actual temporary-file checks reproduced and rejected pre-existing-probe deletion and restoration I/O failure; the helper preserved unowned content and attempted every restoration before worktree cleanup. That prior candidate's full default selftest exited 0 in 2370.022 seconds, including real migration failure/order and cleanup assertions. Before/after selftest SHA-256 was identical: 1BCDA93FEF27E0ECFF639530A90900CEEFD8EA4A31A7DCADA48D93D9654170DF. It does not validate the then-current FB695355 callsite-fix successor or later sources. Raw output and terminal receipt are in `.review/T0-SELFTEST-SCAFFOLD-ONLY.full-selftest.raw.log` and `.review/T0-SELFTEST-SCAFFOLD-ONLY.full-selftest.json`. This is functional validation, not a performance claim. Formal ship gates and independent R3 are recorded separately by task-loop.

## Cleanup callsite regression (2026-09-08)

The successor selftest source SHA-256 FB695355856C885183C84011A70A715F82850BD5E3C52BB0DCE0F725E7652A84 passed the extracted actual callsite harness: each restoration-state failure and an errors-only failure skips migration-wrong; a final-only error remains visible; the clean path runs migration-wrong once. Four predicate deletions and deletion of either collected-error diagnostic were rejected. The harness executes both source Restore assignments, the complete first guard/else and final guard, with local cleanup-result and Gradle stubs; it does not rerun Gradle or the full selftest. Evidence: `.review/T0-SELFTEST-SCAFFOLD-ONLY.callsite.actual.ps1`, `.raw.log` and `.json`. The earlier helper-only modeled-callsite probes are not this control-flow evidence.

## Current-source validation (2026-09-09)

At HEAD `5bff8a15047f5690369047c6254c2aa7b3115cb6`, the default full selftest exited natively 0 in 2629.010 seconds. Before/after source SHA-256 was `BFACF8485F7255DDF0C7E39B6671AB50C8071E933B6AF4F00DBB48D06DD29376`; all tracked-file hashes remained unchanged. Seeded, workflow and core completed with PASS. The real 17a3 migration failure/order checks and final directory/worktree cleanup ran and passed. The log separately records 21 skips (seeded 11, workflow 1, core 9); these are not claims of executed coverage. Raw output, native exit, timestamps and source manifest are in `.review/current-source-full/`; full-log SHA-256 is `4322FAC56559D6E7D92E75ACF57003709479D4A098796ACAA146BC0F08B71B3D`.

The preceding current-base attempt at source `7C80AA8F...` exposed a stale canary extraction boundary: the tenancy snapshot assignment had moved before the core-check closure. Using the next forced-test assignment as the extraction end restores the existing Windows/POSIX invocation checks without changing either invocation or weakening their assertions. That attempt was stopped after its known seeded failure and is not a full success.

The original snapshot.actual and callsite.actual runners also parsed this exact BFAC source and each exited natively 0: 11 named temporary-file/control-flow cases passed and 10 named helper, predicate or diagnostic mutations were rejected. Both actual restore assignments and guards ran with local file fixtures and a Gradle counter stub; those bounded runs do not themselves prove real Gradle execution. Exact runner copies, raw logs, native exits, semantic oracles and source-binding manifest are in `.review/current-source-r4/`. Historical receipts above remain historical; normal ship DoD, verify, independent R3 and candidate CI are separate gates.