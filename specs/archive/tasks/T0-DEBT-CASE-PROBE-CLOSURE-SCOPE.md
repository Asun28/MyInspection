---
id: T0-DEBT-CASE-PROBE-CLOSURE-SCOPE
title: Make 17cc case mutation probes host-independent (repay TD25)
depends_on: []
status: merged
branch: T0-DEBT-CASE-PROBE-CLOSURE-SCOPE
worktree: C:\wt\T0-DEBT-CASE-PROBE-CLOSURE-SCOPE
allow_paths:
  - scripts/selftest.ps1
forbid:
  - Reopening or editing the paid TD23 card, tracker history, or its archived evidence
  - Weakening existing case-mut marker, exit-code, classifier, or SHA-restoration assertions
  - Converting Invoke-CaseProbe itself to a GetNewClosure scriptblock that freezes LASTEXITCODE
  - Editing tracker, task-card, TASK-BOARD, or unrelated harness code in the implementation worktree
non_goals:
  - Changing Gradle manifest discovery or licence policy
  - Fixing TD3, TD9, TD22, or any other selftest debt
  - Refactoring unrelated GetNewClosure sites
diagnosis:
  root_cause: The 17cc case-mut closure invokes Invoke-CaseProbe by name, but GetNewClosure captures variables rather than guaranteeing the enclosing function binding survives in every no-profile host.
  same_class: All seven case mutations share this single probe construction site; the other 17cc closures either explicitly capture their helper or reference captured variables only.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -Command "& '.\scripts\selftest.ps1' -Shard seeded"
dod_exit: 0
dod_assert: The real case-mut probe keeps exact marker, child exit, classifier, and SHA-restoration results after its ambient Invoke-CaseProbe binding is removed; both File and Command seeded entry hosts exit 0.
review_gate: codex {verdict:pass}
hygiene: Keep one explicit helper capture and one real-probe isolation mutation; deleting the capture or restoring the bare helper call must fail with a dedicated TD25 code.
doc_sync: Orchestrator marks TD25 paid and records the merge on master after R3; implementation diff does not edit tracker or task metadata.
---

# T0-DEBT-CASE-PROBE-CLOSURE-SCOPE

## Outcome and acceptance

Make the existing 17cc(case-mut) probe independent of ambient PowerShell function lookup without changing its child-process exit semantics. Preserve `Invoke-CaseProbe` as a function so `$LASTEXITCODE` is read immediately after each child invocation.

## File-level implementation plan

1. Add a focused isolation check around the real case-mut probe, remove the ambient `Invoke-CaseProbe` binding, and capture formal RED through `task.ps1 -Phase red`.
2. Capture `${function:Invoke-CaseProbe}` once and invoke that captured command from every case-mut closure; restore the ambient binding in `finally` for the remaining seeded suite.
3. Add deletion/bare-call mutation coverage with a dedicated TD25 diagnostic, then run both complete seeded hosts and normal ship gates.

## Key assumptions and exclusions

- `pwsh -NoProfile -File` and `pwsh -NoProfile -Command '& script'` are both supported review hosts.
- The child process must continue to determine each mutation's fresh exit code; no cached or closure-captured `$LASTEXITCODE` is acceptable.
- TD23 remains closed: it handled `Invoke-MarkerAssertion` at a different closure site and did not authorize this helper.

## Rejected alternative

Declaring the Command host unsupported was rejected because R3 may legitimately invoke a script through that host and no repository contract excludes it.
