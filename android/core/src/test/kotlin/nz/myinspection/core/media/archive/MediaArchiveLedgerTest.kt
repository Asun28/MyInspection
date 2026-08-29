package nz.myinspection.core.media.archive

import nz.myinspection.core.backup.format.BackupFormat
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.report.Audience
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MediaArchiveLedgerTest {
    @Test
    fun `state writes use one injected instant reject empty reason and preserve finalized evidence`() {
        MediaArchiveDbFixture().use { fixture ->
            seedFinalizedPhoto(fixture)
            val inspectionBefore = fixture.db.inspectionQueries.selectById("inspection-final").executeAsOne()
            val photoBefore = fixture.db.photoQueries.selectById("photo-final").executeAsOne()
            val ledger = fixture.ledger()
            val entry = ArchiveAssetIdentity("media/final.jpg", HASH_A, 100)

            val rejected = assertFailsWith<IllegalArgumentException> {
                ledger.recordAssetState(entry, MediaArchiveState.PRESENT, "")
            }
            assertTrue(rejected.message.orEmpty().contains("[ARCHIVE-STATE-REASON-EMPTY]"))

            ledger.recordAssetState(entry, MediaArchiveState.PRESENT, "x")
            ledger.recordAssetState(entry, MediaArchiveState.ARCHIVED, "verified")
            val row = fixture.db.mediaArchiveQueries.selectLocalAssetStateByPath(entry.relPath).executeAsOne()
            assertEquals(listOf(HASH_A, 100L, "ARCHIVED", NOW, "verified"), listOf(row.content_hash, row.byte_size, row.state, row.changed_at, row.reason))
            assertEquals(1, fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList().size)
            ledger.recordAssetState(entry, MediaArchiveState.RESTORING, "r")
            assertEquals(
                "RESTORING",
                fixture.db.mediaArchiveQueries.selectLocalAssetStateByPath(entry.relPath).executeAsOne().state,
            )
            ledger.recordAssetState(entry, MediaArchiveState.ARCHIVED, "verified")
            assertEquals(inspectionBefore, fixture.db.inspectionQueries.selectById("inspection-final").executeAsOne())
            assertEquals(photoBefore, fixture.db.photoQueries.selectById("photo-final").executeAsOne())

            assertFailsWith<IllegalStateException> {
                ledger.recordAssetState(
                    ArchiveAssetIdentity(entry.relPath, HASH_B, 101),
                    MediaArchiveState.ARCHIVED,
                    "different bytes",
                )
            }
            assertEquals(HASH_A, fixture.db.mediaArchiveQueries.selectLocalAssetStateByPath(entry.relPath).executeAsOne().content_hash)
        }
    }

    @Test
    fun `report receipts cover both audiences all qualities and share the injected clock`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val stateEntry = ArchiveAssetIdentity("media/a.jpg", HASH_A, 100)
            ledger.recordAssetState(stateEntry, MediaArchiveState.PRESENT, "captured")

            PhotoQualityProfile.entries.forEachIndexed { index, quality ->
                ledger.recordReportExport(
                    inspectionId = "inspection-1",
                    audience = Audience.LANDLORD,
                    quality = quality,
                    asset = ArchiveAssetIdentity("reports/landlord-$index.pdf", HASH_A, 200 + index.toLong()),
                )
            }
            ledger.recordReportExport(
                inspectionId = "inspection-1",
                audience = Audience.TENANT,
                quality = PhotoQualityProfile.MEDIUM,
                asset = ArchiveAssetIdentity("reports/tenant.pdf", HASH_B, 250),
            )

            val receipts = fixture.db.mediaArchiveQueries.selectReportExportReceiptsByInspection("inspection-1").executeAsList()
            assertEquals(listOf("LOW", "MEDIUM", "HIGH", "EXTRA_HIGH"), receipts.filter { it.audience == "LANDLORD" }.map { it.quality })
            assertTrue(receipts.all { it.exported_at == NOW })
            assertEquals(NOW, fixture.db.mediaArchiveQueries.selectLocalAssetStateByPath(stateEntry.relPath).executeAsOne().changed_at)
            assertEquals(ArchiveEligibility.Eligible, ledger.cleanupEligible("inspection-1"))
            assertEquals(1, BackupFormat.FORMAT_VERSION)

            val receiptCount = receipts.size
            assertFailsWith<Exception> {
                ledger.recordReportExport(
                    "inspection-1",
                    Audience.TENANT,
                    PhotoQualityProfile.MEDIUM,
                    ArchiveAssetIdentity("reports/tenant-duplicate.pdf", HASH_A, 251),
                )
            }
            assertEquals(
                receiptCount,
                fixture.db.mediaArchiveQueries.selectReportExportReceiptsByInspection("inspection-1").executeAsList().size,
            )
        }
    }

    @Test
    fun `cleanup eligibility reports the missing audience code`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            ledger.recordReportExport(
                "inspection-2",
                Audience.LANDLORD,
                PhotoQualityProfile.LOW,
                ArchiveAssetIdentity("reports/landlord.pdf", HASH_A, 200),
            )
            ledger.recordReportExport(
                "inspection-2",
                Audience.LANDLORD,
                PhotoQualityProfile.HIGH,
                ArchiveAssetIdentity("reports/landlord-high.pdf", HASH_A, 210),
            )
            ledger.recordReportExport(
                "inspection-other",
                Audience.TENANT,
                PhotoQualityProfile.LOW,
                ArchiveAssetIdentity("reports/tenant-other.pdf", HASH_A, 220),
            )

            assertIneligible(ArchiveIneligibility.EXPORT_RECEIPT_MISSING, ledger.cleanupEligible("inspection-2"))
        }
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
            "photo-final", null, "room-final", "media/final.jpg", HASH_A, null, "CAMERA", 0, NOW, NOW,
        )
        assertEquals(1L, fixture.db.inspectionQueries.finalizeIfDraft(NOW, "final-hash", NOW, "inspection-final").value)
    }
}
