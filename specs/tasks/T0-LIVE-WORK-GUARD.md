---
id: T0-LIVE-WORK-GUARD
title: Show each session the worktrees, branches and handoff files other sessions hold (read-only live-work probe and SessionStart summary)
status: merged
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/live-work.ps1
  - .claude/settings.json
  - specs/tasks/T0-LIVE-WORK-GUARD.md
forbid:
  - Fetching, pushing or any network call in the probe
  - Writing to any worktree, branch, ref or file from the probe (every git call it makes passes --no-optional-locks)
  - Changing check-cards.ps1, review.ps1, post-merge.ps1, lessons.ps1, _scope.ps1 or the scope and budget gates
non_goals:
  - A claim or lease registry keyed by session id; who holds a worktree is read from the worktree's own state
  - One set of handoff files per session (T0-MAIN-CHECKOUT-READONLY keeps one set per checkout); this card only lists _local/handoff-*.md
  - Guarding the edit tools (T0-MAIN-CHECKOUT-READONLY)
  - The AIDLC project's multi-session coordinator (D:\Projects\AIDLC, plan v5 Package S); the R5 note points it at this probe
  - The task.ps1 start stop, the selftest gate 15 fixture and the session rules in CLAUDE.md, the task-loop skill and docs/HANDOFF.md (T0-LIVE-WORK-START)
  - The dirty-worktree ask hook (T0-DIRTY-WORKTREE-HOOK)
acceptance:
  - "A1 scripts/live-work.ps1 lists the work held outside the calling worktree, without writing or fetching: for every registered worktree other than the caller's (the main checkout included), its path, its branch or 'detached', the paths git status --porcelain reports (untracked included), the paths changed by commits on its HEAD that are not on origin/<Base> (default master), and the newest last-write time among those paths that exist, or its HEAD commit time when none of them exists. It prints one ASCII line [LIVE-WORK] path=<p> branch=<b> uncommitted=<n> unmerged=<n> newest=<ISO-8601> per worktree that has any, collapses worktrees whose newest change is older than -SinceHours (default 48) into one [LIVE-WORK-STALE] count=<n> line, and prints [LIVE-WORK-NONE] only when it probed every registered worktree and none holds work. It also prints [LIVE-WORK-HANDOFF] file=<f> task=<id> updated=<value> for the main checkout's progress.md HANDOFF block and for each _local/handoff-*.md. A registered worktree whose directory is missing, or that it has not finished probing when -BudgetSec (default 10) runs out, is printed as [LIVE-WORK-UNKNOWN] path=<p> with the reason. Every git call runs under that deadline and is killed when it passes it, so one slow worktree cannot hold the summary past the budget. It exits 0"
  - "A2 live-work.ps1 -TaskId <id> prints [LIVE-WORK-OVERLAP] for each other worktree whose uncommitted or unmerged paths fall under the card's allow_paths (read from origin/<Base>:specs/tasks/<id>.md, else from the caller's tree, matched the way the scope gate matches) and whose newest change (A1) is within -SinceHours, for a worktree whose branch is <id>, and for a local branch <id> or r5-<id> or a remote-tracking origin/<id> or origin/r5-<id> whose tip is not on origin/<Base>. A worktree whose paths overlap but whose newest change is older gets [LIVE-WORK-OVERLAP-STALE] instead, which does not count. It exits 3 when it printed any [LIVE-WORK-OVERLAP], 0 when none, and 2 when a probe fails: an unreadable card, a git error, a registered worktree whose directory is missing, or a git call still running when -BudgetSec (default 120 with -TaskId) runs out"
  - "A3 .claude/settings.json runs live-work.ps1 (the A1 summary) as a SessionStart hook with a 15-second timeout, so every session sees the worktrees, branches and handoff files other sessions hold before it acts"
  - "A4 live-work.ps1 -SelfCheck builds temporary Git fixtures (a bare origin, a main checkout and linked worktrees) and covers: [LIVE-WORK-NONE] when nothing is held; a [LIVE-WORK] line for an uncommitted change, for a commit not on the base in a detached worktree (branch=detached unmerged=1), and for the main checkout; [LIVE-WORK-STALE]; both [LIVE-WORK-HANDOFF] lines; a deletion-only worktree getting its HEAD commit time; [LIVE-WORK-UNKNOWN] and no [LIVE-WORK-NONE] for a worktree whose directory was removed and for one whose git status a slow core.fsmonitor hook holds past -BudgetSec, the second run ending within -BudgetSec plus 10 seconds; A2 exit 3 for a worktree changing the card's allow_paths, for a clean worktree on a branch named after the card, for a local branch r5-<id> and for a remote-tracking origin/<id> whose tips are not on the base, and for a card read from the caller's tree when the base lacks it; exit 0 for a card nothing overlaps and for an overlap older than -SinceHours ([LIVE-WORK-OVERLAP-STALE]); exit 2 for a card that cannot be read and for a missing worktree directory. A failing fixture git command reports git's own output and the fixture root. It prints [LIVE-WORK-SELF-CHECK-PASS]"
  - "A5 The Tier-S full selftest run passes on the shipped candidate"
dod_command: $p = 'scripts/live-work.ps1'; if (-not (Test-Path -LiteralPath $p)) { Write-Host "[DOD-FAIL] missing $p"; exit 1 }; $o = (& pwsh -NoProfile -File $p -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[LIVE-WORK-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $s = Get-Content -Raw -LiteralPath '.claude/settings.json' | ConvertFrom-Json; $ss = @($s.hooks.SessionStart.hooks | Where-Object { [string]$_.command -match 'live-work\.ps1' }); if ($ss.Count -ne 1 -or $ss[0].timeout -ne 15) { Write-Host '[DOD-FAIL] SessionStart registration'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes against the production probe and real Git fixtures (A1, A2, A4), and the SessionStart summary is registered exactly once with a 15-second timeout (A3); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing scripts/live-work.ps1.
review_gate: codex {verdict:pass}
budget: 450
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the allow_paths overlap test, drop the -SinceHours test of an overlap, drop the branch-name check, map exit 3 to 0, drop the ref arm, drop the HEAD-time fallback, drop the unknown count from the [LIVE-WORK-NONE] test, skip missing worktree directories again, drop the per-call deadline, drop the caller's-tree card fallback; every one must make the self-check fail.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry, and leave a note for the AIDLC project (plan v5 Package S) that MyInspection has live-work.ps1.
---

# T0-LIVE-WORK-GUARD

Opened by user request on 2026-09-25, after one session's uncommitted work was discarded by another session
working the same card. The user asked that every session, whether driven by the task loop or the AIDLC loop,
check the work other sessions hold, the existing handoff documents and the live sessions before it acts.

## What happened (2026-09-25, times NZST)

1. About 19:40 session A started `T0-POST-MERGE-R5-GUARDS` (`task.ps1 -Phase start`, worktree
   `C:\wt\T0-POST-MERGE-R5-GUARDS`) and implemented all three of its fixes, uncommitted: DoD 79/79, 14/14 mutants,
   a real `-DryRun`, and a tier-1 selftest pass that finished about 21:10.
2. At 20:55 PR #391, from another session, split that card into three on origin without knowing A held its
   worktree. The first two split cards then shipped (#393, #396); they edit the same file,
   `scripts/post-merge.ps1`.
3. About 22:18 a session B recorded starting the narrowed `T0-POST-MERGE-R5-GUARDS` at master `8146d794`.
   Start refuses an existing worktree (task.ps1:1043), yet A's worktree reflog shows its branch moved to
   `8146d794` at 22:17:57 and `reset: moving to HEAD` at 22:17:58. A's uncommitted work was gone, and A found
   out minutes later, when the patch it saved for the split cards came out empty.

Nothing told either session about the other. A's SessionStart hook printed the main checkout's progress.md
HANDOFF, which belonged to a third task (L273). `[SHIP-CONCURRENT-SESSION]` only runs at ship, and only looks
at the main checkout and at worktree directory names. L218 (check for an active writer before touching a
worktree) and L273 (progress.md may belong to another session) exist only in the ledger.

## Design

1. **See it (A1, A3).** A read-only probe lists what other worktrees hold, uncommitted and unmerged, with the
   newest change time, plus the handoff files, and every session gets that list at start. Worktrees nobody has
   touched for two days collapse into one count so the list stays short. A worktree the probe cannot read, or
   cannot finish reading in time, is reported as unknown, never as holding nothing.
2. **Check one card (A2).** The `-TaskId` mode answers whether another worktree or branch holds a card's paths
   or name. The paths come from the base card, the same as the scope gate reads them. Only a worktree changed
   within the last 48 hours counts: measured on 2026-09-25, 14 worktrees overlapped this card's original paths
   and about 10 of them had not changed since August or early September (user ruling 2026-09-25).

## Split (user ruling 2026-10-07)

This card first also carried the `task.ps1 start` stop, the gate 15 fixture change, the session rules in
CLAUDE.md, the task-loop skill and docs/HANDOFF.md, and the dirty-worktree hook, in one 450-line diff (PR #447).
Codex R3 round 1 on PR #447 blocked: registered worktrees whose directory is missing were dropped silently, so
the summary said `[LIVE-WORK-NONE]` and `-TaskId` exited 0; the time budget was checked only between worktrees,
so one slow `git status` could outrun the 15-second SessionStart timeout; and the self-check left most named
surfaces untested. The fixes did not fit the budget, so on 2026-10-07 the user chose three cards, shipped in
this order:

1. `T0-LIVE-WORK-GUARD` (this card, PR #447): the probe and its SessionStart summary, with the R3 fixes.
2. `T0-LIVE-WORK-START`: the `task.ps1 start` stop, the gate 15 fixture and the session rules.
3. `T0-DIRTY-WORKTREE-HOOK`: the PreToolUse hook that asks before a discarding git command.

Implementation evidence from the combined candidate (R4 of the probe mutants, the A3 real start run, Tier-S
runs) stays in PR #447's history and in the records of the cards that now own each part.

## Implementation record (2026-10-07)

Delivered through the AIDLC loop (goal `g-20260925112601-012c7e`, revision 1 after the split, no `aidlc init`);
task-loop and `task.ps1` do the work under the goal's card lease.

**Changes after R3 round 1 on PR #447.** A registered worktree whose directory is missing is kept: the summary
prints `[LIVE-WORK-UNKNOWN] … has no directory …` and `-TaskId` exits 2. `[LIVE-WORK-NONE]` is printed only when no
worktree was unknown. Every git call the probe makes goes through `Invoke-LiveWorkGitRaw`, which starts git as a
process, waits for it at most until the run's deadline (`-BudgetSec`: 10 s for the summary, 120 s with `-TaskId`)
and then kills it with its process tree; reading its output once it has exited has no separate bound. A ref that
does not exist (`rev-parse --verify --quiet` exits 1) is skipped, any other exit fails the probe. The self-check's
fixture wrapper reports git's own output and the directory it ran in, and a fixture failure keeps the fixture root
and names it. The self-check went from 12 probe cases to 19, plus one case for the fixture wrapper.

**A slow worktree, for real.** A per-worktree `core.fsmonitor` hook (`extensions.worktreeConfig`) that sleeps slows
`git status` in that one worktree only: measured 20.2 s there against 0.1 s in the main checkout. The self-check
uses a 15 s sleep against a 6 s budget, so the calls before that status have room on a loaded machine. On Windows
the hook's `sleep` can outlive the killed git; it ends by itself: a check after one self-check run found the
leftover `sleep` already gone and no fixture directory left.

**Measured on this machine (40 registered worktrees).** The summary took 7.8 s with no `[LIVE-WORK-UNKNOWN]` line.

**R4.** Single-statement edits of `scripts/live-work.ps1` (SHA-256
`E7814B537557F39A64D398795B8BDADDDA4D6D6A6FED8EDC95E4307A5AF64A81`), the file restored after each and checked
against that hash; the unmutated self-check (20 cases) passed first. A kill is exit 1 with
`[LIVE-WORK-SELF-CHECK-FAIL]` and the named case among the failures. 10 of 10 killed. In the first batch M9
survived: the slow-worktree case accepted any unknown line, and the next git call also found the budget spent. The
case now requires the kill's own reason, and the batch was rerun on the final bytes.

| id | single-statement change | killed by |
|---|---|---|
| M1 | overlap test `if ($inside.Count -or $named)` -> `if ($named)` | overlap: a worktree changing the card's allow_paths exits 3 |
| M2 | `$h.Newest -ge $cutoff` -> `$true` | overlap: an overlap older than -SinceHours is [LIVE-WORK-OVERLAP-STALE] and exits 0 |
| M3 | branch-name test -> `$named = $false` | overlap: a clean worktree on a branch named after the card exits 3 |
| M4 | `exit 3` on an overlap -> `exit 0` | overlap: a worktree changing the card's allow_paths exits 3 |
| M5 | ref arm `if ($anc -eq 1)` -> `if ($false)` | overlap: a local branch r5-<id> whose tip is not on the base exits 3 |
| M6 | HEAD-time fallback condition -> `$false` | summary: a worktree holding only a deletion gets its HEAD commit time as newest |
| M7 | `-and -not $unknown` dropped from the [LIVE-WORK-NONE] test | summary: a registered worktree whose directory is missing is [LIVE-WORK-UNKNOWN] and no [LIVE-WORK-NONE] follows |
| M8 | missing directories skipped again (`-and (Test-Path …)` on the bare test) | overlap: a registered worktree whose directory is missing exits 2 |
| M9 | per-call wait `if (-not $p.WaitForExit(…))` -> `if ($false)` | summary: a git status a slow fsmonitor hook holds past -BudgetSec is killed at the deadline … |
| M10 | caller's-tree card fallback -> `elseif ($false)` | overlap: a card the base lacks is read from the caller's tree |

**Tier-S full selftest** (`-Parallel`, this worktree): `fc51968f` passed all 17 gates in five shards (1221 s).
Later commits change only this record and other cards.

## R5 (2026-10-07)

Merged by PR #447 (squash `372ebc34`, reviewed head `3264bfd3`, CI `37593283167` attempt 1). Codex R3 round 2 passed
both axes with zero findings. Round 1 blocked the earlier combined candidate, which led to the split above.

**Note for the AIDLC project (plan v5 Package S).** MyInspection now has `scripts/live-work.ps1`. Run with no
arguments, it prints what other worktrees hold (`[LIVE-WORK]`, `[LIVE-WORK-STALE]`, `[LIVE-WORK-UNKNOWN]`,
`[LIVE-WORK-NONE]`, `[LIVE-WORK-HANDOFF]`) and always exits 0. With `-TaskId <id>` it exits 3 when another worktree
or branch holds the card's paths or name, 0 when none does, and 2 when it cannot tell. A multi-session coordinator
can call it before it dispatches a card instead of keeping its own registry of who holds a worktree.

**Known limits, not fixed here.** Reading git's output after git has exited has no separate time bound. Paths
that git status quotes with C escapes keep the escapes. The SessionStart hook has about 5 s over the 10 s budget
for pwsh startup.
