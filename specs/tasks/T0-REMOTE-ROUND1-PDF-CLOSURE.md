---
id: T0-REMOTE-ROUND1-PDF-CLOSURE
title: Complete round-one R5 with the verified Typography archive
status: merged
depends_on: [T0-REMOTE-ROUND1-CLOSURE, T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE]
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND1-PDF-CLOSURE.md
  - specs/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/archive/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - docs/adr/0007-report-interchange.md
acceptance:
  - "A1 Preserve the full Typography remote contract except merged status and appended delivery records. Publish its observed PR305, reviewed head, candidate CI, formal R3, final source pins, the actual earlier 12-mutation context, final test totals, copied-evidence audit and cleanup receipt. Bind these to the approved reviewed candidate and merge; do not claim a post-alignment mutation rerun."
  - "A2 After PR306 actually merges, retain every byte of its complete 196-card archive and add only the Typography card (197 total). Remove its active copy and generate the index from the complete archive. The first closure card remains active and unchanged."
  - "A3 Update only CLAUDE current stage, Task Board Typography status/link and first-round total, and ADR-0007 Typography publication. Two functional products of ten complete R5 after this PR merges. Preserve Boundary/Pagination todo and deferred composition, platform-glyph and device acceptance boundaries."
  - "A4 Fixed five approved payloads, portable lifecycle bindings, merged source blobs, test/mutation context, exact archive move, pending successors, card validation, generated index and complete pinned-base-to-candidate plus staged whitespace pass. Original-main scope, budget, formal R3 and exact-candidate CI remain mandatory."
forbid:
  - Product source, tests, scripts, configuration, schema, dependencies, lessons or debt changes
  - Altering prior archived cards or the first closure card
  - Reusing original local acceptance as remote acceptance or declaring device acceptance
  - Direct master push, history rewriting, bypassing merge gates or raising diff limits
non_goals:
  - Implementing later product rounds or completing external prereview features
  - Reconciling divergent original-main history
dod_command: $raw = Get-Content -LiteralPath 'specs/tasks/T0-REMOTE-ROUND1-PDF-CLOSURE.md' -Raw; $blocks = [regex]::Matches($raw, '(?ms)^```powershell\r?\n(.*?)^```[ \t]*$'); if ($blocks.Count -ne 1) { throw 'Expected one approved assertion block' }; & ([scriptblock]::Create($blocks[0].Groups[1].Value)); if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: The local DoD requires the copied original PR/CI/R3/cleanup/audit JSON, 319 manifest entries and original 12-mutation records; their actual file hashes and portable values must match. It checks five approved payloads, source/test/mutation context, the 196-to-197 archive projection, two pending successors and complete diff whitespace. It does not attest live external state or rerun product tests.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata closure with explicit SkipRed. Retain the actual earlier 12-mutation evidence and its source-context distinction; exercise altered CI, cleanup, mutation, status and payload negatives after populating the final assertions.
doc_sync: This PR completes Typography R5 and round-one documentation. Its own merged status is effective only when the PR actually merges; retain that merge receipt in the controller ledger and run guarded original-main cleanup. Keep this closure card active until ordinary later archival maintenance.
---

# Typography R5 closure

The first closure PR covers registration PRs 301/303 and Logging PR304. The first closure merged as 0b893828d4cd8cf42f46cb87e2c5d0105ab5cd39 via PR306 and its guarded cleanup completed. This card records Typography PR305 against that actual base. Functional PR305 has already merged; its R5 status remains pending until this metadata PR passes the ordinary gates and merges. Registration cards do not count as products. The target after this closure is two of ten products and one of five rounds.

The structured Typography receipt in the archived card is copied unchanged from the preserved oversized repair. It records the first formal Sol/high PASS, exact candidate CI, original-main cleanup, three final source pins, 255 report tests, six E2E tests, 976 core tests with four existing skips, and 12 actual earlier remote mutations. The pre-alignment mutation test source and final header-corrected source differ only in comment text; no post-alignment mutation rerun is asserted. Full original logs and XML remain in the ignored copied evidence directory and must be available for reviewer inspection.

The complete pinned base contains 196 archived cards; every original archive and the first closure card remain unchanged. This card adds only the Typography archive and its reviewed documentation. Its own merged target status becomes effective only upon this PR merging; until then, the controller ledger records pending R5. No new lesson is added because the original-file and exact-candidate evidence requirements already cover the observed repairs.

## Approved payload assertions

```powershell
$ErrorActionPreference = 'Stop'
$base = '0b893828d4cd8cf42f46cb87e2c5d0105ab5cd39'
$expected = @{
  'specs/archive/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md' = '3D86FE8FABFF15C3ADC8945F77DF18F26D1700927576C00E853925EB29879ACE'
  'specs/archive/cards-index.md' = '39A5ECD9ED36457D70AB8CBF8ED6505A54A49D8BB02FFBC2463FE61E78FD0490'
  'CLAUDE.md' = '793C39587680FC1A21DA41800139CC66A99B1BD4183166641130489B8D58F446'
  'docs/TASK-BOARD.md' = 'BAD3C287275C25CEB21E3ABB1968C9F4BF63EA47744BC0194E2223EDF2B0E291'
  'docs/adr/0007-report-interchange.md' = '0F1B4BF91FD779D27404167DFB641DAC8C24586042240BFFC959FF18B4DCEC40'
}
if ($base -notmatch '^[0-9a-f]{40}$' -or @($expected.Values | Where-Object { $_ -notmatch '^[0-9A-F]{64}$' }).Count -ne 0) { throw '[R5-PREP] Actual base and five approved payload hashes are required' }
git merge-base --is-ancestor $base HEAD
if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Pinned PDF closure base is not an ancestor' }
git diff --check $base
if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Complete pinned-base diff has whitespace errors' }

$id = 'T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE'
$head = '9f06217c8c521e72ce9faca2513bad0d18a46fa2'
$merge = '3351c06c99ba8d85e3e008b7a89cdac43bb2470d'
$path = "specs/archive/tasks/$id.md"
if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '[R5-PAYLOAD] Missing Typography archive' }
$raw = Get-Content -LiteralPath $path -Raw
$blocks = [regex]::Matches($raw,'(?ms)^<!-- remote-lifecycle-receipt -->\r?\n```json\r?\n(.*?)^```[ \t]*$')
if ($blocks.Count -ne 1) { throw '[R5-BINDING] Expected one portable lifecycle receipt' }
$r = $blocks[0].Groups[1].Value | ConvertFrom-Json
$utf8 = [Text.UTF8Encoding]::new($false,$true)
$evidenceRoot = Join-Path $PWD '_local/rotating-card-orchestrator/pdf-evidence/profile-remote'
function Read-Proof([string]$relative,[string]$sha) {
  $file = Join-Path $evidenceRoot $relative
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "[R5-EVIDENCE] Missing copied proof: $relative" }
  $bytes = [IO.File]::ReadAllBytes($file)
  if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -cne $sha) { throw "[R5-EVIDENCE] Copied proof hash differs: $relative" }
  return $utf8.GetString($bytes) | ConvertFrom-Json
}
function Assert-SameJson($left,$right,[string]$label) {
  # Captured objects retain their original key order; compare all captured values.
  $a = ConvertTo-Json -InputObject $left -Depth 80 -Compress
  $b = ConvertTo-Json -InputObject $right -Depth 80 -Compress
  if (-not [string]::Equals($a,$b,[StringComparison]::Ordinal)) { throw "[R5-EVIDENCE] Portable $label differs from copied proof" }
}
$sourcePr = Read-Proof 'final-ship/pr305-merged.json' '5B94D62EA00C1DBD5F5E84A032E16FEC3C01E924BD3C1D98C065B287E7FF30A0'
$sourceCi = Read-Proof 'final-ship/ci-run35159732680.json' 'EC62C8614C4CBC4AD85CAD95997D49FC8462E7E5D423EFCC39FE6CE09396B77A'
Assert-SameJson $r.formalR3 (Read-Proof 'final-ship/formal-r3.json' 'A1D34465C9E9131FEAA37BB10AD1D7A1F7823D3E682817E24855434AE407E940') 'R3'
Assert-SameJson $r.cleanup (Read-Proof 'root-cleanup-audit/cleanup-result.json' '8BED38F114F152A386DD963B8F5F15DE70470C2238A1F6F742E1033FB986235F') 'cleanup'
Assert-SameJson $r.cleanupAudit (Read-Proof 'root-cleanup-audit/audit.json' 'F4B5DB52EB4F608278945261D6E67936C7A3F6820985855E0821133BD108CF6B') 'audit'
foreach ($key in @('number','url','state','headRefOid','mergedAt')) { if ($r.pr.$key -cne $sourcePr.$key) { throw '[R5-EVIDENCE] Portable PR differs from copied proof' } }
if ($r.pr.mergeCommit.oid -cne $sourcePr.mergeCommit.oid) { throw '[R5-EVIDENCE] Portable PR merge differs' }
$ciProjection = [ordered]@{ databaseId=$sourceCi.databaseId; headSha=$sourceCi.headSha; conclusion=$sourceCi.conclusion; status=$sourceCi.status; event=$sourceCi.event; jobs=@($sourceCi.jobs | ForEach-Object { [ordered]@{name=$_.name;status=$_.status;conclusion=$_.conclusion} }) }
Assert-SameJson $r.candidateCI $ciProjection 'CI'
foreach ($audit in $r.cleanupAudit.manifestAudits) {
  $manifest = Read-Proof "$($audit.dir)/manifest.json" $audit.sha256
  if (@($manifest).Count -ne $audit.files) { throw '[R5-EVIDENCE] Copied manifest count differs' }
  foreach ($item in $manifest) {
    $rel = [string]$item.path
    if ($rel -match '^([A-Za-z]:|[\\/])' -or $rel -match '(^|[\\/])\.\.([\\/]|$)') { throw '[R5-EVIDENCE] Unsafe manifest path' }
    $leaf = Join-Path $evidenceRoot "$($audit.dir)/$rel"
    if (-not (Test-Path -LiteralPath $leaf -PathType Leaf) -or (Get-FileHash -LiteralPath $leaf -Algorithm SHA256).Hash -cne $item.sha256) { throw '[R5-EVIDENCE] Copied manifest member differs' }
  }
}
$oldR4 = 'verified-20260917/r4-profile-20260916T215236078Z'
Assert-SameJson $r.mutationReceipt (Read-Proof "$oldR4/complete.json" 'D67052FC13758517F152E8A772EEE5C21C9E4EA9E153B7DA6C135A90E24DF5E3') 'mutation receipt'
$sourceMutants = Read-Proof "$oldR4/mutants.json" '50D03D955F19044682AF1D47D5BDBA9CDEE7252CEC7FA01331FA86A6782E963D'
$mutantProjection = @($sourceMutants | ForEach-Object { [ordered]@{id=$_.id;expectedFailure=$_.expectedFailure;exitCode=$_.exitCode;failedTests=$_.failedTests;tests=$_.tests;killed=$_.killed;restoredAllThree=$_.restoredAllThree} })
Assert-SameJson $r.mutations $mutantProjection 'named mutations'
foreach ($i in 0..2) { if ($r.sourcePins[$i].path -cne $r.cleanupAudit.sourcePins[$i].path -or $r.sourcePins[$i].sha256 -cne $r.cleanupAudit.sourcePins[$i].sha256) { throw '[R5-EVIDENCE] Final source pin differs from copied audit' } }
if ($r.id -cne $id -or $r.pr.number -ne 305 -or $r.pr.state -cne 'MERGED' -or $r.pr.headRefOid -cne $head -or $r.pr.mergeCommit.oid -cne $merge) { throw '[R5-BINDING] PR identity differs' }
if ($r.formalR3.branch -cne $id -or $r.formalR3.sha -cne $head -or $r.formalR3.verdict -cne 'pass' -or @($r.formalR3.reasons).Count -ne 0) { throw '[R5-BINDING] Formal review differs' }
if ($r.candidateCI.databaseId -ne 35159732680 -or $r.candidateCI.headSha -cne $head -or $r.candidateCI.event -cne 'pull_request' -or $r.candidateCI.status -cne 'completed' -or $r.candidateCI.conclusion -cne 'success') { throw '[R5-BINDING] Candidate CI differs' }
foreach ($name in @('verify','required')) {
  $jobs = @($r.candidateCI.jobs | Where-Object { $_.name -ceq $name })
  if ($jobs.Count -ne 1 -or $jobs[0].status -cne 'completed' -or $jobs[0].conclusion -cne 'success') { throw '[R5-BINDING] Required CI job differs' }
}
if ($r.cleanup.head -cne $head -or $r.cleanup.merge -cne $merge -or $r.cleanup.exit -ne 0 -or $r.cleanup.worktreeAbsent -cne $true -or $r.cleanup.branchAbsent -cne $true) { throw '[R5-BINDING] Cleanup receipt differs' }
if ($r.cleanupAudit.head -cne $head -or $r.cleanupAudit.merge -cne $merge -or $r.cleanupAudit.mergeTokenVerified -cne $true -or $r.cleanupAudit.worktreeClean -cne $true -or $r.cleanupAudit.mutationContextUnchanged -cne $true) { throw '[R5-BINDING] Cleanup audit differs' }
if (@($r.sourcePins).Count -ne 3) { throw '[R5-BINDING] Final source pins differ' }
foreach ($pin in $r.sourcePins) {
  $blob = (& git rev-parse "$merge`:$($pin.path)").Trim()
  if ($LASTEXITCODE -ne 0 -or $blob -cne $pin.blob) { throw '[R5-BINDING] Merged source blob differs' }
}
if ($r.mutationReceipt.killed -ne 12 -or $r.mutationReceipt.mutants -ne 12 -or $r.mutationReceipt.restoredDoDExit -ne 0 -or @($r.mutations).Count -ne 12) { throw '[R5-BINDING] Mutation total differs' }
if ($r.mutationReceipt.sources[1].sha256 -cne '769249A43320D4A10EBAB3CB2DCDE7F7E8162FCB629A36156540178603C08AF7' -or $r.sourcePins[1].sha256 -cne '5476FC14D285D2FCC79AEBD8190FA1DCD78A6FCDA284E0672C0058AD408D4A44') { throw '[R5-BINDING] Pre/post alignment source context differs' }
foreach ($m in $r.mutations) {
  if ($m.exitCode -ne 1 -or $m.tests -ne 5 -or $m.killed -cne $true -or $m.restoredAllThree -cne $true -or [Array]::IndexOf([string[]]$m.failedTests,[string]$m.expectedFailure) -lt 0) { throw '[R5-BINDING] Named mutation failure differs' }
}
foreach ($group in @(@('report',255),@('e2e',6),@('fullCore',976))) {
  $xml = $r.cleanupAudit.xml.($group[0])
  $skip = if ($group[0] -ceq 'fullCore') { 4 } else { 0 }
  if ($xml.tests -ne $group[1] -or $xml.failures -ne 0 -or $xml.errors -ne 0 -or $xml.skipped -ne $skip) { throw '[R5-BINDING] Final test audit differs' }
}

foreach ($pair in $expected.GetEnumerator()) {
  if (-not (Test-Path -LiteralPath $pair.Key -PathType Leaf)) { throw "[R5-PAYLOAD] Missing approved payload: $($pair.Key)" }
  $text = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path $PWD $pair.Key))).Replace("`r`n","`n")
  $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($text)))
  if ($actual -cne $pair.Value) { throw "[R5-PAYLOAD] Approved payload differs: $($pair.Key)" }
}
if (Test-Path -LiteralPath "specs/tasks/$id.md") { throw '[R5-STATUS] Typography active card remains' }
if ($raw -cnotmatch '(?m)^status: merged\r?$') { throw '[R5-STATUS] Typography archive not merged' }
foreach ($pending in @('T1-STORAGE-PATH-BOUNDARY-REMOTE','T3-PDF-PAGINATION-FIXTURES-REMOTE')) {
  if ((Get-Content -LiteralPath "specs/tasks/$pending.md" -Raw) -cnotmatch '(?m)^status: todo\r?$') { throw "[R5-STATUS] Pending successor changed: $pending" }
}
if ((Get-Content -LiteralPath 'specs/tasks/T0-REMOTE-ROUND1-PDF-CLOSURE.md' -Raw) -cnotmatch '(?m)^status: merged\r?$') { throw '[R5-STATUS] PDF closure transition missing' }
pwsh -NoProfile -File scripts/check-cards.ps1
if ($LASTEXITCODE -ne 0) { throw 'Card validation failed' }
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet
if ($LASTEXITCODE -ne 0) { throw 'Archive projection differs' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Working diff whitespace failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff whitespace failed' }
Write-Output '[R5-PDF-CLOSURE-PASS] Typography lifecycle, five approved payloads and two pending successors verified.'
```
