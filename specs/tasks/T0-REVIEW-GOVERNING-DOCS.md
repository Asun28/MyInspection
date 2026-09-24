---
id: T0-REVIEW-GOVERNING-DOCS
title: Keep the full-effort R3 for docs that define the gates, the security rules and the agents' boundaries
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/_config.ps1
  - docs/SCAFFOLD-SYNC.md
  - specs/tasks/T0-REVIEW-GOVERNING-DOCS.md
forbid:
  - Changing scripts/review.ps1, scripts/_cards.ps1, scripts/task.ps1 or any other scaffold code (option B goes upstream)
  - Emptying or narrowing Tier0Paths, or any change that lowers the review class or acceptance of another card
  - Changing ReviewGate, ReviewEffort, ReviewEffortBySize, the round cap or the diff budget
non_goals:
  - Rewording or moving the rules inside the seven governing docs
  - Changing how a routed skip or the never-path floor works
acceptance:
  - "A1 OD-1 below is settled on master before RED, and the chosen option is written into this card; the dod_command is kept if the choice is C or A and rewritten on master first otherwise"
  - "A2 Each of docs/QUALITY-RUBRIC.md, docs/SECURITY.md, docs/LICENSE-POLICY.md, docs/TRUST-MANIFEST.md, docs/DEVOPS-WORKFLOW.md, CLAUDE.md and AGENTS.md, as a card's only allow_path, resolves to the review class adversarial through the real Get-ScaffoldCardTier and ReviewIntensityByTier accessors; RED on master prints [DOD-FAIL] for docs/QUALITY-RUBRIC.md tier=0 class=advisory"
  - "A3 An ordinary docs/research note still computes tier 0, so tiering is not switched off"
  - "A4 Reverting the config change alone makes the dod_command exit 1 again (the single-statement mutant for A2), recorded in the card"
  - "A5 docs/SCAFFOLD-SYNC.md records the local override of the adopted upstream Tier-0 default (#387, adopted in PR #297) with its reason"
dod_command: . ./scripts/_config.ps1; . ./scripts/_cards.ps1; foreach ($n in 'Get-ScaffoldCardTier','Get-ScaffoldTierSPaths','Get-ScaffoldTier0Paths','Get-ScaffoldReviewIntensityByTier') { if (-not (Get-Command $n -ErrorAction SilentlyContinue)) { Write-Host "[DOD-FAIL] missing $n"; exit 1 } }; $map = Get-ScaffoldReviewIntensityByTier; foreach ($p in 'docs/QUALITY-RUBRIC.md','docs/SECURITY.md','docs/LICENSE-POLICY.md','docs/TRUST-MANIFEST.md','docs/DEVOPS-WORKFLOW.md','CLAUDE.md','AGENTS.md') { $t = [string](Get-ScaffoldCardTier -AllowPaths @($p) -TierSPaths @(Get-ScaffoldTierSPaths) -Tier0Paths @(Get-ScaffoldTier0Paths)).Tier; if ($map.ContainsKey($t) -and ([string]$map[$t] -cne 'adversarial')) { Write-Host "[DOD-FAIL] $p tier=$t class=$($map[$t])"; exit 1 } }; if ([string](Get-ScaffoldCardTier -AllowPaths @('docs/research/example.md') -TierSPaths @(Get-ScaffoldTierSPaths) -Tier0Paths @(Get-ScaffoldTier0Paths)).Tier -cne '0') { Write-Host '[DOD-FAIL] docs/research no longer tier 0'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: every listed governing doc resolves to the adversarial review class through the production accessors, and a docs/research note still computes tier 0; prints [DOD-PASS]
review_gate: codex {verdict:pass}
hygiene: one single-statement mutant (revert the config change) re-runs the dod_command to exit 1 (A4); no new script or test runner
doc_sync: TASK-BOARD, docs/SCAFFOLD-SYNC.md
---

# T0-REVIEW-GOVERNING-DOCS

Opened by user decision on 2026-09-24, after `T0-REVIEW-LOW-RISK` was retired as superseded by the upstream
tiering adopted in PR #297.

## Problem (measured 2026-09-24 on master `95087e4c`)

`Tier0Paths` is `docs/`, `specs/`, `context/`, `CHANGELOG.md` and root `*.md`. A card whose allow_paths are all
under those entries computes tier 0, and `ReviewIntensityByTier` maps tier 0 to `advisory`. Measured with
`Get-ScaffoldCardTier`: each of the seven files listed in A2, as a card's only allow_path, computes tier 0 and the
class `advisory`.

What `advisory` changes in this repo:

- `review.ps1` runs one R3 pass at effort `low`, where the configured `ReviewEffort` is `high` and
  `ReviewEffortBySize` gives medium and large diffs `high`, and it prints that a further round is voluntary.
- `selftest.ps1 -TaskId` uses the fixed tier-0 gate set as the card's acceptance run.
- It does not make a block non-blocking here: `ReviewGate = 'required'`, so any non-zero review exit still stops
  the ship at every tier.

These files define the R3 standard, the security and license rules, the outbound trust surface and the agents'
hard boundaries. A weakening edit to them is the change a single low-effort pass is most likely to miss. The
Opus 5.5 R3 on `T0-REVIEW-LOW-RISK` (`a9506225`) blocked the same class of rule in that card. Severity is
moderate: the deterministic gates and a blocking R3 still apply.

## OD-1: which fix (settled: C)

**Decision (user, 2026-09-24): C.** The dod_command stays as written. The change is the tier `0` value of
`ReviewIntensityByTier` in `scripts/_config.ps1`, plus the comment above it, which currently says the values
are T301's decision and must name this card for tier `0` instead. A and B are not taken.

- **C (recommended):** set `ReviewIntensityByTier` tier `0` to `adversarial`. One config line. Tiers and
  selftest acceptance stay as they are, and small doc diffs stay at effort `low` through `ReviewEffortBySize`.
  Cost: medium and large doc-only diffs get effort `high` and lose the one-pass guidance.
- **A:** add the seven files to `TierSPaths`. Full-effort review for exactly these files, but each card touching
  one becomes tier S and needs the full selftest run (1033 s and 979 s on the 2026-09-14 tier-S runs).
  `CLAUDE.md` is in many cards' allow_paths, so this is the costly option.
- **B:** make the never-path floor in `review.ps1` also raise `advisory` to `adversarial` for paths in
  `ReviewSkipWhen.NeverPaths`. The most targeted fix, but it changes scaffold code, so it goes upstream through
  `scripts/scaffold-sync.ps1 report` rather than into this card.

The allow_paths serve A and C alike. This card's own tier is S (`scripts/_config.ps1` is in `TierSPaths`).
Estimate: under 40 changed lines for C or A.
