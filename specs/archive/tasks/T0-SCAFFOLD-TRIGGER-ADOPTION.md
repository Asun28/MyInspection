---
id: T0-SCAFFOLD-TRIGGER-ADOPTION
title: Adopt product-only scaffold trigger exclusion with shared focused proof
depends_on: []
parallelizable_with: []
status: merged
superseded_by: T0-SCAFFOLD-TRIGGER-REMOTE
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

## Focused evidence

- Official RED at `18ad0627`: DoD exit 1, missing compliance exclusion, gate `8.2d`; the same focused fixture is now GREEN.
- The actual `canary-harness` fixture passes with the existing continuation and source-contract mutations intact.
- Actual gate 17ai inventory and ordered-site mutations: RED before the note/comment repair, PASS afterward.
- Actual `Test-SelftestGateIdContract` on the final source: PASS, 110 headings / 110 failure owners / 710 explicit messages.
- Full selftest, real gate 17a3 migration execution, verify and R3 remain required for final integrated acceptance.

Validation (2026-09-06 NZ): the exact production 17a3 migration block passed (exit 0,
224.24 seconds, no environment skip). Real TestNG output contains one intended
Td4ContinueProbeTest AssertionError; the no-continue test-first case, continue/ADDED
case, wrong-migration/REMOVED case and fixture cleanup all passed. Focused trigger,
canary source-contract, actual 17ai inventory/mutations and gate-ID ownership also pass.
Full integrated selftest remains the final delivery check; no full-suite result is claimed here.

Combined validation update: `ee1ba6e7` passed core/workflow/scanner, the migration canary,
inventory checks and project verify (32.10 seconds). Its sole 17ac mutation-setup failure
from the review-policy integration was repaired; complete actual 17ac replay passed in
574.42 seconds, then `e7b8f567` seeded-remote passed in1007.14 seconds. No prerequisite
failure skips remain; nine existing environment/post-init skips are reported. Source
identities and logs are in the main checkout's `_local/upstream-integration/`. These
source-matched regression results do not claim a new single full-all run or idle timing.

Final integration for the additional authorized review (2026-09-07): current main
`e4f8a211` is merged, retaining its meta defaults, skill routing and audited product docs.
The binding final acceptance receipt is `.review/trigger-final-20260907/full-selftest-result.json`
and its sibling `full-selftest.log`: exact HEAD before/after, tracked-file hashes before/after,
exit code, elapsed time and all-shard terminal output. This additional R3 is invoked only
when that receipt proves a complete exit-0 run on this exact reviewed candidate. Historical
`1e8986d0` and combined-shard results above are not substituted for this receipt.
The sibling `focused.log` replays the actual 17ai inventory and all its ordered-site mutants,
plus the shared trigger and canary fixtures, against the final integration.

Official local delivery: exact feature `1672386367fe112e7c9cc47a719c2b6aa47dd097`, full selftest exit 0 / 1980.670s with all tracked bytes stable; R3 pass on the same SHA; local merge `9229141a189b83037fa33ac66c05e3e140957d0b`. Final evidence preserved in `_local/upstream-v047-implementation/evidence/T0-SCAFFOLD-TRIGGER-ADOPTION/` before cleanup.

This delivery supersedes the [earlier preparation](../../tasks/T0-SELFTEST-SCAFFOLD-ONLY.md). Its separate branch and uncommitted files remain preserved; none of its historical RED output is claimed as this card's official RED receipt.

## Reconcile note (2026-09-24)

Origin delivered this card's work before the 2026-09 local/origin reconcile: the product-only trigger exclusion through `T0-SCAFFOLD-TRIGGER-REMOTE` ([PR #245](https://github.com/Asun28/MyInspection/pull/245), `b4a72de9`) and the isolated migration-failure fixture through `T0-SELFTEST-SCAFFOLD-ONLY` ([PR #272](https://github.com/Asun28/MyInspection/pull/272)). This DoD's `selftest.ps1 -Fixture scaffold-trigger` does not exist in origin's selftest. The local implementation (merged locally as `9229141a` on 2026-09-07) is replaced by origin's scaffold in the reconcile.
