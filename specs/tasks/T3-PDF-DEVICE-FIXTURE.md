---
id: T3-PDF-DEVICE-FIXTURE
title: Debug-only authorized real80 fixture preflight and fixed filtered report input
status: todo
depends_on: [T3-REPORT-CONTENT-ADAPTER, T1-SPIKE-PLATFORM]
parallelizable_with: [T3-PDF-MEASUREMENT-BINDING]
allow_paths:
  - android/app/src/debug/kotlin/nz/myinspection/app/export/pdf/PdfDeviceFixture.kt
  - android/app/src/debug/kotlin/nz/myinspection/app/export/pdf/PdfFixtureManifest.kt
  - android/app/src/debug/kotlin/nz/myinspection/app/export/pdf/AndroidFixtureManifestReader.kt
  - android/app/src/test/kotlin/nz/myinspection/app/export/pdf/PdfDeviceFixtureTest.kt
  - docs/spike/PDF-DEVICE-FIXTURE.md
forbid:
  - Changes to core contracts, typography, composer, render program, executor, release entrypoints or runtime dependencies
  - Canvas/PdfDocument execution, device acceptance claims, generated or private photos substituted for the authorized real fixture
  - Querying user inspections/media, modifying original photos, clearing app data, uninstalling the app or publishing export receipts
non_goals:
  - Android text measurement, pagination, rendering, four-quality device or visual acceptance
  - Export UI, publication, reopen verification or verified-artifact receipts
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 the debug fixture preflight accepts only the frozen authorized real80 manifest, with exactly 80 distinct photo IDs and paths, required category coverage, compatible license/provenance, positive dimensions and matching original file hashes; missing or inconsistent evidence fails before a report is returned"
  - "A2 file resolution is confined to the dedicated fixture root and manifest entries, never searches private albums or application records, and preserves source bytes"
  - "A3 a deterministic ReportSnapshot built only from the fixed fixture is projected through the delivered ReportContentAdapter with one fixed audience/privacy option; exactly 80 photos survive, and the canonical photo multiset matches the report photos"
  - "A4 the fixed input records independently established literal expected native data_hash and filtered semanticFingerprint; actual values match their respective constants, remain distinct labels, and are not used as each other's oracle"
  - "A5 fixed bilingual labels, unique photo references and room/item placement support later CJK and appendix-reference checks; the fixture has no dependency on future measurement, renderer or launcher code"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:assembleDebug :app:assembleRelease; if ($LASTEXITCODE -ne 0) { exit 1 }; cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest
dod_exit: 0
dod_assert: debug/release builds and JVM fixture behavior tests pass; controlled local real-manifest preflight evidence and fixed expected hashes are recorded in docs/spike/PDF-DEVICE-FIXTURE.md; no PDF/device success is claimed
review_gate: codex {verdict:pass}
hygiene: named compiling mutations omit exact-count/category/hash/path guards or alter fixed snapshot/reference content and must fail behavioral assertions; restore source SHA before independent review
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-PDF-DEVICE-FIXTURE

## Independent predecessor and fixed input

This is the pre-RED split of DEVICE-ACCEPTANCE's manifest/input work. The current delivered APIs suffice: core/model/InspectionSnapshot.kt, report/ReportModel.kt, ReportContentAdapter.adapt(report, audience, options), ReportContent.nativeIntegrity.dataHash and ReportContent.semanticFingerprint. It neither imports TextMeasurer nor creates DocumentPlan/PdfRenderProgram. MEASUREMENT-BINDING can proceed independently in its own worktree; both cards must really ship to count as two deliveries.

The collection is in controlled local storage at C:/Users/Admin/.agent-reach/myinspection-real80. Select and freeze the repaired manifest's accepted bytes and SHA-256 only after independent source-to-file authorization and category review; the earlier manifest was rejected for shifted provenance and must not be treated as input truth. An existing photo_count or content_check string alone is not proof. Preserve the full provenance/license manifest locally, including rejected-file separation. Raw photos and identifying artifacts remain outside Git. Record manifest digest, exact fixed-input constants and sufficient audit references in the evidence document; do not put 80 raw photo records or images into source to fit the budget.

Keep parsing as a narrow Android JSON reader feeding a typed manifest preflight; the actual pure JVM preflight and fixture builder receive typed rows/file access without running Android stubs. JVM tests exercise the real guard/builder paths, including changed bytes, duplicate/missing photos, absent category/authorization, root escape and snapshot/reference drift. Fake metadata in negative/unit cases is test input only, never substituted physical evidence. The fixed local real-manifest preflight must separately establish the actual 80 input files and hashes; platform reader/device integration remains observable in DEVICE-ACCEPTANCE.

Construct native and report photos from the same ordered immutable descriptors. Use fixed valid UUIDv7 inspection/property/room/item/photo IDs, timestamps and template identity; keep the existing canonical and presentation hash domains distinct. Room panoramas can be room-level and other categories item-level, with short unique references repeated by the existing composer inline and appendix captions. Fix all photos as non-private for this test and project with explicit fixed audience and default privacy exclusion, so exactly 80 survive without querying live records.

Before RED, document the concrete input, derive the expected full native digest and semantic digest once using independently checked serialized inputs, freeze the literal constants and record the derivation command/output. The test must compare produced values to those constants, not compute expected and actual through the same call. No current numeric digest is approved by this draft. ReportContent's constructor is private; use the delivered adapter rather than fabricating it. No font/layout facts are frozen here.

The independently audited input is now frozen as `manifest-approved-20260917.json`, SHA-256 `8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5`, in the controlled directory above. Its array is not ID-sorted; IDs are non-contiguous, and accepted inputs include JPEG/MPO and PNG. Preserve exact descriptors and bytes rather than assuming sequential IDs or converting formats. The native/report mapping and its two expected digests still require the pre-RED derivation above.

Portable JVM cases use synthetic typed rows and exercise the real guards/builder; they do not prove the real80 hashes. Separately execute the actual final builder with the approved 80 descriptors using a controlled local JVM harness, compare to independently derived literal digests, and preserve manifest/harness/final production hashes, exact classpath/command, output and exit status. An ignored harness and row feed are sufficient; no new public entrypoint or personal-machine path in ordinary DoD is needed. Missing or altered approved input must fail that explicit run, never silently skip. Preserve the independent canonical objects and derivation outside Git; record the readable mapping and evidence recipe in the allowed document. Budget its additional 20–40 tracked documentation lines before RED, keeping the existing 650-line/45k early stop.

## Full diff budget and ownership

File-level forecast, including complete tests/evidence/R4: PdfFixtureManifest.kt 100-140 lines; AndroidFixtureManifestReader.kt 25-30; PdfDeviceFixture.kt 90-120; PdfDeviceFixtureTest.kt 120-155; PDF-DEVICE-FIXTURE.md 35-55 including mutation summary. Subtotal 370-500 changed lines; reserve another 90-125 for repairs. Complete forecast 460-625 lines / 28k-40k unified-diff characters, not a measured implementation diff. First authored candidate should remain within 500 lines / 32k characters. Recompute using review.ps1 -SizeOnly before first ship; at 650 lines or 45k characters stop and re-scope before spending R3 rounds. Do not compress tests or omit fixture evidence.

DEVICE-ACCEPTANCE consumes this input and retains every physical four-quality/80-photo obligation, its launcher and actual measurement/composer/builder/executor integration. This card never declares those complete. Author: GPT-5.6 Terra high; independent R3: GPT-5.6 Sol high.
