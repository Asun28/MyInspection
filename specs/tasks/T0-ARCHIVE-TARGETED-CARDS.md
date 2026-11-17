---
id: T0-ARCHIVE-TARGETED-CARDS
title: Archive only explicitly selected merged task cards
status: merged
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
  - "A5 Extend existing selftest 12e with executable production-script fixtures covering selection, comma input, exact bytes, repeat calls, DryRun, valid Quiet, all input/state refusals and mode conflicts. Assert exit/sentinel and whole-tree side effects; observe forbidden cleanup/Git/network/task invocations and debt/lessons reads, preservation of LF/CRLF index endings, LF fallback, UTF-8 without BOM and identical-index write avoidance. Inject move, duplicate-source removal and index-write failures; assert nonzero exit, retained complete source or destination bytes, completed-move retention and successful rerun index repair. Use call/read/write observations where unchanged file bytes cannot prove absence of an operation. Existing legacy/debt/lessons/CheckCardsIndex fixtures remain. No parallel test entrypoint is added to the repository."
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

## Delivery status

This PR registers future work. The todo status means no targeted archive implementation or executable targeted behavior fixtures have been delivered. Registration checks, existing legacy selftests and CI are separate evidence and do not prove this capability. After registration passes independent R3 and exact CI and merges, implementation starts through separate R1, RED, GREEN, Tier-S proof and normal task ship. The registration and future four-file implementation are separate review units; the unwritten implementation budget remains unproven.

## Implementation delivery receipt (2026-10-04)

The earlier registration-era Delivery status is historical. This implementation was delivered by PR436: reviewed head52c1e0a75eaa54737ec35576acb91e00f10b452a, squash83b2f87ba5b2997beffb9418d86a9006586e3002 at2026-10-04T14:04:58Z. Original-D normal ship exited0 at14:05:00Z. R3 round2 returned spec=pass and standards=pass, zero findings, run_status=success. Exact-head ci.yml pull_request run37207356196 attempt1 succeeded: verify111451222145 and required111452911842.

The four-file implementation measures597 changed lines/40002 UTF16, with separate25% reserve747/50003. Registered scoped fixtures retain77 cases and the original22 unique compiling R4 controls. R3 round1 on82d5 reported two real coverage gaps: missing success-marker/Quiet-output assertions and incomplete observation of Test-Path/debt/lessons enumeration. The repair changes only selftest.ps1. Fifteen identical production faults survived before repair and triggered their named assertions after repair; every mutant parsed, with8 normal baseline and2 helper controls exiting0. These fifteen pairs verify the repaired tests; the original22 R4 controls are inherited, not claimed as newly rerun. Final scoped Only12 exited0. Fixed52c1 full Parallel acceptance exited0 across five shards, union17 gates, wall1242.3s; optional fixture skips remain visible in the raw log.

Root evidence under _local/rotating-card-orchestrator: targeted-archive-r3-round1-preserved-20261004 manifest442AE6DE retains the first BLOCK and first full/ship records; targeted-archive-r3-repair-preserved-20261004 manifestD3F6A668 preserves1701 payloads/19177247 bytes; targeted-archive-full-r2-preserved-20261004 manifestF3D320D5 preserves six payloads/776588 bytes. These are late byte-preserving copies, not reconstructed runtime seals. Repair diff captures differed only in LF/CRLF representation; normalized equality is not raw-byte equality. Reviewer shutdown hook/MCP warnings are retained with the successful native verdict and ship result. No review Reset occurred.

This PR changes no real task card or archive index. AndroidStorage, DeviceFixture and MeasurementBinding metadata closure remains separate. Runtime I/O failures are nonzero and preserve completed moves for repair by rerun; no atomic rollback or concurrent-writer guarantee is claimed. This is a necessary delivery split, zero new products. R5 synchronization and subsequent normal cleanup have their own evidence and are not claimed by the feature merge alone.
