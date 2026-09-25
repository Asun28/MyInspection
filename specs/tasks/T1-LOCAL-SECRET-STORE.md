---
id: T1-LOCAL-SECRET-STORE
title: LocalSecretEnvelopeStore - envelope files under the checked no-backup secret-envelope directory, atomic replace and path-free failures
depends_on: [T1-LOCAL-SECRET-BOX, T1-APP-STORAGE-POLICY]
status: merged
branch: T1-LOCAL-SECRET-STORE
worktree: C:\wt\T1-LOCAL-SECRET-STORE
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/LocalSecretEnvelopeStore.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/LocalSecretEnvelopeStoreTest.kt
forbid:
  - Changing LocalSecretBox, its envelope format, its ports or its mapping (T1-LOCAL-SECRET-BOX), AppStoragePolicy, StoragePathBoundary, the frozen SQLDelight schema or the backup format
  - New Gradle or runtime dependencies, Robolectric or other Android JVM doubles, source-text assertions standing in for behavior
  - Any android.* import; paths, file names or exception text in a failure the store raises
non_goals:
  - The Android Keystore adapter, the unlock port and the device probe (T1-LOCAL-DATA-SECURITY); production assembly (T1-APP-BOUNDARY-ASSEMBLY)
  - Removing a temporary file that a killed process left between writing it and the move (it holds ciphertext only, at most one envelope in size); fsync of the directory after the move
  - Routing EnvelopeStoreException.stage and cleanupFailed into SafeLog: that needs a SafeLog operation outside allow_paths, and belongs to the first caller that logs store failures (T5-BACKUP-IO) [FOLLOW-UP]
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: The app JVM suite and assembleDebug pass, and build/test-results for testDebugUnitTest contains LocalSecretEnvelopeStoreTest with every A1-A3 test present and passing. On the base the test class does not compile (the store does not exist), so the command exits non-zero. LocalSecretBoxTest and the existing platform tests stay green.
requirements:
  - "R1 The store shall resolve the secret-envelope directory through AppStoragePolicy.location(SECRET_ENVELOPE) on every call and replace an envelope only by an atomic move of a synced temporary file created in that directory."
  - "R2 When a store operation fails, the store shall raise one fixed message without a cause, so no path or file name leaves it."
acceptance:
  - "A1 [R1] Over a real AppStoragePolicy whose fake environment roots sit in the StoragePathFixture temp tree, replace writes `<purpose label>.envelope` under noBackup/secret-envelope, and after several replaces that directory holds only envelope files. A hard link taken to the previous envelope still holds the old bytes after the next replace, so the new bytes arrive by a move and not by rewriting the old file. Read returns what replace wrote, null for an absent envelope, and at most MAX_ENVELOPE_BYTES + 1 bytes of a larger file."
  - "A2 [R1] After the secret-envelope directory is replaced by an alias (the StoragePathFixture junction) that leaves the root, the next read and the next replace fail, and nothing is written through the alias."
  - "A3 [R2] A failed move (the target name is a non-empty directory) fails with an IOException whose message is exactly `secret envelope store failed` and whose cause is null, deletes its temporary file and leaves that directory intact; the A2 failures carry the same message and no cause."
review_gate: codex {verdict:pass}
budget: 400
hygiene: R4 single-point mutants, each killed by a named test and recorded in a trailing R4 receipt comment in LocalSecretEnvelopeStoreTest.kt with the production-file SHA-256 - location cached at construction, read cap enlarged, absent read raising, envelope file name changed, temporary-file cleanup removed, direct in-place write instead of temp file and move, redaction removed, original exception kept as the cause. Compile-only kills do not count.
doc_sync: TASK-BOARD row (R5)
---

# T1-LOCAL-SECRET-STORE

## Why this card exists

User ruling 2026-09-25: the fresh-context pre-review of `T1-LOCAL-SECRET-BOX` added seven test gaps, which put that card at about 836 changed lines against `budget: 800`. The production envelope-file port moves here so the box ships at about 670 lines with room for R3 fixes. The code is written and passing in the box worktree; it moves over unchanged apart from what this card's R3 asks for.

## Shape

- `LocalSecretEnvelopeStore(policy: AppStoragePolicy) : SecretEnvelopeFiles` (the port and `MAX_ENVELOPE_BYTES` come from T1-LOCAL-SECRET-BOX).
- Read uses a bounded loop instead of `InputStream.readNBytes`, which Android adds only at API 33 (minSdk is 26).
- Every public call wraps ordinary exceptions into `IOException("secret envelope store failed")` without a cause. `LocalSecretBox` already discards store exceptions; this keeps the store safe for any other caller.
- Replace goes through an internal `EnvelopeFileOperations` seam (sync, move, delete), so tests can record that the temporary file is synced after its bytes are written and then moved with `ATOMIC_MOVE`, and can inject failures; the platform object is tested directly.
- Failures are an internal `EnvelopeStoreException`: the message is exactly `secret envelope store failed`, with no cause and no suppressed exceptions, a closed `stage` (LOCATE, READ, WRITE, MOVE) and `cleanupFailed` when a failed replace could not delete its temporary file.
- One writer at a time is assumed: the temporary file name is unpredictable, and two concurrent replaces of the same purpose leave whichever move ran last.

## Budget

About 180 changed lines with the receipt; `budget: 300`.

2026-09-25 user ruling: raised to `budget: 400`. Codex R3 round 1 blocked on one spec finding (the sync and the atomic move were not shown by any test) and two standards findings (an unchecked temporary-file delete; failures without a closed, path-free stage code). The fixes and the tests a fresh-context review asked for measure about 355 lines.

## R5 delivery (2026-09-25)

Merged by [PR #377](https://github.com/Asun28/MyInspection/pull/377) as squash `412f9fb4` (reviewed head `edeef67f`; CI run `36094068454`, `verify` and `required` success; `codex-review` success). The merged `android/` files are byte-identical to the reviewed head. 370 changed lines against `budget: 400`.

- RED: on base `123aab0c` the test class did not compile (`Unresolved reference 'LocalSecretEnvelopeStore'`).
- Tests: 7 in `LocalSecretEnvelopeStoreTest`; app suite 295 tests, 0 failures.
- R4: 23/23 single-point mutants killed by their named test with `java.lang.AssertionError`, against production SHA-256 `0E7E827E…`. The receipt is the trailing comment in the test file; the evidence is in `_local/local-secret-store/r4` in the main checkout. Three earlier kills (absent read, file name, missing directory) came from an exception thrown by the mutated code rather than an assertion; the tests now assert success explicitly, and the batch was rerun.
- R3: Codex `gpt-5.6-sol` high. Round 1 on `cfa358c8` blocked on one spec finding (no test showed the temporary file's sync or the atomic move) and two standards findings (an unchecked temporary-file delete; failures without a closed, path-free stage). A fresh-context review of the fix then found adapter-level mutants that the new seam hid, a suppressed-exception path and stage labels no test could tell apart. The fix: an internal `EnvelopeFileOperations` seam with the platform object tested directly, `Files.deleteIfExists` with a reported `cleanupFailed`, and an internal `EnvelopeStoreException` with a closed stage (LOCATE, READ, WRITE, MOVE). The user raised the budget from 300 to 400 (#382). Round 2 passed with no findings.
- Follow-up recorded on the card: routing the stage into SafeLog belongs to the first caller that logs store failures (T5-BACKUP-IO), because SafeLog is outside this card.
- Selftest: tier-1 routing escalated to the full 17 gates and passed in 2771 s on `cfa358c8`; only `android/` files changed after that.
- Author: the card names GPT-5.6 Terra; the work was done by a Claude Opus 5.5 session.