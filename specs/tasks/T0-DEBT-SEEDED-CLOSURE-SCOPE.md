---
id: T0-DEBT-SEEDED-CLOSURE-SCOPE
title: Make seeded mutation closures self-contained (repay TD23)
depends_on: []
status: todo
branch: T0-DEBT-SEEDED-CLOSURE-SCOPE
worktree: C:\wt\T0-DEBT-SEEDED-CLOSURE-SCOPE
allow_paths:
  - scripts/selftest.ps1
forbid:
  - Editing TD21 task-inventory logic or CLAUDE.md
  - Weakening 17cc marker, A/B control, exit-code, or SHA-restoration assertions
  - Editing tracker, task-card, or TASK-BOARD metadata in the implementation worktree
  - Reusing an existing worktree or bypassing formal RED
non_goals:
  - Changing license policy or Gradle manifest discovery
  - Fixing archived-card references tracked by TD22
  - Broad refactoring of the seeded shard
diagnosis:
  root_cause: The 17cc GetNewClosure probe resolves Invoke-MarkerAssertion dynamically from an enclosing script scope that is not guaranteed to remain visible in an isolated review host.
  same_class: The A/B 17cc mutation probe is the only closure in the seeded shard that directly calls this outer helper by name; other helper calls are not part of this card.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: An isolated fixture built from the real 17cc probe fails when the outer function binding is removed before the fix, then passes with exact marker/exit results after the assertion helper is explicitly captured; the full seeded shard remains green.
review_gate: codex {verdict:pass}
hygiene: Keep one explicit helper capture and one focused isolation mutation; do not alter TD21 behavior.
doc_sync: Orchestrator marks TD23 paid and records the merge on master after R3; implementation diff does not edit tracker/task metadata.
---

# T0-DEBT-SEEDED-CLOSURE-SCOPE

## Outcome and acceptance

Make the existing 17cc A/B mutation probe independent of ambient function-name lookup. Preserve all existing marker, control, child-exit, and SHA-restoration behavior while making the same closure callable after its enclosing helper binding is no longer visible.

## File-level implementation plan

1. Add a focused seeded fixture that constructs the actual probe shape, removes the ambient helper binding, invokes the closure, and requires the existing structured marker/exit result; capture formal RED.
2. Explicitly capture the assertion helper before `GetNewClosure()` and invoke that captured script block inside the A/B probe.
3. Run the seeded shard, project verify, and independent R3 ship from the dedicated worktree.

## Rejected alternatives

- Keeping the fix in TD21: violates one-debt/one-card traceability.
- Weakening TD21 to a focused command: hides a real seeded-host portability defect.
- Replacing the 17cc mutation suite: unnecessary and risks losing existing license-scanner evidence.

