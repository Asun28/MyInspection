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

## Verification evidence (2026-09-06)

- `scripts/selftest.ps1` SHA-256: `EC631C06D93C2BB736ABA88A85F7E42C35D31C70132A89F3557FBFEBAE0FBE46`.
- `scripts/_validation.ps1` SHA-256: `81BBEF41800282FBEFDB362028212D79AAE6E280AB1465EB4E3C0DDDB0F8DF53`.
- Official RED observed `pure case 'skills' expected skills, got all` before the implementation.
  The final DoD passed (`.review/skill-routing-green1.log`), independently repeated during the first-ship prereview.
- Real Git fixtures select skills for committed, staged, dirty and untracked skill edits and within-skill renames;
  cross-boundary renames, frozen skills, edited config and mixed paths select all using the pinned base.
  The actual entry plus actual aggregator runs exactly core and workflow over dirty/untracked task snapshots,
  preserves meta/lint values and lint binding state, rejects both child failures and invalid receipts, and cleans snapshots.
  Its delay trap proves this route does not enter the existing 75-second seeded contention wait.
- Existing `selftest.ps1 -Fixture meta-routing` passed (`.review/skill-meta-aggregation.log`), including all's three children.
- Eight parseable, isolated actual-source mutants were rejected by named semantic assertions: skill class, frozen priority,
  subset switch, resolved source, omitted workflow, meta forwarding, entry exit and failure-protocol propagation.
  Results and unchanged source hashes: `.review/skill-mutations/`. No live source was mutated.

| Complete ordinary shard | Exit | Seconds | Source stable |
|---|---:|---:|---|
| core | 0 | 1119.8556 | yes |
| workflow | 0 | 766.8460 | yes |

Both emitted their final PASS sentinels. Timed runs and records: `.review/skill-shards/`.
These are sequential Windows validation durations, not a whole-suite before/after speed benchmark.
The smaller route and removed fixed wait are established by the real-entry tests above.
