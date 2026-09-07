---
id: T0-SCAFFOLD-TRIGGER-REMOTE
title: Publish scaffold-only selftest trigger on current upstream
depends_on: []
parallelizable_with: []
status: in-progress
branch: T0-SCAFFOLD-TRIGGER-REMOTE
worktree: C:\wt\T0-SCAFFOLD-TRIGGER-REMOTE
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - specs/tasks/T0-SCAFFOLD-TRIGGER-REMOTE.md
  - specs/archive/tasks/T0-SCAFFOLD-TRIGGER-REMOTE.md
  - specs/archive/cards-index.md
forbid:
  - Removing or weakening an existing selftest assertion, shard, operating system, job, or manual-dispatch path
  - Changing product tests, verify, R3, timeouts, dependencies, release, or deployment behavior
  - Excluding scaffold-owned license or secret configuration from the default-branch canary
non_goals:
  - Selftest pagination, nightly scheduling, meta-gate routing, or unrelated local-master changes
  - Publishing the superseded T0-SELFTEST-SCAFFOLD-ONLY branch or its historical uncommitted work
diagnosis:
  root_cause: The broad configs/** push selector treats product compliance rules as scaffold authority, so an unrelated product-only push launches all ten scaffold selftest jobs.
  same_class: The existing gate 8.2d contract lacks a focused entry point and a mutation that proves the product exclusion is required.
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Fixture scaffold-trigger *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or $t -cnotmatch '(?m)^\[SELFTEST-FIXTURE\] scaffold-trigger PASS\s*$') { exit 1 }
dod_exit: 0
dod_assert: The focused fixture executes the real scaffold trigger contract and passes only when product compliance is excluded while scaffold-owned paths and workflow_dispatch remain covered.
acceptance:
  - "A1 A configs/compliance/**-only default-branch push does not trigger scaffold-selftest"
  - "A2 Scripts, hooks, workflows, configs/licenses/**, configs/secrets/** and workflow_dispatch remain covered"
  - "A3 The focused fixture and gate 8.2d share the same trigger-contract implementation; removing the exclusion fails both"
  - "A4 Both operating systems, all five shards, existing assertions and product verification remain unchanged"
  - "A5 The PR is based on current origin/master and contains no unrelated local-master history"
review_gate: codex {verdict:pass}
hygiene: Reuse the existing trigger parser and mutation suite; add no dispatcher, job, dependency, schedule, or copied contract.
doc_sync: Align DEVOPS-WORKFLOW and DELIVERY-CHAINS with the product-only exclusion; archive this card after remote merge.
---

# T0-SCAFFOLD-TRIGGER-REMOTE

This card publishes the already validated scaffold-only trigger boundary as a narrow PR rebuilt on
current `origin/master`. It does not push the divergent local `master` or reuse the preserved stale
preparation branch.

## Acceptance

1. Product compliance changes do not spend scaffold selftest time.
2. Scaffold authority and manual coverage remain unchanged.
3. A focused executable contract supplies RED/GREEN evidence without running unrelated gates.
4. Normal task-loop R3, candidate CI, merge, documentation sync, and cleanup complete remotely.
