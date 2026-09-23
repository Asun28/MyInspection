# PDF device fixture: authorized real80 preflight and fixed report input

Delivered by `T3-PDF-DEVICE-FIXTURE` as debug-only code under `android/app/src/debug/.../export/pdf/`: it preflights
the frozen real80 manifest, binds each row to its verified file and builds one deterministic `ReportSnapshot`
projected through the delivered `ReportContentAdapter` (LANDLORD, private photos excluded). It draws nothing,
measures nothing and claims no device or PDF result; `T3-PDF-DEVICE-ACCEPTANCE` consumes it. Its size exceeds the
card's 650-line / 45k early stop; the user approved proceeding under the 1000/60k R3 gate (card, 2026-09-23 ruling).

## Frozen input and preflight

- Manifest `manifest-approved-20260917.json`, SHA-256 `8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5`,
  in controlled local storage outside Git (`~/.agent-reach/myinspection-real80/`, photos under `photos/`): 80 rows,
  122,672,806 bytes, 78 JPEG + 1 MPO + 1 PNG, largest `real80-002` at 4200x18400, ids non-contiguous and not sorted.
- Template `data/templates/routine-v2.json`, raw-byte SHA-256 `fd06639f0b7117b1b3cbbf74a71b19cf0f0cc2ee4e374256ad967ad2bdcfe078`
  (`TemplateLoader` contentHash definition); the template snapshot id is fixture-local, not an installed row.
- The public route is `AndroidFixtureManifestReader.preflight(fixtureDir)`: it hashes the manifest bytes it reads and
  refuses them before `org.json` parses. The pure-JVM `PdfFixtureManifest.preflight` it calls is internal and compares
  the digest it is handed (tests hand it synthetic rows). It refuses with `FixtureRefusal("[FIXTURE-<CODE>] ...")` and returns no fixture:
  `MANIFEST-DIGEST` · `FIXTURE-ID` · `AUTHORIZATION` · `PHOTO-COUNT` (declared or actual != 80) · `DISTINCT-ID` /
  `DISTINCT-FILENAME` / `DISTINCT-HASH` · `CATEGORY-COUNTS` (declared and actual both 20/20/21/19) · `DIMENSIONS` ·
  `LICENSE` (`Public domain`, `CC0`, `CC BY <n>.<n>[ <cc>]` only) · `CONTENT-CHECK` · `FILENAME` (a separator or `:`
  stream suffix in the name, or the entry is not a regular file under `NOFOLLOW_LINKS`: a directory, link or junction
  under that name is refused, never followed; the empty name, `.` and `..` name directories) · `FILE-MISSING` · `FILE-BYTES`
  (one channel opened with `NOFOLLOW_LINKS`: a link swapped in after the attribute check is not followed, and
  whatever that channel reads is what gets hashed). Only `root/<filename>` is opened and no directory is listed.
  `AuthorizedFixture` and `AuthorizedPhoto` have private constructors and no `copy`; their only factories are that
  internal preflight and the internal `AuthorizedPhoto.verify`, which checks the file first. `photos`,
  `FixedReportInput.descriptors` and the required category counts are read-only copies. The builder itself never reads photo bytes. Bytes are verified only at
  preflight: nothing re-verifies `FixturePhotoDescriptor.file` when it is read later. `contentHash` is carried so a
  consumer can re-hash at draw time; `T3-PDF-DEVICE-ACCEPTANCE` A7 requires that re-hash and refuses a mismatch
  before anything from that file is drawn.

## Fixed mapping (`PdfDeviceFixture`)

Anchor `1789603200000` ms (2026-09-17T00:00:00Z): scheduledAt = anchor, finalizedAt = anchor + 60000. UUIDv7 =
`(anchor << 80) | (7 << 76) | (2 << 62) | counter`; counters inspection 1, property 2, template 3, room `0x100 +
group`, item `0x200 + group`, photo `0x1000 + manifest ordinal`. Photo capturedAt = `1789599600000 + ordinal * 1000`
(fixed fallback, not EXIF); source `imported`, privacy false, exifTimeMs null. Tenancy, previous and baseline null.

| Group | Category / count | Room label | Item (Routine v2 sort) | Status | References |
|---|---|---|---|---|---|
| 1 | room_panorama / 20 | Panoramas / 房间全景 | none, room-level | none | 1.R.01 to 1.R.20 |
| 2 | low_light / 20 | Low light / 低光场景 | BED-LIGHT-01 (52) | GOOD | 2.1.01 to 2.1.20 |
| 3 | high_texture / 21 | Fine textures / 精细纹理 | HAL-WALL-01 (83) | FAIR | 3.1.01 to 3.1.21 |
| 4 | nameplate / 19 | Nameplates / 设备铭牌 | GEN-METER-01 (74) | POOR | 4.1.01 to 4.1.19 |

Canonical items are ordered by (template sort, room id, stable id): BED-LIGHT-01, GEN-METER-01, HAL-WALL-01; report
rooms follow group order, so the semantic traversal is BED-LIGHT-01, HAL-WALL-01, GEN-METER-01. Property address
`PDF fixture only / 仅用于 PDF 测试`, kind RENTAL. Glossary GOOD/FAIR/POOR/NOT_APPLICABLE.

## Expected digests

- native `data_hash` = `f8573b3252196b7ac36201755a2e6dbb9fcd91b2485899679aa5230c32751023` (canonical object 12,564 UTF-8 bytes)
- `semanticFingerprint` = `af17258a955afaa0dc73bab853db8ed9fa2424b043274f31bafa01c974a7287c` (canonical object 20,711 bytes)

Derived before RED by a Python-stdlib oracle that imports no application code (calibrated against the three published
`GoldenVectorTest` vectors, then NFC, UTF-16 key order, compact UTF-8, SHA-256 over the mapping above and the approved
manifest); oracle, canonical objects, receipt and the 392-assertion independent audit stay outside Git under
`_local/rotating-card-orchestrator/device-preflight/`. `PdfDeviceFixture.build` compares native to the native constant
and semantic to the semantic constant (`[FIXTURE-NATIVE-DRIFT]` / `[FIXTURE-SEMANTIC-DRIFT]`); the test file types
both literals itself rather than reading them back from the constants.

## Actual Kotlin builder comparison (explicit real-input run)

Portable tests use synthetic rows and prove guards and shape only. The env-gated test `real80 approved input
reproduces the frozen digests` skips (TestNG `SkipException`; the :app test task runs `useTestNG()`) when
`MYINSPECTION_REAL80_DIR` is unset and fails, never skips, once it is set:

```
$env:MYINSPECTION_REAL80_DIR = 'C:\Users\Admin\.agent-reach\myinspection-real80'
cmd /c android\gradlew.bat -p android --offline --no-daemon --rerun-tasks --no-build-cache -q :app:testDebugUnitTest --tests "nz.myinspection.app.export.pdf.PdfDeviceFixtureTest"
```

`--rerun-tasks --no-build-cache` is required: `org.gradle.caching=true` and the Test task's cache key ignores the
environment, so an unchanged classpath restores the previous result whatever the variable says (observed: an empty
directory "passed" until the flags were added). Registering the variable as a task input is :app build-script work
outside this card (follow-up). 2026-09-24, three consecutive runs on the delivered sources (SHA-256
`PdfFixtureManifest.kt` `b0704cee...58f471`, `PdfDeviceFixture.kt` `e4cb6858...06109f`, `AndroidFixtureManifestReader.kt`
`bff96f46...8e6ab8`, `PdfDeviceFixtureTest.kt` `a1ce1736...274ef7`; reports under `_local/T3-PDF-DEVICE-FIXTURE/real80/`):

| Input directory | Exit | real80 test | First line of the outcome |
|---|---|---|---|
| approved collection | 0 | PASS, 11 tests / 0 skipped / 0 failures | `[REAL80-FIXTURE] manifest=87211606...84ccad5 photos=80 native=f8573b32...751023 semantic=af17258a...a7287c` |
| copy with one byte appended to `photos/real80-001.jpg` | 1 | FAIL | `[FIXTURE-FILE-BYTES] real80-001 bytes differ from the manifest (281931 bytes read)` |
| empty directory | 1 | FAIL | `java.io.FileNotFoundException: .../manifest-approved-20260917.json` |

The passing run also asserts the 12,564-byte native canonical length; the receipt values equal the literals above.

## R4 mutation receipt

2026-09-24: 63 single-point mutants of the three debug files at the SHAs above, one at a time against the focused
class with `MYINSPECTION_REAL80_DIR` unset, so the real80 test skips and every kill below is portable. 63/63 killed,
each by a failing test in a produced report (none by compilation); the source was restored and re-hashed after each.
Per-mutant records (edit, exit, failing tests, first failure, restored SHA) and the batch script stay outside Git
under `_local/T3-PDF-DEVICE-FIXTURE/r4/`. Exact edit, grouped by a killing test (the records list every failing test):

- `preflight refuses each inconsistent manifest with its own code`: M01 drop `&& manifest.rows.size == PHOTO_COUNT` ·
  M02 drop `manifest.photoCount == PHOTO_COUNT &&` · M03 / M04 drop the declared / actual `== REQUIRED_CATEGORY_COUNTS` ·
  M06-M08 distinct photo_id / filename / sha256 → `true` · M09 fixture_id → `true` · M10 authorization → `true` ·
  M11-M13 drop `width > 0` / `height > 0` / `bytes > 0` · M14 licence → `true` · M15 `CC_BY.matches(...)` →
  `startsWith("CC BY")` · M16 content_check → `true` · M17 segment guard → `name.isNotBlank()` · M18-M20 drop the
  `'/'` / `'\\'` / `':'` clause · M21 digest equality → `length == 64` · M23 drop `sha256Hex(body) == row.sha256` ·
  M24 drop `body.size.toLong() == row.bytes`.
- The nine portable tests that run the preflight (all but the reader test): M05 required `"nameplate" to 19` → `18`
  (the good manifest is refused with CATEGORY-COUNTS).
  All six tests that build: F17 `isRoomLevel = false` (the adapter rejects the room-level slot with IllegalArgumentException).
- `preflight refuses an entry that is not a regular file under the root`: M22 `entry!!.isRegularFile` → `true`.
- `reader refuses manifest bytes that are not the approved ones`: R01 the reader's digest →
  `PdfFixtureManifest.APPROVED_MANIFEST_SHA256` · R02 the reader's `requireApprovedDigest(digest)` → `Unit`.
- `verified types expose no public factory and their collections are read-only`: M25 / F26 `photos` / `descriptors`
  without `unmodifiableList` · M26 `Collections.unmodifiableMap(` → `LinkedHashMap(` · M27 / M29 the object / companion
  `internal fun preflight` → `fun preflight` · M28 / M30 `AuthorizedFixture` / `AuthorizedPhoto` `private constructor(`
  → `constructor(` · M31 `internal fun verify(` → `fun verify(`.
- `references and UUIDv7 identities derive from manifest order`: F01 swap the `"R"` / `"1"` slots · F02 drop
  `padStart(2, '0')` · F03 photo counter `0x1000` → `0x1001` · F08 capture step `1000L` → `1001L`.
- `fixed identity literals match the frozen mapping and the real template bytes`: F04 room counter `0x100` → `0x110` ·
  F05 item counter `0x200` → `0x210` · F15 address `仅用于 PDF 测试` → `仅用于测试` · F16 kind `RENTAL` → `OTHER` ·
  F21 template hash `fd06639f...` → `ed06639f...` · F22 anchor + 1000 ms.
- `builder places exactly eighty photos across the four fixed groups`: F06 descriptors `.reversed()` · F09 room label
  `Panoramas` → `Panorama` · F10 room label `低光场景` → `低光` · F18 photos private `false` → `true` · F19 `LANDLORD` →
  `TENANT` · F20 `includePrivacyPhotos = true` · F27 descriptor `itemId` → `null` · F28 descriptor `contentHash` →
  `photo.row.photoId` · F29 descriptor `manifestOrdinal` → `0` · F30 only the `ReportPhoto` reference → `reference + "x"`.
- `canonical items follow template order while rooms follow group order`: F07 drop the `sortedWith(...)` ordering ·
  F11 item label `纹理样本` → `纹理` · F12 note `测试铭牌小字` → `测试铭牌` · F13 HAL-WALL-01 `FAIR` → `POOR` · F14
  glossary `可见正常损耗` → `可见损耗`.
- `expected digests are distinct literals and drift is refused by name`: F23 `build` without `verifyFixedDigests` ·
  F24 native compared to the semantic constant · F25 semantic compared to the native constant.

Not mutated: the org.json parsing in `AndroidFixtureManifestReader.read` (org.json is an Android stub on the JVM, so
only a device run exercises it) and the two `NOFOLLOW_LINKS` options. The non-regular-file test uses a plain directory on every host and a file
symlink where one can be created, else an unprivileged `mklink /J` junction (this host); a followed junction is still
a directory, so dropping `NOFOLLOW_LINKS` is observable only where a file symlink can be planted.
