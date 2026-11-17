---
id: T0-PREREVIEW-RUNNER
title: Bounded two-phase process runner scripts/_subprocess.ps1 and review.ps1 switched to it with inherited environment (zero behaviour change)
status: todo
depends_on: []
allow_paths:
  - scripts/_subprocess.ps1
  - scripts/review.ps1
  - scripts/selftest.ps1
  - docs/QUALITY-RUBRIC.md
  - scripts/fixtures/prereview/runner/
  - specs/tasks/T0-PREREVIEW-RUNNER.md
dod_command: $t = (& pwsh -NoProfile -File scripts/selftest.ps1 -Shard seeded-git -Fixture review-runner *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[SELFTEST-FIXTURE] review-runner PASS')) { exit 1 }
dod_exit: 0
dod_assert: The seeded-git shard runs 17gg and 17a..17y (incl. 17g, 17j, 17r, 17t(doc)) unchanged and green, then the review-runner fixture placed after 17y and before the seeded:17-git-main record early-exits with [SELFTEST-FIXTURE] review-runner PASS after proving inherit mode (a planted non-REVIEW_ sentinel reaches the reviewer shim), cleared mode (Start-BoundedWorker -Environment hides it), timeout exit 124 with tree kill, the 64 KiB no-deadlock case, absolute-path resolution, and that gate 6, 17z and 17ff literals plus every [R3-...] emission are still in review.ps1.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 scripts/_subprocess.ps1 exports Start-BoundedWorker (-FilePath resolved to an absolute path with Get-Command before ProcessStartInfo is built, -ArgumentList, -WorkingDirectory, -StdinFile, -StdoutFile, -StderrFile, -Environment) returning a handle, Wait-BoundedWorker -Handle -TimeoutSec returning {TimedOut, ExitCode} with the whole process tree killed and ExitCode 124 on timeout, and Invoke-BoundedWorker as the synchronous wrapper."
  - "A2 -Environment omitted or null inherits the parent environment unchanged; a dictionary means ProcessStartInfo.Environment.Clear() followed by exactly those entries; the fixture plants a non-REVIEW_ sentinel in the parent and proves it reaches the reviewer shim in inherit mode and is absent in cleared mode."
  - "A3 Stdin is fed asynchronously from -StdinFile and closed, stdout and stderr are drained asynchronously into their files; a shim that never reads stdin and never exits, fed a prompt over 64 KiB, ends with TimedOut and exit 124 instead of a pipe deadlock."
  - "A4 review.ps1 launches the reviewer through Invoke-BoundedWorker in inherit mode, so REVIEW_OUT, REVIEW_WT, REVIEW_MODEL, REVIEW_EFFORT and REVIEW_IGNORE_USER_CFG still arrive through the environment and the ReviewCommand contract and TRUST-MANIFEST R3 row stay true; timeout and success shims reproduce the previous {TimedOut, ExitCode} pairs."
  - "A5 The literals selftest pins stay in review.ps1: the first verdict = hashtable (gate 6), model_reasoning_effort= and REVIEW_EFFORT (17z), the 17ff anchors, and every [R3-...] emission (17t(doc)); the fixture greps them."
  - "A6 The fixture review-runner is a ValidateSet entry placed after 17y and before the seeded:17-git-main record in the seeded-git main region with an early exit (the through-gate8 shape); docs/QUALITY-RUBRIC.md gains one pointer paragraph naming the runner (DocSyncMap 14f pairs review.ps1 with it)."
  - "A7 The diff measured with review.ps1 -SizeOnly before RED stays under about 450 changed lines; the prompt assembly is not touched (that is T0-PREREVIEW-FACTS-EXTRACT)."
forbid:
  - Changing the reviewer prompt, the verdict parsing, .rounds accounting or the environment the reviewer receives
  - Moving the prompt assembly or any [R3-...] emission out of review.ps1
  - Any second process launcher elsewhere in the repository
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Single-line deletion mutants that must turn the fixture red: drop Environment.Clear() in cleared mode (sentinel visible); drop the absolute-path resolution (fake .cmd on PATH not found); drop the tree kill (child of the hanging shim survives); make stdin synchronous (64 KiB case hangs to the outer timeout); drop the QUALITY-RUBRIC pointer (14f red on the real run).
doc_sync: docs/QUALITY-RUBRIC.md pointer paragraph in the same commit; TASK-BOARD status after merge.
---

# T0-PREREVIEW-RUNNER

## Context

First card of the review.ps1 chain (RUNNER -> FACTS-EXTRACT -> FACTS -> ROUND-ACCOUNTING). It moves the reviewer launcher into a reusable two-phase runner so the prereview workers (T0-PREREVIEW-WORKERS) can start two processes in parallel with a cleared, allowlisted environment, while the codex reviewer keeps inheriting the parent environment exactly as today.

## Runner contract carried by this card

- Start-BoundedWorker -FilePath -ArgumentList -WorkingDirectory -StdinFile -StdoutFile -StderrFile [-Environment IDictionary] -> handle. The executable is resolved with Get-Command to an absolute path before ProcessStartInfo is built (CreateProcess resolves .exe only, so a .cmd or .ps1 shim first on PATH would otherwise be invisible).
- Wait-BoundedWorker -Handle -TimeoutSec -> {TimedOut, ExitCode}; on timeout the process tree is killed and ExitCode is 124.
- Invoke-BoundedWorker = Start + Wait in one call, inherit mode; review.ps1 uses only this.
- Environment modes: omitted/null = inherit; dictionary = Environment.Clear() then the dictionary. The worker allowlist itself is built by T0-PREREVIEW-WORKERS, not here.
- Stdin is fed asynchronously from a file and closed; stdout/stderr are drained asynchronously to files (no 64 KiB pipe deadlock).

## Notes

Split line: ~400 lines; the card owns only the launcher move and the runner; the prompt assembly move is T0-PREREVIEW-FACTS-EXTRACT. Fixture review-runner sits in the seeded-git main region after 17y (17z lives in seeded-remote; its literals are asserted by grep here). Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
