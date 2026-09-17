---
id: T0-PREREVIEW-POLICY-SOURCE-CHECK
title: Verify committed prereview source identity and complete policy replay
status: todo
branch: T0-PREREVIEW-POLICY-SOURCE-CHECK
worktree: C:\wt\T0-PREREVIEW-POLICY-SOURCE-CHECK
depends_on: [T0-PREREVIEW-POLICY-SOURCE]
allow_paths:
  - scripts/fixtures/prereview/policy-source/manifest.json
  - scripts/fixtures/prereview/policy-source/verify.ps1
  - scripts/fixtures/prereview/policy-source/selfcheck.ps1
  - docs/plans/PREREVIEW-REMOTE-ADOPTION.md
dod_command: if((Get-FileHash -LiteralPath scripts/fixtures/prereview/policy-source/manifest.json -Algorithm SHA256).Hash -cne '79EA6AB2F52FCE4BA000A78F13391EC50F26F91739153007B742C2CB02A5022F') { exit 1 }; $t=(& pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/selfcheck.ps1 *>&1 | Out-String); if($LASTEXITCODE -ne 0 -or -not $t.Contains('[POLICY-SOURCE-SELFCHECK-PASS]')) { exit 1 }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 The full manifest matches the independent base-card SHA-256, retaining source blob/hash/length identities and the exact counted 14+6 replacement recipe. The verifier checks both committed raw sources, replays every replacement and verifies each complete reconstructed body's length and SHA-256 without unavailable local commits or ignored files. [fixed manifest hash and default verifier]"
  - "A2 CandidateRoot compares both actual active document bodies before their unique source-receipt markers with the complete replayed bodies; exact bodies pass and a changed body fails. [positive candidate and candidate-body negative probe]"
  - "A3 Source-byte alteration, replacement-count alteration and altered replay output each return nonzero and identify the intended digest or count guard. Probes operate on disposable copies; originals remain unchanged. [three named negative probes]"
  - "A4 The existing adoption plan distinguishes committed historical sources from active policy and describes both reproducible commands. No workers, network calls, delivery gates or active POLICY files are changed. [document and scope review]"
budget: 350
non_goals: [Policy activation, source byte edits, worker or runner execution, delivery gate changes]
hygiene: Preserve both positive faces and four named semantic negative probes. Run probes only on temporary copies and verify source files stay unchanged; do not count parser failures as guard evidence.
---

# T0-PREREVIEW-POLICY-SOURCE-CHECK

Use the actual task router at this card's execution base; only newly run candidate checks establish acceptance. Preserve the full source and all four negative probes when fitting the independent budget.

After both prerequisite PRs actually merge, POLICY absorbs the new baseline without rewriting history, updates only its two allowed receipts to the durable paths, and runs the committed verifier with CandidateRoot plus the original DoD. Root separately authorizes round handling and the next normal protected ship; this card itself grants no reset or bypass.
