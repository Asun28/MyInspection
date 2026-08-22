package nz.myinspection.core.report

import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex
import nz.myinspection.core.capture.AdverseStatuses
import nz.myinspection.core.template.TemplateDomains
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Pure Kotlin layout engine. Renderers consume measured runs and slots without wrapping or pagination. */
class ReportComposer(private val textMeasurer: TextMeasurer) {
    fun compose(report: ReportSnapshot, audience: Audience, options: ReportOptions = ReportOptions()): DocumentPlan {
        validateProjection(report)
        val dataHash = sha256Hex(canonicalJson(report.canonical))
        val photos = report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
            .filter { options.includePrivacyPhotos || !it.privacy }
        val adverseItems = report.rooms.flatMap { room ->
            room.items.filter { AdverseStatuses.isAdverse(report.canonical.type, it.snapshot.status) }
                .map { room.id to it }
        }
        val pages = mutableListOf<MutableList<PlacedBlock>>()
        val paginator = Paginator(pages)

        paginator.forcePage(listOf(coverBlock(report, adverseItems)))
        paginator.startSection(
            sectionTitle("status-glossary", BilingualText("Status glossary", "评级词表")),
            report.statusDefinitions.map(::statusBlock),
        )
        paginator.startSection(
            sectionTitle("summary", BilingualText("Summary", "摘要")),
            adverseItems.map { (roomId, item) -> summaryBlock(roomId, item) },
        )

        if (report.rooms.isNotEmpty()) paginator.newPage()
        for (room in report.rooms) {
            val visibleRoomPhotos = room.photos.filter { it in photos }
            val title = roomTitleBlock(room)
            if (room.items.isEmpty()) {
                // A heading whose room has nothing to show is an orphan by construction, so the projection
                // must not contain one. When there is photography, the heading travels with its first picture:
                // otherwise the heading can end a page and its only content start the next one.
                require(visibleRoomPhotos.isNotEmpty()) {
                    "room ${room.id} has neither items nor visible photos; it would render as an orphan heading"
                }
                val firstPhoto = imageBlock(visibleRoomPhotos.first(), ImagePurpose.INLINE)
                paginator.addGroup(listOf(title, firstPhoto))
                visibleRoomPhotos.drop(1).forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
                continue
            }
            val firstPhoto = visibleRoomPhotos.firstOrNull()?.let { imageBlock(it, ImagePurpose.INLINE) }
            val openingFixedHeight = title.heightMm + (firstPhoto?.heightMm ?: 0)
            val firstItem = itemBlock(room.items.first(), audience, room.items.first().photos.filter { it in photos })
            val firstItemChunks = splitBlock(firstItem, BODY_HEIGHT_MM - openingFixedHeight)
            paginator.addGroup(listOfNotNull(title, firstPhoto, firstItemChunks.first()))
            firstItemChunks.drop(1).forEach(paginator::add)
            visibleRoomPhotos.drop(1).forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
            for (item in room.items.drop(1)) {
                paginator.add(itemBlock(item, audience, item.photos.filter { it in photos }))
            }
        }

        photos.map { imageBlock(it, ImagePurpose.APPENDIX) }.chunked(2).forEach { pair ->
            paginator.forcePage(
                listOf(sectionTitle("photo-appendix", BilingualText("Photo appendix", "照片附录"))) + pair,
            )
        }

        val closing = buildList {
            if (audience == Audience.LANDLORD) addAll(report.remediations.map(::remediationBlock))
            addAll(report.supplements.map(::supplementBlock))
            add(disclaimerBlock())
            if (audience == Audience.TENANT) add(tenantAgreementBlock())
        }
        paginator.startSection(sectionTitle("closing", BilingualText("Closing", "报告结尾")), closing)

        val totalPages = pages.size
        val completed = pages.mapIndexed { index, blocks ->
            PagePlan(index + 1, blocks + footerBlock(dataHash, index + 1, totalPages))
        }
        return DocumentPlan(audience, dataHash, completed)
    }

    private fun validateProjection(report: ReportSnapshot) {
        require(report.rooms.map { it.id }.toSet().size == report.rooms.size) { "duplicate room id" }
        val items = report.rooms.flatMap { it.items }
        val photos = report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
        require(items.map { it.id }.toSet().size == items.size) { "duplicate report item id" }
        require(photos.map { it.id }.toSet().size == photos.size) { "duplicate report photo id" }
        require(photos.all { it.id.isNotBlank() && it.reference.isNotBlank() && it.capturedAt > 0 }) {
            "report photos require an id, reference and positive capture time"
        }
        // Every back-reference the renderer prints has to resolve. Blank identifiers and duplicate photo
        // references still satisfy the multiset checks above, yet produce a report whose evidence cannot be
        // traced back to anything - the one thing the reference is for.
        require(report.rooms.all { it.id.isNotBlank() }) { "report rooms require a non-blank id" }
        require(items.all { it.id.isNotBlank() }) { "report items require a non-blank id" }
        require(photos.map { it.reference }.toSet().size == photos.size) { "duplicate report photo reference" }
        // Room-level and item-level photography are different evidence. Nesting that contradicts the canonical
        // flag misfiles it, and the misfiling is invisible in the rendered output.
        require(report.rooms.all { room -> room.photos.all { it.snapshot.isRoomLevel } }) {
            "a room-level photo slot holds a photo whose canonical isRoomLevel is false"
        }
        require(report.rooms.all { room -> room.items.all { item -> item.photos.none { it.snapshot.isRoomLevel } } }) {
            "an item photo slot holds a photo whose canonical isRoomLevel is true"
        }
        require(multiset(items.map { it.snapshot }) == multiset(report.canonical.items)) {
            "report items do not match canonical snapshot"
        }
        require(multiset(photos.map { it.snapshot }) == multiset(report.canonical.photos)) {
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
    }

    private fun <T> multiset(values: List<T>): Map<T, Int> = values.groupingBy { it }.eachCount()

    private fun coverBlock(report: ReportSnapshot, adverseItems: List<Pair<String, ReportItem>>): SizedBlock {
        // Pending work is the number of distinct items carrying a remediation, not a second copy of the
        // adverse count. They differ whenever an item is adverse but has no remediation yet, or vice versa.
        val pendingItemCount = report.remediations.map { it.itemId }.distinct().size
        val counts = report.rooms.flatMap { room ->
            room.items.groupingBy { it.snapshot.status }.eachCount().entries.map {
                RoomStatusCount(room.id, it.key, it.value)
            }
        }
        val lines = buildList {
            addAll(runs(report.canonical.property.address, TextLanguage.ORIGINAL, TextStyle.TITLE, 180))
            addAll(
                runs(
                    "${report.canonical.type} · ${isoUtc(report.canonical.scheduledAt)}",
                    TextLanguage.NEUTRAL,
                    TextStyle.BODY,
                    BODY_WIDTH_MM,
                    endY(),
                ),
            )
            report.tenancyReference?.let {
                addAll(runs(it, TextLanguage.NEUTRAL, TextStyle.BODY, BODY_WIDTH_MM, endY()))
            }
            // The totals have to be drawn, not merely carried as metadata: a cover that silently omits them
            // is exactly as wrong as one that computes them incorrectly, and only the drawn runs are read.
            addAll(runs("Adverse items / 不利项：${adverseItems.size}", TextLanguage.NEUTRAL, TextStyle.BODY, BODY_WIDTH_MM, endY()))
            addAll(runs("Pending remediation / 待处理：$pendingItemCount", TextLanguage.NEUTRAL, TextStyle.BODY, BODY_WIDTH_MM, endY()))
            counts.forEach {
                addAll(runs("${it.roomId} · ${it.status} · ${it.count}", TextLanguage.NEUTRAL, TextStyle.BODY, BODY_WIDTH_MM, endY()))
            }
        }
        return sized(
            CoverBlock(
                report.canonical.property.address,
                report.canonical.type,
                report.canonical.scheduledAt,
                report.tenancyReference,
                adverseItems.size,
                pendingItemCount,
                counts,
                lines,
            ),
            100,
        )
    }

    private fun sectionTitle(key: String, title: BilingualText): SizedBlock {
        val textRuns = bilingualRuns(title, TextStyle.TITLE, 180)
        return sized(SectionTitleBlock(key, title, textRuns), 10)
    }

    private fun statusBlock(definition: StatusDefinition): SizedBlock {
        val labelRuns = bilingualRuns(definition.label, TextStyle.BODY, 180)
        val textRuns = labelRuns + bilingualRuns(definition.description, TextStyle.CAPTION, 180, labelRuns.endY())
        return sized(StatusDefinitionBlock(definition.status, definition.label, definition.description, textRuns), 20)
    }

    private fun summaryBlock(roomId: String, item: ReportItem): SizedBlock {
        val textRuns = runs("$roomId · ${item.id} · ${item.snapshot.status}", TextLanguage.NEUTRAL, TextStyle.BODY, 180)
        return sized(SummaryItemBlock(item.id, roomId, item.snapshot.status, textRuns), 16)
    }

    private fun roomTitleBlock(room: ReportRoom): SizedBlock {
        val textRuns = bilingualRuns(room.label, TextStyle.TITLE, 180)
        return sized(RoomTitleBlock(room.id, room.label, textRuns), 12)
    }

    /**
     * One row of the item table. When the item has visible photos the text column narrows to
     * [ITEM_TEXT_WIDTH_MM] and the pictures occupy a fixed [INLINE_THUMB_MM] column on the right, so the
     * renderer draws a table with a picture column rather than text followed by loose full-width images.
     */
    private fun itemBlock(item: ReportItem, audience: Audience, visiblePhotos: List<ReportPhoto>): SizedBlock {
        val visibleJudgment = item.snapshot.wearOrDamage.takeIf { audience == Audience.LANDLORD }
        val textWidth = if (visiblePhotos.isEmpty()) BODY_WIDTH_MM else ITEM_TEXT_WIDTH_MM
        val textRuns = buildList {
            addAll(bilingualRuns(item.label, TextStyle.BODY, textWidth))
            addAll(
                runs(
                    listOfNotNull(item.snapshot.status, visibleJudgment).joinToString(" · "),
                    TextLanguage.NEUTRAL,
                    TextStyle.BODY,
                    textWidth,
                    endY(),
                ),
            )
            item.snapshot.note?.takeIf { it.isNotBlank() }?.let {
                addAll(runs(it, TextLanguage.ORIGINAL, TextStyle.BODY, textWidth, endY()))
            }
        }
        var thumbY = 0
        val thumbnails = visiblePhotos.map { photo ->
            val slot = imageSlot(
                photo,
                ImagePurpose.INLINE,
                x = THUMB_COLUMN_X_MM,
                y = thumbY,
                widthMm = INLINE_THUMB_MM,
                imageHeightMm = INLINE_THUMB_MM,
            )
            thumbY += slot.heightMm + THUMB_GAP_MM
            slot
        }
        val thumbnailHeight = thumbnails.lastOrNull()?.let { it.yMm + it.heightMm } ?: 0
        val block = ItemRowBlock(
            item.id,
            item.label,
            item.snapshot.status,
            item.snapshot.note,
            visibleJudgment,
            textRuns,
            thumbnails,
        )
        return SizedBlock(maxOf(18, textRuns.endY() + 2, thumbnailHeight), block, 18)
    }

    /** Room-level photography: a full-width picture that stands on its own between item rows. */
    private fun imageBlock(photo: ReportPhoto, purpose: ImagePurpose): SizedBlock {
        val imageHeight = if (purpose == ImagePurpose.INLINE) 44 else 116
        val slot = imageSlot(photo, purpose, x = 0, y = 0, widthMm = BODY_WIDTH_MM, imageHeightMm = imageHeight)
        return SizedBlock(slot.heightMm, slot, slot.heightMm)
    }

    /**
     * One image slot with its geometry resolved. The caption is capped at [MAX_CAPTION_LINES] measured lines
     * with an explicit elision marker: an image slot must never be splittable, and the only way an image slot
     * can outgrow a page is an unbounded caption, so the bound lives here rather than in the paginator.
     * The structural fields keep the full reference regardless of what the caption shows.
     */
    private fun imageSlot(
        photo: ReportPhoto,
        purpose: ImagePurpose,
        x: Int,
        y: Int,
        widthMm: Int,
        imageHeightMm: Int,
    ): ImageSlotBlock {
        val capturedAt = photo.snapshot.exifTimeMs ?: photo.capturedAt
        val caption = "${photo.reference} · ${photo.snapshot.source} · ${isoUtc(capturedAt)}"
        val measured = textMeasurer.measure(caption, TextStyle.CAPTION, widthMm)
        val kept = measured.lines.take(MAX_CAPTION_LINES).toMutableList()
        if (measured.lines.size > MAX_CAPTION_LINES) kept[kept.lastIndex] = kept.last() + CAPTION_ELISION
        val textRuns = kept.mapIndexed { index, line ->
            TextRun(
                line,
                TextLanguage.NEUTRAL,
                TextStyle.CAPTION,
                x,
                y + imageHeightMm + index * measured.lineHeightMm,
                widthMm,
                measured.lineHeightMm,
            )
        }
        val height = imageHeightMm + kept.size * measured.lineHeightMm + 2
        return ImageSlotBlock(
            photoId = photo.id,
            purpose = purpose,
            reference = photo.reference,
            source = photo.snapshot.source,
            capturedAt = capturedAt,
            textRuns = textRuns,
            xMm = x,
            yMm = y,
            widthMm = widthMm,
            heightMm = height,
        )
    }

    /** Locale-independent, fixed-offset rendering. Renderers must never be handed raw epoch milliseconds. */
    private fun isoUtc(epochMillis: Long): String = ISO_UTC.format(Instant.ofEpochMilli(epochMillis))

    private fun remediationBlock(remediation: ReportRemediation): SizedBlock {
        val textRuns = buildList {
            addAll(bilingualRuns(remediation.text, TextStyle.BODY, 180))
            addAll(runs(remediation.urgency.name, TextLanguage.NEUTRAL, TextStyle.BODY, 180, endY()))
        }
        return sized(RemediationBlock(remediation.itemId, remediation.urgency, remediation.text, textRuns), 20)
    }

    private fun supplementBlock(supplement: ReportSupplement): SizedBlock {
        val textRuns = buildList {
            addAll(runs(supplement.reference, TextLanguage.NEUTRAL, TextStyle.CAPTION, 180))
            addAll(runs(supplement.text, TextLanguage.ORIGINAL, TextStyle.BODY, 180, endY()))
        }
        return sized(SupplementBlock(supplement.reference, supplement.text, textRuns), 14)
    }

    private fun disclaimerBlock(): SizedBlock {
        val textRuns = bilingualRuns(REPORT_DISCLAIMER, TextStyle.CAPTION, 180)
        return sized(DisclaimerBlock(REPORT_DISCLAIMER, textRuns), 24)
    }

    private fun tenantAgreementBlock(): SizedBlock {
        val label = BilingualText("Tenant agreement / signature", "租客同意 / 签名")
        val textRuns = bilingualRuns(label, TextStyle.BODY, 180)
        return sized(TenantAgreementBlock(label, textRuns), 24)
    }

    private fun footerBlock(dataHash: String, page: Int, totalPages: Int): PlacedBlock {
        // The drawn footer carries the short hash; the full digest stays in FooterBlock.dataHash for
        // verification. Printing all 64 characters was never the contract and does not fit the footer.
        val shortHash = dataHash.take(SHORT_HASH_LENGTH)
        val textRuns = runs("$shortHash · $page/$totalPages", TextLanguage.NEUTRAL, TextStyle.CAPTION, BODY_WIDTH_MM)
        return PlacedBlock(
            PAGE_MARGIN_MM,
            BODY_BOTTOM_MM,
            A4_WIDTH_MM - 2 * PAGE_MARGIN_MM,
            10,
            FooterBlock(dataHash, shortHash, page, totalPages, textRuns),
        )
    }

    private fun bilingualRuns(text: BilingualText, style: TextStyle, widthMm: Int, startY: Int = 0): List<TextRun> {
        val en = runs(text.en, TextLanguage.EN, style, widthMm, startY)
        return en + runs(text.zh, TextLanguage.ZH, style, widthMm, en.endY())
    }

    private fun runs(
        text: String,
        language: TextLanguage,
        style: TextStyle,
        widthMm: Int,
        startY: Int = 0,
    ): List<TextRun> {
        val measured = textMeasurer.measure(text, style, widthMm)
        return measured.lines.mapIndexed { index, line ->
            TextRun(line, language, style, 0, startY + index * measured.lineHeightMm, widthMm, measured.lineHeightMm)
        }
    }

    private fun List<TextRun>.endY(): Int = maxOfOrNull { it.yMm + it.heightMm } ?: 0

    private fun sized(content: TextBearingBlock, minimumHeightMm: Int): SizedBlock = SizedBlock(
        maxOf(minimumHeightMm, content.textRuns.endY() + 2),
        content,
        minimumHeightMm,
    )

    private fun splitBlock(block: SizedBlock, maxHeightMm: Int = BODY_HEIGHT_MM): List<SizedBlock> {
        require(maxHeightMm > 0) { "no page space remains for block" }
        if (block.heightMm <= maxHeightMm) return listOf(block)
        require(block.content !is ImageSlotBlock) {
            // Splitting would emit two slots carrying the same photoId: the same photograph printed twice,
            // each with part of its caption. Captions are capped at composition time so this cannot happen.
            "image slots are indivisible and must never exceed the page body"
        }
        val content = block.content as? TextBearingBlock
            ?: throw IllegalArgumentException("indivisible block exceeds page body")
        val paired = content.textRuns.filter { it.language == TextLanguage.EN || it.language == TextLanguage.ZH }
        val flowing = content.textRuns.filterNot { it in paired }
        require(paired.endY() + 2 <= maxHeightMm && flowing.isNotEmpty()) {
            "indivisible bilingual content exceeds page body"
        }
        val chunks = mutableListOf<List<TextRun>>()
        var current = paired.toMutableList()
        var currentHeight = current.endY()
        for (run in flowing) {
            if (current.isNotEmpty() && currentHeight + run.heightMm + 2 > maxHeightMm) {
                chunks += rebase(current)
                current = mutableListOf()
                currentHeight = 0
            }
            current += run.copy(yMm = currentHeight)
            currentHeight += run.heightMm
        }
        if (current.isNotEmpty()) chunks += rebase(current)
        return chunks.map { chunk ->
            val updated = content.withRuns(chunk)
            SizedBlock(maxOf(block.minHeightMm, chunk.endY() + 2), updated, block.minHeightMm)
        }
    }

    private fun rebase(runs: List<TextRun>): List<TextRun> {
        var y = 0
        return runs.map { run -> run.copy(yMm = y).also { y += run.heightMm } }
    }

    private fun TextBearingBlock.withRuns(runs: List<TextRun>): TextBearingBlock = when (this) {
        is CoverBlock -> copy(textRuns = runs)
        is SectionTitleBlock -> copy(textRuns = runs)
        is StatusDefinitionBlock -> copy(textRuns = runs)
        is SummaryItemBlock -> copy(textRuns = runs)
        is RoomTitleBlock -> copy(textRuns = runs)
        is ItemRowBlock -> copy(textRuns = runs)
        is ImageSlotBlock -> copy(textRuns = runs)
        is RemediationBlock -> copy(textRuns = runs)
        is SupplementBlock -> copy(textRuns = runs)
        is DisclaimerBlock -> copy(textRuns = runs)
        is TenantAgreementBlock -> copy(textRuns = runs)
        is FooterBlock -> copy(textRuns = runs)
    }

    private data class SizedBlock(val heightMm: Int, val content: DocumentBlock, val minHeightMm: Int = heightMm)

    private inner class Paginator(private val pages: MutableList<MutableList<PlacedBlock>>) {
        fun newPage() {
            if (pages.lastOrNull()?.isEmpty() != true) pages.add(mutableListOf())
        }

        fun forcePage(blocks: List<SizedBlock>) {
            newPage()
            blocks.forEach(::add)
        }

        fun startSection(title: SizedBlock, blocks: List<SizedBlock>) {
            newPage()
            if (blocks.isEmpty()) {
                add(title)
                return
            }
            val firstChunks = splitBlock(blocks.first(), BODY_HEIGHT_MM - title.heightMm)
            addGroup(listOf(title, firstChunks.first()))
            firstChunks.drop(1).forEach(::add)
            blocks.drop(1).forEach(::add)
        }

        fun add(block: SizedBlock) {
            splitBlock(block).forEach { chunk ->
                var page = pages.lastOrNull()
                var y = page?.bodyEnd() ?: PAGE_MARGIN_MM
                if (page == null || y + chunk.heightMm > BODY_BOTTOM_MM) {
                    newPage()
                    page = pages.last()
                    y = PAGE_MARGIN_MM
                }
                page += place(chunk, y)
            }
        }

        fun addGroup(blocks: List<SizedBlock>) {
            val totalHeight = blocks.sumOf { it.heightMm }
            require(totalHeight <= BODY_HEIGHT_MM) { "indivisible block group exceeds one page" }
            var page = pages.lastOrNull()
            var y = page?.bodyEnd() ?: PAGE_MARGIN_MM
            if (page == null || y + totalHeight > BODY_BOTTOM_MM) {
                newPage()
                page = pages.last()
                y = PAGE_MARGIN_MM
            }
            blocks.forEach { block -> page += place(block, y).also { y += block.heightMm } }
        }

        private fun place(block: SizedBlock, y: Int) = PlacedBlock(
            PAGE_MARGIN_MM,
            y,
            A4_WIDTH_MM - 2 * PAGE_MARGIN_MM,
            block.heightMm,
            block.content,
        )

        private fun List<PlacedBlock>.bodyEnd(): Int = lastOrNull()?.let { it.yMm + it.heightMm } ?: PAGE_MARGIN_MM
    }

    /** Layout constants are internal so the layout-contract tests assert the real numbers, not copies. */
    internal companion object {
        const val BODY_HEIGHT_MM = BODY_BOTTOM_MM - PAGE_MARGIN_MM
        const val BODY_WIDTH_MM = A4_WIDTH_MM - 2 * PAGE_MARGIN_MM
        /** Fixed picture column for item evidence, per the card's ~40 mm inline thumbnail. */
        const val INLINE_THUMB_MM = 40
        const val THUMB_GAP_MM = 2
        const val THUMB_COLUMN_X_MM = BODY_WIDTH_MM - INLINE_THUMB_MM
        const val ITEM_TEXT_WIDTH_MM = THUMB_COLUMN_X_MM - 5
        /** Caption bound that keeps image slots indivisible whatever a reference string contains. */
        const val MAX_CAPTION_LINES = 3
        const val CAPTION_ELISION = "…"
        const val SHORT_HASH_LENGTH = 12
        private val ISO_UTC: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
    }
}
