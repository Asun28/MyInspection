---
id: T0-ARCHIVE-TARGETED-CARDS
title: Archive only explicitly selected merged task cards
status: todo
branch: T0-ARCHIVE-TARGETED-CARDS
worktree: C:\wt\T0-ARCHIVE-TARGETED-CARDS
depends_on: []
allow_paths:
  - scripts/archive.ps1
  - scripts/selftest.ps1
  - specs/archive/README.md
  - specs/README.md
budget: 750
tier: S
review_gate: codex {verdict:pass}
dod_command: pwsh -NoProfile -File scripts/selftest.ps1 -Only 12
dod_exit: 0
dod_assert: Gate 12 exits zero and the new 12e targeted-card behavior cases pass against the production archive script in isolated fixtures; this scoped DoD does not replace Tier-S full selftest.
acceptance:
  - "A1 -CardsOnly requires bound nonempty -CardIds; CardIds requires true CardsOnly. Expand each array item on commas, trim surrounding whitespace, validate every token with Test-ScaffoldCardId before constructing its path, and reject empty/null/invalid/duplicate tokens. Mixed Check, CheckCardsIndex, LessonsOnly, LessonIds or RestoreLessonIds parameters are rejected before any archive data read or write. DryRun and Quiet remain valid."
  - "A2 Before mutation validate the entire selection: matching front-matter id and exactly merged status; unknown ids, non-merged cards, existing declared worktree directories, non-file selected endpoints and differing hot/cold bytes reject the whole selection with nonzero exit. Cold-only merged cards are successful no-ops; identical hot/cold bytes allow removing only the redundant hot copy. DryRun performs the same validation and writes nothing."
  - "A3 Successful targeted mode moves only selected hot cards byte-for-byte and updates only cards-index.md through existing Get-CardsIndexText; all unselected hot/cold cards, tracker/debt indices, lessons and other files remain untouched. It never invokes cleanup, Git, network or task phases. Existing mode behavior without either new parameter remains unchanged."
  - "A4 Reuse Get-CardField, Test-ExactBytes, Get-ScaffoldTextNewline and Write-ScaffoldUtf8Text. Preserve existing cards-index line endings with LF fallback, UTF-8 without BOM; regenerate from the complete cold-card set, including unselected cold cards, and skip an identical index write. Do not use debt files as targeted newline fallback."
  - "A5 Extend existing selftest 12e with executable production-script fixtures covering selection, comma input, exact bytes, repeat calls, DryRun, all input/state refusals and mode conflicts; assert exit/sentinel and whole-tree side effects. Existing legacy/debt/lessons/CheckCardsIndex fixtures remain. No parallel test entrypoint is added to the repository."
  - "A6 Both existing README files describe targeted usage, refusal versus runtime failure semantics and the side-effect boundary. Runtime I/O failure exits nonzero and never overwrites a divergent existing archive; already completed moves are not rolled back, source or complete destination remains, and rerun may repair the index. No transaction or concurrent-writer guarantee is claimed."
forbid:
  - Amending this card in the subsequent implementation PR
  - Changing task/review/CI controllers or the shared card-ID grammar
  - Moving real cards or regenerating real archive indices in this implementation PR
  - Modifying old task-card scopes or immutable evidence seals
non_goals:
  - Debt or lessons selection, card restoration, automatic cleanup or worktree removal
  - Multi-writer locking, a cross-file transaction or a general registration controller
  - Android product implementation or any new product-count increment
hygiene: Each new behavior case names the fault it catches; execute deliberate behavior mutations only after the same frozen baseline passes, reject parse-error kills, and retain only tests with unique detection value. Mutation evidence stays private.
doc_sync: specs/archive/README.md and specs/README.md usage in the implementation PR; normal R5 own-card status and project records through the existing authorized workflow.
---

# T0-ARCHIVE-TARGETED-CARDS

## Outcome

After Android/Fixture delivery, the operator can archive exactly the named merged cards without sweeping unrelated cards or technical debt. This is a necessary delivery split, zero new products.

## Public invocation

```powershell
pwsh -NoProfile -File scripts/archive.ps1 -CardsOnly -CardIds T1-ONE,T1-TWO -DryRun
pwsh -NoProfile -File scripts/archive.ps1 -CardsOnly -CardIds T1-ONE,T1-TWO
```

Native -File callers use one comma-separated string; PowerShell callers may also supply string arrays. No wildcard or path input. Trim token edges only; do not uppercase, silently discard empties or deduplicate.

## Refusal and recovery

Use ASCII sentinels [ARCHIVE-CARD-MODE], [ARCHIVE-CARD-BADID], [ARCHIVE-CARD-DUPLICATE], [ARCHIVE-CARD-UNKNOWN], [ARCHIVE-CARD-IDENTITY], [ARCHIVE-CARD-NOT-MERGED], [ARCHIVE-HELD], [ARCHIVE-CARD-DIVERGED], [ARCHIVE-CARD-PATH]. All validation is performed for every requested card before directory creation, movement or index writes. One refusal means no selected card moves, even if an earlier item is valid. DryRun follows the same path through validation and exits before writes.

Only hot exists: validate then move. Only cold exists: validate then leave it. Both exist: validate both and compare raw bytes with Test-ExactBytes; identical bytes allow completing removal of the hot duplicate, any difference refuses. Neither exists: unknown. A declared worktree directory holds the selected card even in the cold-only case; absent/empty worktree fields retain the legacy meaning of no declared hold. Relative worktree paths resolve against RepoRoot; absolute ones remain absolute. No Git worktree registry scan is added.

After valid real calls, recompute the full cold-card index through the existing generator, writing only when generated bytes differ. Reading unselected cold cards is necessary for that projection; they are not modified. Do not enumerate unselected hot cards. Targeted mode must avoid even newline probing debt/lessons paths.

I/O failures are nonzero failures, not validation refusals: no global rollback is promised. Move without Force into an absent target; for an identical pre-existing target remove only the redundant selected source after rechecking bytes. Never delete the sole complete card copy. If a later move/index write fails, report failure and retain the completed moves; rerun validates cold-only entries and repairs the derived index. Normal single-writer execution is assumed.

## Verification sequence after execution authorization

1. Integrate the reviewed test fragment inside existing 12e; run the scoped DoD against unchanged production and inspect the feature-missing RED, not only a nonzero code.
2. Implement archive.ps1 minimally, preserving the legacy branches; run the same DoD to GREEN and the named mutation controls.
3. Measure the complete implementation diff including all four files: first <=750 changed lines and <=45000 UTF-16 units, plus separate ceil(1.25x) reserve against 1000/60000. Stop and return for a scope decision if over; no compression or acceptance deletion.
4. Complete Tier-S full selftest, verify, scope/license/secret gates, original R3 and exact CI through the normal ship workflow. Scoped -Only12 is not full acceptance.

## Preparation status

This text is an unregistered proposal. No candidate, fixture, test or archive command has run. The root has approved a one-time two-card registration PR bootstrap with a dedicated registration card, real original-reviewer R3 and exact CI; remote execution remains separately authorized. Its own registration diff and the future four-file implementation diff are separate review units; preparation-document size is not evidence that the unwritten implementation fits.
