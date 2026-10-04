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

## Round4 original generation evidence carrier

This appendix supplies the separately required A1/A2 evidence. The entire original card above, including eight allowed paths, six acceptance items, four document operations, two append payloads/cold hashes and read-only DoD, is unchanged. No new product or original cleanup run is claimed. The checker below is additional read-only evidence verification, not a replacement for the original DoD or formal R3.

The reviewer receives this complete card from the fixed baseline via review.ps1:591–602/885–886. The local, gitignored worktree-relative input is `_local/round4-review-evidence/`: `original/` is the complete 231-file original bundle plus its original root artifact-manifest.json; `runtime.jsonl` is the exact original outer-call excerpt. Missing input is a verification failure. No PR comment, external path alone, symlink, model change or permission change is part of this mechanism.

Original manifest SHA256 is A65E109B142A9AED3EA3F4A2BD01A04EA0117E55831A6BE2DA5F47A4CFE432DD. Original runtime excerpt SHA256 is ADEA5CEEB8E7798250871086C57EEE24FDF9990BB8B3F992D61A71C098525DE2. Raw payload is 2236636 bytes plus manifest 41157 bytes and excerpt 49034 bytes. Every physical original file, including metadata, cache and nested manifests, is retained; only its exact root manifest is excluded from its own inventory. Never execute archived generate/capture code with historical absolute paths.

The original JSONL source is owner session 01a0aab2-5a58-7341-b127-757880b5d8fa, lines14784–14803, byte offset62434409/length49034; source prefix through byte62483443 has SHA256 BE498E328C91F60221EFD02D068FE1161336203FE5C8888C65269F66FF3A9B5F. Original outer prepare/dryrun/generate/documents records return native0 and include call/result linkage and UTC. Their source is preserved exactly; extraction and byte seals are later preservation, not original signed timestamps.

Original runtime was based at d4aa3418176922fc890f3763946c6b4a8c1facf1 and bound to head70ba04304de3cb490be748476215f7014a9c8852/tree4898df2bf33d49b159a27fb57ced5907fcfa3d1a. The helper performed in-memory inventory equality assertions after DryRun/repeat; independently saved post-DryRun/post-repeat arrays are absent, and Python optimization environment was not separately captured. The saved pre/post generation arrays, actual commands, helper source, outer exits and committed artifacts remain verifiable. No replay is performed or claimed by this checker.

For verification, extract the exact `round4-evidence-python` fence to a TEMP Python file and invoke it with the reviewed repository directory and the evidence directory. It reads full raw material and Git objects, validates the historical fixed tree, then checks the current clean candidate against its actual local origin/master with the same operations and the delivered read-only index check. The caller/root separately pins remote freshness and actual publication identity; a local-ref check is not a remote freshness check. Retain both historical and current identity fields from its output. A new publication must not relabel the original runtime.

Expected success is native0 with `[ROUND4-EVIDENCE-OK]` only after custody, runtime and historical-tree stages. Failures retain native1 and a specific code. The second fence is the complete real-stage negative harness, used only on private copies as specified by T0-ROUND4-EVIDENCE-CARRIER. It intentionally corrupts inputs after custody for later-stage tests; the public verifier has no flag bypassing custody. Formal review still independently judges this evidence and its stated limitations.

<!-- round4-evidence-python -->
```python
"""Read-only verification of the fixed Round4 evidence and a current candidate."""
import datetime
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
from pathlib import Path, PurePosixPath

BASE = 'd4aa3418176922fc890f3763946c6b4a8c1facf1'
HEAD = '70ba04304de3cb490be748476215f7014a9c8852'
TREE = '4898df2bf33d49b159a27fb57ced5907fcfa3d1a'
CARD = 'specs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md'
MANIFEST = 'A65E109B142A9AED3EA3F4A2BD01A04EA0117E55831A6BE2DA5F47A4CFE432DD'
TRACE = 'ADEA5CEEB8E7798250871086C57EEE24FDF9990BB8B3F992D61A71C098525DE2'
IDS = ['T1-APP-STORAGE-ANDROID', 'T3-PDF-MEASUREMENT-BINDING']
COLD = ['DEA9373D95B5C00FBBAE7DEB1FDA814041BE8EC5D9EAD647CCC30278C9B23B47',
        '71264073438BE0853CD85DD0BD69CAE8CCE6AEB64A8C58A31101D9AF88FA3077']
ARCHIVE = ['pwsh', '-NoProfile', '-File', 'scripts/archive.ps1', '-CardsOnly', '-CardIds', ','.join(IDS)]
INDEX = 'specs/archive/cards-index.md'
SELECTED = {f'specs/{folder}/{name}.md' for folder in ['tasks', 'archive/tasks'] for name in IDS} | {INDEX}
DOCS = {'docs/adr/0007-report-interchange.md', 'docs/TASK-BOARD.md', 'CLAUDE.md'}
BLOCKS = [
    ('adr-current-request-boundary', 'docs/adr/0007-report-interchange.md', r'^After the remote Typography and Pagination predecessors,[^\n]+$', '82DE24F2105B91EC0D1DD3D82B6DEAF4EB4EB4D5B7568EEE11740DC916F27E39', False),
    ('adr-binding-publication', 'docs/adr/0007-report-interchange.md', r'^### TextRun measurement binding remote publication\n\n[^\n]+$', 'D8B5D7432D267C63CB8BD2A36FB4D3629CF410C473B589DF9B27EAD30272F615', True),
    ('board-binding-row', 'docs/TASK-BOARD.md', r'^\| W4 \| T3-PDF-MEASUREMENT-BINDING \|[^\n]+$', 'E2E367EAC34C887EA1F74D98BDB694A8EACF201942F969B6499AF97E8551E8A6', False),
    ('claude-binding-current', 'CLAUDE.md', r'^- \*\*PDF measurement binding delivered \(2026-10-04, PR #433\)\*\*:[^\n]+$', '89012AC55455AE7E2119C57C024214BC760972A2D845CCB602EEAC1466F0F500', False),
]


class EvidenceError(Exception):
    pass


def need(condition, code):
    if not condition:
        raise EvidenceError(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest().upper()


def decode_json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            need(key not in result, 'DUPLICATE')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs)


def read_json(path):
    return decode_json(path.read_bytes())


def physical(root):
    need(root.is_dir(), 'BUNDLE')
    result = {}
    for parent, dirs, files in os.walk(root, followlinks=False):
        for path in [Path(parent)] + [Path(parent) / name for name in dirs + files]:
            info = path.lstat()
            need(not stat.S_ISLNK(info.st_mode) and not getattr(info, 'st_file_attributes', 0) & 1024, 'REPARSE')
        for name in files:
            path = Path(parent) / name
            need(path.is_file(), 'PATH')
            result[path.relative_to(root).as_posix()] = sha(path.read_bytes())
    return result


def manifest_paths(entries):
    result = {}
    for entry in entries:
        name = entry['path']
        path = PurePosixPath(name)
        need(name and not path.is_absolute() and path.as_posix() == name and
             all(part not in ('', '.', '..') and ':' not in part and '\\' not in part for part in path.parts), 'PATH')
        need(name not in result, 'DUPLICATE')
        result[name] = entry
    return result


def bundle(evidence):
    all_files = physical(evidence)
    root = evidence / 'original'
    need(root.is_dir() and (evidence / 'runtime.jsonl').is_file(), 'BUNDLE')
    raw = (root / 'artifact-manifest.json').read_bytes()
    need(sha(raw) == MANIFEST, 'ROOT-MANIFEST')
    entries = manifest_paths(decode_json(raw)['files'])
    expected = {'original/' + name for name in entries} | {'original/artifact-manifest.json', 'runtime.jsonl'}
    need(set(all_files) == expected, 'INVENTORY')
    for name, entry in entries.items():
        data = (root / name).read_bytes()
        need(len(data) == entry['bytes'] and sha(data) == entry['sha256'], 'PAYLOAD')
    trace = (evidence / 'runtime.jsonl').read_bytes()
    need(sha(trace) == TRACE, 'TRACE')
    return root, [decode_json(line) for line in trace.splitlines()]


def utc(text):
    value = datetime.datetime.fromisoformat(text.replace('Z', '+00:00'))
    need(value.utcoffset() == datetime.timedelta(0), 'ORDER')
    return value


def phase(execution, start, stdout, argv, marker):
    need(execution.get('argv') == argv and start.get('argv') == argv, 'ARGV')
    need(type(execution.get('nativeExit')) is int and execution['nativeExit'] == 0, 'NATIVE')
    need(execution.get('cwd') == 'C:\\wt\\T0-ROUND45-DELIVERY-CLOSEOUT', 'CWD')
    need(start == {key: execution[key] for key in ('argv', 'cwd', 'startedUtc')}, 'START')
    a, b = utc(execution['startedUtc']), utc(execution['endedUtc'])
    need(a < b, 'ORDER')
    need(stdout.splitlines().count(marker) == 1, 'OUTPUT')
    return a, b


def outer(records, helper):
    payloads = [record['payload'] for record in records]
    calls = {p['call_id']: p for p in payloads if p.get('type') == 'custom_tool_call'}
    outputs = {p['call_id']: p for p in payloads if p.get('type') == 'custom_tool_call_output'}
    expected = ['call_tfTVDcx89hcpe54Gx7b8Q2Xq', 'call_JFQ5JAHDp6kk9I8AEe2OksqI', 'call_R1T3DAOMIh1MRsKsW9l7AJVK']
    need(set(calls) == set(outputs) == set(expected), 'CALL-ID')
    source = calls[expected[0]]['input']
    patch = json.JSONDecoder().raw_decode(source[source.index('tools.apply_patch(') + len('tools.apply_patch('):])[0]
    recovered = ('\n'.join(line[1:] for line in patch.splitlines() if line.startswith('+')) + '\n').encode('utf8')
    need(recovered == helper and sha(helper) == '4B2F771C39A7CE171E0B951BCFE7960A0E27DF8F1E9CCB6A46F4A175F174C988', 'HELPER-SOURCE')
    ends = []
    for number, name in enumerate(['prepare', 'dryrun', 'generate', 'documents']):
        command = 'python _local/round4-real-closeout-20261004/generate-candidate.py ' + name
        events = [p for p in payloads if p.get('type') == 'item_completed' and p.get('item', {}).get('command', [''])[-1] == command]
        need(len(events) == 1, 'OUTER-COMMAND')
        event = events[0]
        item = event['item']
        need(item['command'] == ['C:\\Program Files\\PowerShell\\7\\pwsh.exe', '-Command', command], 'OUTER-COMMAND')
        need(type(item['exit_code']) is int and item['exit_code'] == 0, 'OUTER-NATIVE')
        need(decode_json(item['stdout'].splitlines()[-1]) == {'phase': name, 'completed': True}, 'OUTER-OUTPUT')
        start, end = event['started_at_ms'], event['completed_at_ms']
        need(start < end and (not ends or ends[-1] < start), 'ORDER')
        ends.append(end)
        call = expected[0 if number < 2 else number - 1]
        need(command in calls[call]['input'], 'OUTER-COMMAND')
        returned = [decode_json(x['text']) for x in outputs[call]['output'] if x.get('text', '').startswith('{"chunk_id"')]
        need(any(r.get('exit_code') == 0 and r.get('output') == item['stdout'] for r in returned), 'OUTER-OUTPUT')


def runtime(root, records):
    outer(records, (root / 'generate-candidate.py').read_bytes())
    need(sha((root / 'capture.py').read_bytes()) == '0C7EC9718FDF9780B6AF2DE91CBCF0C4657A92B9EC6EBA5CD56780718DEEAF84', 'CAPTURE-SOURCE')
    for name in ['prepare', 'dryrun', 'generate', 'documents']:
        folder = root / 'runs' / (name + '-refs')
        record = read_json(folder / 'execution.json')
        need(record['argv'] == ['git', 'rev-parse', 'HEAD', 'origin/master'] and record['nativeExit'] == 0 and
             (folder / 'stdout.raw').read_bytes().splitlines() == [BASE.encode(), BASE.encode()], 'RUNTIME-BASE')
    previous = None
    phases = [('actual-generator-dryrun', ARCHIVE + ['-DryRun'], b'[ARCHIVE-CARDS-DRYRUN] selected=2; no writes'),
              ('actual-generator', ARCHIVE, b'[ARCHIVE-CARDS-OK] selected=2; moved=2'),
              ('actual-generated-index-check', ['pwsh', '-NoProfile', '-File', 'scripts/archive.ps1', '-CheckCardsIndex'], b'[ARCHIVE-CHECK-OK] archive cards-index check: PASS'),
              ('actual-generator-repeat', ARCHIVE, b'[ARCHIVE-CARDS-OK] selected=2; moved=0')]
    for name, argv, marker in phases:
        folder = root / 'runs' / name
        start, end = phase(read_json(folder / 'execution.json'), read_json(folder / 'start.json'), (folder / 'stdout.raw').read_bytes(), argv, marker)
        need(previous is None or previous < start, 'ORDER')
        need((folder / 'stderr.raw').read_bytes() == b'', 'STDERR')
        need(type(read_json(folder / 'process.json')['pid']) is int, 'PROCESS')
        outer_name = 'dryrun' if name == 'actual-generator-dryrun' else 'generate'
        command = 'python _local/round4-real-closeout-20261004/generate-candidate.py ' + outer_name
        event = next(r['payload'] for r in records if r['payload'].get('item', {}).get('command', [''])[-1] == command)
        need(event['started_at_ms'] <= start.timestamp() * 1000 < end.timestamp() * 1000 <= event['completed_at_ms'], 'ORDER')
        previous = end


def git(repo, *args, data=None):
    env = os.environ.copy()
    env['GIT_OPTIONAL_LOCKS'] = '0'
    result = subprocess.run(['git', '-C', str(repo), *args], input=data, capture_output=True, env=env)
    need(result.returncode == 0, 'GIT')
    return result.stdout


def tree(repo, revision):
    entries = []
    for record in git(repo, 'ls-tree', '-rz', '--full-tree', revision).split(b'\0'):
        if record:
            meta, path = record.split(b'\t', 1)
            mode, kind, oid = meta.split()
            need(kind == b'blob' and mode in (b'100644', b'100755'), 'TREE-TYPE')
            entries.append((path.decode('utf8'), oid))
    raw = git(repo, 'cat-file', '--batch', data=b'\n'.join(oid for _, oid in entries) + b'\n')
    offset, result = 0, {}
    for path, oid in entries:
        end = raw.index(b'\n', offset)
        found, kind, size = raw[offset:end].split()
        need(found == oid and kind == b'blob', 'GIT-BLOB')
        size = int(size)
        result[path] = raw[end + 1:end + 1 + size]
        offset = end + size + 2
    need(offset == len(raw), 'GIT-BLOB')
    return result


def binding(record):
    need(record.get('head') == HEAD, 'HEAD')
    need(record.get('tree') == TREE, 'TREE')


def inventories(baseline, before, prepared, generated):
    extras = {'.git', '.review/T0-ROUND45-DELIVERY-CLOSEOUT.red'}
    need(set(before) == set(baseline) | extras and all(before[path] == sha(raw) for path, raw in baseline.items()), 'BASE-INVENTORY')
    hot = {f'specs/tasks/{name}.md' for name in IDS}
    need(set(prepared) == set(before) and all(prepared[path] == before[path] for path in before if path not in hot), 'PREPARED')
    need([prepared[f'specs/tasks/{name}.md'] for name in IDS] == COLD, 'PREPARED')
    need({p: value for p, value in before.items() if p not in SELECTED} ==
         {p: value for p, value in generated.items() if p not in SELECTED}, 'UNSELECTED')
    need(set(generated) == (set(before) - hot) | {f'specs/archive/tasks/{name}.md' for name in IDS}, 'GENERATED')
    need([generated[f'specs/archive/tasks/{name}.md'] for name in IDS] == COLD, 'GENERATED')
    need(generated[INDEX] == 'C45A6F193B0E0FC20C3E4612F820A6A99A0AE580918228D7C448A36D5FB188AB', 'GENERATED')


def one(text, pattern):
    matches = list(re.finditer(pattern, text, re.MULTILINE))
    need(len(matches) == 1, 'UNIQUE')
    return matches[0]


def candidate(baseline, actual, card):
    expected = dict(baseline)
    text = card.decode('utf8')
    for name, digest in zip(IDS, COLD):
        hot, cold = f'specs/tasks/{name}.md', f'specs/archive/tasks/{name}.md'
        suffix = one(text, '(?s)<!-- append:' + re.escape(name) + ' -->\n```text\n(.*?)```').group(1).encode('utf8')
        expected[cold] = baseline[hot] + suffix
        del expected[hot]
        need(hot not in actual and actual.get(cold) == expected[cold] and sha(expected[cold]) == digest, 'COLD')
    for name, path, pattern, digest, append in BLOCKS:
        block = one(text, '(?s)### ' + re.escape(name) + '\n.*?```markdown\n(.*?)\n```').group(1)
        need(sha(block.encode('utf8')) == digest, 'BLOCK')
        before = expected[path].decode('utf8')
        if append:
            need(not re.search(pattern, before, re.MULTILINE), 'UNIQUE')
            after = before + '\n' + block + '\n'
        else:
            match = one(before, pattern)
            after = before[:match.start()] + block + before[match.end():]
        expected[path] = after.encode('utf8')
    need(all(actual.get(path) == expected[path] for path in DOCS), 'DOCUMENT')
    expected[INDEX] = actual[INDEX]
    need(actual == expected, 'SCOPE')


def verify(repo, evidence):
    root, records = bundle(evidence)
    print('[EVIDENCE-CUSTODY-OK]')
    runtime(root, records)
    print('[EVIDENCE-RUNTIME-OK]')
    binding(read_json(root / 'candidate-binding.json'))
    need(git(repo, 'rev-parse', HEAD + '^{tree}').decode().strip() == TREE, 'TREE')
    baseline, historical = tree(repo, BASE), tree(repo, HEAD)
    need(sha(baseline[CARD]) == 'D936EF9F65F9BE5FE22CA526B566F5F46206F86AE85679668D581227E787E1EE', 'CONTRACT')
    inventories(baseline, *[read_json(root / name) for name in ['before-actual-inventory.json', 'before-generator-inventory.json', 'after-generator-inventory.json']])
    candidate(baseline, historical, baseline[CARD])
    need(sha(historical[INDEX]) == 'C45A6F193B0E0FC20C3E4612F820A6A99A0AE580918228D7C448A36D5FB188AB', 'INDEX')
    for path in DOCS | {INDEX} | {f'specs/archive/tasks/{name}.md' for name in IDS}:
        need((root / 'canonical-preserved' / path).read_bytes() == historical[path], 'PRESERVED')
    print('[EVIDENCE-HISTORICAL-TREE-OK]')
    current = git(repo, 'rev-parse', 'HEAD').decode().strip()
    base = git(repo, 'rev-parse', 'origin/master').decode().strip()
    git(repo, 'merge-base', '--is-ancestor', BASE, base)
    need(git(repo, 'merge-base', current, base).decode().strip() == base, 'CURRENT-BASE')
    need(not git(repo, 'status', '--porcelain'), 'CURRENT-DIRTY')
    base_files, head_files = tree(repo, base), tree(repo, current)
    need(base_files[CARD] == head_files[CARD], 'CURRENT-CONTRACT')
    candidate(base_files, head_files, base_files[CARD])
    changes = git(repo, 'diff', '--raw', '--no-renames', base, current).decode().splitlines()
    need({line.split('\t')[1] for line in changes} == SELECTED | DOCS, 'CURRENT-SCOPE')
    for line in changes:
        old, new = line.split('\t')[0].split()[:2]
        need(old[1:] in ('000000', '100644') and new in ('000000', '100644'), 'CURRENT-MODE')
    check = subprocess.run(['pwsh', '-NoProfile', '-File', 'scripts/archive.ps1', '-CheckCardsIndex'], cwd=repo, capture_output=True)
    need(check.returncode == 0 and check.stdout.splitlines().count(b'[ARCHIVE-CHECK-OK] archive cards-index check: PASS') == 1, 'INDEX')
    print(json.dumps({'originalBase': BASE, 'originalHead': HEAD, 'originalTree': TREE,
                      'currentBase': base, 'currentHead': current,
                      'currentTree': git(repo, 'rev-parse', current + '^{tree}').decode().strip(),
                      'originalEndpointArrays': 'not separately saved; original in-memory assertions and outer native exits retained',
                      'replayPerformed': False}))
    print('[ROUND4-EVIDENCE-OK]')


if __name__ == '__main__':
    try:
        verify(Path(sys.argv[1]), Path(sys.argv[2]))
    except Exception as error:
        print(f'[ROUND4-EVIDENCE-FAIL] {error}', file=sys.stderr)
        sys.exit(1)
```

<!-- round4-evidence-tests -->
```python
"""One case per process; fixtures are private copies of the fixed original data."""
import copy
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
source, evidence, repo, case = map(Path, sys.argv[1:5])
case = str(case)
spec = importlib.util.spec_from_file_location('evidence', source)
v = importlib.util.module_from_spec(spec)
spec.loader.exec_module(v)
root = evidence / 'original'
before = v.physical(evidence)
saved = {}
created = []


def overwrite(path, content):
    saved[path] = path.read_bytes()
    path.write_bytes(content)


def expect(code, action):
    try:
        action()
    except v.EvidenceError as error:
        if str(error) != code:
            raise AssertionError(f'{case}: wanted {code}, got {error}')
        print(f'[EXPECTED-{code}]')
        return
    raise AssertionError(f'{case}: did not reject {code}')


try:
    if case == 'positive':
        v.verify(repo, evidence)
    elif case in {'missing-bundle', 'missing-file', 'tampered-file', 'root-manifest', 'extra-file', 'reparse'}:
        target = root / 'runs/actual-generator/stdout.raw'
        if case == 'missing-bundle':
            expect('BUNDLE', lambda: v.verify(repo, evidence / 'absent'))
        elif case == 'missing-file':
            saved[target] = target.read_bytes()
            target.unlink()
            expect('INVENTORY', lambda: v.bundle(evidence))
        elif case == 'tampered-file':
            overwrite(target, target.read_bytes() + b'x')
            expect('PAYLOAD', lambda: v.bundle(evidence))
        elif case == 'root-manifest':
            target = root / 'artifact-manifest.json'
            overwrite(target, target.read_bytes() + b' ')
            expect('ROOT-MANIFEST', lambda: v.bundle(evidence))
        elif case == 'extra-file':
            target = root / 'unexpected.txt'
            target.write_bytes(b'extra')
            created.append(target)
            expect('INVENTORY', lambda: v.bundle(evidence))
        else:
            target = root / 'unexpected-link'
            quote = lambda path: "'" + str(path).replace("'", "''") + "'"
            command = f"New-Item -ItemType Junction -Path {quote(target)} -Target {quote(root / 'runs')} | Out-Null"
            result = subprocess.run(['pwsh', '-NoProfile', '-Command', command], capture_output=True)
            if result.returncode:
                raise RuntimeError(result.stderr.decode('utf8', errors='replace'))
            created.append(target)
            expect('REPARSE', lambda: v.bundle(evidence))
    elif case in {'path-escape', 'duplicate-path'}:
        entries = v.read_json(root / 'artifact-manifest.json')['files']
        if case == 'path-escape':
            entries[0]['path'] = '../outside'
            expect('PATH', lambda: v.manifest_paths(entries))
        else:
            entries.append(copy.deepcopy(entries[0]))
            expect('DUPLICATE', lambda: v.manifest_paths(entries))
    elif case in {'argv', 'native', 'order', 'missing-command'}:
        label = 'actual-generator'
        execution = v.read_json(root / 'runs' / label / 'execution.json')
        start = v.read_json(root / 'runs' / label / 'start.json')
        output = (root / 'runs' / label / 'stdout.raw').read_bytes()
        if case == 'argv':
            execution['argv'][-1] += ',T3-PDF-DEVICE-FIXTURE'
            start['argv'] = execution['argv']
            code = 'ARGV'
        elif case == 'native':
            execution['nativeExit'] = 1
            code = 'NATIVE'
        elif case == 'missing-command':
            del execution['argv']
            code = 'ARGV'
        else:
            execution['endedUtc'] = '2026-10-04T15:00:00+00:00'
            code = 'ORDER'
        expect(code, lambda: v.phase(execution, start, output, v.ARCHIVE, b'[ARCHIVE-CARDS-OK] selected=2; moved=2'))
    elif case in {'outer-native', 'outer-call', 'helper-source'}:
        records = [v.decode_json(line) for line in (evidence / 'runtime.jsonl').read_bytes().splitlines()]
        helper = (root / 'generate-candidate.py').read_bytes()
        if case == 'outer-native':
            event = next(x for x in records if x.get('payload', {}).get('item', {}).get('command', [''])[-1].endswith('generate-candidate.py dryrun'))
            event['payload']['item']['exit_code'] = 1
            code = 'OUTER-NATIVE'
        elif case == 'outer-call':
            event = next(x for x in records if x.get('payload', {}).get('type') == 'custom_tool_call_output')
            event['payload']['call_id'] = 'wrong-call'
            code = 'CALL-ID'
        else:
            helper += b'# changed\n'
            code = 'HELPER-SOURCE'
        expect(code, lambda: v.outer(records, helper))
    elif case in {'head', 'tree'}:
        binding = v.read_json(root / 'candidate-binding.json')
        binding[case] = v.BASE
        expect(case.upper(), lambda: v.binding(binding))
    elif case in {'unselected', 'missing-unselected', 'base-inventory'}:
        baseline = v.tree(repo, v.BASE)
        a = v.read_json(root / 'before-actual-inventory.json')
        b = v.read_json(root / 'before-generator-inventory.json')
        c = v.read_json(root / 'after-generator-inventory.json')
        name = 'specs/tasks/T3-PDF-DEVICE-FIXTURE.md'
        if case == 'base-inventory':
            a[name] = '0' * 64
            code = 'BASE-INVENTORY'
        elif case == 'missing-unselected':
            del c[name]
            code = 'UNSELECTED'
        else:
            c[name] = '0' * 64
            code = 'UNSELECTED'
        expect(code, lambda: v.inventories(baseline, a, b, c))
    elif case in {'cold', 'document', 'outside-scope'}:
        baseline = v.tree(repo, v.BASE)
        candidate = v.tree(repo, v.HEAD)
        card = baseline[v.CARD]
        if case == 'cold':
            path = f'specs/archive/tasks/{v.IDS[0]}.md'
            candidate[path] += b'x'
            code = 'COLD'
        elif case == 'document':
            candidate['docs/adr/0007-report-interchange.md'] += b'outside\n'
            code = 'DOCUMENT'
        else:
            candidate['docs/QUALITY-RUBRIC.md'] += b'x'
            code = 'SCOPE'
        expect(code, lambda: v.candidate(baseline, candidate, card))
    else:
        raise ValueError(case)
finally:
    for path, data in saved.items():
        path.write_bytes(data)
    for path in created:
        if path.is_dir():
            path.rmdir()
        else:
            path.unlink()
    if v.physical(evidence) != before:
        raise AssertionError('fixture restoration failed')
print(json.dumps({'case': case, 'completed': True, 'fixtureRestored': True}))
sys.exit(0 if case == 'positive' else 1)
```
