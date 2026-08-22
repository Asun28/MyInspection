package nz.myinspection.core.compliance

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Negative coverage for the loader's fail-closed branches, and proof that the frozen collections it hands out
 * really are frozen.
 *
 * Both classes of guard were previously unfalsifiable: deleting a validation branch, or the
 * `Collections.unmodifiable*` wrapper around a returned collection, left the whole suite green. A guard no test
 * can turn red is indistinguishable from an absent guard the next time someone "simplifies" it.
 *
 * Each rejection case is expressed as a minimal edit to one known-good document, so the case names the single
 * property under test rather than re-stating a whole config.
 */
class ComplianceConfigRejectionTest {

    // --- fail-closed: an invalid *built-in* config must throw, never silently degrade ---

    @Test
    fun `every named validation branch rejects the built-in config it applies to`() {
        rejectionCases().forEach { (label, mutate) ->
            val bytes = mutate(validConfig()).encodeToByteArray()
            val failure = assertFailsWith<ComplianceConfigException>(
                message = "expected the built-in config to be rejected for: $label",
            ) { ComplianceConfigLoader.load(bytes) }
            assertTrue(
                failure.errors.isNotEmpty(),
                "rejection for '$label' produced no diagnostic; a fail-closed branch must say what it refused",
            )
        }
    }

    /**
     * The same malformed documents, supplied as checksum-valid *overrides*, must be observably rejected and fall
     * back to the built-in rules rather than throwing. Built-in and override paths share one validator but differ
     * in failure policy, and only exercising one of them leaves the other free to drift.
     */
    @Test
    fun `the same invalid documents are rejected as overrides and fall back to built-in`() {
        rejectionCases().forEach { (label, mutate) ->
            val overrideBytes = mutate(validConfig()).encodeToByteArray()
            val loaded = ComplianceConfigLoader.load(
                validConfig().encodeToByteArray(),
                ComplianceOverride(overrideBytes, sha256Hex(overrideBytes)),
            )
            assertEquals(
                ComplianceConfigSource.BUILT_IN,
                loaded.source,
                "override '$label' was accepted; invalid overrides must fall back",
            )
            assertTrue(
                loaded.overrideRejection != null,
                "override '$label' was rejected silently; the rejection must be observable",
            )
        }
    }

    @Test
    fun `malformed UTF-8 and unknown JSON fields are rejected rather than coerced`() {
        // A lone continuation byte is not valid UTF-8; decoding must refuse, not substitute U+FFFD.
        val malformedUtf8 = validConfig().encodeToByteArray().let { it + byteArrayOf(0x80.toByte()) }
        assertFailsWith<Exception> { ComplianceConfigLoader.load(malformedUtf8) }

        val unknownField = validConfig().replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1,\n  \"unexpectedField\": true,",
        )
        assertFailsWith<Exception> { ComplianceConfigLoader.load(unknownField.encodeToByteArray()) }
    }

    // --- the returned collections are genuinely unmodifiable ---

    @Test
    fun `config collections reject mutation through a cast and keep their values`() {
        val config = ComplianceConfigLoader.load(validConfig().encodeToByteArray()).config

        val sourceRefsBefore = config.sourceRefs.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableSourceRefs = config.sourceRefs as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutableSourceRefs.add("https://example.invalid/") }
        assertFailsWith<UnsupportedOperationException> { mutableSourceRefs.clear() }
        assertEquals(sourceRefsBefore, config.sourceRefs)

        val rulesBefore = config.rules.keys.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableRules = config.rules as MutableMap<String, EntryPurposeRule>
        assertFailsWith<UnsupportedOperationException> { mutableRules.remove("inspection") }
        assertFailsWith<UnsupportedOperationException> {
            mutableRules["smuggled"] = config.rules.getValue("inspection")
        }
        assertEquals(rulesBefore, config.rules.keys.toList())

        val exemptBefore = config.rules.getValue("inspection").frequencyLimit.exemptTypes.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableExempt = config.rules.getValue("inspection").frequencyLimit.exemptTypes as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutableExempt.add("ROUTINE") }
        assertEquals(exemptBefore, config.rules.getValue("inspection").frequencyLimit.exemptTypes)
    }

    @Test
    fun `validation errors reject mutation through a cast`() {
        val failure = assertFailsWith<ComplianceConfigException> {
            ComplianceConfigLoader.load(validConfig().replace("\"days\": 28", "\"days\": 0").encodeToByteArray())
        }
        val before = failure.errors.toList()
        @Suppress("UNCHECKED_CAST")
        val mutable = failure.errors as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutable.clear() }
        assertEquals(before, failure.errors)
    }

    // --- fixtures ---

    /** Each case edits exactly one property of [validConfig], so the label names the branch under test. */
    private fun rejectionCases(): List<Pair<String, (String) -> String>> = listOf(
        "unsupported schemaVersion" to { c: String -> c.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") },
        "malformed effectiveDate" to { c: String -> c.replace("\"2025-12-01\"", "\"01-12-2025\"") },
        "unknown timezone" to { c: String -> c.replace("\"Pacific/Auckland\"", "\"Pacific/Nowhere\"") },
        "non-v1 timezone" to { c: String -> c.replace("\"Pacific/Auckland\"", "\"UTC\"") },
        "empty sourceRefs" to { c: String -> c.replace(SOURCE_REFS, "\"sourceRefs\": [],") },
        "duplicate sourceRefs" to { c: String ->
            c.replace(SOURCE_REFS, "\"sourceRefs\": [\"$SOURCE_URL\", \"$SOURCE_URL\"],")
        },
        "non-https sourceRef" to { c: String -> c.replace("https://", "http://") },
        // Truncating at the rules object is the only edit that genuinely empties it; a "replace {" trick
        // silently produced the original document, and this suite caught that on first run.
        "empty rules" to { c: String -> c.substringBefore(RULES_OPEN) + RULES_OPEN + "}}" },
        "invalid entry purpose" to { c: String -> c.replace("\"inspection\":", "\"Inspection\":") },
        "non-positive noticeMinHours" to { c: String -> c.replace("\"noticeMinHours\": 48", "\"noticeMinHours\": 0") },
        "non-positive noticeMaxDays" to { c: String -> c.replace("\"noticeMaxDays\": 14", "\"noticeMaxDays\": 0") },
        "minimum notice exceeds maximum" to { c: String ->
            c.replace("\"noticeMinHours\": 48", "\"noticeMinHours\": 480")
                .replace("\"noticeMaxDays\": 14", "\"noticeMaxDays\": 1")
        },
        "non-positive frequency days" to { c: String -> c.replace("\"days\": 28", "\"days\": 0") },
        "duplicate exempt types" to { c: String ->
            c.replace("[\"INGOING\", \"EXIT\", \"ANNUAL\"]", "[\"INGOING\", \"INGOING\"]")
        },
        "unknown exempt type" to { c: String ->
            c.replace("[\"INGOING\", \"EXIT\", \"ANNUAL\"]", "[\"SPOT_CHECK\"]")
        },
        "malformed visit window time" to { c: String -> c.replace("\"start\": \"08:00\"", "\"start\": \"8am\"") },
        "inverted ordinary window" to { c: String -> c.replace("\"end\": \"19:00\"", "\"end\": \"07:00\"") },
        "boarding window wider than ordinary" to { c: String ->
            c.replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"20:00\"")
        },
        "inverted boarding window" to { c: String ->
            c.replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"07:00\"")
        },
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun validConfig(): String = """
        {
          "schemaVersion": 1,
          "effectiveDate": "2025-12-01",
          $SOURCE_REFS
          "timezone": "Pacific/Auckland",
          $RULES_OPEN
            "inspection": {
              "noticeMinHours": 48,
              "noticeMaxDays": 14,
              "visitWindow": {"start": "08:00", "end": "19:00", "boardingHouseEnd": "18:00"},
              "frequencyLimit": {"days": 28, "exemptTypes": ["INGOING", "EXIT", "ANNUAL"]}
            }
          $RULES_CLOSE
        }
    """.trimIndent()

    private companion object {
        const val SOURCE_URL =
            "https://www.legislation.govt.nz/act/public/1986/120/en/latest/sections/DLM95504/"
        const val SOURCE_REFS = "\"sourceRefs\": [\"$SOURCE_URL\"],"
        const val RULES_OPEN = "\"rules\": {"
        const val RULES_CLOSE = "}"
    }
}
