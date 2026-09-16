---
id: T3-PDF-PAGINATION-FIXTURES-REMOTE
title: Publish the locally verified fixed-height pagination fixtures
status: todo
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE]
parallelizable_with: []
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerPaginationTest.kt
forbid:
  - persistent production changes, new or changed APIs, typography profile injection or snapshot migration
  - changes to other test files, dropping existing behavior assertions, new pagination algorithms or production test bypasses
non_goals:
  - language-aware measurement, TextRun snapshots, caption elision binding, PdfTextOp and Android implementation
plan_ref: docs/adr/0007-report-interchange.md#fixed-height-pagination-fixtures-remote-publication
acceptance:
  - "A1 only the existing room-opening and section-opening pagination fixtures migrate from content-dependent 93mm and 250mm line heights to multiple fixed 4mm lines, using the current three-argument TextMeasurer and two-argument MeasuredText APIs unchanged"
  - "A2 the room fixture uses 23 lines per language and preserves the two-chunk assertion, no first-chunk thumbnail, the exact continuation photo identity and no overflow under the reduced first-item budget"
  - "A3 the section fixture uses 130 fixed-height body lines and asserts exact chunk heights 246/254/30, its first chunk on the title page immediately after the title, a continuation exceeding the first-page remainder, complete ordered text reconstruction and no overflow"
  - "A4 all existing report tests and core e2e pass; two named compiling temporary budget mutations are rejected by these tests and exact production bytes are restored, with zero final production diff"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: fixed-height room and section fixtures retain first-chunk and full-continuation budget coverage, exact layout and text guarantees, all report/e2e tests pass, and production remains unchanged.
review_gate: codex {verdict:pass}
hygiene: two named compiling budget mutations require fresh named framework AssertionError evidence and SHA-256 byte restoration; compiler/infrastructure errors or uncaught injected exceptions are not mutation kills; preserve every existing assertion.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-PAGINATION-FIXTURES-REMOTE

This is a test-fixture refactor with no new product behavior. Execute a baseline DoD before changing the two fixtures, then the same DoD after migration. It is explicitly non-TDD: do not fabricate a business RED; the historical local ship used -Base master -Local -SkipRed. The user requested remote PR publication and merge on 2026-09-17; the first and resumed remote ship commands use -Base master -SkipRed so the script records the same non-TDD exception. Formal independent Sol/high R3 and all other ship gates remain mandatory.

Use the existing ReportTestFixtures.measurer and the existing composer field. The current port is TextMeasurer.measure(text, style, widthMm), and MeasuredText accepts only lines and lineHeightMm. Do not introduce language, snapshot, typography constructor arguments or adapters ahead of MEASUREMENT-BINDING.

Change exactly these two existing test bodies: `room opening uses its reduced budget only for the first item chunk` and `section opening uses its reduced budget only for the first flowing chunk`. Replace the former's content-dependent 93mm heading with 23 fixed 4mm lines in each language; its available first-item space changes from 19mm to 21mm, still below its 54mm thumbnail. Replace the latter's content-dependent 250mm single line with 130 distinguishable 60-character body lines; the title leaves 247mm for the first chunk, while continuation pages regain 257mm. Preserve the existing photo and overflow assertions. Replace the obsolete single-line text assertion with exact reconstruction of the complete ordered text across all chunks, and retain the title grouping and continuation-capacity assertions described above. Do not weaken any unrelated test.

R4 temporarily changes only the two existing production budget selections: M1 in splitItemRow makes continuation chunks reuse firstMaxHeightMm; M2 in splitBlock makes flowing continuation chunks reuse firstMaxHeightMm. Run the corresponding named test on each mutant. Both positive fixtures explicitly assert that compose succeeds using runCatching plus assertTrue before inspecting its result. Each mutant must compile and yield a fresh named java.lang.AssertionError attributable to the changed budget: M1 fails this positive-success assertion and M2 fails the exact chunk-height assertion. Never call a compiler failure, uncaught injected exception or any nonzero exit a kill. Preserve the source bytes before each mutation, restore and verify SHA-256 after each, and finish with the full DoD on restored production. The final diff contains only the allowed test file; concise real evidence may be recorded there after execution, never as a PENDING success claim.

The isolated two-hunk candidate is 39 additions+deletions / 3822 LF-normalized diff-hunk characters before file headers and real R4 evidence. Budget approximately 60-90 changed lines / 5-7k characters including receipt; recompute the complete actual diff before ship. The unchanged hard gate is 1000 changed lines / 60000 characters. This predecessor does not claim the original room and section fixtures measured production typography: it preserves their pagination behavior with lawful fixed-height fixtures for the subsequent immutable-binding card.

Implementation record (2026-09-17): feature 8e828486a06341059fbe7e21f922540673904982 merged locally at 7e4e35d3398869b0cd173d40d269c006399af87a after first formal Sol/high R3 pass with no findings. Baseline, migrated and restored DoD each passed 328 report tests and six core e2e tests. M1 and M2 each produced the required fresh named AssertionError; production bytes were restored to SHA-256 5297B78BF90124BD67DE88FCA8FA20853A9139BD4D404BFE8DE24D2D40662293. The final test-only diff is 58 changed lines / 5408 characters. Ship recorded the authorized non-TDD SkipRed exception and passed verify, scope, license, secret and size gates. No new technical debt or recurring lesson was identified; lesson capture is explicitly skipped.

Remote publication: the preceding implementation record describes local-only evidence, not current remote acceptance. The remote candidate must rerun baseline and migrated DoD, both named R4 mutations with exact source restoration, restored DoD, verify, scope, license, secret and complete-diff budget gates, formal independent Sol/high R3, and exact-candidate CI before merging. At origin/master 0a4d1fe41a1232ace1fc18bec65d6d135eb20e6a, the Composer, pagination test and shared test fixtures have the same Git blobs as the original feature parent; this is compatibility evidence, not a test-run substitute. The declared T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE predecessor remains a delivery-order dependency.

This remote alias republishes the completed local T3-PDF-PAGINATION-FIXTURES card; it is not an additional feature or an additional delivered card toward the five-round target. Keep the original archived card and its evidence unchanged. Run all phase commands through D:/Projects/MyInspection/scripts/task.ps1: start with -Base origin/master; ship with -Base master -SkipRed, without -Local. The origin-based canonical branch/worktree name is T3-PDF-PAGINATION-FIXTURES-REMOTE. R5 records the actual remote PR, reviewed HEAD, merge and current test evidence, then archives this alias only.
