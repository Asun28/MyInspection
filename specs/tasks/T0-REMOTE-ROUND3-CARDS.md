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
sweep: The selected task contracts, three scoped ADR/Board notes and security-parent dependency line cover this metadata registration. Original product behavior, earlier review history and all deferred capabilities remain assigned.
acceptance:
  - "A1 Register exactly 2 todo contracts with their complete existing allow_paths, acceptance, DoD and exclusions; map only approved remote identities, dependencies and parallel pairs."
  - "A2 Preserve the selected cards' original local or pre-RED history; require new candidate evidence and actual predecessor merges. Aliases and registrations add no product count."
  - "A3 Update only the scoped ADR, Board and security-parent dependency notes; preserve the parent's original acceptance and executable DoD. Deferred renderer/export/storage capabilities remain undelivered."
  - "A4 Fixed approved payload assertions, check-cards, archive projection and whitespace pass before normal original-main scope, full-diff budget, formal R3, exact-head CI and remote PR merge."
forbid:
  - Product code, configuration, runtime dependencies, frozen schema, CI or scripts
  - Rewriting original histories, weakening acceptance or claiming pending product evidence complete
non_goals:
  - Implementing any registered capability or publishing source photographs
dod_command: $raw = Get-Content specs/tasks/T0-REMOTE-ROUND3-CARDS.md -Raw; $b = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($b.Count -ne 1) { throw 'one assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Exact approved static payloads and card/archive/whitespace validation pass; future product acceptance is not claimed by this registration.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata registration uses SkipRed; actual contract-corruption negatives verify fixed payload assertions before publication.
doc_sync: Record actual registration PR, reviewed head, checks and remote merge, then include its closure in the appropriate reviewed R5 metadata batch. Product cards remain todo until their own closures.
---

# T0-REMOTE-ROUND3-CARDS

This exact candidate was projected from observed remote base `00842ba9134bc592adc1c6a4569740d1fbe0bf36`, which already contains the functional Pagination PR #309 and Boundary PR #310 merges. It registers the existing Round 3 Policy and Requests contracts as todo; it does not mark either product complete or claim Round 2 R5 closure. Those R5 metadata deliveries may proceed separately. Policy and Requests may enter their own R1 only after this registration actually merges and each card’s true functional predecessors are merged. Preserve their complete acceptance and local history; use original-main task-loop with origin/master start, full metadata DoD, scope, official diff budget, independent R3, exact-head CI and remote PR merge. If the remote base advances, reproject and reapprove all six payload bytes before ship. The fixed hashes below are for this pinned candidate only; normal limits remain authoritative.

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
```
