---
id: T3-DOCX-IMAGE-QUALIFICATION
title: Bounded DOCX image validation with conservative review retention
depends_on: [T3-DOCX-PACKAGE-READER]
parallelizable_with: []
status: todo
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
