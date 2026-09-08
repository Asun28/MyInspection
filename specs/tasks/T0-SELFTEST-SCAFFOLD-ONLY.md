---
id: T0-SELFTEST-SCAFFOLD-ONLY
title: Keep scaffold selftest off product-only changes
depends_on: [T0-SELFTEST-PAGED-PERF]
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
