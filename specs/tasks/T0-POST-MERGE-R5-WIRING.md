---
id: T0-POST-MERGE-R5-WIRING
title: post-merge.ps1 -SelfCheck checks the commands, parameters and variables its plumbing uses
status: todo
depends_on: [T0-POST-MERGE-R5-BOARD-TABLE]
parallelizable_with: []
allow_paths:
  - scripts/post-merge.ps1
  - specs/tasks/T0-POST-MERGE-R5-WIRING.md
forbid:
  - Loosening any guard T0-POST-MERGE-DOCS-PR or T0-POST-MERGE-R5-BOARD-TABLE delivered, or widening the direct-merge allowlist
  - Changing scripts/_guard.ps1, scripts/_ci.ps1, scripts/task.ps1 or any existing gate
non_goals:
  - -DryRun (T0-POST-MERGE-R5-GUARDS)
  - Running the git or gh plumbing itself inside -SelfCheck
acceptance:
  - "A1 post-merge.ps1 -SelfCheck also checks the wiring: after loading _guard.ps1 and _ci.ps1, every Verb-Noun command in post-merge.ps1 resolves, every named parameter it passes exists on that command, and every Scaffold* variable it reads is defined"
  - "A2 The check reports each of the three kinds on a small script text that has one defect of each kind, so each arm has a single-statement mutation that fails a named SelfCheck case; one more mutation renames a helper post-merge.ps1 calls and fails the check on the file itself. All are recorded in this card with their killing cases, the file restored by SHA-256"
  - "A3 A failing check names every unresolved command, command parameter and variable in one message, so one run shows every break"
dod_command: pwsh -NoProfile -File scripts/post-merge.ps1 -SelfCheck; if ($LASTEXITCODE -ne 0) { exit 1 }; exit 0
dod_exit: 0
dod_assert: the SelfCheck passes against the production functions, including the wiring check on post-merge.ps1 itself and on the defect script text, and prints [POST-MERGE-SELF-CHECK-PASS]
review_gate: codex {verdict:pass}
budget: 120
hygiene: single-statement mutations for the wiring check's command, parameter and variable arms, and one renamed helper, file restored by SHA-256
doc_sync: TASK-BOARD
---

# T0-POST-MERGE-R5-WIRING

Second of three PRs split from `T0-POST-MERGE-R5-GUARDS` (its A2) on 2026-09-25, when the user asked for that
card's three fixes to be delivered as separate PRs.

`-SelfCheck` covers the pure functions only, so a renamed helper or parameter in `_guard.ps1` or `_ci.ps1`
leaves the DoD green while every live r5 or prune fails. This card makes the SelfCheck resolve what the plumbing
calls, without running it.

R3: Opus 5.5 through `ReviewCommand` instead of Codex (user ruling 2026-09-25).

## Implementation record

- RED: the six wiring cases were written first. Against the file without `Get-PostMergeWiringIssues`, 6 of 81
  SelfCheck cases failed: two threw because the function did not exist, and four failed their own assertions on an
  empty result. GREEN: 81 of 81.
- The wiring cases run last in the SelfCheck, after it dot-sources `_guard.ps1` (which loads `_config.ps1` and
  `_lessons.ps1`) and `_ci.ps1`. At load time those files define functions and variables only. The defect text is
  single-quoted: a variable inside a double-quoted string is part of this file's AST and would be checked too.
- Scope of the check: Verb-Noun command names, the named parameters written after them, and `Scaffold*` variables.
  It does not check native tools (`git`, `gh`), splatted or positional arguments, or commands reached through a
  scriptblock variable such as `& $Call`.
- A3: with `_guard.ps1` not loaded (mutant W7), the SelfCheck prints one failing case naming both breaks:
  `wiring: every command, parameter and Scaffold* variable in this file resolves (threw: unresolved: command
  Assert-PersonalAccount, command Get-ScaffoldWorktreeRoot)`.
- R4 (A2): 10 single-statement mutants. Each made `-SelfCheck` exit 1 with its named case failing and no parse error;
  the file was restored after each and ended at SHA-256 `5131466CE3E25D45CD30BF9B1681FA3BD183115832BB42CE78A5A48329EFCF03`.
  W1-W3 are the three arms, W4-W6 rename a helper, a parameter and a variable that the plumbing uses, W7-W8 drop
  one of the two library loads, W9 keeps only the first break, and W10 drops the Verb-Noun filter.

| id | statement | mutation | killing case |
|---|---|---|---|
| W1 | `if (-not $cmd) { $bad.Add("command $name"); continue }` | `if (-not $cmd) { continue }` | wiring: an unknown command is reported |
| W2 | `if (-not $cmd.Parameters.ContainsKey($pa.ParameterName)) { $bad.Add("parameter $name -$($pa.ParameterName)") }` | (deleted) | wiring: an unknown parameter is reported |
| W3 | `if (-not (Get-Variable -Name $v.VariablePath.UserPath -ErrorAction SilentlyContinue)) { $bad.Add("variable $($v.VariablePath.UserPath)") }` | (deleted) | wiring: an undefined Scaffold* variable is reported |
| W4 | `$wt = Join-Path (Get-ScaffoldWorktreeRoot) $branchName` | `$wt = Join-Path (Get-ScaffoldWorktreeRootRenamed) $branchName` | wiring: every command, parameter and Scaffold* variable in this file resolves |
| W5 | `Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemote; Invoke-PostMergePrune` | `Assert-PersonalAccount -RepoRoot $RepoRoot -CheckRemoteRenamed; Invoke-PostMergePrune` | wiring: every command, parameter and Scaffold* variable in this file resolves |
| W6 | `-ceq $ScaffoldCiFanInJob })` | `-ceq $ScaffoldCiFanInJobRenamed })` | wiring: every command, parameter and Scaffold* variable in this file resolves |
| W7 | `. (Join-Path $PSScriptRoot '_guard.ps1'); . (Join-Path $PSScriptRoot '_ci.ps1')` | `. (Join-Path $PSScriptRoot '_ci.ps1')` | wiring: every command, parameter and Scaffold* variable in this file resolves |
| W8 | `. (Join-Path $PSScriptRoot '_guard.ps1'); . (Join-Path $PSScriptRoot '_ci.ps1')` | `. (Join-Path $PSScriptRoot '_guard.ps1')` | wiring: every command, parameter and Scaffold* variable in this file resolves |
| W9 | `return @($bad \| Sort-Object -Unique)` | `return @($bad \| Select-Object -First 1)` | wiring: one run names every break |
| W10 | `if ("$name" -cnotmatch '^[A-Za-z]+-[A-Za-z]+$') { continue }` | (deleted) | wiring: every command, parameter and Scaffold* variable in this file resolves |
