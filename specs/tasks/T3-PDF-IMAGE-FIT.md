---
id: T3-PDF-IMAGE-FIT
title: Fixed FIT_CENTER drawing rectangle from the unchanged placement frame and actual decoded dimensions
status: merged
depends_on: [T3-PDF-RENDERER]
parallelizable_with: []
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/PdfImageFit.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfImageFitTest.kt
forbid:
  - Changes to core sampling, quality, program geometry, composer, text measurement, fonts or source image bytes
  - Source bounds, sampled decoding, streams, bitmaps, Canvas, PdfDocument or configurable fit modes
non_goals:
  - Source bounds, delegated sampling, decode and stream/bitmap ownership (T3-PDF-IMAGE-OWNERSHIP)
  - The Android BitmapFactory/Canvas adapter (T3-PDF-IMAGE-BRIDGE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 the frame is the unchanged placement rectangle in page points (xPt, yPt, xPt+widthPt, yPt+heightPt); a placement with non-positive width or height is refused"
  - "A2 the fitted rectangle uses scale=min(frameWidth/imageWidth, frameHeight/imageHeight) on the given decoded dimensions and centres the whole scaled image inside the frame, preserving aspect ratio with no cropping or stretching"
  - "A3 non-positive decoded dimensions or a frame without positive width and height are refused instead of producing an infinite or NaN rectangle"
  - "A4 pure JVM tests assert independently calculated literal rectangles for landscape, portrait, square, long (4200x18400 proportion) and odd decoded sizes in offset frames"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: debug assembly and app JVM tests pass, including literal FIT_CENTER rectangles and refusal of empty frames and non-positive decoded sizes; no decoding or drawing is claimed
review_gate: codex {verdict:pass}
hygiene: named compiling mutations centre-crop (maxOf), stretch either axis, drop either centring offset, swap frame axes or accept empty or non-positive input; each must fail a meaningful assertion
doc_sync: TASK-BOARD
---

# T3-PDF-IMAGE-FIT

## Split record (2026-09-23, user ruling)

`T3-PDF-IMAGE-BRIDGE` was implemented to a green DoD as one 536-line change (branch `T3-PDF-IMAGE-BRIDGE` at
`2746fafd`), but its R3 never ran because the Codex quota was exhausted. The user asked for several smaller
changes that are easier to review. The card is split into three sequential cards, each with its own RED, DoD,
R4 and R3:

1. `T3-PDF-IMAGE-FIT` (this card): the pure fit geometry.
2. `T3-PDF-IMAGE-OWNERSHIP`: bounds, delegated sampling, decode, draw and stream/bitmap ownership through a narrow port.
3. `T3-PDF-IMAGE-BRIDGE` (narrowed): the Android BitmapFactory/Canvas adapter.

Review routing for this chain while the Codex quota is exhausted (user ruling 2026-09-23): repeated DeepSeek V4
Flash pre-review rounds until no block, then a fresh Opus 5.5 subagent performs the R3 review against
`docs/QUALITY-RUBRIC.md`. This does not change the rubric, DoD, verify, scope, licence or secret gates.

## Design

`PdfImageFit.kt` holds `PdfPointRect` (page points, fractional) and the single fit policy. The frame comes from
the composer's fixed placement unchanged; the fit uses the dimensions the decoder actually returned, never the
source bounds or a predicted rounded size. Scale is `min(frameWidth/imageWidth, frameHeight/imageHeight)`; the
scaled image is centred on both axes. There is no fit-mode parameter and no production epsilon. Input that would
make the arithmetic divide by zero or produce an inverted frame is refused, so no caller can receive an infinite
or NaN rectangle.

Literal expectations are calculated independently of the production code, for example a 1000x400 image in the
frame (10, 20, 110, 70) gives (10, 25, 110, 65), and a 1000x2000 image in the same frame gives (47.5, 20, 72.5, 70).

Forecast: `PdfImageFit.kt` 35–45 lines, `PdfImageFitTest.kt` 80–100, R4 summary 12–15; 130–160 lines /
8k–11k characters including repair reserve.

## Delivery record (2026-09-23)

Merged locally: master `f367ca86`, feature `daa4f720`, 2 files / 196 lines. `PdfImageFit.kt` refuses non-positive
decoded sizes and frames without finite edges and positive extents, computes the fit in Double and clamps every edge
into the frame. 11 tests; R4 26/26 compiling mutants killed against SHA-256 `56FCE5A3...ACD33`.

Review: DeepSeek V4 Flash pre-review rounds 1-8 (blocks on an infinite frame edge and on stale receipts, all fixed),
then fresh Opus 5.5 R3 rounds 1-5. Rounds 1 and 2 found real float defects (a near-MAX frame gave an infinite or NaN
rectangle; the fix still rounded the frame width in Float). Rounds 3 and 4 found comments that claimed more than the
code guarantees. Round 5 passed on tree `0092e994`. The user authorized rounds 3, 4 and 5 one at a time beyond the
cap of 2. `ship -Local` ran every deterministic gate; its optional R3 leg was skipped because codex was removed from
PATH for that process, and the merged tree equals the reviewed tree.
