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

## Rejected alternatives

Opaque/read-only attachment; DOCX layout as schema; separate PDF/HTML projections; CSS-only privacy hiding; raw DOCX/author retention; automatic status/privacy/finalize confirmation.

## Consequences

Imports follow ordinary draft, autosave, completeness, finalize and immutable-history rules. Hostile-input and crash-recovery tests are mandatory. Renderer and operational UI remain separate cards; this ADR fixes their shared boundary.
