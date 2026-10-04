package nz.myinspection.app.platform

import android.os.Environment
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM coverage of the adapter's pure mapper and helper. The adapter's Context and Environment calls cannot run on the
 * JVM; AppStorageProbeActivity checks them on devices (docs/storage-android-probe.md).
 */
class AndroidAppStorageEnvironmentTest {
    @Test
    fun `mounted maps to mounted and read-only keeps its own state`() {
        assertEquals(ExternalMediaVolumeState.MOUNTED, externalMediaVolumeState(Environment.MEDIA_MOUNTED))
        assertEquals(
            ExternalMediaVolumeState.MOUNTED_READ_ONLY,
            externalMediaVolumeState(Environment.MEDIA_MOUNTED_READ_ONLY),
        )
    }

    @Test
    fun `every other raw state maps to unmounted`() {
        listOf(
            Environment.MEDIA_UNKNOWN, Environment.MEDIA_UNMOUNTED, Environment.MEDIA_REMOVED,
            Environment.MEDIA_CHECKING, Environment.MEDIA_NOFS, Environment.MEDIA_SHARED,
            Environment.MEDIA_BAD_REMOVAL, Environment.MEDIA_UNMOUNTABLE, Environment.MEDIA_EJECTING,
            "", "MOUNTED",
        ).forEach { raw ->
            assertEquals(ExternalMediaVolumeState.UNMOUNTED, externalMediaVolumeState(raw), raw)
        }
    }

    @Test
    fun `only an existing directory counts as writable`() {
        val directory = createTempDirectory("app-storage-android").toFile()
        try {
            val plainFile = File(directory, "plain").apply { writeText("x") }
            val absent = File(directory, "absent")
            assertTrue(plainFile.isFile && plainFile.canWrite(), "precondition: writable plain file")
            assertFalse(absent.exists(), "precondition: absent path")

            assertTrue(isWritableDirectory(directory))
            assertFalse(isWritableDirectory(plainFile))
            assertFalse(isWritableDirectory(absent))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `a directory that cannot be written is not writable`() {
        val directory = createTempDirectory("app-storage-android").toFile()
        try {
            val readOnly = object : File(directory.path) {
                override fun canWrite(): Boolean = false
            }
            assertTrue(readOnly.isDirectory, "precondition: existing directory")

            assertFalse(isWritableDirectory(readOnly))
        } finally {
            directory.deleteRecursively()
        }
    }
}
