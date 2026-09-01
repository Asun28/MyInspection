package nz.myinspection.core.report.content

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nz.myinspection.core.report.Audience
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.REPORT_DISCLAIMER
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportPhoto
import nz.myinspection.core.report.ReportRoom
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.report.ReportTestFixtures

/**
 * Breaks named by this suite:
 * - a renderer receives a different ordering or semantic subset;
 * - tenant or privacy filtering happens after serialization;
 * - the parity fingerprint ignores included report meaning or includes excluded landlord meaning;
 * - a filtered artifact is mislabeled as a hash of only its visible subset;
 * - malformed projection/provenance crosses the shared renderer boundary.
 */
class ReportContentProjectorTest {
    private val projector = ReportContentProjector()

    @Test
    fun `projects one ordered semantic report with separately labelled provenance`() {
        val report = privatePhotoReport()
        val content = projector.project(report, Audience.LANDLORD, provenance = provenance())

        assertEquals(
            ReportIdentity(
                inspectionId = "insp-0001",
                propertyId = "prop-0001",
                propertyAddress = "12 Aroha Ave, Auckland",
                propertyKind = "RENTAL",
                isBoardingHouse = false,
                inspectionType = "ROUTINE",
                scheduledAt = 1_755_302_400_000L,
                finalizedAt = 1_755_309_600_000L,
                tenancyReference = "TENANCY-42",
                templateId = "tpl-routine-v3",
                templateVersion = 3,
                templateContentHash = "template-hash-1",
            ),
            content.identity,
        )
        assertEquals(Audience.LANDLORD, content.audience)
        assertEquals(1, content.contractVersion)
        assertEquals(PrivatePhotoScope.EXCLUDED, content.privatePhotoScope)
        assertEquals(ReportOrigin.LEGACY_DOCX_IMPORT, content.origin)
        assertEquals(
            NativeIntegrity("ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d"),
            content.nativeIntegrity,
        )
        assertEquals(provenance(), content.importProvenance)
        assertEquals(report.statusDefinitions, content.statusDefinitions)
        assertEquals(
            ReportContentSummary(
                roomStatusCounts = listOf(
                    ReportContentRoomStatusCount("room-kitchen", "GOOD", 1),
                    ReportContentRoomStatusCount("room-kitchen", "POOR", 1),
                ),
                adverseItems = listOf(
                    ReportContentSummaryItem(
                        "room-kitchen",
                        "item-poor",
                        "POOR",
                        BilingualText("Carpet", "地毯"),
                        "墙面有刮痕，需重新粉刷",
                    ),
                ),
                pendingRemediationCount = 1,
            ),
            content.summary,
        )
        assertEquals(
            listOf("room-kitchen" to BilingualText("Kitchen / Lounge", "厨房 / 客厅")),
            content.rooms.map { it.id to it.label },
        )
        assertEquals(
            listOf(
                listOf("item-good", "kitchen.wall.paint", "Wall paint", "墙面油漆", "GOOD", null, null),
                listOf("item-poor", "lounge.carpet", "Carpet", "地毯", "POOR", "墙面有刮痕，需重新粉刷", "DAMAGE"),
            ),
            content.rooms.single().items.map {
                listOf(it.id, it.stableId, it.label.en, it.label.zh, it.status, it.note, it.wearOrDamage)
            },
        )
        assertEquals(
            listOf(ReportContentPhoto("photo-room", "ph-hash-2", "imported", "1.R.1", 1_755_303_200_000L, false)),
            content.allPhotos(),
        )
        assertEquals(report.supplements, content.supplements)
        assertEquals(report.remediations, content.remediations)
        assertEquals(REPORT_DISCLAIMER, content.disclaimer)
        assertNull(content.tenantAgreement)
        assertTrue(Regex("[0-9a-f]{64}").matches(content.semanticFingerprint))
    }

    @Test
    fun `audience and privacy are removed before content reaches either renderer`() {
        val report = privatePhotoReportWithTenantVocabulary()
        val landlord = projector.project(report, Audience.LANDLORD)
        val tenant = projector.project(report, Audience.TENANT)
        val tenantWithPrivatePhoto = projector.project(
            report,
            Audience.TENANT,
            options = ReportOptions(includePrivacyPhotos = true),
        )

        assertFalse(landlord.allPhotos().any { it.id == "photo-item" })
        assertFalse(tenant.allPhotos().any { it.id == "photo-item" })
        assertEquals(listOf("photo-room", "photo-item"), tenantWithPrivatePhoto.allPhotos().map { it.id })
        assertTrue(tenantWithPrivatePhoto.allPhotos().single { it.id == "photo-item" }.privacy)
        assertEquals(PrivatePhotoScope.EXPLICITLY_INCLUDED, tenantWithPrivatePhoto.privatePhotoScope)

        assertEquals(listOf("item-poor"), landlord.remediations.map { it.itemId })
        assertEquals(1, landlord.summary.pendingRemediationCount)
        assertEquals("DAMAGE", landlord.rooms.single().items.last().wearOrDamage)
        assertTrue(tenant.remediations.isEmpty())
        assertNull(tenant.summary.pendingRemediationCount)
        assertNull(tenant.rooms.single().items.last().wearOrDamage)
        assertEquals("DAMAGE remediation 建议 HIGH", tenant.rooms.single().items.last().note)
        assertEquals(BilingualText("Tenant agreement / signature", "租客同意 / 签名"), tenant.tenantAgreement)
        assertNull(landlord.tenantAgreement)
    }

    @Test
    fun `native integrity always attests the full canonical snapshot not the visible photo subset`() {
        val report = privatePhotoReport()
        val withoutPrivate = projector.project(report, Audience.LANDLORD)
        val withPrivate = projector.project(
            report,
            Audience.LANDLORD,
            options = ReportOptions(includePrivacyPhotos = true),
        )

        val expectedNativeHash = "ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d"
        assertEquals(expectedNativeHash, withoutPrivate.nativeIntegrity.dataHash)
        assertEquals(expectedNativeHash, withPrivate.nativeIntegrity.dataHash)
        assertNotEquals(withoutPrivate.semanticFingerprint, withPrivate.semanticFingerprint)
    }

    @Test
    fun `golden fingerprints and named mutations cover the complete semantic contract`() {
        val report = privatePhotoReport()
        val first = projector.project(report, Audience.LANDLORD, provenance = provenance())
        val repeated = projector.project(report, Audience.LANDLORD, provenance = provenance())
        val tenant = projector.project(report, Audience.TENANT, provenance = provenance())
        val room = report.rooms.single()
        val mutations = listOf(
            "identity" to report.copy(tenancyReference = "TENANCY-43"),
            "glossary" to report.copy(statusDefinitions = report.statusDefinitions.map {
                if (it.status == "GOOD") it.copy(description = BilingualText("Changed", "已更改")) else it
            }),
            "room and summary identity" to report.copy(rooms = listOf(room.copy(id = "room-kitchen-2"))),
            "item identity" to report.copy(rooms = listOf(room.copy(items = room.items.map {
                if (it.id == "item-good") it.copy(id = "item-good-2") else it
            }))),
            "room label" to report.copy(rooms = listOf(room.copy(label = BilingualText("Changed room", "已更改房间")))),
            "item label" to report.copy(rooms = listOf(room.copy(items = room.items.map {
                if (it.id == "item-good") it.copy(label = BilingualText("Changed item", "已更改项目")) else it
            }))),
            "photo reference" to report.copy(rooms = listOf(room.copy(photos = room.photos.map {
                it.copy(reference = "1.R.2")
            }))),
            "supplement deletion" to report.copy(supplements = emptyList()),
            "remediation deletion" to report.copy(remediations = emptyList()),
        )

        assertEquals(first.semanticFingerprint, repeated.semanticFingerprint)
        assertEquals("bf723b08cf75fa0357666cec63af223d566fb2984c5aacd6638636159bce6e9a", first.semanticFingerprint)
        assertEquals("bc92f38d2685c048ee07b1582f9b8e9d67a30bde684a438a2861f85d954cc662", tenant.semanticFingerprint)
        mutations.forEach { (name, changed) ->
            assertNotEquals(
                first.semanticFingerprint,
                projector.project(changed, Audience.LANDLORD, provenance = provenance()).semanticFingerprint,
                "$name was omitted from the semantic fingerprint",
            )
        }
        assertNotEquals(
            first.semanticFingerprint,
            projector.project(report, Audience.LANDLORD, provenance = provenance().copy(sourceSha256 = "d".repeat(64)))
                .semanticFingerprint,
            "provenance was omitted from the semantic fingerprint",
        )
        val changedRemediation = report.copy(remediations = report.remediations.map {
            it.copy(text = BilingualText("Replace carpet", "更换地毯"))
        })
        assertEquals(
            tenant.semanticFingerprint,
            projector.project(changedRemediation, Audience.TENANT, provenance = provenance()).semanticFingerprint,
            "landlord-only remediation changed the tenant semantic fingerprint",
        )
    }

    @Test
    fun `malformed projection is rejected instead of becoming divergent renderer input`() {
        val report = privatePhotoReport()
        val room = report.rooms.single()
        val duplicateItemId = report.copy(
            rooms = listOf(room.copy(items = listOf(room.items.first(), room.items.last().copy(id = room.items.first().id)))),
        )
        val mismatchedCanonical = report.copy(
            rooms = listOf(
                room.copy(
                    items = listOf(room.items.first(), room.items.last().copy(snapshot = room.items.last().snapshot.copy(status = "GOOD"))),
                ),
            ),
        )

        assertEquals(
            "duplicate report item id",
            assertFailsWith<IllegalArgumentException> {
                projector.project(duplicateItemId, Audience.LANDLORD)
            }.message,
        )
        assertEquals(
            "report items do not match canonical snapshot",
            assertFailsWith<IllegalArgumentException> {
                projector.project(mismatchedCanonical, Audience.LANDLORD)
            }.message,
        )
    }

    @Test
    fun `legacy provenance accepts only bounded separately named integrity claims`() {
        val shortSourceHash = provenance().copy(sourceSha256 = "abc")
        val uppercaseMappingHash = provenance().copy(mappingReceiptSha256 = "B".repeat(64))
        val blankExtractor = provenance().copy(extractorVersion = " ")

        listOf(shortSourceHash, uppercaseMappingHash, blankExtractor).forEach { malformed ->
            assertFailsWith<IllegalArgumentException> {
                projector.project(privatePhotoReport(), Audience.LANDLORD, provenance = malformed)
            }
        }
    }

    @Test
    fun `privacy filtering removes a photo-only room instead of leaving an orphan heading`() {
        val base = privatePhotoReport()
        val extraSnapshot = base.canonical.photos.single { it.isRoomLevel }
            .copy(contentHash = "private-room-hash")
        val privateOnlyRoom = ReportRoom(
            id = "room-private-only",
            label = BilingualText("Stored belongings", "存放物品"),
            items = emptyList(),
            photos = listOf(
                ReportPhoto(
                    id = "photo-private-room",
                    snapshot = extraSnapshot,
                    privacy = true,
                    reference = "2.R.1",
                    capturedAt = 1_755_303_300_000L,
                ),
            ),
        )
        val report = base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + extraSnapshot),
            rooms = base.rooms + privateOnlyRoom,
        )

        assertEquals(
            listOf("room-kitchen"),
            projector.project(report, Audience.LANDLORD).rooms.map { it.id },
        )
        assertEquals(
            listOf("room-kitchen", "room-private-only"),
            projector.project(report, Audience.LANDLORD, ReportOptions(true)).rooms.map { it.id },
        )
    }

    @Test
    fun `projected container lists cannot be mutated after fingerprinting`() {
        val source = privatePhotoReport()
        val itemPhotos = source.rooms.single().items.last().photos.toMutableList()
        val items = source.rooms.single().items.map {
            if (it.id == "item-poor") it.copy(photos = itemPhotos) else it
        }.toMutableList()
        val roomPhotos = source.rooms.single().photos.toMutableList()
        val rooms = mutableListOf(source.rooms.single().copy(items = items, photos = roomPhotos))
        val definitions = source.statusDefinitions.toMutableList()
        val supplements = source.supplements.toMutableList()
        val remediations = source.remediations.toMutableList()
        val content = projector.project(
            source.copy(
                rooms = rooms,
                statusDefinitions = definitions,
                supplements = supplements,
                remediations = remediations,
            ),
            Audience.LANDLORD,
            options = ReportOptions(includePrivacyPhotos = true),
        )
        val fingerprint = content.semanticFingerprint

        itemPhotos.clear()
        items.clear()
        roomPhotos.clear()
        rooms.clear()
        definitions.clear()
        supplements.clear()
        remediations.clear()

        assertTrue(
            ReportContent::class.java.declaredConstructors.filterNot { it.isSynthetic }
                .all { Modifier.isPrivate(it.modifiers) },
        )
        assertImmutable(content.statusDefinitions)
        assertImmutable(content.summary.roomStatusCounts)
        assertImmutable(content.summary.adverseItems)
        assertImmutable(content.rooms)
        assertImmutable(content.rooms.single().photos)
        assertImmutable(content.rooms.single().items)
        assertImmutable(content.rooms.single().items.single { it.id == "item-poor" }.photos)
        assertImmutable(content.supplements)
        assertImmutable(content.remediations)
        assertEquals(listOf("room-kitchen"), content.rooms.map { it.id })
        assertEquals(listOf("photo-room", "photo-item"), content.allPhotos().map { it.id })
        assertEquals(fingerprint, content.semanticFingerprint)
    }

    private fun privatePhotoReport(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val room = base.rooms.single()
        val item = room.items.last()
        return base.copy(
            rooms = listOf(room.copy(items = room.items.dropLast(1) + item.copy(photos = item.photos.map { it.copy(privacy = true) }))),
        )
    }

    private fun privatePhotoReportWithTenantVocabulary(): ReportSnapshot {
        val base = privatePhotoReport()
        val room = base.rooms.single()
        val changedItem = room.items.last().copy(
            snapshot = room.items.last().snapshot.copy(note = "DAMAGE remediation 建议 HIGH"),
        )
        return base.copy(
            canonical = base.canonical.copy(items = listOf(room.items.first().snapshot, changedItem.snapshot)),
            rooms = listOf(room.copy(items = listOf(room.items.first(), changedItem))),
        )
    }

    private fun provenance() = LegacyImportProvenance(
        sourceSha256 = "a".repeat(64),
        normalizedManifestSha256 = "b".repeat(64),
        mappingReceiptSha256 = "c".repeat(64),
        extractorVersion = "docx-report-v1",
        sourceReportDate = "2025-08-16",
    )

    private fun ReportContent.allPhotos(): List<ReportContentPhoto> =
        rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }

    private fun <T> assertImmutable(values: List<T>) {
        assertFailsWith<UnsupportedOperationException> { (values as MutableList<T>).clear() }
    }
}
