---
id: T3-REPORT-IMPORT-REVIEW-DECISIONS
title: Explicit immutable import decisions and exhaustive source accounting
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR, T3-REPORT-IMPORT-PLAN-PROJECTION]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-REVIEW-DECISIONS
worktree: C:\wt\T3-REPORT-IMPORT-REVIEW-DECISIONS
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/ImportReview.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/plan/ImportReviewTest.kt
forbid:
  - Invented native IDs, automatic status or privacy confirmation, silent source loss, conflicting target writes
  - Database or filesystem writes, network, non-Routine import, or changes to frozen contracts
non_goals:
  - Preview, bulk confirmation, READY authorization, mapping receipt, DOCX parsing, media publication, or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 derive immutable review state from delivered snapshot and projection; preserve context blockers, complete source ownership and initially unrated native targets"
  - "A2 explicit decisions account for every owner, alias and caption constituent; unknown, duplicate or incomplete commands reject without changing prior state; unresolved content and warnings remain named blockers"
  - "A3 exact suggestions remain non-terminal until individual confirmation; selected target and status must be current and allowed, unsupported or blank source statuses require explicit selection, and conflicting target writes reject"
  - "A4 photos begin UNREVIEWED_EXCLUDED; include or exclude requires individual privacy review, missing image references cannot be included, and all linked placements remain accounted for"
  - "A5 each summary paragraph is explicitly included or reasoned excluded; included paragraphs join in manifest order with one newline into GEN-SUMMARY-01 with one explicit allowed status; empty inclusion writes no summary"
  - "A6 warning disposition remains closed, provenance exclusions use fixed reasons, unresolved warning ownership cannot be blanket acknowledged, and every material decision edit creates an immutable new state"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.plan.*"
dod_exit: 0
dod_assert: literal manifests and real projection-to-decision flows verify context, exhaustive constituents, explicit status and privacy, ordered summary and atomic conflict rejection
review_gate: codex {verdict:pass}
hygiene: source omission, wrong status, invented target, privacy bypass, summary order, warning acknowledgement and mutable-state faults fail named behavior tests
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-REVIEW-DECISIONS

User approved this sequential predecessor on 2026-09-08 before RED. It completes
individual review decisions only; unresolved sources and native choices are
facts for the parent, never READY or authority to write. Preserve the delivered
projection API. The parent T3-REPORT-IMPORT-PLANNER retains all original acceptance
and supplies current preview, atomic bulk confirmation and deterministic receipt.
