---
name: verify-main-screen
description: >-
  Project verify skill for the Android main screen (MainActivity and SkeletonScreen), driven on an
  emulator with adb. Use when a card changes a covered file or asks for ui-verify on that screen.
---
# verify-main-screen

## Coverage
- `android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt`
- `android/app/src/main/kotlin/nz/myinspection/app/skeleton/SkeletonScreen.kt` and `SkeletonState.kt`

Sync commit: `ffa037d2a69cafc7f93e07fbca9bdb0ea197a48f`

## Launch
PowerShell 7, from the checkout root. Pick `$out`, a capture directory outside every worktree. Every adb call names the serial: a phone may be attached.
1. Record what already runs, which is not yours to stop: `Get-CimInstance Win32_Process | ? Name -match '^(adb|emulator|qemu|netsimd|crashpad|java)' | select ProcessId,ParentProcessId,Name`.
2. Build: `cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug`, exit 0.
3. Boot cold (a quick boot can hang, L352) without a window (no host clipboard) and keep the pid: `$emu = Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" '-avd Pixel_6 -no-window -no-snapshot -no-boot-anim -no-audio -port 5580' -PassThru -WindowStyle Minimized`. Ready when `adb -s emulator-5580 shell getprop sys.boot_completed` prints 1; give up after 180 s. List the processes of step 1 again and keep the new pids. Stop here unless `adb -s emulator-5580 shell getprop ro.kernel.qemu` prints 1.
4. Keep whether the app was there: `adb -s emulator-5580 shell pm path nz.myinspection.app`. Install: `adb -s emulator-5580 install -r android\app\build\outputs\apk\debug\app-debug.apk`.

## Drive
Save this as `$out\drive.ps1` and run `pwsh -NoProfile -File $out\drive.ps1 -Out $out`. Exit 0: every row is Met or a known violation. It types one character per call, because a whole-string `input text` drops characters under load. It refuses an emulator with a window, because that shares the host clipboard and the on-screen keyboard shows it.
```powershell
param([Parameter(Mandatory)][string]$Out, [string]$Serial = 'emulator-5580', [string]$Assert)
$ErrorActionPreference = 'Stop'; $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"; $script:bad = 0
function Dev { $o = & $adb -s $Serial @args 2>$null; if ($LASTEXITCODE) { throw "adb $args" }; $o }
function Dump { foreach ($n in 1..5) { Dev shell rm -f /sdcard/ui.xml; if ("$(Dev shell uiautomator dump /sdcard/ui.xml)" -match 'dumped') { Dev pull /sdcard/ui.xml "$Out\ui.xml" | Out-Null; $script:ui = [xml](Get-Content -Raw "$Out\ui.xml"); return }; Start-Sleep 1 }; throw 'no dump' }
function Find([string]$t) { $script:ui.SelectSingleNode("//node[starts-with(@text,'$t')]") }
function Rect($n) { , [int[]]([regex]::Matches($n.bounds, '\d+').Value) }
function BarBottom { $b = $script:ui.SelectSingleNode("//node[@resource-id='android:id/action_bar_container']"); if ($b) { (Rect $b)[3] } else { 0 } }
function Control([string]$t) { $n = Find $t; if (-not $n) { throw "no text $t" }; $n.SelectSingleNode("ancestor-or-self::node[@clickable='true'][1]") }
function Tap([string]$t) { Dump; $r = Rect (Control $t); $top = [Math]::Max($r[1], (BarBottom)); if ($top -ge $r[3]) { throw "$t is covered" }; Dev shell input tap ([int](($r[0] + $r[2]) / 2)) ([int](($top + $r[3]) / 2)); Start-Sleep 2 }
function Enter([string]$v) { foreach ($c in $v.ToCharArray()) { Dev shell input text "$c".Replace(' ', '%s') }; Start-Sleep 2; Dump; if (-not (Find $v)) { throw "not typed: $v" } }
function Check([string]$c) { $k, $t = $c -split ':', 2; switch ($k) { 'has' { [bool](Find $t) } 'no' { -not (Find $t) } 'on' { (Control $t).enabled -eq 'true' } 'off' { (Control $t).enabled -eq 'false' } 'seen' { (Rect (Find $t))[1] -ge (BarBottom) } default { throw $c } } }
function Shot([string]$id) { Dev shell screencap -p /sdcard/s.png; Dev pull /sdcard/s.png "$Out\$id.png" | Out-Null; (Get-FileHash "$Out\$id.png").Hash.ToLower() }
function Row([string]$id, [string[]]$checks, [switch]$Known) { Dump; $obs = $checks | % { "$_=$(Check $_)" }; $ok = -not ($obs -match '=False$'); if (-not $ok -and -not $Known) { $script:bad++ }; "| $id | $($checks -join ', ') | $($obs -join ', ') | $id.png sha256 ``$(Shot $id)`` | $(if ($ok) { 'Met' } elseif ($Known) { 'Not Met (known)' } else { 'Not Met' }) |" }
if ("$(Dev shell getprop ro.kernel.qemu)".Trim() -ne '1') { throw "$Serial is not an emulator" }; if (-not (Get-Process qemu-system-*-headless -ErrorAction SilentlyContinue)) { throw 'the emulator has a window' }
if ($Assert) { Dump; if (Check $Assert) { exit 0 }; "ASSERT FALSE $Assert"; exit 1 }
Dev shell am force-stop nz.myinspection.app; Dev shell am start -W -n nz.myinspection.app/.MainActivity | Out-Null; Start-Sleep 2
$rec = Start-Process $adb "-s $Serial shell screenrecord --time-limit 180 /sdcard/flow.mp4" -PassThru -WindowStyle Hidden
Row 'base' 'has:Walking skeleton', 'has:Address', 'has:Start inspection', 'no:Export PDF'
Row 'visible' 'seen:Walking skeleton', 'seen:Address' -Known
Tap 'Address'; Enter '1 Example Street'; Tap 'Start inspection'
Row 'step-1' 'has:1 Example Street', 'has:Started ', 'has:Item', 'has:Take photo', 'off:Export PDF'
$n = & pwsh -NoProfile -File $PSCommandPath -Out $Out -Serial $Serial -Assert 'on:Export PDF'; $rc = $LASTEXITCODE; $ok = $rc -eq 1 -and "$n" -eq 'ASSERT FALSE on:Export PDF'; if (-not $ok) { $bad++ }
"| negative | on:Export PDF must exit 1 | exit $rc, $n | negative.png sha256 ``$(Shot 'negative')`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |"
Tap 'Item'; Enter 'Sample wall'; Row 'step-2' 'has:Sample wall', 'off:Export PDF'
Tap "$([char]0x597D)"; Row 'step-3' 'has:Sample wall', 'on:Export PDF'
$whole = -not $rec.HasExited; if ($whole) { Dev shell 'kill -2 $(pidof screenrecord)' }; $rec.WaitForExit(15000) | Out-Null; Start-Sleep 1; Dev pull /sdcard/flow.mp4 "$Out\video.mp4" | Out-Null
$len = (Get-Item "$Out\video.mp4").Length; $ok = $whole -and $len -gt 0; if (-not $ok) { $bad++ }
"| video | base to step-3 recorded | $len bytes, still recording at step-3: $whole | video.mp4 sha256 ``$((Get-FileHash "$Out\video.mp4").Hash.ToLower())`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |"
Dev shell rm -f /sdcard/ui.xml /sdcard/s.png /sdcard/flow.mp4
exit $bad
```

## Flows
One flow: type an address, start the inspection, type an item, choose the first condition. End state: `Export PDF` is enabled. The rows `base` and `step-1` to `step-3` assert each state from the uiautomator dump; `negative` asserts the end state too early and must fail. Look at each picture as well: the dump lists nodes that another view covers.

Known violation: on API 35 the action bar covers the title and the Address label (row `visible`). When a card fixes it, remove `-Known`.

## Not covered
The photo flow (system camera), the PDF export flow (system file picker), other API levels and a physical phone.

## Cleanup
1. If step 4 of Launch printed no path, `adb -s emulator-5580 uninstall nz.myinspection.app`; else `adb -s emulator-5580 shell am force-stop nz.myinspection.app`.
2. For each new pid of Launch, check its name with `Get-Process -Id <pid>`, then `Stop-Process -Id <pid>`: the emulator, its qemu, netsimd and crashpad_handler children, the adb server if this run started it, and any java left by the build.
3. Wait until `Get-Process -Id <pid>` fails for each of them; a new boot fails while the old qemu still exits. Keep `$out`.
