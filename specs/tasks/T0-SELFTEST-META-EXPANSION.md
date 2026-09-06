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

Final `scripts/selftest.ps1` SHA-256: `3CDA762926FDEC0D8ACE1A8955B738C68975BA21D1A6FDA83C654AD80315A7D2`.
The real RED rejected the old default with `META-EXPANSION-DEFAULT`. A later handler-isolation
regression also failed with `META-EXPANSION-SCOPE` before fixing both replay handlers to `Function:local:`.
R3 round 1 blocked feature `23120579` because lightweight production source guards were inside the protocol selector.
The repair keeps all four failure-protocol call checks/deletion mutations and both terminal skip/overlap guards ordinary.
The new regression failed first with `META-LIVE-PLACEMENT`; the repaired DoD passed (`meta-live-green2.log`).
It covers actual entry/child forwarding, receipts, selector/completion/outer-condition mutations and six production-call
deletions through the real ordinary-mode control envelope. Independent source mutations re-deferring either live-check
group are rejected with `META-LIVE-MUTATION`; deleting the actual terminal overlap-collection assignment is also rejected
by that ordinary envelope (`meta-live-wiring-mutations2.log`). The standalone `skip-ledger` fixture passed too.

| Final source run | Exit | Seconds | Meta receipts |
|---|---:|---:|---|
| `selftest.ps1 -Shard core -IncludeMeta` | 0 | 1090.16 | `8.2e/protocol`, `8.2e/harness`: EXECUTED |
| `selftest.ps1 -Shard workflow -IncludeMeta` | 0 | 688.55 | `1i/fixtures`: EXECUTED |

Both full shard runs kept the source hash unchanged and emitted their PASS sentinels.
Logs are retained under `.review/meta-shards-final4/`; focused and mutation logs above are under `.review/`.

Matched scope measurement used the same final source in an isolated instrumented copy, after the shard runs.
Each real scope ran once with IncludeMeta true (the previously unconditional bodies), then once with false;
only the source-path environment and measurement wrapper were supplied. Both scopes retain their live source checks in both modes.

| Scope | Included seconds | Ordinary seconds |
|---|---:|---:|
| `1i/fixtures` plus live source check | 24.9541 | 2.2949 |
| `8.2e/protocol` plus live source checks | 24.8368 | 1.4849 |

The added control-envelope checks cost 16.4724 seconds. These two scopes saved 46.0111 seconds in this
single matched run, or about 29.5387 seconds after that added cost. This is a scoped measurement,
not a full-suite before/after benchmark or a CI speed promise. It excludes the already registered
aggregation stress harness. Result and output: `.review/meta-cost-r3-repair/result.json` and `stdout.log`.
Source stayed unchanged throughout. Earlier-source and failed measurement-wrapper logs are retained separately,
and are not evidence for this final candidate.
