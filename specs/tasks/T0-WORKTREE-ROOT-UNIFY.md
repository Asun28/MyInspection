---
id: T0-WORKTREE-ROOT-UNIFY
title: Point task.ps1 and the aidlc loop at one worktree root, D:\wt\MyInspection
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/_config.ps1
  - aidlc.config.json
  - specs/tasks/T0-WORKTREE-ROOT-UNIFY.md
sweep: "Grep for C:\\wt, C:/wt, WorktreeRoot and worktreeRoot over tracked files (2026-11-16, 518a806c): scripts/_config.ps1:51 sets WorktreeRoot to the empty string, so task.ps1 creates C:\\wt\\<id>; aidlc.config.json:8 sets worktreeRoot to the empty string, so aidlc resolves the repository-scoped default C:\\wt\\MyInspection\\<id>. The other hits describe the empty default (task.ps1:102 and :1054, selftest.ps1:101 and :8661, specs/tasks/_TEMPLATE.md, docs) or are card front matter worktree fields, which check-cards.ps1:398 reads only for their last segment; all of them stay true, so they stay."
forbid:
  - Changing Get-ScaffoldWorktreeRoot, task.ps1, selftest.ps1 or any other script logic
  - Changing the default used when WorktreeRoot is empty
  - Moving, removing or repairing worktrees inside this card's diff
non_goals:
  - Moving the Gradle user home, the selftest temp root or Codex's own worktrees off C:
  - Moving the existing worktrees under C:\wt (done after merge with robocopy and git worktree repair, outside git history)
acceptance:
  - "A1 scripts/_config.ps1 sets WorktreeRoot = 'D:\\wt\\MyInspection', so on Windows Get-ScaffoldWorktreeRoot returns D:\\wt\\MyInspection and task.ps1 -Phase start creates D:\\wt\\MyInspection\\<id>."
  - "A2 aidlc.config.json sets worktreeRoot to the same string, so aidlc's card probe expects the directory task.ps1 creates. On base aidlc expects C:\\wt\\MyInspection\\<id> while task.ps1 creates C:\\wt\\<id>, and aidlc card next stops every card with reason ownership."
  - "A3 The full selftest (Tier S, all 17 gates) passes on the shipped candidate; gate 15 injects its own temporary root, and off Windows Get-ScaffoldWorktreeRoot still ignores a drive-shaped value (T218), so Linux CI keeps ~/.wt. Evidence form: the implementation record carries a full run on a commit C (launch and end HEAD, an empty git status --porcelain --untracked-files=all, each shard's exit and [GATE-RESULT-SUMMARY] failed=none, selftest: PASS), and git diff --stat C HEAD lists only this card; a commit cannot carry the output of a run on itself, and the reviewer reads only the diff (L227)."
dod_command: $ps = pwsh -NoProfile -Command ". ./scripts/_config.ps1; Get-ScaffoldWorktreeRoot"; if ($LASTEXITCODE -ne 0 -or $ps -cne 'D:\wt\MyInspection') { Write-Host "[DOD-FAIL] Get-ScaffoldWorktreeRoot returned '$ps'"; exit 1 }; $aj = (Get-Content -Raw -LiteralPath aidlc.config.json | ConvertFrom-Json).worktreeRoot; if ($aj -cne $ps) { Write-Host "[DOD-FAIL] aidlc.config.json worktreeRoot '$aj' differs from '$ps'"; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: Get-ScaffoldWorktreeRoot returns D:\wt\MyInspection and aidlc.config.json worktreeRoot is the same string; prints [DOD-PASS]. On base it exits 1 at the first check because the accessor returns C:\wt.
review_gate: codex {verdict:pass}
budget: 50
hygiene: R4, recorded in the R5 note - put back each of the two empty values one at a time; each must make the DoD exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-WORKTREE-ROOT-UNIFY

## Why now

PR 464 (`aidlc init --ship-path scaffold`, 518a806c) laid the aidlc loop over this repository with an empty
`worktreeRoot`. aidlc resolves an empty root to `%SystemDrive%\wt\<repository name>`, so it expects
`C:\wt\MyInspection\<id>`. `task.ps1` resolves its own empty `WorktreeRoot` to `C:\wt` and creates
`C:\wt\<id>`. On 2026-11-16 `aidlc card next T0-AGP-BUILTIN-KOTLIN` stopped with reason ownership: "worktree for
T0-AGP-BUILTIN-KOTLIN is at C:\wt\T0-AGP-BUILTIN-KOTLIN, expected C:\wt\MyInspection\T0-AGP-BUILTIN-KOTLIN". No card
can move through the loop until the two tools agree.

The root moves to D: because C: had 3.7 GB free on 2026-11-16 and `C:\wt` held 3.4 GB; D: had 28 GB free. The aidlc
init worktree already sits at `D:\wt\MyInspection\aidlc-init`. The user asked the same day for the old worktrees
under `C:\wt` to move to the new location.

## After merge (not in the diff)

Move this repository's registered worktrees from `C:\wt\<name>` to `D:\wt\MyInspection\<name>` with
`robocopy /E /MOVE` and then `git worktree repair <new path>`; `git worktree move` cannot cross drives on Windows.
Run `scripts/live-work.ps1` first and leave any worktree with changes from the last 48 hours in place (L218).
Directories under `C:\wt` that this repository does not register stay where they are.

## Implementation record (2026-11-16)

- Change: `scripts/_config.ps1` sets `WorktreeRoot = 'D:\wt\MyInspection'` with one comment line saying
  `aidlc.config.json` must hold the same string; `aidlc.config.json` sets `"worktreeRoot": "D:\\wt\\MyInspection"`.
  3 insertions, 2 deletions outside this card.
- RED: `task.ps1 -Phase red` on 1455c072 printed `[DOD-FAIL] Get-ScaffoldWorktreeRoot returned 'C:\wt'` and exited 1.
- DoD: `[DOD-PASS]`, exit 0.
- R4 (hygiene): the runner first rewrote both files through its own byte path (SHA-256 unchanged) and saw the DoD
  pass. M1 put back `WorktreeRoot = ''`: DoD exit 1, `[DOD-FAIL] Get-ScaffoldWorktreeRoot returned 'C:\wt'`.
  M2 put back `"worktreeRoot": ""`: DoD exit 1, `[DOD-FAIL] aidlc.config.json worktreeRoot '' differs from
  'D:\wt\MyInspection'`. 2/2 killed; both files restored to SHA-256 `1159885C...D2D9DA` (`_config.ps1`) and
  `50D319A6...6BAE4A` (`aidlc.config.json`).
- Tier S (A3): `selftest.ps1 -Parallel` (all 17 gates, five shards) from this worktree on C = `f1e7a9e5`, the
  merge of origin/master `83a15e1e`. Wrapper lines and each shard's header and summary (summary cut after `wall=`):
  ```text
  head=f1e7a9e52cd8887178b2107aad0bbaa17e87e8f9 status=[] start=2026-11-17T10:23:09.4652514+13:00
  ===== [SELFTEST-PARALLEL] 分片 light（-Only 1,2,3,4,5,6,7,8,9,10,11,12,13,14,16）exit=0 · 完整日志 =====
  [GATE-RESULT-SUMMARY] failed=none ran=15 wall=1144.1s
  ===== [SELFTEST-PARALLEL] 分片 e2e（-Only 15）exit=0 · 完整日志 =====
  [GATE-RESULT-SUMMARY] failed=none ran=1 wall=1335.1s
  ===== [SELFTEST-PARALLEL] 分片 seed-pre（-Only 17pre）exit=0 · 完整日志 =====
  [GATE-RESULT-SUMMARY] failed=none ran=1 wall=1029.7s
  ===== [SELFTEST-PARALLEL] 分片 seed-post（-Only 17post）exit=0 · 完整日志 =====
  [GATE-RESULT-SUMMARY] failed=none ran=1 wall=1955.2s
  ===== [SELFTEST-PARALLEL] 分片 seed-b（-Only 17t,17ac）exit=0 · 完整日志 =====
  [GATE-RESULT-SUMMARY] failed=none ran=1 wall=664.9s
  selftest: PASS
  exit=0 end=2026-11-17T10:56:15.8946922+13:00 headAfter=f1e7a9e52cd8887178b2107aad0bbaa17e87e8f9 statusAfter=[]
  ```
  `status=[]` is `git status --porcelain --untracked-files=all`; `git diff --stat f1e7a9e5 HEAD` lists only this card.
  An earlier run on C failed one 17post case on its 25 s wall-time bound (`T37-CIGATE/DEADLINE` git-fetch-hang) and
  passed every other gate.
