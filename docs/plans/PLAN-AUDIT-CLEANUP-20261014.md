# Plan: unused-code audit cleanup (2026-10-14)

Goal `g-20261014091214-314b46` (aidlc, size T1, target development). The user asked on 2026-10-14 to fix every
finding of that day's unused-code audit through right-sized cards, worktrees and PRs, and delegated every
decision because they were unavailable. This plan records what the audit found, what each finding becomes,
and the decisions taken under that delegation.

## Findings and where each one goes

| Finding | Origin | Becomes |
|---|---|---|
| `scripts/_validation.ps1` (502 lines): no function in it has a caller outside the file; only its own examples run, from selftest gate 1h | local, `T0-SELFTEST-RISK-ROUTING` (#273); #297 replaced `selftest -TaskId` with the upstream tier routing | `T0-SCRIPTS-ORPHAN-CLEANUP` |
| `Add-GradleLicenseFinding` (`scripts/check-licenses.ps1`) and the nested `Get-WorkflowActiveStepIndices` (`scripts/license-scanner-check.ps1`): no caller | local (#20, `97a6e220`) | `T0-SCRIPTS-ORPHAN-CLEANUP` |
| `scripts/fixtures/gate2/android/gradlew{,.bat}`: no script reads them; selftest writes its own stubs | local (`T3-E2E-*`) | `T0-SCRIPTS-ORPHAN-CLEANUP` |
| `PhotoOrphanCleanupReport` has a private constructor but a public generated `copy()` | local | `T2-PHOTO-INVARIANT-CLEANUP` |
| `PhotoImportPipeline.kt`: `check(plan is PhotoIngestPlan.WriteNewAsset)` that the compiler proves always true | local | `T2-PHOTO-INVARIANT-CLEANUP` |
| `android.builtInKotlin=false` and `android.newDsl=false`: opt-outs AGP removes in 10.0; the build warns that the `org.jetbrains.kotlin.android` plugin is deprecated | local (`T0-TOOLCHAIN`) | `T0-AGP-BUILTIN-KOTLIN` |
| `android-library` plugin alias declared in the catalog and the root build, applied by no module | local (`T0-TOOLCHAIN`) | `T0-AGP-BUILTIN-KOTLIN` |
| 13 `Prereview*` knobs in `scripts/_config.ps1` that no code reads, because the prereview runner has not shipped | local | TD192 (TD188 already covers `PrereviewRiskyEffort`) |
| Five upstream card checks that `scripts/check-cards.ps1` keeps switched off for the legacy card corpus | upstream, adopted in #297 | TD193 |
| Seven adopted functions with no caller, four `Upstream*` knobs whose accessors read other key names, and a self-check gate that passes a file once any one of its self-checks runs | upstream, adopted in #297 | upstream issues, recorded in `docs/SCAFFOLD-SYNC.md` |

Kept on purpose and not changed: the Compose and settings screens, `reminderScheduler()` and the core services
that only tests call (they wait for `T2-CAPTURE-UI` and the other wiring cards), the 15 SQLDelight queries only
tests call (frozen), the skeleton (deleted by `T2-CAPTURE-UI`), `compose-ui-tooling` and
`compose-ui-tooling-preview` (debug tooling and the preview annotations, kept for the UI cards), the four Python
steps in `ci.yml` that skip when `pyproject.toml` is absent (scaffold design), and the `PlanDir` comment in
`scripts/_config.ps1` (it describes the scaffold repository and is accurate).

## Order of work

1. Register the three cards, TD192, TD193 and this plan (docs-only PR, CI-gated). File the upstream issues
   first so the ledger entry can link them.
2. `T0-SCRIPTS-ORPHAN-CLEANUP` (Tier S: full selftest on the shipped candidate).
3. `T2-PHOTO-INVARIANT-CLEANUP` (Tier 1). Disjoint paths from step 2, so its build work can overlap the
   step 2 selftest; it ships after step 2 because one R3 reviewer serves both.
4. `T0-AGP-BUILTIN-KOTLIN` (Tier 1) after step 3, so the two Gradle-heavy cards never build at the same time.
5. Integrated acceptance on the final master: `scripts/verify.ps1`, `:app:check`, `:app:assembleDebug`.

## Risks

- Deleting `_validation.ps1` removes a module selftest gate 1h runs; gate 1h must still pass with the two other
  local cores it runs.
- The AGP migration changes how `:app` compiles Kotlin. A probe on `01a2e801` built and tested `:app` offline
  with built-in Kotlin before the card was written; the card still has to show the Kotlin version stays 2.4.10.
- `PhotoImportPipeline.kt` is read as text by two core wiring tests; the change keeps every fragment they assert.

## Proof

Each card's DoD, `verify.ps1`, Codex R3 and CI. Integrated acceptance runs once more on the final master.
