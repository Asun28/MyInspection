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
dod_command: $o = (& pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[POST-MERGE-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $ast = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts/post-merge.ps1').Path, [ref]$null, [ref]$null); $vs = @($ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -ceq 'Command' } | ForEach-Object { $_.Attributes | Where-Object { $_.TypeName.Name -ceq 'ValidateSet' } | ForEach-Object { $_.PositionalArguments.Value } }); foreach ($c in 'r5', 'prune', 'audit', 'retire') { if ($vs -cnotcontains $c) { Write-Host "[DOD-FAIL] subcommand $c missing"; exit 1 } }; foreach ($f in '.claude/skills/task-loop/SKILL.md', 'docs/DEVOPS-WORKFLOW.md') { $t = Get-Content -Raw -LiteralPath $f; foreach ($k in 'post-merge.ps1 audit', 'post-merge.ps1 retire') { if (-not $t.Contains($k)) { Write-Host "[DOD-FAIL] $f lacks $k"; exit 1 } } }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The SelfCheck passes against the production functions, including the A1 and A3 cases; the Command parameter's ValidateSet holds r5, prune, audit and retire; the skill and DEVOPS-WORKFLOW.md name both new subcommands; prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] subcommand audit missing.
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

Runs on 2026-09-25/26 with the candidate `scripts/post-merge.ps1` in the card worktree, SHA-256 `B79E55439750EA3D3693C9A64D7F6A301CC07C70D29B374A44AF3FAA5CA4BF76`, except the first audit run, which used an earlier version.

- **Live audit.** Earlier version, origin/master `f1d3273a`: `[CARD-DRIFT-R5-MISSING] T0-CLAUDE-MD-L360-TD190 todo #411`, `[CARD-DRIFT-COUNT] 1`, exit 1. #411 had merged minutes earlier in another session, so this card reported it and left it alone. The SHA above, origin/master `6959caf9`, run from a directory outside the repo with `GH_REPO=cli/cli`: `[CARD-DRIFT-NONE]`, exit 0, and `git for-each-ref`, `FETCH_HEAD` and `packed-refs` were unchanged. In a throwaway clone whose `origin/master` was set 5 commits behind the remote, audit left that ref at the old commit and created no `FETCH_HEAD`. That audit output cannot show the gh pin, because origin has no drift to find; the pin is shown by `Set-PostMergeGhRepo` turning `GH_REPO=cli/cli` into `github.com/asun28/myinspection`, after which `gh pr list` returned a MyInspection PR.
- **The measurement above, replayed.** The seven R5-MISSING cards had been settled by other sessions since `0cf55f83`. `Get-PostMergeCardDrift` at the SHA above, on the cards of `0cf55f83` with the PR list of 2026-09-26, reports those seven, the two CLOSED cards, and `T0-POST-MERGE-R5-GUARDS` (#398) and `T1-LOCAL-DATA-SECURITY` (#394), whose PRs merged after that measurement.
- **retire, live.** Refused with `[POST-MERGE-RETIRE-REFUSED]`, exit 1, leaving no branch or worktree: a merged card (`T0-POST-MERGE-R5-GUARDS`), an open PR (#321), an unknown successor (`T0-NOPE`). `retire -DryRun` on `T0-POST-MERGE-LESSONS` built the A2 shape (status merged, `superseded_by` after it, an appended section, the card's board row), passed check-cards and check-secrets, and pushed nothing. `r5 -DryRun` on the same card still changes CLAUDE.md, the card and the row; `r5 -SupersededBy` stops with `[POST-MERGE-INPUT]`.
- **R4.** SelfCheck 115 cases, baseline pass. 26 single-statement mutants, 26 killed by the case named below. The runner first rewrote the unmutated file through its own write path and got identical bytes and a pass; a mutant that failed to parse would have counted as void; 26 executed of 26 declared; the file was restored to the SHA above.

| Mutant | Case that failed |
|---|---|
| count an OPEN PR as absent | audit: an open PR suppresses both kinds |
| ignore superseded_by | audit: superseded_by suppresses both kinds |
| report NONE when gh fails | audit: a gh failure is ERROR, never NONE |
| drop the superseding-card existence check | retire: refuses an unknown successor |
| let retire's judge accept a CLAUDE.md change | retire judge: a CLAUDE.md change |
| insert superseded_by without setting the status | retire: status merged, one superseded_by after it, body untouched |
| count a fork PR | audit: a PR from a fork does not count |
| compare the head branch without case | audit: a PR whose head is another card id does not count |
| judge a merged card; compare merged by culture | audit: a merged card has no drift; audit: merged is compared ordinally |
| accept an empty card list; print an empty status | audit: no card read is ERROR; audit: a card without a status line |
| retire ignores an open PR / a merged card / superseded_by | retire: refuses an open PR / a merged card / a card with superseded_by |
| treat an empty superseded_by as absent | retire: refuses an empty superseded_by line |
| retire accepts the card as its own successor / a card not on the base | retire: refuses the card as its own successor / a card not in specs/tasks/ |
| retire judge skips the card text / the board rule | retire judge: a further front-matter edit / another board row |
| read front-matter fields past the closing `---` | audit: a merged PR on a todo card is R5-MISSING |
| take the oldest PR; drop the count line | audit: closed PRs alone are CLOSED; audit: drift lines end with the count, exit 1 |
| accept an origin off github.com; echo the origin URL | gh repo: an origin off github.com is refused without echoing it |
| drop the host from GH_REPO | gh repo: an origin with a token in it gives host/owner/repo |