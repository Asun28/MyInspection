package nz.myinspection.app.privacy.policy

import java.io.File
import java.lang.reflect.Modifier
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClipboardPolicyTest {
    private val noticeBody = "Notice of entry: 42 Example St, 10:00 on 2026-11-12."

    @Test
    fun `an explicit copy notice action writes the notice body marked sensitive`() {
        val decision = ClipboardPolicy.authorize(
            ClipboardRequest.Write(ClipboardTrigger.EXPLICIT_COPY_NOTICE, ClipboardPayload.NOTICE_BODY, noticeBody),
        )

        val allowed = assertIs<ClipboardDecision.Allowed>(decision)
        assertEquals(noticeBody, allowed.command.text)
        assertTrue(allowed.command.markSensitive)
    }

    @Test
    fun `a write without the explicit copy notice action is denied`() {
        val implicit = listOf(ClipboardTrigger.SCREEN_OPEN, ClipboardTrigger.APP_START, ClipboardTrigger.BACKGROUND)
        implicit.forEach { trigger ->
            val decision = ClipboardPolicy.authorize(
                ClipboardRequest.Write(trigger, ClipboardPayload.NOTICE_BODY, noticeBody),
            )

            assertEquals(ClipboardDecision.Denied(ClipboardDenial.TRIGGER_NOT_EXPLICIT), decision, trigger.name)
        }
    }

    @Test
    fun `a write of anything other than the notice body is denied`() {
        val other = listOf(
            ClipboardPayload.TENANT_CONTACT,
            ClipboardPayload.PROPERTY_ADDRESS,
            ClipboardPayload.REPORT_TEXT,
            ClipboardPayload.BACKUP_PASSWORD,
        )
        other.forEach { payload ->
            val decision = ClipboardPolicy.authorize(
                ClipboardRequest.Write(ClipboardTrigger.EXPLICIT_COPY_NOTICE, payload, "Jane Tenant 021 000 0000"),
            )

            assertEquals(ClipboardDecision.Denied(ClipboardDenial.PAYLOAD_NOT_NOTICE_BODY), decision, payload.name)
        }
    }

    @Test
    fun `every clipboard read is denied whatever triggers it`() {
        ClipboardTrigger.entries.forEach { trigger ->
            val decision = ClipboardPolicy.authorize(ClipboardRequest.Read(trigger))

            assertEquals(ClipboardDecision.Denied(ClipboardDenial.READ_NOT_SUPPORTED), decision, trigger.name)
        }
    }

    @Test
    fun `the policy and its write command expose no way to read clipboard content`() {
        val policyMethods = ClipboardPolicy::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .toSet()
        val commandMethods = ClipboardWriteCommand::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(setOf("authorize"), policyMethods)
        assertEquals(setOf("getText", "getMarkSensitive", "toString"), commandMethods)
    }

    @Test
    fun `the write command does not print the notice body`() {
        val allowed = assertIs<ClipboardDecision.Allowed>(
            ClipboardPolicy.authorize(
                ClipboardRequest.Write(ClipboardTrigger.EXPLICIT_COPY_NOTICE, ClipboardPayload.NOTICE_BODY, noticeBody),
            ),
        )

        assertEquals("ClipboardWriteCommand(NOTICE_BODY)", allowed.command.toString())
    }

    @Test
    fun `no class in the policy package references an android type`() {
        // Unit tests may load the app classes from a directory or from a bundled classes.jar.
        val location = File(ClipboardPolicy::class.java.protectionDomain.codeSource.location.toURI())
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
        assertTrue("ClipboardPolicy.class" in classes, "no ClipboardPolicy.class in $location")

        val withAndroid = classes.filterValues { String(it, Charsets.ISO_8859_1).contains("android/") }.keys.sorted()

        assertEquals(emptyList(), withAndroid)
    }

    @Test
    fun `the policy source imports no android type`() {
        val source = File("src/main/kotlin/nz/myinspection/app/privacy/policy/ClipboardPolicy.kt")
        assertTrue(source.isFile, "no source at ${source.absolutePath}")
        val androidImport = Regex("""^\s*import\s+`?android`?\.""")
        assertEquals(emptyList(), source.readLines().filter { androidImport.containsMatchIn(it) })
    }
}
