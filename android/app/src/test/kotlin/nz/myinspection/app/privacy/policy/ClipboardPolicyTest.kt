package nz.myinspection.app.privacy.policy

import java.lang.reflect.Modifier
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
}
