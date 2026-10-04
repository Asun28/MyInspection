---
id: T0-ROUND45-DELIVERY-CLOSEOUT
title: Preserve and synchronize three delivered product cards through targeted archival
status: todo
branch: T0-ROUND45-DELIVERY-CLOSEOUT
worktree: C:\wt\T0-ROUND45-DELIVERY-CLOSEOUT
depends_on: [T0-ARCHIVE-TARGETED-CARDS, T1-APP-STORAGE-ANDROID, T3-PDF-DEVICE-FIXTURE, T3-PDF-MEASUREMENT-BINDING]
allow_paths:
  - specs/tasks/T1-APP-STORAGE-ANDROID.md
  - specs/archive/tasks/T1-APP-STORAGE-ANDROID.md
  - specs/tasks/T3-PDF-DEVICE-FIXTURE.md
  - specs/archive/tasks/T3-PDF-DEVICE-FIXTURE.md
  - specs/tasks/T3-PDF-MEASUREMENT-BINDING.md
  - specs/archive/tasks/T3-PDF-MEASUREMENT-BINDING.md
  - specs/archive/cards-index.md
  - docs/adr/0007-report-interchange.md
  - docs/TASK-BOARD.md
  - CLAUDE.md
budget: 750
tier: 0
review_gate: codex {verdict:pass}
sweep: "Read-only exact-ID rg on the three product cards, docs/TASK-BOARD.md, CLAUDE.md and ADR0007 at observed7381; inspected ADR0006, SECURITY, probe recipe and archive README. Audit REPORT SHA256 90FAE970EAFB5F1FF7D0C4B12E2009D00244EA9EF5704D5F76609FD5397229AA records ten paths and already-synchronized Android faces. This records the performed scope sweep; current check-cards local compatibility disables CARD-SWEEP enforcement."
dod_command: & { $ErrorActionPreference = 'Stop'; function Read-ClosureText([string]$Path) { $s = [Text.UTF8Encoding]::new($false, $true).GetString([IO.File]::ReadAllBytes($Path)); if ($s.StartsWith([string][char]0xFEFF)) { $s = $s.Substring(1) }; $s = $s.Replace("`r`n", "`n"); if ($s.Contains("`r")) { throw "Unexpected bare CR: $Path" }; return $s }; function Assert-ClosureHash([string]$Text, [string]$Expected) { $actual = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($Text))); if (-not [string]::Equals($actual, $Expected, [StringComparison]::Ordinal)) { throw "Content digest mismatch: $actual" } }; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ROUND45-DELIVERY-CLOSEOUT; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; $cold = @{ 'T1-APP-STORAGE-ANDROID' = 'DEA9373D95B5C00FBBAE7DEB1FDA814041BE8EC5D9EAD647CCC30278C9B23B47'; 'T3-PDF-DEVICE-FIXTURE' = '4BF04E7BE77325E50E09BD4194A2B611DD5A4E6020415632977E25688C847719'; 'T3-PDF-MEASUREMENT-BINDING' = '71264073438BE0853CD85DD0BD69CAE8CCE6AEB64A8C58A31101D9AF88FA3077' }; foreach ($id in $cold.Keys) { if (Test-Path -LiteralPath "specs/tasks/$id.md") { throw "Hot card remains: $id" }; Assert-ClosureHash (Read-ClosureText "specs/archive/tasks/$id.md") $cold[$id] }; $blocks = @( @('docs/adr/0007-report-interchange.md', '(?m)^After the remote Typography and Pagination predecessors,[^\n]+$', '82DE24F2105B91EC0D1DD3D82B6DEAF4EB4EB4D5B7568EEE11740DC916F27E39'), @('docs/adr/0007-report-interchange.md', '(?m)^### TextRun measurement binding remote publication\n\n[^\n]+$', 'D8B5D7432D267C63CB8BD2A36FB4D3629CF410C473B589DF9B27EAD30272F615'), @('docs/adr/0007-report-interchange.md', '(?m)^### Device fixture remote publication\n\n[^\n]+$', '2B88A2E30661AA7C1214834D479CB37CF250BC245C47D97DD0C307E0F06BD3D4'), @('docs/TASK-BOARD.md', '(?m)^\| W4 \| T3-PDF-MEASUREMENT-BINDING \|[^\n]+$', 'E2E367EAC34C887EA1F74D98BDB694A8EACF201942F969B6499AF97E8551E8A6'), @('docs/TASK-BOARD.md', '(?m)^\| W4 \| T3-PDF-DEVICE-FIXTURE \|[^\n]+$', 'D199303DAD39E3AF8DAF8FADDF4DA4352940094AFFBE4C77ECD131D66A7A482A'), @('CLAUDE.md', '(?m)^- \*\*PDF measurement binding delivered \(2026-10-04, PR #433\)\*\*:[^\n]+$', '89012AC55455AE7E2119C57C024214BC760972A2D845CCB602EEAC1466F0F500'), @('CLAUDE.md', '(?m)^- \*\*PDF device fixture remote accounting \(PR #338\)\*\*:[^\n]+$', 'A63997F91FE289277477C0ACCF5E6A57A4662B9A0FAD7BC1C84C80059A3C6823') ); foreach ($b in $blocks) { $m = [regex]::Matches((Read-ClosureText $b[0]), $b[1]); if ($m.Count -ne 1) { throw "Expected exactly one document block: $($b[0]) / $($b[1])" }; Assert-ClosureHash $m[0].Value $b[2] }; Write-Output '[THREE-PRODUCT-CLOSEOUT-OK]'; exit 0 }
dod_exit: 0
dod_assert: Both existing gates succeed; exactly the three selected hot paths are absent; three cold normalized UTF-8/LF digests and seven unique exact document-block digests match frozen independent constants; final sentinel THREE-PRODUCT-CLOSEOUT-OK.
acceptance:
  - "A1 Preserve the complete existing contracts and dated history of exactly StorageAndroid, DeviceFixture and MeasurementBinding. Append only the reviewed provenance additions to hot sources. Expected cold normalized hashes are frozen in DoD; raw source-prefix and generator byte-preservation evidence is separately required."
  - "A2 Use the delivered archive.ps1 CardsOnly/CardIds mode for exactly those three IDs, with DryRun then real generation. Generate the complete cards-index with its existing generator, never hand-edit cold cards or index. Whole unselected hot/cold inventories and debt/lesson bytes stay unchanged; repeat selected generation has no byte changes. DoD checks all three hot absences, cold content and CheckCardsIndex."
  - "A3 Add Fixture remote PR338/head/CI/merge accounting and links to the complete five-blob record in ADR0007, Board and CLAUDE. Preserve local delivery, distinguish product fifth-round R3 from P4 seventh-round review, keep both unresolved follow-ups and their original locator, and retain cleanup output/seal limitations."
  - "A4 Add Binding PR433 actual snapshot delivery and existing PR435 cleanup provenance to ADR0007; correct only the current Requests paragraph's undelivered TextRun statement and current Board/CLAUDE pending summaries. Preserve all dated forecasts and still-pending Ops, platform glyph, CJK, clipping and full device acceptance."
  - "A5 Freeze exact proposed document blocks before candidate construction; DoD checks each complete block against its independent digest and rejects zero or duplicate matches. Review exact before/after replacements and unchanged surrounding content. Reuse root-audited original delivery/cleanup evidence; no historic rerun or fresh verification claim."
  - "A6 Complete normal formal R3, exact-head CI and merge, then metadata-unit R5/cleanup and an explicit R5.5 disposition before strict counts change. This necessary metadata split adds zero products and does not assert other unfinished products delivered."
forbid:
  - Registering this draft before root confirms archive capability and its R5 delivery plus the actual final base
  - Manually editing cold archive bodies or cards-index
  - Modifying product code, existing controllers, scripts or test helpers
  - Rewriting dated history, old sealed evidence, original task scopes or original cleanup outcomes
  - Including AndroidTextMeasurer or undelivered TEXT-METRICS-OPS as one of these three products
non_goals:
  - Product implementation, device acceptance or historical test and cleanup reruns
  - Closing the two existing Fixture reconciliation follow-ups
  - Debt or lesson sweeping, global archival, unrelated hot/cold movement or new product credit
hygiene: DoD is read-only and uses independent fixed constants. Validate its failure causes during the later authorized workflow; do not add a parallel test/helper file or promise a new product mutation batch. Original test and review receipts remain inherited evidence.
doc_sync: Three complete cards through generated archival; ADR0007, Board and CLAUDE delivery boundaries. The metadata unit's own status, final merge facts and cleanup follow the normal separately authorized R5 path.
---

# T0-ROUND45-DELIVERY-CLOSEOUT

## Finalized prerequisite record and separate implementation

This complete contract was finalized privately on2026-10-04 UTC for registration review at069d35db3a62ceea0763b01e2ae7ffd77bdb397d. It remains unregistered and todo; finalization does not execute metadata closure. The actual implementation baseline will be the subsequently merged registration/R5 state and must be independently rechecked before R1.

- Approved prerequisite/source base:069d35db3a62ceea0763b01e2ae7ffd77bdb397d.
- Archive capability PR436 reviewed head:52c1e0a75eaa54737ec35576acb91e00f10b452a.
- Archive R3: round2 spec/standards pass, zero findings, run_status success; saved verdict SHA256264314855E8CDDA767EF069C343F0149E9412715929AA250E919149EA0C8D750. Original round1 BLOCK remains history; no Reset.
- Archive CI:37207356196 attempt1, pull_request, success at that head; verify111451222145 and required111452911842 succeeded.
- Archive feature merge:83b2f87ba5b2997beffb9418d86a9006586e3002.
- Archive R5 PR437: head5077d517fba9f28010944e572e97e01a8e6f722a, CI37208089699 attempt1 success, merge069d35db3a62ceea0763b01e2ae7ffd77bdb397d.
- Recorded normal cleanup: native0; directory, branch, worktree registration and T24 absent. Root preservation manifest:CDA4DB9A9115375E947367AC71C278267EFC49C851F1A7BE3516F615C141E5D5. Cleanup advisory warnings remain in its full log; R5.5 explicitly reuses existing lessons with no new duplicate lesson.

These are recorded prerequisite facts, not executions of this future card. The saved PR/CI/cleanup records and verdict were read locally; no fresh remote query, runtime test or cleanup ran in finalization. Root's grant binds the exact CI job identities. Later preservation seals remain distinct from original runtime evidence.

The three source cards and append payloads are unchanged at this base, and all seven planned document blocks still match their intended old location or remain absent. Fixture's five blobs also match069d35db; its dated7381 comparison remains intact history. The two later archive-delivery updates in Board/CLAUDE remain outside this card's exact replacement blocks and must be preserved.

Registration has its own separately authorized lifecycle and must land before this implementation unit. This implementation allowlist contains exactly ten paths; it does not include modifying this metadata card. Later own-card R5 status is a separate existing workflow. The750-line ceiling is unchanged. Measuring the two-card registration projection does not prove the later ten-path metadata candidate fits; generation, its own full diff measurement and DoD remain unperformed.

## Frozen content and exact change plan

The three cold normalized UTF-8/LF SHA256 values are:

- T1-APP-STORAGE-ANDROID: DEA9373D95B5C00FBBAE7DEB1FDA814041BE8EC5D9EAD647CCC30278C9B23B47
- T3-PDF-DEVICE-FIXTURE: 4BF04E7BE77325E50E09BD4194A2B611DD5A4E6020415632977E25688C847719
- T3-PDF-MEASUREMENT-BINDING: 71264073438BE0853CD85DD0BD69CAE8CCE6AEB64A8C58A31101D9AF88FA3077

Fixture uses the locator-supplemented payload, not the earlier audited payload hash. Original audit and original inputs stay preserved. The new locator is inherited from root's verified reconciliation excerpt at session55a97974 line3534, notification2026-09-24T05:08:59.248Z, SHA256 AAD33FDB182451CD3FC0C5D192AAA2749274FAE8FA054DECC4D4D3FE415AED30. This preparation has not independently reverified that runtime evidence or all historical CI/cleanup.

1. Recheck the three full hot append payloads after this contract is registered and its R5 lands. Their expected content is frozen from approved069d35db. Stop on source drift; do not silently retarget expected constants.
2. Apply only the four exact replacements and three additions specified below: Requests paragraph, two Board rows, current Binding CLAUDE bullet, two ADR sections, and adjacent Fixture CLAUDE bullet. Keep every unrelated byte/paragraph.
3. Preserve each original hot file as an exact prefix. Use only the shipped targeted generator for the selected three card moves and complete cards-index projection.
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

### adr-fixture-publication

Target: docs/adr/0007-report-interchange.md. Operation: append section after Binding section; retain local Fixture delivery.
Expected normalized block SHA256: 2B88A2E30661AA7C1214834D479CB37CF250BC245C47D97DD0C307E0F06BD3D4

```markdown
### Device fixture remote publication

T3-PDF-DEVICE-FIXTURE's local delivery is preserved remotely by PR #338, reviewed head d6fa1df0dcbb51c7e2ee3a3fe4c847352b80fcba, CI 35958723288, merge 68d3833360fc2c9c7600974ba2af8f717e79ec16. All five product blobs at that merge match the observed 7381c39b main tree; their exact paths and Git IDs are in the [complete archived card](../../specs/archive/tasks/T3-PDF-DEVICE-FIXTURE.md). The product's Opus 5.5 fifth-round R3 on tree f22b89c4 remains separate from P4's seventh-round reconciliation review. Its two nonblocking follow-ups retain the original session/line/excerpt locator and remain unresolved by this closure. Original cleanup exit 0 and later absence observations retain the last-15-lines output limitation; retrospective seals do not replace runtime evidence. The delivered fixed input and dual hashes do not claim device or four-quality acceptance; DEVICE-ACCEPTANCE still owns that work.
```

### board-binding-row

Target: docs/TASK-BOARD.md. Operation: replace exact row once.
Expected normalized block SHA256: E2E367EAC34C887EA1F74D98BDB694A8EACF201942F969B6499AF97E8551E8A6

```markdown
| W4 | T3-PDF-MEASUREMENT-BINDING | TextRun精确快照、caption最终来源与DEFAULT双图同页 | T3-PDF-MEASUREMENT-REQUESTS | M | GPT-6 Astra · high | GPT-5.6 Sol R3 · high | **merged** via PR #433 (head edf27103, CI 37198374361/1, squash 16d708c0); TextRun snapshot binding delivered, unblocks TEXT-METRICS-OPS; historical pre-RED draft retained; ADR0007 synchronized; complete card preserved by targeted archival; PR #435 cleanup provenance retained. Metadata merge and cleanup govern strict closure. |
```

### board-fixture-row

Target: docs/TASK-BOARD.md. Operation: replace exact row once; preserve local record as prefix up to closing table delimiter.
Expected normalized block SHA256: D199303DAD39E3AF8DAF8FADDF4DA4352940094AFFBE4C77ECD131D66A7A482A

```markdown
| W4 | T3-PDF-DEVICE-FIXTURE | debug real80清单预检与固定ReportContent/双哈希输入 | T3-REPORT-CONTENT-ADAPTER,T1-SPIKE-PLATFORM | M | GPT-5.6 Terra · high | DeepSeek V4 Flash预审 → Opus 5.5 R3（Codex配额恢复前） | **merged**；2026-09-24本地08ab4d8a，feature89285d67；825行（用户批准超650/45k早停）；11测试、63/63变异；真实Kotlin builder复现双hash；DeepSeek V4 Flash 7轮预审，Opus 5.5 R3第5轮pass（第3–5轮经用户逐轮授权）；合并树=评审树f22b89c4；不宣称设备验收；远端 PR #338，head d6fa1df0dcbb51c7e2ee3a3fe4c847352b80fcba，CI 35958723288，merge 68d3833360fc2c9c7600974ba2af8f717e79ec16；[完整归档卡](../specs/archive/tasks/T3-PDF-DEVICE-FIXTURE.md)保留五 blob 对账、cleanup 证据边界及两项未解决跟进；P4 第7轮对账评审与产品第5轮 R3 分开；不增加设备验收结论 |
```

### claude-binding-current

Target: CLAUDE.md. Operation: replace exact current bullet once.
Expected normalized block SHA256: 89012AC55455AE7E2119C57C024214BC760972A2D845CCB602EEAC1466F0F500

```markdown
- **PDF measurement binding delivered (2026-10-04, PR #433)**: exact accepted measurement snapshots now follow all TextRun paths, including caption elision and split/rebased blocks. Head edf27103690ab6d062e95065ba2c4cf6798c6ac5 passed R3 round 2 and CI 37198374361/1; squash 16d708c0931bbe339d27d813a9e3dc717a4c66c8. PdfTextOp forwarding remains TEXT-METRICS-OPS; ADR0007 is synchronized and the [complete card](specs/archive/tasks/T3-PDF-MEASUREMENT-BINDING.md) is preserved by targeted archival, including PR #435 cleanup provenance. Metadata merge and cleanup govern strict closure.
```

### claude-fixture-remote

Target: CLAUDE.md. Operation: insert immediately before unchanged 2026-09-24 local Fixture paragraph.
Expected normalized block SHA256: A63997F91FE289277477C0ACCF5E6A57A4662B9A0FAD7BC1C84C80059A3C6823

```markdown
- **PDF device fixture remote accounting (PR #338)**: reviewed head d6fa1df0dcbb51c7e2ee3a3fe4c847352b80fcba, CI 35958723288 and merge 68d3833360fc2c9c7600974ba2af8f717e79ec16 preserve the five product blobs recorded in the [complete archived card](specs/archive/tasks/T3-PDF-DEVICE-FIXTURE.md); they match the observed 7381c39b main tree. The local product's Opus 5.5 fifth-round R3 on f22b89c4 and P4's seventh-round reconciliation review remain distinct. Both nonblocking follow-ups remain with their original locator. Cleanup exit 0, later absence observations, last-15-lines output retention and retrospective-seal limits stay documented. The historical local delivery paragraph below is unchanged; no device or four-quality acceptance is added.
```

## Generation evidence outside DoD

Capture an approved-base inventory of all unselected hot/cold cards and debt/lesson files before execution, including file names and raw SHA256. Record DryRun with unchanged physical inventory, the real exact-three call with raw cold bytes equal to reviewed hot payloads, complete index validation, then a repeat exact-three call with no byte or inventory change. Do not insert a mutating archive command into DoD. Do not substitute legacy global archive for the undelivered targeted capability.

Current document statements describe the candidate's content once generated; they do not claim its future merge/cleanup has already happened. Product strict counts can change only after the actual metadata lifecycle completes.
