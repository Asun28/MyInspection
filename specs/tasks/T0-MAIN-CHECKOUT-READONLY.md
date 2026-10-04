---
id: T0-MAIN-CHECKOUT-READONLY
title: Keep the shared main checkout read-only for tracked files and fast-forwarded to origin/master, so parallel sessions stop leaving work in it
status: todo
depends_on: [T0-POST-MERGE-DOCS-PR]
parallelizable_with: [T0-POST-MERGE-LESSONS]
branch: T0-MAIN-CHECKOUT-READONLY
worktree: C:\wt\T0-MAIN-CHECKOUT-READONLY
allow_paths:
  - scripts/main-checkout.ps1
  - .claude/hooks/guard-main-checkout.ps1
  - .claude/settings.json
  - scripts/_config.ps1
  - .gitignore
  - CLAUDE.md
  - docs/DEVOPS-WORKFLOW.md
  - specs/tasks/T0-MAIN-CHECKOUT-READONLY.md
sweep: "rg '主检出|main checkout' over CLAUDE.md, docs/DEVOPS-WORKFLOW.md, docs/HANDOFF.md and .claude/skills/task-loop/SKILL.md. The rule this card enforces is already taught at task-loop SKILL.md:24 (all edits inside the card worktree, never the main checkout); that file belongs to T0-POST-MERGE-LESSONS and its sentence stays true, so it is not edited. DEVOPS-WORKFLOW.md:46/53 (phase commands run from the main checkout, edits happen in the worktree) gets the enforcement and sync note. CLAUDE.md's execution boundary gets the rule. HANDOFF.md:24/78 describe the gitignored handoff files, which the hook allows, so they stay."
forbid:
  - Stashing, resetting, rebasing, committing, checking out or deleting anything in the main checkout; sync only runs git merge --ff-only
  - Guarding Bash or PowerShell commands; only the four edit tools are guarded
  - Changing scripts/task.ps1, scripts/review.ps1, scripts/lessons.ps1, scripts/post-merge.ps1 or anything under .claude/skills/ (the two post-merge cards own post-merge.ps1, lessons.ps1 and the task-loop skill)
  - Adding a key to the config table through the local file, or printing the local-override notice on stdout, which hooks and DoD parsers read
  - Fetching anything but origin's default branch
non_goals:
  - Where lessons.ps1 bump writes by default; T0-POST-MERGE-LESSONS keeps it on the main checkout, and changing that is a separate decision
  - One set of handoff files (progress.md, task_plan.md, findings.md) per session; they stay one set per checkout
  - Catching Bash or PowerShell writes into the main checkout
  - The repository's delete_branch_on_merge setting and the missing master ruleset
  - A post-merge.ps1 path for card registration and card amendment PRs
acceptance:
  - "A1 scripts/main-checkout.ps1 -AsLibrary exposes one decision function: a path is a main-checkout write when its nearest existing ancestor directory lies in a non-bare Git working tree whose resolved git-dir equals its resolved common-dir, and git check-ignore reports the repo-relative path as not ignored. A path in a linked worktree (including one nested under an ignored directory of the main checkout), a path outside any repository, and an ignored path are not. Any Git failure answers not-a-main-checkout-write (fail-open, as guard-frozen is)"
  - "A2 .claude/hooks/guard-main-checkout.ps1 is registered in .claude/settings.json under PreToolUse with the matcher Edit|Write|MultiEdit|NotebookEdit. It reads the event as UTF-8, takes tool_input.file_path (notebook_path for NotebookEdit), asks the A1 function, and on a main-checkout write prints the same deny JSON shape guard-frozen prints, with a reason naming the path and the two ways out: a card worktree from task.ps1 -Phase start, or scripts/post-merge.ps1 for post-merge docs. Otherwise it prints nothing. It always exits 0; unreadable input prints nothing"
  - "A3 main-checkout.ps1 sync resolves the main checkout from any worktree of the repository, fetches origin's default branch with a 15-second limit, and prints exactly one status line, the first that applies in this order: [SYNC-MAIN-OFFLINE] when the fetch fails or times out; [SYNC-MAIN-SKIP] when HEAD is not the default branch; [SYNC-MAIN-AHEAD] <n> when local commits are not on origin; [SYNC-MAIN-BUSY] when any pwsh process's command line runs task.ps1 or review.ps1; [SYNC-MAIN-CURRENT] when nothing is behind; [SYNC-MAIN-OK] <old>..<new> after git merge --ff-only succeeds; [SYNC-MAIN-BLOCKED] with the paths git names when the fast-forward is refused, HEAD and the working files unchanged. It exits 0 in every case"
  - "A4 .claude/settings.json runs main-checkout.ps1 sync once as a SessionStart hook, so every session begins on origin's control-plane scripts (phase commands run from the main checkout, L86) or is told in one line why not"
  - "A5 scripts/_config.ps1 dot-sources scripts/_config.local.ps1 right after the table is built when that file exists, and .gitignore ignores that path, so a machine-local override (ReviewCommand and ReviewModel while the Codex quota is out) is never an edit to a tracked file. A key the table does not already have throws [CONFIG-LOCAL-UNKNOWN-KEY]; when any value changed, one line [CONFIG-LOCAL] <keys> goes to stderr. Without the file every key and value is unchanged"
  - "A6 main-checkout.ps1 -SelfCheck builds temporary fixtures (a bare origin, a main checkout, a linked worktree) and covers every A1 case; A2 by piping event JSON into the real hook (deny JSON for a tracked main-checkout file and for an untracked file that is not ignored; nothing for the ignored, linked-worktree, outside-repo and unreadable cases); each A3 status except BUSY on real Git, BUSY through an injected process list, and BLOCKED leaving the dirty file's bytes intact; A5 applied, unknown key rejected, notice on stderr only, absent file unchanged. It prints [MAIN-CHECKOUT-SELF-CHECK-PASS]"
  - "A7 CLAUDE.md's execution boundary states that tracked files in the main checkout are changed only through a worktree and a PR, enforced by the hook for the edit tools, with the two ways out and the local config file; docs/DEVOPS-WORKFLOW.md documents sync and the SessionStart hook next to its L86 note"
  - "A8 The Tier-S full selftest run passes on the shipped candidate"
dod_command: $p = 'scripts/main-checkout.ps1'; $h = '.claude/hooks/guard-main-checkout.ps1'; foreach ($f in @($p, $h)) { if (-not (Test-Path -LiteralPath $f)) { Write-Host "[DOD-FAIL] missing $f"; exit 1 } }; $o = (& pwsh -NoProfile -File $p -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[MAIN-CHECKOUT-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $s = Get-Content -Raw -LiteralPath '.claude/settings.json' | ConvertFrom-Json; $pre = @($s.hooks.PreToolUse | Where-Object { @($_.hooks.command) -match 'guard-main-checkout\.ps1' }); if ($pre.Count -ne 1 -or $pre[0].matcher -cne 'Edit|Write|MultiEdit|NotebookEdit') { Write-Host '[DOD-FAIL] PreToolUse registration'; exit 1 }; if (@(@($s.hooks.SessionStart.hooks.command) -match 'main-checkout\.ps1.* sync').Count -ne 1) { Write-Host '[DOD-FAIL] SessionStart sync registration'; exit 1 }; if (-not ((Get-Content -Raw -LiteralPath '.gitignore') -split "`r?`n" -ccontains 'scripts/_config.local.ps1')) { Write-Host '[DOD-FAIL] gitignore entry'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes against the production decision function, the real hook and real Git fixtures (A1-A3, A5), the hook and the sync are each registered exactly once (A2, A4), and .gitignore lists the local config file (A5); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] missing scripts/main-checkout.ps1.
review_gate: codex {verdict:pass}
budget: 450
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the git-dir equals common-dir test, drop the check-ignore test, make the hook print nothing on a deny, replace --ff-only with a plain merge, skip the AHEAD check, let the local file add a key, send the notice to stdout; every one must make the self-check exit non-zero.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry, and tell the running sessions to move any ReviewCommand edit out of the tracked scripts/_config.ps1 into scripts/_config.local.ps1.
---

# T0-MAIN-CHECKOUT-READONLY

Opened by user request on 2026-09-25: work done after a merge stays in the local main checkout instead of
reaching origin, and several sessions working at once confuse each other's steps.

`T0-POST-MERGE-DOCS-PR` and `T0-POST-MERGE-LESSONS` (PR #359) automate the R5 docs PR, the lessons PR and the
pruning of merged remote branches, each from a worktree cut from origin. They stop their own script from
writing into the main checkout. Nothing stops a session from doing it, and nothing brings the main checkout
back in line with origin. This card closes those two gaps.

## Problem (measured 2026-09-25 on origin/master `53be6be9`)

- **Sessions write into the shared main checkout.** Right now it holds another session's edit to the tracked
  `scripts/_config.ps1`: `ReviewCommand` points at a script in that session's temporary scratchpad and
  `ReviewModel` is `claude-opus-5-5`. Phase commands run from the main checkout (L86), so any session's R3 now
  runs that session's private script. Earlier handoffs record the same pattern with `docs/SCAFFOLD-SYNC.md` and
  `specs/tech-debt-tracker.md`, where uncommitted edits kept master from fast-forwarding.
- **The main checkout falls behind origin.** Master could not fast-forward past uncommitted edits several times
  on 2026-09-24 and 2026-09-25, and was pulled forward by hand when it could. While it is behind, every phase
  command runs outdated scripts, and a script added on origin (such as `post-merge.ps1`) is missing entirely.
- **Two sessions opened overlapping cards within minutes of each other** on 2026-09-25 (PR #359 and this
  request), because neither could see the other's work until it reached origin.
- **The rule already exists but nothing enforces it.** task-loop `SKILL.md:24` says all edits happen in the card
  worktree, never the main checkout.

## Design

1. **Read-only by hook (A1, A2).** Edits through the edit tools to tracked or unignored files in the main
   checkout are denied with a message naming the way out. Gitignored files (the handoff files, `_local/`,
   `.secrets/`, `runtime/`) and every linked worktree stay writable. Writes made inside a script are out of
   this hook's reach: `post-merge.ps1` is barred from the main checkout by its own card, while `lessons.ps1 bump`
   without `-Here` still writes there (see non_goals).
2. **Fast-forward only, never anything else (A3, A4).** `sync` runs at session start and on demand. It only runs
   `git merge --ff-only`, and it skips when local master is ahead or any ship or review is running, so a ship
   started from the main checkout does not switch to newer scripts partway through.
3. **A home for machine-local overrides (A5).** The Codex-quota workaround (a different `ReviewCommand`) moves to
   a gitignored `scripts/_config.local.ps1`. It can only change existing keys and always announces itself on
   stderr, so a swapped R3 backend stays visible without dirtying a tracked file.

Estimate: about 330 changed lines (script with self-check about 220, hook about 35, settings about 16, config and
gitignore about 15, docs about 15, this card's R5 note about 30). `depends_on` puts it after
`T0-POST-MERGE-DOCS-PR`, which also edits CLAUDE.md and DEVOPS-WORKFLOW.md and is being implemented now; its
allow_paths do not overlap `T0-POST-MERGE-LESSONS`.
