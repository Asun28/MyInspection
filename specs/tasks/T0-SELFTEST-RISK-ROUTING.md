---
id: T0-SELFTEST-RISK-ROUTING
title: Select existing scaffold selftest coverage from pinned task changes
depends_on: []
parallelizable_with: []
status: todo
branch: T0-SELFTEST-RISK-ROUTING
worktree: C:\wt\T0-SELFTEST-RISK-ROUTING
allow_paths:
  - scripts/_validation.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - CLAUDE.md
  - specs/tasks/T0-SELFTEST-RISK-ROUTING.md
forbid:
  - Removing assertions, changing product tests, weakening security/frozen-contract checks or suppressing failures
  - Trusting a branch-edited card/config as the baseline authority for cheaper routing
non_goals:
  - Changing default unfiltered selftest, CI required-status names, R3 policy or shard composition
dod_command: pwsh -NoProfile -File scripts/_validation.ps1 -SelfCheck
dod_exit: 0
dod_assert: The real routing decision and repository-bound fixtures prove product, documentation, critical and unknown paths, baseline ownership and failure cases before the selftest entry point consumes them.
acceptance:
  - "A1 An explicit selftest TaskId resolves the matching worktree and baseline card against a pinned local base; invalid IDs, missing authority or unreadable repository state refuse cheaper routing"
  - "A2 Committed and pending changed paths are considered with rename source and destination; ordinary product paths select no scaffold checks, ordinary docs select core, critical/frozen/unknown paths select all"
  - "A3 No-scaffold output does not claim product verification passed; selected core/all runs retain nonzero failure propagation and existing shard contents"
  - "A4 The unfiltered selftest entry point remains full coverage; direct route tests and repository fixtures exercise actual production routing, including branch-only authority edits and unknown paths"
review_gate: codex {verdict:pass}
hygiene: Table-drive path classes against the real decision; use small temporary git repositories for authority and dirty-path cases, and existing selftest aggregation evidence for execution.
doc_sync: Document explicit task-scoped selftest routing and the separate product verify requirement in the existing workflow and entry contract.
---

# T0-SELFTEST-RISK-ROUTING

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.
