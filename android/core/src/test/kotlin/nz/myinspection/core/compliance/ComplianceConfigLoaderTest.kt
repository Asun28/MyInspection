package nz.myinspection.core.compliance

import nz.myinspection.core.compliance.ComplianceTestFixtures.TENANCY_REF
import nz.myinspection.core.compliance.ComplianceTestFixtures.configJson
import nz.myinspection.core.compliance.ComplianceTestFixtures.sha256Hex
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The documents this class hands the loader are the ones it must accept; refusals live in [ComplianceConfigRejectionTest]. */
class ComplianceConfigLoaderTest {
    @Test
    fun `valid built-in bytes load the inspection rule without hidden defaults`() {
        val result = ComplianceConfigLoader.load(configJson().encodeToByteArray())

        assertEquals(ComplianceConfigSource.BUILT_IN, result.source)
        assertEquals(null, result.overrideRejection)
        assertEquals(1, result.config.schemaVersion)
        assertEquals("2025-12-01", result.config.effectiveDate.toString())
        assertEquals("Pacific/Auckland", result.config.timezone.id)
        assertEquals(
            listOf(
                "https://www.legislation.govt.nz/act/public/1986/120/en/latest/sections/DLM95504/",
                "https://www.tenancy.govt.nz/maintenance-and-inspections/inspections/",
            ),
            result.config.sourceRefs,
        )
        assertEquals(
            EntryPurposeRule(
                noticeMinHours = 48,
                noticeMaxDays = 14,
                visitWindow = VisitWindow(
                    start = LocalTime.of(8, 0),
                    end = LocalTime.of(19, 0),
                    boardingHouseEnd = LocalTime.of(18, 0),
                ),
                frequencyLimit = FrequencyLimit(days = 28, exemptTypes = listOf("INGOING", "EXIT")),
            ),
            result.config.rules.getValue("inspection"),
        )
    }

    @Test
    fun `override applies only when its raw-byte digest and schema version both match`() {
        val builtIn = configJson(noticeMinHours = 48).encodeToByteArray()
        val override = configJson(noticeMinHours = 72).encodeToByteArray()

        val accepted = ComplianceConfigLoader.load(
            builtInBytes = builtIn,
            override = ComplianceOverride(override, sha256Hex(override)),
        )
        assertEquals(ComplianceConfigSource.OVERRIDE, accepted.source)
        assertEquals(72, accepted.config.rules.getValue("inspection").noticeMinHours)

        val badDigest = ComplianceConfigLoader.load(
            builtInBytes = builtIn,
            override = ComplianceOverride(override, "0".repeat(64)),
        )
        assertEquals(ComplianceConfigSource.BUILT_IN, badDigest.source)
        assertEquals(OverrideRejection.CHECKSUM_MISMATCH, badDigest.overrideRejection)
        assertEquals(48, badDigest.config.rules.getValue("inspection").noticeMinHours)

        val wrongSchema = configJson(schemaVersion = 2, noticeMinHours = 72).encodeToByteArray()
        val badSchema = ComplianceConfigLoader.load(
            builtInBytes = builtIn,
            override = ComplianceOverride(wrongSchema, sha256Hex(wrongSchema)),
        )
        assertEquals(ComplianceConfigSource.BUILT_IN, badSchema.source)
        assertEquals(OverrideRejection.SCHEMA_VERSION_MISMATCH, badSchema.overrideRejection)
        assertEquals(48, badSchema.config.rules.getValue("inspection").noticeMinHours)
    }

    @Test
    fun `override snapshots caller bytes before later mutation`() {
        val builtIn = configJson(noticeMinHours = 48).encodeToByteArray()
        val source = configJson(noticeMinHours = 72).encodeToByteArray()
        val signed = ComplianceOverride(source, sha256Hex(source))

        source.fill('x'.code.toByte())
        val accepted = ComplianceConfigLoader.load(builtIn, signed)

        assertEquals(ComplianceConfigSource.OVERRIDE, accepted.source)
        assertEquals(null, accepted.overrideRejection)
        assertEquals(72, accepted.config.rules.getValue("inspection").noticeMinHours)
    }

    @Test
    fun `newer-schema override is classified before strict v1 shape decoding`() {
        val builtIn = configJson(noticeMinHours = 48).encodeToByteArray()
        val structurallyV2 = """{"schemaVersion":2,"v2Only":{"replacement":"shape"}}""".encodeToByteArray()

        val result = ComplianceConfigLoader.load(
            builtIn,
            ComplianceOverride(structurallyV2, sha256Hex(structurallyV2)),
        )

        assertEquals(ComplianceConfigSource.BUILT_IN, result.source)
        assertEquals(OverrideRejection.SCHEMA_VERSION_MISMATCH, result.overrideRejection)
        assertEquals(48, result.config.rules.getValue("inspection").noticeMinHours)
    }

    @Test
    fun `invalid civil windows and source URLs fail closed instead of widening the rule`() {
        val invalidCases = listOf(
            configJson().replace("\"start\": \"08:00\"", "\"start\": \"08:00:30\""),
            configJson().replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"20:00\""),
            configJson().replace(TENANCY_REF, "https://"),
        )

        invalidCases.forEach { invalid ->
            assertFailsWith<ComplianceConfigException> {
                ComplianceConfigLoader.load(invalid.encodeToByteArray())
            }
        }
    }

    @Test
    fun `v1 requires Pacific Auckland and a checksum-valid timezone override falls back`() {
        val builtIn = configJson().encodeToByteArray()
        val utc = configJson().replace("Pacific/Auckland", "UTC").encodeToByteArray()

        assertFailsWith<ComplianceConfigException> {
            ComplianceConfigLoader.load(utc)
        }

        val overrideResult = ComplianceConfigLoader.load(
            builtInBytes = builtIn,
            override = ComplianceOverride(utc, sha256Hex(utc)),
        )
        assertEquals(ComplianceConfigSource.BUILT_IN, overrideResult.source)
        assertEquals(OverrideRejection.INVALID_CONFIG, overrideResult.overrideRejection)
        assertEquals("Pacific/Auckland", overrideResult.config.timezone.id)
    }

    @Test
    fun `digest-valid malformed and semantically invalid overrides are rejected observably`() {
        val builtIn = configJson().encodeToByteArray()
        val malformed = "{".encodeToByteArray()
        val invalidRule = configJson(noticeMinHours = 0).encodeToByteArray()

        listOf(malformed, invalidRule).forEach { invalidOverride ->
            val result = ComplianceConfigLoader.load(
                builtInBytes = builtIn,
                override = ComplianceOverride(invalidOverride, sha256Hex(invalidOverride)),
            )
            assertEquals(ComplianceConfigSource.BUILT_IN, result.source)
            assertEquals(OverrideRejection.INVALID_CONFIG, result.overrideRejection)
            assertEquals(48, result.config.rules.getValue("inspection").noticeMinHours)
        }
    }
}
