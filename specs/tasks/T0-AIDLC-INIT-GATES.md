---
id: T0-AIDLC-INIT-GATES
title: Register the files aidlc init added in PR 464 so selftest gates 8 and 9 pass again
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - scripts/selftest.ps1
  - docs/DELIVERY-CHAINS.md
  - CLAUDE.md
  - specs/tasks/T0-AIDLC-INIT-GATES.md
sweep: "2026-11-16 on bd9db075. The six scheduled scaffold-selftest runs on 518a806c (PR 464) listed that day, 2026-11-10 to 2026-11-15, all failed; the three read (42831732814, 43036228413, 43722890340) failed gates 8 and 9 and no other gate, on both OSes. A local full run on c81deafe reported failed=8,9 and no other gate. Gate 8.1 (selftest.ps1:2309 $RootAllow) reports evals, intent, plans, aidlc.config.json, aidlc.ops.example.json, bands.yaml, REVIEW.md. Gate 9j reads the trigger-layer row of docs/DELIVERY-CHAINS.md:23 and reports aidlc-loop, diagnose, grilling, merge-conflicts, secure-api-review, tdd. CLAUDE.md:22-23 lists the allowed top-level files and names none of the seven."
forbid:
  - Moving, renaming or deleting any file aidlc init added
  - Changing gate 8 or gate 9 logic, or adding an exemption to either gate
  - Editing the aidlc section of CLAUDE.md
non_goals:
  - The other CLAUDE.md skill indexes (交付层 bullets), which are pointers, not the gate 9j source
  - Gate 9 warnings that do not fail the gate
acceptance:
  - "A1 $RootAllow in scripts/selftest.ps1 lists the seven top-level entries aidlc init added (evals, intent, plans, aidlc.config.json, aidlc.ops.example.json, bands.yaml, REVIEW.md), so gate 8.1 no longer reports them."
  - "A2 The .claude/skills/{...} list in the trigger-layer row of docs/DELIVERY-CHAINS.md names aidlc-loop, diagnose, grilling, merge-conflicts, secure-api-review and tdd, with one note saying aidlc init laid them down, so gate 9j no longer reports them."
  - "A3 The CLAUDE.md root-file rule names the same four files and three directories, so the written rule and $RootAllow agree."
  - "A4 selftest -Only 8,9 passes, and the Tier-S full selftest passes on the shipped candidate."
dod_command: $PSNativeCommandUseErrorActionPreference = $false; $o = (& pwsh -NoProfile -File scripts/selftest.ps1 -Only 8,9 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -ne 0 -or -not $o.Contains('[SELFTEST-ONLY-PASS]')) { Write-Host ($o.Substring([Math]::Max(0, $o.Length - 3000))); Write-Host "[DOD-FAIL] selftest -Only 8,9 rc=$rc"; exit 1 }; $c = Get-Content -Raw -LiteralPath CLAUDE.md -Encoding utf8; foreach ($n in @('aidlc.config.json', 'aidlc.ops.example.json', 'bands.yaml', 'REVIEW.md', 'intent/', 'plans/', 'evals/')) { if (-not ([regex]::Match($c, '(?m)^- 根目录\*\*只允许\*\*[^\n]*\n[^\n]*').Value.Contains($n))) { Write-Host "[DOD-FAIL] the CLAUDE.md root-file rule does not name $n"; exit 1 } }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: selftest gates 8 and 9 pass and the two-line CLAUDE.md root-file rule names all seven entries; prints [DOD-PASS]. On base it exits 1 because gate 8 reports the seven entries.
review_gate: codex {verdict:pass}
budget: 45
hygiene: R4, recorded in the R5 note - put back the base $RootAllow, the base trigger-layer row and the base CLAUDE.md rule one at a time; each must make the DoD exit 1.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add the CLAUDE.md current-stage entry.
---

# T0-AIDLC-INIT-GATES

## Why now

PR 464 (`aidlc init --ship-path scaffold`, 518a806c, 2026-11-06) added four top-level files, three top-level
directories and six skills. It merged on CI (`verify`, `required`); the full selftest runs only on master pushes
and on the nightly schedule; the six nightlies from 2026-11-10 to 2026-11-15 failed, the three read on gates 8
and 9 only. Every Tier-S card needs a green
full selftest on its candidate, so no Tier-S card can ship until these two gates know aidlc's files. On
2026-11-16 the full selftest for `T0-WORKTREE-ROOT-UNIFY` (c81deafe) failed on these two gates and passed every
other gate.

The files stay where aidlc put them: aidlc reads `aidlc.config.json` from the repository root and owns the
`intent/`, `plans/` and `evals/` layout (CLAUDE.md section "AI-native SDLC (aidlc)").
