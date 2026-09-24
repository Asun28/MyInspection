---
id: T0-SELFTEST-SKILL-ROUTING
title: Route skill-only changes through existing core and workflow coverage without seeded product-independent regressions
status: merged
superseded_by: T0-SCAFFOLD-UPSTREAM-ADOPTION
depends_on: [T0-SELFTEST-META-EXPANSION,T0-SELFTEST-RISK-ROUTING]
allow_paths:
  - scripts/_validation.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - specs/tasks/T0-SELFTEST-SKILL-ROUTING.md
dod_command: pwsh -NoProfile -File scripts/_validation.ps1 -SelfCheck
dod_exit: 0
dod_assert: Real task-routing fixtures select core plus workflow for skill-only changes while frozen, unknown and mixed-risk surfaces retain all coverage.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 Changes confined to .claude/skills/ select existing core and workflow coverage, including hooks, vendored skill provenance, links, task-loop and lesson references"
  - "A2 Frozen paths, other .claude surfaces, scripts, critical contracts, invalid inputs and mixed classes retain all; ordinary product and ordinary document routes retain their existing behavior"
  - "A3 Pinned base authority and committed, staged, dirty, untracked and rename path collection remain effective; edited branch configuration cannot lower the route"
  - "A4 The real task entry dispatches exactly core and workflow over snapshots, forwards meta/lint flags and fails on either child failure or malformed receipt; ordinary all still dispatches its three children"
  - "A5 Run focused routing and aggregation fixtures plus the affected shards, recording actual route and timing evidence"
forbid:
  - Weakening product verify, frozen-path handling, unknown-path handling or CI matrix integrity
non_goals:
  - Introducing an upstream per-gate token framework or changing seeded assertions
hygiene: Extend existing routing fixtures and aggregator; no second test runner or duplicate gate implementation.
doc_sync: Describe the skill route and exact preserved fallback behavior in the existing operating documents.
---

# T0-SELFTEST-SKILL-ROUTING

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.

## R5 closure by upstream adoption — 2026-09-11

Superseded by [T0-SCAFFOLD-UPSTREAM-ADOPTION](./T0-SCAFFOLD-UPSTREAM-ADOPTION.md), delivered in [PR #297](https://github.com/Asun28/MyInspection/pull/297) (`d991cc928c4dd36607cc19eace40ec3cc8c01dd1`). `status: merged` records closure through that merged replacement; it does not claim this original card's implementation, DoD, RED, mutation or performance plan was independently completed. The original worktree and evidence are preserved; its old execution queue is retired.
