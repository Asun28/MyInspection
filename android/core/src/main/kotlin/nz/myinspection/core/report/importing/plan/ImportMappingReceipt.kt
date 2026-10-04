package nz.myinspection.core.report.importing.plan

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nz.myinspection.core.canon.CanonicalJson
import nz.myinspection.core.canon.sha256Hex

/** Redacted commitment to reviewed choices, independent of the finalized native inspection hash. */
class ImportMappingReceipt private constructor(val canonicalJson: String, val sha256: String) {
    companion object {
        fun create(current: ImportReview, preview: ImportReviewPreview): ImportMappingReceipt {
            require(preview.isReady(current)) { "CURRENT_READY_PREVIEW_REQUIRED" }
            val context = current.plan.context
            val binding = requireNotNull(context.template)
            val warnings = current.plan.rows.flatMap { row ->
                row.sourceIds.filter { it.category == ImportSourceCategory.WARNING }.zip(row.warnings.map { it.code.name })
            }.toMap()
            val statuses = current.items.associate { it.target to it.status }
            // Allowlist projection only: no source text, source location, image bytes or display labels.
            val json = obj(
                "version" to JsonPrimitive(1),
                "context" to obj(
                    "property_id" to str(context.propertyId), "tenancy_id" to str(context.tenancyId),
                    "report_date" to str(context.reportDate), "source_sha256" to str(context.sourceSha256),
                    "manifest_digest" to str(context.manifestDigest), "has_active_draft" to JsonPrimitive(context.hasActiveDraft),
                    "template" to obj("id" to str(binding.id), "content_hash" to str(binding.contentHash),
                        "type" to str(binding.template.type), "version" to JsonPrimitive(binding.template.version)),
                    "rooms" to JsonArray(context.roomInstances.map { room ->
                        obj("room_key" to str(room.roomKey), "instance_no" to JsonPrimitive(room.instanceNo))
                    }),
                    "suppressed_stable_ids" to JsonArray(context.suppressedStableIds.sorted().map(::str)),
                ),
                "summary_status" to str(current.summaryStatus),
                "sources" to JsonArray(current.sources.map { source -> obj(
                    "id" to str(source.id.opaque()), "owner" to str(source.owner.opaque()),
                    "state" to str(source.state.name), "decision" to decision(source.decision),
                    "reason" to str(source.reason?.name), "warning" to str(warnings[source.id]),
                ) }),
                "targets" to JsonArray(current.plan.targets.map { obj("target" to target(it), "status" to str(statuses[it])) }),
            )
            val canonical = CanonicalJson.serialize(json)
            return ImportMappingReceipt(canonical, sha256Hex("MYINSPECTION-IMPORT-MAPPING-1\n" + canonical))
        }

        private fun decision(value: ImportDecision?): JsonElement = when (value) {
            null -> JsonNull
            is ImportDecision.Item -> obj("type" to str("ITEM"), "target" to target(value.target), "status" to str(value.status))
            is ImportDecision.Note -> obj("type" to str("NOTE"), "target" to target(value.target))
            is ImportDecision.Photo -> obj("type" to str("PHOTO"), "target" to target(value.target), "privacy" to str(value.privacy.name))
            ImportDecision.Summary -> obj("type" to str("SUMMARY"))
            is ImportDecision.Exclude -> obj("type" to str("EXCLUDE"), "reason" to str(value.reason.name), "privacy" to str(value.privacy?.name))
        }

        private fun target(value: ImportTarget) = obj("room_key" to str(value.roomKey),
            "instance_no" to JsonPrimitive(value.instanceNo), "stable_id" to str(value.stableId))
        private fun ImportSourceId.opaque() = "${category.name}:$index"
        private fun str(value: String?) = JsonPrimitive(value)
        private fun obj(vararg fields: Pair<String, JsonElement>) = JsonObject(mapOf(*fields))
    }
}
