---
id: T0-CARD-TEMPLATE-SLIM
title: Adopt concise upstream card templates with compatible acceptance and optional requirement links
depends_on: []
parallelizable_with: []
status: todo
branch: T0-CARD-TEMPLATE-SLIM
worktree: C:\wt\T0-CARD-TEMPLATE-SLIM
allow_paths:
  - specs/tasks/_TEMPLATE.md
  - specs/README.md
  - .claude/workflows/decompose-cards.mjs
  - scripts/_cards.ps1
  - scripts/check-cards.ps1
  - scripts/selftest.ps1
  - docs/PLAN-TEMPLATE.md
  - docs/DEVOPS-WORKFLOW.md
  - specs/tasks/T0-CARD-TEMPLATE-SLIM.md
forbid:
  - Product behavior, frozen contracts, new dependencies, or changing the R3 merge decision
  - Making existing cards require new fields or mandatory mutation work merely to satisfy a template
non_goals:
  - General risk routing, review arbitration, CI/shard scheduling, or migrating historical cards
diagnosis: The old template and generator repeat optional paperwork and force at least three acceptance items, while neither supports optional requirement links consistently.
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Fixture card-acceptance
dod_exit: 0
dod_assert: The focused card-acceptance fixture reuses the existing 10h assertions and runs the full existing-card checker. One full core run remains required for final acceptance, separately from edit-and-retry iterations.
acceptance:
  - "A1 The template and generator agree on concise core fields, optional supporting fields and optional testable requirements; the generator no longer demands every optional field"
  - "A2 One or more meaningful quoted A1..An acceptance items are valid; all existing cards remain valid and malformed or nonsequential declared lists remain rejected"
  - "A3 Optional requirement IDs can be cited from acceptance; missing, duplicate or empty cited requirements are rejected from front matter only, while absent requirements create no new obligation"
  - "A4 Existing card self-tests cover the changed contract, including invalid and legacy inputs; core selftest and full card validation pass"
review_gate: codex {verdict:pass}
hygiene: Extend the existing card-contract examples and targeted guard mutations; no parallel validator or English-sentence regex gate.
doc_sync: Update specs/README.md and plan/workflow guidance with the same optional-field and acceptance contract.
---

# T0-CARD-TEMPLATE-SLIM

User approved applying the useful upstream findings on 2026-09-05. Adapt upstream PRs #363 and #366
to this project's quoted A-numbered lists. Keep existing cards compatible and adoption proportional:
requirements help link obligations to evidence but never invent obligations to fill a field.

The user's fewer-gates instruction authorizes a bounded replay of the existing card tests for
iteration. This fixture does not change general task routing, CI scheduling or shard contents.
The full core acceptance in A4 remains mandatory before final delivery.

Final acceptance evidence (2026-09-06 NZ): the full `selftest.ps1 -Shard core` completed
with exit 0 and `selftest(core): PASS` on the production implementation (1496.5 seconds under
concurrent machine load; this is validation, not a speed benchmark). The focused fixture
also passed the complete existing-card checker, generator-schema assertions and seven guard
mutations (110.3 seconds after adding R0/R01 rejection fixtures). Deleting only the positive-ID
condition made both new fixtures fail; restoring it made both pass. Production code remained
unchanged, so the full core run was not repeated for these test additions. Logs are retained
under this worktree's `_local/` directory.
