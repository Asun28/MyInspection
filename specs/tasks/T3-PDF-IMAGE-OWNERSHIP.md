---
id: T3-PDF-IMAGE-OWNERSHIP
title: Image bridge orchestration with delegated sampling and stream/bitmap ownership through a narrow port
status: todo
depends_on: [T3-PDF-IMAGE-FIT, T3-PDF-RENDERER]
parallelizable_with: []
allow_paths:
  - specs/tasks/T3-PDF-IMAGE-OWNERSHIP.md
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/PdfImageBridge.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfImageBridgeTest.kt
forbid:
  - Changes to core sampling, quality, program geometry, composer, text measurement, fonts or source image bytes
  - Copying fit or sampling arithmetic, Android framework types, PdfDocument lifecycle, output publication, path selection, bitmap caches or configurable fit modes
  - Claims that JVM bitmap counts prove physical PDF peak memory or four-quality acceptance
non_goals:
  - The Android BitmapFactory/Canvas adapter (T3-PDF-IMAGE-BRIDGE)
  - Page and document execution, text forwarding and output stream lifecycle (T3-PDF-RENDER-DEVICE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 each PdfImageOp reads source bounds, calls the delivered PdfImageSampling.inSampleSize with the unchanged source and target dimensions, then passes that result to sampled decoding; no sampling arithmetic or path policy is copied"
  - "A2 the complete decoded image (source rectangle 0,0,width,height) is drawn at the delivered T3-PDF-IMAGE-FIT rectangle computed from actual decoded dimensions inside the unchanged placement; an empty frame is refused before any source is opened"
  - "A3 every acquired decoded image receives a recycle attempt immediately after drawing or failure, before another decode; null or failed decoding, or a decoded image without pixels, never draws or fabricates a placeholder"
  - "A4 each source stream opened for bounds or decode receives a close attempt; a close failure after decoding cannot lose ownership of the acquired image; failures propagate and prevent a success claim or later image operation, with cleanup failures attached as suppressed to the failure already in flight"
  - "A5 pure JVM tests exercise the real bridge through a recording fake port that decides nothing, with literal rectangle expectations, sampling boundary values, exact operation order, at most one live decoded image across consecutive operations and resource fault cases"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: debug assembly and app JVM tests pass, including real bridge sampling forwarding, fitting from decoded dimensions and ownership behavior; no Android adapter, PdfDocument or device acceptance is claimed
review_gate: codex {verdict:pass}
hygiene: named compiling mutations alter sample forwarding, use source rather than decoded dimensions, omit or delay recycle, drop a close on a failure path, let a cleanup failure replace the failure in flight and lose decoded ownership when source close fails; each must fail a meaningful assertion
doc_sync: TASK-BOARD
---

# T3-PDF-IMAGE-OWNERSHIP

Second of the three cards split from `T3-PDF-IMAGE-BRIDGE` on 2026-09-23 (see the split record and review routing
in `T3-PDF-IMAGE-FIT`). It starts only after `T3-PDF-IMAGE-FIT` is merged.

## Design

`PdfImageBridge.kt` holds the narrow port (`PdfImagePort`), the decoded-image handle (`PdfDecodedImage`), whole-pixel
source rectangles (`PdfPixelRect`), the bridge's own failure type and the orchestration. The port opens the
caller-authorized source for `placement.photoId`, reads bounds, decodes with the sample size it is handed and draws
where it is told; it decides nothing about the picture. The bridge opens one stream per purpose (bounds, then
decode) and closes each. A decoded image becomes the bridge's when the port returns it; a port that throws or
returns null must not leave one behind.

Order per op: frame check, open, bounds, close, delegated sampling, open, decode, close, draw, recycle. If the
decode stream's close fails after a successful decode, the image is still recycled before the failure propagates.
A close or recycle failure under an earlier failure is attached as suppressed rather than replacing it. A recycle
that throws is not proof the image was released. One draw call holds at most one decoded image and attempts its
recycle before returning, so calls made one after another never hold two; that is the ownership property
`PdfPageProgram.decodedByteBound` assumes, not a bound on native memory.

Product photographs are already upright from intake; no EXIF rotation is added.

Behavioral cases: landscape, portrait, square and 4200x18400 long images in offset frames; odd returned decoder
dimensions; a 4000x2000 source targeting 1000x500 (sample 4) and 3999x2000 targeting 1000x500 (sample 2);
consecutive ops with maximum live count 1; bounds/open/decode/null failures; zero-pixel decode on either axis;
draw/recycle failure; source-close failure after bounds and after decode; cleanup failures under an earlier failure.

Forecast: `PdfImageBridge.kt` 95–110 lines, `PdfImageBridgeTest.kt` 250–290 including the R4 summary; 345–400
lines / 24k–30k characters including repair reserve.

## Remote delivery (2026-09-24)

Implemented, reviewed and merged locally on 2026-09-23 (local merge `1558594d`, reviewed tree `b85231ae`); the card is
registered on origin first, then its product PR publishes the same two source files. The R3 for this PR is a fresh Opus 5.5 instance
run by `review.ps1` through `ReviewCommand`, by user ruling while the Codex quota is exhausted.
