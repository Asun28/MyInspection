---
id: T0-SELFTEST-META-EXPANSION
title: Adapt v0.47 nightly meta coverage across selftest selection, receipts and operating contracts
status: todo
depends_on: []
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

User authorized v0.47 adoption on 2026-09-06. Adapt upstream T275/T279 by behavior, not label.
The local 17ac is a reviewer trust-boundary regression, unlike upstream's mutation-runner 17ac.
Shared selftest edits are delivered before the dependent cards; this card does not reset any existing R3 counter.

## Verification evidence (2026-09-06)

Final `scripts/selftest.ps1` SHA-256: `745485BFC638D4E2D59A83B17BC5EDCEC65866410FED2DBF0957968ED75BA40A`.
The real RED rejected the old default with `META-EXPANSION-DEFAULT`. A later handler-isolation
regression also failed with `META-EXPANSION-SCOPE` before fixing both replay handlers to `Function:local:`.
Final DoD passed, including actual entry/child forwarding, receipt failures and selector/completion/outer-condition mutations.
Independent read-only preflight found no remaining issue after these repairs; it is not the official R3 verdict.

| Final source run | Exit | Seconds | Meta receipts |
|---|---:|---:|---|
| `selftest.ps1 -Shard core -IncludeMeta` | 0 | 1095.24 | `8.2e/protocol`, `8.2e/harness`: EXECUTED |
| `selftest.ps1 -Shard workflow -IncludeMeta` | 0 | 901.72 | `1i/fixtures`: EXECUTED |

Both full shard runs kept the source hash unchanged and emitted their PASS sentinels.
Logs are retained under `.review/meta-shards-final3/`; the focused DoD log is `.review/meta-expansion-final-dod3.log`.

Matched scope measurement used the same final source in an isolated instrumented copy, after the shard runs.
Each real scope ran once with IncludeMeta true (the previously unconditional bodies), then once with false;
only the source-path environment and measurement wrapper were supplied. The 1i live source check ran in both modes.

| Scope | Included seconds | Ordinary seconds |
|---|---:|---:|
| `1i/fixtures` plus live source check | 20.2193 | 2.0871 |
| `8.2e/protocol` | 13.1930 | 0.0004 |

The added control-envelope check cost 3.5661 seconds. These two scopes saved 31.3248 seconds in this
single matched run, or about 27.7588 seconds after that added cost. This is a scoped measurement,
not a full-suite before/after benchmark or a CI speed promise. It excludes the already registered
aggregation stress harness. Result and output: `.review/meta-cost/result.json` and `stdout.log`.
The original source remained unchanged; two failed measurement-wrapper attempts are retained separately.
