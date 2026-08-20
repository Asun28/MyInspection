package nz.myinspection.core.compliance

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Collections

data class VisitWindow(
    val start: LocalTime,
    val end: LocalTime,
    val boardingHouseEnd: LocalTime,
)

data class FrequencyLimit(
    val days: Int,
    val exemptTypes: List<String>,
)

data class EntryPurposeRule(
    val noticeMinHours: Int,
    val noticeMaxDays: Int,
    val visitWindow: VisitWindow,
    val frequencyLimit: FrequencyLimit,
)

class ComplianceConfig internal constructor(
    val schemaVersion: Int,
    val effectiveDate: LocalDate,
    val sourceRefs: List<String>,
    val timezone: ZoneId,
    val rules: Map<String, EntryPurposeRule>,
)

data class ComplianceOverride(
    val bytes: ByteArray,
    val expectedSha256: String,
)

enum class ComplianceConfigSource { BUILT_IN, OVERRIDE }

enum class OverrideRejection {
    CHECKSUM_MISMATCH,
    SCHEMA_VERSION_MISMATCH,
    INVALID_CONFIG,
}

data class LoadedComplianceConfig(
    val config: ComplianceConfig,
    val source: ComplianceConfigSource,
    val overrideRejection: OverrideRejection?,
)

class ComplianceConfigException(val errors: List<String>) :
    IllegalArgumentException("compliance config validation failed: ${errors.joinToString("; ")}")

/**
 * Loads the APK-owned rule bytes and, when supplied, an integrity-bound override.
 *
 * The override digest is deliberately supplied outside the JSON document. That makes the digest an independent trust
 * input and avoids a self-referential field in the frozen rule schema. Invalid overrides are observable but fall back
 * to the built-in rule set; an invalid built-in config fails closed.
 */
object ComplianceConfigLoader {
    private const val SUPPORTED_SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = false }

    fun load(
        builtInBytes: ByteArray,
        override: ComplianceOverride? = null,
    ): LoadedComplianceConfig {
        val builtIn = parseValidated(builtInBytes, SUPPORTED_SCHEMA_VERSION)
        if (override == null) {
            return LoadedComplianceConfig(builtIn, ComplianceConfigSource.BUILT_IN, null)
        }

        if (!MessageDigest.isEqual(
                sha256Hex(override.bytes).encodeToByteArray(),
                override.expectedSha256.lowercase().encodeToByteArray(),
            )
        ) {
            return LoadedComplianceConfig(
                builtIn,
                ComplianceConfigSource.BUILT_IN,
                OverrideRejection.CHECKSUM_MISMATCH,
            )
        }

        val raw = try {
            decodeRaw(override.bytes)
        } catch (_: Exception) {
            return LoadedComplianceConfig(
                builtIn,
                ComplianceConfigSource.BUILT_IN,
                OverrideRejection.INVALID_CONFIG,
            )
        }
        if (raw.schemaVersion != builtIn.schemaVersion) {
            return LoadedComplianceConfig(
                builtIn,
                ComplianceConfigSource.BUILT_IN,
                OverrideRejection.SCHEMA_VERSION_MISMATCH,
            )
        }

        val accepted = try {
            validateAndFreeze(raw, builtIn.schemaVersion)
        } catch (_: Exception) {
            return LoadedComplianceConfig(
                builtIn,
                ComplianceConfigSource.BUILT_IN,
                OverrideRejection.INVALID_CONFIG,
            )
        }
        return LoadedComplianceConfig(accepted, ComplianceConfigSource.OVERRIDE, null)
    }

    private fun parseValidated(bytes: ByteArray, expectedSchemaVersion: Int): ComplianceConfig =
        validateAndFreeze(decodeRaw(bytes), expectedSchemaVersion)

    private fun decodeRaw(bytes: ByteArray): RawComplianceConfig =
        json.decodeFromString(RawComplianceConfig.serializer(), decodeUtf8Strict(bytes))

    private fun validateAndFreeze(raw: RawComplianceConfig, expectedSchemaVersion: Int): ComplianceConfig {
        val errors = mutableListOf<String>()
        if (raw.schemaVersion != expectedSchemaVersion) {
            errors += "config: unsupported schemaVersion ${raw.schemaVersion}"
        }

        val effectiveDate = try {
            LocalDate.parse(raw.effectiveDate)
        } catch (_: Exception) {
            errors += "config: effectiveDate must be ISO-8601"
            null
        }
        val timezone = try {
            ZoneId.of(raw.timezone)
        } catch (_: Exception) {
            errors += "config: timezone is unknown"
            null
        }

        if (raw.sourceRefs.isEmpty()) errors += "config: sourceRefs is empty"
        raw.sourceRefs.forEachIndexed { index, ref ->
            val uri = try {
                URI(ref)
            } catch (_: Exception) {
                null
            }
            if (uri == null ||
                !uri.scheme.equals("https", ignoreCase = true) ||
                uri.host.isNullOrBlank() ||
                uri.userInfo != null ||
                ref.any { it.isISOControl() }
            ) {
                errors += "sourceRefs[$index]: must be a safe HTTPS URL"
            }
        }
        if (raw.sourceRefs.distinct().size != raw.sourceRefs.size) {
            errors += "config: sourceRefs contains duplicates"
        }
        if (raw.rules.isEmpty()) errors += "config: rules is empty"

        val rules = linkedMapOf<String, EntryPurposeRule>()
        raw.rules.forEach { (purpose, rule) ->
            val label = "rules[$purpose]"
            if (!purpose.matches(Regex("[a-z][a-z0-9-]*"))) {
                errors += "$label: entry purpose is invalid"
            }
            if (rule.noticeMinHours < 1) errors += "$label: noticeMinHours must be positive"
            if (rule.noticeMaxDays < 1) errors += "$label: noticeMaxDays must be positive"
            if (rule.noticeMinHours > rule.noticeMaxDays.toLong() * 24L) {
                errors += "$label: minimum notice exceeds maximum notice"
            }
            if (rule.frequencyLimit.days < 1) errors += "$label: frequency days must be positive"
            if (rule.frequencyLimit.exemptTypes.distinct().size != rule.frequencyLimit.exemptTypes.size) {
                errors += "$label: exemptTypes contains duplicates"
            }
            rule.frequencyLimit.exemptTypes.forEach { type ->
                if (type !in SUPPORTED_INSPECTION_TYPES) errors += "$label: unknown exempt inspection type $type"
            }

            val start = parseTime(rule.visitWindow.start, "$label.visitWindow.start", errors)
            val end = parseTime(rule.visitWindow.end, "$label.visitWindow.end", errors)
            val boardingEnd = parseTime(
                rule.visitWindow.boardingHouseEnd,
                "$label.visitWindow.boardingHouseEnd",
                errors,
            )
            if (start != null && end != null && !start.isBefore(end)) {
                errors += "$label: visit window start must be before end"
            }
            if (start != null && boardingEnd != null && !start.isBefore(boardingEnd)) {
                errors += "$label: boarding-house window start must be before end"
            }
            if (end != null && boardingEnd != null && boardingEnd.isAfter(end)) {
                errors += "$label: boarding-house window cannot be wider than the ordinary window"
            }

            if (start != null && end != null && boardingEnd != null) {
                rules[purpose] = EntryPurposeRule(
                    noticeMinHours = rule.noticeMinHours,
                    noticeMaxDays = rule.noticeMaxDays,
                    visitWindow = VisitWindow(start, end, boardingEnd),
                    frequencyLimit = FrequencyLimit(
                        rule.frequencyLimit.days,
                        Collections.unmodifiableList(rule.frequencyLimit.exemptTypes.toList()),
                    ),
                )
            }
        }

        if (errors.isNotEmpty()) throw ComplianceConfigException(Collections.unmodifiableList(errors))
        return ComplianceConfig(
            schemaVersion = raw.schemaVersion,
            effectiveDate = requireNotNull(effectiveDate),
            sourceRefs = Collections.unmodifiableList(raw.sourceRefs.toList()),
            timezone = requireNotNull(timezone),
            rules = Collections.unmodifiableMap(rules),
        )
    }

    private fun parseTime(raw: String, label: String, errors: MutableList<String>): LocalTime? =
        if (!raw.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) {
            errors += "$label: must be HH:mm"
            null
        } else {
            LocalTime.parse(raw)
        }

}

internal val SUPPORTED_INSPECTION_TYPES: Set<String> =
    Collections.unmodifiableSet(setOf("ROUTINE", "INGOING", "EXIT", "ANNUAL"))

@Serializable
private data class RawComplianceConfig(
    val schemaVersion: Int,
    val effectiveDate: String,
    val sourceRefs: List<String>,
    val timezone: String,
    val rules: Map<String, RawEntryPurposeRule>,
)

@Serializable
private data class RawEntryPurposeRule(
    val noticeMinHours: Int,
    val noticeMaxDays: Int,
    val visitWindow: RawVisitWindow,
    val frequencyLimit: RawFrequencyLimit,
)

@Serializable
private data class RawVisitWindow(
    val start: String,
    val end: String,
    val boardingHouseEnd: String,
)

@Serializable
private data class RawFrequencyLimit(
    val days: Int,
    val exemptTypes: List<String>,
)

private fun decodeUtf8Strict(bytes: ByteArray): String =
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
