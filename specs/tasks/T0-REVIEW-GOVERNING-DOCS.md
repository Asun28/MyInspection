---
id: T0-REVIEW-GOVERNING-DOCS
title: Give the docs that define the gates, the security rules and the agents' boundaries the adversarial R3 class
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/_config.ps1
  - scripts/selftest.ps1
  - docs/SCAFFOLD-SYNC.md
  - specs/tasks/T0-REVIEW-GOVERNING-DOCS.md
forbid:
  - Changing scripts/review.ps1, scripts/_cards.ps1, scripts/task.ps1 or any other scaffold code (option B goes upstream); the one exception is the gate 17ib production-policy check in scripts/selftest.ps1 (A6)
  - Removing the gate 17ib production-policy check or loosening it to accept more than one value
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
  - "A6 Gate 17ib's production-policy check in scripts/selftest.ps1 (added by PR #297, it fails when the live ReviewIntensityByTier tier 0 value is not 'advisory') expects 'adversarial' instead and still fails on any other value; the Tier-S full selftest run passes with the new value, and fails at that check with the old one"
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

**Amendment (2026-09-24, found while implementing):** gate 17ib in `scripts/selftest.ps1` pins the live
tier `0` value to `advisory` as a drift check, so C cannot pass the tier-S acceptance run without updating
that one expected value. `scripts/selftest.ps1` is added to allow_paths for that check only (A6); the check
stays and keeps failing on any value but the decided one.

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

## Implementation record (2026-09-24, base `0f0c4b52`)

The change was first written on base `734732b0`. Origin then gained two commits (#334, #346) that also edit
`scripts/_config.ps1` and `scripts/selftest.ps1`, in hunks that do not overlap this card's. After fast-forwarding
onto `0f0c4b52`, GREEN, A4, A6 and the full selftest were re-run on the merged bytes. The RED and pre-review
bullets name the commit each one ran on.

- Change: `scripts/_config.ps1` sets tier `0` to `adversarial`, and the comment above the map names this card.
  `scripts/selftest.ps1` gate 17ib expects `adversarial`; its comment now states the deployed policy.
  `docs/SCAFFOLD-SYNC.md` records the override in its "deliberately forked" section (A5).
- RED (A2): `task.ps1 -Phase red` ran at `a96c3ab1`, master when this worktree was created, and exited 1 with
  `[DOD-FAIL] docs/QUALITY-RUBRIC.md tier=0 class=advisory`. It was re-run through `Get-ScaffoldDodPayload` in a
  detached checkout of `8cc80f30`, the base PR #347 was shipped and reviewed against. The dod_command there is
  identical to the base card's, and it exited 1 with the same line.
- GREEN (A2, A3): the dod_command, run through `Get-ScaffoldDodPayload`, exits 0 with `[DOD-PASS]`. Its last check
  is that `docs/research/example.md` still computes tier 0.
- A4 mutant: tier `0` set back to `advisory` in `scripts/_config.ps1` alone makes the dod_command exit 1 with the
  RED line above. The file was restored byte for byte (SHA-256 `0D415124...58D6FF5B`).
- A6 mutant: the same revert, with the new `scripts/selftest.ps1`, makes `selftest.ps1 -Only 17post` exit 1 in
  156.7 s. Its one failure is `17ib production-policy drift: expected ReviewIntensityByTier['0']='adversarial' with
  ReviewGate='required'`. The file was restored and its SHA-256 matched.
- Pre-review: DeepSeek V4 Flash round 1 on the `734732b0` candidate and round 2 on the `0f0c4b52` candidate
  (shipped unchanged as `29df461a`), both pass with no findings.
- R3 round 1 (Opus 5.5 through `ReviewCommand`, Codex out of quota) on `29df461a`: block, two spec findings, both
  fixed in the next commit. This record's lead sentence and RED line claimed more re-runs than had happened, and
  the `docs/SCAFFOLD-SYNC.md` paragraph implied that small governing-doc diffs no longer get effort `low`.
- Full selftest (tier-S bar, A6): `selftest.ps1 -Parallel` from this worktree exited 0. All 5 shards exited 0
  (their union covers all 17 gates), 1158.2 s wall, and 17ib reported OK. It ran on these bytes (SHA-256):
  `scripts/_config.ps1` `0D415124683063D8E6F9A954678125E4AC6EA4EFD513979566C3F4CD58D6FF5B`,
  `scripts/selftest.ps1` `3D32CC422B5F14C532FACB0BF55F096D618DF0DBE7882F4B55FE3FDB3A966BC2`,
  `docs/SCAFFOLD-SYNC.md` `B0D8DB4F51F96F6634C5DD5851615D532C590E8428C4C19BB8D0B5385EC5DEC5`.
  After the run only prose changed: this record, and the `docs/SCAFFOLD-SYNC.md` paragraph reworded after R3
  round 1 (now SHA-256 `B952C8093648F4A2D4D127F18362917E67E094C01571704775DF98608538C55D`). That paragraph is above
  the file's single `SCAFFOLD-SYNC-LEDGER` marker, and the text from the marker to the end is unchanged.
  `Get-SyncedVersion` in `scripts/scaffold-sync.ps1`, which `scripts/triage.ps1` also loads, reads version rows
  only after that marker.
