---
id: T0-REMOTE-PREREVIEW-CARDS
title: Register four prereview publication cards without delivering their implementations
status: todo
branch: T0-REMOTE-PREREVIEW-CARDS
worktree: C:\wt\T0-REMOTE-PREREVIEW-CARDS
depends_on: []
allow_paths:
  - specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/tasks/T0-PREREVIEW-SCHEMA.md
  - specs/tasks/T0-PREREVIEW-RECORDS.md
  - specs/tasks/T0-PREREVIEW-FACTS-LIB.md
  - specs/tasks/T0-PREREVIEW-STATE-1A.md
acceptance:
  - "A1 Publish only this registration card and the four listed prereview task cards, all under specs/tasks/. Each filename, id, branch and worktree leaf agrees and passes check-cards. No archived task is changed."
  - "A2 The four publication cards remain todo and carry executable future DoD commands, bounded allow_paths, acceptance, exclusions, hygiene and R5 duties. SCHEMA precedes RECORDS and FACTS-LIB; STATE-1A depends on both. Registration does not claim any implementation, RED, R3, CI or feature merge is complete."
  - "A3 SCHEMA first publishes schema revision 1, carrying the final local unit-ID revision. FACTS-LIB explicitly treats the absent runtime checklist document as a precondition supplied by its temporary self-check fixture. No Phase-1b implementation or relaxation of the existing remote harness is authorized."
  - "A4 The candidate passes check-cards and archive.ps1 -CheckCardsIndex; none of these five files contains trailing horizontal whitespace. The complete five-file diff stays below 500 changed lines and 40000 diff characters, including context and Git diff headers."
  - "A5 The registration itself is reviewed and merged through a remote PR using the original main checkout task-loop controller. Its documentation-only SkipRed does not waive any product card's testing or merge gates. No direct push to master, history rewriting, receipt reuse or product implementation is part of this card."
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; foreach ($id in @('T0-REMOTE-PREREVIEW-CARDS','T0-PREREVIEW-SCHEMA','T0-PREREVIEW-RECORDS','T0-PREREVIEW-FACTS-LIB','T0-PREREVIEW-STATE-1A')) { $p = 'specs/tasks/' + $id + '.md'; if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { exit 1 }; if (@(Select-String -LiteralPath $p -Pattern '[ \t]+$').Count -gt 0) { exit 1 } }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --cached --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Task-card validation and the unchanged archive-index projection pass; all five declared cards exist and are free of trailing horizontal whitespace; working and staged diffs pass whitespace checks.
review_gate: codex {verdict:pass}
forbid:
  - Product code, schema artifacts, scripts, configuration, CI, hook, skill or archive changes
  - Changing the original local feature worktrees, RED proofs, watershed receipts or shared history
  - Claiming registration is implementation delivery, or transferring historical acceptance to an untested remote candidate
  - Direct push to master, bypassing R3 or CI, or weakening the remote harness
non_goals:
  - Implementing or testing the four registered features during this documentation card
  - Publishing runtime checklists, workers, a prereview command, or Phase-1b integration
  - Reconciling unrelated local history, task boards, lessons or debt registries
doc_sync: After the remote PR is actually merged, record its PR number, reviewed head, CI evidence and merge OID in the controller delivery ledger; mark this registration card merged through normal reviewed R5 metadata work. Keep the four feature cards todo until their own deliveries close. Any archive move is separate R5 work outside this five-file implementation diff.
hygiene: This is a non-TDD metadata registration card; use the original task-loop ship with explicit SkipRed. Do not add tests that merely repeat these text edits. Preserve the owner-approved four-card payload and its final hashes in the ignored delivery ledger before publication.
---

# T0-REMOTE-PREREVIEW-CARDS

Register four independent future deliveries on the remote baseline, where no prereview implementation or task card currently exists. The approved publication sequence is SCHEMA, then RECORDS and FACTS-LIB, then STATE-1A. Each implementation remains responsible for its own real candidate validation, formal R3, CI and remote PR merge.

The SCHEMA publication begins at revision 1 and the STATE-1A scope excludes review_status. Historical local evidence explains the source behavior; it is not evidence that a remote candidate has passed its gates. The feature owner supplies the final card payload and immutable source mappings; the controller records those hashes before making this registration PR.

Run start and ship with D:/Projects/MyInspection/scripts/task.ps1 from the original main checkout. Start this isolated worktree from origin/master and ship against master with explicit SkipRed for this documentation-only card. The original controller reads its registered main-checkout scope; its formal reviewer supports a worktree card when the pinned remote baseline does not yet contain it. Include this registration card in the reviewed candidate. No alternate or weaker controller is introduced.

Measure the full five-card staged or committed diff before the first ship. The registration ceiling is 499 changed lines and 39999 diff characters; if the final owner-approved payload exceeds either value, stop and revise the scope before publication. The normal ship still runs its mandatory deterministic gates, formal review and exact-head remote CI checks. Do not run any feature card's future implementation DoD as this registration card's acceptance.
