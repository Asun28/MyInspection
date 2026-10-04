---
id: T0-PREREVIEW-WORKERS-DEEPSEEK
title: prereview-workers.ps1 -Adapter deepseek - child-process lens adapter with endpoint-host logging before HTTP and an offline HttpListener replay
status: todo
depends_on: [T0-PREREVIEW-WORKERS-CLAUDE]
allow_paths:
  - scripts/prereview-workers.ps1
  - scripts/fixtures/prereview/workers-deepseek/
  - specs/tasks/T0-PREREVIEW-WORKERS-DEEPSEEK.md
dod_command: pwsh -NoProfile -File scripts/prereview-workers.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; $t = (& pwsh -NoProfile -File scripts/prereview-workers.ps1 -SelfCheck -Adapter deepseek *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-DEEPSEEK-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck -Adapter deepseek exits 0 and prints [PREREVIEW-DEEPSEEK-SELFCHECK-PASS] after a local HttpListener on PRE_LENS_ENDPOINT replays a valid envelope (parsed and stamped), bad JSON (=> [PRE-BAD-RECORD], lens skipped) and an empty body (=> [PRE-NO-OUTPUT]); with PRE_LIVE=1 set only for the direct adapter call and the key absent, the logged line is lens_endpoint=production and the listener saw zero requests; a dummy key value never appears in any log; a missing key => [PRE-WORKER-MISSING].
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 -Adapter deepseek runs as a child process launched by the runner (timeout and PRE_LIVE gating apply), posts prompt.txt with Invoke-RestMethod to the DeepSeek anthropic-compatible messages endpoint using PRE_MODEL (deepseek-v4-flash by default, PrereviewLensDocModel for documents over PrereviewMaxFileBytes), reads DEEPSEEK_API_KEY at call time, and parses one envelope JSON object out of the response text."
  - "A2 Before any HTTP call and before reading the key the adapter logs one ASCII line, lens_endpoint=production or lens_endpoint=override; PRE_LENS_ENDPOINT is honoured only while PRE_LIVE is unset, and with PRE_LIVE=1 the production host is fixed regardless of the environment."
  - "A3 The self-check replays through a local HttpListener bound to PRE_LENS_ENDPOINT: a valid envelope is parsed, unpacked and stamped; a bad JSON body yields [PRE-BAD-RECORD] with the lens skipped and its records discarded whole; an empty body yields [PRE-NO-OUTPUT]."
  - "A4 With PRE_LIVE=1 set only inside one direct adapter call, the key removed and the listener running, the logged line is lens_endpoint=production and the listener records zero requests; this is the single registered exception to the rule that no -SelfCheck sets PRE_LIVE and it holds only under those three conditions together."
  - "A5 A missing DEEPSEEK_API_KEY yields [PRE-WORKER-MISSING]; the key value never appears in stdout, stderr or worker logs (the fixture greps a dummy value); the key reaches this adapter only, never the claude worker."
  - "A6 -SelfCheck clears inherited PRE_LIVE and PRE_LENS_ENDPOINT before it starts, sets CI=1 and the private fake-claude PATH for every child, and prints [PREREVIEW-DEEPSEEK-SELFCHECK-PASS]; the plain WORKERS -SelfCheck stays green."
  - "A7 The adapter passes -Proxy $env:HTTPS_PROXY explicitly to Invoke-RestMethod; in the PRE_LIVE=1 case with a planted fake key the loopback sink records zero connections and the logged host is the production host (a mutant honouring the override logs the listener host, a mutant dropping -Proxy is caught by the sink)."
forbid:
  - Any request to a non-listener host during -SelfCheck
  - Claiming a structured-output guarantee for the endpoint (a bad envelope is a skip, never a blocker)
  - Logging the key value; honouring PRE_LENS_ENDPOINT under PRE_LIVE=1
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: A mutant that honours PRE_LENS_ENDPOINT under PRE_LIVE=1 must log lens_endpoint=override (A4 red); a mutant that logs after the key check must fail the ordering assertion; one single-line-deletion mutant per code mapping.
---

# T0-PREREVIEW-WORKERS-DEEPSEEK

## Context

Third writer of scripts/prereview-workers.ps1. The DeepSeek lens adapter runs as a child process through the same runner so timeout and PRE_LIVE gating apply; the endpoint host is logged before any HTTP; PRE_LENS_ENDPOINT is honoured only while PRE_LIVE is unset (offline fixtures replay bodies through a local HttpListener).

## Notes

Split line: ~250 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
