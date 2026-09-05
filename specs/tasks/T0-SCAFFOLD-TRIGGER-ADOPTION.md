---
id: T0-SCAFFOLD-TRIGGER-ADOPTION
title: Adopt product-only scaffold trigger exclusion with shared focused proof
depends_on: []
parallelizable_with: []
status: todo
branch: T0-SCAFFOLD-TRIGGER-ADOPTION
worktree: C:\wt\T0-SCAFFOLD-TRIGGER-ADOPTION
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - scripts/selftest.ps1
  - scripts/task.ps1
  - CLAUDE.md
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - docs/TASK-BOARD.md
  - specs/tasks/T0-SCAFFOLD-TRIGGER-ADOPTION.md
forbid:
  - Removing or weakening existing assertions, ordered gate inventory checks, operating systems, shards, or manual dispatch
  - Changing product tests, product compliance data, verify, R3 decisions, timeouts, dependencies, or deployment behavior
  - Excluding scaffold-owned license or secret configuration from the push canary
non_goals:
  - Pagination performance, risk routing, nightly meta policy, or remote service configuration
  - Reconstructing historical RED evidence or changing the preserved T0-SELFTEST-SCAFFOLD-ONLY branch
diagnosis:
  root_cause: The configs/** push selector includes product compliance data; a scaffold migration fixture also mutates that data to force an unrelated test failure.
  same_class: Gate 17ai has an unclassified task-help continuation and obsolete note entries; the missing local-gate summary comment must be restored while retaining every ordered gate assertion.
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Fixture scaffold-trigger *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or $t -cnotmatch '(?m)^\[SELFTEST-FIXTURE\] scaffold-trigger PASS\s*$') { exit 1 }
dod_exit: 0
dod_assert: The focused fixture and gate 8.2d share the actual trigger contract and existing mutation matrix; the unchanged workflow fails for its missing product exclusion before any implementation is applied.
acceptance:
  - "A1 A product-only push, including configs/compliance/**, does not trigger scaffold-selftest; scripts, hooks, workflows, configs/licenses/** and configs/secrets/** remain covered"
  - "A2 The focused fixture and gate 8.2d execute one shared assertion and mutation implementation; removing the product exclusion fails both, and focused iteration does not execute gates 1 through 8"
  - "A3 Gate 17a3 uses an isolated temporary failing test without reading or changing product compliance configuration, and retains its real --continue and migration failure proofs"
  - "A4 Gate 17ai classifies the existing task-help continuation and removes obsolete note entries while preserving all ordered gate assertions; task.ps1 changes only restore its local-gate summary comment"
  - "A5 Both operating systems, all five shards, manual dispatch and any independently adopted nightly schedule remain intact; final full selftest, verify, normal scope checks and R3 pass on the integrated candidate"
review_gate: codex {verdict:pass}
hygiene: Move the existing gate 8.2d assertions into one shared focused entry, retain discriminating mutations, and keep the existing migration and inventory canaries as final behavioral proof.
doc_sync: Align existing workflow authority documents and TASK-BOARD; archive this card after merge and link the preserved earlier preparation as superseded work without claiming recovered RED evidence.
---

# T0-SCAFFOLD-TRIGGER-ADOPTION

This is a fresh test-first adoption of the user's authorized scaffold reductions. The prepared
T0-SELFTEST-SCAFFOLD-ONLY branch, including its three uncommitted files, remains unchanged.
Its historical direct selftest RED is contextual evidence, not an official task RED receipt.

The new worktree starts from current main. Tests first tighten the existing trigger contract,
then the main-checkout task runner records genuine RED against the unchanged workflow.
Only afterward are the narrow workflow, canary, inventory and documentation changes applied.
The card deliberately contains no pagination implementation and requires no old stacked branch.

The focused DoD is an iteration check. Final acceptance still includes full selftest and verify,
including real gate 17a3 and 17ai execution. Coordinate overlapping nightly work by merging
its exact schedule and meta wiring; this card neither adds nor removes a nightly policy.
