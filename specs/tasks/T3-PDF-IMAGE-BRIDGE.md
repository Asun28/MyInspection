---
id: T3-PDF-IMAGE-BRIDGE
title: Android sampled image bridge with fixed full-image fitting and resource ownership
status: todo
depends_on: [T3-PDF-RENDERER, T1-SPIKE-PLATFORM]
parallelizable_with: []
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/PdfImageBridge.kt
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/AndroidPdfImagePort.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfImageBridgeTest.kt
forbid:
  - Changes to core sampling, quality, program geometry, composer, text measurement, fonts or source image bytes
  - PdfDocument page/document lifecycle, output publication, path selection, bitmap caches or configurable fit modes
  - New dependencies or claims that JVM bitmap counts prove physical PDF peak memory or four-quality acceptance
non_goals:
  - Page and document execution, text forwarding and output stream lifecycle (T3-PDF-RENDER-DEVICE)
  - Fixed real80 fixture construction and full physical acceptance (DEVICE-FIXTURE and DEVICE-ACCEPTANCE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 each PdfImageOp reads source bounds, calls the delivered PdfImageSampling.inSampleSize with the unchanged source and target dimensions, then passes that result to sampled decoding; no sampling arithmetic or path policy is copied"
  - "A2 the complete decoded image is drawn with fixed FIT_CENTER inside its unchanged placement, using actual decoded dimensions, preserving aspect ratio and centering without cropping, stretching or recalculating sampling targets"
  - "A3 every acquired decoded bitmap receives a recycle attempt immediately after drawing or failure, before another decode; null or failed decoding never draws or fabricates a placeholder"
  - "A4 each source stream opened for bounds or decode receives a close attempt; a close failure after decoding cannot lose ownership of the acquired bitmap; failures propagate and prevent a success claim or later image operation"
  - "A5 pure JVM tests exercise the real bridge through narrow ports, with literal rectangle expectations, sampling boundary values, operation order and resource fault cases; the Android adapter directly uses BitmapFactory and Canvas without future executor or font APIs"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: debug assembly and app JVM tests pass, including real bridge sampling/fitting/ownership behavior; no PdfDocument or full device acceptance is claimed
review_gate: codex {verdict:pass}
hygiene: named compiling mutations alter sample forwarding, use source rather than decoded dimensions, stretch or crop the fitted image, omit or delay recycle, and lose decoded ownership when source close fails; each must fail a meaningful assertion
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-PDF-IMAGE-BRIDGE

## Pre-RED split and ownership

The former combined executor forecast reached 688–894 lines including normal tests and repair reserve. Split this independent image unit before any executor RED or R3. It depends only on the already delivered render program/sampling and platform spike, not future text metrics or fonts. RENDER-DEVICE consumes the delivered bridge; full physical acceptance remains with DEVICE-ACCEPTANCE.

Keep image orchestration, the narrow image handle/port and a pure FIT_CENTER rectangle operation in PdfImageBridge.kt. AndroidPdfImagePort.kt binds caller-provided authorized source access and drawing target to BitmapFactory/Canvas. The caller identifies the source for placement.photoId; the bridge does not search albums, choose storage paths or create a PdfDocument. The adapter owns each stream it opens and closes bounds and decode streams separately. Expose only enough resource ownership to test a stream-close failure after successful decoding without leaking the bitmap. No generic resource graph or session framework is needed.

For a valid fixed frame and positive decoded width/height, use scale=min(frameWidth/bitmapWidth, frameHeight/bitmapHeight), then center the scaled whole image within that frame. The source rectangle covers the entire decoded bitmap. Keep the original placement and targetWidthPx/targetHeightPx unchanged. The returned bitmap dimensions, not the original bounds or a predicted rounded size, control the fitted rectangle. Reject invalid sizes and propagate draw/recycle/open/decode/close failures; cleanup attempts must still occur when an earlier operation fails. A throwing cleanup call is not proof that the resource was released.

Product photographs are already oriented by the intake pipeline; this bridge adds no EXIF rotation. Approved real80 files have 67 absent orientation tags and 13 values of 1, but this read-only input observation does not prove Android decoding. A maximum of one managed decoded bitmap is an ownership property, not a total native PdfDocument-memory bound: the isolated single-image diagnostic retained native allocation until write/close even after bitmap.recycle.

## Behavioral checks and budget

Use landscape, portrait, square and 4200x18400 long-image cases in offset frames, with independently calculated literal rectangles and complete-source assertions. Include odd returned decoder dimensions, a 4000x2000 source targeting1000x500 (sample4) and3999x2000 targeting1000x500 (sample2), consecutive ops with maximum live count1, bounds/open/decode/null failures, invalid decoded size, draw/recycle failure and source-close failure after decode. Fake ports must exercise real ownership logic, not implement the algorithm being tested. Ordinary floating-point assertion tolerance does not permit a production epsilon that changes the fit policy.

File forecast: PdfImageBridge.kt70–90 lines; AndroidPdfImagePort.kt45–65; PdfImageBridgeTest.kt150–185; R4 summary15–20. Candidate280–360, plus70–90 repair reserve gives350–450 lines /22k–32k full diff characters. These are forecasts. Recompute before RED and first ship; stop and re-scope at650 lines or45k characters without compressing tests or omitting acceptance. Author GPT-5.6 Terra high for decode/source-close ownership; this includes more than the fixed fitting arithmetic. Formal independent R3 remains GPT-5.6 Sol high.
