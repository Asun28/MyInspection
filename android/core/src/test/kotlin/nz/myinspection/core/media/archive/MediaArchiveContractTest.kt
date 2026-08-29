package nz.myinspection.core.media.archive

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import nz.myinspection.core.backup.format.BackupFormat
import nz.myinspection.core.backup.format.BackupFormatException
import nz.myinspection.core.backup.format.BackupManifest
import nz.myinspection.core.backup.format.BackupScope
import nz.myinspection.core.backup.format.BackupSourceFile
import nz.myinspection.core.backup.format.BackupWriter
import nz.myinspection.core.backup.format.ScriptedRandom
import nz.myinspection.core.backup.format.TEST_ITERATIONS
import nz.myinspection.core.backup.format.TEST_PASSPHRASE
import nz.myinspection.core.backup.format.sourceFile
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.db.Uuid7RandomSource
import nz.myinspection.core.media.ContentHash
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.report.Audience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaArchiveContractTest {
    @Test
    fun `write failures are classified and never create evidence`() {
        val cases = listOf(
            BackupFormatException("writer rejected input") to ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH,
            IOException("provider write unavailable") to ArchiveReceiptFailure.VERIFY_UNAVAILABLE,
        )

        cases.forEach { (failure, expected) ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore().apply { writeFailure = failure }
                val result = receiptService(fixture, store).createVerifiedReceipt(
                    archiveTarget(),
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(expected, result, failure.message.orEmpty())
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
                assertEquals(0, store.reopenCalls)
            }
        }
    }

    @Test
    fun `every incomplete identity returned after write fails closed`() {
        val complete = archiveTarget()
        val cases = listOf(
            complete.copy(destinationKind = null),
            complete.copy(destinationKind = ""),
            complete.copy(destinationRef = null),
            complete.copy(destinationRef = ""),
            complete.copy(objectRef = null),
            complete.copy(objectRef = ""),
            complete.copy(versionRef = ""),
        )

        cases.forEach { returnedIdentity ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore().apply { writeIdentity = { returnedIdentity } }
                val result = receiptService(fixture, store).createVerifiedReceipt(
                    complete,
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(ArchiveReceiptFailure.RECEIPT_INCOMPLETE, result, returnedIdentity.toString())
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
                assertEquals(0, store.reopenCalls)
            }
        }
    }

    @Test
    fun `reopen format and availability failures are classified without evidence`() {
        val cases = listOf(
            BackupFormatException("provider rejected stored object") to ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH,
            IOException("provider unavailable") to ArchiveReceiptFailure.VERIFY_UNAVAILABLE,
        )

        cases.forEach { (failure, expected) ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore().apply { reopenFailure = failure }
                val result = receiptService(fixture, store).createVerifiedReceipt(
                    archiveTarget(),
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(expected, result, failure.message.orEmpty())
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
            }
        }
    }

    @Test
    fun `readback source and close failures are unavailable and never create evidence`() {
        var closeAttempted = false
        val inputs: List<(ByteArray) -> InputStream> = listOf(
            {
                object : InputStream() {
                    override fun read(): Int = throw IOException("source disconnected")
                }
            },
            { bytes ->
                object : ByteArrayInputStream(bytes) {
                    override fun close() {
                        closeAttempted = true
                        throw IOException("source close failed")
                    }
                }
            },
        )

        inputs.forEach { inputFactory ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore().apply { readbackInput = inputFactory }
                val result = receiptService(fixture, store).createVerifiedReceipt(
                    archiveTarget(),
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE, result)
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
            }
        }
        assertTrue(closeAttempted)
    }

    @Test
    fun `valid readback with different manifest is rejected without evidence`() {
        MediaArchiveDbFixture().use { fixture ->
            val differentArchive = archiveBytes(BackupScope.Full, twoPhotoFiles())
            val store = InMemoryArchiveStore().apply { readbackBytes = { differentArchive } }
            val result = receiptService(fixture, store).createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles()),
            )

            assertRejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH, result)
            assertEvidenceCounts(fixture, receipts = 0, entries = 0)
        }
    }

    @Test
    fun `valid readback with the same manifest but different archive bytes is rejected without evidence`() {
        MediaArchiveDbFixture().use { fixture ->
            val differentlyEncrypted = archiveBytes(BackupScope.Full, onePhotoFiles(), randomSeed = 9)
            val store = InMemoryArchiveStore().apply { readbackBytes = { differentlyEncrypted } }
            val result = receiptService(fixture, store).createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles(), randomSeed = 1),
            )

            assertRejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH, result)
            assertEvidenceCounts(fixture, receipts = 0, entries = 0)
        }
    }

    @Test
    fun `all six required receipt fields fail closed while null version succeeds`() {
        val target = archiveTarget()
        val cases = listOf(
            IncompleteCase("destination kind", target.copy(destinationKind = null), FixedSequenceClock(NOW), BackupScope.Full),
            IncompleteCase("destination ref", target.copy(destinationRef = null), FixedSequenceClock(NOW), BackupScope.Full),
            IncompleteCase("object ref", target.copy(objectRef = null), FixedSequenceClock(NOW), BackupScope.Full),
            IncompleteCase("exported at", target, FixedSequenceClock(0), BackupScope.Full),
            IncompleteCase("verified at", target, FixedSequenceClock(NOW, 0), BackupScope.Full),
            IncompleteCase("scope", target, FixedSequenceClock(NOW), null),
            IncompleteCase("empty version", target.copy(versionRef = ""), FixedSequenceClock(NOW), BackupScope.Full),
        )

        cases.forEach { case ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore()
                val service = receiptService(fixture, store, case.clock)
                val result = service.createVerifiedReceipt(
                    target = case.target,
                    scope = case.scope,
                    passphrase = TEST_PASSPHRASE,
                    writeArchive = archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(ArchiveReceiptFailure.RECEIPT_INCOMPLETE, result, case.label)
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
            }
        }

        MediaArchiveDbFixture().use { fixture ->
            val service = receiptService(fixture, InMemoryArchiveStore())
            val result = service.createVerifiedReceipt(
                target = target,
                scope = BackupScope.Full,
                passphrase = TEST_PASSPHRASE,
                writeArchive = archiveWriter(BackupScope.Full, onePhotoFiles()),
            )

            assertTrue(result is ArchiveReceiptResult.Recorded)
            assertNull(fixture.db.mediaArchiveQueries.selectVerifiedBackupReceiptById(result.receiptId).executeAsOne().version_ref)
        }
    }

    @Test
    fun `one changed archive byte is rejected while exact readback atomically records every manifest entry`() {
        MediaArchiveDbFixture().use { fixture ->
            val store = InMemoryArchiveStore().apply {
                readbackBytes = { stored ->
                    stored.copyOf().also { bytes -> bytes[BackupFormat.HEADER_BYTES + 10] = (bytes[BackupFormat.HEADER_BYTES + 10].toInt() xor 1).toByte() }
                }
            }
            val service = receiptService(fixture, store)
            val result = service.createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, twoPhotoFiles()),
            )

            assertRejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH, result)
            assertEvidenceCounts(fixture, receipts = 0, entries = 0)
        }

        MediaArchiveDbFixture().use { fixture ->
            val service = receiptService(fixture, InMemoryArchiveStore())
            val result = service.createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, twoPhotoFiles()),
            )

            assertTrue(result is ArchiveReceiptResult.Recorded)
            val entries = fixture.db.mediaArchiveQueries.selectVerifiedBackupReceiptEntries(result.receiptId).executeAsList()
            assertEquals(listOf("photos/b.jpg", "photos/a.jpg", "db.sqlite"), entries.map { it.rel_path })
            assertEquals(
                listOf(
                    Triple("photos/b.jpg", ContentHash.sha256Hex(PHOTO_B_BYTES), PHOTO_B_BYTES.size.toLong()),
                    Triple(PHOTO_A_PATH, PHOTO_A_HASH, PHOTO_A_BYTES.size.toLong()),
                    Triple("db.sqlite", DB_HASH, DB_BYTES.size.toLong()),
                ),
                entries.map { Triple(it.rel_path, it.content_hash, it.byte_size) },
            )
            assertEvidenceCounts(fixture, receipts = 1, entries = 3)
        }
    }

    @Test
    fun `entry conflict rolls the receipt back instead of leaving half evidence`() {
        MediaArchiveDbFixture().use { fixture ->
            val predictedId = Uuid7Generator(fixture.clock, Uuid7RandomSource { 0L }).next()
            fixture.insertEntry(predictedId, "db.sqlite", DB_HASH, DB_BYTES.size.toLong())
            val beforeEntries = fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList()
            val service = receiptService(fixture, InMemoryArchiveStore())

            assertFailsWith<Exception> {
                service.createVerifiedReceipt(
                    archiveTarget(),
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )
            }

            assertEquals(0, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceipts().executeAsList().size)
            assertEquals(beforeEntries, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList())
        }
    }

    @Test
    fun `reopen IOException records nothing preserves archived state and leaves the risk visible`() {
        MediaArchiveDbFixture().use { fixture ->
            val asset = ArchiveAssetIdentity(PHOTO_A_PATH, PHOTO_A_HASH, PHOTO_A_BYTES.size.toLong())
            val ledger = fixture.ledger()
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "kept")
            fixture.insertReceipt("unrelated")
            fixture.insertEntry("unrelated", "unrelated.bin", HASH_A, 1)
            val beforeState = fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList()
            val beforeReceipts = fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceipts().executeAsList()
            val beforeEntries = fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList()
            val store = InMemoryArchiveStore().apply { reopenFailure = IOException("unavailable") }

            val result = receiptService(fixture, store).createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles()),
            )

            assertRejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE, result)
            assertEquals(beforeReceipts, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceipts().executeAsList())
            assertEquals(beforeEntries, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList())
            assertEquals(beforeState, fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList())
            assertEquals(listOf(PHOTO_A_PATH), ledger.assetsArchivedWithoutValidReceipt())
        }
    }

    @Test
    fun `every opaque readback identity field must exactly match the written identity`() {
        val target = archiveTarget()
        val mismatches = listOf(
            target.copy(destinationKind = "saf"),
            target.copy(destinationRef = "TREE://root"),
            target.copy(objectRef = "Backup.mibk"),
            target.copy(versionRef = "v1"),
        )
        mismatches.forEach { reopenedIdentity ->
            MediaArchiveDbFixture().use { fixture ->
                val store = InMemoryArchiveStore().apply { readbackIdentity = { reopenedIdentity } }
                val result = receiptService(fixture, store).createVerifiedReceipt(
                    target,
                    BackupScope.Full,
                    TEST_PASSPHRASE,
                    archiveWriter(BackupScope.Full, onePhotoFiles()),
                )

                assertRejected(ArchiveReceiptFailure.VERIFY_IDENTITY_MISMATCH, result)
                assertEvidenceCounts(fixture, receipts = 0, entries = 0)
            }
        }

        MediaArchiveDbFixture().use { fixture ->
            val versioned = target.copy(versionRef = "v1")
            val store = InMemoryArchiveStore().apply { readbackIdentity = { it.copy(versionRef = null) } }
            val result = receiptService(fixture, store).createVerifiedReceipt(
                versioned,
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles()),
            )
            assertRejected(ArchiveReceiptFailure.VERIFY_IDENTITY_MISMATCH, result)
            assertEvidenceCounts(fixture, receipts = 0, entries = 0)
        }
    }

    @Test
    fun `readback revocation is persisted exactly and remains ineligible`() {
        MediaArchiveDbFixture().use { fixture ->
            val asset = ArchiveAssetIdentity(PHOTO_A_PATH, PHOTO_A_HASH, PHOTO_A_BYTES.size.toLong())
            val ledger = fixture.ledger()
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")
            val store = InMemoryArchiveStore().apply { revokedAtMs = 0L }
            val result = receiptService(fixture, store).createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles()),
            )

            assertTrue(result is ArchiveReceiptResult.Recorded)
            assertEquals(0L, result.revokedAtMs)
            assertEquals(
                0L,
                fixture.db.mediaArchiveQueries.selectVerifiedBackupReceiptById(result.receiptId).executeAsOne().revoked_at,
            )
            assertIneligible(ArchiveIneligibility.RECEIPT_REVOKED, ledger.archivedEligible(asset))
            assertEquals(listOf(PHOTO_A_PATH), ledger.assetsArchivedWithoutValidReceipt())
        }
    }

    @Test
    fun `requested scope must match readback manifest and property scope persists its owner`() {
        val propertyScope = BackupScope.Property("property-1")
        MediaArchiveDbFixture().use { fixture ->
            val mismatch = receiptService(fixture, InMemoryArchiveStore()).createVerifiedReceipt(
                archiveTarget(),
                propertyScope,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, onePhotoFiles()),
            )
            assertRejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH, mismatch)
            assertEvidenceCounts(fixture, receipts = 0, entries = 0)
        }

        MediaArchiveDbFixture().use { fixture ->
            val result = receiptService(fixture, InMemoryArchiveStore()).createVerifiedReceipt(
                archiveTarget(),
                propertyScope,
                TEST_PASSPHRASE,
                archiveWriter(propertyScope, onePhotoFiles()),
            )
            assertTrue(result is ArchiveReceiptResult.Recorded)
            val row = fixture.db.mediaArchiveQueries
                .selectVerifiedBackupReceiptById(result.receiptId)
                .executeAsOne()
            assertEquals("property", row.scope_kind)
            assertEquals("property-1", row.scope_property_id)
        }
    }

    @Test
    fun `full finalized chain never changes any photo or inspection column`() {
        MediaArchiveDbFixture().use { fixture ->
            seedFinalizedPhoto(fixture)
            val evidenceBefore = finalizedEvidenceHash(fixture)
            val asset = ArchiveAssetIdentity(FINAL_PHOTO_PATH, FINAL_PHOTO_HASH, FINAL_PHOTO_BYTES.size.toLong())
            val ledger = fixture.ledger()
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")

            val result = receiptService(fixture, InMemoryArchiveStore()).createVerifiedReceipt(
                archiveTarget(),
                BackupScope.Full,
                TEST_PASSPHRASE,
                archiveWriter(BackupScope.Full, finalizedPhotoFiles()),
            )

            assertTrue(result is ArchiveReceiptResult.Recorded)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(asset))
            assertEquals(evidenceBefore, finalizedEvidenceHash(fixture))
        }
    }

    @Test
    fun `fixed inputs produce identical primary key ordered hashes for all four archive tables`() {
        assertEquals(deterministicArchiveTableHashes(), deterministicArchiveTableHashes())
    }

    private fun deterministicArchiveTableHashes(): List<String> = MediaArchiveDbFixture().use { fixture ->
        val ledger = fixture.ledger()
        ledger.recordAssetState(
            ArchiveAssetIdentity(PHOTO_A_PATH, PHOTO_A_HASH, PHOTO_A_BYTES.size.toLong()),
            MediaArchiveState.ARCHIVED,
            "archived",
        )
        ledger.recordReportExport(
            inspectionId = "inspection-deterministic",
            audience = Audience.LANDLORD,
            quality = PhotoQualityProfile.LOW,
            asset = ArchiveAssetIdentity("reports/deterministic.pdf", HASH_A, 10),
        )
        val result = receiptService(fixture, InMemoryArchiveStore()).createVerifiedReceipt(
            archiveTarget(),
            BackupScope.Full,
            TEST_PASSPHRASE,
            archiveWriter(BackupScope.Full, onePhotoFiles()),
        )
        assertTrue(result is ArchiveReceiptResult.Recorded)

        listOf(
            tableHash(
                fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList().map {
                    row(it.rel_path, it.content_hash, it.byte_size, it.state, it.changed_at, it.reason)
                },
            ),
            tableHash(
                fixture.db.mediaArchiveQueries.selectAllReportExportReceipts().executeAsList().map {
                    row(it.id, it.inspection_id, it.audience, it.quality, it.rel_path, it.content_hash, it.byte_size, it.exported_at)
                },
            ),
            tableHash(
                fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceipts().executeAsList().map {
                    row(
                        it.id, it.destination_kind, it.destination_ref, it.object_ref, it.version_ref,
                        it.exported_at, it.verified_at, it.scope_kind, it.scope_property_id, it.revoked_at,
                    )
                },
            ),
            tableHash(
                fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList().map {
                    row(it.receipt_id, it.rel_path, it.content_hash, it.byte_size)
                },
            ),
        )
    }

    private fun finalizedEvidenceHash(fixture: MediaArchiveDbFixture): String {
        val inspection = fixture.db.inspectionQueries.selectById("inspection-final").executeAsOne()
        val photo = fixture.db.photoQueries.selectById("photo-final").executeAsOne()
        return tableHash(
            listOf(
                row(
                    inspection.id, inspection.type, inspection.property_id, inspection.tenancy_id,
                    inspection.template_version_id, inspection.scheduled_at, inspection.previous_inspection_id,
                    inspection.baseline_inspection_id, inspection.status, inspection.finalized_at,
                    inspection.data_hash, inspection.created_at, inspection.updated_at, inspection.deleted_at,
                ),
                row(
                    photo.id, photo.inspection_item_id, photo.room_instance_id, photo.rel_path,
                    photo.content_hash, photo.exif_time_ms, photo.source, photo.privacy_flag,
                    photo.created_at, photo.updated_at, photo.deleted_at,
                ),
            ),
        )
    }

    private fun seedFinalizedPhoto(fixture: MediaArchiveDbFixture) {
        fixture.db.propertyQueries.insert("property-final", "1 Test St", "RENTAL", 0, NOW, NOW)
        fixture.db.templateVersionQueries.insert("template-final", "ROUTINE", 1, "template-hash", NOW, NOW)
        fixture.db.inspectionQueries.insert(
            "inspection-final", "ROUTINE", "property-final", null, "template-final", NOW,
            null, null, "DRAFT", null, null, NOW, NOW,
        )
        fixture.db.roomInstanceQueries.insert("room-final", "inspection-final", "BEDROOM", 1, "Bedroom", NOW, NOW)
        fixture.db.photoQueries.insert(
            "photo-final", null, "room-final", FINAL_PHOTO_PATH, FINAL_PHOTO_HASH, null, "CAMERA", 0, NOW, NOW,
        )
        assertEquals(
            1L,
            fixture.db.inspectionQueries.finalizeIfDraft(NOW, "final-hash", NOW, "inspection-final").value,
        )
    }
}

private data class IncompleteCase(
    val label: String,
    val target: ArchiveIdentity,
    val clock: ClockMs,
    val scope: BackupScope?,
)

private class FixedSequenceClock(vararg values: Long) : ClockMs {
    private val values = values.copyOf()
    private var index = 0

    override fun nowMs(): Long = values[minOf(index++, values.lastIndex)]
}

private class InMemoryArchiveStore : ArchiveStore {
    var writeFailure: IOException? = null
    var writeIdentity: (ArchiveIdentity) -> ArchiveIdentity = { it }
    var readbackIdentity: (ArchiveIdentity) -> ArchiveIdentity = { it }
    var readbackBytes: (ByteArray) -> ByteArray = { it.copyOf() }
    var readbackInput: (ByteArray) -> InputStream = { ByteArrayInputStream(it) }
    var reopenFailure: IOException? = null
    var revokedAtMs: Long? = null
    var storedBytes: ByteArray = byteArrayOf()
    var reopenCalls: Int = 0

    override fun <T> write(
        target: ArchiveIdentity,
        producer: (OutputStream) -> T,
    ): WrittenArchive<T> {
        val output = ByteArrayOutputStream()
        val value = producer(output)
        output.close()
        storedBytes = output.toByteArray()
        writeFailure?.let { throw it }
        return WrittenArchive(writeIdentity(target), value)
    }

    override fun reopen(written: ArchiveIdentity): ArchiveReadback {
        reopenCalls++
        reopenFailure?.let { throw it }
        return ArchiveReadback(
            identity = readbackIdentity(written),
            revokedAtMs = revokedAtMs,
            input = readbackInput(readbackBytes(storedBytes)),
        )
    }
}

private fun receiptService(
    fixture: MediaArchiveDbFixture,
    store: ArchiveStore,
    clock: ClockMs = fixture.clock,
): VerifiedArchiveReceiptService = VerifiedArchiveReceiptService(
    db = fixture.db,
    store = store,
    clock = clock,
    uuid = Uuid7Generator(clock, Uuid7RandomSource { 0L }),
)

private fun archiveTarget(): ArchiveIdentity = ArchiveIdentity(
    destinationKind = "SAF",
    destinationRef = "tree://root",
    objectRef = "backup.mibk",
    versionRef = null,
)

private fun archiveWriter(
    scope: BackupScope,
    files: List<BackupSourceFile>,
    randomSeed: Byte = 1,
): (OutputStream) -> BackupManifest = { output ->
    BackupWriter.writeWith(
        out = output,
        passphrase = TEST_PASSPHRASE,
        scope = scope,
        createdAtMs = NOW,
        appVersion = "test",
        files = files,
        kdfIterations = TEST_ITERATIONS,
        random = ScriptedRandom(byteArrayOf(randomSeed, (randomSeed + 1).toByte(), (randomSeed + 2).toByte())),
    )
}

private fun archiveBytes(scope: BackupScope, files: List<BackupSourceFile>, randomSeed: Byte = 1): ByteArray =
    ByteArrayOutputStream().use { output ->
        archiveWriter(scope, files, randomSeed)(output)
        output.toByteArray()
    }

private fun onePhotoFiles(): List<BackupSourceFile> = listOf(
    sourceFile("db.sqlite", DB_BYTES),
    sourceFile(PHOTO_A_PATH, PHOTO_A_BYTES, "property-1"),
)

private fun twoPhotoFiles(): List<BackupSourceFile> = onePhotoFiles() +
    sourceFile("photos/b.jpg", PHOTO_B_BYTES, "property-2")

private fun finalizedPhotoFiles(): List<BackupSourceFile> = listOf(
    sourceFile("db.sqlite", DB_BYTES),
    sourceFile(FINAL_PHOTO_PATH, FINAL_PHOTO_BYTES, "property-final"),
)

private fun assertRejected(
    expected: ArchiveReceiptFailure,
    actual: ArchiveReceiptResult,
    clue: String = "",
) {
    assertEquals(ArchiveReceiptResult.Rejected(expected), actual, clue)
    assertEquals(expected.code, (actual as ArchiveReceiptResult.Rejected).failure.code, clue)
}

private fun assertEvidenceCounts(
    fixture: MediaArchiveDbFixture,
    receipts: Int,
    entries: Int,
) {
    assertEquals(receipts, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceipts().executeAsList().size)
    assertEquals(entries, fixture.db.mediaArchiveQueries.selectAllVerifiedBackupReceiptEntries().executeAsList().size)
}

private fun row(vararg values: Any?): String = values.joinToString(separator = "") { value ->
    if (value == null) {
        "N;"
    } else {
        val text = value.toString()
        "V${text.length}:$text;"
    }
}

private fun tableHash(rows: List<String>): String = ContentHash.sha256Hex(rows.joinToString("\n").encodeToByteArray())

private val DB_BYTES = byteArrayOf(7, 8, 9)
private val PHOTO_A_BYTES = byteArrayOf(0, 1, 2, 3)
private val PHOTO_B_BYTES = byteArrayOf(10, 11)
private val FINAL_PHOTO_BYTES = byteArrayOf(20, 21, 22)
private val DB_HASH = ContentHash.sha256Hex(DB_BYTES)
private val PHOTO_A_HASH = ContentHash.sha256Hex(PHOTO_A_BYTES)
private val FINAL_PHOTO_HASH = ContentHash.sha256Hex(FINAL_PHOTO_BYTES)
private const val PHOTO_A_PATH = "photos/a.jpg"
private const val FINAL_PHOTO_PATH = "photos/final.jpg"
