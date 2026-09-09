---
id: T0-SCAFFOLD-UPSTREAM-ADOPTION
title: Adopt merged upstream scaffold coupling groups into MyInspection
status: in-review
allow_paths:
  - .claude/skills/task-loop/SKILL.md
  - .claude/skills/spec-ears/
  - .github/workflows/ci.yml
  - .github/workflows/scaffold-selftest.yml
  - docs/DEVOPS-WORKFLOW.md
  - docs/QUALITY-RUBRIC.md
  - docs/rubric-detail.md
  - docs/SCAFFOLD-SYNC.md
  - docs/SCAFFOLD-UPSTREAM-ADOPTION-20260910.md
  - init-scaffold.ps1
  - scripts/_cards.ps1
  - scripts/_ci.ps1
  - scripts/_config.ps1
  - scripts/_guard.ps1
  - scripts/_gitbase.ps1
  - scripts/_scope.ps1
  - scripts/archive.ps1
  - scripts/check-budget.ps1
  - scripts/check-cards.ps1
  - scripts/check-scope.ps1
  - scripts/check-secrets.ps1
  - scripts/mutate.ps1
  - scripts/review.ps1
  - scripts/selftest.ps1
  - scripts/task.ps1
  - specs/tasks/_TEMPLATE.md
  - specs/tasks/T0-SCAFFOLD-UPSTREAM-ADOPTION.md
  - specs/verdict.schema.json
  - CLAUDE.md
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-UPSTREAM-ADOPTION
dod_exit: 0
dod_assert: The adoption card, its complete scope and all task-card parsing rules remain valid; the PR separately records the source ref, configuration preservation, CI fan-in and the single independent review outcome.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 The adopted script modules come from the fixed merged upstream source and retain their coupled interfaces."
  - "A2 MyInspection preserves GoldenEvidence/Android verification, archive index checks, project identity, frozen paths and origin provenance."
  - "A3 CI verify remains the product job and required is a strict fan-in over that job."
  - "A4 Required review remains blocking and this PR receives exactly one independent Codex review before merge."
  - "A5 The adoption record names the 18 direct source PRs plus the fixed complete-module prerequisites, and explicitly leaves INPUT, TRIAGE and RECEIPT unresolved."
sweep: "rg over scripts/, .claude/, .github/, docs/ and specs/ identified the coupled task, review, card, guard, initializer, selftest, CI and rubric surfaces listed in allow_paths."
forbid:
  - Replacing Android business code, GoldenEvidence verification, or the existing archive index behavior
  - Treating upstream CI as a fresh MyInspection acceptance result
  - Merging after a blocking result from this PR's sole Codex review
non_goals:
  - Reimplementing the unresolved INPUT, TRIAGE or receipt-loss cards
  - Reopening old worktrees, historical R3 counters, RED evidence, mutation batches or performance runs
hygiene: Reuse the committed upstream implementation as a coupled port and validate the local configuration and CI adapter; do not create duplicate script implementations.
doc_sync: Update the scaffold decision ledger, authority index and adoption manifest with the exact source and exclusions.
---

# T0-SCAFFOLD-UPSTREAM-ADOPTION

This is a direct adoption of already merged upstream scaffold work.  The
authoritative source, included groups, local adaptations and exclusions are
recorded in `docs/SCAFFOLD-UPSTREAM-ADOPTION-20260910.md`.  The delivery path
is one new worktree, one new PR and one independent Codex review.  A blocking
review leaves the PR unmerged; it does not start a second review loop.
