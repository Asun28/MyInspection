---
id: T0-ARCHIVE-TARGETED-CARDS-REGISTER
title: Register the bounded targeted-card archive contract without implementing it
status: todo
branch: T0-ARCHIVE-TARGETED-CARDS-REGISTER
worktree: C:\wt\T0-ARCHIVE-TARGETED-CARDS-REGISTER
depends_on: []
allow_paths:
  - specs/tasks/T0-ARCHIVE-TARGETED-CARDS-REGISTER.md
  - specs/tasks/T0-ARCHIVE-TARGETED-CARDS.md
  - docs/TASK-BOARD.md
budget: 750
tier: 0
review_gate: codex {verdict:pass}
acceptance:
  - "A1 The PR adds exactly this registration card and T0-ARCHIVE-TARGETED-CARDS.md and adds only their two W0 todo rows to the existing eight-column main table in docs/TASK-BOARD.md. These are the only three changed files; all pre-existing Board rows remain byte-identical. Both cards use canonical IDs and pass check-cards; each has exactly one main-table Board row for normal R5."
  - "A2 The future implementation card remains todo, permits only archive.ps1, existing selftest.ps1 and the two existing specs README files, and defines all six acceptance items covering selection, validation, side effects, original helpers, behavior tests and failure semantics."
  - "A3 This PR registers a future capability only: no implementation, behavioral RED/GREEN, archive action, Android feature or product-count increment is claimed. Its formal R3 judges this registration contract; future implementation needs separate R1, RED, GREEN, Tier-S proof and normal task ship."
  - "A4 Original-D review.ps1 produces an actual independent pass for this branch/card and complete three-file diff, with no routed skip; exact reviewed HEAD has successful pull_request ci.yml workflow/run-attempt, complete successful jobs and required fan-in for this PR. Recheck base/head and exact scope before authorized squash merge."
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ARCHIVE-TARGETED-CARDS-REGISTER; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ARCHIVE-TARGETED-CARDS; if ($LASTEXITCODE -ne 0) { exit 1 }; . ./scripts/_cards.ps1; $fm = Get-FrontMatter (Get-Content -LiteralPath specs/tasks/T0-ARCHIVE-TARGETED-CARDS.md -Raw); if ((Get-UncommentedValue (Get-Scalar $fm 'status')) -cne 'todo') { exit 1 }; $paths = @(Get-YamlListItems $fm 'allow_paths'); $want = @('scripts/archive.ps1','scripts/selftest.ps1','specs/archive/README.md','specs/README.md'); if ($paths.Count -ne 4 -or @($want | Where-Object { $paths -cnotcontains $_ }).Count -ne 0) { exit 1 }; if ((Get-YamlListCount $fm 'acceptance') -ne 6) { exit 1 }; if ((Get-FileHash -LiteralPath specs/tasks/T0-ARCHIVE-TARGETED-CARDS.md -Algorithm SHA256).Hash -cne 'B780289226D5DC1D0FA077D8882E3D6E6FD46ECE85E5A08B8126B42D48A67D25') { Write-Host '[ARCHIVE-REGISTER-FUTURE-HASH]'; exit 1 }; $rfm = Get-FrontMatter (Get-Content -LiteralPath specs/tasks/T0-ARCHIVE-TARGETED-CARDS-REGISTER.md -Raw); $lifecycle = @(Get-YamlListItems $rfm 'acceptance' | Where-Object { $_.StartsWith('A3 ', [StringComparison]::Ordinal) }); if ($lifecycle.Count -ne 1 -or -not [string]::Equals($lifecycle[0], 'A3 This PR registers a future capability only: no implementation, behavioral RED/GREEN, archive action, Android feature or product-count increment is claimed. Its formal R3 judges this registration contract; future implementation needs separate R1, RED, GREEN, Tier-S proof and normal task ship.', [StringComparison]::Ordinal)) { Write-Host '[ARCHIVE-REGISTER-LIFECYCLE]'; exit 1 }; $ids = @('T0-ARCHIVE-TARGETED-CARDS-REGISTER','T0-ARCHIVE-TARGETED-CARDS'); $scope = @(git diff --name-status origin/master --); if ($LASTEXITCODE -ne 0 -or $scope.Count -ne 3) { exit 1 }; foreach ($entry in @('M' + [char]9 + 'docs/TASK-BOARD.md') + @($ids | ForEach-Object { 'A' + [char]9 + 'specs/tasks/' + $_ + '.md' })) { if ($scope -cnotcontains $entry) { exit 1 } }; $delta = @(git diff --numstat origin/master -- docs/TASK-BOARD.md); if ($LASTEXITCODE -ne 0 -or $delta.Count -ne 1 -or $delta[0] -cne ('2' + [char]9 + '0' + [char]9 + 'docs/TASK-BOARD.md')) { exit 1 }; $rows = @(Get-Content -LiteralPath docs/TASK-BOARD.md); $header = '| 波 | 卡 id | 产出（一句话） | depends_on | 难度 | 首选模型 · effort | 备选 | 卡片状态 / 备注 |'; $top = [Array]::IndexOf($rows, $header); if ($top -lt 0 -or $rows[$top + 1] -cne '|---|---|---|---|---|---|---|---|') { exit 1 }; $end = $top + 2; while ($end -lt $rows.Count -and $rows[$end].StartsWith('|')) { $end++ }; foreach ($id in $ids) { $hits = @(for ($i = 0; $i -lt $rows.Count; $i++) { if ($rows[$i].StartsWith('|')) { $cells = $rows[$i].Trim().Trim('|').Split('|'); if ($cells.Count -ge 2 -and [string]::Equals($cells[1].Trim(), $id, [StringComparison]::Ordinal)) { $i } } }); if ($hits.Count -ne 1 -or $hits[0] -le ($top + 1) -or $hits[0] -ge $end) { exit 1 }; $cells = $rows[$hits[0]].Trim().Trim('|').Split('|'); if ($cells.Count -ne 8 -or $cells[0].Trim() -cne 'W0' -or $cells[7].Trim() -cnotmatch '^\*\*todo\*\*(?:：|$)') { exit 1 } }; Write-Host '[ARCHIVE-REGISTER-DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: Both cards pass the existing shape validator and the future card retains todo, exact four-path implementation scope and six acceptance items; the independently reviewed future UTF-8/LF/no-BOM bytes are bound by SHA256, A3 lifecycle wording is checked exactly, and the complete three-path diff has exactly two added W0 todo main-table Board rows. Runtime capability is not tested or claimed here.
forbid:
  - Production implementation, real archive moves, controller changes or old card amendments
  - Direct master push, fabricated T24 credentials, skipped R3 or weakened CI
non_goals:
  - Delivering the future archive feature or increasing any product count
  - Providing a reusable bootstrap exception for other cards
hygiene: Reuse existing card validators; no new behavior tests in this metadata-only PR. The future implementation card owns the real selftest12e fixtures.
doc_sync: After merge, use normal post-merge r5 for this registration card and its existing Board row; retain exact delivery evidence and keep the future implementation card and its Board row todo.
---

# T0-ARCHIVE-TARGETED-CARDS-REGISTER

This is the single-use registration contract authorized by root on 2026-10-04 and corrected before formal review to include the two required Board rows, under the user's permission for necessary delivery splits and normal R3/CI, with the prohibition on whole-master push unchanged. It creates the origin baseline contract needed by task.ps1; it does not deliver the archive behavior.

Only this PR may use the approved bootstrap sequence rather than task ship: exactly two added card paths plus only their two new Board rows, static card and scope checks, original-D review.ps1 real independent R3, exact-head CI, final base/head/scope recheck and ordinary squash merge. No round reset or generic no-card review. Review branch must exactly equal this card ID so the existing worktree-card fallback selects this registration contract. The absence of the future implementation is intentional scope, not a passed implementation acceptance test.

DoD verifies contract shape, seals the complete reviewed future-card bytes with SHA256, checks A3 lifecycle wording, and verifies the exact three-path diff plus both added main-table identities; A2 semantic completeness, A3 truthful status and A4 external delivery evidence also require the registration workflow and R3. The byte seal prevents removing or substituting contract semantics after independent review; it is registration integrity evidence, not runtime behavior evidence. No claim that the DoD alone proves R3 or CI. If refs, scope or reviewed head change, invalidate old bindings and re-verify; never waive a failed result.

After merge, retain actual R3/CI/PR/head/base/merge records and keep the future card todo. Standard cleanup can use its existing online MERGED+exact-head fallback when no task-generated T24 exists; never forge one or pass Force to manufacture success. Then the distinct implementation ID starts from the merged origin card and follows normal task.ps1 R1/RED/ship. Registration and implementation have separate branches, review histories and round counters.
