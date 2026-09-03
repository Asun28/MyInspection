---
id: T0-SELFTEST-PAGED-PERF
title: Replace duplicate pagination ship fixtures with direct real-function coverage
depends_on: [T0-CI-PAGED-CONTRACT]
parallelizable_with: [T0-SCAFFOLD-SYNC-046]
status: todo
branch: T0-SELFTEST-PAGED-PERF
worktree: C:\wt\T0-SELFTEST-PAGED-PERF
allow_paths:
  - scripts/selftest.ps1
  - specs/tech-debt-tracker.md
  - specs/tasks/T0-SELFTEST-PAGED-PERF.md
forbid:
  - Changing scripts/task.ps1 or any production gate decision, assertion, endpoint, OS, or shard coverage
  - Making pagination tests pass against a copied helper instead of the real production function definition
  - Splitting the shard, raising timeouts, ignoring failures, or deleting the target-reaching canaries
non_goals:
  - Upstream tiered acceptance, nightly meta-gate routing, workflow trigger changes, or other selftest regions
  - Candidate-CI identity, deadline, jobs-drift, or receipt-loss behavior
diagnosis: T37-CIGATE/API-CONTRACT runs roughly 27 complete ship subprocesses at about 12 seconds each even though most cases exercise one pagination helper, inflating seeded-remote from about 2.5 to 9.6 minutes.
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Fixture ci-paged-direct *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or $t -cnotmatch '(?m)^\s*T37-CIGATE/API-CONTRACT/DIRECT OK\s*$') { exit 1 }
dod_exit: 0
dod_assert: The lightweight fixture executes the real pagination function directly across the full malformed/identity/page matrix. Final acceptance separately requires six endpoint boundary ships, the deterministic full-ship budget, seeded-remote, full selftest, and verify.
acceptance:
  - "A1 The test harness extracts and executes the exact Test-JsonInteger and Get-GhPagedCollectionBeforeDeadline definitions from scripts/task.ps1, with no copied production logic"
  - "A2 The complete malformed total/item/id/page-replay matrix runs directly, while each of check-runs, workflow-runs, and jobs keeps one replay rejection and one valid pagination full-ship boundary proof"
  - "A3 T37-CIGATE/API-CONTRACT enforces at most six full ship invocations and retains the existing A4 pagination mutations and success receipt"
  - "A4 On an otherwise idle machine, three seeded-remote runs have a median below five minutes; the final full selftest and verify gates remain green"
review_gate: codex {verdict:pass}
hygiene: Load exact production functions through the PowerShell AST; table-drive direct cases; retain only discriminating endpoint E2E boundaries and a deterministic invocation-count budget.
doc_sync: Mark TD163 paid with the measured before/after evidence and archive this card after merge.
---

# T0-SELFTEST-PAGED-PERF

Pay TD163 by changing test level, not coverage: exhaustive helper behavior runs in-process against the
real production definitions, while six full ship cases prove the three endpoint integrations.

The card DoD intentionally runs only the new direct fixture so RED and R3 can replay it cheaply. The six
full-ship boundary cases and aggregate suites remain mandatory final acceptance evidence.
