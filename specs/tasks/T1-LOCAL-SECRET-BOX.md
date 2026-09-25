---
id: T1-LOCAL-SECRET-BOX
title: LocalSecretBox JVM core - AES-GCM envelope, alias/version/purpose isolation and NEEDS_UNLOCK/NEEDS_PASSPHRASE mapping over key, unlock and envelope-file ports
depends_on: [T1-APP-STORAGE-POLICY, T1-APP-STORAGE-ANDROID]
status: merged
branch: T1-LOCAL-SECRET-BOX
worktree: C:\wt\T1-LOCAL-SECRET-BOX
allow_paths:
  - android/app/src/main/kotlin/nz/myinspection/app/platform/LocalSecretBox.kt
  - android/app/src/test/kotlin/nz/myinspection/app/platform/LocalSecretBoxTest.kt
forbid:
  - Android Keystore, KeyguardManager or UserManager code and any android.* import in the new files (the Android adapters and the device probe belong to T1-LOCAL-DATA-SECURITY)
  - New Gradle or runtime dependencies, Robolectric or other Android JVM doubles, source-text assertions standing in for behavior
  - Changing AppStoragePolicy, StoragePathBoundary, SafeLog, AndroidAppStorageEnvironment, the frozen SQLDelight schema or the backup format
  - Persisting or logging plaintext, keys, paths or exception text; a plaintext fallback on any failure; deleting or rewriting an existing envelope on a failed seal or on any open
non_goals:
  - AndroidKeyStore key generation, the unlock-state adapter, key non-exportability evidence and the device probe (T1-LOCAL-DATA-SECURITY)
  - Backup states, VerifiedBackupReceipt handling, passphrase UX and scheduling (T5-BACKUP-IO); production assembly (T1-APP-BOUNDARY-ASSEMBLY)
  - SafeLog events for box failures; key rotation or re-sealing beyond the readable-version set
dod_command: cmd /c android\gradlew.bat -p android --offline --no-daemon -q :app:testDebugUnitTest :app:assembleDebug
dod_exit: 0
dod_assert: The app JVM suite and assembleDebug pass, and build/test-results for testDebugUnitTest contains LocalSecretBoxTest with every A1-A5 test present and passing. On the base the test class does not compile (the production types do not exist), so the command exits non-zero. The existing SafeLog, AppStoragePolicy, AndroidAppStorageEnvironment and StoragePathBoundary tests stay green.
requirements:
  - "R1 When a secret is sealed, the box shall persist only a version byte, a 96-bit nonce produced by the cipher provider, and the ciphertext with its 128-bit tag, and a fresh nonce shall be used for every seal."
  - "R2 When an envelope is opened, the box shall return the secret only after GCM authentication of that envelope under its own alias and associated data, and shall never return plaintext for a modified, foreign or malformed envelope."
  - "R3 The box shall isolate secrets by Keystore alias, key version and purpose: the alias names purpose and version, and the associated data binds a fixed domain string, the version and the purpose."
  - "R4 When the device is not unlocked, the box shall return the retryable NEEDS_UNLOCK; when the envelope or key is missing, unreadable or unusable, the version is unsupported, or authentication fails, it shall return NEEDS_PASSPHRASE with a closed reason; in every failure the stored envelope stays byte-for-byte unchanged."
  - "R5 The box shall zero-fill the plaintext byte buffers it creates and give the caller a secret holder that zero-fills its characters on close."
acceptance:
  - "A1 [R1] For each purpose, a sealed envelope file is exactly 29 + (UTF-8 length) bytes: byte 0 equals the seal version, bytes 1-12 are the provider's nonce, the rest is ciphertext plus a 16-byte tag, the envelope contains no run of the plaintext's UTF-8 bytes, and the seal writes exactly one envelope, under its own purpose, through the file port. Two seals of the same purpose and plaintext under the same key give different nonces and different ciphertexts. The box never passes an IV for encryption (Android Keystore refuses a caller IV under randomized encryption) and returns UNAVAILABLE when the provider's nonce is not 12 bytes; a GCM-shaped test cipher that records init parameters and can report a 16-byte nonce shows both."
  - "A2 [R2] Flipping one bit in the nonce, in the ciphertext or in the tag returns NeedsPassphrase(AUTHENTICATION_FAILED). Setting the version byte to a version outside the readable set returns NeedsPassphrase(VERSION_UNSUPPORTED) without a key-port call; setting it to another readable version returns AUTHENTICATION_FAILED. An envelope shorter than 29 bytes or longer than the cap returns ENVELOPE_CORRUPT, and a file holding the raw plaintext never opens. No failing open returns Opened, and the file bytes are unchanged after every case."
  - "A3 [R3] A recording key port shows seal and open ask exactly `myinspection.secret.<purpose label>.v<version>` for every purpose and readable version, and open asks for the version in the envelope, not the seal version. On a single-key port (every alias returns the same key) an envelope sealed for one purpose and placed under the other purpose fails with AUTHENTICATION_FAILED, and a v1 envelope relabelled v2 (both readable) fails with AUTHENTICATION_FAILED. Dropping purpose or version from the alias, dropping purpose or version from the associated data, or dropping the associated data makes a named test fail."
  - "A4 [R4] Open: unlock port reports locked (or throws) -> NeedsUnlock with no key-port or file call; no file -> ENVELOPE_MISSING; read throws -> NeedsUnlock if the port now reports locked, else ENVELOPE_UNREADABLE; key port returns null -> KEY_MISSING; key port or cipher init throws -> NeedsUnlock if the port now reports locked, else KEY_UNUSABLE; tag failure -> AUTHENTICATION_FAILED. Seal: locked -> NEEDS_UNLOCK with no key-port or file call; key, cipher or write failure -> NEEDS_UNLOCK if the port now reports locked, else UNAVAILABLE, with the previous envelope bytes unchanged. Only Exception is caught; an injected Error propagates with its identity. Every row has its own named test, so swapping or deleting one mapping fails a test."
  - "A5 [R5] A helper-level test captures the UTF-8 bytes built for seal and the decrypted bytes handed to decoding and finds them all zero after the helper returns, on the success path and on a throwing path. SecretChars.close() zero-fills its chars. toString of Opened and SecretChars shows no secret. An empty secret, a secret over the length cap and malformed UTF-16 (a lone surrogate) are rejected with a fixed message that contains none of the input."
review_gate: codex {verdict:pass}
budget: 800
hygiene: R4 single-point mutants, each killed by a named test and recorded in a trailing R4 receipt comment in LocalSecretBoxTest.kt (the AppStoragePolicyTest precedent) with the production-file SHA-256 values it ran against - caller-supplied fixed nonce, nonce-size check removed, AAD dropped, purpose or version dropped from AAD, purpose or version dropped from the alias, readable-version check removed, length checks removed, each A4 mapping swapped, seal pre-check removed, write failure reported as STORED, each zero-fill removed, SecretChars.close made a no-op, open using the seal version instead of the envelope version. Compile-only kills do not count.
doc_sync: TASK-BOARD row; ADR-0006 and SECURITY note that the JVM core exists while the Keystore adapter and device evidence remain with T1-LOCAL-DATA-SECURITY (R5)
---

# T1-LOCAL-SECRET-BOX

## Why this card exists

User ruling 2026-09-25: deliver `T1-LOCAL-DATA-SECURITY` in three PRs (card registration, this JVM core, then the Android adapters with a device probe). Everything the parent's DoD asks of `LocalSecretBox` that a JVM can prove lives here; the parent keeps the Android Keystore key port, the unlock-state port, non-exportability and the real-device evidence, and closes the parent's full DoD.

## Shape

- `LocalSecretBox(keys: SecretKeyPort, unlock: DeviceUnlockPort, files: SecretEnvelopeFiles, sealVersion = 1, readableVersions = setOf(1))` with `seal(purpose, plaintext: CharArray): SecretSealResult` and `open(purpose): SecretOpenResult`. Versions are constructor values because the version-isolation acceptance needs two readable versions; production uses the defaults.
- `SecretPurpose`: `BACKUP_PASSPHRASE` (ADR-0006 background backup) and `REMEDIATION_API_KEY` (SECURITY.md and specs/android-module-boundaries.md).
- Cipher `AES/GCM/NoPadding`, 128-bit tag. Encryption initialises without an IV and reads `cipher.iv`, so the same code path works with Android Keystore keys, which generate the nonce themselves.
- `SecretOpenResult`: `Opened(SecretChars)`, `NeedsUnlock`, `NeedsPassphrase(reason)`; reasons `ENVELOPE_MISSING`, `ENVELOPE_UNREADABLE`, `ENVELOPE_CORRUPT`, `VERSION_UNSUPPORTED`, `KEY_MISSING`, `KEY_UNUSABLE`, `AUTHENTICATION_FAILED`. `SecretSealResult`: `STORED`, `NEEDS_UNLOCK`, `UNAVAILABLE`.
- The production `SecretEnvelopeFiles` is `LocalSecretEnvelopeStore` (T1-LOCAL-SECRET-STORE). Tests here use JDK software AES keys, in-memory fakes for the ports and one GCM-shaped cipher double.
- The lock check is repeated after a key, cipher or read failure, so a device that locks during the operation reports the retryable state rather than asking for the passphrase.

## Budget

Estimate 600-720 changed lines / 34k-42k characters (production about 230, tests about 420, R4 receipt comment about 50). `budget: 800` follows the 800-line split rule; above that, split before writing more.

2026-09-25 user ruling: after the fresh-context pre-review added seven test gaps, the card measured about 836 lines with its receipt. The envelope-file store, its test and the former R6/A6 moved to T1-LOCAL-SECRET-STORE; this card is now about 630 lines plus the receipt, still under `budget: 800`.

## R5 delivery (2026-09-25)

Merged by [PR #371](https://github.com/Asun28/MyInspection/pull/371) as squash `b045a2dc` (reviewed head `83b5ebaa`; CI run `36078602806`, `verify` and `required` success; `codex-review` success). The merged `android/` files are byte-identical to the reviewed head. 673 changed lines against `budget: 800`.

- RED: on base `3f75d89a` the new test class did not compile (`Unresolved reference 'SecretPurpose'`, `'LocalSecretBox'`).
- Tests: 23 in `LocalSecretBoxTest`; app suite 288 tests, 0 failures (1 pre-existing skip, `PdfDeviceFixtureTest` real80).
- R4: 43/43 single-point mutants killed by their named test with `java.lang.AssertionError`, against production SHA-256 `D9AECFB4…`; the receipt is the trailing comment in the test file and the evidence is `_local/local-secret-box/r4` in the main checkout.
- Pre-review: a fresh-context Opus 5.5 review before ship found 19 items. Seven test gaps were fixed: the nonce-size test passed only because `updateAAD` threw on CBC, open-uses-envelope-version was untested, no-write after every open, the no-caller-IV rule (now through a GCM-shaped test cipher), the size-cap round trip, the seal-side cipher failure, and the store's atomic move. Closing them put the card over budget, so the envelope store moved to `T1-LOCAL-SECRET-STORE` (#369). The unusable-key re-seal question went to the parent card.
- check-secrets flagged the file names `SecretEnvelopeStore*.kt` (a path segment starting with `secret`); per the user's rule they were renamed to `LocalSecretEnvelopeStore` (#361), not allowlisted.
- R3: Codex `gpt-5.6-sol` high. Round 1 blocked on two spec findings, both in the R4 evidence: M31 (the decoder scratch-buffer zero-fill) survived as unobservable, and the receipt's pre-receipt hash had no exact byte range. The scratch buffer became an injectable parameter (plus M43, an overflowing decode), R4 was rerun, and the receipt now gives `head -n 437 … | sha256sum`. Round 2 passed with no findings.
- Selftest: tier-1 routing escalated to the full 17 gates (`android/` paths have no GATE-MAP route) and passed in 2413 s on `5522786f`; only `android/` files changed after that.
- Author: the card names GPT-5.6 Terra; the work was done by a Claude Opus 5.5 session.