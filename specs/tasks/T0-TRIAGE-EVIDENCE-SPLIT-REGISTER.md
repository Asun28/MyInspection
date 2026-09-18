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
  - "A1 Register exactly one todo behavior predecessor for existing discovery/read/raw-field/HEAD failure observability; preserve its complete acceptance, executable DoD and deferred directory-case boundary."
  - "A2 Preserve every existing CASE DoD argument and check; explicitly add only -CaseModeProfile require-dual-actual to enforce the already approved final dual-actual obligation. Preserve original history and register the approved predecessor, acceptance restatements, paired-document scope and doc_sync correction. Neither behavior is delivered by registration."
  - "A3 Fixed approved payload, unchanged source and original historical-byte assertions pass with check-cards, archive index and whitespace checks; genuine corruption negatives reject altered inputs."
forbid:
  - Product scripts, documents, configuration, CI, schema, source behavior or test changes
  - Resetting CASE review counter1, rewriting original RED/T35/R3 history, weakening final dual-actual acceptance or reusing earlier review grants
non_goals:
  - Implementing or verifying INPUTS or CASE behavior; changing PR282 or starting another product round
dod_command: $raw = Get-Content specs/tasks/T0-TRIAGE-EVIDENCE-SPLIT-REGISTER.md -Raw; $b = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($b.Count -ne 1) { throw 'one assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }; Write-Host '[TRIAGE-REGISTER-DOD-OK] metadata only'
dod_exit: 0
dod_assert: Exact approved metadata payloads, unchanged source, original history, card validation, archive index and whitespace checks of working tree, pinned-base/index and pinned-base/HEAD pass; no product acceptance is claimed.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata registration uses SkipRed; fixed-pin corruption controls plus card/archive/whitespace negatives are recorded against the final approved registration source.
doc_sync: Record reviewed registration head, checks, CI and merge through the existing R5 metadata workflow. INPUTS and CASE remain todo until their own delivery gates complete.
---

# T0-TRIAGE-EVIDENCE-SPLIT-REGISTER

This closes the budget prerequisite of existing PR #294. Base is `6ae82ab632da851985ce6c68402d02a6ace4bb09`. The orchestrator approved one behavior predecessor followed by the original CASE PR. It does not create a new product objective or session.

Only the two task-card payloads are registered. CASE keeps every original executable DoD argument, redirection and exit/PASS/FAIL check. Its sole argument addition is the explicitly approved `-CaseModeProfile require-dual-actual`, enforcing the existing final sensitive/insensitive actual-root obligation without weakening it; three acceptance rows restate those obligations. The previous doc_sync:none judgment is corrected because source diagnostics and CaseModeProfile alter the contract taught by LOOP-ENGINEERING. Product documentation changes belong to the two later behavior PRs, not this registration.

Before R1, the root controller must bootstrap only this approved registration card on its main control line using the established metadata bootstrap. The A/B payloads go through a normal registration PR; do not push those payloads directly to master. No bootstrap, branch or PR was created during ignored preparation. If any pinned source advances, reproject and review the actual changed inputs before updating these pins.

The untouched source triage digest below belongs to remote base, not repaired local candidate c95b75fb. That later candidate's SHA-256 is 6246C413C3C1D9C9A7B9E2844388A1CC8E650DEC159FBCE1E3E79A8C22C3DB31 and is historical/prospective evidence only here. CASE's original card digest is 24383C157E45FC7961656780AA64E897F05DF78E8B6D554D1C63278D554D4ABA. Its complete 2026-09-09 history remains contiguous, including the withdrawn RED claim; no history is relabelled as new acceptance.

```powershell
$ErrorActionPreference = 'Stop'
function Get-RegistrationCanonicalBytes([string]$Path) {
    $raw = [IO.File]::ReadAllBytes((Join-Path $PWD $Path))
    $value = [Text.UTF8Encoding]::new($false,$true).GetString($raw).Replace("`r`n","`n")
    return ,([Text.Encoding]::UTF8.GetBytes($value))
}
$casePath = 'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md'
$caseBytes = Get-RegistrationCanonicalBytes $casePath
$caseText = [Text.Encoding]::UTF8.GetString($caseBytes)
$historyStart = $caseText.IndexOf('## 2026-09-09 current-source R4 evidence', [StringComparison]::Ordinal)
if ($historyStart -lt 0) { throw '[TRIAGE-REGISTER-HISTORY] original history missing' }
$historyTail = [Text.Encoding]::UTF8.GetBytes($caseText.Substring($historyStart))
if ($historyTail.Length -lt 5273) { throw '[TRIAGE-REGISTER-HISTORY] original history truncated' }
$historyBytes = [byte[]]::new(5273)
[Array]::Copy($historyTail, 0, $historyBytes, 0, 5273)
if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($historyBytes)) -cne '43B0D588DAB54E284A387E3A37AE089F0CB2E0CC938AD3AC7065DD5B1361E592') {
    throw '[TRIAGE-REGISTER-HISTORY] original history bytes changed'
}
$sourcePins = @{
    'scripts/triage.ps1' = '1DA2BA39797DDD8E00ACA7D4DA3088A74977BA78498B54A2347311D342CC9580'
    'scripts/selftest.ps1' = '3E8D8B9908F2EB15C0A472CF6C24069E9B61981C37C3B3847017D39DA1F4099F'
    'scripts/check-cards.ps1' = '02B2436F5C00CC047BD96D205B19BC67FB0329ED620DCECDB3D1007C50494C5C'
    'scripts/_cards.ps1' = '1611AA1712908DDACE7E1C99E79829FAA49BABD2A06C8B38C97C96A066913D44'
    'scripts/archive.ps1' = '74FB4D80559AA0DA9E05597551C457A69BD24CFFC6CA9873041DB5CD6CF3F2B9'
    'scripts/_config.ps1' = 'B2F2F5DCF2ECFF8F1A1CA56C5B9A1131E4804EBB3AA8B2859DB531C2A998CF21'
    'docs/LOOP-ENGINEERING.md' = 'DE58B3D74ED7F96CC8C672242CB0A7AC8E7A24A5E352ED9077F490D28B1EF395'
}
foreach ($path in $sourcePins.Keys) {
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData((Get-RegistrationCanonicalBytes $path)))
    if ($hash -cne $sourcePins[$path]) { throw "[TRIAGE-REGISTER-SOURCE] registration changed pinned source: $path" }
}
$payloadPins = @{
    'specs/tasks/T0-TRIAGE-EVIDENCE-INPUTS.md' = 'A9BFE2EB9833BED3AAFAAC52E9914C22731DCB01FD90486B951981FA145953CD'
    'specs/tasks/T0-TRIAGE-EVIDENCE-CASE.md' = '21533BB7E7C2C9FECF379EBE5AEA4E17202738F1F6D38EEE62C3D3670A71C0EA'
}
foreach ($path in $payloadPins.Keys) {
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData((Get-RegistrationCanonicalBytes $path)))
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
```

## 2026-09-18 registration metadata negative evidence

A2's sole CASE command addition is `-CaseModeProfile require-dual-actual`: removing exactly that substring reproduces the baseline `dod_command` byte-for-byte. Every original argument, stream redirection, native-exit condition and PASS/FAIL check is retained. INPUTS remains A9BFE2EB9833BED3AAFAAC52E9914C22731DCB01FD90486B951981FA145953CD; CASE remains 21533BB7E7C2C9FECF379EBE5AEA4E17202738F1F6D38EEE62C3D3670A71C0EA, including all 5273 original historical bytes.

The following are actual metadata-only native outcomes observed in an isolated snapshot of c0afac77175786b55c61c6ebb0292a64a33431a8 plus the complete three registration cards. The same cases are replayed after this receipt text is added; the final raw native receipts bind the full final D-card SHA. The executable assertion block remains SHA-256 `FE5098D28A895DC8FB7EE50EC3E8DBB0151C540412B7884D7F40E3A864A541E4` (UTF-8 block body between the existing fence delimiters). Positive and byte-restored complete DoD both return native 0 with `[TRIAGE-REGISTER-DOD-OK]`.

`full` means this card's exact extracted `dod_command`; `card` means `pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-TRIAGE-EVIDENCE-INPUTS`; `index` means `pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet`. The targeted card/index checks exercise their own gates without the earlier payload hash masking their diagnostic. Each rejection asserts both native exit 1 and the named diagnostic; restored bytes are checked after every mutation.

| Negative | Exact input change in the private snapshot | Invoked check | Native exit | Required diagnostic |
|---|---|---|---:|---|
| payload-a | INPUTS `status: todo` -> `status: in-progress` | full | 1 | TRIAGE-REGISTER-PAYLOAD |
| payload-b | CASE `  - docs/LOOP-ENGINEERING.md` -> `  - docs/QUALITY-RUBRIC.md` | full | 1 | TRIAGE-REGISTER-PAYLOAD |
| history | CASE `The preceding round-1 repair paragraph` -> `The corrupted round-1 repair paragraph` | full | 1 | TRIAGE-REGISTER-HISTORY |
| source | Append LF, `# metadata negative source drift`, LF to the snapshot `scripts/triage.ps1` | full | 1 | TRIAGE-REGISTER-SOURCE |
| acceptance-shape | Remove INPUTS A3 acceptance row using `(?m)^  - "A3 [^\r\n]*\r?\n` | card | 1 | CARD-ACCEPTANCE-INVALID |
| archive-index | Append LF, `invalid metadata projection`, LF to the snapshot archive index | index | 1 | ARCHIVE-CHECK-DRIFT |
| whitespace | D `status: todo` -> `status: todo` followed by two spaces, working tree only | full | 1 | trailing whitespace |
| whitespace-staged | Same D trailing spaces staged in private index; working-tree diff clean | full | 1 | TRIAGE-REGISTER-WHITESPACE-INDEX and trailing whitespace |
| whitespace-committed | Same spaces in private HEAD; restore D bytes and stage them so working tree and base-to-index whitespace checks are clean | full | 1 | TRIAGE-REGISTER-WHITESPACE-HEAD and trailing whitespace |

The staged/committed controls use private `GIT_DIR`, `GIT_WORK_TREE`, `GIT_INDEX_FILE` and `GIT_OBJECT_DIRECTORY`, with the original objects only as a read-only alternate. `read-tree` starts the private index at c0; `add --intent-to-add` exposes the new A/D cards. The committed control uses `write-tree` and `commit-tree -p c0`, changes only the private detached HEAD, then restores/stages the D file before invoking the full DoD. Both require clean working-tree diffs; the committed case additionally requires clean pinned-base/index whitespace, so it specifically tests the HEAD gate. Neither uses a real task branch or changes canonical history.

Raw child scripts, native start/end/exit receipts and unabridged logs are retained in `_local/pr-orchestrator-20260917/pr321-metadata-repair-20260918/checks-final/`; `metadata-check-results-final.json` records all eleven outcomes and log SHA-256 values. This visible table reports concrete executed cases; the final-source replay must pass before this draft is applied. The suite checks registration metadata and gate sensitivity; INPUTS/CASE behavior delivery and the original CASE review counter/RED/T35/R3 history remain separate and unchanged.
