---
name: verify-main-screen
description: >-
  Project verify skill for the Android main screen (MainActivity and SkeletonScreen), driven on an
  emulator with adb. Use when a card changes a covered file or asks for ui-verify on that screen.
---
# verify-main-screen

## Coverage
Under `android/app/src/main/kotlin/nz/myinspection/app/`: `MainActivity.kt`, `skeleton/SkeletonScreen.kt` and `skeleton/SkeletonState.kt`.

Sync commit: `ffa037d2a69cafc7f93e07fbca9bdb0ea197a48f`

## Launch
PowerShell 7, from the checkout root. Pick `$out`, a capture directory outside every worktree, and save the block of Drive as `$out\drive.ps1`.
1. Build: `cmd /c android\gradlew.bat -p android --offline --no-daemon --no-watch-fs -q "-Pkotlin.compiler.execution.strategy=in-process" :app:assembleDebug`, exit 0. It leaves no daemon, and does not list the host's drives, which can hang.
2. Boot: `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Boot`, exit 0. It refuses a taken port, boots cold (a quick boot can hang, L352) without a window (a window shares the host clipboard, which the on-screen keyboard shows), starts an adb server only if none listens, records every process it owns with its start time in `$out\own.json`, and stops them itself if the boot fails.
3. Keep whether the app was there: `adb -s emulator-5580 shell pm path nz.myinspection.app`.

## Drive
Run `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Apk android\app\build\outputs\apk\debug\app-debug.apk`. Exit 0: every row is Met or a known violation; 2: a row is Not Met or Unverified; 1: the driver failed. Before it installs, drives or captures, it checks that the port of the serial belongs to the headless qemu that this run booted. It installs the APK and compares the installed bytes. It types one character per call, because a whole-string `input text` drops characters under load. Every adb call to a device names the serial: a phone may be attached.
```powershell
param([Parameter(Mandatory)][string]$Out, [string]$Serial = 'emulator-5580', [string]$Apk, [string]$Assert, [switch]$Boot, [switch]$Stop)
$ErrorActionPreference = 'Stop'; $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"; $own = "$Out\own.json"; $port = [int]($Serial -replace '\D'); $script:bad = 0
function Dev { $o = & $adb -s $Serial @args 2>&1; if ($LASTEXITCODE) { throw "adb $($args): $o" }; $o | ? { $_ -isnot [Management.Automation.ErrorRecord] } }; function Listen([int[]]$p) { @(Get-NetTCPConnection -State Listen -ErrorAction Ignore | ? LocalPort -in $p) }
function Mine { # the recorded processes that still run, and their descendants; pid plus start time is the identity
  $all = @(Get-CimInstance Win32_Process); $set = @{}; foreach ($o in @(Get-Content -Raw $own | ConvertFrom-Json)) { $all | ? { $_.ProcessId -eq $o.id -and "$($_.CreationDate.Ticks)" -eq $o.born } | % { $set[[int]$_.ProcessId] = $_ } }
  do { $n = $set.Count; foreach ($p in $all) { $q = $set[[int]$p.ParentProcessId]; if ($q -and $p.CreationDate -ge $q.CreationDate) { $set[[int]$p.ProcessId] = $p } } } while ($set.Count -gt $n); $set.Values }
function Keep([int[]]$add) { $old = if (Test-Path $own) { @(Get-Content -Raw $own | ConvertFrom-Json) }; $new = @(Get-CimInstance Win32_Process | ? ProcessId -in $add | % { [pscustomobject]@{ id = [int]$_.ProcessId; name = $_.Name; born = "$($_.CreationDate.Ticks)" } }); @($old) + $new | ? { $_ } | Sort-Object id, born -Unique | ConvertTo-Json -AsArray | Set-Content $own }
function StopMine { $m = @(Mine); foreach ($p in $m) { Stop-Process -Id $p.ProcessId -Force -ErrorAction Ignore }; foreach ($p in $m) { foreach ($n in 1..60) { $gone = -not (Get-CimInstance Win32_Process -Filter "ProcessId=$($p.ProcessId)" | ? CreationDate -eq $p.CreationDate); if ($gone) { break }; Start-Sleep 1 }; "$($p.Name) $($p.ProcessId) gone: $gone"; if (-not $gone) { $script:bad++ } } }; if ($Stop) { StopMine; exit ($bad ? 2 : 0) }
if ($Boot) {
  if (Test-Path $own) { throw "$own exists" }; if (Listen $port, ($port + 1)) { throw "port $port is taken" }
  try {
    if (-not (Listen 5037)) { Keep (Start-Process $adb 'nodaemon server' -PassThru -WindowStyle Hidden).Id }
    $emu = Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" "-avd Pixel_6 -no-window -no-snapshot -no-boot-anim -no-audio -port $port" -PassThru -WindowStyle Hidden; Keep $emu.Id
    foreach ($n in 1..60) { Start-Sleep 3; if ($emu.HasExited) { throw 'the emulator exited' }; Keep @(Mine).ProcessId; if ("$(& $adb -s $Serial shell getprop sys.boot_completed 2>$null)" -eq '1') { break }; if ($n -eq 60) { throw 'no boot in 180 s' } }; if (-not (Mine | ? Name -like 'qemu-system-*-headless.exe')) { throw 'no headless qemu recorded' }
    foreach ($p in Mine) { "$($p.Name) $($p.ProcessId)" }; exit 0 } catch { if (Test-Path $own) { StopMine }; throw } }
$qemu = @(Mine | ? Name -like 'qemu-system-*-headless.exe').ProcessId; if (-not $qemu -or -not (Listen $port | ? OwningProcess -in $qemu)) { throw "$Serial is not the headless emulator of this run" }; if ("$(Dev shell getprop ro.kernel.qemu)".Trim() -ne '1') { throw "$Serial is not an emulator" }
function Dump { foreach ($n in 1..5) { try { Dev shell rm -f /sdcard/ui.xml; if ("$(Dev shell uiautomator dump /sdcard/ui.xml)" -match 'dumped') { Dev pull /sdcard/ui.xml "$Out\ui.xml" | Out-Null; $script:ui = [xml](Get-Content -Raw "$Out\ui.xml"); return } } catch { "$_" | Out-File -Append "$Out\dump.log" }; Start-Sleep 1 }; throw 'no dump' }
function Find([string]$t) { $script:ui.SelectSingleNode("//node[starts-with(@text,'$t')]") }; function Rect($n) { , [int[]]([regex]::Matches($n.bounds, '\d+').Value) }; function BarBottom { $b = $script:ui.SelectSingleNode("//node[@resource-id='android:id/action_bar_container']"); if ($b) { (Rect $b)[3] } else { 0 } }
function Control([string]$t) { $n = Find $t; if (-not $n) { throw "no text $t" }; $n.SelectSingleNode("ancestor-or-self::node[@clickable='true'][1]") }
function Tap([string]$t) { Dump; $r = Rect (Control $t); $top = [Math]::Max($r[1], (BarBottom)); if ($top -ge $r[3]) { throw "$t is covered" }; Dev shell input tap ([int](($r[0] + $r[2]) / 2)) ([int](($top + $r[3]) / 2)); Start-Sleep 2 }
function Enter([string]$v) { foreach ($c in $v.ToCharArray()) { Dev shell input text "$c".Replace(' ', '%s') }; Start-Sleep 2; Dump; if (-not (Find $v)) { throw "not typed: $v" } }
function Check([string]$c) { $k, $t = $c -split ':', 2; switch ($k) { 'has' { [bool](Find $t) } 'no' { -not (Find $t) } 'on' { (Control $t).enabled -eq 'true' } 'off' { (Control $t).enabled -eq 'false' } 'seen' { (Rect (Find $t))[1] -ge (BarBottom) } default { throw $c } } }; function Shot([string]$id) { Dev shell screencap -p /sdcard/s.png; Dev pull /sdcard/s.png "$Out\$id.png" | Out-Null; (Get-FileHash "$Out\$id.png").Hash.ToLower() }
function Row([string]$id, [string[]]$checks, [switch]$Known) { Dump; $obs = $checks | % { "$_=$(Check $_)" }; $ok = -not ($obs -match '=False$'); if (-not $ok -and -not $Known) { $script:bad++ }; "| $id | $($checks -join ', ') | $($obs -join ', ') | $id.png sha256 ``$(Shot $id)`` | $(if ($ok) { 'Met' } elseif ($Known) { 'Not Met (known)' } else { 'Not Met' }) |" }
if ($Assert) { Dump; if (Check $Assert) { exit 0 }; "ASSERT FALSE $Assert"; exit 1 }
Dev install -r --no-incremental $Apk | Out-Null; $path = "$(Dev shell pm path nz.myinspection.app)" -replace '^package:'; $want = (Get-FileHash $Apk).Hash.ToLower(); $got = ("$(Dev shell sha256sum $path)" -split ' ')[0]; if ($got -ne $want) { throw "installed $got, built $want" }; "apk sha256 $got"; Dev shell am force-stop nz.myinspection.app; Dev shell am start -W -n nz.myinspection.app/.MainActivity | Out-Null; Start-Sleep 2; $rec = $null; $pidDev = 0; $cut = $true
try {
$rec = Start-Process $adb "-s $Serial shell ""echo `$`$; exec screenrecord --time-limit 180 /sdcard/flow.mp4""" -PassThru -NoNewWindow -RedirectStandardOutput "$Out\rec.txt"; $null = $rec.Handle
foreach ($n in 1..20) { if ("$(Get-Content -Raw "$Out\rec.txt")" -match '^(\d+)\s') { $pidDev = [int]$Matches[1]; break }; Start-Sleep -Milliseconds 500 }; if (-not $pidDev) { throw 'no recorder' }; $sw = [Diagnostics.Stopwatch]::StartNew(); Start-Sleep 1
Row 'base' 'has:Walking skeleton', 'has:Address', 'has:Start inspection', 'no:Export PDF'
Row 'visible' 'seen:Walking skeleton', 'seen:Address' -Known; Tap 'Address'; Enter '1 Example Street'; Tap 'Start inspection'
Row 'step-1' 'has:1 Example Street', 'has:Started ', 'has:Item', 'has:Take photo', 'off:Export PDF'
$n = & pwsh -NoProfile -File $PSCommandPath -Out $Out -Serial $Serial -Assert 'on:Export PDF'; $rc = $LASTEXITCODE; $ok = $rc -eq 1 -and "$n" -eq 'ASSERT FALSE on:Export PDF'; if (-not $ok) { $bad++ }
"| negative | on:Export PDF must exit 1 | exit $rc, $n | negative.png sha256 ``$(Shot 'negative')`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |"
Tap 'Item'; Enter 'Sample wall'; Row 'step-2' 'has:Sample wall', 'off:Export PDF'
$at = $sw.Elapsed.TotalSeconds; Tap "$([char]0x597D)"; Row 'step-3' 'has:Sample wall', 'on:Export PDF'; $cut = $rec.HasExited
} finally {
  if ($rec) { if ($pidDev -and -not $rec.HasExited) { & $adb -s $Serial shell kill -2 $pidDev 2>$null | Out-Null }; $done = $rec.WaitForExit(20000); if (-not $done) { if ($pidDev) { & $adb -s $Serial shell kill -9 $pidDev 2>$null | Out-Null }; Stop-Process -Id $rec.Id -ErrorAction Ignore; $null = $rec.WaitForExit(5000) }
    $left = if ($pidDev) { "$(& $adb -s $Serial shell "cat /proc/$pidDev/cmdline 2>/dev/null")" -match 'screenrecord' } else { $null }; $hostGone = -not (Get-CimInstance Win32_Process -Filter "ProcessId=$($rec.Id)" | ? Name -eq 'adb.exe'); "recorder: host adb $($rec.Id) gone: $hostGone; device screenrecord $pidDev gone: $(-not $left)" } }
Dev pull /sdcard/flow.mp4 "$Out\video.mp4" | Out-Null; $b = [IO.File]::ReadAllBytes("$Out\video.mp4"); $i = [Text.Encoding]::Latin1.GetString($b).IndexOf('mvhd'); $len = 0; if ($i -gt 0) { $u = { param($k) [uint32][Convert]::ToHexString($b, $k, 4).Insert(0, '0x') }; $len = [Math]::Round((& $u ($i + 20)) / (& $u ($i + 16)), 1) }
$at = [Math]::Round($at, 1); $ok = -not $cut -and $done -and $rec.ExitCode -eq 0 -and $hostGone -and -not $left -and $len -ge $at; if (-not $ok) { $bad++ }
"| video | finished file that lasts to the step-3 tap ($at s) | $len s, recording at step-3: $(-not $cut), stopped: $done, exit $($rec.ExitCode) | video.mp4 sha256 ``$((Get-FileHash "$Out\video.mp4").Hash.ToLower())`` | $(if ($ok) { 'Met' } else { 'Unverified' }) |"
Dev shell rm -f /sdcard/ui.xml /sdcard/s.png /sdcard/flow.mp4; Remove-Item "$Out\rec.txt"; exit ($bad ? 2 : 0)
```

## Flows
One flow: type an address, start the inspection, type an item, choose the first condition. End state: `Export PDF` is enabled. The rows `base` and `step-1` to `step-3` assert each state from the uiautomator dump; `negative` asserts the end state too early and must fail. Look at each picture as well: the dump lists nodes that another view covers. Known violation: on API 35 the action bar covers the title and the Address label (row `visible`). When a card fixes it, remove `-Known`.

## Not covered
The photo flow (system camera), the PDF export flow (system file picker), other API levels and a physical phone.

## Cleanup
1. If step 2 of Launch failed, it cleaned up: stop here. If step 3 printed no path, `adb -s emulator-5580 uninstall nz.myinspection.app`; if it printed one, `adb -s emulator-5580 shell am force-stop nz.myinspection.app`; if it did not run, neither.
2. `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Stop`, exit 0: it stops only the processes of `own.json` and their descendants, after a check of pid and start time, and prints each as gone from the process table. Keep `$out`.
