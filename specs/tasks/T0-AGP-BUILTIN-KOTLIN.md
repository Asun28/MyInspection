---
id: T0-AGP-BUILTIN-KOTLIN
title: Move :app to AGP's built-in Kotlin and new DSL, and drop the plugin aliases no module applies
status: todo
depends_on: [T2-PHOTO-INVARIANT-CLEANUP]
parallelizable_with: []
allow_paths:
  - android/gradle.properties
  - android/build.gradle.kts
  - android/app/build.gradle.kts
  - android/gradle/libs.versions.toml
  - docs/LICENSE-POLICY.md
  - specs/tasks/T0-AGP-BUILTIN-KOTLIN.md
sweep: "rg 'builtInKotlin|newDsl|suppressAgpWarnings|kotlin\\.android|kotlin-android|android\\.library|android-library' over tracked files outside specs/archive and the lesson ledger (2026-10-14, 01a2e801): android/gradle.properties:13-19 (the two opt-outs, their comments, the warning suppression), android/app/build.gradle.kts:11 and android/build.gradle.kts:4,6 (plugin aliases), android/gradle/libs.versions.toml:65,67 (catalog entries), docs/LICENSE-POLICY.md:63 (lists the kotlin-android alias and plugin id). LICENSE-POLICY.md:68 names com.android.library as a plugin the AGP artifact provides, which stays true, so it stays. scripts/ and configs/ name neither plugin id."
forbid:
  - Changing a dependency version, compileSdk, minSdk, targetSdk, the JVM toolchain, the Compose BOM or any plugin version
  - Changing :core's build or the backup-manifest verification task in android/app/build.gradle.kts
  - New Gradle or runtime dependencies
non_goals:
  - Upgrading AGP, Kotlin or Gradle
  - The configuration cache Gradle suggests at the end of each build
acceptance:
  - "A1 android/gradle.properties no longer sets android.builtInKotlin, android.newDsl or android.sync.suppressAgpWarnings, and the comments that explained them go with them."
  - "A2 No build file applies or declares org.jetbrains.kotlin.android, and the version catalog no longer lists kotlin-android or android-library (no module applies com.android.library). docs/LICENSE-POLICY.md stops listing the kotlin-android alias and its plugin id."
  - "A3 A full offline rebuild (--rerun-tasks --no-build-cache) of :app:assembleDebug :app:check :core:check passes, and its output no longer carries the warning Deprecated 'org.jetbrains.kotlin.android' plugin usage, which the same build prints on the base."
  - "A4 :app still runs on Kotlin 2.4.10: dependencyInsight on debugRuntimeClasspath resolves org.jetbrains.kotlin:kotlin-stdlib to 2.4.10."
dod_command: $gp = Get-Content -Raw -LiteralPath android/gradle.properties; if ($gp -match '(?m)^\s*android\.(builtInKotlin|newDsl|sync\.suppressAgpWarnings)\s*=') { Write-Host '[DOD-FAIL] an AGP opt-out is still set'; exit 1 }; $cat = Get-Content -Raw -LiteralPath android/gradle/libs.versions.toml; if ($cat -match '(?m)^\s*(kotlin-android|android-library)\s*=') { Write-Host '[DOD-FAIL] an unused plugin alias is still in the catalog'; exit 1 }; $b = (Get-Content -Raw -LiteralPath android/build.gradle.kts) + (Get-Content -Raw -LiteralPath android/app/build.gradle.kts); if ($b -match 'libs\.plugins\.(kotlin\.android|android\.library)|org\.jetbrains\.kotlin\.android') { Write-Host '[DOD-FAIL] a build file still names a removed plugin'; exit 1 }; if ((Get-Content -Raw -LiteralPath docs/LICENSE-POLICY.md).Contains('org.jetbrains.kotlin.android')) { Write-Host '[DOD-FAIL] LICENSE-POLICY.md still lists the kotlin-android plugin'; exit 1 }; $o = (cmd /c "android\gradlew.bat -p android --offline --no-daemon --rerun-tasks --no-build-cache :app:assembleDebug :app:check :core:check 2>&1" | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 4000))); Write-Host "[DOD-FAIL] gradle rc=$rc"; exit 1 }; if ($o.Contains("Deprecated 'org.jetbrains.kotlin.android' plugin usage")) { Write-Host '[DOD-FAIL] the kotlin-android deprecation still prints'; exit 1 }; $d = @(cmd /c "android\gradlew.bat -p android --offline --no-daemon -q :app:dependencyInsight --dependency org.jetbrains.kotlin:kotlin-stdlib --configuration debugRuntimeClasspath 2>&1" | Where-Object { $_.Trim() }); if ($LASTEXITCODE -ne 0 -or $d.Count -eq 0 -or $d[0].Trim() -cne 'org.jetbrains.kotlin:kotlin-stdlib:2.4.10') { Write-Host "[DOD-FAIL] app kotlin-stdlib is not 2.4.10: $($d | Select-Object -First 1)"; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The opt-outs, the two catalog aliases and the plugin references are gone, LICENSE-POLICY.md no longer lists the kotlin-android plugin, a full offline rebuild passes without the kotlin-android deprecation, and :app resolves kotlin-stdlib 2.4.10; prints [DOD-PASS]. On base it exits 1 at the first check because gradle.properties sets android.builtInKotlin=false.
review_gate: codex {verdict:pass}
budget: 60
hygiene: R4, recorded in the R5 note - put back each removed gradle.properties line, catalog alias and plugin line one at a time; each must make the DoD exit 1 (the text checks, or the build failing on the restored kotlin-android plugin).
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-AGP-BUILTIN-KOTLIN

From the 2026-10-14 unused-code audit (plan: `docs/plans/PLAN-AUDIT-CLEANUP-20261014.md`, aidlc goal
`g-20261014091214-314b46`).

## Why now

`T0-TOOLCHAIN` turned off two AGP 9 defaults, built-in Kotlin and the new DSL, because SQLDelight and the classic
`org.jetbrains.kotlin.android` plugin had not been checked against them in 2026-08. AGP 10 removes both opt-outs,
and every build already warns that the plugin is deprecated. Only `:app` applies AGP; `:core` is a plain
Kotlin/JVM module with SQLDelight, so the new DSL never reaches SQLDelight.

## Probe before this card was written

On `01a2e801`, a throwaway detached worktree with the A1 and A2 edits ran
`:app:assembleDebug :app:testDebugUnitTest :app:check :core:check` offline: BUILD SUCCESSFUL, no deprecation
warning. `buildEnvironment` showed AGP's `kotlin-gradle-plugin:2.2.10 -> 2.4.10` (the root `apply false`
declarations of the other Kotlin plugins keep 2.4.10 on the classpath), and `:app` resolved `kotlin-stdlib:2.4.10`.
The `android.library` alias goes too: no module applies it.

## Order

The dependency on `T2-PHOTO-INVARIANT-CLEANUP` is about order, not code: that card's DoD reads `:app` compiler
warnings in the `w:` line form, and this card changes how `:app` compiles Kotlin, so that card ships first.
Both cards build the whole Android project, so they never run at the same time.

## Catalog rule

The catalog's header says later cards change only source directories, so that parallel cards do not collide on
it. This card is a toolchain card like `T0-TOOLCHAIN`, runs alone, and removes two entries only; the user
delegated the decision on 2026-10-14.
