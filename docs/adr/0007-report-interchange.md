# 0007 — Native report import and shared PDF/HTML export

Date: 2026-09-02 · Status: **accepted**

## Context

Privacy-sensitive DOCX reports need to become editable native history. Their ZIP/XML and visual pagination are untrusted and cannot be the data model. PDF and HTML must carry the same reviewed meaning without separate database queries or filtering.

## Decision

### Native Routine draft

- Import starts in a selected property, is blocked by an active draft, and targets deterministic current Routine v2; v1 remains historical only.
- Extraction is read-only. Every row, note, photo and caption is mapped, explicitly excluded or a blocker. Even exact status suggestions remain blockers until individual or previewed bulk confirmation; only `CONFIRMED` and reasoned `EXCLUDED` rows are terminal.
- Photos begin transiently `UNREVIEWED_EXCLUDED`; persisted privacy is user-confirmed. Missing template items stay unrated and normal completeness still blocks finalize.
- Source summary can become the real `GEN-SUMMARY-01` item note only with a user-selected allowed status. Author, attendance, organisation and pagination are provenance exclusions.
- Commit stages reviewed media with a recovery marker, then atomically creates one ordinary editable ROUTINE `DRAFT` and immutable provenance/mapping receipt. It never auto-finalizes or rewrites finalized history links.
- Process death before commit releases source access, deletes staging/manifest/mapping and resets to Choose file with confirmed Details retained; process death after the atomic transaction verifies its marker and enters exactly that ordinary draft. No review decision is falsely restored.
- The source remains untouched; v1 retains no raw DOCX. Raw retention requires a separately reviewed backup-format version.

### Hostile package boundary

The reader enforces entry/byte/ratio/XML/image bounds; rejects ambiguous or traversing paths, external relationships, macros, OLE, ActiveX and encryption; disables entities/network; and reads only allowlisted Word story/relationship/media parts. Strings and links remain inert. No business DB or final-media write occurs before reviewed commit.

Implementation record (2026-09-06): `T3-DOCX-PACKAGE-READER` implements the bounded, no-write package boundary using standard ZIP/SAX APIs. It explicitly rejects XInclude and counts XML elements across all parts; errors retain only closed reasons and numeric counts. Supported image payloads are byte-bounded PNG/JPEG signatures, with pixel and decoding validation remaining downstream.

Split decision (2026-09-06, user-approved): `T3-DOCX-IMAGE-QUALIFICATION` precedes `T3-DOCX-REPORT-EXTRACTOR`. Layout-sized media may be excluded only after the supported non-interlaced RGB8/RGBA8 PNG payload passes bounded structural, CRC and zlib/scanline validation. PNG signature/IHDR and JPEG SOF/SOS/EOI markers alone do not prove a complete payload. JPEG and unsupported or unverified inputs remain review-required. Header dimensions are candidates, not decoding attestation. This conservative core boundary has no file writes, Android decoder or new runtime dependency; universal decoding is outside this split.

Implementation record (2026-09-06): `T3-DOCX-IMAGE-QUALIFICATION` now implements that conservative boundary. Qualification validates complete CRC-checked structural chunks and a single bounded zlib stream with exact legal scanlines. The 64 KiB, 64-chunk and 24-pixel-per-axis caps retain unsupported inputs for review; proven header dimensions still enforce the 40,000,000-pixel rejection limit before those caps. No file writes or runtime dependencies were added.

Implementation record (2026-09-07): `T3-DOCX-REPORT-EXTRACTOR` populates the independently delivered immutable `DOCX-EXTRACT-1` evidence manifest using the delivered XML-tree and image-qualification boundaries. Synthetic coverage retains 64 logical item names, only 24 cell-associated rows, 89 captions and 67 substantive images without inventing photo pairs. Unpaired columns and unmarked narrative/signoffs remain review candidates; marked metadata and source URLs are excluded. Only SHIM_QUALIFIED media is excluded; unqualified PNG and every JPEG retain candidate dimensions, content hashes, review warnings and reachable drawing placements. Non-breaking and optional hyphens retain their Unicode text; legacy page numbers are excluded with pagination warnings. Unsupported run children, tracked revisions/text and malformed field phases fail closed. A universal character-data guard rejects nonblank node values except Word t/instrText in an active paragraph, preventing silent loss under paragraphs, cells, runs or extension nodes; whitespace-only formatting remains compatible. Unsupported drawing containers reject; repeated inline/anchor frames retain order and empty frames remain unresolved placements. Missing or unknown field phases reject. Source URLs are scrubbed from visible text and safely warned in field instructions while ordinary cached labels remain; URI matching respects token boundaries. Identity values require adjacent sibling paragraphs with the same parent, and expired or dangling identity labels remain explicit warnings. The malicious-XML extractor integration test verifies the delivered parser defense. The original supplied package is rejected upstream with UNSUPPORTED_PART for custom document properties; compatibility follow-up is TD174.

Split decision (2026-09-06, second user approval): `T3-DOCX-EXTRACTION-MANIFEST` and `T3-DOCX-XML-TREE` are independent predecessors with disjoint source/test files. The former preserves the existing immutable evidence API and `DOCX-EXTRACT-1` digest encoding with direct-constructor vectors; the latter moves the existing internal namespace-aware tree/parser and tests closed DTD/entity rejection without external I/O. Package byte/depth/node/text bounds remain upstream in `DocxPackageReader`. After both are delivered, the extractor retains all 42 integration tests and repairs drawing loss and identity-label scope within its remaining three files. The unchanged 1,000-line/60,000-character review limits require this split; the user approves one additional official extractor review-counter restoration after implementation, with prior evidence preserved and every gate rerun. No reader allowlist expansion is included.

Compatibility correction (2026-09-06, first-preflight finding): the Manifest predecessor explicitly names the six ASCII whitespace characters previously matched by the JDK default regex. Android's default Unicode character classes otherwise collapse additional interior whitespace and change normalized evidence committed by the digest. This scoped correction preserves existing JDK vectors, public shapes and `DOCX-EXTRACT-1` encoding; it does not claim byte-identical source migration or completed ART testing. Final source verification is rerun after the one-line change.

Implementation record (2026-09-06): `T3-DOCX-XML-TREE` is delivered. The existing parser/tree declarations are now internal in their original package. Direct tests cover namespace, attributes, text, ordering and parent helpers, malformed XML and eight DTD/entity cases, with a calibrated test-only I/O guard. Reader resource limits remain the upstream input boundary.

Implementation record (2026-09-06): `T3-DOCX-EXTRACTION-MANIFEST` is delivered. The existing immutable evidence API and DOCX-EXTRACT-1 byte encoding remain unchanged; independent constructor vectors, all eight copied/read-only collections, raw/normalized/source preservation and field/order/null/Unicode behavior are tested.

### Shared semantic boundary

One immutable `ReportContent` is created after audience/photo filtering. It carries ordered identity, glossary, rooms/items/statuses/notes, reviewed photos, supplements, disclaimer, tenant agreement and separately labelled provenance—never Android, URI/path, A4 geometry, pagination or renderer fields. PDF `DocumentPlan` is layout-only; renderers cannot query, refilter or reintroduce removed bytes. The A4 layout engine reaches that content only through `ReportContentAdapter`, and its layout entry point accepts no audience and no photo option, so re-deciding either downstream is unrepresentable rather than merely forbidden. The layout restates the native `data_hash` it was given and never recomputes one from filtered content; import provenance is drawn under its own heading, never as native integrity.

Integrity labels remain distinct:

- native `data_hash`: unchanged finalized native-evidence claim;
- semantic fingerprint: deterministic hash of versioned filtered content;
- artifact SHA-256: exact generated PDF/HTML bytes;
- import provenance: source, normalized-manifest and mapping-receipt hashes plus extractor version/source date.

No label claims that the native hash attests the DOCX or that different audience artifacts have identical bytes.
Native/semantic and optional source/mapping claims may be embedded. Artifact SHA-256 is computed only after close/reopen verification and is shown in the external receipt/UI, never circularly embedded in the bytes it hashes.

### Formats

PDF is default, keeps four quality levels and is the only archive-eligible report. HTML is optional, self-contained UTF-8, accessible/responsive/printable, and has no quality selector, script, external resource or active content. Both use the same filtered content, escaped serialization, audience/format naming, re-open verification and typed temporary `content://` sharing.

Implementation record (2026-09-06): `T3-REPORT-HTML-PRESENTATION` supplies responsive, A4 print, dark and forced-colour CSS with renderer/class-enum parity and an independently checked literal CSP style digest. It uses system fonts; embedded fonts remain outside this card. Browser visual validation was unavailable, so the verified evidence covers CSS rules and renderer output bytes.

## Rejected alternatives

Opaque/read-only attachment; DOCX layout as schema; separate PDF/HTML projections; CSS-only privacy hiding; raw DOCX/author retention; automatic status/privacy/finalize confirmation.

## Consequences

Imports follow ordinary draft, autosave, completeness, finalize and immutable-history rules. Hostile-input and crash-recovery tests are mandatory. Renderer and operational UI remain separate cards; this ADR fixes their shared boundary.


Implementation record (2026-09-07): `T3-DOCX-CUSTOM-PROPERTIES` locally merged as `b00bcbcd` after formal R3 pass. The fixed transitional `docProps/custom.xml` part requires its exact content type, Properties root QName and exactly one internal package-root custom-properties relationship. Existing ZIP/CRC/expansion and XML limits run before discard. Inert child XML is opaque; full custom-property/VT schema validation is not claimed. Property names, values and comments are excluded from returned parts and extraction evidence; the existing root relationship part can retain its structural target. Synthetic equivalent packages yield identical manifest fields and normalized digest. TD174 is closed for this boundary only; full private-source import and device acceptance remain separate.
