---
id: T0-REMOTE-ROUND45-CONTRACT-EVIDENCE
title: Publish the four existing Round 4 and Round 5 registered source contracts
status: todo
depends_on: [T0-REMOTE-ROUND3-CARDS]
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND45-CONTRACT-EVIDENCE.md
  - docs/evidence/round45-contracts/manifest.json
  - docs/evidence/round45-contracts/T1-APP-STORAGE-ANDROID.registered.txt
  - docs/evidence/round45-contracts/T3-PDF-MEASUREMENT-BINDING.registered.txt
  - docs/evidence/round45-contracts/T3-PDF-DEVICE-FIXTURE.registered.txt
  - docs/evidence/round45-contracts/T3-PDF-TEXT-METRICS-OPS.registered.txt
sweep: Publish only four complete original local task contracts and their exact provenance. Their registration, current acceptance, dependencies, review histories and execution remain assigned to the existing Round 4 and Round 5 pair registrations and product cards.
acceptance:
  - "A1 Preserve all four previously registered local contracts byte-for-byte, with their original Git commit:path, blob, raw SHA-256 and length independently checkable. Reject missing, extra, linked or changed source files."
  - "A2 Keep this metadata publication to its six exact paths. Do not register or mark a product complete, change Board/ADR/parent status, or claim current successor bytes equal to originals where an approved rescope occurred."
  - "A3 Reject the candidate unless a separate root approval record and approved-card copy match the latest original-D master path commit, its sole changed path, committed blob, whole raw card bytes and exact six-path scope. Full metadata DoD, official diff budget, formal R3, exact-head CI and remote merge remain required."
forbid:
  - Product implementation, production configuration, task status changes or rewritten original text
  - Treating a co-edited payload and manifest hash as proof without the independently fixed historical Git blob
non_goals:
  - Registering the Round 4/5 product pairs or running their runtime tests
dod_command: $raw = Get-Content specs/tasks/T0-REMOTE-ROUND45-CONTRACT-EVIDENCE.md -Raw; $b = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($b.Count -ne 1) { throw 'one assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Independent original-D whole-card authority and four raw source files match approved historical Git blob identities, SHA-256 and manifest metadata; exact directory contents and complete six-path Git scope pass staged, committed and working whitespace checks. Product DoD is not claimed.
review_gate: codex {verdict:pass}
hygiene: Synthetic authority controls must reject co-edited candidate and pins plus wrong record repository/ref/path/commit/blob/whole SHA/scope; source and scope corruption controls must fail named guards. The revised whole card requires a newly bound independent root approval record and a fresh full DoD; the earlier approved card and passing DoD do not authorize revised bytes.
doc_sync: Record the actual evidence publication merge and immutable source pins for later Round 4 and Round 5 registration comparisons; neither pair is registered here.
---

# Four registered source contracts

The earlier six-path candidate was committed as `cc7faa1c7a605f9447950ebe51b8f5b775d0b11e` with parent `15f3931b77924f5d1ae3e55cb0c866bc36e85946`. Its full metadata DoD exited 0 at 07:58 UTC, but the subsequent formal R3 blocked it: the reviewer's two-dot comparison (`47b78af825c699608821eb75bfa77104ac4bb86b..cc7faa1c7a605f9447950ebe51b8f5b775d0b11e`) listed 22 paths, while the actual PR three-dot comparison (`47b78af825c699608821eb75bfa77104ac4bb86b...cc7faa1c7a605f9447950ebe51b8f5b775d0b11e`) contained only the same six approved paths. This revision targets that actual review base. Root must make a non-rewriting merge of the remote base into the canonical branch, preserving the prior commit and its evidence, before the revised candidate can receive fresh DoD, budget, and formal review. It copies the four existing **local registered** contracts under `docs/evidence/round45-contracts/`. Each raw file is compared to the independently approved SHA-256 and Git blob identity, and the manifest repeats the original commit:path provenance for readers. The four source commits are not ancestors of this remote base, so source commit:path is verified against the original local Git history before approval; a remote delivery clone can still check the raw byte SHA and computed Git blob. No local product history or current remote successor is silently substituted. The Round 4 and Round 5 registrations must later compare each original with its own current card, preserve unchanged fields and ordered history, and state Binding's approved nineteen-case transfer explicitly. This publication adds no product card or product runtime and does not change Board or ADR status.

The fixed commit:path, Git blob and raw SHA below require independent owner approval with the whole-card bytes. This DoD reads a separately created root approval record and the committed master blob for the entire card; changing the source, manifest and these embedded pins together cannot satisfy that independent whole-card check. The independent root approval record and approved-card copy do exist for the earlier whole card: original-D master sole-path commit `42597418e9bba4099a7eb95214e75bfb86e90d12`, blob `82f9ffae04e9100485c6170119443e59f3af9b94`, whole SHA-256 `34B2989CB6250C5876AF7979F0F95A4BF554386AABF8E2F84388C14232144EF0`. That record cannot authorize this revised whole card. Root must approve the revised bytes, commit only this manager path on original-D master, retain the old approval evidence, and bind an updated independent record and approved-card copy to the latest sole-path commit and the six approved paths. After a non-rewriting canonical merge with the actual remote base, run the revised full metadata DoD and official size check. The earlier passing DoD is historical evidence only, not a result for this revision.

```powershell
$ErrorActionPreference = 'Stop'
$base = '47b78af825c699608821eb75bfa77104ac4bb86b'
$dir = 'docs/evidence/round45-contracts'
$manager = 'specs/tasks/T0-REMOTE-ROUND45-CONTRACT-EVIDENCE.md'
$manifestPath = "$dir/manifest.json"
# BEGIN independent own-card approval guard
function Need($ok,[string]$message) { if (-not $ok) { throw $message } }
function Eq($actual,$expected,[string]$message) { Need ([string]::Equals([string]$actual,[string]$expected,[StringComparison]::Ordinal)) $message }
function SetEq($actual,$expected,[string]$message) {
    $set=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($item in $actual) { Need ($set.Add([string]$item)) $message }
    Need ($set.SetEquals([string[]]$expected)) $message
}
$authority='D:/Projects/MyInspection'
$approvalRoot='D:/Projects/MyInspection/_local/rotating-card-orchestrator/round45-source-own-card-approval'
$approvalScope=@($manager,$manifestPath,
    "$dir/T1-APP-STORAGE-ANDROID.registered.txt",
    "$dir/T3-PDF-MEASUREMENT-BINDING.registered.txt",
    "$dir/T3-PDF-DEVICE-FIXTURE.registered.txt",
    "$dir/T3-PDF-TEXT-METRICS-OPS.registered.txt")
foreach($file in @("$approvalRoot/approval.json","$approvalRoot/approved-card.md")) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw '[OWN-AUTHORITY-MISSING]' }
    if ((Get-Item -LiteralPath $file -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw '[OWN-AUTHORITY-LINK]' }
}
$approval=Get-Content -LiteralPath "$approvalRoot/approval.json" -Raw | ConvertFrom-Json -AsHashtable
Eq $approval.schemaVersion 1 '[OWN-AUTHORITY-SCHEMA]'
Eq $approval.task 'T0-REMOTE-ROUND45-CONTRACT-EVIDENCE' '[OWN-AUTHORITY-TASK]'
Eq $approval.repository $authority '[OWN-AUTHORITY-REPOSITORY]'
Eq $approval.ref 'refs/heads/master' '[OWN-AUTHORITY-REF]'
Eq $approval.path $manager '[OWN-AUTHORITY-PATH]'
Eq $approval.base $base '[OWN-AUTHORITY-BASE]'
SetEq @($approval.allow_paths) $approvalScope '[OWN-AUTHORITY-SCOPE]'
Need ($approval.rootAuthorization -is [string] -and -not [string]::IsNullOrWhiteSpace($approval.rootAuthorization)) '[OWN-AUTHORITY-ROOT-DECISION]'
function ApprovalGit([string[]]$arguments) {
    $value=@(& git.exe -C $authority @arguments)
    Need ($LASTEXITCODE -eq 0) '[OWN-AUTHORITY-GIT]'
    return ($value -join "`n")
}
$approvalCommit=ApprovalGit @('log','-1','--format=%H','refs/heads/master','--',$manager)
Need ($approvalCommit -cmatch '^[0-9a-f]{40}$') '[OWN-AUTHORITY-COMMIT]'
Eq $approval.commit $approvalCommit '[OWN-AUTHORITY-COMMIT]'
& git.exe -C $authority merge-base --is-ancestor $approvalCommit refs/heads/master
Need ($LASTEXITCODE -eq 0) '[OWN-AUTHORITY-ANCESTOR]'
Need ((ApprovalGit @('rev-list','--parents','-n','1',$approvalCommit)).Split(' ').Count -eq 2) '[OWN-AUTHORITY-PARENT]'
SetEq @((ApprovalGit @('diff-tree','--no-commit-id','--name-only','-r',$approvalCommit)).Split("`n")) @($manager) '[OWN-AUTHORITY-SOLE-PATH]'
$approvalBlob=ApprovalGit @('rev-parse',('{0}:{1}' -f $approvalCommit,$manager))
Need ($approvalBlob -cmatch '^[0-9a-f]{40}$') '[OWN-AUTHORITY-BLOB]'
Eq $approval.blob $approvalBlob '[OWN-AUTHORITY-BLOB]'
$start=[Diagnostics.ProcessStartInfo]::new()
$start.FileName='git.exe'; $start.UseShellExecute=$false; $start.CreateNoWindow=$true
$start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
foreach($arg in @('-C',$authority,'cat-file','blob',$approvalBlob)) { [void]$start.ArgumentList.Add($arg) }
$process=[Diagnostics.Process]::new(); $process.StartInfo=$start; $buffer=[IO.MemoryStream]::new()
try {
    Need ($process.Start()) '[OWN-AUTHORITY-BLOB-READ]'
    $stderr=$process.StandardError.ReadToEndAsync()
    $process.StandardOutput.BaseStream.CopyTo($buffer)
    $process.WaitForExit()
    Need ($process.ExitCode -eq 0) '[OWN-AUTHORITY-BLOB-READ]'
    Eq $stderr.GetAwaiter().GetResult() '' '[OWN-AUTHORITY-BLOB-STDERR]'
    $approvedBytes=$buffer.ToArray()
} finally { $buffer.Dispose(); $process.Dispose() }
$approvedSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($approvedBytes))
Eq $approval.sha256 $approvedSha '[OWN-AUTHORITY-WHOLE-SHA]'
Eq ([Convert]::ToBase64String([IO.File]::ReadAllBytes("$approvalRoot/approved-card.md"))) ([Convert]::ToBase64String($approvedBytes)) '[OWN-AUTHORITY-APPROVED-COPY]'
Eq ([Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $PWD $manager)))) ([Convert]::ToBase64String($approvedBytes)) '[OWN-AUTHORITY-CANDIDATE]'
# END independent own-card approval guard
$fixed = @(
    @('T1-APP-STORAGE-ANDROID','a718507f31ededba510d1be9335f3c9683e0650b','specs/tasks/T1-APP-STORAGE-ANDROID.md','684adfb406263a55c7ac4d6ad2a268053487f500','66DA765394F287AA8A08B060CB45A7982FAF826E0B9A743689873C2B23EF52D1',5129),
    @('T3-PDF-MEASUREMENT-BINDING','9635a6503c3a25efa017a40e6da628f5f0dfa57e','specs/tasks/T3-PDF-MEASUREMENT-BINDING.md','ac802da62d7a007fe5c26cadbde29ab665cfeb91','CE312FAE39B424112A7C57C1B9B1410FD7A371F7FCEF8A291028CD8C733F2FC1',6524),
    @('T3-PDF-DEVICE-FIXTURE','6ff56fc2bf829f496f197b74171e989b2941e40e','specs/tasks/T3-PDF-DEVICE-FIXTURE.md','1decac7622657aeb228de6c06addcc66e1feefe0','B6BCCDE1A17EEDF15BE1164FB450B79A68DACE67280330926B9620BA4D83C96A',9862),
    @('T3-PDF-TEXT-METRICS-OPS','e5cf6339223031519b6c41a734d1a6b67d0cd815','specs/tasks/T3-PDF-TEXT-METRICS-OPS.md','38c45eed3271051d5322b18650b89259c728ba7b','6F673C8EB46477BDDAD76AEDFF7643700F9F28F8B37FECB5BD3B407A41734CD1',4118)
)
$expectedPaths = @($manager,$manifestPath)
$expectedNames = @('manifest.json')
$dirItem = Get-Item -LiteralPath $dir -Force
$manifestItem = Get-Item -LiteralPath $manifestPath -Force
if (($dirItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or ($manifestItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw '[SOURCE-LINK] directory or manifest' }
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
if (([string]::Join('|',@($manifest.PSObject.Properties.Name | Sort-Object))) -cne 'purpose|sources' -or $manifest.purpose -cne 'Byte-exact previously registered local contracts; no product acceptance or status change') { throw '[SOURCE-MANIFEST-SCHEMA]' }
if (@($manifest.sources).Count -ne 4) { throw '[SOURCE-MANIFEST-COUNT]' }
foreach ($entry in $fixed) {
    $id,$commit,$oldPath,$blob,$sha,$length = $entry
    $copy = "$dir/$id.registered.txt"
    $expectedPaths += $copy
    $expectedNames += "$id.registered.txt"
    $records = @($manifest.sources | Where-Object { $_.id -ceq $id })
    if ($records.Count -ne 1) { throw "[SOURCE-MANIFEST-ID] $id" }
    $record = $records[0]
    $keys = @($record.PSObject.Properties.Name | Sort-Object)
    $wantedKeys = @('copyPath','id','rawBytes','rawSha256','sourceBlob','sourceCommit','sourcePath')
    if (([string]::Join('|',$keys)) -cne ([string]::Join('|',$wantedKeys))) { throw "[SOURCE-MANIFEST-FIELDS] $id" }
    if ($record.sourceCommit -cne $commit -or $record.sourcePath -cne $oldPath -or $record.sourceBlob -cne $blob -or $record.rawSha256 -cne $sha -or $record.rawBytes -ne $length -or $record.copyPath -cne $copy) { throw "[SOURCE-MANIFEST-PIN] $id" }
    if (-not (Test-Path -LiteralPath $copy -PathType Leaf)) { throw "[SOURCE-MISSING] $id" }
    $item = Get-Item -LiteralPath $copy -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "[SOURCE-LINK] $id" }
    $bytes = [IO.File]::ReadAllBytes($item.FullName)
    if ($bytes.Length -ne $length -or [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) -cne $sha) { throw "[SOURCE-RAW-SHA] $id" }
    [byte[]]$header = [Text.Encoding]::ASCII.GetBytes("blob $($bytes.Length)") + [byte]0
    $actualBlob = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData([byte[]]($header + $bytes)))
    if ($actualBlob -cne $blob.ToUpperInvariant()) { throw "[SOURCE-RAW-BLOB] $id" }
}
$actualNames = @(Get-ChildItem -LiteralPath $dir -Force | ForEach-Object Name | Sort-Object)
if (([string]::Join('|',$actualNames)) -cne ([string]::Join('|',@($expectedNames | Sort-Object)))) { throw '[SOURCE-DIRECTORY-SET]' }
$allPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($arguments in @(
    @('diff','--name-only',$base,'HEAD'),
    @('diff','--cached','--name-only',$base),
    @('diff','--name-only',$base),
    @('ls-files','--others','--exclude-standard')
)) {
    $names=@(& git.exe @arguments)
    if ($LASTEXITCODE -ne 0) { throw '[SOURCE-SCOPE-GIT]' }
    foreach($path in $names) { if($path) { [void]$allPaths.Add($path) } }
}
SetEq @($allPaths) $expectedPaths '[SOURCE-SCOPE]'
git diff --cached --check $base -- @expectedPaths
if ($LASTEXITCODE -ne 0) { throw '[SOURCE-WHITESPACE-STAGED]' }
git diff --check $base HEAD -- @expectedPaths
if ($LASTEXITCODE -ne 0) { throw '[SOURCE-WHITESPACE-COMMITTED]' }
git diff --check -- @expectedPaths
if ($LASTEXITCODE -ne 0) { throw '[SOURCE-WHITESPACE-WORKING]' }
Write-Host '[ROUND45-SOURCE-PUBLICATION-OK] four historical contracts, exact scope and whitespace'
```
