# LocalSecretBox Android Keystore device probe

Device acceptance for `AndroidSecretKeys` (card `T1-LOCAL-DATA-SECURITY`). The JVM tests cover only the pure
seal-key decision and the fixed-message redaction; every AndroidKeyStore and UserManager call is checked by the debug
`LocalSecretBoxProbeActivity` on a real API 33 phone and an API 35 emulator, driving the production `LocalSecretBox`. A
green build is not device acceptance.

## What the probe checks

The probe runs once per launch against the installed candidate APK. It uses key version 201, so its alias
`myinspection.secret.backup-passphrase.v201` is not a production alias (production seals version 1), and keeps envelopes in memory, so no
production envelope file is touched. Expected values come from AndroidKeyStore, `KeyInfo` and `UserManager` read by
the probe itself. A failed check throws a `java.lang.AssertionError` named after the check and ends the run; any other
exception ends it as `ERROR`, which the host script never accepts.

| Group | Checks |
|---|---|
| pre, unlock | the probe alias is absent at start; the adapter's unlock state and `UserManager.isUserUnlocked` are both true (the app is not direct-boot aware, so it never runs before first unlock) |
| A5 round trip | seal returns STORED and open returns the sealed text |
| A4 key | the key is in AndroidKeyStore; `encoded` is null; `KeyInfo`: 256 bits, purposes exactly encrypt and decrypt, block modes exactly GCM, paddings exactly none, no user authentication; a caller-supplied GCM IV is refused with `InvalidAlgorithmParameterException` (randomized encryption) |
| A4 same key | an envelope from the first seal still opens after a second seal |
| A5 envelopes | two seals give different nonces and ciphertexts; a flipped bit in the nonce, the ciphertext and the tag each gives AUTHENTICATION_FAILED with the bytes unchanged; a 5-byte envelope gives ENVELOPE_CORRUPT with the bytes unchanged; deleting the alias gives KEY_MISSING with the envelope unchanged |
| A7 rebuild | a decrypt-only key planted at the alias is refused for encryption; seal replaces it (STORED), open succeeds, and the new key's purposes are encrypt and decrypt. An EC signing key planted at the alias makes open report KEY_UNUSABLE, and seal replaces it the same way |
| cleanup | the probe alias is gone |

`KeyInfo` on API 33 and 35 has no unlocked-device-required getter, so that property is shown by behavior instead:
the `LocalSecretBoxLockedProbeReceiver` check below opens an envelope while the screen is locked. Security level, secure-hardware
placement and `KeyguardManager.isDeviceLocked` are recorded on a `KEYINFO` line and never asserted, because hardware Keystore is not a
guarantee on every device (ADR-0006).

## Lock-screen check (emulator only)

A DUMP-gated debug receiver, run by the second script. With a temporary PIN set, `prepare` seals under key version
202 into a probe-owned file while the screen is unlocked; the screen is then turned off, which locks it; `open` must
find `KeyguardManager.isDeviceLocked` true and still open the envelope, the property the weekly background backup
depends on (ADR-0006 §3). The script clears the PIN and prints `[LOCKED-OK]` only after `locksettings verify` reports no credential; `cleanup` deletes the alias and the file. It runs only when
`ro.kernel.qemu=1`: locking a personal phone needs its owner present. Lock-screen removal, credential clearing and key
invalidation on the phone stay with `T7-SMOKE-POLISH` A7.

## Receipt and leftovers

The activity writes `files/secret-box-probe/<runId>.txt` in its own app data: `run=`, `apk=<sha256 of base.apk>`,
one `PASS <check>` per passed check with the `KEYINFO` line before `A4.keystore.callerIvRefused`, then
`DONE <runId>`, `FAIL <check> java.lang.AssertionError` or `ERROR <class>`. The receiver writes one receipt per
phase, `<runId>-<phase>.txt`. Receipts hold no key material, paths or plaintext; the scripts remove them. A process
killed mid-run can leave a probe alias (v201 or v202) or `locked-<runId>.envelope`; the next run deletes the alias
first. Both components are exported only to callers holding `android.permission.DUMP`, which `adb shell` holds.

## Host run

Build the candidate with the card DoD and save the first script as `.secrets\secret-probe.ps1`. It checks the APK's
application id and the installed app's signer, installs with `install -r` only, starts only the probe activity with a
fresh run id, reads and removes that run's receipt, force-stops the app and requires `pidof` to report no process. It
prints the device, the SHA-256 of the APK and of the three sources, the `android/` tree id of the checkout and the
number of `git status --porcelain` entries under `android/`, and exits 0 only when the receipt matches line by line
(`-Expect <check>`: the checks before it, then that check failing with `java.lang.AssertionError`).

```powershell
param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Apk, [string]$Expect = 'pass',
    [string]$Repo = (Get-Location).Path)
$ErrorActionPreference = 'Stop'
$package = 'nz.myinspection.app'
$checks = @(
    'pre.aliasAbsent', 'unlock.adapterMatchesUserManager', 'A5.roundTrip', 'A4.key.inAndroidKeyStore',
    'A4.key.notExportable', 'A4.key.size256', 'A4.key.purposesEncryptDecrypt', 'A4.key.gcmOnly', 'A4.key.noPadding',
    'A4.key.noUserAuthentication', 'A4.keystore.callerIvRefused', 'A4.sameKeyAcrossSeals', 'A5.freshNonceAndCiphertext',
    'A5.tamper.nonce', 'A5.tamper.ciphertext', 'A5.tamper.tag', 'A5.corruptEnvelope', 'A5.keyMissing',
    'A7.plantedKeyCannotEncrypt', 'A7.unusableKeyRebuiltOnSeal', 'A7.nonSecretEntryIsUnusable', 'A7.nonSecretEntryReplacedOnSeal',
    'cleanup.aliasRemoved')
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
if ($LASTEXITCODE -ne 0 -or -not $tree) { Fail 'android tree' }
$status = @(git -C $Repo status --porcelain -- android)
if ($LASTEXITCODE -ne 0) { Fail 'git status' }
$dirty = $status.Count
$identity = "model=$(Prop ro.product.model) sdk=$(Prop ro.build.version.sdk) type=$(Prop ro.build.type) " +
    "qemu=$(Prop ro.kernel.qemu) apk=$apkSha keys=$(Sha "$Repo/$platform/AndroidSecretKeys.kt") " +
    "box=$(Sha "$Repo/$platform/LocalSecretBox.kt") " +
    "probe=$(Sha "$Repo/android/app/src/debug/kotlin/nz/myinspection/app/platform/LocalSecretBoxProbeActivity.kt") " +
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
$receiptPath = "files/secret-box-probe/$runId.txt"
& $adb -s $Serial shell am start -W -n "$package/.platform.LocalSecretBoxProbeActivity" --es runId $runId | Out-Null
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
    if (Same $check 'A4.keystore.callerIvRefused') { $lines += '<KEYINFO>' }
    $lines += "PASS $check"
}
if (Same $Expect 'pass') { $lines += "DONE $runId" } else {
    $lines = @($lines[0..([array]::IndexOf($lines, "PASS $Expect") - 1)]) + "FAIL $Expect java.lang.AssertionError"
}
$ok = $receipt.Count -eq $lines.Count
for ($i = 0; $ok -and $i -lt $lines.Count; $i++) {
    $ok = if (Same $lines[$i] '<KEYINFO>') {
        $receipt[$i] -cmatch '^KEYINFO securityLevel=-?[0-9]+ insideSecureHardware=(true|false) deviceLocked=(true|false)$'
    } else { Same $receipt[$i] $lines[$i] }
}
if (-not $ok) { Fail "receipt does not match expect=$Expect" }
Write-Output "[PROBE-OK] expect=$Expect run=$runId $identity"
```

Save the second script as `.secrets\secret-probe-locked.ps1`; it requires the installed APK to be byte-identical to
the candidate, so run it after the first script on the emulator.

```powershell
param([Parameter(Mandatory)][string]$Serial, [Parameter(Mandatory)][string]$Apk, [string]$Expect = 'pass')
$ErrorActionPreference = 'Stop'
$package = 'nz.myinspection.app'
$pin = '4826'
$adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
function Fail([string]$why) { Write-Output "[LOCKED-FAIL] $why"; exit 1 }
function Same([string]$a, [string]$b) { [string]::Equals($a, $b, [StringComparison]::Ordinal) }
function Sha([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Prop([string]$name) { ((& $adb -s $Serial shell getprop $name) -join '').Trim() }
if (-not (Same (Prop ro.kernel.qemu) '1')) { Fail 'the locked check runs on the emulator only' }
if (-not (Same $Expect 'pass') -and -not (Same $Expect 'locked.open.whileScreenLocked')) { Fail "unknown check $Expect" }
$installed = (& $adb -s $Serial shell pm path $package) -replace '^package:', ''
if (-not $installed) { Fail 'candidate not installed; run secret-probe.ps1 first' }
$copy = Join-Path ([IO.Path]::GetTempPath()) "installed-$([guid]::NewGuid()).apk"
& $adb -s $Serial pull $installed $copy | Out-Null
$installedSha = Sha $copy
Remove-Item -LiteralPath $copy
if (-not (Same $installedSha (Sha $Apk))) { Fail 'installed APK is not the candidate' }
$runId = [guid]::NewGuid().ToString()
function Phase([string]$name, [string[]]$checks, [string]$failAt) {
    & $adb -s $Serial shell am broadcast -n "$package/.platform.LocalSecretBoxLockedProbeReceiver" --es runId $runId --es phase $name | Out-Null
    $path = "files/secret-box-probe/$runId-$name.txt"
    $receipt = $null
    foreach ($attempt in 1..20) {
        & $adb -s $Serial shell run-as $package ls $path *> $null
        if ($LASTEXITCODE -eq 0) { $receipt = @(& $adb -s $Serial shell run-as $package cat $path); break }
        Start-Sleep -Seconds 1
    }
    if (-not $receipt) { return $false }
    & $adb -s $Serial shell run-as $package rm -f $path
    if ($LASTEXITCODE -ne 0) { return $false }
    $receipt | ForEach-Object { Write-Host "  $_" }
    $lines = @("run=$runId phase=$name")
    foreach ($check in $checks) {
        if (Same $check $failAt) { $lines += "FAIL $check java.lang.AssertionError"; break }
        $lines += "PASS $check"
    }
    if (-not $failAt) { $lines += "DONE $runId" }
    [bool](($receipt.Count -eq $lines.Count) -and -not @(0..($lines.Count - 1) | Where-Object { -not (Same $receipt[$_] $lines[$_]) }))
}
$failAt = if (Same $Expect 'pass') { '' } else { $Expect }
$ok = $false
try {
    & $adb -s $Serial shell locksettings set-pin $pin | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail 'set-pin' }
    & $adb -s $Serial shell input keyevent KEYCODE_WAKEUP
    $prepared = Phase 'prepare' @('locked.pre.screenUnlocked', 'locked.prepare.sealed') ''
    & $adb -s $Serial shell input keyevent KEYCODE_POWER
    Start-Sleep -Seconds 3
    $opened = Phase 'open' @('locked.pre.screenLocked', 'locked.open.whileScreenLocked') $failAt
    $ok = $prepared -and $opened
} finally {
    & $adb -s $Serial shell locksettings clear --old $pin | Out-Null
    $clearExit = $LASTEXITCODE
    $verify = ((& $adb -s $Serial shell locksettings verify) -join '').Trim()
    & $adb -s $Serial shell input keyevent KEYCODE_WAKEUP
    & $adb -s $Serial shell wm dismiss-keyguard
    Start-Sleep -Seconds 1
    $cleaned = Phase 'cleanup' @('locked.cleanup.removed') ''
    & $adb -s $Serial shell am force-stop $package
    $pidProbe = @(& $adb -s $Serial shell "pidof $package; echo rc=`$?")
}
if ($clearExit -ne 0 -or -not (Same $verify 'Lock credential verified successfully')) { Fail 'the temporary PIN is still set' }
if (-not $cleaned) { Fail 'cleanup phase' }
if (-not (Same $pidProbe[-1] 'rc=1')) { Fail 'app process still running' }
if (-not $ok) { Fail "receipts do not match expect=$Expect" }
Write-Output "[LOCKED-OK] expect=$Expect run=$runId apk=$installedSha"
```

```powershell
cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
pwsh -NoProfile -File .secrets\secret-probe.ps1 -Serial <adb serial> -Apk android\app\build\outputs\apk\debug\app-debug.apk
pwsh -NoProfile -File .secrets\secret-probe-locked.ps1 -Serial emulator-5554 -Apk android\app\build\outputs\apk\debug\app-debug.apk
```

## Mutations

Each mutant changes `AndroidSecretKeys.kt`. A device mutant counts as detected only when `:app:assembleDebug` exits
0 and the named script exits 0 with `-Expect <check>` on the named devices. A JVM mutant counts when
`:app:compileDebugUnitTestKotlin` exits 0 and the named test fails with `java.lang.AssertionError`.

| Id | Change | Detected by |
|---|---|---|
| D01b | a software AES key instead of an AndroidKeyStore key | `A5.roundTrip`, both devices |
| D02 | key size 128 | `A4.key.size256`, both devices |
| D03 | block mode CBC | `A5.roundTrip`, both devices |
| D04 | padding PKCS7 | `A5.roundTrip`, both devices |
| D05 | user authentication required | `A5.roundTrip`, both devices |
| D06 | `setUnlockedDeviceRequired(true)` added | phone: `A5.roundTrip`; emulator: the first script passes and the lock-screen script fails at `locked.open.whileScreenLocked` |
| D07 | seal treats every existing key as unusable | `A4.sameKeyAcrossSeals`, both devices |
| D08 | seal treats the device as locked, so an unusable key is kept | `A7.unusableKeyRebuiltOnSeal`, both devices |
| D09 | randomized encryption not required | `A4.keystore.callerIvRefused`, both devices |
| D11 | `existingKey` returns null | `A5.roundTrip`, both devices |
| D12 | an entry that is not a secret key reads as missing | `A7.nonSecretEntryIsUnusable`, both devices |
| J01 | a locked device replaces an unusable key | `a key that cannot encrypt is replaced only while the device is unlocked` |
| J02 | an unlocked device keeps an unusable key | `a key that cannot encrypt is replaced only while the device is unlocked` |
| J03 | a key that can encrypt is replaced | `a missing seal key is created and a key that can encrypt is kept` |
| J04 | redaction keeps the cause | `keystore failures become one fixed message without a cause and an Error keeps its identity` |
| J05 | redaction copies the message | `keystore failures become one fixed message without a cause and an Error keeps its identity` |
| J06 | redaction catches `Throwable` | `keystore failures become one fixed message without a cause and an Error keeps its identity` |
| J07 | a load failure is rethrown while unlocked | `an entry that cannot be loaded is replaced only while the device is unlocked` |
| J08 | a load failure is replaced while locked | `an entry that cannot be loaded is replaced only while the device is unlocked` |
| J09 | `existingKey` without redaction | `the adapter redacts failures at each entry point and reports the unlock state it reads` |
| J10 | `keyForSeal` without redaction | `the adapter redacts failures at each entry point and reports the unlock state it reads` |
| J11 | `isUnlocked` without redaction | `the adapter redacts failures at each entry point and reports the unlock state it reads` |
| J12 | `isUnlocked` always true | `the adapter redacts failures at each entry point and reports the unlock state it reads` |
| J13 | `isUnlocked` always false | `the adapter redacts failures at each entry point and reports the unlock state it reads` |

Not counted, with the reason: D01 (`KeyGenerator` without the provider name) is equivalent, because Android selects
AndroidKeyStore from the `KeyGenParameterSpec`; its run passed every check on both devices. Passing `true` instead of
the unlock state to the seal decision is equivalent in practice, because the app is not direct-boot aware and the
state is always true while app code runs. Removing `doFinal()` from the usability check and `@Synchronized` from
`keyForSeal` change nothing a single-threaded probe can observe.

## Evidence

EVIDENCE-PENDING
