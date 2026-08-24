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

class ComplianceOverride(
    bytes: ByteArray,
    val expectedSha256: String,
) {
    // Copy at the trust boundary: the caller must not be able to change signed input after constructing it.
    private val signedBytes = bytes.copyOf()

    internal fun snapshotBytes(): ByteArray = signedBytes.copyOf()
}

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
        val overrideBytes = override.snapshotBytes()

        if (!MessageDigest.isEqual(
                sha256Hex(overrideBytes).encodeToByteArray(),
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
            decodeRaw(overrideBytes)
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

    private fun decodeRaw(bytes: ByteArray): RawComplianceConfig {
        val text = decodeUtf8Strict(bytes)
        val raw = json.decodeFromString(RawComplianceConfig.serializer(), text)
        val ambiguous = ambiguousKeys(text)
        if (ambiguous.isNotEmpty()) throw ComplianceConfigException(Collections.unmodifiableList(ambiguous))
        return raw
    }

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
        if (raw.timezone != V1_TIMEZONE) {
            errors += "config: timezone must be $V1_TIMEZONE for schemaVersion 1"
        }

        if (raw.sourceRefs.isEmpty()) errors += "config: sourceRefs is empty"
        // One diagnostic per condition, or a deleted condition is reported by whichever sibling happens to trip
        // on the same fixture and nothing turns red. Measured with java.net.URI: only the credential branch
        // refuses "https://attacker@www.tenancy.govt.nz/", and both "https:///path" and "https:x" parse
        // hostless. The control-character check runs before the parse because every control character makes
        // URI throw, so behind the parse it could never fire.
        raw.sourceRefs.forEachIndexed { index, ref ->
            val label = "sourceRefs[$index]"
            if (ref.any { it.isISOControl() }) errors += "$label: must not contain control characters"
            val uri = try {
                URI(ref)
            } catch (_: Exception) {
                null
            }
            if (uri == null) {
                errors += "$label: must be a parsable URL"
            } else {
                if (!uri.scheme.equals("https", ignoreCase = true)) errors += "$label: must use HTTPS"
                if (uri.host.isNullOrBlank()) errors += "$label: must name a host"
                if (uri.userInfo != null) errors += "$label: must not carry credentials"
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

    private const val V1_TIMEZONE = "Pacific/Auckland"

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

/**
 * Object keys that would let the document say one thing and mean another.
 *
 * The pinned kotlinx build resolves duplicate keys last-wins and reports nothing, so an override whose SHA-256
 * a user verified could still enforce a value no reader of those bytes would predict — the digest authenticates
 * the whole file, which puts "what the bytes say" inside the trust boundary. Keys are compared exactly as
 * written and an escaped key is refused outright, so two spellings of one name cannot differ textually and
 * collide after decoding; no key in this schema needs an escape.
 *
 * The scan assumes well-formed JSON and so runs only after the parser has accepted the text: there, a string
 * followed by ':' is always an object key.
 */
private fun ambiguousKeys(text: String): List<String> {
    val errors = mutableListOf<String>()
    val scopes = ArrayDeque<MutableSet<String>>()
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '{' -> { scopes.addLast(mutableSetOf()); index++ }
            '}' -> { scopes.removeLastOrNull(); index++ }
            '"' -> {
                var end = index + 1
                while (end < text.length && text[end] != '"') end += if (text[end] == '\\') 2 else 1
                val literal = text.substring(index + 1, minOf(end, text.length))
                index = end + 1
                var next = index
                while (next < text.length && text[next].isWhitespace()) next++
                if (next < text.length && text[next] == ':' && scopes.isNotEmpty()) {
                    if (literal.contains('\\')) {
                        errors += "config: JSON key $literal uses an escape sequence"
                    } else if (!scopes.last().add(literal)) {
                        errors += "config: duplicate JSON key $literal"
                    }
                }
            }
            else -> index++
        }
    }
    return errors
}

private fun decodeUtf8Strict(bytes: ByteArray): String =
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
