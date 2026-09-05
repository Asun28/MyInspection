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

Validation (2026-09-06 NZ): meta-routing passed with the actual all entry and every child
receiving both flag values. Exact 8.2e source replay passed with IncludeMeta=false and a
DEFERRED receipt. Full enabled replay executed all stress cases and failed only its existing
early-exit controller watchdog while concurrent jobs were active; rerunning that exact case
on unchanged source passed, with EXECUTED receipt. The initial failed log is retained beside
the passing recheck under `_local/`; no timeout or assertion was changed to obtain it.
A full integrated selftest remains required before final delivery.
