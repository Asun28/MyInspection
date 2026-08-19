package nz.myinspection.core.media

import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** A closed, verified temporary file that a caller may publish with its existing file-store policy. */
class StagedFile internal constructor(
    val file: File,
    val digest: StreamDigest,
)

/**
 * Writes one producer pass into a sibling temporary file and verifies the closed file before returning it. This class
 * never publishes the final target: callers retain their existing no-overwrite/publish and DB-compensation policies.
 */
object StreamFileStager {
    fun stage(target: File, producer: (OutputStream) -> Unit): StagedFile = stageWith(target, producer)

    /** Runs [action] while owning the staged temp's final cleanup. A cleanup failure is never silently ignored. */
    fun <T> useAndDiscard(staged: StagedFile, action: () -> T): T = useAndDiscardWith(staged, action)

    /** Internal fault seam: the real file lifecycle stays intact while tests can inject write/read/delete failures. */
    internal fun stageWith(
        target: File,
        producer: (OutputStream) -> Unit,
        openOutput: (File) -> OutputStream = { file -> file.outputStream() },
        openInput: (File) -> InputStream = { file -> file.inputStream() },
        delete: (File) -> Boolean = { file -> file.delete() || !file.exists() },
    ): StagedFile {
        val parent = checkNotNull(target.parentFile) { "staged target has no parent: ${target.path}" }
        parent.mkdirs()
        val temp = File.createTempFile("${target.name}-", ".tmp", parent)
        try {
            val expected = StreamDigests.writeAndClose(openOutput(temp), producer)
            openInput(temp).use { input -> StreamDigests.verify(input, expected) }
            return StagedFile(temp, expected)
        } catch (primary: Throwable) {
            try {
                deleteOrThrow(temp, delete)
            } catch (cleanupFailure: Throwable) {
                primary.addSuppressed(cleanupFailure)
            }
            throw primary
        }
    }

    /** Internal fault seam for cleanup after a successfully staged asset. */
    internal fun <T> useAndDiscardWith(
        staged: StagedFile,
        action: () -> T,
        delete: (File) -> Boolean = { file -> file.delete() || !file.exists() },
    ): T {
        var primary: Throwable? = null
        try {
            return action()
        } catch (failure: Throwable) {
            primary = failure
            throw failure
        } finally {
            try {
                deleteOrThrow(staged.file, delete)
            } catch (cleanupFailure: Throwable) {
                val failure = primary
                if (failure == null) throw cleanupFailure
                failure.addSuppressed(cleanupFailure)
            }
        }
    }

    private fun deleteOrThrow(file: File, delete: (File) -> Boolean) {
        check(delete(file)) { "temporary file cleanup failed: ${file.path}" }
    }
}
