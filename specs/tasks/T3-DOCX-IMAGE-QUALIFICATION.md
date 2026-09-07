---
id: T3-DOCX-IMAGE-QUALIFICATION
title: Bounded DOCX image qualification for safe layout-shim exclusion
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
  - Authorizing exclusion from signatures, dimensions, PNG chunk framing, or JPEG SOI/SOF/SOS/EOI markers alone
non_goals:
  - Universal image decoding, Android decoder adapters, visual-content classification, metadata persistence, or changes to the reader or extractor
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 a deterministic immutable result separates nullable header dimensions from verified layout-shim qualification; qualifying does not mutate input bytes or write anything"
  - "A2 only complete non-interlaced 8-bit RGB or RGBA PNGs with width and height each 1..24, encoded size at most 65536 bytes and at most 64 chunks can qualify; unsupported profiles, ancillary chunks and JPEG remain review-required"
  - "A3 qualification validates the full PNG signature, exactly one first IHDR, legal IHDR fields, consecutive IDAT chunks, exactly one terminal zero-length IEND, EOF and every chunk CRC; one zlib stream must finish without a dictionary, unread compressed bytes or progress stalls, producing exactly the expected scanlines with filter bytes 0..4"
  - "A4 complete headers without payloads, truncated or corrupted payloads, repaired-CRC corrupt zlib, truncation followed by forged end markers, invalid chunk ordering and overflow-shaped lengths cannot qualify; valid tiny RGB/RGBA fixtures do qualify and normal substantive images remain retained"
  - "A5 proven header dimensions retain the 40000000-pixel closed rejection bound; qualification caps return review-required, decompression is bounded by the tiny scanline budget, native inflater resources are released, and failures expose no input text, filenames or nested causes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.docx.image.*"
dod_exit: 0
dod_assert: synthetic PNG/JPEG payload adversaries prove qualified exclusion versus retained review, precise boundary behavior, bounded inflation, deterministic results and source-byte preservation
review_gate: codex {verdict:pass}
hygiene: each qualification boundary has a named deletion mutation with assertion failures and restored source/test SHA evidence
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-DOCX-IMAGE-QUALIFICATION

## Deliverable

Provide the read-only image qualification boundary needed before the DOCX extractor can exclude layout-sized media. Dimensions are only candidates until the narrowly supported PNG payload is completely validated; a conservative review result is not a claim that unsupported input is corrupt. For the accepted RGB/RGBA subset, exact scanline byte counts, legal filter selectors and a complete valid zlib stream establish reconstructible pixels without a general image decoder. Qualification implements the approved size-based exclusion policy, not a claim about business meaning.

## Split scope

This predecessor supplies the bounded image qualification consumed by the extractor. Only qualified images may be excluded; unqualified images retain IMAGE_REVIEW_REQUIRED. Each card follows its own delivery gates within unchanged review limits.

## Required adversarial fixtures

- Both formats: complete header without payload, truncated payload, corrupted payload, and truncated data with an appended end marker; JPEG always remains review-required.
- PNG: signature/IHDR/CRC/ordering errors; duplicate or separated structural chunks; unknown chunks; overflowing lengths; missing IEND or trailing bytes; empty and fragmented IDAT including a zlib header/trailer split across chunks.
- Inflation: missing or bad Adler trailer, dictionary requests, short/long output, a second zlib stream or trailing compressed data, no progress, and invalid filter selectors including the final row.
- Exact edges: 1 and 24 pixel qualifying dimensions, 25 pixel retained dimensions, qualification byte/chunk limits and limit+1, valid over-40MP header rejection, all five scanline filters, RGB and RGBA, deterministic repeat calls and unchanged caller bytes.
