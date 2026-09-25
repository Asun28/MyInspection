---
id: T0-CLAUDE-MD-L360-TD190
title: Promote L360 into CLAUDE.md's must-load lessons, and correct the selftest -TaskId line (TD190)
status: todo
depends_on: []
parallelizable_with: []
allow_paths:
  - CLAUDE.md
  - docs/lessons/LEDGER.md
  - docs/lessons/powershell-and-gh.md
  - specs/tech-debt-tracker.md
  - specs/tasks/T0-CLAUDE-MD-L360-TD190.md
forbid:
  - Changing any lesson's symptom, root cause or rule, or any tier or recurrence other than those A1 and A3 name
  - Changing scripts/selftest.ps1 or any other script
  - Editing CLAUDE.md outside its must-load lessons section and its workflow-selftest bullet
non_goals:
  - Adding a -Base parameter to selftest.ps1 (the other fix TD190 names)
  - The DoD of T0-RECEIPT-LOSS-SOURCE-CONTRACT, which passes a -Shard parameter that selftest.ps1 does not have
acceptance:
  - "A1 CLAUDE.md's must-load lessons section holds L360 in place of L267, still 10 resident ids (user ruling 2026-09-25 to promote L360; L21 and L267 are tied least active at recurrence 2, and L360's rule already holds L267's core, that a mutant run which did not parse is never a kill). In the ledger L360 becomes tier must and L267 tier ondemand, and L267 gets a line in docs/lessons/powershell-and-gh.md. lessons.ps1 check passes"
  - "A2 CLAUDE.md's workflow-selftest bullet no longer tells the reader to pass -Base to selftest.ps1, and describes -TaskId as scripts/selftest.ps1's own parameter help does: the card is read from the base commit the script detects, the tier and [GATE-MAP] choose the gates, and a card's evidence comes from the copy in its worktree (L344). The clause about product paths being reported as not applicable goes, since the current router has no such rule"
  - "A3 TD190 is marked paid with this card as its pointer, and L344 (selftest.ps1 has no -Base) gets one recurrence"
dod_command: pwsh -NoProfile -File scripts/lessons.ps1 check; if ($LASTEXITCODE -ne 0) { exit 1 }; $c = [IO.File]::ReadAllText('CLAUDE.md'); if ($c.Contains('-TaskId <id> -Base') -or $c.Contains('[L267]') -or -not $c.Contains('[L360]')) { exit 1 }; exit 0
dod_exit: 0
dod_assert: lessons.ps1 check passes (resident ids within the cap of 10, tiers and CLAUDE.md agree), CLAUDE.md names L360 and not L267 as a resident id, and no longer shows -TaskId <id> -Base
review_gate: codex {verdict:pass}
budget: 60
hygiene: none; the DoD's three text checks each fail on today's master (RED)
doc_sync: TASK-BOARD
---

# T0-CLAUDE-MD-L360-TD190

Opened on 2026-09-25 at the user's request after `T0-POST-MERGE-R5-GUARDS` was delivered as three PRs: promote
L360 (its recurrence reached 2 in #404) and fix TD190.

R3: Opus 5.5 through `ReviewCommand` instead of Codex, as for the three `T0-POST-MERGE-R5-*` cards.