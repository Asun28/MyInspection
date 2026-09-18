---
id: T0-TRIAGE-EVIDENCE-SPLIT-REGISTER
title: Register one evidence-input predecessor and preserve the existing PR294 case contract
status: todo
depends_on: []
allow_paths:
  - specs/tasks/T0-TRIAGE-EVIDENCE-SPLIT-REGISTER.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md
  - specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md
acceptance:
  - "A1 Register exactly one todo behavior predecessor for existing discovery/read/raw-field/HEAD failure observability; preserve its executable DoD and deferred directory-case boundary while correcting A2 to accept exact normalized and historical evidence shapes."
  - "A2 Preserve every existing CASE DoD argument and check; explicitly add only -CaseModeProfile require-dual-actual to enforce the already approved final dual-actual obligation. Preserve original history and register the approved predecessor, acceptance restatements, paired-document scope and doc_sync correction. Neither behavior is delivered by registration."
  - "A3 Fixed approved payload, unchanged source and original historical-byte assertions pass with check-cards, archive index and whitespace checks; genuine corruption negatives reject altered inputs."
forbid:
  - Product scripts, documents, configuration, CI, schema, source behavior or test changes
  - Resetting CASE review counter1, rewriting original RED/T35/R3 history, weakening final dual-actual acceptance or reusing earlier review grants
non_goals:
  - Implementing or verifying INPUTS or CASE behavior; changing PR282 or starting another product round
dod_command: $raw = Get-Content specs/tasks/T0-TRIAGE-EVIDENCE-SPLIT-REGISTER.md -Raw; $b = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($b.Count -ne 1) { throw 'one assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value))
dod_exit: 0
dod_assert: Raw approved payload and original 5273 historical bytes, unchanged source, card/archive and three whitespace surfaces pass; the delivered block executes 12 actual corruption controls with exact native exits and diagnostics, plus positive and restored checks.
review_gate: codex {verdict:pass}
hygiene: Metadata registration uses SkipRed; the delivered DoD runs all twelve corruption controls in private disposable Git state and preserves raw/native receipts without changing real refs, index, cards or history.
doc_sync: Record reviewed registration head, checks, CI and merge through the existing R5 metadata workflow. INPUTS and CASE remain todo until their own delivery gates complete.
---

# T0-TRIAGE-EVIDENCE-SPLIT-REGISTER

This closes the budget prerequisite of existing PR #294. Base is `6ae82ab632da851985ce6c68402d02a6ace4bb09`. The orchestrator approved one behavior predecessor followed by the original CASE PR. It does not create a new product objective or session.

Only the two task-card payloads are registered. CASE keeps every original executable DoD argument, redirection and exit/PASS/FAIL check. Its sole argument addition is the explicitly approved `-CaseModeProfile require-dual-actual`, enforcing the existing final sensitive/insensitive actual-root obligation without weakening it; three acceptance rows restate those obligations. The previous doc_sync:none judgment is corrected because source diagnostics and CaseModeProfile alter the contract taught by LOOP-ENGINEERING. Product documentation changes belong to the two later behavior PRs, not this registration.

Before R1, the root controller must bootstrap only this approved registration card on its main control line using the established metadata bootstrap. The A/B payloads go through a normal registration PR; do not push those payloads directly to master. No bootstrap, branch or PR was created during ignored preparation. If any pinned source advances, reproject and review the actual changed inputs before updating these pins.

The untouched source triage digest below belongs to remote base, not repaired local candidate c95b75fb. That later candidate's SHA-256 is 6246C413C3C1D9C9A7B9E2844388A1CC8E650DEC159FBCE1E3E79A8C22C3DB31 and is historical/prospective evidence only here. CASE's original card digest is 24383C157E45FC7961656780AA64E897F05DF78E8B6D554D1C63278D554D4ABA. Its complete 2026-09-09 history remains contiguous, including the withdrawn RED claim; no history is relabelled as new acceptance.

```powershell
function Assert-Registration {
$ErrorActionPreference = 'Stop'
function Get-RegistrationRawBytes([string]$Path) {
    return ,([IO.File]::ReadAllBytes((Join-Path $PWD $Path)))
}
$casePath = 'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md'
$caseBytes = Get-RegistrationRawBytes $casePath
$caseText = [Text.UTF8Encoding]::new($false,$true).GetString($caseBytes)
$historyStart = $caseText.IndexOf('## 2026-09-09 current-source R4 evidence', [StringComparison]::Ordinal)
if ($historyStart -lt 0) { throw '[TRIAGE-REGISTER-HISTORY] original history missing' }
$historyOffset = [Text.Encoding]::UTF8.GetByteCount($caseText.Substring(0,$historyStart))
if ($caseBytes.Length - $historyOffset -lt 5273) { throw '[TRIAGE-REGISTER-HISTORY] original history truncated' }
$historyBytes = [byte[]]::new(5273)
[Array]::Copy($caseBytes, $historyOffset, $historyBytes, 0, 5273)
if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($historyBytes)) -cne '43B0D588DAB54E284A387E3A37AE089F0CB2E0CC938AD3AC7065DD5B1361E592') {
    throw '[TRIAGE-REGISTER-HISTORY] original history bytes changed'
}
$sourcePins = @{
    'scripts/review.ps1' = '124BE2DD5DC4448E6B2E27D445E72C0DE9CBD3E84BD324246ED1D4AC11EA7329'
    'specs/verdict.schema.json' = '7FD70EBAA6E65F231CB78ED6E26D42BDDFBF92612CC371CCCB8705D090A04672'
    'scripts/triage.ps1' = '1DA2BA39797DDD8E00ACA7D4DA3088A74977BA78498B54A2347311D342CC9580'
    'scripts/selftest.ps1' = '3E8D8B9908F2EB15C0A472CF6C24069E9B61981C37C3B3847017D39DA1F4099F'
    'scripts/check-cards.ps1' = '02B2436F5C00CC047BD96D205B19BC67FB0329ED620DCECDB3D1007C50494C5C'
    'scripts/_cards.ps1' = '1611AA1712908DDACE7E1C99E79829FAA49BABD2A06C8B38C97C96A066913D44'
    'scripts/archive.ps1' = '74FB4D80559AA0DA9E05597551C457A69BD24CFFC6CA9873041DB5CD6CF3F2B9'
    'scripts/_config.ps1' = 'B2F2F5DCF2ECFF8F1A1CA56C5B9A1131E4804EBB3AA8B2859DB531C2A998CF21'
    'docs/LOOP-ENGINEERING.md' = 'DE58B3D74ED7F96CC8C672242CB0A7AC8E7A24A5E352ED9077F490D28B1EF395'
}
foreach ($path in $sourcePins.Keys) {
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData((Get-RegistrationRawBytes $path)))
    if ($hash -cne $sourcePins[$path]) { throw "[TRIAGE-REGISTER-SOURCE] registration changed pinned source: $path" }
}
$payloadPins = @{
    'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md' = '10FDA06A5F3D84193FB2ABF08453A2991AAE91AB2BA36C247BF3A05497099B16'
    'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md' = '21533BB7E7C2C9FECF379EBE5AEA4E17202738F1F6D38EEE62C3D3670A71C0EA'
}
foreach ($path in $payloadPins.Keys) {
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData((Get-RegistrationRawBytes $path)))
    if ($hash -cne $payloadPins[$path]) { throw "[TRIAGE-REGISTER-PAYLOAD] approved contract changed: $path" }
}
Write-Host '[TRIAGE-REGISTER-PINS-OK] exact payload, unchanged source and original history'
$registrationBase = '6ae82ab632da851985ce6c68402d02a6ace4bb09'
$registrationPaths = @('specs/tasks/T0-TRIAGE-EVIDENCE-SPLIT-REGISTER.md', 'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md', 'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md')
Write-Host '[TRIAGE-REGISTER-WHITESPACE-INDEX] pinned base to actual index'
git diff --cached --check $registrationBase -- @registrationPaths
if ($LASTEXITCODE -ne 0) { throw '[TRIAGE-REGISTER-WHITESPACE-INDEX] invalid indexed metadata diff' }
Write-Host '[TRIAGE-REGISTER-WHITESPACE-HEAD] pinned base to actual HEAD'
git diff --check $registrationBase HEAD -- @registrationPaths
if ($LASTEXITCODE -ne 0) { throw '[TRIAGE-REGISTER-WHITESPACE-HEAD] invalid committed metadata diff' }
pwsh -NoProfile -File scripts/check-cards.ps1
if ($LASTEXITCODE -ne 0) { throw 'card validation failed' }
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet
if ($LASTEXITCODE -ne 0) { throw 'archive index failed' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw 'working whitespace failed' }
Write-Host '[TRIAGE-REGISTER-CHECKS-OK] raw metadata checks'
}
function Test-RegistrationEvidenceSamples {
    $raw = [IO.File]::ReadAllText((Join-Path $PWD 'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md'))
    $blocks = [regex]::Matches($raw, '(?s)```json\r?\n(.*?)\r?\n```')
    if ($blocks.Count -ne 1) { throw '[TRIAGE-REGISTER-SCHEMA] expected one INPUTS fixture block' }
    $data = $blocks[0].Groups[1].Value | ConvertFrom-Json
    $schema = $data.profile | ConvertTo-Json -Depth 20 -Compress
    $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $positive = 0; $negative = 0
    foreach ($sample in $data.samples) {
        if ($sample.valid -isnot [bool] -or -not $names.Add($sample.name)) { throw '[TRIAGE-REGISTER-SCHEMA] invalid expectation or duplicate name' }
        $accepted = Test-Json -Json ($sample.record | ConvertTo-Json -Depth 20 -Compress) -Schema $schema -ErrorAction SilentlyContinue
        if ($accepted -ne $sample.valid) { throw "[TRIAGE-REGISTER-SCHEMA] unexpected fixture outcome: $($sample.name)" }
        if ($sample.valid) { $positive++ } else { $negative++ }
        Write-Host "[TRIAGE-REGISTER-SCHEMA-SAMPLE] $($sample.name) accepted=$accepted expected=$($sample.valid)"
    }
    if ($positive -ne 6 -or $negative -ne 24) { throw '[TRIAGE-REGISTER-SCHEMA] incomplete exact fixture inventory' }
    Write-Host '[TRIAGE-REGISTER-SCHEMA-OK] 6 historical/normalized positives and 24 schema negatives; metadata only'
}
function Test-RegistrationControls {
    $origin = (Get-Location).Path
    $utf8 = [Text.UTF8Encoding]::new($false, $true)
    $card = 'specs/tasks/T0-TRIAGE-EVIDENCE-SPLIT-REGISTER.md'
    $paths = @($card, 'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md', 'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md')
    $sourceSha = (Get-FileHash -LiteralPath (Join-Path $origin $card)).Hash
    $seed = (& git.exe --no-optional-locks rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'control seed unavailable' }
    $objects = (& git.exe --no-optional-locks rev-parse --path-format=absolute --git-path objects | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'control object store unavailable' }
    $temp = Join-Path $origin ('_local/triage-registration-controls/' + [guid]::NewGuid().ToString('N'))
    $snapshot = Join-Path $temp 'snapshot'
    $fixture = Join-Path $temp 'private.git'
    New-Item -ItemType Directory -Path $temp, (Join-Path $fixture 'refs'), (Join-Path $fixture 'objects') | Out-Null
    & git.exe --no-optional-locks archive --format=zip "--output=$temp/base.zip" $seed
    if ($LASTEXITCODE -ne 0) { throw 'control archive failed' }
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $temp 'base.zip'), $snapshot)
    foreach ($path in $paths) { [IO.File]::WriteAllBytes((Join-Path $snapshot $path), [IO.File]::ReadAllBytes((Join-Path $origin $path))) }
    [IO.File]::WriteAllText((Join-Path $fixture 'HEAD'), $seed + "`n", $utf8)
    [IO.File]::WriteAllText((Join-Path $fixture 'config'), "[core]`nrepositoryformatversion = 0`nbare = false`nautocrlf = false`nlongpaths = true`n", $utf8)
    $probe = Join-Path $temp 'actual-checks.ps1'
    $definition = (Get-Command Assert-Registration).ScriptBlock.ToString()
    [IO.File]::WriteAllText($probe, "function Assert-Registration {`n" + $definition + "`n}`nAssert-Registration`n", $utf8)
    $cardProbe = Join-Path $temp 'card-check.ps1'
    [IO.File]::WriteAllText($cardProbe, 'pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-TRIAGE-EVIDENCE-INPUTS; exit $LASTEXITCODE', $utf8)
    $archiveProbe = Join-Path $temp 'archive-check.ps1'
    [IO.File]::WriteAllText($archiveProbe, 'pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; exit $LASTEXITCODE', $utf8)
    $names = @('GIT_DIR','GIT_WORK_TREE','GIT_INDEX_FILE','GIT_OBJECT_DIRECTORY','GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_OPTIONAL_LOCKS')
    $saved = @{}
    foreach ($name in $names) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
    $rows = [Collections.Generic.List[object]]::new()
    function Restore-ControlIndex {
        [IO.File]::WriteAllText((Join-Path $fixture 'HEAD'), $seed + "`n", $utf8)
        & git.exe read-tree $seed
        if ($LASTEXITCODE -ne 0) { throw 'private index restore failed' }
        & git.exe add --intent-to-add -- @paths
        if ($LASTEXITCODE -ne 0) { throw 'private intent entries failed' }
    }
    function Invoke-Control([string]$Name, [string]$Entry, [int]$ExpectedExit, [string]$Marker, [switch]$Whitespace) {
        $PSNativeCommandUseErrorActionPreference = $false
        $log = Join-Path $temp ($Name + '.raw.log')
        $started = [DateTime]::UtcNow.ToString('o'); $code = $null; $failure = $null
        try { & pwsh -NoProfile -File $Entry *> $log; $code = $LASTEXITCODE } catch { $failure = $_.Exception.Message }
        $receipt = [ordered]@{name=$Name;startedUtc=$started;endedUtc=[DateTime]::UtcNow.ToString('o');nativeExit=$code;launchFailure=$failure;sourceSha256=$sourceSha;entry=$Entry;expectedExit=$ExpectedExit;marker=$Marker}
        [IO.File]::WriteAllText((Join-Path $temp ($Name + '.native.json')), ($receipt | ConvertTo-Json) + "`n", $utf8)
        $text = [IO.File]::ReadAllText($log)
        $ok = $null -eq $failure -and $code -eq $ExpectedExit -and $text.Contains($Marker, [StringComparison]::Ordinal)
        if ($ExpectedExit -ne 0) { $ok = $ok -and -not $text.Contains('[TRIAGE-REGISTER-CHECKS-OK]', [StringComparison]::Ordinal) }
        if ($Whitespace) { $ok = $ok -and $text.Contains('trailing whitespace', [StringComparison]::Ordinal) }
        $rows.Add([pscustomobject]@{name=$Name;nativeExit=$code;passed=$ok;logSha256=(Get-FileHash -LiteralPath $log).Hash})
        [IO.File]::WriteAllText((Join-Path $temp 'results.json'), ($rows | ConvertTo-Json) + "`n", $utf8)
        if (-not $ok) { throw "[TRIAGE-REGISTER-CONTROL-FAILED] $Name exit=$code; expected=$ExpectedExit marker=$Marker; raw=$log" }
        Write-Host "[TRIAGE-REGISTER-CONTROL] $Name exit=$code marker=$Marker"
    }
    function Add-OneCR([string]$Text, [switch]$History) {
        $start = if ($History) { $Text.IndexOf('## 2026-09-09 current-source R4 evidence', [StringComparison]::Ordinal) } else { 0 }
        if ($start -lt 0) { throw 'newline history anchor missing' }
        $offset = $Text.IndexOf("`n", $start)
        if ($offset -lt 0 -or ($offset -gt 0 -and $Text[$offset-1] -eq "`r")) { throw 'newline control needs an original LF' }
        return $Text.Insert($offset, "`r")
    }
    try {
        $env:GIT_DIR = $fixture; $env:GIT_WORK_TREE = $snapshot
        $env:GIT_INDEX_FILE = Join-Path $temp 'index'; $env:GIT_OBJECT_DIRECTORY = Join-Path $fixture 'objects'
        $env:GIT_ALTERNATE_OBJECT_DIRECTORIES = (@($objects, $saved['GIT_ALTERNATE_OBJECT_DIRECTORIES']) | Where-Object { $_ }) -join [IO.Path]::PathSeparator
        $env:GIT_OPTIONAL_LOCKS = '0'
        Push-Location $snapshot
        try {
            Restore-ControlIndex
            Invoke-Control 'positive' $probe 0 'TRIAGE-REGISTER-CHECKS-OK'
            $a = 'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md'; $b = 'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md'
            $cases = @(
                @{Name='payload-a';Path=$a;Change={param($s) $s.Replace('status: todo','status: in-progress')};Entry=$probe;Marker='TRIAGE-REGISTER-PAYLOAD'},
                @{Name='payload-b';Path=$b;Change={param($s) $s.Replace('  - docs/LOOP-ENGINEERING.md','  - docs/QUALITY-RUBRIC.md')};Entry=$probe;Marker='TRIAGE-REGISTER-PAYLOAD'},
                @{Name='history';Path=$b;Change={param($s) $s.Replace('The preceding round-1 repair paragraph','The corrupted round-1 repair paragraph')};Entry=$probe;Marker='TRIAGE-REGISTER-HISTORY'},
                @{Name='source';Path='scripts/triage.ps1';Change={param($s) $s+"`n# metadata negative source drift`n"};Entry=$probe;Marker='TRIAGE-REGISTER-SOURCE'},
                @{Name='acceptance-shape';Path=$a;Change={param($s) [regex]::Replace($s,'(?m)^  - "A3 [^\r\n]*\r?\n','')};Entry=$cardProbe;Marker='CARD-ACCEPTANCE-INVALID'},
                @{Name='archive-index';Path='specs/archive/cards-index.md';Change={param($s) $s+"`ninvalid metadata projection`n"};Entry=$archiveProbe;Marker='ARCHIVE-CHECK-DRIFT'},
                @{Name='whitespace';Path=$card;Change={param($s) $s.Replace('status: todo','status: todo  ')};Entry=$probe;Marker='trailing whitespace'},
                @{Name='newline-a';Path=$a;Change={param($s) Add-OneCR $s};Entry=$probe;Marker='TRIAGE-REGISTER-PAYLOAD'},
                @{Name='newline-b';Path=$b;Change={param($s) Add-OneCR $s};Entry=$probe;Marker='TRIAGE-REGISTER-PAYLOAD'},
                @{Name='newline-history';Path=$b;Change={param($s) Add-OneCR $s -History};Entry=$probe;Marker='TRIAGE-REGISTER-HISTORY'}
            )
            foreach ($case in $cases) {
                $path = Join-Path $snapshot $case.Path
                $bytes = [IO.File]::ReadAllBytes($path); $hash = (Get-FileHash -LiteralPath $path).Hash
                $text = $utf8.GetString($bytes); $mutated = & $case.Change $text
                if ($mutated -ceq $text) { throw "control did not alter input: $($case.Name)" }
                try {
                    [IO.File]::WriteAllText($path, $mutated, $utf8)
                    Invoke-Control $case.Name $case.Entry 1 $case.Marker
                } finally {
                    [IO.File]::WriteAllBytes($path, $bytes)
                    if ((Get-FileHash -LiteralPath $path).Hash -cne $hash) { throw 'control byte restore failed' }
                }
            }
            $dPath = Join-Path $snapshot $card
            $dBytes = [IO.File]::ReadAllBytes($dPath); $dHash = (Get-FileHash -LiteralPath $dPath).Hash
            foreach ($mode in @('staged','committed')) {
                try {
                    [IO.File]::WriteAllText($dPath, $utf8.GetString($dBytes).Replace('status: todo','status: todo  '), $utf8)
                    & git.exe add -- @paths
                    if ($LASTEXITCODE -ne 0) { throw 'private staging failed' }
                    if ($mode -eq 'committed') {
                        $tree = (& git.exe write-tree | Out-String).Trim()
                        if ($LASTEXITCODE -ne 0) { throw 'private tree failed' }
                        $commit = (& git.exe -c user.name=metadata-control -c user.email=metadata@example.invalid -c commit.gpgSign=false commit-tree $tree -p $seed -m 'ignored whitespace control' | Out-String).Trim()
                        if ($LASTEXITCODE -ne 0 -or $commit -cnotmatch '^[0-9a-f]{40}$') { throw 'private commit failed' }
                        [IO.File]::WriteAllText((Join-Path $fixture 'HEAD'), $commit + "`n", $utf8)
                        [IO.File]::WriteAllBytes($dPath, $dBytes)
                        & git.exe add -- $card
                        if ($LASTEXITCODE -ne 0) { throw 'private clean index staging failed' }
                        & git.exe diff --cached --check 6ae82ab632da851985ce6c68402d02a6ace4bb09 -- @paths
                        if ($LASTEXITCODE -ne 0) { throw 'committed control index is not clean' }
                    }
                    & git.exe diff --check
                    if ($LASTEXITCODE -ne 0) { throw 'whitespace control working tree check failed' }
                    $dirty = @(& git.exe diff --name-only)
                    if ($LASTEXITCODE -ne 0 -or $dirty.Count) { throw 'whitespace control working tree is not clean' }
                    $marker = if ($mode -eq 'staged') { 'TRIAGE-REGISTER-WHITESPACE-INDEX' } else { 'TRIAGE-REGISTER-WHITESPACE-HEAD' }
                    [IO.File]::WriteAllText((Join-Path $temp ($mode + '-surface.json')), (@{workingTreeClean=$true;indexClean=($mode -eq 'committed');privateHead=[IO.File]::ReadAllText((Join-Path $fixture 'HEAD')).Trim()} | ConvertTo-Json), $utf8)
                    Invoke-Control ('whitespace-' + $mode) $probe 1 $marker -Whitespace
                } finally {
                    [IO.File]::WriteAllBytes($dPath, $dBytes)
                    Restore-ControlIndex
                    if ((Get-FileHash -LiteralPath $dPath).Hash -cne $dHash) { throw 'staged/committed control restore failed' }
                }
            }
            Invoke-Control 'restored' $probe 0 'TRIAGE-REGISTER-CHECKS-OK'
            if ($rows.Count -ne 14 -or @($rows | Where-Object { -not $_.passed }).Count) { throw 'incomplete actual control inventory' }
            Write-Host "[TRIAGE-REGISTER-CONTROLS-OK] 12/12; positive/restored=0; raw/native=$temp; source=$sourceSha"
        } finally { Pop-Location }
    } finally {
        foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    }
}
Assert-Registration
Test-RegistrationEvidenceSamples
Test-RegistrationControls
Write-Host '[TRIAGE-REGISTER-DOD-OK] raw bytes and actual self-verifying metadata controls'
```


## Executable registration controls

The single delivered block is the complete DoD. It hashes raw INPUTS/CASE/source file bytes and copies exactly 5273 original historical bytes directly from CASE's byte array; it does not normalize line endings. A2's sole CASE command addition remains `-CaseModeProfile require-dual-actual`; CASE and original history are unchanged. INPUTS A2 is corrected only to the pinned producer/consumer evidence formats; its behavior remains deferred.

Each run archives current Git HEAD into a new ignored fixture and overlays the exact current three card files. Private HEAD/index/objects use existing objects only as read-only alternates. The child probe is extracted from this block's actual `Assert-Registration` function; it executes the delivered checks without recursive controls. Normal DoD always runs checks and controls, with no skip argument. No real branch, index or file is mutated. Child scripts, native UTC/exit receipts and full logs remain under the printed `_local/triage-registration-controls/<unique-id>` path.

Each control mutates one input and restores exact bytes. `payload-a` changes INPUTS status; `payload-b` changes CASE's allowed document; `history` corrupts its original paragraph; `source` appends a source comment. `acceptance-shape` removes INPUTS A3 and invokes check-cards directly; `archive-index` appends invalid projection text and invokes archive validation directly, preventing payload failure from masking either gate. `whitespace` adds trailing spaces in D's working tree. Staged/committed controls create genuine private index/HEAD corruption and prove earlier surfaces clean; they require the matching INDEX/HEAD diagnostic plus trailing-whitespace output. `newline-a`, `newline-b` and `newline-history` insert one CR before an existing LF and require raw payload/history rejection.

All twelve negatives require exactly native 1 and their named diagnostic. Positive/restored checks require native 0 and the checks success marker. The final controls marker requires all fourteen actual child outcomes. Launch errors retain null native exits and fail DoD. Each receipt binds the full D source hash and is written before log hashing. Failure prevents the final DoD success marker. Earlier review failures and local evidence stay historical; this executable suite supplies its own new evidence on every normal DoD run.

## Normalized evidence metadata correction

Normal DoD checks INPUTS's 6 valid and 24 invalid schema samples before the unchanged corruption controls. This registers future behavior only. CASE remains byte-identical.
