---
id: T4-SYMBOL-REMOTE-CONTRACT
title: Recover the approved symbol chrome V2 delivery contract with explicit scope
status: in-progress
depends_on: []
parallelizable_with: []
branch: T4-SYMBOL-REMOTE-CONTRACT
worktree: C:\wt\T4-SYMBOL-REMOTE-CONTRACT
allow_paths:
  - specs/tasks/T4-SYMBOL-REMOTE-CONTRACT.md
  - specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md
forbid:
  - Changing design documents, Kotlin, Gradle, schema, tokens, dependencies, workflows or gate implementations
  - Claiming local historical tests or human adjudication as current remote implementation or R3 approval
  - Weakening the original acceptance obligations, discarding earlier review history or bypassing a failed gate
non_goals:
  - Publishing the symbol design itself, UI implementation, archive closeout or unrelated local work
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: PR260 used an ad-hoc branch without a matching task scope; its recovered historical acceptance also omitted row-level state-carrier repairs, which have now been added and verified.
acceptance:
  - "A1 the entire change is confined to this scope card and T4-DESIGN-SYMBOL-CHROME-V2, whose status remains todo pending design publication"
  - "A2 the target card preserves approved OD-1/OD-2 decisions and every original acceptance check, adds 15 unique row checks for all identified repairs, and distinguishes historical tables from final substitution deltas"
  - "A3 PR260 and its two BLOCK outcomes remain traceable; the user-approved additional review is not represented as a fresh unlimited review allowance"
  - "A4 card schema, archive index, secret scan, complete-diff budget, formal independent review and candidate CI pass before remote merge"
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-SYMBOL-REMOTE-CONTRACT; if ($LASTEXITCODE) { exit 1 }; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-DESIGN-SYMBOL-CHROME-V2; if ($LASTEXITCODE) { exit 1 }; $c=Get-Content -LiteralPath 'specs/tasks/T4-DESIGN-SYMBOL-CHROME-V2.md'; $d=@($c | Where-Object { $_.StartsWith('dod_command: ') }); if ($d.Count -ne 1 -or ([regex]::Matches($d[0], "@[(]'[DU]',")).Count -ne 40 -or @($c | Where-Object { $_ -ceq 'status: todo' }).Count -ne 1) { exit 1 }; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; exit $LASTEXITCODE
dod_exit: 0
dod_assert: both cards validate, the target remains todo with 40 declared checks, and the archive projection is unchanged; scope and review separately verify the semantic and path obligations
review_gate: codex {verdict:pass}
hygiene: this metadata recovery reuses existing card/index checks and the retained 40-case source-text verification; it adds no executable production or testing framework
doc_sync: retain links between this recovery and PR260; the symbol design card stays todo until its separate implementation PR succeeds
---

# T4-SYMBOL-REMOTE-CONTRACT

## Approved recovery scope

On 2026-09-08 the user approved correcting the formal task scope, preserving review history, and **one additional formal R3 review** after the two reviews of [PR #260](https://github.com/Asun28/MyInspection/pull/260). This card and its matching branch implement that recovery. They do not reset the substantive history or authorize repeated reviews after another BLOCK.

- First review, `5d2481a98f60c7d9c6816db5bed1ed2a511011ea`: missing row-level acceptance assertions. Fixed in `d09414d034f6d700a31953c8923ed750a9a0558b` by retaining the 25 existing checks and adding 15 unique row anchors. All 40 checks were independently challenged in an isolated fixture; each failed by its named assertion; the pinned candidate and restored candidate passed.
- Second review, `d09414d034f6d700a31953c8923ed750a9a0558b`: no matching scope card for the ad-hoc branch. This formal card supplies the exact two-path scope and valid task identity. Neither old verdict is relabelled as PASS.

The original local design merge `53673571` and its eight historical R3 rounds remain local-delivery history. This recovery publishes only the approved decisions, strengthened acceptance command and evidence distinctions. It changes no design rule or product behavior. Run the normal remote ship pipeline with `-SkipRed` because this is a non-TDD metadata registration, not a recovery shortcut for an implementation card. The target design's real baseline RED and 40 source-text mutation checks are recorded in that card and must be revalidated on its separate publication candidate.

If the authorized additional R3 returns BLOCK, stop for the user; do not use another branch, counter reset, automatic retry or manual merge to obtain a further verdict. PR260 remains the preserved historical review record and will be cross-linked with the correctly named recovery PR.
