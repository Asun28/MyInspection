---
id: T0-STALE-PR-CLOSEOUT
title: Record the 2026-11-17 ruling that closed four stale PRs and retires their cards
status: merged
depends_on: []
allow_paths:
  - specs/tasks/T0-STALE-PR-CLOSEOUT.md
forbid:
  - Code or test changes; this card is a decision record
non_goals:
  - Reimplementing any closed PR; a later card starts from current master if the work is wanted again
acceptance:
  - "A1 PRs #294, #308, #319 and #440 are closed unmerged, each with a comment that names the ruling and keeps its branch."
  - "A2 The triage defect of #294 is filed upstream as Asun28/claude-devops-scaffold#406 and recorded in docs/SCAFFOLD-SYNC.md."
  - "A3 T0-TRIAGE-EVIDENCE-CASE, T0-TRIAGE-EVIDENCE-INPUTS, T0-REMOTE-ROUND45-CONTRACT-EVIDENCE and T0-ROUND45-DELIVERY-CLOSEOUT are retired with superseded_by T0-STALE-PR-CLOSEOUT through post-merge.ps1 retire; T0-PREREVIEW-REMOTE-POLICY is retired with superseded_by T0-PREREVIEW-PROTOCOL-DOC."
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1
dod_exit: 0
dod_assert: The card registry validates with this record card in it. A1 and A2 are GitHub facts checked by hand on 2026-11-17 (links below); A3 lands as separate retire PRs that name this card.
review_gate: none (decision record; the retire PRs merge on CI under the 2026-09-26 ruling)
hygiene: No tests; no code.
doc_sync: none beyond the retire PRs and docs/plans/PLAN-STALE-PRS-20261117.md
---

# T0-STALE-PR-CLOSEOUT

The user asked on 2026-11-17 to resolve or close old open PRs and to split large work into cards of about 200
changed lines. This card records the decisions for the PRs that were closed. It is registered as merged because the
decision itself is complete when it lands; it has no implementation PR. The plan is
`docs/plans/PLAN-STALE-PRS-20261117.md`.

| PR | Card | Ruling | Why |
|---|---|---|---|
| [#294](https://github.com/Asun28/MyInspection/pull/294) | T0-TRIAGE-EVIDENCE-CASE, T0-TRIAGE-EVIDENCE-INPUTS | close, report upstream (user) | The defect is in `scripts/triage.ps1`, adopted from the scaffold in #297, so it goes upstream ([#406](https://github.com/Asun28/claude-devops-scaffold/issues/406)). The branch conflicts with master and grew to about 580 changed lines. |
| [#308](https://github.com/Asun28/MyInspection/pull/308) | T0-PREREVIEW-REMOTE-POLICY | close as superseded | Both documents reached master in the 2026-09 reconcile (T0-PREREVIEW-PROTOCOL-DOC, T0-PREREVIEW-CHECKLISTS); the branch conflicts with them. |
| [#319](https://github.com/Asun28/MyInspection/pull/319) | T0-REMOTE-ROUND45-CONTRACT-EVIDENCE | close, retire (user) | Bookkeeping for products that are merged; R3 blocked twice on proof of the evidence itself. |
| [#440](https://github.com/Asun28/MyInspection/pull/440) | T0-ROUND45-DELIVERY-CLOSEOUT | close, retire (user) | Record-only closeout for merged products; R3 blocked on archive inventory evidence. |

Branches stay on origin, so any of this work can restart from them.
