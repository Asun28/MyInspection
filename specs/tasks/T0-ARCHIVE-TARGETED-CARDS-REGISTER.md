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
budget: 750
tier: 0
review_gate: codex {verdict:pass}
acceptance:
  - "A1 The PR adds exactly this registration card and T0-ARCHIVE-TARGETED-CARDS.md; no previous card, production script, test, index, board or seal changes. Both use canonical IDs and pass the existing check-cards command."
  - "A2 The future implementation card remains todo, permits only archive.ps1, existing selftest.ps1 and the two existing specs README files, and defines all six acceptance items covering selection, validation, side effects, original helpers, behavior tests and failure semantics."
  - "A3 This PR registers a future capability only: no implementation, behavioral RED/GREEN, archive action, Android feature or product-count increment is claimed. Its formal R3 judges this registration contract; future implementation needs separate R1, RED, GREEN, Tier-S proof and normal task ship."
  - "A4 Original-D review.ps1 produces an actual independent pass for this branch/card and complete two-card diff, with no routed skip; exact reviewed HEAD has successful pull_request ci.yml workflow/run-attempt, complete successful jobs and required fan-in for this PR. Recheck base/head and exact scope before authorized squash merge."
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ARCHIVE-TARGETED-CARDS-REGISTER; if ($LASTEXITCODE -ne 0) { exit 1 }; pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-ARCHIVE-TARGETED-CARDS; if ($LASTEXITCODE -ne 0) { exit 1 }; . ./scripts/_cards.ps1; $fm = Get-FrontMatter (Get-Content -LiteralPath specs/tasks/T0-ARCHIVE-TARGETED-CARDS.md -Raw); if ((Get-UncommentedValue (Get-Scalar $fm 'status')) -cne 'todo') { exit 1 }; $paths = @(Get-YamlListItems $fm 'allow_paths'); $want = @('scripts/archive.ps1','scripts/selftest.ps1','specs/archive/README.md','specs/README.md'); if ($paths.Count -ne 4 -or @($want | Where-Object { $paths -cnotcontains $_ }).Count -ne 0) { exit 1 }; if ((Get-YamlListCount $fm 'acceptance') -ne 6) { exit 1 }; Write-Host '[ARCHIVE-REGISTER-DOD-PASS]'; exit 0
dod_exit: 0
dod_assert: Both cards pass the existing shape validator and the future card retains todo, exact four-path implementation scope and six acceptance items; runtime capability is not tested or claimed here.
forbid:
  - Production implementation, real archive moves, controller changes or old card amendments
  - Direct master push, fabricated T24 credentials, skipped R3 or weakened CI
non_goals:
  - Delivering the future archive feature or increasing any product count
  - Providing a reusable bootstrap exception for other cards
hygiene: Reuse existing card validators; no new behavior tests in this metadata-only PR. The future implementation card owns the real selftest12e fixtures.
doc_sync: Registration evidence and this card's later merged status follow the root-authorized documentation route; the future implementation card stays todo.
---

# T0-ARCHIVE-TARGETED-CARDS-REGISTER

This is the single-use registration contract authorized by root on 2026-10-04, under the user's permission for necessary delivery splits and normal R3/CI, with the prohibition on whole-master push unchanged. It creates the origin baseline contract needed by task.ps1; it does not deliver the archive behavior.

Only this PR may use the approved bootstrap sequence rather than task ship: exact two added card paths, static card checks, original-D review.ps1 real independent R3, exact-head CI, final base/head/scope recheck and ordinary squash merge. No round reset or generic no-card review. Review branch must exactly equal this card ID so the existing worktree-card fallback selects this registration contract. The absence of the future implementation is intentional scope, not a passed implementation acceptance test.

DoD verifies contract shape; A1 exact diff, A2 semantic completeness, A3 truthful status and A4 external delivery evidence are checked by the registration workflow and R3. No claim that the DoD alone proves R3 or CI. If refs, scope or reviewed head change, invalidate old bindings and re-verify; never waive a failed result.

After merge, retain actual R3/CI/PR/head/base/merge records and keep the future card todo. Standard cleanup can use its existing online MERGED+exact-head fallback when no task-generated T24 exists; never forge one or pass Force to manufacture success. Then the distinct implementation ID starts from the merged origin card and follows normal task.ps1 R1/RED/ship. Registration and implementation have separate branches, review histories and round counters.
