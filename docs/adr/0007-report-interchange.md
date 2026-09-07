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

### Pure import planning contract

Approved split registration: `T3-REPORT-IMPORT-PLAN-SNAPSHOT` → `T3-REPORT-IMPORT-PLAN-PROJECTION` → `T3-REPORT-IMPORT-PLANNER` run sequentially. The parent retains its original acceptance and downstream dependencies. All registered work remains pending remote delivery; no local merge, review-counter reset or gate waiver is claimed.

- The pure input binds selected property, tenancy, report date, source SHA-256, manifest digest, active Routine v2 template ID/content hash and active-draft preflight result. Freeze caller collections at input construction and consume that one snapshot throughout planning. Report date is a valid strict ISO calendar date (`YYYY-MM-DD`); missing/invalid dates are named blockers. It is a snapshot, not proof of live database authorization. Assembly obtains the active template using `TemplateStore.currentRoutineVersionId`; commit revalidates mutable state.
- Room targets use existing `(room_key, instance_no, stable_id)` semantics. Input copies the ordered configured room plan and suppression set from existing capture preflight; it never creates future draft UUIDs or infers counts from a report. Every configured unsuppressed template-instance target remains in the unrated target inventory. Native `android/core/src/main/kotlin/nz/myinspection/core/capture/RoomInstancePlanning.kt` derives display labels from configured count (`count == 1L` uses the bare room key) and filters suppressed stable IDs before forming active room keys: fully suppressed rooms require no instance or target; partially suppressed rooms remain required. Preview/receipt bind this context; commit recomputes it.
- Exact suggestions compare source and template `textEn`/`textZh` through the same existing `ExtractedText.normalized` operation, case-sensitively. A supplied source room must match the normalized configured room display label. Only one matching target permits a suggestion; missing or ambiguous targets remain actionable. Source statuses must exactly match an allowed status after the same normalization; no synonym conversion or automatic confirmation occurs.
- Every manifest entry has one review owner, addressed by category/index under its manifest digest. Exact item/fragment overlaps may share an owner only with equal source location and raw text; identical text at different locations stays distinct. Preserve item name, status and comment together, with explicit accounting if only part is retained. Unclaimed fragments, identity evidence and warnings remain visible; source package paths never enter the receipt.
- A unique CAPTION fragment and its candidate captions at the same package part/paragraph may share a grouped owner retaining the entire parent text and every candidate. Grouping does not assert that candidates exhaust the parent text. Multiple candidates, missing/ambiguous parent fragments, and residual text require explicit constituent decisions; no silent prefix loss or bulk ambiguous association is permitted. Reallocated occurrence numbers are not evidence of text equality.
- Each substantive image owns its explicitly linked placements; repeated placements and missing image references remain accounted for. Initial privacy is always transient `UNREVIEWED_EXCLUDED`; absent bytes cannot be confirmed as an included image.
- Warning disposition is closed: `PAGINATION_EXCLUDED`, `METADATA_EXCLUDED`, `URL_EXCLUDED`, `LAYOUT_IMAGE_EXCLUDED` are visible extractor provenance exclusions with fixed reasons. `UNRESOLVED_TEXT`, `AMBIGUOUS_COLUMNS`, `UNRESOLVED_NARRATIVE`, `MISSING_IMAGE` attach actionable obligations to an unambiguous source owner, otherwise a named global blocker. `IMAGE_REVIEW_REQUIRED` requires individual photo privacy review; `AMBIGUOUS_CAPTIONS` prevents automatic/bulk caption associations. No blanket warning acknowledgement can settle unresolved content.
- Summary paragraphs individually include into the summary or exclude with a reason. Included paragraphs join in manifest order with one newline into a single `GEN-SUMMARY-01` note, with one explicit allowed status selected for the complete note. Empty inclusion writes no summary. Duplicate inclusion and conflicting target writes reject instead of overwriting.
- Any material context or decision edit invalidates preview. Bulk confirmation requires the complete current preview, rejects partial/stale actions atomically, and applies only to exact allowed suggestions. A ready plan is immutable and still has no authority to write the database.
- Receipt uses the existing `CanonicalJson.serialize` and an independent versioned SHA-256 domain. It records deterministic ordered opaque source IDs, selected native IDs, context binding, source/manifest digests, decisions, bounded exclusion reason codes and warnings. It excludes raw source text/bytes, package paths, URLs and author/vendor data, and is distinct from the native finalized inspection hash.

### Hostile package boundary

The reader enforces entry/byte/ratio/XML/image bounds; rejects ambiguous or traversing paths, external relationships, macros, OLE, ActiveX and encryption; disables entities/network; and returns only allowlisted Word story/relationship/media parts. The planned custom-properties exception admits only normalized `docprops/custom.xml` with the exact transitional content type/root and one internal package-root relationship; it applies all existing ZIP/XML checks, then discards the part before returned parts and extraction. Property names, values and comments never enter report evidence or diagnostics; other metadata compatibility is unchanged. Strings and links remain inert. No business DB or final-media write occurs before reviewed commit.

### DOCX extraction split contract

`T3-DOCX-IMAGE-QUALIFICATION`, `T3-DOCX-XML-TREE` and `T3-DOCX-EXTRACTION-MANIFEST` precede the existing extractor. Each retains independent acceptance and delivery gates; the extractor keeps its integration coverage and synthetic ambiguity counts. No review-size limit changes.

Only complete bounded non-interlaced RGB8/RGBA8 PNG payloads may qualify as layout shims: structural ordering, every CRC and one complete zlib stream with exact legal scanlines must pass. Limits are 24 pixels per axis, 65536 encoded bytes and 64 chunks; proven header dimensions still enforce the 40000000-pixel rejection bound. JPEG and unsupported/unverified input remain review-required; headers or end markers alone never authorize exclusion.

The internal namespace-aware XML tree rejects DTD/entities before external access, with package byte/depth/node/text limits remaining upstream. The immutable manifest preserves the existing API and DOCX-EXTRACT-1 encoding with independent vectors and copied/read-only collections. Normalization explicitly fixes the original six ASCII whitespace characters to avoid platform regex drift.

`T3-DOCX-CUSTOM-PROPERTIES` follows reader and extractor: admit only the fixed transitional custom-properties part with exact content type/root and one internal package-root relationship, apply existing ZIP/XML safety/resource bounds, then discard before returned parts and extraction. Names, values and comments never become report evidence or diagnostics. Inert child XML remains opaque; no broad metadata allowlist, runtime fetching, full private-source import or implementation delivery is implied.

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
