---
id: T0-SHIP-REVIEW-BASE-BUNDLE
title: Bind both ship review legs and their executable helpers to the same immutable gate baseline
status: todo
depends_on: [T0-SELFTEST-SKILL-ROUTING]
allow_paths:
  - scripts/task.ps1
  - scripts/review.ps1
  - scripts/_review-bundle.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/QUALITY-RUBRIC.md
  - specs/tasks/T0-SHIP-REVIEW-BASE-BUNDLE.md
dod_command: pwsh -NoProfile -File scripts/_review-bundle.ps1 -SelfCheck
dod_exit: 0
dod_assert: Real Git fixtures prove immutable reviewer and helper bytes, a shared baseline for review input, both ship call sites and fail-closed extraction.
review_gate: codex {verdict:pass}
diagnosis: Local ship loads a candidate-owned reviewer, while remote ship loads mutable main code; upstream PR374 pins only review.ps1 and TD278 records separately resolved gate and review baselines.
acceptance:
  - "A1 Both ship legs extract review.ps1 and its underscore helper scripts from the exact baseline commit already used by scope, with byte-identical regular blobs and no mutable-worktree file fallback"
  - "A2 The reviewer resolves card, rubric, frozen paths and diff from that same explicit commit even if the base branch moves; standalone review retains current baseline resolution"
  - "A3 Candidate or main-working-copy changes to reviewer/config/helpers cannot affect the bundle; absent reviewer, unreadable blobs and nonregular entries fail before reviewer invocation"
  - "A4 Both real ship call sites use the bundle and pinned baseline; success and failure cleanup are exercised, with a visible diagnostic if a temporary bundle cannot be removed"
  - "A5 Existing review round caps, diff budgets, exact-head verdict checks and fail-closed behavior remain intact; integration fixtures use a local deterministic backend and the final real R3 remains required"
forbid:
  - Resetting existing cards, importing advisory-on-error review rules, weakening checks
non_goals:
  - Reimplementing T0-REVIEW-LOW-RISK or adjudicating its capped review
hygiene: Keep extraction in one small module with its own SelfCheck; test actual Git blobs and process invocation, not copied implementations.
doc_sync: Replace obsolete candidate-reviewer guidance with the exact BASE source and standalone-review distinction.
---

# T0-SHIP-REVIEW-BASE-BUNDLE

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.

Local candidate 90a8eaca repaired replacement-ref findings after two blocked R3 rounds. The user authorized one counter reset on 2026-09-08; it has already been consumed. Preserve the historical findings and current counter when moving to the remote candidate; a new worktree does not authorize another reset. Remote publication is explicitly authorized, while the remaining forbid and non-goals stay in force.
