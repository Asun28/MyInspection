---
id: T1-APP-STORAGE-POLICY-TESTS
title: Pin AppStoragePolicy port arguments, environment conversion and catch breadth with tests
status: merged
depends_on: [T1-APP-STORAGE-POLICY-REMOTE]
parallelizable_with: []
allow_paths:
  - specs/tasks/T1-APP-STORAGE-POLICY-TESTS.md
  - android/app/src/test/kotlin/nz/myinspection/app/platform/AppStoragePolicyTest.kt
forbid:
  - Any change to production sources, including AppStoragePolicy.kt and StoragePathBoundary.kt
  - Removing an existing test case or weakening an existing assertion
non_goals:
  - Android storage adapter, raw volume-state mapping and device self-checks (T1-APP-STORAGE-ANDROID)
  - LocalSecretBox and production assembly (T1-LOCAL-DATA-SECURITY, T1-APP-BOUNDARY-ASSEMBLY)
plan_ref: docs/adr/0006-offline-security-backup-hardening.md
acceptance:
  - "A1 the fake environment checks the directory passed to usableBytes, as it already does for the state and writable probes, so a policy that measures space on any other directory fails a named test"
  - "A2 in the conversion test the device-protected environment's own no-backup directory, app root and DP root each make StoragePathBoundary refuse the credential root, so reading any one of them instead of the converted environment's value fails a named test"
  - "A3 the credential failure test also injects an IOException from the no-backup getter and a SecurityException from the conversion; each still yields the fixed message with no cause and no sensitive text"
  - "A4 the media probe failure test fails each of the four media reads (directory getter, state, writable, space) with IllegalStateException, IOException and SecurityException; each closes to Unavailable without sensitive text, and the test name says it covers every probe"
  - "A5 a new test refuses, with the fixed message and no cause, a no-backup root that lies outside the app root but inside its parent (an external sibling) and a no-backup root equal to the app root, so widening the app root passed to StoragePathBoundary.create fails a named test"
  - "A6 the eight named compiling mutants that survive the current test file (app root widened to its parent; space measured on another directory; app root or DP root read from the unconverted environment; the constructor catch and the media catch each narrowed to RuntimeException and to IllegalStateException) each fail a named test with AssertionError after this change"
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: app JVM tests and debug assembly pass with the hardened AppStoragePolicyTest; production bytes are unchanged
review_gate: codex {verdict:pass}
hygiene: a fresh named 44-mutant set over the unchanged production file (routes, conversion, DP rejection, create/resolveChild wiring, media guards, space boundary, texts, both catches and the A6 classes) is run on the final test bytes; the receipt maps each mutant to the test that kills it and pins both files
doc_sync: TASK-BOARD
---

# T1-APP-STORAGE-POLICY-TESTS

## Why

`T1-APP-STORAGE-POLICY-REMOTE` merged by PR #316 with a Codex R3 pass. A twin implementation of the same card on the
local master line then went through three fresh Opus 5.5 R3 rounds (2026-09-24), and the first two rounds found test
gaps that apply to the origin test file as well:

- the fake `usableBytes` ignores its directory argument, unlike the state and writable fakes;
- the conversion test's device-protected environment has roots that would also accept the credential root, so reading
  the unconverted environment's app root or DP root goes unnoticed;
- every ordinary failure reaching the policy's own two catch sites is an `IllegalStateException` (the boundary swallows
  its own path exceptions), so narrowing either `catch (_: Exception)` is not detected, and a port `IOException` or
  `SecurityException` carrying a tenant path would then escape;
- the test named for the media state probe injects a failure only into the space probe;
- found by the RED batch below rather than by those rounds: no test uses a no-backup root outside the app root but
  inside its parent, so widening the app root handed to `StoragePathBoundary.create` goes unnoticed.

Production behaviour is already correct for all of these; this card only makes the tests able to see a regression.

## RED

No production defect exists, so the DoD cannot go red before the change. The RED evidence is a mutation batch on the
current test file (origin `1583b4d7`, test SHA-256 `659ECCD7...`, production `C031E652...`): 36 of 44 named mutants die
and the eight A6 mutants survive. After this change each of the eight fails a named test with `AssertionError`. The ship
records the non-TDD route with `-SkipRed`.

## Budget

Test-only: about 55–75 changed test lines plus a replaced R4 receipt of about 35 lines and this card, 140–190 lines /
9k–13k characters.

## Review

While the Codex quota is exhausted, R3 is a fresh Opus 5.5 instance run by `review.ps1` through `ReviewCommand` (user
ruling 2026-09-23/24). If R3 blocks more than three times, the card is split instead of repaired further.

## Delivery (R5, 2026-09-24)

Merged by [PR #332](https://github.com/Asun28/MyInspection/pull/332): reviewed head `a2d788e030b54704500f4139e37d0b224cc3426d`, CI run `35932836390` (verify and required succeeded), merge `d2e98e9c29ed2d14a0dab1482e304b0c80be954b`. R3 was a fresh Opus 5.5 instance through `ReviewCommand` while the Codex quota is exhausted: pass on the first round, after the ship's own Codex leg failed closed on the usage limit (probe-confirmed, round reset). R4: the previous test file killed 36 of 44 named mutants; this one kills 44 of 44, including the eight A6 survivors. Production bytes unchanged.
