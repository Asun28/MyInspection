---
id: T0-ROUND45-DELIVERY-CLOSEOUT
title: Close round 4 StorageAndroid and MeasurementBinding records through targeted archival
status: todo
branch: T0-ROUND45-DELIVERY-CLOSEOUT
worktree: C:\wt\T0-ROUND45-DELIVERY-CLOSEOUT
depends_on: [T0-ARCHIVE-TARGETED-CARDS, T1-APP-STORAGE-ANDROID, T3-PDF-MEASUREMENT-BINDING]
allow_paths:
  - specs/tasks/T1-APP-STORAGE-ANDROID.md
  - specs/archive/tasks/T1-APP-STORAGE-ANDROID.md
  - specs/tasks/T3-PDF-MEASUREMENT-BINDING.md
  - specs/archive/tasks/T3-PDF-MEASUREMENT-BINDING.md
  - specs/archive/cards-index.md
  - docs/adr/0007-report-interchange.md
  - docs/TASK-BOARD.md
  - CLAUDE.md
budget: 750
tier: 0
review_gate: codex {verdict:pass}
sweep: "The historical7381 audit covered three products and ten paths (REPORT SHA256 90FAE970EAFB5F1FF7D0C4B12E2009D00244EA9EF5704D5F76609FD5397229AA). Under the explicit round4-first instruction, this revision selects only StorageAndroid and MeasurementBinding, eight paths and four document operations. Fixture remains unselected for round5. ADR0006, SECURITY and the Android probe recipe were already synchronized; current check-cards compatibility disables CARD-SWEEP enforcement."
dod_command: & { $ErrorActionPreference = 'Stop'; $lines = @(git show 'origin/master:specs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md'); if ($LASTEXITCODE -ne 0) { throw '[CLOSEOUT-CONTRACT-BASE]' }; $matches = [regex]::Matches(($lines -join "`n"), '(?s)<!-- closeout-dod -->\n```powershell\n(.*?)\n```'); if ($matches.Count -ne 1) { throw '[CLOSEOUT-CONTRACT-RUNNER]' }; & ([scriptblock]::Create($matches[0].Groups[1].Value)); exit $LASTEXITCODE }
dod_exit: 0
dod_assert: Both existing gates pass; execution base equals origin/master with ancestry, registered-card and scope checks; two cold files equal frozen raw sources plus exact append text and retain independent cold hashes; four unique block hashes and complete raw ADR/Board/CLAUDE projections match; ROUND4-CLOSEOUT-OK.
acceptance:
  - "A1 Preserve the complete existing contracts and dated history of exactly round4 StorageAndroid and MeasurementBinding. Append only the two exact reviewed provenance payloads. DoD proves raw frozen-source-plus-append equality and independent cold hashes; generator byte-preservation evidence is separately required."
  - "A2 Use the delivered archive.ps1 CardsOnly/CardIds mode for exactly those two IDs, with DryRun then real generation. Generate the complete cards-index with its existing generator, never hand-edit cold cards or index. Whole unselected hot/cold inventories, including Fixture, and debt/lesson bytes stay unchanged; repeat selected generation has no byte changes. DoD checks both hot absences, cold content and CheckCardsIndex."
  - "A3 Defer Fixture and TEXT-METRICS-OPS intact until round4 completes its full authorized delivery and cleanup. Do not alter Fixture hot/cold cards or its ADR/Board/CLAUDE records. Preserve the original product/P4 review distinction, unresolved follow-ups, source locator, historical payload and cleanup/seal limitations for round5; add no device-acceptance or product-count claim."
  - "A4 Add Binding PR433 actual snapshot delivery and existing PR435 cleanup provenance to ADR0007; correct only the current Requests paragraph's undelivered TextRun statement and current Board/CLAUDE pending summaries. Preserve all dated forecasts and still-pending Ops, platform glyph, CJK, clipping and full device acceptance."
  - "A5 Freeze exactly four complete document operations before construction; DoD checks independent block digests and unique matches, constructs the complete projection from the verified execution base, and compares every byte of ADR0007, Board and CLAUDE including all unchanged surrounding content. Reuse root-audited original delivery/cleanup evidence; no historic rerun or fresh verification claim."
  - "A6 Complete normal formal R3, exact-head CI and merge, then metadata-unit R5/cleanup and an explicit R5.5 disposition before strict counts change. This necessary metadata split adds zero products and does not assert other unfinished products delivered."
forbid:
  - Registering this draft before root confirms archive capability and its R5 delivery plus the actual final base
  - Manually editing cold archive bodies or cards-index
  - Modifying product code, existing controllers, scripts or test helpers
  - Rewriting dated history, old sealed evidence, original task scopes or original cleanup outcomes
  - Executing round5 Fixture, AndroidTextMeasurer or TEXT-METRICS-OPS work during this round4 closure
non_goals:
  - Product implementation, device acceptance or historical test and cleanup reruns
  - Closing the two existing Fixture reconciliation follow-ups
  - Debt or lesson sweeping, global archival, unrelated hot/cold movement or new product credit
hygiene: DoD is read-only and uses independent fixed constants. Validate its failure causes during the later authorized workflow; do not add a parallel test/helper file or promise a new product mutation batch. Original test and review receipts remain inherited evidence.
doc_sync: Two complete round4 cards through generated archival; ADR0007, Board and CLAUDE Binding delivery boundaries. The metadata unit's own status, final merge facts and cleanup follow the normal separately authorized R5 path. Round5 work waits for complete round4 closure.
---

# T0-ROUND45-DELIVERY-CLOSEOUT

## Finalized prerequisite record and separate implementation

This complete contract is a private round4 rescope of PR438's actual round1 F1/F2 repair. The two historical ROUND45 task IDs and the same PR/review history are retained. It remains todo and unadopted. The saved prerequisite facts below were read locally; the root grant binds the CI job identities. No new product execution or acceptance is claimed by this repair.

- Approved prerequisite/source base:069d35db3a62ceea0763b01e2ae7ffd77bdb397d.
- Archive capability PR436 reviewed head:52c1e0a75eaa54737ec35576acb91e00f10b452a.
- Archive R3: round2 spec/standards pass, zero findings, run_status success; saved verdict SHA256264314855E8CDDA767EF069C343F0149E9412715929AA250E919149EA0C8D750. Original round1 BLOCK remains history; no Reset.
- Archive CI:37207356196 attempt1, pull_request, success at that head; verify111451222145 and required111452911842 succeeded.
- Archive feature merge:83b2f87ba5b2997beffb9418d86a9006586e3002.
- Archive R5 PR437: head5077d517fba9f28010944e572e97e01a8e6f722a, CI37208089699 attempt1 success, merge069d35db3a62ceea0763b01e2ae7ffd77bdb397d.
- Recorded normal cleanup: native0; directory, branch, worktree registration and T24 absent. Root preservation manifest:CDA4DB9A9115375E947367AC71C278267EFC49C851F1A7BE3516F615C141E5D5. Cleanup advisory warnings remain in its full log; R5.5 explicitly reuses existing lessons with no new duplicate lesson.

The two round4 source cards and append payloads remain unchanged at069d35db, and all four Binding document operations remain applicable. Preserve the later archive-delivery entries in Board/CLAUDE. Registration must complete its separate lifecycle before this eight-path implementation; its own-card R5 is outside implementation scope. A registration budget does not establish metadata acceptance.

## Execution baseline and complete verification

DoD reads its runner from the registered card on local origin/master. It requires git merge-base HEAD origin/master to equal the resolved origin/master, successful native exits and ancestry from069d35db to that base and then HEAD. The current card must equal that baseline card, candidate changes must stay in its allowlist, and nonignored untracked files fail. No unpublished environment variable or invented future R5 merge is used.

This is a local-ref proof, not a remote freshness proof. Future R1/ship/root authorization must separately pin the actual post-registration/R5 base, refresh/check refs and stop on drift before execution. The DoD does not fetch or silently choose another baseline. Its source-plus-append proof pins original069d bytes; its document projection starts from that actual execution base, retaining registration/R5 context. Baseline documents must be UTF-8/LF without BOM; encoding drift fails rather than rewriting outside content.

The readable runner retains all four independent digests and uniqueness checks. Three exact replacements use unique baseline matches; the Binding ADR section appends without trimming the baseline. Whole raw UTF-8 comparisons reject every changed surrounding byte, including Fixture records. Generator/DryRun/repeat and unselected inventories still need separate evidence.

## Frozen content and exact change plan

The two round4 cold normalized UTF-8/LF SHA256 values are:

- T1-APP-STORAGE-ANDROID: DEA9373D95B5C00FBBAE7DEB1FDA814041BE8EC5D9EAD647CCC30278C9B23B47

- T3-PDF-MEASUREMENT-BINDING: 71264073438BE0853CD85DD0BD69CAE8CCE6AEB64A8C58A31101D9AF88FA3077

## Deferred round5 obligations

The user requires full round4 completion before round5 and at most three simultaneously active sessions including root. This card does not create new sessions or finer stages. Fixture and TEXT-METRICS-OPS work remains deferred; neither is delivered or credited by this metadata unit.

Fixture's full current hot card stays unchanged. Its reviewed locator-supplemented append payload, five-blob accounting and three proposed document operations remain preserved in the prior complete private seal0B9F64B187614F8FF3F7C9E2D4E8B9DBC1C2E638A49AB8B6727AF71CA5DE619C. The deferred cold target remains4BF04E7BE77325E50E09BD4194A2B611DD5A4E6020415632977E25688C847719; it is not an executable target here. Later round5 must register its complete contract and verify its then-current sources.

Retain PR338/head/CI/merge and the five product blobs, product Opus fifth-round R3 versus P4 seventh-round review, cleanup exit/output limits and retrospective-seal distinction. The two unresolved follow-ups remain the pre-split SHARE-SCREEN-PRIVACY assumptions in five downstream cards including T5-DIAGNOSTIC-EXPORT, and the outdated platform/storage/SafeLog ownership row. Original locator: reconciliation session55a97974 line3534, notification2026-09-24T05:08:59.248Z, excerpt SHA256 AAD33FDB182451CD3FC0C5D192AAA2749274FAE8FA054DECC4D4D3FE415AED30. No device or four-quality acceptance is added. TEXT-METRICS-OPS still owns PdfTextOp forwarding and placed-box validation; glyphs, CJK, clipping and full device acceptance remain separate.

1. Recheck both complete round4 hot append payloads after this contract is registered and its R5 lands. Expected content is frozen from approved069d35db. Stop on source drift; do not silently retarget constants.
2. Apply only the three exact replacements and one addition below: Requests paragraph, Binding Board row, current Binding CLAUDE bullet and Binding ADR section. Keep every other byte, including all Fixture content.
3. Preserve each original hot file as an exact prefix. Use only the shipped targeted generator for the selected two card moves and complete cards-index projection.
4. Check the single-line read-only DoD plus separately retained inventories/generation evidence. Its normalized content proof does not replace raw byte preservation.
5. Measure the complete actual review diff, including generator index and Git rename presentation. First candidate <=750 changed lines and <=45000 UTF-16 units; independently show ceil(1.25x) repair reserve against 1000/60000. Stop for a scope decision if exceeded; do not compress content or remove acceptance.

## Exact document operations

### adr-current-request-boundary

Target: docs/adr/0007-report-interchange.md. Operation: replace exact paragraph once.
Expected normalized block SHA256: 82DE24F2105B91EC0D1DD3D82B6DEAF4EB4EB4D5B7568EEE11740DC916F27E39

```markdown
After the remote Typography and Pagination predecessors, T3-PDF-MEASUREMENT-REQUESTS supplies the actual language-aware request API, required immutable MeasuredText snapshot, selected profile and validation at every entry, including late caption candidates and footer. Its legacy migrations preserve existing layouts and fixed pagination budgets. Emitted TextRun binding is delivered by T3-PDF-MEASUREMENT-BINDING through PR #433. PdfTextOp forwarding, platform glyph measurement, rendering and full device acceptance remain separate undelivered capabilities.
```

### adr-binding-publication

Target: docs/adr/0007-report-interchange.md. Operation: append section; preserve every existing dated paragraph.
Expected normalized block SHA256: D8B5D7432D267C63CB8BD2A36FB4D3629CF410C473B589DF9B27EAD30272F615

```markdown
### TextRun measurement binding remote publication

T3-PDF-MEASUREMENT-BINDING is delivered through PR #433, reviewed head edf27103690ab6d062e95065ba2c4cf6798c6ac5, exact CI 37198374361 attempt 1, squash 16d708c0931bbe339d27d813a9e3dc717a4c66c8. Every emitted TextRun carries its accepted complete snapshot; preceding caption lines retain their original snapshot and the final shortened line uses the accepted candidate measurement. Splits, rebases and moved thumbnails preserve those values. Character-safe shortening and DEFAULT two-photo layout remain unchanged. R3 round 1's two test gaps were repaired; round 2 passed both axes. The candidate passed 344 report tests, six e2e tests, 13 unique semantic faults in 16 executions and full selftest. R5 PR #435 and normal cleanup exit 0 are recorded with the preserved recovery errors and later-seal limitations in the [complete archived card](../../specs/archive/tasks/T3-PDF-MEASUREMENT-BINDING.md). Dated pre-RED forecasts remain history. PdfTextOp forwarding and actual rounded placed-box validation remain TEXT-METRICS-OPS; Android glyphs, CJK, clipping and full device acceptance remain separate obligations.
```

### board-binding-row

Target: docs/TASK-BOARD.md. Operation: replace exact row once.
Expected normalized block SHA256: E2E367EAC34C887EA1F74D98BDB694A8EACF201942F969B6499AF97E8551E8A6

```markdown
| W4 | T3-PDF-MEASUREMENT-BINDING | TextRun精确快照、caption最终来源与DEFAULT双图同页 | T3-PDF-MEASUREMENT-REQUESTS | M | GPT-6 Astra · high | GPT-5.6 Sol R3 · high | **merged** via PR #433 (head edf27103, CI 37198374361/1, squash 16d708c0); TextRun snapshot binding delivered, unblocks TEXT-METRICS-OPS; historical pre-RED draft retained; ADR0007 synchronized; complete card preserved by targeted archival; PR #435 cleanup provenance retained. Metadata merge and cleanup govern strict closure. |
```

### claude-binding-current

Target: CLAUDE.md. Operation: replace exact current bullet once.
Expected normalized block SHA256: 89012AC55455AE7E2119C57C024214BC760972A2D845CCB602EEAC1466F0F500

```markdown
- **PDF measurement binding delivered (2026-10-04, PR #433)**: exact accepted measurement snapshots now follow all TextRun paths, including caption elision and split/rebased blocks. Head edf27103690ab6d062e95065ba2c4cf6798c6ac5 passed R3 round 2 and CI 37198374361/1; squash 16d708c0931bbe339d27d813a9e3dc717a4c66c8. PdfTextOp forwarding remains TEXT-METRICS-OPS; ADR0007 is synchronized and the [complete card](specs/archive/tasks/T3-PDF-MEASUREMENT-BINDING.md) is preserved by targeted archival, including PR #435 cleanup provenance. Metadata merge and cleanup govern strict closure.
```

## Generation evidence outside DoD

Capture an approved-base inventory of all unselected hot/cold cards and debt/lesson files before execution, including file names and raw SHA256. Record DryRun with unchanged physical inventory, the real exact-two call with raw cold bytes equal to reviewed hot payloads, complete index validation, then a repeat exact-two call with no byte or inventory change. Do not insert a mutating archive command into DoD. Use the delivered targeted capability; never substitute a legacy global sweep.

Current document statements describe the candidate's content once generated; they do not claim its future merge/cleanup has already happened. Product strict counts can change only after the actual metadata lifecycle completes.

## Frozen exact append payloads

The text inside each fence is the exact append suffix, including its leading blank line and final LF. Concatenate it to the pinned original source without trimming or newline conversion. Independent source blob/SHA256 and final cold hashes are in the runner.

### Append T1-APP-STORAGE-ANDROID

<!-- append:T1-APP-STORAGE-ANDROID -->
```text

## Evidence preservation boundary (2026-10-04)

The original human authorization applies only to this product's Opus R3 replacement. The original cleanup command and successful tool response are preserved; they expose no standalone native-exit field and retained only the last 15 output lines. Evidence3 has its own recorded launch and background completion exit 0; the earlier 265-test result is not its per-phase proof. Later byte seals are retrospective preservation, not original runtime seals. This closure reuses PR #351/#352 and the original device, review, CI and cleanup evidence without rerunning them.
```

### Append T3-PDF-MEASUREMENT-BINDING

<!-- append:T3-PDF-MEASUREMENT-BINDING -->
```text

## Evidence preservation boundary (2026-10-04)

The reviewed functional head is edf27103690ab6d062e95065ba2c4cf6798c6ac5 (PR #433), with CI 37198374361 attempt 1 and merge 16d708c0931bbe339d27d813a9e3dc717a4c66c8. Narrow R5 PR #435 uses head 3403e73c08d166493ccb660ad19acb0e410a3184, CI 37198928219 attempt 1 and merge 7381c39b0931d05743666392823ae9b99f6abd29. Normal cleanup returned 0 and directory, branch, registration and T24 were absent; its preceding long-path/not-worktree fallback errors remain in the full log. The 261-leaf preservation manifest F2B2298BA3330ECDE047BF7158306ECA3403B3680B75C0915257E3B385D82F0C and independent audit 05C8A8FCB93FD4CCAD58D97EC93BF10E2F0BCEF85F1DB6D919ECCF4FF699C2AA are later preservation checks. The separate 21-leaf R5/cleanup manifest is 31E81C1B551E0E10285FC041DACE92075C11B19B69E709A370D225E8480380D8. These seals do not replace original runtime evidence.
```

## Read-only acceptance runner

<!-- closeout-dod -->
```powershell
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$ordinal = [StringComparison]::Ordinal
function Assert-Text([string]$Actual, [string]$Expected, [string]$Name) {
    if (-not [string]::Equals($Actual, $Expected, $ordinal)) { throw "[CLOSEOUT-MISMATCH] $Name" }
}
function Assert-Hash([string]$Text, [string]$Expected) {
    Assert-Text ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($Text)))) $Expected 'digest'
}
function Read-Text([string]$Path) { $utf8.GetString([IO.File]::ReadAllBytes($Path)) }
function Read-Git([string[]]$Arguments) {
    $start = [Diagnostics.ProcessStartInfo]::new('git')
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::Start($start)
    $bytes = [IO.MemoryStream]::new()
    try {
        $errorRead = $process.StandardError.ReadToEndAsync()
        $process.StandardOutput.BaseStream.CopyTo($bytes)
        $process.WaitForExit()
        $errorText = $errorRead.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "[CLOSEOUT-GIT] $Arguments : $errorText" }
        return $utf8.GetString($bytes.ToArray())
    } finally { $bytes.Dispose(); $process.Dispose() }
}
function One-Match([string]$Text, [string]$Pattern) {
    $hits = [regex]::Matches($Text, $Pattern)
    if ($hits.Count -ne 1) { throw "[CLOSEOUT-UNIQUE] $Pattern" }
    return $hits[0]
}
$cardPath = 'specs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md'
$card = Read-Text $cardPath
$base = (Read-Git @('merge-base', 'HEAD', 'origin/master')).Trim()
$origin = (Read-Git @('rev-parse', 'origin/master')).Trim()
if ($base -cnotmatch '^[0-9a-f]{40}$') { throw '[CLOSEOUT-BASE]' }
Assert-Text $base $origin 'execution base must equal origin/master'
$null = Read-Git @('merge-base', '--is-ancestor', $base, 'HEAD')
$frozen = '069d35db3a62ceea0763b01e2ae7ffd77bdb397d'
$null = Read-Git @('merge-base', '--is-ancestor', $frozen, $base)
Assert-Text $card (Read-Git @('show', "${base}:$cardPath")) 'registered contract'
. ./scripts/_cards.ps1
$allowed = @(Get-YamlListItems (Get-FrontMatter $card) 'allow_paths')
$changed = (Read-Git @('diff', '--no-renames', '--name-only', $base, '--')).Split("`n", [StringSplitOptions]::RemoveEmptyEntries)
foreach ($path in $changed) {
    if (@($allowed | Where-Object { [string]::Equals($_, $path, $ordinal) }).Count -ne 1) { throw "[CLOSEOUT-SCOPE] $path" }
}
if ((Read-Git @('ls-files', '--others', '--exclude-standard')).Length) { throw '[CLOSEOUT-UNTRACKED]' }
pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ROUND45-DELIVERY-CLOSEOUT
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$products = @(
    @('T1-APP-STORAGE-ANDROID', 'dfffee93c5b711f1a5be5eea8cb4efea2a1cef2a', '41163C8F2D5E615C5C5E5D2A0B42AC2BC1887053251571A3600D8916847BC5FD', 'DEA9373D95B5C00FBBAE7DEB1FDA814041BE8EC5D9EAD647CCC30278C9B23B47'),
    @('T3-PDF-MEASUREMENT-BINDING', 'bee93d87bcd9eee38f8a32b0ed7589fad2681358', '5390C0E5C13DB03ED9C4A003C0877F33BB8C7539B8B5554DE6A9494CDB57C6FE', '71264073438BE0853CD85DD0BD69CAE8CCE6AEB64A8C58A31101D9AF88FA3077')
)
foreach ($product in $products) {
    $id, $blob, $sourceHash, $coldHash = $product
    $hot = "specs/tasks/$id.md"
    Assert-Text (Read-Git @('rev-parse', "${frozen}:$hot")).Trim() $blob 'source blob'
    $source = Read-Git @('show', "${frozen}:$hot")
    Assert-Hash $source $sourceHash
    Assert-Text (Read-Git @('show', "${base}:$hot")) $source 'execution source'
    $append = (One-Match $card ('(?s)<!-- append:' + [regex]::Escape($id) + ' -->\n```text\n(.*?)```')).Groups[1].Value
    $cold = Read-Text "specs/archive/tasks/$id.md"
    if (Test-Path -LiteralPath $hot) { throw "[CLOSEOUT-HOT] $id" }
    Assert-Text $cold ($source + $append) "raw source plus append: $id"
    Assert-Hash ($cold.TrimStart([char]0xFEFF).Replace("`r`n", "`n")) $coldHash
}
$blocks = @(
    @('adr-current-request-boundary', 'docs/adr/0007-report-interchange.md', '(?m)^After the remote Typography and Pagination predecessors,[^\n]+$', '82DE24F2105B91EC0D1DD3D82B6DEAF4EB4EB4D5B7568EEE11740DC916F27E39', 'replace'),
    @('adr-binding-publication', 'docs/adr/0007-report-interchange.md', '(?m)^### TextRun measurement binding remote publication\n\n[^\n]+$', 'D8B5D7432D267C63CB8BD2A36FB4D3629CF410C473B589DF9B27EAD30272F615', 'append'),
    @('board-binding-row', 'docs/TASK-BOARD.md', '(?m)^\| W4 \| T3-PDF-MEASUREMENT-BINDING \|[^\n]+$', 'E2E367EAC34C887EA1F74D98BDB694A8EACF201942F969B6499AF97E8551E8A6', 'replace'),
    @('claude-binding-current', 'CLAUDE.md', '(?m)^- \*\*PDF measurement binding delivered \(2026-10-04, PR #433\)\*\*:[^\n]+$', '89012AC55455AE7E2119C57C024214BC760972A2D845CCB602EEAC1466F0F500', 'replace')
)
foreach ($path in @('docs/adr/0007-report-interchange.md', 'docs/TASK-BOARD.md', 'CLAUDE.md')) {
    $expected = Read-Git @('show', "${base}:$path")
    if ($expected.Contains("`r") -or $expected.StartsWith([string][char]0xFEFF, $ordinal)) { throw "[CLOSEOUT-BASE-ENCODING] $path" }
    $actual = Read-Text $path
    foreach ($block in @($blocks | Where-Object { [string]::Equals($_[1], $path, $ordinal) })) {
        $name, $unused, $pattern, $hash, $operation = $block
        $text = (One-Match $card ('(?s)### ' + [regex]::Escape($name) + '\n.*?```markdown\n(.*?)\n```')).Groups[1].Value
        Assert-Hash $text $hash
        Assert-Hash (One-Match $actual $pattern).Value $hash
        if ($operation -eq 'replace') {
            $old = One-Match $expected $pattern
            $expected = $expected.Remove($old.Index, $old.Length).Insert($old.Index, $text)
        } elseif ($operation -eq 'append') {
            if ([regex]::Matches($expected, $pattern).Count) { throw "[CLOSEOUT-ALREADY-PRESENT] $name" }
            $expected += "`n" + $text + "`n"
        } else {
            throw "[CLOSEOUT-OPERATION] $operation"
        }
    }
    Assert-Text $actual $expected "entire document: $path"
}
Write-Output '[ROUND4-CLOSEOUT-OK]'
exit 0
```
