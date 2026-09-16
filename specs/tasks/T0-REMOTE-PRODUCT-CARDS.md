---
id: T0-REMOTE-PRODUCT-CARDS
title: Register four isolated remote delivery aliases for locally verified product cards
status: todo
depends_on: []
allow_paths:
  - specs/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/tasks/T1-STORAGE-PATH-BOUNDARY-REMOTE.md
  - specs/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md
  - specs/tasks/T1-LOCAL-DATA-SECURITY.md
  - docs/TASK-BOARD.md
  - docs/adr/0006-offline-security-backup-hardening.md
  - docs/adr/0007-report-interchange.md
forbid:
  - product code, scripts, configuration, frozen contracts, dependencies or unrelated local history
  - weakening the original product acceptance, marking pending remote deliveries complete, or rewriting existing receipts
non_goals:
  - implementing or remotely delivering any of the four product changes
  - reconciling the original dirty master or registering the entire later PDF and security backlog
acceptance:
  - "A1 Register exactly four todo aliases for SafeMediaLogging, StoragePathBoundary, TypographyContract and PaginationFixtures; preserve each original allow_paths, executable DoD and complete behavioral acceptance."
  - "A2 Alias provenance distinguishes local-only feature/merge evidence from pending remote RED, DoD, mutation, R3 and candidate CI evidence; the pagination test refactor retains its approved non-TDD exception."
  - "A3 Typography keeps the existing renderer prerequisite; pagination depends on the remote Typography alias; the two platform aliases keep the existing platform prerequisite. Aliases do not count as additional product cards."
  - "A4 Only append the aliases and their scoped design notes to the remote registry; retain the security parent's existing acceptance and DoD when identifying the extracted logging prerequisite."
  - "A5 This registration itself passes original-main task-loop scope, full diff budget, independent R3 and exact-candidate CI and is remotely merged through a PR."
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: all task cards and the unchanged generated archive index validate; whitespace and original-main scope/budget gates pass for only the nine approved metadata paths
review_gate: codex {verdict:pass}
hygiene: genuine metadata registration with SkipRed recorded; no fabricated product RED or mutation claims
doc_sync: the four Task Board entries and ADR notes are included; record this registration PR and merge at R5
---

# T0-REMOTE-PRODUCT-CARDS

The user requested logged PRs followed by passing checks and remote merges for all cards on 2026-09-17. The original checkout and remote master have unrelated divergent history. This registration creates separate remote-delivery identities, retaining all original local commits and evidence, and does not publish that history or claim any new product behavior.

Execute every phase through the existing main checkout at D:/Projects/MyInspection/scripts/task.ps1. Start this genuine documentation card with -Base origin/master; ship with -Base master -SkipRed, without -Local. The original controller reads this already approved main-checkout card for scope; its R3 reader explicitly supports worktree-fallback when the pinned remote baseline lacks the registration card. Include this exact card in the candidate. Do not switch controller versions, change guards or directly push master to bootstrap scope.

After this PR merges, each alias starts independently from the refreshed remote master and executes the registered product acceptance. Existing local evidence is preserved provenance; it cannot substitute for tests on the new candidate. Record remote PR, reviewed head, passing CI and merge before closing the alias. Keep the five-round product count unchanged.

Forecast: approximately 350 changed lines and 32,000 diff characters including all metadata and context. Recompute before ship, stop for a split above 700 lines or 48,000 characters, and retain the 1,000-line / 60,000-character hard limit. Metadata references to later local cards do not claim those cards exist remotely or that their functionality is delivered.
