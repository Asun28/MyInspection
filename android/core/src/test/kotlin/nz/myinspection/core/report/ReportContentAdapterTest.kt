package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.report.content.LegacyImportProvenance
import nz.myinspection.core.report.content.ReportContent
import nz.myinspection.core.report.content.ReportContentProjector

/**
 * The layout engine paginates a [ReportContent] and nothing else, and the snapshot entry point is the
 * adapter. Audience and privacy are decided once, upstream, so the content-side entry point takes neither
 * an [Audience] nor a [ReportOptions]: every call below that composes from content is itself the proof
 * that no audience or privacy argument can reach the paginator.
 */
class ReportContentAdapterTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)
    private val adapter = ReportContentAdapter()

    private fun content(
        audience: Audience,
        options: ReportOptions = ReportOptions(),
        provenance: LegacyImportProvenance? = null,
        report: ReportSnapshot = ReportTestFixtures.report(),
    ): ReportContent = adapter.adapt(report, audience, options, provenance)

    /**
     * The bridge exists to carry layout preconditions, not to re-decide meaning. The fingerprint is a
     * canonical hash of the whole projected graph, so one equality covers every field of it at once.
     */
    @Test
    fun `the layout bridge projects exactly what the shared projector projects`() {
        val projector = ReportContentProjector()
        val report = ReportTestFixtures.report()
        Audience.entries.forEach { audience ->
            listOf(false, true).forEach { includePrivate ->
                val options = ReportOptions(includePrivacyPhotos = includePrivate)
                assertEquals(
                    projector.project(report, audience, options).semanticFingerprint,
                    adapter.adapt(report, audience, options).semanticFingerprint,
                    "the bridge altered the shared projection for $audience, private=$includePrivate",
                )
            }
        }
    }

    /**
     * A room the author declared with neither items nor photographs disappears from the shared projection,
     * which is the right answer for a format that has no headings to strand. The paginator does have one,
     * so the bridge refuses it while the snapshot is still in view rather than dropping it silently.
     */
    @Test
    fun `the bridge refuses a room the projection would silently discard`() {
        val report = ReportTestFixtures.report()
        val bare = report.copy(
            rooms = report.rooms + ReportRoom("room-bare", BilingualText("Bare", "空房"), emptyList(), emptyList()),
        )
        val failure = assertFailsWith<IllegalArgumentException> { adapter.adapt(bare, Audience.LANDLORD) }
        assertTrue(
            failure.message!!.contains("room-bare") && failure.message!!.contains("orphan heading"),
            "expected the room-bare orphan-heading refusal, got: ${failure.message}",
        )
    }

    @Test
    fun `a plan built from content alone follows that content's audience, hash and filtered payload`() {
        Audience.entries.forEach { audience ->
            val content = content(audience)
            val plan = composer.compose(content)

            assertEquals(content.audience, plan.audience)
            assertEquals(content.nativeIntegrity.dataHash, plan.dataHash)
            assertEquals(
                content.remediations.map { it.itemId },
                plan.blocks().filterIsInstance<RemediationBlock>().map { it.itemId },
                "$audience remediation blocks do not mirror the content",
            )
            assertEquals(
                content.rooms.flatMap { it.items }.associate { it.id to it.wearOrDamage },
                plan.blocks().filterIsInstance<ItemRowBlock>().associate { it.itemId to it.wearOrDamage },
                "$audience item rows do not mirror the content judgment column",
            )
            assertEquals(
                content.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
                    .map { it.id }.toSet(),
                plan.blocks().filterIsInstance<ImageSlotBlock>().map { it.photoId }.toSet(),
                "$audience picture set does not mirror the content",
            )
            assertEquals(
                content.tenantAgreement,
                plan.blocks().filterIsInstance<TenantAgreementBlock>().singleOrNull()?.label,
            )
        }
    }

    /**
     * A photo-only room draws its own pictures, and its heading has to leave with the first of them. The
     * opening picture is therefore taken out of the run that follows, which a room holding a single picture
     * cannot tell apart from taking nothing out: the second picture is what makes the two readings differ.
     * Item evidence is nested in its row as thumbnails, so the slots counted here are the room-level ones.
     */
    @Test
    fun `room photography is drawn once each, in content order`() {
        val content = adapter.adapt(reportWithTwoPhotoRoom(), Audience.LANDLORD)
        val plan = composer.compose(content)

        assertEquals(
            content.rooms.flatMap { it.photos }.map { it.id },
            plan.blocks().filterIsInstance<ImageSlotBlock>()
                .filter { it.purpose == ImagePurpose.INLINE }
                .map { it.photoId },
        )
    }

    /**
     * The direct entry point reads a photo-only room's first picture without checking there is one, and it
     * is right not to: the projection never yields a room with neither items nor pictures. A room the
     * privacy filter empties is removed there, and a room that had neither to begin with is refused by the
     * bridge one step earlier, where the author can still be told which room it was. Since [ReportContent]
     * has a private constructor and is reachable only through that projection, the state the layout would
     * fail on is unrepresentable rather than merely unhandled - which is what this pins, for both readers
     * and through the direct entry point that owns no guard of its own.
     */
    @Test
    fun `a room with neither items nor pictures cannot reach the layout through any entry point`() {
        Audience.entries.forEach { audience ->
            val content = adapter.adapt(reportWithPrivatePhotoOnlyRoom(), audience)

            assertTrue(
                content.rooms.none { it.items.isEmpty() && it.photos.isEmpty() },
                "$audience content carries a room the layout would have nothing to draw for",
            )
            assertTrue(content.rooms.none { it.id == PRIVATE_ROOM_ID }, "$audience kept the emptied room")
            val plan = composer.compose(content)
            assertTrue(
                plan.blocks().filterIsInstance<RoomTitleBlock>().none { it.roomId == PRIVATE_ROOM_ID },
                "$audience drew a heading for a room with nothing left under it",
            )
        }
    }

    /** A photo-only room whose only picture is private, so the default projection empties it completely. */
    private fun reportWithPrivatePhotoOnlyRoom(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val snapshot = PhotoSnapshot("ph-ensuite-private", "camera", 1_755_400_000_000L, isRoomLevel = true)
        val photo = ReportPhoto("photo-ensuite-private", snapshot, true, "3.R.1", 1_755_400_000_000L)
        return base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + snapshot),
            rooms = base.rooms +
                ReportRoom(PRIVATE_ROOM_ID, BilingualText("Ensuite", "套间"), emptyList(), listOf(photo)),
        )
    }

    /** A photo-only room carrying two pictures; the fixtures elsewhere give such a room only one. */
    private fun reportWithTwoPhotoRoom(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val snapshots = (1..2).map { index ->
            PhotoSnapshot("ph-ensuite-$index", "camera", 1_755_400_000_000L + index, isRoomLevel = true)
        }
        val photos = snapshots.mapIndexed { index, snapshot ->
            ReportPhoto("photo-ensuite-${index + 1}", snapshot, false, "2.R.${index + 1}", snapshot.exifTimeMs!!)
        }
        return base.copy(
            canonical = base.canonical.copy(photos = base.canonical.photos + snapshots),
            rooms = base.rooms + ReportRoom("room-ensuite", BilingualText("Ensuite", "套间"), emptyList(), photos),
        )
    }

    @Test
    fun `summary rows, cover totals and status counts are the shared content in its own order`() {
        val content = content(Audience.LANDLORD)
        val plan = composer.compose(content)
        val cover = plan.blocks().filterIsInstance<CoverBlock>().single()

        assertEquals(
            content.summary.adverseItems.map { listOf(it.roomId, it.itemId, it.status) },
            plan.blocks().filterIsInstance<SummaryItemBlock>().map { listOf(it.roomId, it.itemId, it.status) },
        )
        assertEquals(content.summary.adverseItems.size, cover.adverseItemCount)
        assertEquals(content.summary.pendingRemediationCount, cover.pendingItemCount)
        assertEquals(
            content.summary.roomStatusCounts.map { listOf(it.roomId, it.status, it.count.toString()) },
            cover.roomStatusCounts.map { listOf(it.roomId, it.status, it.count.toString()) },
        )
    }

    /**
     * Import provenance is a claim about a source document. It is drawn under its own heading and never
     * folded into the footer, whose digest attests the native evidence and only that.
     */
    @Test
    fun `an imported report gains a separately labelled provenance section a native report never has`() {
        val native = composer.compose(content(Audience.LANDLORD))
        val imported = composer.compose(content(Audience.LANDLORD, provenance = PROVENANCE))

        assertTrue(native.blocks().none { it is ProvenanceBlock }, "a native report drew import provenance")
        assertTrue(native.blocks().filterIsInstance<SectionTitleBlock>().none { it.key == PROVENANCE_KEY })
        assertEquals(native.pages.size + 1, imported.pages.size, "the provenance section did not take one page")

        val page = imported.pages.single { page -> page.blocks.any { it.content is ProvenanceBlock } }
        val heading = page.blocks.first().content
        assertEquals(PROVENANCE_KEY, (heading as SectionTitleBlock).key, "provenance is not under its own heading")
        val block = page.blocks.map { it.content }.filterIsInstance<ProvenanceBlock>().single()
        assertEquals(PROVENANCE.sourceSha256, block.sourceSha256)
        assertEquals(PROVENANCE.normalizedManifestSha256, block.normalizedManifestSha256)
        assertEquals(PROVENANCE.mappingReceiptSha256, block.mappingReceiptSha256)
        assertEquals(PROVENANCE.extractorVersion, block.extractorVersion)
        assertEquals(PROVENANCE.sourceReportDate, block.sourceReportDate)

        assertTrue(
            imported.blocks().filterIsInstance<FooterBlock>().all { it.dataHash == native.dataHash },
            "the footer of an imported report attests something other than the native evidence",
        )
    }

    /**
     * Every identifying value the plan carries has to be traceable to the content it was built from. The
     * drawn runs are deliberately not walked: a run is a composition of these fields with the layout's own
     * punctuation, so it is the fields, not the sentences, that this can be an exact statement about.
     * Section titles are the layout's own furniture and are excluded for the same reason.
     */
    @Test
    fun `no field in either audience plan is absent from that plan's input content`() {
        Audience.entries.forEach { audience ->
            val content = content(audience, provenance = PROVENANCE)
            val plan = composer.compose(content)
            val vocabulary = content.vocabulary()

            assertEquals(
                emptyList(),
                plan.blocks().flatMap { it.fields() }.filterNot { it in vocabulary }.distinct(),
                "the $audience plan carries values its content never offered it to draw",
            )
            val captured = content.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
                .map { it.capturedAt }.toSet()
            assertTrue(plan.blocks().filterIsInstance<ImageSlotBlock>().all { it.capturedAt in captured })
            val cover = plan.blocks().filterIsInstance<CoverBlock>().single()
            assertEquals(content.identity.scheduledAt, cover.scheduledAt)
            assertEquals(content.identity.tenancyReference, cover.tenancyReference)
        }
    }

    private fun DocumentPlan.blocks(): List<DocumentBlock> = pages.flatMap { page -> page.blocks.map { it.content } }

    private fun BilingualText.parts(): List<String> = listOf(en, zh)

    private fun ReportContent.vocabulary(): Set<String> = buildSet {
        add(identity.propertyAddress)
        add(identity.inspectionType)
        identity.tenancyReference?.let(::add)
        add(nativeIntegrity.dataHash)
        add(nativeIntegrity.dataHash.take(SHORT_HASH_CHARS))
        statusDefinitions.forEach { addAll(it.label.parts() + it.description.parts() + it.status) }
        rooms.forEach { room ->
            add(room.id)
            addAll(room.label.parts())
            (room.photos + room.items.flatMap { it.photos }).forEach { addAll(listOf(it.id, it.reference, it.source)) }
            room.items.forEach { item ->
                addAll(listOf(item.id, item.status) + item.label.parts())
                item.note?.let(::add)
                item.wearOrDamage?.let(::add)
            }
        }
        supplements.forEach { addAll(listOf(it.reference, it.text)) }
        remediations.forEach { addAll(listOf(it.itemId, it.urgency.name) + it.text.parts()) }
        addAll(disclaimer.parts())
        tenantAgreement?.let { addAll(it.parts()) }
        importProvenance?.let {
            addAll(
                listOfNotNull(
                    it.sourceSha256,
                    it.normalizedManifestSha256,
                    it.mappingReceiptSha256,
                    it.extractorVersion,
                    it.sourceReportDate,
                ),
            )
        }
    }

    private fun DocumentBlock.fields(): List<String> = when (this) {
        is CoverBlock -> listOfNotNull(address, inspectionType, tenancyReference) +
            roomStatusCounts.flatMap { listOf(it.roomId, it.status) }
        is SectionTitleBlock -> emptyList()
        is StatusDefinitionBlock -> listOf(status) + label.parts() + description.parts()
        is SummaryItemBlock -> listOf(itemId, roomId, status)
        is RoomTitleBlock -> listOf(roomId) + label.parts()
        is ItemRowBlock -> listOfNotNull(itemId, status, note, wearOrDamage) + label.parts()
        is ImageSlotBlock -> listOf(photoId, reference, source)
        is RemediationBlock -> listOf(itemId, urgency.name) + text.parts()
        is SupplementBlock -> listOf(reference, text)
        is DisclaimerBlock -> text.parts()
        is TenantAgreementBlock -> label.parts()
        is FooterBlock -> listOf(dataHash, shortHash)
        is ProvenanceBlock -> listOfNotNull(
            sourceSha256,
            normalizedManifestSha256,
            mappingReceiptSha256,
            extractorVersion,
            sourceReportDate,
        )
    }

    /*
     * R4 receipts. Each row is one single-point change to production source, run against the card's DoD
     * command; every file was restored afterwards and its SHA-256 re-checked against the batch baseline.
     * Production digests the batch ran against (the composer source is named without its extension: the
     * purity scan in this package forbids that spelling, and this comment is inside its scan):
     *   composer source          5297b78bf90124bd...
     *   ReportContentAdapter     5e9e4cabc194fb34...
     *   DocumentPlan             3c448d7e9c70c90a...
     *   ReportContentProjector   311a1d956243fa90...  (M16 only; mutated in the tree, never committed)
     *
     * M1  A1  audience <- Audience.LANDLORD instead of content.audience              KILLED
     * M2  A1  footer digest <- semanticFingerprint instead of nativeIntegrity        KILLED
     * M3  A1  remediation list truncated to none                                     KILLED
     * M4  A2  photo-only room redraws the picture that left with its heading         KILLED (2nd pass)
     * M5  A2  cover minimum height 100 -> 90                                         KILLED
     * M6  A3  provenance section never emitted for an imported report                KILLED
     * M7  A3  provenance source hash written into the manifest-hash field            KILLED
     * M8  A3  provenance folded under the closing heading                            KILLED
     * M9  A3  summary rows reversed out of the shared content's order                KILLED
     * M10 A4  cover draws identity.propertyId in place of the address                KILLED
     * M11 A4  tenant agreement replaced by wording the layout invented               KILLED
     * M12 A3  footer attests the imported source hash instead of native evidence     KILLED
     * M13 A5  adapter's orphan-heading refusal short-circuited to always pass        KILLED
     * M14 A1  privacy option dropped on the way to the projection                    KILLED
     * M15 A2  room with items redraws the picture that opened it                     KILLED
     * M16 A5  projector's rendered capture-time guard relaxed to > -1                KILLED
     * M17 A5  projection stops dropping a room left with neither items nor photos    KILLED
     *
     * M4 survived the first pass: nothing then distinguished a photo-only room's opening picture from the
     * run that follows it, because every such fixture in the suite holds exactly one picture. The room
     * photography test above and its two-picture fixture were added for it, and M15 was added with it to
     * cover the same cut in the branch for rooms that do have items.
     *
     * M16 and M17 mutate ReportContentProjector, which this card does not change. R3 round 1 asked whether
     * the direct entry point can meet a room it cannot draw; the answer is that the projection makes that
     * state unrepresentable, and an unreachable guard in the layout would be one no mutation could kill.
     * These two receipts are what stands in for it: they show the upstream guards are live and that this
     * suite still fails when either is removed.
     */
    private companion object {
        const val PROVENANCE_KEY = "provenance"
        const val PRIVATE_ROOM_ID = "room-ensuite-private"

        /** The footer draws a prefix of the digest; the vocabulary has to admit that prefix, not a new value. */
        const val SHORT_HASH_CHARS = 12
        val PROVENANCE = LegacyImportProvenance(
            sourceSha256 = "a".repeat(64),
            normalizedManifestSha256 = "b".repeat(64),
            mappingReceiptSha256 = "c".repeat(64),
            extractorVersion = "extractor-1.0.0",
            sourceReportDate = "2026-03-01",
        )
    }
}
