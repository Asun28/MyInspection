---
id: T3-REPORT-IMPORT-PLAN-SNAPSHOT
title: Immutable import plan model and selected-context preflight snapshot
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR]
parallelizable_with: []
status: todo
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

The immutable model and context preflight precede projection. Retain one frozen snapshot;
commit independently revalidates live property, tenancy, template, rooms and draft state.
Reuse template validation and capture room identities without DB access.

Native reference: `android/core/src/main/kotlin/nz/myinspection/core/capture/RoomInstancePlanning.kt`.
A4 preserves its label rule: `if (count == 1L) roomKey else "$roomKey $instanceNo"`.
One repeatable instance uses `BEDROOM`, not `BEDROOM 1`; repeatability permits multiple instances,
not a suffix. Include explicit single-instance positive and negative tests.

That function filters `check_item_def` by `stable_id !in suppressedStableIds` before forming
`activeItemRoomKeys`, retaining only declared rooms in that set. A4 therefore requires configured
instances only for rooms with unsuppressed items: fully suppressed rooms need no instance or
target; partially suppressed rooms remain required.
