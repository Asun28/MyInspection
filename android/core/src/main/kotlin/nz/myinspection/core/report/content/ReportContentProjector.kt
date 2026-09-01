package nz.myinspection.core.report.content

import java.time.LocalDate
import java.util.Collections
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.myinspection.core.canon.CanonicalJson
import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.capture.AdverseStatuses
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.REPORT_DISCLAIMER
import nz.myinspection.core.report.ReportItem
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportPhoto
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.template.TemplateDomains

/** Builds the single, validated semantic graph shared by every report format. */
class ReportContentProjector {
    fun project(
        report: ReportSnapshot,
        audience: Audience,
        options: ReportOptions = ReportOptions(),
        provenance: LegacyImportProvenance? = null,
    ): ReportContent = ReportContent.project(report, audience, options, provenance)
}

internal object ReportContentProjectionBuilder {
    fun build(
        report: ReportSnapshot,
        audience: Audience,
        options: ReportOptions,
        provenance: LegacyImportProvenance?,
    ): ReportContentProjectionValues {
        validate(report, provenance)
        val includePhoto: (ReportPhoto) -> Boolean = { options.includePrivacyPhotos || !it.privacy }
        val rooms = immutable(report.rooms.mapNotNull { room ->
            val roomPhotos = immutable(room.photos.filter(includePhoto).map(::photoContent))
            if (room.items.isEmpty() && roomPhotos.isEmpty()) return@mapNotNull null
            ReportContentRoom(
                id = room.id,
                label = room.label,
                photos = roomPhotos,
                items = immutable(room.items.map { item ->
                    ReportContentItem(
                        id = item.id,
                        stableId = item.snapshot.stableId,
                        label = item.label,
                        status = item.snapshot.status,
                        note = item.snapshot.note,
                        wearOrDamage = item.snapshot.wearOrDamage.takeIf { audience == Audience.LANDLORD },
                        photos = immutable(item.photos.filter(includePhoto).map(::photoContent)),
                    )
                }),
            )
        })
        val remediations = immutable(report.remediations.takeIf { audience == Audience.LANDLORD }.orEmpty())
        val summary = ReportContentSummary(
            roomStatusCounts = immutable(rooms.flatMap { room ->
                room.items.map { it.status }.distinct().map { status ->
                    ReportContentRoomStatusCount(room.id, status, room.items.count { it.status == status })
                }
            }),
            adverseItems = immutable(rooms.flatMap { room ->
                room.items.filter { AdverseStatuses.isAdverse(report.canonical.type, it.status) }.map { item ->
                    ReportContentSummaryItem(room.id, item.id, item.status, item.label, item.note)
                }
            }),
            pendingRemediationCount = remediations.map { it.itemId }.distinct().size
                .takeIf { audience == Audience.LANDLORD },
        )
        val finalizedAt = requireNotNull(report.canonical.finalizedAt) { "report content requires a finalized inspection" }
        return ReportContentProjectionValues(
            contractVersion = 1,
            identity = ReportIdentity(
                inspectionId = report.canonical.id,
                propertyId = report.canonical.property.id,
                propertyAddress = report.canonical.property.address,
                propertyKind = report.canonical.property.kind,
                isBoardingHouse = report.canonical.property.isBoardingHouse,
                inspectionType = report.canonical.type,
                scheduledAt = report.canonical.scheduledAt,
                finalizedAt = finalizedAt,
                tenancyReference = report.tenancyReference,
                templateId = report.canonical.template.id,
                templateVersion = report.canonical.template.version,
                templateContentHash = report.canonical.template.contentHash,
            ),
            audience = audience,
            privatePhotoScope = if (options.includePrivacyPhotos) {
                PrivatePhotoScope.EXPLICITLY_INCLUDED
            } else {
                PrivatePhotoScope.EXCLUDED
            },
            origin = if (provenance == null) ReportOrigin.NATIVE else ReportOrigin.LEGACY_DOCX_IMPORT,
            nativeIntegrity = NativeIntegrity(sha256Hex(canonicalJson(report.canonical))),
            importProvenance = provenance,
            statusDefinitions = immutable(report.statusDefinitions),
            summary = summary,
            rooms = rooms,
            supplements = immutable(report.supplements),
            remediations = remediations,
            disclaimer = REPORT_DISCLAIMER,
            tenantAgreement = TENANT_AGREEMENT.takeIf { audience == Audience.TENANT },
        )
    }

    private fun photoContent(photo: ReportPhoto) = ReportContentPhoto(
        id = photo.id,
        contentHash = photo.snapshot.contentHash,
        source = photo.snapshot.source,
        reference = photo.reference,
        capturedAt = photo.snapshot.exifTimeMs ?: photo.capturedAt,
        privacy = photo.privacy,
    )

    private fun validate(report: ReportSnapshot, provenance: LegacyImportProvenance?) {
        require(report.rooms.map { it.id }.toSet().size == report.rooms.size) { "duplicate room id" }
        require(report.rooms.all { it.id.isNotBlank() }) { "report rooms require a non-blank id" }
        val items = report.rooms.flatMap { it.items }
        val photos = report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
        require(items.map { it.id }.toSet().size == items.size) { "duplicate report item id" }
        require(items.all { it.id.isNotBlank() }) { "report items require a non-blank id" }
        require(photos.map { it.id }.toSet().size == photos.size) { "duplicate report photo id" }
        require(photos.map { it.reference }.toSet().size == photos.size) { "duplicate report photo reference" }
        require(photos.all { it.id.isNotBlank() && it.reference.isNotBlank() }) {
            "report photos require an id and reference"
        }
        require(photos.all { (it.snapshot.exifTimeMs ?: it.capturedAt) > 0 }) {
            "report photos require a positive rendered capture time"
        }
        require(report.rooms.all { room -> room.photos.all { it.snapshot.isRoomLevel } }) {
            "a room-level photo slot holds a photo whose canonical isRoomLevel is false"
        }
        require(report.rooms.all { room -> room.items.all { item -> item.photos.none { it.snapshot.isRoomLevel } } }) {
            "an item photo slot holds a photo whose canonical isRoomLevel is true"
        }
        require(multiset(items.map(ReportItem::snapshot)) == multiset(report.canonical.items)) {
            "report items do not match canonical snapshot"
        }
        require(multiset(photos.map(ReportPhoto::snapshot)) == multiset(report.canonical.photos)) {
            "report photos do not match canonical snapshot"
        }
        val itemIds = items.mapTo(mutableSetOf()) { it.id }
        require(report.remediations.all { it.itemId in itemIds }) { "remediation references unknown item" }
        val allowedStatuses = requireNotNull(TemplateDomains.allowedStatusesFor(report.canonical.type)) {
            "unknown inspection type: ${report.canonical.type}"
        }
        require(report.statusDefinitions.map { it.status }.toSet().size == report.statusDefinitions.size) {
            "duplicate status definition"
        }
        require(report.statusDefinitions.map { it.status }.toSet() == allowedStatuses) {
            "report glossary must exactly cover the ${report.canonical.type} status domain"
        }
        require(items.all { it.snapshot.status in allowedStatuses }) { "item status is outside the template domain" }
        require(report.supplements.all { it.reference.isNotBlank() }) { "supplement reference must not be blank" }
        require(report.supplements.map { it.reference }.toSet().size == report.supplements.size) {
            "duplicate supplement reference"
        }
        provenance?.validate()
    }

    private fun LegacyImportProvenance.validate() {
        require(SHA256.matches(sourceSha256)) { "source SHA-256 must be lowercase hexadecimal" }
        require(SHA256.matches(normalizedManifestSha256)) { "manifest SHA-256 must be lowercase hexadecimal" }
        require(SHA256.matches(mappingReceiptSha256)) { "mapping SHA-256 must be lowercase hexadecimal" }
        require(extractorVersion.isNotBlank() && extractorVersion.length <= 64) { "extractor version is invalid" }
        sourceReportDate?.let {
            require(it.length == 10 && runCatching { LocalDate.parse(it) }.isSuccess) { "source report date is invalid" }
        }
    }

    private fun <T> multiset(values: List<T>): Map<T, Int> = values.groupingBy { it }.eachCount()

    private fun <T> immutable(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

    private val SHA256 = Regex("[0-9a-f]{64}")
    private val TENANT_AGREEMENT = BilingualText("Tenant agreement / signature", "租客同意 / 签名")
}

internal fun reportContentFingerprint(content: ReportContent): String =
    sha256Hex(CanonicalJson.serialize(content.toJson()))

private fun ReportContent.toJson(): JsonObject = buildJsonObject {
    put("contract_version", contractVersion)
    put("identity", identity.toJson())
    put("audience", audience.name)
    put("private_photo_scope", privatePhotoScope.name)
    put("origin", origin.name)
    put("native_data_hash", nativeIntegrity.dataHash)
    put("import_provenance", importProvenance?.toJson() ?: JsonNull)
    put("status_definitions", buildJsonArray {
        statusDefinitions.forEach { definition -> add(buildJsonObject {
            put("status", definition.status); put("label", definition.label.toJson())
            put("description", definition.description.toJson())
        }) }
    })
    put("summary", buildJsonObject {
        put("room_status_counts", buildJsonArray { summary.roomStatusCounts.forEach { count -> add(buildJsonObject {
            put("room_id", count.roomId); put("status", count.status); put("count", count.count)
        }) } })
        put("adverse_items", buildJsonArray { summary.adverseItems.forEach { item -> add(item.toJson()) } })
        put("pending_remediation_count", summary.pendingRemediationCount)
    })
    put("rooms", buildJsonArray { rooms.forEach { add(it.toJson()) } })
    put("supplements", buildJsonArray { supplements.forEach { supplement -> add(buildJsonObject {
        put("reference", supplement.reference); put("text", supplement.text)
    }) } })
    put("remediations", buildJsonArray { remediations.forEach { remediation -> add(buildJsonObject {
        put("item_id", remediation.itemId); put("urgency", remediation.urgency.name); put("text", remediation.text.toJson())
    }) } })
    put("disclaimer", disclaimer.toJson())
    put("tenant_agreement", tenantAgreement?.toJson() ?: JsonNull)
}

private fun ReportIdentity.toJson() = buildJsonObject {
    put("inspection_id", inspectionId); put("property_id", propertyId); put("property_address", propertyAddress)
    put("property_kind", propertyKind); put("is_boarding_house", isBoardingHouse); put("inspection_type", inspectionType)
    put("scheduled_at", scheduledAt); put("finalized_at", finalizedAt); put("tenancy_reference", tenancyReference)
    put("template_id", templateId); put("template_version", templateVersion); put("template_content_hash", templateContentHash)
}

private fun LegacyImportProvenance.toJson() = buildJsonObject {
    put("source_sha256", sourceSha256); put("normalized_manifest_sha256", normalizedManifestSha256)
    put("mapping_receipt_sha256", mappingReceiptSha256); put("extractor_version", extractorVersion)
    put("source_report_date", sourceReportDate)
}

private fun ReportContentRoom.toJson() = buildJsonObject {
    put("id", id); put("label", label.toJson())
    put("photos", buildJsonArray { photos.forEach { add(it.toJson()) } })
    put("items", buildJsonArray { items.forEach { add(it.toJson()) } })
}

private fun ReportContentItem.toJson() = buildJsonObject {
    put("id", id); put("stable_id", stableId); put("label", label.toJson()); put("status", status)
    put("note", note); put("wear_or_damage", wearOrDamage)
    put("photos", buildJsonArray { photos.forEach { add(it.toJson()) } })
}

private fun ReportContentSummaryItem.toJson() = buildJsonObject {
    put("room_id", roomId); put("item_id", itemId); put("status", status); put("label", label.toJson()); put("note", note)
}

private fun ReportContentPhoto.toJson() = buildJsonObject {
    put("id", id); put("content_hash", contentHash); put("source", source); put("reference", reference)
    put("captured_at", capturedAt); put("privacy", privacy)
}

private fun BilingualText.toJson() = buildJsonObject { put("en", en); put("zh", zh) }
