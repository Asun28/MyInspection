---
id: T3-PDF-TYPOGRAPHY-CONTRACT
title: Pure-data typography profile and signed metric guard
status: in-progress
depends_on: [T3-PDF-RENDERER]
parallelizable_with: []
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/ReportTypography.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/ReportTypographyTest.kt
forbid:
  - changing TextMeasurer, MeasuredText, TextRun, ReportComposer, PdfTextOp, builder, existing fakes or core e2e
  - Android imports, font assets, actual glyph measurement, wrapping, pagination or a second default table
  - claiming pure-data tests prove composed appendix capacity, Android glyphs, CJK coverage or visual clipping
non_goals:
  - request binding, snapshot propagation and caption elision metrics (T3-PDF-MEASUREMENT-BINDING)
  - PdfTextOp forwarding and actual rounded placed-box validation (T3-PDF-TEXT-METRICS-OPS)
plan_ref: context/DESIGN.md#backup-report-health-and-compliance-component-matrix
acceptance:
  - "A1 immutable ReportTypography fixes TITLE=12pt/5mm, BODY=11pt/6mm and CAPTION=9pt/4mm; explicit custom profiles preserve their own rows without changing DEFAULT"
  - "A2 EN resolves to LATIN_SANS and ZH/ORIGINAL/NEUTRAL resolve to CJK_FALLBACK without character inspection"
  - "A3 inert TextMetricSnapshot carries style, language, role, font size, baseline offset and signed glyph top/bottom; malformed data may be constructed, then the internal line-box guard rejects non-finite point values, nonpositive size, negative baseline, wrong bound signs or either escaping edge"
  - "A4 direct tests isolate the guard from any profile binding check, exercise each field with NaN and both infinities, accept negative glyphTopPt and exact edge equality, and reject zero/negative font sizes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: direct profile/role/signed-metric tests pass and the unchanged report suite and core e2e remain green; no claim of composition or platform glyph verification.
review_gate: codex {verdict:pass}
hygiene: named compiling assertion mutations change a default row or role, remove positive-size or finite checks, relax signed/edge guards, and reject valid edge equality; keep evidence concise and never count compiler failures as assertion coverage.
doc_sync: ADR-0007 + TASK-BOARD dependency note after merge
---

# T3-PDF-TYPOGRAPHY-CONTRACT

This card adds only the immutable profile, font-role enum, inert metric snapshot and shared line-box guard. Existing TextStyle and TextLanguage are reused. A malformed snapshot is data until a consumer explicitly calls the guard; do not reject in the snapshot constructor or silently substitute defaults.

All point values are finite. fontSizePt > 0, baselineOffsetPt >= 0, glyphTopPt <= 0 and glyphBottomPt >= 0. A glyph fits a supplied point-height exactly when baselineOffsetPt + glyphTopPt >= 0 and baselineOffsetPt + glyphBottomPt <= heightPt. Negative top is valid; equality on both edges is valid. The helper neither derives metrics nor converts geometry.

The DEFAULT rows remain 12pt/5mm, 11pt/6mm and 9pt/4mm. Their assembled two-photo appendix regression belongs intact to T3-PDF-MEASUREMENT-BINDING: (2*5+2)+2*(108+3*4+2)=256mm within the existing 257mm body, with two maximum-three-line captions on the same page. This card alone does not establish that assembly works.

Isolated source candidate: 126 additions+deletions / 6,062 LF-normalized unified-diff characters. It has not run current-scope GREEN or R4. Include subsequent tests, evidence and repairs in the final budget; pause at 800 lines or 50,000 characters, and retain the hard 1,000-line / 60,000-character gate. Never compress code or discard acceptance to fit.
