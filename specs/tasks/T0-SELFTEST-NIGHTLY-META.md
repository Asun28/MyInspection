---
id: T0-SELFTEST-NIGHTLY-META
title: Run selftest aggregation stress checks nightly with explicit coverage receipts
depends_on: []
status: todo
branch: T0-SELFTEST-NIGHTLY-META
worktree: C:\wt\T0-SELFTEST-NIGHTLY-META
allow_paths:
  - scripts/selftest.ps1
  - .github/workflows/scaffold-selftest.yml
  - docs/DEVOPS-WORKFLOW.md
  - CLAUDE.md
  - specs/tasks/T0-SELFTEST-NIGHTLY-META.md
forbid:
  - Removing assertions, changing product verification, adding operating systems or shards, or raising timeouts
  - Skipping production-script behavior checks or treating absent meta receipts as success
non_goals:
  - Moving production guard mutation matrices to the nightly lane
  - Importing the upstream gate registry or changing unfiltered local selftest coverage
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture meta-routing
dod_exit: 0
dod_assert: The focused fixture exercises the actual meta selector and receipt checks, exact CI scheduling/flag wiring and aggregation flag propagation.
acceptance:
  - "A1 Default local and manual/nightly runs retain full coverage; ordinary scaffold pushes defer only the aggregation stress harness to the nightly lane"
  - "A2 A single explicit meta registry reports executed/deferred checks and rejects unknown, duplicated or missing receipts; production checks and failure propagation remain unchanged"
  - "A3 IncludeMeta is forwarded through all aggregation children; workflow preserves both OSes and five shards, adds one daily schedule and keeps the effective 20-minute timeout"
review_gate: codex {verdict:pass}
hygiene: Reuse the existing aggregation fixture and exact workflow assertions; no second runner or copied production logic.
doc_sync: Document full local default, explicit deferred coverage and daily/manual meta coverage in existing authority files.
---

# T0-SELFTEST-NIGHTLY-META

Adapt upstream PRs #362/#365/#369 only at the local selftest aggregation stress harness.
The regular path retains matrix validation, failure-protocol tests and production-script tests.
The user's 2026-09-05 instruction authorizes adoption. No runtime saving is claimed before measurement.

Validation (2026-09-06 NZ): the focused fixture replays the full production outer 8.2e
control envelope, replacing only its expensive stress body with an observable body. It proves
both IncludeMeta outcomes (EXECUTED and DEFERRED receipts), preserves the real `all` entry and
its terminating exit, and retains true/false receipts from each real child. Genuine RED
sources are `_local/meta-routing-genuine-red.log` (the former extracted-fragment replay
survived a forced preceding branch) and `_local/meta-all-genuine-red.log` (the former extracted
all-entry replay accepted an exit bypass); the full-envelope replay rejects those failures plus
selector inversion, completion deletion, and receipt-output deletions. Focused GREEN is
`_local/meta-routing-final-green.log` (exit `0`); mutation replay preserves the source SHA.

Combined evidence: the original `ee1` full run passed core, workflow, and scanner. A setup
issue in the separate review-policy integration at `17ac` was fixed in final `e7`, whose full
actual 17ac run passed in 574.42 s. The final seeded-remote run passed in 1007.14 s with nine
known skips and no prerequisite skips; `verify.ps1` passed in 32.10 s. These are combined-run
records only: no new single full-`all` run or idle-speed claim is made here.
