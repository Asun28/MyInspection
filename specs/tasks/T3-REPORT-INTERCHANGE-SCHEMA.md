---
id: T3-REPORT-INTERCHANGE-SCHEMA
title: Schema v6 for immutable import provenance and format-aware export receipts
depends_on: [T3-REPORT-CONTENT-CONTRACT, T5-MEDIA-ARCHIVE-SCHEMA]
parallelizable_with: []
status: todo
branch: T3-REPORT-INTERCHANGE-SCHEMA
worktree: C:\wt\T3-REPORT-INTERCHANGE-SCHEMA
allow_paths:
  - android/core/src/main/sqldelight/nz/myinspection/core/db/5.sqm
  - android/core/src/main/sqldelight/databases/5.db
  - android/core/src/main/sqldelight/nz/myinspection/core/db/ReportInterchange.sq
  - android/core/src/main/sqldelight/nz/myinspection/core/db/MediaArchive.sq
  - android/core/src/test/kotlin/nz/myinspection/core/report/interchange/
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
  - "A4 duplicate or mutated provenance fails closed, and no row can store raw source bytes, paths, author metadata, or vendor URLs"
  - "A5 media cleanup eligibility continues to accept verified PDF receipts only; an HTML receipt cannot unlock photo archival"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.interchange.*" --tests "nz.myinspection.core.media.archive.*"
dod_exit: 0
dod_assert: schema v5-to-v6 migration, tuple constraints, immutable provenance, and PDF-only media eligibility all pass against real SQLite
review_gate: codex {verdict:pass}
hygiene: migration and constraint tests use literal rows and deletion or branch mutations
doc_sync: DATABASE-DESIGN + ADR-0007 + TASK-BOARD
---

# T3-REPORT-INTERCHANGE-SCHEMA

## Deliverable

Add the smallest version-reviewed persistence needed for native import provenance and two export formats. Native data_hash semantics remain unchanged; the source and mapping hashes are separate claims.
