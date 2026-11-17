package nz.myinspection.app.privacy.policy

enum class ClipboardTrigger {
    EXPLICIT_COPY_NOTICE,
    SCREEN_OPEN,
    APP_START,
    BACKGROUND,
}

enum class ClipboardPayload {
    NOTICE_BODY,
    TENANT_CONTACT,
    PROPERTY_ADDRESS,
    REPORT_TEXT,
    BACKUP_PASSWORD,
}

sealed interface ClipboardRequest {
    data class Write(val trigger: ClipboardTrigger, val payload: ClipboardPayload, val text: String) : ClipboardRequest {
        override fun toString(): String = "Write($trigger, $payload)"
    }

    data class Read(val trigger: ClipboardTrigger) : ClipboardRequest
}

enum class ClipboardDenial {
    READ_NOT_SUPPORTED,
    TRIGGER_NOT_EXPLICIT,
    PAYLOAD_NOT_NOTICE_BODY,
}

/** The one clipboard operation the app may perform: write a notice body, marked sensitive. */
class ClipboardWriteCommand internal constructor(val text: String) {
    val markSensitive: Boolean = true

    override fun toString(): String = "ClipboardWriteCommand(NOTICE_BODY)"
}

sealed interface ClipboardDecision {
    class Allowed internal constructor(val command: ClipboardWriteCommand) : ClipboardDecision {
        override fun toString(): String = "Allowed($command)"
    }

    data class Denied(val reason: ClipboardDenial) : ClipboardDecision
}

/** Clipboard writes only on the user's explicit "Copy notice" action. There is no read operation. */
object ClipboardPolicy {
    fun authorize(request: ClipboardRequest): ClipboardDecision = when (request) {
        is ClipboardRequest.Read -> ClipboardDecision.Denied(ClipboardDenial.READ_NOT_SUPPORTED)
        is ClipboardRequest.Write -> when {
            request.trigger != ClipboardTrigger.EXPLICIT_COPY_NOTICE ->
                ClipboardDecision.Denied(ClipboardDenial.TRIGGER_NOT_EXPLICIT)
            request.payload != ClipboardPayload.NOTICE_BODY ->
                ClipboardDecision.Denied(ClipboardDenial.PAYLOAD_NOT_NOTICE_BODY)
            else -> ClipboardDecision.Allowed(ClipboardWriteCommand(request.text))
        }
    }
}
