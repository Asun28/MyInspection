---
id: T0-SELFTEST-RISK-ROUTING-R5
title: Archive verified task-risk routing and synchronize its delivery record
status: merged
depends_on: [T0-SELFTEST-RISK-ROUTING]
allow_paths:
  - specs/tasks/T0-SELFTEST-RISK-ROUTING.md
  - specs/archive/tasks/T0-SELFTEST-RISK-ROUTING.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - specs/tasks/T0-SELFTEST-RISK-ROUTING-R5.md
forbid:
  - Runtime, workflow, configuration, budget, review-counter or pending-card contract changes
  - Archiving SCAFFOLD-ONLY, this management card in this batch, other cards or technical debt
  - Claiming merge or cleanup from historical candidate evidence, or claiming an all-shards full selftest
non_goals:
  - Repeating PR275 corrections, implementing downstream capabilities or changing acceptance policy
  - Rewriting historical registration cards, creating a new archive engine or public helper
diagnosis: Implementation ship and squash cleanup produce facts absent from the pre-merge card and board; the old R5 draft compounded this by keeping its management contract outside the candidate and carrying stale pending/18-record statements. This closeout delivers its own contract, qualifies old notes as history, appends exact final receipts and synchronizes the sole missing board row and current-stage entry. The authority sweep and preservation decisions are documented below.
acceptance:
  - "A1 PR 273 reviewed head 9af2af3a4d4a4abc93549d82da2350b186954cd2 and actual merge fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c are proven by original T24, final official verdict and captured merged-PR identity; official cleanup exited zero before archive"
  - "A2 The original RISK card is preserved apart from merged status, a historical-context note and the appended exact receipt; the live card is absent and the existing archive projection validates"
  - "A3 CLAUDE current stage and the sole RISK board completion row agree with the final receipt; existing CLAUDE/workflow routing and separate product verify instructions remain unchanged"
  - "A4 The exact six-path diff includes this self-contained management card with its post-merge merged status projection; pending capability cards, other board rows, implementation source and unrelated archive history remain unchanged"
  - "A5 Evidence distinguishes 11 current targeted mutation kills from 14 historical unchanged-function reuse records, and makes no all-shards full selftest claim"
dod_command: $ErrorActionPreference='Stop'; $b='fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c'; $m='fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c'; & git merge-base --is-ancestor $m $b; if($LASTEXITCODE){throw 'merge absent from pinned base'}; $a=@('specs/tasks/T0-SELFTEST-RISK-ROUTING.md','specs/archive/tasks/T0-SELFTEST-RISK-ROUTING.md','specs/archive/cards-index.md','CLAUDE.md','docs/TASK-BOARD.md','specs/tasks/T0-SELFTEST-RISK-ROUTING-R5.md'); $paths=@(& git -c core.quotepath=false diff --name-only --no-renames $b --); if($LASTEXITCODE){throw 'diff failed'}; $paths+=@(& git -c core.quotepath=false ls-files --others --exclude-standard); if($LASTEXITCODE){throw 'untracked scan failed'}; $paths=@($paths|Sort-Object -Unique); if(($paths -join "`n") -cne (($a|Sort-Object) -join "`n")){throw 'exact six delivery paths required'}; if(Test-Path -LiteralPath $a[0]){throw 'live implementation card remains'}; $read={param($p) [IO.File]::ReadAllText((Join-Path $PWD $p)).Replace("`r`n","`n")}; $hash={param($s) [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($s)))}; $expected=@{'specs/archive/tasks/T0-SELFTEST-RISK-ROUTING.md'='4C2360B5B3FEA3258C06447856DC98346EA5CBB5F60725015D0702D130B47A70';'specs/archive/cards-index.md'='C3665D5F758B9307A96C645D82C73CDB9993AB9B1E3077AA09B3838DD5990780';'CLAUDE.md'='38B190333C1C25FC3AAD3948D5440E0485BEC1067DDB1B54C9F1A2FCD5719112';'docs/TASK-BOARD.md'='06E465B1305518DA3323AC3633A79C5606C53B959EDF6856CAB129F86B053445'}; foreach($p in $expected.Keys){if((& $hash (& $read $p)) -cne $expected[$p]){throw ('exact metadata mismatch '+$p)}}; $card=& $read $a[5]; if([regex]::Matches($card,'(?m)^status: merged$').Count -ne 1){throw 'management post-merge status projection missing'}; if((& $hash ([regex]::Replace($card,'(?m)^dod_command: .*\n',''))) -cne '841DEDA78E84AFE0D7EA0B02F931E082E34F135D180F5D1D19C0D7B5093104F4'){throw 'management contract changed'}; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if($LASTEXITCODE){exit 1}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SELFTEST-RISK-ROUTING-R5; if($LASTEXITCODE){exit 1}; Write-Host 'RISK-R5-METADATA-PASS'
dod_exit: 0
dod_assert: The exact six paths and generated base-specific text hashes match, management fields/body are preserved, the implementation live card is absent, and existing archive projection and management-card checks pass.
review_gate: codex {verdict:pass}
hygiene: Metadata SkipRed only; reuse archive.ps1 and exact normalized-text comparisons. No redundant runner or repeated implementation SelfCheck.
doc_sync: CLAUDE.md current stage, docs/TASK-BOARD.md RISK completion, original RISK status/archive and generated cards index; preserve existing CLAUDE/workflow routing. The management status is the post-merge merged projection delivered in this PR under DEVOPS-WORKFLOW section 1; its own future merge SHA and gate success are not claimed. Official cleanup follows its actual successful ship, and a later ordinary archive batch may move it.
---

# RISK routing delivery closeout

Administrative base fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c. Implementation PR 273 merged on 2026-09-08 as fcdb4d8ca5f4d76c2fe73fc6177bc828e239ce6c, reviewed head 9af2af3a4d4a4abc93549d82da2350b186954cd2. The archived implementation receipt names original T24, cleanup, ship, focused and mutation evidence hashes. Conditional preparation rejects absent identities, non-pass final R3, incomplete official cleanup, changed reviewed blobs or mismatched captures before writes. This management PR remains pending. Its status: merged is explicitly the post-merge projection included in the ship PR, not a claim that this PR has already merged or passed its gates. No future management merge SHA is filled in.

The changed paths are both sides of one card move, generated archive index, CLAUDE current stage, RISK board row and this management card. The single-line DoD embeds exact LF-normalized result hashes and checks its own contract excluding only the self-referential dod_command line. Reviewer execution does not require ignored evidence or an uncommitted helper.

Authority sweep: CLAUDE current stage/workflow entry; DEVOPS-WORKFLOW TaskId paragraph/R5 mapping; TASK-BOARD route row; RISK front matter/body/doc_sync; specs/README status/fields; specs/archive/README and archive.ps1; task-loop R5; task.ps1 T24 mint/cleanup CAS; tracked Markdown references to the RISK ID and explicit TaskId selftest commands. Existing implementation workflow text supplies the behavior and is preserved without new write scope. No root README exists at the inspected base.

NIGHTLY/SKILL depends_on IDs and historical registration assertions remain unchanged. SCAFF is only the unique board insertion anchor. PR275 corrections are outside this closeout. If the eventual base introduces another RISK row or changes required routing source/text, preparation stops for a new exact comparison rather than overwriting later work.

PR275 round one exposed the invisible management card; round two identified a stale corresponding BOARD row, missing doc_sync authority and absent diagnosis. This card covers those three classes for its own RISK facts and does not deliver the PR275 fixes.

R5.5: no new lesson added by this metadata-only batch; task-loop/L97 already covers the same-class authority sweep. Independently discovered implementation debt requires its own authorized card.
