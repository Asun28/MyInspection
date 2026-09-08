---
id: T3-REPORT-IMPORT-PLANNER
title: Explicit import review and deterministic mapping receipt
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR, T3-REPORT-IMPORT-PLAN-PROJECTION, T3-REPORT-IMPORT-REVIEW-DECISIONS]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-PLANNER
worktree: C:\wt\T3-REPORT-IMPORT-PLANNER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/plan/
forbid:
  - Fabricated stable IDs, auto-confirmed statuses, silent row or photo drops, writes, network, source paths, or vendor metadata in receipts
  - INGOING, EXIT, ANNUAL, baseline mutation, auto-finalize, or bypass of current-template completeness
non_goals:
  - DOCX byte parsing, database commit, media publication, navigation, or Compose UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 planning requires one selected property, report date, tenancy context, current Routine template, and no existing active draft; consume and verify the predecessor context and source inventory"
  - "A2 every extracted row, comment, summary, caption, and substantive photo is mapped, explicitly excluded with a reason, or remains a named blocker; shared-source aliases retain complete ownership"
  - "A3 exact allowed matches are non-terminal suggestions until individually confirmed or explicitly bulk-confirmed from a complete current preview; blank/unknown status blocks, unsupported statuses require explicit selection, and missing current-template items remain unrated"
  - "A4 every photo begins transient UNREVIEWED_EXCLUDED and remains a blocker until privacy review; ambiguous caption associations cannot be bulk-confirmed"
  - "A5 canonical mapping receipt JSON and hash are deterministic and contain selected native IDs, opaque source IDs, decisions, exclusions, source and manifest digest, template binding, and warnings but no raw path, URL, author, or source bytes"
  - "A6 any material edit invalidates preview; partial or stale bulk actions reject atomically; duplicate target decisions never overwrite silently; READY requires exhaustive terminal decisions, a current preview, and zero blockers"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.plan.*"
dod_exit: 0
dod_assert: literal manifests and full projection-to-review flows prove exhaustive mapping, explicit confirmation, privacy, current-preview gates, Routine-only scope, and exact deterministic receipt bytes
review_gate: codex {verdict:pass}
hygiene: wrong status, missing decision, invented ID, silent exclusion, stale preview, privacy bypass and receipt omission mutations each fail a dedicated behaviour test
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-PLANNER

This parent card completes the import review and receipt after source projection.

Provide the pure review model used by the Field Ledger import workflow. It
translates extraction evidence into explicit native choices but has no authority
to write them. Retain the original parent ID for downstream COMMIT/UI dependencies.

The projection predecessor owns initial inventory and suggestions. This card
completes original A1-A5 with explicit decisions, preview, privacy and receipt,
including integrated tests of the delivered projection. Commit independently
rechecks live property/tenancy/template/room configuration and active-draft state.

User-approved split (2026-09-08): T3-REPORT-IMPORT-REVIEW-DECISIONS delivers
immutable individual decisions, constituent accounting, privacy and ordered
summary aggregation first. This parent consumes that independently verified
state and completes current preview, atomic bulk confirmation, READY and receipt.
Original A1-A6 and integrated DoD remain unchanged; downstream COMMIT/UI still
depend on this parent, not the predecessor. Both cards run sequentially through
the existing review-size and delivery gates.
