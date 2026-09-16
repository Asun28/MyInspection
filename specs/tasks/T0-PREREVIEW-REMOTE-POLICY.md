---
id: T0-PREREVIEW-REMOTE-POLICY
title: Adopt prereview policy bytes and document the pending protocol
status: todo
branch: T0-PREREVIEW-REMOTE-POLICY
worktree: C:\wt\T0-PREREVIEW-REMOTE-POLICY
depends_on: [T0-PREREVIEW-REMOTE-SCHEMA]
allow_paths:
  - docs/PREREVIEW-PROTOCOL.md
  - docs/PREREVIEW-CHECKLISTS.md
dod_command: $t = (& pwsh -NoProfile -File scripts/check-prereview-schema.ps1 -Anchors docs/PREREVIEW-PROTOCOL.md *>&1 | Out-String); if ($LASTEXITCODE -ne 0 -or -not $t.Contains('[PREREVIEW-ANCHORS-OK]')) { exit 1 }; $d = Get-Content docs/PREREVIEW-CHECKLISTS.md -Raw; foreach ($lens in @('code','tests','prose','scripts')) { if ($d -notmatch ('(?m)^## Lens: ' + $lens + '\s*$')) { exit 1 } }; exit 0
dod_exit: 0
review_gate: codex {verdict:pass}
plan_ref: docs/plans/PREREVIEW-REMOTE-ADOPTION.md
acceptance:
  - "A1 The protocol status-code table matches the revision 1 schema bidirectionally without missing, duplicate or extra codes. [anchor check]"
  - "A2 Checklists retain the approved four lenses and candidate/coverage policy, with policy bytes available at the next merge-base. [lens check and source comparison]"
  - "A3 Protocol hunk identities include the positive per-file ordinal, and adoption status explicitly distinguishes available artifacts from pending worker/runner/RECORDS implementations. [document review]"
budget: 460
non_goals: [Worker execution, RECORDS implementation, R3 changes]
hygiene: Documentation adoption; no new behavior or mutation suite.
---

# T0-PREREVIEW-REMOTE-POLICY

Remote adoption of the already approved final local artifact; no original local card is relabelled as remotely merged. Source and excluded consumers: docs/plans/PREREVIEW-REMOTE-ADOPTION.md.

The original tests and mutation receipts are historical evidence only. Run the DoD and the current remote delivery gates on this candidate. R5 records the new PR/review/CI identity and archives this adoption card.
