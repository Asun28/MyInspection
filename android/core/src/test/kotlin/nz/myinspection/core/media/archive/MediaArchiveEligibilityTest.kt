package nz.myinspection.core.media.archive

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaArchiveEligibilityTest {
    @Test
    fun `provider signals do not cover bytes until an exact entry exists`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val asset = ArchiveAssetIdentity("media/a.jpg", HASH_A, 100)
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")
            fixture.insertReceipt("receipt-provider", destinationKind = "CLOUD", destinationRef = "cloud://recent", exportedAt = NOW + 10)

            assertIneligible(ArchiveIneligibility.ASSET_NOT_COVERED, ledger.archivedEligible(asset))
            fixture.insertEntry("receipt-provider", asset.relPath, asset.contentHash, asset.byteSize)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(asset))
        }
    }

    @Test
    fun `path candidates distinguish hash then size before exact coverage`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val asset = ArchiveAssetIdentity("media/a.jpg", HASH_A, 100)
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")
            fixture.insertReceipt("wrong-hash")
            fixture.insertEntry("wrong-hash", asset.relPath, HASH_B, asset.byteSize)
            assertIneligible(ArchiveIneligibility.HASH_MISMATCH, ledger.archivedEligible(asset))

            fixture.insertReceipt("wrong-size")
            fixture.insertEntry("wrong-size", asset.relPath, HASH_A, asset.byteSize + 1)
            assertIneligible(ArchiveIneligibility.SIZE_MISMATCH, ledger.archivedEligible(asset))
            fixture.insertReceipt("wrong-size-low")
            fixture.insertEntry("wrong-size-low", asset.relPath, HASH_A, asset.byteSize - 1)
            assertIneligible(ArchiveIneligibility.SIZE_MISMATCH, ledger.archivedEligible(asset))

            fixture.insertReceipt("exact")
            fixture.insertEntry("exact", asset.relPath, asset.contentHash, asset.byteSize)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(asset))
        }
    }

    @Test
    fun `local state and exact local identity are mandatory`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val asset = ArchiveAssetIdentity("media/state.jpg", HASH_A, 100)
            ledger.recordAssetState(asset, MediaArchiveState.PRESENT, "present")
            fixture.insertReceipt("state")
            fixture.insertEntry("state", asset.relPath)

            assertIneligible(ArchiveIneligibility.ASSET_NOT_COVERED, ledger.archivedEligible(asset))
            ledger.recordAssetState(asset, MediaArchiveState.RESTORING, "restoring")
            assertIneligible(ArchiveIneligibility.ASSET_NOT_COVERED, ledger.archivedEligible(asset))
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")
            assertIneligible(
                ArchiveIneligibility.ASSET_NOT_COVERED,
                ledger.archivedEligible(asset.relPath, HASH_A, asset.byteSize + 1),
            )
        }
    }

    @Test
    fun `revocation is terminal even at or before verification and never rewinds local state`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            listOf(NOW, NOW - 1, NOW + 1, 0L).forEachIndexed { index, revokedAt ->
                val path = "media/revoked-$index.jpg"
                val asset = ArchiveAssetIdentity(path, HASH_A, 100)
                ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "kept")
                fixture.insertReceipt("revoked-$index", revokedAt = revokedAt)
                fixture.insertEntry("revoked-$index", path)

                assertIneligible(ArchiveIneligibility.RECEIPT_REVOKED, ledger.archivedEligible(path, HASH_A, 100))
                val state = fixture.db.mediaArchiveQueries.selectLocalAssetStateByPath(path).executeAsOne()
                assertEquals(listOf("ARCHIVED", NOW, "kept"), listOf(state.state, state.changed_at, state.reason))
            }
        }
    }

    @Test
    fun `a valid exact receipt wins over revoked and future candidates`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val asset = ArchiveAssetIdentity("media/mixed.jpg", HASH_A, 100)
            ledger.recordAssetState(asset, MediaArchiveState.ARCHIVED, "archived")
            fixture.insertReceipt("revoked", revokedAt = NOW)
            fixture.insertEntry("revoked", asset.relPath)
            fixture.insertReceipt("future", verifiedAt = NOW + 1)
            fixture.insertEntry("future", asset.relPath)

            assertIneligible(ArchiveIneligibility.RECEIPT_REVOKED, ledger.archivedEligible(asset))
            fixture.insertReceipt("valid")
            fixture.insertEntry("valid", asset.relPath)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(asset))
        }
    }

    @Test
    fun `property scope covers every active owner while full covers shared paths`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val path = "media/shared.jpg"
            ledger.recordAssetState(ArchiveAssetIdentity(path, HASH_A, 100), MediaArchiveState.ARCHIVED, "archived")
            fixture.seedOwner("property-1", "1", path, HASH_A)
            fixture.seedOwner("property-2", "2", path, HASH_A)

            fixture.insertReceipt("property-1-receipt", scopeKind = "property", propertyId = "property-1")
            fixture.insertEntry("property-1-receipt", path)
            assertIneligible(ArchiveIneligibility.PROPERTY_MISMATCH, ledger.archivedEligible(path, HASH_A, 100))

            fixture.insertReceipt("property-2-receipt", scopeKind = "property", propertyId = "property-2")
            fixture.insertEntry("property-2-receipt", path)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(path, HASH_A, 100))

            fixture.db.mediaArchiveQueries.revokeVerifiedBackupReceipt(NOW, "property-1-receipt")
            fixture.db.mediaArchiveQueries.revokeVerifiedBackupReceipt(NOW, "property-2-receipt")
            fixture.insertReceipt("full-receipt")
            fixture.insertEntry("full-receipt", path)
            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(path, HASH_A, 100))
        }
    }

    @Test
    fun `conflicting active hashes fail closed even under a full receipt`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val path = "media/conflict.jpg"
            ledger.recordAssetState(ArchiveAssetIdentity(path, HASH_A, 100), MediaArchiveState.ARCHIVED, "archived")
            fixture.seedOwner("property-1", "1", path, HASH_A)
            fixture.seedOwner("property-2", "2", path, HASH_B)
            fixture.insertReceipt("full")
            fixture.insertEntry("full", path)

            assertIneligible(ArchiveIneligibility.HASH_MISMATCH, ledger.archivedEligible(path, HASH_A, 100))
        }
    }

    @Test
    fun `verification at clock is valid but future verification fails closed`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            val valid = ArchiveAssetIdentity("media/valid.jpg", HASH_A, 100)
            val future = ArchiveAssetIdentity("media/future.jpg", HASH_A, 100)
            ledger.recordAssetState(valid, MediaArchiveState.ARCHIVED, "archived")
            ledger.recordAssetState(future, MediaArchiveState.ARCHIVED, "archived")
            fixture.insertReceipt("valid", verifiedAt = NOW)
            fixture.insertEntry("valid", valid.relPath)
            fixture.insertReceipt("future", verifiedAt = NOW + 1)
            fixture.insertEntry("future", future.relPath)

            assertEquals(ArchiveEligibility.Eligible, ledger.archivedEligible(valid.relPath, HASH_A, 100))
            assertIneligible(ArchiveIneligibility.RECEIPT_FUTURE_TIME, ledger.archivedEligible(future.relPath, HASH_A, 100))
        }
    }

    @Test
    fun `invalid archived assets are stable sorted and the audit is read only`() {
        MediaArchiveDbFixture().use { fixture ->
            val ledger = fixture.ledger()
            listOf("media/c.jpg", "media/a.jpg", "media/b.jpg").forEach { path ->
                ledger.recordAssetState(ArchiveAssetIdentity(path, HASH_A, 100), MediaArchiveState.ARCHIVED, "archived")
            }
            fixture.insertReceipt("valid")
            fixture.insertEntry("valid", "media/b.jpg")
            fixture.insertReceipt("revoked", revokedAt = NOW)
            fixture.insertEntry("revoked", "media/a.jpg")
            val before = fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList()

            assertEquals(listOf("media/a.jpg", "media/c.jpg"), ledger.assetsArchivedWithoutValidReceipt())
            assertEquals(before, fixture.db.mediaArchiveQueries.selectAllLocalAssetStates().executeAsList())
        }
    }
}
