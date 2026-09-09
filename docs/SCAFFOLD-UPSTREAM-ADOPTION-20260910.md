# Upstream scaffold adoption — 2026-09-10

This change adopts 18 directly corresponding merged upstream PRs, together
with the prerequisite history needed for their complete coupled modules, from
`refs/scaffold-upstream/adoption-20260910`
([`96ebfcec2a1ff89ac77e665123978d7ae138c857`](https://github.com/Asun28/claude-devops-scaffold/commit/96ebfcec2a1ff89ac77e665123978d7ae138c857)).  It is a single, reviewable
port into MyInspection, not a claim that this project reran upstream's test
or review evidence.

The direct sources are [upstream #160](https://github.com/Asun28/claude-devops-scaffold/pull/160),
[upstream #162](https://github.com/Asun28/claude-devops-scaffold/pull/162),
[upstream #343](https://github.com/Asun28/claude-devops-scaffold/pull/343),
[upstream #345](https://github.com/Asun28/claude-devops-scaffold/pull/345),
[upstream #357](https://github.com/Asun28/claude-devops-scaffold/pull/357),
[upstream #358](https://github.com/Asun28/claude-devops-scaffold/pull/358),
[upstream #360](https://github.com/Asun28/claude-devops-scaffold/pull/360),
[upstream #362](https://github.com/Asun28/claude-devops-scaffold/pull/362),
[upstream #365](https://github.com/Asun28/claude-devops-scaffold/pull/365),
[upstream #371](https://github.com/Asun28/claude-devops-scaffold/pull/371),
[upstream #373](https://github.com/Asun28/claude-devops-scaffold/pull/373),
[upstream #374](https://github.com/Asun28/claude-devops-scaffold/pull/374),
[upstream #375](https://github.com/Asun28/claude-devops-scaffold/pull/375),
[upstream #376](https://github.com/Asun28/claude-devops-scaffold/pull/376),
[upstream #377](https://github.com/Asun28/claude-devops-scaffold/pull/377),
[upstream #380](https://github.com/Asun28/claude-devops-scaffold/pull/380),
[upstream #381](https://github.com/Asun28/claude-devops-scaffold/pull/381), and
[upstream #387](https://github.com/Asun28/claude-devops-scaffold/pull/387).
Their merge SHAs and file-level provenance are retained in the adoption
snapshot named by this PR.

## Included merged upstream groups

| Local pending work | Adopted upstream PRs | Local result |
|---|---|---|
| PAGED, NIGHTLY and META selftest routing | #357, #358, #360, #362, #365, #376, #380, #381 | Computed tier routing, nightly meta coverage and its probe/reprobe support arrive as one coupled implementation. |
| SKILL routing | #371 | Gate-to-skill routing arrives with the shared guard and selftest support. |
| LOW review intensity | #387 | Tier 0 uses one advisory read; Tier 1 and S retain adversarial review. |
| OID binding and review base bundle | #343, #345, #374, #375 | Scope, ship and reviewer bind to the same immutable baseline. |
| Init assignment anchors | #373, #377 | Initializer rewrites target assignments rather than lookalike text; MyInspection keeps an empty `PlanDir`. |
| ASCII ship/card/secret/review codes | #160, #162 | The current task, card, secret, archive and review result-code surfaces are adopted together. |

## Project adaptations

- `scripts/verify.ps1`, its GoldenEvidence/Android checks, and the existing
  `verify` CI job remain project-owned.  CI adds only a `required` fan-in job
  which succeeds solely when `verify` succeeds.
- The MyInspection identity, portable worktree root, product frozen paths,
  origin `0.29.0`, and project DocSync mappings remain in `_config.ps1`.
  The adopted high-water is `0.47.0`; upstream-only budget and mutation
  exemptions stay disabled.
- `ReviewGate` remains `required`; content-based review skipping is disabled.
  This PR is merged only after its one independent Codex review reports no
  block.  Upstream's advisory default is not adopted as this project's merge
  policy.
- MyInspection retains its production `review.ps1` hard ceiling of 1,000
  changed lines and 60,000 diff characters.  The source's current task loop
  no longer carries the legacy RED/waterline-receipt ship gate; that current
  upstream workflow is adopted, but no historical receipt is imported or
  represented as fresh evidence.
- `check-cards.ps1` retains the existing local enforcement set for its legacy
  corpus.  Tier computation and ASCII result surfaces are active; later
  upstream budget, sweep, mutation, requirement-citation, near-miss,
  arbitration and dangling-reference requirements await a dedicated corpus
  migration card.
- `archive.ps1` retains `-CheckCardsIndex`; a compatible upstream `-Check`
  read-only projection check is added for the adopted cleanup caller.  The
  already-initialized project does not add the upstream template-only root
  documentation.

## Not completed by this adoption

`T0-R3-DIFF-INPUT-TRUST` and `T0-TRIAGE-EVIDENCE-CASE` have no corresponding
merged upstream replacement.  `T0-RECEIPT-LOSS-FAIL-CLOSED` is intentionally
excluded because the historical upstream receipt feature was removed.  These
cards remain separate work; this document does not recast them as accepted.

## Evidence boundary

The source PRs were confirmed merged with their published upstream CI.  The
fixed source includes their complete module history, so this port does not
claim every changed line was introduced by those 18 PRs alone.  Local evidence
is limited to parsing and targeted static contract checks recorded in the
adoption PR.  No upstream run is presented as a fresh MyInspection acceptance
run.
