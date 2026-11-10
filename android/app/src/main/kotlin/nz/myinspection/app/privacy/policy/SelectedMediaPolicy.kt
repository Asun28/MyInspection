package nz.myinspection.app.privacy.policy

/** Where a media item handed to the app came from. Only the system photo picker and a single SAF document are allowed. */
enum class MediaOrigin {
    SYSTEM_PHOTO_PICKER,
    SAF_OPEN_DOCUMENT,
    SAF_OPEN_DOCUMENT_TREE,
    MEDIA_STORE_QUERY,
    FILE_PATH,
    INBOUND_SHARE_INTENT,
}

/** What the caller reports about one selection result, before any URI is opened. */
data class MediaSelection(
    val origin: MediaOrigin,
    val itemCount: Int,
    val chosenByUser: Boolean,
)

enum class MediaSelectionRejection {
    ORIGIN_NOT_ALLOWED,
    NOT_SINGLE_ITEM,
    NOT_USER_SELECTED,
}

/** Proof that one item was chosen by the user in an allowed system UI. It carries no URI or path. */
class SelectedMediaCapability internal constructor(val origin: MediaOrigin) {
    override fun toString(): String = "SelectedMediaCapability($origin)"
}

sealed interface MediaSelectionDecision {
    class Accepted internal constructor(val capability: SelectedMediaCapability) : MediaSelectionDecision {
        override fun toString(): String = "Accepted($capability)"
    }

    data class Rejected(val reason: MediaSelectionRejection) : MediaSelectionDecision
}

object SelectedMediaPolicy {
    // Behavioural RED baseline stub: accepts every selection.
    fun evaluate(selection: MediaSelection): MediaSelectionDecision =
        MediaSelectionDecision.Accepted(SelectedMediaCapability(selection.origin))
}
