package nz.myinspection.app.feature.schedule

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nz.myinspection.core.schedule.InspectionScheduleType

class ReminderContractsTest {
    private val route = ScheduleRoute("property-a", InspectionScheduleType.ROUTINE)
    private val dueAt = Instant.parse("2026-08-03T00:00:00.000000001Z")
    private val spec = WorkSpecFactory().create(route, dueAt)

    @Test
    fun `occurrence identity pins exact seconds and nanos golden vector`() {
        assertEquals(
            "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108",
            spec.occurrenceId,
        )
        assertEquals(spec.occurrenceId, reminderOccurrenceId(route, dueAt))
        assertEquals("schedule-reminder:${spec.occurrenceId}", spec.uniqueWorkName)
        assertEquals(route, spec.route)
        assertEquals(dueAt, spec.dueAt)
        assertEquals(64, spec.occurrenceId.length)
        assertTrue(spec.occurrenceId.matches(Regex("[0-9a-f]{64}")))
        assertTrue(route.propertyId !in spec.uniqueWorkName)
        assertFailsWith<IllegalArgumentException> {
            WorkSpecFactory().create(route.copy(propertyId = ""), dueAt)
        }
    }

    @Test
    fun `occurrence identity changes for every canonical input component`() {
        val variants = listOf(
            reminderOccurrenceId(route.copy(propertyId = "property-b"), dueAt),
            reminderOccurrenceId(route.copy(inspectionType = InspectionScheduleType.ANNUAL), dueAt),
            reminderOccurrenceId(route, dueAt.plusNanos(1)),
        )

        variants.forEach { variant -> assertNotEquals(spec.occurrenceId, variant) }
        assertEquals(variants.size, variants.toSet().size)
    }

    @Test
    fun `generation identifiers pin immutable work request vectors`() {
        assertEquals(
            "40fe7461-9be1-3ce7-8bdf-28b48b76359e",
            reminderGenerationId(spec.occurrenceId, 0).toString(),
        )
        assertEquals(
            "590ca815-2783-322a-acde-39ab31dafd39",
            reminderGenerationId(spec.occurrenceId, 1).toString(),
        )
        assertNotEquals(
            reminderGenerationId(spec.occurrenceId, 0),
            reminderGenerationId(spec.occurrenceId, 1),
        )
        assertFailsWith<IllegalArgumentException> {
            reminderGenerationId(spec.occurrenceId, -1)
        }
    }

    @Test
    fun `route intent carries collision safe private route identity`() {
        val intent = reminderRouteIntentSpec(route, dueAt)
        val expectedIdentity = NotificationIdentity(intent.notificationTag, intent.notificationId)

        assertEquals("myinspection://schedule/reminder/${spec.occurrenceId}", intent.data)
        assertEquals(spec.occurrenceId, intent.notificationTag)
        assertEquals(0, intent.notificationId)
        assertEquals(-1055326466, intent.requestCode)
        assertEquals(route.propertyId, intent.propertyId)
        assertEquals(route.inspectionType.name, intent.inspectionType)
        assertTrue(intent.isExplicit)
        assertTrue(intent.isImmutable)
        assertEquals(expectedIdentity, reminderNotificationIdentity(intent))

        val other = reminderRouteIntentSpec(
            route.copy(inspectionType = InspectionScheduleType.ANNUAL),
            dueAt,
        )
        assertNotEquals(intent.data, other.data)
        assertNotEquals(intent.notificationTag, other.notificationTag)
    }

    @Test
    fun `notification copy has exact bilingual text for all schedule types`() {
        val expected = mapOf(
            InspectionScheduleType.ROUTINE to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Routine inspection / 定期巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.ANNUAL to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Annual home check / 年度住宅检查 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.INGOING to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Ingoing inspection / 入住巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
            InspectionScheduleType.EXIT to NotificationCopy(
                title = "Inspection reminder / 巡检提醒",
                body = "Exit inspection / 退租巡检 is due. Open MyInspection to review the property. / " +
                    "已到建议日期，请打开 MyInspection 查看物业。",
            ),
        )

        assertEquals(InspectionScheduleType.values().toSet(), expected.keys)
        expected.forEach { (type, copy) ->
            assertEquals(copy, scheduleNotificationCopy(type))
            assertTrue("property-a" !in "${copy.title} ${copy.body}")
        }
    }

    @Test
    fun `delivery plan declares an immutable private alert once policy`() {
        val notify = assertIs<DeliveryPlan.Notify>(plan(sdkInt = 32, permissionGranted = false))

        assertEquals(true, notify.onlyAlertOnce)
        assertEquals(NotificationVisibility.PRIVATE, notify.visibility)
        // PUBLIC exists so this assertion can fail. Its presence is what makes the
        // PRIVATE choice a constraint rather than a restatement of the only option.
        assertEquals(listOf("PRIVATE", "PUBLIC"), NotificationVisibility.entries.map { it.name })
        assertTrue(notify.intent.isExplicit)
        assertTrue(notify.intent.isImmutable)
        assertEquals(
            "myinspection://schedule/reminder/" +
                "c118fefec6ee20d89eafa5533048237237d39116af40aa85123fb1f70c404108",
            notify.intent.data,
        )
        assertEquals(-1055326466, notify.intent.requestCode)
    }

    @Test
    fun `permission only gates delivery from API 33 and only while it is withheld`() {
        assertIs<DeliveryPlan.Retry>(plan(sdkInt = 33, permissionGranted = false))
        // Granted on API 33+ must still notify. Without the permissionGranted term in the
        // condition this stays Retry forever and the user is never reminded.
        assertIs<DeliveryPlan.Notify>(plan(sdkInt = 33, permissionGranted = true))
        // Below API 33 there is no runtime permission, so neither value may gate delivery.
        assertIs<DeliveryPlan.Notify>(plan(sdkInt = 32, permissionGranted = false))
        assertIs<DeliveryPlan.Notify>(plan(sdkInt = 32, permissionGranted = true))
    }

    private fun plan(sdkInt: Int, permissionGranted: Boolean): DeliveryPlan =
        reminderDeliveryPlan(
            sdkInt = sdkInt,
            permissionGranted = permissionGranted,
            route = route,
            dueAt = dueAt,
        )

    @Test
    fun `request code truncation collides while route identity stays distinct`() {
        val shared = "c118fefe"
        val first = shared + "0".repeat(56)
        val second = shared + "f".repeat(56)

        assertEquals(-1055326466, reminderRequestCode(first))
        assertEquals(reminderRequestCode(first), reminderRequestCode(second))
        assertNotEquals(first, second)
        // Both rejects below keep a parseable 8-hex prefix, so only the shape guard can refuse
        // them. An input with a non-hex prefix would raise NumberFormatException, a subclass of
        // IllegalArgumentException, and would pass this assertion with the guard deleted.
        assertFailsWith<IllegalArgumentException> {
            reminderRequestCode(first.dropLast(1))
        }
        assertFailsWith<IllegalArgumentException> {
            reminderRequestCode(first.uppercase())
        }
    }

    @Test
    fun `route identity stays unique across many occurrences`() {
        val specs = (0 until 200).map { index ->
            reminderRouteIntentSpec(
                route.copy(propertyId = "property-$index"),
                dueAt.plusSeconds(index.toLong()),
            )
        }

        assertEquals(specs.size, specs.map { it.data }.toSet().size)
        assertEquals(specs.size, specs.map { it.notificationTag }.toSet().size)
        specs.forEach { intent ->
            assertTrue(intent.notificationTag.matches(Regex("[0-9a-f]{64}")))
            assertTrue(intent.data.endsWith(intent.notificationTag))
        }
    }

    @Test
    fun `generation identity rejects occurrence ids outside the canonical shape`() {
        val rejected = listOf(
            "",
            "not-hex",
            spec.occurrenceId.dropLast(1),
            spec.occurrenceId + "0",
            spec.occurrenceId.uppercase(),
            " " + spec.occurrenceId,
        )

        rejected.forEach { candidate ->
            assertFailsWith<IllegalArgumentException>("expected rejection of '$candidate'") {
                reminderGenerationId(candidate, 0)
            }
        }
    }

    @Test
    fun `occurrence identity rejects a blank property at its own entry point`() {
        assertFailsWith<IllegalArgumentException> {
            reminderOccurrenceId(route.copy(propertyId = "   "), dueAt)
        }
        assertFailsWith<IllegalArgumentException> {
            reminderOccurrenceId(route.copy(propertyId = ""), dueAt)
        }
    }

    @Test
    fun `pending reminder projects the same spec as the factory`() {
        assertEquals(spec, PendingReminder(route, dueAt).toSpec())
    }

    @Test
    fun `work data keys pin the exact wire names the delivery worker reads`() {
        val keys = listOf(
            ReminderWorkKeys.OCCURRENCE_ID,
            ReminderWorkKeys.PROPERTY_ID,
            ReminderWorkKeys.INSPECTION_TYPE,
            ReminderWorkKeys.DUE_AT,
            ReminderWorkKeys.GENERATION_NUMBER,
        )

        assertEquals(
            listOf(
                "reminder_occurrence_id",
                "reminder_property_id",
                "reminder_inspection_type",
                "reminder_due_at",
                "reminder_generation_number",
            ),
            keys,
        )
        assertEquals(keys.size, keys.toSet().size)
    }
}

/*
 * Semantic mutation receipts for ReminderContracts.kt (card A5).
 *
 * Each row was applied alone to the final snapshot, the two focused test classes were run
 * expecting a non-zero exit, and the file was restored and re-hashed. Every mutation was
 * killed and the file returned to the identical digest, so no row below is a survivor.
 *
 * SHA-256 before all mutations: cb40ba26d198ee651c76c5262b8389ecf573b5d1575c6f7fabe5f9d36b4c06b8
 * SHA-256 after all mutations:  cb40ba26d198ee651c76c5262b8389ecf573b5d1575c6f7fabe5f9d36b4c06b8
 *
 * M1 [A1] RED exit 1
 *   selector: append(dueAt.nano)
 *   effect: nano dropped from canonical identity: two instants 1ns apart collide
 * M2 [A1] RED exit 1
 *   selector: append(dueAt.epochSecond)
 *   effect: seconds dropped from canonical identity: every due time collides
 * M3 [A1] RED exit 1
 *   selector: require(route.propertyId.isNotBlank()) { "propertyId must not be blank" } val canonical
 *   effect: blank property accepted, minting an identity for a property that does not exist
 * M4 [A2] RED exit 1
 *   selector: require(generationNumber >= 0) { "generationNumber must be non-negative" }
 *   effect: negative generations accepted, minting work ids for impossible generations
 * M5 [A2] RED exit 1
 *   selector: fun reminderGenerationId(occurrenceId: String, generationNumber: Long): UUID {
 *     require(occurrenceId.matches(OC
 *   effect: arbitrary strings accepted as occurrence ids when deriving work request ids
 * M6 [A3] RED exit 1
 *   selector: notificationTag = occurrenceId,
 *   effect: notification tag truncated to 32 bits: distinct occurrences replace each other
 * M7 [A3] RED exit 1
 *   selector: isImmutable = true,
 *   effect: PendingIntent no longer declared immutable, allowing intent field injection
 * M8 [A3] RED exit 1
 *   selector: body = "$label is due. Open MyInspection to review the property. / " +
 *   effect: notification body copy silently altered away from the agreed bilingual text
 * M9 [A3] RED exit 1
 *   selector: const val DUE_AT = "reminder_due_at"
 *   effect: work data key renamed: downstream ReminderWorker reads a key that is never written
 * M10 [A3] RED exit 1
 *   selector: internal fun reminderRequestCode(occurrenceId: String): Int {
 *     require(occurrenceId.matches(OCCURRENCE_ID_PATTE
 *   effect: request code derived from an unvalidated string, so a short id throws NumberFormatException
 * M16 [A3] RED exit 1
 *   selector: ): DeliveryPlan = if (sdkInt >= 33 && !permissionGranted) {
 *   effect: granting notification permission still yields Retry: the user is never reminded
 * M17 [A3] RED exit 1
 *   selector: val visibility: NotificationVisibility = NotificationVisibility.PRIVATE,
 *   effect: reminder body naming the tenant's property is shown on the lock screen
 */
