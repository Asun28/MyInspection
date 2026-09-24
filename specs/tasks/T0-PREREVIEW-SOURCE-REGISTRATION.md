---
id: T0-PREREVIEW-SOURCE-REGISTRATION
title: Register durable policy source prerequisites and strengthen POLICY acceptance
status: todo
branch: T0-PREREVIEW-SOURCE-REGISTRATION
worktree: C:\wt\T0-PREREVIEW-SOURCE-REGISTRATION
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA]
allow_paths:
  - specs/tasks/T0-PREREVIEW-SOURCE-REGISTRATION.md
  - specs/tasks/T0-PREREVIEW-POLICY-SOURCE.md
  - specs/tasks/T0-PREREVIEW-POLICY-SOURCE-CHECK.md
  - specs/tasks/T0-PREREVIEW-REMOTE-POLICY.md
  - docs/plans/PREREVIEW-REMOTE-ADOPTION.md
dod_command: $ErrorActionPreference='Stop'; $raw=Get-Content specs/tasks/T0-PREREVIEW-SOURCE-REGISTRATION.md -Raw; $b=[regex]::Matches($raw,'(?s)```powershell\r?\n(.*?)\r?\n```'); if($b.Count -ne 1) { throw 'one registration assertion block required' }; & ([scriptblock]::Create($b[0].Groups[1].Value)); & pwsh -NoProfile -File scripts/check-cards.ps1; if($LASTEXITCODE -ne 0) { exit 1 }; & pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 Publish this registration card and the complete independently pinned SOURCE and SOURCE-CHECK todo contracts in the same remote PR. SOURCE owns only the two raw files; CHECK owns the complete recipe, checker and four negative probes. Registration does not execute their implementation DoDs. [fixed payload SHA-256 and card checks]"
  - "A2 Preserve the complete old POLICY contract except the approved SOURCE-CHECK dependency, stronger A2, prepended source replay and exact note retaining both real blocks. Reversing precisely these amendments reproduces contractBase byte-for-byte after LF normalization, including the original DoD, acceptance, two allowed paths and budget 460. [fixed payload and reversible whole-contract comparison]"
  - "A3 Preserve the execution-base plan except the fixed prerequisite sequence and historical-source note. Both fixed whole-plan SHA-256 and exact reverse comparison pass. No source or POLICY merge is claimed complete; round reset requires separate explicit authorization. [fixed plan payload and reverse comparison]"
  - "A4 The complete committed, staged and unstaged candidate diff against executionBase contains exactly these five paths, including this card; all files are tracked before DoD. Whole-file, complete-diff and staged whitespace checks, check-cards and archive-index projection pass. [exact path set and deterministic metadata checks]"
  - "A5 This registration is pushed, formally reviewed and merged through its own remote PR using the authorized controller. Local bootstrap is only for R1; the identical complete registration card enters the candidate. No direct master push, R3 bypass or unregistered control card is allowed. [protected delivery and actual PR receipt]"
budget: 300
non_goals: [Source data publication, comparison implementation, R3 reset, POLICY shipping]
hygiene: Metadata only. Preserve fixed payloads and verify the complete contract rather than searching its own declarations. No feature acceptance is claimed by this registration.
doc_sync: Record this registration's actual PR, reviewed head, CI and merge OID; close it through reviewed R5 metadata. SOURCE, SOURCE-CHECK and POLICY retain their own later delivery duties.
---

# T0-PREREVIEW-SOURCE-REGISTRATION

The authorized controller bootstraps R1 with the externally approved canonical copy. Root freezes its complete SHA-256 outside this card and compares both the R1 canonical copy and the reviewed PR head byte-for-byte against that approval. The entire identical card is the fifth payload path in the same registration PR. Bootstrap never permits a direct master push or omission of this card from remote history.

contractBase permanently identifies the old POLICY contract. executionBase pins remote master at 74aa9cb7ac6e70bbae30cb5d3a2024d8c95d6a0c for this registration. If that baseline changes before execution, reconcile upstream changes and obtain approval for every changed payload and external own-card pin before acceptance. Never derive expected hashes from the candidate during DoD. Stage all new candidate paths before DoD; the full working-tree comparison then includes committed, staged and unstaged content.

The two fixed source-card hashes and the fixed POLICY/plan hashes cover every LF-normalized byte. Decode strict UTF-8, reject BOM, and normalize CRLF only. Reverse comparisons additionally prove that only the exact approved POLICY and plan amendments were made. The old POLICY block note is an explicit approved append, not permission to edit other history. The assertion block reads committed baseline content; it does not execute either future source checker or modify any file.

```powershell
$ErrorActionPreference = 'Stop'
$contractBase = '4e89f3c1571758093304d5a7c3bb94e834bc3cf4'
$executionBase = '74aa9cb7ac6e70bbae30cb5d3a2024d8c95d6a0c'
$own = 'specs/tasks/T0-PREREVIEW-SOURCE-REGISTRATION.md'
$policyPath = 'specs/tasks/T0-PREREVIEW-REMOTE-POLICY.md'
$planPath = 'docs/plans/PREREVIEW-REMOTE-ADOPTION.md'
$expected = @{
    'specs/tasks/T0-PREREVIEW-POLICY-SOURCE.md' = '999979F38EB8877C5193E69D9608EC9D66B837E481740D15EBBA006F3F7F1CA1'
    'specs/tasks/T0-PREREVIEW-POLICY-SOURCE-CHECK.md' = 'D60EB497724ED440C6463D2ABBD951CAE1590ADDE97657A237EAACC56EDDFEA3'
    'specs/tasks/T0-PREREVIEW-REMOTE-POLICY.md' = '17E91287AB1C1A951A565D9844252F3C17552BD70FCD0DC4ED462CC8E38570D0'
    'docs/plans/PREREVIEW-REMOTE-ADOPTION.md' = '0E125840BF7C1604FDBDCFF0D5482291C5B3BDDC18144594218EF3A6BEDDB592'
}
function Read-Lf([string]$path) {
    $text = [Text.UTF8Encoding]::new($false,$true).GetString([IO.File]::ReadAllBytes((Join-Path $PWD $path)))
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { throw "UTF-8 BOM: $path" }
    return $text.Replace("`r`n","`n")
}
function Hash-Text([string]$text) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))) }
function Read-Git([string]$oid,[string]$path) {
    $text = (& git -c core.quotepath=false show "${oid}:$path" | Out-String).Replace("`r`n","`n")
    if ($LASTEXITCODE -ne 0) { throw "missing baseline payload: ${oid}:$path" }; return $text
}
function Replace-Once([string]$text,[string]$old,[string]$new) {
    if ([regex]::Matches($text,[regex]::Escape($old)).Count -ne 1) { throw 'approved amendment must occur exactly once' }
    return $text.Replace($old,$new)
}
function Remove-Suffix([string]$text,[string]$suffix) {
    if (-not $text.EndsWith($suffix,[StringComparison]::Ordinal)) { throw 'approved append changed or missing' }
    return $text.Substring(0,$text.Length-$suffix.Length)
}
foreach ($path in $expected.Keys) {
    if ((Hash-Text (Read-Lf $path)) -cne $expected[$path]) { throw "fixed approved payload changed: $path" }
}
$oldPolicy = Read-Git $contractBase $policyPath
if ((Hash-Text $oldPolicy) -cne '32D82470783A5D48BF873BD2B4CA8511C03935161D1B5E727463982377E4069E') { throw 'old POLICY contract identity changed' }
if (-not [string]::Equals((Read-Git $executionBase $policyPath),$oldPolicy,[StringComparison]::Ordinal)) { throw 'execution-base POLICY differs; reconcile before R1' }
$policy = Read-Lf $policyPath
$policy = Remove-Suffix $policy ("`n" + 'PR #308 retained two real source-evidence blocks: first head fd682ef6a57e648283ed4680dbd56712effada10 and second head efcc79084b0a23fd5eda587e3334750c4b1d8a23. Both formal verdicts and the round counter remain preserved. Source publication precedes the next repair; any subsequent round handling requires the separate explicit authorization recorded by the coordinator. This amendment strengthens A2 and the DoD, and grants no bypass.' + "`n")
$policy = Replace-Once $policy 'depends_on: [T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-POLICY-SOURCE-CHECK]' 'depends_on: [T0-PREREVIEW-REMOTE-SCHEMA]'
$policy = Replace-Once $policy '  - "A2 Checklists retain the approved four lenses and candidate/coverage policy, with policy bytes available at the next merge-base. Approved source bytes, the exact replacement recipe and the reproducible comparison are committed artifacts; the verifier compares both complete candidate bodies without unavailable local commits or ignored evidence. [lens check and committed source replay]"' '  - "A2 Checklists retain the approved four lenses and candidate/coverage policy, with policy bytes available at the next merge-base. [lens check and source comparison]"'
$policy = Replace-Once $policy 'dod_command: $s=(& pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/verify.ps1 -CandidateRoot . *>&1 | Out-String); if($LASTEXITCODE -ne 0 -or -not $s.Contains(''[POLICY-SOURCE-EVIDENCE-PASS]'')) { exit 1 }; ' 'dod_command: '
if (-not [string]::Equals($policy,$oldPolicy,[StringComparison]::Ordinal)) { throw 'POLICY has changes outside approved amendments' }
$plan = Read-Lf $planPath
$plan = Remove-Suffix $plan ("`n" + 'POLICY source comparison must be reproducible from committed files. Ignored local source copies establish provenance during preparation but do not satisfy this delivery requirement. Register and merge the SOURCE and SOURCE-CHECK prerequisites before resuming POLICY; preserve both earlier real R3 verdicts and obtain separate authorization for further formal review. The source text is historical data under the existing prereview fixture tree, not a new active policy.' + "`n")
$plan = Replace-Once $plan ('2. T0-PREREVIEW-POLICY-SOURCE: publish only the two exact historical source files in a dedicated raw fixture directory, independently pinned by inventory, length, Git blob and SHA-256. Original wording and references remain unchanged.' + "`n" + '3. T0-PREREVIEW-POLICY-SOURCE-CHECK: publish the complete audited replacement recipe, offline source-identity/replay verifier and four negative probes. These two pending prerequisites address POLICY PR #308''s durable-source evidence gap.' + "`n") ''
$plan = Replace-Once $plan '4. T0-PREREVIEW-REMOTE-POLICY:' '2. T0-PREREVIEW-REMOTE-POLICY:'
$plan = Replace-Once $plan '5. T0-PREREVIEW-REMOTE-FACTS:' '3. T0-PREREVIEW-REMOTE-FACTS:'
if (-not [string]::Equals($plan,(Read-Git $executionBase $planPath),[StringComparison]::Ordinal)) { throw 'plan has changes outside approved amendments' }
& git merge-base --is-ancestor $executionBase HEAD
if ($LASTEXITCODE -ne 0) { throw 'executionBase must be an ancestor' }
$untracked = @(& git ls-files --others --exclude-standard)
if ($LASTEXITCODE -ne 0 -or $untracked.Count -ne 0) { throw 'stage all new candidate files before full-diff acceptance' }
$paths = @($own) + @($expected.Keys)
$actual = @(& git -c core.quotepath=false diff --no-ext-diff --no-textconv --no-renames --name-only $executionBase --)
if ($LASTEXITCODE -ne 0 -or $actual.Count -ne 5) { throw 'exactly five changed paths required' }
if (-not [string]::Equals((($actual | Sort-Object -CaseSensitive) -join "`n"),(($paths | Sort-Object -CaseSensitive) -join "`n"),[StringComparison]::Ordinal)) { throw 'registration five-path scope mismatch' }
foreach ($path in $paths) { if ((Read-Lf $path) -match '(?m)[ \t]+$') { throw "trailing horizontal whitespace: $path" } }
& git -c core.quotepath=false diff --no-ext-diff --no-textconv --check $executionBase --
if ($LASTEXITCODE -ne 0) { throw 'complete candidate whitespace failed' }
& git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'staged whitespace failed' }
$numstat = @(& git -c core.quotepath=false diff --no-ext-diff --no-textconv --no-renames --numstat $executionBase --)
if ($LASTEXITCODE -ne 0) { throw 'complete numstat failed' }
[long]$lines = 0
foreach ($row in $numstat) {
    if ($row -notmatch '^(\d+)\t(\d+)\t[^\t\r\n]+$') { throw 'text numstat required' }
    $lines += [long]$Matches[1] + [long]$Matches[2]
}
$diff = (& git -c core.quotepath=false diff --no-ext-diff --no-textconv --no-color --unified=3 $executionBase -- | Out-String).Replace("`r`n","`n")
if ($LASTEXITCODE -ne 0 -or $lines -gt 300 -or $diff.Length -gt 60000) { throw 'complete registration budget exceeded' }
Write-Host "[POLICY-SOURCE-REGISTRATION-PASS] five paths; lines=$lines chars=$($diff.Length)"
```

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. The source documents that `scripts/fixtures/prereview/policy-source/raw/` copies are now on master, and each raw copy is byte-identical to its document (`git hash-object` equal on 2026-09-24). Whether this card is closed, narrowed or kept is a user decision.
