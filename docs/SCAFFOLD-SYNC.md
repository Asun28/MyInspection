# Fleet loop: this project and the scaffold it came from

> Entry point: `scripts/scaffold-sync.ps1`. This file is both the chain document and the decision
> ledger — the ledger table lives at the bottom, under the `SCAFFOLD-SYNC-LEDGER` sentinel.

`init-scaffold.ps1` is a one-time snapshot. Left alone, the relationship with the upstream scaffold
decays in both directions at once: fixes made upstream never reach this project, and problems found
here never reach upstream. Two commands close that into a loop.

```
   MyInspection  --- report (an issue) --->  claude-devops-scaffold
        ^                                          |
        |                                          v
        +---  check / decide / patch  <---  a new release
```

Neither direction is automatic. Both are things a human or an agent decides to do, and both leave a
record.

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

That composes the issue body, stamps it with provenance (scaffold version, OS, PowerShell version),
scans it with `check-secrets.ps1` because the issue is public, prints it, writes it to
`_local/scaffold-issue.md`, and **stops**. Read it, then re-run with `-Send` to actually create it.

**This direction has already paid, twice.** Installing the loop found a defect in the loop itself:
`Get-SyncedVersion` ignored its own `SCAFFOLD-SYNC-LEDGER` sentinel and read every table in this file,
so an unrelated version cell could silently become the high-water mark. Filed as upstream **#201**;
upstream v0.44.0 repaired it, and the bounded reader plus four adversarial fixtures are backfilled here.

**And before that.** Three issues filed from here on 2026-08-21 — #183 (promote probe
ignores `enforced_by`), #184 (the must-tier cap counts bullets, not context cost), #185 (every triage
probe is blind to delivery state) — came back as upstream fixes #189, #188 and #190 in v0.43.0, and
those are exactly what this project backfilled first.

## Direction 2 — deciding whether to take an upstream release

```powershell
git remote add scaffold https://github.com/Asun28/claude-devops-scaffold.git   # once
pwsh -File scripts\scaffold-sync.ps1 check -Fetch
```

`check` prints only the CHANGELOG **Downstream** block of each release between this project's
high-water mark and the newest upstream tag. Those blocks carry the **coupling groups** — which files
must be backfilled together and what breaks if you take only half — and that is information a raw
`git diff` can never give you.

Applying a patch is deliberately **not** automated. It stays three git commands under human decision:

```
git diff scaffold-tags/v<old>..scaffold-tags/v<new> -- scripts/ .claude/ .github/ > _local/scaffold-backfill.patch
git apply --3way --check _local/scaffold-backfill.patch
git apply --3way _local/scaffold-backfill.patch
```

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

Two upstream releases are **permanently** declined, and the reason is one decision, not fourteen:

**Upstream v0.30.0 demoted the R3 second-model review from a mandatory merge gate to an advisory
opinion, and v0.31.0 then deleted the ~2,066 lines of hardening that only a mandatory gate needs.**
This project went the other way: R3 here is a blocking merge gate with a round cap
(`ReviewRoundCap`), a diff budget, and `-ExpectHead` commit-identity binding, because this is a
free/private repo with no server-side required checks — local gates plus R3 *are* the merge闸.
Upstream's own changelog states the consequence plainly: *"downstream wanting a hardened mandatory
review should pin ≤0.30.0."*

Everything downstream of that fork which exists **only** to serve the advisory model is declined for
the same reason, and rows below say so rather than repeating the argument.

## Decision ledger

<!-- SCAFFOLD-SYNC-LEDGER: one row per upstream release, newest first. Every release gets a row,
     including the ones skipped. `scaffold-sync.ps1 check` treats the newest version here as the
     high-water mark of what this project has evaluated, whatever the decision was; with no rows it
     falls back to the provenance stamp in scripts/_config.ps1 (ScaffoldVersion). Decisions:
     applied = took it whole | partial = took some, reason says which and why | skipped = took none.
     Do not delete rows: a removed row silently reopens a settled question.
     This marker is executable scope, not decoration: only version-first rows below it whose second
     cell is applied/partial/skipped count. Removing it safely falls back to provenance. -->

| version | decision | date | reason / what was taken | issue |
|---|---|---|---|---|
| v0.44.0 | partial | 2026-08-24 | **Applied the sync-ledger coupling group**: `scripts/scaffold-sync.ps1` now reads only decision-shaped rows below the sentinel, repairing #201; selfcheck pins decoys above/below the ledger, a missing sentinel, and a version-first non-decision row. **Deferred** the card-validation group because its mutation-registry rule would reject 31 current live cards and overlaps `T0-CARD-ACCEPTANCE-FIELD`; deferred shared-core selfchecks because all 7 local cores need local examples and the group overlaps PR #127; deferred content-aware handoff throttling to its own card because hook/selftest files are outside this card's registered scope. **N/A:** T115's upstream default `DocSyncMap` expansion is deliberately not mandatory post-init; this project keeps its four-pair custom map and lacks two upstream sources named by that map. Official tag `v0.44.0` verified at `af4f5724cc5403bfa0521a68c552362bb96f4dd5`. | upstream #201 / v0.44.0 |
| v0.43.0 | partial | 2026-08-23 | **Took the three fixes this project filed upstream**: #188 cap counts resident lesson IDs (new `scripts/_lessons.ps1` + `lessons.ps1 check` + triage probe 5), #189 promote probe reads `enforced_by` and gained its inverse (new probe `lessons-demote`), #190 `delivery-blocked` probe. All four wired into `triage.ps1 selfcheck` with hermetic fixtures; six single-line mutations kill them. **Left for follow-up cards**: #186/#187/#191/#192/#193/#194/#195, #180, #181/#182 — see the open table below. **N/A here**: #197 (`mutate.ps1` does not exist in this tree), #186's `required` fan-in job (this project has no CI merge gate). | our #183/#184/#185 |
| v0.42.0 | partial | 2026-08-23 | The fleet loop itself: `scripts/scaffold-sync.ps1`, `_config.ps1` `UpstreamRepo` + `Get-ScaffoldUpstreamRepo`, triage probe `scaffold-stale`, and this document. **Not taken**: the `selftest.ps1` gate 1g/12f wiring — this project's selftest has diverged to 10,987 lines against upstream's 8,446, so the wiring is its own card, not a patch hunk. | upstream #201 |
| v0.41.0 | partial | 2026-08-23 | Took **TD130**: `_cards.ps1` front-matter parser tolerates a leading U+FEFF, so card text piped through `git show BASEREF:specs/tasks/<id>.md` no longer loses its front-matter and makes full-form `check-scope` fail closed with `[SCOPE-UNDECIDABLE]` blaming the card. Mutation-proven (without the anchor, BOM-prefixed front matter parses to null). **Already present**: the `HANDOFF-REVALIDATE` sentinel trio. **N/A**: `init-scaffold.ps1` pruning (no init script in a generated tree), `mutate.ps1` `-NoNewline`. **Follow-up**: `lessons.ps1` `-NoNewline`. **Skipped**: R3 English fail-closed reasons — this project's `[R3-*]` operator surface is Chinese and `QUALITY-RUBRIC.md` §5 is anchored to it. | |
| v0.40.0 | partial | 2026-08-23 | Took the **idea** rather than the text: the subtraction it prescribes was carried out here (19 resident IDs -> 9), on evidence from the LEDGER's own `enforced_by` and `recurrence` fields. Not taken: the literal `EXPIRES-WHEN:` annotations and the four selftest `Fail` hint strings plus hint text in four selftest `Fail` messages, two of which are meta-only. The `EXPIRES-WHEN:` idea is worth adopting, but it is written against upstream's 5-law Tier-1 while ours currently holds **19** resident lesson IDs — copying the section would describe the wrong repo. Registered as a follow-up to do as our own pass. | |
| v0.39.0 | skipped | 2026-08-23 | Deletes task-loop step 4.6 and selftest gate 11c on the grounds that Opus 5 self-verifies without being asked. **L205 is Tier-1 here** and says the opposite for the case that actually bit us: the *repair* round needs a fresh-context adversarial pass, because a repair diff is often larger than the first implementation and stops being read as new code. Deliberate divergence, revisit only with evidence from our own rounds. | |
| v0.38.0 | skipped | 2026-08-23 | Demotes L165 and L196 out of Tier-1. Both are load-bearing here — L165 is this project's mutation-evidence standard (cited by every card's hygiene field) and L196 is the cross-session restore guard that stopped a real mid-batch worktree corruption. The right demotion set for **this** project is the four that triage probe 9 now names on evidence (L3, L164, L171, L181), not upstream's two. | |
| v0.37.0 | partial (workflow half already applied) | 2026-08-23 | This tree already keeps `scaffold-selftest.yml` off `pull_request` and locks both triggers with its own gate 8.2d/8.2e. The `task.ps1` half is **N/A**: this project has no CI check gate at all — `task.ps1` merges on local gates plus R3 and treats CI `verify` as informational, so there is no shard-matrix expectation to deadlock. | |
| v0.36.0 | deferred | 2026-08-23 | ASCII state codes wave 2 (`check-cards.ps1`, `archive.ps1`, `check-secrets.ps1`, `review.ps1`, 23 selftest re-anchor sites). Genuinely valuable here — it is L165's own failure mode — but it is a re-anchoring exercise against a selftest that has diverged from upstream's, so it is a card, not a patch. Own card. | |
| v0.35.0 | superseded locally | 2026-08-23 | Lessons hot/cold split (`lessons.ps1 archive`). **Already being implemented independently** by card `T0-LESSONS-COLD-RECALL` (PR #51, +148 lines in `lessons.ps1`). Do not double-apply; reconcile after that PR merges. | |
| v0.34.0 | deferred | 2026-08-23 | ASCII state codes wave 1 (`task.ps1` ~25 message lines, 37 selftest sites). Same family and same reasoning as v0.36.0 — take both in one card or neither. | |
| v0.33.0 | skipped | 2026-08-23 | Upstream's quantified subtraction protocol plus the diet it was first applied to. The diet is upstream's own `CLAUDE.md`; the protocol is worth adopting and is registered as a follow-up together with v0.40.0, because both are about the same thing: a candidate generator for cutting resident prose. Our 19/10 overage is the case that needs it. | |
| v0.32.0 | not applicable | 2026-08-23 | CI check gate on every merge path (this project has no CI merge gate — see v0.37.0) and `mutate.ps1` evidence coherence (no `mutate.ps1` in this tree; mutation batches here are per-card ad-hoc scripts). Nothing to apply. | |
| v0.31.0 | skipped (deliberate fork) | 2026-08-23 | Deletes the R3 reviewer-sandbox defenses and the T35 waterline receipts because the threat model died with the advisory review. Both are live here: `review.ps1` still carries `[R3-REVIEW-DIR-UNSAFE]` and `task.ps1` still carries `[T35-RECEIPT]`/`[TD85-RESUME]`. See "Where this project deliberately forked" above. | |
| v0.30.0 | skipped (deliberate fork) | 2026-08-23 | Demotes R3 to advisory (`ReviewGate`) and removes the RED evidence gate from ship. This is the doctrine fork. Settled — do not re-litigate without an explicit ruling that changes how this repo merges. | |

> Row shape, for reference only (blockquoted so the parser does not read it as a real row):
>
> | version | decision | date | reason / what was taken | issue |
> |---|---|---|---|---|
> | v0.44.0 | applied | 2026-09-02 | whole coupling group (`_guard.ps1` + `selftest.ps1`) | #12 |

## Still open from the versions above

<!-- Version cells here remain backticked for readability, but v0.44.0 removed the correctness
     dependency on that convention: the parser now requires both ledger region and row shape. -->
These are the pieces that were judged worth taking but are too big or too entangled to ride along
with the backfill card that opened this ledger. Each one is a card, not a patch hunk.

| what | source release | why it is its own card |
|---|---|---|
| ASCII state codes for `task.ps1` / `review.ps1` / `check-cards.ps1` / `archive.ps1` / `check-secrets.ps1`, with the selftest anchors moved onto the codes | `v0.34.0`+`v0.36.0` | ~60 re-anchor sites against a selftest that diverged from upstream's; closes L165's own encoding-fragile channel |
| ~~Tier-1 subtraction pass~~ **done in the same card**: 19 resident IDs -> 9 (10 demoted: 4 machine-guarded named by probe 10, 6 with `recurrence: 1`) | `v0.33.0`+`v0.40.0` (protocol) | the ceiling was the reason to do it; the per-rule judgement used only LEDGER fields (`enforced_by`, `recurrence`), never taste |
| `_context.ps1` bounded, framed hook injection | `v0.43.0` #187 | new shared library plus every hook call site |
| ADR header contract + `check-adr.ps1` (sub-gate 14h) | `v0.43.0` #193 | this project has 6 ADRs written before the contract; needs the waiver marker decision |
| Card `sweep:` field for cards above five `allow_paths` (sub-gate 10h) | `v0.43.0` #194 | would fail existing oversized cards at `-Phase start` until each declares its sweep |
| Card-rule declared examples (`_cards.ps1` + `check-cards.ps1`) | `v0.43.0` #191 | our `_cards.ps1` is 83 lines against upstream's 230 — a rewrite, not a merge |
| Doc character budgets (sub-gate 14g), gate map (14i), generated PR titles, archive indices as verified projections | `v0.43.0` #192/#195/#180/#181/#182 | each is small on its own; batch them once the selftest wiring above lands |
| `lessons.ps1` writes with `-NoNewline` | `v0.41.0` | trivial, but collides with PR #51 which is rewriting the same file |
| Card scalar/rule package: empty scalar, duplicate TD claims, nested-pwsh DoD exit, mutation-registry ownership, acceptance warning | `v0.44.0` | overlaps the narrower `T0-CARD-ACCEPTANCE-FIELD`; the registry rule would reject 31 live cards because this project still uses per-card ad-hoc mutation evidence rather than upstream's `specs/mutations/` registry |
| Shared-core selfcheck gate and examples | `v0.44.0` | all 7 local function-bearing `scripts/_*.ps1` cores currently lack the upstream example contract; port as a local migration after PR #127 instead of copying 1,431 divergent lines |
| Content-aware handoff reminder throttle | `v0.44.0` | compatible and independent, but its two hooks plus standing selftest are outside this card's registered `allow_paths`; give it its own RED-first card |

**Resolved upstream, not yet backfilled here:** v0.44.0 repairs `Get-Scalar` capturing the following
line for an empty field. It belongs to the deferred card scalar/rule package above, not this fleet-loop
card, because the coupled validation migration must be evaluated against this project's live cards.
