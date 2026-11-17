---
id: T0-REMOTE-PRODUCT-CARDS
title: Register four isolated remote delivery aliases for locally verified product cards
status: merged
depends_on: []
allow_paths:
  - specs/tasks/T0-REMOTE-PRODUCT-CARDS.md
  - specs/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md
  - specs/tasks/T1-STORAGE-PATH-BOUNDARY-REMOTE.md
  - specs/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md
  - specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md
  - specs/tasks/T1-LOCAL-DATA-SECURITY.md
  - docs/TASK-BOARD.md
  - docs/adr/0006-offline-security-backup-hardening.md
  - docs/adr/0007-report-interchange.md
sweep: On the candidate, rg -n 'T1-(LOCAL-DATA-SECURITY|SAFE-MEDIA-LOGGING|STORAGE-PATH-BOUNDARY)|T3-PDF-(TYPOGRAPHY-CONTRACT|PAGINATION-FIXTURES)' over TASK-BOARD, ADR-0006, ADR-0007 and the security parent found the four registry entries, both ADR notes and parent prerequisites; the remaining five paths are this card and its four complete alias contracts.
forbid:
  - product code, scripts, configuration, frozen contracts, dependencies or unrelated local history
  - weakening the original product acceptance, marking pending remote deliveries complete, or rewriting existing receipts
non_goals:
  - implementing or remotely delivering any of the four product changes
  - reconciling the original dirty master or registering the entire later PDF and security backlog
acceptance:
  - "A1 Register exactly four todo aliases for SafeMediaLogging, StoragePathBoundary, TypographyContract and PaginationFixtures; preserve each original allow_paths, executable DoD and complete behavioral acceptance."
  - "A2 Alias provenance distinguishes local-only feature/merge evidence from pending remote RED, DoD, mutation, R3 and candidate CI evidence; the pagination test refactor retains its approved non-TDD exception."
  - "A3 Typography keeps the existing renderer prerequisite; pagination depends on the remote Typography alias; the two platform aliases keep the existing platform prerequisite. Aliases do not count as additional product cards."
  - "A4 Only append the aliases and their scoped design notes to the remote registry; retain the security parent's existing acceptance and DoD when identifying the extracted logging prerequisite."
  - "A5 This registration itself passes original-main task-loop scope, full diff budget, independent R3 and exact-candidate CI and is remotely merged through a PR."
dod_command: $raw = Get-Content specs/tasks/T0-REMOTE-PRODUCT-CARDS.md -Raw; $blocks = [regex]::Matches($raw, '(?s)```powershell\r?\n(.*?)\r?\n```'); if ($blocks.Count -ne 1) { throw 'expected one registration assertion block' }; & ([scriptblock]::Create($blocks[0].Groups[1].Value)); pwsh -NoProfile -File scripts/check-cards.ps1; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex -Quiet; if ($LASTEXITCODE -ne 0) { exit 1 }; git diff --check; if ($LASTEXITCODE -ne 0) { exit 1 }
dod_exit: 0
dod_assert: A1-A4 exact approved payload hashes and exactly four todo aliases pass; card syntax, unchanged archive index, whitespace and original-main scope/budget gates pass for the nine approved metadata paths
review_gate: codex {verdict:pass}
hygiene: genuine metadata registration with SkipRed recorded; no fabricated product RED or mutation claims
doc_sync: the four Task Board entries and ADR notes are included; record this registration PR and merge at R5
---

# T0-REMOTE-PRODUCT-CARDS

The user requested logged PRs followed by passing checks and remote merges for all cards on 2026-09-17. The original checkout and remote master have unrelated divergent history. This registration creates separate remote-delivery identities, retaining all original local commits and evidence, and does not publish that history or claim any new product behavior.

Execute every phase through the existing main checkout at D:/Projects/MyInspection/scripts/task.ps1. Start this genuine documentation card with -Base origin/master; ship with -Base master -SkipRed, without -Local. The original controller reads this already approved main-checkout card for scope; its R3 reader explicitly supports worktree-fallback when the pinned remote baseline lacks the registration card. Include this exact card in the candidate. Do not switch controller versions, change guards or directly push master to bootstrap scope.

After this PR merges, each alias starts independently from the refreshed remote master and executes the registered product acceptance. Existing local evidence is preserved provenance; it cannot substitute for tests on the new candidate. Record remote PR, reviewed head, passing CI and merge before closing the alias. Keep the five-round product count unchanged.

Repair measurement: 338 changed lines and 46,874 diff characters including all metadata, executable assertions and context. Recompute before ship, stop for a split above 700 lines or 48,000 characters, and retain the 1,000-line / 60,000-character hard limit. Metadata references to later local cards do not claim those cards exist remotely or that their functionality is delivered.

## Registration assertions

The fixed hashes below pin the reviewed registration output, not future product test results. Normalize only CRLF to LF; retain all other bytes, including EOF. Do not regenerate expected values during DoD. A1 pins all original allow_paths, acceptance and executable DoD fields; A2 pins the complete local-only provenance, pending remote gates and pagination exception; A3 pins exact dependencies; A4 pins both ADRs, the board and the security parent. The independent pre-publication comparison used each original local card's five behavioral fields (Boundary A1-A7, with remote-only A8 added), plus the parent's unchanged acceptance and DoD. Historical feature and merge OIDs are recorded in each alias. This registration checks the approved static output; it does not rerun or attest historical product tests. A5 remains enforced by task-loop, formal R3 and candidate CI.

```powershell
$ErrorActionPreference = 'Stop'
$expected = @{
    'specs/tasks/T1-SAFE-MEDIA-LOGGING-REMOTE.md' = '3B0131D4B42388138D614FD655BF8F174C30CC5CD5AEEEC3357AB95907A9A733'
    'specs/tasks/T1-STORAGE-PATH-BOUNDARY-REMOTE.md' = '98E6B7229A770C5946A6E3AA4D0A6A6AD0EFBDB8067A59E813AE47E4969A8A28'
    'specs/tasks/T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE.md' = '0B7570C5A92285E331C45786EC13548D38FA70900CB96FAB64236C0EEB828CD5'
    'specs/tasks/T3-PDF-PAGINATION-FIXTURES-REMOTE.md' = '163FB1DBDEC4AC419A57B49833AFFCF7F65A7F80A09D11224C3FD835E1353A66'
    'specs/tasks/T1-LOCAL-DATA-SECURITY.md' = '38915AA51399503F1617E809E4695000A7B0235610F9931A5B95A6BD9E003805'
    'docs/TASK-BOARD.md' = '77CE074BDF7519442BD2BB41ACE1F30F4C4741764E7698B8C3FB203DF65D6267'
    'docs/adr/0006-offline-security-backup-hardening.md' = '8572D775CFA879CEA0F6A42E7966351BD49897E2E1738846AFCF501D58CA0C02'
    'docs/adr/0007-report-interchange.md' = 'BA55A6AC66BF67B860902A06C2734687958B6EA26501F2BCD34D118C65838A48'
}
$aliases = @($expected.Keys | Where-Object { $_ -match '/T[13]-.+-REMOTE\.md$' } | Sort-Object)
$actual = @(Get-ChildItem specs/tasks -File | Where-Object Name -match '^T[13]-.+-REMOTE\.md$' | ForEach-Object { 'specs/tasks/' + $_.Name } | Sort-Object)
if ($aliases.Count -ne 4 -or (Compare-Object $aliases $actual)) { throw 'A1: remote alias set differs' }
foreach ($path in $expected.Keys) {
    $rawBytes = [IO.File]::ReadAllBytes((Join-Path $PWD $path))
    $text = [Text.UTF8Encoding]::new($false, $true).GetString($rawBytes).Replace("`r`n", "`n")
    $bytes = [Text.Encoding]::UTF8.GetBytes($text)
    $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))
    if ($hash -cne $expected[$path]) { throw "A1-A4: approved contract changed: $path" }
}
foreach ($path in $aliases) {
    if ([IO.File]::ReadAllText((Join-Path $PWD $path)) -notmatch '(?m)^status: todo\r?$') {
        throw "A1: alias is not todo: $path"
    }
}
Write-Host '[REMOTE-REGISTRATION-OK] A1-A4 exact approved payload and four todo aliases'
```

## Remote delivery receipt (2026-09-17 NZ)

PR [#301](https://github.com/Asun28/MyInspection/pull/301); reviewed head 01454f18ad04a1e254f3df748b4082d11aca1447; squash merge 1ce3f5aef130ddd3fac19632a46e04c6671f91a6. Formal Sol/high attempt 2 PASS, empty reasons; exact-candidate CI [35151534312](https://github.com/Asun28/MyInspection/actions/runs/35151534312) passed verify and required.

The second formal Sol/high review passed after the self-verifying payload and byte-fidelity assertions were repaired. The first BLOCK and six real negative cases remain in the evidence ledger. The four product contracts retain their original acceptance; this registration is not a product delivery.

Original-main task-loop cleanup exited 0 at 2026-09-16T22:03:18.7002769+00:00; canonical worktree and local branch are absent. The controller independently checked the copied evidence before cleanup. Evidence below is retained in the ignored local delivery ledger; these hashes identify the checked receipts and are not a claim that the files are published by this metadata PR.

- `_local/rotating-card-orchestrator/remote-delivery/T0-REMOTE-PRODUCT-CARDS/pre-cleanup-evidence-manifest.json` — SHA-256 `5F2EE7554060D23F359F557F7DF29B088F51354986E833EBE91099066CCAF01B`.
- `_local/rotating-card-orchestrator/remote-delivery/T0-REMOTE-PRODUCT-CARDS/cleanup-result.json` — SHA-256 `38BC40ABB6034460FCDB08CBBB2C4BF1C8C9B5D5CFA48FEB6DA684BA50C34D52`.

### Portable lifecycle evidence

The JSON below contains selected observed PR/CI fields, the actual formal verdict and cleanup receipt, and the recorded evidence audit. Registration manifest counts were independently rechecked against the copied files; product audits retain their source and test context. It is a historical snapshot, not a live service assertion. Full original logs, XML and manifests remain at the referenced ignored paths and are available in the review worktree; this PR publishes the portable snapshot.

<!-- remote-lifecycle-receipt -->
```json
{
  "id": "T0-REMOTE-PRODUCT-CARDS",
  "pr": {
    "number": 301,
    "url": "https://github.com/Asun28/MyInspection/pull/301",
    "state": "MERGED",
    "headRefOid": "01454f18ad04a1e254f3df748b4082d11aca1447",
    "mergeCommit": {
      "oid": "1ce3f5aef130ddd3fac19632a46e04c6671f91a6"
    },
    "mergedAt": "2026-09-16T21:19:49Z"
  },
  "formalR3": {
    "reasons": [],
    "verdict": "pass",
    "sha": "01454f18ad04a1e254f3df748b4082d11aca1447",
    "branch": "T0-REMOTE-PRODUCT-CARDS"
  },
  "candidateCI": {
    "databaseId": 35151534312,
    "headSha": "01454f18ad04a1e254f3df748b4082d11aca1447",
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
    "head": "01454f18ad04a1e254f3df748b4082d11aca1447",
    "merge": "1ce3f5aef130ddd3fac19632a46e04c6671f91a6",
    "copiedEvidenceFiles": 18,
    "endedUtc": "2026-09-16T22:03:18.7002769Z"
  },
  "evidenceAudit": {
    "kind": "verifiedManifestProjection",
    "manifestSha256": "5F2EE7554060D23F359F557F7DF29B088F51354986E833EBE91099066CCAF01B",
    "manifestEntries": 18,
    "verifiedEntries": 18,
    "copiedEvidenceFiles": 18
  }
}
```
