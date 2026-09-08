---
id: T0-SCAFFOLD-REMOTE-CARDS
title: Register the bounded remote scaffold adoption sequence
status: todo
depends_on: []
allow_paths:
  - specs/tasks/T0-SCAFFOLD-REMOTE-CARDS.md
  - specs/tasks/T0-SELFTEST-RISK-ROUTING.md
  - specs/tasks/T0-SELFTEST-NIGHTLY-META.md
  - specs/tasks/T0-SELFTEST-META-EXPANSION.md
  - specs/tasks/T0-SELFTEST-SKILL-ROUTING.md
  - specs/tasks/T0-SHIP-REVIEW-BASE-BUNDLE.md
  - specs/tasks/T0-REVIEW-LOW-RISK.md
  - specs/tasks/T0-INIT-ASSIGNMENT-ANCHORS.md
  - specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md
  - specs/tasks/T0-SELFTEST-PAGED-PERF.md
  - docs/TASK-BOARD.md
forbid:
  - Changing scripts, workflows, product code, remote repository settings or existing task requirements other than the explicitly bounded dependency correction
  - Treating local historical implementation or test results as current remote acceptance
  - Weakening existing gate, timeout, review-round or source-integrity requirements
non_goals:
  - Implementing the eight registered capabilities or importing divergent local master history
  - Resetting review counters or retiring unrelated cards
acceptance:
  - "A1 Only this scope card, the eight named pending adoption cards, the PAGED-PERF dependency and the corresponding two TASK-BOARD rows change; all eight target cards remain todo"
  - "A2 Dependencies encode RISK then NIGHTLY then META then SKILLS then BUNDLE then INIT; SKILLS also names RISK, LOW-RISK has no dependency, and SCAFFOLD-ONLY has no dependency and PAGED-PERF depends on both CI-PAGED-CONTRACT and SCAFFOLD-ONLY"
  - "A3 Existing task requirements and allow_paths are preserved, except explicit remote publication authorization, real remote dependency ordering and preservation of the current 30/20-minute CI timeout matrix"
  - "A4 BASE-BUNDLE and LOW-RISK record their historical findings and consumed one-time resets; a new worktree does not create a new review allowance"
  - "A5 Card validation, scope, verify, license, secret and diff-budget checks, independent R3 and candidate CI pass before merge"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; foreach ($id in @('T0-SELFTEST-RISK-ROUTING','T0-SELFTEST-NIGHTLY-META','T0-SELFTEST-META-EXPANSION','T0-SELFTEST-SKILL-ROUTING','T0-SHIP-REVIEW-BASE-BUNDLE','T0-REVIEW-LOW-RISK','T0-INIT-ASSIGNMENT-ANCHORS','T0-SELFTEST-SCAFFOLD-ONLY')) { $c=Get-Content -LiteralPath ('specs/tasks/'+$id+'.md') -Raw; if ([regex]::Matches($c,'(?m)^status: todo\r?$').Count -ne 1) { throw ('pending status '+$id) } }
dod_exit: 0
dod_assert: Existing card validation succeeds and each registered capability is explicitly pending; scope and independent review validate the exact path and semantic obligations.
review_gate: codex {verdict:pass}
hygiene: Reuse existing card checks and independent semantic preflight; this metadata registration adds no test runner or production implementation.
doc_sync: Record this registration PR as the remote starting point; leave the eight capability cards todo until their own verified implementation PRs merge.
---

# T0-SCAFFOLD-REMOTE-CARDS

The user authorized completion of all unfinished scaffold cards through independent worktrees and PRs on 2026-09-08. BASE-BUNDLE has four real prerequisites that were delivered only on the divergent local line. LOW-RISK, INIT-ASSIGNMENT-ANCHORS and SCAFFOLD-ONLY also lack remote card registration. This metadata-only task registers those eight bounded cards without importing product history or representing local results as remote delivery.

Use task-loop with Astra high for coordination and the configured independent Sol high R3. The existing review/reset history stays attached to BASE-BUNDLE and LOW-RISK. This is non-TDD card registration: use the documented SkipRed path while retaining every other ship gate. The eight implementation cards must each establish fresh behavior-based RED and current-source evidence.

The current remote gate 17a3 can execute the migration verifier before its intended failing test. SCAFFOLD-ONLY A5 supplies an isolated temporary test and deterministic fixture-only ordering; this repair is independent of pagination. Register it before PAGED-PERF, retaining every acceptance assertion and the five-minute performance target. TASK-BOARD changes are limited to these two dependency rows.
