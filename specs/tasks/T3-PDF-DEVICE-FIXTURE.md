---
id: T3-PDF-DEVICE-FIXTURE
title: Debug-only authorized real80 fixture preflight and fixed filtered report input
status: merged
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

Before RED, document the concrete input, derive the expected full native digest and semantic digest once using independently checked serialized inputs, freeze the literal constants and record the derivation command/output. The test must compare produced values to those constants, not compute expected and actual through the same call. The approved literals and mapping are recorded below. ReportContent's constructor is private; use the delivered adapter rather than fabricating it. No font/layout facts are frozen here.

The independently audited input is now frozen as `manifest-approved-20260917.json`, SHA-256 `8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5`, in the controlled directory above. Its array is not ID-sorted; IDs are non-contiguous, and accepted inputs include JPEG/MPO and PNG. Preserve exact descriptors and bytes rather than assuming sequential IDs or converting formats.

The independently derived and audited fixed mapping is approved before implementation: native data_hash `f8573b3252196b7ac36201755a2e6dbb9fcd91b2485899679aa5230c32751023`; semanticFingerprint `af17258a955afaa0dc73bab853db8ed9fa2424b043274f31bafa01c974a7287c`. The native and semantic canonical objects are respectively12564 and20711 UTF-8 bytes. Controlled evidence is under `_local/rotating-card-orchestrator/device-preflight/fixture-oracle/`, with independent audit and `fixture-oracle-root-approval.json` beside it; the approval preserves the original draft input/receipt bytes. Copy the readable mapping, literal values and audit recipe into the allowed delivery document, without requiring these local paths in ordinary DoD. Mapping uses fixed UUIDv7/time, actual Routine v2 raw-byte template hash, LANDLORD/default privacy exclusion and20/20/21/19 category grouping. The actual Kotlin builder comparison remains mandatory and has not been performed by this oracle.

An isolated physical diagnostic decoded/drew all80 approved inputs in160 fixed pages at the existing EXTRA_HIGH sample values and wrote a PDF without observed OOM; sampled native heap reached1,766,499,376 bytes before write/close. It validates that diagnostic run only. It neither changes the inputs nor replaces the future delivered composer/executor four-quality and visual acceptance.

Portable JVM cases use synthetic typed rows and exercise the real guards/builder; they do not prove the real80 hashes. Separately execute the actual final builder with the approved 80 descriptors using a controlled local JVM harness, compare to independently derived literal digests, and preserve manifest/harness/final production hashes, exact classpath/command, output and exit status. An ignored harness and row feed are sufficient; no new public entrypoint or personal-machine path in ordinary DoD is needed. Missing or altered approved input must fail that explicit run, never silently skip. Preserve the independent canonical objects and derivation outside Git; record the readable mapping and evidence recipe in the allowed document. Budget its additional 20–40 tracked documentation lines before RED, keeping the existing 650-line/45k early stop.

## Full diff budget and ownership

File-level forecast, including complete tests/evidence/R4: PdfFixtureManifest.kt 100-140 lines; AndroidFixtureManifestReader.kt 25-30; PdfDeviceFixture.kt 90-120; PdfDeviceFixtureTest.kt 120-155; PDF-DEVICE-FIXTURE.md 35-55 including mutation summary. Subtotal 370-500 changed lines; reserve another 90-125 for repairs. Complete forecast 460-625 lines / 28k-40k unified-diff characters, not a measured implementation diff. First authored candidate should remain within 500 lines / 32k characters. Recompute using review.ps1 -SizeOnly before first ship; at 650 lines or 45k characters stop and re-scope before spending R3 rounds. Do not compress tests or omit fixture evidence.

Budget ruling (user, 2026-09-23): the delivered candidate `0061b8e8` measures 708 changed lines / 48,888 characters against `master...HEAD`. The user approved proceeding at that size instead of trimming or splitting, because it stays under the 1000-line / 60,000-character R3 gate and trimming would discard evidence or invalidate the 28/28 R4 receipt. Repairs must not push it past that gate. Review routing while the Codex quota is exhausted (same ruling as `T3-PDF-IMAGE-FIT`): repeated DeepSeek V4 Flash pre-review rounds until no block, then a fresh Opus 5.5 subagent performs the R3 review against `docs/QUALITY-RUBRIC.md`; rubric, DoD, verify, scope, licence and secret gates are unchanged.

DEVICE-ACCEPTANCE consumes this input and retains every physical four-quality/80-photo obligation, its launcher and actual measurement/composer/builder/executor integration. This card never declares those complete. Author: GPT-5.6 Terra high; independent R3: GPT-5.6 Sol high.

## Delivery record (2026-09-24)

Merged locally: master `08ab4d8a`, feature `89285d67`, 5 files / 825 lines / 56,560 characters (user budget ruling
above). The public route `AndroidFixtureManifestReader.preflight` hashes the manifest bytes it reads and refuses a
non-approved digest before org.json parses; the digest-taking preflights, `AuthorizedFixture` and `AuthorizedPhoto`
construction are internal or private, and verified collections are read-only copies. 11 JVM tests; R4 63/63 portable
kills with the real80 variable unset. The actual Kotlin builder reproduces native `f8573b32...751023` and semantic
`af17258a...a7287c` on the approved real80 collection; a one-byte change is refused with `FIXTURE-FILE-BYTES`.

Review: DeepSeek V4 Flash pre-review rounds 1-7 (round 1 blocked on the size stop, resolved by the ruling above),
then fresh Opus 5.5 R3 rounds 1-5. Round 1 found the verified-by-construction claim unenforced (public digest-taking
preflight, castable mutable collections) plus three comments beyond the code; round 2 untested visibility guards and
descriptor fields; round 3 a doc sentence claiming DEVICE-ACCEPTANCE re-hashes at draw time (the user then added A7 to
that card); round 4 an untested reader digest refusal and one overstated test comment. Round 5 passed on tree
`f22b89c4`. The user authorized rounds 3, 4 and 5 one at a time beyond the cap of 2. `ship -Local` ran every
deterministic gate; its optional R3 leg was skipped because codex was removed from PATH for that process, and the
merged tree equals the reviewed tree. Follow-up: test inputs of `:app:testDebugUnitTest` are `T0-APP-TEST-SOURCE-INPUTS`.
