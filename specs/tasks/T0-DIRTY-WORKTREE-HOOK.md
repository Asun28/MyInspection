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

**Self-check.** 133 hook cases join the probe's 20. Each pipes event JSON into the real hook; an ask must be exactly
one line of JSON (stderr is merged in) with `hookEventName` PreToolUse, `permissionDecision` ask, and a reason
naming `wtA (branch T9-OTHER): 1 uncommitted path(s), newest change 20…` and the other-session sentence; "prints
nothing" means no line at all; every case requires exit 0. A git error comes from a fixture worktree whose index is
garbage. The deadline case gives worktree wtS a clean filter that sleeps past the 10 s deadline; the fsmonitor case
points wtA's `core.fsmonitor` at a script that would write a marker file.

**R3 round 1 (on `19ecca2e`).** Codex blocked on two spec findings and one standards finding. (1) The arms matched
the raw text, so `git reset "--hard"`, `git "reset" --hard`, `checkout -fq`, `switch -fq`, `worktree remove -ff` and
`worktree remove --force -- <path>` did not ask, while `stash "list"`, `restore -Sq`, `checkout --`, commands missing
an operand git requires and `git RESET` did. The hook now reads a git command as words, as its header describes. (2)
The deadline had no case; see the slow clean filter above. (3) `git status` ran a repository's `core.fsmonitor`
program; the hook now passes `-c core.fsmonitor=` through the library's new `$script:LiveWorkGitConfig` (empty by
default, so the probe is unchanged). Clean filters can still run, as in any git status. Two fresh-context reviews of
the fix added abbreviated long options (`--har`), `stash -- list`, `checkout --pathspec-from-file` and a case for every
`--force`, `-W` and `$takes` entry.

**R4.** `c3-mutate.ps1` (scratchpad) ran each single-statement mutant of the hook against `live-work.ps1 -SelfCheck`
in its own copy of `scripts/` and the hook, five at a time, beside an unmutated control, on commit `9b67d600`
(hook SHA-256 `259E4ADB…882A`, `live-work.ps1` `491E3FA6…F14C`). A mutant counts as killed only when its search
string hit exactly once, the mutated file parses, and the self-check exits 1 with `[LIVE-WORK-SELF-CHECK-FAIL]` and
at least one named failing case. The control passed (153 cases), 80 of 80 declared mutants ran and were killed, and
both source files were unchanged after the batch. Before R3 round 1, 43 of 43 were killed on `14cf56d9`. Every
hygiene item maps to a row below; a row with several ids lists each one's count and first failing case in order.

| id | single-statement change in the hook | cases failed | first failing case |
|---|---|---|---|
| M01 | the dirty test -> `$true` (hygiene: drop the dirty-worktree test) | 44 | git -C <clean> reset --hard prints nothing |
| M02–M10 | each arm -> `{ $false }`: reset, checkout, restore, clean, stash, switch, branch, update-ref, worktree | 23 / 6 / 5 / 4 / 6 / 7 / 4 / 2 / 3 | git -C <dirty> reset --hard asks / git -C <dirty> checkout -f asks / git -C <dirty> restore src/app.txt asks / git -C <dirty> clean -fd asks / git -C <dirty> stash asks / git -C <dirty> switch -C lw-x asks / git -C <dirty> branch -D lw-x asks / git -C <dirty> update-ref refs/heads/lw-x HEAD asks / git worktree remove --force <dirty> asks |
| M11 | stash arm -> `$true` (hygiene: drop the stash list/show exclusion) | 3 | git -C <wt> stash list on a dirty worktree prints nothing |
| M12 | restore's --staged exclusion dropped (hygiene: drop the restore --staged exclusion) | 2 | git -C <wt> restore --staged src/app.txt on a dirty worktree prints nothing |
| M13–M16 | cd, Set-Location, Push-Location, pushd each dropped from the directory changes | 4 / 3 / 1 / 1 | cd moves the target / a relative -C joins the directory a Set-Location moved to / Push-Location moves the target / pushd moves the target |
| M17, M18, M22 | a relative -C used as given; joined to the event cwd; -C ignored | 2 / 1 / 88 | a relative -C joins the event cwd / a relative -C joins the directory a Set-Location moved to / git -C <dirty> reset --hard asks |
| M19 | the outer catch prints an ask (hygiene: ask on unreadable input) | 1 | unreadable input prints nothing |
| M20 | the /c/... mapping off | 1 | a Git Bash /c/... path is read as C:/... |
| M21 | worktree remove keeps the -C/cwd target | 4 | git worktree remove --force <dirty> asks |
| M23–M25 | final `exit 1`; branch always detached; the per-segment catch rethrows | 133 / 58 / 3 | git -C <dirty> reset --hard asks / git -C <dirty> reset --hard asks / a git error in one command leaves the later ones checked |
| M26, M27 | Pop-Location/popd and a closing `)` pop without restoring | 2 / 1 | Pop-Location moves it back / the closing parenthesis of ( ... ) moves it back |
| M28, M29 | leading `(` / trailing `)` not stripped | 3 / 2 | a cd inside ( ... ) moves the target inside it / a cd inside ( ... ) moves the target inside it |
| M30, M31 | the `&` prefix / `.exe` not allowed | 1 / 1 | & git.exe is git / & git.exe is git |
| M32 | -C read case-insensitively (a -c value read as a directory) | 1 | a -c option is not a directory (dirty asks) |
| M33 | `-Path`/`-LiteralPath` not skipped | 1 | Set-Location -LiteralPath moves the target |
| M34 | `--` not read as the end of options | 2 | git -C <dirty> checkout -- src/app.txt asks |
| M35, M71 | `--force-*` / `-C` dropped from the switch arm | 1 / 2 | git -C <dirty> switch --force-create lw-x asks / git -C <dirty> switch -C lw-x asks |
| M36, M64 | `-f` / `--force` dropped from switch's operand clause | 2 / 1 | git -C <dirty> switch -f T9-OTHER asks / git -C <dirty> switch --force T9-OTHER asks |
| M37, M65 | `-M` / `--force` dropped from the branch arm | 1 / 1 | git -C <dirty> branch -M lw-x asks / git -C <dirty> branch --force lw-x asks |
| M62, M63, M66, M67 | `--force` dropped from checkout / clean; `-W` from restore; `--pathspec-from-file` from checkout | 1 / 1 / 1 / 1 | git -C <dirty> checkout --forc asks / git -C <dirty> clean --f asks / git -C <dirty> restore -SW src/app.txt asks / git -C <dirty> checkout --pathspec-from-file=list.txt asks |
| M38 | the ignored-file count never taken (hygiene: drop the ignored-file count) | 2 | clean -fdx on a worktree whose only extra file is ignored asks |
| M39 | `-X` no longer counts ignored files | 1 | clean -fX counts ignored files too |
| M40 | PowerShell parentheses treated as a subshell | 1 | in PowerShell ( ... ) keeps the location change |
| M41 | quoted text not masked | 2 | a ) inside quotes does not close the subshell |
| M42 | `popd` dropped | 1 | popd moves it back |
| M43 | quotes not removed from words | 3 | git -C <dirty> reset "--hard" asks |
| M44 | bundled short options not split | 8 | git -C <dirty> clean -fd asks |
| M45 | splitting continues past an option that takes a value | 3 | git -C <wt> checkout -bxf on a dirty worktree prints nothing |
| M46 | a separate value kept as an operand, and a valueless value option counted | 8 | git -C <dirty> stash -m list asks |
| M47 | `--opt=value` not split at `=` | 3 | git -C <dirty> switch -Clw-y asks |
| M48, M60 | subcommand / flags compared case-insensitively | 1 / 3 | git -C <wt> RESET --hard on a dirty worktree prints nothing / git -C <wt> switch -c lw-z on a dirty worktree prints nothing |
| M49–M52 | the operand check dropped: branch, switch, update-ref, restore | 1 / 3 / 1 / 3 | git -C <wt> branch -D on a dirty worktree prints nothing / git -C <wt> switch -f on a dirty worktree prints nothing / git -C <wt> update-ref on a dirty worktree prints nothing / git -C <wt> restore --source HEAD on a dirty worktree prints nothing |
| M53, M54 | checkout: `--` alone asks / `.` no longer asks | 2 / 1 | git -C <wt> checkout -- on a dirty worktree prints nothing / git -C <dirty> checkout . asks |
| M55 | `core.fsmonitor` left on | 1 | a repository's core.fsmonitor program does not run (dirty still asks) |
| M56 | no deadline | 1 | past the deadline the slow command and every later one are left unasked, and the ask found before it is printed |
| M57, M58 | update-ref `--stdin` / restore `--pathspec-from-file` not asked | 1 / 1 | git -C <dirty> update-ref --stdin asks / git -C <dirty> restore --pathspec-from-file=list.txt asks |
| M59 | the worktree arm ignores its subcommand | 1 | git -C <wt> worktree add -f <wt> T9-OTHER on a dirty worktree prints nothing |
| M61 | long-option abbreviations not accepted | 3 | git -C <dirty> reset --har asks |
| M68 | stash reads operands after `--` | 1 | git -C <dirty> stash -- list asks |
| M69, M70, M72, M73, M74, M75, M76, M77, M78, M79, M80 | one entry dropped from `$takes`: restore --conflict, stash --pathspec-from-file, checkout -B, checkout -b, clean -e, restore -s, restore --source, stash -m, stash --message, switch -c, switch -C | 1 / 1 / 1 / 1 / 1 / 1 / 1 / 1 / 1 / 1 / 1 | git -C <wt> restore --conflict merge on a dirty worktree prints nothing / git -C <dirty> stash --pathspec-from-file list asks / git -C <wt> checkout -Bxf on a dirty worktree prints nothing / git -C <wt> checkout -bxf on a dirty worktree prints nothing / git -C <wt> clean -exf on a dirty worktree prints nothing / git -C <wt> restore -s HEAD on a dirty worktree prints nothing / git -C <wt> restore --source HEAD on a dirty worktree prints nothing / git -C <dirty> stash -m list asks / git -C <dirty> stash --message list asks / git -C <wt> switch -f -c lw-z on a dirty worktree prints nothing / git -C <wt> switch -C on a dirty worktree prints nothing |

**DoD and A3.** After `T0-LIVE-WORK-START` merged (PR #452, `827ca3d2`), origin/master was merged in and the A3
clause was appended to CLAUDE.md's L218 execution-boundary line. The DoD printed `[DOD-PASS]` there and again after
origin/master `7392ae6e` (that card's R5, #454) was merged in.

**Tier-S (A4).** The card's computed tier is S, so the acceptance run is `selftest.ps1 -TaskId T0-DIRTY-WORKTREE-HOOK`
(the full suite) from the card worktree's own copy (L344) on this PR's final head, the commit that records
R3 round 1, launched with an empty `git status --porcelain` and no edit during or after it. Its launch HEAD, status and
result line are posted on the PR. A commit cannot carry the result of a run on itself, so this record names the run
instead.

**[FOLLOW-UP]** (outside A1, which defines both the commands and the target):
- A command run from a clean worktree that moves a branch checked out in another, dirty worktree
  (`git update-ref refs/heads/<that branch> …`, `git branch -f <that branch> …`) prints nothing, because the target
  is the -C, cd or cwd worktree. That is the cross-session shape of the incident in `T0-LIVE-WORK-GUARD`.
- A plain `git worktree remove <wt>` (no `--force`) deletes a worktree that holds only ignored files; git refuses only
  for untracked or modified files, and A1 asks only with `--force`.
- Other commands that discard work but are not in A1's list: `checkout <path>` without `--`, `merge --abort`,
  `rebase --abort`, `cherry-pick --abort`.
