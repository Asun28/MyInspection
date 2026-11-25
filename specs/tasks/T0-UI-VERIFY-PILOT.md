---
id: T0-UI-VERIFY-PILOT
title: Pilot the optional ui-verify skill once on the app's main screen, on an Android emulator, with a verify-main-screen skill
depends_on: []
parallelizable_with: []
status: todo
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

## Attempt evidence
RED: `aidlc card red` on master `ffa037d2`, `[DOD-FAIL] missing .claude/skills/ui-verify/SKILL.md`, exit 1.

Run on `8d6a06739fd571702a96c013c4f22339713a97a6`, 2026-11-24 19:10 to 19:13 UTC, by a fresh verifier agent that had the skill path, the capture directory and no device step. HEAD and an empty `git status` before and after. Device `emulator-5580` (AVD `Pixel_6`), API 35, `ro.kernel.qemu` 1. The app was installed before, so it stays. `app-debug.apk` sha256 `2f6d5cf5cefccdc8646571723c70598093a53e18be32b2f6042d6c87773c3d95`. Captures: `D:\wt\MyInspection-captures\T0-UI-VERIFY-PILOT\run1`; `git ls-files` lists none. The coordinator hashed the files again (equal) and found `drive.ps1` equal to the committed block. Every row below came from `pwsh -NoProfile -File $out\drive.ps1 -Out $out`, exit 0; step-1 is the state after the address and Start, step-2 after the item, step-3 after the condition.

| Target | Source | Expected | Actual | Evidence | Verdict |
|---|---|---|---|---|---|
| base capture | driver row `base` | Walking skeleton, Address and Start inspection in the dump, no Export PDF | all four true | `base.png` sha256 `f09b72f9e3d71a3e2dfd97931262727443e8f31a8f6d78885cd663a8c241d66e` | Met |
| title and Address label visible | driver row `visible` | both below the action bar | both false: an app defect, the action bar covers them on API 35 | `visible.png`, same sha256 as `base.png` | Not Met, known violation; owner: the repository owner, follow-up issue filed at CLOSE |
| step 1 | driver row `step-1` | the address, a text starting `Started `, Item, Take photo; Export PDF disabled | all five true; the typed address is in the dump and hidden in the picture | `step-1.png` sha256 `827b8b5575f87d10cff321f52b7d2c424c60f359fb420de481074b1f7289def6` | Met |
| negative check | `drive.ps1 -Assert on:Export PDF` in the state of step 1 | exit 1 | exit 1, `ASSERT FALSE on:Export PDF` | `negative.png`, same sha256 as `step-1.png` | Met |
| step 2 | driver row `step-2` | Sample wall; Export PDF disabled | both true | `step-2.png` sha256 `3401a43e53e55db8178f78dcfa559f6e6d4bdf25f72e959adde15bc74107c3ed` | Met |
| step 3, the end state | driver row `step-3` | Sample wall; Export PDF enabled | both true | `step-3.png` sha256 `c335b9fb8af7a49f2671bdb06cb060820124ec5e941944ae68898c230b6960bf` | Met |
| screen recording | driver row `video` | recording from base to step 3 | 891023 bytes, recorder alive at step 3; 240 frames, 44.8 s; the coordinator viewed 12 frames spread over it, they show the flow up to the enabled Export PDF and no real data | `video.mp4` sha256 `64e999fb344f77177b59ef1cb4820bd495fc1f619c7c68202f4eb0c9cae4f05f` | Met |
| before and after pair | not run | - | this card changes no interface | none | Unverified |
| photo flow | not run | - | the system camera is outside the app's window | none | Unverified |
| PDF export flow | not run | - | the system file picker is outside the app's window | none | Unverified |
| `aidlc evidence retain` | not run before the candidate | - | it needs the recorded success; the result goes to CLOSE | none | Unverified |

The agent opened the five pictures: they show the typed sample text, the keyboard's word suggestions and the clock, no real data.

Processes the run started, each gone by `Get-CimInstance Win32_Process -Filter "ProcessId=<pid>"` printing nothing: emulator 51508, qemu-system-x86_64-headless 65736, netsimd 66068, crashpad_handler 56460, adb 44732. The build left no java process. A crashpad_handler (43148) ran before and stays.

Failed tries before the run, while the skill was written: the driver pressed Start before adb had typed (step-1 Not Met, so the check caught it); a whole-string `input text` dropped characters; a keyboard setting and a disabled input method did not keep the keyboard away (the second one opened the voice input); a boot failed while the old qemu still held the AVD. A windowed emulator showed a fragment of the host clipboard on its keyboard; those captures were deleted, and the skill now boots without a window and refuses one. The IME changes were reverted on the AVD. The processes of these tries were stopped by pid.

Answers (R4):
1. Did the text suffice? `ui-verify`: yes for the shape of the skill and the rows, no for the device (next answer). `verify-main-screen`: the agent finished with three additions: it extracted the code block itself, wrote its own boot loop, and noticed that the first adb call starts the server after the listing of step 1.
2. What was missing in `ui-verify`, as proposed AIDLC issues: (a) made-up data in the app does not keep host data (clipboard, keyboard suggestions, notifications) out of a capture; (b) item 2 says that a picture alone is not a pass, and not that an assertion alone can pass on an element that is covered; (c) no capture name for a baseline without a pair, a negative check or an extra row; (d) cleanup by pid does not say to wait until the process table drops the pid, nor that a tool can start a server of its own; (e) retain takes the digest of the recorded success, and the captures of a run whose evidence is committed come from an earlier commit.
3. How long: writing the skill with its probes about 40 min; build 71 s the first time and 12 s up to date; boot 29 s; drive 53 s; cleanup 9 s; the agent's whole run 180 s.
4. Should `screenshot` join `CANDIDATE_BOUND_KINDS`? Recommendation: no, not now. `verifyAudit` blocks an entry whose digest is not the merged one, and a commit that changes only this card changes the digest, so each evidence commit or repair would make valid captures stale (`8d6a0673` here). Two driver runs of one APK gave different sha256 values (clock and time text), so the bytes do not show the candidate either; the APK sha256 does. Revisit it with a rule that compares the covered files.

Selftest (A7) on C = `8d6a0673`, empty `git status` before and after: `[GATE-RESULT-SUMMARY] failed=11,14 ran=9 wall=375.2s`, exit 1: 3, 4, 5, 8, 9, 15 and 16 PASS, 11 and 14 FAIL. On base `ffa037d2`, `-Only 11,14`: `failed=11,14 ran=2 wall=231.1s`, exit 1. The sorted WARNING lines of both logs are equal (`comm` shows none only in the candidate's); the two behind the failures are the dangling link `CLAUDE.md` to `scripts/_validation.ps1` (gate 11) and 14q, whose nested run fails on gate 11 alone. Gate 14's other nested gates pass in both. `git diff --stat C HEAD` lists only this card.
