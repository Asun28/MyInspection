---
id: T0-SELFTEST-NIGHTLY-META
title: Run selftest aggregation stress checks nightly with explicit coverage receipts
depends_on: [T0-SELFTEST-RISK-ROUTING]
status: merged
superseded_by: T0-SCAFFOLD-UPSTREAM-ADOPTION
branch: T0-SELFTEST-NIGHTLY-META
worktree: C:\wt\T0-SELFTEST-NIGHTLY-META
allow_paths:
  - scripts/selftest.ps1
  - scripts/_validation.ps1
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
  - "A3 IncludeMeta is forwarded through all aggregation children; workflow preserves both OSes and five shards, adds one daily schedule and preserves the target baseline timeout matrix (Windows seeded-git 30 minutes; all other OS/shard combinations 20 minutes)"
review_gate: codex {verdict:pass}
hygiene: Reuse the existing aggregation fixture and exact workflow assertions; no second runner or copied production logic.
doc_sync: Document full local default, explicit deferred coverage and daily/manual meta coverage in existing authority files.
---

# T0-SELFTEST-NIGHTLY-META

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.

## R5 closure by upstream adoption — 2026-09-11

Superseded by [T0-SCAFFOLD-UPSTREAM-ADOPTION](./T0-SCAFFOLD-UPSTREAM-ADOPTION.md), delivered in [PR #297](https://github.com/Asun28/MyInspection/pull/297) (`d991cc928c4dd36607cc19eace40ec3cc8c01dd1`). `status: merged` records closure through that merged replacement; it does not claim this original card's implementation, DoD, RED, mutation or performance plan was independently completed. The original worktree and evidence are preserved; its old execution queue is retired.
