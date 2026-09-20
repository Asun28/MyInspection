# PDF device fixture: authorized real80 preflight and fixed report input

Delivered by `T3-PDF-DEVICE-FIXTURE` as debug-only code under `android/app/src/debug/.../export/pdf/`: it preflights
the frozen real80 manifest, binds each row to its verified file and builds one deterministic `ReportSnapshot`
projected through the delivered `ReportContentAdapter` (LANDLORD, private photos excluded). It draws nothing,
measures nothing and claims no device or PDF result; `T3-PDF-DEVICE-ACCEPTANCE` consumes it. Measured diff about 710
lines / 48k characters against the card's 460-625 forecast: the overage is pre-R3 review hardening (no-follow
regular-file check, `:` streams, exact placement pin, directory/junction case) kept in the card rather than split,
under the 1000/60k gate.

## Frozen input and preflight

- Manifest `manifest-approved-20260917.json`, SHA-256 `8721160680e73a2ce3570666ac416e31515bbd16fefb2a106e180790884ccad5`,
  in controlled local storage outside Git (`~/.agent-reach/myinspection-real80/`, photos under `photos/`): 80 rows,
  122,672,806 bytes, 78 JPEG + 1 MPO + 1 PNG, largest `real80-002` at 4200x18400, ids non-contiguous and not sorted.
- Template `data/templates/routine-v2.json`, raw-byte SHA-256 `fd06639f0b7117b1b3cbbf74a71b19cf0f0cc2ee4e374256ad967ad2bdcfe078`
  (`TemplateLoader` contentHash definition); the template snapshot id is fixture-local, not an installed row.
- `PdfFixtureManifest.preflight` refuses with `FixtureRefusal("[FIXTURE-<CODE>] ...")` and returns no fixture:
  `MANIFEST-DIGEST` · `FIXTURE-ID` · `AUTHORIZATION` · `PHOTO-COUNT` (declared or actual != 80) · `DISTINCT-ID` /
  `DISTINCT-FILENAME` / `DISTINCT-HASH` · `CATEGORY-COUNTS` (declared and actual both 20/20/21/19) · `DIMENSIONS` ·
  `LICENSE` (`Public domain`, `CC0`, `CC BY <n>.<n>[ <cc>]` only) · `CONTENT-CHECK` · `FILENAME` (a separator or `:`
  stream suffix in the name, or the entry is not a regular file under `NOFOLLOW_LINKS`: a directory, link or junction
  under that name is refused, never followed; blank and dot entries name directories) · `FILE-MISSING` · `FILE-BYTES`
  (the bytes are read through one channel opened with `NOFOLLOW_LINKS`, so nothing swapped in after the attribute
  check is what gets hashed). Only `root/<filename>` is opened and no directory is listed; the Android reader
  refuses the digest before `org.json` parses. `AuthorizedFixture` has a private constructor, so the builder only
  sees verified rows; the builder itself never reads photo bytes, and a consumer that later reads
  `FixturePhotoDescriptor.file` for drawing re-hashes at that time (DEVICE-ACCEPTANCE), since this card verifies at preflight.

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
outside this card (follow-up). 2026-09-20, three consecutive runs on the delivered sources (SHA-256
`PdfFixtureManifest.kt` `00af3d24...5297d8`, `PdfDeviceFixture.kt` `94ede9c8...69ab99`, `AndroidFixtureManifestReader.kt`
`d01baac3...fb368d`, `PdfDeviceFixtureTest.kt` `04e19e92...c500d7`):

| Input directory | Exit | real80 test | First line of the outcome |
|---|---|---|---|
| approved collection | 0 | PASS, 10 tests / 0 skipped / 0 failures | `[REAL80-FIXTURE] manifest=87211606...84ccad5 photos=80 native=f8573b32...751023 semantic=af17258a...a7287c` |
| copy with one byte appended to `photos/real80-001.jpg` | 1 | FAIL | `[FIXTURE-FILE-BYTES] real80-001 bytes differ from the manifest (281931 bytes read)` |
| empty directory | 1 | FAIL | `java.io.FileNotFoundException: .../manifest-approved-20260917.json` |

The passing run also asserts the 12,564-byte native canonical length; the receipt values equal the literals above.

## R4 mutation summary

28 single-point mutations (ids M01-M29; M23 was retired when the non-blank clause it targeted became dead code) of the
two pure-JVM production files at the SHAs above, each run against the focused test class with the source restored
and re-hashed afterwards; killer tests named per mutant in the batch log. Guards
(`PdfFixtureManifest.kt`): drop actual row count · drop actual / declared category counts · alter the required
category constant · drop file SHA-256 / file size comparison · drop distinct-hash · drop license · weaken license to
a `CC BY` prefix · weaken segment guard to non-blank · drop each of the slash, backslash and colon clauses · weaken
the manifest digest to a length check · drop the regular-file guard (a directory or a junction under the entry name
then reaches the byte read and fails there with an IOException instead of the refusal). Fixed content (`PdfDeviceFixture.kt`): swap R/1 reference slots ·
drop reference zero padding · shift the photo UUID counter · reverse descriptor order · drop canonical item ordering ·
alter the capture-time rule · alter a room label · alter an item status · compare semantic against the native
constant · skip verification in `build` · mark photos private · alter the template hash literal · shift the anchor.
Result 28/28 killed, none by compilation failure. The non-regular-file test uses a plain directory on every host
and a file symlink where one can be created, else an unprivileged `mklink /J` junction (this host); only the
`NOFOLLOW_LINKS` option itself needs a file symlink to observe, since a followed junction is still a directory.
