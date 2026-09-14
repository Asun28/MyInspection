---
id: T0-PREREVIEW-WORKERS-CLAUDE
title: prereview-workers.ps1 second writer - the built-in claude adapter with absolute-path resolution, inline settings and schema projection, PRE_LIVE guard and the offline argv probe
status: todo
depends_on: [T0-PREREVIEW-WORKERS]
allow_paths:
  - scripts/prereview-workers.ps1
  - scripts/fixtures/prereview/workers-claude/
  - specs/tasks/T0-PREREVIEW-WORKERS-CLAUDE.md
dod_command: pwsh -NoProfile -File scripts/prereview-workers.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; $t = (& pwsh -NoProfile -File scripts/prereview-workers.ps1 -SelfCheck -Adapter claude *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-CLAUDE-SELFCHECK-PASS]')) { exit 1 }
dod_exit: 0
dod_assert: The plain prereview-workers.ps1 -SelfCheck (T0-PREREVIEW-WORKERS) stays green, then -SelfCheck -Adapter claude exits 0 and prints [PREREVIEW-CLAUDE-SELFCHECK-PASS] after the adapter resolves claude to the fake's absolute path, the fake receives --settings JSON equal to the project deny list and the inline --json-schema projection byte-equal to worker-envelope.min.json, the envelope is extracted from the CLI wrapper JSON, a built-in launch without PRE_LIVE yields [PRE-LIVE-REFUSED] with no spawn, the four projection checks pass, and the argv probe with HOME and USERPROFILE on an empty temp directory exits with a not-logged-in message and zero proxy-sink connections.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 The adapter builds the argv claude -p --safe-mode --restricted --tools Read,Grep,Glob --allowedTools Read,Grep,Glob --permission-prompts none --no-session-persistence --settings (inline JSON generated at run time from .claude/settings.json permissions.deny) --output-format json --json-schema (inline contents of scripts/fixtures/prereview/schema/worker-envelope.min.json) --model PRE_MODEL --effort PRE_EFFORT, cwd = the pack directory, prompt on stdin, launched through Start-PrereviewWorker."
  - "A2 The executable is resolved with Get-Command to an absolute path before the runner is called, and the fixture asserts the resolved path equals the fake claude placed first on a private PATH; the fake records its own path and argv into a marker file that the fixture compares."
  - "A3 A built-in launch without PRE_LIVE=1 yields [PRE-LIVE-REFUSED] and spawns nothing; the two adapter fixture cases that need the fake to run set PRE_LIVE=1 in-process only after asserting the fake resolution, CI=1 and the loopback HTTPS_PROXY sink, and clear it when the case ends (the registered exception to the no-PRE_LIVE rule)."
  - "A4 The envelope is extracted from the claude CLI wrapper JSON (structured output), unpacked to JSONL and stamped with provenance by the WORKERS unpacker; a wrapper without structured output yields [PRE-NO-OUTPUT]."
  - "A5 The four projection checks live here: worker-envelope.min.json derives from the record schema's $defs, is at most 4096 bytes, uses only the allowlisted keywords (type, properties, required, additionalProperties, enum, const, items, anyOf, $ref, $defs, description) and is byte-stable through PowerShell native-argument quoting."
  - "A6 The argv probe runs the real claude CLI with the full flag set and HOME and USERPROFILE pointed at an empty temp directory (no OAuth state), with the proxy sink armed: it exits non-zero with a not-logged-in message, never a parameter error, and the sink records zero connections; the probe result and CLI version are recorded in the card body as evidence, not in the DoD."
  - "A7 -SelfCheck -Adapter claude clears inherited PRE_LIVE and PRE_LENS_ENDPOINT, spawns only the fake, and prints [PREREVIEW-CLAUDE-SELFCHECK-PASS]; the plain -SelfCheck of T0-PREREVIEW-WORKERS stays green and runs first in the DoD."
forbid:
  - Using --bare (it skips the OAuth login state)
  - Passing a file path to --json-schema or --settings (the CLI takes inline JSON)
  - Setting PRE_LIVE outside the two registered adapter cases
  - Reaching any network host from a fixture (the proxy sink must record zero connections)
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Deletion mutants: drop the absolute-path resolution (fake not reached, marker absent); drop the PRE_LIVE guard (a launch without PRE_LIVE spawns); drop the keyword allowlist check (a projection with minimum passes); drop the byte-stability check (a projection with an unbalanced quote passes); drop the deny-list generation (settings JSON empty).
doc_sync: TASK-BOARD status after merge; the argv probe evidence (CLI version, exit message) goes into the card body.
---

# T0-PREREVIEW-WORKERS-CLAUDE

## Context

Second writer of scripts/prereview-workers.ps1 (chain WORKERS -> WORKERS-CLAUDE -> WORKERS-DEEPSEEK). T0-PREREVIEW-WORKERS delivers the command contract, the cleared allowlisted environment, the custom-command transport and the envelope unpacker; this card adds the built-in Claude adapter.

## Adapter contract carried by this card

- Flags verified against claude 2.1.270 on 2026-09-14: --safe-mode, --restricted, --tools, --allowedTools, --permission-prompts, --no-session-persistence, --settings (inline JSON), --json-schema (inline JSON), --output-format json, --model, --effort. Re-verify at implementation and record the version.
- Subscription OAuth only; ANTHROPIC_API_KEY is stripped by the WORKERS environment; CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1.
- cwd = the pack directory (never the worktree): the worker reads facts, diff, card, rubric, checklists, changed-file slices and the exported snapshot tree only.

## Notes

Split line: ~300 lines. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
