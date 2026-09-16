---
id: T3-PDF-TYPOGRAPHY-CONTRACT
title: Pure-core language-aware measurement and TextRun snapshot contract
status: in-progress
depends_on: [T3-PDF-RENDERER]
parallelizable_with: []
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportModel.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/DocumentPlan.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportTypography.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportContentAdapterTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTestFixtures.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerLayoutContractTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerGoldenTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerPaginationTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTypographyTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilderTest.kt
  - android/core/src/e2eTest/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreHarness.kt
forbid:
  - PdfTextOp fields, PdfRenderProgramBuilder production changes, or claiming draw-operation metric forwarding before T3-PDF-TEXT-METRICS-OPS
  - android.* / androidx.* imports, Android Paint/Typeface calls, PdfDocument, Canvas, BitmapFactory, font assets, or export lifecycle
  - a new wrapping/pagination algorithm, character inspection, audience/privacy decisions, or a second typography table outside ReportTypography
  - builder-local font metrics, style defaults, or baseline derivation
  - claiming a JVM test proves Android glyph metrics, CJK fallback coverage, or visual clipping
non_goals:
  - AndroidReportTextMeasurer, its glyph-metrics port, Android assembly, font loading, and actual text wrapping; all are owned by T3-PDF-ANDROID-TEXT-MEASURER
  - PDF execution, image sampling, file publication, receipt, export UI, or third-party PDF/font dependencies
  - device visual acceptance; T3-PDF-DEVICE-ACCEPTANCE Agent supplies the full device evidence
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 ReportTypography is the only pure-data profile and fixes TITLE=12pt/5mm, BODY=11pt/6mm and CAPTION=9pt/4mm; EN resolves to LATIN_SANS and ZH/ORIGINAL/NEUTRAL resolve to CJK_FALLBACK without character inspection; its worst-case three-line captions preserve two 108mm appendix photographs within the existing 257mm body"
  - "A2 TextMeasurer receives TextLanguage and its MeasuredText returns a metric snapshot bound to requested style, language, resolved role and font size; ReportComposer refuses mismatched or non-finite snapshots, nonpositive font sizes, negative baseline offsets, invalid signed glyph bounds, or line-box-incompatible snapshots before emitting TextRun"
  - "A3 TextRun carries the accepted snapshot unchanged; existing PDF builder test fixtures adapt their direct TextRun constructors and selected profiles mechanically, while PdfTextOp fields and all builder metric behavior belong solely to T3-PDF-TEXT-METRICS-OPS"
  - "A4 core tests pin the three profile rows, language forwarding, snapshot validation and exact forwarding into TextRun; existing golden layouts retain explicit safe small-text snapshots and core e2e adapts its deterministic fake without weakening evidence"
  - "A5 core tests prove data propagation only. AndroidReportTextMeasurer later uses the selected Paint/Typeface to measure/wrap and produce the actual snapshot; its real Typeface/CJK/clipping proof belongs solely to T3-PDF-DEVICE-ACCEPTANCE"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: core report tests prove the fixed profile and language-aware snapshot reach composition and TextRun unchanged; existing golden layouts and core e2e remain green with explicit deterministic safe snapshots; no assertion claims PdfTextOp forwarding or actual Typeface/glyph proof.
review_gate: codex {verdict:pass}
hygiene: R4 keeps named compiling assertion mutations for changing a default profile row, dropping language forwarding, accepting a mismatched snapshot and accepting a local out-of-box snapshot; remove survivors only after full DoD proves redundancy.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-TYPOGRAPHY-CONTRACT

## Contract

`ReportTypography` is the single approved default table:

| Style | Font size | Line height |
|---|---:|---:|
| TITLE | 12pt | 5mm |
| BODY | 11pt | 6mm |
| CAPTION | 9pt | 4mm |

Language selects the role without character inspection: EN is `LATIN_SANS`; ZH, ORIGINAL and NEUTRAL are `CJK_FALLBACK`, because ORIGINAL and NEUTRAL may contain Chinese.

`TextMeasurer` gains `TextLanguage`; no new wrapping algorithm is added. Its `MeasuredText` returns a `TextMetricSnapshot`, and `ReportComposer` copies an accepted snapshot to each `TextRun`. The snapshot binds requested style, language, resolved role, font size, `baselineOffsetPt`, and actual signed glyphTopPt/glyphBottomPt evidence. Numeric values must be finite; the baseline is non-negative; profile fields must match the request; and the glyph box must fit the converted run line box.

The baseline is not a pure-data guess about a system font. Later, `T3-PDF-ANDROID-TEXT-MEASURER` owns `AndroidReportTextMeasurer`: it consumes this profile, uses selected `Paint`/`Typeface` to wrap and calculate the actual baseline and bounds, and returns this snapshot. It must be a narrow adapter separate from PdfDocument execution and may not re-layout in the executor.

Successor `T3-PDF-TEXT-METRICS-OPS` owns every `PdfTextOp` field and builder metric validation/forwarding. This card leaves the existing builder behavior unchanged. Its PDF test edits only supply explicit required TextRun snapshots and matching selected Composer profiles; no fallback snapshot or draw-operation completion claim is permitted.

## Test plan and budget

RED tests pin default rows, language forwarding, mismatch/non-finite/nonpositive-font-size/negative-baseline/invalid-signed-bounds/out-of-box rejection, and exact snapshot propagation through composer into TextRun. Existing golden fakes return explicit safe small-text snapshots, preserving their pagination assertions. The core e2e fake accepts language and returns the same explicit snapshot; its evidence is not weakened.

A default-profile regression must compose an appendix with two photographs carrying the maximum three caption lines. Existing geometry is unchanged: the bilingual title and two image slots require (2*5+2)+2*(108+3*4+2)=256mm, within the 257mm body. The earlier 8mm/5mm title/caption proposal required 268mm and was rejected before RED. This regression must use the production default profile, not the small profile injected by legacy layout tests. Actual Android font bounds remain a later device measurement obligation.

Legacy pagination fakes that change one style's line height according to text violate the new immutable profile binding. The two 258mm single-line rejection cases therefore assert the explicit profile mismatch at the measurement boundary. Rebuild the 93mm room-heading and 250mm section-opening fixtures with multiple fixed-height lines: retain the reduced first-chunk budget, full-page budget for following chunks, title grouping, complete text reconstruction and no-overflow assertions. Exact fixture numbers may change to represent whole lines; no production bypass, dynamic profile or weaker pagination acceptance is allowed.

The signature migration has twelve allowed files: four production profile/measurement/run/composer files, six report tests/fixtures, the existing PdfRenderProgramBuilderTest constructor adaptation, and GoldenEvidenceCoreHarness. PDF operation production and its new behavior tests transfer intact to T3-PDF-TEXT-METRICS-OPS.

Isolated candidate measured before GREEN: 529 additions+deletions and 48,699 LF-normalized unified-diff characters, including both new files and ordinary three-line context. No GREEN, R4 or repair is yet included. Recompute after each evidence or source edit; at 800 lines or 50,000 characters pause and split before heavy execution. Do not compress code or drop acceptance evidence. The hard 1,000-line / 60,000-character gate remains unchanged.

The default values above are the selected production profile, not proof of platform font bounds. ReportComposer may receive an explicit immutable ReportTypography profile as a dependency, with the default selected when omitted; measurement validation compares against that exact selected profile, not hard-coded global defaults. Existing deterministic layout tests may supply explicit small-font profiles paired with their historical line heights. This is dependency injection, not a user setting or a bypass: all roles, finiteness, binding and glyph-box checks still apply. No test-only methods or silent fallback snapshot are permitted. The selected profile's style/language/role/font-size/line-height must agree with the measured snapshot before composition; the successor OPS card must validate direct-constructor runs against their carried measured evidence without consulting another profile table.



Signed metric convention: all point values are finite. fontSizePt > 0 and baselineOffsetPt >= 0. glyphTopPt <= 0 and glyphBottomPt >= 0 are signed offsets relative to the baseline (DEVICE obtains maximum font bounds from Paint.FontMetrics.top/bottom, not absolute lengths and not a character-count estimate). Negative glyphTopPt is valid. A glyph fits exactly when baselineOffsetPt + glyphTopPt >= 0 and baselineOffsetPt + glyphBottomPt <= the actual converted line-box height. Composer validates its local line box in this card; the successor OPS card revalidates the actual edge-to-edge converted placed box, so rounding cannot silently clip. Tests must include valid negative glyphTopPt and reject negative baseline, nonpositive size, wrong bound signs, non-finite values and each out-of-box side independently. Do not reject every negative metric as one blanket rule.

TextMetricSnapshot is inert immutable data. ReportComposer validates it at the measurement boundary; do not constructor-reject malformed snapshots, because the successor builder must validate direct TextRun evidence independently.
