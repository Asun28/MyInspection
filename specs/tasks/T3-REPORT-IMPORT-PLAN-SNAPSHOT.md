---
id: T3-REPORT-IMPORT-PLAN-SNAPSHOT
title: Immutable import plan model and selected-context preflight snapshot
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR]
parallelizable_with: []
status: merged
branch: T3-REPORT-IMPORT-PLAN-SNAPSHOT
worktree: C:\wt\T3-REPORT-IMPORT-PLAN-SNAPSHOT
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/ImportPlan.kt
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/ImportPlanningSnapshot.kt
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/plan/ImportPlanningSnapshotTest.kt
forbid:
  - Database or filesystem writes, network, fabricated native IDs, inferred property room counts, non-ROUTINE templates, automatic rating or privacy confirmation
  - Frozen schema or canonical serializer changes, and treating a caller snapshot as live database authorization
non_goals:
  - Source-row projection, candidate matching, review reducers, receipt serialization, DOCX parsing, media publication, commit, or UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 input construction defensively freezes caller room and suppression collections plus nested template collections; output context, targets, blockers and shared plan-model collections are immutable"
  - "A2 context binds the exact selected property, tenancy, ISO report date, source SHA-256, manifest normalizedDigest, template ID/content hash, configured room snapshot, suppression set and active-draft preflight result"
  - "A3 missing selected context, invalid ISO calendar date, invalid source/template digest, absent or invalid active Routine v2 binding, and an active draft are named blockers; ordinary and leap dates have explicit positive controls"
  - "A4 configured target inventory uses only validated template stable IDs and existing room_key plus instance_no identities; reject unknown suppression, missing or duplicate rooms, noncontiguous or out-of-range instances, invalid display labels and repeated singleton rooms"
  - "A5 suppression removes only selected known template items; all configured unsuppressed targets remain initially unrated, ordered by the configured room snapshot and template item order; no source suggestion or native write is produced"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.plan.ImportPlanningSnapshotTest"
dod_exit: 0
dod_assert: literal contexts and template fixtures prove immutable construction, exact binding, independently isolated invalid-input gates, configured instance targets, and all-targets-unrated initial state
review_gate: codex {verdict:pass}
hygiene: context-field substitution, missing defensive copy, invalid-date acceptance, wrong template type or version, wrong instance and suppression mutations fail relevant behaviour assertions
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-PLAN-SNAPSHOT

The shared immutable model and pure context preflight precede source projection.
Input and output retain one frozen snapshot; downstream commit must independently
revalidate live property, tenancy, current template, room configuration and draft state.
Use the existing template validator and capture room-identity semantics without DB access.

Native display-label reference: `android/core/src/main/kotlin/nz/myinspection/core/capture/RoomInstancePlanning.kt`
derives labels from the configured instance count: `if (count == 1L) roomKey else "$roomKey $instanceNo"`.
A repeatable room configured with one instance therefore uses `BEDROOM`, not `BEDROOM 1`.
Repeatability controls whether multiple instances are allowed; it does not by itself add a label suffix.
The snapshot must preserve this existing native convention, with explicit single-instance positive
and negative tests. This source citation clarifies A4; it does not change its acceptance criterion.

The same native function first filters `check_item_def` by `stable_id !in suppressedStableIds`,
then forms `activeItemRoomKeys` and retains only declared rooms in that set. Consequently A4's
required room inventory is the configured inventory for rooms with at least one unsuppressed
item. A fully suppressed room contributes neither a required room instance nor a target;
a partially suppressed room remains required. This convention already existed at the
pre-card baseline `043de67b`; requiring fully suppressed rooms would diverge from capture.

This predecessor follows the projection candidate's independent pre-review. The
980-line candidate needs input-freezing, date-validation, initial-unrated and source
coverage corrections; completing its missing behavior coverage exceeds the unchanged
1000-line budget. Preserve its worktree and RED evidence. This new card has its own
RED, DoD, review and delivery; no review-counter reset or acceptance reduction is implied.

## Delivery record — 2026-09-08

Locally merged as `8fba92e3c99a4fb0e60e1d5aed46d40640b1dd20`; reviewed feature
`bb6315a0c6e41e29e6b957f794de83f78efef67e` received formal R3 pass after one
user-authorized counter restoration. Both prior block verdicts and the native
capture evidence addressing their label/inventory assumptions remain preserved.
No review bypass or acceptance reduction occurred.

DoD: 6 tests, zero failures/errors/skips. Project verify: 983 core plus 6 E2E
tests, zero failures/errors; four existing Windows media tests skipped. Thirty
valid behavior-mutation runs cover 29 distinct faults; one compile-only attempt
is explicitly excluded. The final production bytes remain unchanged from the
initial frozen implementation; subsequent tests added missing boundary coverage.
Scope, license, secrets and size checks passed (394 changed lines, 27291 diff
characters). Evidence is retained under `_local/card-loop-01a073fa-import-planner/`.
This delivers the immutable model and selected-context snapshot only; source
projection, user review, receipt, native writes and device acceptance remain later work.
