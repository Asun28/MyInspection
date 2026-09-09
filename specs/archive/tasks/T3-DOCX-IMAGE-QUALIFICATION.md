---
id: T3-DOCX-IMAGE-QUALIFICATION
title: Bounded DOCX image validation with conservative review retention
depends_on: [T3-DOCX-PACKAGE-READER]
parallelizable_with: []
status: merged
branch: T3-DOCX-IMAGE-QUALIFICATION
worktree: C:\wt\T3-DOCX-IMAGE-QUALIFICATION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/docx/image/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/docx/image/
forbid:
  - Private sample bytes or text in git, file or database writes, network, ImageIO or android.graphics in production, or new runtime dependencies
  - Authorizing automatic exclusion from image dimensions, format validity or payload validation
non_goals:
  - Universal image decoding, Android decoder adapters, visual-content classification, metadata persistence, or changes to the reader or extractor
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 a deterministic immutable result separates nullable header dimensions from a validated-small-image candidate; neither result authorizes exclusion or mutates input bytes"
  - "A2 only complete non-interlaced 8-bit RGB or RGBA PNGs with width and height each 1..24, encoded size at most 65536 bytes and at most 64 chunks yield VALIDATED_SMALL_CANDIDATE; both this and REVIEW_REQUIRED retain mandatory human review"
  - "A3 qualification validates the full PNG signature, exactly one first IHDR, legal IHDR fields, consecutive IDAT chunks, exactly one terminal zero-length IEND, EOF and every chunk CRC; one zlib stream must finish without a dictionary, unread compressed bytes or progress stalls, producing exactly the expected scanlines with filter bytes 0..4"
  - "A4 complete headers without payloads, truncated or corrupted payloads, repaired-CRC corrupt zlib, forged end markers, invalid ordering and overflow-shaped lengths cannot validate; valid tiny RGB/RGBA content images yield candidates, never decorative classifications"
  - "A5 proven header dimensions retain the 40000000-pixel closed rejection bound; qualification caps return review-required, decompression is bounded by the tiny scanline budget, native inflater resources are released, and failures expose no input text, filenames or nested causes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.image.*"
dod_exit: 0
dod_assert: synthetic PNG/JPEG payload adversaries prove bounded validation candidates without exclusion authority, precise boundaries, deterministic results and source-byte preservation
review_gate: codex {verdict:pass}
hygiene: each qualification boundary has a named deletion mutation with assertion failures and restored source/test SHA evidence
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-IMAGE-QUALIFICATION

## Deliverable

Return bounded read-only validation. Valid PNG pixels do not prove decoration; unsupported input remains reviewable, not necessarily corrupt. User approval on 2026-09-08 replaces local size-based exclusion with retention.

API: `DocxImageDimensions(width: Int, height: Int)`; `DocxImageDisposition { REVIEW_REQUIRED, VALIDATED_SMALL_CANDIDATE }`; immutable `DocxImageQualification(dimensions: DocxImageDimensions?, disposition: DocxImageDisposition)`; `DocxImageQualifier.qualify(bytes: ByteArray): DocxImageQualification`. In this image package, define `class DocxImagePixelLimitException : RuntimeException("DOCX_IMAGE_PIXELS")`. Proven dimensions above 40000000 pixels throw exactly this type, fixed message and null cause; tests assert all three. No reader error change or input-bearing fields. Neither disposition permits exclusion.

## Split scope

The extractor retains all accepted images and placements with IMAGE_REVIEW_REQUIRED; no contextual shim exclusion is authorized. Its integration tests prove small-content retention. This unit tests validation; both cards retain independent gates.

## Required adversarial fixtures

- Both formats: complete header without payload, truncated payload, corrupted payload, and truncated data with an appended end marker; JPEG always remains review-required.
- PNG: signature/IHDR/CRC/ordering errors; duplicate or separated structural chunks; overflowing lengths; missing IEND or trailing bytes; empty/fragmented IDAT including split zlib header/trailer. Only IHDR/IDAT/IEND are supported: every other chunk, critical or ancillary, yields REVIEW_REQUIRED without throwing (subject to the pixel limit). Test unknown critical ABCD and ancillary abCD separately with independently verified lengths/CRCs and a valid RGB control; inserting either preserves dimensions and changes only disposition to REVIEW_REQUIRED. This subset limit does not label ancillary-bearing PNGs corrupt.
- Inflation: missing or bad Adler trailer, dictionary requests, short/long output, a second zlib stream or trailing compressed data, no progress, and invalid filter selectors including the final row.
- Exact edges: 1 and 24 pixel validated candidates, 25 pixel unverified candidates, byte/chunk limits and limit+1, valid over-40MP header rejection, all five scanline filters, RGB and RGBA, repeated calls and unchanged bytes. Include a valid small content-pattern image: validation never labels it decorative.

## Remote delivery — 2026-09-08

Delivered by [PR #269](https://github.com/Asun28/MyInspection/pull/269), squash `b43a8d41d9f483284fd6666fe5c17f61c5e47a0c`, reviewed head `0c92e37a9084376f69285ff91d2ee93b83404abf`. First formal R3 passed with empty reasons; exact-head [CI run 34195217644](https://github.com/Asun28/MyInspection/actions/runs/34195217644) succeeded. Normal remote task-loop ship passed RED, DoD, verify, scope, licence, secrets and complete-diff budget gates, then merged with exit 0.

Fresh RED failed all 22 tests; final DoD passed 22/22 without skips. All 35 fresh mutations failed assertions, with a 22-test positive control. Physically deleting the byte-limit test and its source guard let the mutant survive all 927 core tests (4 existing skips); the unique test and source were restored exactly. Final qualifier SHA-256 `c7094ebd340c829a0a6d675c8a02b96c0d27740f047ad342854603f20bad3a83`; final test SHA-256 `42daa4bebdf5b3cba17659a74a279e4e34f7439326715754f7249419494679f3`. Raw XML, mutation receipts and original/restored hashes are preserved under `_local/projection-20260908/remote-image-evidence/`; remote proof is in `remote-image-delivery/`.

Both dispositions retain human review; format validation never permits exclusion. The exact public pixel-limit exception has a fixed message and null cause. Production adds no writes, network or runtime dependency. Official cleanup completed after proof preservation. R5 debt scan found no new concrete divergence; R5.5 skips duplicating L310. Extractor retention and complete import remain separate deliveries.
