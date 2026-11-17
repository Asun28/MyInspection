---
id: T1-PRIVACY-SENSITIVE-SURFACE
title: SensitiveSurfacePolicy, the closed list of screens that get a secure window
depends_on: [T1-LOCAL-DATA-SECURITY]
status: merged
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

## Attempt evidence

Attempt 1, 2026-11-17, code tree `451966cc` on base `21f33cb9`. DoD: exit 0; SensitiveSurfacePolicyTest 5 tests, 0 failures.

| Item | Proof | Result |
|---|---|---|
| A1 | Tests `backup password, restore preflight, tenant contact and full sensitive photo use a secure window` and `capture, inspection list and report action carry no secure window`. RED `3591596d` (stub marks every surface ordinary): DoD exit 1; the secure-surface test and the exact-set test fail on `AssertionError`. | pass |
| A2 | Test `only the four listed surfaces are secure, so no surface applies a global secure window` compares the whole secure set. R4 S1-S4 (drop each of the four from the secure set) and S5-S7 (add each ordinary surface to it) are each killed by an `AssertionError` with no compile error: 7 of 7. | pass |
| A3 | Test `no class in the policy package references an android type` reads every class of the package (from the unit-test classes directory or `classes.jar`) and fails on any `android/` reference, private members and method bodies included; adding a private function with an `android.content.ClipData` parameter to `SensitiveSurfacePolicy` fails that test alone on an `AssertionError` (added after review). Test `the policy source imports no android type` fails on any `import android.` line; adding `import android.content.ClipData` to `SensitiveSurfacePolicy.kt` fails that test alone (R3 finding). It only returns a `SurfaceProtection` value and sets no window flag. | pass |

The R4 runner first rewrote the file unchanged (same SHA-256) and passed unmutated, and restored and rechecked the file after each mutant.

## R5 2026-11-17

Merged via PR #494 (squash `13748ced`, reviewed head `26adfb3b`, CI run 44017539704 attempt 1, Codex R3 pass with zero findings). aidlc stopped this card at its 3-hour card deadline while it waited for the local Gradle lane shared with goal g-20261014091214-314b46; by the user's ruling it shipped through task.ps1 with every gate.
