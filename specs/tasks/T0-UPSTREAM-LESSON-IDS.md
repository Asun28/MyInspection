---
id: T0-UPSTREAM-LESSON-IDS
title: Say in docs/LESSONS.md that lesson ids in code adopted from the scaffold are upstream ids, and report the collision upstream
status: merged
depends_on: []
parallelizable_with: []
allow_paths:
  - docs/LESSONS.md
  - docs/SCAFFOLD-SYNC.md
  - specs/tasks/T0-UPSTREAM-LESSON-IDS.md
forbid:
  - Editing any file under scripts/, .claude/ or .github/; the adopted files stay as adopted (user ruling 2026-09-25)
  - Changing, renumbering or adding entries in docs/lessons/LEDGER.md
non_goals:
  - Relabeling the adopted citations one by one (user ruling 2026-09-25 - rejected, every later scaffold sync would conflict on those lines)
  - Changing how the scaffold cites lesson ids; the upstream issue filed under A2 asks for that
acceptance:
  - "A1 docs/LESSONS.md has a section saying that code under scripts/, .claude/ and .github/ taken from the scaffold in PR #297 (merge d991cc92, upstream commit 96ebfcec) cites lesson ids from the upstream ledger, that the two ledgers assign ids independently so a cited id usually names a different lesson in docs/lessons/LEDGER.md, and that new local code and docs cite only local ids. It gives one checked example (scripts/selftest.ps1 gate 17ib cites L353, the upstream rule on other paths into an asserted value; the local L353 is the check-secrets naming rule), the command that tells which commit a line came from (git blame -L), the command that reads the upstream entry offline (git show 96ebfcec:docs/lessons/LEDGER.md), and the upstream ledger URL at that commit for a clone that lacks the commit"
  - "A2 An upstream issue is filed with scripts/scaffold-sync.ps1 report, read before it is sent with -Send, asking the scaffold to cite its lesson ids in a form a downstream ledger cannot collide with. docs/SCAFFOLD-SYNC.md Direction 1 gets a paragraph starting 'Upstream lesson ids' that states the collision and links that issue"
  - "A3 lessons.ps1 check passes (in the DoD). The two A1 commands are run once on the shipped checkout and their output goes in the R5 note: git blame on scripts/selftest.ps1 line 14976 names d991cc92, and git show 96ebfcec:docs/lessons/LEDGER.md has a '## L353' heading. They are not in the DoD because a clone without the upstream commit cannot run the second one"
dod_command: $l = Get-Content -Raw -LiteralPath 'docs/LESSONS.md'; $s = Get-Content -Raw -LiteralPath 'docs/SCAFFOLD-SYNC.md'; foreach ($n in @('git blame -L', 'git show 96ebfcec:docs/lessons/LEDGER.md', 'd991cc92', 'https://github.com/Asun28/claude-devops-scaffold/blob/96ebfcec2a1ff89ac77e665123978d7ae138c857/docs/lessons/LEDGER.md')) { if (-not $l.Contains($n)) { Write-Host "[DOD-FAIL] docs/LESSONS.md lacks: $n"; exit 1 } }; if ($s -notmatch 'Upstream lesson ids[^\r\n]*(\r?\n[^\r\n]+)*?https://github\.com/Asun28/claude-devops-scaffold/issues/\d+') { Write-Host '[DOD-FAIL] docs/SCAFFOLD-SYNC.md has no Upstream lesson ids paragraph linking an upstream issue'; exit 1 }; & pwsh -NoProfile -File scripts/lessons.ps1 check *> $null; if ($LASTEXITCODE -ne 0) { Write-Host '[DOD-FAIL] lessons.ps1 check'; exit 1 }; Write-Host '[DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: docs/LESSONS.md carries the four A1 anchors (the git blame command, the offline git show command, the adoption merge id and the upstream ledger URL), docs/SCAFFOLD-SYNC.md has the A2 paragraph with an upstream issue link, and lessons.ps1 check passes; prints [DOD-PASS]. On base it exits 1 with [DOD-FAIL] docs/LESSONS.md lacks: git blame -L.
review_gate: codex {verdict:pass}
budget: 60
hygiene: R4 removes each A1 anchor from docs/LESSONS.md in turn and drops the issue link from the A2 paragraph; each must turn the DoD red with its own [DOD-FAIL] line. The results go in the R5 note.
doc_sync: At R5 set status merged, update the TASK-BOARD row and add a CLAUDE.md current-stage entry (post-merge.ps1 r5).
---

# T0-UPSTREAM-LESSON-IDS

Opened on 2026-09-25 by user request, after PR #409 promoted the local L353 (check-secrets naming) and two
comments in `scripts/selftest.ps1` turned out to cite an L353 with another meaning.

## What was found

- `scripts/selftest.ps1:14976` and `:15144` (gate 17ib) cite L353 for "a green arm proves nothing when the
  asserted value has a second path in". `git blame` puts both lines in `d991cc92`, the 2026-09-11 scaffold
  adoption (PR #297), which took the code from upstream commit `96ebfcec`.
- The upstream ledger at `96ebfcec` has L353 with exactly that rule. The local L353 is the check-secrets naming
  rule (L353 was added here on 2026-09-25).
- It is not one id. On origin/master `184607a9`, 461 lines under scripts/, .claude/ and .github/ that match
  `\bL\d{1,3}\b` were introduced by `d991cc92`. Nine of the ids they cite were checked (L302, L303, L308, L313,
  L317, L325, L337, L352, L353): each matches its upstream entry at `96ebfcec` and names a different lesson in
  the local ledger.

## Decision (user, 2026-09-25)

Of three options (docs note plus upstream report; relabel the two L353 lines; relabel all adopted
citations), the user chose the docs note plus upstream report: the adopted files stay as they are, so a later
scaffold sync does not conflict on them, and the fix to the citation form belongs upstream.

## R5 delivery (2026-09-25)

Merged by [PR #416](https://github.com/Asun28/MyInspection/pull/416) as squash `35021694` (reviewed head `a95557f3`; CI run `36131605345`, `verify` and `required` success; `codex-review` success). 27 changed lines against `budget: 60`.

- RED: on base `8942e16c` the DoD exited 1 with `[DOD-FAIL] docs/LESSONS.md lacks: git blame -L`.
- A2: upstream issue [claude-devops-scaffold#400](https://github.com/Asun28/claude-devops-scaffold/issues/400), composed with `scaffold-sync.ps1 report`, read, then sent with `-Send`.
- A3, run on the shipped checkout: `git blame -L 14976,14976 -- scripts/selftest.ps1` names `d991cc92`, and `git show 96ebfcec:docs/lessons/LEDGER.md` has a `## L353` heading whose rule begins "Before trusting a green arm, ask by what OTHER paths the asserted value could reach the observation". `lessons.ps1 check` passes inside the DoD; selftest gate 16 (`-Only 16`, diagnostic only) passed.
- R4 5/5: removing `git blame -L`, the offline `git show` path, every `d991cc92`, or the upstream ledger URL from docs/LESSONS.md, or the issue link from the SCAFFOLD-SYNC paragraph, each turned the DoD red with its own `[DOD-FAIL]` line. A first `d991cc92` mutant replaced only one of its two occurrences and left the DoD green; the note still named the commit, so that mutant was equivalent and was re-aimed at both occurrences.
- R3: Codex `gpt-5.6-sol`. Round 1 passed with no findings; the CI gate then stopped on `[CI-GATE-BASE-MOVED]` (another PR merged to master), and the resumed ship's round 2 also passed with no findings.
