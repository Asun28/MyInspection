---
id: T3-PDF-TEXT-METRICS-OPS
title: PdfTextOp measured metric and baseline forwarding
depends_on: [T3-PDF-TYPOGRAPHY-CONTRACT]
parallelizable_with: []
status: todo
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgram.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilder.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/pdf/PdfRenderProgramBuilderTest.kt
forbid:
  - ReportTypography/default/profile recomputation, Android imports, font measurement, wrapping, pagination, geometry policy changes, or asset work
  - accepting direct TextRun metrics without validating style/language binding and the actual rounded placed line box
non_goals:
  - changing A's profile, TextMeasurer, MeasuredText, TextRun, Composer or legacy migration tests
  - Android adapter/executor/device acceptance
acceptance:
  - "A1 PdfTextOp carries the supplied metric snapshot and baselineYPt; builder preserves the approved snapshot and adds only supplied baselineOffsetPt to the existing converted y origin"
  - "A2 builder rejects direct invalid metrics and validates the actual rounded placed line box, including a 2mm placement whose converted height differs from a local/abstract box"
  - "A3 signed snapshot and baseline forwarding are exact behavior, including valid negative glyphTopPt; no defaults, font metrics or sample/layout policy are recomputed"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:test --tests "nz.myinspection.core.report.*"; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :core:e2eTest
dod_exit: 0
dod_assert: behavior tests prove direct invalid rejection, actual rounded-box rejection and literal baseline/snapshot forwarding; all core report and e2e tests remain green.
review_gate: codex {verdict:pass}
hygiene: named R4 mutations remove actual-box validation, replace forwarded metrics, drop baseline offset, or omit run binding; each must fail an assertion rather than compilation.
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-PDF-TEXT-METRICS-OPS

This is split B. It receives A's validated `TextRun` snapshot but owns every `PdfTextOp` change and all behavior that makes it executable: direct-constructor rejection, actual placed-box rounding guard and exact baseline forwarding. It must not reconsult `ReportTypography`; a second lookup could silently replace the measured snapshot.

The nested-thumbnail expected-op migration and the existing no-picture `PdfTextOp` constructor migration are part of B. The frozen candidate extraction is **144 changed lines / 11,443 LF unified-diff characters** (`b-source.patch` / `b-budget.json` in the ignored evidence mirror). It is not GREEN evidence: R4 receipt, repair iterations and final exact extraction remain outstanding.

The snapshot is inert data, so validation of direct runs occurs in the builder, not in its constructor. The existing shared guard requires finite point values, fontSizePt > 0, baselineOffsetPt >= 0, glyphTopPt <= 0 and glyphBottomPt >= 0. Against the actual converted placed height, baselineOffsetPt + glyphTopPt >= 0 and baselineOffsetPt + glyphBottomPt <= heightPt. Builder also checks carried style/language against the run. It must not consult another profile or calculate font metrics.

Tests pin a valid negative glyph top, invalid direct evidence without prior composition, and the 2mm line at y=2mm: local conversion is 6pt while converted edges y=2..4mm leave only 5pt. A 6pt glyph extent must be rejected. A valid forwarding case pins yPt=6, baselineYPt=9.25 and the complete unchanged snapshot. JVM evidence never proves actual Android glyphs, CJK or clipping; DEVICE-ACCEPTANCE retains that obligation.
Recompute the complete diff after R4 and repairs; at 800 lines or 50,000 characters pause and split. Do not compress code or remove acceptance. The hard 1,000-line / 60,000-character review gate remains unchanged.
