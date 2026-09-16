---
id: T0-REMOTE-PREREVIEW-CARDS
title: Register RECORDS and STATE publication contracts and record the schema review correction
status: merged
branch: T0-REMOTE-PREREVIEW-CARDS
worktree: C:\wt\T0-REMOTE-PREREVIEW-CARDS
depends_on: []
allow_paths:
  - specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md
  - specs/tasks/T0-PREREVIEW-RECORDS.md
  - specs/tasks/T0-PREREVIEW-STATE-1A.md
  - specs/tasks/T0-PREREVIEW-REMOTE-SCHEMA.md
  - docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 Publish this registration and the two listed new task cards; update the existing REMOTE-SCHEMA evidence/hygiene notes and adoption plan only for its explicit PR 302 review repairs. Card identities pass check-cards. The complete scope is these five metadata files; no archive changes."
  - "A2 Both publication cards remain todo and carry executable future DoD commands, bounded allow_paths, complete original acceptance, exclusions, hygiene and R5 duties. RECORDS depends on T0-PREREVIEW-REMOTE-SCHEMA; STATE-1A depends on that schema, RECORDS and T0-PREREVIEW-REMOTE-FACTS. Registration does not claim implementation or acceptance is complete. Preserve the existing REMOTE-SCHEMA acceptance and executable DoD; allow only the two documented review repairs to source-byte identity, retain prior mutation evidence and rerun the complete behavioral suite; label its initial proof historical and require new full selftest, formal R3 and CI for the repaired candidate."
  - "A3 Reuse the separately owned T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-REMOTE-POLICY and T0-PREREVIEW-REMOTE-FACTS adoption chain. Do not register duplicate schema/facts cards, weaken their contracts or start a dependent implementation before its remote prerequisites have actually merged."
  - "A4 The candidate passes check-cards and archive.ps1 -CheckCardsIndex; none of these five files contains trailing horizontal whitespace. The complete five-file diff stays below 500 changed lines and 40000 diff characters, including context and Git diff headers."
  - "A5 The registration itself is reviewed and merged through a remote PR using the original main checkout task-loop controller. Its documentation-only SkipRed does not waive any product card's testing or merge gates. No direct push to master, history rewriting, receipt reuse or product implementation is part of this card."
dod_command: $raw = Get-Content specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md -Raw; $blocks = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($blocks.Count -ne 1) { throw 'expected one registration assertion block' }; & ([scriptblock]::Create($blocks[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; foreach ($p in @('specs/tasks/T0-REMOTE-PREREVIEW-CARDS.md','specs/tasks/T0-PREREVIEW-RECORDS.md','specs/tasks/T0-PREREVIEW-STATE-1A.md','specs/tasks/T0-PREREVIEW-REMOTE-SCHEMA.md','docs/plans/PREREVIEW-REMOTE-ADOPTION.md')) { if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { exit 1 }; if (@(Select-String -LiteralPath $p -Pattern '[ \t]+$').Count -gt 0) { exit 1 } }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --cached --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: Fixed owner-approved RECORDS and STATE-1A contracts plus the reviewed schema evidence/plan corrections, exact selected feature identities and todo status pass; task-card validation and the unchanged archive-index projection pass; the complete candidate diff against pinned base stays below 500 changed lines and 40000 characters; all five declared metadata files exist and are free of trailing horizontal whitespace; working and staged diffs pass whitespace checks.
review_gate: codex {verdict:pass}
forbid:
  - Product code, schema artifacts, scripts, configuration, CI, hook, skill or archive changes
  - Changing the original local feature worktrees, RED proofs, watershed receipts or shared history
  - Claiming registration is implementation delivery, or transferring historical acceptance to an untested remote candidate
  - Direct push to master, bypassing R3 or CI, or weakening the remote harness
non_goals:
  - Implementing or testing the two registered features during this documentation card
  - Publishing runtime checklists, workers, a prereview command, or Phase-1b integration
  - Reconciling unrelated local history, task boards, lessons or debt registries
doc_sync: After the remote PR is actually merged, record its PR number, reviewed head, CI evidence and merge OID in the controller delivery ledger; mark this registration card merged through normal reviewed R5 metadata work. Keep both feature cards todo until their own deliveries close. Any archive move is separate R5 work outside this five-file metadata diff.
hygiene: This is a non-TDD metadata registration card; use the original task-loop ship with explicit SkipRed. Do not add tests that merely repeat these text edits. Preserve the owner-approved payload and its final hashes in the ignored delivery ledger before publication.
---

# T0-REMOTE-PREREVIEW-CARDS

Register the remaining RECORDS and STATE-1A deliveries, and correct the existing schema adoption evidence notes before its next review. Another active task owns the schema, policy and facts adoption chain already registered on remote master at 5d2a5dfcc1ed192a7f7cb8b69c63fecd1aac1fdb. The prior four-card draft was preserved and reduced before its first ship to avoid duplicate implementations. After PR 302 blocked, this registration also took ownership of its two metadata corrections; the schema implementation remains with its existing owner. No existing branch or review history was rewritten.

The shared schema publishes revision 1 and the STATE-1A scope excludes review_status. Each implementation remains responsible for actual candidate validation, formal R3, CI and remote PR merge. Historical local evidence is provenance, not remote acceptance. The feature owner supplies the two complete card payloads; the controller records their hashes before publication.

Run start and ship with D:/Projects/MyInspection/scripts/task.ps1 from the original main checkout. Start this isolated worktree from origin/master and ship against master with explicit SkipRed for this documentation-only card. The original controller reads its registered main-checkout scope; its formal reviewer supports a worktree card when the pinned remote baseline does not yet contain it. Include this registration card in the reviewed candidate. No alternate or weaker controller is introduced.

The DoD measures the complete tracked working-tree diff against pinned base 1ce3f5aef130ddd3fac19632a46e04c6671f91a6, including committed, staged and unstaged changes, context and headers. Nonignored untracked files must be staged before this check so none are silently omitted. The registration ceiling is 499 changed lines and 39999 diff characters; if the final owner-approved payload exceeds either value, stop and revise the scope before publication. The normal ship still runs its mandatory deterministic gates, formal review and exact-head remote CI checks. Do not run either feature card's future implementation DoD as this registration card's acceptance.

## Registration assertions

The fixed hashes below pin the two complete owner-approved publication payloads and the two reviewed schema metadata files: acceptance, executable DoD, dependencies, provenance, and every other LF-normalized byte. Decode raw bytes as strict UTF-8 and reject a BOM before normalizing only CRLF to LF; expected values are constants and are never regenerated during DoD. The selected identity and status checks apply only to the two new todo publications. The two existing metadata files preserve the initial 13f2cbfb proof as history and require new proof after the scoped PR 302 repairs; their notes do not mark the repaired schema complete. Original-main's mandatory scope gate remains responsible for the complete changed-path set.

These pins detect contract corruption before publication; they do not prove either feature implementation, rerun its historical tests, or transfer historical evidence to this candidate.

```powershell
$ErrorActionPreference = 'Stop'
$expected = @{
    'specs/tasks/T0-PREREVIEW-RECORDS.md' = @{ hash = '6B0EFAA27C66F798C3824E94B9785535AB3DAC20D6C5527D23A7CDA91D0367DB'; id = 'T0-PREREVIEW-RECORDS'; depends = '[T0-PREREVIEW-REMOTE-SCHEMA]' }
    'specs/tasks/T0-PREREVIEW-STATE-1A.md' = @{ hash = 'B6511CC70CAAE58C062AFD25335D0FC9BBCAA3CB28033EB0FD8CBAFF9CA608FD'; id = 'T0-PREREVIEW-STATE-1A'; depends = '[T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-RECORDS, T0-PREREVIEW-REMOTE-FACTS]' }
    'specs/tasks/T0-PREREVIEW-REMOTE-SCHEMA.md' = @{ hash = '47969F62F5F9AE14810D2DD853C541FE58675D987C9363B514F72A4B4E222364' }
    'docs/plans/PREREVIEW-REMOTE-ADOPTION.md' = @{ hash = 'A71D97DAB9D95CEA9933BCEFCC211AFA3066FCD557248EFE07DB9D6FFEE39079' }
}
foreach ($path in $expected.Keys) {
    $bytes = [IO.File]::ReadAllBytes((Join-Path $PWD $path))
    $text = [Text.UTF8Encoding]::new($false, $true).GetString($bytes)
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xFEFF) { throw "registration payload has UTF-8 BOM: $path" }
    $normalized = $text.Replace("`r`n", "`n")
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes($normalized)))
    if ($hash -cne $expected[$path].hash) { throw "approved registration contract changed: $path" }
    if ($expected[$path].id) {
        $idMatches = $normalized -match ('(?m)^id: ' + [regex]::Escape($expected[$path].id) + '$')
        $dependencyMatches = $normalized -match ('(?m)^depends_on: ' + [regex]::Escape($expected[$path].depends) + '$')
        if (-not $idMatches -or -not $dependencyMatches -or $normalized -notmatch '(?m)^status: todo$') {
            throw "selected identity, todo status, or dependency changed: $path"
        }
    }
}
Write-Host '[REMOTE-PREREVIEW-REGISTRATION-OK] A1-A3 exact approved payloads and selected todo identities'
$base = '1ce3f5aef130ddd3fac19632a46e04c6671f91a6'
& git merge-base --is-ancestor $base HEAD
if ($LASTEXITCODE -ne 0) { throw 'registration budget base is missing or not an ancestor' }
$untracked = @(& git ls-files --others --exclude-standard)
if ($LASTEXITCODE -ne 0 -or $untracked.Count -ne 0) { throw 'registration budget requires all candidate files tracked' }
$numstat = @(& git -c core.quotepath=false diff --no-ext-diff --no-textconv --numstat $base)
if ($LASTEXITCODE -ne 0) { throw 'registration budget numstat failed' }
$diff = (& git -c core.quotepath=false diff --no-ext-diff --no-textconv --no-color --unified=3 $base | Out-String).Replace("`r`n", "`n")
if ($LASTEXITCODE -ne 0) { throw 'registration budget full diff failed' }
[decimal]$changedLines = 0
foreach ($row in $numstat) {
    if ($row -notmatch '^(\d+)\t(\d+)\t[^\t\r\n]+$') { throw 'registration budget requires valid text numstat rows' }
    $changedLines += [decimal]$Matches[1] + [decimal]$Matches[2]
}
if ($changedLines -ge 500) { throw "registration changed-line budget exceeded: $changedLines >= 500" }
if ($diff.Length -ge 40000) { throw "registration diff-character budget exceeded: $($diff.Length) >= 40000" }
Write-Host "[REMOTE-PREREVIEW-BUDGET-OK] A4 base=$base changedLines=$changedLines diffChars=$($diff.Length)"
```

## Remote delivery receipt (2026-09-17 NZ)

PR [#303](https://github.com/Asun28/MyInspection/pull/303); reviewed head 0b474848973b348a6877da54d1ce2077a444e55e; squash merge 5d7fdc910561be463824690b4d4689335bbb0bd8. Formal Sol/high attempt 2 PASS, empty reasons; exact-candidate CI [35157398386](https://github.com/Asun28/MyInspection/actions/runs/35157398386) passed verify and required.

The second formal Sol/high review passed after A4 gained executable full-diff budget enforcement. Eight actual current/boundary/staged/untracked/restoration cases and five payload corruption cases passed. The first BLOCK is preserved. RECORDS and STATE remain pending features; this registration is not a product delivery.

Original-main task-loop cleanup exited 0 at 2026-09-16T22:27:37.5321703+00:00; canonical worktree and local branch are absent. The controller independently checked the copied evidence before cleanup. Evidence below is retained in the ignored local delivery ledger; these hashes identify the checked receipts and are not a claim that the files are published by this metadata PR.

- `_local/rotating-card-orchestrator/remote-delivery/T0-REMOTE-PREREVIEW-CARDS/pre-cleanup-evidence-manifest.json` — SHA-256 `D10CFE5796724107CFA0328A7795D4F9BEE692EF0B98CAEA566474BE76620C93`.
- `_local/rotating-card-orchestrator/remote-delivery/T0-REMOTE-PREREVIEW-CARDS/cleanup-result.json` — SHA-256 `E99B7E98091786362BFE53392D122027D33F5544C59A89559D6EFE32413361F3`.

### Portable lifecycle evidence

The JSON below contains selected observed PR/CI fields, the actual formal verdict and cleanup receipt, and the recorded evidence audit. Registration manifest counts were independently rechecked against the copied files; product audits retain their source and test context. It is a historical snapshot, not a live service assertion. Full original logs, XML and manifests remain at the referenced ignored paths and are available in the review worktree; this PR publishes the portable snapshot.

<!-- remote-lifecycle-receipt -->
```json
{
  "id": "T0-REMOTE-PREREVIEW-CARDS",
  "pr": {
    "number": 303,
    "url": "https://github.com/Asun28/MyInspection/pull/303",
    "state": "MERGED",
    "headRefOid": "0b474848973b348a6877da54d1ce2077a444e55e",
    "mergeCommit": {
      "oid": "5d7fdc910561be463824690b4d4689335bbb0bd8"
    },
    "mergedAt": "2026-09-16T22:24:27Z"
  },
  "formalR3": {
    "verdict": "pass",
    "sha": "0b474848973b348a6877da54d1ce2077a444e55e",
    "branch": "T0-REMOTE-PREREVIEW-CARDS",
    "reasons": []
  },
  "candidateCI": {
    "databaseId": 35157398386,
    "headSha": "0b474848973b348a6877da54d1ce2077a444e55e",
    "conclusion": "success",
    "status": "completed",
    "event": "pull_request",
    "jobs": [
      {
        "name": "verify",
        "status": "completed",
        "conclusion": "success"
      },
      {
        "name": "required",
        "status": "completed",
        "conclusion": "success"
      }
    ]
  },
  "cleanup": {
    "exit": 0,
    "worktreeAbsent": true,
    "branchAbsent": true,
    "head": "0b474848973b348a6877da54d1ce2077a444e55e",
    "merge": "5d7fdc910561be463824690b4d4689335bbb0bd8",
    "copiedEvidenceFiles": 21,
    "endedUtc": "2026-09-16T22:27:37.5321703Z"
  },
  "evidenceAudit": {
    "kind": "verifiedManifestProjection",
    "manifestSha256": "D10CFE5796724107CFA0328A7795D4F9BEE692EF0B98CAEA566474BE76620C93",
    "manifestEntries": 21,
    "verifiedEntries": 21,
    "copiedEvidenceFiles": 21
  }
}
```
