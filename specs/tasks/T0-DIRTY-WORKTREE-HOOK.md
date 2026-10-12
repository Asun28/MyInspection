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
  - Undoing shell escapes, resolving abbreviations other than A1's, knowing the value of options other than A1's, and recognising commands git itself rejects (user ruling 2026-10-12; A1 says what the hook reads)
  - Stopping clean or smudge filters a repository configures, which the hook's git status runs as any git status does (T0-LIVE-WORK-GIT-FILTERS)
acceptance:
  - "A1 .claude/hooks/guard-dirty-worktree.ps1 is registered under PreToolUse with the matcher Bash|PowerShell. It reads the event as UTF-8 and runs git only when the command matches a git command that discards or moves work: reset --hard, --merge or --keep; checkout -f, checkout -- <paths> or checkout . ; restore without --staged alone; clean with -f and without -n or --dry-run; stash other than stash list or stash show; switch -C, -f or --discard-changes; branch -f, -D or -M; update-ref; worktree remove --force; none of these when an -h or --help option comes before --, since git then prints its usage. It reads a git command as the words the shell passes with only single and double quotes removed: the subcommand and options are matched case-sensitively, bundled short options count one by one (-fd), a word of three or more characters that begins a long option the hook looks for counts as that option (--har is --hard; -h and --help count only spelled out, as git reset --h is a hard reset), the values of checkout -b and -B, restore -s, --source and --conflict, clean -e, stash -m, --message and --pathspec-from-file, and switch -c and -C are skipped, and every word after -- is an operand. Outside A1, so the hook may miss them or ask about them: shell escapes (a backslash or backtick before a character, as in an escaped --hard or a path with escaped spaces), other abbreviations, the value of any other option (read as an operand), and commands git itself rejects (an unknown option, a Boolean option given a value, a missing value or operand). A wrong ask costs one confirmation; a missed command is as it was before this card. It resolves the worktree each one targets (-C <path>, absolute or relative to the directory so far, else the directory of the last cd, Set-Location or Push-Location earlier in the same command, else the event's cwd; for worktree remove, the path being removed), and when that worktree has uncommitted changes, or for clean with -x or -X ignored files, it prints permissionDecision ask with a reason naming the worktree, its branch, its uncommitted count (and for clean -x or -X its ignored count) and newest change time (an ignored directory is counted but not dated), and that another session may hold it. When the target has no uncommitted changes (and, for clean -x or -X, no ignored files), for any other command and for unreadable input it prints nothing. A command it cannot check (a git error or a path it cannot resolve) is left unasked while the other commands in the same tool call are still checked; once its git deadline passes, every later command is left unasked too, and asks already found are still printed. It always exits 0"
  - "A2 live-work.ps1 -SelfCheck pipes event JSON into the real hook and covers: an ask naming the dirty worktree for reset --hard, reset --merge, reset --keep, checkout -f, checkout -- <path>, checkout ., restore <path>, restore --staged --worktree <path>, clean -fd, stash, stash push, switch -C, switch --discard-changes, branch -D, branch -f, update-ref and worktree remove --force <dirty>; for the targets -C <absolute>, -C <relative> from a cwd, cd, Set-Location, Push-Location and the event cwd; nothing for the same commands on a clean worktree, for restore --staged <path>, stash list, stash show, git status, and unreadable input; an ask for clean -fdx on a worktree whose only extra file is ignored, and nothing for clean -fd there; nothing on the dirty worktree for clean -nfd, clean -f --dry-run, reset --hard -h, clean -fh and stash --help; an ask for worktree remove --force -- <path> where <path> is a relative path to a dirty worktree whose name starts with -"
  - "A3 CLAUDE.md's execution-boundary live-work line (T0-LIVE-WORK-START) adds that the dirty-worktree hook asks before a discarding git command on a worktree with uncommitted changes"
  - "A4 The selftest acceptance of the card's computed tier passes on the shipped candidate"
dod_command: $h = '.claude/hooks/guard-dirty-worktree.ps1'; if (-not (Test-Path -LiteralPath $h)) { Write-Host "[DOD-FAIL] missing $h"; exit 1 }; $o = (& pwsh -NoProfile -File scripts/live-work.ps1 -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[LIVE-WORK-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $s = Get-Content -Raw -LiteralPath '.claude/settings.json' | ConvertFrom-Json; $pre = @($s.hooks.PreToolUse | Where-Object { @($_.hooks.command) -match 'guard-dirty-worktree\.ps1' }); if ($pre.Count -ne 1 -or $pre[0].matcher -cne 'Bash|PowerShell') { Write-Host '[DOD-FAIL] PreToolUse registration'; exit 1 }; if (-not (Get-Content -Raw -LiteralPath 'CLAUDE.md').Contains('dirty-worktree')) { Write-Host '[DOD-FAIL] CLAUDE.md does not name the hook'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes with the hook cases against the real hook and real Git fixtures (A1, A2), the hook is registered exactly once under PreToolUse with matcher Bash|PowerShell (A1), and CLAUDE.md names it (A3); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing .claude/hooks/guard-dirty-worktree.ps1.
review_gate: codex {verdict:pass}
budget: 420
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the hook's dirty-worktree test, replace each arm (reset, checkout, restore, clean, stash, switch, branch, update-ref, worktree) with a pattern that never matches, drop the stash list/show exclusion, drop the restore --staged exclusion, stop cd, Set-Location and Push-Location from moving the target, stop relative -C from joining the directory so far, drop the ignored-file count for clean -x, make the hook ask on unreadable input, drop the clean -n/--dry-run exclusion, drop the -h/--help exclusion, let -h count when abbreviated, split option-like words after -- as options; every one must make the self-check fail.
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

## Ruling 2026-10-12: A1 says what the hook reads

Codex R3 blocked PR #455 twice (round 1 at `19ecca2e`, round 2 at `7a3acdff`, the round cap) on one
question: whether the hook must parse every spelling git and the shell accept. Round 2 named shell escapes
(an escaped `--hard`, a `-C` path with escaped spaces), an abbreviated value option (`stash --mes list`), a
dash-led path after `--`, and asks on commands git rejects (`reset --hard=no`, `clean -fz`, `switch --force-nope
x`, `update-ref -m reason`) or that only preview (`clean -nf`, `reset --hard -h`). The user ruled to keep the hook
a reader of the common spellings rather than a git grammar: A1 now states the reading rule and what lies outside
it, the hook stops asking about dry runs and help, and words after `--` stay whole. The hook only asks, so a wrong
ask costs one confirmation and a missed command leaves things as they were before this card. Round 2's standards
finding (a repository's clean filter still runs under the hook's `git status`) is moved to
`T0-LIVE-WORK-GIT-FILTERS`, since the SessionStart probe runs the same `git status`. With `ReviewGate='required'` an
`arbitration:` entry cannot merge this PR (the blocking R3 leg throws on any non-zero exit before reading one), so
the rounds are reset and R3 judges the amended card.

## Evidence carried from PR #447 (combined candidate)

**`ask` under bypass permissions.** Before relying on `permissionDecision: ask`, a throwaway headless session
(`claude -p --permission-mode bypassPermissions`, a project hook that always answers `ask`) was told to run
`echo askprobe > marker.txt`. The hook fired, the command did not run (no `marker.txt`), and the model received
the reason. The same run with `deny` behaved the same. So `ask` is not approved silently in bypass mode.
