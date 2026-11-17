---
id: T1-PRIVACY-CLIPBOARD-POLICY
title: ClipboardPolicy, a write-only clipboard command for the explicit Copy notice action
depends_on: [T1-LOCAL-DATA-SECURITY]
status: todo
branch: T1-PRIVACY-CLIPBOARD-POLICY
worktree: D:\wt\MyInspection\T1-PRIVACY-CLIPBOARD-POLICY
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/policy/ClipboardPolicy.kt
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/policy/ClipboardPolicyTest.kt
  - specs/tasks/T1-PRIVACY-CLIPBOARD-POLICY.md
forbid:
  - Any clipboard read API, or reading the clipboard in the background or at startup
  - Android framework types in the policy; it stays pure JVM
  - Runtime outbound network
non_goals:
  - Wiring the policy into the notice screen (a later UI card)
  - The manifest gate and SelectedMediaPolicy (T1-PRIVACY-MANIFEST-MEDIA), SensitiveSurfacePolicy (T1-PRIVACY-SENSITIVE-SURFACE)
acceptance:
  - "A1 An explicit Copy notice action yields a write command for the notice body, marked sensitive; a write without that action, or of anything other than the notice body, is denied."
  - "A2 Every clipboard read is denied whatever triggers it; reflection shows the policy's only public method is authorize and the write command's only public methods are getText, getMarkSensitive and toString; the write command's toString does not print the notice body."
  - "A3 The policy stays pure JVM and writes nothing itself: it imports no android.* type and only returns a command for a later UI card to apply."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest --tests nz.myinspection.app.privacy.policy.ClipboardPolicyTest
dod_exit: 0
dod_assert: The six ClipboardPolicyTest cases run and pass on the candidate. On base the class does not exist, so the RED commit carries a stub policy that allows every request unmarked and exposes a read method, failing the tests on assertions.
review_gate: codex {verdict:pass}
budget: 190
hygiene: R4 mutates the action check, the body check, the sensitive flag, the read denial and the toString redaction; each mutant must fail the DoD.
doc_sync: At R5 set status merged and update the TASK-BOARD row.
---

# T1-PRIVACY-CLIPBOARD-POLICY

Split on 2026-11-17 from `T1-PRIVACY-MANIFEST-POLICY-GATE` (PR #465) under the user's rule that a card changes
about 200 lines or fewer. This card keeps the clipboard half of R2; the parent card's text stays the contract.
Plan: `docs/plans/PLAN-STALE-PRS-20261117.md`.

Starting point: PR #465's head `7456984c` holds `ClipboardPolicy.kt` (59 lines) and `ClipboardPolicyTest.kt`
(six tests, 86 lines), green on that head. Bring them over onto current master. RED must fail on assertions, not
on missing symbols (the reason #465's first review blocked).

## Attempt evidence

Attempt 1, 2026-11-17, code tree `44279439` on base `21f33cb9`. DoD: exit 0; ClipboardPolicyTest 7 tests, 0 failures.

| Item | Proof | Result |
|---|---|---|
| A1 | RED `8a90de7c` (stub allows every request, unmarked): DoD exit 1, all 6 tests fail on `AssertionError`. R4 C1 (drop the explicit-action check), C2 (drop the notice-body check), C3 (`markSensitive = false`) and C7 (wrong denial reason) are each killed by an `AssertionError`. | pass |
| A2 | Tests `every clipboard read is denied whatever triggers it`, `the policy and its write command expose no way to read clipboard content` (reflection) and `the write command does not print the notice body`. R4 C4 (allow reads), C6 (add a public `readPrimaryClip`) and C5 (`toString` prints the body) are each killed by an `AssertionError`. | pass |
| A3 | Test `no class in the policy package references an android type` reads every class of the package (from the unit-test classes directory or `classes.jar`) and fails on any `android/` reference, private members and method bodies included; adding a private function with an `android.content.ClipData` parameter to `ClipboardPolicy` fails that test alone on an `AssertionError` (added after review). The policy returns a `ClipboardWriteCommand` and calls no clipboard API. | pass |

R4 ran each mutant against `:app:testDebugUnitTest --tests ...ClipboardPolicyTest`; a kill needed an `AssertionError` and no compile error. The runner first rewrote the file unchanged (same SHA-256) and passed unmutated, and restored and rechecked the file after each mutant: 7 of 7 killed.
