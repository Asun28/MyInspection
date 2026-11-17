---
id: T1-PRIVACY-MANIFEST-MEDIA
title: Merged-manifest gate for cleartext and broad media reads, plus SelectedMediaPolicy
depends_on: [T1-LOCAL-DATA-SECURITY]
status: merged
branch: T1-PRIVACY-MANIFEST-MEDIA
worktree: D:\wt\MyInspection\T1-PRIVACY-MANIFEST-MEDIA
allow_paths:
  - android/app/build.gradle.kts
  - android/app/src/main/AndroidManifest.xml
  - android/app/src/main/kotlin/nz/myinspection/app/privacy/policy/SelectedMediaPolicy.kt
  - android/app/src/test/kotlin/nz/myinspection/app/privacy/policy/SelectedMediaPolicyTest.kt
  - specs/tasks/T1-PRIVACY-MANIFEST-MEDIA.md
forbid:
  - Runtime outbound network; changes to the frozen schema or backup format
  - Requesting READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE to import photos, or scanning the gallery around the system Photo Picker or SAF
  - FileProvider, content or file URI handling, share staging, temporary grants (T1-SHARE-SCREEN-PRIVACY)
non_goals:
  - ClipboardPolicy (T1-PRIVACY-CLIPBOARD-POLICY) and SensitiveSurfacePolicy (T1-PRIVACY-SENSITIVE-SURFACE)
  - Media permissions the parent card does not name, such as READ_MEDIA_VIDEO and MANAGE_EXTERNAL_STORAGE
  - Permission request UI, navigation, PDF or HTML rendering
acceptance:
  - "A1 The merged-manifest task in :app:check requires android:usesCleartextTraffic=false next to the existing allowBackup and backup/D2D checks, and fails when any uses-permission or uses-permission-sdk-23 element declares READ_MEDIA_IMAGES or READ_EXTERNAL_STORAGE; removing the cleartext attribute or adding either permission to the production manifest makes :app:check fail."
  - "A2 SelectedMediaPolicy accepts only a single user-selected item from the system Photo Picker or a single SAF document; every other origin, a batch or empty selection, and an item the user did not choose are rejected. JVM tests fail on an assertion (not a missing symbol) when any allowlist entry is removed."
  - "A3 The existing allowBackup and backup/D2D domain checks keep their behaviour: the build.gradle.kts change adds the cleartext entry and the permission check and changes no existing code line; and the compiled classes of the policy package, as the unit tests load them, reference no android.* type."
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:check :app:assembleDebug
dod_exit: 0
dod_assert: :app:check runs the merged-manifest gate on the real merged manifest and the SelectedMediaPolicy JVM tests; exit 0 on the candidate. On base the gate passes and the policy tests do not exist, so RED is a test commit whose policy stub fails the tests on assertions plus a manifest edit the gate rejects.
review_gate: codex {verdict:pass}
budget: 190
hygiene: R4 mutates the cleartext attribute entry, each forbidden permission, each permission tag and each SelectedMediaPolicy allowlist branch; each mutant must fail :app:check.
doc_sync: At R5 set status merged, update the TASK-BOARD row; ADR-0006 and docs/SECURITY.md get one line each naming the gate.
---

# T1-PRIVACY-MANIFEST-MEDIA

Split on 2026-11-17 from `T1-PRIVACY-MANIFEST-POLICY-GATE` (PR #465, 357 lines) under the user's rule that a card
changes about 200 lines or fewer. This card keeps R1 and the media half of R2; the parent card's text stays the
contract. Plan: `docs/plans/PLAN-STALE-PRS-20261117.md`.

## Amendment 2026-11-17 (A3)

A3 used to say the policy "imports no android.* type". Three Codex R3 rounds each found legal Kotlin that a source regex misses (extra spaces, a comment between tokens, spaces around the dot), and only an unused import can escape the bytecode check. By the user's ruling A3 now names the property that matters at runtime: the compiled classes reference no android.* type. The source-import test is dropped.

Starting point: PR #465's head `7456984c` already holds the gate (17 lines in `build.gradle.kts`, one manifest
attribute) and `SelectedMediaPolicy` with five tests (122 lines), green on that head. Bring them over onto current
master; do not copy the other two policies. #465's first formal review blocked on its RED receipt failing on
missing symbols, so this card's RED must fail on assertions: commit a stub `SelectedMediaPolicy` that rejects
everything (accept tests fail) or accepts everything (reject tests fail) before the real one.

## Attempt evidence

Attempt 5, 2026-11-17, code tree `832bcb03` (merged with master `ca813eb2`, which carries T1-PRIVACY-SENSITIVE-SURFACE). DoD: exit 0; 311 JVM tests, 0 failures, 1 skipped.

| Item | Proof | Result |
|---|---|---|
| A1 | RED `187aae04`: DoD exit 1, `:app:verifyDebugBackupManifest` fails on the missing `android:usesCleartextTraffic=false`. R4 M1-M5, each against `:app:verifyDebugBackupManifest`, killed with the attribute or permission named in the failure: drop the attribute; set it to `true`; add `READ_MEDIA_IMAGES` as `uses-permission`; add `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"`; add `READ_MEDIA_IMAGES` as `uses-permission-sdk-23`. | pass |
| A2 | `SelectedMediaPolicyTest`, 5 tests. On `187aae04` (reject-all stub) 4 fail on `AssertionError`; the fifth, every other origin rejected, is killed by M8. R4 M6-M11 against `:app:testDebugUnitTest --tests ...SelectedMediaPolicyTest`, each killed by an `AssertionError` with no compile error: drop either allowed origin; allow `SAF_OPEN_DOCUMENT_TREE`; `itemCount < 1`; `itemCount > 1`; drop the `chosenByUser` check. | pass |
| A3 | `git diff 71ebe8b9 HEAD -- android/app/build.gradle.kts`: 19 insertions, 2 deletions. The 2 deleted lines are the old comment that listed three asserted attributes, rewritten to name four (R3 round 1); no existing code line is deleted. Test `no class in the policy package references an android type` reads every class of the package (from the unit-test classes directory or `classes.jar`) and fails on any `android/` reference, private members and method bodies included; adding a private function with an `android.content.ClipData` parameter to `SelectedMediaPolicy` fails that test alone on an `AssertionError` (added after review). | pass |

The R4 runner first rewrote both mutated files unchanged (same SHA-256) and passed both tasks unmutated; every mutated file was restored and its SHA-256 rechecked.

## R5 2026-11-17

Merged via PR #489 (squash `c0db11f9`, reviewed head `add13697`, CI run 44023021590 attempt 1, Codex R3 pass). R3 round 1 blocked on a stale gate comment (fixed); A3 was amended in PR #496 by the user's ruling. The CI gate stopped twice on [CI-GATE-BASE-MOVED] when the sibling cards merged first; the branch took master and the ship reran its gates. aidlc stopped this card at its 3-hour card deadline, and by the user's ruling it shipped through task.ps1 with every gate.
