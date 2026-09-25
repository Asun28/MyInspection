---
id: T0-POST-MERGE-CARD-DRIFT
title: Close the loop on the card itself after a merge or a closed PR - a drift audit, a retire path, and a task-loop rule that the turn runs from ship through R5
status: todo
depends_on: [T0-POST-MERGE-R5-GUARDS]
parallelizable_with: []
branch: T0-POST-MERGE-CARD-DRIFT
worktree: C:\wt\T0-POST-MERGE-CARD-DRIFT
allow_paths:
  - scripts/post-merge.ps1
  - .claude/skills/task-loop/SKILL.md
  - docs/DEVOPS-WORKFLOW.md
  - specs/tasks/T0-POST-MERGE-CARD-DRIFT.md
forbid:
  - Loosening any guard the post-merge cards delivered or widening r5's allowlist
  - audit writing anything (files, refs, branches, PRs), or printing [CARD-DRIFT-NONE] after a git or gh failure
  - retire touching CLAUDE.md, any card other than the one named, or any board row other than that card's own
  - Changing scripts/task.ps1, scripts/triage.ps1, scripts/check-cards.ps1, scripts/archive.ps1 or any existing gate
non_goals:
  - Settling the seven cards the 2026-09-25 measurement reports as R5-MISSING (listed below); several carry pending user rulings on the TASK-BOARD, so each is settled by the user or at this card's R5, not by this code
  - Archiving merged cards; scripts/archive.ps1 owns that, and 49 are due
  - An audit probe in scripts/triage.ps1 (open PRs #294 and #321 both change that file)
  - Making ship run r5 by itself (the post-merge cards keep scripts/task.ps1 unchanged)
acceptance:
  - "A1 post-merge.ps1 audit reads every card in specs/tasks/ of a freshly fetched origin/<base> and every same-repository PR in every state through gh, writes nothing, and prints one line per drift: [CARD-DRIFT-R5-MISSING] <id> <status> #<pr> when the card's status is not merged, it has no superseded_by, a MERGED PR has the card id as its head branch, and no OPEN PR does; [CARD-DRIFT-CLOSED] <id> <status> #<pr> when the status is not merged, there is no superseded_by, no PR with that head is OPEN or MERGED, and a CLOSED one exists. It ends with [CARD-DRIFT-NONE] and exit 0 when nothing drifted, [CARD-DRIFT-COUNT] <n> and exit 1 when something did, and [CARD-DRIFT-ERROR] and exit 2 when git or gh fails"
  - "A2 post-merge.ps1 retire -TaskId <id> -SupersededBy <id> -BoardStatusFile <f> -CardNoteFile <f> stops with [POST-MERGE-RETIRE-REFUSED] before creating anything unless the card is in specs/tasks/ on origin/<base> with a status other than merged and no superseded_by, no OPEN PR has the card id as head, and the superseding id names a card in specs/tasks/ or specs/archive/tasks/ there. It then takes r5's path (fresh worktree from origin/<base>, check-cards, check-secrets, the CI check on the exact head, squash merge with --match-head-commit, prune of its own branch) with its own allowlist: the card file, whose only front-matter change is status becoming merged plus one superseded_by line after it, the rest being an appended section; and the card's row in the main board table (the T0-POST-MERGE-R5-GUARDS rule). Anything else stops with [POST-MERGE-SCOPE] before a push"
  - "A3 post-merge.ps1 -SelfCheck gains cases on injected card texts and PR lists: each A1 drift kind; an OPEN PR suppressing both kinds; superseded_by suppressing both; a merged card; a PR whose head is another card's id; a PR from a fork; a gh failure giving ERROR, never NONE. For retire: the front-matter edit (status and superseded_by set once, a body line reading status: todo untouched, CRLF kept), each refusal condition, and the allowlist judge accepting exactly the A2 shape while rejecting a CLAUDE.md change, a second card, another board row and any further front-matter edit"
  - "A4 The task-loop skill says three things: a merge is where R5 starts, so after ship exits 0 the same turn runs r5, cleanup and R5.5 (a fifth case under 不该停); a card whose PR closes without a merge (split, superseded, retired by the user) is finished with post-merge.ps1 retire and never left at todo (added to 完成条件); and before starting a card the session runs post-merge.ps1 audit, fixes the drift on cards it delivered itself (r5 or retire), and reports the rest to the user"
  - "A5 docs/DEVOPS-WORKFLOW.md names post-merge.ps1 audit and post-merge.ps1 retire where it describes R5"
  - "A6 One live audit run on the shipped candidate is recorded in this card with its lines and exit code, and each R4 single-statement mutant is recorded with the SelfCheck case that failed"
dod_command: $o = (& pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[POST-MERGE-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $ast = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts/post-merge.ps1').Path, [ref]$null, [ref]$null); $vs = @($ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -ceq 'Command' } | ForEach-Object { $_.Attributes | Where-Object { $_.TypeName.Name -ceq 'ValidateSet' } | ForEach-Object { $_.PositionalArguments.Value } }); foreach ($c in 'r5', 'prune', 'audit', 'retire') { if ($vs -cnotcontains $c) { Write-Host "[DOD-FAIL] subcommand $c missing"; exit 1 } }; foreach ($f in '.claude/skills/task-loop/SKILL.md', 'docs/DEVOPS-WORKFLOW.md') { $t = Get-Content -Raw -LiteralPath $f; foreach ($k in 'post-merge.ps1 audit', 'post-merge.ps1 retire') { if (-not $t.Contains($k)) { Write-Host "[DOD-FAIL] $f lacks $k"; exit 1 } } }; $sk = @(Get-Content -LiteralPath '.claude/skills/task-loop/SKILL.md'); if (-not ($sk -join "`n").Contains('**不该停的五种**')) { Write-Host '[DOD-FAIL] A4 the skill does not list five cases under 不该停'; exit 1 }; foreach ($need in @(@('A4 same turn', '`ship`', '`post-merge.ps1 r5`', '`cleanup`', 'R5.5'), @('A4 closed PR', '`post-merge.ps1 retire -TaskId <id> -SupersededBy <id>', '不留在 todo'), @('A4 audit first', '`pwsh -NoProfile -File scripts\post-merge.ps1 audit`', '`[CARD-DRIFT-R5-MISSING]` 走 `post-merge.ps1 r5`', '`[CARD-DRIFT-CLOSED]` 走 `post-merge.ps1 retire`', '报告给用户'))) { $hit = @($sk | Where-Object { $l = $_; @($need | Select-Object -Skip 1 | Where-Object { -not $l.Contains($_) }).Count -eq 0 }); if ($hit.Count -ne 1) { Write-Host "[DOD-FAIL] $($need[0]) - $($hit.Count) skill lines carry all of its anchors, expected 1"; exit 1 } }; $cd = Get-Content -Raw -LiteralPath 'specs/tasks/T0-POST-MERGE-CARD-DRIFT.md'; $i = $cd.IndexOf('## Evidence (A6, R4)'); if ($i -lt 0) { Write-Host '[DOD-FAIL] A6 evidence section missing'; exit 1 }; $ev = $cd.Substring($i); $sha = (Get-FileHash -LiteralPath 'scripts/post-merge.ps1' -Algorithm SHA256).Hash; if (-not $ev.Contains('SHA-256 `' + $sha + '`')) { Write-Host "[DOD-FAIL] A6 evidence is not bound to post-merge.ps1 $sha"; exit 1 }; if ($ev -cnotmatch '\[CARD-DRIFT-(NONE|COUNT)\][^\r\n]*?exit [01]\b') { Write-Host '[DOD-FAIL] A6 no live audit line with its exit code'; exit 1 }; $pm = Get-Content -Raw -LiteralPath 'scripts/post-merge.ps1'; foreach ($m in 'count an OPEN PR as absent', 'ignore superseded_by', 'report NONE when gh fails', 'drop the superseding-card existence check', 'let retire''s judge accept a CLAUDE.md change', 'insert superseded_by without setting the status') { $row = [regex]::Match($ev, '(?m)^\| ' + [regex]::Escape($m) + ' \| (.+?) \|$'); if (-not $row.Success -or $pm -cnotmatch ("(?m)^\s*(Check|Throws) '" + [regex]::Escape($row.Groups[1].Value) + "'")) { Write-Host "[DOD-FAIL] A6 mutant '$m' is not recorded with an existing SelfCheck case"; exit 1 } }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The SelfCheck passes against the production functions and runs audit and retire through their real plumbing against recording stubs (A1-A3); the Command parameter's ValidateSet holds r5, prune, audit and retire; the skill and DEVOPS-WORKFLOW.md name both new subcommands; the skill lists five 不该停 cases and has exactly one line each carrying the same-turn R5 rule, the retire rule of 完成条件 and the audit-first rule (A4); the card's Evidence section names the SHA-256 of scripts/post-merge.ps1, has a live audit line with its exit code, and records each hygiene mutant with a SelfCheck case that exists (A6); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] subcommand audit missing.
review_gate: codex {verdict:pass}
budget: 350
hygiene: R4 runs single-statement mutants against the SelfCheck, each recorded in the card with the case that failed - count an OPEN PR as absent, ignore superseded_by, report NONE when gh fails, drop the superseding-card existence check, let retire's judge accept a CLAUDE.md change, insert superseded_by without setting the status; file restored by SHA-256.
doc_sync: At R5 set status merged through post-merge.ps1 r5, update the TASK-BOARD row, and settle or hand to the user each R5-MISSING card the live audit reports.
---

# T0-POST-MERGE-CARD-DRIFT

Opened by user request on 2026-09-25: after a PR merges, the doc governance still has to be done by hand, and the card
on master has to be updated as well.

`T0-POST-MERGE-DOCS-PR` gave R5 a command (`post-merge.ps1 r5`). What is still missing is making sure it runs, and
covering the cards whose PR never merges.

## Problem (measured 2026-09-25 on origin/master `0cf55f83`, 125 live cards)

- **R5 is skipped.** Seven live cards have a merged PR whose head branch is the card id but still say `status: todo`:
  `T0-PREREVIEW-POLICY-SOURCE` (#314), `T0-PREREVIEW-REMOTE-SCHEMA` (#302), `T0-PREREVIEW-SOURCE-REGISTRATION` (#311),
  `T0-REMOTE-ROUND2-PAGINATION-CLOSURE` (#320), `T0-REMOTE-ROUND3-CARDS` (#313), `T0-SCAFFOLD-CARD-CONTRACT-REPAIR`
  (#275) and `T3-PDF-MEASUREMENT-REQUESTS` (#317). Some of these may be deliberate (the board records pending user
  rulings on the prereview adoption), which is why the audit reports and does not fix.
- **Cards closed without a merge are never retired.** Two live cards had a closed, unmerged PR, no open PR, and still
  said `todo`, although their work had been replaced by merged cards: `T4-DESIGN-SYMBOL-CHROME` (#236, replaced by
  `T4-DESIGN-SYMBOL-CHROME-V2`) and `T0-RECONCILE-LESSONS-PATTERN-FIXTURE` (#144, replaced by
  `T0-RECONCILE-LESSONS-FINAL-FIXTURE`, #145). The PR that registers this card retires both by hand, using the repo's
  convention for a retired card: `status: merged` plus `superseded_by`.
- **Nothing in the task loop asks for either.** The skill's 完成条件 names R5, but a session can end its turn at the
  merge, and no step covers a card whose PR closes unmerged.

## Design

1. **audit** (A1) is read-only and deterministic about failure: a git or gh error is its own exit code, never a clean
   report. It judges by the PR whose head branch is the card id, which is how `task.ps1 ship` names branches.
2. **retire** (A2) reuses r5's worktree, gate, CI, merge and prune path, with a narrower allowlist: no CLAUDE.md entry,
   because a retired card delivered nothing.
3. **The skill** (A4) makes R5 part of the same turn as the merge, adds retire for closed PRs, and runs audit before new
   work, so drift from earlier sessions surfaces instead of piling up.

`depends_on` puts this after `T0-POST-MERGE-R5-GUARDS`, which is in progress and edits the same two files
(`post-merge.ps1` and the task-loop skill). `T0-POST-MERGE-LESSONS` edits them too; whichever of the two starts second
rebases on the other. Estimate: about 280 changed lines (audit about 70, retire about 60 by sharing r5's tail, SelfCheck
cases about 90, skill and workflow text about 15, this card's R5 note about 45).

## Evidence (A6, R4)

All runs on 2026-09-26 except the replay use the candidate `scripts/post-merge.ps1` in the card worktree, SHA-256 `9890F68268FF718DFC3D3DDB9DFF2B8C788CA38823C4165807DE3BE654EDA831`.

- **Live audit.** Origin/master `490999a9`, run from outside the repo with `GH_REPO=cli/cli`: `[CARD-DRIFT-NONE]`, exit 0; refs, `FETCH_HEAD` and `packed-refs` unchanged. A clone whose `origin/master` sat 5 commits behind kept that ref and got no `FETCH_HEAD`.
- **Replay.** `Get-PostMergeCardDrift` (unchanged since it ran at SHA `B79E5543`) on the cards of `0cf55f83` with the PR list of 2026-09-26 reports the seven R5-MISSING and two CLOSED cards above, plus #398 and #394, merged after that measurement.
- **retire and r5, live.** Refused (`[POST-MERGE-RETIRE-REFUSED]`, exit 1, nothing left) for a merged card, an open PR (#321) and an unknown successor. `retire -DryRun` built the A2 shape and passed check-cards and check-secrets; `r5 -DryRun` still changes CLAUDE.md, the card and the row; `r5 -SupersededBy` exits 1.
- **Plumbing in the SelfCheck.** Recording stubs replace `git`, `gh`, `pwsh` and `Assert-PersonalAccount`; the temp repos run without the caller's `GIT_*` variables or git config, and each run starts from `GH_REPO=cli/cli`. Every subcommand runs through `Invoke-PostMergeCommand` and is judged by exit code, printed text and calls: the `dispatch:` and `plumbing:` cases below. It also passes with `GIT_DIR` set to a throwaway repo, which it leaves untouched.
- **R4.** SelfCheck 127 cases, baseline pass. 60 single-statement mutants, 60 killed, each by the case in its row. The runner rewrote the unmutated file through its write path first (identical bytes, pass); a mutant that failed to parse would have been void; 60 executed of 60 declared; file restored to the SHA above.

| Id | Mutant | Case that failed |
|---|---|---|
| M1 | count an OPEN PR as absent | audit: an open PR suppresses both kinds |
| M2 | ignore superseded_by | audit: superseded_by suppresses both kinds |
| M3 | report NONE when gh fails | audit: a gh failure is ERROR, never NONE |
| M4 | drop the superseding-card existence check | retire: refuses an unknown successor |
| M5 | let retire's judge accept a CLAUDE.md change | retire judge: a CLAUDE.md change |
| M6 | insert superseded_by without setting the status | retire: status merged, one superseded_by after it, body untouched |
| M7 | count a fork PR | audit: a PR from a fork does not count |
| M8 | compare the head without case | audit: a PR whose head is another card id does not count |
| M9 | judge a merged card | audit: a merged card has no drift |
| M10 | accept an empty card list | audit: no card read is ERROR |
| M11 | retire ignores an open PR | retire: refuses an open PR |
| M12 | retire ignores a merged card | retire: refuses a merged card |
| M13 | retire ignores superseded_by | retire: refuses a card with superseded_by |
| M14 | retire accepts the card as its own successor | retire: refuses the card as its own successor |
| M15 | retire accepts a card not on the base | retire: refuses a card not in specs/tasks/ |
| M16 | retire judge skips the card text | retire judge: a further front-matter edit |
| M17 | retire judge skips the board rule | retire judge: another board row |
| M18 | read fields past the front matter | audit: a merged PR on a todo card is R5-MISSING |
| M19 | take the oldest PR | audit: closed PRs alone are CLOSED |
| M20 | drop the count line | audit: drift lines end with the count, exit 1 |
| M21 | compare merged by culture | audit: merged is compared ordinally |
| M22 | treat an empty superseded_by as absent | retire: refuses an empty superseded_by line |
| M23 | print an empty status | audit: a card without a status line |
| M24 | accept an origin off github.com | gh repo: an origin off github.com is refused without echoing it |
| M25 | echo the origin URL | gh repo: an origin off github.com is refused without echoing it |
| M26 | drop the host from GH_REPO | gh repo: an origin with a token in it gives host/owner/repo |
| M27 | audit skips the gh pin | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M28 | audit fetch writes refs | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M29 | audit reads the moving ref | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M30 | retire skips its refusals | plumbing: retire exits 1 on an open PR before anything is created |
| M31 | retire skips the gh pin | plumbing: retire runs r5's route to CI, merge and prune |
| M32 | skip the CI wait | plumbing: retire runs r5's route to CI, merge and prune |
| M33 | merge without the head pin | plumbing: retire runs r5's route to CI, merge and prune |
| M34 | skip the prune | plumbing: retire runs r5's route to CI, merge and prune |
| M35 | cut the worktree from HEAD | plumbing: retire runs r5's route to CI, merge and prune |
| M36 | skip the base fetch | plumbing: retire runs r5's route to CI, merge and prune |
| M37 | look up CI runs by the base name | plumbing: retire runs r5's route to CI, merge and prune |
| M38 | audit skips the account check | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M39 | retire skips the account check | plumbing: retire runs r5's route to CI, merge and prune |
| M40 | drop the pre-merge head and base recheck | plumbing: retire exits 1 without merging or pruning when the PR base moved |
| M41 | skip audit inside its arm | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M42 | make the retire arm a no-op | plumbing: retire exits 1 on an open PR before anything is created |
| M43 | exit 0 from audit whatever it found | plumbing: audit exits 1 on drift after exactly its read-only calls, account first, gh pinned |
| M44 | exit 0 after a thrown error | plumbing: r5 with -SupersededBy exits 1 before any call |
| M45 | let r5 take -SupersededBy | plumbing: r5 with -SupersededBy exits 1 before any call |
| M46 | send r5 down the retire path | plumbing: r5 -DryRun builds and checks its change, then stops before any push |
| M47 | make the r5 arm call nothing | plumbing: r5 -DryRun builds and checks its change, then stops before any push |
| M48 | drop the DryRun stop | plumbing: r5 -DryRun builds and checks its change, then stops before any push |
| M49 | drop the scope stop | plumbing: retire stops with [POST-MERGE-SCOPE] before any push when CLAUDE.md changed |
| M50 | give retire the r5 judge | plumbing: retire stops with [POST-MERGE-SCOPE] before any push when CLAUDE.md changed |
| M51 | drop the prune DryRun refusal | plumbing: prune -DryRun exits 1 before any call, and no command exits 2 |
| M52 | exit 0 on an unknown command | plumbing: prune -DryRun exits 1 before any call, and no command exits 2 |
| M53 | read cards without the native wrapper | plumbing: audit exits 0 without drift and 2 when gh or git fails |
| M54 | write a ref during the retire fetch | plumbing: retire runs r5's route to CI, merge and prune |
| M55 | prune skips the gh pin | plumbing: prune checks the account, pins gh and keeps a branch origin lacks |
| M56 | run the gates from the main checkout | plumbing: retire runs r5's route to CI, merge and prune |
| M57 | merge with --admin | plumbing: retire runs r5's route to CI, merge and prune |
| M58 | r5 skips its CLAUDE.md entry | plumbing: r5 -DryRun builds and checks its change, then stops before any push |
| M59 | drop the gate stop | plumbing: retire stops with [POST-MERGE-GATE] before any push when a gate fails |
| M60 | exit 0 whatever the command | dispatch: the script with no subcommand exits 2 |
