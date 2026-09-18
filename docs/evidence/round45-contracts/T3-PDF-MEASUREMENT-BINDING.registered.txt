---
id: T3-PDF-MEASUREMENT-BINDING
title: Exact TextRun measurement snapshots and final caption provenance
status: todo
depends_on: [T3-PDF-MEASUREMENT-REQUESTS]
parallelizable_with: [T3-PDF-DEVICE-FIXTURE]
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/DocumentPlan.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportComposer.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportComposerLayoutContractTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilderTest.kt
forbid:
  - redefining ReportTypography defaults or the metric guard, Android imports, font assets or actual Paint/Typeface measurement
  - PdfTextOp or PdfRenderProgramBuilder production changes, new wrapping/pagination algorithms, language-content detection or silent fallback snapshots
  - weakening existing report/e2e evidence or claiming JVM tests prove Android glyphs, CJK coverage or visual clipping
  - redefining or bypassing the predecessor TextMeasurer/MeasuredText request API, profile selection or all-entry validation
non_goals:
  - PdfTextOp metric forwarding and actual rounded placed-box validation (T3-PDF-TEXT-METRICS-OPS)
  - Android measurement, PDF execution, images, export lifecycle or real80 device acceptance
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 Consume T3-PDF-MEASUREMENT-REQUESTS language-aware API, required MeasuredText snapshot and complete request/profile/local-box validation unchanged; all its positive and selective-entry negative tests remain in the full DoD."
  - "A2 every emitted TextRun carries the complete snapshot from its actual measurement unchanged; all measurement paths including validation, height reserves, captions, elision, footer and ordinary runs use the same request binding boundary"
  - "A3 an elided caption's final line carries the snapshot returned by the final successful candidate measurement; preceding lines retain their original snapshot, and the existing character-safe elision algorithm and layout remain unchanged"
  - "A4 the production DEFAULT composes two 108mm appendix photographs with maximum three-line captions on one page: 256mm within the existing 257mm body; legacy golden/fake/e2e profiles remain explicit and all prior layout guarantees remain covered"
  - "A5 tests use distinguishable safe snapshots for different requests and compare complete expected values per output; isolated invalid-binding and local-box cases remain covered by the predecessor. All three existing direct TextRun constructors in PDF builder tests receive explicit snapshots; no default or nullable snapshot is introduced, and no new PdfTextOp behavior is claimed."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: request binding, complete per-measurement TextRun snapshots including final caption elision, DEFAULT same-page two-photo capacity, all existing report layouts and core e2e pass; no PdfTextOp or actual platform glyph claim.
review_gate: codex {verdict:pass}
hygiene: named compiling AssertionError mutations replace actual snapshots in ordinary/photo/thumbnail/footer runs, reuse old elision snapshots, replace candidate text with measured lines and break DEFAULT two-photo capacity; retain predecessor request and pagination regression suites.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-MEASUREMENT-BINDING

Consume the complete request boundary delivered by T3-PDF-MEASUREMENT-REQUESTS. Add a required TextRun snapshot field once and update every production and direct-test constructor in the same card. Forward the actual accepted snapshot unchanged, including through block movement, splitting and rebasing. Do not invent metrics or reconsult defaults to replace evidence.

TextMetricSnapshot remains inert. All point values must be finite; font size is positive; baseline is nonnegative; signed top is nonpositive and bottom nonnegative. The shared helper checks both glyph edges. Composer-side tests must isolate the intended mismatch or bound failure; predecessor direct tests separately prove numerical guards without binding masking.

Caption shortening already remeasures candidates. Preserve the snapshot from the final accepted candidate with that final line, while earlier retained lines keep the original measurement's snapshot. Do not perform new layout or substitute the old snapshot. The regression returns different safe metrics for original and ellipsis-bearing text and checks complete values.

Keep the DEFAULT assembly regression using two photographs, each with three caption runs, on the same appendix page. The existing title and slots use (2*5+2)+2*(108+3*4+2)=256mm of the unchanged 257mm body. A pure-data profile test cannot replace this composed-plan assertion.

Legacy profile and request migration is delivered by MEASUREMENT-REQUESTS; fixed-height pagination fixtures are already delivered by PAGINATION-FIXTURES. Preserve all tests and consume their merged baseline. Reapply only remaining snapshot-owned hunks from the parked seven-file draft, never whole old files. Do not repeat the API migration or add a production test bypass.

The PDF builder test file is allowed only to adapt three existing direct TextRun constructors; matching Composer profiles come from the predecessor. PdfTextOp fields and direct-run placed-box validation belong to OPS. Actual Android glyph/Typeface/CJK/clipping evidence remains in DEVICE-ACCEPTANCE.

The round-2 draft has not run RED or GREEN. A fresh pre-RED audit found the unsplit47,164-character estimate lacked25% repair reserve (58,955–60,890 with reserve), so request validation is now a complete predecessor. This four-file successor forecasts175–202 lines/14,358–19,258 characters including R4, or219–253 lines/17,948–24,073 with25% repair reserve. Preserve intact the parked every-output snapshot, final-elision snapshot and DEFAULT two-photo tests and their exclusive helpers. After the predecessor closes, preserve the old draft again, refresh only owned files safely, and obtain this card's official RED before production. Early650 lines/45k and hard1,000/60k remain; no compressed code or deleted acceptance. Author GPT-6 Astra · high; independent GPT-5.6 Sol · high formal R3.
