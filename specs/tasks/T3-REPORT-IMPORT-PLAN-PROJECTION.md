---
id: T3-REPORT-IMPORT-PLAN-PROJECTION
title: Exhaustive source inventory and conservative Routine import candidates
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR, T3-REPORT-IMPORT-PLAN-SNAPSHOT]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-PLAN-PROJECTION
worktree: C:\wt\T3-REPORT-IMPORT-PLAN-PROJECTION
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/plan/
forbid:
  - Invented native stable IDs or room counts, automatic confirmation, inferred legacy status conversion, silent source loss
  - Database or filesystem writes, network, non-ROUTINE imports, edits to frozen schema or canonical serializer
non_goals:
  - Review reducers, ready authorization, mapping receipt, DOCX parsing, database commit, media publication, or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 immutable input binds selected property, tenancy, report date, current active Routine v2 template and configured room instances; missing context or active draft blocks planning"
  - "A2 each manifest entry has exactly one review owner; exact overlapping item/fragment evidence aliases without losing comments, repeated text, caption residuals, summaries, placements, images, or warnings"
  - "A3 candidate targets come only from the selected template and configured room instances; exact unique name/room matches remain suggestions, ambiguous or unknown targets block, and no IDs or room counts are invented"
  - "A4 only exact allowed status values are suggested; unsupported and blank legacy statuses retain source evidence and block until explicit selection; missing template-instance items remain unrated"
  - "A5 every substantive photo begins UNREVIEWED_EXCLUDED/ACTION_REQUIRED; repeated placements remain covered, missing images and ambiguous captions are visible obligations"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.plan.*"
dod_exit: 0
dod_assert: literal manifests and templates prove immutable context, complete single-owner source coverage, conservative suggestions, instance identity, and initial blockers
review_gate: codex {verdict:pass}
hygiene: source-drop, duplicate-alias, wrong-status, invented-target, wrong-room-instance and reviewed-photo-default mutations each fail behavior assertions
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-PLAN-PROJECTION

This card is the source-projection predecessor of T3-REPORT-IMPORT-PLANNER.

This predecessor supplies the pure immutable projection used by
T3-REPORT-IMPORT-PLANNER. It never claims ready or authorizes a native write.
Consume the independently delivered SNAPSHOT model and preflight; retain integrated
context checks. Every target remains unrated until the later explicit review phase.
Shared matching and source-ownership rules are defined in ADR-0007, Pure import planning contract.
The calling workflow obtains current template and property room configuration;
this pure snapshot does not prove live DB state and commit must revalidate it.

User approved conservative legacy status handling on 2026-09-07. Original values
remain review evidence; no Excellent/Average/Clean conversion is inferred.
