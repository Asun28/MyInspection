package nz.myinspection.core.media

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.testng.SkipException

class NoFollowLeafDeletionTest {
    @Test
    fun `treats an already absent leaf as a successful idempotent deletion`() = inTempDir { root ->
        assertTrue(File(root, "photos/property/inspection").mkdirs())

        assertTrue(NoFollowLeafDeletion.delete(root, "photos/property/inspection/missing.jpg"))
    }

    @Test
    fun `treats an absent intermediate directory as a successful idempotent deletion`() = inTempDir { root ->
        assertTrue(File(root, "photos").mkdirs())

        assertTrue(NoFollowLeafDeletion.delete(root, "photos/property/inspection/missing.jpg"))
    }

    @Test
    fun `treats an absent media root as a successful idempotent deletion`() = inTempDir { container ->
        val absentRoot = File(container, "missing-media-root")

        assertTrue(NoFollowLeafDeletion.delete(absentRoot, "photos/property/inspection/missing.jpg"))
    }

    @Test
    fun `accepts a trusted root reached through an aliased temporary parent`() = inTempDir { container ->
        val realParent = File(container, "real-parent").also { assertTrue(it.mkdirs()) }
        val realRoot = File(realParent, "media").also { assertTrue(it.mkdirs()) }
        val aliasParent = File(container, "alias-parent")
        createDirectoryAliasOrSkip(aliasParent, realParent)
        val leaf = File(realRoot, "photos/property/inspection/photo.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeText("orphan")
        }
        val trustedRootThroughAlias = File(aliasParent, "media")

        try {
            assertTrue(NoFollowLeafDeletion.delete(trustedRootThroughAlias, "photos/property/inspection/photo.jpg"))
            assertFalse(leaf.exists())
        } finally {
            Files.deleteIfExists(aliasParent.toPath())
        }
    }

    @Test
    fun `deletes a regular shaped leaf through only real lexical parents`() = inTempDir { root ->
        val leaf = File(root, "photos/property/inspection/photo.jpg").also {
            assertTrue(it.parentFile!!.mkdirs())
            it.writeText("orphan")
        }

        assertTrue(NoFollowLeafDeletion.delete(root, "photos/property/inspection/photo.jpg"))
        assertFalse(leaf.exists())
    }

    @Test
    fun `rejects a parent alias without deleting its referent leaf`() = inTempDir { root ->
        val referent = File(root, "real-inspection").also { assertTrue(it.mkdirs()) }
        val protectedLeaf = File(referent, "photo.jpg").also { it.writeText("protected") }
        val property = File(root, "photos/property").also { assertTrue(it.mkdirs()) }
        val alias = File(property, "inspection")
        createDirectoryAliasOrSkip(alias, referent)
        try {
            assertFalse(NoFollowLeafDeletion.delete(root, "photos/property/inspection/photo.jpg"))
            assertEqualsText("protected", protectedLeaf)
        } finally {
            Files.deleteIfExists(alias.toPath())
        }
    }

    @Test
    fun `revalidates every parent after a boundary race before deleting`() = inTempDir { root ->
        val inspection = File(root, "photos/property/inspection").also { assertTrue(it.mkdirs()) }
        val originalLeaf = File(inspection, "photo.jpg").also { it.writeText("original") }
        val parked = File(root, "parked-inspection")
        val referent = File(root, "replacement-inspection").also { assertTrue(it.mkdirs()) }
        val protectedLeaf = File(referent, "photo.jpg").also { it.writeText("protected") }
        var aliasCreated = false
        try {
            val deleted = NoFollowLeafDeletion.delete(
                root,
                "photos/property/inspection/photo.jpg",
                beforeDelete = {
                    assertTrue(inspection.renameTo(parked))
                    createDirectoryAliasOrSkip(inspection, referent)
                    aliasCreated = true
                },
            )

            assertFalse(deleted)
            assertEqualsText("original", File(parked, "photo.jpg"))
            assertEqualsText("protected", protectedLeaf)
        } finally {
            if (aliasCreated) Files.deleteIfExists(inspection.toPath())
        }
    }

    private fun createDirectoryAliasOrSkip(link: File, target: File) {
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            return
        } catch (_: UnsupportedOperationException) {
            // Try an unprivileged Windows junction below.
        } catch (_: IOException) {
            // Try an unprivileged Windows junction below.
        } catch (_: SecurityException) {
            // Try an unprivileged Windows junction below.
        }
        if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.path, target.path)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) return
            throw SkipException("directory aliases are unavailable: $output")
        }
        throw SkipException("directory aliases are unavailable on this host")
    }

    private fun assertEqualsText(expected: String, file: File) {
        assertTrue(file.isFile)
        assertTrue(file.readText() == expected)
    }

    private fun inTempDir(block: (File) -> Unit) {
        val root = kotlin.io.path.createTempDirectory("td14-no-follow-delete-").toFile()
        try {
            block(root)
        } finally {
            root.deleteRecursively()
        }
    }
}
