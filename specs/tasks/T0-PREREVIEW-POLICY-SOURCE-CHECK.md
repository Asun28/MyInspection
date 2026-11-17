---
id: T0-PREREVIEW-POLICY-SOURCE-CHECK
title: Verify committed prereview source identity and complete policy replay
status: merged
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

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. The source documents that `scripts/fixtures/prereview/policy-source/raw/` copies are now on master, and each raw copy is byte-identical to its document (`git hash-object` equal on 2026-09-24). Whether this card is closed, narrowed or kept is a user decision.

## Remote delivery (2026-10-05)

PR [#318](https://github.com/Asun28/MyInspection/pull/318) merged at `2026-10-04T23:31:41Z`: reviewed head `29e3d44721c57f778cea1060dc686216c7a27939`, base `e754e0f2755d2f7fff1fe21c7237153592f7a0b7`, squash `4ec53add20b62a02c021301a397a3eaa63cfe281`. The reviewed and merged tree is `e1dd12fa551ff08e30c4dd666d07856776b23f94`. This delivery resolves this card's earlier reconcile disposition; it does not decide the remaining POLICY or FACTS cards.

The original protected ship passed DoD, verify, four-path scope (342 changed lines against budget 350), license and secret checks. CI run `37243668732`, attempt 1, workflow `.github/workflows/ci.yml`, ran for the reviewed head; `verify` and `required` succeeded. The original ship checked the PR/run association before its protected squash merge; the post-merge REST run snapshot has an empty `pull_requests` array, while the PR's final checks link the same run and jobs.

Formal Sol/high R3 passed both spec and standards with zero findings. This is the fifth historical formal attempt and round 1 after the separately authorized counter reset. The reviewer actually read the committed raw sources and ran the original DoD with exit 0 and its expected sentinel. The user's one next-review authorization was consumed at `2026-10-04T23:24:58.167965Z`. All four earlier BLOCK results remain preserved; no result was relabeled or bypassed.

The current candidate's fresh `selftest.ps1 -Parallel` completed with native exit 0 at `2026-10-04T23:14:23.1267446Z`: five original shards covered the normal 17-gate union. `-IncludeMeta` was not passed; the missing T11 baseline and unsupported Windows file-symlink arms remain explicit skips. This is not a claim of nightly/meta or device validation. The selfcheck exercised original replay, exact candidate comparison, eight negative probes and three equal-length SHA-256 cases; originals remained unchanged.

Evidence is retained under `_local/prereview-remote-20260917/`: current full-preflight manifest `7DBF150534627BB4BA4CA16313AFE9D261C5BEF938843C3C7329174704D9F9E2`, original ship manifest `3ABBF22DB23476CF7FC4A909F2B62D10FBDF52EE5E2763EDA325AE6F772C9C58`. These local archives retain native exits, full reviewer input/output and session, actual tool calls, CI/API snapshots, candidate guards and the original T24 token. They supplement the committed source receipts and PR; they are not required by the verifier at runtime.

Debt scan: no new code defect was identified in this delivered diff; the final formal review had zero findings. R5.5: explicitly skip a new ledger entry because the observed guard-specific probe, command-string escaping and reviewer-failure classification lessons are already covered by L165, L17 and L21/L205. This card adds no product functionality and does not activate POLICY or complete the later FACTS delivery.
