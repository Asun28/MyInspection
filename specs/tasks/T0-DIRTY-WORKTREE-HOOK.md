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
  - "A1 .claude/hooks/guard-dirty-worktree.ps1 is registered under PreToolUse with the matcher Bash|PowerShell. It reads the event as UTF-8 and runs git only when the command matches a git command that discards or moves work: reset --hard, --merge or --keep; checkout -f, checkout -- <paths> or checkout . ; restore without --staged alone; clean with -f; stash other than stash list or stash show; switch -C, -f or --discard-changes; branch -f, -D or -M; update-ref; worktree remove --force. It resolves the worktree each one targets (-C <path>, absolute or relative to the directory so far, else the directory of the last cd, Set-Location or Push-Location earlier in the same command, else the event's cwd; for worktree remove, the path being removed), and when that worktree has uncommitted changes, or for clean with -x or -X ignored files, it prints permissionDecision ask with a reason naming the worktree, its branch, its uncommitted count (and for clean -x or -X its ignored count) and newest change time, and that another session may hold it. When the target holds nothing the command would discard, for any other command and for unreadable input it prints nothing; a command it cannot check (a git error, a path it cannot resolve, its git deadline) is left unasked while the other commands in the same tool call are still checked. It always exits 0"
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
