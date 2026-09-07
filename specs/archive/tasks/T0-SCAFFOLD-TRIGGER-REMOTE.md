---
id: T0-SCAFFOLD-TRIGGER-REMOTE
title: Publish scaffold-only selftest trigger on current upstream
depends_on: []
parallelizable_with: []
status: merged
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
  root_cause: The broad configs/** push selector treated product compliance rules as scaffold authority, so an unrelated product-only push launched all ten scaffold selftest jobs.
  same_class: The former gate 8.2d contract lacked a focused entry point and a mutation proving the product exclusion was required.
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
doc_sync: Completed in PR #245; this card was archived after remote merge.
---

# T0-SCAFFOLD-TRIGGER-REMOTE

PR #245 merged as `b4a72de9f9411b527a3140760e1c89cad0abc59e` after RED/GREEN, project verify,
scope, license, secret, diff-budget, R3, and candidate CI gates passed. The first R3 round correctly
blocked because the task card was absent from the PR; the card was added and the second round passed.

The delivered trigger excludes product compliance changes while retaining scaffold-owned paths,
manual dispatch, both operating systems, and all five shards. The focused contract runs in seconds;
the full scaffold suite remains available for scaffold changes and explicit operator runs.
