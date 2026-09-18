---
id: T0-REMOTE-ROUND3-CARDS
title: Register the round-three pair remote product contracts
status: todo
depends_on: []
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND3-CARDS.md
  - specs/tasks/T1-APP-STORAGE-POLICY-REMOTE.md
  - specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md
  - docs/adr/0006-offline-security-backup-hardening.md
  - docs/adr/0007-report-interchange.md
  - docs/TASK-BOARD.md
  - specs/tasks/T1-LOCAL-DATA-SECURITY.md
  - docs/evidence/round3-contracts/T1-APP-STORAGE-POLICY.registered.txt
  - docs/evidence/round3-contracts/T3-PDF-MEASUREMENT-REQUESTS.registered.txt
sweep: The selected task contracts, three scoped ADR/Board notes and security-parent dependency line cover this metadata registration. Original product behavior, earlier review history and all deferred capabilities remain assigned.
acceptance:
  - "A1 Register exactly 2 todo contracts. Pin both complete previously registered local source cards and prove the unchanged allow_paths, exclusions and executable DoD against them; preserve Policy A1 and its complete A2 text split into A2/A3. Requests A1/A4/A5 remain exact; its A2/A3, dod_assert, hygiene and budget/body changes are the expressly approved numerical-integration transfer to the Binding successor, not verbatim preservation."
  - "A2 Retain the selected cards' original local and pre-RED history in order, including Policy R3 repair history; expose the two complete registered-source contracts and their Git blob identities in this PR. Require new candidate evidence and actual predecessor merges. Aliases and registrations add no product count."
  - "A3 Update only the scoped ADR, Board and security-parent dependency notes; preserve the parent's original acceptance and executable DoD. Deferred renderer/export/storage capabilities remain undelivered."
  - "A4 Fixed approved payload assertions, check-cards, archive projection and whitespace pass before normal original-main scope, full-diff budget, formal R3, exact-head CI and remote PR merge."
forbid:
  - Product code, configuration, runtime dependencies, frozen schema, CI or scripts
  - Rewriting original histories, weakening acceptance or claiming pending product evidence complete
non_goals:
  - Implementing any registered capability or publishing source photographs
dod_command: $raw = Get-Content specs/tasks/T0-REMOTE-ROUND3-CARDS.md -Raw; $b = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($b.Count -ne 1) { throw 'one assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Exact approved static payloads, original registered source bytes and permitted source-to-current mapping, full nine-path scope and staged/committed plus working whitespace, card and archive validation pass; future product acceptance is not claimed by this registration.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata registration uses SkipRed; actual contract-corruption negatives verify fixed payload assertions before publication.
doc_sync: Record actual registration PR, reviewed head, checks and remote merge, then include its closure in the appropriate reviewed R5 metadata batch. Product cards remain todo until their own closures.
---

# T0-REMOTE-ROUND3-CARDS

This exact candidate was projected from observed remote base `6ae82ab632da851985ce6c68402d02a6ace4bb09`, which already contains the functional Pagination PR #309 and Boundary PR #310 merges. It registers the existing Round 3 Policy and Requests contracts as todo; it does not mark either product complete or claim Round 2 R5 closure. Those R5 metadata deliveries may proceed separately. Policy and Requests may enter their own R1 only after this registration actually merges and each card’s true functional predecessors are merged. The two previously registered local contracts are included byte-for-byte under `docs/evidence/round3-contracts/`; Policy's A2 is divided without deleting text, while Requests' documented A2/A3, `dod_assert`, `hygiene` and budget/body rescope assigns the 19 Composer numerical integration cases to approved Binding successor SHA `116E319659B3E43DED00D7246A9AE9D54D3EE2836A216AB8B76EDD126EE8D52E`. Prior formal R3 attempts at `b14cc2f3` and `aab6c818` returned BLOCK; this candidate retains their repair history and adds source and committed-whitespace proofs. Preserve the complete remaining acceptance and local history; use original-main task-loop with origin/master start, full metadata DoD, scope, official diff budget, independent R3, exact-head CI and remote PR merge. If the remote base advances, reproject and reapprove all six payload bytes before ship. The fixed hashes below are for this pinned candidate only; normal limits remain authoritative.

```powershell
$ErrorActionPreference = 'Stop'
$expected = @{
    'specs/tasks/T1-APP-STORAGE-POLICY-REMOTE.md' = 'CB59BCE9B88A939426EAADEB908869D598BC575BB241BA63A510BA819AA9625B'
    'specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md' = '3B3D4506D11779E975548E5374CD140E936A638D8823119BCA8762394912C2E7'
    'docs/adr/0006-offline-security-backup-hardening.md' = '73CBF21AE71A3F8BD8B88CB6871873A9469C8E8810F0FC76B19FF0CFAED9C188'
    'docs/adr/0007-report-interchange.md' = '9F551AFC8F93ED08E5AA2AE01E563F7456E87FA74098B0224757078CD072F16D'
    'docs/TASK-BOARD.md' = '0331FE0DFF901C3EC6A22B6F9E425B68A476AF8B6BE44C0CA1C5038A655C6863'
    'specs/tasks/T1-LOCAL-DATA-SECURITY.md' = 'E1C388F07F02227B6B7A9E49FD4ABFEC90678F763430785E7FD05D759DA08568'
}
foreach ($path in $expected.Keys) {
    $bytes = [IO.File]::ReadAllBytes((Join-Path $PWD $path))
    $text = [Text.UTF8Encoding]::new($false,$true).GetString($bytes).Replace("`r`n","`n")
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text)))
    if ($hash -cne $expected[$path]) { throw "approved registration contract changed: $path" }
}
Write-Host '[REMOTE-REGISTRATION-PAYLOAD-OK] exact approved static contracts'
$sources = @{
    Policy = @('docs/evidence/round3-contracts/T1-APP-STORAGE-POLICY.registered.txt','443C2499AD9C4F8061BE89AEB766DA2A3FDC8DC4F6D99BE0FEFD0EEAB56933C4','44407a84230614591a0e6eadaad0d404cd76ca8d','specs/tasks/T1-APP-STORAGE-POLICY-REMOTE.md')
    Requests = @('docs/evidence/round3-contracts/T3-PDF-MEASUREMENT-REQUESTS.registered.txt','AE366AFAD677A746A63072ED030B0B14C059DFBE036639EEB95F5D4BFC0CF4B6','36e0117b1bc18240bd0737a4306294f0adff0602','specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md')
}
function Read-Contract($path) {
    $text = [Text.UTF8Encoding]::new($false,$true).GetString([IO.File]::ReadAllBytes((Join-Path $PWD $path))).Replace("`r`n","`n")
    $match = [regex]::Match($text,'\A---\n(?<meta>.*?)\n---\n(?<body>[\s\S]*)\z','Singleline')
    if (-not $match.Success) { throw "[SOURCE-FORMAT] $path" }
    $fields = @{}
    foreach ($part in [regex]::Matches($match.Groups['meta'].Value,'(?ms)^(?<key>[a-z_]+):.*?(?=^[a-z_]+:|\z)')) {
        $key = $part.Groups['key'].Value
        if ($fields.ContainsKey($key)) { throw "[SOURCE-DUPLICATE-FIELD] $path $key" }
        $fields[$key] = $part.Value.TrimEnd("`n")
    }
    return @{ Fields=$fields; Body=$match.Groups['body'].Value }
}
function Assert-SameFields($old,$new,$keys,$name) {
    foreach ($key in $keys) {
        if (-not $old.Fields.ContainsKey($key) -or -not $new.Fields.ContainsKey($key) -or $old.Fields[$key] -cne $new.Fields[$key]) {
            throw "[SOURCE-PRESERVED-FIELD] $name $key"
        }
    }
}
function Acceptance-Items($card) {
    $lines = @($card.Fields['acceptance'] -split "`n" | Select-Object -Skip 1)
    if ($lines.Count -lt 2 -or @($lines | Where-Object { $_ -cnotmatch '^  - "A[0-9]+ ' }).Count) { throw '[SOURCE-ACCEPTANCE-FORMAT]' }
    return ,$lines
}
function Acceptance-Text($line) {
    if ($line -cnotmatch '^  - "A[0-9]+ (?<body>.*)"$') { throw '[SOURCE-ACCEPTANCE-FORMAT]' }
    return $Matches['body']
}
function Assert-History($old,$new,$keep,$name) {
    $oldParts = @([regex]::Split($old.Body,'\n\s*\n') | ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -cnotmatch '^# ' })
    $newParts = @([regex]::Split($new.Body,'\n\s*\n') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $position = 0
    foreach ($paragraph in @($oldParts | Select-Object -First $keep)) {
        $found = -1
        for ($i=$position; $i -lt $newParts.Count; $i++) { if ($newParts[$i] -ceq $paragraph) { $found=$i; break } }
        if ($found -lt 0) { throw "[SOURCE-HISTORY] $name missing/reordered original paragraph" }
        $position = $found + 1
    }
}
foreach ($name in @('Policy','Requests')) {
    $source = $sources[$name]
    $bytes = [IO.File]::ReadAllBytes((Join-Path $PWD $source[0]))
    $sha = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($sha -cne $source[1]) { throw "[SOURCE-RAW-SHA] $name" }
    [byte[]]$header = [Text.Encoding]::ASCII.GetBytes("blob $($bytes.Length)") + [byte]0
    [byte[]]$gitBlob = $header + $bytes
    $blob = [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData($gitBlob))
    if ($blob -cne $source[2].ToUpperInvariant()) { throw "[SOURCE-GIT-BLOB] $name" }
    $old = Read-Contract $source[0]
    $new = Read-Contract $source[3]
    if ($name -eq 'Policy') {
        Assert-SameFields $old $new @('allow_paths','forbid','non_goals','dod_command','dod_exit','dod_assert','hygiene','requirements') $name
        $before = Acceptance-Items $old; $after = Acceptance-Items $new
        if ($before.Count -ne 2 -or $after.Count -ne 3 -or $before[0] -cne $after[0] -or (Acceptance-Text $before[1]) -cne ((Acceptance-Text $after[1]) + (Acceptance-Text $after[2]))) { throw '[SOURCE-POLICY-A2-SPLIT]' }
        Assert-History $old $new 7 $name
    } else {
        Assert-SameFields $old $new @('allow_paths','forbid','non_goals','dod_command','dod_exit') $name
        $before = Acceptance-Items $old; $after = Acceptance-Items $new
        if ($before.Count -ne 5 -or $after.Count -ne 5) { throw '[SOURCE-REQUESTS-ACCEPTANCE-COUNT]' }
        foreach ($i in @(0,3,4)) { if ($before[$i] -cne $after[$i]) { throw "[SOURCE-REQUESTS-PRESERVED-A$($i+1)]" } }
        if ($before[1] -ceq $after[1] -or $before[2] -ceq $after[2] -or $old.Fields['dod_assert'] -ceq $new.Fields['dod_assert'] -or $old.Fields['hygiene'] -ceq $new.Fields['hygiene']) { throw '[SOURCE-REQUESTS-APPROVED-RESCOPE-MISSING]' }
        Assert-History $old $new 2 $name
    }
}
Write-Host '[REMOTE-REGISTRATION-SOURCE-OK] registered originals, preserved fields, approved split and history'
$registrationBase = '6ae82ab632da851985ce6c68402d02a6ace4bb09'
$registrationPaths = @(
    'specs/tasks/T0-REMOTE-ROUND3-CARDS.md',
    'specs/tasks/T1-APP-STORAGE-POLICY-REMOTE.md',
    'specs/tasks/T3-PDF-MEASUREMENT-REQUESTS.md',
    'docs/adr/0006-offline-security-backup-hardening.md',
    'docs/adr/0007-report-interchange.md',
    'docs/TASK-BOARD.md',
    'specs/tasks/T1-LOCAL-DATA-SECURITY.md',
    'docs/evidence/round3-contracts/T1-APP-STORAGE-POLICY.registered.txt',
    'docs/evidence/round3-contracts/T3-PDF-MEASUREMENT-REQUESTS.registered.txt'
)
Write-Host '[REMOTE-REGISTRATION-WHITESPACE-INDEX] pinned base to actual index'
git diff --cached --check $registrationBase -- @registrationPaths
if ($LASTEXITCODE -ne 0) { throw '[REMOTE-REGISTRATION-WHITESPACE-INDEX] invalid indexed metadata diff' }
Write-Host '[REMOTE-REGISTRATION-WHITESPACE-HEAD] pinned base to actual HEAD'
git diff --check $registrationBase HEAD -- @registrationPaths
if ($LASTEXITCODE -ne 0) { throw '[REMOTE-REGISTRATION-WHITESPACE-HEAD] invalid committed metadata diff' }
$scopePaths = @(
    git diff --name-only $registrationBase HEAD
    git diff --cached --name-only $registrationBase
    git diff --name-only $registrationBase
) | Sort-Object -Unique
foreach ($path in $scopePaths) {
    if ($path -notin $registrationPaths) { throw "[REMOTE-REGISTRATION-SCOPE] $path" }
}
Write-Host '[REMOTE-REGISTRATION-SCOPE-OK] full nine-path candidate'
```
