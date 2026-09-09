---
id: T1-SPIKE-PLATFORM-R5
title: Archive the verified platform spike and record its remote delivery
status: merged
depends_on: [T1-SPIKE-PLATFORM]
branch: T1-SPIKE-PLATFORM-R5
worktree: C:\wt\T1-SPIKE-PLATFORM-R5
allow_paths:
  - specs/tasks/T1-SPIKE-PLATFORM-R5.md
  - specs/tasks/T1-SPIKE-PLATFORM.md
  - specs/archive/tasks/T1-SPIKE-PLATFORM.md
  - specs/archive/cards-index.md
  - CLAUDE.md
  - docs/TASK-BOARD.md
  - docs/spike/PLATFORM-SPIKE.md
forbid:
  - Changing source, tests, dependencies, shared scripts, budgets or review counters
  - Archiving another card or this management card, or changing lessons or technical debt
  - Relabelling historical device, selftest or local-only delivery evidence as a new result
non_goals:
  - Product implementation, fresh device operations, new tooling or unrelated cleanup
plan_ref: docs/DEVOPS-WORKFLOW.md
diagnosis: The independently verified feature merge requires narrowly scoped archive and documentation closeout.
acceptance:
  - "A1 the complete original card is preserved except merged status and one appended receipt identifying actual PR286 reviewed head and full merge"
  - "A2 exactly the seven physical paths change, only the original spike enters archive, and every previous archive index row remains identical"
  - "A3 CLAUDE, the existing TASK-BOARD spike row and report identify actual PR286/full merge; report also identifies the reviewed head and preserves historical/device evidence boundaries"
  - "A4 the management card carries the intended merged projection in its own PR without claiming its future review, CI or merge, and archive/card validators pass"
dod_command: $ErrorActionPreference='Stop'; $b='6ad05ec40b6bcfc7a1831cc36a1e71f856d335fb'; $head='a8cdd4d691c7860db7a9330e2454478e426da35f'; $pr='PR '+[char]35+'286'; $own='specs/tasks/T1-SPIKE-PLATFORM-R5.md'; $live='specs/tasks/T1-SPIKE-PLATFORM.md'; $arc='specs/archive/tasks/T1-SPIKE-PLATFORM.md'; $idx='specs/archive/cards-index.md'; $docs=@('CLAUDE.md','docs/TASK-BOARD.md','docs/spike/PLATFORM-SPIKE.md'); $expected=@($own,$live,$arc,$idx)+$docs; $changed=@(git diff --name-only --no-renames $b --); if($LASTEXITCODE){throw 'diff failed'}; $untracked=@(git ls-files --others --exclude-standard); if($LASTEXITCODE){throw 'untracked scan failed'}; if(@(Compare-Object ($expected|Sort-Object) (@($changed+$untracked)|Sort-Object -Unique) -CaseSensitive).Count){throw 'seven-path scope mismatch'}; if(Test-Path -LiteralPath $live){throw 'live card remains'}; $original=@(git show "${b}:$live"); if($LASTEXITCODE){throw 'original card unavailable'}; $original=($original -join "`n")+"`n"; $archived=[IO.File]::ReadAllText((Join-Path (Get-Location) $arc)).Replace("`r`n","`n"); $marker="`n"+[char]35+[char]35+" Remote feature delivery receipt`n"; $parts=$archived.Split(@($marker),[StringSplitOptions]::None); if($parts.Count -ne 2){throw 'receipt marker must occur once'}; $expectedOriginal=[regex]::Replace($original,'(?m)^status: (todo|in-progress)$','status: merged'); if($parts[0] -cne $expectedOriginal){throw 'original card changed beyond status and receipt'}; foreach($required in @($pr,$head,$b)){if(-not $parts[1].Contains($required)){throw 'incomplete feature receipt'}}; foreach($d in $docs){$t=[IO.File]::ReadAllText((Join-Path (Get-Location) $d)); if(-not $t.Contains($pr) -or -not $t.Contains($b)){throw "missing real PR/full merge: $d"}}; $report=[IO.File]::ReadAllText((Join-Path (Get-Location) 'docs/spike/PLATFORM-SPIKE.md')); if(-not $report.Contains($head)){throw 'report lacks reviewed head'}; $prior=@(git ls-tree -r --name-only $b -- specs/archive/tasks); if($LASTEXITCODE){throw 'archive baseline unavailable'}; $now=@(git ls-files --cached --others --exclude-standard specs/archive/tasks); if($LASTEXITCODE){throw 'archive current scan failed'}; if(@(Compare-Object (@($prior+$arc)|Sort-Object -Unique) ($now|Sort-Object -Unique) -CaseSensitive).Count){throw 'unexpected archive membership'}; $oldIndex=@(git show "${b}:$idx"); if($LASTEXITCODE){throw 'index base unavailable'}; $oldRows=@($oldIndex|Where-Object{$_ -cmatch '^[|] T[0-9]+-'}); $newRows=@(Get-Content -LiteralPath $idx|Where-Object{$_ -cmatch '^[|] T[0-9]+-'}); $target=@($newRows|Where-Object{$_ -cmatch '^[|] T1-SPIKE-PLATFORM [|] merged [|]'}); if($target.Count -ne 1){throw 'missing or duplicate target index row'}; $rest=@($newRows|Where-Object{$_ -cnotmatch '^[|] T1-SPIKE-PLATFORM [|]'}); if(($oldRows -join "`n") -cne ($rest -join "`n")){throw 'other index rows changed'}; $metadataHashes=@{'CLAUDE.md'='5FA802731F45DF522BB828C0CC087D05707500A85DBD095AF8618AF47C0D8A23';'docs/TASK-BOARD.md'='F77C405BE8B8AABE4251CB217A7061E0FBB54FE16D1CF3B8D7F6A0DB73F78D3D';'docs/spike/PLATFORM-SPIKE.md'='E9059D7C3B3DA88C4589C7AEE91D63FC65C389EAE4DF2CF348AE317B1908B8EE'}; foreach($p in $metadataHashes.Keys){$text=[IO.File]::ReadAllText((Join-Path (Get-Location) $p)).Replace("`r`n","`n"); $actualHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($text))); if($actualHash -cne $metadataHashes[$p]){throw "metadata content mismatch: $p"}}; $self=[IO.File]::ReadAllText((Join-Path (Get-Location) $own)).Replace("`r`n","`n"); $self=[regex]::Replace($self,'(?m)^dod_command: .*',''); if($self -cnotmatch '(?m)^status: merged$' -or -not $self.Contains('post-merge projection; no future review, CI or merge claim')){throw 'management projection missing'}; pwsh -NoProfile -File scripts/archive.ps1 -CheckCardsIndex; if($LASTEXITCODE){throw 'archive projection failed'}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T1-SPIKE-PLATFORM-R5; if($LASTEXITCODE){throw 'management schema failed'}; 'SPIKE-R5-METADATA-PASS'
dod_exit: 0
dod_assert: seven physical paths only; original card preserved modulo status and one exact receipt; archive membership/index preserved; real feature identities in all three documents; valid management post-merge projection
review_gate: codex {verdict:pass}
hygiene: metadata-only SkipRed, explicitly recorded by normal ship; behavioral metadata assertions plus official archive/card validators, with no runtime-test claim
doc_sync: this management card remains live with merged post-merge projection; archive only the original SPIKE and preserve all historical evidence
---

# Platform spike remote closeout

Administrative base: 6ad05ec40b6bcfc7a1831cc36a1e71f856d335fb. Feature: PR #286, reviewed head a8cdd4d691c7860db7a9330e2454478e426da35f. Feature R3 first round PASS with empty reasons; exact-head CI run 34298102514 succeeded, native ship and official cleanup exited 0, and the feature worktree is absent. Reviewed tree fead018c159bdede58f8501e994e9ff6c775baf1 equals the actual merged tree. These are feature facts, not future management-PR claims.

Delivery status semantics: post-merge projection; no future review, CI or merge claim. The card begins todo for registration/start and is changed to merged together with its final metadata outputs before ship. Its own future merge SHA is not inserted. Formal R3 remains mandatory, and no review-counter reset is granted by this card.

R5.5: the T24 backup-name collision is being recorded separately; this seven-path batch does not modify the lesson ledger. Existing source/evidence binding lessons remain applicable.

Receipt preservation: formal review JSON, RED and T35 are retained in ignored spike-receipts. The attempted T24 backup was overwritten by T35 under the same filename, and official cleanup consumed the original T24. Its minting and successful CAS deletion are evidenced by native ship/cleanup logs; no preserved original T24 is claimed or reconstructed.

The three document hashes supplement the structural scope, original-card, receipt and index checks: they pin the reviewed SPIKE-only edits and unchanged historical report evidence; they do not substitute for those behavior checks.
