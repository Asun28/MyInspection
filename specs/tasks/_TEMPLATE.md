---
# id naming (machine-checked): T<stage>-<UPPER-KEBAB>, regex ^T\d+-[A-Z0-9]+(-[A-Z0-9]+)*$
#   OK: T0-SCAFFOLD / T2-API / T3-REVIEW-GATE   NOT: t1-foo / T1_FOO / my-task
#   id == file name == branch == worktree leaf. The T?-EXAMPLE below is a deliberate placeholder
#   violation and check-cards SKIPS this file. The required fields come first, then one optional block.
id: T?-EXAMPLE
title: one-sentence deliverable
status: todo            # todo | in-progress | in-review | merged
branch: T?-EXAMPLE
worktree: C:\wt\T?-EXAMPLE   # = <WorktreeRoot>\<id> (default <system drive>\wt); see scripts/_config.ps1
allow_paths:            # the paths this card may change; the ship scope gate blocks anything outside them
  # A new tool in dod_command means the manifest installing it (pyproject.toml / package.json) sits here too.
  - path/to/...
dod_command: uv run python -m pytest <tests> -q   # only tools CI already has, or that the card installs
# Three machine-checked traps, one line each; the reasoning and the sentinels are in specs/README.md:
#   L95  no `$variable` inside a nested `pwsh -Command` payload - it interpolates away and mints a fake RED.
#   L245 a payload that itself spawns pwsh must END on an explicit `; exit 0`, never on a branch's exit.
#   L308 assert every repo function it calls: if (-not (Get-Command <n> -ErrorAction SilentlyContinue)) { exit 1 } - that proves the NAME only; a wrong method on what it RETURNS throws and skips the arm the same way, so read the RED's OUTPUT, not just its exit code.
dod_exit: 0
review_gate: codex {verdict:pass}   # optional, kept filled: declaring it is what invokes the R3 reviewer
acceptance:            # CLOSED numbered list rubric #6 judges against; required once review_gate is set
  #   ([CARD-ACCEPTANCE]). WHAT is verified, not HOW; a gap outside it is [FOLLOW-UP]. Replace the seeds:
  - 1. <one fact that means done, naming the assertion that covers it>. [dod arm 1]
  - 2. <the next one; a gap outside this list is [FOLLOW-UP], not a block>. [dod arm 2]
# ─────── Optional below. Absent is silent; no gate asks for any of these. Meanings: specs/README.md.
# requirements:        # optional `R<n>.` items, one EARS line each, one `shall`. UNCOMMENT FIRST, then cite
#   #   `[R<n>]` on the acceptance item each closes - a citation with no live item BLOCKS. skills/spec-ears
#   - R1. The <system> shall <observable response>.
#   - R2. WHEN <trigger>, the <system> shall <observable response>.
#   - R3. WHILE <state>, the <system> shall <observable response>.
#   - R4. IF <condition>, THEN the <system> shall <observable response>.
#   - R5. WHERE <feature> is enabled, the <system> shall <observable response>.
#   - R6. WHEN <trigger>, the <system> shall reject it within [TBD: timeout, ms].  # never invent a number
# depends_on: []       # prerequisite card ids (topological order, decides what may run in parallel)
# parallelizable_with: []   # parallel card ids; their allow_paths must not overlap (machine-checked)
# plan_ref: <PlanDir>/PLAN.md#section   # this card's plan section - the implementer's minimal pointer
# budget: 400          # declared net changed lines (added+deleted); once declared it is a merge gate
# tier: S              # acceptance tier - computed from allow_paths, may only be RAISED, never lowered
# sweep: "<the grep you ran, and the teaching faces it found>"   # REQUIRED above five allow_paths (L97)
# forbid: [<cross-cutting hard boundaries this card may not cross: network, credentials, frozen contracts>]
# non_goals: [<a capability this card deliberately does not build; rubric #14 judges scope creep on it>]
# diagnosis:           # bugfix cards only (rubric #17: repair the root cause, not the symptom)
#   root_cause: <why it broke, not how it showed>  ·  same_class: <sibling call sites checked too?>
# dod_assert: <the machine-checkable assertion the command produces, in prose>
# hygiene: <R4 test hygiene; a promised mutation batch needs room in allow_paths for BOTH its artifacts>
# doc_sync: <the docs to bring back in step after merge (R5)>
# superseded_by: <the later card that deliberately deleted what this card's DoD asserted>
---

# T?-EXAMPLE

## Deliverable
(The single deliverable, matching this card's section of the plan.)

## Acceptance (DoD = command + exit code + assertion; paired with the closed `acceptance:` list)
```powershell
<dod_command>
```
- Expected exit code: 0
- Assertion: <...>
