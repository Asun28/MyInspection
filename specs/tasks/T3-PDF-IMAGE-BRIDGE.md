---
id: T3-PDF-IMAGE-BRIDGE
title: Android BitmapFactory/Canvas port that completes the delivered image bridge
status: merged
depends_on: [T3-PDF-IMAGE-OWNERSHIP, T1-SPIKE-PLATFORM]
parallelizable_with: []
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/export/pdf/AndroidPdfImagePort.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/AndroidPdfImagePortTest.kt
forbid:
  - Changes to core sampling, quality, program geometry, composer, text measurement, fonts or source image bytes
  - Changes to the delivered bridge, port or fit geometry; PdfDocument page/document lifecycle, output publication, path selection, bitmap caches or configurable fit modes
  - New dependencies or claims that JVM tests prove Android decoding, physical PDF peak memory or four-quality acceptance
non_goals:
  - Page and document execution, text forwarding and output stream lifecycle (T3-PDF-RENDER-DEVICE)
  - Fixed real80 fixture construction and full physical acceptance (DEVICE-FIXTURE and DEVICE-ACCEPTANCE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 the adapter implements the delivered PdfImagePort with BitmapFactory: bounds decode with inJustDecodeBounds and return null when either reported dimension is non-positive; sampled decode passes the bridge's sampleSize unchanged as inSampleSize and returns null when the decoder does"
  - "A2 draw calls Canvas.drawBitmap with the given source pixel rectangle and destination point rectangle unchanged; the decoded-image handle reads width and height from the bitmap itself and recycle calls Bitmap.recycle"
  - "A3 openSource returns the caller's authorized stream for the photoId and closeSource closes exactly that stream; the adapter searches no album, chooses no path, opens no document and applies no EXIF rotation, matrix, rescaling, font or executor API"
  - "A4 a JVM test reads the adapter source and asserts the required platform calls and the absence of the listed forbidden APIs; the adapter is device code (L280) and no device acceptance is claimed"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: debug assembly compiles the adapter against the delivered port and app JVM tests pass, including the adapter source scan; no PdfDocument or device acceptance is claimed
review_gate: codex {verdict:pass}
hygiene: named source mutations drop inJustDecodeBounds, stop forwarding inSampleSize, or introduce a listed forbidden API; each must fail the source-scan test
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-PDF-IMAGE-BRIDGE

## Split record (2026-09-23, user ruling)

This card was first implemented as one 536-line change covering fit geometry, bridge ownership and the Android
adapter (branch now renamed `wip-image-bridge-combined`, commit `2746fafd`; DoD green, R3 never ran because the
Codex quota was exhausted). The user asked for several smaller changes. The id is kept for the last card of the
chain, so `T3-PDF-RENDER-DEVICE` still depends on the complete bridge:

1. `T3-PDF-IMAGE-FIT`: pure fit geometry.
2. `T3-PDF-IMAGE-OWNERSHIP`: orchestration, delegated sampling, stream/bitmap ownership through the narrow port.
3. `T3-PDF-IMAGE-BRIDGE` (this card): the Android adapter over BitmapFactory and Canvas.

Review routing while the Codex quota is exhausted is recorded in `T3-PDF-IMAGE-FIT`.

## Design

`AndroidPdfImagePort.kt` binds caller-provided authorized source access (a function from photoId to a new
`InputStream` per call) and the page `Canvas` to the delivered `PdfImagePort`. It owns each stream it opens and
closes bounds and decode streams separately through `closeSource`. The bitmap a decode returns belongs to the
bridge until it recycles it. The decoder reports -1 for both bounds when the bytes are not a picture it
understands, which the adapter reports as null.

Product photographs are already oriented by the intake pipeline; this adapter adds no EXIF rotation. Approved
real80 files have 67 absent orientation tags and 13 values of 1, but this read-only input observation does not
prove Android decoding. A maximum of one managed decoded bitmap is an ownership property, not a total native
PdfDocument-memory bound: the isolated single-image diagnostic retained native allocation until write/close even
after bitmap.recycle.

Forecast: `AndroidPdfImagePort.kt` 50–60 lines, `AndroidPdfImagePortTest.kt` 30–45 including the R4 summary;
80–105 lines / 5k–7k characters.

## Delivery record (2026-09-23)

Merged locally: master `fee6451f`, feature `4993e073`, 2 files / 174 lines. With this card the image bridge chain is
complete (FIT `f367ca86`, OWNERSHIP `1558594d`, adapter `fee6451f`), so `T3-PDF-RENDER-DEVICE` can consume it.

Review: DeepSeek V4 Flash pre-review rounds 1-5 (round 2's block was a tooling artefact: the review input had diffed
master's tip, which another session had moved, instead of the merge-base). Fresh Opus 5.5 R3 rounds 1 and 2 blocked
the source-scan test: first a denylist that rotation, density, baker and content-resolver edits slipped past, then
comment stripping that code hidden in string literals slipped past, plus presence checks that added statements
passed. After the round cap the user chose to pin the exact source: the one JVM test now asserts the adapter file
equals the reviewed text (CRLF normalised), and R4 killed 22/22 compiling mutants including every demonstrated
escape. Opus R3 round 3 (user-authorized) passed on tree `3a61ec1e`, which equals the merged tree. `ship -Local` ran
every deterministic gate; its optional R3 leg was skipped because codex was removed from PATH for that process.

Follow-up (outside this card): `:app:testDebugUnitTest` does not declare the source files that source-reading tests
read, so an edit that leaves bytecode identical (for example a comment) can leave this pin UP-TO-DATE and unreviewed.
`android/core` declares such inputs (PR #188); `:app` needs the same, or DoD runs with `--rerun-tasks --no-build-cache`.
