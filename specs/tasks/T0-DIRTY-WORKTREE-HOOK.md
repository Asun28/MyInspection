---
id: T0-DIRTY-WORKTREE-HOOK
title: Ask the user before a git command discards or moves work in a worktree that has uncommitted changes
status: todo
depends_on: [T0-LIVE-WORK-START]
parallelizable_with: []
allow_paths:
  - .claude/hooks/guard-dirty-worktree.ps1
  - .claude/settings.json
  - scripts/live-work.ps1
  - CLAUDE.md
  - specs/tasks/T0-DIRTY-WORKTREE-HOOK.md
forbid:
  - Denying or rewriting a git command; the hook only asks the user (permissionDecision ask)
  - Fetching, pushing or any network call in the hook
  - Writing to any worktree, branch, ref or file from the hook (every git call it makes passes --no-optional-locks)
  - Changing the probe's summary or -TaskId behaviour in scripts/live-work.ps1; this card only adds self-check cases there
non_goals:
  - Guarding the edit tools (T0-MAIN-CHECKOUT-READONLY)
  - Parsing commands built from variables or aliases, or git options other than -C and -c before the subcommand; the hook header says these are not seen
acceptance:
  - "A1 .claude/hooks/guard-dirty-worktree.ps1 is registered under PreToolUse with the matcher Bash|PowerShell. It reads the event as UTF-8 and runs git only when the command matches a git command that discards or moves work: reset --hard, --merge or --keep; checkout -f, checkout -- <paths> or checkout . ; restore without --staged alone; clean with -f; stash other than stash list or stash show; switch -C, -f or --discard-changes; branch -f, -D or -M; update-ref; worktree remove --force. It resolves the worktree each one targets (-C <path>, absolute or relative to the directory so far, else the directory of the last cd, Set-Location or Push-Location earlier in the same command, else the event's cwd; for worktree remove, the path being removed), and when that worktree has uncommitted changes, or for clean with -x or -X ignored files, it prints permissionDecision ask with a reason naming the worktree, its branch, its uncommitted count (and for clean -x or -X its ignored count) and newest change time (an ignored directory is counted but not dated), and that another session may hold it. When the target has no uncommitted changes (and, for clean -x or -X, no ignored files), for any other command and for unreadable input it prints nothing. A command it cannot check (a git error or a path it cannot resolve) is left unasked while the other commands in the same tool call are still checked; once its git deadline passes, every later command is left unasked too, and asks already found are still printed. It always exits 0"
  - "A2 live-work.ps1 -SelfCheck pipes event JSON into the real hook and covers: an ask naming the dirty worktree for reset --hard, reset --merge, reset --keep, checkout -f, checkout -- <path>, checkout ., restore <path>, restore --staged --worktree <path>, clean -fd, stash, stash push, switch -C, switch --discard-changes, branch -D, branch -f, update-ref and worktree remove --force <dirty>; for the targets -C <absolute>, -C <relative> from a cwd, cd, Set-Location, Push-Location and the event cwd; nothing for the same commands on a clean worktree, for restore --staged <path>, stash list, stash show, git status, and unreadable input; an ask for clean -fdx on a worktree whose only extra file is ignored, and nothing for clean -fd there"
  - "A3 CLAUDE.md's execution-boundary live-work line (T0-LIVE-WORK-START) adds that the dirty-worktree hook asks before a discarding git command on a worktree with uncommitted changes"
  - "A4 The selftest acceptance of the card's computed tier passes on the shipped candidate"
dod_command: $h = '.claude/hooks/guard-dirty-worktree.ps1'; if (-not (Test-Path -LiteralPath $h)) { Write-Host "[DOD-FAIL] missing $h"; exit 1 }; $o = (& pwsh -NoProfile -File scripts/live-work.ps1 -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[LIVE-WORK-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $s = Get-Content -Raw -LiteralPath '.claude/settings.json' | ConvertFrom-Json; $pre = @($s.hooks.PreToolUse | Where-Object { @($_.hooks.command) -match 'guard-dirty-worktree\.ps1' }); if ($pre.Count -ne 1 -or $pre[0].matcher -cne 'Bash|PowerShell') { Write-Host '[DOD-FAIL] PreToolUse registration'; exit 1 }; if (-not (Get-Content -Raw -LiteralPath 'CLAUDE.md').Contains('dirty-worktree')) { Write-Host '[DOD-FAIL] CLAUDE.md does not name the hook'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes with the hook cases against the real hook and real Git fixtures (A1, A2), the hook is registered exactly once under PreToolUse with matcher Bash|PowerShell (A1), and CLAUDE.md names it (A3); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing .claude/hooks/guard-dirty-worktree.ps1.
review_gate: codex {verdict:pass}
budget: 350
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the hook's dirty-worktree test, replace each arm (reset, checkout, restore, clean, stash, switch, branch, update-ref, worktree) with a pattern that never matches, drop the stash list/show exclusion, drop the restore --staged exclusion, stop cd, Set-Location and Push-Location from moving the target, stop relative -C from joining the directory so far, drop the ignored-file count for clean -x, make the hook ask on unreadable input; every one must make the self-check fail.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry.
---

# T0-DIRTY-WORKTREE-HOOK

Split from `T0-LIVE-WORK-GUARD` on 2026-10-07 (user ruling; that card's "Split" section has the reason). It
carries that card's acceptance A5, with the coverage Codex R3 found missing on PR #447: most arms of the hook
and the Set-Location, Push-Location and relative -C target forms had no executable case. The incident behind it
is in `T0-LIVE-WORK-GUARD`'s "What happened": session B's start reset session A's worktree.

## Design

A git command that would discard or move work in a worktree with uncommitted changes asks the user first. The
hook asks, it never denies, so a session discarding its own work loses one confirmation. It reuses the probe's
`Get-LiveWorkUncommitted` and `Get-LiveWorkNewest` (dot-sourced with `-AsLibrary`), so it reports a worktree the
same way the SessionStart summary does.

`clean -x` and `clean -X` also count ignored files (user ruling 2026-10-09): this repo keeps `_local/`,
`progress.md` and `.secrets/` ignored and in no repository, so `git clean -fdx` in a worktree with nothing else to
lose would delete them without a question.

## Evidence carried from PR #447 (combined candidate)

**`ask` under bypass permissions.** Before relying on `permissionDecision: ask`, a throwaway headless session
(`claude -p --permission-mode bypassPermissions`, a project hook that always answers `ask`) was told to run
`echo askprobe > marker.txt`. The hook fired, the command did not run (no `marker.txt`), and the model received
the reason. The same run with `deny` behaved the same. So `ask` is not approved silently in bypass mode.

## Implementation record (2026-10-09)

Delivered through the AIDLC loop (goal `g-20260925112601-012c7e`) by a second session while `T0-LIVE-WORK-START`
shipped from the first (user ruling 2026-10-09). The hook starts from the combined candidate of PR #447
(`00a0e56d:.claude/hooks/guard-dirty-worktree.ps1`). Changes on top of it, most from two fresh-context reviews
before ship:

- **Git must start its segment.** The candidate matched `git` anywhere after a space, so a commit whose message
  mentions `git reset --hard`, or `echo git stash`, asked about a dirty cwd. The match is now anchored at the start
  of a segment (optionally after PowerShell's `&`).
- **Quoted text is masked** before the command is split at `;`, `|`, `&&`, `||` and newlines and before its
  parentheses are counted, so `git commit -m "wip; git stash pop later"` does not ask and a `)` inside quotes does
  not close a subshell.
- **Directory changes follow the shell more closely.** `pushd` joins cd, Set-Location and Push-Location;
  Pop-Location and popd undo a push; in Bash the closing parenthesis of a `( ... )` subshell undoes a cd made inside
  it, while PowerShell parentheses keep the location change. A directory change that does not start its segment
  (`if (...) { Set-Location x }` on one line) is not seen, and the header says so.
- **One unreadable command does not cancel the others.** Each segment is checked in its own try/catch, so a git
  error or an unresolvable path leaves only that command unasked. The library's `$ErrorActionPreference = 'Stop'`
  had made a missing drive in a later segment discard an ask already found. Once the shared deadline passes, every
  later command is unasked too (A1 as worded by #453).
- **Git deadline.** Dot-sourced with `-AsLibrary`, the probe sets no deadline, so the hook sets one shared 10 s
  deadline for its git calls; settings.json gives the hook a 15 s timeout.
- **clean -x and -X count ignored files** (A1 amended by #451, user ruling 2026-10-09), listed with
  `git ls-files --others --ignored --exclude-standard --directory`. An ignored directory counts once and is not
  dated: its own last-write time does not change when a file inside it does, so it would make live work look stale.
- `switch --force-create` (the long form of `-C`) joins the switch arm. A Git Bash path `/c/...` is read as `C:/...`
  on Windows, so an absolute path from the Bash tool resolves.

**Self-check.** 70 hook cases join the probe's 20. Each pipes event JSON into the real hook; an ask must be exactly
one line of JSON (stderr is merged in) with `hookEventName` PreToolUse, `permissionDecision` ask, and a reason
naming `wtA (branch T9-OTHER): 1 uncommitted path(s), newest change 20…` and the other-session sentence; "prints
nothing" means no line at all; every case requires exit 0. A git error is produced portably by a fixture worktree
whose index is garbage, so `status` fails while `rev-parse` succeeds. The deadline itself has no case.

**R4.** `c3-mutate.ps1` (scratchpad) ran each single-statement mutant of the hook against `live-work.ps1 -SelfCheck`
in its own copy of `scripts/` and the hook, three at a time, beside an unmutated control, on commit `14cf56d9`
(hook SHA-256 `54E221CB…ED9C`, `live-work.ps1` `260716C4…FBBB`). A mutant counts as killed only when its search
string hit exactly once, the mutated file parses, and the self-check exits 1 with `[LIVE-WORK-SELF-CHECK-FAIL]` and
at least one named failing case. The control passed (90 cases), 43 of 43 declared mutants ran and were killed, and
both source files were unchanged after the batch. Before relying on it, the same harness killed 38 of 38 on an earlier
draft with a passing control (L360). Every hygiene item maps to a row below.

| id | single-statement change in the hook | cases failed | first failing case |
|---|---|---|---|
| M01 | the dirty test `if ($unc.Count -or $ign.Count)` -> `if ($true)` (hygiene: drop the dirty-worktree test) | 25 | git -C <clean> reset --hard prints nothing |
| M02 | reset arm -> `(?!)` (never matches) | 18 | git -C <dirty> reset --hard asks |
| M03 | checkout arm -> `(?!)` | 3 | git -C <dirty> checkout -f asks |
| M04 | restore arm -> `(?!)` | 2 | git -C <dirty> restore src/app.txt asks |
| M05 | clean arm -> `(?!)` | 3 | git -C <dirty> clean -fd asks |
| M06 | stash arm -> `(?!)` | 2 | git -C <dirty> stash asks |
| M07 | switch arm -> `(?!)` | 4 | git -C <dirty> switch -C lw-x asks |
| M08 | branch arm -> `(?!)` | 3 | git -C <dirty> branch -D lw-x asks |
| M09 | update-ref arm -> `(?!)` | 1 | git -C <dirty> update-ref refs/heads/lw-x HEAD asks |
| M10 | worktree arm -> `(?!)` | 1 | git worktree remove --force <dirty> asks |
| M11 | stash arm -> `^` (hygiene: drop the stash list/show exclusion) | 2 | git -C <wt> stash list on a dirty worktree prints nothing |
| M12 | restore arm -> `^` (hygiene: drop the restore --staged exclusion) | 1 | git -C <wt> restore --staged src/app.txt on a dirty worktree prints nothing |
| M13 | `cd` dropped from the directory-change verbs | 4 | cd moves the target |
| M14 | `Set-Location` dropped | 3 | a relative -C joins the directory a Set-Location moved to |
| M15 | `Push-Location` dropped | 1 | Push-Location moves the target |
| M16 | `pushd` dropped | 1 | pushd moves the target |
| M17 | a relative -C used as given, not joined | 2 | a relative -C joins the event cwd |
| M18 | a relative -C joined to the event cwd, not the directory so far | 1 | a relative -C joins the directory a Set-Location moved to |
| M19 | the outer catch prints an ask (hygiene: ask on unreadable input) | 1 | unreadable input prints nothing |
| M20 | the /c/... mapping guard -> `if ($false)` | 1 | a Git Bash /c/... path is read as C:/... |
| M21 | worktree remove keeps the -C/cwd target | 2 | git worktree remove --force <dirty> asks |
| M22 | the -C loop deleted | 48 | git -C <dirty> reset --hard asks |
| M23 | final `exit 0` -> `exit 1` | 70 | git -C <dirty> reset --hard asks |
| M24 | branch always reported as detached | 35 | git -C <dirty> reset --hard asks |
| M25 | the per-segment catch rethrows | 2 | a git error in one command leaves the later ones checked |
| M26 | Pop-Location/popd pops without restoring | 2 | Pop-Location moves it back |
| M27 | a closing `)` pops without restoring | 1 | the closing parenthesis of ( ... ) moves it back |
| M28 | leading `(` not stripped | 3 | a cd inside ( ... ) moves the target inside it |
| M29 | trailing `)` not stripped | 2 | a cd inside ( ... ) moves the target inside it |
| M30 | git matched anywhere after a space (the candidate's pattern) | 3 | git commit -m "note: git reset --hard asks" on a dirty worktree prints nothing |
| M31 | `&` prefix not allowed | 1 | & git.exe is git |
| M32 | `.exe` not allowed | 1 | & git.exe is git |
| M33 | -C extraction made case-insensitive (a -c value read as a directory) | 1 | a -c option is not a directory (dirty asks) |
| M34 | `-Path`/`-LiteralPath` not skipped | 1 | Set-Location -LiteralPath moves the target |
| M35 | worktree remove path read without skipping --force | 2 | git worktree remove --force <dirty> asks |
| M36 | `--force-create` dropped from the switch arm | 1 | git -C <dirty> switch --force-create lw-x asks |
| M37 | `-f` dropped from the switch arm | 1 | git -C <dirty> switch -f T9-OTHER asks |
| M38 | `M` dropped from the branch arm | 1 | git -C <dirty> branch -M lw-x asks |
| M39 | the ignored-file count for clean -x -> never taken (hygiene: drop the ignored-file count) | 2 | clean -fdx on a worktree whose only extra file is ignored asks |
| M40 | `-X` no longer counts ignored files | 1 | clean -fX counts ignored files too |
| M41 | PowerShell parentheses treated as a subshell | 1 | in PowerShell ( ... ) keeps the location change |
| M42 | quoted text not masked | 2 | a ) inside quotes does not close the subshell |
| M43 | `popd` dropped | 1 | popd moves it back |

**[FOLLOW-UP]** (outside A1, which defines both the commands and the target):
- A command run from a clean worktree that moves a branch checked out in another, dirty worktree
  (`git update-ref refs/heads/<that branch> …`, `git branch -f <that branch> …`) prints nothing, because the target
  is the -C, cd or cwd worktree. That is the cross-session shape of the incident in `T0-LIVE-WORK-GUARD`.
- A plain `git worktree remove <wt>` (no `--force`) deletes a worktree that holds only ignored files; git refuses only
  for untracked or modified files, and A1 asks only with `--force`.
- Other commands that discard work but are not in A1's list: `checkout <path>` without `--`, `merge --abort`,
  `rebase --abort`, `cherry-pick --abort`.
