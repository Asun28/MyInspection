---
id: T3-REPORT-IMPORT-UI
title: Field Ledger DOCX import flow that creates a reviewed editable draft
depends_on: [T3-REPORT-IMPORT-COMMIT, T2-CAPTURE-UI]
parallelizable_with: []
status: todo
branch: T3-REPORT-IMPORT-UI
worktree: C:\wt\T3-REPORT-IMPORT-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/reportimport/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/reportimport/
  - android/app/src/main/res/values/strings.xml
  - android/app/src/main/res/drawable/
forbid:
  - Parser, mapping, or persistence rules in Composable or ViewModel; raw paths or vendor metadata in UI; silent defaults; auto-finalize; or non-Routine import
  - Decorative redesign outside Field Ledger tokens, gesture-only controls, colour-only state, or an in-app DOCX/report viewer
non_goals:
  - OCR, PDF import, template editing, import history viewer, export rendering, or automatic issue resolution
plan_ref: context/DESIGN.md#primary-inspection-journey
acceptance:
  - "A1 a selected property with no active draft exposes Import existing report and the declared Details, Choose file, Scan, Match, Review, Create draft task route; Details confirms tenancy/report date and shows the locked current Routine template"
  - "A2 persistent step progress shows exact rows, photos, mappings, exclusions, and blockers; each error is adjacent, announced, and first-blocker focus is deterministic"
  - "A3 MATCHED and ACTION_REQUIRED rows remain blockers; status suggestions require confirmation, photo-caption ambiguity requires assignment/exclusion, and every photo starts visibly transient UNREVIEWED_EXCLUDED rather than with a persisted privacy value"
  - "A4 Create editable draft is the sole primary action only when the complete manifest is terminal CONFIRMED/EXCLUDED, preview is current, and blockers are zero; success replaces import with ordinary Capture/Review and never says finalized"
  - "A5 cancel/picker/space failures and live recreation preserve the last safe in-process state; pre-commit process death clears source/staging/mapping and resets to Choose file with Details retained, while post-commit recovery enters the verified draft; 48dp, TalkBack, 200 percent text, dark, landscape, and reduced motion pass"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: route, state-machine, restoration, focus, semantics, responsive-state previews, and core-boundary tests pass; world-class UX checklist is attached to review evidence
review_gate: codex {verdict:pass}
hygiene: UI tests assert user-observable state and real reducer outputs, never mock existence or source text
doc_sync: DESIGN + UI-UX-ELEMENTS + TASK-BOARD
---

# T3-REPORT-IMPORT-UI

## Deliverable

Add a property-scoped, non-technical import experience in the existing Field Ledger language. It makes ambiguity visible and recoverable, then hands the user to the normal editable inspection review instead of inventing a separate legacy editor.
