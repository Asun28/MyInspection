package nz.myinspection.app.privacy.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelectedMediaPolicyTest {
    @Test
    fun `a single user-selected item from the system photo picker is accepted`() {
        val decision = SelectedMediaPolicy.evaluate(
            MediaSelection(MediaOrigin.SYSTEM_PHOTO_PICKER, itemCount = 1, chosenByUser = true),
        )

        val accepted = assertIs<MediaSelectionDecision.Accepted>(decision)
        assertEquals(MediaOrigin.SYSTEM_PHOTO_PICKER, accepted.capability.origin)
    }

    @Test
    fun `a single user-selected document from the storage access framework is accepted`() {
        val decision = SelectedMediaPolicy.evaluate(
            MediaSelection(MediaOrigin.SAF_OPEN_DOCUMENT, itemCount = 1, chosenByUser = true),
        )

        val accepted = assertIs<MediaSelectionDecision.Accepted>(decision)
        assertEquals(MediaOrigin.SAF_OPEN_DOCUMENT, accepted.capability.origin)
    }

    @Test
    fun `every origin other than the photo picker and a single SAF document is rejected`() {
        val outside = listOf(
            MediaOrigin.SAF_OPEN_DOCUMENT_TREE,
            MediaOrigin.MEDIA_STORE_QUERY,
            MediaOrigin.FILE_PATH,
            MediaOrigin.INBOUND_SHARE_INTENT,
        )
        outside.forEach { origin ->
            val decision = SelectedMediaPolicy.evaluate(MediaSelection(origin, itemCount = 1, chosenByUser = true))

            assertEquals(
                MediaSelectionDecision.Rejected(MediaSelectionRejection.ORIGIN_NOT_ALLOWED),
                decision,
                origin.name,
            )
        }
    }

    @Test
    fun `a batch or empty selection is rejected even from an allowed origin`() {
        listOf(MediaOrigin.SYSTEM_PHOTO_PICKER, MediaOrigin.SAF_OPEN_DOCUMENT).forEach { origin ->
            listOf(0, 2, 30).forEach { count ->
                val decision = SelectedMediaPolicy.evaluate(MediaSelection(origin, itemCount = count, chosenByUser = true))

                assertEquals(
                    MediaSelectionDecision.Rejected(MediaSelectionRejection.NOT_SINGLE_ITEM),
                    decision,
                    "${origin.name} x $count",
                )
            }
        }
    }

    @Test
    fun `an item the user did not choose is rejected even from an allowed origin`() {
        listOf(MediaOrigin.SYSTEM_PHOTO_PICKER, MediaOrigin.SAF_OPEN_DOCUMENT).forEach { origin ->
            val decision = SelectedMediaPolicy.evaluate(MediaSelection(origin, itemCount = 1, chosenByUser = false))

            assertEquals(
                MediaSelectionDecision.Rejected(MediaSelectionRejection.NOT_USER_SELECTED),
                decision,
                origin.name,
            )
        }
    }
}
