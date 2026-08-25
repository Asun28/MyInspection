# Fleet loop: this project and the scaffold it came from

> Entry point: `scripts/scaffold-sync.ps1`. This file is both the chain document and the decision
> ledger — the ledger table lives at the bottom, under the `SCAFFOLD-SYNC-LEDGER` sentinel.

`init-scaffold.ps1` is a snapshot. `report` sends reusable defects upstream; `check` brings release
decisions back. Neither direction is automatic, and both leave a record.

## Direction 1 — reporting a scaffold defect upstream

When something goes wrong here and the fix belongs in the **meta layer**, it is an upstream issue,
not a local patch. The discriminator is where the fix would land:

| The fix belongs in | Then it is | Where it goes |
|---|---|---|
| `scripts/` `.claude/` `.github/` or the root template infrastructure | a scaffold defect | an upstream issue |
| `android/` `configs/` `data/` `prompts/` and the rest of the product | this project's bug | a local task card |
| a toolchain pitfall that will recur for us specifically | a lesson | `scripts/lessons.ps1 add` |
| an architecture or tooling choice this project made | a decision | `docs/adr/` |

```powershell
pwsh -File scripts\scaffold-sync.ps1 report `
  -Title   'lessons-promote probe ignores the enforced_by field it claims to support' `
  -Surface 'scripts/triage.ps1' `
  -Summary 'what the scaffold did, and what it should have done' `
  -Repro   'the smallest command plus its observed output' `
  -LessonId 'L97'
```

That composes the issue body, stamps it with provenance (scaffold origin/current versions, OS, PowerShell version),
scans it with `check-secrets.ps1` because the issue is public, prints it, writes it to
`_local/scaffold-issue.md`, and **stops**. Read it, then re-run with `-Send` to actually create it.

This loop already returned local #183/#184/#185 as upstream v0.43 fixes #189/#188/#190. It also found
its own unbounded-ledger bug (#201), fixed upstream in v0.44 and backfilled here with adversarial tests.

## Direction 2 — deciding whether to take an upstream release

```powershell
git remote add scaffold https://github.com/Asun28/claude-devops-scaffold.git   # once
pwsh -File scripts\scaffold-sync.ps1 check -Fetch
```

`check` prints only the CHANGELOG **Downstream** block of each release between this project's
high-water mark and the newest upstream tag. Those blocks carry the **coupling groups** — which files
must be backfilled together and what breaks if you take only half — and that is information a raw
`git diff` can never give you.

Patch application is deliberately **not** automated: inspect the coupling group, run `git diff`, then
`git apply --3way --check` before applying.

**Not taking a version is a first-class outcome.** An upstream change can be wrong *for this project*
— it can delete a gate we added on purpose, rewrite a script we have customised, or touch a file that
does not exist in our tree. Writing the reason down settles the question; `check` then stops raising
it. A row you delete silently reopens a settled argument.

`scripts/triage.ps1` probe `scaffold-stale` surfaces this state on the normal cadence sweep. It reads
the ledger below plus the upstream tags **already on disk** and **never touches the network** — that
keeps the heartbeat read-only, offline and deterministic (`docs/LOOP-ENGINEERING.md`). Refreshing is
the job of the explicit `check -Fetch`.

Staleness is advisory. It never enters `task.ps1 ship` and never blocks a merge: being behind the
scaffold is not a reason to stop shipping this app.

## Where this project deliberately forked from the scaffold

v0.30 made R3 advisory and v0.31 removed mandatory-gate hardening. This project deliberately keeps
R3 blocking—with round cap, diff budget, and head binding—so advisory-only follow-ons are declined.

## Decision ledger

<!-- One row per upstream release, newest first. Every release gets a row,
     including the ones skipped. `scaffold-sync.ps1 check` treats the newest version here as the
     high-water mark of what this project has evaluated, whatever the decision was; with no rows it
     falls back to the immutable provenance stamp in scripts/_config.ps1 (ScaffoldOriginVersion).
     ScaffoldVersion mirrors the latest evaluated ledger high-water mark. Decisions:
     applied = took it whole | partial = took some, reason says which and why | skipped = took none.
     Do not delete rows: a removed row silently reopens a settled question.
     This marker is executable scope, not decoration: only version-first rows below it whose second
     cell is applied/partial/skipped count. Removing the marker safely falls back to provenance. -->
<!-- SCAFFOLD-SYNC-LEDGER -->

| version | decision | date | reason / what was taken | issue |
|---|---|---|---|---|
| v0.45.0 | partial | 2026-08-26 | Took the seeded shard split (2 OS × 5 shards; gate 17 split into git/remote/scanner) and group 2 already applied locally at `f9070ff`: strict ledger, public report guards, and exact remote identity. Groups 1/3 remain partial; the remaining group 4 features are deferred. Origin is v0.29.0; current is v0.45.0. Tag `db835867e6f1bab740f13b48e4bae009a34521ef`. | upstream #259/#260/#261 / v0.45.0 |
| v0.44.0 | partial | 2026-08-24 | Took #201's bounded sync-ledger reader. Deferred card validation (31 live-card migration), seven shared-core selfchecks (overlaps PR #127), and handoff throttling (outside scope). Upstream `DocSyncMap` is N/A to this custom four-pair map. Tag `af4f5724cc5403bfa0521a68c552362bb96f4dd5`. | upstream #201 / v0.44.0 |
| v0.43.0 | partial | 2026-08-23 | Took our #188 ID cap, #189 guarded promote/demote, and #190 delivery-blocked fixes with hermetic triage tests. Deferred #180/#181/#182/#186/#187/#191–#195; #197 and required-CI fan-in are N/A. | our #183/#184/#185 |
| v0.42.0 | partial | 2026-08-23 | Took the fleet command, upstream config, `scaffold-stale`, and this ledger. Deferred divergent selftest 1g/12f wiring to its own card. | upstream #201 |
| v0.41.0 | partial | 2026-08-23 | Took TD130's BOM-safe card parser. Handoff sentinels already existed; init/mutate items are N/A; lessons `-NoNewline` remains follow-up. Kept this project's Chinese R3 operator surface. | |
| v0.40.0 | partial | 2026-08-23 | Took evidence-based subtraction (19 resident IDs → 9). Deferred upstream-specific `EXPIRES-WHEN:` prose/hints to a local pass. | |
| v0.39.0 | skipped | 2026-08-23 | Keep task-loop 4.6/selftest 11c: L205 requires fresh adversarial review after repair. | |
| v0.38.0 | skipped | 2026-08-23 | Keep load-bearing Tier-1 L165 mutation evidence and L196 restore safety; local probe 9 identifies the evidence-based demotion set. | |
| v0.37.0 | partial | 2026-08-23 | **Workflow half already applied**: this tree keeps `scaffold-selftest.yml` off `pull_request` and locks both triggers with gate 8.2d/8.2e. The `task.ps1` half is **N/A**: this project has no CI check gate, so there is no shard-matrix expectation to deadlock. | |
| v0.36.0 | skipped | 2026-08-23 | **Deferred to its own card**: ASCII state codes wave 2 needs 23 local selftest re-anchors against a divergent tree. | |
| v0.35.0 | skipped | 2026-08-23 | **Superseded locally** by `T0-LESSONS-COLD-RECALL` / PR #51; do not double-apply. | |
| v0.34.0 | skipped | 2026-08-23 | **Deferred with v0.36.0**: ASCII state codes wave 1 and its 37 local selftest re-anchors belong in the same card. | |
| v0.33.0 | skipped | 2026-08-23 | Upstream's diet targets its own `CLAUDE.md`; the useful subtraction protocol is grouped with the v0.40 local follow-up. | |
| v0.32.0 | skipped | 2026-08-23 | **Not applicable**: this project has no CI merge gate and no `mutate.ps1`; nothing to apply. | |
| v0.31.0 | skipped | 2026-08-23 | **Deliberate fork**: keep the R3 reviewer-sandbox defenses and T35 waterline receipts; see "Where this project deliberately forked" above. | |
| v0.30.0 | skipped | 2026-08-23 | **Deliberate doctrine fork**: keep R3 and RED as ship gates; do not re-litigate without an explicit merge-policy ruling. | |

## Still open from the versions above

Each deferred item below is a future card, not a patch hunk.

| what | source release | why it is its own card |
|---|---|---|
| ASCII state codes + selftest re-anchors | `v0.34.0`+`v0.36.0` | ~60 divergent anchors |
| Bounded `_context.ps1` hook injection | `v0.43.0` #187 | shared core + all hook call sites |
| ADR header/check contract | `v0.43.0` #193 | six legacy ADRs need a waiver decision |
| Card `sweep:` for >5 paths | `v0.43.0` #194 | migrate existing oversized cards first |
| Declared card-rule examples | `v0.43.0` #191 | local `_cards.ps1` requires a rewrite |
| Doc budgets, gate map, PR titles, archive projections | `v0.43.0` #192/#195/#180–#182 | batch with selftest wiring |
| `lessons.ps1 -NoNewline` | `v0.41.0` | reconcile after PR #51 |
| Card scalar/rule package, including empty-scalar fix | `v0.44.0` | overlaps `T0-CARD-ACCEPTANCE-FIELD`; migrate 31 cards |
| Shared-core selfchecks | `v0.44.0` | add local examples for seven cores after PR #127 |
| Content-aware handoff throttle | `v0.44.0` | hooks/selftest need a RED-first card |
| Shared metadata, PSGallery hardening, and remaining R3 review package | `v0.45.0` groups 1/3/4 | local equivalents are partial or overlap active work; migrate in dedicated cards |
