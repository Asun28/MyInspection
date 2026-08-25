package nz.myinspection.core.report

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 字面量纪律, as an executable check rather than a convention. An assertion phrased as
 * `assertEquals(<the composer's own constant>, thumbnail.widthMm)` compares the production value with
 * itself and stays green when that constant is changed to anything at all, so no test in this package may
 * name the composer's companion at all. The companion is `private`, which stops the *code* path; this scan
 * is what stops the doc-comment path, where the same number gets restated and then quietly drifts.
 *
 * The complementary half of the card's purity clause - no `android.` / `androidx.` reaches `:core` - is a
 * build fact, not a text fact: `:core` is a `kotlin.jvm` module with no Android artifact on its compile
 * classpath, so `import android.x` does not compile. A source-text blacklist for it would be strictly
 * weaker than that (fully-qualified names and reflection walk straight past a name list) while reading as
 * though it were the guarantee, so it is deliberately not written here.
 */
class ReportSourcePurityTest {
    @Test
    fun `no report test source names the composer's companion, not even in a comment`() {
        // Assembled from two pieces on purpose: this scan covers its own file too, and a literal needle
        // here would be its own first violation - which is exactly the loophole an exclusion list opens.
        val needle = "ReportComposer" + "."
        val sources = testSourceFiles()

        assertEquals(
            listOf(
                "ReportComposerGoldenTest.kt",
                "ReportComposerLayoutContractTest.kt",
                "ReportComposerPaginationTest.kt",
                "ReportSourcePurityTest.kt",
                "ReportTestFixtures.kt",
            ),
            sources.map { it.name },
            "the scan must cover every test source in the package, including itself",
        )
        val hits = sources.flatMap { file ->
            file.readText(Charsets.UTF_8).lines().mapIndexedNotNull { index, line ->
                "${file.name}:${index + 1}: ${line.trim()}".takeIf { line.contains(needle) }
            }
        }
        assertEquals(emptyList(), hits, "a test names the composer's companion instead of writing the value out")
    }

    /**
     * Gradle runs a test with the project directory as its working directory, but a scan that silently
     * finds nothing is a scan that passes forever. Walk up to the directory that holds this package's test
     * sources and refuse outright if it is not there.
     */
    private fun testSourceFiles(): List<File> {
        val suffix = "src/test/kotlin/nz/myinspection/core/report"
        var directory: File? = File(System.getProperty("user.dir")).absoluteFile
        while (directory != null) {
            listOf(suffix, "core/$suffix", "android/core/$suffix").forEach { candidate ->
                val resolved = File(directory, candidate)
                if (resolved.isDirectory) {
                    val sources = resolved.listFiles { file -> file.name.endsWith(".kt") }.orEmpty()
                    assertTrue(sources.isNotEmpty(), "no Kotlin sources under $resolved")
                    return sources.sortedBy { it.name }
                }
            }
            directory = directory.parentFile
        }
        fail("cannot locate $suffix from ${System.getProperty("user.dir")}; the purity scan read nothing")
    }
}
