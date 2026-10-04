---
id: T0-PREREVIEW-REMOTE-POLICY
title: Adopt prereview policy bytes and document the pending protocol
status: todo
branch: T0-PREREVIEW-REMOTE-POLICY
worktree: C:\wt\T0-PREREVIEW-REMOTE-POLICY
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA, T0-PREREVIEW-POLICY-SOURCE-CHECK]
allow_paths:
  - docs/PREREVIEW-PROTOCOL.md
  - docs/PREREVIEW-CHECKLISTS.md
dod_command: $s=(& pwsh -NoProfile -File scripts/fixtures/prereview/policy-source/verify.ps1 -CandidateRoot . *>&1 | Out-String); if($LASTEXITCODE -ne 0 -or -not $s.Contains('[POLICY-SOURCE-EVIDENCE-PASS]')) { exit 1 }; $t = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 -Anchors docs/PREREVIEW-PROTOCOL.md *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-ANCHORS-OK]')) { exit 1 }; $d = Get-Content docs/PREREVIEW-CHECKLISTS.md -Raw; foreach ($lens in @('code','tests','prose','scripts')) { if ($d -notmatch ('(?m)^## Lens: ' + $lens + '\s*$')) { exit 1 } }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 The protocol status-code table matches the revision 1 schema bidirectionally without missing, duplicate or extra codes. [anchor check]"
  - "A2 Checklists retain the approved four lenses and candidate/coverage policy, with policy bytes available at the next merge-base. Approved source bytes, the exact replacement recipe and the reproducible comparison are committed artifacts; the verifier compares both complete candidate bodies without unavailable local commits or ignored evidence. [lens check and committed source replay]"
  - "A3 Protocol hunk identities include the positive per-file ordinal, and adoption status explicitly distinguishes available artifacts from pending worker/runner/RECORDS implementations. [document review]"
budget: 460
non_goals: [Worker execution, RECORDS implementation, R3 changes]
hygiene: Documentation adoption; no new behavior or mutation suite.
---

# T0-PREREVIEW-REMOTE-POLICY

Remote adoption of the already approved final local artifact; no original local card is relabelled as remotely merged. Source and excluded consumers: docs/plans/PREREVIEW-REMOTE-ADOPTION.md.

The original tests and mutation receipts are historical evidence only. Run the DoD and the current remote delivery gates on this candidate. R5 records the new PR/review/CI identity and archives this adoption card.

PR #308 retained two real source-evidence blocks: first head fd682ef6a57e648283ed4680dbd56712effada10 and second head efcc79084b0a23fd5eda587e3334750c4b1d8a23. Both formal verdicts and the round counter remain preserved. Source publication precedes the next repair; any subsequent round handling requires the separate explicit authorization recorded by the coordinator. This amendment strengthens A2 and the DoD, and grants no bypass.

## Reconcile note (2026-09-24)

The 2026-09 local/origin reconcile landed the local-only parts of local master's PR review v2 phase 1a chain on master: PROTOCOL-DOC (`a66af219`), CHECKLISTS (`2782b55b`), RECORDS (`dec30514`), FACTS-LIB (`b675d6a6`) and STATE-1A (`62ec5f3b`). For the schema and its checker (SCHEMA and UNIT-ID-REVISION), master keeps origin's versions from T0-PREREVIEW-REMOTE-SCHEMA. Both files in this card's allow_paths, `docs/PREREVIEW-PROTOCOL.md` and `docs/PREREVIEW-CHECKLISTS.md`, are therefore on master at their local merged bytes. Whether this card is closed, narrowed or kept is a user decision.
