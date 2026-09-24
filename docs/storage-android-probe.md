# Android storage adapter device probe

Device acceptance for `AndroidAppStorageEnvironment` (card `T1-APP-STORAGE-ANDROID`). The JVM tests cover only the
pure raw-state mapper and the writable-directory helper; every Context and Environment call is checked by the debug
`AppStorageProbeActivity` on a real API 33 phone and an API 35 emulator. A green build is not device acceptance.

## What the probe checks

The probe runs once per launch, in `onCreate`, against the installed candidate APK. Expected values are platform
getters called by the probe itself, never the adapter's results. A failed check throws a `java.lang.AssertionError`
whose message is the check name and ends the run; any other exception ends it as `ERROR`, which the host script
never accepts. Check names say which kind of fact they rest on: `pre` and the unmarked checks are platform facts,
`synthetic` and `controlled` checks run the real adapter over a wrapper or `File` subclass that substitutes one value.

| Group | Checks |
|---|---|
| A1 pre | CE/DP context markers; `noBackupFilesDir` ≠ `filesDir`; DP data and no-backup roots ≠ CE; app external ≠ shared storage root and public Pictures |
| A1 adapter | marker on CE and DP contexts; no-backup, data, DP data and external roots equal the getters; DP → CE conversion gives the CE marker and CE roots |
| A1 policy | `AppStoragePolicy` over the CE adapter and over the DP adapter (converted) routes all six categories under the real CE no-backup root |
| A1 synthetic | a `ContextWrapper` over the real DP context that only reports a false CE marker keeps the DP root, and the policy refuses it (fixed message, no cause) |
| A2 pre | raw states recorded first: app external, global and shared root `mounted`; internal `filesDir` and the shared root's parent neither mounted nor read-only |
| A2 adapter | external → MOUNTED, internal → UNMOUNTED, shared root → MOUNTED; a fresh owned directory is writable, a writable plain file and an absent child are not; the policy gives Available over the real adapter |
| A2 controlled | a wrapper that only supplies the absent child as external directory: its state reads mounted, space reads without error, 0 bytes are requested, writability is false, and the policy gives Unavailable without creating it |
| A3 | controlled: a `File` whose usable space is a sentinel is handed over exactly; platform: the adapter's usable space equals two direct reads in an attempt where they agree |
| A3 controlled | `NameNotFoundException` and `SecurityException` from package-context creation become the fixed message without a cause; an `Error` propagates as the same instance |
| cleanup | the external fixture is gone after the checks |

The read-only raw state and a directory without write permission are covered only by the JVM tests: an ordinary APK
cannot mount a read-only volume, and the probe does not change directory permissions. The unknown raw state is
covered on the devices too, where the internal directory reads `unknown` and must map to UNMOUNTED, as well as by
the JVM test. Device-protected default storage is not reproduced: this app does not
request it and the card treats it as system-only, so the synthetic false-marker wrapper stands in for a context that
claims CE while holding DP roots.

## Receipt and leftovers

The probe writes `files/app-storage-probe/<runId>.txt` in its own app data: `run=<runId>`,
`apk=<sha256 of the installed base.apk>`, one `STATE name=value …` line of raw platform states, one `PASS <check>` per
passed check, then `DONE <runId>`, `FAIL <check> java.lang.AssertionError` (`unnamed` if the message is not a check
name) or `ERROR <exception class>`. It holds no paths and no file content.

The probe creates and deletes the external fixture `app-storage-probe-<runId>`; a process killed mid-run leaves it
behind. The host script removes the run's receipt but leaves the `files/app-storage-probe/` directory. Reading
the DP context's `noBackupFilesDir` makes the platform create an empty `no_backup` directory in device-protected
storage. The activity is exported only to callers holding `android.permission.DUMP`, which `adb shell` holds and
ordinary apps cannot obtain on their own.

## Host run

Build the candidate with the card DoD, save the script below as `.secrets\storage-probe.ps1` and run it from the
repository root once per device. It checks the APK's application id and, when the app is already installed, that both
are signed by the same certificate; installs with `install -r` only (app data kept); starts only the probe activity
with a fresh run id; reads and removes that run's receipt; force-stops the app and requires `pidof` to report no
process. It prints the device model, API level, build type, emulator flag, the SHA-256 of the APK, the adapter
source and the probe source, the Git tree id of the committed `android/` directory and the number of `android/`
paths that differ from it (0 for the candidate itself, 1 for a mutant). It exits 0 only when the receipt has exactly
the expected lines for this run id and APK: every check in order ending in `DONE`, or (`-Expect <check>`) the checks
before `<check>` followed by that check failing with `java.lang.AssertionError`. Every line must equal its expected
text except the `STATE` line, which must match the `STATE name=value …` pattern; its values are asserted inside the
probe by `A2.pre.rawStates`.

```powershell
param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Apk, [string]$Expect = 'pass',
    [string]$Repo = (Get-Location).Path)
$ErrorActionPreference = 'Stop'
$package = 'nz.myinspection.app'
$checks = @(
    'A1.pre.contextMarkers', 'A1.pre.noBackupDistinctFromFiles', 'A1.pre.dpDistinctFromCe',
    'A1.pre.externalDistinctFromPublic', 'A1.marker.ce', 'A1.marker.dp', 'A1.root.noBackup', 'A1.root.appData',
    'A1.root.deviceProtectedData', 'A1.root.external', 'A1.convert.marker', 'A1.convert.roots',
    'A1.policy.ceRoutes', 'A1.policy.dpConvertedRoutes', 'A1.synthetic.falseMarkerKeepsDpRoot',
    'A1.synthetic.falseMarkerRejected', 'A2.pre.rawStates', 'A2.state.external', 'A2.state.internal',
    'A2.state.volumeRoot', 'A2.writable.ownedDirectory', 'A2.writable.plainFile', 'A2.writable.absentPath',
    'A2.policy.available', 'A2.controlled.absentExternalUnavailable', 'A3.controlled.usableSpaceSentinel',
    'A3.usableSpace', 'A3.controlled.nameNotFoundRefused', 'A3.controlled.securityRefused',
    'A3.controlled.errorPropagates', 'cleanup.fixtureRemoved')
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$tools = Join-Path $env:ANDROID_HOME 'build-tools\35.0.0'
$platform = 'android/app/src/main/kotlin/nz/myinspection/app/platform'
function Fail([string]$why) { Write-Output "[PROBE-FAIL] $why"; exit 1 }
function Same([string]$a, [string]$b) { [string]::Equals($a, $b, [StringComparison]::Ordinal) }
function Sha([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Prop([string]$name) { ((& $adb -s $Serial shell getprop $name) -join '').Trim() }
function CertDigest([string]$path) {
    $out = cmd /c "`"$tools\apksigner.bat`" verify --print-certs `"$path`""
    $match = [regex]::Match(($out -join "`n"), 'Signer #1 certificate SHA-256 digest: ([0-9a-f]{64})')
    if (-not $match.Success) { Fail 'no signer digest' }
    $match.Groups[1].Value
}
if (-not (Same $Expect 'pass') -and [array]::IndexOf($checks, $Expect) -lt 0) { Fail "unknown check $Expect" }
$apkSha = Sha $Apk
$tree = ((git -C $Repo rev-parse 'HEAD:android') -join '').Trim()
$dirty = @(git -C $Repo status --porcelain -- android).Count
$identity = "model=$(Prop ro.product.model) sdk=$(Prop ro.build.version.sdk) type=$(Prop ro.build.type) " +
    "qemu=$(Prop ro.kernel.qemu) apk=$apkSha src=$(Sha "$Repo/$platform/AndroidAppStorageEnvironment.kt") " +
    "probe=$(Sha "$Repo/android/app/src/debug/kotlin/nz/myinspection/app/platform/AppStorageProbeActivity.kt") " +
    "android-tree=$tree android-dirty=$dirty"
Write-Output "[PROBE-ID] $identity"
if (-not (Same ((& "$tools\aapt2.exe" dump packagename $Apk) -join '').Trim() $package)) { Fail 'application id' }
$installed = (& $adb -s $Serial shell pm path $package) -replace '^package:', ''
if ($installed) {
    $copy = Join-Path ([IO.Path]::GetTempPath()) "installed-$([guid]::NewGuid()).apk"
    & $adb -s $Serial pull $installed $copy | Out-Null
    $sameSigner = Same (CertDigest $copy) (CertDigest $Apk)
    Remove-Item -LiteralPath $copy
    if (-not $sameSigner) { Fail 'signing certificate differs from the installed app' }
}
$install = & $adb -s $Serial install -r $Apk
if ($LASTEXITCODE -ne 0 -or -not (($install -join "`n") -cmatch 'Success')) { Fail 'install -r' }
$runId = [guid]::NewGuid().ToString()
$receiptPath = "files/app-storage-probe/$runId.txt"
& $adb -s $Serial shell am start -W -n "$package/.platform.AppStorageProbeActivity" --es runId $runId | Out-Null
$receipt = $null
foreach ($attempt in 1..30) {
    & $adb -s $Serial shell run-as $package ls $receiptPath *> $null
    if ($LASTEXITCODE -eq 0) { $receipt = @(& $adb -s $Serial shell run-as $package cat $receiptPath); break }
    Start-Sleep -Seconds 1
}
if ($receipt) {
    & $adb -s $Serial shell run-as $package rm -f $receiptPath
    if ($LASTEXITCODE -ne 0) { Fail 'receipt removal' }
}
& $adb -s $Serial shell am force-stop $package
if ($LASTEXITCODE -ne 0) { Fail 'force-stop' }
$pidProbe = @(& $adb -s $Serial shell "pidof $package; echo rc=`$?")
if (-not (Same $pidProbe[-1] 'rc=1')) { Fail 'app process still running' }
if (-not $receipt) { Fail 'no receipt for this run' }
$receipt | ForEach-Object { Write-Output "  $_" }
$lines = @("run=$runId", "apk=$apkSha")
foreach ($check in $checks) {
    if (Same $check 'A2.pre.rawStates') { $lines += '<STATE>' }
    $lines += "PASS $check"
}
if (Same $Expect 'pass') { $lines += "DONE $runId" } else {
    $lines = @($lines[0..([array]::IndexOf($lines, "PASS $Expect") - 1)]) + "FAIL $Expect java.lang.AssertionError"
}
$ok = $receipt.Count -eq $lines.Count
for ($i = 0; $ok -and $i -lt $lines.Count; $i++) {
    $ok = if (Same $lines[$i] '<STATE>') { $receipt[$i] -cmatch '^STATE( [a-zA-Z]+=[a-z_]+)+$' } else { Same $receipt[$i] $lines[$i] }
}
if (-not $ok) { Fail "receipt does not match expect=$Expect" }
Write-Output "[PROBE-OK] expect=$Expect run=$runId $identity"
```

```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
pwsh -NoProfile -File .secrets\storage-probe.ps1 -Serial <adb serial> -Apk android\app\build\outputs\apk\debug\app-debug.apk
```

## Mutations

Each mutant changes `AndroidAppStorageEnvironment.kt` as shown. A device mutant counts as detected only when
`:app:assembleDebug` exits 0 and the script above exits 0 with `-Expect <check>` on both devices (so `install -r`
succeeded and the probe failed exactly there). A JVM mutant counts when `:app:compileDebugUnitTestKotlin` exits 0 and
the named test fails with an assertion.

| Id | Change | Detected by |
|---|---|---|
| D01 | `noBackupFilesDir` returns `context.filesDir` | `A1.root.noBackup` |
| D02 | `appDataDir` returns `context.filesDir` | `A1.root.appData` |
| D03 | `deviceProtectedDataDir` returns `context.dataDir` | `A1.root.deviceProtectedData` |
| D04 | `appSpecificExternalMediaDir` returns the public Pictures directory | `A1.root.external` |
| D05 | `isDeviceProtectedStorage` returns `false` | `A1.marker.dp` |
| D06 | conversion returns `AndroidAppStorageEnvironment(context)` | `A1.convert.marker` |
| D07 | state reads `Environment.getExternalStorageState()` (global) | `A2.state.internal` |
| D08 | writability returns `true` | `A2.writable.plainFile` |
| D09 | space returns `directory.freeSpace` | `A3.controlled.usableSpaceSentinel` |
| D10 | conversion refusal keeps the caught exception as cause | `A3.controlled.nameNotFoundRefused` |
| D11 | conversion refusal copies the caught message | `A3.controlled.nameNotFoundRefused` |
| D12 | conversion catches only `NameNotFoundException` | `A3.controlled.securityRefused` |
| D13 | conversion catches `Throwable` | `A3.controlled.errorPropagates` |
| D14 | state reads `directory.parentFile` | `A2.state.volumeRoot` |
| J01 | `MEDIA_MOUNTED` maps to UNMOUNTED | `mounted maps to mounted and read-only keeps its own state` |
| J02 | `MEDIA_MOUNTED_READ_ONLY` maps to MOUNTED | `mounted maps to mounted and read-only keeps its own state` |
| J03 | other raw states map to MOUNTED | `every other raw state maps to unmounted` |
| J04 | helper checks `exists()` instead of `isDirectory` | `only an existing directory counts as writable` |
| J05 | helper drops `canWrite()` | `a directory that cannot be written is not writable` |

## Evidence

Run 2026-09-25 on a Samsung SM-A346E (API 33, `user` build) and an `sdk_gphone64_x86_64` emulator (API 35,
`userdebug`, `ro.kernel.qemu=1`), every build from commit `37c235be` of this branch, whose `android/` tree id is
`ca63349872091c21c0eb683f9bad1050a31145f6` (`git rev-parse 37c235be:android`). The script prints that tree id and
`android-dirty` on every run, so a run can be matched to a candidate by comparing it with
`git rev-parse <candidate>:android`; the whole `android/` tree, prerequisites included, is what it identifies. Source
SHA-256 at that commit: adapter `54dd0218…72d965`, probe `7a1ad65f…c23d18`; host script as extracted from this file
`cf12ee73…91a2ff`.

| Step | Result |
|---|---|
| RED, on base `68d38333` before the adapter existed | DoD exit 1: unresolved `AndroidAppStorageEnvironment` |
| Baseline | DoD exit 0 (265 app JVM tests, 0 failures); both devices `[PROBE-OK]` 31/31 with `android-dirty=0`, APK `63e62c07…22ada6` |
| Control: unmodified APK with `-Expect A1.root.noBackup` (API 33) | script exit 1, receipt does not match |
| Mutation batch | 19/19 detected: D01–D14 built (exit 0) and matched their `-Expect` on both devices, each run printing the tree id above with `android-dirty=1`; J01–J05 compiled and failed the named test with `java.lang.AssertionError`; adapter restored to `54dd0218…` |
| Final, restored | DoD exit 0 (265 app JVM tests, 0 failures); both devices `[PROBE-OK]` 31/31 with `android-dirty=0`, APK `63e62c07…22ada6` |

Raw states on both devices: `external=mounted internal=unknown global=mounted root=mounted rootParent=unknown`.