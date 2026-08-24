package nz.myinspection.core.compliance

import kotlinx.serialization.SerializationException
import java.nio.charset.MalformedInputException
import nz.myinspection.core.compliance.ComplianceTestFixtures.EXEMPT_TYPES
import nz.myinspection.core.compliance.ComplianceTestFixtures.LEGISLATION_REF
import nz.myinspection.core.compliance.ComplianceTestFixtures.RULES_OPEN
import nz.myinspection.core.compliance.ComplianceTestFixtures.SOURCE_REFS
import nz.myinspection.core.compliance.ComplianceTestFixtures.TENANCY_REF
import nz.myinspection.core.compliance.ComplianceTestFixtures.configJson
import nz.myinspection.core.compliance.ComplianceTestFixtures.sha256Hex
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
 * Each rejection case is expressed as a minimal edit to the one known-good document in
 * [ComplianceTestFixtures], so the case names the single property under test rather than re-stating a whole
 * config. Documents the loader must *accept* live in [ComplianceConfigLoaderTest]; this class is the refusals.
 */
class ComplianceConfigRejectionTest {

    // --- fail-closed: an invalid *built-in* config must throw, never silently degrade ---

    @Test
    fun `every named validation branch rejects the built-in config with its own diagnostic`() {
        rejectionCases().forEach { case ->
            val bytes = case.mutate(configJson()).encodeToByteArray()
            val failure = assertFailsWith<ComplianceConfigException>(
                message = "expected the built-in config to be rejected for: ${case.label}",
            ) { ComplianceConfigLoader.load(bytes) }
            assertTrue(
                failure.errors.any { it.contains(case.diagnostic) },
                "rejection for '${case.label}' never reported '${case.diagnostic}', so this case was being kept " +
                    "green by some other branch tripping on the same fixture and the branch under test could be " +
                    "deleted unnoticed. Diagnostics were: ${failure.errors}",
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
        rejectionCases().forEach { case ->
            val overrideBytes = case.mutate(configJson()).encodeToByteArray()
            val loaded = ComplianceConfigLoader.load(
                configJson().encodeToByteArray(),
                ComplianceOverride(overrideBytes, sha256Hex(overrideBytes)),
            )
            assertEquals(
                ComplianceConfigSource.BUILT_IN,
                loaded.source,
                "override '${case.label}' was accepted; invalid overrides must fall back",
            )
            // Naming the kind, not just its presence: refusing a document because it declares another schema
            // version is a different trust decision from refusing one this version calls invalid.
            assertEquals(
                case.rejection,
                loaded.overrideRejection,
                "override '${case.label}' was rejected as the wrong kind",
            )
        }
    }

    /**
     * Each expected type is the narrowest one the contract promises. `Exception` was wide enough to be
     * satisfied by the very coercion being forbidden: a decoder that substitutes U+FFFD for the malformed byte
     * still throws, because the replacement character lands after the closing brace and the parser refuses the
     * trailing input — so `String(bytes, Charsets.UTF_8)` was a green mutant of `decodeUtf8Strict`.
     */
    @Test
    fun `malformed UTF-8 and unknown JSON fields are rejected rather than coerced`() {
        // A lone continuation byte is not valid UTF-8; decoding must refuse, not substitute U+FFFD.
        val malformedUtf8 = configJson().encodeToByteArray().let { it + byteArrayOf(0x80.toByte()) }
        assertFailsWith<MalformedInputException> { ComplianceConfigLoader.load(malformedUtf8) }

        val unknownField = configJson().replace(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1,\n  \"unexpectedField\": true,",
        )
        assertFailsWith<SerializationException> { ComplianceConfigLoader.load(unknownField.encodeToByteArray()) }
    }

    @Test
    fun `each source reference condition reports its complete ordered indexed diagnostics`() {
        val cases = listOf(
            SourceRefCase(
                "control character",
                "https://www.tenancy.govt.nz/" + TAB_ESCAPE + "inspections/",
                listOf(
                    "sourceRefs[1]: must not contain control characters",
                    "sourceRefs[1]: must be a parsable URL",
                ),
            ),
            SourceRefCase(
                "unparsable",
                "https://",
                listOf("sourceRefs[1]: must be a parsable URL"),
            ),
            SourceRefCase(
                "non-https",
                "http://www.tenancy.govt.nz/maintenance-and-inspections/inspections/",
                listOf("sourceRefs[1]: must use HTTPS"),
            ),
            SourceRefCase(
                "hostless",
                "https:///maintenance-and-inspections/inspections/",
                listOf("sourceRefs[1]: must name a host"),
            ),
            SourceRefCase(
                "credentials",
                "https://attacker@www.tenancy.govt.nz/maintenance-and-inspections/inspections/",
                listOf("sourceRefs[1]: must not carry credentials"),
            ),
        )
        val branchDiagnostics = listOf(
            "must not contain control characters",
            "must be a parsable URL",
            "must use HTTPS",
            "must name a host",
            "must not carry credentials",
        )
        assertEquals(branchDiagnostics.size, branchDiagnostics.distinct().size, "sourceRef branch diagnostics must be unique")

        cases.forEach { case ->
            val json = configJson().replace(TENANCY_REF, case.replacement)
            val failure = assertFailsWith<ComplianceConfigException>(case.label) {
                ComplianceConfigLoader.load(json.encodeToByteArray())
            }
            assertEquals(case.expectedErrors, failure.errors, "wrong ordered diagnostics for ${case.label}")
        }
    }

    // --- the returned collections are genuinely unmodifiable ---

    @Test
    fun `config collections reject mutation through a cast and keep their values`() {
        val config = ComplianceConfigLoader.load(configJson().encodeToByteArray()).config

        val sourceRefsBefore = config.sourceRefs.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableSourceRefs = config.sourceRefs as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutableSourceRefs.add("https://example.invalid/") }
        assertFailsWith<UnsupportedOperationException> { mutableSourceRefs.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { mutableSourceRefs.clear() }
        assertEquals(sourceRefsBefore, config.sourceRefs)

        val rulesBefore = config.rules.keys.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableRules = config.rules as MutableMap<String, EntryPurposeRule>
        assertFailsWith<UnsupportedOperationException> { mutableRules.remove("inspection") }
        assertFailsWith<UnsupportedOperationException> {
            mutableRules["smuggled"] = config.rules.getValue("inspection")
        }
        assertFailsWith<UnsupportedOperationException> { mutableRules.clear() }
        assertEquals(rulesBefore, config.rules.keys.toList())

        val exemptBefore = config.rules.getValue("inspection").frequencyLimit.exemptTypes.toList()
        @Suppress("UNCHECKED_CAST")
        val mutableExempt = config.rules.getValue("inspection").frequencyLimit.exemptTypes as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutableExempt.add("ROUTINE") }
        assertFailsWith<UnsupportedOperationException> { mutableExempt.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { mutableExempt.clear() }
        assertEquals(exemptBefore, config.rules.getValue("inspection").frequencyLimit.exemptTypes)
    }

    @Test
    fun `validation errors reject mutation through a cast`() {
        val failure = assertFailsWith<ComplianceConfigException> {
            ComplianceConfigLoader.load(configJson().replace("\"days\": 28", "\"days\": 0").encodeToByteArray())
        }
        val before = failure.errors.toList()
        @Suppress("UNCHECKED_CAST")
        val mutable = failure.errors as MutableList<String>
        assertFailsWith<UnsupportedOperationException> { mutable.add("smuggled") }
        assertFailsWith<UnsupportedOperationException> { mutable.removeAt(0) }
        assertFailsWith<UnsupportedOperationException> { mutable.clear() }
        assertEquals(before, failure.errors)
    }

    // --- fixtures ---

    /**
     * One rejection fixture: a minimal edit to [ComplianceTestFixtures.configJson], the diagnostic substring
     * the branch under test must produce, and how the override path is expected to classify it.
     *
     * [diagnostic] is what makes the case falsifiable. Several fixtures unavoidably trip a second branch too —
     * an unknown timezone is also not the v1 timezone, `noticeMaxDays: 0` also puts the minimum above the
     * maximum, an end time before the start is also narrower than the boarding-house close — so asserting only
     * "something was refused" left those three branches individually deletable.
     */
    private data class RejectionCase(
        val label: String,
        val diagnostic: String,
        val rejection: OverrideRejection = OverrideRejection.INVALID_CONFIG,
        val mutate: (String) -> String,
    )

    private data class SourceRefCase(
        val label: String,
        val replacement: String,
        val expectedErrors: List<String>,
    )

    private fun rejectionCases(): List<RejectionCase> = listOf(
        RejectionCase(
            "unsupported schemaVersion",
            "unsupported schemaVersion 2",
            OverrideRejection.SCHEMA_VERSION_MISMATCH,
        ) { it.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2") },
        RejectionCase("malformed effectiveDate", "effectiveDate must be ISO-8601") {
            it.replace("\"2025-12-01\"", "\"01-12-2025\"")
        },
        RejectionCase("unknown timezone", "timezone is unknown") {
            it.replace("\"Pacific/Auckland\"", "\"Pacific/Nowhere\"")
        },
        RejectionCase("non-v1 timezone", "timezone must be Pacific/Auckland") {
            it.replace("\"Pacific/Auckland\"", "\"UTC\"")
        },
        RejectionCase("empty sourceRefs", "sourceRefs is empty") { it.replace(SOURCE_REFS, "\"sourceRefs\": [],") },
        RejectionCase("duplicate sourceRefs", "sourceRefs contains duplicates") {
            it.replace(SOURCE_REFS, "\"sourceRefs\": [\"$LEGISLATION_REF\", \"$LEGISLATION_REF\"],")
        },
        // One case per sourceRef condition; the URI measurements that decide which shape reaches which
        // condition are recorded beside those conditions in ComplianceConfigLoader.
        RejectionCase("non-https sourceRef", "must use HTTPS") { it.replace("https://", "http://") },
        RejectionCase("credentialed sourceRef", "must not carry credentials") {
            it.replace("https://www.tenancy", "https://attacker@www.tenancy")
        },
        RejectionCase("hostless hierarchical sourceRef", "must name a host") {
            it.replace(TENANCY_REF, "https:///maintenance-and-inspections/")
        },
        RejectionCase("hostless opaque sourceRef", "must name a host") {
            it.replace(TENANCY_REF, "https:maintenance-and-inspections")
        },
        RejectionCase("unparsable sourceRef", "must be a parsable URL") {
            it.replace(TENANCY_REF, "https://")
        },
        RejectionCase("control character in sourceRef", "must not contain control characters") {
            it.replace(TENANCY_REF, "https://www.tenancy.govt.nz/" + TAB_ESCAPE + "inspections/")
        },
        // Truncating at the rules object is the only edit that genuinely empties it; a "replace {" trick
        // silently produced the original document, and this suite caught that on first run.
        RejectionCase("empty rules", "rules is empty") { it.substringBefore(RULES_OPEN) + RULES_OPEN + "}}" },
        RejectionCase("invalid entry purpose", "entry purpose is invalid") {
            it.replace("\"inspection\":", "\"Inspection\":")
        },
        RejectionCase("non-positive noticeMinHours", "noticeMinHours must be positive") {
            it.replace("\"noticeMinHours\": 48", "\"noticeMinHours\": 0")
        },
        RejectionCase("non-positive noticeMaxDays", "noticeMaxDays must be positive") {
            it.replace("\"noticeMaxDays\": 14", "\"noticeMaxDays\": 0")
        },
        RejectionCase("minimum notice exceeds maximum", "minimum notice exceeds maximum notice") {
            it.replace("\"noticeMinHours\": 48", "\"noticeMinHours\": 480")
                .replace("\"noticeMaxDays\": 14", "\"noticeMaxDays\": 1")
        },
        RejectionCase("non-positive frequency days", "frequency days must be positive") {
            it.replace("\"days\": 28", "\"days\": 0")
        },
        RejectionCase("duplicate exempt types", "exemptTypes contains duplicates") {
            it.replace(EXEMPT_TYPES, "[\"INGOING\", \"INGOING\"]")
        },
        RejectionCase("unknown exempt type", "unknown exempt inspection type SPOT_CHECK") {
            it.replace(EXEMPT_TYPES, "[\"SPOT_CHECK\"]")
        },
        RejectionCase("malformed visit window time", "visitWindow.start: must be HH:mm") {
            it.replace("\"start\": \"08:00\"", "\"start\": \"8am\"")
        },
        RejectionCase("inverted ordinary window", "visit window start must be before end") {
            it.replace("\"end\": \"19:00\"", "\"end\": \"07:00\"")
        },
        RejectionCase("boarding window wider than ordinary", "boarding-house window cannot be wider") {
            it.replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"20:00\"")
        },
        RejectionCase("inverted boarding window", "boarding-house window start must be before end") {
            it.replace("\"boardingHouseEnd\": \"18:00\"", "\"boardingHouseEnd\": \"07:00\"")
        },
        // Measured on the pinned kotlinx build, both duplicate shapes were accepted last-wins and reported
        // nothing: the scalar pair yielded noticeMinHours 1, the repeated rule key yielded the later object.
        // The document is authenticated by one digest over the whole file, so a reviewer who checked that
        // digest and read the bytes would have approved a rule the loader did not enforce.
        RejectionCase("duplicate scalar key", "duplicate JSON key noticeMinHours") {
            it.replace("\"noticeMinHours\": 48,", "\"noticeMinHours\": 48, \"noticeMinHours\": 1,")
        },
        RejectionCase("duplicate rule key", "duplicate JSON key inspection") {
            it.replace("\"inspection\": {", SHADOW_RULE + "\"inspection\": {")
        },
        // An escaped key is refused rather than unescaped, so two spellings of one name cannot slip past the
        // duplicate check by differing textually. kotlinx decodes this one to `noticeMinHours`.
        RejectionCase("escaped JSON key", "uses an escape sequence") {
            it.replace("\"noticeMinHours\":", "\"notice\\u004dinHours\":")
        },
    )

    private companion object {
        /** A complete but tampered inspection rule, used to give the good document a second `inspection` key. */
        const val SHADOW_RULE =
            "\"inspection\": {\"noticeMinHours\": 1, \"noticeMaxDays\": 1, " +
                "\"visitWindow\": {\"start\": \"08:00\", \"end\": \"19:00\", \"boardingHouseEnd\": \"18:00\"}, " +
                "\"frequencyLimit\": {\"days\": 1, \"exemptTypes\": []}}, "

        /** A JSON escape for U+0009, spelled from two pieces so no tool can turn this source into a real tab. */
        const val TAB_ESCAPE = "\\" + "u0009"
    }
}
