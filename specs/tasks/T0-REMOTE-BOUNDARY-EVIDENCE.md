---
id: T0-REMOTE-BOUNDARY-EVIDENCE
title: Publish independently verifiable remote Boundary evidence
status: merged
depends_on: [T1-STORAGE-PATH-BOUNDARY-REMOTE]
allow_paths:
  - specs/tasks/T0-REMOTE-BOUNDARY-EVIDENCE.md
acceptance:
  - "A1 Publish the actual PR310 portable lifecycle and complete saved-proof verifier: exact reviewed/merged production and test paths/blobs, three full 177-app/20-direct inventories, all 35 distinct compiling primary-AssertionError mutants, secondary exceptions, raw logs/exits/APKs, unchanged source restoration and final receipt-only test appendix."
  - "A2 Rehash all 1015 original proof leaves and both mandatory child manifests; bind actual PR/CI/T24/T35, two wrapper attempts versus one actual Sol/high PASS, first quota/no-output, original rounds=1, raw-file absence and guarded cleanup. Verify all 101 final saved XML suites: app177, core976 with four existing skips, and six E2E cases; no additional historical rerun is implied."
  - "A3 Only this active management card changes. Product alias status, complete archive/index, Board totals and product documents remain unchanged. Verify external whole-card approval, exact publication scope, card/index and whole-base/committed/working/staged whitespace. Normal remote review, CI, merge and cleanup remain required."
forbid:
  - Runtime, tests, scripts, configurations, dependencies or other metadata changes
  - Product status/count changes or claiming this evidence publication completes product R5
  - Missing raw proof, weakened acceptance, direct master push or skipped remote gates
non_goals:
  - Product closure, Android integration, POSIX execution or later-I/O guarantees
dod_command: $evidenceCardRaw=Get-Content 'specs/tasks/T0-REMOTE-BOUNDARY-EVIDENCE.md' -Raw; $c=[regex]::Matches($evidenceCardRaw,'(?ms)^```powershell\r?\n(.*?)^```[ \t]*$'); $p=[regex]::Matches($evidenceCardRaw,'(?ms)^```ps1\r?\n(.*?)^```[ \t]*$'); if($c.Count -ne 1 -or $p.Count -ne 1){throw 'Expected one evidence core and one publication guard'}; . ([scriptblock]::Create($c[0].Groups[1].Value)); & ([scriptblock]::Create($p[0].Groups[1].Value)); if($LASTEXITCODE -ne 0){exit 1}
dod_exit: 0
dod_assert: Complete original evidence core and publication-only scope/card/index/whitespace guard pass. Saved-record verification is not live GitHub attestation or a historical test rerun.
review_gate: codex {verdict:pass}
hygiene: Metadata SkipRed; preserve runtime 7, manifest 4 and lifecycle 11 negative controls. Recheck split publication scope and consumer pin negatives before formal ship.
doc_sync: Keep this card active and immutable for the dependent closure; target merged status becomes effective only on its own actual PR merge. This management card adds zero to the ten-product count.
---

# Remote Boundary evidence publication

This evidence publication carries the complete observed Boundary product proof in one active metadata card. Its reviewed target status takes effect only when this management PR merges. The later closure owns alias archival and product documentation; this publication changes neither the alias nor the product count. The earlier combined candidate measured 49,723 Git diff characters, so the evidence and closure are delivered in separate management PRs.

Copy the unchanged 1,015 original files and pinned outer manifest to the reviewer-visible ignored root named in the core. The product's first failed quota attempt remains preserved and distinct from its actual successful review. Four existing core file-symlink skips remain explicit; Windows Junction execution does not establish POSIX execution.

The unique `powershell` fence is the complete reusable evidence core. It reads this card's portable record through `$evidenceCardRaw` and exports its checked variables/functions in the caller scope. The separate `ps1` fence is used only by this card's own DoD to validate publication scope; consumers must not execute it. Both fences and this receipt are protected by the entire reviewed card blob/SHA after actual merge. No scripts or configuration files are added.

The publication base is the observed remote merge `6a8cce2f022c58812aff47c2e4c1f2f890c910de`. The independent whole-card SHA-256 is supplied at `approved-evidence-card.sha256` beside the copied proof in each review checkout; the executable guard compares it with this exact LF card. The candidate runs the complete proof core and the one-path publication guard. Formal R3, exact-head CI, this management PR merge and original-main guarded cleanup still require their own observed records. The later closure will bind those records without changing this card.

<!-- boundary-remote-lifecycle-receipt -->
```json
{
  "schema": "boundary-remote-r5-observed-v1",
  "id": "T1-STORAGE-PATH-BOUNDARY-REMOTE",
  "productPr": {
    "number": 310,
    "url": "https://github.com/Asun28/MyInspection/pull/310",
    "state": "MERGED",
    "reviewedHead": "bfefc1057a0ecbdfde4f747193c68c0069bdb73c",
    "mergeOid": "74aa9cb7ac6e70bbae30cb5d3a2024d8c95d6a0c",
    "mergedAt": "2026-09-17T23:20:41Z"
  },
  "formalR3": {
    "model": "gpt-5.6-sol",
    "effort": "high",
    "verdict": "pass",
    "wrapperAttempts": 2,
    "actualReviews": 1,
    "firstAttempt": "R3-NO-OUTPUT:usage_limit:no_code_verdict",
    "roundsFilePresent": true,
    "reasons": [],
    "rounds": 1,
    "rawTxtExists": false,
    "savedShipCommandsResetRounds": false
  },
  "candidateCI": {
    "run": 35174714509,
    "head": "bfefc1057a0ecbdfde4f747193c68c0069bdb73c",
    "jobs": [
      "verify",
      "required"
    ]
  },
  "cleanup": {
    "exit": 0,
    "worktreeAbsent": true,
    "branchAbsent": true,
    "endedUtc": "2026-09-17T23:34:57.6185122Z",
    "mergeTokenVerified": true
  },
  "sources": [
    {
      "path": "android/app/src/main/kotlin/nz/myinspection/app/platform/StoragePathBoundary.kt",
      "blob": "c4f29f5e6ed20a9813d6a3c9a59123c7f476e98e",
      "sha256": "41DCC7DFA834D3C04AF6EB2CF925B70FE5EF700C32EC739D0CEC54509204F3DB"
    },
    {
      "path": "android/app/src/test/kotlin/nz/myinspection/app/platform/StoragePathBoundaryTest.kt",
      "blob": "6e1c83b9d64a2fe1b7ccda554e08648984e4d046",
      "sha256": "46182BF6AD9B99260D432C988C83E7F03FB871E5B4E263CBCABEE1D831C67AC9"
    }
  ],
  "runtime": {
    "executionHead": "553d53382f3b663dac19ed1c607ffa35ee499d0c",
    "executionIdentity": "197eae03-37ab-4364-9590-62550dfa9111",
    "planSha256": "DF33D35DD04A71780378B2D29452F44D122AD50327866710D3E1687D9C8D9770",
    "productionSha256": "41DCC7DFA834D3C04AF6EB2CF925B70FE5EF700C32EC739D0CEC54509204F3DB",
    "preTailTestSha256": "6CDE4BE12DD76B26056E645F03EF5415DCDC04DE0A1F2DAE67997433F87B1202",
    "finalTestSha256": "46182BF6AD9B99260D432C988C83E7F03FB871E5B4E263CBCABEE1D831C67AC9",
    "stages": [
      {
        "name": "green",
        "suites": 9,
        "tests": 177,
        "direct": 20,
        "failures": 0,
        "errors": 0,
        "skips": 0,
        "testSha256": "6CDE4BE12DD76B26056E645F03EF5415DCDC04DE0A1F2DAE67997433F87B1202"
      },
      {
        "name": "restored-dod",
        "suites": 9,
        "tests": 177,
        "direct": 20,
        "failures": 0,
        "errors": 0,
        "skips": 0,
        "testSha256": "6CDE4BE12DD76B26056E645F03EF5415DCDC04DE0A1F2DAE67997433F87B1202"
      },
      {
        "name": "post-tail-dod",
        "suites": 9,
        "tests": 177,
        "direct": 20,
        "failures": 0,
        "errors": 0,
        "skips": 0,
        "testSha256": "46182BF6AD9B99260D432C988C83E7F03FB871E5B4E263CBCABEE1D831C67AC9"
      }
    ],
    "mutations": 35,
    "distinctMutants": 35,
    "primaryAssertionKills": 35,
    "assertionFailures": 73,
    "secondaryNonAssertionFailures": 4,
    "secondaryErrors": 0,
    "windowsJunctions": "EXECUTED_NO_SKIPS",
    "posix": "NOT_EXECUTED"
  },
  "proof": {
    "root": "_local/rotating-card-orchestrator/round2-r5-proof/boundary/original",
    "manifestSha256": "7E5B140C0A69F1C5613823E6DBDFB51DF1D113D4786A0F48F65B333F8CBAAFD9"
  },
  "evidence": {
    "cleanupAudit": "cleanup/audit.json",
    "cleanupResult": "cleanup/cleanup-result.json",
    "cleanupLog": "cleanup/cleanup.log",
    "childManifests": [
      {
        "root": "delivery",
        "path": "delivery/manifest.json",
        "sha256": "A0A7E8DE6CA68061E5CA99C504FEF0333931BFC026BDFE1B25AFA500BC7A4BA8"
      },
      {
        "root": "delivery/canonical-evidence/ship-attempt-01-preserved",
        "path": "delivery/canonical-evidence/ship-attempt-01-preserved/manifest.json",
        "sha256": "3D3FB6202E08F95B77747E0DA127D9D06D0F194C59D15807CB1B94D4857C6F8A"
      }
    ]
  }
}
```

```powershell
$productProofBase='74aa9cb7ac6e70bbae30cb5d3a2024d8c95d6a0c'
$manifestPin='7E5B140C0A69F1C5613823E6DBDFB51DF1D113D4786A0F48F65B333F8CBAAFD9'
$proof='_local/rotating-card-orchestrator/round2-r5-proof/boundary/original'
# Verify the copied original runtime evidence.
$ErrorActionPreference='Stop'
function Need([bool]$ok,[string]$why) { if (!$ok) { throw "[R5-PROOF] $why" } }
function Eq($a,$b,[string]$why) { Need ([string]::Equals([string]$a,[string]$b,[StringComparison]::Ordinal)) $why }
function Sha([string]$p) { (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash }
function J([string]$p) { Get-Content -LiteralPath $p -Raw | ConvertFrom-Json -Depth 100 -DateKind String }
function HText([string]$s) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($s))) }
function SetEq($actual,$expected,[string]$why) {
    $a=@($actual); $b=@($expected); Need ($a.Count -eq $b.Count) "$why count"
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($x in $a) { Need ($seen.Add([string]$x)) "$why duplicate" }
    $expectedSeen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($x in $b) { Need ($expectedSeen.Add([string]$x)) "$why expected duplicate"; Need ($seen.Contains([string]$x)) "$why missing $x" }
}
function Manifest([string]$folder,[string]$file,[string]$pin) {
    Eq (Sha $file) $pin 'manifest pin'; $m=J $file
    $names=@(); foreach($e in @($m.entries)) {
        $p=[string]$e.path; Need ($p -match '^[^:/\\]+(?:/[^:/\\]+)*$' -and $p -notmatch '(^|/)\.{1,2}(/|$)') 'manifest safe relative path'
        $f=Join-Path $folder $p; Need ((Get-Item -LiteralPath $f).Length -eq $e.bytes) "manifest length $p"
        Eq (Sha $f) $e.sha256 "manifest member $p"; $names+=$p
    }
    $leaves=@(Get-ChildItem -LiteralPath $folder -File -Recurse | Where-Object { $_.FullName -cne [IO.Path]::GetFullPath($file) } | ForEach-Object { [IO.Path]::GetRelativePath([IO.Path]::GetFullPath($folder),$_.FullName).Replace('\','/') })
    SetEq $names $leaves 'manifest complete leaves'
}
$rt=Join-Path $proof 'runtime'
$alias='T1-STORAGE-PATH-BOUNDARY-REMOTE'
$sourceRel='android/app/src/main/kotlin/nz/myinspection/app/platform/StoragePathBoundary.kt'
$testRel='android/app/src/test/kotlin/nz/myinspection/app/platform/StoragePathBoundaryTest.kt'
$prodPin='41DCC7DFA834D3C04AF6EB2CF925B70FE5EF700C32EC739D0CEC54509204F3DB'
$bodyPin='6CDE4BE12DD76B26056E645F03EF5415DCDC04DE0A1F2DAE67997433F87B1202'
$testPin='46182BF6AD9B99260D432C988C83E7F03FB871E5B4E263CBCABEE1D831C67AC9'
$planPin='DF33D35DD04A71780378B2D29452F44D122AD50327866710D3E1687D9C8D9770'
$executionHead='553d53382f3b663dac19ed1c607ffa35ee499d0c'
$executionId='197eae03-37ab-4364-9590-62550dfa9111'
$boundarySuite='nz.myinspection.app.platform.StoragePathBoundaryTest'
$plan=J "$rt/r4-plan.json"; Eq (Sha "$rt/r4-plan.json") $planPin 'approved mutation plan'
Eq $plan.head $executionHead 'execution HEAD'; Eq $plan.task $alias 'execution task'; Eq $plan.executionIdentity $executionId 'execution identity'
Eq $plan.productionSha256 $prodPin 'plan production'; Eq $plan.testSha256 $bodyPin 'plan pre-tail test'
Eq (Sha "$proof/active-alias.md") $plan.cardSHA256 'runtime contract'
function Identity($r) { Eq $r.task $alias 'record task'; Eq $r.head $executionHead 'record execution HEAD'; Eq $r.executionIdentity $executionId 'record execution ID'; Eq $r.planSHA256 $planPin 'record plan' }
function Timing($r) { Need ($null -ne $r.startedUtc -and $null -ne $r.endedUtc -and [DateTimeOffset]::Parse($r.endedUtc) -ge [DateTimeOffset]::Parse($r.startedUtc)) 'raw process timing' }
function Xml([string]$path,[bool]$green) {
    [xml]$x=Get-Content -LiteralPath $path -Raw; $s=$x.DocumentElement; Eq $s.LocalName 'testsuite' 'XML root'
    $cases=@($s.SelectNodes('./testcase')); Need ($cases.Count -gt 0) 'empty XML'
    foreach($field in @('tests','failures','errors','skipped')) { Need ($s.HasAttribute($field)) "XML $field absent" }
    $fails=@($s.SelectNodes('./testcase/failure')); $errors=@($s.SelectNodes('./testcase/error')); $skips=@($s.SelectNodes('./testcase/skipped'))
    Need ([int]$s.tests -eq $cases.Count -and [int]$s.failures -eq $fails.Count -and [int]$s.errors -eq $errors.Count -and [int]$s.skipped -eq $skips.Count -and $skips.Count -eq 0) 'XML counts/skips'
    $names=@($cases|ForEach-Object { Need (![string]::IsNullOrWhiteSpace($_.name)) 'case name'; Eq $_.classname $s.name 'case class'; [string]$_.name })
    SetEq $names $names 'unique testcases'
    if ([string]::Equals($s.name,$boundarySuite,[StringComparison]::Ordinal)) { Need ($cases.Count -eq 20) 'direct cases'; SetEq $names $plan.expectedBoundaryNames 'direct names' }
    if ($green) { Need ($fails.Count -eq 0 -and $errors.Count -eq 0) 'non-green XML' }
    return [pscustomobject]@{suite=$s;cases=$cases;failures=$fails;errors=$errors;names=$names}
}
function ExitRecord([string]$dir,[string]$phase,[int]$code,[string]$command) {
    $e=J "$dir/$phase-exit.json"; Need ($e.exit -eq $code) 'mutation process exit'; Timing $e; Eq $e.command $command 'mutation command'
    foreach($stream in @('stdout','stderr')) { Eq (Sha "$dir/$phase.$stream.log") $e."${stream}SHA256" "mutation $stream" }; return $e
}
$expectedSuites=@('nz.myinspection.app.feature.schedule.ReminderContractsTest','nz.myinspection.app.feature.schedule.ReminderDiagnosticsTest','nz.myinspection.app.feature.schedule.ReminderReceiptStoreTest','nz.myinspection.app.feature.schedule.ReminderSchedulerTest','nz.myinspection.app.feature.schedule.ReminderWorkerTest','nz.myinspection.app.feature.schedule.ScheduleUiTest','nz.myinspection.app.platform.SafeLogTest',$boundarySuite,'nz.myinspection.app.ui.theme.FieldLedgerThemeContractTest')
$stages=@(); $inventoryKeys=$null
foreach($stage in @('green','restored-dod','post-tail-dod')) {
    $dir="$rt/runs/$stage"; $s=J "$dir/receipt.json"; $e=J "$dir/exit.json"; Identity $s; Identity $e; Timing $e
    Eq $s.status 'PASSED' 'full DoD status'; Need ($s.exit -eq 0 -and $e.exit -eq 0 -and @($s.fixtureChildren).Count -eq 0) 'full DoD exit/fixtures'
    foreach($k in @('startedUtc','endedUtc','command')) { Eq $s.$k $e.$k "full DoD $k" }
    Eq $e.command 'cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache --rerun-tasks -q :app:testDebugUnitTest :app:assembleDebug' 'forced complete DoD command'
    foreach($stream in @('stdout','stderr')) { $h=Sha "$dir/$stream.log"; Eq $h $e."${stream}SHA256" 'DoD raw log'; Eq $h $s."${stream}SHA256" 'DoD receipt log' }
    $pin=if($stage -ceq 'post-tail-dod'){$testPin}else{$bodyPin}
    Eq (Sha "$dir/production.bytes") $prodPin 'DoD production bytes'; Eq $s.productionSHA256 $prodPin 'DoD production receipt'
    Eq (Sha "$dir/test.bytes") $pin 'DoD test bytes'; Eq $s.testSHA256 $pin 'DoD test receipt'
    Eq (Sha "$dir/app-debug.apk") $s.apkSHA256 'assembled APK'; Need ((Get-Item "$dir/app-debug.apk").Length -eq $s.apkBytes -and $s.apkBytes -gt 0) 'APK length'
    $pre=J "$dir/source-suite-preflight.json"; Eq $pre.head $executionHead 'suite preflight HEAD'; Eq $pre.status 'SOURCE_DISCOVERY_ONLY' 'suite preflight kind'; SetEq $pre.suites $expectedSuites 'suite preflight'
    $files=@(Get-ChildItem "$dir/xml" -File -Filter '*.xml'); SetEq @($files.BaseName|ForEach-Object {$_.Substring(5)}) $expectedSuites 'actual suite set'
    $keys=@(); $total=0; $records=@($s.inventory.suites); Need ($records.Count -eq 9) 'inventory suite count'
    foreach($file in $files) {
        $x=Xml $file.FullName $true; Eq $x.suite.name $file.BaseName.Substring(5) 'suite filename'
        Need ([DateTimeOffset]::Parse($x.suite.timestamp) -ge [DateTimeOffset]::Parse($e.startedUtc) -and [DateTimeOffset]::Parse($x.suite.timestamp) -le [DateTimeOffset]::Parse($e.endedUtc)) 'saved XML execution window'
        $rs=@($records|Where-Object {[string]::Equals($_.name,$x.suite.name,[StringComparison]::Ordinal)}); Need ($rs.Count -eq 1) 'inventory suite identity'; Eq $rs[0].file $file.Name 'inventory file'
        $caseKeys=@($x.cases|ForEach-Object { $_.classname+'|'+$_.name }); SetEq $caseKeys @($rs[0].testcases|ForEach-Object {$_.classname+'|'+$_.name}) 'raw case inventory'
        $keys+=$caseKeys; $total+=$x.cases.Count
    }
    Need ($total -eq 177 -and $s.inventory.testcaseCount -eq 177 -and $s.inventory.suiteCount -eq 9) 'full app totals'
    if($null -eq $inventoryKeys){$inventoryKeys=$keys}else{SetEq $keys $inventoryKeys 'restored complete inventory'}
    $diff=[IO.File]::ReadAllText("$dir/full.diff").Replace("`r`n","`n"); Need ($diff.Length -eq $s.fullDiffLfChars) 'stage diff chars'
    $parts=@([regex]::Split($diff,'(?m)(?=^diff --git )')|Where-Object {$_}); Need ($parts.Count -eq 2) 'stage diff scope'
    $paths=@(); $added=0; foreach($part in $parts) {
        $rel=[regex]::Match($part,'(?m)^\+\+\+ b/(.+)$').Groups[1].Value; $paths+=$rel
        Need ($part -match '(?m)^--- /dev/null$') 'expected new source'
        $lines=@($part -split "`n"|Where-Object {$_.StartsWith('+') -and !$_.StartsWith('+++')}|ForEach-Object {$_.Substring(1)}); $added+=$lines.Count
        $file=if($rel -ceq $sourceRel){'production.bytes'}elseif($rel -ceq $testRel){'test.bytes'}else{throw 'Unexpected source diff'}
        Eq (($lines -join "`n")+"`n") ([IO.File]::ReadAllText("$dir/$file")) 'stage diff actual bytes'
    }
    SetEq $paths @($sourceRel,$testRel) 'functional scope'; Need ($added -eq $s.changedLines -and $added -le 615 -and $diff.Length -le 36000) 'functional budget'
    $stages+= [ordered]@{name=$stage;suites=9;tests=177;direct=20;failures=0;errors=0;skips=0;testSha256=$pin}
}
$source=[IO.File]::ReadAllText("$rt/runs/green/production.bytes")
$body=[IO.File]::ReadAllText("$rt/runs/green/test.bytes"); $final=[IO.File]::ReadAllText("$rt/runs/post-tail-dod/test.bytes")
Need ($final.StartsWith($body,[StringComparison]::Ordinal)) 'final test body changed after mutations'
$tail=$final.Substring($body.Length); Need ($tail.TrimStart().StartsWith('/*',[StringComparison]::Ordinal) -and $tail.TrimEnd().EndsWith('*/',[StringComparison]::Ordinal) -and [regex]::Matches($tail,'\*/').Count -eq 1) 'receipt-only final test appendix'
$mutants=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal); $rows=@(); $assertions=0; $otherFailures=0; $secondaryErrors=0
Need (@($plan.mutations).Count -eq 35) 'plan mutation count'; SetEq @($plan.mutations.id) @(1..35|ForEach-Object {'N{0:00}' -f $_}) 'mutation ids'
foreach($m in $plan.mutations) {
    $dir="$rt/r4/$($m.id)"; $mr=J "$dir/receipt.json"; Identity $mr; Eq $mr.id $m.id 'mutation id'; Eq $mr.status 'KILLED' 'mutation status'
    Eq $mr.sourceSHA256 $prodPin 'mutation baseline'; Eq $mr.testSHA256 $bodyPin 'mutation test'; Eq $mr.restoredSHA256 $prodPin 'mutation restoration'; Eq $mr.primaryTest $m.primaryTest 'primary test'
    Need ($null -eq $mr.problem -and @($mr.fixtureChildren).Count -eq 0 -and $mr.compileExit -eq 0 -and $mr.testExit -eq 1) 'mutation result'
    foreach($k in @('kind','expectedMatches','before','after')) { Eq $mr.selector.$k $m.selector.$k 'selector binding' }
    Eq $m.source $sourceRel 'mutation source'; Eq $m.selector.kind 'exact_text' 'selector kind'
    Need ($m.selector.expectedMatches -eq 1 -and [regex]::Matches($source,[regex]::Escape($m.selector.before)).Count -eq 1 -and ![string]::Equals($m.selector.before,$m.selector.after,[StringComparison]::Ordinal)) 'selector unique change'
    $rebuilt=$source.Replace($m.selector.before,$m.selector.after); $sha=HText $rebuilt
    Eq $sha (Sha "$dir/mutant.bytes") 'rebuilt mutant bytes'; Eq $sha $mr.mutantSHA256 'actual mutant pin'; Need ($mutants.Add($sha)) 'duplicate mutant bytes'
    $compile=ExitRecord $dir 'compile' 0 $plan.compileCommand; $test=ExitRecord $dir 'test' 1 $plan.testCommand
    Need ([DateTimeOffset]::Parse($compile.endedUtc) -le [DateTimeOffset]::Parse($test.startedUtc)) 'compile precedes test'
    Eq (Sha "$dir/boundary.xml") $mr.xmlSHA256 'mutant raw XML'; $x=Xml "$dir/boundary.xml" $false; Eq $x.suite.name $boundarySuite 'mutant suite'
    Need ([DateTimeOffset]::Parse($x.suite.timestamp) -ge [DateTimeOffset]::Parse($test.startedUtc) -and [DateTimeOffset]::Parse($x.suite.timestamp) -le [DateTimeOffset]::Parse($test.endedUtc)) 'mutant fresh XML execution window'
    $primary=@($x.cases|Where-Object {[string]::Equals($_.name,$m.primaryTest,[StringComparison]::Ordinal)}); Need ($primary.Count -eq 1) 'primary case present'
    $fail=@($primary[0].SelectNodes('./failure')); Need ($fail.Count -eq 1) 'one primary failure'; Eq $fail[0].type 'java.lang.AssertionError' 'primary framework AssertionError'
    Eq $mr.primaryFailure.type $fail[0].type 'recorded failure type'; Eq $mr.primaryFailure.message $fail[0].message 'recorded failure message'; Eq $mr.primaryFailure.detail $fail[0].InnerText 'recorded stack'
    SetEq @($mr.historicalIds) @($m.historicalIds) 'historical mapping'
    foreach($failure in $x.failures) { if($failure.type -ceq 'java.lang.AssertionError'){$assertions++}else{$otherFailures++} }; $secondaryErrors+=$x.errors.Count
    $rows+=[ordered]@{id=$m.id;primary=$m.primaryTest;mutantSha256=$sha;compileExit=0;testExit=1;failureType='java.lang.AssertionError'}
}
Need ($mutants.Count -eq 35 -and $assertions -eq 73 -and $otherFailures -eq 4 -and $secondaryErrors -eq 0) 'primary and secondary failure totals'
$progress=J "$rt/r4/progress.json"; Identity $progress
Need ($progress.completed -eq 35 -and $progress.killed -eq 35 -and $progress.invalid -eq 0 -and $progress.survived -eq 0 -and $progress.sourcePinned -ceq $true) 'batch completion'; Eq $progress.restoredSHA256 $prodPin 'batch restored'
$cv=J "$rt/controller-final-validation.json"; Identity $cv; Eq $cv.status 'AUTHORIZED_HEAVY_VALIDATION_COMPLETE' 'controller runtime status'; Eq $cv.finalTestSHA256 $testPin 'controller final test'
Eq $cv.windowsJunctions 'EXECUTED_NO_SKIPS' 'Windows fixtures'; Eq $cv.posix 'NOT_EXECUTED' 'POSIX limit'
Eq (Sha "$rt/final-cached.diff") $cv.fullDiffSHA256 'final staged diff pin'; Eq ([IO.File]::ReadAllText("$rt/final-cached.diff")) ([IO.File]::ReadAllText("$rt/runs/post-tail-dod/full.diff")) 'actual staged full diff'
$red=J "$rt/red/official.red"; Eq $red.taskId $alias 'RED task'; Eq $red.sha $executionHead 'RED head'; Eq $red.phase 'red' 'RED phase'; Need ($red.dodExit -eq 1) 'RED nonzero DoD'
$redExit=J "$rt/red/exit.json"; Eq $redExit.head $executionHead 'RED command head'; Need ($redExit.exit -eq 0) 'official RED exit'; Timing $redExit
Eq (Sha "$rt/red/test.bytes") $bodyPin 'RED test body'; Need ((Get-Content "$rt/red/task-red.log" -Raw) -match 'BUILD FAILED' ) 'raw RED failure log'
$observed=[ordered]@{executionHead=$executionHead;executionIdentity=$executionId;planSha256=$planPin;productionSha256=$prodPin;preTailTestSha256=$bodyPin;finalTestSha256=$testPin;stages=$stages;mutations=35;distinctMutants=35;primaryAssertionKills=35;assertionFailures=73;secondaryNonAssertionFailures=4;secondaryErrors=0;windowsJunctions='EXECUTED_NO_SKIPS';posix='NOT_EXECUTED'}

Need (@((J "$proof/copy-manifest.json").entries).Count -eq 1015) 'original proof leaf count'
Manifest $proof "$proof/copy-manifest.json" $manifestPin
$evidenceReceipts=[regex]::Matches($evidenceCardRaw,'(?ms)^<!-- boundary-remote-lifecycle-receipt -->\r?\n```json\r?\n(.*?)\r?\n```[ \t]*$')
Need ($evidenceReceipts.Count -eq 1) 'one published portable lifecycle receipt'
$evidenceReceiptJson=$evidenceReceipts[0].Groups[1].Value
$r=$evidenceReceiptJson|ConvertFrom-Json -Depth 100 -DateKind String
# Caller supplies the parsed portable receipt $r and copied-original root $proof.
function P([string]$rel) { Need ($rel -match '^[^:/\\]+(?:/[^:/\\]+)*$' -and $rel -notmatch '(^|/)\.{1,2}(/|$)') 'safe proof reference'; Join-Path $proof $rel }
Eq $r.schema 'boundary-remote-r5-observed-v1' 'portable schema'; Eq $r.id $alias 'portable task'
Eq $r.proof.root '_local/rotating-card-orchestrator/round2-r5-proof/boundary/original' 'portable proof location'; Eq $r.proof.manifestSha256 $manifestPin 'portable final copy pin'
foreach($key in $observed.Keys) { Eq ($r.runtime.$key|ConvertTo-Json -Depth 30 -Compress) ($observed[$key]|ConvertTo-Json -Depth 30 -Compress) "portable runtime $key" }
$delivery='delivery/canonical-evidence'; $firstDir="$delivery/ship-attempt-01-preserved"; $secondDir="$delivery/ship-attempt-02"; $finalDir="$secondDir/final-evidence"
$pr=J (P "$finalDir/pr310-merged.json"); $ci=J (P "$finalDir/ci-run35174714509.json"); $jobs=J (P "$finalDir/ci-run35174714509-jobs.json")
Eq $r.productPr.number '310' 'approved PR'; Eq $r.productPr.reviewedHead 'bfefc1057a0ecbdfde4f747193c68c0069bdb73c' 'approved reviewed head'
Eq $r.productPr.mergeOid '74aa9cb7ac6e70bbae30cb5d3a2024d8c95d6a0c' 'approved merge'; Eq $r.candidateCI.run '35174714509' 'approved CI run'
Eq $pr.number $r.productPr.number 'original PR number'; Eq $pr.state 'MERGED' 'original PR state'; Eq $r.productPr.state $pr.state 'portable PR state'
foreach($pair in @(@($pr.headRefOid,$r.productPr.reviewedHead),@($pr.mergeCommit.oid,$r.productPr.mergeOid),@($pr.url,$r.productPr.url),@($pr.mergedAt,$r.productPr.mergedAt))) { Eq $pair[0] $pair[1] 'portable PR original binding' }
$actualReviews=0; $outcomes=@(); $shipLogs=@()
foreach($i in 0..1) {
    $dir=if($i -eq 0){$firstDir}else{$secondDir}; $logRel=if($i -eq 0){"$dir/ship/ship.log"}else{"$dir/ship.log"}
    $exitRel=if($i -eq 0){"$dir/ship/exit.json"}else{"$dir/exit.json"}; $reviewRel=if($i -eq 0){"$dir/review/$alias.json"}else{"$dir/final-evidence/review/$alias.json"}
    $ar=J (P $reviewRel); $e=J (P $exitRel); $log=Get-Content -LiteralPath (P $logRel) -Raw; $shipLogs+=$log
    Eq $ar.sha $pr.headRefOid 'attempt exact head'; Eq $ar.branch $alias 'attempt branch'; Timing $e
    Eq (Sha (P $logRel)) $e.logSHA256 'attempt raw log hash'; Need ($e.exit -eq (1-$i)) 'attempt process result'
    Eq $e.command "pwsh -NoProfile -File D:/Projects/MyInspection/scripts/task.ps1 -TaskId $alias -Phase ship -Base master" 'normal ship without ResetRounds/SkipRed/Local'
    # Prompt examples are not verdicts. Parse only the CLI codex -> JSON -> tokens used result section.
    $rawVerdicts=[regex]::Matches($log,'(?m)^codex\r?\n(\{[^\r\n]+\})\r?\ntokens used\r?$')
    if($i -eq 0) {
        Need ($rawVerdicts.Count -eq 0 -and @($ar.reasons).Count -eq 1 -and $ar.reasons[0] -match '^\[R3-NO-OUTPUT\]') 'first wrapper no actual verdict'
        Eq $ar.verdict 'block' 'first wrapper block'; Need ($log -match '(?m)^ERROR: You.ve hit your usage limit\.') 'first actual quota error'
        $outcomes+= 'R3-NO-OUTPUT:usage_limit:no_code_verdict'
    } else {
        Need ($rawVerdicts.Count -eq 1) 'one final actual reviewer verdict'; $v=$rawVerdicts[0].Groups[1].Value|ConvertFrom-Json -DateKind String
        Eq $v.verdict 'pass' 'raw actual PASS'; Need (@($v.reasons).Count -eq 0) 'raw actual reasons'; Eq $ar.verdict $v.verdict 'wrapper matches actual'; Need (@($ar.reasons).Count -eq 0) 'wrapper PASS reasons'
        Need ($log -match '(?m)^model: gpt-5\.6-sol\r?$' -and $log -match '(?m)^reasoning effort: high\r?$' -and $log -match '(?m)^Posted commit status codex-review=success\r?$') 'actual configured review and posted result'
        $actualReviews++; $outcomes+='pass'
    }
}
Need ($r.formalR3.wrapperAttempts -eq $shipLogs.Count -and $r.formalR3.actualReviews -eq $actualReviews -and $actualReviews -eq 1) 'derived wrapper and actual-review counts'
Eq $r.formalR3.firstAttempt $outcomes[0] 'portable first quota classification'; Eq $r.formalR3.verdict $outcomes[1] 'portable final actual verdict'
SetEq @($r.formalR3.reasons) @($ar.reasons) 'portable actual review reasons'
Eq $r.formalR3.model 'gpt-5.6-sol' 'formal model'; Eq $r.formalR3.effort 'high' 'formal effort'
foreach($rel in @("$firstDir/review/$alias.rounds","$finalDir/review/$alias.rounds")) { Eq ((Get-Content -LiteralPath (P $rel) -Raw).Trim()) '1' 'actual preserved rounds file' }
$firstManifest=J (P "$firstDir/manifest.json"); $summary=J (P "$delivery/remote-delivery-summary.json")
Need ($r.formalR3.roundsFilePresent -ceq $true -and $r.formalR3.rounds -eq 1 -and $r.formalR3.rawTxtExists -ceq $false -and $r.formalR3.savedShipCommandsResetRounds -ceq $false) 'portable rounds/raw/reset observations'
Need ($firstManifest.rawReplyFileExisted -ceq $false -and $summary.rawTxtExists -ceq $false) 'saved raw.txt absence'
Eq $firstManifest.rounds '1' 'first manifest rounds'; Eq $summary.roundsActual '1' 'final observed rounds'
Need (!(Test-Path -LiteralPath (P "$firstDir/review/$alias.raw.txt")) -and !(Test-Path -LiteralPath (P "$finalDir/review/$alias.raw.txt"))) 'copied review raw.txt absent'
$shipLog=$shipLogs[1]
foreach($mark in @('verify: PASS','T24-MERGETOKEN',"[CI-GATE-PASS] #310/$($pr.headRefOid) [required,verify]")) { Need ($shipLog.Contains($mark,[StringComparison]::Ordinal)) "formal ship log $mark" }
Eq $ci.id $r.candidateCI.run 'CI id'; Eq $ci.head_sha $pr.headRefOid 'CI exact head'; Eq $ci.event 'pull_request' 'CI event'; Eq $ci.status 'completed' 'CI completion'; Eq $ci.conclusion 'success' 'CI result'
Need ($jobs.total_count -eq 2 -and @($jobs.jobs).Count -eq 2) 'actual CI jobs'; SetEq @($jobs.jobs.name) @('verify','required') 'exact CI job set'
foreach($job in $jobs.jobs) { Eq $job.status 'completed' 'CI job completion'; Eq $job.conclusion 'success' 'CI job success'; Eq $job.head_sha $ci.head_sha 'CI job head'; Eq $job.run_id $ci.id 'CI job run' }
SetEq @($r.candidateCI.jobs) @('verify','required') 'portable jobs'; Eq $r.candidateCI.head $ci.head_sha 'portable CI head'
$t35=J (P "$finalDir/T35-watermark.json"); Eq $t35.taskId $alias 'T35 task'; Eq $t35.redSha $executionHead 'T35 RED'; Eq $t35.commitSha $pr.headRefOid 'T35 committed head'
$token=Get-Content -LiteralPath (P "$finalDir/T24-merge-token.txt") -Raw
Need ([regex]::Matches($token,'(?m)^tip='+[regex]::Escape($pr.headRefOid)+'\r?$').Count -eq 1 -and [regex]::Matches($token,'(?m)^merged_pr=#310\r?$').Count -eq 1) 'T24 actual PR and head'
$requiredChildren=@('delivery/manifest.json',"$firstDir/manifest.json")
SetEq @($r.evidence.childManifests.path) $requiredChildren 'required original child manifests'
foreach($child in @($r.evidence.childManifests)) {
    $isBundle=$child.path -ceq 'delivery/manifest.json'; $root=if($isBundle){'delivery'}else{$firstDir}
    $pin=if($isBundle){'A0A7E8DE6CA68061E5CA99C504FEF0333931BFC026BDFE1B25AFA500BC7A4BA8'}else{'3D3FB6202E08F95B77747E0DA127D9D06D0F194C59D15807CB1B94D4857C6F8A'}
    Eq $child.root $root 'child manifest root'; Eq $child.sha256 $pin 'approved child manifest pin'; Eq (Sha (P $child.path)) $pin 'original child manifest bytes'
    $cm=J (P $child.path); $members=@($cm.files); Need ($members.Count -eq $(if($isBundle){588}else{11})) 'original manifest member count'
    $listed=@(); foreach($member in $members) {
        $rel=([string]$member.path).Replace('\','/'); $file=P "$root/$rel"; $length=if($isBundle){$member.length}else{$member.bytes}
        Need ((Get-Item -LiteralPath $file).Length -eq $length) 'child member length'; Eq (Sha $file) $member.sha256 'child member SHA'; $listed+=$rel
    }
    $childRoot=P $root; $actual=@(Get-ChildItem -LiteralPath $childRoot -File -Recurse|ForEach-Object {[IO.Path]::GetRelativePath([IO.Path]::GetFullPath($childRoot),$_.FullName).Replace('\','/')}|Where-Object {$_ -cne 'manifest.json'})
    SetEq $listed $actual 'complete original child manifest'
}
SetEq @($r.sources.path) @($sourceRel,$testRel) 'exact distinct source paths'
foreach($pin in $r.sources) {
    $expected=if($pin.path -ceq $sourceRel){$prodPin}else{$testPin}; Eq $pin.sha256 $expected 'portable source pin'
    foreach($commit in @($pr.headRefOid,$pr.mergeCommit.oid)) { $blob=git rev-parse ('{0}:{1}' -f $commit,$pin.path); Need ($LASTEXITCODE -eq 0) 'merged source lookup'; Eq $blob $pin.blob 'reviewed and merged source identity' }
    $copy=if($pin.path -ceq $sourceRel){"$rt/runs/post-tail-dod/production.bytes"}else{"$rt/runs/post-tail-dod/test.bytes"}
    $blob=git hash-object --no-filters -- $copy; Need ($LASTEXITCODE -eq 0) 'read-only blob hash'; Eq $blob $pin.blob 'copied source bytes to Git blob'
}
git merge-base --is-ancestor $executionHead $pr.headRefOid; Need ($LASTEXITCODE -eq 0) 'execution ancestor of reviewed head'

$audit=J (P $r.evidence.cleanupAudit); $cleanup=J (P $r.evidence.cleanupResult)
foreach($value in @($audit,$cleanup)) { Eq $value.head $pr.headRefOid 'cleanup reviewed head'; Eq $value.merge $pr.mergeCommit.oid 'cleanup merged object' }
Need ($audit.worktreeClean -ceq $true -and $audit.mergeTokenVerified -ceq $true -and $cleanup.exit -eq 0 -and $cleanup.worktreeAbsent -ceq $true -and $cleanup.branchAbsent -ceq $true) 'guarded cleanup observations'
Eq $r.cleanup.exit $cleanup.exit 'portable cleanup exit'; Eq $r.cleanup.worktreeAbsent $cleanup.worktreeAbsent 'portable worktree absent'; Eq $r.cleanup.branchAbsent $cleanup.branchAbsent 'portable branch absent'
Eq $r.cleanup.endedUtc $cleanup.endedUtc 'portable cleanup time'
Need ($r.cleanup.mergeTokenVerified -ceq $audit.mergeTokenVerified -and $r.cleanup.mergeTokenVerified -ceq $true) 'portable actual merge-token audit'
Need ($audit.wrapperAttempts -eq 2 -and $audit.actualReviewerVerdicts -eq 1 -and $audit.rounds -eq 1 -and $audit.ResetRounds -ceq $false -and $audit.rawTxtPresent -ceq $false -and $audit.T35Present -ceq $true -and $audit.SkipRed -ceq $false) 'independent review/RED audit'
$cleanupLog=Get-Content -LiteralPath (P $r.evidence.cleanupLog) -Raw; Need ($cleanupLog -match 'T24-MERGETOKEN' -and $cleanupLog -notmatch 'T24-MERGETOKEN[^\r\n]*-Force') 'guarded cleanup raw log'
git merge-base --is-ancestor $pr.mergeCommit.oid $productProofBase; Need ($LASTEXITCODE -eq 0) 'product merged before closure base'

# Final saved outputs are checked separately; this does not claim an additional forced run.
$xmlInventory=J (P "$finalDir/final-xml-inventory.json")
SetEq @($xmlInventory.kind) @('app','core','core-e2e') 'final saved XML groups'
$finalXmlCount=0
foreach($group in @('app','core','core-e2e')) {
    $expected=if($group -ceq 'app'){@(9,177,0)}elseif($group -ceq 'core'){@(89,976,4)}else{@(3,6,0)}
    $files=@(Get-ChildItem -LiteralPath (P "$finalDir/$group-final-xml") -File -Filter '*.xml')
    Need ($files.Count -eq $expected[0]) 'final XML suite count'; $total=0; $skipKeys=@(); $caseKeys=@()
    foreach($file in $files) {
        [xml]$doc=Get-Content -LiteralPath $file.FullName -Raw; $suite=$doc.DocumentElement
        Eq $suite.LocalName 'testsuite' 'final XML root'; $cases=@($suite.SelectNodes('./testcase')); $f=@($suite.SelectNodes('./testcase/failure')); $e=@($suite.SelectNodes('./testcase/error')); $s=@($suite.SelectNodes('./testcase/skipped'))
        Need ($cases.Count -gt 0 -and [int]$suite.tests -eq $cases.Count -and [int]$suite.failures -eq $f.Count -and [int]$suite.errors -eq $e.Count -and [int]$suite.skipped -eq $s.Count -and $f.Count -eq 0 -and $e.Count -eq 0) 'final XML actual nodes'
        foreach($case in $cases) { Need (![string]::IsNullOrWhiteSpace($case.name)) 'final case identity'; Eq $case.classname $suite.name 'final case class'; $caseKeys+=$case.classname+'|'+$case.name }
        foreach($skipped in $s){$skipKeys+=$file.Name+'|'+$skipped.ParentNode.name+'|'+$skipped.message}; $total+=$cases.Count
    }
    SetEq $caseKeys $caseKeys 'final unique case inventory'; Need ($total -eq $expected[1] -and $skipKeys.Count -eq $expected[2]) 'final node totals'
    $record=@($xmlInventory|Where-Object {$_.kind -ceq $group}); Need ($record.Count -eq 1 -and $record[0].xmlFiles -eq $files.Count -and $record[0].testcases -eq $total -and $record[0].failures -eq 0 -and $record[0].errors -eq 0 -and $record[0].skipped -eq $skipKeys.Count) 'final saved inventory totals'
    SetEq $skipKeys @($record[0].skippedCases|ForEach-Object {$_.file+'|'+$_.name+'|'+$_.message}) 'final named existing skips'
    $auditGroup=if($group -ceq 'core-e2e'){'e2e'}else{$group}
    Need ($audit.finalXml.$auditGroup.suites -eq $files.Count -and $audit.finalXml.$auditGroup.tests -eq $total -and $audit.finalXml.$auditGroup.skips -eq $skipKeys.Count) 'independent final XML audit'
    $finalXmlCount+=$files.Count
}
Need ($finalXmlCount -eq 101 -and $audit.finalXmlFiles -eq 101) 'complete final saved XML inventory'

Write-Output '[BOUNDARY-EVIDENCE-CORE-PASS] Original runtime, lifecycle, cleanup and final saved XML verified.'

```

```ps1
$publicationBase='6a8cce2f022c58812aff47c2e4c1f2f890c910de'
$publicationPath='specs/tasks/T0-REMOTE-BOUNDARY-EVIDENCE.md'
Need ($publicationBase -match '^[0-9a-f]{40}$') 'actual publication base unresolved'
$approved=(Get-Content '_local/rotating-card-orchestrator/round2-r5-proof/boundary/approved-evidence-card.sha256' -Raw).Trim()
Need ($approved -match '^[0-9A-F]{64}$') 'approved publication pin'; Eq (HText $evidenceCardRaw.Replace("`r`n","`n")) $approved 'approved whole evidence card'
Need ($evidenceCardRaw -cmatch '(?m)^status: merged\r?$') 'publication reviewed target status'
$paths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($ga in @(@('diff','--no-renames','--name-only',$publicationBase,'--'),@('diff','--no-renames','--name-only',"$publicationBase...HEAD",'--'),@('diff','--cached','--no-renames','--name-only','--'),@('ls-files','--others','--exclude-standard'))) {
    $found=@(& git @ga); Need ($LASTEXITCODE -eq 0) 'publication scope query'; foreach($p in $found){if($p){[void]$paths.Add($p)}}
}
SetEq @($paths) @($publicationPath) 'publication changes only its own card'
pwsh -NoProfile -File scripts/check-cards.ps1; Need ($LASTEXITCODE -eq 0) 'publication card validation'
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; Need ($LASTEXITCODE -eq 0) 'publication unchanged archive index'
git merge-base --is-ancestor $publicationBase HEAD; Need ($LASTEXITCODE -eq 0) 'publication base ancestor'
git merge-base --is-ancestor $pr.mergeCommit.oid $publicationBase; Need ($LASTEXITCODE -eq 0) 'actual product merge precedes publication base'
git diff --check $publicationBase; Need ($LASTEXITCODE -eq 0) 'publication whole-base whitespace'
git diff --check "$publicationBase...HEAD"; Need ($LASTEXITCODE -eq 0) 'publication committed whitespace'
git diff --check; Need ($LASTEXITCODE -eq 0) 'publication working whitespace'
git diff --cached --check; Need ($LASTEXITCODE -eq 0) 'publication staged whitespace'
Write-Output '[BOUNDARY-EVIDENCE-PUBLICATION-PASS] Original evidence checked; product status and counts remain unchanged.'

```
