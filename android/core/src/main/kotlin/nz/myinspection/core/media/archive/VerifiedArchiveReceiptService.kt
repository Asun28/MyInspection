package nz.myinspection.core.media.archive

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import nz.myinspection.core.backup.format.BackupException
import nz.myinspection.core.backup.format.BackupManifest
import nz.myinspection.core.backup.format.BackupReader
import nz.myinspection.core.backup.format.BackupScope
import nz.myinspection.core.backup.format.BackupSink
import nz.myinspection.core.backup.format.BackupSourceException
import nz.myinspection.core.backup.format.BackupFileEntry
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator

/** Opaque provider identity. Nullable fields represent an untrusted, potentially incomplete boundary response. */
data class ArchiveIdentity(
    val destinationKind: String?,
    val destinationRef: String?,
    val objectRef: String?,
    val versionRef: String?,
)

data class WrittenArchive<T>(
    val identity: ArchiveIdentity,
    val value: T,
)

data class ArchiveReadback(
    val identity: ArchiveIdentity,
    val revokedAtMs: Long?,
    val input: InputStream,
)

/** Provider-neutral streaming boundary. Provider adapters belong to downstream cards. */
interface ArchiveStore {
    /** Returns only after the provider output has been closed and committed. */
    @Throws(IOException::class)
    fun <T> write(
        target: ArchiveIdentity,
        producer: (OutputStream) -> T,
    ): WrittenArchive<T>

    @Throws(IOException::class)
    fun reopen(written: ArchiveIdentity): ArchiveReadback
}

enum class ArchiveReceiptFailure(val code: String) {
    RECEIPT_INCOMPLETE("[ARCHIVE-RECEIPT-INCOMPLETE]"),
    VERIFY_READBACK_MISMATCH("[ARCHIVE-VERIFY-READBACK-MISMATCH]"),
    VERIFY_IDENTITY_MISMATCH("[ARCHIVE-VERIFY-IDENTITY-MISMATCH]"),
    VERIFY_UNAVAILABLE("[ARCHIVE-VERIFY-UNAVAILABLE]"),
}

sealed interface ArchiveReceiptResult {
    data class Recorded(
        val receiptId: String,
        val revokedAtMs: Long?,
    ) : ArchiveReceiptResult

    data class Rejected(val failure: ArchiveReceiptFailure) : ArchiveReceiptResult
}

/**
 * Writes an encrypted archive, reopens it through the same provider-neutral boundary, verifies the real backup
 * format to EOF, then records the receipt and every verified manifest entry in one database transaction.
 */
class VerifiedArchiveReceiptService(
    private val db: MyInspectionDatabase,
    private val store: ArchiveStore,
    private val clock: ClockMs,
    private val uuid: Uuid7Generator,
) {
    fun createVerifiedReceipt(
        target: ArchiveIdentity,
        scope: BackupScope?,
        passphrase: CharArray,
        writeArchive: (OutputStream) -> BackupManifest,
    ): ArchiveReceiptResult {
        val exportedAt = clock.nowMs()
        if (!identityComplete(target) || exportedAt <= 0 || scope == null) return incomplete()

        val written = try {
            store.write(target, writeArchive)
        } catch (_: BackupException) {
            return rejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH)
        } catch (_: IOException) {
            return rejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE)
        }
        if (!identityComplete(written.identity)) return incomplete()

        val readback = try {
            store.reopen(written.identity)
        } catch (_: BackupException) {
            return rejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH)
        } catch (_: IOException) {
            return rejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE)
        }

        val reopenedManifest = try {
            readback.input.use { input ->
                if (readback.identity != written.identity) {
                    return rejected(ArchiveReceiptFailure.VERIFY_IDENTITY_MISMATCH)
                }
                BackupReader.read(input, passphrase, VERIFY_ONLY_SINK)
            }
        } catch (_: BackupSourceException) {
            return rejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE)
        } catch (_: BackupException) {
            return rejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH)
        } catch (_: IOException) {
            return rejected(ArchiveReceiptFailure.VERIFY_UNAVAILABLE)
        }

        if (reopenedManifest.canonicalJson != written.value.canonicalJson || reopenedManifest.scope != scope) {
            return rejected(ArchiveReceiptFailure.VERIFY_READBACK_MISMATCH)
        }

        val verifiedAt = clock.nowMs()
        if (verifiedAt <= 0) return incomplete()
        val receiptId = uuid.next()
        val scopeFields = scope.toReceiptScope()
        db.transaction {
            db.mediaArchiveQueries.insertVerifiedBackupReceipt(
                id = receiptId,
                destination_kind = requireNotNull(written.identity.destinationKind),
                destination_ref = requireNotNull(written.identity.destinationRef),
                object_ref = requireNotNull(written.identity.objectRef),
                version_ref = written.identity.versionRef,
                exported_at = exportedAt,
                verified_at = verifiedAt,
                scope_kind = scopeFields.kind,
                scope_property_id = scopeFields.propertyId,
                revoked_at = readback.revokedAtMs,
            )
            reopenedManifest.files.forEach { file ->
                db.mediaArchiveQueries.insertVerifiedBackupReceiptEntry(
                    receipt_id = receiptId,
                    rel_path = file.relPath,
                    content_hash = file.sha256,
                    byte_size = file.sizeBytes,
                )
            }
        }
        return ArchiveReceiptResult.Recorded(receiptId, readback.revokedAtMs)
    }

    private fun identityComplete(identity: ArchiveIdentity): Boolean =
        !identity.destinationKind.isNullOrEmpty() &&
            !identity.destinationRef.isNullOrEmpty() &&
            !identity.objectRef.isNullOrEmpty() &&
            (identity.versionRef == null || identity.versionRef.isNotEmpty())

    private fun BackupScope.toReceiptScope(): ReceiptScope = when (this) {
        BackupScope.Full -> ReceiptScope(FULL_SCOPE, null)
        is BackupScope.Property -> ReceiptScope(PROPERTY_SCOPE, propertyId)
    }

    private fun incomplete(): ArchiveReceiptResult = rejected(ArchiveReceiptFailure.RECEIPT_INCOMPLETE)

    private fun rejected(failure: ArchiveReceiptFailure): ArchiveReceiptResult = ArchiveReceiptResult.Rejected(failure)

    private data class ReceiptScope(val kind: String, val propertyId: String?)

    private companion object {
        const val FULL_SCOPE = "full"
        const val PROPERTY_SCOPE = "property"

        val VERIFY_ONLY_SINK = object : BackupSink {
            override fun onManifest(manifest: BackupManifest) = Unit

            override fun openFile(file: BackupFileEntry): OutputStream? = null
        }
    }
}
