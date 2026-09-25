---
id: T0-CLAUDE-MD-RETIRE-MERGE
title: Name post-merge.ps1 retire in CLAUDE.md's scope of merges without R3, next to r5
status: todo
depends_on: [T0-POST-MERGE-CARD-DRIFT]
parallelizable_with: []
allow_paths:
  - CLAUDE.md
  - specs/tasks/T0-CLAUDE-MD-RETIRE-MERGE.md
forbid:
  - Widening what r5 or retire may change, or naming any other command that merges without R3
  - Editing any CLAUDE.md line other than the 执行边界 bullet on merges without R3 and the 开发工作流 R5 bullet
  - Changing scripts/post-merge.ps1 or any other script
non_goals:
  - The task-loop skill and docs/DEVOPS-WORKFLOW.md lines on retire, which describe the command rather than record the ruling
acceptance:
  - "A1 CLAUDE.md's 执行边界 bullet on merges without R3 records the user ruling of 2026-09-26: a PR opened by post-merge.ps1 retire merges on CI alone and may only set the named card's status to merged with one superseded_by line, append a section to that card, and change that card's docs/TASK-BOARD.md row; anything else stops with [POST-MERGE-SCOPE] before a push. The r5 clause and its 2026-09-25 ruling stay word for word"
  - "A2 CLAUDE.md's 开发工作流 R5 bullet names post-merge.ps1 retire as the way to finish a card whose PR closed without a merge, merged on CI alone like r5"
  - "A3 No other CLAUDE.md line changes: the diff is exactly those two lines"
dod_command: $c = [IO.File]::ReadAllText((Resolve-Path 'CLAUDE.md').Path); $l = @($c -split '\r?\n'); $b = @($l | Where-Object { $_.StartsWith('- **无 R3 直接合并的范围') }); if ($b.Count -ne 1 -or -not $b[0].Contains('post-merge.ps1 r5 开的 R5 文档同步 PR 只能改本卡卡片、本卡的 docs/TASK-BOARD.md 行、CLAUDE.md「当前阶段」节内的新增行') -or -not $b[0].Contains('post-merge.ps1 retire') -or -not $b[0].Contains('superseded_by') -or -not $b[0].Contains('2026-09-26')) { Write-Host '[DOD-FAIL] A1 the no-R3 merge bullet'; exit 1 }; $r = @($l | Where-Object { $_.StartsWith('- **R5 文档同步**') }); if ($r.Count -ne 1 -or -not $r[0].Contains('post-merge.ps1 retire')) { Write-Host '[DOD-FAIL] A2 the R5 bullet'; exit 1 }; $n = (git diff --numstat (git merge-base origin/master HEAD) -- CLAUDE.md) -join ' '; if ($n -notmatch '^2\s+2\s+CLAUDE\.md$') { Write-Host "[DOD-FAIL] A3 CLAUDE.md numstat is '$n', expected two lines changed"; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: The no-R3 merge bullet keeps the r5 clause word for word and adds post-merge.ps1 retire with superseded_by and the 2026-09-26 ruling; the R5 bullet names post-merge.ps1 retire; git diff --numstat against the merge base shows exactly 2 lines changed in CLAUDE.md; prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] A1 the no-R3 merge bullet.
review_gate: codex {verdict:pass}
budget: 40
hygiene: none; each of the three DoD checks fails on today's master (RED)
doc_sync: At R5 set status merged through post-merge.ps1 r5 and update the TASK-BOARD row
---

# T0-CLAUDE-MD-RETIRE-MERGE

Opened on 2026-09-26 at the user's request after `T0-POST-MERGE-CARD-DRIFT` shipped `post-merge.ps1 retire`: retire
merges its PR on CI alone, as r5 does, but the CLAUDE.md line recording the 2026-09-25 ruling on merges without R3
names r5 only. The user ruled on 2026-09-26 that retire is covered, inside the allowlist retire enforces (the named
card's status and one `superseded_by` line, an appended section, and the card's board row).