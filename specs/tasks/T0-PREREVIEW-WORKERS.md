---
id: T0-PREREVIEW-WORKERS
title: prereview-workers.ps1 first writer - worker command contract, cleared allowlisted environment, custom-command transport, envelope unpacking with provenance stamping and the PRE-* code mapping
status: todo
depends_on: [T0-PREREVIEW-SCHEMA, T0-PREREVIEW-RUNNER, T0-PREREVIEW-PROTOCOL-DOC]
allow_paths:
  - scripts/prereview-workers.ps1
  - scripts/fixtures/prereview/workers/
  - specs/tasks/T0-PREREVIEW-WORKERS.md
dod_command: $t = (& pwsh -NoProfile -File scripts/prereview-workers.ps1 -SelfCheck *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-WORKERS-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: -SelfCheck exits 0 and prints [PREREVIEW-WORKERS-SELFCHECK-PASS] with a custom shim command on a private PATH: the envelope is taken from PRE_OUT, unpacked to schema-valid JSONL and stamped with provenance from the environment; empty output yields [PRE-NO-OUTPUT], a hanging shim yields exit 124 and [PRE-TIMEOUT], a missing command yields [PRE-WORKER-MISSING]; sentinel variables planted in the parent are absent in the child while the essentials, HTTPS_PROXY (loopback sink) and CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC are present, asserted by key names only.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 Start-PrereviewWorker -Role discoverer|lens -Command -PackDir -OutPath and Receive-PrereviewWorker -Handle -TimeoutSec wrap the runner (Start-BoundedWorker / Wait-BoundedWorker from scripts/_subprocess.ps1) with stdin = prompt.txt, cwd = the pack, and the environment dictionary built here."
  - "A2 The environment dictionary is built from an allowlist only: SystemRoot, windir, COMSPEC, PATHEXT, APPDATA, LOCALAPPDATA, ProgramData, TEMP, TMP, PATH, HOME, USERPROFILE, proxy and CA variables (HTTPS_PROXY, HTTP_PROXY, NO_PROXY, SSL_CERT_FILE, NODE_EXTRA_CA_CERTS), PRE_* and CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1; ANTHROPIC_API_KEY, DEEPSEEK_API_KEY, GH_TOKEN and every other variable are absent; the fixture plants sentinel variables in the parent and asserts by key names that they are absent in the child while the essentials are present."
  - "A3 A custom Prereview*Command value receives the prompt on stdin and writes one envelope JSON to PRE_OUT; the unpacker validates the envelope, writes records to JSONL, and stamps snapshot_tree, worker_id, model_id, lens, schema_version and schema_revision from the environment and facts.json, rejecting any record that already carries them ([PRE-BAD-RECORD])."
  - "A4 Runner outcomes map to codes: no output file or empty output [PRE-NO-OUTPUT]; TimedOut [PRE-TIMEOUT]; command not found [PRE-WORKER-MISSING]; built-in requested without PRE_LIVE=1 [PRE-LIVE-REFUSED]; for the discoverer these make the run incomplete, for the lens they make the worker skipped with skip_code."
  - "A5 Shims live in scripts/fixtures/prereview/workers/shims/: echo-env (key names only, never values), hang (never reads stdin, never exits), empty (writes nothing), valid (writes a fixture envelope); the self-check puts their directory first on a private PATH and sets HTTPS_PROXY to a loopback record-and-refuse listener with NO_PROXY empty."
  - "A6 -SelfCheck clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, sets CI=1, never sets PRE_LIVE, spawns only shims, and prints [PREREVIEW-WORKERS-SELFCHECK-PASS]; the built-in adapters are the next two cards."
forbid:
  - Implementing the claude or deepseek adapters here (next two cards)
  - Echoing environment values in any shim or log
  - Setting PRE_LIVE anywhere in this card
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop Environment.Clear() before the allowlist (planted sentinel visible); drop the provenance rejection (a pre-stamped record passes); drop the TimedOut mapping (hang case yields no code); drop the proxy variable from the allowlist (the sink is not reached in a later live-refusal case).
---

# T0-PREREVIEW-WORKERS

## Context

First writer of scripts/prereview-workers.ps1 (chain WORKERS -> WORKERS-CLAUDE -> WORKERS-DEEPSEEK). Owns the worker command contract of the plan: stdin prompt, env PRE_FACTS, PRE_OUT, PRE_MODEL, PRE_EFFORT, PRE_LENS, PRE_TIMEOUT_SEC, PRE_LIVE; one envelope JSON out; unpack and stamp.

## Notes

Split line: ~300 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
