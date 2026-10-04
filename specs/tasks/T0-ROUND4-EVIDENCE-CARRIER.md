---
id: T0-ROUND4-EVIDENCE-CARRIER
title: Expose and independently verify the preserved round4 generation evidence
status: todo
branch: T0-ROUND4-EVIDENCE-CARRIER
worktree: C:\wt\T0-ROUND4-EVIDENCE-CARRIER
depends_on: [T0-ROUND45-DELIVERY-CLOSEOUT-REGISTER]
allow_paths:
  - specs/tasks/T0-ROUND4-EVIDENCE-CARRIER.md
  - specs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md
  - docs/TASK-BOARD.md
budget: 750
tier: 0
review_gate: codex {verdict:pass}
acceptance:
  - "A1 Add only this necessary amendment card, its one W0 Board row, and the evidence appendix to the registered closeout card. Preserve every original closeout byte as a prefix, its eight paths, six acceptance rows, four operations, two cold hashes and read-only DoD. Compare the complete Board projection to the actual approved base."
  - "A2 The complete appendix includes discoverable fixed raw-data pins and all readable verifier and negative-test source. Verify full physical bundle membership and hashes, original helper source/call-result/native/UTC evidence, full approved-base and unselected inventory relationships, original head/tree and current candidate content/index/scope. Do not trust proof booleans as verification."
  - "A3 Test the real checker privately with a complete positive and specific missing, tampered, path, reparse, argv, native, ordering, wrong-head/tree, unselected, cold and outside-document negatives. Every negative reaches its intended guard; restore the exact fixture inventory. Retain original runtime and later preservation distinctions, including absent independent endpoint arrays and unknown Python optimization environment. No historical generation or replay is required or claimed."
  - "A4 This metadata amendment adds no product or strict closure. Only separate authorization permits actual adoption, independent normal R3, exact-head CI, merge and its narrow R5/cleanup. Closeout PR440 remains BLOCK until its own later authorized workflow. Future publication pins remain null in private preparation. Round5 remains deferred."
dod_command: & { $ErrorActionPreference = 'Stop'; $text = [IO.File]::ReadAllText('specs/tasks/T0-ROUND4-EVIDENCE-CARRIER.md'); $m = [regex]::Matches($text, '(?s)<!-- evidence-amendment-dod -->\n```powershell\n(.*?)\n```'); if ($m.Count -ne 1) { throw '[AMEND-RUNNER]' }; & ([scriptblock]::Create($m[0].Groups[1].Value)); exit $LASTEXITCODE }
dod_exit: 0
dod_assert: Exact three-path metadata projection, future complete hash and original prefix, unique one-row Board delta, both card checks and AMENDMENT-OK.
forbid:
  - Product code, controllers, review scripts, configuration, model routing or permission changes
  - Editing old sealed evidence, verdicts, counters, frozen closeout payloads or original cleanup outcomes
  - Claiming private checks are actual publication, R3, CI, merge, closure or Round5
hygiene: Complete readable code and test cost is included in this amendment diff. Evidence data is local and immutable; no new code is hidden there. No Reset or routed-skip acceptance.
doc_sync: One new Board row only; the closeout card remains todo. This amendment's own status and actual delivery facts require its separate normal R5.
---

# T0-ROUND4-EVIDENCE-CARRIER

PR440 round1 independently blocked missing discoverable generation evidence. This amendment uses the existing complete-base-card review input, without changing review.ps1. Its only contract effect is the explicit evidence carrier and complete verifiable method appended to the original card. The fixed original head remains historical, not a fabricated future publication head.

Approved preparation base: d4aa3418176922fc890f3763946c6b4a8c1facf1. Complete proposed closeout-card SHA256: 0B03472A054528AEB413E43F9A213AD245963F707EBE3D6AE1C8D14279DC2670. Source prefix SHA256: D936EF9F65F9BE5FE22CA526B566F5F46206F86AE85679668D581227E787E1EE.

The two Python fences in the appendix are the entire new checker and test implementation. After budget fit, extract them byte-for-byte into private/TEMP files; do not execute archived capture/generate scripts. The test command takes verifier file, evidence directory, isolated repository, and one case name. Positive expects native 0 and ROUND4-EVIDENCE-OK; each negative expects native 1, its exact EXPECTED marker and fixtureRestored:true. Infrastructure errors, missing markers or arbitrary nonzero exits are failures of the test.

The complete case set is positive, missing-bundle, missing-file, tampered-file, root-manifest, extra-file, reparse, path-escape, duplicate-path, argv, native, order, missing-command, outer-native, outer-call, helper-source, head, tree, unselected, missing-unselected, base-inventory, cold, document, outside-scope. Semantic cases call actual checker stages with one corrupted input, so the immutable package hash does not conceal later guards. The public verify entry always performs the complete sequence; there is no bypass flag.

Use a new independent private Git repository containing the exact original base and head for behavioral fixtures. Existing Git objects may be read/copied, never shared as writable alternates. Tests do not regenerate archives. Record each native command, stdout/stderr and UTC externally to the tested evidence; compare complete fixture inventory after each case. This is fresh verification of preserved evidence, not recovered original endpoint snapshots.

Also require a second complete positive using a synthetic B1 containing this entire three-file amendment. Apply the original preserved eight-path patch to B1 in a separate private repository, producing synthetic H1 while retaining all new metadata; set only that repository's origin/master to B1. Invoke the public verifier without bypasses and require native0/ROUND4-EVIDENCE-OK with original d4aa/70ba/4898 and distinct current B1/H1/tree identities. Do not execute the old generator or label these commits as published. This proves the post-amendment current-candidate branch, which an old-head positive alone cannot establish.

First assemble this whole card, the complete appendix including tests, and the complete Board change. Measure the real unfiltered Git diff and numstat as review.ps1 does; first <=750 changed lines /45000 UTF-16 units, ceil(1.25x) <=1000/60000. Also report full injected card/prompt and raw-data volume. Oversize means stop, not truncate or hide new source. The original closeout's 22/14842 budget does not apply to this amendment.

## Exact Board addition

Insert this single row immediately before the unique existing T0-ROUND45-DELIVERY-CLOSEOUT-REGISTER W0 row; all other Board bytes stay unchanged.

<!-- amendment-board-row -->
```text
| W0 | T0-ROUND4-EVIDENCE-CARRIER | 第4轮原始生成证据入口与独立校验 | T0-ROUND45-DELIVERY-CLOSEOUT-REGISTER | S | GPT-6 Astra · high | GPT-5.6 Terra · high | **todo**：仅补证据契约；不改变产品与原评审历史 |
```

## Registration integrity DoD

This read-only check validates metadata integrity, not the closeout implementation. Its separate private evidence tests must also be inspected before adopting this candidate.

<!-- evidence-amendment-dod -->
```powershell
$ErrorActionPreference = 'Stop'
$ordinal = [StringComparison]::Ordinal
$utf8 = [Text.UTF8Encoding]::new($false, $true)
function Git-Raw([string[]]$Arguments) {
    $info = [Diagnostics.ProcessStartInfo]::new('git')
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Environment['GIT_OPTIONAL_LOCKS'] = '0'
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::Start($info)
    $memory = [IO.MemoryStream]::new()
    $errors = $process.StandardError.ReadToEndAsync()
    $process.StandardOutput.BaseStream.CopyTo($memory)
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "[AMEND-GIT] $($errors.Result)" }
    return $utf8.GetString($memory.ToArray())
}
$base = (Git-Raw @('merge-base', 'HEAD', 'origin/master')).Trim()
if (-not [string]::Equals($base, 'd4aa3418176922fc890f3763946c6b4a8c1facf1', $ordinal)) { throw '[AMEND-BASE]' }
if (-not [string]::Equals((Git-Raw @('rev-parse', 'origin/master')).Trim(), $base, $ordinal)) { throw '[AMEND-BASE]' }
$future = 'specs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md'
$raw = [IO.File]::ReadAllBytes($future)
if ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($raw)) -cne '0B03472A054528AEB413E43F9A213AD245963F707EBE3D6AE1C8D14279DC2670') { throw '[AMEND-CARD]' }
$original = Git-Raw @('show', "${base}:$future")
if (-not $utf8.GetString($raw).StartsWith($original, $ordinal)) { throw '[AMEND-PREFIX]' }
$self = [IO.File]::ReadAllText('specs/tasks/T0-ROUND4-EVIDENCE-CARRIER.md', $utf8)
$matches = [regex]::Matches($self, '(?s)<!-- amendment-board-row -->\n```text\n(.*?)\n```')
if ($matches.Count -ne 1) { throw '[AMEND-ROW]' }
$row = $matches[0].Groups[1].Value + "`n"
$board = Git-Raw @('show', "${base}:docs/TASK-BOARD.md")
$anchor = '(?m)^\| W0 \| T0-ROUND45-DELIVERY-CLOSEOUT-REGISTER \|'
$matches = [regex]::Matches($board, $anchor)
if ($matches.Count -ne 1 -or $board.Contains($row)) { throw '[AMEND-ROW]' }
$expected = $board.Insert($matches[0].Index, $row)
if (-not [string]::Equals([IO.File]::ReadAllText('docs/TASK-BOARD.md', $utf8), $expected, $ordinal)) { throw '[AMEND-BOARD]' }
$changes = (Git-Raw @('diff', '--no-renames', '--name-status', $base, '--')).Trim().Split("`n")
$want = @("M`tdocs/TASK-BOARD.md", "A`tspecs/tasks/T0-ROUND4-EVIDENCE-CARRIER.md", "M`tspecs/tasks/T0-ROUND45-DELIVERY-CLOSEOUT.md")
[Array]::Sort($changes, [StringComparer]::Ordinal)
[Array]::Sort($want, [StringComparer]::Ordinal)
if (-not [string]::Equals(($changes -join "`n"), ($want -join "`n"), $ordinal)) { throw '[AMEND-SCOPE]' }
if ((Git-Raw @('ls-files', '--others', '--exclude-standard')).Length) { throw '[AMEND-UNTRACKED]' }
foreach ($id in @('T0-ROUND4-EVIDENCE-CARRIER', 'T0-ROUND45-DELIVERY-CLOSEOUT')) {
    pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId $id
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Write-Output '[AMENDMENT-OK]'
exit 0
```

## Actual delivery remains separate

Private future publication values are null. Before any actual bootstrap or normal lifecycle, re-query refs and source seals, require explicit root adoption authorization and measure that actual entire candidate. This new task uses its own ordinary review history. Do not reset PR440 or count this amendment as another product.

After amendment R3/CI/merge/R5/cleanup, later authorized closeout must absorb the actual new base without rewriting history. Preserve original d4aa/70ba/tree4898 evidence and independently validate the new complete candidate from its own base plus the same frozen operations. If sources, unique blocks or scope drift, stop; do not relabel old runtime as a run on the new head.
