---
id: T3-PDF-MEASUREMENT-REQUESTS
title: Language-aware measurement requests and complete Composer validation
status: todo
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT, T3-PDF-PAGINATION-FIXTURES]
parallelizable_with: [T1-APP-STORAGE-ANDROID]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportModel.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTestFixtures.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportContentAdapterTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerGoldenTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerLayoutContractTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerPaginationTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilderTest.kt
  - android/core/src/e2eTest/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreHarness.kt
forbid:
  - Changing DocumentPlan or TextRun fields, PdfTextOp or PdfRenderProgramBuilder production, ReportTypography defaults or the shared metric guard
  - Android imports, fonts, silent/default/fabricated snapshots, parallel old/new measurement APIs or test bypasses
  - New wrapping/pagination/elision algorithms, weakening existing report/e2e assertions, claiming actual platform glyph evidence
non_goals:
  - Exact TextRun snapshot propagation, final-elision snapshot ownership and assembled DEFAULT two-photo regression (T3-PDF-MEASUREMENT-BINDING)
  - Android measurement, PDF execution, images, export lifecycle and device acceptance
acceptance:
  - "A1 TextMeasurer receives the actual TextLanguage and MeasuredText requires an explicit snapshot. Composer selects DEFAULT only when no explicit immutable profile is supplied, validates style/language/role/font size/line height against that profile and calls the existing signed guard using the existing local PdfGeometry conversion."
  - "A2 Every measurement entry uses the same validation boundary: ordinary runs, initial validation, height reserves, original captions, elision candidates and footer. Invalid evidence tests selectively reach each target rather than all failing at the first disclaimer; direct predecessor numerical tests continue to pass without binding masking."
  - "A3 Independent request assertions distinguish EN/ZH/ORIGINAL/NEUTRAL and representative entrypoints using known fixture text. Wrong language/profile/binding or out-of-box evidence rejects. No content-based language guessing, fallback metrics or skipped measurement is introduced."
  - "A4 All existing report/e2e layouts remain covered with explicit immutable legacy profiles, including fixed pagination first/continuation budgets and text reconstruction. Two old 258mm cases now reject their explicit profile mismatch. Existing explicit-profile two-photo capacity remains; the complete omitted-profile DEFAULT assembled regression stays with the successor."
  - "A5 TextRun remains its current shape without a snapshot field. The existing character-safe elision still uses the successful candidate String, never measured.lines.single as replacement text. Exact output snapshot provenance is explicitly pending T3-PDF-MEASUREMENT-BINDING."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: all request-language/profile/binding/local-box and selective-entry assertions, existing report tests and core e2e pass; no output snapshot or platform measurement completion claim.
review_gate: codex {verdict:pass}
hygiene: named compiling AssertionError mutations for language, each binding guard, local-box validation and ordinary/reserve/photo/elision/footer entry bypass; preserve all predecessor pagination evidence
doc_sync: ADR-0007 + TASK-BOARD（R5）
---

# T3-PDF-MEASUREMENT-REQUESTS

Pre-RED split from the original MEASUREMENT-BINDING draft. Its 47,164-character forecast included planned R4 but only 2,836 characters of spare capacity; adding 25% repair reserve makes the unsplit forecast58,955–60,890 characters. This predecessor owns the complete request API, selected immutable profile, all-entry validation and necessary legacy test migration. The successor owns all emitted TextRun snapshots, final-caption snapshot provenance and the full DEFAULT composed-plan regression. Every original acceptance remains assigned; neither card alone claims the combined feature complete.

This is a coherent API change across nine files. Preserve the seven parked test/e2e originals and their backup; build a fresh canonical worktree and selectively reuse only request-owned hunks. Do not overwrite the merged fixed-height PaginationFixtures with the old whole file. Leave the original binding worktree preserved until its successor turn. PdfRenderProgramBuilderTest changes here are limited to necessary Composer profile injection; its three direct TextRun constructors change only in the successor. No production bypass or temporary snapshot default is allowed.

Before RED, identify each negative fixture's actual target entry and demonstrate why earlier measurements remain valid. Preserve successful candidate text in existing elision. The three parked tests for every-output snapshot, final elision snapshot and omitted-profile DEFAULT two-photo capacity, with their exclusive helpers, move intact to the successor; add independent request-language assertions here so moving the combined snapshot test does not lose request coverage.

Full candidate forecast374–400 changed lines /36,820–39,420 UTF-16 diff characters includes tests and R4. With25% repair reserve:468–500 lines /46,025–49,275 characters. Recompute from actual merged baseline before RED; if the first full candidate forecast exceeds40k without reserve, re-scope before execution. Early800 lines/50k and hard1,000/60k remain; no compression or deleted assertions. Author GPT-6 Astra · xhigh for the cross-file contract and entry-specific tests; independent GPT-5.6 Sol · high formal R3.
