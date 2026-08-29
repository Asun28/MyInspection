package nz.myinspection.core.media.archive

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.db.Uuid7RandomSource

internal class MutableArchiveClock(var now: Long = NOW) : ClockMs {
    override fun nowMs(): Long = now
}

internal class MediaArchiveDbFixture : AutoCloseable {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: MyInspectionDatabase
    val clock = MutableArchiveClock()
    val uuid = Uuid7Generator(clock, Uuid7RandomSource { 0L })

    init {
        MyInspectionDatabase.Schema.create(driver)
        db = MyInspectionDatabase(driver)
    }

    fun ledger(): MediaArchiveLedger = MediaArchiveLedger(db, clock, uuid)

    fun insertReceipt(
        id: String,
        verifiedAt: Long = clock.now,
        scopeKind: String = "full",
        propertyId: String? = null,
        revokedAt: Long? = null,
        destinationKind: String = "SAF",
        destinationRef: String = "tree://latest",
        exportedAt: Long = clock.now,
    ) {
        db.mediaArchiveQueries.insertVerifiedBackupReceipt(
            id = id,
            destination_kind = destinationKind,
            destination_ref = destinationRef,
            object_ref = "$id.mibk",
            version_ref = null,
            exported_at = exportedAt,
            verified_at = verifiedAt,
            scope_kind = scopeKind,
            scope_property_id = propertyId,
            revoked_at = revokedAt,
        )
    }

    fun insertEntry(
        receiptId: String,
        relPath: String,
        hash: String = HASH_A,
        byteSize: Long = 100,
    ) {
        db.mediaArchiveQueries.insertVerifiedBackupReceiptEntry(receiptId, relPath, hash, byteSize)
    }

    fun seedOwner(propertyId: String, suffix: String, relPath: String, hash: String) {
        val templateId = "template-$suffix"
        val inspectionId = "inspection-$suffix"
        val roomId = "room-$suffix"
        db.propertyQueries.insert(propertyId, "1 Test St", "RENTAL", 0, NOW, NOW)
        db.templateVersionQueries.insert(templateId, "ROUTINE", suffix.filter(Char::isDigit).toLong(), "template-hash-$suffix", NOW, NOW)
        db.inspectionQueries.insert(
            id = inspectionId,
            type = "ROUTINE",
            property_id = propertyId,
            tenancy_id = null,
            template_version_id = templateId,
            scheduled_at = NOW,
            previous_inspection_id = null,
            baseline_inspection_id = null,
            status = "DRAFT",
            finalized_at = null,
            data_hash = null,
            created_at = NOW,
            updated_at = NOW,
        )
        db.roomInstanceQueries.insert(roomId, inspectionId, "BEDROOM", 1, "Bedroom", NOW, NOW)
        db.photoQueries.insert(
            id = "photo-$suffix",
            inspection_item_id = null,
            room_instance_id = roomId,
            rel_path = relPath,
            content_hash = hash,
            exif_time_ms = null,
            source = "CAMERA",
            privacy_flag = 0,
            created_at = NOW,
            updated_at = NOW,
        )
    }

    override fun close() {
        driver.close()
    }
}

internal fun assertIneligible(expected: ArchiveIneligibility, actual: ArchiveEligibility) {
    kotlin.test.assertEquals(ArchiveEligibility.Ineligible(expected), actual)
    kotlin.test.assertEquals(expected.code, (actual as ArchiveEligibility.Ineligible).reason.code)
}

internal const val NOW = 1_700_000_000_000L
internal const val HASH_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
internal const val HASH_B = "baaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
