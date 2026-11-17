# Plan: resolve or close the stale open PRs (2026-11-17)

Goal `g-20261117003816-2171ee` (aidlc, size T1, target development). On 2026-11-17 the user asked to resolve or
close the old open PRs and to split large work into cards of about 200 changed lines, so each passes review.

## What happens to each open PR

| PR | Lines | State on 2026-11-17 | Becomes |
|---|---:|---|---|
| #465 `T1-PRIVACY-MANIFEST-POLICY-GATE` | 357 | CI green, mergeable; first formal review blocked on a RED receipt that failed on missing symbols | Closed. Three cards reuse its code: `T1-PRIVACY-MANIFEST-MEDIA` (gate + SelectedMediaPolicy, about 140 lines), `T1-PRIVACY-CLIPBOARD-POLICY` (about 145), `T1-PRIVACY-SENSITIVE-SURFACE` (about 72). The parent is retired with superseded_by `T1-PRIVACY-MANIFEST-MEDIA`. |
| #282 `T4-DESIGN-PAIR-USAGE-DUTIES` | 131 | Draft card registration; conflicts on `docs/TASK-BOARD.md`; both prerequisite cards are now merged on master | Rebased onto master and merged as a card registration (CI only, like #467). |
| #294, #308, #319, #440 | 22 to 580 | Closed 2026-11-17 | Recorded in `T0-STALE-PR-CLOSEOUT`; their cards are retired with `post-merge.ps1 retire`. |

`T1-SHARE-SCREEN-PRIVACY` depended on the parent privacy card; it now depends on the three split cards, so retiring
the parent (status merged) does not unblock it early.

## Order of work

1. This registration PR: the four cards, this plan, the board rows, the dependency change, the upstream issue link.
2. Close #465; retire the parent privacy card and the five cards of the closed PRs (one retire PR each, CI only).
3. Rebase and merge #282.
4. Ship `T1-PRIVACY-MANIFEST-MEDIA`, then `T1-PRIVACY-CLIPBOARD-POLICY`, then `T1-PRIVACY-SENSITIVE-SURFACE`
   through the aidlc card loop (RED, DoD, review, ship). They touch disjoint files but run one at a time: no two
   Gradle jobs, and no Gradle job beside a full selftest (L381).

## Risks

- The other open goal (`g-20261014091214-314b46`) runs full selftests in its own worktree until its 19:00 NZDT
  deadline. Each Gradle DoD here waits until no selftest or Gradle job is running.
- #465's code was green on an October base. Master has moved since, so each card reruns its DoD on current master.

## Proof

- Registration: `check-cards.ps1` exits 0 and CI is green.
- Each split card: its own DoD on the shipped candidate, an assertion-failing RED commit, R3 pass, CI green.
- The goal: after the three merge, `:app:check :app:assembleDebug` on master runs all 14 policy tests and the gate.
