package nz.myinspection.core.media

import java.io.File
import java.io.OutputStream

/** Thin platform adapter: Android supplies Bitmap.compress, while this core workflow owns the stream lifecycle. */
fun interface StreamEncoder<Input> {
    fun encodeInto(input: Input, output: OutputStream)
}

/** The small staging contract keeps lifecycle tests executable without replacing the real file stager. */
interface VerifiedAssetStager {
    fun stage(target: File, producer: (OutputStream) -> Unit): StagedFile

    fun <T> useAndDiscard(staged: StagedFile, action: () -> T): T
}

/** A resource that must remain exclusively held from publishing an asset until its database record is decided. */
interface PublicationLease<Result> : AutoCloseable {
    fun finish(result: Result)

    /** A post-record cleanup failure is reportable but can never replace the completed database result. */
    fun onCompletedCleanupFailure(failure: Throwable) = Unit
}

/**
 * Shared final-JPEG workflow for camera and import. A publisher remains app-owned because its no-overwrite policy is
 * filesystem-specific; recording remains app-owned because it carries the existing DB compensation contract.
 */
object VerifiedAssetWorkflow {
    fun <Input, Plan, Result> encodeStagePublishRecord(
        target: File,
        input: Input,
        encoder: StreamEncoder<Input>,
        plan: (StagedFile) -> Plan,
        shouldPublish: (Plan) -> Boolean,
        publicationLease: (Plan) -> PublicationLease<Result>? = { null },
        publish: (StagedFile, Plan) -> Unit,
        record: (Plan) -> Result,
    ): Result = encodeStagePublishRecordWith(
        stager = StreamFileStager,
        target = target,
        input = input,
        encoder = encoder,
        plan = plan,
        shouldPublish = shouldPublish,
        publicationLease = publicationLease,
        publish = publish,
        record = record,
    )

    /** Internal production-stager seam: tests may observe or fault real file edges without faking a staged result. */
    internal fun <Input, Plan, Result> encodeStagePublishRecordWith(
        stager: VerifiedAssetStager,
        target: File,
        input: Input,
        encoder: StreamEncoder<Input>,
        plan: (StagedFile) -> Plan,
        shouldPublish: (Plan) -> Boolean,
        publicationLease: (Plan) -> PublicationLease<Result>? = { null },
        publish: (StagedFile, Plan) -> Unit,
        record: (Plan) -> Result,
    ): Result {
        val staged = stager.stage(target) { output -> encoder.encodeInto(input, output) }
        return stager.useAndDiscard(staged) {
            val planned = plan(staged)
            val shouldPublishAsset = shouldPublish(planned)
            val lease = if (shouldPublishAsset) publicationLease(planned) else null
            var primary: Throwable? = null
            var recordCompleted = false
            try {
                if (shouldPublishAsset) {
                    publish(staged, planned)
                }
                val result = record(planned)
                recordCompleted = true
                lease?.finish(result)
                result
            } catch (failure: Throwable) {
                primary = failure
                throw failure
            } finally {
                try {
                    lease?.close()
                } catch (closeFailure: Throwable) {
                    val failure = primary
                    if (failure != null) {
                        failure.addSuppressed(closeFailure)
                    } else if (recordCompleted) {
                        try {
                            lease?.onCompletedCleanupFailure(closeFailure)
                        } catch (reportFailure: Throwable) {
                            closeFailure.addSuppressed(reportFailure)
                        }
                    } else {
                        throw closeFailure
                    }
                }
            }
        }
    }

}
