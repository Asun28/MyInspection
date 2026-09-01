---
id: T3-REPORT-IMPORT-PLANNER
title: Deterministic review plan from legacy extraction to current Routine template
depends_on: [T2-ROUTINE-CONTEXT-V2, T3-DOCX-REPORT-EXTRACTOR]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-PLANNER
worktree: C:\wt\T3-REPORT-IMPORT-PLANNER
allow_paths:
  - android/core/src/main/kotlin/nz/myinspection/core/report/importing/plan/
  - android/core/src/test/kotlin/nz/myinspection/core/report/importing/plan/
forbid:
  - Fabricated stable IDs, auto-confirmed statuses, silent row or photo drops, writes, network, source paths, or vendor metadata
  - INGOING, EXIT, ANNUAL, baseline mutation, auto-finalize, or bypass of current-template completeness
non_goals:
  - DOCX byte parsing, database commit, media publication, navigation, or Compose UI
plan_ref: docs/adr/0007-report-interchange.md
acceptance:
  - "A1 planning requires one selected property, report date, tenancy context, current Routine template, and no existing active draft"
  - "A2 every extracted row, comment, summary, caption, and substantive photo is mapped, explicitly excluded with a reason, or remains a named blocker"
  - "A3 exact allowed matches are non-terminal suggestions until individually confirmed or explicitly bulk-confirmed from a complete preview; blank/unknown status blocks and missing current-template items remain unrated"
  - "A4 every photo begins transient UNREVIEWED_EXCLUDED and remains a blocker until privacy review; ambiguous caption associations cannot be bulk-confirmed"
  - "A5 canonical mapping receipt JSON and hash are deterministic and contain IDs, decisions, exclusions, source digest, and warnings but no raw path, URL, author, or source bytes"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q --rerun-tasks --no-build-cache :core:test --tests "nz.myinspection.core.report.importing.plan.*"
dod_exit: 0
dod_assert: literal templates and extraction manifests prove exhaustive mapping, blockers, privacy defaults, Routine-only scope, and deterministic receipt bytes
review_gate: codex {verdict:pass}
hygiene: wrong status, missing decision, invented ID, and silent exclusion mutations each fail a dedicated behaviour test
doc_sync: ADR-0007 + TASK-BOARD
---

# T3-REPORT-IMPORT-PLANNER

## Deliverable

Provide the pure review model used by the Field Ledger import workflow. It translates extraction evidence into explicit native choices but has no authority to write them.
