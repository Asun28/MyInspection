package nz.myinspection.core.compliance

import java.security.MessageDigest
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComplianceConfigLoaderTest {
    @Test
    fun `valid built-in bytes load the inspection rule without hidden defaults`() {
        val result = ComplianceConfigLoader.load(validConfig().encodeToByteArray())

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
                frequencyLimit = FrequencyLimit(days = 28, exemptTypes = listOf("INGOING", "EXIT", "ANNUAL")),
            ),
            result.config.rules.getValue("inspection"),
        )
    }

    @Test
    fun `override applies only when its raw-byte digest and schema version both match`() {
        val builtIn = validConfig(noticeMinHours = 48).encodeToByteArray()
        val override = validConfig(noticeMinHours = 72).encodeToByteArray()

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

        val wrongSchema = validConfig(schemaVersion = 2, noticeMinHours = 72).encodeToByteArray()
        val badSchema = ComplianceConfigLoader.load(
            builtInBytes = builtIn,
            override = ComplianceOverride(wrongSchema, sha256Hex(wrongSchema)),
        )
        assertEquals(ComplianceConfigSource.BUILT_IN, badSchema.source)
        assertEquals(OverrideRejection.SCHEMA_VERSION_MISMATCH, badSchema.overrideRejection)
        assertEquals(48, badSchema.config.rules.getValue("inspection").noticeMinHours)
    }

    @Test
    fun `invalid civil windows and source URLs fail closed instead of widening the rule`() {
        val invalidCases = listOf(
            validConfig().replace("\"start\": \"08:00\"", "\"start\": \"08:00:30\""),
            validConfig().replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"20:00\""),
            validConfig().replace(
                "https://www.tenancy.govt.nz/maintenance-and-inspections/inspections/",
                "https://",
            ),
        )

        invalidCases.forEach { invalid ->
            assertFailsWith<ComplianceConfigException> {
                ComplianceConfigLoader.load(invalid.encodeToByteArray())
            }
        }
    }

    private fun validConfig(
        schemaVersion: Int = 1,
        noticeMinHours: Int = 48,
    ): String =
        """
        {
          "schemaVersion": $schemaVersion,
          "effectiveDate": "2025-12-01",
          "sourceRefs": [
            "https://www.legislation.govt.nz/act/public/1986/120/en/latest/sections/DLM95504/",
            "https://www.tenancy.govt.nz/maintenance-and-inspections/inspections/"
          ],
          "timezone": "Pacific/Auckland",
          "rules": {
            "inspection": {
              "noticeMinHours": $noticeMinHours,
              "noticeMaxDays": 14,
              "visitWindow": {
                "start": "08:00",
                "end": "19:00",
                "boardingHouseEnd": "18:00"
              },
              "frequencyLimit": {
                "days": 28,
                "exemptTypes": ["INGOING", "EXIT", "ANNUAL"]
              }
            }
          }
        }
        """.trimIndent()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
