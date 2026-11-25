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
PowerShell 7, from the checkout root. Pick `$out`, a capture directory outside every worktree, and save the block of Drive, without its two fence lines, as `$out\drive.ps1`.
1. Build: `cmd /c android\gradlew.bat -p android --offline --no-daemon --no-watch-fs -q "-Pkotlin.compiler.execution.strategy=in-process" :app:assembleDebug`, exit 0. It leaves no daemon, and does not list the host's drives, which can hang.
2. Boot: `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Boot`, exit 0. It refuses a taken port, boots cold (a quick boot can hang, L352) without a window (a window shares the host clipboard, which the on-screen keyboard shows), starts an adb server only if none listens, records the processes it owns with their start times in `own.json` and whether the app was there in `app.txt`, and stops what it owns if it fails. After a failure, take a new `$out`; a line without `gone: True` is a process to look at by hand.

## Drive
Run `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Apk android\app\build\outputs\apk\debug\app-debug.apk`. Exit 0: every row is Met or a known violation; 2: a row is Not Met or Unverified; 1: the driver failed. Before it installs, drives or captures, it checks that the port of the serial belongs to the headless qemu that this run booted. It installs the APK and compares the installed bytes, starts the app and waits until three dumps in a row show its screen; after a cold boot on a busy host it taps Wait on a "System UI" or "Pixel Launcher isn't responding" dialog, never on one of the app. It types one character per call, because a whole-string `input text` drops characters under load. Every adb call to a device names the serial: a phone may be attached.
```powershell
param([Parameter(Mandatory)][string]$Out, [string]$Serial = 'emulator-5580', [string]$Apk, [string]$Assert, [switch]$Boot, [switch]$Stop)
$ErrorActionPreference = 'Stop'; $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"; $own = "$Out\own.json"; $port = [int]($Serial -replace '\D'); $script:bad = 0; $script:held = @(); $script:recPid = 0
function Dev { $o = & $adb -s $Serial @args 2>&1; if ($LASTEXITCODE) { throw "adb $($args): $o" }; $o | ? { $_ -isnot [Management.Automation.ErrorRecord] } }; function Listen([int[]]$p) { @(Get-NetTCPConnection -State Listen -ErrorAction Ignore | ? LocalPort -in $p) }
function Mine { # the recorded processes that still run, and their descendants; pid plus start time is the identity; -Boot holds the handle of what it started, so that pid is not reused
  $all = @(Get-CimInstance Win32_Process); $set = @{}; foreach ($h in $script:held) { $set[[int]$h.ProcessId] = $h }; if (Test-Path $own) { foreach ($o in @(Get-Content -Raw $own | ConvertFrom-Json)) { $all | ? { $_.ProcessId -eq $o.id -and "$($_.CreationDate.Ticks)" -eq $o.born } | % { $set[[int]$_.ProcessId] = $_ } } }
  do { $n = $set.Count; foreach ($p in $all) { $q = $set[[int]$p.ParentProcessId]; if ($q -and $p.CreationDate -ge $q.CreationDate) { $set[[int]$p.ProcessId] = $p } } } while ($set.Count -gt $n); $set.Values | ? { $_.Name } }
function Keep([int[]]$add) { $old = if (Test-Path $own) { @(Get-Content -Raw $own | ConvertFrom-Json) }; $new = @(Get-CimInstance Win32_Process | ? ProcessId -in $add | % { [pscustomobject]@{ id = [int]$_.ProcessId; name = $_.Name; born = "$($_.CreationDate.Ticks)" } }); @($old) + $new | ? { $_ } | Sort-Object id, born -Unique | ConvertTo-Json -AsArray | Set-Content $own }
function StopMine { foreach ($r in 1..3) { $m = @(Mine); foreach ($p in $m) { Stop-Process -Id $p.ProcessId -Force -ErrorAction Ignore }; foreach ($p in $m) { foreach ($n in 1..60) { $gone = -not (Get-CimInstance Win32_Process -Filter "ProcessId=$($p.ProcessId)" | ? CreationDate -eq $p.CreationDate); if ($gone) { break }; Start-Sleep 1 }; "$($p.Name) $($p.ProcessId) gone: $gone"; if (-not $gone) { $script:bad++ } } }; Get-CimInstance Win32_Process | ? { $_.Name -match '^(emulator|qemu)' -and $_.CommandLine -match "-port $port\b" } | % { "$($_.Name) $($_.ProcessId) gone: not stopped, its owner is not proven"; $script:bad++ } }
function Bound { try { $q = @(Mine | ? Name -like 'qemu-system-*-headless.exe').ProcessId; [bool]($q -and (Listen $port | ? OwningProcess -in $q) -and "$(Dev shell getprop ro.kernel.qemu)".Trim() -eq '1') } catch { $false } }
if ($Stop) { if (-not (Test-Path $own)) { throw "no $own" }; $had = "$(Get-Content "$Out\app.txt" -ErrorAction Ignore)"; try { if ($had -notin 'present', 'absent' -or -not (Bound)) { 'app: not touched, its earlier state or the emulator is not proven' } elseif ($had -eq 'present') { Dev shell am force-stop nz.myinspection.app; 'app: stopped' } else { "app: uninstalled $(Dev uninstall nz.myinspection.app)" } } catch { "app: $_"; $bad++ }; StopMine; exit ($bad ? 2 : 0) }
if ($Boot) {
  if (Test-Path $own) { throw "$own exists" }; if (Listen $port, ($port + 1)) { throw "port $port is taken" }
  function Hold($p) { $null = $p.Handle; $script:held += [pscustomobject]@{ ProcessId = $p.Id; CreationDate = $p.StartTime; Name = $null }; Keep $p.Id; $p }
  try { if (-not (Listen 5037)) { $srv = Hold (Start-Process $adb 'nodaemon server' -PassThru -WindowStyle Hidden) }; $t = [Diagnostics.Stopwatch]::StartNew()
    $emu = Hold (Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" "-avd Pixel_6 -no-window -no-snapshot -no-boot-anim -no-audio -port $port" -PassThru -WindowStyle Hidden)
    while ($true) { $dead = $emu.HasExited; Keep @(Mine).ProcessId; if ($dead) { throw 'the emulator exited' }; if ("$(& $adb -s $Serial shell getprop sys.boot_completed 2>$null)" -eq '1') { break }; if ($t.Elapsed.TotalSeconds -gt 180) { throw 'no boot in 180 s' }; Start-Sleep 1 }; if (-not (Bound)) { throw 'no headless qemu recorded' }
    "$(if ("$(Dev shell pm list packages nz.myinspection.app) " -match 'package:nz\.myinspection\.app\s') { 'present' } else { 'absent' })" | Set-Content "$Out\app.txt"; foreach ($p in Mine) { "$($p.Name) $($p.ProcessId)" }; exit 0 } catch { "boot failed: $_"; StopMine; throw } }
if (-not (Bound)) { throw "$Serial is not the headless emulator of this run" }; function Dump { foreach ($n in 1..5) { try { Dev shell rm -f /sdcard/ui.xml; if ("$(Dev shell uiautomator dump /sdcard/ui.xml)" -match 'dumped') { Dev pull /sdcard/ui.xml "$Out\ui.xml" | Out-Null; $script:ui = [xml](Get-Content -Raw "$Out\ui.xml"); return } } catch { "$_" | Out-File -Append "$Out\dump.log" }; Start-Sleep 1 }; throw 'no dump' }
function Find([string]$t) { $script:ui.SelectSingleNode("//node[starts-with(@text,'$t')]") }; function Rect($n) { , [int[]]([regex]::Matches($n.bounds, '\d+').Value) }; function BarBottom { $b = $script:ui.SelectSingleNode("//node[@resource-id='android:id/action_bar_container']"); if ($b) { (Rect $b)[3] } else { 0 } }
function Control([string]$t) { $n = Find $t; if (-not $n) { throw "no text $t" }; $n.SelectSingleNode("ancestor-or-self::node[@clickable='true'][1]") }
function Tap([string]$t) { Dump; $r = Rect (Control $t); $top = [Math]::Max($r[1], (BarBottom)); if ($top -ge $r[3]) { throw "$t is covered" }; Dev shell input tap ([int](($r[0] + $r[2]) / 2)) ([int](($top + $r[3]) / 2)); Start-Sleep 2 }
function Enter([string]$v) { foreach ($c in $v.ToCharArray()) { Dev shell input text "$c".Replace(' ', '%s') }; Start-Sleep 2; Dump; if (-not (Find $v)) { throw "not typed: $v" } }
function Check([string]$c) { $k, $t = $c -split ':', 2; switch ($k) { 'has' { [bool](Find $t) } 'no' { -not (Find $t) } 'on' { (Control $t).enabled -eq 'true' } 'off' { (Control $t).enabled -eq 'false' } 'seen' { $n = Find $t; [bool]$n -and (Rect $n)[1] -ge (BarBottom) } default { throw $c } } }; function Shot([string]$id) { Dev shell screencap -p /sdcard/s.png; Dev pull /sdcard/s.png "$Out\$id.png" | Out-Null; (Get-FileHash "$Out\$id.png").Hash.ToLower() }
function Row([string]$id, [string[]]$checks, [switch]$Known) { Dump; $obs = $checks | % { "$_=$(Check $_)" }; $ok = -not ($obs -match '=False$'); if (-not $ok -and -not $Known) { $script:bad++ }; "| $id | $($checks -join ', ') | $($obs -join ', ') | $id.png sha256 ``$(Shot $id)`` | $(if ($ok) { 'Met' } elseif ($Known) { 'Not Met (known)' } else { 'Not Met' }) |" }
if ($Assert) { Dump; if (Check $Assert) { exit 0 }; "ASSERT FALSE $Assert"; exit 1 }
Dev install -r --no-incremental $Apk | Out-Null; $path = "$(Dev shell pm path nz.myinspection.app)" -replace '^package:'; $want = (Get-FileHash $Apk).Hash.ToLower(); $got = ("$(Dev shell sha256sum $path)" -split ' ')[0]; if ($got -ne $want) { throw "installed $got, built $want" }; "apk sha256 $got"
Dev shell am force-stop nz.myinspection.app; Dev shell am start -W -n nz.myinspection.app/.MainActivity | Out-Null; Start-Sleep 2
$clean = 0; $waits = 0; foreach ($n in 1..40) { try { Dump; if ((Find 'System UI isn') -or (Find 'Pixel Launcher isn')) { $clean = 0; $waits++; Tap 'Wait' } elseif (-not (Find 'Walking skeleton')) { $clean = 0 } elseif (++$clean -ge 3) { break } } catch { $clean = 0; "not ready: $_" }; Start-Sleep 4 }; "settle: $waits system dialogs answered with Wait"; if ($clean -lt 3) { throw 'the device does not settle' }
$tag = [guid]::NewGuid().ToString('N'); $recErr = $null; $cut = 'unknown'; $at = 0; $len = 0; Remove-Item "$Out\video.mp4" -ErrorAction Ignore # the tag in its file name makes the recorder ours
function Rec([string]$sig) { $o = "$(& $adb -s $Serial shell "$(if ($sig) { "pkill -$sig -f '[f]low-$tag'; " })p=`$(pgrep -f '[f]low-$tag'); echo rc=`$? `$p" 2>$null)".Trim(); if ($LASTEXITCODE) { 'unknown' } elseif ($o -match '^rc=0 ([\d ]+)$') { $script:recPid = $Matches[1]; 'alive' } elseif ($o -eq 'rc=1') { 'gone' } else { 'unknown' } }
try {
try { Dev shell "screenrecord --time-limit 180 /sdcard/flow-$tag.mp4 </dev/null >/dev/null 2>&1 &"; foreach ($n in 1..10) { Start-Sleep 1; if (($s = Rec) -eq 'alive') { break } }; if ($s -ne 'alive') { throw "the recorder is $s" } } catch { $recErr = "$_" }; $sw = [Diagnostics.Stopwatch]::StartNew(); Start-Sleep 1
Row 'base' 'has:Walking skeleton', 'has:Address', 'has:Start inspection', 'no:Export PDF'
Row 'visible' 'seen:Walking skeleton', 'seen:Address' -Known; Tap 'Address'; Enter '1 Example Street'; Tap 'Start inspection'
Row 'step-1' 'has:1 Example Street', 'has:Started ', 'has:Item', 'has:Take photo', 'off:Export PDF'
$n = & pwsh -NoProfile -File $PSCommandPath -Out $Out -Serial $Serial -Assert 'on:Export PDF'; $rc = $LASTEXITCODE; $ok = $rc -eq 1 -and "$n" -eq 'ASSERT FALSE on:Export PDF'; if (-not $ok) { $bad++ }
"| negative | on:Export PDF must exit 1 | exit $rc, $n | negative.png sha256 ``$(Shot 'negative')`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |"
Tap 'Item'; Enter 'Sample wall'; Row 'step-2' 'has:Sample wall', 'off:Export PDF'
$at = [Math]::Round($sw.Elapsed.TotalSeconds, 1); Tap "$([char]0x597D)"; Row 'step-3' 'has:Sample wall', 'on:Export PDF'; $cut = Rec
} finally {
  $left = Rec 2; foreach ($n in 1..20) { if ($left -eq 'gone') { break }; Start-Sleep 1; $left = Rec $(if ($n -eq 18) { '9' }) }; "recorder: device screenrecord pid $recPid, file tag ${tag}: $left"; if ($left -ne 'gone') { $bad++ }
  if (-not $recErr) { try { Dev pull "/sdcard/flow-$tag.mp4" "$Out\video.mp4" | Out-Null } catch { $recErr = "$_" } }; try { Dev shell rm -f /sdcard/ui.xml /sdcard/s.png "/sdcard/flow-$tag.mp4" } catch { "device files not removed: $_"; $bad++ } }
try { if ($recErr) { throw $recErr }; $b = [IO.File]::ReadAllBytes("$Out\video.mp4"); $i = [Text.Encoding]::Latin1.GetString($b).IndexOf('mvhd'); if ($i -lt 0 -or $i + 24 -gt $b.Length) { throw 'no mvhd, the file is not finished' }; $u = { param($k) [uint32][Convert]::ToHexString($b, $k, 4).Insert(0, '0x') }; $len = [Math]::Round((& $u ($i + 20)) / (& $u ($i + 16)), 1) } catch { $recErr = "$_" -replace '[\r\n|]+', ' ' }
$ok = -not $recErr -and $cut -eq 'alive' -and $left -eq 'gone' -and $len -ge $at; if (-not $ok) { $bad++ }; $sha = if (Test-Path "$Out\video.mp4") { (Get-FileHash "$Out\video.mp4").Hash.ToLower() } else { 'none' }
"| video | finished file that lasts to the step-3 tap ($at s) | $len s, recorder at step-3 $cut, at the end $left, error: $recErr | video.mp4 sha256 ``$sha`` | $(if ($ok) { 'Met' } else { 'Unverified' }) |"
exit ($bad ? 2 : 0)
```

## Flows
One flow: type an address, start the inspection, type an item, choose the first condition. End state: `Export PDF` is enabled. The rows `base` and `step-1` to `step-3` assert each state from the uiautomator dump; `negative` asserts the end state too early and must fail. Look at each picture as well: the dump lists nodes that another view covers. Known violation: on API 35 the action bar covers the title and the Address label (row `visible`). When a card fixes it, remove `-Known`.

## Not covered
The photo flow (system camera), the PDF export flow (system file picker), other API levels and a physical phone.

## Cleanup
`pwsh -NoProfile -File $out\drive.ps1 -Out $out -Stop`, exit 0, also after a failed Drive. Only when the port of the serial still belongs to the recorded qemu, it removes the app, or stops it if it was there before. Then it stops the processes of `own.json` and their descendants, after a check of pid and start time, and prints each as gone from the process table. Keep `$out`.
