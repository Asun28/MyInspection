---
id: T0-PREREVIEW-PROTOCOL-DOC
title: docs/PREREVIEW-PROTOCOL.md (1a protocol and status-code table), TRUST-MANIFEST rows for both providers, CLAUDE.md index row and task-loop 4.6 advisory sentence
status: todo
depends_on: [T0-PREREVIEW-SCHEMA]
allow_paths:
  - docs/PREREVIEW-PROTOCOL.md
  - docs/TRUST-MANIFEST.md
  - CLAUDE.md
  - .claude/skills/task-loop/SKILL.md
  - scripts/fixtures/prereview/protocol/
  - specs/tasks/T0-PREREVIEW-PROTOCOL-DOC.md
dod_command: $t = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 -Anchors docs/PREREVIEW-PROTOCOL.md *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-ANCHORS-OK]')) { exit 1 }; $r = (Get-Content docs/TRUST-MANIFEST.md | Where-Object { $_ -match '^\s*\|' }) -join ' '; if (-not ($r.Contains('PrereviewDiscoverCommand') -and $r.Contains('PrereviewLensCommand'))) { exit 1 }; $g = Get-Content CLAUDE.md -Raw; if (-not $g.Contains('docs/PREREVIEW-PROTOCOL.md') -or $g.Contains('scripts/prereview')) { exit 1 }; if (-not (Get-Content .claude/skills/task-loop/SKILL.md -Raw).Contains('prereview.ps1 run')) { exit 1 }; if ((Get-Item docs/PREREVIEW-PROTOCOL.md).Length -gt 30720) { exit 1 }
dod_exit: 0
dod_assert: -Anchors prints [PREREVIEW-ANCHORS-OK]: each of the 20 enum codes appears exactly once in the first table under the Status codes heading and the table has no code outside the enum; TRUST-MANIFEST table rows (lines starting with |) contain PrereviewDiscoverCommand and PrereviewLensCommand; CLAUDE.md cites docs/PREREVIEW-PROTOCOL.md and contains no scripts/prereview path literal; task-loop SKILL.md names prereview.ps1 run; the protocol file is at most 30720 bytes; routed selftest (gates 11 and 11c) green is attached as pre-R3 evidence.
review_gate: codex {verdict:pass}
plan_ref: docs/TASK-BOARD.md#pr-review-v2
parallelizable_with: [T0-PREREVIEW-RUNNER, T0-PREREVIEW-FACTS-EXTRACT, T0-PREREVIEW-FACTS, T0-PREREVIEW-CHECKLISTS, T0-PREREVIEW-RECORDS, T0-PREREVIEW-PROMPT, T0-PREREVIEW-FACTS-LIB, T0-PREREVIEW-FACTPACK, T0-PREREVIEW-FACTPACK-SLICES, T0-PREREVIEW-STATE-1A]
acceptance:
  - "A1 docs/PREREVIEW-PROTOCOL.md (at most 30720 bytes) documents the 1a protocol by the document name, never by a not-yet-existing script path: canonical order, what enters the pack and what workers see, the worker command contract, the record and coverage shapes, the finding taxonomy C1..C7, the first-live-run checklist, link-r3 and recall usage, and the 1a checkpoint rule; 1b content is limited to its status-code rows plus one pointer paragraph."
  - "A2 A table under the heading Status codes (the first table there) lists each of the 20 enum codes exactly once with owner, phase, meaning and counts-as, matching specs/prereview-record.schema.json both ways under -Anchors; [PRE-NO-NETWORK-IN-CI] and [PRE-RUN-DISABLED] are owned by RUN, the former counting as built-in workers refused while custom commands and shims proceed."
  - "A3 docs/TRUST-MANIFEST.md gains two table rows whose boundary cell matches neither MCP nor R3, naming PrereviewDiscoverCommand and PrereviewLensCommand in the pointer cell: Anthropic (claude CLI, subscription OAuth, non-essential traffic disabled, no session persistence; outbound = the whole pack including the exported snapshot tree) and DeepSeek (api.deepseek.com anthropic-compatible messages endpoint, DEEPSEEK_API_KEY read at call time; outbound = the same prompt.txt as the discoverer: selected checklist sections, QUALITY-RUBRIC, card at base, acceptance.json, fence-hardened diff, changed-file slice up to PrereviewMaxDocBytes, tree index; no tree/ content)."
  - "A4 CLAUDE.md gains one index row for docs/PREREVIEW-PROTOCOL.md and the sentence naming the project's AI tools is rewritten to name both providers as live from 1a; no scripts/prereview path literal enters CLAUDE.md (gate 11) and every path it cites exists at this card's merge."
  - "A5 .claude/skills/task-loop/SKILL.md step 4.6 gains one sentence naming prereview.ps1 run as the advisory tool before the first ship, keeping the existing advisory non-gate, first-round-only and QUALITY-RUBRIC phrases so gate 11c stays green."
  - "A6 review.ps1 -SizeOnly measured before RED reports diffChars at most 50000 for the whole card; the pointer cell of each TRUST row and the protocol text cite docs/PREREVIEW-CHECKLISTS.md by name only."
forbid:
  - Any scripts/prereview path literal in CLAUDE.md (those enter with LOOP-DOCS in 1b)
  - 1b prose (dispositions, batch loop, gate precedence, dispute escalation) beyond status rows and one pointer paragraph
  - Editing docs/QUALITY-RUBRIC.md, docs/DEVOPS-WORKFLOW.md, docs/PREREVIEW-CHECKLISTS.md or scripts/_config.ps1
non_goals:
  - Replacing the codex R3 gate with a ReviewCommand pipeline backend, or feeding the packet into the codex prompt (Phase 2)
  - Codex candidate verification, DeepSeek re-check of fixes, codex bounded independent check (Phase 2, schema_version 2)
  - Incremental changed-units-only re-discovery, snapshot refs, coverage carry-forward, automatic re-open of fixed candidates
  - Vote counting, a lock file, a refs plane, run -Live overrides, a new top-level selftest gate number, an infra round cap
  - Fully qualified -Base refs, cross-file JSON schema references, changes to specs/verdict.schema.json or scripts/_gitbase.ps1
  - Any Phase-1b behaviour: dispositions, review_status and gate predicates, batch semantics, the ship gate leg, ledger rows, metrics
hygiene: Anchor mutants: delete one status row => -Anchors red; add a made-up [PRE-...] row => red; duplicate a row => red; move a knob name out of the table row into prose => the DoD row check red.
doc_sync: This card is the doc sync itself: CLAUDE.md index row plus rewritten AI-tools sentence, TRUST-MANIFEST rows, task-loop 4.6 sentence; DEVOPS-WORKFLOW and QUALITY-RUBRIC untouched until 1b LOOP-DOCS.
---

# T0-PREREVIEW-PROTOCOL-DOC

## Context

Human-facing protocol for Phase 1a plus the two new outbound trust surfaces. The worker-facing checklists are a separate card (T0-PREREVIEW-CHECKLISTS) because they enter the pack and the policy hash.

## Contents carried by this card

- The status-code table under a heading named Status codes (first table; the code is the first [PRE-...] token of column 1) with the 20 codes of T0-PREREVIEW-SCHEMA; 1b content limited to those rows plus one pointer.
- TRUST-MANIFEST table rows for Anthropic (claude CLI, subscription OAuth, non-essential traffic disabled, no session persistence, cwd = pack, outbound = the whole pack incl. the exported snapshot tree) and DeepSeek (in-repo adapter, key from DEEPSEEK_API_KEY at call time, same prompt.txt as the discoverer); each row names its knob (PrereviewDiscoverCommand, PrereviewLensCommand).
- CLAUDE.md index row and the rewritten AI-tools sentence (both providers live from Phase 1a), without citing script paths that do not exist yet (selftest gate 11).
- One sentence in task-loop step 4.6 naming the advisory packet run, keeping the advisory, non-gate, first-round and QUALITY-RUBRIC phrases (gate 11c).

## Notes

Size: at most 30720 bytes for the protocol; review.ps1 -SizeOnly diffChars at most 50000 before RED. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
