---
name: verify-main-screen
description: >-
  Project verify skill for the Android main screen (MainActivity and SkeletonScreen), driven on an
  emulator with adb. Use when a card changes a covered file or asks for ui-verify on that screen.
---
# verify-main-screen

## Coverage
Under `android/app/src/main/kotlin/nz/myinspection/app/`: `MainActivity.kt`, `skeleton/SkeletonScreen.kt` and `skeleton/SkeletonState.kt`. Under `android/app/src/main/`: `AndroidManifest.xml` and `res/values/themes.xml`, whose NoActionBar theme and the root inset padding in `MainActivity.kt` keep the screen clear of the bars on API 35; a new root screen keeps both.

Sync commit: `1bb9d3b84bd85cbc079d335a324bb7adeab6f46c`

## Launch
PowerShell 7 on Windows, from the checkout root. Pick `$out`, a new capture directory outside every worktree, and save the block of Drive, without its two fence lines, as `$out\drive.ps1`. Build: `cmd /c android\gradlew.bat -p android --offline --no-daemon --no-watch-fs -q "-Pkotlin.compiler.execution.strategy=in-process" :app:assembleDebug`, exit 0. It leaves no daemon, and does not list the host's drives, which can hang. The driver boots the emulator itself.

## Drive
Run `pwsh -NoProfile -File $out\drive.ps1 -Out $out -Apk android\app\build\outputs\apk\debug\app-debug.apk` with a limit of at least 10 min (it takes about 2). Exit 0: every row is Met; 2: a row is Not Met or Unverified, or cleanup is not proven; 1: the driver failed.

The driver puts itself in a Windows job object, so that every process that it or a child starts, an adb server included, belongs to the run, also after its parent ended. It refuses a taken emulator port and an adb server that already runs (that one is not its own: ask its owner). It boots cold (a quick boot can hang, L352) without a window (a window shares the host clipboard, which the on-screen keyboard shows). Before it installs, drives or captures, it checks that a headless qemu of its job listens on the port of the serial, and before each device call that this qemu still runs. It compares the installed bytes with the APK and waits until three dumps in a row show the screen; after a cold boot on a busy host it taps Wait on a "System UI" or "Pixel Launcher isn't responding" dialog, never on one of the app. A node counts as seen only if it overlaps no visible status bar or navigation bar, read from `dumpsys window` once the screen settles, and no action bar in the dump; a tap aims at the part of a control that no bar covers. It types one character per call, because a whole-string `input text` drops characters under load. Every adb call to a device names the serial: a phone may be attached.
```powershell
param([Parameter(Mandatory)][string]$Out, [string]$Serial = 'emulator-5580', [string]$Apk, [string]$Assert)
$ErrorActionPreference = 'Stop'; $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"; $port = [int]($Serial -replace '\D'); $script:bad = 0; $script:qemu = $null; $script:recPid = 0; $script:sys = @(); $fail = 0
function Dev { if (-not $script:qemu -or $script:qemu.HasExited) { throw 'the emulator of this run ended' }; $o = & $adb -s $Serial @args 2>&1; if ($LASTEXITCODE) { throw "adb $($args): $o" }; $o | ? { $_ -isnot [Management.Automation.ErrorRecord] } }; function Listen([int[]]$p) { @(Get-NetTCPConnection -State Listen -ErrorAction Ignore | ? LocalPort -in $p) }
function Dump { foreach ($n in 1..5) { try { Dev shell rm -f /sdcard/ui.xml; if ("$(Dev shell uiautomator dump /sdcard/ui.xml)" -match 'dumped') { Dev pull /sdcard/ui.xml "$Out\ui.xml" | Out-Null; $script:ui = [xml](Get-Content -Raw "$Out\ui.xml"); return } } catch { "$_" | Out-File -Append "$Out\dump.log" }; Start-Sleep 1 }; throw 'no dump' }
function Find([string]$t) { $script:ui.SelectSingleNode("//node[starts-with(@text,'$t')]") }; function Rect($n) { , [int[]]([regex]::Matches($n.bounds, '\d+').Value) }; function SysBars { $o = "$(Dev shell dumpsys window)"; $script:sys = @([regex]::Matches($o, 'InsetsSource id=\S+ type=(?:statusBars|navigationBars) frame=\[(\d+),(\d+)\]\[(\d+),(\d+)\] visible=true') | % { $m = $_; (1..4 | % { $m.Groups[$_].Value }) -join ',' } | Sort-Object -Unique | % { , [int[]]($_ -split ',') }); if (-not $script:sys) { throw 'no system bar bounds in dumpsys window' }; "bars: $(($script:sys | % { $_ -join ',' }) -join ' ')" }
function Bars { $l = [Collections.Generic.List[object]]::new(); foreach ($s in $script:sys) { $l.Add($s) }; $b = $script:ui.SelectSingleNode("//node[@resource-id='android:id/action_bar_container']"); if ($b) { $l.Add((Rect $b)) }; , $l.ToArray() }; function Covered($r) { foreach ($b in (Bars)) { if ($r[0] -lt $b[2] -and $b[0] -lt $r[2] -and $r[1] -lt $b[3] -and $b[1] -lt $r[3]) { return $true } }; $false }
function Control([string]$t) { $n = Find $t; if (-not $n) { throw "no text $t" }; $n.SelectSingleNode("ancestor-or-self::node[@clickable='true'][1]") }
function Tap([string]$t) { Dump; $r = Rect (Control $t); $top = $r[1]; $bot = $r[3]; foreach ($b in (Bars)) { if ($b[0] -lt $r[2] -and $r[0] -lt $b[2]) { if ($b[1] -le $r[1] -and $b[3] -gt $top) { $top = $b[3] } elseif ($b[3] -ge $r[3] -and $b[1] -lt $bot) { $bot = $b[1] } } }; if ($top -ge $bot) { throw "$t is covered" }; Dev shell input tap ([int](($r[0] + $r[2]) / 2)) ([int](($top + $bot) / 2)); Start-Sleep 2 }
function Enter([string]$v) { foreach ($c in $v.ToCharArray()) { Dev shell input text "$c".Replace(' ', '%s') }; Start-Sleep 2; Dump; if (-not (Find $v)) { throw "not typed: $v" } }
function Check([string]$c) { $k, $t = $c -split ':', 2; switch ($k) { 'has' { [bool](Find $t) } 'no' { -not (Find $t) } 'on' { (Control $t).enabled -eq 'true' } 'off' { (Control $t).enabled -eq 'false' } 'seen' { $n = Find $t; [bool]$n -and -not (Covered (Rect $n)) } default { throw $c } } }; function Shot([string]$id) { Dev shell screencap -p /sdcard/s.png; Dev pull /sdcard/s.png "$Out\$id.png" | Out-Null; (Get-FileHash "$Out\$id.png").Hash.ToLower() }
function Row([string]$id, [string[]]$checks) { Dump; $obs = $checks | % { "$_=$(Check $_)" }; $ok = -not ($obs -match '=False$'); if (-not $ok) { $script:bad++ }; "| $id | $($checks -join ', ') | $($obs -join ', ') | $id.png sha256 ``$(Shot $id)`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |" }
if ($Assert) { $script:ui = [xml](Get-Content -Raw "$Out\ui.xml"); if (Check $Assert) { exit 0 }; "ASSERT FALSE $Assert"; exit 1 } # reads the last dump of the driver, calls no device
Add-Type -Namespace Win -Name Job -MemberDefinition (('IntPtr CreateJobObject(IntPtr a, IntPtr n)', 'bool SetInformationJobObject(IntPtr j, int c, byte[] b, int n)', 'bool AssignProcessToJobObject(IntPtr j, IntPtr p)', 'bool QueryInformationJobObject(IntPtr j, int c, byte[] b, int n, IntPtr r)', 'bool IsProcessInJob(IntPtr p, IntPtr j, out bool r)' | % { "[DllImport(`"kernel32`", SetLastError = true)] public static extern $_;" }) -join "`n")
if (Listen 5037) { throw 'an adb server already runs' }; if (Listen $port, ($port + 1)) { throw "port $port is taken" }
$job = [Win.Job]::CreateJobObject(0, 0); $lim = [byte[]]::new(144); $lim[17] = 0x20 # KILL_ON_JOB_CLOSE and no breakaway: if this process is killed, Windows ends the rest of the job
if ($job -eq 0 -or -not [Win.Job]::SetInformationJobObject($job, 9, $lim, 144) -or -not [Win.Job]::AssignProcessToJobObject($job, [Diagnostics.Process]::GetCurrentProcess().Handle)) { throw "no job, error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())" }
function Members { $b = [byte[]]::new(8200); if (-not [Win.Job]::QueryInformationJobObject($job, 3, $b, 8200, 0)) { throw 'the job cannot be read' }; for ($i = 0; $i -lt [BitConverter]::ToInt32($b, 4); $i++) { $id = [int][BitConverter]::ToInt64($b, 8 + 8 * $i); if ($id -ne $PID) { $id } } }
function StopJob { foreach ($n in 1..60) { $m = @(Members); if (-not $m) { break }; foreach ($id in $m) { try { $p = [Diagnostics.Process]::GetProcessById($id); $name = $p.ProcessName; $in = $false; if ([Win.Job]::IsProcessInJob($p.Handle, $job, [ref]$in) -and $in) { $p.Kill(); "ended $name $id" } } catch { "${id}: $_" } }; Start-Sleep 1 }; $m = @(Members); "job: $($m.Count) left $m"; if ($m) { $script:bad++ } }
try {
$null = Start-Process $adb 'nodaemon server' -PassThru -WindowStyle Hidden; $emu = Start-Process "$env:ANDROID_HOME\emulator\emulator.exe" "-avd Pixel_6 -no-window -no-snapshot -no-boot-anim -no-audio -port $port" -PassThru -WindowStyle Hidden; $t = [Diagnostics.Stopwatch]::StartNew()
while ("$(& $adb -s $Serial shell getprop sys.boot_completed 2>$null)" -ne '1') { if ($emu.HasExited) { throw 'the emulator exited' }; if ($t.Elapsed.TotalSeconds -gt 180) { throw 'no boot in 180 s' }; Start-Sleep 1 }
$run = @(Members | % { Get-Process -Id $_ -ErrorAction Ignore }); $run | % { "job: $($_.ProcessName) $($_.Id)" }; $script:qemu = $run | ? { $_.ProcessName -like 'qemu-system-*-headless' -and (Listen $port | ? OwningProcess -eq $_.Id) } | Select-Object -First 1
if ($qemu) { $null = $qemu.Handle }; if (-not $qemu -or $qemu.Id -notin @(Members) -or "$(Dev shell getprop ro.kernel.qemu)".Trim() -ne '1') { throw "$Serial is not the headless emulator of this run" }; "booted in $([int]$t.Elapsed.TotalSeconds) s, API $(Dev shell getprop ro.build.version.sdk), ro.kernel.qemu 1"
Dev install -r --no-incremental $Apk | Out-Null; $path = "$(Dev shell pm path nz.myinspection.app)" -replace '^package:'; $want = (Get-FileHash $Apk).Hash.ToLower(); $got = ("$(Dev shell sha256sum $path)" -split ' ')[0]; if ($got -ne $want) { throw "installed $got, built $want" }; "apk sha256 $got"
Dev shell am force-stop nz.myinspection.app; Dev shell am start -W -n nz.myinspection.app/.MainActivity | Out-Null; Start-Sleep 2
$clean = 0; $waits = 0; foreach ($n in 1..40) { try { Dump; if ((Find 'System UI isn') -or (Find 'Pixel Launcher isn')) { $clean = 0; $waits++; Tap 'Wait' } elseif (-not (Find 'Walking skeleton')) { $clean = 0 } elseif (++$clean -ge 3) { break } } catch { $clean = 0; "not ready: $_" }; Start-Sleep 4 }; "settle: $waits system dialogs answered with Wait"; if ($clean -lt 3) { throw 'the device does not settle' }; SysBars
$tag = [guid]::NewGuid().ToString('N'); $recErr = $null; $cut = 'unknown'; $at = 0; $len = 0; Remove-Item "$Out\video.mp4" -ErrorAction Ignore # the tag in its file name makes the recorder ours
function Rec([string]$sig) { if ($qemu.HasExited) { return 'unknown' }; $o = "$(& $adb -s $Serial shell "$(if ($sig) { "pkill -$sig -f '[f]low-$tag'; " })p=`$(pgrep -f '[f]low-$tag'); echo rc=`$? `$p" 2>$null)".Trim(); if ($LASTEXITCODE) { 'unknown' } elseif ($o -match '^rc=0 ([\d ]+)$') { $script:recPid = $Matches[1]; 'alive' } elseif ($o -eq 'rc=1') { 'gone' } else { 'unknown' } }
try {
try { Dev shell "screenrecord --time-limit 180 /sdcard/flow-$tag.mp4 </dev/null >/dev/null 2>&1 &"; foreach ($n in 1..10) { Start-Sleep 1; if (($s = Rec) -eq 'alive') { break } }; if ($s -ne 'alive') { throw "the recorder is $s" } } catch { $recErr = "$_" }; $sw = [Diagnostics.Stopwatch]::StartNew(); Start-Sleep 1
Row 'base' 'has:Walking skeleton', 'has:Address', 'has:Start inspection', 'no:Export PDF'
Row 'visible' 'seen:Walking skeleton', 'seen:Address'; Tap 'Address'; Enter '1 Example Street'; Tap 'Start inspection'
Row 'step-1' 'has:1 Example Street', 'has:Started ', 'has:Item', 'has:Take photo', 'off:Export PDF'
Dump; $n = & pwsh -NoProfile -File $PSCommandPath -Out $Out -Assert 'on:Export PDF'; $rc = $LASTEXITCODE; $ok = $rc -eq 1 -and "$n" -eq 'ASSERT FALSE on:Export PDF'; if (-not $ok) { $bad++ }
"| negative | on:Export PDF must exit 1 | exit $rc, $n | negative.png sha256 ``$(Shot 'negative')`` | $(if ($ok) { 'Met' } else { 'Not Met' }) |"
Tap 'Item'; Enter 'Sample wall'; Row 'step-2' 'has:Sample wall', 'off:Export PDF'
$at = [Math]::Round($sw.Elapsed.TotalSeconds, 1); Tap "$([char]0x597D)"; Row 'step-3' 'has:Sample wall', 'on:Export PDF'; $cut = Rec
} finally {
  $left = Rec 2; foreach ($n in 1..20) { if ($left -eq 'gone') { break }; Start-Sleep 1; $left = Rec $(if ($n -eq 18) { '9' }) }; "recorder: device screenrecord pid $recPid, file tag ${tag}: $left"; if ($left -ne 'gone') { $bad++ }
  if (-not $recErr) { try { Dev pull "/sdcard/flow-$tag.mp4" "$Out\video.mp4" | Out-Null } catch { $recErr = "$_" } }; try { Dev shell rm -f /sdcard/ui.xml /sdcard/s.png "/sdcard/flow-$tag.mp4" } catch { "device files not removed: $_"; $bad++ } }
try { if ($recErr) { throw $recErr }; $b = [IO.File]::ReadAllBytes("$Out\video.mp4"); $i = [Text.Encoding]::Latin1.GetString($b).IndexOf('mvhd', [StringComparison]::Ordinal); if ($i -lt 0 -or $i + 24 -gt $b.Length) { throw 'no mvhd, the file is not finished' }; $u = { param($k) [uint32][Convert]::ToHexString($b, $k, 4).Insert(0, '0x') }; $len = [Math]::Round((& $u ($i + 20)) / (& $u ($i + 16)), 1) } catch { $recErr = "$_" -replace '[\r\n|]+', ' ' }
$ok = -not $recErr -and $cut -eq 'alive' -and $left -eq 'gone' -and $len -ge $at; if (-not $ok) { $bad++ }; $sha = if (Test-Path "$Out\video.mp4") { (Get-FileHash "$Out\video.mp4").Hash.ToLower() } else { 'none' }
"| video | finished file that lasts to the step-3 tap ($at s) | $len s, recorder at step-3 $cut, at the end $left, error: $recErr | video.mp4 sha256 ``$sha`` | $(if ($ok) { 'Met' } else { 'Unverified' }) |"
} catch { $fail = 1; "driver failed: $_ (line $($_.InvocationInfo.ScriptLineNumber))" } finally {
  if ($qemu) { try { Dev shell 'am force-stop nz.myinspection.app && sync' } catch { "app not stopped: $_"; $bad++ } }; try { StopJob } catch { "job: $_"; $bad++ } }
exit ($fail ? 1 : $bad ? 2 : 0)
```

## Flows
One flow: type an address, start the inspection, type an item, choose the first condition. End state: `Export PDF` is enabled. The rows `base` and `step-1` to `step-3` assert each state from the uiautomator dump; `negative` asserts the end state too early and must fail. Look at each picture as well: the dump lists nodes that another view covers. The row `visible` asserts that no system bar or action bar covers the title and the Address label.

## Not covered
The photo flow (system camera), the PDF export flow (system file picker), other API levels and a physical phone.

## Cleanup
The driver cleans up itself, also after a failure: it stops the app and its recorder on the device, then ends each process of its job by pid, after a check that the pid is in the job, and prints its name. The last line, `job: 0 left`, is the probe: the job lists no process but the driver. If the driver is killed, Windows ends the job. The app stays installed, because an uninstall would also delete data that was there before. Keep `$out`.
