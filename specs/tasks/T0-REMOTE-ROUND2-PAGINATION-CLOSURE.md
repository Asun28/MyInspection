---
id: T0-REMOTE-ROUND2-PAGINATION-CLOSURE
title: Close remote Pagination R5 from the observed PR and preserved proof
status: todo
depends_on: [T0-REMOTE-ROUND2-BOUNDARY-CLOSURE, T3-PDF-PAGINATION-FIXTURES-REMOTE]
parallelizable_with: []
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND2-PAGINATION-CLOSURE.md
  - specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md
  - specs/archive/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - docs/adr/0007-report-interchange.md
forbid:
  - Product source, tests, scripts, configuration, schema, dependency, lesson or debt changes
  - Changing any pre-existing archived card or Boundary closure
  - Treating local Pagination evidence or the product PR merge alone as completed remote R5
  - Claiming measurement binding, renderer behavior, platform glyphs or device acceptance
  - Direct master push, history rewriting, bypassed remote gates or raised diff limits
non_goals:
  - Later PDF product cards or a reusable R5 engine
plan_ref: docs/adr/0007-report-interchange.md#fixed-height-pagination-fixtures-remote-publication
acceptance:
  - "A1 Preserve the full active remote Pagination alias as the archive copy, changing only status to merged and appending one portable observed lifecycle receipt. Its PR309, reviewed head, Sol/high first-pass R3, exact-head CI, merge, source blobs, four forced DoD stages, two named AssertionError mutants, restored production bytes and guarded cleanup must match copied original records."
  - "A2 Seed from the complete actual Boundary R5 merged remote base. Preserve every prior archive card byte-for-byte, add exactly this one alias, remove only its active copy, regenerate the archive index, and leave the closure card active."
  - "A3 Update CLAUDE current stage, the Pagination Task Board row and 4/10 products/2 of 5 rounds, and ADR-0007 remote fixture publication. Later binding and device claims stay pending."
  - "A4 Candidate DoD independently rehashes both copied proof sets, checks child manifests and raw XML, binds portable assertions to actual PR/R3/CI/cleanup and reviewed plus merged Git source blobs, verifies independently approved whole-card bytes, five fixed payloads, complete review-base diff whitespace (the contract binds actual R3 base `47b78af825c699608821eb75bfa77104ac4bb86b` while retaining the initial archive ancestor), card syntax and generated index. Normal verify, size, formal R3, exact-candidate CI, PR merge and guarded cleanup remain required."
dod_command: $raw = Get-Content -LiteralPath 'specs/tasks/T0-REMOTE-ROUND2-PAGINATION-CLOSURE.md' -Raw; $blocks = [regex]::Matches($raw, '(?ms)^```powershell\r?\n(.*?)^```[ \t]*$'); if ($blocks.Count -ne 1) { throw 'Expected one approved assertion block' }; & ([scriptblock]::Create($blocks[0].Groups[1].Value)); if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Verify independently approved whole-card bytes, saved Pagination lifecycle and copied proof, five fixed payloads, one-card archive delta and complete pinned-base diff. This does not query live GitHub or rerun historical tests.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata closure uses -SkipRed. Exercise negative changes to CI, cleanup, each mutant including duplicate IDs, raw XML/review, hidden proof leaves, both manifest sets, status, exact archive bytes, payload and archive count in a disposable projection before ship.
doc_sync: This closure itself becomes effective only on PR merge; retain its own R3/CI/merge and original-main cleanup receipt in the controller ledger. Keep the closure card active until ordinary archival maintenance.
---

# Pagination remote R5 closure

PR #309 merged at `553d53382f3b663dac19ed1c607ffa35ee499d0c` after the reviewed head `09dfa20cf8e75d095b8535a1473b17acdf581628`, first formal Sol/high PASS and exact-head CI run `35171143884` (`verify` and `required` success). The original-main cleanup recorded exit 0 and absent worktree/branch; its raw log also has deletion warnings. A separate 2026-09-18 UTC observation confirms present absence, without asserting when those warnings resolved. Product R5 remains pending until this metadata PR itself passes and merges. No T35 exists because the original ship used authorized `-SkipRed`; no rounds file exists because the first formal R3 passed and the controller writes that file only on BLOCK. The full ship log and normalized review JSON carry that R3 result.

Whole-card approval binds the fixed original `D:/Projects/MyInspection` master latest own-path sole-path commit, blob and raw bytes to root `pagination-own-card-approval/approval.json` and `approved-card.md`. Candidate bytes cannot approve themselves. This closure takes effect on PR merge. First-ship preparation allows 48,000 complete-diff characters with +25% reserve under the unchanged 60,000 hard limit.

The archive appendix must embed **exactly one** `pagination-remote-r5-observed-v1` JSON receipt copied from `portable-lifecycle.json` in this preparation directory. It adds observed remote facts without deleting the original local implementation record or acceptance. The reviewer-visible ignored proof root is `_local/rotating-card-orchestrator/round2-r5-proof/pagination/original/`, with the original `final-ship/`, `root-cleanup-audit/`, four stage receipts/counts/XML trees, and `r4-20260917-131159/` beneath it. Its outer `copy-manifest.json` must list all 242 copied relative leaves with byte length and SHA-256; it must exclude itself. A separate reviewer-visible `_local/rotating-card-orchestrator/round2-r5-proof/product-cleanup-current-20260918T0118/` copy carries the 14-leaf current-absence observation pinned by SHA-256 `225B747F54AD0C180BD35A387C14682D7A33A611BB87CC9EFFF365C6C61E8C6D`. The original cleanup log contains `Filename too long` and `not a working tree` warnings; its exit-0/absence JSON did not preserve a raw post-list at that time. The later UTC 2026-09-18 01:16:43 observation proves current absence only, not when or how the earlier deletion errors resolved. Copy both proof trees before R3; a named path without reviewer-visible files is insufficient.



```powershell
$ErrorActionPreference = 'Stop'
$archiveBase = '2c2160562f0949f4eb66744d1571620046133c2a'
$reviewBase = '47b78af825c699608821eb75bfa77104ac4bb86b'
$archiveBeforeText = '198'
$proofManifestHash = '0288803F8C605F7E6EB8D6DD1126679B49ACEE0E78FD7DCD5BE387992D727B9E'
$payload = @{
  'specs/archive/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md' = '99600BB07901B82EB8539C1527D2955334DC541862AFD2C97033FBE5E577BBCE'
  'specs/archive/cards-index.md' = 'AD0329E6ED9E7D4A7D5946EA3887C7A871300FCB0DE60AAA18A3052E52D5037A'
  'CLAUDE.md' = '9896CCD518B49A9ED1640BD6593A2A0FC9CEBB662673ED7FAFCAF1ECB5A68D14'
  'docs/TASK-BOARD.md' = 'AD2C5652DDBCAB880D3A505DF13C539C28001F80186091D50BD68615A322C222'
  'docs/adr/0007-report-interchange.md' = 'F55769E5191F77DEB56B68DB368888E3348858678DE2C2F09D1174D744C2A8D0'
}
if ($archiveBase -notmatch '^[0-9a-f]{40}$' -or $archiveBeforeText -notmatch '^[1-9][0-9]*$' -or $proofManifestHash -notmatch '^[0-9A-F]{64}$' -or @($payload.Values | Where-Object { $_ -notmatch '^[0-9A-F]{64}$' }).Count -ne 0) { throw '[R5-PREP] Resolve actual base, count and approved hashes' }
$archiveBefore = [int]$archiveBeforeText
function Eq($a,$b,$label) { if (-not [string]::Equals([string]$a,[string]$b,[StringComparison]::Ordinal)) { throw "[R5-BINDING] $label" } }
function Sha($path) { if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "[R5-PROOF] Missing $path" }; (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function J($path) { Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -Depth 100 }
function GitBytes($revision,$path) {
  $start = [Diagnostics.ProcessStartInfo]::new('git')
  $start.ArgumentList.Add('show'); $start.ArgumentList.Add(('{0}:{1}' -f $revision,$path))
  $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true; $start.UseShellExecute = $false
  $process = [Diagnostics.Process]::Start($start); $buffer = [IO.MemoryStream]::new()
  try { $process.StandardOutput.BaseStream.CopyTo($buffer); $process.WaitForExit(); if ($process.ExitCode -ne 0) { throw '[R5-ARCHIVE] Cannot read base alias blob' }; return ,$buffer.ToArray() }
  finally { $buffer.Dispose(); $process.Dispose() }
}
$proof = '_local/rotating-card-orchestrator/round2-r5-proof/pagination/original'
$manifestPath = "$proof/copy-manifest.json"
Eq (Sha $manifestPath) $proofManifestHash 'outer proof manifest hash'
$manifest = J $manifestPath
if ($manifest.files -ne 242 -or @($manifest.entries).Count -ne 242) { throw '[R5-PROOF] Expected exactly 242 copied leaves' }
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $manifest.entries) {
  $rel = [string]$entry.path
  if ($rel -match '^([A-Za-z]:|[\\/])' -or $rel -match '(^|[\\/])\.\.([\\/]|$)' -or -not $seen.Add($rel)) { throw '[R5-PROOF] Unsafe or duplicate manifest path' }
  $file = Join-Path $proof $rel
  if (([IO.File]::ReadAllBytes((Join-Path $PWD $file))).Length -ne $entry.bytes) { throw "[R5-PROOF] Byte length: $rel" }
  Eq (Sha $file) $entry.sha256 "manifest member $rel"
}
$actualLeaves = @(Get-ChildItem -LiteralPath $proof -Recurse -File -Force | ForEach-Object { [IO.Path]::GetRelativePath((Join-Path $PWD $proof),$_.FullName).Replace('\','/') } | Where-Object { $_ -cne 'copy-manifest.json' })
if ($actualLeaves.Count -ne 242 -or $actualLeaves.Count -ne $seen.Count) { throw '[R5-PROOF] Outer manifest omits or invents copied leaves' }
foreach ($leaf in $actualLeaves) { if (-not $seen.Contains($leaf)) { throw "[R5-PROOF] Unlisted copied leaf $leaf" } }
foreach ($needed in @('final-ship/manifest.json','final-evidence.json','root-cleanup-audit/audit.json','root-cleanup-audit/cleanup-result.json','root-cleanup-audit/final-xml-manifest.json','r4-20260917-131159/M1.xml','r4-20260917-131159/M2.xml')) { if (-not $seen.Contains($needed)) { throw "[R5-PROOF] Omitted $needed" } }
Eq (Sha "$proof/final-ship/manifest.json") '5A96ECE0F1C39EF0B29930C087368C6E2279610640E4713B2E149772BB5D7C37' 'original ship manifest'
Eq (Sha "$proof/final-evidence.json") 'BF2474CA192E0B80EA9722AFC5E53D38511C346724110227AA07D249355BD411' 'original final evidence'
Eq (Sha "$proof/root-cleanup-audit/final-xml-manifest.json") 'CE458E0FFC0917CD216B2D178EAC28D566965981607BB5C22D6304DC382E88BA' 'final XML manifest'
$shipManifest = J "$proof/final-ship/manifest.json"
if ($shipManifest.files -ne 10 -or @($shipManifest.entries).Count -ne 10) { throw '[R5-PROOF] Original ship manifest count' }
foreach ($entry in $shipManifest.entries) { $file = "$proof/final-ship/$($entry.path)"; if (([IO.File]::ReadAllBytes((Join-Path $PWD $file))).Length -ne $entry.bytes) { throw '[R5-PROOF] Original ship member length' }; Eq (Sha $file) $entry.sha256 "original ship member $($entry.path)" }
$shipActual = @(Get-ChildItem -LiteralPath "$proof/final-ship" -Recurse -File -Force | ForEach-Object { [IO.Path]::GetRelativePath((Join-Path $PWD "$proof/final-ship"),$_.FullName).Replace('\','/') } | Where-Object { $_ -cne 'manifest.json' })
$shipListed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $shipManifest.entries) { if (-not $shipListed.Add([string]$entry.path)) { throw '[R5-PROOF] Duplicate ship member' } }
if ($shipActual.Count -ne 10 -or $shipListed.Count -ne 10) { throw '[R5-PROOF] Ship member set count' }
foreach ($path in $shipActual) { if (-not $shipListed.Contains($path)) { throw "[R5-PROOF] Unlisted ship member $path" } }
$xmlManifest = J "$proof/root-cleanup-audit/final-xml-manifest.json"
if (@($xmlManifest).Count -ne 92) { throw '[R5-PROOF] Final XML count' }
foreach ($entry in $xmlManifest) { $file = "$proof/root-cleanup-audit/$($entry.path)"; Eq (Sha $file) $entry.sha256 "final XML $($entry.path)" }
$xmlActual = @(Get-ChildItem -LiteralPath "$proof/root-cleanup-audit/final-xml" -Recurse -File -Force | ForEach-Object { 'final-xml/' + [IO.Path]::GetRelativePath((Join-Path $PWD "$proof/root-cleanup-audit/final-xml"),$_.FullName).Replace('\','/') })
$xmlListed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $xmlManifest) { if (-not $xmlListed.Add([string]$entry.path)) { throw '[R5-PROOF] Duplicate final XML member' } }
if ($xmlActual.Count -ne 92 -or $xmlListed.Count -ne 92) { throw '[R5-PROOF] Final XML member set count' }
foreach ($path in $xmlActual) { if (-not $xmlListed.Contains($path)) { throw "[R5-PROOF] Unlisted final XML member $path" } }
$current = '_local/rotating-card-orchestrator/round2-r5-proof/product-cleanup-current-20260918T0118'
Eq (Sha "$current/copy-manifest.json") '225B747F54AD0C180BD35A387C14682D7A33A611BB87CC9EFFF365C6C61E8C6D' 'current cleanup proof manifest'
$currentManifest = J "$current/copy-manifest.json"
if (@($currentManifest.entries).Count -ne 14) { throw '[R5-CLEANUP] Expected 14 current proof leaves' }
$currentListed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($entry in $currentManifest.entries) { $path = [string]$entry.path; if (-not $currentListed.Add($path) -or $path -match '^([A-Za-z]:|[\\/])' -or $path -match '(^|[\\/])\.\.([\\/]|$)') { throw '[R5-CLEANUP] Unsafe or duplicate current proof path' }; $file = "$current/$path"; if (([IO.File]::ReadAllBytes((Join-Path $PWD $file))).Length -ne $entry.bytes) { throw "[R5-CLEANUP] Current proof length $path" }; Eq (Sha $file) $entry.sha256 "current proof $path" }
$currentActual = @(Get-ChildItem -LiteralPath $current -Recurse -File -Force | ForEach-Object { [IO.Path]::GetRelativePath((Join-Path $PWD $current),$_.FullName).Replace('\','/') } | Where-Object { $_ -cne 'copy-manifest.json' })
if ($currentActual.Count -ne 14) { throw '[R5-CLEANUP] Current proof leaf count' }
foreach ($path in $currentActual) { if (-not $currentListed.Contains($path)) { throw "[R5-CLEANUP] Unlisted current proof $path" } }
$currentId = 'T3-PDF-PAGINATION-FIXTURES-REMOTE'
$currentReceipt = J "$current/$currentId/receipt.json"; $currentFs = J "$current/$currentId/filesystem.json"
Eq $currentReceipt.id $currentId 'current cleanup id'; Eq ([DateTime]$currentReceipt.startedUtc).ToUniversalTime().ToString('o') '2026-09-18T01:16:43.3291573Z' 'current cleanup timestamp'
Eq $currentReceipt.repository 'D:/Projects/MyInspection' 'current cleanup repository'; Eq $currentFs.path "C:/wt/$currentId" 'current cleanup path'
if ($currentReceipt.worktreeExit -ne 0 -or $currentReceipt.branchExit -ne 0 -or $currentReceipt.pathRegistered -cne $false -or $currentReceipt.branchPresent -cne $false -or $currentReceipt.currentAbsenceVerified -cne $true -or $currentFs.testPath -cne $false -or $currentFs.directoryExists -cne $false -or $currentFs.fileExists -cne $false -or $null -ne $currentFs.item) { throw '[R5-CLEANUP] Current absence receipt/filesystem' }
foreach ($name in @('worktrees.stderr.txt','branch.stdout.txt','branch.stderr.txt')) { if (([IO.File]::ReadAllBytes((Join-Path $PWD "$current/$currentId/$name"))).Length -ne 0) { throw "[R5-CLEANUP] Nonempty current raw $name" } }
$worktreesRaw = Get-Content -LiteralPath "$current/$currentId/worktrees.stdout.txt" -Raw
if ([regex]::IsMatch($worktreesRaw,'(?m)^(worktree C:/wt/T3-PDF-PAGINATION-FIXTURES-REMOTE|branch refs/heads/T3-PDF-PAGINATION-FIXTURES-REMOTE)\r?$')) { throw '[R5-CLEANUP] Product remains in raw worktree list' }
$currentResult = @(J "$current/result.json" | Where-Object { $_.id -ceq $currentId }); if ($currentResult.Count -ne 1 -or $currentResult[0].currentAbsenceVerified -cne $true) { throw '[R5-CLEANUP] Current result mismatch' }
$alias = 'T3-PDF-PAGINATION-FIXTURES-REMOTE'
$archive = "specs/archive/tasks/$alias.md"
$raw = Get-Content -LiteralPath $archive -Raw
$receiptMatches = [regex]::Matches($raw, '(?ms)^```json\r?\n(\{.*?"schema"\s*:\s*"pagination-remote-r5-observed-v1".*?\})\r?\n```[ \t]*$')
if ($receiptMatches.Count -ne 1) { throw '[R5-BINDING] Expected one portable lifecycle receipt' }
$r = $receiptMatches[0].Groups[1].Value | ConvertFrom-Json -Depth 100
Eq $r.id $alias 'receipt id'
$finalGroups = @{report=@{suites=0;tests=0;failures=0;errors=0;skips=0};e2e=@{suites=0;tests=0;failures=0;errors=0;skips=0};fullCore=@{suites=0;tests=0;failures=0;errors=0;skips=0}}
foreach ($entry in $xmlManifest) { [xml]$fx = Get-Content -LiteralPath "$proof/root-cleanup-audit/$($entry.path)" -Raw; $suite = $fx.DocumentElement; $cases = @($suite.SelectNodes('./testcase')); $failures = @($suite.SelectNodes('./testcase/failure')); $errors = @($suite.SelectNodes('./testcase/error')); $skips = @($suite.SelectNodes('./testcase/skipped')); if ($cases.Count -ne [int]$suite.GetAttribute('tests') -or $failures.Count -ne [int]$suite.GetAttribute('failures') -or $errors.Count -ne [int]$suite.GetAttribute('errors') -or $skips.Count -ne [int]$suite.GetAttribute('skipped')) { throw "[R5-TEST] Final raw XML node/attribute mismatch: $($entry.path)" }; $groups = @('fullCore'); if ($entry.path -like 'final-xml/e2eTest/*') { $groups = @('e2e') }; if ($entry.suite -like 'nz.myinspection.core.report.*') { $groups += 'report' }; foreach ($group in $groups) { $g = $finalGroups[$group]; $g.suites++; $g.tests += $cases.Count; $g.failures += $failures.Count; $g.errors += $errors.Count; $g.skips += $skips.Count } }
foreach ($group in $finalGroups.Keys) { foreach ($field in @('suites','tests','failures','errors','skips')) { if ($finalGroups[$group][$field] -ne $r.finalXml.$group.$field) { throw "[R5-TEST] Final raw XML $group $field" } } }
$pr = J "$proof/final-ship/pr-309.json"; $review = J "$proof/final-ship/review/$alias.json"; $ci = J "$proof/final-ship/ci-35171143884.json"
$delivery = J "$proof/final-ship/delivery.json"; $identity = J "$proof/final-ship/commit-identity.json"
$audit = J "$proof/root-cleanup-audit/audit.json"; $cleanup = J "$proof/root-cleanup-audit/cleanup-result.json"
if ($pr.number -ne 309 -or $pr.state -cne 'MERGED' -or $r.productPr.number -ne 309 -or $r.productPr.state -cne 'MERGED') { throw '[R5-BINDING] PR309 state' }
foreach ($pair in @(@($r.productPr.reviewedHead,$pr.headRefOid),@($r.productPr.mergeOid,$pr.mergeCommit.oid),@($r.productPr.url,$pr.url),@($r.productPr.reviewedHead,$delivery.formalShipHead),@($r.productPr.mergeOid,$delivery.remoteSquash),@($r.productPr.reviewedHead,$audit.head),@($r.productPr.mergeOid,$audit.merge),@($r.productPr.reviewedHead,$cleanup.head),@($r.productPr.mergeOid,$cleanup.merge))) { Eq $pair[0] $pair[1] 'PR/head/merge cross-record' }
if ($review.verdict -cne 'pass' -or @($review.reasons).Count -ne 0 -or $delivery.formalReview.rounds -ne 1 -or $r.formalR3.rounds -ne 1 -or @($r.formalR3.reasons).Count -ne 0) { throw '[R5-BINDING] Formal first R3' }
Eq $review.sha $r.productPr.reviewedHead 'review head'; Eq $r.formalR3.verdict $review.verdict 'receipt review'
Eq $r.formalR3.model 'Sol/high' 'formal R3 model'
$shipLog = Get-Content -LiteralPath "$proof/final-ship/formal-ship.log" -Raw
foreach ($pattern in @('(?m)^model: gpt-5\.6-sol\r?$','(?m)^reasoning effort: high\r?$','(?m)^裁决: pass\r?$','(?m)^\[CI-GATE-PASS\] #309/09dfa20cf8e75d095b8535a1473b17acdf581628 \[required,verify\]\r?$')) { if ([regex]::Matches($shipLog,$pattern).Count -ne 1) { throw '[R5-BINDING] Raw formal ship model/verdict/CI' } }
if ($ci.databaseId -ne 35171143884 -or $ci.headSha -cne $r.productPr.reviewedHead -or $ci.event -cne 'pull_request' -or $ci.conclusion -cne 'success' -or $ci.status -cne 'completed') { throw '[R5-BINDING] Exact-head CI' }
foreach ($name in @('verify','required')) { $jobs = @($ci.jobs | Where-Object { $_.name -ceq $name }); if ($jobs.Count -ne 1 -or $jobs[0].conclusion -cne 'success' -or $jobs[0].status -cne 'completed') { throw "[R5-BINDING] CI $name" } }
$receiptJobs = @($r.candidateCI.jobs | Sort-Object); if (($receiptJobs -join ',') -cne 'required,verify') { throw '[R5-BINDING] Portable CI job names' }
$shipExit = J "$proof/final-ship/formal-ship-exit.json"; if ($shipExit.exit -ne 0 -or $shipExit.command -cnotmatch ' -SkipRed$') { throw '[R5-BINDING] Formal ship exit/SkipRed' }
if ($cleanup.exit -ne 0 -or $cleanup.worktreeAbsent -cne $true -or $cleanup.branchAbsent -cne $true -or $audit.mergeTokenVerified -cne $true -or $audit.worktreeClean -cne $true -or $audit.SkipRed -cne $true -or $audit.T35Present -cne $false -or $audit.roundsFilePresent -cne $false) { throw '[R5-BINDING] Cleanup/SkipRed' }
if ($r.candidateCI.run -ne $ci.databaseId -or $r.candidateCI.head -cne $ci.headSha -or $r.candidateCI.event -cne $ci.event -or $r.candidateCI.conclusion -cne $ci.conclusion -or $r.ship.exit -ne 0 -or $r.ship.skipRed -cne $true -or $r.ship.t35Present -cne $false -or $r.ship.roundsFilePresent -cne $false -or $r.cleanup.exit -ne $cleanup.exit -or $r.cleanup.worktreeAbsent -cne $cleanup.worktreeAbsent -or $r.cleanup.branchAbsent -cne $cleanup.branchAbsent -or $r.cleanup.mergeTokenVerified -cne $audit.mergeTokenVerified) { throw '[R5-BINDING] Portable CI/ship/cleanup drift' }
Eq $r.proofPins.finalShipManifestSha256 (Sha "$proof/final-ship/manifest.json") 'receipt ship manifest pin'; Eq $r.proofPins.finalEvidenceSha256 (Sha "$proof/final-evidence.json") 'receipt final evidence pin'; Eq $r.proofPins.finalXmlManifestSha256 (Sha "$proof/root-cleanup-audit/final-xml-manifest.json") 'receipt final XML pin'
if (@($r.sources).Count -ne 2 -or $audit.sourcePins.Count -ne 2) { throw '[R5-BINDING] Source pin count' }
foreach ($i in 0..1) { $pin = $r.sources[$i]; Eq $pin.path $audit.sourcePins[$i].path 'source path'; Eq $pin.sha256 $audit.sourcePins[$i].sha256 'source SHA'; Eq $pin.blob $audit.sourcePins[$i].blob 'source blob'; foreach ($commit in @($r.productPr.reviewedHead,$r.productPr.mergeOid)) { $blob = git rev-parse ('{0}:{1}' -f $commit,$pin.path); if ($LASTEXITCODE -ne 0) { throw '[R5-BINDING] Missing reviewed or merged source' }; Eq $blob $pin.blob 'reviewed/merged source blob' }; $name = if ($i -eq 0) {'ReportComposer.kt'} else {'ReportComposerPaginationTest.kt'}; $copiedBlob = git hash-object "$proof/final-ship/source/$name"; if ($LASTEXITCODE -ne 0) { throw '[R5-BINDING] Cannot hash copied source' }; Eq $copiedBlob $pin.blob 'copied source Git blob' }
if ($identity.source.unchanged -cne $true -or $identity.source.baseBlob -cne $identity.source.headBlob -or $identity.source.headBlob -cne $r.sources[0].blob -or $identity.test.headBlob -cne $r.sources[1].blob -or $identity.diff.changedLines -ne 53 -or $identity.diff.paths.Count -ne 1 -or $identity.diff.paths[0] -cne $r.sources[1].path) { throw '[R5-BINDING] Test-only committed source identity' }
$e = J "$proof/final-evidence.json"
foreach ($stage in $r.stages) {
  $n = [string]$stage.name
  if ($stage.reportSuites -ne 21 -or $stage.reportTests -ne 255 -or $stage.e2eSuites -ne 3 -or $stage.e2eTests -ne 6 -or $stage.failures -ne 0 -or $stage.errors -ne 0 -or $stage.skips -ne 0) { throw "[R5-TEST] Portable $n totals" }
  foreach ($type in @('report','e2e')) { $strict = J "$proof/$n-$type-strict.json"; $wantSuites = if ($type -ceq 'report') {21} else {3}; $wantTests = if ($type -ceq 'report') {255} else {6}; if ($strict.suiteCount -ne $wantSuites -or $strict.testcaseCount -ne $wantTests -or $strict.failures -ne 0 -or $strict.errors -ne 0 -or $strict.skips -ne 0 -or @($strict.files).Count -ne $wantSuites) { throw "[R5-TEST] $n $type strict counts" }; foreach ($item in $strict.files) { Eq (Sha "$proof/$n-$type-results/$($item.file)") $item.sha256 "strict XML $n $type $($item.file)" } }
  $phase = @($e.phases | Where-Object { $_.phase -ceq $n }); if ($phase.Count -ne 1 -or $phase[0].reportExit -ne 0 -or $phase[0].e2eExit -ne 0) { throw "[R5-TEST] $n exit" }
  foreach ($type in @('report','e2e')) { $files = @(Get-ChildItem -LiteralPath "$proof/$n-$type-results" -Filter '*.xml' -File -Force); $wantSuites = if ($type -ceq 'report') {21} else {3}; $wantTests = if ($type -ceq 'report') {255} else {6}; if ($files.Count -ne $wantSuites) { throw "[R5-TEST] $n $type suites" }; $sum = 0; foreach ($f in $files) { [xml]$x = Get-Content -LiteralPath $f.FullName -Raw; $suite = $x.DocumentElement; $cases = @($suite.SelectNodes('./testcase')); $failures = @($suite.SelectNodes('./testcase/failure')); $errors = @($suite.SelectNodes('./testcase/error')); $skips = @($suite.SelectNodes('./testcase/skipped')); if ($cases.Count -ne [int]$suite.GetAttribute('tests') -or $failures.Count -ne 0 -or $failures.Count -ne [int]$suite.GetAttribute('failures') -or $errors.Count -ne 0 -or $errors.Count -ne [int]$suite.GetAttribute('errors') -or $skips.Count -ne 0 -or $skips.Count -ne [int]$suite.GetAttribute('skipped')) { throw "[R5-TEST] $n $type raw XML node/attribute mismatch: $($f.Name)" }; $sum += $cases.Count }; if ($sum -ne $wantTests) { throw "[R5-TEST] $n $type tests" } }
}
if (@($r.stages).Count -ne 4 -or (@($r.stages | ForEach-Object name) -join ',') -cne 'baseline,migrated,restored,post-tail' -or $e.production.diffEmpty -cne $true -or $e.candidate.diffCheckExit -ne 0) { throw '[R5-TEST] Stage/mutation/scope total' }
$mutationIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($m in $r.mutations) { if (-not $mutationIds.Add([string]$m.id)) { throw '[R5-MUTATION] Duplicate mutation id' } }
if (@($r.mutations).Count -ne 2 -or -not $mutationIds.Contains('M1') -or -not $mutationIds.Contains('M2')) { throw '[R5-MUTATION] Expected M1 and M2 exactly once' }
foreach ($m in $r.mutations) { $mr = J "$proof/r4-20260917-131159/$($m.id).json"; [xml]$mx = Get-Content -LiteralPath "$proof/r4-20260917-131159/$($m.id).xml" -Raw; Eq $m.id $mr.id 'mutant id'; Eq $m.test $mr.test 'mutant test'; Eq $m.failure $mr.failureMessage 'mutant failure'; Eq $m.mutantSha256 $mr.mutantSha256 'mutant source'; Eq $mr.sourceSha256 $r.sources[0].sha256 'mutant original source'; if ($mr.exitCode -ne 1 -or $mx.testsuite.tests -ne '1' -or $mx.testsuite.failures -ne '1' -or $mx.testsuite.errors -ne '0' -or $mx.testsuite.testcase.name -cne $m.test -or $mx.testsuite.testcase.failure.type -cne 'java.lang.AssertionError') { throw "[R5-MUTATION] $($m.id) raw XML" }; Eq $mx.testsuite.testcase.failure.message $m.failure 'raw named failure' }
$r4end = J "$proof/r4-20260917-131159/attempt-end.json"; Eq $r4end.sourceSha256After $r.sources[0].sha256 'restored production'; if ($r4end.exit -ne 0) { throw '[R5-MUTATION] R4 exit' }
if ($audit.finalXml.report.tests -ne 255 -or $audit.finalXml.e2e.tests -ne 6 -or $audit.finalXml.fullCore.tests -ne 976 -or $audit.finalXml.fullCore.skips -ne 4) { throw '[R5-TEST] Final ship XML totals' }
$utf8 = [Text.UTF8Encoding]::new($false)
foreach ($pair in $payload.GetEnumerator()) { $bytes = [IO.File]::ReadAllBytes((Join-Path $PWD $pair.Key)); $lf = $utf8.GetString($bytes).Replace("`r`n","`n"); $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($lf))); Eq $hash $pair.Value "approved payload $($pair.Key)" }
function Need($ok,$label) { if (-not $ok) { throw "[R5-AUTHORITY] $label" } }
function SetEq($a,$b,$label) { $x=@($a|Sort-Object -CaseSensitive); $y=@($b|Sort-Object -CaseSensitive); if ($x.Count -ne $y.Count -or ($x -join "`n") -cne ($y -join "`n")) { throw "[R5-AUTHORITY] $label" } }
# BEGIN independent own-card approval guard
$authority='D:/Projects/MyInspection'
$approvalRoot='D:/Projects/MyInspection/_local/rotating-card-orchestrator/pagination-own-card-approval'
$own='specs/tasks/T0-REMOTE-ROUND2-PAGINATION-CLOSURE.md'
$approvalScope=@($own,'specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md','specs/archive/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md','specs/archive/cards-index.md','CLAUDE.md','docs/TASK-BOARD.md','docs/adr/0007-report-interchange.md')
$approval=Get-Content -LiteralPath "$approvalRoot/approval.json" -Raw | ConvertFrom-Json -AsHashtable
Eq $approval.schemaVersion 1 'approval schema'; Eq $approval.task 'T0-REMOTE-ROUND2-PAGINATION-CLOSURE' 'approval task'
Eq $approval.repository $authority 'fixed approval repository'; Eq $approval.ref 'refs/heads/master' 'fixed approval ref'; Eq $approval.path $own 'fixed approval path'
SetEq @($approval.allow_paths) $approvalScope 'approved seven-path scope'; Need ($approval.rootAuthorization -is [string] -and ![string]::IsNullOrWhiteSpace($approval.rootAuthorization)) 'root authorization evidence'
function ApprovalGit([string[]]$arguments) { $value=@(& git.exe -C $authority @arguments); Need ($LASTEXITCODE -eq 0) 'independent approval Git read'; return ($value -join "`n") }
$approvalCommit=ApprovalGit @('log','-1','--format=%H','refs/heads/master','--',$own)
Need ($approvalCommit -cmatch '^[0-9a-f]{40}$') 'latest master path commit'; Eq $approval.commit $approvalCommit 'approval record equals latest independent path commit'
& git.exe -C $authority merge-base --is-ancestor $approvalCommit refs/heads/master; Need ($LASTEXITCODE -eq 0) 'approval commit on independent master'
Need ((ApprovalGit @('rev-list','--parents','-n','1',$approvalCommit)).Split(' ').Count -eq 2) 'single-parent approval commit'
SetEq @((ApprovalGit @('diff-tree','--no-commit-id','--name-only','-r',$approvalCommit)).Split("`n")) @($own) 'approval commit changes only own card'
$approvalBlob=ApprovalGit @('rev-parse',('{0}:{1}' -f $approvalCommit,$own)); Need ($approvalBlob -cmatch '^[0-9a-f]{40}$') 'approval blob object'; Eq $approval.blob $approvalBlob 'record approval blob'
$start=[Diagnostics.ProcessStartInfo]::new(); $start.FileName='git.exe'; $start.UseShellExecute=$false; $start.CreateNoWindow=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
foreach($arg in @('-C',$authority,'cat-file','blob',$approvalBlob)){[void]$start.ArgumentList.Add($arg)}
$process=[Diagnostics.Process]::new(); $process.StartInfo=$start; $buffer=[IO.MemoryStream]::new()
try { Need ($process.Start()) 'read approved raw blob'; $stderr=$process.StandardError.ReadToEndAsync(); $process.StandardOutput.BaseStream.CopyTo($buffer); $process.WaitForExit(); Need ($process.ExitCode -eq 0) 'approved blob read exit'; Eq $stderr.GetAwaiter().GetResult() '' 'approved blob read stderr'; $approvedBytes=$buffer.ToArray() } finally { $buffer.Dispose(); $process.Dispose() }
$approvalSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($approvedBytes)); Eq $approval.sha256 $approvalSha 'record approved raw SHA'
Eq ([Convert]::ToBase64String([IO.File]::ReadAllBytes("$approvalRoot/approved-card.md"))) ([Convert]::ToBase64String($approvedBytes)) 'root approved raw card equals committed blob'
Eq ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $PWD $own)))) ([Convert]::ToBase64String($approvedBytes)) 'whole candidate equals independently committed approved card'
# END independent own-card approval guard
if (Test-Path -LiteralPath "specs/tasks/$alias.md") { throw '[R5-ARCHIVE] Active alias remains' }
if ($raw -cnotmatch '(?m)^status: merged\r?$') { throw '[R5-ARCHIVE] Alias status' }
$oldAliasBytes = GitBytes $archiveBase "specs/tasks/$alias.md"
$strictUtf8 = [Text.UTF8Encoding]::new($false,$true)
$oldAlias = $strictUtf8.GetString($oldAliasBytes)
if ([regex]::Matches($oldAlias,'(?m)^status: todo\r?$').Count -ne 1) { throw '[R5-ARCHIVE] Base alias status' }
$preserved = [regex]::Replace($oldAlias,'(?m)^status: todo(?=\r?$)','status: merged')
if (-not $raw.StartsWith($preserved,[StringComparison]::Ordinal)) { throw '[R5-ARCHIVE] Original alias bytes changed' }
$appendix = $raw.Substring($preserved.Length)
if (-not $appendix.StartsWith("`n## Remote delivery receipt`n`n",[StringComparison]::Ordinal)) { throw '[R5-ARCHIVE] Unexpected archive suffix' }
$appendixBytes = $strictUtf8.GetBytes($appendix.Substring(1))
Eq ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($appendixBytes))) '26F2F9E7E2EF0E95230BDFA0A29767FFAA25265C6D5B47CD5FDB59ADA2B20F66' 'exact approved appendix'
if (-not [Linq.Enumerable]::SequenceEqual([byte[]]([IO.File]::ReadAllBytes((Join-Path $PWD $archive))),[byte[]]($strictUtf8.GetBytes($preserved + $appendix)))) { throw '[R5-ARCHIVE] Original alias byte relation changed' }
$old = @(git ls-tree -r --name-only $archiveBase -- specs/archive/tasks/); if ($LASTEXITCODE -ne 0 -or $old.Count -ne $archiveBefore) { throw '[R5-ARCHIVE] Actual base archive count' }
$now = @(Get-ChildItem -LiteralPath 'specs/archive/tasks' -Filter '*.md' -File -Force); if ($now.Count -ne $archiveBefore + 1) { throw '[R5-ARCHIVE] Expected exactly one new card' }
foreach ($path in $old) { $orig = git rev-parse ('{0}:{1}' -f $archiveBase,$path); $current = git rev-parse ('HEAD:{0}' -f $path); if ($LASTEXITCODE -ne 0) { throw '[R5-ARCHIVE] Git blob lookup failed' }; Eq $current $orig "pre-existing archive $path"; git diff --quiet HEAD -- $path; if ($LASTEXITCODE -ne 0) { throw "[R5-ARCHIVE] Working archive changed: $path" } }
$owned = @('specs/tasks/T0-REMOTE-ROUND2-PAGINATION-CLOSURE.md','specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md','specs/archive/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md','specs/archive/cards-index.md','CLAUDE.md','docs/TASK-BOARD.md','docs/adr/0007-report-interchange.md')
$committed = @(git diff --no-renames --name-only "$reviewBase...HEAD" | Where-Object { $_ }); if ($LASTEXITCODE -ne 0) { throw '[R5-SCOPE] Committed diff lookup' }
$working = @(git diff --no-renames --name-only | Where-Object { $_ }); if ($LASTEXITCODE -ne 0) { throw '[R5-SCOPE] Working diff lookup' }
$staged = @(git diff --cached --no-renames --name-only | Where-Object { $_ }); if ($LASTEXITCODE -ne 0) { throw '[R5-SCOPE] Staged diff lookup' }
$untracked = @(git ls-files --others --exclude-standard | Where-Object { $_ }); if ($LASTEXITCODE -ne 0) { throw '[R5-SCOPE] Untracked diff lookup' }
$changed = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($path in @($committed + $working + $staged + $untracked)) { [void]$changed.Add([string]$path) }
if ($changed.Count -ne $owned.Count) { throw '[R5-SCOPE] Expected seven owned paths' }
foreach ($path in $owned) { if (-not $changed.Contains($path)) { throw "[R5-SCOPE] Missing $path" } }
if ((Get-Content -LiteralPath 'docs/TASK-BOARD.md' -Raw) -cnotmatch '4/10' -or (Get-Content -LiteralPath 'docs/TASK-BOARD.md' -Raw) -cnotmatch '2/5') { throw '[R5-STATUS] Round totals' }
pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { throw '[R5-STATUS] Card validation' }
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { throw '[R5-ARCHIVE] Generated index' }
git merge-base --is-ancestor $reviewBase HEAD; if ($LASTEXITCODE -ne 0) { throw '[R5-REVIEWBASE] Actual R3 base not ancestor' }
git diff --check "$reviewBase...HEAD"; if ($LASTEXITCODE -ne 0) { throw '[R5-REVIEWBASE] Complete actual R3 diff whitespace' }
git diff --check; if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Working diff whitespace' }
git diff --cached --check; if ($LASTEXITCODE -ne 0) { throw '[R5-DIFF] Staged diff whitespace' }
$resolvedReviewBase = git rev-parse $reviewBase; if ($LASTEXITCODE -ne 0) { throw '[R5-REVIEWBASE] Actual R3 base unavailable' }; Eq $resolvedReviewBase $reviewBase 'actual R3 base identity'
$reviewMergeBase = git merge-base $reviewBase HEAD; if ($LASTEXITCODE -ne 0 -or $reviewMergeBase -notmatch '^[0-9a-f]{40}$') { throw '[R5-REVIEWBASE] Merge base unavailable' }
git merge-base --is-ancestor $reviewBase HEAD; if ($LASTEXITCODE -ne 0) { throw '[R5-REVIEWBASE] Candidate must non-rewriting align to actual R3 base before review' }
Eq $reviewMergeBase $reviewBase 'actual R3 base is the non-rewriting candidate ancestor'
git diff --check "$reviewBase...HEAD"; if ($LASTEXITCODE -ne 0) { throw '[R5-REVIEWBASE] Complete actual R3 diff whitespace' }
Write-Output '[R5-PAGINATION-CLOSURE-PASS] Saved lifecycle, copied proof, archive projection and pinned candidate verified.'
```
