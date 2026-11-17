---
id: T0-SCAFFOLD-SYNC-046
title: Evaluate upstream v0.46.0 and advance the scaffold high-water mark
depends_on: [T0-SCAFFOLD-SYNC-045]
parallelizable_with: [T0-SELFTEST-PAGED-PERF]
status: merged
branch: T0-SCAFFOLD-SYNC-046
worktree: C:\wt\T0-SCAFFOLD-SYNC-046
allow_paths:
  - scripts/_config.ps1
  - scripts/scaffold-sync.ps1
  - docs/SCAFFOLD-SYNC.md
  - specs/tasks/T0-SCAFFOLD-SYNC-046.md
  - CLAUDE.md
forbid:
  - Changing ScaffoldOriginVersion, replacing divergent local files wholesale, or weakening mandatory gates
  - Adopting the released v0.47.0 tiered-acceptance, nightly-meta, or other v0.47 coupling groups in this v0.46 card
  - Product code, schema, dependencies, authentication, publishing, or network writes
non_goals:
  - Reimplementing dual-version behavior already present locally
  - Optimizing selftest; that belongs to T0-SELFTEST-PAGED-PERF
diagnosis: Upstream v0.46.0 formalizes immutable origin versus evaluated current version; this repository already has the behavior but still records v0.45.0 as its evaluated high-water mark.
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-SYNC-046; if ($LASTEXITCODE) { exit 1 }; $self=(& pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck 2>&1 | Out-String); $selfExit=$LASTEXITCODE; Write-Host $self; if ($selfExit -or $self -cnotmatch '(?m)^scaffold-sync selfcheck: PASS(?=[ \t\r]|$)') { exit 1 }; . ./scripts/_config.ps1; $ledger=Get-Content -LiteralPath docs/SCAFFOLD-SYNC.md -Raw; $claude=Get-Content -LiteralPath CLAUDE.md -Raw; if ((Get-ScaffoldOriginVersion) -cne '0.29.0' -or (Get-ScaffoldVersion) -cne '0.46.0' -or $ledger -notmatch '(?m)^\| v0\.46\.0 \| applied \|.*d0c9145970e69626318a26ce922650f1a631c2f0' -or $claude -notmatch 'ScaffoldVersion.*v0\.46\.0' -or $claude -notmatch '当前版本为 \*\*v0\.46\.0\*\*') { exit 1 }
dod_exit: 0
dod_assert: Origin remains 0.29.0; current and the newest valid local ledger row are both v0.46.0, the row is an applied decision pinned to upstream commit/tag d0c9145970e69626318a26ce922650f1a631c2f0, CLAUDE.md current/high-water references are synchronized, and the existing scaffold-sync selfcheck passes both the real v0.46 ledger/origin-current contract and a Get-NewerVersion fixture that recognizes v0.47.0 above base v0.46.0 without adding v0.47.0 to this card's ledger.
acceptance:
  - "A1 ScaffoldOriginVersion remains 0.29.0 while ScaffoldVersion and the newest valid ledger row advance together to 0.46.0"
  - "A2 The ledger records v0.46.0 as applied via the already-present local dual-version coupling group and pins upstream tag d0c9145970e69626318a26ce922650f1a631c2f0"
  - "A3 Released v0.46.0 behavior is accounted for without importing unrelated pre-tag history or the released v0.47 tier/meta groups, and the existing scaffold-sync selfcheck remains green"
review_gate: codex {verdict:pass}
hygiene: Metadata-only alignment uses the existing selfcheck and deterministic local high-water checks; no duplicate test framework or copied upstream implementation.
doc_sync: docs/SCAFFOLD-SYNC.md decision ledger, CLAUDE.md current/high-water references, and this card status
---

# T0-SCAFFOLD-SYNC-046

Record the released upstream coupling group that this repository already implements, while preserving
the immutable v0.29.0 origin and every deliberate local fork.

The status is the normal post-merge projection in this delivery PR (DEVOPS-WORKFLOW §1).
It does not claim that R3, CI, merge or cleanup has already completed.
