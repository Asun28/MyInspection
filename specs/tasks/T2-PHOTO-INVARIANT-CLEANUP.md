---
id: T2-PHOTO-INVARIANT-CLEANUP
title: Keep PhotoOrphanCleanupReport's copy() as private as its constructor, and drop the import pipeline's always-true check
status: merged
depends_on: []
parallelizable_with: [T0-SCRIPTS-ORPHAN-CLEANUP]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/media/PhotoOrphanCleanupRunner.kt
  - android/core/src/test/kotlin/nz/myinspection/core/media/PhotoOrphanCleanupRunnerTest.kt
  - android/app/src/main/kotlin/nz/myinspection/app/media/PhotoImportPipeline.kt
  - specs/tasks/T2-PHOTO-INVARIANT-CLEANUP.md
forbid:
  - Changing what PhotoOrphanCleanupReport.from decides, its fields, or any other class in either file
  - Changing the import pipeline's plan, lease, publish or record steps, or the text the core wiring tests assert (PhotoOrphanCleanupWiringTest, PhotoStreamingWiringTest)
  - New Gradle or runtime dependencies, including kotlin-reflect
non_goals:
  - The same check in CameraPhotoIngestPipeline.kt and GoldenEvidenceCoreHarness.kt, where the compiler cannot prove it (their plan is not narrowed first)
  - The other compiler warnings the 2026-10-14 audit saw (test-only expression and !! warnings, PhotoJpegEncoder's parameter name)
  - Historical R4 receipts that pin an earlier PhotoImportPipeline.kt SHA-256 (SafeLogTest.kt); they describe the file as it was then
acceptance:
  - "A1 PhotoOrphanCleanupReport carries @ConsistentCopyVisibility, so its generated copy has the private constructor's visibility. A new test in PhotoOrphanCleanupRunnerTest finds, through Java reflection on the class, no public method named copy or copy$default, and fails on the base, where copy is public."
  - "A2 In PhotoImportPipeline.kt the publicationLease lambda no longer calls check(plan is PhotoIngestPlan.WriteNewAsset). The enclosing if (plan is PhotoIngestPlan.WriteNewAsset) still narrows the plan, and the core wiring tests that read the file as text pass unchanged."
  - "A3 After a full recompile, the Kotlin compiler prints neither the copy() exposure warning for PhotoOrphanCleanupRunner.kt nor the always-true check warning for PhotoImportPipeline.kt, and :core:test and :app:testDebugUnitTest pass."
dod_command: $o = (cmd /c "android\gradlew.bat -p android --offline --no-daemon --rerun-tasks --no-build-cache :core:test :app:testDebugUnitTest 2>&1" | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 4000))); Write-Host "[DOD-FAIL] gradle rc=$rc"; exit 1 }; if ($o -notmatch "PhotoJpegEncoder\.kt:\d+:\d+ The corresponding parameter in the supertype") { Write-Host '[DOD-FAIL] the compiler warnings are not printed in the form this DoD reads'; exit 1 }; if ($o -match "PhotoOrphanCleanupRunner\.kt:\d+:\d+ Non-public primary constructor is exposed") { Write-Host '[DOD-FAIL] copy() still exposes the private constructor'; exit 1 }; if ($o -match "PhotoImportPipeline\.kt:\d+:\d+ Check for instance is always 'true'") { Write-Host '[DOD-FAIL] always-true check still compiled'; exit 1 }; $x = 'android/core/build/test-results/test/TEST-nz.myinspection.core.media.PhotoOrphanCleanupRunnerTest.xml'; if (-not (Test-Path -LiteralPath $x) -or -not (Select-String -LiteralPath $x -SimpleMatch 'copyIsAsPrivateAsTheConstructor' -Quiet)) { Write-Host '[DOD-FAIL] the A1 test did not run'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The core and app JVM suites pass after a full recompile, the compiler output still shows the unrelated PhotoJpegEncoder warning (so warnings print in the w:-line form the checks read) but carries neither A3 warning, and the PhotoOrphanCleanupRunnerTest results contain copyIsAsPrivateAsTheConstructor; prints [DOD-PASS]. On base it exits 1 because the A1 test fails (copy is public) and both warnings print.
review_gate: codex {verdict:pass}
budget: 80
hygiene: R4 single-point mutant, recorded in the R5 note with the production-file SHA-256 - remove @ConsistentCopyVisibility; copyIsAsPrivateAsTheConstructor must fail. A2 deletes dead code, so the compiler-warning check in the DoD is its proof.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T2-PHOTO-INVARIANT-CLEANUP

From the 2026-10-14 unused-code audit (plan: `docs/plans/PLAN-AUDIT-CLEANUP-20261014.md`, aidlc goal
`g-20261014091214-314b46`).

## A1: copy() bypasses the private constructor

`PhotoOrphanCleanupReport` keeps its constructor private so that `from()` is the only place a report is
built, deriving `decision` from the two cleanup results. Kotlin still generates a public `copy()` for a data class
with a private constructor, so any caller can build a report whose `decision` disagrees with its results.
Kotlin 2.4 warns about this, and `@ConsistentCopyVisibility` is the documented fix: `copy()` takes the
constructor's visibility. No tracked code calls `copy()` on this class.

## A2: the import pipeline re-checks what the compiler already knows

`PhotoImportPipeline.ingest` enters the write path only inside `if (plan is PhotoIngestPlan.WriteNewAsset)` and
passes `{ newAssetPlan }` as the plan, so the lease lambda's `check(plan is PhotoIngestPlan.WriteNewAsset)` can
never fail; the compiler reports it as always true. The camera pipeline's copy of the same line stays, because
its plan is not narrowed first.

## R5 (2026-10-15)

Merged by PR #462 (squash `95876b26`, reviewed head `1169b405`, CI `38680376569` attempt 1). Codex R3 round 1
passed both axes with zero findings.

**hygiene.** R4 single-point mutant on `PhotoOrphanCleanupRunner.kt` (SHA-256
`298B194CA5CF534A7E2A0935291126A9D22D9A97B2D063C65E8BF00A9E656181` at `1169b405`): removing
`@ConsistentCopyVisibility` makes `copyIsAsPrivateAsTheConstructor` fail; the file was restored and its hash
checked. A2 only deletes dead code, so the DoD's compiler-warning check is its proof.

**doc_sync.** This R5 sets the status, the TASK-BOARD row and the CLAUDE.md current-stage entry.

**Ship history.** Attempt 1 stopped at the license gate with `[GRADLE-METADATA]`: Gradle's 30-day cache cleanup had
deleted POMs the scanner reads (TD176). Refreshing the four resolved graphs online first (L329) fixed it. Attempt 2
was stopped during the DoD for low system memory. Attempt 3 passed every gate.
