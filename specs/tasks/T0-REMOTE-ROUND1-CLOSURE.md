---
id: T0-REMOTE-ROUND1-CLOSURE
title: Record remote round-one deliveries and archive their completed cards
status: merged
depends_on: [T0-REMOTE-PRODUCT-CARDS, T0-REMOTE-PREREVIEW-CARDS, T1-SAFE-MEDIA-LOGGING-REMOTE, T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE]
allow_paths:
  - specs/tasks/T0-REMOTE-ROUND1-CLOSURE.md
  - specs/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/archive/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/archive/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/archive/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/archive/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/SECURITY.md
  - docs/TASK-BOARD.md
  - docs/adr/0007-report-interchange.md
sweep: Round-one R5 touches the four completed cards and archive counterparts, generated archive index, current-stage note, SafeLog security delivery, Task Board rows and typography ADR note. The two registration cards do not count as product deliveries. Unrelated card and document content is retained.
acceptance:
  - "A1 Record each of the four prerequisite deliveries with its actual PR number, reviewed head, passing candidate CI run and merge OID. Include SafeLog's current remote test and mutation evidence and Typography's current remote evidence; label prior local history separately. No pending result may be recorded as complete."
  - "A2 After original-main cleanup has succeeded and copied evidence has been independently checked, move exactly the four completed cards to specs/archive/tasks, retaining their original complete contracts except status and appended delivery records. Generate cards-index through archive.ps1; remove their active copies."
  - "A3 Update CLAUDE current stage, SECURITY SafeLog record, the two product Task Board statuses and links, and the ADR-0007 typography publication record. Preserve Boundary and Pagination todo states, original local lineage and all deferred composition, platform-glyph, storage and device-acceptance boundaries. The product count is two remote deliveries of ten."
  - "A4 Fixed approved payload checks cover the archived cards, generated index and four documentation updates. Card validation, archive-index projection and whitespace checks pass; the normal original-main scope, complete-diff budget, formal R3 and exact-candidate CI gates remain mandatory."
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
dod_assert: Four actual prerequisite deliveries have independently verified external receipts; this DoD checks their nine approved LF-normalized payloads, exact archive moves, merged statuses, pending successors, card validation, generated index and whitespace. It does not perform live external attestation.
review_gate: codex {verdict:pass}
hygiene: Genuine metadata closure; explicit SkipRed. Preserve reviewed source, validation and mutation receipts before cleanup; exercise real missing-record, wrong-status and changed-payload negative cases for the final assertions.
doc_sync: This PR contains the R5 documentation and archive updates for all four prerequisite cards. Its own merged status is effective only when this PR actually merges; preserve that merge receipt in the controller ledger and run original-main cleanup. The closure card can remain in the active directory as merged until ordinary later archival maintenance.
---

# Round-one remote closure

This metadata PR completes R5 for PRs 301, 303, 304 and 305. Only Logging and Typography count toward the five-round product target: two of ten. All four functional/registration PRs actually merged and their evidence was independently checked before the original-main cleanup. Full contracts and earlier local provenance are retained in the archived cards.

The status of this closure card is the reviewed target state, effective only when this PR actually merges. Until that event, the controller ledger records this work as pending. This card remains in the active directory for later ordinary archival maintenance, avoiding a recursive metadata-only closure PR.

The archive projection starts from the complete remote archive at 3351c06c99ba8d85e3e008b7a89cdac43bb2470d (193 cards), retains every existing byte, and adds exactly four cards (197 total). Payload digests below were approved only after the external lifecycle receipts had been checked. They validate static records and do not establish live GitHub state or execution history by themselves. No new lesson is added: the observed repairs are covered by the existing exact-proof, byte-fidelity and self-verifying-contract rules.

## Approved payload assertions

```powershell
$ErrorActionPreference = 'Stop'
$expected = @{
  'specs/archive/tasks/T0-REMOTE-PRODUCT-CARDS.md' = '5A68E8577D4AAF09E333CAFA50390988FA2AEAC7EA964DA8250A61C9D9D37C51'
  'specs/archive/tasks/T0-REMOTE-PREREVIEW-CARDS.md' = '9DE14D625A13FB894B483EAE1C5A0961854F4F1FA3C232652FFC32E0CDED0B9D'
  'specs/archive/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md' = '8023BDB1E0C1A24F9409B91342C5E7D6188EC6196F450351CD5E1632DA64A1DD'
  'specs/archive/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md' = 'C27146C7EB27E07CB2BF4EF33377BAB6A3D00FFF7BE63EBA7362A35AECACFF5C'
  'specs/archive/cards-index.md' = '39A5ECD9ED36457D70AB8CBF8ED6505A54A49D8BB02FFBC2463FE61E78FD0490'
  'CLAUDE.md' = 'B57077ECCE1837D622321EC168ED173A6566F0437F9E3E604614D02F9191A5F3'
  'docs/SECURITY.md' = '3D87538721CB50BBC06C3B41390C0D07C3DE452151EC917662263FA57EBEF646'
  'docs/TASK-BOARD.md' = 'EF9F6C6060CDFB051365D985F6296812035E003D160B5B06DAA78369CDFC5C09'
  'docs/adr/0007-report-interchange.md' = '0F1B4BF91FD779D27404167DFB641DAC8C24586042240BFFC959FF18B4DCEC40'
}
$utf8 = [Text.UTF8Encoding]::new($false,$true)
foreach ($pair in $expected.GetEnumerator()) {
  if (-not (Test-Path -LiteralPath $pair.Key -PathType Leaf)) { throw "[R5-PAYLOAD] Missing approved payload: $($pair.Key)" }
  $text = $utf8.GetString([IO.File]::ReadAllBytes((Join-Path $PWD $pair.Key))).Replace("`r`n","`n")
  $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($text)))
  if ($actual -cne $pair.Value) { throw "[R5-PAYLOAD] Approved payload differs: $($pair.Key)" }
}
foreach ($id in @('T0-REMOTE-PRODUCT-CARDS','T0-REMOTE-PREREVIEW-CARDS','T1-SAFE-MEDIA-LOGGING-REMOTE','T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE')) {
  if (Test-Path -LiteralPath "specs/tasks/$id.md") { throw "[R5-STATUS] Completed active card remains: $id" }
  if ((Get-Content -LiteralPath "specs/archive/tasks/$id.md" -Raw) -cnotmatch '(?m)^status: merged\r?$') { throw "[R5-STATUS] Archive not merged: $id" }
}
foreach ($id in @('T1-STORAGE-PATH-BOUNDARY-REMOTE','T3-PDF-PAGINATION-FIXTURES-REMOTE')) {
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
Write-Output '[R5-CLOSURE-PASS] Four prior deliveries, nine approved payloads and two pending successors verified.'
```