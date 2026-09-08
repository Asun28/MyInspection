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
forbid:
  - Changing scripts, workflows, product code, existing task contracts or remote repository settings
  - Treating local historical implementation or test results as current remote acceptance
  - Weakening existing gate, timeout, review-round or source-integrity requirements
non_goals:
  - Implementing the five registered capabilities or importing divergent local master history
  - Resetting review counters or retiring unrelated cards
acceptance:
  - "A1 Only this scope card and the five named pending adoption cards change; all five target cards remain todo"
  - "A2 Dependencies encode RISK then NIGHTLY then META then SKILLS then BUNDLE; SKILLS also names its real RISK dependency"
  - "A3 Existing task requirements and allow_paths are preserved, except explicit remote publication authorization, real remote dependency ordering and preservation of the current 30/20-minute CI timeout matrix"
  - "A4 BASE-BUNDLE records its historical findings and consumed one-time reset; a new worktree does not create a new review allowance"
  - "A5 Card validation, scope, verify, license, secret and diff-budget checks, independent R3 and candidate CI pass before merge"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; foreach ($id in @('T0-SELFTEST-RISK-ROUTING','T0-SELFTEST-NIGHTLY-META','T0-SELFTEST-META-EXPANSION','T0-SELFTEST-SKILL-ROUTING','T0-SHIP-REVIEW-BASE-BUNDLE')) { $c=Get-Content -LiteralPath ('specs/tasks/'+$id+'.md') -Raw; if ([regex]::Matches($c,'(?m)^status: todo\r?$').Count -ne 1) { throw ('pending status '+$id) } }
dod_exit: 0
dod_assert: Existing card validation succeeds and each registered capability is explicitly pending; scope and independent review validate the exact path and semantic obligations.
review_gate: codex {verdict:pass}
hygiene: Reuse existing card checks and independent semantic preflight; this metadata registration adds no test runner or production implementation.
doc_sync: Record this registration PR as the remote starting point; leave the five capability cards todo until their own verified implementation PRs merge.
---

# T0-SCAFFOLD-REMOTE-CARDS

The user authorized completion of all unfinished scaffold cards through independent worktrees and PRs on 2026-09-08. BASE-BUNDLE has four real prerequisites that were delivered only on the divergent local line. This metadata-only task registers those five bounded cards without importing product history or representing local results as remote delivery.

Use task-loop with Astra high for coordination and the configured independent Sol high R3. The existing review/reset history stays attached to BASE-BUNDLE. This is non-TDD card registration: use the documented SkipRed path while retaining every other ship gate. The five implementation cards must each establish fresh behavior-based RED and current-source evidence.
