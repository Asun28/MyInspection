---
id: T3-REPORT-INTERCHANGE-SCHEMA
title: Schema v6 for immutable import provenance and format-aware export receipts
depends_on: [T3-REPORT-CONTENT-CONTRACT, T5-MEDIA-ARCHIVE-SCHEMA]
parallelizable_with: []
status: merged
branch: T3-REPORT-INTERCHANGE-SCHEMA
worktree: C:\wt\T3-REPORT-INTERCHANGE-SCHEMA
allow_paths:
  - android/core/src/main/sqldelight/nz/myinspection/core/db/5.sqm
  - android/core/src/main/sqldelight/databases/5.db
  - android/core/src/main/sqldelight/nz/myinspection/core/db/ReportInterchange.sq
  - android/core/src/main/sqldelight/nz/myinspection/core/db/MediaArchive.sq
  - android/core/src/test/kotlin/nz/myinspection/core/report/interchange/
  - android/core/src/test/kotlin/nz/myinspection/core/media/archive/MediaArchiveSchemaTest.kt
  - android/core/src/test/kotlin/nz/myinspection/core/template/TemplateRoomSchemaTest.kt
  - configs/secrets/tracked-sensitive-allowlist.json
forbid:
  - In-place edits to migrations 1 through 4, databases 1 through 4, canonical JSON v1, or backup format v1
  - Persisting raw DOCX bytes, absolute source paths, vendor URLs, author metadata, or mutable provenance
non_goals:
  - DOCX parsing, native draft commit, renderer bytes, UI, or report delivery claims
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 migration 5 upgrades every existing report receipt to format PDF without changing its inspection, audience, quality, hash, size, or completion meaning"
  - "A2 receipt identity is inspection plus audience plus format plus quality, with PDF allowing four qualities and HTML requiring the explicit NONE quality"
  - "A3 one immutable import receipt records inspection, source SHA-256 and byte size, extractor version, normalized manifest hash, source date, canonical mapping receipt, mapping hash, and imported time"
  - "A4 database constraints reject duplicate inspection/source identities and mutation or replacement of stored provenance; the import table has only the A3 fields, with no raw-source or metadata columns. PLANNER owns canonical mapping JSON, closed fields, privacy and mapping hash generation; COMMIT validates that receipt before persistence. This schema card does not claim to sanitize arbitrary JSON strings or verify their cryptographic hashes"
  - "A5 media cleanup eligibility continues to accept verified PDF receipts only; an HTML receipt cannot unlock photo archival"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.interchange.*" --tests "nz.myinspection.core.media.archive.*" --tests "nz.myinspection.core.template.TemplateRoomSchemaTest"
dod_exit: 0
dod_assert: schema v5-to-v6 migration, tuple constraints, immutable provenance, and PDF-only media eligibility all pass against real SQLite
review_gate: codex {verdict:pass}
version_review: approved 2026-09-08 by user — schema v5-to-v6 via new 5.sqm and schema-only databases/5.db; PDF migration preserves all old receipt fields; PDF four qualities and HTML NONE; immutable import receipt; existing schema assertions updated without dropping migration coverage
hygiene: migration and constraint tests use literal rows and deletion or branch mutations
doc_sync: DATABASE-DESIGN + ADR-0007 + TASK-BOARD
---

# T3-REPORT-INTERCHANGE-SCHEMA

## Deliverable

Add the smallest version-reviewed persistence needed for native import provenance and two export formats. Native data_hash semantics remain unchanged; the source and mapping hashes are separate claims.

## Approved preflight refinement (2026-09-08)

The user approved repairing the card before implementation. Extend the two existing
schema test files because both pin version 5 and the archive test pins the old
receipt columns. Keep their historical migration assertions intact. The legacy
archive query/write API remains PDF-only; new format-aware queries belong to
ReportInterchange.sq, so HTML cannot satisfy MediaArchiveLedger.cleanupEligible.
Register only the new schema-only databases/5.db in the existing exact-path
sensitive-file allowlist; all existing entries and leak checks remain intact.

The import receipt is one immutable row per inspection with a unique source digest.
SQL enforces field shapes, uniqueness and provenance immutability. The mapping JSON
is a receipt produced by PLANNER, not a raw-source container: its canonical encoding,
allowed fields, privacy exclusions and independent hash are owned by that card;
COMMIT revalidates the reviewed receipt before its atomic draft transaction.
Neither card is claimed delivered by this persistence change. No new dependency,
historical migration rewrite, native data_hash change or user-data migration is included.

Implementation budget: approximately 650–800 changed lines including SQL, behavior
tests and mutation receipts, below the unchanged 1000-line/60000-character R3 limits.

## Delivery record (2026-09-08)

- Candidate `b4e77289bc4a1169696ea7609b2f3c65a2974619`, locally merged as `800593b4`; formal R3 first-round `pass`, no reasons. No remote push or PR.
- Real RED preceded implementation; final DoD passed 50 focused tests. Candidate verify passed 1017 core tests (four existing Windows skips) and six Golden Evidence E2E tests. SQLDelight migration verification, scope, license, secrets and size gates passed (480 changed lines / 39353 characters / one schema-only binary).
- Post-merge integration verify passed 1034 core tests (four existing Windows skips), zero failures/errors, and all six E2E tests; this includes the separately merged import review decisions implementation.
- Thirty-two isolated source mutations failed fresh TestNG reports; restored baseline passed. All were rerun against final LF SQL bytes, matching both Git index and merged source. Per-mutant descriptions, failing assertions and final SHA-256 values are recorded in `ReportInterchangeSchemaTest.kt`.
- Local detailed evidence: `_local/T3-REPORT-INTERCHANGE-SCHEMA/` (formal verdict and final mutation logs/results). Fresh and migrated databases cover duplicate/replacement and immutable provenance guards, malformed storage shapes including NUL/BLOB values, typed lookups/order and actual PDF-only media eligibility.
- DATABASE-DESIGN, ADR-0007, TASK-BOARD and CLAUDE current stage synchronized. This persistence delivery does not claim PLANNER/COMMIT completion or real-user migration.
