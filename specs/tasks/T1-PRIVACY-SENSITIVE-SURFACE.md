---
id: T1-PRIVACY-SENSITIVE-SURFACE
title: SensitiveSurfacePolicy, the closed list of screens that get a secure window
depends_on: [T1-LOCAL-DATA-SECURITY]
status: todo
branch: T1-PRIVACY-SENSITIVE-SURFACE
worktree: D:\wt\MyInspection\T1-PRIVACY-SENSITIVE-SURFACE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/policy/SensitiveSurfacePolicy.kt
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/policy/SensitiveSurfacePolicyTest.kt
  - specs/tasks/T1-PRIVACY-SENSITIVE-SURFACE.md
forbid:
  - A global FLAG_SECURE, or setting window flags from the policy; it only classifies
  - Android framework types in the policy; it stays pure JVM
non_goals:
  - Applying the flag in activities or Compose screens (a later UI card)
  - The manifest gate and SelectedMediaPolicy (T1-PRIVACY-MANIFEST-MEDIA), ClipboardPolicy (T1-PRIVACY-CLIPBOARD-POLICY)
acceptance:
  - "A1 Backup password, restore preflight, tenant contact and full sensitive photo are classified secure; capture, inspection list and report action are classified ordinary."
  - "A2 The set of secure surfaces equals exactly those four, so the policy cannot express a global secure window; removing any of the four from the secure set, or adding any of the three ordinary surfaces to it, fails a test on an assertion."
  - "A3 The policy stays pure JVM and only classifies: it imports no android.* type and sets no window flag."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest --tests nz.myinspection.app.privacy.policy.SensitiveSurfacePolicyTest
dod_exit: 0
dod_assert: The three SensitiveSurfacePolicyTest cases run and pass on the candidate. On base the class does not exist, so the RED commit carries a stub that marks every surface ordinary (or every surface secure), failing the tests on assertions.
review_gate: codex {verdict:pass}
budget: 130
hygiene: R4 removes each of the four secure surfaces in turn and adds each ordinary surface to the secure set in turn; each mutant must fail the DoD.
doc_sync: At R5 set status merged and update the TASK-BOARD row.
---

# T1-PRIVACY-SENSITIVE-SURFACE

Split on 2026-11-17 from `T1-PRIVACY-MANIFEST-POLICY-GATE` (PR #465) under the user's rule that a card changes
about 200 lines or fewer. This card keeps A3 and the surface half of R2; the parent card's text stays the contract.
Plan: `docs/plans/PLAN-STALE-PRS-20261117.md`.

Starting point: PR #465's head `7456984c` holds `SensitiveSurfacePolicy.kt` (29 lines) and its test (three tests,
43 lines), green on that head. Bring them over onto current master. RED must fail on assertions, not on missing
symbols.
