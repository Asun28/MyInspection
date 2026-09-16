---
id: T4-SYMBOL-MARKDOWN-PARSER-R5
title: Archive the verified symbol Markdown prerequisite and record remote delivery
status: merged
depends_on: [T4-SYMBOL-MARKDOWN-PARSER]
branch: T4-SYMBOL-MARKDOWN-PARSER-R5
worktree: C:\wt\T4-SYMBOL-MARKDOWN-PARSER-R5
allow_paths:
  - specs/tasks/T4-SYMBOL-MARKDOWN-PARSER-R5.md
  - specs/tasks/T4-SYMBOL-MARKDOWN-PARSER.md
  - specs/archive/tasks/T4-SYMBOL-MARKDOWN-PARSER.md
  - specs/archive/cards-index.md
  - CLAUDE.md
forbid:
  - Changing implementation, tests, dependencies, workflows, gate budgets or review counters
  - Archiving another card, this management card, technical debt or lessons in this batch
  - Claiming PR263 integration or symbol design publication is complete
non_goals:
  - PR263 scope expansion, implementation repairs, new archive tooling or unrelated delivery
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: PR274 has verified remote merge and completed cleanup, but its live card still says in-progress and lacks the final receipt; PR263 explicitly excludes archive closeout.
acceptance:
  - "A1 exact PR274 reviewed head, successful CI, merge and cleanup facts are retained without relabelling the three preceding BLOCKs"
  - "A2 the entire original prerequisite card is preserved apart from merged status, a historical-context note and an appended delivery receipt; only that card moves to archive"
  - "A3 the existing archive projection is valid and CLAUDE records prerequisite-only delivery with PR263 integration and design publication still pending"
  - "A4 the complete diff contains exactly the five allowed paths, no source changes, and includes this management contract"
dod_command: $ErrorActionPreference='Stop'; $b='18741ac29a20da426ffe99c02c924f2d1b3b29f5'; $own='specs/tasks/T4-SYMBOL-MARKDOWN-PARSER-R5.md'; $live='specs/tasks/T4-SYMBOL-MARKDOWN-PARSER.md'; $archive='specs/archive/tasks/T4-SYMBOL-MARKDOWN-PARSER.md'; $index='specs/archive/cards-index.md'; $expected=@($own,$live,$archive,$index,'CLAUDE.md')|Sort-Object; $changed=@(git diff --name-only --no-renames $b --); if($LASTEXITCODE){throw 'scope diff failed'}; $untracked=@(git ls-files --others --exclude-standard); if($LASTEXITCODE){throw 'untracked scan failed'}; $actual=@($changed+$untracked)|Sort-Object -Unique; if(@(Compare-Object $expected $actual -CaseSensitive).Count){throw 'five-path scope mismatch'}; if(Test-Path -LiteralPath $live){throw 'live prerequisite remains'}; $hashes=@{$archive='69D23C7C99D9909F49C4A8BB6ED355E5B8852026B291A703098F8B6F8EC46C7B';$index='10DDB90778141CE18D29CC57A96684E074653E757D75A847A763137903A7A20C';'CLAUDE.md'='0B87C2D99237EDAE2D291953E0CC0C2D1E96579C305DF7768DA0D8179F947BDB';$own='93D8DD5AF62661EF4901B2FF6DD33903FCBCDB6F458308E492E5022622DFB0F2'}; foreach($p in $hashes.Keys){$t=[IO.File]::ReadAllText((Join-Path (Get-Location) $p)).Replace("`r`n","`n"); if($p -ceq $own){$t=[regex]::Replace($t,'(?m)^dod_command: .*\n','')}; $h=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($t))); if($h -cne $hashes[$p]){throw "metadata content mismatch: $p"}}; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if($LASTEXITCODE){throw 'archive projection failed'}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T4-SYMBOL-MARKDOWN-PARSER-R5; if($LASTEXITCODE){throw 'management schema failed'}; 'SYMBOL-PARSER-R5-METADATA-PASS'
dod_exit: 0
dod_assert: exact metadata output and five-path scope match the pinned delivery evidence; original card history is preserved, only its live path disappears, archive projection and management schema pass
review_gate: codex {verdict:pass}
hygiene: metadata-only SkipRed; reuse archive.ps1 with an isolated projection root containing only the selected live card, then verify exact results and scope
doc_sync: archive the original prerequisite and regenerate cards-index; add the exact PR274 receipt and CLAUDE current-stage pointer; this management status is the post-merge projection carried in its own PR, with no claim about its future review, CI or merge SHA
---

# Symbol Markdown prerequisite delivery closeout

User authorization on 2026-09-09: a separate metadata-only archive PR and ONE formal review, limited to these five paths. This is independent of PR263's unused repair/reset/review grant. No counter reset is authorized for this task. Any BLOCK stops for a new user decision.

This management card's merged status is the post-merge projection delivered in the PR under DEVOPS-WORKFLOW section1, not a claim that this management PR has already passed or merged. Its own eventual merge SHA is not filled in; a later ordinary archive batch may archive the management card.

Administrative base18741ac29a20da426ffe99c02c924f2d1b3b29f5 is the actual PR274 squash commit. The reviewed head3b7d51f1519f4b4817289edf2d14c2a68173474e and merged tree are identical (tree0aecf5fe5e76bc5d9f9e9494e20d3c1bd25cebad). Formal R3 PASS has empty reasons; exact-head CI34291612516 succeeded. Normal ship36066 and official cleanup exited0, and the original task worktree/local branch were verified absent. All24 review files were hash-compared when preserved before cleanup, alongside T24/T35 receipts. No runtime/test rerun is inferred from this metadata operation.

Authority sweep covered the original card and its doc_sync, CLAUDE current-stage/workflow index, DEVOPS-WORKFLOW R5, archive README/script, task-loop and tracked references to the prerequisite ID. There is no existing prerequisite TASK-BOARD row to amend. Other cards, archive history and technical debt remain unchanged; no new board scope is needed.

R5.5: no new lesson is added. The existing L26 pinned-API verification rule already covers the relevant experience: actual native calls contradicted a review premise, dedicated boundary tests and mutation witnesses were added, the old verdict stayed preserved, and a subsequent authorized formal review passed. Do not duplicate that existing rule or claim all parser contexts/platforms are exhaustively tested.
