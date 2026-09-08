---
id: T0-SELFTEST-RISK-ROUTING
title: Select existing scaffold selftest coverage from pinned task changes
depends_on: []
parallelizable_with: []
status: in-progress
branch: T0-SELFTEST-RISK-ROUTING
worktree: C:\wt\T0-SELFTEST-RISK-ROUTING
allow_paths:
  - scripts/_validation.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - CLAUDE.md
  - specs/tasks/T0-SELFTEST-RISK-ROUTING.md
forbid:
  - Removing assertions, changing product tests, weakening security/frozen-contract checks or suppressing failures
  - Trusting a branch-edited card/config as the baseline authority for cheaper routing
non_goals:
  - Changing default unfiltered selftest, CI required-status names, R3 policy or shard composition
dod_command: pwsh -NoProfile -File scripts/_validation.ps1 -SelfCheck
dod_exit: 0
dod_assert: The real routing decision and repository-bound fixtures prove product, documentation, critical and unknown paths, baseline ownership and failure cases before the selftest entry point consumes them.
acceptance:
  - "A1 An explicit selftest TaskId resolves the matching worktree and baseline card against a pinned local base; invalid IDs, missing authority or unreadable repository state refuse cheaper routing"
  - "A2 Committed and pending changed paths are considered with rename source and destination; ordinary product paths select no scaffold checks, ordinary docs select core, critical/frozen/unknown paths select all"
  - "A3 No-scaffold output does not claim product verification passed; selected core/all runs retain nonzero failure propagation and existing shard contents"
  - "A4 The unfiltered selftest entry point remains full coverage; direct route tests and repository fixtures exercise actual production routing, including branch-only authority edits and unknown paths"
review_gate: codex {verdict:pass}
hygiene: Table-drive path classes against the real decision; use small temporary git repositories for authority and dirty-path cases, and existing selftest aggregation evidence for execution.
doc_sync: Document explicit task-scoped selftest routing and the separate product verify requirement in the existing workflow and entry contract.
---

# T0-SELFTEST-RISK-ROUTING

Remote adoption is authorized by the user's 2026-09-08 instruction to complete all unfinished scaffold cards in independent worktrees and PRs. This card is pending remote implementation and acceptance; its local source history is provenance only, not a remote pass or merge.

Use task-loop with GPT-6 Astra, high effort; R3 remains the configured GPT-5.6 Sol, high effort. Preserve current remote product changes, scaffold-trigger isolation, CI identity/jobs checks and timeout budgets. Apply only this card's scoped changes, with fresh RED/GREEN, current-source evidence and its own PR. Do not merge the divergent local master or copy historical pass receipts.

## Focused implementation evidence (not remote delivery)

R1 used the control primary checkout with explicit `-Base origin/master`, producing this worktree at `ae40d6b761ed02809eea7b3c5a81ebac559dee92`. The historical source supplied only the scoped routing unit; no divergent source-master commit was imported.

- R2: `scripts/_validation.ps1 -SelfCheck` first exited 1 with `[SELFTEST-RISK-ROUTING-SELFCHECK] routing capability missing`; control-primary `task.ps1 -Phase red` recorded the fresh receipt. Raw logs: `.review/risk-routing-red-initial.log`, `.review/risk-routing-primary-red.log`.
- Focused GREEN: the same DoD exited 0 on LF source. `.review/risk-routing-green-final.log` and `.review/risk-routing-green-manifest.json` bind the output to exact source hashes.
- A1/A2: actual temporary repositories and linked worktrees cover pinned local/remote-tracking baselines, matching identity, authority failures, branch-owned card/config edits, every changed-state input and both rename endpoints for staged, committed and working-tree-only renames. Direct policy cases cover product, docs, mixed, critical, frozen, unknown, case variants and an empty change set.
- A3/A4: the actual selftest entry block consumes real routing; only snapshot/aggregate execution boundaries are controlled in the fixture. Product emits NOT-APPLICABLE without claiming PASS; core/all consume the resolved task worktree and preserve child exits 0/37. Omitted TaskId retains the existing all entry; explicit shard conflicts and routing failures reject dispatch. Existing shard bodies and aggregation tests are unchanged.
- R4: 18 isolated source mutations each exited 1 at its named assertion on the final LF source; `.review/risk-routing-mutations.json` records selectors, replacements, hashes and individual raw logs, with batch output in `.review/risk-routing-r4-batch.log`. An initial HEAD-guard survivor exposed index/HEAD fixture overlap; the corrected fixture separates all three card states. No existing assertion was deleted; the candidate source hashes were unchanged across the final batch.
- Production SHA256: `_validation.ps1` = `80E80B6137ECD8D7EEAC7470354AADCE0F246989B3C0584684AE31DA592329A0`; `selftest.ps1` = `49117616ECEF9D0A18D6521F3C32577C716E8D6419B8DB94BF09ABA99898CB28`.

Full selftest, verify, official R3, PR and merge remain pending the shared delivery window; focused evidence is not a remote acceptance or merge claim.
## Current remote-base alignment evidence (not remote delivery)

The candidate was preserved with all 91 prior review files and source SHA256 values in `.review/alignment-ceb2685e/preservation.json`, then normally fast-forwarded from `ae40d6b7` to `ceb2685e9e3ada76a503377584d512c0c6d2af4d`. No rebase, reset or history rewrite occurred. The original RED is retained as historical evidence; a new tests-only replay extracted the two complete existing selfcheck functions before restoring production. Its real missing-capability failure and control-primary RED receipt are retained under `.review/alignment-ceb2685e/`.

The restored current-base DoD exited 0 (`current-base-green.log`; terminal session 6376). Production `_validation.ps1`, `selftest.ps1` and `_cards.ps1` exactly match all 18 original R4 source hashes; `r4-source-reuse-audit.json` verifies each original named failure and saved mutant, without claiming a new mutation run. Existing entry fixtures execute the actual task-routing entry block and production dispatcher with child exits 0/37 for core/all and check the resolved worktree; no duplicate exit-propagation fixture was added. The default shard bodies remain unchanged. Final source/receipt/log hashes and the complete diff budget including the untracked helper are recorded in `current-base-evidence.json` in that directory.

Only the card's focused SelfCheck was rerun in this alignment window. Project verify, official R3, candidate CI, PR and merge remain pending; no all-shards full-run claim is made.
