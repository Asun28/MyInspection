---
id: T3-PDF-MEASUREMENT-REQUESTS
title: Language-aware measurement requests and complete Composer validation
status: todo
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT-REMOTE, T3-PDF-PAGINATION-FIXTURES-REMOTE]
parallelizable_with: [T1-APP-STORAGE-POLICY-REMOTE]
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
  - "A2 Every measurement entry uses the same validation boundary: ordinary runs, initial validation, height reserves, original captions, rejected and successful elision candidates and footer. Eight selective-entry negatives each reach their named target after legal predecessors; the unchanged direct ReportTypography numerical tests run in the full DoD without binding masking. The two complete Composer numerical integration methods (12 non-finite cases, six sign/edge negatives and one positive control) belong to the Binding successor."
  - "A3 Independent request assertions distinguish EN/ZH/ORIGINAL/NEUTRAL at known fixture text and representative entrypoints. All five style/language/role/font-size/line-height binding mismatches reject independently; explicit immutable profile and omitted DEFAULT selection each have a positive control. Local line-box evidence rejects with baseline8/top-8/bottom3.25 at 4mm (11.25pt exceeds local 11pt), while baseline8/top-8/bottom3 succeeds. Candidate String and supplementary Unicode preservation remain directly asserted. No language guessing, fallback metrics or skipped measurement is introduced."
  - "A4 All existing report/e2e layouts remain covered with explicit immutable legacy profiles, including fixed pagination first/continuation budgets and text reconstruction. Two old 258mm cases now reject their explicit profile mismatch. Existing explicit-profile two-photo capacity remains; the complete omitted-profile DEFAULT assembled regression stays with the successor."
  - "A5 TextRun remains its current shape without a snapshot field. The existing character-safe elision still uses the successful candidate String, never measured.lines.single as replacement text. Exact output snapshot provenance is explicitly pending T3-PDF-MEASUREMENT-BINDING."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: all request-language/profile/binding/local-box and eight selective-entry assertions, unchanged direct ReportTypography numerical tests, candidate String/Unicode checks, existing report tests and core e2e pass; the 19 Composer numerical integration cases are assigned to Binding, with no output snapshot or platform measurement completion claim.
review_gate: codex {verdict:pass}
hygiene: 26 named compiling AssertionError mutations cover ten language routes, seven binding/profile/default checks and nine selective-entry/guard/String failures; E01 initial and ordinary, E02 reserve, E03 original caption, E04 both elision candidates, E05 footer, E06 shared guard call, E07 local line-box, E08 footer-strip substitution and E09 accepted candidate String remain distinct targets. Preserve all direct Typography and predecessor Pagination tests; record actual compile0/test1, named AssertionError, source pins and restoration.
doc_sync: ADR-0007 + TASK-BOARD（R5）
---

# T3-PDF-MEASUREMENT-REQUESTS

Pre-RED split from the original MEASUREMENT-BINDING draft. Its 47,164-character forecast included planned R4 but only 2,836 characters of spare capacity; adding 25% repair reserve makes the unsplit forecast58,955–60,890 characters. This predecessor owns the complete request API, selected immutable profile, all-entry validation and necessary legacy test migration. The successor owns all emitted TextRun snapshots, final-caption snapshot provenance and the full DEFAULT composed-plan regression. Every original acceptance remains assigned; neither card alone claims the combined feature complete.

This is a coherent API change across nine files. Preserve the seven parked test/e2e originals and their backup; build a fresh canonical worktree and selectively reuse only request-owned hunks. Do not overwrite the merged fixed-height PaginationFixtures with the old whole file. Leave the original binding worktree preserved until its successor turn. PdfRenderProgramBuilderTest changes here are limited to necessary Composer profile injection; its three direct TextRun constructors change only in the successor. No production bypass or temporary snapshot default is allowed.

Before RED, identify each negative fixture's actual target entry and demonstrate why earlier measurements remain valid. Preserve successful candidate text in existing elision. The three parked tests for every-output snapshot, final elision snapshot and omitted-profile DEFAULT two-photo capacity, with their exclusive helpers, move intact to the successor; add independent request-language assertions here so moving the combined snapshot test does not lose request coverage. Both complete Composer numerical integration methods also move to Binding: 12 NaN/infinity cases across four fields, six sign/edge rejection cases and one valid signed-edge control. Requests still proves the scalar rules through unchanged direct ReportTypographyTest, all five binding mismatches, eight selective entries and local line-box discrimination before Binding exists.

The simultaneous complete forecast is388–400 changed lines /39,282–39,722 UTF-16 diff units: seven test files324/30,145 measured, two production files36–40/6,537–6,777 forecast, and readable 26-mutation R4 receipt28–36/2,600–2,800 forecast. With25% repair reserve this is485–500 lines /49,103–49,653 units. The first full candidate must be measured against its actual base before RED; if it exceeds40,000 units, return for another scope decision. The upper forecast has only278 units below that stop line, which is not permission to compress code or receipts or remove cases. Early800 lines/50k and hard1,000/60k remain unchanged. Author GPT-6 Astra · xhigh for the cross-file contract and entry-specific tests; independent GPT-5.6 Sol · high formal R3.

## Remote execution order (2026-09-17)

This is product round 3, paired with T1-APP-STORAGE-POLICY-REMOTE, retaining the complete T3-PDF-MEASUREMENT-REQUESTS behavioral contract above. Dependency names in the front matter bind the remote deliveries. Start only after those functional PRs actually merge; registered metadata alone does not satisfy a dependency. Use the original D:/Projects/MyInspection/scripts/task.ps1 from the main checkout: start with -Base origin/master, then fresh RED and ship with -Base master, without -Local. Run the same candidate's full DoD, R4, verify, scope, licenses, secrets, complete-diff budget, independent R3 and exact-head CI before remote PR merge. Record PR/head/checks/merge and complete R5. This execution paragraph supersedes earlier local-only routing text, without reducing any acceptance or adding another card to the five-round count.