---
id: T0-LIVE-WORK-GUARD
title: Show each session the worktrees other sessions hold, stop start from overrunning them, and ask before a git command discards a dirty worktree
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/live-work.ps1
  - .claude/hooks/guard-dirty-worktree.ps1
  - .claude/settings.json
  - scripts/task.ps1
  - .claude/skills/task-loop/SKILL.md
  - docs/HANDOFF.md
  - CLAUDE.md
  - specs/tasks/T0-LIVE-WORK-GUARD.md
sweep: "rg 'L218|L273|活跃写者|他会话|并行会话|other session|another session|live work' over CLAUDE.md, docs/HANDOFF.md, docs/DEVOPS-WORKFLOW.md, .claude/skills/task-loop/SKILL.md, .claude/hooks/*.ps1 and scripts/task.ps1 (2026-09-25, origin/master cf1bd18a). The only hit is task.ps1:1146, the ship-time [SHIP-CONCURRENT-SESSION] notice, which looks at the main checkout and at worktree directory names only; this card leaves it as is. No teaching surface states L218 or L273: CLAUDE.md's execution boundary and the task-loop skill's 前置 get one rule line each, docs/HANDOFF.md gets the per-task handoff rule. docs/DEVOPS-WORKFLOW.md is not edited: it describes phase commands, and T0-MAIN-CHECKOUT-READONLY and T0-POST-MERGE-CARD-DRIFT own its multi-session text."
forbid:
  - Denying or rewriting a git command; the hook only asks the user (permissionDecision ask)
  - Fetching, pushing or any network call in the probe or either hook
  - Writing to any worktree, branch, ref or file from the probe or either hook
  - Changing check-cards.ps1, review.ps1, post-merge.ps1, lessons.ps1, _scope.ps1 or the scope and budget gates
non_goals:
  - A claim or lease registry keyed by session id; who holds a worktree is read from the worktree's own state
  - One set of handoff files per session (T0-MAIN-CHECKOUT-READONLY keeps one set per checkout); this card only lists _local/handoff-*.md
  - Guarding the edit tools (T0-MAIN-CHECKOUT-READONLY)
  - The AIDLC project's multi-session coordinator (D:\Projects\AIDLC, plan v5 Package S); the R5 note points it at this probe
acceptance:
  - "A1 scripts/live-work.ps1 lists the work held outside the calling worktree, without writing or fetching: for every registered worktree other than the caller's (the main checkout included), its path, its branch or 'detached', the paths git status --porcelain reports (untracked included), the paths changed by commits on its HEAD that are not on origin/<Base> (default master), and the newest last-write time among those paths that exist. It prints one ASCII line [LIVE-WORK] path=<p> branch=<b> uncommitted=<n> unmerged=<n> newest=<ISO-8601> per worktree that has any, collapses worktrees whose newest change is older than -SinceHours (default 48) into one [LIVE-WORK-STALE] count=<n> line, and prints [LIVE-WORK-NONE] when nothing is held. It also prints [LIVE-WORK-HANDOFF] file=<f> task=<id> updated=<value> for the main checkout's progress.md HANDOFF block and for each _local/handoff-*.md. A worktree it cannot probe within its time budget is printed as [LIVE-WORK-UNKNOWN] path=<p>. It exits 0"
  - "A2 live-work.ps1 -TaskId <id> prints [LIVE-WORK-OVERLAP] for each other worktree whose uncommitted or unmerged paths fall under the card's allow_paths (read from origin/<Base>:specs/tasks/<id>.md, else from the caller's tree, matched the way the scope gate matches), for a worktree whose branch is <id>, and for a local branch <id> or r5-<id> or a remote-tracking origin/<id> or origin/r5-<id> whose tip is not on origin/<Base>. It exits 3 when it printed any, 0 when none, and 2 when a probe fails"
  - "A3 task.ps1 -Phase start runs A2 before git worktree add. Exit 3 stops start with [START-LIVE-WORK] and the overlap lines, unless -TakeOver is given, which prints the same lines and continues; exit 2 or any other code stops start with [START-LIVE-WORK-UNKNOWN]. When the card's worktree already exists, the existing refusal also prints that worktree's uncommitted count and newest change time and says another session may hold it and it must not be reset; cleanup keeps its dirty-tree guard. One real run is recorded here: start on a card whose paths another worktree holds stops with [START-LIVE-WORK]"
  - "A4 .claude/settings.json runs live-work.ps1 (the A1 summary) as a SessionStart hook that finishes within 15 seconds, so every session sees the worktrees, branches and handoff files other sessions hold before it acts"
  - "A5 .claude/hooks/guard-dirty-worktree.ps1 is registered under PreToolUse with the matcher Bash|PowerShell. It reads the event as UTF-8 and runs git only when the command matches a git command that discards or moves work: reset --hard, --merge or --keep; checkout -f, checkout -- <paths> or checkout . ; restore without --staged alone; clean with -f; stash; switch -C, -f or --discard-changes; branch -f, -D or -M; update-ref; worktree remove --force. It resolves the worktree each one targets (-C <path>, else the event's cwd), and when that worktree has uncommitted changes it prints permissionDecision ask with a reason naming the worktree, its branch, its uncommitted count and newest change time, and that another session may hold it. For a clean worktree, any other command, unreadable input or any error it prints nothing. It always exits 0"
  - "A6 The task-loop skill's 前置, CLAUDE.md's execution boundary and docs/HANDOFF.md tell a session to run live-work.ps1 -TaskId <id> before it starts, resumes, splits or amends a card, and live-work.ps1 before it resets, cleans or removes a worktree; when another session holds overlapping work, stop and ask the user which session owns it (L218). docs/HANDOFF.md adds that a session whose progress.md HANDOFF names another task writes its own handoff to _local/handoff-<id>.md (L273), which A1 lists"
  - "A7 live-work.ps1 -SelfCheck builds temporary fixtures (a bare origin, a main checkout, and two linked worktrees: one with an uncommitted file under a fixture card's allow_paths and one clean) and covers the A1 lines, A2 exit 3 for the overlapping worktree and for a branch named after the card, A2 exit 0 for a card nothing overlaps, and A5 by piping event JSON into the real hook: ask for git -C <dirty> reset --hard and for git checkout -- . with its cwd in the dirty worktree, nothing for the same commands on the clean worktree, for git status and for unreadable input. It prints [LIVE-WORK-SELF-CHECK-PASS]"
  - "A8 The Tier-S full selftest run passes on the shipped candidate"
dod_command: $p = 'scripts/live-work.ps1'; $h = '.claude/hooks/guard-dirty-worktree.ps1'; foreach ($f in @($p, $h)) { if (-not (Test-Path -LiteralPath $f)) { Write-Host "[DOD-FAIL] missing $f"; exit 1 } }; $o = (& pwsh -NoProfile -File $p -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[LIVE-WORK-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $s = Get-Content -Raw -LiteralPath '.claude/settings.json' | ConvertFrom-Json; $pre = @($s.hooks.PreToolUse | Where-Object { @($_.hooks.command) -match 'guard-dirty-worktree\.ps1' }); if ($pre.Count -ne 1 -or $pre[0].matcher -cne 'Bash|PowerShell') { Write-Host '[DOD-FAIL] PreToolUse registration'; exit 1 }; if (@(@($s.hooks.SessionStart.hooks.command) -match 'live-work\.ps1').Count -ne 1) { Write-Host '[DOD-FAIL] SessionStart registration'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes against the production probe, the real hook and real Git fixtures (A1, A2, A5, A7), and the hook and the SessionStart summary are each registered exactly once (A4, A5); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing scripts/live-work.ps1.
review_gate: codex {verdict:pass}
budget: 450
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the allow_paths overlap test, drop the branch-name check, map exit 3 to 0, drop the start stop, make -TakeOver the default, drop the hook's dirty-worktree test, drop each of the reset --hard, checkout --, clean -f and worktree remove --force arms, make the hook ask on unreadable input; every one must make the self-check or the recorded start run fail.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry, fill L218's enforced_by, and leave a note for the AIDLC project (plan v5 Package S) that MyInspection has live-work.ps1 and the dirty-worktree hook.
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

1. **See it (A1, A4).** A read-only probe lists what other worktrees hold, uncommitted and unmerged, with the
   newest change time, plus the handoff files, and every session gets that list at start. Worktrees nobody has
   touched for two days collapse into one count so the list stays short.
2. **Check before start (A2, A3).** Starting a card whose paths another worktree has changed, or whose branch
   already exists, stops until the user says to take it over. The paths come from the base card, the same as
   the scope gate reads them.
3. **Ask before discarding (A5).** A git command that would discard or move work in a worktree with uncommitted
   changes asks the user first. The hook asks, it never denies, so a session discarding its own work loses one
   confirmation.
4. **Say it where it is read (A6).** One rule line each in CLAUDE.md's execution boundary (always loaded) and
   the task-loop skill, and the per-task handoff rule in docs/HANDOFF.md.

Estimate: about 400 changed lines. Measure the candidate before RED (L266); if it exceeds 450, split A5 into
its own card.

## Order with other cards

`T0-MAIN-CHECKOUT-READONLY` also edits `.claude/settings.json` (a SessionStart hook) and CLAUDE.md's execution
boundary. The card first depended on it; the user dropped that dependency on 2026-09-25, because the two only
add entries to the same files. Whichever of the two ships second merges origin/master before ship and keeps
both SessionStart hooks and both execution-boundary lines. `T0-POST-MERGE-LESSONS` and
`T0-POST-MERGE-CARD-DRIFT` edit the task-loop skill's R5 lines; this card adds one line under 前置, so merge
origin/master before ship.
