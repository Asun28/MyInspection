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
  - Resetting existing cards, importing advisory-on-error review rules, weakening checks or publishing remotely
non_goals:
  - Reimplementing T0-REVIEW-LOW-RISK or adjudicating its capped review
hygiene: Keep extraction in one small module with its own SelfCheck; test actual Git blobs and process invocation, not copied implementations.
doc_sync: Replace obsolete candidate-reviewer guidance with the exact BASE source and standalone-review distinction.
---

# T0-SHIP-REVIEW-BASE-BUNDLE

Adapt upstream PR #374 and TD278 together. This is an independent source-integrity repair,
not another review of the pending low-risk policy candidate. Preserve that candidate and reconcile
its implementation normally after a separately authorized verdict. Avoid new dependencies.

