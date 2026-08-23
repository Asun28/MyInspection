package nz.myinspection.core.compliance

import nz.myinspection.core.compliance.ComplianceTestFixtures.RULES_FILE
import nz.myinspection.core.compliance.ComplianceTestFixtures.configJson
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ComplianceEngineTest {
    private val zone = ZoneId.of("Pacific/Auckland")

    @Test
    fun `notice and ordinary-rental visit boundaries are inclusive and each outside edge blocks`() {
        val engine = engine()
        val atOpening = atNz("2026-08-10T08:00")
        val atClosing = atNz("2026-08-10T19:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofHours(48)))),
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(atClosing, atClosing.minus(Duration.ofDays(14)))),
        )
        assertBlocked(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofHours(48)).plusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        assertBlocked(
            engine.validateSchedule(request(atOpening, atOpening.minus(Duration.ofDays(14)).minusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_EARLY,
        )
        assertBlocked(
            engine.validateSchedule(request(atNz("2026-08-10T07:59:59"), atNz("2026-08-08T07:59:59"))),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        assertBlocked(
            engine.validateSchedule(request(atNz("2026-08-10T19:00:00.001"), atNz("2026-08-08T19:00:00.001"))),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
    }

    @Test
    fun `boarding-house closing time is 18h while the signed product notice floor remains 48h`() {
        val engine = engine()
        val closing = atNz("2026-08-10T18:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(closing, closing.minus(Duration.ofHours(48)), boardingHouse = true)),
        )
        assertBlocked(
            engine.validateSchedule(
                request(atNz("2026-08-10T18:00:00.001"), atNz("2026-08-08T18:00:00.001"), boardingHouse = true),
            ),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        assertBlocked(
            engine.validateSchedule(request(closing, closing.minus(Duration.ofHours(24)), boardingHouse = true)),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
    }

    @Test
    fun `same-property routine inside four civil weeks blocks even with consent, while exact boundary passes`() {
        val engine = engine()
        val previous = ExistingScheduledEntry(
            entryId = "entry-previous",
            propertyId = "property-a",
            entryPurpose = "inspection",
            inspectionType = "ROUTINE",
            scheduledAt = atNz("2026-08-01T10:00"),
        )

        assertBlocked(
            engine.validateSchedule(
                request(
                    scheduledAt = atNz("2026-08-15T10:00"),
                    noticeGivenAt = atNz("2026-08-13T10:00"),
                    existingEntries = listOf(previous),
                    tenantConsented = true,
                ),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(
                    scheduledAt = atNz("2026-08-29T10:00"),
                    noticeGivenAt = atNz("2026-08-27T10:00"),
                    existingEntries = listOf(previous),
                ),
            ),
        )
    }

    @Test
    fun `frequency considers only same-property non-exempt inspection entries`() {
        val scheduled = atNz("2026-08-15T10:00")
        val notice = scheduled.minus(Duration.ofHours(48))
        val near = atNz("2026-08-01T10:00")
        val irrelevant = listOf(
            ExistingScheduledEntry("e-other-property", "property-b", "inspection", "ROUTINE", near),
            ExistingScheduledEntry("e-ingoing", "property-a", "inspection", "INGOING", near),
            ExistingScheduledEntry("e-exit", "property-a", "inspection", "EXIT", near),
            ExistingScheduledEntry("e-annual", "property-a", "inspection", "ANNUAL", near),
        )

        assertIs<ScheduleValidation.Pass>(
            engine().validateSchedule(request(scheduled, notice, existingEntries = irrelevant)),
        )
        listOf("INGOING", "EXIT", "ANNUAL").forEach { type ->
            assertIs<ScheduleValidation.Pass>(
                engine().validateSchedule(request(scheduled, notice, inspectionType = type, existingEntries = listOf(
                    ExistingScheduledEntry("e-routine", "property-a", "inspection", "ROUTINE", near),
                ))),
            )
        }
    }

    @Test
    fun `notice uses elapsed time across both NZ DST transitions while frequency uses civil days`() {
        val engine = engine()
        val springSchedule = atNz("2026-09-28T08:00")
        val springWallClock48h = atNz("2026-09-26T08:00")
        assertEquals(Duration.ofHours(47), Duration.between(springWallClock48h, springSchedule))
        assertBlocked(
            engine.validateSchedule(request(springSchedule, springWallClock48h)),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(springSchedule, springSchedule.minus(Duration.ofHours(48)))),
        )

        val autumnSchedule = atNz("2026-04-06T08:00")
        val autumnWallClock48h = atNz("2026-04-04T08:00")
        assertEquals(Duration.ofHours(49), Duration.between(autumnWallClock48h, autumnSchedule))
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(autumnSchedule, autumnWallClock48h)),
        )

        val previous = ExistingScheduledEntry(
            "e-dst-previous", "property-a", "inspection", "ROUTINE", atNz("2026-09-01T10:00"),
        )
        assertEquals(
            Duration.ofHours(671),
            Duration.between(previous.scheduledAt, atNz("2026-09-29T10:00")),
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(
                    atNz("2026-09-29T10:00"),
                    atNz("2026-09-27T10:00"),
                    existingEntries = listOf(previous),
                ),
            ),
        )
    }

    @Test
    fun `entry-purpose rules are data driven and unknown purposes fail closed`() {
        val config = ComplianceConfigLoader.load(configJson(alternatePurpose = true).encodeToByteArray()).config
        val engine = ComplianceEngine(config)
        val scheduled = atNz("2026-08-10T10:00")

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(72)), entryPurpose = "fixture-purpose"),
            ),
        )
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), entryPurpose = "not-configured"),
            ),
            ComplianceReasonKey.UNKNOWN_ENTRY_PURPOSE,
        )
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), inspectionType = "ROUTIEN"),
            ),
            ComplianceReasonKey.UNKNOWN_INSPECTION_TYPE,
        )
    }

    @Test
    fun `blank property and malformed same-purpose history fail closed with stable reasons`() {
        val scheduled = atNz("2026-08-15T10:00")
        val notice = scheduled.minus(Duration.ofHours(48))

        assertBlocked(
            engine().validateSchedule(request(scheduled, notice, propertyId = "")),
            ComplianceReasonKey.INVALID_PROPERTY_ID,
        )
        assertBlocked(
            engine().validateSchedule(
                request(
                    scheduled,
                    notice,
                    existingEntries = listOf(
                        ExistingScheduledEntry("e-bad-type", "property-a", "inspection", "ROUTIEN", atNz("2026-08-01T10:00")),
                    ),
                ),
            ),
            ComplianceReasonKey.INVALID_HISTORY_ENTRY,
        )
    }

    /**
     * A purpose the config *does* know is genuinely someone else's quota: its own rule carries its own
     * frequency limit, so it must not consume the inspection one.
     */
    @Test
    fun `history belonging to another configured entry purpose cannot trigger the inspection frequency limit`() {
        val engine = ComplianceEngine(
            ComplianceConfigLoader.load(configJson(alternatePurpose = true).encodeToByteArray()).config,
        )
        val scheduled = atNz("2026-08-15T10:00")
        val other = ExistingScheduledEntry(
            "e-other-purpose", "property-a", "fixture-purpose", "ROUTINE", atNz("2026-08-01T10:00"),
        )

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), existingEntries = listOf(other)),
            ),
        )
    }

    /**
     * A purpose the config does *not* know is not someone else's quota, it is a broken row: the rule that would
     * govern it cannot be looked up. Skipping it silently drops a genuine competitor from the comparison — the
     * config validator itself rejects "Inspection", so a row carrying it is corruption, not a different visit.
     */
    @Test
    fun `history carrying an unknown entry purpose fails closed instead of vanishing from the comparison`() {
        val scheduled = atNz("2026-08-15T10:00")
        val unknown = ExistingScheduledEntry(
            "e-unknown-purpose", "property-a", "Inspection", "ROUTINE", atNz("2026-08-12T10:00"),
        )

        assertBlocked(
            engine().validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), existingEntries = listOf(unknown)),
            ),
            ComplianceReasonKey.INVALID_HISTORY_ENTRY,
        )
    }

    @Test
    fun `repository rule file loads and drives the signed inspection behavior`() {
        val bytes = Files.readAllBytes(findRepositoryFile(RULES_FILE))
        val loaded = ComplianceConfigLoader.load(bytes)
        val engine = ComplianceEngine(loaded.config)
        val scheduled = atNz("2026-08-15T10:00")

        assertEquals(ComplianceConfigSource.BUILT_IN, loaded.source)
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(48)))),
        )
        assertBlocked(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(47)))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
    }


    /**
     * The suite used to configure only the signed defaults, so an engine that ignored the config entirely and
     * hard-coded 48/14/08:00/19:00/18:00/28 stayed green. Every case here is chosen to sit on the *other* side
     * of the corresponding default: each assertion fails if the constant, rather than the config, is consulted.
     */
    @Test
    fun `every threshold is driven by config, proven by values that straddle the signed defaults`() {
        val strict = ComplianceEngine(
            ComplianceConfigLoader.load(
                configJson(
                    noticeMinHours = 72,
                    noticeMaxDays = 7,
                    windowStart = "09:00",
                    windowEnd = "17:00",
                    boardingHouseEnd = "16:00",
                    frequencyDays = 42,
                ).encodeToByteArray(),
            ).config,
        )
        val scheduled = atNz("2026-08-12T10:00")

        // 48h clears the default floor but not a configured 72h floor.
        assertBlocked(
            strict.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(48)))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        assertIs<ScheduleValidation.Pass>(
            strict.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(72)))),
        )
        // 10 days is inside the default 14-day ceiling but beyond a configured 7-day ceiling.
        assertBlocked(
            strict.validateSchedule(request(scheduled, scheduled.minus(Duration.ofDays(10)))),
            ComplianceReasonKey.NOTICE_TOO_EARLY,
        )
        // 08:30 and 18:00 are inside the default ordinary window but outside a configured 09:00-17:00 one.
        listOf("2026-08-12T08:30", "2026-08-12T18:00").forEach { local ->
            val at = atNz(local)
            assertBlocked(
                strict.validateSchedule(request(at, at.minus(Duration.ofHours(72)))),
                ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
            )
        }
        // 17:00 is the configured ordinary close but past the configured boarding-house close of 16:00.
        val atClose = atNz("2026-08-12T17:00")
        assertIs<ScheduleValidation.Pass>(
            strict.validateSchedule(request(atClose, atClose.minus(Duration.ofHours(72)))),
        )
        assertBlocked(
            strict.validateSchedule(
                request(atClose, atClose.minus(Duration.ofHours(72)), boardingHouse = true),
            ),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        // 30 civil days clears the default 28-day limit but not a configured 42-day one.
        assertBlocked(
            strict.validateSchedule(
                request(
                    scheduled,
                    scheduled.minus(Duration.ofHours(72)),
                    existingEntries = listOf(
                        ExistingScheduledEntry("e-30d", "property-a", "inspection", "ROUTINE", atNz("2026-07-13T10:00")),
                    ),
                ),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
    }

    /**
     * `exemptTypes` is read twice — once for the requested type, once for each stored one — and the suite only
     * ever configured the signed INGOING/EXIT/ANNUAL list, so either read could be a hard-coded set and stay
     * green. A config that exempts ROUTINE *instead* inverts both: the same fixtures now demand the opposite
     * verdicts, so a literal set fails whichever side it is written on.
     */
    @Test
    fun `exempt inspection types come from config on both the requested and the stored side`() {
        val engine = ComplianceEngine(
            ComplianceConfigLoader.load(configJson(exemptTypes = """["ROUTINE"]""").encodeToByteArray()).config,
        )
        val scheduled = atNz("2026-08-19T10:00")
        val notice = scheduled.minus(Duration.ofHours(48))
        val twentySevenDaysEarlier = atNz("2026-07-23T10:00")
        val routine = ExistingScheduledEntry("e-routine", "property-a", "inspection", "ROUTINE", twentySevenDaysEarlier)
        val ingoing = ExistingScheduledEntry("e-ingoing", "property-a", "inspection", "INGOING", twentySevenDaysEarlier)

        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(scheduled, notice, existingEntries = listOf(routine))),
        )
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, notice, inspectionType = "INGOING", existingEntries = listOf(ingoing)),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
    }

    /**
     * The civil timezone is config data too. 2026-08-19T20:30Z is 20:30 in UTC — past any closing time — and
     * 08:30 the next morning in Pacific/Auckland, so one Instant reaches opposite verdicts under two configs
     * that differ in nothing else. The UTC config is built directly because the loader pins v1 to
     * Pacific/Auckland, and that validator must not be weakened to serve a test.
     */
    @Test
    fun `the civil timezone that decides visit hours comes from config`() {
        val auckland = ComplianceConfigLoader.load(configJson().encodeToByteArray()).config
        val utc = ComplianceConfig(
            schemaVersion = auckland.schemaVersion,
            effectiveDate = auckland.effectiveDate,
            sourceRefs = auckland.sourceRefs,
            timezone = ZoneId.of("UTC"),
            rules = auckland.rules,
        )
        val scheduled = Instant.parse("2026-08-19T20:30:00Z")
        val notice = scheduled.minus(Duration.ofHours(48))

        assertIs<ScheduleValidation.Pass>(
            ComplianceEngine(auckland).validateSchedule(request(scheduled, notice)),
        )
        assertBlocked(
            ComplianceEngine(utc).validateSchedule(request(scheduled, notice)),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
    }

    /**
     * A row cannot be competition for itself. Before [ScheduleRequest.currentEntryId] existed, handing the
     * engine the real history while moving one of its rows made that row block its own move, so the only way
     * to reschedule anything was for every caller to pre-filter — an undocumented contract nothing enforced.
     */
    @Test
    fun `rescheduling excludes the row under edit but a genuine competing row still blocks`() {
        val engine = engine()
        val underEdit = ExistingScheduledEntry(
            "entry-under-edit", "property-a", "inspection", "ROUTINE", atNz("2026-08-10T10:00"),
        )
        val competitor = ExistingScheduledEntry(
            "entry-competitor", "property-a", "inspection", "ROUTINE", atNz("2026-08-12T10:00"),
        )
        val moveTo = atNz("2026-08-14T10:00")
        val notice = moveTo.minus(Duration.ofHours(48))

        // Same history, same target date: the only difference is whether the row under edit is named.
        assertBlocked(
            engine.validateSchedule(request(moveTo, notice, existingEntries = listOf(underEdit))),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(moveTo, notice, existingEntries = listOf(underEdit), currentEntryId = underEdit.entryId),
            ),
        )
        // Excluding self must not excuse a different row that genuinely conflicts.
        assertBlocked(
            engine.validateSchedule(
                request(
                    moveTo,
                    notice,
                    existingEntries = listOf(underEdit, competitor),
                    currentEntryId = underEdit.entryId,
                ),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
    }

    /**
     * Exclusion by id is only sound while ids are identities. `entryId` is free text the caller supplies, and a
     * not-yet-persisted row carries "", while a buggy mapper repeats one id across rows — either way a single
     * `currentEntryId` silently excludes *several* competing rows and the four-week cap stops existing. The
     * gate must refuse the history rather than approve a schedule it cannot reason about.
     */
    @Test
    fun `ambiguous reschedule identity fails closed instead of excusing several competing rows`() {
        val engine = engine()
        val moveTo = atNz("2026-08-14T10:00")
        val notice = moveTo.minus(Duration.ofHours(48))
        fun routine(id: String, at: String) =
            ExistingScheduledEntry(id, "property-a", "inspection", "ROUTINE", atNz(at))
        fun verdict(rows: List<ExistingScheduledEntry>, current: String? = null) =
            engine.validateSchedule(request(moveTo, notice, existingEntries = rows, currentEntryId = current))

        // Two unpersisted rows, both matched by currentEntryId "": a third Routine inside four days used to pass.
        val unpersisted = listOf(routine("", "2026-08-10T10:00"), routine("", "2026-08-12T10:00"))
        assertBlocked(verdict(unpersisted, current = ""), ComplianceReasonKey.INVALID_HISTORY_ENTRY)
        // A single blank id is already unusable, with nothing named for it to be confused with.
        assertBlocked(verdict(unpersisted.take(1)), ComplianceReasonKey.INVALID_HISTORY_ENTRY)
        // Non-blank but repeated: naming that id later would excuse both rows at once.
        val repeated = listOf(routine("entry-dup", "2026-08-10T10:00"), routine("entry-dup", "2026-08-12T10:00"))
        assertBlocked(verdict(repeated), ComplianceReasonKey.INVALID_HISTORY_ENTRY)
        // Naming a row that is not in the history is not a reschedule; accepting it as a new visit would let a
        // caller drop the row under edit from the list and reuse its date.
        val single = listOf(routine("entry-under-edit", "2026-08-10T10:00"))
        assertBlocked(verdict(single, current = "entry-absent"), ComplianceReasonKey.INVALID_HISTORY_ENTRY)
    }

    /**
     * The authoritative file previously had only its notice floor exercised. Every rule it declares gets a
     * pass and a fail here, so silently editing any published value breaks a test rather than shipping.
     */
    @Test
    fun `every rule in the authoritative repository file has a passing and a failing case`() {
        val bytes = Files.readAllBytes(findRepositoryFile(RULES_FILE))
        val engine = ComplianceEngine(ComplianceConfigLoader.load(bytes).config)
        val scheduled = atNz("2026-08-19T10:00")

        // noticeMinHours = 48
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(48)))),
        )
        assertBlocked(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofHours(48)).plusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_SHORT,
        )
        // noticeMaxDays = 14
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofDays(14)))),
        )
        assertBlocked(
            engine.validateSchedule(request(scheduled, scheduled.minus(Duration.ofDays(14)).minusMillis(1))),
            ComplianceReasonKey.NOTICE_TOO_EARLY,
        )
        // visitWindow 08:00-19:00 ordinary
        listOf("2026-08-19T08:00" to true, "2026-08-19T19:00" to true, "2026-08-19T07:59:59" to false, "2026-08-19T19:00:00.001" to false)
            .forEach { (local, expectPass) ->
                val at = atNz(local)
                val result = engine.validateSchedule(request(at, at.minus(Duration.ofHours(48))))
                if (expectPass) assertIs<ScheduleValidation.Pass>(result)
                else assertBlocked(result, ComplianceReasonKey.OUTSIDE_VISIT_WINDOW)
            }
        // visitWindow boardingHouseEnd = 18:00
        val boardingClose = atNz("2026-08-19T18:00")
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(boardingClose, boardingClose.minus(Duration.ofHours(48)), boardingHouse = true),
            ),
        )
        val pastBoardingClose = atNz("2026-08-19T18:00:00.001")
        assertBlocked(
            engine.validateSchedule(
                request(pastBoardingClose, pastBoardingClose.minus(Duration.ofHours(48)), boardingHouse = true),
            ),
            ComplianceReasonKey.OUTSIDE_VISIT_WINDOW,
        )
        // frequencyLimit days = 28, exemptTypes = INGOING/EXIT/ANNUAL
        assertBlocked(
            engine.validateSchedule(
                request(
                    scheduled,
                    scheduled.minus(Duration.ofHours(48)),
                    existingEntries = listOf(
                        ExistingScheduledEntry("e-27d", "property-a", "inspection", "ROUTINE", atNz("2026-07-24T10:00")),
                    ),
                ),
            ),
            ComplianceReasonKey.FREQUENCY_LIMIT,
        )
        assertIs<ScheduleValidation.Pass>(
            engine.validateSchedule(
                request(
                    scheduled,
                    scheduled.minus(Duration.ofHours(48)),
                    existingEntries = listOf(
                        ExistingScheduledEntry("e-28d", "property-a", "inspection", "ROUTINE", atNz("2026-07-22T10:00")),
                    ),
                ),
            ),
        )
        listOf("INGOING", "EXIT", "ANNUAL").forEach { exempt ->
            assertIs<ScheduleValidation.Pass>(
                engine.validateSchedule(
                    request(
                        scheduled,
                        scheduled.minus(Duration.ofHours(48)),
                        existingEntries = listOf(
                            ExistingScheduledEntry("e-$exempt", "property-a", "inspection", exempt, atNz("2026-08-18T10:00")),
                        ),
                    ),
                ),
            )
        }
        // entry purposes: only "inspection" is published, anything else fails closed.
        assertBlocked(
            engine.validateSchedule(
                request(scheduled, scheduled.minus(Duration.ofHours(48)), entryPurpose = "maintenance"),
            ),
            ComplianceReasonKey.UNKNOWN_ENTRY_PURPOSE,
        )
    }

    /**
     * The published exemption list names ANNUAL next to INGOING/EXIT although the requirements name only the
     * latter two. That is deliberate: ANNUAL is the owner-occupied yearly check (自住房年检), not an entry into
     * a tenancy, so the RTA's four-week ceiling on repeat inspections has nothing to apply to. It is recorded
     * here because the rule schema carries no prose field to record it in.
     */
    @Test
    fun `ANNUAL is exempt because an owner-occupied annual check is not a tenancy entry`() {
        val loaded = ComplianceConfigLoader.load(Files.readAllBytes(findRepositoryFile(RULES_FILE)))
        val scheduled = atNz("2026-08-19T10:00")
        val yesterday = ExistingScheduledEntry(
            "e-annual-yesterday", "property-a", "inspection", "ANNUAL", atNz("2026-08-18T10:00"),
        )

        assertEquals(
            listOf("INGOING", "EXIT", "ANNUAL"),
            loaded.config.rules.getValue("inspection").frequencyLimit.exemptTypes,
        )
        assertIs<ScheduleValidation.Pass>(
            ComplianceEngine(loaded.config).validateSchedule(
                request(
                    scheduled,
                    scheduled.minus(Duration.ofHours(48)),
                    inspectionType = "ANNUAL",
                    existingEntries = listOf(yesterday),
                ),
            ),
        )
    }

    /** Blocked reasons are handed out as an unmodifiable view; callers must not be able to edit a verdict. */
    @Test
    fun `blocked reasons reject mutation through a cast`() {
        val blocked = assertIs<ScheduleValidation.Blocked>(
            engine().validateSchedule(request(atNz("2026-08-10T10:00"), atNz("2026-08-10T09:00"))),
        )
        val before = blocked.reasons.toList()
        @Suppress("UNCHECKED_CAST")
        val mutable = blocked.reasons as MutableList<ComplianceReason>
        assertFailsWith<UnsupportedOperationException> { mutable.clear() }
        assertFailsWith<UnsupportedOperationException> {
            mutable.add(ComplianceReason(ComplianceReasonKey.FREQUENCY_LIMIT))
        }
        assertEquals(before, blocked.reasons)
    }

    private fun engine(): ComplianceEngine =
        ComplianceEngine(ComplianceConfigLoader.load(configJson().encodeToByteArray()).config)

    private fun request(
        scheduledAt: Instant,
        noticeGivenAt: Instant,
        propertyId: String = "property-a",
        entryPurpose: String = "inspection",
        inspectionType: String = "ROUTINE",
        boardingHouse: Boolean = false,
        tenantConsented: Boolean = false,
        existingEntries: List<ExistingScheduledEntry> = emptyList(),
        currentEntryId: String? = null,
    ) = ScheduleRequest(
        propertyId = propertyId,
        entryPurpose = entryPurpose,
        inspectionType = inspectionType,
        isBoardingHouse = boardingHouse,
        scheduledAt = scheduledAt,
        noticeGivenAt = noticeGivenAt,
        tenantConsented = tenantConsented,
        existingEntries = existingEntries,
        currentEntryId = currentEntryId,
    )

    private fun assertBlocked(result: ScheduleValidation, vararg keys: ComplianceReasonKey) {
        val blocked = assertIs<ScheduleValidation.Blocked>(result)
        assertEquals(keys.toList(), blocked.reasons.map { it.key })
    }

    private fun atNz(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun findRepositoryFile(relative: String): Path {
        var cursor: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (cursor != null) {
            val candidate = cursor.resolve(relative)
            if (Files.isRegularFile(candidate)) return candidate
            cursor = cursor.parent
        }
        error("repository file not found: $relative")
    }
}
