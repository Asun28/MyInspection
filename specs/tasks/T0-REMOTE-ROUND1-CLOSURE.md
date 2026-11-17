---
id: T0-REMOTE-ROUND1-CLOSURE
title: Close registration and SafeLog remote delivery records
status: merged
depends_on: [T0-REMOTE-PRODUCT-CARDS, T0-REMOTE-PREREVIEW-CARDS, T1-SAFE-MEDIA-LOGGING-REMOTE]
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND1-CLOSURE.md
  - specs/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/archive/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/archive/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/archive/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/SECURITY.md
  - docs/TASK-BOARD.md
sweep: First-batch R5 touches the two registration cards and SafeLog card, their archive counterparts, generated archive index, current-stage note, SafeLog security delivery and Task Board. The two registration cards do not count as product deliveries. Typography remains active/todo for its later R5 closure. Unrelated card and document content is retained.
acceptance:
  - "A1 Record PRs 301, 303 and 304 with portable PR/R3/CI/cleanup receipts bound to their reviewed heads, merge OIDs and passing candidate CI. SafeLog's remote test, source and 24 named mutation evidence is retained separately from prior local history; no pending result is complete."
  - "A2 After original-main cleanup and independent copied-evidence audit, move exactly these three cards to specs/archive/tasks, retaining complete original contracts except status and appended delivery records. Generate cards-index from the complete 193-card pinned-base archive plus three moves (196 total); remove their active copies."
  - "A3 Update CLAUDE current stage, SECURITY SafeLog record and Task Board. Logging R5 is complete (1/10); Typography feature PR #305 is merged but its card remains active/todo pending separate R5. Preserve Boundary and Pagination todo, original local lineage and deferred composition, platform-glyph, storage and device-acceptance boundaries."
  - "A4 Fixed approved payload checks cover three archived cards, generated index and three documentation updates. Card validation, archive-index projection, pinned-base complete-diff and staged whitespace checks pass; normal original-main scope, diff budget, formal R3 and exact-candidate CI gates remain mandatory."
forbid:
  - Product source, tests, configuration, scripts, schema or dependency changes
  - Changing unrelated cards, archive records, lessons or debt rows
  - Reusing old local evidence as acceptance of a remote candidate
  - Direct push to master, history rewriting or bypassing any merge gate
non_goals:
  - Implementing any later round or publishing device acceptance
  - Re-running already accepted product mutations solely for metadata edits
  - Reconciling divergent local master history
dod_command: $raw = Get-Content -LiteralPath 'specs/tasks/T0-REMOTE-ROUND1-CLOSURE.md' -Raw; $blocks = [regex]::Matches($raw, '(?ms)^```powershell\r?\n(.*?)^```[ \t]*$'); if ($blocks.Count -ne 1) { throw 'Expected one approved assertion block' }; & ([scriptblock]::Create($blocks[0].Groups[1].Value)); if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Three actual prerequisite deliveries have independently verified external receipts; this DoD checks seven approved LF-normalized payloads, portable PR/R3/CI/cleanup candidate bindings, merged SafeLog source blobs, test and mutation audits, exact archive moves, merged statuses, pending successors, card validation, generated index and complete pinned-base whitespace. It also requires 332 copied original evidence files, validates their actual SHA/length, resolves six child manifests and binds all portable lifecycle records to the original JSON. It does not perform live external attestation.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata closure; explicit SkipRed. Preserve reviewed source, validation and mutation receipts before cleanup; exercise missing-record, wrong-status, changed-payload and original-evidence missing/byte-corruption/manifest negative cases, then restore exact bytes and rerun DoD.
doc_sync: This PR contains the R5 documentation and archive updates for the two registration cards and SafeLog. Typography's functional merge is recorded as R5 pending for a separate card. This card's own merged status is effective only when this PR actually merges; preserve that merge receipt in the controller ledger and run original-main cleanup. The closure card can remain active as merged until ordinary later archival maintenance.
---

# Registration and SafeLog remote closure

This metadata PR completes R5 for PRs 301, 303 and 304. Only Logging counts toward the five-round product target in this batch: one of ten closed. Typography's functional PR #305 also merged, but its R5 record and archive move remain pending. The three registrations/function PRs in this batch merged and their evidence was independently checked before original-main cleanup. Full contracts and earlier local provenance are retained in the archived cards.

The status of this closure card is the reviewed target state, effective only when this PR actually merges. Until that event, the controller ledger records this work as pending. This card remains in the active directory for later ordinary archival maintenance, avoiding a recursive metadata-only closure PR.

The archive projection starts from the complete remote archive at 3351c06c99ba8d85e3e008b7a89cdac43bb2470d (193 cards), retains every existing byte, and adds exactly three cards (196 total). Payload digests below were approved only after the external lifecycle receipts had been checked. The portable observed receipts are asserted against fixed approved candidates and source blobs; full copied artifacts are also available to the reviewer in the worktree. These historical snapshots do not query live GitHub or rerun historical tests. The first formal R3 BLOCK and its three findings remain preserved in the controller ledger; this split addresses all three without altering prior functional evidence. No new lesson is added: the observed repairs are covered by existing exact-proof, byte-fidelity and self-verifying-contract rules.

Local ship additionally requires the preserved originals at their candidate-relative ignored paths. The DoD reads every one of the 332 inventoried files, verifies its bytes and SHA-256, checks all six child manifests against those files, and compares portable PR/R3/CI/cleanup/audit fields with their original captured JSON. Missing originals fail closed; CI verify does not rerun this local archival DoD. The second formal BLOCK is preserved separately from the policy-denied reviewer read attempts. No historical product test is described as rerun by these archival checks.

## Approved payload assertions

```powershell
$ErrorActionPreference = 'Stop'
$base = '3351c06c99ba8d85e3e008b7a89cdac43bb2470d'
git merge-base --is-ancestor $base HEAD
if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Pinned closure base is not an ancestor' }
git diff --check $base
if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Complete pinned-base diff has whitespace errors' }
$deliveries = @(
  @('T0-REMOTE-PRODUCT-CARDS',301,'01454f18ad04a1e254f3df748b4082d11aca1447','1ce3f5aef130ddd3fac19632a46e04c6671f91a6',35151534312),
  @('T0-REMOTE-PREREVIEW-CARDS',303,'0b474848973b348a6877da54d1ce2077a444e55e','5d7fdc910561be463824690b4d4689335bbb0bd8',35157398386),
  @('T1-SAFE-MEDIA-LOGGING-REMOTE',304,'6216e9d16f93cac6b4a62a1edb93de09c9746330','cd7e20160a093817c9a4245cfafe9e393e9a6e49',35158547855)
)
foreach ($entry in $deliveries) {
  $path = "specs/archive/tasks/$($entry[0]).md"
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '[R5-PAYLOAD] Missing archived receipt' }
  $raw = Get-Content -LiteralPath $path -Raw
  $blocks = [regex]::Matches($raw,'(?ms)^<!-- remote-lifecycle-receipt -->\r?\n```json\r?\n(.*?)^```[ \t]*$')
  if ($blocks.Count -ne 1) { throw '[R5-BINDING] Expected one portable lifecycle receipt' }
  $r = $blocks[0].Groups[1].Value | ConvertFrom-Json
  if ($r.id -cne $entry[0] -or $r.pr.number -ne $entry[1] -or $r.pr.state -cne 'MERGED' -or $r.pr.headRefOid -cne $entry[2] -or $r.pr.mergeCommit.oid -cne $entry[3]) { throw '[R5-BINDING] PR identity differs' }
  if ($r.formalR3.branch -cne $entry[0] -or $r.formalR3.sha -cne $entry[2] -or $r.formalR3.verdict -cne 'pass' -or @($r.formalR3.reasons).Count -ne 0) { throw '[R5-BINDING] Formal review differs' }
  if ($r.candidateCI.databaseId -ne $entry[4] -or $r.candidateCI.headSha -cne $entry[2] -or $r.candidateCI.event -cne 'pull_request' -or $r.candidateCI.status -cne 'completed' -or $r.candidateCI.conclusion -cne 'success') { throw '[R5-BINDING] Candidate CI differs' }
  foreach ($name in @('verify','required')) {
    $jobs = @($r.candidateCI.jobs | Where-Object { $_.name -ceq $name })
    if ($jobs.Count -ne 1 -or $jobs[0].status -cne 'completed' -or $jobs[0].conclusion -cne 'success') { throw '[R5-BINDING] Required CI job differs' }
  }
  if ($r.cleanup.head -cne $entry[2] -or $r.cleanup.merge -cne $entry[3] -or $r.cleanup.exit -ne 0 -or $r.cleanup.worktreeAbsent -cne $true -or $r.cleanup.branchAbsent -cne $true) { throw '[R5-BINDING] Cleanup receipt differs' }
  if ($entry[1] -eq 301 -or $entry[1] -eq 303) {
    $count = if ($entry[1] -eq 301) { 18 } else { 21 }
    if ($r.evidenceAudit.manifestEntries -ne $count -or $r.evidenceAudit.verifiedEntries -ne $count -or $r.evidenceAudit.copiedEvidenceFiles -ne $count -or $r.cleanup.copiedEvidenceFiles -ne $count) { throw '[R5-BINDING] Registration evidence audit differs' }
  } else {
    if ($r.cleanupAudit.head -cne $entry[2] -or $r.cleanupAudit.merge -cne $entry[3] -or $r.cleanupAudit.mergeTokenVerified -cne $true -or $r.cleanupAudit.worktreeClean -cne $true) { throw '[R5-BINDING] Product cleanup audit differs' }
    foreach ($pin in $r.sourcePins) {
      $blob = (& git rev-parse "$($entry[3]):$($pin.path)").Trim()
      if ($LASTEXITCODE -ne 0 -or $blob -cne $pin.blob) { throw '[R5-BINDING] Merged source blob differs' }
    }
    if (@($r.sourcePins).Count -ne 7 -or $r.evidenceAudit.status -cne 'PASS' -or $r.evidenceAudit.reconstructedMutants -ne 24 -or $r.evidenceAudit.allNamedAssertionErrors -cne $true -or $r.evidenceAudit.appTests -ne 157 -or $r.cleanupAudit.xml.tests -ne 1134 -or $r.cleanupAudit.xml.failures -ne 0 -or $r.cleanupAudit.xml.errors -ne 0 -or $r.cleanupAudit.xml.skipped -ne 4) { throw '[R5-BINDING] Logging test or mutation audit differs' }
  }
}

# Local ship requires the copied originals; these checks never query the network.
$proofRoot = '_local/rotating-card-orchestrator/'
$receiptDir = 'remote-delivery/T0-REMOTE-ROUND1-CLOSURE/actual-receipts'
$manifestPath = "$proofRoot$receiptDir/evidence-manifest.json"
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or (Get-FileHash -LiteralPath $manifestPath).Hash -cne 'A6D3185C85E05ECAD3B92DBF8F455484F7812E9B66F691E0F6AB17FA62BAD24C') { throw '[R5-EVIDENCE] Approved original-file manifest missing or changed' }
$originals = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
if ($originals.Count -ne 332) { throw '[R5-EVIDENCE] Original-file count differs' }
$files = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach ($item in $originals) {
  $path = $item.path.Replace('\','/')
  if (-not $path.StartsWith($proofRoot,[StringComparison]::Ordinal) -or $path -match '(^|/)(\.|\.\.|)(/|$)|:' -or $files.ContainsKey($path)) { throw '[R5-EVIDENCE] Unsafe or duplicate evidence path' }
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "[R5-EVIDENCE] Missing original: $path" }
  $file = Get-Item -LiteralPath $path
  if (($file.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $file.Length -ne $item.bytes -or (Get-FileHash -LiteralPath $path).Hash -cne $item.sha256) { throw "[R5-EVIDENCE] Original bytes differ: $path" }
  $files.Add($path,$item)
}
function Read-Original([string]$relative) {
  if (-not $files.ContainsKey("$proofRoot$relative")) { throw '[R5-EVIDENCE] Source outside verified inventory' }
  Get-Content -LiteralPath "$proofRoot$relative" -Raw | ConvertFrom-Json
}
function Assert-Original($actual,$expected) {
  # Captured objects retain their original key order; require their full JSON values.
  if (-not [string]::Equals(($actual | ConvertTo-Json -Depth 30 -Compress),($expected | ConvertTo-Json -Depth 30 -Compress),[StringComparison]::Ordinal)) { throw '[R5-EVIDENCE] Portable record differs from original JSON' }
}
function Assert-ChildManifest([string]$manifest,[string]$directory,[int]$count,[string]$sha) {
  if ($files["$proofRoot$manifest"].sha256 -cne $sha) { throw '[R5-EVIDENCE] Child-manifest digest differs' }
  $entries = @(Read-Original $manifest)
  if ($entries.Count -ne $count) { throw '[R5-EVIDENCE] Child-manifest count differs' }
  $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
  foreach ($entry in $entries) {
    $key = "$proofRoot$directory/$($entry.path.Replace('\','/'))"
    if (-not $seen.Add($key) -or -not $files.ContainsKey($key) -or $files[$key].bytes -ne $entry.bytes -or $files[$key].sha256 -cne $entry.sha256) { throw '[R5-EVIDENCE] Child entry differs from verified actual file' }
  }
}
foreach ($entry in $deliveries) {
  $raw = Get-Content -LiteralPath "specs/archive/tasks/$($entry[0]).md" -Raw
  $r = [regex]::Match($raw,'(?ms)^<!-- remote-lifecycle-receipt -->\r?\n```json\r?\n(.*?)^```[ \t]*$').Groups[1].Value | ConvertFrom-Json
  $dir = "remote-delivery/$($entry[0])"
  if ($entry[1] -eq 304) { $dir = 'evidence-T1-SAFE-MEDIA-LOGGING/remote' }
  $prFile = if ($entry[1] -eq 304) { "$dir/ship-attempt-02/pr304-merged.json" } else { "$dir/pre-cleanup-pr.json" }
  $r3File = if ($entry[1] -eq 304) { "$dir/ship-attempt-02/review/$($entry[0]).json" } else { "$dir/final-review.json" }
  $cleanupFile = if ($entry[1] -eq 304) { "$dir/root-cleanup-audit/cleanup-result.json" } else { "$dir/cleanup-result.json" }
  $pr = Read-Original $prFile
  foreach ($field in @('url','state','headRefOid','mergeCommit','mergedAt')) { Assert-Original $r.pr.$field $pr.$field }
  $number = if ($entry[1] -eq 304) { (Read-Original "$dir/root-cleanup-audit/audit.json").pr } else { $pr.number }
  Assert-Original $r.pr.number $number
  Assert-Original $r.formalR3 (Read-Original $r3File)
  Assert-Original $r.cleanup (Read-Original $cleanupFile)
  $ci = Read-Original "$receiptDir/ci-$($entry[1]).json"
  foreach ($field in @('databaseId','headSha','conclusion','status','event')) { Assert-Original $r.candidateCI.$field $ci.$field }
  if (@($ci.jobs).Count -ne @($r.candidateCI.jobs).Count) { throw '[R5-EVIDENCE] CI job count differs' }
  foreach ($job in $r.candidateCI.jobs) {
    $original = @($ci.jobs | Where-Object { [string]::Equals($_.name,$job.name,[StringComparison]::Ordinal) })
    if ($original.Count -ne 1) { throw '[R5-EVIDENCE] CI job identity differs' }
    foreach ($field in @('name','status','conclusion')) { Assert-Original $job.$field $original[0].$field }
  }
  if ($entry[1] -ne 304) {
    Assert-ChildManifest "$dir/pre-cleanup-evidence-manifest.json" $dir $r.evidenceAudit.verifiedEntries $r.evidenceAudit.manifestSha256
  } else {
    Assert-Original $r.evidenceAudit (Read-Original "$dir/root-evidence-audit.json")
    Assert-Original $r.cleanupAudit (Read-Original "$dir/root-cleanup-audit/audit.json")
    Assert-ChildManifest "$dir/evidence-manifest.json" "$dir/validation" 119 '8AC9B7CBAD51E0F1DA120D7F1E1F4BB34E4E15F70959EC76D94D072A4A4B6FCB'
    foreach ($m in $r.cleanupAudit.manifests) {
      $subdir = $m.file.Replace('-manifest.json','')
      Assert-ChildManifest "$dir/$($m.file)" "$dir/$subdir" $m.count $m.sha256
    }
  }
}
Write-Output '[R5-EVIDENCE-PASS] 332 original files, six child manifests and three complete lifecycle source bindings verified.'

$expected = @{
  'specs/archive/tasks/T0-REMOTE-PRODUCT-CARDS.md' = '2301C039BFABE1A561E3EDE3B0EF507BDE8A718A52EAD39AA24EB207B447F590'
  'specs/archive/tasks/T0-REMOTE-PREREVIEW-CARDS.md' = '4EB92457FA049CAEB9B1096E4710770292B317353C36F8F001847250A09DC9D6'
  'specs/archive/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md' = '644E2B2F90E347D96900FC8CB537E9D28D31245337D6512F7402905E6C6CBD60'
  'specs/archive/cards-index.md' = '0D04A7336659B24DFB834AA81258EBB05D06CBD9DE51AF6D5E719E0DA3EC40DB'
  'CLAUDE.md' = 'AB683B83A5018FDC378E803DAD2398276C30EAAC5ADACF40B31699393FD9802F'
  'docs/SECURITY.md' = '3D87538721CB50BBC06C3B41390C0D07C3DE452151EC917662263FA57EBEF646'
  'docs/TASK-BOARD.md' = 'C08067BE1FD2BD021042F188F2FD46224E6F54C5204436E8C8BA2465BCB90D7A'
}
$utf8 = [Text.UTF8Encoding]::new($false,$true)
foreach ($pair in $expected.GetEnumerator()) {
  if (-not (Test-Path -LiteralPath $pair.Key -PathType Leaf)) { throw "[R5-PAYLOAD] Missing approved payload: $($pair.Key)" }
  $text = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path $PWD $pair.Key))).Replace("`r`n","`n")
  $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($text)))
  if ($actual -cne $pair.Value) { throw "[R5-PAYLOAD] Approved payload differs: $($pair.Key)" }
}
foreach ($id in @('T0-REMOTE-PRODUCT-CARDS','T0-REMOTE-PREREVIEW-CARDS','T1-SAFE-MEDIA-LOGGING-REMOTE')) {
  if (Test-Path -LiteralPath "specs/tasks/$id.md") { throw "[R5-STATUS] Completed active card remains: $id" }
  if ((Get-Content -LiteralPath "specs/archive/tasks/$id.md" -Raw) -cnotmatch '(?m)^status: merged\r?$') { throw "[R5-STATUS] Archive not merged: $id" }
}
foreach ($id in @('T1-STORAGE-PATH-BOUNDARY-REMOTE','T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE','T3-PDF-PAGINATION-FIXTURES-REMOTE')) {
  if ((Get-Content -LiteralPath "specs/tasks/$id.md" -Raw) -cnotmatch '(?m)^status: todo\r?$') { throw "[R5-STATUS] Pending successor changed: $id" }
}
if ((Get-Content -LiteralPath 'specs/tasks/T0-REMOTE-ROUND1-CLOSURE.md' -Raw) -cnotmatch '(?m)^status: merged\r?$') { throw '[R5-STATUS] Closure transition missing' }
pwsh -NoProfile -File scripts/check-cards.ps1
if ($LASTEXITCODE -ne 0) { throw 'Card validation failed' }
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet
if ($LASTEXITCODE -ne 0) { throw 'Archive projection differs' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'Working diff whitespace failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff whitespace failed' }
Write-Output '[R5-CLOSURE-PASS] Three prior deliveries, seven approved payloads and three pending successors verified.'
```
