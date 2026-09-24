---
id: T1-LOCAL-SECRET-STORE
title: LocalSecretEnvelopeStore - envelope files under the checked no-backup secret-envelope directory, atomic replace and path-free failures
depends_on: [T1-LOCAL-SECRET-BOX, T1-APP-STORAGE-POLICY]
status: todo
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
budget: 300
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
- One writer at a time is assumed: the temporary file name is unpredictable, and two concurrent replaces of the same purpose leave whichever move ran last.

## Budget

About 180 changed lines with the receipt; `budget: 300`.
