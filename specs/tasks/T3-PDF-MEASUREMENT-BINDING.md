---
id: T3-PDF-MEASUREMENT-BINDING
title: Language-aware measurement binding and exact TextRun snapshots
status: todo
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT, T3-PDF-PAGINATION-FIXTURES]
parallelizable_with: [T1-LOCAL-DATA-SECURITY]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportModel.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/DocumentPlan.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTestFixtures.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportContentAdapterTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerGoldenTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerLayoutContractTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerPaginationTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilderTest.kt
  - android/core/src/e2eTest/kotlin/nz/myinspection/core/e2e/GoldenEvidenceCoreHarness.kt
forbid:
  - redefining ReportTypography defaults or the metric guard, Android imports, font assets or actual Paint/Typeface measurement
  - PdfTextOp or PdfRenderProgramBuilder production changes, new wrapping/pagination algorithms, language-content detection or silent fallback snapshots
  - weakening existing report/e2e evidence or claiming JVM tests prove Android glyphs, CJK coverage or visual clipping
non_goals:
  - PdfTextOp metric forwarding and actual rounded placed-box validation (T3-PDF-TEXT-METRICS-OPS)
  - Android measurement, PDF execution, images, export lifecycle or real80 device acceptance
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 TextMeasurer receives TextLanguage and MeasuredText requires a snapshot; ReportComposer validates requested style/language/role/font size and line height against the explicitly selected immutable profile, then invokes the shared signed metric guard for its locally converted line box"
  - "A2 every emitted TextRun carries the complete snapshot from its actual measurement unchanged; all measurement paths including validation, height reserves, captions, elision, footer and ordinary runs use the same request binding boundary"
  - "A3 an elided caption's final line carries the snapshot returned by the final successful candidate measurement; preceding lines retain their original snapshot, and the existing character-safe elision algorithm and layout remain unchanged"
  - "A4 the production DEFAULT composes two 108mm appendix photographs with maximum three-line captions on one page: 256mm within the existing 257mm body; legacy golden/fake/e2e profiles remain explicit and all prior layout guarantees remain covered"
  - "A5 tests use distinguishable safe snapshots for different requests and compare complete expected values per output; isolated invalid-binding and local-box cases reject; existing PDF builder tests receive only necessary direct TextRun snapshots/profile injection and make no new PdfTextOp behavior claim"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: request binding, complete per-measurement TextRun snapshots including final caption elision, DEFAULT same-page two-photo capacity, all existing report layouts and core e2e pass; no PdfTextOp or actual platform glyph claim.
review_gate: codex {verdict:pass}
hygiene: named compiling assertion mutations drop language forwarding, accept mismatched or out-of-box evidence, replace measured snapshots with fixed/derived values, and reuse the original caption snapshot after elision; retain pagination first-chunk versus continuation-budget evidence.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-MEASUREMENT-BINDING

Consume the predecessor's single immutable ReportTypography profile, defaulting to DEFAULT only when the caller omits an explicit profile. Measurement must match the selected style, language, resolved role, font size and line height before emitting runs. Use the predecessor's guard with the existing PdfGeometry local conversion. Do not invent metrics or reconsult defaults to replace rejected evidence.

TextMetricSnapshot remains inert. All point values must be finite; font size is positive; baseline is nonnegative; signed top is nonpositive and bottom nonnegative. The shared helper checks both glyph edges. Composer-side tests must isolate the intended mismatch or bound failure; predecessor direct tests separately prove numerical guards without binding masking.

Caption shortening already remeasures candidates. Preserve the snapshot from the final accepted candidate with that final line, while earlier retained lines keep the original measurement's snapshot. Do not perform new layout or substitute the old snapshot. The regression returns different safe metrics for original and ellipsis-bearing text and checks complete values.

Keep the DEFAULT assembly regression using two photographs, each with three caption runs, on the same appendix page. The existing title and slots use (2*5+2)+2*(108+3*4+2)=256mm of the unchanged 257mm body. A pure-data profile test cannot replace this composed-plan assertion.

Legacy fakes that changed a single style's line height according to text violate the new immutable binding. Two 258mm single-line cases now assert the explicit profile mismatch. The old 93mm room-heading and 250mm section-opening fixture migration is delivered first by T3-PDF-PAGINATION-FIXTURES. Consume those fixed-height fixtures without redoing their refactor; preserve reduced first-chunk budget, full continuation budget, title grouping, full text reconstruction and no overflow. Do not add a production test bypass.

The PDF builder test file is allowed only to adapt three existing direct TextRun constructors and matching Composer profiles. PdfTextOp fields and direct-run placed-box validation belong to the successor OPS card. Actual Android glyph/Typeface/CJK/clipping evidence remains in DEVICE-ACCEPTANCE.

The round-2 test-only draft has not run RED or GREEN. Per-entry invalid-evidence regressions and exact candidate-text preservation bring the projected complete change, including planned R4, to 553 changed lines / 50,986 LF-normalized diff characters. Moving the two independently verifiable fixture hunks to T3-PDF-PAGINATION-FIXTURES projects this card at 514 lines / 47,164 characters, leaving 2836 characters below the early line for repairs. These are pre-execution projections, not test or mutation evidence. After the predecessor merges, start from its actual baseline, preserve all remaining tests, and obtain a new current-card official RED before production. Recompute the actual diff after every increment; pause at 800 lines or 50,000 characters before heavy execution and split if necessary. Preserve the hard 1,000-line / 60,000-character gate without compressing code or dropping acceptance.
