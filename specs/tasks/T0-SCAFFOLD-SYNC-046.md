---
id: T0-SCAFFOLD-SYNC-046
title: Evaluate upstream v0.46.0 and advance the scaffold high-water mark
depends_on: [T0-SCAFFOLD-SYNC-045]
parallelizable_with: [T0-SELFTEST-PAGED-PERF]
status: todo
branch: T0-SCAFFOLD-SYNC-046
worktree: C:\wt\T0-SCAFFOLD-SYNC-046
allow_paths:
  - scripts/_config.ps1
  - scripts/scaffold-sync.ps1
  - docs/SCAFFOLD-SYNC.md
  - specs/tasks/T0-SCAFFOLD-SYNC-046.md
forbid:
  - Changing ScaffoldOriginVersion, replacing divergent local files wholesale, or weakening mandatory gates
  - Adopting unreleased upstream tiered-acceptance or meta-nightly work
  - Product code, schema, dependencies, authentication, publishing, or network writes
non_goals:
  - Reimplementing dual-version behavior already present locally
  - Optimizing selftest; that belongs to T0-SELFTEST-PAGED-PERF
diagnosis: Upstream v0.46.0 formalizes immutable origin versus evaluated current version; this repository already has the behavior but still records v0.45.0 as its evaluated high-water mark.
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-SYNC-046; if ($LASTEXITCODE) { exit 1 }; & pwsh -NoProfile -File scripts/scaffold-sync.ps1 selfcheck; if ($LASTEXITCODE) { exit 1 }; $out = (& pwsh -NoProfile -File scripts/scaffold-sync.ps1 check 2>&1 | Out-String); if ($LASTEXITCODE -or $out -notmatch 'evaluated up to v0\.46\.0; no newer upstream release') { exit 1 }; . ./scripts/_config.ps1; if ((Get-ScaffoldOriginVersion) -cne '0.29.0' -or (Get-ScaffoldVersion) -cne '0.46.0') { exit 1 }
dod_exit: 0
dod_assert: Origin remains 0.29.0, current becomes 0.46.0, the v0.46.0 ledger row names the exact release tag and local-equivalent decision, scaffold-sync selfcheck passes, and check reports no unevaluated release.
acceptance:
  - "A1 ScaffoldOriginVersion remains 0.29.0 while ScaffoldVersion and the newest valid ledger row advance together to 0.46.0"
  - "A2 The ledger records v0.46.0 as applied via the already-present local dual-version coupling group and pins upstream tag d0c9145970e69626318a26ce922650f1a631c2f0"
  - "A3 Released v0.46.0 behavior is accounted for without importing unrelated pre-tag history or unreleased tier/meta changes, and scaffold-sync selfcheck/check remain green"
review_gate: codex {verdict:pass}
hygiene: Metadata-only alignment uses the existing selfcheck and live high-water check; no duplicate test framework or copied upstream implementation.
doc_sync: docs/SCAFFOLD-SYNC.md decision ledger and this card status
---

# T0-SCAFFOLD-SYNC-046

Record the released upstream coupling group that this repository already implements, while preserving
the immutable v0.29.0 origin and every deliberate local fork.
