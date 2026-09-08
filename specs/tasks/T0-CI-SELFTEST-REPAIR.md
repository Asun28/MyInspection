---
id: T0-CI-SELFTEST-REPAIR
title: Repair scanner inventory drift and Windows seeded-git CI timeout
depends_on: []
status: todo
branch: T0-CI-SELFTEST-REPAIR
worktree: C:\wt\T0-CI-SELFTEST-REPAIR
allow_paths:
  - scripts/selftest.ps1
  - scripts/task.ps1
  - .github/workflows/scaffold-selftest.yml
  - docs/DEVOPS-WORKFLOW.md
  - CLAUDE.md
forbid:
  - Removing or weakening ordered gate assertions or mutation coverage
  - Changing product code, dependencies, R3 policy, or candidate CI gate behavior
non_goals:
  - Publishing unrelated local master history or changing nightly routing
diagnosis:
  root_cause: Gate 17ai inventory anchors drifted from task.ps1; the timeout expression tests nonexistent shard seeded so Windows seeded-git is killed at 20 minutes.
  same_class: Audit all 17ai discovery entries and preserve every ordered-site mutation; validate the timeout selection across all ten matrix entries.
acceptance:
  - "A1 seeded-scanner passes with all existing 17ai ordered-site deletion mutations intact"
  - "A2 Windows seeded-git receives 30 minutes; the other nine matrix entries retain 20 minutes"
  - "A3 Both operating systems and all five shards remain enabled; actual candidate CI and post-merge scaffold-selftest pass"
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-scanner
dod_exit: 0
dod_assert: seeded-scanner returns zero with selftest PASS; core timeout matrix checks, seeded-git, verify and R3 also pass before delivery is complete.
review_gate: codex {verdict:pass}
hygiene: Preserve existing 17ai negative controls and prove a reverted timeout condition fails the matrix assertion.
doc_sync: Record remote repair evidence in CLAUDE.md and clarify the Windows seeded-git budget in DEVOPS-WORKFLOW; archive after merge.
---

# T0-CI-SELFTEST-REPAIR

Repair runs 34172655807 and 34161912768 from the remote master baseline.
The first run also has a GitHub annotation confirming the Windows seeded-git
job exceeded 20 minutes. Reuse only the previously delivered local 17ai repair;
do not import the divergent local branch. Expected implementation budget is
under 150 changed lines. Historical runs remain historical evidence; success
must be established on a new run containing this repair.
