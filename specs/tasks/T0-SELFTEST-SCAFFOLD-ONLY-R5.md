---
id: T0-SELFTEST-SCAFFOLD-ONLY-R5
title: Archive the verified scaffold-only delivery and synchronize its metadata
status: merged
depends_on: [T0-SELFTEST-SCAFFOLD-ONLY]
branch: T0-SELFTEST-SCAFFOLD-ONLY-R5
worktree: C:\wt\T0-SELFTEST-SCAFFOLD-ONLY-R5
allow_paths:
  - specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY-R5.md
  - specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md
  - specs/archive/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
forbid:
  - Changing source, workflow, tests, dependencies, budgets, review counters, lessons, technical debt or unrelated documentation
  - Archiving before the actual feature cleanup receipt, copying historical evidence as current, or inventing a future R5 result
  - Claiming the current selftest, formal R3, exact-head CI, ship or cleanup facts before the renderer receives actual receipts
non_goals:
  - Implementing or repairing the scaffold feature, rerunning feature validation, or changing DEVOPS-WORKFLOW or DELIVERY-CHAINS
  - Creating a new archive engine, changing archive history, adding lessons, or archiving this management card in the same batch
diagnosis: The feature card and its remote delivery facts are separate from the post-merge metadata projection. This bounded R5 candidate preserves the feature card, appends only verified final receipts, regenerates the archive index, and updates the existing current-stage and board anchors.
acceptance:
  - "A1 The actual feature PR 272, reviewed head d3a206a979468888b208569a2701a9dd3b67fcf6, merged commit 7500992541d15cc7a53b06efe4560dc62123dcff and merged tree a1ef3cd8f724583fff2e0b75a46464c6403e2350 are bound to the supplied final receipts; formal R3 is pass and exact-head CI is run 34314990805; head d3a206a979468888b208569a2701a9dd3b67fcf6; conclusion success; evidence scaffold-only-authorized-ship/ci-final.json."
  - "A2 The current-source full selftest fact is recorded as source BFACF8485F7255DDF0C7E39B6671AB50C8071E933B6AF4F00DBB48D06DD29376; exit 0; elapsed 2629.0102968s; 17a3 real17a3PASS; explicit skips 21; evidence scaffold-only-authorized-ship/receipts-272-final/nested-review/current-source-full/end.json; root-audit.json; full.log SHA256 4322FAC56559D6E7D92E75ACF57003709479D4A098796ACAA146BC0F08B71B3D; native ship and cleanup exits are 0 and 0; no historical result is relabelled as current delivery evidence."
  - "A3 The original feature card is preserved apart from status merged and one appended actual-delivery receipt; the live card is absent, the generated archive index matches, and only this feature enters archive."
  - "A4 The exact six-path metadata scope is enforced; the management card is a post-merge projection and contains no claim about its own future R3, CI, merge or cleanup."
  - "A5 CLAUDE and the existing TASK-BOARD feature row bind the same actual delivery facts; DEVOPS-WORKFLOW and DELIVERY-CHAINS remain unchanged."
dod_command: $ErrorActionPreference='Stop'; $b='7500992541d15cc7a53b06efe4560dc62123dcff'; $m='7500992541d15cc7a53b06efe4560dc62123dcff'; $t='a1ef3cd8f724583fff2e0b75a46464c6403e2350'; $pr='272'; $reviewed='d3a206a979468888b208569a2701a9dd3b67fcf6'; $a=@('specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY-R5.md','specs/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md','specs/archive/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md','specs/archive/cards-index.md','CLAUDE.md','docs/TASK-BOARD.md'); & git merge-base --is-ancestor $m $b; if($LASTEXITCODE){throw 'feature merge is not in pinned base'}; if((& git rev-parse ($m+'^{tree}')) -cne $t){throw 'merged tree mismatch'}; $p=@(& git diff --name-only --no-renames $b --); $p+=@(& git ls-files --others --exclude-standard); $p=@($p|Sort-Object -Unique); if(($p -join ([char]10)) -cne (($a|Sort-Object)-join ([char]10))){throw 'exact six-path scope mismatch'}; if(Test-Path -LiteralPath $a[1]){throw 'live feature card remains'}; $read={param($x)[IO.File]::ReadAllText((Join-Path $PWD $x)).Replace(([string]([char]13) + [string]([char]10)), [string]([char]10)).Replace([string]([char]13), [string]([char]10))}; $hash={param($x)[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($x)))}; $expected=@{'specs/archive/tasks/T0-SELFTEST-SCAFFOLD-ONLY.md'='0B0A901FEF182788C52713314AD33BF913B727B47A7C6ED63D63E7DDEFF348FC';'specs/archive/cards-index.md'='42D429163EA4483562D23F95894EEB747E96F7F40FF64418F239058E6E7856BE';'CLAUDE.md'='A6CA59AAAB79065E1440A414D0246D8682D33E5B28BED41F4B0D47999CF4EEBE';'docs/TASK-BOARD.md'='B05B55F4B987B89D5BCF7F70F1E159684B12C01E514F4DCD5D1602CD663FFC89'}; foreach($x in $expected.Keys){if((&$hash (&$read $x))-cne $expected[$x]){throw ('exact metadata hash mismatch: '+$x)}}; $c=&$read $a[2]; if($c -notmatch ('PR #'+$pr) -or $c -notmatch $reviewed -or $c -notmatch $m -or $c -notmatch $t){throw 'actual feature receipt facts missing'}; $old=(& git show ($b+':'+$a[1])|Out-String).Replace(([string]([char]13) + [string]([char]10)), [string]([char]10)).Replace([string]([char]13), [string]([char]10)); $old=$old.TrimEnd([char]10)+[char]10; $old=[regex]::Replace($old,'(?m)^status: (todo|in-progress|in-review|merged)$','status: merged'); $parts=$c.Split(@([char]10+'## Delivery receipt ('),[StringSplitOptions]::None); if($parts.Count -ne 2 -or $parts[0] -cne $old){throw 'original feature card changed beyond status and one receipt'}; $own=&$read $a[0]; $own=[regex]::Replace($own,'(?m)^dod_command: .*$' + [char]10,''); if((&$hash $own)-cne '98630898C7E65CE1A4B35BBDD03A3E8F1F5550FD29571B89BDB4C28388578F4A'){throw 'management card contract changed'}; & pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if($LASTEXITCODE){exit 1}; & pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SELFTEST-SCAFFOLD-ONLY-R5; if($LASTEXITCODE){exit 1}; Write-Host 'SCAFFOLD-ONLY-R5-METADATA-PASS'
dod_exit: 0
dod_assert: Exact LF-normalized hashes bind the complete archive card, generated archive index, CLAUDE pointer, TASK-BOARD row and management-card contract; the original card prefix is preserved apart from merged status and one receipt, the six-path scope is exact, and actual feature identities/tree are bound.
review_gate: codex {verdict:pass}
hygiene: Metadata-only SkipRed; use the existing archive.ps1 projection in an ignored staging root and exact LF-normalized comparisons. No feature test rerun, new runner, lesson or technical-debt entry.
doc_sync: Archive the original feature card, regenerate cards-index, update the existing CLAUDE current-stage pointer and TASK-BOARD row. This card's merged status is only the post-merge projection carried by its own future R5 PR; no future management outcome is preclaimed.
---

# T0-SELFTEST-SCAFFOLD-ONLY R5

This is a rendered post-merge metadata projection. It is not valid for delivery while any unresolved fact token remains. The feature's prior historical full selftest and current-source bounded R4 evidence retain their original provenance; the final receipt below is populated only from the actual feature PR and native delivery records.

Administrative base: `7500992541d15cc7a53b06efe4560dc62123dcff`.

Feature PR `#272`, reviewed head `d3a206a979468888b208569a2701a9dd3b67fcf6`, merge `7500992541d15cc7a53b06efe4560dc62123dcff`, merged tree `a1ef3cd8f724583fff2e0b75a46464c6403e2350`, merge date `2026-09-09`.

Final formal R3: `pass`; exact-head CI: `run 34314990805; head d3a206a979468888b208569a2701a9dd3b67fcf6; conclusion success; evidence scaffold-only-authorized-ship/ci-final.json`; current-source full selftest: `source BFACF8485F7255DDF0C7E39B6671AB50C8071E933B6AF4F00DBB48D06DD29376; exit 0; elapsed 2629.0102968s; 17a3 real17a3PASS; explicit skips 21; evidence scaffold-only-authorized-ship/receipts-272-final/nested-review/current-source-full/end.json; root-audit.json; full.log SHA256 4322FAC56559D6E7D92E75ACF57003709479D4A098796ACAA146BC0F08B71B3D`; normal ship exit: `0`; official cleanup exit: `0`.

The feature card's body remains its original text. Its appended receipt records only the facts above, and the archive index is generated by the existing archive mechanism. The R5 management card itself remains a pending PR artifact until its own normal task-loop gates complete; this text does not supply that future outcome.

R5.5: perform the post-cleanup lesson review from actual evidence. Do not add a lesson unless a reusable new issue is demonstrated; the current provenance distinction is already recorded.
