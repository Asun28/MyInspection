---
id: T0-DEBT-REFERENCE-INTEGRITY
title: Authority TD reference integrity (repay TD16)
depends_on: [T0-GATE-FIXFORWARD]
status: todo
branch: T0-DEBT-REFERENCE-INTEGRITY
worktree: C:\wt\T0-DEBT-REFERENCE-INTEGRITY
allow_paths:
  - docs/SECURITY.md
  - docs/IDEA-TO-PLAN.md
  - scripts/_config.ps1
  - docs/LOOP-ENGINEERING.md
  - docs/QUALITY-RUBRIC.md
  - scripts/review.ps1
  - scripts/selftest.ps1
forbid:
  - Renumbering, deleting, or rewriting tracker history
  - Weakening review, scope, security, or selftest gates
  - Editing TD21 task-count prose or any product/runtime code
non_goals:
  - A repository-wide rewrite of every historical scaffold TD mention
  - Changing R3 behavior, verdict semantics, or task-card scope semantics
diagnosis:
  root_cause: Downstream initialization retained unqualified scaffold TD numbers while the project tracker reused those local numbers for unrelated debts, and some referenced upstream rows were not carried into this repository.
  same_class: Audit the six named authority locations in TD16; qualify genuine upstream history and replace project guidance with stable local section links.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: The six TD16 authority references no longer resolve to unrelated or missing local debt rows; genuine upstream history is explicitly namespaced; a deterministic seeded fixture rejects an unqualified missing or wrong-topic local TD reference with file and reference diagnostics, and its targeted mutation is killed.
review_gate: codex {verdict:pass}
hygiene: Keep one focused behavioral fixture; prove the intended reference mutation fails and avoid source-text-only change detectors.
doc_sync: Orchestrator marks TD16 paid and records the merge on master after R3; implementation diff does not edit tracker or task metadata.
---

# T0-DEBT-REFERENCE-INTEGRITY

## Outcome and acceptance

Repay TD16 without changing tracker identities. Operator-facing project guidance must use stable local section links. References that describe upstream scaffold history must be explicitly labelled as upstream rather than silently resolving against this project's tracker.

The regression test must exercise the reference-validation behavior on a controlled fixture. It must fail for the intended stale/missing reference reason before implementation and pass only after the authority mapping is corrected.

## File-level implementation plan

1. Add the minimal seeded reference-integrity fixture in `scripts/selftest.ps1` and capture RED through `task.ps1 -Phase red`.
2. Correct only the six authority locations recorded in TD16.
3. Run the seeded shard, targeted mutation proof, full card DoD, and normal R3 ship.

## Rejected alternatives

- Renumbering current debts: breaks append-only tracker identity and existing merge evidence.
- Treating all bare historical TD mentions as local: preserves the ambiguity that caused this debt.
- Combining the stale task-count fix: TD21 has its own required fresh worktree.
