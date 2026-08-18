---
id: T0-DEBT-R3-MOVING-REF-UNIX
title: Repair the 17ac Unix moving-ref git shim with a quoted absolute target (repay TD27)
depends_on: []
status: todo
branch: T0-DEBT-R3-MOVING-REF-UNIX
worktree: C:\wt\T0-DEBT-R3-MOVING-REF-UNIX
allow_paths:
  - scripts/selftest.ps1
forbid:
  - Editing scripts/review.ps1, scripts/task.ps1, scripts/_config.ps1, workflows, or task-card/tracker metadata from the implementation worktree
  - Changing TD2/T0-LICENSE-SCANNER licence-scanner behavior or TD9/8.2e aggregator-fixture behavior
  - Weakening, skipping, or replacing existing 17ac pinning assertions or mutation classifiers with bare nonzero checks
  - Network access, new dependencies, automatic publishing, or a shared/piggyback worktree
non_goals:
  - Rewriting R3 authority selection or scripts/review.ps1
  - Fixing other selftest defects, including TD9 core aggregation flakiness
  - Generic refactoring of unrelated PowerShell wrappers or CI
diagnosis:
  root_cause: The extensionless Unix git wrapper is launched by a pwsh shebang but expands PSScriptRoot empty on Ubuntu, so its child command resolves to /git-shim.ps1 instead of the fixture-local shim.
  same_class: The defect is confined to 17ac's non-Windows emitted git wrapper. The Windows git.ps1 wrapper has a real script root and must retain its current platform behavior; unrelated wrapper sites are out of scope.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded
dod_exit: 0
dod_assert: On Windows and Ubuntu, the seeded shard exits 0. A forced-POSIX hermetic 17ac probe runs the actual emitted body through pwsh against a separately located absolute target whose path contains whitespace and an apostrophe, forwards its sentinel arguments, and reports TD27-POSIX-SHIM; deleting the one POSIX invocation emission line must instead exit nonzero with ABSENT-TD27-POSIX-INVOKE and restore the fixture SHA. On Ubuntu the real extensionless shebang wrapper is selected in a fresh child, advances master immediately after merge-base, and preserves the old pinned diff/card/rubric/frozen authority OID.
review_gate: codex {verdict:pass}
hygiene: Reuse Invoke-LineDeletionMutation and the existing 17ac fixture; retain one direct forced-POSIX behavior probe plus one deletion mutant with an exact marker classifier and SHA restoration.
doc_sync: After merge, the orchestrator marks TD27 paid and records the PR on master. The implementation worktree changes neither tracker nor task-card metadata.
---

# T0-DEBT-R3-MOVING-REF-UNIX

## Outcome

Repay TD27 by making the 17ac moving-ref fixture's Unix git shim invoke its
fixture-local git-shim.ps1 through a safely quoted absolute path. This restores
the existing immutable-base test on Ubuntu without changing R3, licence
scanning, CI wiring, or the TD9 aggregator.

## R1 placement and queue rule

TD27 is the operational prerequisite that unblocks TD2's inherited seeded
failure. Keep exactly one active implementation:

1. Pause T0-LICENSE-SCANNER without editing it, and verify its existing
   C:\wt\T0-LICENSE-SCANNER worktree is clean. Preserve its committed tip.
2. From master, create this task's distinct branch and fresh worktree with the
   start phase. Edit only
   C:\wt\T0-DEBT-R3-MOVING-REF-UNIX\scripts\selftest.ps1 on branch
   T0-DEBT-R3-MOVING-REF-UNIX.
3. Do not touch TD2 while TD27 is active, and do not piggyback TD27 into the
   TD2 worktree or branch.
4. After TD27 merges, merge the updated master into the paused TD2 worktree
   without rewriting history, then rerun TD2's seeded and scanner evidence
   before resuming its normal ship path.

The card itself and TD27 tracker record are master-side orchestration metadata.
They are not implementation-worktree edits.

## Evidence and exact Linux reproduction

PR #20 Ubuntu seeded job 95566561516 on 0e350ee failed in 17ac after its fresh
child resolved the fixture git wrapper. The extensionless
td3-git-shim/git body was a pwsh shebang followed by a PSScriptRoot-relative
call; Ubuntu expanded that reference to /git-shim.ps1. Consequently the shim
never advanced master after the first merge-base, and the test reported missing
pinned authority sentinels rather than the actual wrapper failure.

Preserve the real non-Windows reproduction in 17ac:

- Write git-shim.ps1 beside the extensionless git wrapper, retain the
  #!/usr/bin/env pwsh first line, chmod the Unix wrapper executable, prepend its
  directory to PATH, and launch review.ps1 in a fresh pwsh child.
- Prove the child resolves that exact git wrapper, the marker appears after the
  first merge-base, and master equals the prepared moved OID.
- Keep the existing assertions that the prompt uses the original OID for the
  diff, complete base card, rubric, FrozenPaths, and task-card source label;
  moved-base sentinels must remain absent.

This is the actual Linux-host proof. Do not simulate chmod or shebang execution
on Windows.

## R2 RED first, including a local Windows-observable failure

Before correcting the Unix call, first extract the current two wrapper bodies
into the smallest local 17ac emitter that accepts the actual shim full path and
an explicit platform choice. Its initial POSIX branch must preserve the current
bad PSScriptRoot-relative behavior so that the new test is genuinely RED.

Add a hermetic forced-POSIX probe that runs on every host:

1. Create a temporary target git-shim.ps1 at an absolute path containing both
   whitespace and an apostrophe; put the wrapper in a different temporary
   directory.
2. Emit the POSIX body through the same 17ac emitter, save that exact body as a
   disposable git.ps1 harness, and invoke it with pwsh -NoProfile -File plus
   sentinel arguments. The shebang is a PowerShell comment in this local probe.
3. Assert only the target's dedicated TD27-POSIX-SHIM marker and exact argument
   receipt. The old PSScriptRoot form must fail because its script root is the
   separate wrapper directory.

This probe needs no Unix executable bit, shell, network, or host-specific PATH
semantics, so task.ps1 RED can record a semantic failing DoD locally on Windows
before the green change. A missing target, parser error, or unrelated failure is
not acceptable RED evidence.

## Green implementation boundary

Keep the Windows branch as git.ps1 with its current script-root behavior. Keep
the POSIX branch as extensionless git with its shebang and executable bit, but
emit an invocation of the resolved absolute git-shim.ps1 target. The path must
be a PowerShell single-quoted literal with every embedded apostrophe doubled,
then invoked with the call operator and @args. Do not interpolate the target
through PSScriptRoot in the emitted extensionless file, and do not use a shell
string or current working directory.

## Mutation proof and acceptance

Reuse Invoke-LineDeletionMutation; do not create a second mutation framework.
Uniquely locate the behavior-bearing POSIX invocation emission line, delete it
only in a fixture copy, parse the mutant, and execute the forced-POSIX probe.
Its classifier must require nonzero exit and the exact
ABSENT-TD27-POSIX-INVOKE marker; a control copy must remain green and every
fixture SHA must be restored. A generic crash, an earlier assertion, or a
missing mutation target is a setup failure, not mutation death.

After the locally recorded RED, run the seeded shard to green on Windows and
the existing Ubuntu seeded matrix job to green. The final two-host evidence must
show the forced-POSIX probe, real 17ac moving-ref proof, and its deletion mutant
all passing. No TD2, TD9, review.ps1, workflow, tracker, or task-card change is
part of the implementation diff.
