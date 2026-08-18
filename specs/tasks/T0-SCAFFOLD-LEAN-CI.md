---
id: T0-SCAFFOLD-LEAN-CI
title: Stop launching scaffold-only CI shards for ordinary product pull requests
depends_on: [T0-HARNESS-PERF]
plan_ref: _local/PLAN.md#71-scaffold-lean-ci-cost-plan-2026-08-18
parallelizable_with: []
status: todo
branch: T0-SCAFFOLD-LEAN-CI
worktree: C:\wt\T0-SCAFFOLD-LEAN-CI
allow_paths:
  - .github/workflows/scaffold-selftest.yml
  - CLAUDE.md
  - docs/DEVOPS-WORKFLOW.md
  - docs/DELIVERY-CHAINS.md
  - docs/scaffold-architecture.html
forbid:
  - Removing or weakening R3, verify, a selftest gate, an operating system, or a shard
  - Adding scripts, jobs, actions, dependencies, caches, test frameworks, or runtime code
  - Changing ci.yml, gh-bootstrap ruleset semantics, secrets, authentication, release, or deployment behavior
non_goals:
  - Further optimizing selftest internals or changing local aggregate scheduling
  - Fixing TD9 or bundling unrelated scaffold and product technical debt
  - Supporting custom branch rules that manually require scaffold shard job names
diagnosis:
  root_cause: The pull_request event remained deliberately unfiltered because scaffold jobs were assumed to be required checks, but the supported ruleset requires only verify and the configured R3 status; product-only PRs therefore pay for six irrelevant jobs on every update.
  same_class: ci.yml remains unfiltered on pull requests because verify is a supported required check; scaffold-selftest push already has the correct authority filter, and no other workflow launches the six scaffold-only jobs.
dod_command: $workflow = Get-Content .github/workflows/scaffold-selftest.yml -Raw; $filter = "paths: ['scripts/**', '.claude/**', '.github/**', 'configs/**', '!**.md']"; $escaped = [regex]::Escape($filter); if (([regex]::Matches($workflow, $escaped)).Count -ne 2) { exit 1 }; foreach ($event in @('push', 'pull_request')) { if ($workflow -notmatch "(?m)^  ${event}:\s*\r?\n    branches: \[main, master\]\r?\n    $escaped$") { exit 1 } }
dod_exit: 0
dod_assert: The identical non-Markdown scaffold authority filter appears exactly twice, immediately beneath push and pull_request after their main/master branch declarations. Deleting only the pull_request filter makes the command fail. Because this card changes the workflow itself, the existing CI run supplies the unchanged 2 OS x 3 shard wiring evidence.
review_gate: codex {verdict:pass}
hygiene: Configuration-only change; reuse the existing selftest contract plus one discriminating exact-count assertion, with no new test code or parallel framework.
doc_sync: Keep CLAUDE.md, DEVOPS-WORKFLOW.md, DELIVERY-CHAINS.md, scaffold-architecture.html, and TASK-BOARD aligned with the filtered PR behavior and record measured before/after workflow launches after merge.
---

# T0-SCAFFOLD-LEAN-CI

## Deliverable

Make `scaffold-selftest` run on pull requests only when its existing non-Markdown scaffold authority paths change. Ordinary product PRs should keep `verify` and R3 but should not start any of the six scaffold OS-by-shard jobs.

## Evidence

- PRs #5–#11 changed no files under `scripts/`, `.claude/`, `.github/`, or `configs/`.
- Those PRs still generated 60 scaffold workflow runs and 360 shard-job launches.
- `T0-HARNESS-PERF` already reduced local full selftest time by about 55%; avoiding irrelevant launches is now the lower-complexity optimization.
- R3 remains unchanged because the same PR cohort demonstrates that it caught material licence, privacy, data-loss, determinism, legal-wording, and test-validity defects.

## Implementation constraint

Copy the existing push `paths` filter verbatim beneath `pull_request`. Update only the authoritative explanations that currently say PRs are deliberately unfiltered. Do not introduce a change-detection script, dispatcher job, reusable workflow, dependency, or new test framework.

The supported branch ruleset remains safe: `gh-bootstrap.ps1` requires `verify` plus the configured R3 status, not scaffold shard job names. If a repository owner later creates a custom ruleset that requires those shard names, that custom configuration must be corrected before retaining this filter.

## Acceptance

1. A pull request changing product paths such as `android/**` does not start `scaffold-selftest`.
2. A pull request changing any included non-Markdown scaffold authority path starts all six existing jobs.
3. `push` and `pull_request` still target both `main` and `master`.
4. `verify`, R3, both operating systems, all three shards, and all selftest gates are unchanged.
5. The card DoD and normal repository verification pass without adding implementation code.
