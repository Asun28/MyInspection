---
id: T0-SCAFFOLD-UPSTREAM-ADOPTION
title: Adopt merged upstream scaffold coupling groups into MyInspection
status: in-review
allow_paths:
  - .claude/hooks/handoff-resume.ps1
  - .claude/hooks/_throttle.ps1
  - .claude/hooks/handoff-reminder.ps1
  - .claude/skills/task-loop/SKILL.md
  - .claude/skills/triage/SKILL.md
  - .claude/skills/spec-ears/
  - .claude/workflows/plan-forge.mjs
  - .claude/workflows/decompose-cards.mjs
  - .claude/workflows/scout-options.mjs
  - .github/workflows/ci.yml
  - .github/workflows/scaffold-selftest.yml
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - docs/LOOP-ENGINEERING.md
  - docs/IDEA-TO-PLAN.md
  - docs/idea-to-plan-diagram.html
  - docs/scaffold-architecture.html
  - docs/SCOUT-OPTIONS.md
  - docs/QUALITY-RUBRIC.md
  - docs/rubric-detail.md
  - docs/SCAFFOLD-SYNC.md
  - docs/SCAFFOLD-UPSTREAM-ADOPTION-20260910.md
  - init-scaffold.ps1
  - scripts/_cards.ps1
  - scripts/_ci.ps1
  - scripts/_config.ps1
  - scripts/_context.ps1
  - scripts/_guard.ps1
  - scripts/_gitbase.ps1
  - scripts/_scope.ps1
  - scripts/_symbol-markdown.ps1
  - scripts/_unicode.ps1
  - scripts/_validation.ps1
  - scripts/_lessons.ps1
  - scripts/lessons.ps1
  - scripts/handoff.ps1
  - scripts/archive.ps1
  - scripts/check-budget.ps1
  - scripts/check-cards.ps1
  - scripts/check-licenses.ps1
  - scripts/check-scope.ps1
  - scripts/check-secrets.ps1
  - scripts/mutate.ps1
  - scripts/review.ps1
  - scripts/selftest.ps1
  - scripts/scaffold-sync.ps1
  - scripts/task.ps1
  - scripts/triage.ps1
  - specs/tasks/_TEMPLATE.md
  - specs/tasks/T0-SCAFFOLD-UPSTREAM-ADOPTION.md
  - specs/verdict.schema.json
  - CLAUDE.md
dod_command: pwsh -NoProfile -File scripts/check-cards.ps1 -TaskId T0-SCAFFOLD-UPSTREAM-ADOPTION
dod_exit: 0
dod_assert: The adoption card and scope remain valid; the PR records candidate-bound selftest and safety checks, CI results, and each independently authorized review outcome.
review_gate: codex {verdict:pass}
acceptance:
  - "A1 The adopted script modules come from the fixed merged upstream source and retain their coupled interfaces."
  - "A2 MyInspection preserves GoldenEvidence/Android verification, archive index checks, project identity, frozen paths and origin provenance."
  - "A3 CI verify remains the product job and required is a strict fan-in over that job."
  - "A4 Required review remains blocking; the user authorized one further review after the first block, and only a pass on the repaired candidate permits merge."
  - "A5 The adoption record names the 18 direct source PRs plus the fixed complete-module prerequisites, and explicitly leaves INPUT, TRIAGE and RECEIPT unresolved."
  - "A6 Selftest retains project-specific regression coverage and complete dependencies; diagnostic fixtures leave the candidate checkout unchanged."
  - "A7 Review artifact operations reject unsafe paths, round-cap/reset behavior is retained, and ignored local logs are not described as tracked evidence."
sweep: "rg over scripts/, .claude/, .github/, docs/ and specs/ identified the coupled task, review, card, guard, initializer, selftest, CI and rubric surfaces listed in allow_paths."
forbid:
  - Replacing Android business code, GoldenEvidence verification, or the existing archive index behavior
  - Treating upstream CI as a fresh MyInspection acceptance result
  - Merging without a passing authorized review and successful CI on the current candidate
non_goals:
  - Reimplementing the unresolved INPUT, TRIAGE or receipt-loss cards
  - Reopening old worktrees, historical R3 counters, RED evidence, mutation batches or performance runs
hygiene: Reuse the committed upstream implementation as a coupled port and validate the local configuration and CI adapter; do not create duplicate script implementations.
doc_sync: Update the scaffold decision ledger, authority index and adoption manifest with the exact source, exclusions and tracked-sensitive allowlist adapter.
---

# T0-SCAFFOLD-UPSTREAM-ADOPTION

This is a direct adoption of already merged upstream scaffold work.  The
authoritative source, included groups, local adaptations and exclusions are
recorded in `docs/SCAFFOLD-UPSTREAM-ADOPTION-20260910.md`.  The delivery path
is one new worktree and one new PR. The first authorized review returned block.
The user then explicitly authorized repairing its four remaining integration
findings and running Codex review again. This authorization covers one further
review of the repaired candidate; a block leaves the PR unmerged.

That additional review returned block on `6c9c1473`: it required a stronger
shared CI contract, committed behavioral coverage of the review cap and unsafe
artifact paths, and evidence readable inside the review workspace. Subsequent
repairs remain part of these integration findings. The user then authorized
parallel DeepSeek V4 Pro PR pre-reviews for cost control, followed by another
Codex review only when none of those pre-reviews blocks. Full current-candidate
validation and CI remain required; neither substitutes for the Codex verdict.
The `e4ea807e` full run exposed a CI rename fixture that left `needs` stale;
the fixture now renames both job and dependency with explicit setup assertions.

The CI compatibility repair restores the existing fail-closed, exact-path
SQLDelight tracked-sensitive allowlist adapter in `scripts/check-secrets.ps1`.
It does not modify schema databases, the allowlist JSON, `.gitignore` or the
source review record.
