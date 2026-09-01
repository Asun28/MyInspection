---
id: T3-REPORT-EXPORT-UI
title: Field Ledger PDF and HTML export, open, save, and share workflow
depends_on: [T3-REPORT-EXPORT-CORE, T2-CAPTURE-UI, T1-SHARE-SCREEN-PRIVACY]
parallelizable_with: []
status: todo
branch: T3-REPORT-EXPORT-UI
worktree: C:\wt\T3-REPORT-EXPORT-UI
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/MainActivity.kt
  - android/app/src/main/kotlin/nz/myinspection/app/feature/reportexport/
  - android/app/src/test/kotlin/nz/myinspection/app/feature/reportexport/
  - android/app/src/main/res/values/strings.xml
  - android/app/src/main/res/drawable/
forbid:
  - Rendering, receipt, audience, or privacy rules in UI; raw file paths; fake delivery or backup claims; broad URI grants; or an in-app report viewer
  - Forcing an HTML quality choice, deleting a ready artifact when another fails, decorative dashboard metrics, or colour-only completion state
non_goals:
  - Cloud upload, email integration, delivery tracking, report editing, DOCX import, or custom browser/PDF viewer
plan_ref: context/DESIGN.md#post-finalize-handoff
acceptance:
  - "A1 PDF is the default format with Medium quality, HTML has no quality control, and switching format preserves independent landlord and tenant ready or failed states"
  - "A2 each audience card names format, privacy scope, integrity fingerprint, and Generating, Ready, or Failed evidence with exactly one recovery action"
  - "A3 Open launches the system PDF viewer or browser; Save uses the system document picker; Share uses a temporary typed content URI and returns focus to its source action"
  - "A4 verified wording says Report ready only after core verification and never claims delivered, received, stored, uploaded, or backed up"
  - "A5 process restoration, repeated activation, provider cancellation, 48dp targets, TalkBack order, 200 percent text, dark, landscape, and reduced motion pass without losing a verified sibling artifact"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: route, reducer, system-surface, restoration, focus, semantics, and responsive-state tests pass for two audiences and both formats
review_gate: codex {verdict:pass}
hygiene: UI tests assert visible evidence and real state transitions, never framework mechanics or mock presence
doc_sync: DESIGN + UI-UX-ELEMENTS + TASK-BOARD
---

# T3-REPORT-EXPORT-UI

## Deliverable

Present dual-format export without turning the post-finalize handoff into a settings form. Format is explicit, PDF quality stays contextual, audience states remain independent, and verified artifacts survive every recoverable failure.
