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

## Implementation record (2026-09-15)

- Delivered: docs/PREREVIEW-PROTOCOL.md (26477 bytes; sections 1-10 for 1a, the Status codes table, one 1b pointer paragraph), two TRUST-MANIFEST rows (boundary cells match neither MCP nor R3; knob names in the pointer cell) plus one maintenance bullet, CLAUDE.md index row 24 and the rewritten AI-tools sentence, one sentence in task-loop 4.6. Commands are named by subcommand (`prereview.ps1 run`, `link-r3`, `recall`); every `docs/specs/scripts/.claude` path the protocol and CLAUDE.md cite exists in this tree except `docs/PREREVIEW-CHECKLISTS.md`, which A3/A6 require by name. Size: about 320 changed lines / 41000 diff chars including this record (A6 limit 50000), measured with the review.ps1 arithmetic on the staged tree before ship. `scripts/fixtures/prereview/protocol/` was allowed but not needed: the anchor mutants below run against the schema fixtures' own negative shapes.
- R4 hygiene, all four mutants red on scratchpad copies (the delivered files untouched): delete the `[PRE-TIMEOUT]` row => `missing enum row`; add a `[PRE-MADE-UP]` row => `code outside the enum`; duplicate the `[PRE-SECRETS]` row => `duplicate row`; move `PrereviewLensCommand` from its pointer cell into prose => the DoD row check is false while the prose still contains the name. Control: the delivered protocol prints `[PREREVIEW-ANCHORS-OK]` (20 vs 20).
- Pre-R3 fresh-context rubric review (task-loop 4.6) of the first draft returned 7 block-class and 13 minor findings, all of one class: a protocol sentence saying more than the cited card acceptance row (universal `PRE_LIVE` single-writer claim refuted by its own parenthetical; `DEEPSEEK_API_KEY` both absent from every worker and read by the lens child; a 1a cleanup step that no 1a card owns; a run-level `duration_s` the state does not have; outside-pack refusal attributed to the `permissions.deny` list; a median and two targets not in TASK-BOARD; the truncated-file list placed in the closed `facts` record). Every line was reworded to the owning card's own acceptance text or dropped.
- Routed selftest (`selftest.ps1 -TaskId T0-PREREVIEW-PROTOCOL-DOC -Base master`, mode=all because CLAUDE.md is a critical path): run from the worktree's own `scripts/selftest.ps1`, because with mode all or core the script tests its own `$RepoRoot` (only the skills mode targets the task worktree); the first run from the main checkout tested the main checkout and failed on its untracked `.aidlc/` root entry (gate 8, plus the fail/skip overlap attributed to 16), unrelated to this card. The worktree run failed gate 16 once: the C3 row cited L324, which exists only in the main checkout's uncommitted ledger, not at base (gates 11 and 11c were green in that run: 157 links, 4.6 contract intact). L324 dropped from the C3 row; the CHECKLISTS card names L324 in its A3 and will hit the same gate until that ledger stanza is committed. Final worktree-rooted run: `selftest(all): PASS`, exit 0, 1422 s (seeded / workflow / core all exit 0; core: gate 9g trust manifest OK, gate 11 157 links no dangling, gate 11c 4.6 contract OK, gate 16 64 files no dangling; skips are the usual post-init and nightly-deferred set). Bytes tested: protocol SHA-256 76dfcd8ef2a3e934806c6c22eeab685e7228172dff5cbf5a326859328ffe8c6f, TRUST-MANIFEST 66ad049ee1358786a839f640f91bfa1002143933520af5c9f76bacc809185e9a, CLAUDE.md a48573e9959ca11db7e6c976a6da9fb11bc7a9b1e39aaef80b82d8eb917d612e, SKILL.md 9f639ee8718e17327c3628690e5d795967cb53ecec322eb89776930756123314; only this card body changed after that run.
- [FOLLOW-UP] T0-PREREVIEW-FACTPACK-SLICES A1 lists over-size files "in facts.json as truncated with their size", but `$defs/facts` in the frozen schema is closed (`additionalProperties: false`) with no such field, so a facts.json carrying that list cannot validate as FACTPACK A4 requires. The protocol names the SLICES card, not facts.json, for that list; the SLICES owner settles the home of the list (a `schema_revision` bump on SCHEMA's files or another pack file) before it starts.

## Notes

Size: at most 30720 bytes for the protocol; review.ps1 -SizeOnly diffChars at most 50000 before RED. Collision rule (authority: docs/TASK-BOARD.md section PR review v2): no worktree branch and no todo card whose changes or allow_paths touch a chain file (review.ps1, task.ps1, selftest.ps1, _config.ps1, CLAUDE.md, the task-loop skill, QUALITY-RUBRIC.md, DEVOPS-WORKFLOW.md, TRUST-MANIFEST.md, TASK-BOARD.md) may start while the chain card owning that file is open; merge or retire first. origin/master (67 ahead, 37 chain-file commits incl. #265 which delivered T0-CI-DEADLINE-CONTAINMENT upstream) is not reconciled into master while any 1a chain card is in flight; reconcile before T0-PREREVIEW-RUNNER starts or after T0-PREREVIEW-LINK-RECALL merges, by user decision, after closing the local T0-CI-DEADLINE-CONTAINMENT card against #265.
