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
        validateMeasurer()
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

        paginator.forcePage(listOf(coverBlock(report, audience, adverseItems)))
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
                // A room carrying nothing at all is a malformed projection: its heading could only ever be an
                // orphan. A room whose photography is entirely privacy-filtered is a different case - that is
                // the default projection of a room full of the tenant's belongings - so it is simply skipped.
                require(room.photos.isNotEmpty()) {
                    "room ${room.id} has neither items nor photos; it would render as an orphan heading"
                }
                if (visibleRoomPhotos.isEmpty()) continue
                // The heading travels with its first picture: otherwise it can end a page and its only
                // content start the next one.
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

        // Two large pictures per appendix page, titled. The box is sized so that a title plus two slots
        // carrying the longest caption the cap allows still fits the body; placing the three as one group
        // means an outsized measurer fails loudly here instead of demoting the page to a single picture
        // and leaving the follow-on page without the title that was already spent.
        photos.map { imageBlock(it, ImagePurpose.APPENDIX) }.chunked(APPENDIX_PER_PAGE).forEach { pair ->
            paginator.newPage()
            paginator.addGroup(listOf(sectionTitle(APPENDIX_TITLE_KEY, APPENDIX_TITLE)) + pair)
        }

        val closingBody = buildList {
            if (audience == Audience.LANDLORD) addAll(report.remediations.map(::remediationBlock))
            addAll(report.supplements.map(::supplementBlock))
        }
        paginator.startSection(sectionTitle("closing", BilingualText("Closing", "报告结尾")), closingBody)
        val closingTail = buildList {
            add(disclaimerBlock())
            if (audience == Audience.TENANT) add(tenantAgreementBlock())
        }
        // The tenant agreement may never create a new final page without the mandatory disclaimer. Treat
        // the fixed tenant tail as one indivisible unit at residual boundaries; the landlord has one block.
        paginator.addGroup(closingTail)

        val totalPages = pages.size
        val completed = pages.mapIndexed { index, blocks ->
            PagePlan(index + 1, blocks + footerBlock(dataHash, index + 1, totalPages))
        }
        return DocumentPlan(audience, dataHash, completed)
    }

    /**
     * The measurer is an injected seam, so its line heights are inputs. Two pieces of this layout are fixed
     * against them and no caller can shorten either: the composer's own bilingual disclaimer, which is never
     * split across pages, and the appendix page, whose picture box is a constant and whose density is two
     * per page. A measurer at which either no longer fits the body makes every report ungenerable, so it is
     * refused here, where the message can name the styles and the measurements, rather than deep inside
     * pagination where the message can only name the blocks it failed to place.
     *
     * The appendix bound is checked whether or not this particular report has photographs. The measurer is a
     * fixed property of the renderer, not of the report, and a precondition that only bites on the first
     * report that happens to carry a picture is a precondition that ships broken.
     */
    private fun validateMeasurer() {
        val disclaimer = bilingualRuns(REPORT_DISCLAIMER, TextStyle.CAPTION, BODY_WIDTH_MM)
        val required = disclaimer.endY() + 2
        val captionLineMm = disclaimer.first().heightMm
        require(required <= BODY_HEIGHT_MM) {
            "the measurer reports a ${captionLineMm}mm CAPTION line height, at which the fixed " +
                "disclaimer measures ${required}mm and cannot fit the ${BODY_HEIGHT_MM}mm page body"
        }
        // The same section title block and the same worst-case slot the appendix loop builds, so the two
        // cannot drift: a caption spends the cap in full whenever a reference is long enough to wrap.
        val title = sectionTitle(APPENDIX_TITLE_KEY, APPENDIX_TITLE)
        val titleLineMm = (title.content as TextBearingBlock).textRuns.first().heightMm
        val slotMm = APPENDIX_IMAGE_MM + MAX_CAPTION_LINES * captionLineMm + 2
        val appendixPageMm = title.heightMm + APPENDIX_PER_PAGE * slotMm
        require(appendixPageMm <= BODY_HEIGHT_MM) {
            "the measurer reports a ${titleLineMm}mm TITLE line height and a ${captionLineMm}mm CAPTION line " +
                "height, at which an appendix page (a ${title.heightMm}mm section title plus " +
                "$APPENDIX_PER_PAGE slots of ${slotMm}mm at the $MAX_CAPTION_LINES-line caption cap) measures " +
                "${appendixPageMm}mm and cannot fit the ${BODY_HEIGHT_MM}mm page body"
        }
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
        // The caption renders the EXIF instant when the photo has one, so that is the value to guard.
        // Checking capturedAt alone reports success while every caption for the photo reads 1970-01-01.
        photos.forEach { photo ->
            val rendered = photo.snapshot.exifTimeMs ?: photo.capturedAt
            require(rendered > 0) {
                "photo ${photo.id} would render capture time $rendered; the drawn instant must be positive"
            }
        }
        // Every back-reference the renderer prints has to resolve: the multiset checks above are satisfied
        // by blank identifiers and repeated references, which render evidence that traces to nothing.
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

    /**
     * The answer sheet, and it is one page. The room-by-status breakdown grows with the property, so the
     * drawn part is capped and says how many rows it left out; [CoverBlock.roomStatusCounts] still carries
     * the whole breakdown, and the summary section lists every adverse item in full.
     */
    private fun coverBlock(
        report: ReportSnapshot,
        audience: Audience,
        adverseItems: List<Pair<String, ReportItem>>,
    ): SizedBlock {
        // Pending work is the number of distinct items carrying a remediation, not a second copy of the
        // adverse count. They differ whenever an item is adverse but has no remediation yet, or vice versa.
        val pendingItemCount = report.remediations.map { it.itemId }.distinct().size
            .takeIf { audience == Audience.LANDLORD }
        val counts = report.rooms.flatMap { room ->
            room.items.groupingBy { it.snapshot.status }.eachCount().entries.map {
                RoomStatusCount(room.id, it.key, it.value)
            }
        }
        val head = buildList {
            addAll(runs(report.canonical.property.address, TextLanguage.ORIGINAL, TextStyle.TITLE, BODY_WIDTH_MM))
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
            addAll(
                bilingualRuns(
                    BilingualText("Adverse items: ${adverseItems.size}", "不利项：${adverseItems.size}"),
                    TextStyle.BODY,
                    BODY_WIDTH_MM,
                    endY(),
                ),
            )
            pendingItemCount?.let { pending ->
                addAll(
                    bilingualRuns(
                        BilingualText("Pending remediation: $pending", "待处理：$pending"),
                        TextStyle.BODY,
                        BODY_WIDTH_MM,
                        endY(),
                    ),
                )
            }
        }
        val budget = BODY_HEIGHT_MM - 2
        // The marker below is appended whenever a row is left out, and the loop can leave every row out, so
        // the header is only admissible with room for it: admitting the header alone lets a header that
        // fills the budget draw the marker past it, and the block then outgrows the body and is split onto
        // a second page - a second cover carrying the same address and the same totals.
        val elisionReserve = if (counts.isEmpty()) 0 else measuredHeight(coverElision(counts.size), TextStyle.BODY)
        require(head.endY() + elisionReserve <= budget) {
            "the cover header measures ${head.endY()}mm and its elision marker ${elisionReserve}mm, which " +
                "together cannot fit the ${budget}mm available"
        }
        val lines = head.toMutableList()
        var drawnCounts = 0
        for (count in counts) {
            val line = runs(
                "${count.roomId} · ${count.status} · ${count.count}",
                TextLanguage.NEUTRAL,
                TextStyle.BODY,
                BODY_WIDTH_MM,
                lines.endY(),
            )
            val reserve = if (drawnCounts + 1 < counts.size) elisionReserve else 0
            if (line.endY() + reserve > budget) break
            lines += line
            drawnCounts++
        }
        if (drawnCounts < counts.size) {
            lines += runs(
                coverElision(counts.size - drawnCounts),
                TextLanguage.NEUTRAL,
                TextStyle.BODY,
                BODY_WIDTH_MM,
                lines.endY(),
            )
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

    private fun coverElision(remaining: Int): String = "$CAPTION_ELISION $remaining more rows / 另有 $remaining 行见摘要"

    private fun measuredHeight(text: String, style: TextStyle): Int =
        textMeasurer.measure(text, style, BODY_WIDTH_MM).let { it.lines.size * it.lineHeightMm }

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
        val imageHeight = if (purpose == ImagePurpose.INLINE) PANORAMA_IMAGE_MM else APPENDIX_IMAGE_MM
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
        if (measured.lines.size > MAX_CAPTION_LINES) {
            kept[kept.lastIndex] = elidedLineThatFits(kept.last(), widthMm)
        }
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
            imageHeightMm = imageHeightMm,
            heightMm = height,
        )
    }

    /** Removes complete Unicode code points until the marker measures as one line in the target column. */
    private fun elidedLineThatFits(line: String, widthMm: Int): String {
        var prefix = line
        while (true) {
            val candidate = prefix + CAPTION_ELISION
            if (textMeasurer.measure(candidate, TextStyle.CAPTION, widthMm).lines.size == 1) return candidate
            require(prefix.isNotEmpty()) {
                "the caption elision marker cannot fit the ${widthMm}mm caption column"
            }
            val lastCodePoint = prefix.codePointBefore(prefix.length)
            prefix = prefix.dropLast(Character.charCount(lastCodePoint))
        }
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
        // The drawn footer carries the short hash, which is what fits the strip; the full digest stays in
        // FooterBlock.dataHash for verification.
        val shortHash = dataHash.take(SHORT_HASH_LENGTH)
        val footerText = "$shortHash · $page/$totalPages"
        val measured = textMeasurer.measure(footerText, TextStyle.CAPTION, BODY_WIDTH_MM)
        require(measured.lines.size == 1 && measured.lineHeightMm <= FOOTER_HEIGHT_MM) {
            "footer text must measure as one line within the 10mm strip"
        }
        val textRuns = listOf(
            TextRun(
                measured.lines.single(),
                TextLanguage.NEUTRAL,
                TextStyle.CAPTION,
                0,
                0,
                BODY_WIDTH_MM,
                measured.lineHeightMm,
            ),
        )
        return PlacedBlock(
            PAGE_MARGIN_MM,
            BODY_BOTTOM_MM,
            A4_WIDTH_MM - 2 * PAGE_MARGIN_MM,
            FOOTER_HEIGHT_MM,
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
        require(maxHeightMm > 0) { "no page space remains for ${block.content.describe()}" }
        if (block.heightMm <= maxHeightMm) return listOf(block)
        val content = block.content
        if (content is ItemRowBlock) return splitItemRow(block, content, maxHeightMm)
        require(content !is ImageSlotBlock) {
            // Splitting would emit two slots carrying the same photoId: the same photograph printed twice,
            // each with part of its caption.
            "${content.describe()} is indivisible and measures ${block.heightMm}mm, over the ${maxHeightMm}mm available"
        }
        val text = content as? TextBearingBlock
            ?: throw IllegalArgumentException("${content.describe()} is indivisible and exceeds the page body")
        val (paired, flowing) = text.textRuns.partition {
            it.language == TextLanguage.EN || it.language == TextLanguage.ZH
        }
        require(paired.endY() + 2 <= maxHeightMm) {
            "${content.describe()} has a bilingual pair measuring ${paired.endY() + 2}mm, " +
                "over the ${maxHeightMm}mm available; an en/zh pair is never split across pages"
        }
        require(flowing.isNotEmpty()) {
            "${content.describe()} measures ${block.heightMm}mm and carries no free text to flow onto a second page"
        }
        val chunks = mutableListOf<List<TextRun>>()
        var current = paired.toMutableList()
        var currentHeight = current.endY()
        for (run in flowing) {
            require(run.heightMm + 2 <= maxHeightMm) {
                "${content.describe()} has a flowing line measuring ${run.heightMm}mm; with 2mm padding it " +
                    "cannot fit in the ${maxHeightMm}mm page body"
            }
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
            val updated = text.withRuns(chunk)
            SizedBlock(maxOf(block.minHeightMm, chunk.endY() + 2), updated, block.minHeightMm)
        }
    }

    /**
     * An item row is two drawn columns: measured text on the left, evidence thumbnails on the right. A row
     * taller than the space available is cut into chunks that own **disjoint** slices of both columns, so no
     * photograph is ever printed under two fragments of one note. The bilingual label repeats on every chunk
     * because a continuation row still has to say which item its pictures belong to.
     */
    private fun splitItemRow(block: SizedBlock, row: ItemRowBlock, maxHeightMm: Int): List<SizedBlock> {
        val (label, flowing) = row.textRuns.partition {
            it.language == TextLanguage.EN || it.language == TextLanguage.ZH
        }
        require(label.endY() + 2 <= maxHeightMm) {
            "item ${row.itemId} has a bilingual label measuring ${label.endY() + 2}mm, " +
                "over the ${maxHeightMm}mm available"
        }
        val pendingText = ArrayDeque(flowing)
        val pendingThumbnails = ArrayDeque(row.thumbnails)
        val chunks = mutableListOf<SizedBlock>()
        while (chunks.isEmpty() || pendingText.isNotEmpty() || pendingThumbnails.isNotEmpty()) {
            val textRuns = label.toMutableList()
            var textY = label.endY()
            while (pendingText.isNotEmpty() && textY + pendingText.first().heightMm + 2 <= maxHeightMm) {
                val run = pendingText.removeFirst()
                textRuns += run.copy(yMm = textY)
                textY += run.heightMm
            }
            val thumbnails = mutableListOf<ImageSlotBlock>()
            var thumbY = 0
            while (pendingThumbnails.isNotEmpty() && thumbY + pendingThumbnails.first().heightMm <= maxHeightMm) {
                val slot = pendingThumbnails.removeFirst()
                thumbnails += slot.movedTo(thumbY)
                thumbY += slot.heightMm + THUMB_GAP_MM
            }
            require(textRuns.size > label.size || thumbnails.isNotEmpty() || chunks.isEmpty()) {
                "item ${row.itemId} cannot place its next line or thumbnail in the ${maxHeightMm}mm available"
            }
            val thumbnailEnd = (thumbY - THUMB_GAP_MM).coerceAtLeast(0)
            chunks += SizedBlock(
                maxOf(block.minHeightMm, textY + 2, thumbnailEnd),
                row.copy(textRuns = textRuns, thumbnails = thumbnails),
                block.minHeightMm,
            )
        }
        return chunks
    }

    /** Moves a slot inside its containing block, carrying the caption runs with the picture. */
    private fun ImageSlotBlock.movedTo(newYMm: Int): ImageSlotBlock {
        val delta = newYMm - yMm
        if (delta == 0) return this
        return copy(yMm = newYMm, textRuns = textRuns.map { it.copy(yMm = it.yMm + delta) })
    }

    /** Identifies a block in a failure message: a bare measurement names nothing the author can look up. */
    private fun DocumentBlock.describe(): String = when (this) {
        is CoverBlock -> "cover"
        is SectionTitleBlock -> "section title $key"
        is StatusDefinitionBlock -> "status definition $status"
        is SummaryItemBlock -> "summary row $itemId"
        is RoomTitleBlock -> "room title $roomId"
        is ItemRowBlock -> "item $itemId"
        is ImageSlotBlock -> "image slot $photoId"
        is RemediationBlock -> "remediation for $itemId"
        is SupplementBlock -> "supplement $reference"
        is DisclaimerBlock -> "disclaimer"
        is TenantAgreementBlock -> "tenant agreement"
        is FooterBlock -> "footer for page $pageNumber"
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
            require(totalHeight <= BODY_HEIGHT_MM) {
                "${blocks.joinToString(" + ") { it.content.describe() }} must be placed together but measure " +
                    "${totalHeight}mm, over the ${BODY_HEIGHT_MM}mm page body"
            }
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

    /**
     * Layout constants are private. Tests write the numbers out instead: an assertion phrased as
     * `assertEquals(INLINE_THUMB_MM, thumbnail.widthMm)` compares this constant with itself and stays green
     * when it is changed to anything at all.
     */
    private companion object {
        const val BODY_HEIGHT_MM = BODY_BOTTOM_MM - PAGE_MARGIN_MM
        const val BODY_WIDTH_MM = A4_WIDTH_MM - 2 * PAGE_MARGIN_MM
        /** Fixed picture column for item evidence, per the card's ~40 mm inline thumbnail. */
        const val INLINE_THUMB_MM = 40
        const val THUMB_GAP_MM = 2
        /** Full-width room panorama between item rows. */
        const val PANORAMA_IMAGE_MM = 44
        const val APPENDIX_PER_PAGE = 2
        const val APPENDIX_TITLE_KEY = "photo-appendix"
        private val APPENDIX_TITLE = BilingualText("Photo appendix", "照片附录")
        /**
         * Appendix picture box. The invariant it has to satisfy is that a section title plus
         * [APPENDIX_PER_PAGE] slots, each spending the [MAX_CAPTION_LINES] cap in full, still fit
         * [BODY_HEIGHT_MM] - which is a joint property of this number and of the TITLE and CAPTION line
         * heights the injected measurer reports, never of this number alone. [validateMeasurer] evaluates it
         * against the measurer actually handed in and refuses the pair it cannot serve; writing one
         * measurer's arithmetic out here instead would state as unconditional a sum that holds only for
         * whatever measurer the author had in front of them.
         */
        const val APPENDIX_IMAGE_MM = 108
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
