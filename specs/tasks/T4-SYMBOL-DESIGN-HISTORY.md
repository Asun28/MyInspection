---
id: T4-SYMBOL-DESIGN-HISTORY
title: Preserve the existing local symbol design review history without changing remote acceptance
status: merged
depends_on: []
branch: T4-SYMBOL-DESIGN-HISTORY
worktree: C:\wt\T4-SYMBOL-DESIGN-HISTORY
allow_paths:
  - specs/tasks/T4-SYMBOL-DESIGN-HISTORY.md
  - specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md
forbid:
  - Changing target frontmatter, executable acceptance, current normative requirements, product code or shared gates
  - Claiming local historical verification as current remote design delivery or R3 approval
  - Consuming PR263 reset or review authorization
non_goals:
  - Parser integration, new consumer tests, design publication, archive closeout or unrelated local work
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: The existing PR263 candidate carries lengthy local review history together with consumer repairs; retaining all history and real-entrypoint tests exceeds the complete-diff character budget.
acceptance:
  - "A1 copy the existing R4 and eight local R3 review records verbatim, with an explicit local-history-only note"
  - "A2 preserve every byte of the remote target outside that insertion, including todo status, existing frontmatter DoD, pending-decision section and original change log"
  - "A3 only the target card and this management card change; PR263 remains open and its repair/reset/review grant stays separate"
  - "A4 metadata checks, full project gates, independent review and exact-head CI pass before merging"
dod_command: $ErrorActionPreference='Stop'; $p='specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md'; $t=[IO.File]::ReadAllText((Join-Path (Get-Location) $p)).Replace("`r`n","`n"); $a=$t.IndexOf('## R4 变异收据'); $z=$t.IndexOf('## 变更记录',[Math]::Max(0,$a)); if($a -lt 0 -or $z -le $a){throw 'SYMBOL-HISTORY-SOURCE: missing region'}; $history=$t.Substring($a,$z-$a); $sourceHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($history))); if($sourceHash -cne '4FB8BE198E416F20FB3FC3E90E1DB789B5D57270186EF7D4440FD0C882A00D4F'){throw 'SYMBOL-HISTORY-SOURCE: hash drift'}; $h=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($t))); if($h -cne '5752F51CE2CD052F9F4CA6BB6645807159C4C5D02FC9E4E1E7D10B7F42A0E1D5'){throw 'SYMBOL-HISTORY: content drift'}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-SYMBOL-DESIGN-HISTORY; if($LASTEXITCODE){exit 1}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-DESIGN-SYMBOL-CHROME-V2; exit $LASTEXITCODE
dod_exit: 0
dod_assert: exact target content matches the preserved history insertion and unchanged remote acceptance; both card schemas validate
review_gate: codex {verdict:pass}
hygiene: metadata-only SkipRed; preserve original historical text, do not manufacture a production RED or rerun its historical tests
doc_sync: include the management status projection in its approved PR; keep target status todo and PR263 integration pending
---

# Local design history preservation

The preserved history's LF-normalized SHA-256 is 4FB8BE198E416F20FB3FC3E90E1DB789B5D57270186EF7D4440FD0C882A00D4F, independently computed from the source commit named below. The DoD verifies those copied bytes against that literal; it does not require a future clone to retain the unmerged PR263 commit object.

Independent value: these eight local review rounds and the R4 receipt are absent from the canonical remote card. Publishing them preserves specific findings, repairs, rejected alternatives and source-bound evidence for later audits even if PR263 integration remains blocked. It does not approve the old failed verdicts or substitute for PR263's existing review counter.

Authorization recorded 2026-09-09: the user replied “授权” to the explicit request for this separate two-file history PR and ONE formal review without resetting rounds. A BLOCK requires a new decision; it does not authorize another invocation. PR263's existing reset/review grant remains separate and unused. This card's merged status is the post-merge projection included in its own PR under DEVOPS-WORKFLOW section 1, not a claim that review or merge has already occurred. The target card remains todo.

Source history is the exact section beginning R4 mutation receipt through the end of local R3 rounds7-8, before Change log, in PR263 head af230c16cd39c88c601a4af983c1a0f777f52c5e. It records the original local design candidate, not current remote completion. Existing remote acceptance remains untouched; parser integration and all old/new checks stay in the subsequent PR263 repair.

After this independently reviewed history-only change merges, PR263 normally merges that new base and retains the exact same history, removing it from PR263's new diff without deleting or weakening any record. This is a genuine two-stage publication, not a higher diff limit or a reset through branch recreation. R5.5: no new lesson is introduced by this mechanical history publication.
