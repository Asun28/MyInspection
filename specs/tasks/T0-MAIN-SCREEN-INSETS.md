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
  - "A6 [R6] Attempt evidence names the device serial, API level and ro.kernel.qemu value, the capture directory, and for each started process its name, pid and the probe that shows it is gone; `git ls-files` lists no capture or APK (reviewer-verified against the diff)."
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

## Acceptance (DoD = command + exit code + assertion; paired with the closed `acceptance:` list)
The command is `dod_command` in the front matter. Expected exit code: 0. Assertion: `dod_assert`.
Arm 1 is the row `visible` and the known-violation text, arm 2 Coverage and the sync commit, arm 3 assembleDebug,
arm 4 the sha256 count, arm 5 selftest gate 9.

## Diagnosis result (2026-11-26, before the fix commit)
H1 holds. Run by `diag.ps1` (the driver's boot, job object and cleanup, plus `dumpsys` reads) on `emulator-5580`, API 35,
`ro.kernel.qemu` 1, with the base APK (built at `de41bd91`, sha256 `2f6d5cf5cefccdc8646571723c70598093a53e18be32b2f6042d6c87773c3d95`)
and one probe APK built in a detached scratch worktree at `de41bd91`, never committed: the base plus a `Theme.Probe`
(`android:Theme.Material.Light.NoActionBar`) on `<application>` and nothing else (sha256 `ce2472997dc6ab48e73f76e77f8bd85d4320488c5507c95b4b1f2e787a9d72f3`).

| Observation | Command | Base APK | Probe APK |
|---|---|---|---|
| theme of the app window | `dumpsys window windows`, line `compat={420dpi} theme=` of the MainActivity window | `theme=0x0`: no theme declared, the platform default with its action bar | `theme=0x7f0c010d`, the probe theme |
| system bars | `dumpsys window`, `InsetsSource ... type=statusBars` / `navigationBars` | status bar `[0,0][1080,128]`, navigation bar `[0,2337][1080,2400]`, both visible | the same |
| action bar | `action_bar_container` in the uiautomator dump | `[0,0][1080,275]` (status bar plus a 147 px bar) | absent |
| title and Address label | node bounds in the same dump | `[42,42][471,116]` and `[84,211][248,274]`: content starts at y 0, under both bars | the same bounds: under the status bar |

The probe discriminates: H1 predicts that without the action bar the content stays at the top of the window, under the status
bar, because API 35 draws the window edge-to-edge; a cause in the layout of `SkeletonScreen` or in the action bar alone predicts
the title below the status bar once the bar is gone. The probe's title stayed at y 42 under a status bar that ends at y 128.
No hypothesis was refuted. The fix therefore needs both parts: no action bar (it is drawn over the content and Compose insets do
not include it) and the system bar insets at the root. Nothing else was tried. With the fix (`1bb9d3b8`) the same dump puts the
title at `[42,170][471,244]`: 128 px of status bar plus the 16 dp padding.

## PR summary
### What
On API 35 the main screen's title and Address label now sit below the status bar instead of under the action bar. The `verify-main-screen` skill checks that against the real bar bounds and no longer carries a known violation.
### Why / Intent
Point 1 of issue 506. The manifest declared no theme, so the platform default drew its action bar, and API 35 draws the window edge-to-edge, so the content started under both bars.
### How / Behaviour
A NoActionBar app theme removes the bar. `enableEdgeToEdge()` and `safeDrawingPadding()` at the Compose root keep the content clear of the status bar, the navigation bar, a cutout and the keyboard on every API level. `skeleton/` does not change. The driver's `seen` check now reads the status and navigation bar bounds from `dumpsys window`.
### System impact
One theme resource, one manifest attribute and the app root change. The merged-manifest gate checks four other attributes and still passes. Scripts and CI do not change.
### Security
Made-up data on the headless emulator that each run booted. Every adb call names emulator-5580. The app was never uninstalled. Captures and APKs stay outside git.
### Tests and review
The DoD passes, and four mutants of it fail. A ui-verify before and after pair and a status-bar mutant of the device check ran on the emulator. Gates 11 and 14 fail as they do on base.
### Risk
`T2-CAPTURE-UI` replaces the root composable; the skill's Coverage says that a new root keeps the theme and the inset padding.
### Ownership
The repository owner.
### Docs
The skill text. A separate docs change sets the card status and the board row after the merge.

## Attempt evidence
| Item | Proof | Result |
|---|---|---|
| A1 | Diagnosis result above | H1 confirmed by two observations and one probe |
| A2 | DoD arm 3; the diff: theme, manifest attribute, root in `MainActivity.kt` | pass; no forbidden fix |
| A3 | runs `probe` and `probe-mutant` below | Not Met unmutated, Met with the status bar dropped |
| A4 | DoD arms 1 and 2; R4 mutants below | pass |
| A5 | runs `before` and `after` below, same `drive.ps1` sha256 `20c0d431dbeec694d7ce6feff4cfde371086d300d343b68e5a2bb96956276c67` | before exit 2 on `visible` alone; after exit 0 |
| A6 | device and process paragraphs below; `git ls-files` | no capture or APK in git; each process gone |
| A7 | DoD arm 5; selftest on C below | 15 gates pass; 11 and 14 fail with the same warnings as on base |

RED: `aidlc card red` on `de41bd91`: `[DOD-FAIL] row visible still has -Known`, exit 1.
Commits: `87c2a1e0` the check of R3 (with `-Known` still on), `1bb9d3b8` the fix, `9c5300ef` the skill without the mark, Sync commit `1bb9d3b8`.
Device: `emulator-5580` (AVD `Pixel_6`), API 35, `ro.kernel.qemu` 1, booted cold by each run. Captures: `D:\wt\MyInspection-captures\T0-MAIN-SCREEN-INSETS\<run>`; `git ls-files` lists no `.png`, `.mp4` or `.apk`. Candidate APK (built at `1bb9d3b8`) sha256 `ebd8d797444c147304314f8a438281ae63df97106bc82ed6d955c524099b901f`. Each run: the committed Drive block of `9c5300ef` saved as `<run>\drive.ps1` and run as `pwsh -NoProfile -File <run>\drive.ps1 -Out <run> -Apk <apk>` from the worktree; the mutant changes one line of that copy.

Run `before`, base APK, 2026-11-26 01:49:48 to 01:51:33 UTC, exit 2 (`visible` the only row not Met):

| Target | Expected | Actual | Evidence | Verdict |
|---|---|---|---|---|
| base | Walking skeleton, Address, Start inspection; no Export PDF | all true | base.png sha256 `80f5f337ed001814ce8b4db260fc22d33f3f4a85a26ca3fefade6a43c97b03cd` | Met |
| visible | both nodes clear of every bar | both false: the action bar covers them | `visible.png`, same sha256 as `base.png` | Not Met, as expected before the fix |
| step-1 | address typed, `Started `, Item, Take photo; Export PDF disabled | all true | step-1.png sha256 `23c86e5e1182fd145ab6f7eeeb72eb0a4f1bd71d2a239551f17737a819b1c7df` | Met |
| negative | `on:Export PDF` exits 1 | exit 1 | negative.png sha256 `51b01c9ca0e4329b4bbfd127cc6f0c9a7ee67723634416c468e747ecdca36a81` | Met |
| step-2 | Sample wall; Export PDF disabled | both true | step-2.png sha256 `685b1cb6b1f0d93d5bdbea28d315caaa6b889b9f310beaf8cbb41910343ab3f3` | Met |
| step-3 | Sample wall; Export PDF enabled | both true | step-3.png sha256 `0a4f9edaaf72a98decceff49c9b2cec6ae57da5357ea2ba5807182040b3106ce` | Met |
| video | finished, lasts to the step-3 tap (37.9 s) | 45.9 s, recorder gone at the end | video.mp4 sha256 `f7c99e62a07b7935055b68d3c9791b1eb1f8ffb853571c910723dc9ff6d71972` | Met |

Run `after`, candidate APK, 02:01:33 to 02:03:38 UTC, exit 0:

| Target | Expected | Actual | Evidence | Verdict |
|---|---|---|---|---|
| base | as before | all true | base.png sha256 `80178cc760c7d09533c9e389ce74cf4cf0239695aed3174a397693271956c9fa` | Met |
| visible | both nodes clear of every bar | both true; bars `0,0,1080,128` and `0,2337,1080,2400`, no action bar | `visible.png`, same sha256 as `base.png` | Met |
| step-1 | as before | all true | step-1.png sha256 `e59f651ae7835545120511221f53e6c8553b6437d34319e5cb9cd42e5fb4f87b` | Met |
| negative | exit 1 | exit 1 | `negative.png`, same sha256 as `step-1.png` | Met |
| step-2 | as before | both true | step-2.png sha256 `f89417450cfcd5e2e65d4f364fda3f0b4936a388797870fdd88ce3bfc01d68e0` | Met |
| step-3 | as before | both true | step-3.png sha256 `14318e1c9734cd5aa9d7a33d69ee79cff40f3c540eff12d794642a80bada03b5` | Met |
| video | finished, lasts to the step-3 tap (41 s) | 48.7 s, recorder gone at the end | video.mp4 sha256 `5d33f1cdd6a6ead2e9045cd312ebb578c752b9ef73b175603ff9444fb6435226` | Met |

Run `probe`, probe APK, 01:51:42 to 01:53:36 UTC, exit 2: `visible` Not Met with `seen:Walking skeleton=False` (the status bar covers it), `seen:Address=True`; visible.png sha256 `98a04103956f07c43c8b9f5c906a64c1862f67bd9e12de55ae9c35917549e6b9`; every other row Met. The old check, which compared only with the action bar, would have passed this screen.
Run `probe-mutant`, probe APK, 01:59:37 to 02:01:32 UTC, exit 0: the copy drops every bar whose top is 0 (the status bar) before the check; bars `0,2337,1080,2400`; `visible` Met with both true; visible.png sha256 `8443e000955f2aa55992c27f0ffb8c2ffef0681c13c8a4ddd754895d0a36e387`. So the status bar bound is what catches the probe.
Pictures, looked at: `before` shows the action bar over the title; `probe` shows the title under the status bar icons; `after` shows the title and the Address field below the status bar, then the typed sample text with the keyboard and its word suggestions (Street, wall). No real data.
Processes: each run printed `ended` with name and pid for the seven host processes of its job, then `job: 0 left`, and `gone` for its device recorder: before adb 19284, emulator 34368, conhost 65668 and 52748, qemu-system-x86_64-headless 63324, crashpad_handler 11128, netsimd 31840, screenrecord 3280; probe 60156, 32240, 16848, 17696, 58136, 57728, 28340, screenrecord 3232; probe-mutant 35808, 54608, 17592, 62268, 67344, 64424, 59336, screenrecord 3308; after 50472, 39964, 39448, 62584, 17712, 3448, 49292, screenrecord 3333 (same name order as before). After the runs no adb, emulator, qemu or netsimd process ran and ports 5037, 5580 and 5581 were free. A crashpad_handler (43148) ran before and stays. The app stays installed (the candidate APK); no run uninstalled it. Each Gradle build left no java process.
Failed tries: (1) `diag.ps1` first run: both APK paths reached one parameter, the first install failed, cleanup ended its seven processes, `job: 0 left`. (2) The first `probe-mutant` copy kept only `navigationBars` in the bar pattern and stopped with `no system bar bounds in dumpsys window` (exit 1, `job: 0 left`); replaced by the copy above. (3) The first `after` run: `base` and `visible` Met, then the screen stayed black in both pictures and the app left the foreground before the address was typed (`not typed: 1 Example Street`, exit 1, `job: 0 left`, ended adb 51472, emulator 52160, conhost 58436 and 64804, qemu 16680, crashpad_handler 55020, netsimd 42760, screenrecord 3340 gone). A `diag.ps1` run of the same APK right after it found the app resumed, the picture rendered, the title at `[42,170][471,244]` and an empty crash log, and the repeat run above passed, so it is recorded as an emulator display failure, cause not established. The third `diag.ps1` run printed no process lines to a file; it exited 0, which the script allows only after `job: 0 left`, and the process check after it found none of its processes.

DoD on C = `7617c45651be66986a03532f7395ce7b4453a2ce`, run through `Get-ScaffoldDodPayload` (the ship's payload): `[DOD-PASS]`, exit 0, 21 s, no java left. The first run on `a94c5d69` printed `[DOD-FAIL] 0 distinct sha256 values in the evidence`, because the capture names carried code marks that arm 4 does not read; `7617c456` removes the marks and changes no other text.
R4, one at a time in a fresh clone of C, each through the same payload; control (unmutated clone) `[DOD-PASS]`, exit 0: M1 `-Known` put back on the row visible: exit 1, `row visible still has -Known`. M2 sync commit of 40 zeros: exit 1, `not an ancestor of HEAD`. M3 a line added to `MainActivity.kt` and committed after the sync commit: exit 1, `a covered file changed after sync commit`. M4 `AndroidManifest.xml` dropped from Coverage: exit 1, `Coverage does not name AndroidManifest.xml`.
Selftest (A7) on C, empty `git status` before and after: `selftest.ps1 -TaskId T0-MAIN-SCREEN-INSETS` read the card at base `de41bd91`, tier 1, and ran the full suite because the three android paths match no GATE-MAP row (`gates=full`): `[GATE-RESULT-SUMMARY] failed=11,14 ran=17 wall=1826.4s`, exit 1; 1 to 10, 12, 13 and 15 to 17 PASS, 11 and 14 FAIL. On base `de41bd91` (a clean detached worktree, which differs from `a6a06975` only by this card's file), `-Only 11,14`: `failed=11,14 ran=2 wall=200.1s`, exit 1. The sorted distinct `WARNING` lines (upper case, the gate warnings; the lint `[Warning]` lines come from gates that ran only on C) of both logs are equal, 11 each; the one behind the failures is the dangling link `CLAUDE.md` to `scripts/_validation.ps1`, and gate 14 fails through its nested run of gate 11. `git diff --stat C HEAD` lists only this card.