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

Approved pending split: `T3-REPORT-IMPORT-PLAN-SNAPSHOT` → `T3-REPORT-IMPORT-PLAN-PROJECTION` → `T3-REPORT-IMPORT-PLANNER`, sequentially. Parent acceptance and downstream dependencies remain.

- The pure input binds selected property, tenancy, report date, source SHA-256, manifest digest, active Routine v2 template ID/content hash and active-draft preflight result. Freeze caller collections at input construction and consume that one snapshot throughout planning. Report date is a valid strict ISO calendar date (`YYYY-MM-DD`); missing/invalid dates are named blockers. It is a snapshot, not proof of live database authorization. Assembly obtains the active template using `TemplateStore.currentRoutineVersionId`; commit revalidates mutable state.
- Room targets use existing `(room_key, instance_no, stable_id)` semantics. Input copies the ordered configured room plan and suppression set from existing capture preflight; it never creates future draft UUIDs or infers counts from a report. Every configured unsuppressed template-instance target remains in the unrated target inventory. Native `android/core/src/main/kotlin/nz/myinspection/core/capture/RoomInstancePlanning.kt` derives display labels from configured count (`count == 1L` uses the bare room key) and filters suppressed stable IDs before forming active room keys: fully suppressed rooms require no instance or target; partially suppressed rooms remain required. Preview/receipt bind this context; commit recomputes it.
- Exact suggestions compare source and template `textEn`/`textZh` through the same `ExtractedText.normalized` operation specified in the extraction contract below, case-sensitively. A supplied source room must match the normalized configured room display label. Only one matching target permits a suggestion; missing or ambiguous targets remain actionable. Source statuses must exactly match an allowed status after the same normalization; no synonym conversion or automatic confirmation occurs.
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

`T3-DOCX-IMAGE-QUALIFICATION`, `T3-DOCX-XML-TREE` and `T3-DOCX-EXTRACTION-MANIFEST` precede the extractor, each with independent gates. The reference below fixes integration acceptance.

User-approved conservative image rule (2026-09-08): complete bounded non-interlaced RGB8/RGBA8 PNG validation yields only a small-image candidate, never proof of decoration. Structural ordering, CRCs and one exact zlib scanline stream must pass within 24 pixels per axis, 65536 bytes and 64 chunks; proven headers retain the 40000000-pixel rejection bound. Every accepted image and placement remains for review, including valid small content images. The synthetic fixture retains 82 images (67 larger plus 15 small), 83 placements and 82 IMAGE_REVIEW_REQUIRED warnings; 64 items/89 captions remain. LAYOUT_IMAGE_EXCLUDED stays a wire enum value but this extractor never emits it; no automatic shim rule is authorized.

The binding [extraction contract](../references/docx-extraction-contract-llms.txt) specifies the API, field/null/order encoding, independent golden vectors, normalization and parser behavior; implementation status is recorded below. The internal XML tree rejects DTD/entities before external access, with package resource limits upstream. The manifest copies all eight lists and uses the specified DOCX-EXTRACT-1 encoding and six-ASCII-whitespace normalization.

`T3-DOCX-CUSTOM-PROPERTIES` follows reader and extractor under the package boundary above. The linked contract pins literal bindings and adversarial controls; inert child XML stays opaque. Other metadata, runtime fetching and full private-source import remain outside this exception.

Implementation record (2026-09-08): `T3-DOCX-PACKAGE-READER` is remotely delivered by [PR #242](https://github.com/Asun28/MyInspection/pull/242), squash `a4febb7fb554aca6dc8efebc063279dd48683bf1`; reviewed head `d56d4e396fd21c0c9c7a9634fc73ee590816b0ba` received formal R3 pass with empty reasons and candidate CI `verify` SUCCESS. Standard ZIP/SAX APIs enforce the bounded no-write package boundary, reject XInclude and count XML elements across all parts; errors expose only closed reasons and numeric counts. Latest recovery DoD (28 tests) and project verify passed. The 41 historical same-blob mutations were not rerun here. Image handling is encoded-byte bounds and PNG/JPEG signatures only. At that reader merge, image qualification, the XML-tree/manifest/extractor split, custom-properties compatibility and pure import planning remained pending under separate delivery gates.

Implementation record (2026-09-08): `T3-DOCX-XML-TREE` is remotely delivered by [PR #261](https://github.com/Asun28/MyInspection/pull/261), squash `94dfbe58`, reviewed head `de12cd09`, formal R3 pass and exact-candidate CI success. Fresh RED/GREEN covers 6 parser tests; 11 production and 2 I/O-counter mutations fail named assertions. Physical ancestor-test deletion demonstrated its unique guard and was restored. The internal ordered namespace-aware tree rejects DTD/entities before external I/O; bounded reader parts remain the production input. JDK 17 checks do not claim ART acceptance or complete extraction/import delivery.

Implementation record (2026-09-08): `T3-DOCX-EXTRACTION-MANIFEST` is remotely delivered by [PR #262](https://github.com/Asun28/MyInspection/pull/262), squash `11cf5899`, reviewed head `38c15858`, formal R3 pass and exact-candidate CI success. Its eight collections are detached and read-only; independent 151/1472/230-byte vectors pin DOCX-EXTRACT-1, raw/source/null ordering and normalization. Fresh RED had 11 failures; GREEN passed 10 retained tests, 45 mutations failed assertions, and full-core deletion validation justified removing one redundant test. Image qualification, extraction, custom-properties compatibility and pure planning remain separate pending deliveries; JVM validation does not establish ART acceptance.

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

Implementation record (2026-09-08): `T3-REPORT-HTML-PRESENTATION` is remotely merged through PR #250 (`e792ea75`). Responsive, A4 print, dark and forced-colour CSS rules, class parity and the fixed CSP digest pass tests. System fonts remain in use; browser layout and glyph rendering were not verified because local URL access was denied. No alternate route was used, and this delivery does not change the bilingual report requirement.
