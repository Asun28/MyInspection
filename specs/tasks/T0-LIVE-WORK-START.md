---
id: T0-LIVE-WORK-START
title: Stop task.ps1 start from overrunning work another session holds, and tell every session to check live work first
status: todo
depends_on: [T0-LIVE-WORK-GUARD]
parallelizable_with: []
allow_paths:
  - scripts/task.ps1
  - scripts/selftest.ps1
  - docs/DEVOPS-WORKFLOW.md
  - CLAUDE.md
  - .claude/skills/task-loop/SKILL.md
  - docs/HANDOFF.md
  - specs/tasks/T0-LIVE-WORK-START.md
sweep: "rg 'L218|L273|活跃写者|他会话|并行会话|other session|another session|live work' over CLAUDE.md, docs/HANDOFF.md, docs/DEVOPS-WORKFLOW.md, .claude/skills/task-loop/SKILL.md, .claude/hooks/*.ps1 and scripts/task.ps1 (2026-09-25, origin/master cf1bd18a, carried over from T0-LIVE-WORK-GUARD on the 2026-10-07 split). The only hit is task.ps1:1146, the ship-time [SHIP-CONCURRENT-SESSION] notice, which looks at the main checkout and at worktree directory names only; this card leaves it as is. No teaching surface states L218 or L273: CLAUDE.md's execution boundary and the task-loop skill's 前置 get one rule line each, docs/HANDOFF.md gets the per-task handoff rule. scripts/selftest.ps1: gate 15's fixture starts several cards that all declare README.md while its earlier steps leave README.md edits in other fixture worktrees, so those later starts pass -TakeOver (user ruling 2026-09-25), and gate 15 gains the executable start cases (A3). docs/DEVOPS-WORKFLOW.md: _config.ps1 DocSyncMap couples scripts/task.ps1 to it, so its R1 row gets one sentence on start's live-work check (user ruling 2026-09-26). Its multi-session text stays with T0-MAIN-CHECKOUT-READONLY and T0-POST-MERGE-CARD-DRIFT."
forbid:
  - Changing scripts/live-work.ps1 (T0-LIVE-WORK-GUARD owns it)
  - Changing check-cards.ps1, review.ps1, post-merge.ps1, lessons.ps1, _scope.ps1 or the scope and budget gates
  - A -TakeOver that continues past a probe failure (live-work.ps1 exit 2)
non_goals:
  - The probe itself (T0-LIVE-WORK-GUARD)
  - The dirty-worktree ask hook (T0-DIRTY-WORKTREE-HOOK)
  - Guarding the edit tools or making the main checkout read-only (T0-MAIN-CHECKOUT-READONLY)
acceptance:
  - "A1 task.ps1 -Phase start runs live-work.ps1 -TaskId <id> before git worktree add. Exit 3 stops start with [START-LIVE-WORK] and the overlap lines, unless -TakeOver is given, which prints the same lines and continues; exit 2 or any other code stops start with [START-LIVE-WORK-UNKNOWN], with or without -TakeOver. When the card's worktree already exists, the existing refusal also prints that worktree's uncommitted count and newest change time as the ASCII pair uncommitted=<n> newest=<ISO-8601> and says another session may hold it and it must not be reset; cleanup keeps its dirty-tree guard. -TakeOver is a start parameter described in task.ps1's help"
  - "A2 Selftest gate 15 keeps running: its fixture starts after those in 15a and 15b' pass -TakeOver, as does the 15r fixture helper. A new gate 15 block runs the real task.ps1 in the gate 15 fixture and prints [15LW-OK] only when all of these hold: a start whose card declares a path another fixture worktree has uncommitted exits non-zero with [START-LIVE-WORK] and creates no worktree; the same start with -TakeOver exits 0 and creates it; a start whose probe fails (a registered fixture worktree whose directory was removed) exits non-zero with [START-LIVE-WORK-UNKNOWN] and creates no worktree, also with -TakeOver; and a start of a card whose worktree exists with one uncommitted edit, with -TakeOver, refuses with uncommitted=1 and leaves the edit in place"
  - "A3 The task-loop skill's 前置, CLAUDE.md's execution boundary and docs/HANDOFF.md each tell a session to run live-work.ps1 -TaskId <id> before it starts, resumes, splits or amends a card, and live-work.ps1 before it resets, cleans or removes a worktree; when another session holds overlapping work, stop and ask the user which session owns it (L218). docs/HANDOFF.md adds that a session whose progress.md HANDOFF names another task writes its own handoff to _local/handoff-<id>.md (L273), which the probe lists"
  - "A4 docs/DEVOPS-WORKFLOW.md's R1 row says start runs the live-work check and stops on an overlap unless -TakeOver is given"
  - "A5 The Tier-S full selftest run passes on the shipped candidate"
dod_command: $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 15 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[15LW-OK]')) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 4000))); Write-Host "[DOD-FAIL] selftest -Only 15 rc=$rc or no [15LW-OK]"; exit 1 }; foreach ($f in @('CLAUDE.md', '.claude/skills/task-loop/SKILL.md', 'docs/HANDOFF.md')) { $t = Get-Content -Raw -LiteralPath $f; if (-not ($t.Contains('live-work.ps1 -TaskId') -and $t.Contains('L218'))) { Write-Host "[DOD-FAIL] $f lacks the live-work rule"; exit 1 } }; if (-not (Get-Content -Raw -LiteralPath 'docs/HANDOFF.md').Contains('_local/handoff-')) { Write-Host '[DOD-FAIL] docs/HANDOFF.md lacks the per-task handoff rule'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: Gate 15 passes with the new start block printing [15LW-OK] (A1, A2), and the three teaching surfaces carry the live-work rule and HANDOFF.md the per-task handoff rule (A3); prints [DOD-PASS]. On base it exits 1 because gate 15 prints no [15LW-OK].
review_gate: codex {verdict:pass}
budget: 300
hygiene: R4 runs single-statement mutants against selftest -Only 15, each recorded in the R5 note with the case that caught it - drop the start stop, make -TakeOver the default, let -TakeOver continue past exit 2, map exit 2 to 0 in start, drop the existing-worktree count; every one must make the [15LW-OK] block fail.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry, and fill L218's enforced_by with task.ps1 start's live-work check.
---

# T0-LIVE-WORK-START

Split from `T0-LIVE-WORK-GUARD` on 2026-10-07 (user ruling; that card's "Split" section has the reason). It
carries what that card's acceptance A3 and A6 asked for, plus the executable start cases Codex R3 found missing
on PR #447. The incident behind it is in `T0-LIVE-WORK-GUARD`'s "What happened".

## Design

1. **Check before start (A1).** Starting a card whose paths another worktree has changed, or whose branch
   already exists, stops until the user says to take it over. A probe that fails stops start even with
   `-TakeOver`, because nobody can say what the unread worktree holds.
2. **Keep the selftest fixtures running (A2).** Gate 15 starts several fixture cards that all declare
   `README.md` while its earlier steps leave `README.md` edits in other fixture worktrees, which the new check
   reads as another session's work. Those later starts pass `-TakeOver`; the starts in 15a and 15b' stay plain.
3. **Say it where it is read (A3, A4).** One rule line each in CLAUDE.md's execution boundary (always loaded)
   and the task-loop skill, the per-task handoff rule in docs/HANDOFF.md, one sentence in the R1 row.

## Evidence carried from PR #447 (combined candidate)

- **A real start run** (2026-09-26) in a `--shared` clone carrying the candidate `task.ps1` and `live-work.ps1`:
  a second worktree held an uncommitted edit to `.claude/skills/task-loop/SKILL.md`; start stopped with
  `[START-LIVE-WORK]` and created no worktree; with `-TakeOver` it continued; a third start with `-TakeOver` on
  the existing worktree refused with `（1 个未提交改动，最新改动 …）` and the edit stayed. A2 turns this into an
  executable selftest block, which matches the ASCII `uncommitted=<n>` pair rather than the Chinese text, because
  gate 15 also runs on windows-latest, whose console code page can turn the Chinese into `?` (L165, L17).
- **Gate 15** on the first candidate failed at four fixture starts (15w, 15g9, 15h3, 15h4) until those starts
  passed `-TakeOver` (#421).

## Order with other cards

`T0-MAIN-CHECKOUT-READONLY` also edits CLAUDE.md's execution boundary; whichever ships second merges
origin/master before ship and keeps both lines. `T0-DIRTY-WORKTREE-HOOK` ships after this card and adds the
hook to the execution-boundary line this card writes.
