---
id: T0-UI-VERIFY-PILOT
title: Pilot the optional ui-verify skill once on the app's main screen, on an Android emulator, with a verify-main-screen skill
depends_on: []
parallelizable_with: []
status: merged
branch: T0-UI-VERIFY-PILOT
worktree: D:\wt\MyInspection\T0-UI-VERIFY-PILOT
allow_paths:
  - .claude/skills/ui-verify/SKILL.md
  - .claude/skills/verify-main-screen/SKILL.md
  - docs/DELIVERY-CHAINS.md
  - specs/tasks/T0-UI-VERIFY-PILOT.md
requirements:
  - R1. The file .claude/skills/ui-verify/SKILL.md shall be byte-identical to the template at AIDLC merge 0ef9ae77 (git blob ed777e5352a5ede6c8dfbfd4bcff27f759b30632).
  - R2. The skill verify-main-screen shall hold, with no placeholder left, its coverage with a sync commit, launch, drive, flows with their end states, the flows it does not drive, and cleanup by pid, and shall use only Gradle, adb and the device's own screencap, screenrecord and uiautomator.
  - R3. The evidence shall hold one row per capture with the command, the expected state, the observed state, the sha256 and the verdict, one negative check that fails as it must, and an Unverified row with a reason for each flow or capture that was not run.
  - R4. The evidence shall answer whether the skill text sufficed on Android, what was missing, how long each part took, and whether screenshot should join CANDIDATE_BOUND_KINDS (a recommendation only).
  - R5. The run shall use made-up data on an emulator only, shall keep every capture outside every worktree and out of git, and shall end with every process it started stopped by pid and named.
  - R6. The trigger-layer row of docs/DELIVERY-CHAINS.md shall name ui-verify and verify-main-screen, because selftest gate 9j fails on a skill directory that the row does not name.
forbid:
  - A change under android/, scripts/, .github/, .claude/agents/, .claude/hooks/ or CLAUDE.md, or any change to the AIDLC repository
  - Editing the ui-verify text to fit Android; a defect in it becomes a proposed AIDLC issue in the evidence
  - aidlc init, a new dependency, a new Gradle or adb plugin, an instrumented or Robolectric test
  - Installing on, driving or capturing a physical phone; real property, tenant or account data
  - Committing a capture, or writing one inside any worktree of this repository
non_goals:
  - "A before and after pair: this card changes no interface, so the pair stays Unverified and a later card that changes the screen owns it"
  - "The photo flow (system camera) and the PDF export flow (system file picker), both outside the app's own window"
  - "ScheduleScreen, RetentionSettingsScreen and the debug PlatformSpikeActivity"
  - "Adding screenshot to CANDIDATE_BOUND_KINDS, a gate, or a CI job that boots an emulator"
  - "The skill indexes in CLAUDE.md, which are pointers and not the gate 9j source"
acceptance:
  - "A1 [R1] git hash-object of .claude/skills/ui-verify/SKILL.md is ed777e5352a5ede6c8dfbfd4bcff27f759b30632. [dod arm 1]"
  - "A2 [R2] .claude/skills/verify-main-screen/SKILL.md has the sections Coverage, Launch, Drive, Flows, Not covered and Cleanup, and a Sync commit line with a 40-digit sha that is an ancestor of HEAD (dod arm 2); its text reads as R2 says, names the covered files, starts the explicit component nz.myinspection.app/.MainActivity, passes the emulator serial to every adb call, refuses a device whose ro.kernel.qemu is not 1, and finds controls by text, not by fixed coordinates (reviewer-verified against the diff)."
  - "A3 [R3] The Attempt evidence table has a Met row for the base capture, for each numbered step and for the negative check, one for the screen recording or an Unverified row with the reason, each capture that exists with its sha256 (dod arm 3 counts at least four distinct sha256 values, because the negative check may capture the same pixels as step 1); the end state is asserted from a uiautomator dump, not from a picture (reviewer-verified against the diff)."
  - "A4 [R3] The photo flow, the PDF export flow, the before and after pair and evidence retain have rows that say Unverified with the reason, or Met with a command and an observation; none of them is a silent pass (reviewer-verified against the diff)."
  - "A5 [R4] Attempt evidence answers the four questions of R4 from the run, each with the step or time that supports it, and lists each ui-verify defect as a proposed AIDLC issue (reviewer-verified against the diff)."
  - "A6 [R5] Attempt evidence names the device serial, API level and ro.kernel.qemu value, the APK sha256, the capture directory, and for each started process its name, pid and the probe that shows it is gone; git ls-files lists no capture (reviewer-verified against the diff)."
  - "A7 [R6] selftest -Only 9 passes (dod arm 4). On a commit C whose summary lines are in the evidence, of the GATE-MAP gates of the changed paths, 3, 4, 5, 8, 9, 15 and 16 pass, and 11 and 14 fail with the same warnings as on base ffa037d2, because committed CLAUDE.md names scripts/_validation.ps1, which PR 490 deleted; git diff --stat C HEAD lists only this card."
dod_command: $PSNativeCommandUseErrorActionPreference = $false; $u = '.claude/skills/ui-verify/SKILL.md'; $v = '.claude/skills/verify-main-screen/SKILL.md'; foreach ($f in @($u, $v)) { if (-not (Test-Path -LiteralPath $f)) { Write-Host "[DOD-FAIL] missing $f"; exit 1 } }; $b = (& git hash-object $u); if ($b -cne 'ed777e5352a5ede6c8dfbfd4bcff27f759b30632') { Write-Host "[DOD-FAIL] ui-verify blob $b"; exit 1 }; $t = Get-Content -Raw -LiteralPath $v -Encoding utf8; foreach ($h in @('Coverage', 'Launch', 'Drive', 'Flows', 'Not covered', 'Cleanup')) { if ($t -notmatch ('(?m)^\x23\x23 ' + $h + '\s*$')) { Write-Host "[DOD-FAIL] verify-main-screen has no section $h"; exit 1 } }; $s = [regex]::Match($t, '(?m)^Sync commit: `?([0-9a-f]{40})`?\s*$').Groups[1].Value; if (-not $s) { Write-Host '[DOD-FAIL] no sync commit'; exit 1 }; & git merge-base --is-ancestor $s HEAD; if ($LASTEXITCODE -ne 0) { Write-Host "[DOD-FAIL] sync commit $s is not an ancestor of HEAD"; exit 1 }; $c = Get-Content -Raw -LiteralPath 'specs/tasks/T0-UI-VERIFY-PILOT.md' -Encoding utf8; $e = $c.Substring($c.LastIndexOf('Attempt evidence')); $n = @([regex]::Matches($e, 'sha256 `([0-9a-f]{64})`') | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique).Count; if ($n -lt 4) { Write-Host "[DOD-FAIL] $n distinct sha256 values in the evidence"; exit 1 }; $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 9 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[SELFTEST-ONLY-PASS]')) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 3000))); Write-Host "[DOD-FAIL] selftest -Only 9 rc=$rc"; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The ui-verify copy has the pinned blob id, verify-main-screen has its six sections and an ancestor sync commit, the evidence holds at least four distinct sha256 values, and selftest gate 9 passes; prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing .claude/skills/ui-verify/SKILL.md. It starts no emulator and no Gradle task.
review_gate: codex {verdict:pass}
budget: 190
hygiene: R4, one at a time in a scratch copy, each must make the DoD exit 1 on the arm it names - change one byte of the ui-verify copy; drop the Cleanup section; replace the sync commit with 40 zeros; drop ui-verify from the DELIVERY-CHAINS row. The negative check of the run is the mutant of the device assertion.
doc_sync: At R5 set status merged and update the TASK-BOARD row. post-merge.ps1 r5 always adds a CLAUDE.md entry and the owner has uncommitted CLAUDE.md edits, so run r5 once those edits are committed or discarded by the owner; until then a separate docs PR changes only the card status and the board row.
---

# T0-UI-VERIFY-PILOT

## Deliverable
The unchanged `ui-verify` skill, one `verify-main-screen` skill for the app's main screen, and the record of one run.

## Why
`ui-verify` merged in the AIDLC repository on 2026-11-25 (PR 478, merge `0ef9ae77`). Its trial covered one web page. It did not
run video, numbered captures, a device or `aidlc evidence retain`. This card tries these on Android.

## Facts read at 8b8ed921 (2026-11-25)
- **Screen**: `MainActivity.kt:35` calls `SkeletonScreen()`, declared at `skeleton/SkeletonScreen.kt:45`. It is the only screen
  call in `android/app/src/main`.
- **Two launchers**: the debug manifest gives `PlatformSpikeActivity` a LAUNCHER filter too, so a launch by package
  (`monkey -p`) does not select one activity. Start `nz.myinspection.app/.MainActivity`.
- **No test tags**: `SkeletonScreen` sets no test tag and no content description, so the driver finds nodes by text. The Export button's enabled state may
  sit on the parent of its text node.
- **No FLAG_SECURE**: the app sets none, so a screencap should not be black.
- **Device**: AVD `Pixel_6`, 1080x2400, 420 dpi, `android-35` `google_apis` `x86_64`, `fastboot.forceFastBoot=yes`. L352: a
  quick boot hung for 16 minutes; a cold boot with `-no-snapshot` took about 40 s.
- **Earlier device runs**: `docs/storage-android-probe.md` and `docs/local-secret-box-probe.md` used an API 35 emulator and a
  personal phone. A phone may be attached, so the serial check in A2 is required.
- **Gradle**: `android/gradle.properties` sets `org.gradle.daemon=false`. A Kotlin compile daemon can still remain.
- **Vendoring**: `ui-verify` is the owner's own text; `T0-UI-VERIFY-SKILL` forbids adapted passages, so no NOTICE file is needed.

## Flow
Made-up data: address `1 Example Street`, item `Sample wall`.

| Step | Action | End state in the uiautomator dump |
|---|---|---|
| base | start the activity on a fresh process | `Walking skeleton`, `Address` and `Start inspection` present; `Export PDF` absent |
| 1 | type the address, press `Start inspection` | a text that starts with `Started `; `Item` and `Take photo` present; Export PDF disabled |
| negative | assert that Export PDF is enabled in the state of step 1 | the assertion exits 1 |
| 2 | type the item | `Sample wall` present; Export PDF still disabled |
| 3 | press the first condition button (U+597D, the label of `GOOD`) | Export PDF enabled |

The flow has several steps, so one `screenrecord` covers it and each step has a numbered capture. If the recorder fails, its row is Unverified and the numbered
captures stand. The base capture is a baseline, not the first half of a pair.

## Run
1. Agree a Gradle slot with the other live sessions first (L389); the card clock counts the wait.
2. Record the pids of adb, emulator, qemu and java processes that already run. They are not this card's to stop.
3. Write both skills and the index row, then commit. One fresh agent follows `verify-main-screen` from its text alone; give it
   the path and no device step. Record each step it had to add.
4. If the text is wrong, correct it, commit, and repeat the whole run. Keep the failed run's rows.
5. Captures go to `D:\wt\MyInspection-captures\T0-UI-VERIFY-PILOT`, which is in no worktree. The run
   creates it, and the files stay after cleanup.
6. Uninstall the app from the emulator only if this run installed it; otherwise leave it.
7. `aidlc evidence retain` needs the recorded success, so its row is Unverified in the candidate and its result goes to CLOSE.

## Questions the run answers
- Did the text of `ui-verify` suffice on Android, and what was missing? Known before the run: the capture names `before`, `after`,
  `step-<n>` and `video` have none for a baseline without a pair or for a negative check.
- How long did writing the skill, the build, the boot, the drive and the cleanup take?
- Should `screenshot` join `CANDIDATE_BOUND_KINDS` (`src/audit/manifest.ts:50` in AIDLC)? Base it on whether the capture's
  binding to its candidate was clear without it.

## Related cards
- `T2-CAPTURE-UI` (todo) deletes `skeleton/` and changes `MainActivity.kt`, so it changes the covered files. Its `allow_paths`
  hold no skill file, so it cannot update `verify-main-screen` without an amendment. Decision: keep the skill after the pilot. At
  CLOSE this card files one follow-up issue that asks the owner to add the skill path to `T2-CAPTURE-UI` before it starts, so
  that card updates the skill for its new screen. This card does not edit `T2-CAPTURE-UI`.
- `T4-SCHEDULE-UI-SYMBOL-CHROME` shares no path with this card. Its screen is not reachable from `MainActivity`.

## Acceptance (DoD = command + exit code + assertion; paired with the closed `acceptance:` list)
The command is `dod_command` in the front matter. Expected exit code: 0. Assertion: `dod_assert`.
Arm 1 is the blob id, arm 2 the sections and sync commit, arm 3 the sha256 count, arm 4 selftest gate 9.

## Amendment 2026-11-25 (A7)
A7 asked for gates 11 and 14 to pass. Both already fail on master `ffa037d2` (`CLAUDE.md:78`, gate 14 through its nested run of gate 11), and this card may not change CLAUDE.md. The owner chose in the session to amend A7 to the base comparison.

## PR summary
### What
This change adds the unchanged `ui-verify` skill and one `verify-main-screen` skill. It also records one run on an Android emulator.
### Why / Intent
The earlier trial of `ui-verify` covered one web page. This card tests the skill on a device.
### How / Behaviour
The new skill starts the app, types made-up data and asserts each state from a screen dump. It saves pictures and one video outside git.
### System impact
The app, the scripts and the CI do not change. One index row names the two skills.
### Security
The run used made-up data. The driver acts only on the headless emulator that the same run booted. It stops only the processes of its own job and never uninstalls the app.
### Tests and review
The DoD passes, and each of the four mutants makes it fail. Gates 11 and 14 fail as they do on the base commit.
### Risk
The skill finds controls by text, so a label change stops the driver with a failure.
### Ownership
The repository owner keeps both skills. The owner also decides on the five proposed AIDLC issues.
### Docs
`docs/DELIVERY-CHAINS.md` names the two skills. A separate docs change sets the card status and the board row after the merge.

## Attempt evidence
| Item | Proof | Result |
|---|---|---|
| A1 | DoD arm 1; mutant: one byte of `ui-verify` changed | pass; mutant exit 1, `ui-verify blob` |
| A2 | DoD arm 2; mutants: no Cleanup section, sync commit of 40 zeros | pass; mutants exit 1, `no section Cleanup` and `not an ancestor` |
| A3 | DoD arm 3 and the run table below | Met rows with sha256, negative check exit 1 |
| A4 | the four Unverified rows below | each with its reason |
| A5 | the four answers below | answered, proposals (a) to (e) |
| A6 | the run and process paragraphs below; `git ls-files` | no capture in git, each process gone |
| A7 | DoD arm 4; mutant: `ui-verify` dropped from the index row; selftest on C below | pass; mutant exit 1, `9j [SKILL-CONTRACT]` |

RED: `aidlc card red` on master `ffa037d2`, `[DOD-FAIL] missing .claude/skills/ui-verify/SKILL.md`, exit 1. The mutants ran one at a time in a scratch clone at C; the control there printed `[DOD-PASS]`, exit 0.
Run on C = `2e3fb055ff1d2ca7c82253b5770d4c68c7e3b5d8`, 2026-11-25 09:59 to 10:03 UTC, by a fresh verifier agent that had the skill path, the capture directory, limits and the report list, and no device step. HEAD and an empty `git status` before and after. Device `emulator-5580` (AVD `Pixel_6`), API 35, `ro.kernel.qemu` 1. Built and installed `app-debug.apk` sha256 `2f6d5cf5cefccdc8646571723c70598093a53e18be32b2f6042d6c87773c3d95`. Captures: `D:\wt\MyInspection-captures\T0-UI-VERIFY-PILOT\run6`; `git ls-files` lists none. The coordinator hashed the files again (equal) and found `drive.ps1` equal to the committed block. Every row came from `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Apk android\app\build\outputs\apk\debug\app-debug.apk`, exit 0, `settle: 0 system dialogs answered with Wait`; step-1 is the state after the address and Start, step-2 after the item, step-3 after the condition.

| Target | Source | Expected | Actual | Evidence | Verdict |
|---|---|---|---|---|---|
| base capture | driver row `base` | Walking skeleton, Address and Start inspection in the dump, no Export PDF | all four true | `base.png` sha256 `4558cf35928f48a7aeadd85237e1ee1e0901d3791007e35ba3ef55e452d57f2f` | Met |
| title and Address label visible | driver row `visible` | both below the action bar | both false: an app defect, the action bar covers them on API 35 | `visible.png` sha256 `dcbfcfaa320b351e1e235ee96f1c0328cdee27765353fe18f9c1b1262288126c` (status bar icons changed since `base.png`) | Not Met, known violation; owner: the repository owner, follow-up issue filed at CLOSE |
| step 1 | driver row `step-1` | the address, a text starting `Started `, Item, Take photo; Export PDF disabled | all five true; the typed address is in the dump and hidden in the picture | `step-1.png` sha256 `e041218c8a2bf07985e0b8f445bd5fa43121ffb8870b952f81b0f0a74a071ff0` | Met |
| negative check | `drive.ps1 -Assert on:Export PDF` in the state of step 1 | exit 1 | exit 1, `ASSERT FALSE on:Export PDF` | `negative.png`, same sha256 as `step-1.png` | Met |
| step 2 | driver row `step-2` | Sample wall; Export PDF disabled | both true | `step-2.png` sha256 `46a5731741c652dac3ab42129bf0deb55661960ff6b441aad0d025d5c7f3955e` | Met |
| step 3, the end state | driver row `step-3` | Sample wall; Export PDF enabled | both true | `step-3.png` sha256 `a558fd70dc36ed65b0ceb85b5a6d0358cfc5515acafdda87beac17fefb8243a9` | Met |
| screen recording | driver row `video` | a finished file that lasts to the step-3 tap, at 57 s | 65.5 s by its header, recorder alive at step-3 and gone at the end; 249 frames, the coordinator viewed 12 spread over it: the flow up to the enabled Export PDF, no real data | `video.mp4` sha256 `f3488c7a5206b08f393764ff14c0f6198522bc1dcd8676e0c3185a754d006974` | Met |
| before and after pair | not run | - | this card changes no interface | none | Unverified |
| photo flow | not run | - | the system camera is outside the app's window | none | Unverified |
| PDF export flow | not run | - | the system file picker is outside the app's window | none | Unverified |
| `aidlc evidence retain` | not run before the candidate | - | it needs the recorded success; the result goes to CLOSE | none | Unverified |

The agent opened the six pictures: typed sample text, the keyboard's word suggestions and the clock, no real data.
Processes: the driver (one process in a Windows job object) printed `ended` for adb server 34164, emulator 7852, conhost 13996 and 64256, qemu-system-x86_64-headless 57544, crashpad_handler 64724 and netsimd 41616, then `job: 0 left`, read from the job; and `gone` for its recorder, device screenrecord 2857, by `pgrep` on the tag in its file name. The agent and the coordinator each looked for these seven host pids in `Win32_Process` after the run and found none; ports 5037, 5580 and 5581 were free. Every other command is synchronous. The build left no java. A crashpad_handler (43148) ran before and stays.
Failed tries: (1) the run on `64473edf` (driver exit 1): a "System UI isn't responding" dialog covered the app on a busy host, and `seen:` crashed on a missing node; two tries before it failed on that agent's own extraction script, and it deleted their files and one stray script in the checkout. (2) R3 blocked `0c246d7e` (F1 to F7), `8bd0d0ca` (F2, F5, F6, F8, F9) and `ce9aacba` (F9, F10); the runs on `8d6a0673`, `bf468f7c` and `120ab0e6` passed with the faults of those findings. (3) The run on `ce8e5214` passed, and its cleanup uninstalled the app from the AVD, because the boot step read it as absent where earlier boots had found it; the cause is not established. The uninstall is removed since `120ab0e6`, and that run installed the app again, its earlier data on that AVD is lost. (4) Two drafts of the wait failed (`/sdcard` not ready; a Pixel Launcher dialog). (5) Two builds failed: Gradle's file watcher hung in `listFileSystems`, then its cache lock stayed held; hence `--no-watch-fs`. (6) Earlier drafts: Start pressed before adb had typed; dropped characters; keyboard settings that did not help; a boot while the old qemu held the AVD; a windowed emulator that showed a fragment of the host clipboard (captures deleted, AVD keyboard changes reverted). All processes were stopped by pid.
Failure paths of the R3 repairs, each run on the driver of `53c68fe1` without its message edit (C differs from it only in what it prints: process names, API level) or a variant of one or two replaced strings; after each one the process table held no process of the run. Exit 1 before any start: emulator port taken; adb server port taken. Exit 1, `job: 0 left`: emulator exits; launcher exits after it started a child (the child ended); no boot in time; a window; no headless qemu in the job; bad APK; installed bytes that differ; planned throw mid-flow (no recorder left); screen that never settles. F10: the adb server killed mid-flow; the server that adb started in its place was in the job and ended, exit 1. F9: a child whose parent had ended, then qemu killed mid-flow; the child ended, exit 1. The driver itself killed mid-flow with six processes alive: Windows ended all. The job cannot be read: `job: the job cannot be read`, and the job ended with the driver. A process that does not end (the kill left out): `job: 7 left`, then ended with the driver. Alone, with the job code of the driver: a child that asks to break away is refused (error 5), a detached child stays in the job and ended. Recorder that ends early, does not start, ignores the signal (killed, file not finished), or whose probe fails (`unknown`): every capture row printed, video Unverified, exit 2, `job: 0 left`. Not run: a job that Windows does not create, which throws before any start.
Answers (R4):
1. Did the text suffice? `ui-verify`: yes for the shape of the skill and the rows, no for the device (next answer). `verify-main-screen`: the agent of the 10:00 UTC run added no device step, only a wrapper that sets `$out` and the working directory; earlier runs showed the missing wait, the unclear extraction and the uninstall, now repaired in the text.
2. What was missing in `ui-verify`, as proposed AIDLC issues: (a) made-up data in the app does not keep host data (clipboard, keyboard suggestions, notifications) out of a capture; (b) item 2 says that a picture alone is not a pass, and not that an assertion alone can pass on an element that is covered; (c) no capture name for a baseline without a pair, a negative check or an extra row; (d) cleanup by pid does not ask for an identity (pid and start time), a wait until the process table drops it, a tool that starts a server of its own (on Windows a job object holds all of these, pids do not), the recorder on the device, or that cleanup must not delete what was there before; (e) retain takes the digest of the recorded success, and the captures of a run whose evidence is committed come from an earlier commit.
3. How long: writing the skill with its probes about 40 min and the three R3 repairs about 150 min; on C, with other work on the host, build 61 s, boot 61 s (the driver prints `booted in`), drive about 118 s, cleanup about 2 s, the whole run about 250 s. The driver took 181.2 s (10:00:28.3 to 10:03:29.6 UTC); it prints no time for the last two, so cleanup is from the time of its last capture file, `video.mp4` (10:03:27.8), to its end, and drive is the rest, with the start of the driver and the install in it; on a quieter host the driver took 123 s with a boot of 36 s.
4. Should `screenshot` join `CANDIDATE_BOUND_KINDS`? Recommendation: no, not now. `verifyAudit` blocks an entry whose digest is not the merged one, and a commit that changes only this card changes the digest, so each evidence commit or repair would make valid captures stale. Two driver runs of one APK gave different sha256 values (clock and time text), so the bytes do not show the candidate either; the APK sha256 does. Revisit it with a rule that compares the covered files.

Selftest (A7) on C, empty `git status` before and after: `[GATE-RESULT-SUMMARY] failed=11,14 ran=9 wall=556.1s`, exit 1: 3, 4, 5, 8, 9, 15 and 16 PASS, 11 and 14 FAIL. On base `ffa037d2`, `-Only 11,14`: `failed=11,14 ran=2 wall=231.1s`, exit 1. The sorted distinct WARNING lines of both logs are equal (11 each); the two behind the failures are the dangling link `CLAUDE.md` to `scripts/_validation.ps1` (gate 11) and 14q, whose nested run fails on gate 11 alone. `git diff --stat C HEAD` lists only this card.

## R5 2026-11-25

Merged via PR #505 (squash `b62c9762`, reviewed head `904550ad`, CI run 45690404143, Codex R3 and the ship's Codex review pass with no finding). R3 decisions 1 to 3 (F1 to F10) and one ship review (F11) blocked; each finding was repaired. Owner rulings in the session: A7 amended (PR #504), three deadline extensions, one redesign (a Windows job object owns every process of the driver), one continuation with two decisions after F11. Follow-up: #506. `post-merge.ps1 r5` was not run, because it adds a CLAUDE.md entry and the owner has uncommitted edits there.
