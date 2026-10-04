---
id: T3-PDF-DEVICE-ACCEPTANCE
title: Real-device four-quality PDF acceptance with a fixed complex 80-photo fixture
status: todo
depends_on: [T3-PDF-RENDER-DEVICE, T3-PDF-DEVICE-FIXTURE]
allow_paths:
  - android/app/src/debug/kotlin/nz/myinspection/app/export/pdf/
  - android/app/src/debug/kotlin/nz/myinspection/app/spike/PlatformSpikeActivity.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfDeviceAcceptanceTest.kt
  - docs/spike/PDF-DEVICE-ACCEPTANCE.md
forbid:
  - Synthetic placeholder images or private user photos substituted for the authorized real-photo fixture
  - Changes to production renderer, layout, quality, sampling rules, original images, or artifact publication and receipt code
  - Release entrypoints, new dependencies, device data clearing or uninstalling the user's app
non_goals:
  - Export UI, chooser, sharing, atomic publication, reopen verification and verified-artifact receipts (EXPORT-CORE/UI)
  - Manifest parsing/authorization preflight and fixed ReportSnapshot/ReportContent construction (T3-PDF-DEVICE-FIXTURE)
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 a debug-only runner executes the delivered PdfRenderProgram and real Android executor with exactly one fixed authorized 80-photo fixture across LOW, MEDIUM, HIGH and EXTRA_HIGH"
  - "A2 all four exports succeed on the target physical device and record exact fixture manifest, app/source revision, quality parameters, PDF bytes and SHA-256, page count and observed peak-memory sampling method; no OOM occurs"
  - "A3 output size is overall monotonic across the four qualities; any adjacent content-entropy exception has per-image evidence rather than an invented absolute MB limit"
  - "A4 each quality has explicit visual/manual-check PASS or FAIL conclusions for complete CJK glyphs, nameplate small-text readability, no OOM, and correct appendix numbering/backreferences; High nameplate text must be readable"
  - "A5 each drawn footer matches the first 12 characters of the fixed native data_hash, program.identity.dataHash matches its full fixed native value, and program.identity.semanticFingerprint matches the fixed filtered-content value across qualities; these distinct labels are never compared for equality"
  - "A6 missing fixture authorization, missing categories or device evidence, and absent or failed manual-check conclusions prevent an acceptance-complete claim; debug integration has no release entrypoint"
  - "A7 each FixturePhotoDescriptor.file opened for drawing is re-hashed against the descriptor's contentHash and a mismatch is refused before anything from it is drawn, because DEVICE-FIXTURE verifies the bytes only at preflight (user ruling 2026-09-24)"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug :app:assembleRelease; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: builds and app JVM runner tests pass; docs/spike/PDF-DEVICE-ACCEPTANCE.md contains the actual physical-device four-quality records, fixed authorized real80 manifest reference and per-quality binary manual-check conclusions; A1-A6 evidence is mandatory in addition to build green
review_gate: codex {verdict:pass}
hygiene: named mutations prove runner visits all four qualities and refuses an incomplete fixture; restore source SHA before independent evidence review
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-PDF-DEVICE-ACCEPTANCE

## Approved split (2026-09-17)
This card retains the complete real-device acceptance from T3-PDF-RENDER-DEVICE. EXPORT-CORE depends on this card; delivering the executor alone does not complete product PDF export. Receipt-side semantic fingerprint/reopen verification remains with EXPORT-CORE, which can only run after this acceptance card.

Read the DEVICE card, PdfRenderProgram and PdfImageSampling, docs/spike/PLATFORM-SPIKE.md, ADR-0007, and the delivered typography contract before designing the runner. Existing runPdfStress uses generated placeholders and a fixed sample of4; its measurements are a baseline only and must not appear as evidence for this card.

## Required fixture and preflight
Use exactly80 authorized real photographs including room panoramas, small-print nameplates, low-light and high-entropy scenes. Record stable test photo IDs, dimensions, category coverage, provenance/permission and file hashes in a fixed local manifest. Preserve original bytes. Do not search private device photographs or assume a supplied business file is authorized for testing. No qualifying dataset was identified in the 2026-09-17 tracked-asset preflight; obtain its explicit test-only location/permission before capture or import; under the current authorization the Agent may source a licensed public test collection without accessing private albums. Raw photographs and potentially identifying artifacts stay in controlled local storage, not Git.

Consume the delivered DEVICE-FIXTURE fixed audience/privacy-filtered ReportContent, expected full native hash, semantic fingerprint and photo references. Do not recreate its manifest guards or fixed snapshot builder. Use the delivered composer/typography/program/executor chain, not hand-written Canvas pages. The debug runner may resolve only the manifest's test assets and write only its own test output directory. It must not query, replace or clear user inspections/media or publish export receipts. Register one launcher action in the existing debug activity; do not add a main/release entrypoint.

The fixture manifest and fixed input must first pass DEVICE-FIXTURE; actual typography and runner integration are still reviewed here. File-level forecast: debug PdfDeviceAcceptanceRunner.kt 90-120 lines; AndroidPdfDeviceAcceptance.kt 100-135; PlatformSpikeActivity.kt 8-14 changed lines; PdfDeviceAcceptanceTest.kt 80-110; PDF-DEVICE-ACCEPTANCE.md 100-140; mutation summary 20-30. Subtotal 398-549; repair reserve 100-135. Complete forecast 498-684 lines / 31k-46k unified-diff characters, not measured source. Target <=750 lines including all evidence; at 800 lines or 50k characters split before RED/first R3 rather than dropping acceptance.

## Evidence and manual checks
For each quality record input manifest digest, device/model/API, APK/source identity, quality/dpi/sample observations, elapsed time, output bytes/digest, page count, and memory observations with method/interval/checkpoints. Distinguish sampled maximum from continuous peak; document missing samples and failure exit evidence. Validate physical-device no-OOM behavior, not merely a JVM allocation counter.

Record four binary manual-check conclusions per quality: CJK complete, nameplate readable, no OOM, appendix numbering/backreferences correct. Low/Medium readability can be recorded FAIL as an observation without weakening the mandatory High readability condition; all required successful checks must be explicit. CJK, no-OOM and appendix linkage must pass every quality. Explain size exceptions with actual per-image evidence. Keep screenshot/page references and hashes for audit; a build result or old spike screenshot never fills an absent manual-check verdict.

Compare each rendered footer to the fixed native hash prefix of 12 characters and its page count; separately compare program.identity.dataHash to the full fixed source native hash. Compare program.identity.semanticFingerprint to the fixed filtered-content fingerprint. Appendix numbering/backreferences mean the same unique photo.reference appears in the inline and appendix captions for the same photo; the delivered program has no clickable PDF links or return-page fields, and this card does not add them. Exactly 80 means distinct source photos; their inline plus appendix placements normally produce 160 image operations, which must not be confused with fixture count. PDF has no public PdfDocument metadata API. No claim that receipt-side fingerprint, atomic publication or exact reopen verification is complete here.

JVM tests exercise the actual debug runner's four-quality iteration and refusal of a failed/incomplete DEVICE-FIXTURE result through narrow ports; fixture parsing and detailed guard behavior remain tested by that predecessor. Actual Android execution and manual checks remain mandatory: test fakes cannot attest device rendering.

The user authorized the Agent to perform every device action and visual inspection without human assistance. Agent visual review of actual exported pages, with page/screenshot evidence and explicit binary conclusions, fulfills manual checks; missing evidence is still a failure. Publicly sourced real photographs must have explicit compatible licenses and stable provenance/hashes. Do not replace photographic categories with generated illustrations, placeholders or synthetic patterns. No repeat permission request is needed for routine device operations within these boundaries.
