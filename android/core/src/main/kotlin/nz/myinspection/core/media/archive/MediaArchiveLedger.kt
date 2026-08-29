package nz.myinspection.core.media.archive

import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.report.Audience

/** Provider-neutral identity of bytes tracked by the local archive ledger. */
data class ArchiveAssetIdentity(
    val relPath: String,
    val contentHash: String,
    val byteSize: Long,
) {
    init {
        require(relPath.isNotEmpty()) { "archive asset path must not be empty" }
        require(contentHash.isNotEmpty()) { "archive asset hash must not be empty" }
        require(byteSize >= 0) { "archive asset size must not be negative" }
    }
}

enum class MediaArchiveState { PRESENT, ARCHIVED, RESTORING }

enum class ArchiveIneligibility(val code: String) {
    EXPORT_RECEIPT_MISSING("[ARCHIVE-EXPORT-RECEIPT-MISSING]"),
    ASSET_NOT_COVERED("[ARCHIVE-ASSET-NOT-COVERED]"),
    HASH_MISMATCH("[ARCHIVE-HASH-MISMATCH]"),
    SIZE_MISMATCH("[ARCHIVE-SIZE-MISMATCH]"),
    RECEIPT_REVOKED("[ARCHIVE-RECEIPT-REVOKED]"),
    PROPERTY_MISMATCH("[ARCHIVE-PROPERTY-MISMATCH]"),
    RECEIPT_FUTURE_TIME("[ARCHIVE-RECEIPT-FUTURE-TIME]"),
}

sealed interface ArchiveEligibility {
    data object Eligible : ArchiveEligibility

    data class Ineligible(val reason: ArchiveIneligibility) : ArchiveEligibility
}

/**
 * Persists local archive state and evaluates cleanup eligibility from verified exact-content evidence.
 * Verified receipt creation belongs to the archive-target contract and is intentionally absent here.
 */
class MediaArchiveLedger(
    private val db: MyInspectionDatabase,
    private val clock: ClockMs,
    private val uuid: Uuid7Generator,
) {
    fun recordAssetState(
        asset: ArchiveAssetIdentity,
        state: MediaArchiveState,
        reason: String,
    ) {
        require(reason.isNotEmpty()) { "[ARCHIVE-STATE-REASON-EMPTY]" }
        db.transaction {
            val existing = db.mediaArchiveQueries
                .selectLocalAssetStateByPath(asset.relPath)
                .executeAsOneOrNull()
            if (existing == null) {
                db.mediaArchiveQueries.insertLocalAssetState(
                    rel_path = asset.relPath,
                    content_hash = asset.contentHash,
                    byte_size = asset.byteSize,
                    state = state.name,
                    changed_at = clock.nowMs(),
                    reason = reason,
                )
            } else {
                check(existing.content_hash == asset.contentHash && existing.byte_size == asset.byteSize) {
                    "archive asset identity changed for ${asset.relPath}"
                }
                db.mediaArchiveQueries.updateLocalAssetStateIfIdentityMatches(
                    state = state.name,
                    changed_at = clock.nowMs(),
                    reason = reason,
                    rel_path = asset.relPath,
                    content_hash = asset.contentHash,
                    byte_size = asset.byteSize,
                )
            }
        }
    }

    fun recordReportExport(
        inspectionId: String,
        audience: Audience,
        quality: PhotoQualityProfile,
        asset: ArchiveAssetIdentity,
    ): String {
        val exportedAt = clock.nowMs()
        val id = uuid.next()
        db.mediaArchiveQueries.insertReportExportReceipt(
            id = id,
            inspection_id = inspectionId,
            audience = audience.name,
            quality = quality.name,
            rel_path = asset.relPath,
            content_hash = asset.contentHash,
            byte_size = asset.byteSize,
            exported_at = exportedAt,
        )
        return id
    }

    fun cleanupEligible(inspectionId: String): ArchiveEligibility {
        val audiences = db.mediaArchiveQueries
            .selectReportExportReceiptsByInspection(inspectionId)
            .executeAsList()
            .mapTo(mutableSetOf()) { it.audience }
        return if (Audience.entries.all { it.name in audiences }) {
            ArchiveEligibility.Eligible
        } else {
            ArchiveEligibility.Ineligible(ArchiveIneligibility.EXPORT_RECEIPT_MISSING)
        }
    }

    fun archivedEligible(asset: ArchiveAssetIdentity): ArchiveEligibility =
        db.transactionWithResult { evaluateArchivedEligibility(asset, clock.nowMs()) }

    fun archivedEligible(
        relPath: String,
        contentHash: String,
        byteSize: Long,
    ): ArchiveEligibility = archivedEligible(ArchiveAssetIdentity(relPath, contentHash, byteSize))

    fun assetsArchivedWithoutValidReceipt(): List<String> = db.transactionWithResult {
        val now = clock.nowMs()
        db.mediaArchiveQueries.selectArchivedAssetStates().executeAsList()
            .filter { row ->
                evaluateArchivedEligibility(
                    ArchiveAssetIdentity(row.rel_path, row.content_hash, row.byte_size),
                    now,
                ) != ArchiveEligibility.Eligible
            }
            .map { it.rel_path }
    }

    private fun evaluateArchivedEligibility(
        asset: ArchiveAssetIdentity,
        now: Long,
    ): ArchiveEligibility {
        val local = db.mediaArchiveQueries
            .selectLocalAssetStateByPath(asset.relPath)
            .executeAsOneOrNull()
        if (
            local == null ||
            local.state != MediaArchiveState.ARCHIVED.name ||
            local.content_hash != asset.contentHash ||
            local.byte_size != asset.byteSize
        ) {
            return ineligible(ArchiveIneligibility.ASSET_NOT_COVERED)
        }

        val pathCandidates = db.mediaArchiveQueries
            .selectCandidateReceiptEntriesByPath(asset.relPath)
            .executeAsList()
        if (pathCandidates.isEmpty()) return ineligible(ArchiveIneligibility.ASSET_NOT_COVERED)

        val hashCandidates = pathCandidates.filter { it.content_hash == asset.contentHash }
        if (hashCandidates.isEmpty()) return ineligible(ArchiveIneligibility.HASH_MISMATCH)

        val exactCandidates = hashCandidates.filter { it.byte_size == asset.byteSize }
        if (exactCandidates.isEmpty()) return ineligible(ArchiveIneligibility.SIZE_MISMATCH)

        val owners = db.mediaArchiveQueries
            .selectActiveAssetIdentitiesByPath(asset.relPath)
            .executeAsList()
        if (owners.any { it.content_hash != asset.contentHash }) {
            return ineligible(ArchiveIneligibility.HASH_MISMATCH)
        }
        val currentCandidates = exactCandidates.filter { it.revoked_at == null && it.verified_at <= now }
        if (currentCandidates.any { it.scope_kind == FULL_SCOPE }) return ArchiveEligibility.Eligible

        val ownerPropertyIds = owners.mapTo(mutableSetOf()) { it.property_id }
        val coveredPropertyIds = currentCandidates
            .filter { it.scope_kind == PROPERTY_SCOPE }
            .mapNotNullTo(mutableSetOf()) { it.scope_property_id }
        if (ownerPropertyIds.isNotEmpty() && coveredPropertyIds.containsAll(ownerPropertyIds)) {
            return ArchiveEligibility.Eligible
        }

        return when {
            exactCandidates.any { it.revoked_at != null } -> ineligible(ArchiveIneligibility.RECEIPT_REVOKED)
            exactCandidates.any { it.verified_at > now } -> ineligible(ArchiveIneligibility.RECEIPT_FUTURE_TIME)
            else -> ineligible(ArchiveIneligibility.PROPERTY_MISMATCH)
        }
    }

    private fun ineligible(reason: ArchiveIneligibility): ArchiveEligibility = ArchiveEligibility.Ineligible(reason)

    private companion object {
        const val FULL_SCOPE = "full"
        const val PROPERTY_SCOPE = "property"
    }
}
