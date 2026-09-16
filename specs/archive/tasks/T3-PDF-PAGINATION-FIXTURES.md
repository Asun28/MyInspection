---
id: T3-PDF-PAGINATION-FIXTURES
title: Fixed-height pagination fixtures before immutable measurement binding
status: merged
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT]
parallelizable_with: [T1-APP-STORAGE-POLICY]
allow_paths:
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerPaginationTest.kt
forbid:
  - persistent production changes, new or changed APIs, typography profile injection or snapshot migration
  - changes to other test files, dropping existing behavior assertions, new pagination algorithms or production test bypasses
non_goals:
  - language-aware measurement, TextRun snapshots, caption elision binding, PdfTextOp and Android implementation
plan_ref: docs/adr/0007-report-interchange.md#pdf-typography-and-device-acceptance-split-2026-09-17
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

# T3-PDF-PAGINATION-FIXTURES

This is a test-fixture refactor with no new product behavior. Execute a baseline DoD before changing the two fixtures, then the same DoD after migration. It is explicitly non-TDD: do not fabricate a business RED; the first and resumed local ship commands use -Base master -Local -SkipRed so the script records the exception. Formal independent Sol/high R3 and all other ship gates remain mandatory.

Use the existing ReportTestFixtures.measurer and the existing composer field. The current port is TextMeasurer.measure(text, style, widthMm), and MeasuredText accepts only lines and lineHeightMm. Do not introduce language, snapshot, typography constructor arguments or adapters ahead of MEASUREMENT-BINDING.

Change exactly these two existing test bodies: `room opening uses its reduced budget only for the first item chunk` and `section opening uses its reduced budget only for the first flowing chunk`. Replace the former's content-dependent 93mm heading with 23 fixed 4mm lines in each language; its available first-item space changes from 19mm to 21mm, still below its 54mm thumbnail. Replace the latter's content-dependent 250mm single line with 130 distinguishable 60-character body lines; the title leaves 247mm for the first chunk, while continuation pages regain 257mm. Preserve the existing photo and overflow assertions. Replace the obsolete single-line text assertion with exact reconstruction of the complete ordered text across all chunks, and retain the title grouping and continuation-capacity assertions described above. Do not weaken any unrelated test.

R4 temporarily changes only the two existing production budget selections: M1 in splitItemRow makes continuation chunks reuse firstMaxHeightMm; M2 in splitBlock makes flowing continuation chunks reuse firstMaxHeightMm. Run the corresponding named test on each mutant. Both positive fixtures explicitly assert that compose succeeds using runCatching plus assertTrue before inspecting its result. Each mutant must compile and yield a fresh named java.lang.AssertionError attributable to the changed budget: M1 fails this positive-success assertion and M2 fails the exact chunk-height assertion. Never call a compiler failure, uncaught injected exception or any nonzero exit a kill. Preserve the source bytes before each mutation, restore and verify SHA-256 after each, and finish with the full DoD on restored production. The final diff contains only the allowed test file; concise real evidence may be recorded there after execution, never as a PENDING success claim.

The isolated two-hunk candidate is 39 additions+deletions / 3822 LF-normalized diff-hunk characters before file headers and real R4 evidence. Budget approximately 60-90 changed lines / 5-7k characters including receipt; recompute the complete actual diff before ship. The unchanged hard gate is 1000 changed lines / 60000 characters. This predecessor does not claim the original room and section fixtures measured production typography: it preserves their pagination behavior with lawful fixed-height fixtures for the subsequent immutable-binding card.

Implementation record (2026-09-17): feature 8e828486a06341059fbe7e21f922540673904982 merged locally at 7e4e35d3398869b0cd173d40d269c006399af87a after first formal Sol/high R3 pass with no findings. Baseline, migrated and restored DoD each passed 328 report tests and six core e2e tests. M1 and M2 each produced the required fresh named AssertionError; production bytes were restored to SHA-256 5297B78BF90124BD67DE88FCA8FA20853A9139BD4D404BFE8DE24D2D40662293. The final test-only diff is 58 changed lines / 5408 characters. Ship recorded the authorized non-TDD SkipRed exception and passed verify, scope, license, secret and size gates. No new technical debt or recurring lesson was identified; lesson capture is explicitly skipped.
