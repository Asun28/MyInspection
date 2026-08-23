package nz.myinspection.core.compliance

import java.security.MessageDigest

/**
 * The one rule document this package's tests are written against, plus the digest helper the override paths need.
 *
 * It used to be hand-copied into three test classes with three sets of defaults, so a schema change meant three
 * coordinated edits and any drift between them was invisible.
 *
 * Every value is a parameter rather than a separate fixture because the negative cases are expressed as a
 * one-property edit of the good document (see [ComplianceConfigRejectionTest]). Those edits match on the exact
 * spellings below, which is why [SOURCE_REFS] and [RULES_OPEN] are shared constants instead of being re-typed.
 */
internal object ComplianceTestFixtures {
    const val RULES_FILE = "configs/compliance/nz-rules-v1.json"
    const val LEGISLATION_REF = "https://www.legislation.govt.nz/act/public/1986/120/en/latest/sections/DLM95504/"
    const val TENANCY_REF = "https://www.tenancy.govt.nz/maintenance-and-inspections/inspections/"
    const val SOURCE_REFS = "\"sourceRefs\": [\"$LEGISLATION_REF\", \"$TENANCY_REF\"],"
    const val RULES_OPEN = "\"rules\": {"

    /** The signed exemption list; [ComplianceEngineTest] records why ANNUAL belongs in it. */
    const val EXEMPT_TYPES = "[\"INGOING\", \"EXIT\", \"ANNUAL\"]"

    fun configJson(
        schemaVersion: Int = 1,
        noticeMinHours: Int = 48,
        noticeMaxDays: Int = 14,
        windowStart: String = "08:00",
        windowEnd: String = "19:00",
        boardingHouseEnd: String = "18:00",
        frequencyDays: Int = 28,
        exemptTypes: String = EXEMPT_TYPES,
        alternatePurpose: Boolean = false,
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "effectiveDate": "2025-12-01",
          $SOURCE_REFS
          "timezone": "Pacific/Auckland",
          $RULES_OPEN
            "inspection": {
              "noticeMinHours": $noticeMinHours,
              "noticeMaxDays": $noticeMaxDays,
              "visitWindow": {"start": "$windowStart", "end": "$windowEnd", "boardingHouseEnd": "$boardingHouseEnd"},
              "frequencyLimit": {"days": $frequencyDays, "exemptTypes": $exemptTypes}
            }${if (alternatePurpose) ALTERNATE_PURPOSE_RULE else ""}
          }
        }
    """.trimIndent()

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * A second configured purpose, unlike "inspection" in every value a test could read: a higher notice floor
     * and a frequency limit that exempts everything. A rule picked by purpose is therefore distinguishable from
     * the inspection rule being used by default.
     */
    private const val ALTERNATE_PURPOSE_RULE = """,
            "fixture-purpose": {
              "noticeMinHours": 72,
              "noticeMaxDays": 14,
              "visitWindow": {"start": "08:00", "end": "19:00", "boardingHouseEnd": "18:00"},
              "frequencyLimit": {"days": 1, "exemptTypes": ["ROUTINE", "INGOING", "EXIT", "ANNUAL"]}
            }"""
}
