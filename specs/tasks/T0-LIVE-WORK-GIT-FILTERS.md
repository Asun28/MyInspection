---
id: T0-LIVE-WORK-GIT-FILTERS
title: Keep repository filter drivers from running when the live-work probe or the dirty-worktree hook checks a worktree
status: todo
depends_on: [T0-DIRTY-WORKTREE-HOOK]
parallelizable_with: []
allow_paths:
  - scripts/live-work.ps1
  - .claude/hooks/guard-dirty-worktree.ps1
  - specs/tasks/T0-LIVE-WORK-GIT-FILTERS.md
forbid:
  - Changing which git commands the hook asks about or how it reads them (T0-DIRTY-WORKTREE-HOOK)
  - Fetching, pushing or any network call in the probe or the hook
  - Writing to any worktree, branch, ref or file from the probe or the hook
non_goals:
  - Hooks a repository configures for commands other than status, ls-files, rev-parse and branch (the probe and the hook run no others)
acceptance:
  - "A1 When the hook or the probe checks a worktree, no filter driver the repository configures runs (filter.<name>.clean, smudge or process): a fixture whose clean filter writes a marker file leaves no marker after the hook asks about that worktree and after the probe summarises it. Where the count then differs from git status with the filter (a file the filter would clean counts as changed), the hook header and the probe's help say so"
  - "A2 live-work.ps1 -SelfCheck has a case named 'hook: a repository clean filter does not run' and one named 'summary: a repository clean filter does not run', each with the marker fixture of A1; the hook's deadline case still passes, with a slow git call that does not depend on a filter (T0-DIRTY-WORKTREE-HOOK's deadline case sleeps in a clean filter)"
  - "A3 The selftest acceptance of the card's computed tier passes on the shipped candidate"
dod_command: $o = (& pwsh -NoProfile -File scripts/live-work.ps1 -SelfCheck 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[LIVE-WORK-SELF-CHECK-PASS]')) { Write-Host $o; Write-Host "[DOD-FAIL] self-check rc=$rc"; exit 1 }; $t = Get-Content -Raw -LiteralPath 'scripts/live-work.ps1'; foreach ($n in @('hook: a repository clean filter does not run', 'summary: a repository clean filter does not run')) { if (-not $t.Contains($n)) { Write-Host "[DOD-FAIL] no self-check case '$n'"; exit 1 } }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The self-check passes and contains both marker cases (A1, A2); prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] no self-check case 'hook: a repository clean filter does not run'.
review_gate: codex {verdict:pass}
budget: 250
hygiene: R4 runs single-statement mutants against the self-check, each recorded in the R5 note with the case that caught it - drop the filter override from the hook's git calls, drop it from the probe's git calls, make the deadline case's slow call return at once; every one must make the self-check fail.
doc_sync: At R5 set status merged, update the TASK-BOARD row and the CLAUDE.md current-stage entry.
---

# T0-LIVE-WORK-GIT-FILTERS

Split from `T0-DIRTY-WORKTREE-HOOK` on 2026-10-12 (user ruling after Codex R3 round 2 on PR #455). That round's
standards finding: the hook turns `core.fsmonitor` off for its git calls, but `git status` still runs a clean filter
the repository configures, so a filter that writes a file or calls the network runs merely because the hook checked
the worktree. The SessionStart probe runs the same `git status`, so the fix belongs to both, and the hook's
deadline case, which sleeps inside a clean filter, needs another way to make git slow.
