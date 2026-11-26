---
id: T0-MAIN-SCREEN-INSETS
title: On API 35 the action bar no longer covers the title and the Address label of the main screen, proven by a ui-verify before and after pair
depends_on: []
parallelizable_with: []
status: todo
branch: T0-MAIN-SCREEN-INSETS
worktree: D:\wt\MyInspection\T0-MAIN-SCREEN-INSETS
allow_paths:
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/res/values/themes.xml
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - .claude/skills/verify-main-screen/SKILL.md
  - specs/tasks/T0-MAIN-SCREEN-INSETS.md
requirements:
  - R1. Before any production file changes, the card shall record a confirmed root cause, from device observations on the base APK and on at most one scratch probe APK that is never committed.
  - R2. WHEN MainActivity starts on the API 35 emulator, the app shall show the title "Walking skeleton" and the label "Address" with no system bar and no app bar drawn over them.
  - R3. The check behind the row `visible` of verify-main-screen shall fail when a status bar, a navigation bar or an action bar covers either node, with each bar's bounds read from the device, not a constant.
  - R4. verify-main-screen shall run the row `visible` without the known-violation mark, shall hold no text that calls it a known violation, shall list the files of the fix under Coverage, and shall name a Sync commit after which no covered file changes.
  - R5. The evidence shall hold a ui-verify before and after pair: the before run on the base APK with the row `visible` Not Met, and the after run on the candidate APK with every row Met, each with its command, observation and sha256.
  - R6. The run shall use made-up data on emulator-5580 only, shall keep every capture outside every worktree and out of git, shall not uninstall the app, and shall end with every process it started stopped by pid and named.
forbid:
  - A change under android/app/src/main/kotlin/nz/myinspection/app/skeleton/, scripts/, .github/, .claude/agents/, .claude/hooks/, CLAUDE.md or docs/DELIVERY-LOG.md, or any change to the AIDLC repository
  - android:windowOptOutEdgeToEdgeEnforcement, a fixed top padding in dp, or any other fix that holds only for one API level or one device height
  - A new dependency, a version change in libs.versions.toml, an instrumented or Robolectric test, aidlc init
  - Installing on, driving or capturing a physical phone; an adb call to a device that does not name emulator-5580; uninstalling the app; real property, tenant or account data
  - Committing a capture or a probe APK, or writing one inside any worktree of this repository
non_goals:
  - "An app bar or a title bar with the app name: the Field Ledger root screen of T2-CAPTURE-UI owns that"
  - "Other screens (ScheduleScreen, RetentionSettingsScreen, the debug activities), other API levels and a physical phone"
  - "Selftest gates 11 and 14, which already fail on master because CLAUDE.md:78 names the deleted scripts/_validation.ps1"
  - "Screenshot in CANDIDATE_BOUND_KINDS, a CI job that boots an emulator"
acceptance:
  - "A1 [R1] The Diagnosis section names the cause with at least two observations from the base APK on emulator-5580 (for example the theme the activity resolves and the bounds of the bar and of both nodes in one dump) and one discriminating probe whose result the cause predicts and the other candidate causes do not; a refuted hypothesis is recorded, not deleted (reviewer-verified against the diff)."
  - "A2 [R2] cmd /c android\\gradlew.bat ... :app:assembleDebug exits 0 (dod arm 3); the fix sits at the activity or app root, not in skeleton/, so that it outlives the deletion of skeleton/ by T2-CAPTURE-UI; the diff holds none of the forbidden fixes (reviewer-verified against the diff)."
  - "A3 [R3] The `seen` check of the driver compares each node's top and bottom with the bounds of the status bar, the navigation bar and the action bar where present, read in the same run. On the NoActionBar probe of the Diagnosis (no inset handling) the check reports `visible` Not Met; with the status bar bound forced to 0 it reports Met. Both results are in the evidence."
  - "A4 [R4] In verify-main-screen the `Row 'visible'` call has no `-Known`, the text holds no `known violation` / `Not Met (known)` wording, Coverage names each production file this card changes, and the Sync commit is a 40-digit ancestor of HEAD with `git diff --quiet <sync> HEAD` over the covered files (dod arms 1 and 2)."
  - "A5 [R5] Attempt evidence has a `before` run and an `after` run of the same driver text, each with its APK sha256, its exit code, one row per capture and at least four distinct capture sha256 values in total (dod arm 4); before: `visible` Not Met and exit 2; after: every capture row Met, and either exit 0 with the screen recording Met, or exit 2 where the screen recording is the only row not Met and its row is Unverified with a reason (reviewer-verified against the diff)."
  - "A6 [R6] Attempt evidence names the device serial, API level and ro.kernel.qemu value, the capture directory, and for each started process (each member a run's job lists, short-lived ones included) its name, pid and the probe that shows it is gone; the one exception is the first `diag.ps1` run 3 of 2026-11-26, whose process lines were lost: its repeat with a kept transcript and the process sweep after it stand in for it (owner amendment of 2026-11-27); `git ls-files` lists no capture or APK (reviewer-verified against the diff)."
  - "A7 selftest -Only 9 passes (dod arm 5). On a commit C whose summary lines are in the evidence, of the GATE-MAP gates of the changed paths, every gate that passes on base a6a06975 passes, and 11 and 14 fail with the same warnings as on base; git diff --stat C HEAD lists only this card."
dod_command: $PSNativeCommandUseErrorActionPreference = $false; $v = '.claude/skills/verify-main-screen/SKILL.md'; $card = 'specs/tasks/T0-MAIN-SCREEN-INSETS.md'; foreach ($f in @($v, $card)) { if (-not (Test-Path -LiteralPath $f)) { Write-Host "[DOD-FAIL] missing $f"; exit 1 } }; $t = Get-Content -Raw -LiteralPath $v -Encoding utf8; if ($t -match "Row 'visible'[^\r\n]*-Known") { Write-Host '[DOD-FAIL] row visible still has -Known'; exit 1 }; if ($t -match '(?i)known violation|Not Met \(known\)') { Write-Host '[DOD-FAIL] verify-main-screen still names a known violation'; exit 1 }; $cov = [regex]::Match($t, '(?ms)^\x23\x23 Coverage\s*$(.*?)^\x23\x23 ').Groups[1].Value; $mb = (& git merge-base HEAD origin/master); if ($LASTEXITCODE -ne 0 -or -not $mb) { Write-Host '[DOD-FAIL] no merge base with origin/master'; exit 1 }; $chg = @(& git diff --name-only $mb HEAD -- android/app/src/main | ForEach-Object { Split-Path -Leaf $_ }); foreach ($p in @(@('MainActivity.kt', 'AndroidManifest.xml') + $chg | Sort-Object -Unique)) { if (-not $cov.Contains($p)) { Write-Host "[DOD-FAIL] Coverage does not name $p"; exit 1 } }; $s = [regex]::Match($t, '(?m)^Sync commit: `?([0-9a-f]{40})`?\s*$').Groups[1].Value; if (-not $s) { Write-Host '[DOD-FAIL] no sync commit'; exit 1 }; & git merge-base --is-ancestor $s HEAD; if ($LASTEXITCODE -ne 0) { Write-Host "[DOD-FAIL] sync commit $s is not an ancestor of HEAD"; exit 1 }; & git diff --quiet $s HEAD -- android/app/src/main/AndroidManifest.xml android/app/src/main/res/values android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt android/app/src/main/kotlin/nz/myinspection/app/skeleton; if ($LASTEXITCODE -ne 0) { Write-Host "[DOD-FAIL] a covered file changed after sync commit $s"; exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon --no-watch-fs -q "-Pkotlin.compiler.execution.strategy=in-process" :app:assembleDebug; if ($LASTEXITCODE -ne 0) { Write-Host '[DOD-FAIL] assembleDebug'; exit 1 }; $c = Get-Content -Raw -LiteralPath $card -Encoding utf8; $i = $c.LastIndexOf('## Attempt evidence'); if ($i -lt 0) { Write-Host '[DOD-FAIL] no Attempt evidence section'; exit 1 }; $e = $c.Substring($i); $n = @([regex]::Matches($e, '\.(?:png|mp4) sha256 `([0-9a-f]{64})`') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique).Count; if ($n -lt 4) { Write-Host "[DOD-FAIL] $n distinct sha256 values in the evidence"; exit 1 }; $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 9 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[SELFTEST-ONLY-PASS]')) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 3000))); Write-Host "[DOD-FAIL] selftest -Only 9 rc=$rc"; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: verify-main-screen runs the row visible without -Known and names no known violation, its Coverage names MainActivity.kt, AndroidManifest.xml and every file under android/app/src/main that the card changes since its merge base with origin/master, its Sync commit is an ancestor of HEAD with no covered file changed since, assembleDebug passes, the evidence holds at least four distinct sha256 values of .png or .mp4 captures, and selftest gate 9 passes; prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] row visible still has -Known. It starts no emulator.
diagnosis:
  root_cause: "Hypothesis H1, not yet tested: the manifest declares no theme, so the activity takes the platform default theme with its action bar, and on API 35 with targetSdk 35 the enforced edge-to-edge display draws the Compose content from the top of the window, under that bar. Confirmed or refuted in the Diagnosis section before BUILD."
  same_class: "MainActivity is the only launcher screen in src/main; ScheduleScreen and RetentionSettingsScreen are not reachable from it. The debug activities are out of scope. The fix at the app root covers every screen that MainActivity hosts later."
review_gate: codex {verdict:pass}
budget: 160
hygiene: R4, one at a time in a scratch copy, each must make the DoD exit 1 on the arm it names - put -Known back on the row visible; replace the sync commit with 40 zeros; change MainActivity.kt after the sync commit; drop AndroidManifest.xml from Coverage. The status bar mutant of A3 is the mutant of the device check.
doc_sync: At R5 set status merged and update the TASK-BOARD row; close point 1 of issue 506. post-merge.ps1 r5 adds a CLAUDE.md entry and the owner has uncommitted CLAUDE.md edits, so as for T0-UI-VERIFY-PILOT, a separate docs PR changes only the card status and the board row until the owner commits or discards them.
---

# T0-MAIN-SCREEN-INSETS

## Deliverable
The main screen shows its title and its Address label below every bar on API 35, `verify-main-screen` checks that against the
real bar bounds with no known-violation mark, and a ui-verify before and after pair records the change.

## Why
Point 1 of issue 506, from `T0-UI-VERIFY-PILOT` (PR 505): on `emulator-5580` (AVD `Pixel_6`, API 35) the action bar covers the
title "Walking skeleton" and the label "Address" (row `visible`, sha256 `dcbfcfaa320b351e1e235ee96f1c0328cdee27765353fe18f9c1b1262288126c`).
The pilot left its before and after pair Unverified, because it changed no interface. This card changes one, so it runs the pair.

## Facts read at a6a06975 (2026-11-25)
- `android/app/build.gradle.kts`: compileSdk and targetSdk 35, minSdk 26.
- `AndroidManifest.xml` (main): no `android:theme` on `<application>` or on `.MainActivity`. `res/values/` holds only `strings.xml`.
  The merged-manifest gate in `build.gradle.kts` checks four `<application>` attributes by exact value and no theme, so a theme
  attribute does not touch it.
- `MainActivity.kt`: `ComponentActivity`, no `enableEdgeToEdge()`, `setContent { MaterialTheme { Surface(fillMaxSize) { SkeletonScreen() } } }`.
- `skeleton/SkeletonScreen.kt`: a `Column` with `verticalScroll` and `padding(16.dp)`, no inset handling; the first child is the title.
- `verify-main-screen` line 52: `Row 'visible' 'seen:Walking skeleton', 'seen:Address' -Known`. `seen` compares the node top with
  the bottom of `android:id/action_bar_container` and uses 0 when that node is absent. So a fix that removes the action bar
  passes `seen` even if the status bar then covers the title. R3 closes that gap before the pair runs.
- `verify-main-screen` line 12: `Sync commit: ffa037d2...`; line 70 calls the row a known violation; Coverage lists
  `MainActivity.kt`, `skeleton/SkeletonScreen.kt` and `skeleton/SkeletonState.kt` only.

## Diagnosis (R1, before BUILD)
Run on the base APK built at the card's base commit, with the driver of the skill, on `emulator-5580` only:
1. The theme the activity resolves and whether the window is edge-to-edge (for example `dumpsys activity` / `dumpsys window` for
   the package, read-only).
2. The bounds of the action bar, the status bar and both nodes in one dump.
3. One discriminating probe in a scratch copy outside the worktree, never committed: for example the base APK with a NoActionBar
   theme and nothing else. H1 predicts that the title then sits under the status bar, not under an action bar.
Record each observation with its command. If H1 is refuted, record that and stop for the owner before BUILD: a cause outside
`allow_paths` needs an amendment.

## Fix (chosen after the diagnosis)
If H1 holds, the expected fix is: a NoActionBar app theme in `res/values/themes.xml` set on `<application>`, and the system bar
insets applied once at the app root in `MainActivity.kt` (for example `enableEdgeToEdge()` with `safeDrawingPadding()` on the
root), so that content clears the status bar, the navigation bar and the keyboard on every API level. The Diagnosis section
records why this fix follows from the cause. `skeleton/` does not change, because `T2-CAPTURE-UI` deletes it.

## Run
1. Agree a Gradle slot with the other live sessions first (L389); the card clock counts the wait.
2. Record the pids of adb, emulator, qemu and java processes that already run. They are not this card's to stop. The driver
   refuses to start while an adb server runs: ask the owner, do not stop it.
3. RED: record the DoD exit 1 on base. Build the base APK and copy it to the capture directory. Commit the check change of R3
   alone; the row `visible` keeps `-Known` until the fix is in.
4. Diagnosis, then the fix, then the Sync commit line in a last skill commit (the sync commit is the fix commit or later).
5. `before`: the driver of the candidate on the base APK. `after`: the same driver on the candidate APK. Same AVD, same made-up
   data (`1 Example Street`, `Sample wall`). Captures go to `D:\wt\MyInspection-captures\T0-MAIN-SCREEN-INSETS\<run>`.
6. The driver installs with `install -r` and never uninstalls. Leave the app installed.

## Related cards
- `T2-CAPTURE-UI` (todo; `T1-SHARE-SCREEN-PRIVACY`, `T1-APP-BOUNDARY-ASSEMBLY` and `T2-MEDIA-ACCESS-BOUNDARY` among its open
  dependencies) changes `MainActivity.kt`, `skeleton/`, `res/` and this skill. It cannot start before this card merges, so the two
  do not run in parallel. It replaces the root composable, so it must keep the theme and the root inset handling of this card; this
  card adds one line saying so to the skill's Coverage text, not to `T2-CAPTURE-UI`.
- Point 2 of issue 506 is done (a6a06975, PR 508).

## Amendment 2026-11-27 (A6)
R3 decision 4 blocked on A6: the first `diag.ps1` run 3 of 2026-11-26 lost its process lines, and a repeat cannot show how that run's own processes ended. The owner ruled in the session to amend A6: that run is the one exception, covered by its repeat with a kept transcript and the process sweep after it. Every other run lists every member of its job, short-lived ones included, with a gone-probe. A6 also names those short-lived members explicitly.

## Acceptance (DoD = command + exit code + assertion; paired with the closed `acceptance:` list)
The command is `dod_command` in the front matter. Expected exit code: 0. Assertion: `dod_assert`.
Arm 1 is the row `visible` and the known-violation text, arm 2 Coverage and the sync commit, arm 3 assembleDebug,
arm 4 the sha256 count, arm 5 selftest gate 9.
