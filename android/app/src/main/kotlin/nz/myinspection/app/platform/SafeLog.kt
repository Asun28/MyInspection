package nz.myinspection.app.platform

import android.util.Log

fun interface SafeLogSink {
    fun write(message: String)
}

enum class SafeLogOperation(val value: String) {
    MEDIA_TEMP_DELETE("media-temp-delete"),
    IMPORT_TEMP_DELETE("import-temp-delete"),
    PENDING_MARKER_DELETE("pending-marker-delete"),
    PENDING_LEASE_RELEASE("pending-lease-release"),
    ORPHAN_CLEANUP("orphan-cleanup"),
}

enum class SafeLogReason(val value: String) {
    DELETE_FAILED("delete-failed"),
    CLEANUP_FAILED("cleanup-failed"),
    PENDING_REJECTED("pending-rejected"),
    PENDING_FAILED("pending-failed"),
    SOFT_DELETE_REJECTED("soft-delete-rejected"),
    SOFT_DELETE_FAILED("soft-delete-failed"),
    EXECUTION_FAILED("execution-failed"),
}

class SafeLogOpaqueId private constructor(val value: String) {
    companion object {
        private val canonicalUuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        fun fromUuid(value: String): SafeLogOpaqueId = requireNotNull(parse(value)) { "opaque id must be a canonical UUID" }

        fun parse(value: String): SafeLogOpaqueId? =
            value.takeIf(canonicalUuid::matches)
                ?.let(::SafeLogOpaqueId)
    }
}

data class SafeLogEvent(
    val operation: SafeLogOperation,
    val reason: SafeLogReason,
    val opaqueId: SafeLogOpaqueId? = null,
    val count: Int? = null,
    val durationMs: Long? = null,
)

class SafeLog(private val sink: SafeLogSink) {
    fun record(event: SafeLogEvent) {
        val message = buildString {
            append("operation=${event.operation.value} reason=${event.reason.value}")
            event.opaqueId?.let { append(" opaque_id=${it.value}") }
            event.count?.takeIf { it >= 0 }?.let { append(" count=$it") }
            event.durationMs?.takeIf { it >= 0 }?.let { append(" duration_ms=$it") }
        }
        try {
            sink.write(message)
        } catch (_: Throwable) {
            // Diagnostic delivery is best effort and must not change a media result.
        }
    }

    companion object {
        fun android(): SafeLog = SafeLog(SafeLogSink { message -> Log.w(TAG, message) })

        private const val TAG = "MyInspectionSafeLog"
    }
}
