---
id: T0-SELFTEST-META-EXPANSION
title: Adapt v0.47 nightly meta coverage across selftest selection, receipts and operating contracts
status: todo
depends_on: [T0-SELFTEST-NIGHTLY-META]
allow_paths:
  - scripts/selftest.ps1
  - .github/workflows/scaffold-selftest.yml
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - CLAUDE.md
  - specs/tasks/T0-SELFTEST-META-EXPANSION.md
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture meta-routing
dod_exit: 0
dod_assert: The actual production selections defer exactly the registered harness tests by default, execute them with IncludeMeta, and reject missing or contradictory receipts.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 The no-argument entry defaults IncludeMeta to false; explicit true and false propagate unchanged to every child; daily and manual jobs explicitly include meta"
  - "A2 Defer the existing 8.2e aggregation stress harness plus 1i synthetic gate-ID cases and 8.2e failure/skip protocol fixtures; each site reports DEFERRED or EXECUTED and incomplete, duplicate or unknown receipts fail"
  - "A3 The live source gate-ID verdict, real CI wiring and production-script behavior checks remain outside meta selection; in particular local 17ac stays on the ordinary path"
  - "A4 Focused tests exercise actual control envelopes in both modes and kill selector, completion and default/forwarding mutations with named behavior failures"
  - "A5 Run the final affected core and workflow shards with IncludeMeta; record matched before/after timings for the changed fixture scopes without promising an unmeasured full-suite speedup"
forbid:
  - Dropping assertions, moving production enforcer tests to nightly, adding CI jobs or increasing timeouts
non_goals:
  - Copying upstream gate numbers, changing review policy, resolving existing capped cards or optimizing pagination
hygiene: Reuse the current meta-routing fixture and receipt protocol; retain live contract checks and measure actual selected bodies.
doc_sync: Update the default and precise deferred set in existing operating contracts; no new documentation framework.
---

# T0-SELFTEST-META-EXPANSION

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.
