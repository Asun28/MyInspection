#requires -Version 7
[CmdletBinding()]
param([switch]$SelfCheck, [switch]$FrozenOwnerOnly)

. (Join-Path $PSScriptRoot '_cards.ps1')

function Get-ScaffoldReviewPolicy {
  param([string]$CardText, [bool]$BaselineAuthorized, [object]$FrozenPaths,
        [string[]]$RawDiff, [string]$CardPath, [string]$HeadCardText)
  if (-not $BaselineAuthorized -or $null -eq $FrozenPaths -or -not $RawDiff) { return 'blocking' }
  $fm = Get-FrontMatter $CardText
  if (-not $fm) { return 'blocking' }
  $gates = [regex]::Matches($fm, '(?im)^[ \t]*[''"]?review_gate[''"]?[ \t]*:[^\r\n]*')
  if ($gates.Count -ne 1 -or $gates[0].Value -cnotmatch '^review_gate:[ \t]+advisory[ \t]*$') { return 'blocking' }
  try { foreach ($pattern in $FrozenPaths) { if ($pattern -isnot [string]) { return 'blocking' }; [void][regex]::new($pattern) } }
  catch { return 'blocking' }
  foreach ($line in $RawDiff) {
    $entry = [regex]::Match($line, '^:(?<old>000000|100644) (?<new>000000|100644) [0-9a-f]{40} [0-9a-f]{40} [AMD]\t(?<path>[^\t\r\n]+)$')
    if (-not $entry.Success) { return 'blocking' }
    $path = $entry.Groups['path'].Value
    foreach ($pattern in $FrozenPaths) { if ($path -match $pattern) { return 'blocking' } }
    if ($path -ceq $CardPath) {
      if ($entry.Groups['old'].Value -ne '100644' -or $entry.Groups['new'].Value -ne '100644') { return 'blocking' }
      $statusPattern = '(?m)^status:[ \t]*(?<value>todo|in-progress|in-review|merged)[ \t]*\r?$'
      $baseStatus = [regex]::Matches((Get-FrontMatter $CardText), $statusPattern)
      $headFm = Get-FrontMatter $HeadCardText
      if (-not $headFm) { return 'blocking' }
      $headStatus = [regex]::Matches($headFm, $statusPattern)
      if ($baseStatus.Count -ne 1 -or $headStatus.Count -ne 1) { return 'blocking' }
      if ($baseStatus[0].Groups['value'].Value -ceq $headStatus[0].Groups['value'].Value) { return 'blocking' }
      # Compare the complete card; only the one frontmatter status scalar may differ.
      $baseOffset = $CardText.IndexOf($fm, [StringComparison]::Ordinal) + $baseStatus[0].Groups['value'].Index
      $headOffset = $HeadCardText.IndexOf($headFm, [StringComparison]::Ordinal) + $headStatus[0].Groups['value'].Index
      $baseNormalized = $CardText.Remove($baseOffset, $baseStatus[0].Groups['value'].Length).Insert($baseOffset, 'bookkeeping')
      $headNormalized = $HeadCardText.Remove($headOffset, $headStatus[0].Groups['value'].Length).Insert($headOffset, 'bookkeeping')
      if ($baseNormalized -cne $headNormalized) { return 'blocking' }
      continue
    }
    if ($path -cne 'docs/research/property-inspect.md') { return 'blocking' }
  }
  return 'advisory'
}

if (-not $SelfCheck) { return }
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$policyRoot = Join-Path ([IO.Path]::GetTempPath()) "review-policy-$PID-$([guid]::NewGuid().ToString('N'))"
$policyFailures = [Collections.Generic.List[string]]::new()
$savedPath = $env:PATH; $savedExt = $env:PATHEXT; $savedMode = $env:REVIEW_POLICY_TEST_MODE
function Assert-ReviewPolicyCheck([bool]$Condition, [string]$Name) {
  if (-not $Condition) { $policyFailures.Add($Name); Write-Host "REVIEW-POLICY-FAIL: $Name" }
}
function Invoke-ReviewPolicyCase {
  param([string]$Name, [string]$Gate = 'advisory', [string]$Path = 'docs/research/property-inspect.md',
        [string]$Mode = 'block', [string]$CardChange = '', [string]$Frozen = "@('docs/frozen/')", [switch]$Rename, [string]$ExistingRoot, [string]$ConfigText, [switch]$ExistingDocument, [switch]$Mixed)
  $caseRoot = if ($ExistingRoot) { $ExistingRoot } else { Join-Path $policyRoot $Name }
  $cardPath = 'specs/tasks/T9-REVIEW-POLICY.md'
  if (-not $ExistingRoot) {
  New-Item -ItemType Directory -Force $caseRoot, (Join-Path $caseRoot 'docs'), (Join-Path $caseRoot 'scripts'), (Join-Path $caseRoot 'specs/tasks') | Out-Null
  & git -C $caseRoot init -q -b master
  $card = "---`nid: T9-REVIEW-POLICY`nstatus: todo`nreview_gate: $Gate`n---`n# approved card`n"
  [IO.File]::WriteAllText((Join-Path $caseRoot $cardPath), $card)
  Set-Content -LiteralPath (Join-Path $caseRoot 'docs/QUALITY-RUBRIC.md') -Value '# fixture rubric'
  $baselineConfig = if ($ConfigText) { $ConfigText } else { '$script:ScaffoldConfig = @{ FrozenPaths = ' + $Frozen + ' }' }
  Set-Content -LiteralPath (Join-Path $caseRoot 'scripts/_config.ps1') -Value $baselineConfig
  if ($ExistingDocument) {
    New-Item -ItemType Directory -Force (Split-Path (Join-Path $caseRoot $Path) -Parent) | Out-Null
    Set-Content -LiteralPath (Join-Path $caseRoot $Path) -Value 'baseline document'
  }
  if ($Rename) { Set-Content -LiteralPath (Join-Path $caseRoot 'scripts/critical.ps1') -Value '# rename source' }
  & git -C $caseRoot -c core.autocrlf=false add -A
  & git -C $caseRoot -c user.email=policy@test.invalid -c user.name=policy-test commit -q -m baseline
  & git -C $caseRoot switch -q -c T9-REVIEW-POLICY
  New-Item -ItemType Directory -Force (Split-Path (Join-Path $caseRoot $Path) -Parent) | Out-Null
  if ($Rename) { & git -C $caseRoot mv scripts/critical.ps1 $Path }
  else { Set-Content -LiteralPath (Join-Path $caseRoot $Path) -Value 'ordinary explanation' }
  if ($Mixed) { Set-Content (Join-Path $caseRoot 'scripts/tool.ps1') '# source' }
  if ($CardChange) {
    $headCard = switch ($CardChange) {
      status { $card.Replace('status: todo', 'status: merged') }
      status-inner-whitespace { $card.Replace('status: todo', 'status: merged   ') }
      outer-whitespace { "$card`n" }
      outer-whitespace-status { $card.Replace('status: todo', 'status: merged') + "`n" }
      unchanged-status { $card.Replace('status: todo', 'status: todo ') }
      content { $card.Replace('# approved card', '# changed approval') }
      optin { $card.Replace('review_gate: blocking', 'review_gate: advisory') }
      delete { '' }
    }
    if ($CardChange -eq 'delete') { Remove-Item -LiteralPath (Join-Path $caseRoot $cardPath) }
    else { [IO.File]::WriteAllText((Join-Path $caseRoot $cardPath), $headCard) }
  }
  & git -C $caseRoot -c core.autocrlf=false add -A
  & git -C $caseRoot -c user.email=policy@test.invalid -c user.name=policy-test commit -q -m candidate
  } else {
    if (-not ([IO.Path]::GetFullPath($caseRoot).StartsWith($policyRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase))) { throw 'fixture root escaped' }
    Remove-Item -LiteralPath (Join-Path $caseRoot '.review') -Recurse -Force -ErrorAction SilentlyContinue
  }
  $env:REVIEW_POLICY_TEST_MODE = $Mode
  $caseTimeout = if ($Mode -eq 'timeout') { 2 } else { 30 }
  $reviewOutput = (& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'review.ps1') -WorktreePath $caseRoot -Base master -LocalBase -TimeoutSec $caseTimeout 2>&1 | Out-String)
  $reviewExit = $LASTEXITCODE
  $verdictFile = Join-Path $caseRoot '.review/T9-REVIEW-POLICY.json'
  $verdict = $null
  if (Test-Path -LiteralPath $verdictFile -PathType Leaf) { $verdict = Get-Content -LiteralPath $verdictFile -Raw | ConvertFrom-Json }
  [pscustomobject]@{ Exit=$reviewExit; Output=$reviewOutput; Verdict=$verdict; Root=$caseRoot; Rounds=(Test-Path -LiteralPath (Join-Path $caseRoot '.review/T9-REVIEW-POLICY.rounds')) }
}
try {
  $stubDir = Join-Path $policyRoot 'bin'
  New-Item -ItemType Directory -Force $stubDir | Out-Null
  $stubFile = Join-Path $stubDir 'codex.ps1'
  @'
[Console]::In.ReadToEnd() | Out-Null
$payload = @{ verdict='block'; reasons=@('Fixture finding preserved') }
switch -Wildcard ($env:REVIEW_POLICY_TEST_MODE) {
  duplicate-* {
    $key = $env:REVIEW_POLICY_TEST_MODE.Substring(10)
    $payload = @{ verdict='pass'; reasons=@(); sha=(& git -C $env:REVIEW_WT rev-parse HEAD).Trim(); branch=(& git -C $env:REVIEW_WT branch --show-current).Trim() }
    $json = $payload | ConvertTo-Json -Compress
    Set-Content $env:REVIEW_OUT ('{"' + $key + '":null,' + $json.Substring(1)); exit 0
  }
  pass { $payload = @{ verdict='pass'; reasons=@() } }
  Verdict { $payload = @{ Verdict='pass'; reasons=@() } }
  Reasons { $payload = @{ verdict='pass'; Reasons=@() } }
  SHA { $payload = @{ verdict='pass'; reasons=@(); SHA=(& git -C $env:REVIEW_WT rev-parse HEAD).Trim() } }
  Branch { $payload = @{ verdict='pass'; reasons=@(); Branch=(& git -C $env:REVIEW_WT branch --show-current).Trim() } }
  identity { $payload = @{ verdict='pass'; reasons=@(); sha=(& git -C $env:REVIEW_WT rev-parse HEAD).Trim(); branch=(& git -C $env:REVIEW_WT branch --show-current).Trim() } }
  json-comment { Set-Content $env:REVIEW_OUT '{"verdict":"block",/* comment */"reasons":["Fixture finding preserved"]}'; exit 0 }
  json-single-quote { Set-Content $env:REVIEW_OUT "{'verdict':'block','reasons':['Fixture finding preserved']}"; exit 0 }
  json-unquoted-property { Set-Content $env:REVIEW_OUT '{verdict:"block",reasons:["Fixture finding preserved"]}'; exit 0 }
  json-trailing-comma { Set-Content $env:REVIEW_OUT '{"verdict":"block","reasons":["Fixture finding preserved"],}'; exit 0 }
  wrapped-pass { Set-Content $env:REVIEW_OUT '[{"verdict":"pass","reasons":[]}]'; exit 0 }
  wrapped-block { Set-Content $env:REVIEW_OUT '  [{"verdict":"block","reasons":["Wrapped finding"]}]'; exit 0 }
  extra-field { $payload.extra = 'unexpected' }
  blank-reason { $payload.reasons = @(' ') }
  scalar-reason { $payload.reasons = 'Finding' }
  nonstring-reason { $payload.reasons = @('Finding', 42) }
  pass-with-reasons { $payload = @{ verdict='pass'; reasons=@('Unexpected finding') } }
  block-without-reasons { $payload = @{ verdict='block'; reasons=@() } }
  nonstring-sha { $payload.sha = 42 }
  nonstring-branch { $payload.branch = 42 }
  lowercase-branch { $payload.branch = (& git -C $env:REVIEW_WT branch --show-current).Trim().ToLowerInvariant() }
  empty { exit 0 }
  malformed { Set-Content $env:REVIEW_OUT '{"verdict":'; exit 0 }
  badreasons { $payload.reasons = 42 }
  stale { $payload.sha = '0000000000000000000000000000000000000000' }
  timeout { Start-Sleep -Seconds 10; exit 0 }
}
$payload | ConvertTo-Json -Compress | Set-Content $env:REVIEW_OUT
if ($env:REVIEW_POLICY_TEST_MODE -eq 'writefail') { (Get-Item -LiteralPath $env:REVIEW_OUT).IsReadOnly = $true }
if ($env:REVIEW_POLICY_TEST_MODE -eq 'nonzero') { exit 9 }
exit 0
'@ | Set-Content -LiteralPath $stubFile -Encoding utf8
  $stubShell = Join-Path $stubDir 'codex'
  [IO.File]::WriteAllText($stubShell, "#!/bin/sh`nexec pwsh -NoProfile -File '$($stubFile.Replace('\','/'))' `"`$@`"`n")
  if (-not $IsWindows) { & chmod +x $stubShell }
  $env:PATH = "$stubDir$([IO.Path]::PathSeparator)$savedPath"
  if ($IsWindows) { $env:PATHEXT = ".PS1;$savedExt" }
  foreach ($case in @(
    @{Name='wrong-frozen-owner'; ConfigText='$other = @{ FrozenPaths = @() }; $script:ScaffoldConfig = @{}'},
    @{Name='nested-frozen-decoy'; ConfigText='$script:ScaffoldConfig = @{ Other = @{ FrozenPaths = @() } }'}
  )) {
    $result = Invoke-ReviewPolicyCase @case
    Assert-ReviewPolicyCheck ($result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: blocking')) "$($case.Name) cannot authorize advisory"
  }
  if ($FrozenOwnerOnly) {
    if ($policyFailures.Count) { exit 1 }
    Write-Host 'REVIEW-POLICY-FROZEN-OWNER-PASS'; exit 0
  }
  $advisory = Invoke-ReviewPolicyCase 'advisory' -CardChange status
  Assert-ReviewPolicyCheck ($advisory.Exit -eq 0) 'baseline advisory plus status-only bookkeeping accepts valid block'
  Assert-ReviewPolicyCheck ($null -ne $advisory.Verdict -and $advisory.Verdict.verdict -ceq 'block' -and $advisory.Verdict.reasons[0] -ceq 'Fixture finding preserved') 'actual negative verdict and finding persist'
  Assert-ReviewPolicyCheck (-not $advisory.Rounds -and $advisory.Output.Contains('R3 advisory findings:')) 'advisory findings explicit without consuming block rounds'
  foreach ($cardChange in @('outer-whitespace', 'outer-whitespace-status', 'unchanged-status', 'status-inner-whitespace')) {
    $result = Invoke-ReviewPolicyCase "card-$cardChange" -CardChange $cardChange
    Assert-ReviewPolicyCheck ($result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: blocking') -and -not $result.Output.Contains('R3 advisory findings:')) "advisory rejects non-status-only card edit: $cardChange"
  }
  $pass = Invoke-ReviewPolicyCase 'pass' -Mode pass -ExistingRoot $advisory.Root
  Assert-ReviewPolicyCheck ($pass.Exit -eq 0 -and $pass.Verdict.verdict -ceq 'pass') 'ordinary advisory pass accepted'
  $mixed = Invoke-ReviewPolicyCase 'mixed' -Mixed
  Assert-ReviewPolicyCheck ($mixed.Exit -ne 0 -and $mixed.Output.Contains('Review gate policy: blocking')) 'allowlisted document plus source remains blocking'
  $identity = Invoke-ReviewPolicyCase 'identity' -Mode identity -ExistingRoot $advisory.Root
  Assert-ReviewPolicyCheck ($identity.Exit -eq 0 -and $identity.Verdict.verdict -ceq 'pass') 'exact lowercase optional identity fields accepted'
  foreach ($mode in @(
    'duplicate-verdict', 'duplicate-reasons', 'duplicate-sha', 'duplicate-branch',
    'Verdict', 'Reasons', 'SHA', 'Branch', 'wrapped-pass', 'wrapped-block',
    'extra-field', 'blank-reason', 'scalar-reason', 'nonstring-reason', 'pass-with-reasons', 'block-without-reasons',
    'nonstring-sha', 'nonstring-branch', 'lowercase-branch',
    'json-comment', 'json-single-quote', 'json-unquoted-property', 'json-trailing-comma'
  )) {
    $result = Invoke-ReviewPolicyCase "json-$mode" -Mode $mode -ExistingRoot $advisory.Root
    $diagnostic = if ($mode -ceq 'lowercase-branch') { 'Verdict branch does not match the reviewed branch' } else { '[R3-BAD-VERDICT-JSON]' }
    $rejected = $result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: advisory') -and $result.Output.Contains($diagnostic) -and -not $result.Output.Contains('R3 advisory findings:')
    Assert-ReviewPolicyCheck $rejected "advisory rejects malformed JSON: $mode"
    if ($rejected) { Write-Host "REVIEW-POLICY-ADVISORY-REJECTION: $mode" }
  }
  foreach ($case in @(
    @{Name='legacy'; Gate='codex {verdict:pass}'}, @{Name='head-optin'; Gate='blocking'; CardChange='optin'},
    @{Name='critical-rename'; Rename=$true}, @{Name='dynamic-frozen'; Frozen='@(Get-Location)'}
  )) {
    $result = Invoke-ReviewPolicyCase @case
    Assert-ReviewPolicyCheck ($result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: blocking')) "$($case.Name) cannot become advisory"
    if ($case.Name -eq 'legacy') {
      foreach ($mode in @('Verdict', 'wrapped-pass')) {
        $legacyCase = Invoke-ReviewPolicyCase "legacy-$mode" -Mode $mode -ExistingRoot $result.Root
        Assert-ReviewPolicyCheck ($legacyCase.Exit -eq 0 -and $legacyCase.Verdict.verdict -ceq 'pass' -and $legacyCase.Output.Contains('Review gate policy: blocking')) "legacy JSON tolerance preserved: $mode"
      }
    }
  }
  foreach ($mode in @('empty', 'malformed', 'badreasons', 'stale', 'nonzero', 'writefail', 'timeout')) {
    $result = Invoke-ReviewPolicyCase "backend-$mode" -Mode $mode -ExistingRoot $advisory.Root
    Assert-ReviewPolicyCheck ($result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: advisory') -and -not $result.Output.Contains('R3 advisory findings:')) "$mode remains an operational failure under advisory"
    if ($mode -eq 'writefail') { Assert-ReviewPolicyCheck ($result.Output.Contains('[R3-VERDICT-WRITE-FAILED]') -and -not $result.Output.Contains('[R3-OUTPUT-UNREADABLE]')) 'valid negative verdict can be read but failed normalization never accepts advisory' }
  }
  $testCard = "---`nid: T9-REVIEW-POLICY`nstatus: todo`nreview_gate: advisory`n---`n# approved card"
  $rawPrefix = ':100644 100644 ' + ('a' * 40) + ' ' + ('b' * 40) + " M`t"
  $policyInputs = @{CardText=$testCard; BaselineAuthorized=$true; FrozenPaths=@('docs/frozen/'); RawDiff=@($rawPrefix + 'docs/research/property-inspect.md'); CardPath='specs/tasks/T9-REVIEW-POLICY.md'; HeadCardText=$testCard}
  foreach ($path in @('specs/android-module-boundaries.md','docs/credentials.md','docs/encryption.md','docs/deployment.md','docs/ci-pipeline.md','docs/api.md','docs/disaster-recovery.md','docs/research/chapps.md','docs/research/opensource-indie.md','docs/research/synthesis.md','docs/unknown-note.md')) {
    $result = Invoke-ReviewPolicyCase ([IO.Path]::GetFileNameWithoutExtension($path)) -Path $path -ExistingDocument
    Assert-ReviewPolicyCheck ($result.Exit -ne 0 -and $result.Output.Contains('Review gate policy: blocking') -and $result.Verdict.verdict -ceq 'block') "authority path remains blocking: $path"
  }
  foreach ($name in @('securityGuide', 'authentication', 'privacyPolicyGuide', 'licenseGuide', 'workflowGuide', 'rubricGuide', 'deliveryGuide', 'releaseGuide', 'schemaGuide', 'migrationGuide', 'contractGuide', 'requirementsGuide', 'designNotes', 'policyGuide', 'complianceGuide', 'backupGuide', 'retentionRules', 'erasureGuide', 'trustGuide', 'manifestGuide', 'techDebtGuide', 'tech_debtGuide', 'tech-debtGuide')) {
    foreach ($path in @("docs/$name.md", "specs/$name/example.md")) {
      $inputs = $policyInputs.Clone(); $inputs.RawDiff = @($rawPrefix + $path)
      Assert-ReviewPolicyCheck ((Get-ScaffoldReviewPolicy @inputs) -ceq 'blocking') "critical substring path: $path"
    }
  }
  foreach ($case in @(
    @{Name='specs-note'; Path='specs/notes/example.md'},
    @{Name='security'; Path='docs/SECURITY.md'}, @{Name='workflow'; Path='docs/DEVOPS-WORKFLOW.md'},
    @{Name='delivery'; Path='docs/DELIVERY-OPS.md'}, @{Name='requirements'; Path='docs/inspection-app-requirements.md'},
    @{Name='rubric'; Path='docs/QUALITY-RUBRIC.md'}, @{Name='frozen'; Path='docs/frozen/note.md'},
    @{Name='unknown'; Path='payload.txt'}, @{Name='other-card'; Path='specs/tasks/T9-OTHER.md'},
    @{Name='unsafe-path'; Path='docs/../note.md'}, @{Name='archive'; Path='specs/archive/notes.md'}
  )) {
    $inputs = $policyInputs.Clone(); $inputs.RawDiff = @($rawPrefix + $case.Path)
    $want = if ($case.ContainsKey('Want')) { $case.Want } else { 'blocking' }
    Assert-ReviewPolicyCheck ((Get-ScaffoldReviewPolicy @inputs) -ceq $want) "$($case.Name) path classification"
  }
  foreach ($case in @(
    @{Name='duplicate'; CardText=$testCard.Replace('review_gate: advisory', "review_gate: advisory`nreview_gate: advisory")},
    @{Name='malformed-card'; CardText='review_gate: advisory'}, @{Name='no-baseline'; BaselineAuthorized=$false},
    @{Name='unknown-gate'; CardText=$testCard.Replace('advisory','optional')}, @{Name='missing-gate'; CardText=$testCard.Replace('review_gate: advisory','')},
    @{Name='missing-frozen'; FrozenPaths=$null}, @{Name='malformed-frozen'; FrozenPaths=@('[')},
    @{Name='allowlisted-frozen'; FrozenPaths=@('^docs/research/')},
    @{Name='unreadable-diff'; RawDiff=@()}, @{Name='symlink'; RawDiff=@($rawPrefix.Replace('100644 100644','100644 120000') + 'docs/research/property-inspect.md')},
    @{Name='mixed'; RawDiff=@($rawPrefix+'docs/research/property-inspect.md', $rawPrefix+'scripts/tool.ps1')},
    @{Name='changed-card'; RawDiff=@($rawPrefix+'specs/tasks/T9-REVIEW-POLICY.md'); HeadCardText=$testCard.Replace('# approved card','# changed approval')},
    @{Name='body-status'; CardText=($testCard+"`nstatus: todo"); RawDiff=@($rawPrefix+'specs/tasks/T9-REVIEW-POLICY.md'); HeadCardText=($testCard+"`nstatus: todo").Replace('status: todo','status: merged')},
    @{Name='deleted-card'; RawDiff=@($rawPrefix.Replace('100644 100644','100644 000000')+'specs/tasks/T9-REVIEW-POLICY.md')},
    @{Name='added-card'; RawDiff=@($rawPrefix.Replace('100644 100644','000000 100644')+'specs/tasks/T9-REVIEW-POLICY.md')}
  )) {
    $inputs = $policyInputs.Clone()
    foreach ($key in $case.Keys) { if ($key -ne 'Name') { $inputs[$key] = $case[$key] } }
    Assert-ReviewPolicyCheck ((Get-ScaffoldReviewPolicy @inputs) -ceq 'blocking') "$($case.Name) policy input fails closed"
  }
  # Exercise the actual local invocation against two distinguishable script roots.
  $localLine = @(Get-Content -LiteralPath (Join-Path $PSScriptRoot 'task.ps1') | Where-Object { $_ -match '^\s*& pwsh .*review\.ps1.*-LocalBase' })
  Assert-ReviewPolicyCheck ($localLine.Count -eq 1) 'one local review invocation is observable'
  if ($localLine.Count -eq 1) {
    $RepoRoot = Join-Path $policyRoot 'trusted'; $Wt = Join-Path $policyRoot 'reviewed'; $Base = 'master'
    foreach ($root in @($RepoRoot, $Wt)) {
      New-Item -ItemType Directory -Force (Join-Path $root 'scripts') | Out-Null
      Set-Content -LiteralPath (Join-Path $root 'scripts/review.ps1') -Value ('param($WorktreePath,$Base,[switch]$LocalBase); Write-Output ''' + $(if ($root -eq $RepoRoot) { 'trusted-review-source' } else { 'untrusted-review-source' }) + '''')
    }
    $invoked = (& ([scriptblock]::Create($localLine[0])) | Out-String).Trim()
    Assert-ReviewPolicyCheck ($invoked -ceq 'trusted-review-source') 'local ship invokes trusted main checkout reviewer'
  }
} finally {
  $env:PATH = $savedPath; $env:PATHEXT = $savedExt; $env:REVIEW_POLICY_TEST_MODE = $savedMode
  $resolvedPolicyRoot = [IO.Path]::GetFullPath($policyRoot)
  if ($resolvedPolicyRoot.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()), [StringComparison]::OrdinalIgnoreCase) -and (Split-Path $resolvedPolicyRoot -Leaf) -like 'review-policy-*') {
    Remove-Item -LiteralPath $resolvedPolicyRoot -Recurse -Force -ErrorAction SilentlyContinue
  }
}
if ($policyFailures.Count) { exit 1 }
Write-Host 'REVIEW-POLICY-SELF-CHECK-PASS'
exit 0
