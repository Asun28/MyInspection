package nz.myinspection.core.media

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PhotoOrphanCleanupExecutionTest {
    @Test
    fun `execution closes its driver after a successful cleanup`() {
        val driver = RecordingDriver()

        val result = PhotoOrphanCleanupExecution.run(
            open = { driver },
            cleanup = { PhotoOrphanCleanupDecision.SUCCESS },
            retryable = { false },
        )

        assertEquals(PhotoOrphanCleanupDecision.SUCCESS, result.decision)
        assertEquals(1, driver.closeCalls, "the worker lifecycle must close an otherwise successful driver")
    }

    @Test
    fun `execution turns driver close failure after success into retry`() {
        val closeFailure = IOException("close failed")
        val driver = RecordingDriver(closeFailure)

        val result = PhotoOrphanCleanupExecution.run(
            open = { driver },
            cleanup = { PhotoOrphanCleanupDecision.SUCCESS },
            retryable = { it is IOException },
        )

        assertEquals(PhotoOrphanCleanupDecision.RETRY, result.decision, "a close failure must never fake success")
        assertSame(closeFailure, result.failure)
        assertEquals(1, driver.closeCalls)
    }

    @Test
    fun `execution keeps a retryable cleanup primary and suppresses its close failure`() {
        val primary = IOException("database temporarily unavailable")
        val closeFailure = IOException("close also failed")
        val driver = RecordingDriver(closeFailure)

        val result = PhotoOrphanCleanupExecution.run(
            open = { driver },
            cleanup = { throw primary },
            retryable = { it is IOException },
        )

        assertEquals(PhotoOrphanCleanupDecision.RETRY, result.decision)
        assertSame(primary, result.failure, "driver cleanup must not replace the active failure")
        assertEquals(listOf(closeFailure), primary.suppressed.toList())
        assertEquals(1, driver.closeCalls)
    }

    @Test
    fun `execution fails closed for an unknown cleanup error`() {
        val driver = RecordingDriver()
        val primary = IllegalStateException("contract violated")

        val result = PhotoOrphanCleanupExecution.run(
            open = { driver },
            cleanup = { throw primary },
            retryable = { it is IOException },
        )

        assertEquals(PhotoOrphanCleanupDecision.FAILURE, result.decision)
        assertSame(primary, result.failure)
        assertEquals(1, driver.closeCalls)
    }

    private class RecordingDriver(
        private val closeFailure: Throwable? = null,
    ) : AutoCloseable {
        var closeCalls = 0
            private set

        override fun close() {
            closeCalls += 1
            closeFailure?.let { throw it }
        }
    }
}
