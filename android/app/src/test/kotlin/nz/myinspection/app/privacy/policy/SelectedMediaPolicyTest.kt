package nz.myinspection.app.privacy.policy

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun `no class in the policy package references an android type`() {
        // Unit tests may load the app classes from a directory or from a bundled classes.jar.
        val location = File(SelectedMediaPolicy::class.java.protectionDomain.codeSource.location.toURI())
        val prefix = "nz/myinspection/app/privacy/policy/"
        val classes: Map<String, ByteArray> = if (location.isDirectory) {
            location.resolve(prefix).listFiles { f -> f.name.endsWith(".class") }.orEmpty()
                .associate { it.name to it.readBytes() }
        } else {
            ZipFile(location).use { zip ->
                zip.entries().toList()
                    .filter { it.name.startsWith(prefix) && it.name.endsWith(".class") }
                    .filter { '/' !in it.name.removePrefix(prefix) }
                    .associate { it.name.removePrefix(prefix) to zip.getInputStream(it).use { s -> s.readBytes() } }
            }
        }
        assertTrue("SelectedMediaPolicy.class" in classes, "no SelectedMediaPolicy.class in $location")

        val withAndroid = classes.filterValues { String(it, Charsets.ISO_8859_1).contains("android/") }.keys.sorted()

        assertEquals(emptyList(), withAndroid)
    }
}
