---
id: T0-RECEIPT-ARCHIVE-LESSON
title: Record receipt namespace collision and source-complete preservation rule
status: merged
depends_on: [T1-SPIKE-PLATFORM-R5]
branch: T0-RECEIPT-ARCHIVE-LESSON
worktree: C:\wt\T0-RECEIPT-ARCHIVE-LESSON
allow_paths:
  - specs/tasks/T0-RECEIPT-ARCHIVE-LESSON.md
  - docs/lessons/LEDGER.md
forbid:
  - Changing existing ledger bytes, allocating an ID manually, or adding multiple lessons
  - Modifying scripts, other cards, CLAUDE, theme lessons, cold archive, review counters or budgets
  - Claiming the overwritten T24 original was recovered or an automated backup guard exists
non_goals:
  - Receipt tooling repairs, promotion, recurrence bumps, archive migration or product changes
plan_ref: docs/LESSONS.md
acceptance:
  - "A1 every original ledger byte is preserved as an identical prefix, with exactly one appended lesson allocated by official add"
  - "A2 the new entry is a major, ledger-tier, recurrence-one pitfall describing namespace collision, source-first enumeration, unique destinations, pairwise verification and honest missing-original handling"
  - "A3 the complete diff changes exactly the two allowed paths and the management card has explicit post-merge projection semantics without future review or merge claims"
  - "A4 official lesson/card checks pass; original-ledger mutation, second append, severity downgrade and missing preservation rule each fail the DoD"
dod_command: $ErrorActionPreference='Stop'; $b='1747a4de20e7e62c7205d83feb2a456ee242976e'; $count=359950; $prefixSha='B419A1BEAC5A93C753C24AD210F9D87A3D46FABEA75395F2AD374AE1E4ED21AD'; if($b -cnotmatch '^[0-9a-f]{40}$' -or $prefixSha -cnotmatch '^[A-F0-9]{64}$' -or $count -le 0){throw 'baseline not bound'}; $own='specs/tasks/T0-RECEIPT-ARCHIVE-LESSON.md'; $ledger='docs/lessons/LEDGER.md'; $changed=@(git diff --name-only --no-renames $b --); if($LASTEXITCODE){throw 'scope diff failed'}; $untracked=@(git ls-files --others --exclude-standard); if($LASTEXITCODE){throw 'scope scan failed'}; if(@(Compare-Object (@($own,$ledger)|Sort-Object) (@($changed+$untracked)|Sort-Object -Unique) -CaseSensitive).Count){throw 'two-path scope mismatch'}; $bytes=[IO.File]::ReadAllBytes((Join-Path (Get-Location) $ledger)); if($bytes.Length -le $count){throw 'lesson not appended'}; $prefix=[byte[]]::new($count); [Array]::Copy($bytes,0,$prefix,0,$count); $sha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($prefix)); if($sha -cne $prefixSha){throw 'original ledger bytes changed'}; $utf8=[Text.UTF8Encoding]::new($false,$true); $tail=$utf8.GetString($bytes,$count,$bytes.Length-$count).Replace("`r`n","`n"); $lines=@($tail.Trim([char]10).Split([char]10)); if($lines.Count -ne 7){throw 'exactly one seven-line lesson required'}; if($lines[0] -cne '## L327'){throw 'official assigned id mismatch'}; $suffixHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($tail))); if($suffixHash -cne 'C027F9E6A00FFCD7FC1D5F7415769E44E5DB8F4C5B73548723E1B934CB741EB6'){throw 'official generated suffix mismatch'}; if($lines[1] -cnotmatch '^- date: [0-9]{4}-[0-9]{2}-[0-9]{2} ｜ tags: task-loop,evidence,receipts ｜ tier: ledger ｜ kind: pitfall ｜ severity: major ｜ recurrence: 1$'){throw 'major pitfall metadata mismatch'}; if($lines[2] -cne '- symptom: After PR286, flattening scaffold-merged and scaffold-shipped receipts into one directory let T35 overwrite the archived T24; official cleanup then consumed the original T24.'){throw 'lesson field mismatch 2'}; if($lines[3] -cne '- root_cause: Destination identity used basename instead of source namespace plus basename, and a destination-only manifest was produced after the overwrite.'){throw 'lesson field mismatch 3'}; if($lines[4] -cne '- rule: Preserve scaffold-merged/<id>, scaffold-shipped/<id> and worktree-review/<file> namespaces. Before cleanup enumerate required source paths, copy to unique destinations, then verify source/destination counts and exact bytes or SHA for every pair. A destination-only manifest does not prove completeness. Label missing originals missing; never reconstruct them as original evidence.'){throw 'lesson field mismatch 4'}; if($lines[5] -cne '- enforced_by: none（receipt backup completeness is an operator check; no automated archive guard is claimed）'){throw 'lesson field mismatch 5'}; if($lines[6] -cne '- refs: PR286; PR287; _local/routine-remote-recovery/spike-feature-closeout/manifest.json'){throw 'lesson field mismatch 6'}; $self=[IO.File]::ReadAllText((Join-Path (Get-Location) $own)).Replace("`r`n","`n"); $self=[regex]::Replace($self,'(?m)^dod_command: .*',''); if($self -cnotmatch '(?m)^status: merged$' -or -not $self.Contains('post-merge projection; no future review, CI or merge claim')){throw 'management projection missing'}; pwsh -NoProfile -File scripts/lessons.ps1 check; if($LASTEXITCODE){throw 'lesson contract failed'}; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-RECEIPT-ARCHIVE-LESSON; if($LASTEXITCODE){throw 'card contract failed'}; 'RECEIPT-LESSON-METADATA-PASS'
dod_exit: 0
dod_assert: original ledger byte prefix unchanged; exactly one approved major namespace-collision entry; two-path scope and explicit management projection; official lesson/card checks pass
review_gate: codex {verdict:pass}
hygiene: metadata-only explicit SkipRed; independent single-defect negative checks, no runtime or full selftest claim
doc_sync: this card remains live as merged post-merge projection in its own PR; no future self merge OID is written
---

# Receipt namespace preservation lesson

Delivery status semantics: post-merge projection; no future review, CI or merge claim. The bootstrap begins todo and final metadata output changes it to merged before normal ship. Formal R3 and candidate CI remain required. No reset allowance is granted here.

PR286 and PR287 are already remotely merged. The source manifest records missingOriginalT24: T35 overwrote its attempted flat backup, while normal cleanup consumed the original T24. Native logs retain minting/CAS-deletion evidence. This card records the general prevention rule without pretending the original receipt survives.

The source-first prefix proof is bound BEFORE lessons add: record the pristine worktree ledger length and SHA256 after checking it matches the immutable administrative-base checkout. Fill those values and the real administrative base in DoD before activation. No lesson ID is preallocated in this card; official add assigns it from a real cross-repository snapshot under the coordinated ledger window.

ROOT-approved allocation route: use the unchanged official lessons.ps1 add with -RepoRoot on a new ignored snapshot containing the complete real original ledger and any existing original cold ledger, after comparing all available remote/control IDs. Preserve original ledger bytes and snapshots; project only the officially generated new entry suffix into the remote worktree ledger. The operator does not choose or remap the ID. No local historical entries are published. Stop if source bytes or known IDs change.



Official allocation result: L327 was generated by unmodified lessons.ps1 add on the full real snapshot. Only that generated suffix is projected into this remote ledger; all prior local-only history stays outside this diff. The DoD pins its assigned ID and normalized suffix SHA-256 c027f9e6a00ffcd7fc1d5f7415769e44e5db8f4c5b73548723e1b934cb741eb6. Source checks before and after add matched; no original ledger or known-ID set was changed.

Candidate correction: the first unpublished official suffix used noncanonical ASCII none parentheses and failed lessons check. The approved correction regenerated this same logical L327 candidate through official add on a second fresh complete snapshot with canonical fullwidth parentheses. Both original generated suffixes and native outputs are preserved; no second lesson or historical ID reuse is claimed.

Allocation occurred at administrative base 1ee5df140804f7f71668f101cc04088da02d9cc7. The review/DoD scope base is now 1747a4de20e7e62c7205d83feb2a456ee242976e after a normal fast-forward; both bases contain identical ledger blob 6f8cde96e0e62fec0d283c9b4503a26a86f5d591. This synchronization does not rerun allocation or change the generated entry.
