package nz.myinspection.app.privacy.policy

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitiveSurfacePolicyTest {
    @Test
    fun `backup password, restore preflight, tenant contact and full sensitive photo use a secure window`() {
        val listed = listOf(
            AppSurface.BACKUP_PASSWORD,
            AppSurface.RESTORE_PREFLIGHT,
            AppSurface.TENANT_CONTACT,
            AppSurface.FULL_SENSITIVE_PHOTO,
        )
        listed.forEach { surface ->
            assertEquals(SurfaceProtection.SECURE_WINDOW, SensitiveSurfacePolicy.protectionFor(surface), surface.name)
        }
    }

    @Test
    fun `capture, inspection list and report action carry no secure window`() {
        listOf(AppSurface.CAPTURE, AppSurface.INSPECTION_LIST, AppSurface.REPORT_ACTION).forEach { surface ->
            assertEquals(SurfaceProtection.ORDINARY, SensitiveSurfacePolicy.protectionFor(surface), surface.name)
        }
    }

    @Test
    fun `only the four listed surfaces are secure, so no surface applies a global secure window`() {
        val secure = AppSurface.entries
            .filter { SensitiveSurfacePolicy.protectionFor(it) == SurfaceProtection.SECURE_WINDOW }
            .toSet()

        assertEquals(
            setOf(
                AppSurface.BACKUP_PASSWORD,
                AppSurface.RESTORE_PREFLIGHT,
                AppSurface.TENANT_CONTACT,
                AppSurface.FULL_SENSITIVE_PHOTO,
            ),
            secure,
        )
    }

    @Test
    fun `no class in the policy package references an android type`() {
        // Unit tests may load the app classes from a directory or from a bundled classes.jar.
        val location = File(SensitiveSurfacePolicy::class.java.protectionDomain.codeSource.location.toURI())
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
        assertTrue("SensitiveSurfacePolicy.class" in classes, "no SensitiveSurfacePolicy.class in $location")

        val withAndroid = classes.filterValues { String(it, Charsets.ISO_8859_1).contains("android/") }.keys.sorted()

        assertEquals(emptyList(), withAndroid)
    }

    @Test
    fun `the policy source imports no android type`() {
        val source = File("src/main/kotlin/nz/myinspection/app/privacy/policy/SensitiveSurfacePolicy.kt")
        assertTrue(source.isFile, "no source at ${source.absolutePath}")
        assertEquals(emptyList(), source.readLines().filter { it.trimStart().startsWith("import android.") })
    }
}
