---
id: T0-CI-SELFTEST-REPAIR
title: Repair scanner inventory drift and Windows seeded-git CI timeout
depends_on: []
status: merged
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

## Delivery evidence

- [PR #259](https://github.com/Asun28/MyInspection/pull/259) merged as `a293b531916d97aa0b2be83ec5415286d7f333c8`; first formal R3 passed candidate `7b0d9320e2c22805e3ca6530935f2ebb2cd8db3d`.
- Official RED reproduced the five 17ai inventory errors. Repaired scanner, isolated core, Windows seeded-git, verify, scope, license, secret and diff-budget gates passed. All 17 ordered-site deletion mutants remain intact; the timeout selection mutants and budget-guard deletion control passed.
- [Candidate CI](https://github.com/Asun28/MyInspection/actions/runs/34181347064) passed verify. [Post-merge scaffold CI](https://github.com/Asun28/MyInspection/actions/runs/34182253041) passed all ten jobs on the exact merge commit, including both scanners and Windows seeded-git (19m49s).
- The first local core attempt collided with a scanner temporary file; its replacement ran in an isolated snapshot and passed with matching candidate hashes. No failing attempt is presented as a pass.
- Local logs, official RED, R3 verdict and final CI JSON are retained in the control checkout's `_local/ci-selftest-repair/`. No unrelated local master history was published.
