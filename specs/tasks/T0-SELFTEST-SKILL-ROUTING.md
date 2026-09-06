---
id: T0-SELFTEST-SKILL-ROUTING
title: Route skill-only changes through existing core and workflow coverage without seeded product-independent regressions
status: todo
depends_on: [T0-SELFTEST-META-EXPANSION]
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

Adapt upstream PR #371 using the local shard boundary. Core plus workflow retains gates 9/11/14/15/16;
the repository has no compatible upstream token dispatcher to copy. Wait for the preceding selftest change.

