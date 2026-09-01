package nz.myinspection.core.report

import nz.myinspection.core.report.content.LegacyImportProvenance
import nz.myinspection.core.report.content.ReportContent
import nz.myinspection.core.report.content.ReportContentItem
import nz.myinspection.core.report.content.ReportContentPhoto
import nz.myinspection.core.report.content.ReportContentRoom
import nz.myinspection.core.report.content.ReportContentSummaryItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Pure Kotlin layout engine. Renderers consume measured runs and slots without wrapping or pagination. */
class ReportComposer(private val textMeasurer: TextMeasurer) {
    private val adapter = ReportContentAdapter()

    /**
     * Snapshot entry point. Audience and privacy are settled by the adapter's projection before anything
     * here runs, so this is a bridge and not a second place where a report's meaning is decided.
     */
    fun compose(report: ReportSnapshot, audience: Audience, options: ReportOptions = ReportOptions()): DocumentPlan =
        compose(adapter.adapt(report, audience, options))

    /**
     * Lays out one already-projected report. There is deliberately no audience and no privacy option in
     * this signature: every photograph, judgment, remediation and agreement reaching this point is one the
     * projection has already decided this reader may see.
     */
    fun compose(content: ReportContent): DocumentPlan {
        validateMeasurer(content.disclaimer)
        val photos = content.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
        val pages = mutableListOf<MutableList<PlacedBlock>>()
        val paginator = Paginator(pages)

        paginator.forcePage(listOf(coverBlock(content)))
        paginator.startSection(
            sectionTitle("status-glossary", BilingualText("Status glossary", "评级词表")),
            content.statusDefinitions.map(::statusBlock),
        )
        paginator.startSection(
            sectionTitle("summary", BilingualText("Summary", "摘要")),
            content.summary.adverseItems.map(::summaryBlock),
        )

        if (content.rooms.isNotEmpty()) paginator.newPage()
        for (room in content.rooms) {
            val title = roomTitleBlock(room)
            if (room.items.isEmpty()) {
                // A room reaching the layout with no items always has photographs: one carrying neither is
                // refused by the adapter, and one the privacy filter emptied is dropped by the projection.
                // The heading travels with its first picture, which is what stops it ending a page alone
                // while its only content starts the next one.
                paginator.addGroup(listOf(title, imageBlock(room.photos.first(), ImagePurpose.INLINE)))
                room.photos.drop(1).forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
                continue
            }
            val firstPhoto = room.photos.firstOrNull()?.let { imageBlock(it, ImagePurpose.INLINE) }
            val openingFixedHeight = title.heightMm + (firstPhoto?.heightMm ?: 0)
            val firstItemChunks = splitBlock(itemBlock(room.items.first()), BODY_HEIGHT_MM - openingFixedHeight)
            paginator.addGroup(listOfNotNull(title, firstPhoto, firstItemChunks.first()))
            firstItemChunks.drop(1).forEach(paginator::add)
            room.photos.drop(1).forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
            for (item in room.items.drop(1)) {
                paginator.add(itemBlock(item))
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

        // Only an imported report has a source to attest, so a native one is laid out exactly as before.
        content.importProvenance?.let { provenance ->
            paginator.startSection(
                sectionTitle(PROVENANCE_KEY, BilingualText("Imported source", "导入来源")),
                listOf(provenanceBlock(provenance)),
            )
        }

        val closingBody = content.remediations.map(::remediationBlock) + content.supplements.map(::supplementBlock)
        paginator.startSection(sectionTitle("closing", BilingualText("Closing", "报告结尾")), closingBody)
        val closingTail = buildList {
            add(disclaimerBlock(content.disclaimer))
            content.tenantAgreement?.let { add(tenantAgreementBlock(it)) }
        }
        // The tenant agreement may never create a new final page without the mandatory disclaimer. Treat
        // the fixed tenant tail as one indivisible unit at residual boundaries; the landlord has one block.
        paginator.addGroup(closingTail)

        // The footer restates the hash the projection carried out of the finalized snapshot. Recomputing it
        // here would digest the filtered report instead, and the two audiences would then disagree about
        // what the native evidence was.
        val dataHash = content.nativeIntegrity.dataHash
        val totalPages = pages.size
        val completed = pages.mapIndexed { index, blocks ->
            PagePlan(index + 1, blocks + footerBlock(dataHash, index + 1, totalPages))
        }
        return DocumentPlan(content.audience, dataHash, completed)
    }

    /**
     * The measurer is an injected seam, so its line heights are inputs. Two pieces of this layout are fixed
     * against them and no caller can shorten either: the report's mandatory bilingual disclaimer, which the
     * projection always carries in full and which is never split across pages, and the appendix page, whose
     * picture box is a constant and whose density is two per page. A measurer at which either no longer fits
     * the body makes every report ungenerable, so it is refused here, where the message can name the styles
     * and the measurements, rather than deep inside pagination where the message can only name the blocks it
     * failed to place.
     *
     * The appendix bound is checked whether or not this particular report has photographs. The measurer is a
     * fixed property of the renderer, not of the report, and a precondition that only bites on the first
     * report that happens to carry a picture is a precondition that ships broken.
     */
    private fun validateMeasurer(disclaimerText: BilingualText) {
        val disclaimer = bilingualRuns(disclaimerText, TextStyle.CAPTION, BODY_WIDTH_MM)
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

    /**
     * The answer sheet, and it is one page. The room-by-status breakdown grows with the property, so the
     * drawn part is capped and says how many rows it left out; [CoverBlock.roomStatusCounts] still carries
     * the whole breakdown, and the summary section lists every adverse item in full.
     */
    private fun coverBlock(content: ReportContent): SizedBlock {
        val identity = content.identity
        val adverseCount = content.summary.adverseItems.size
        // Pending work is the number of distinct items carrying a remediation, not a second copy of the
        // adverse count. They differ whenever an item is adverse but has no remediation yet, or vice versa.
        // It is null for a reader the projection gave no remediation data to.
        val pendingItemCount = content.summary.pendingRemediationCount
        val counts = content.summary.roomStatusCounts.map { RoomStatusCount(it.roomId, it.status, it.count) }
        val head = buildList {
            addAll(runs(identity.propertyAddress, TextLanguage.ORIGINAL, TextStyle.TITLE, BODY_WIDTH_MM))
            addAll(
                runs(
                    "${identity.inspectionType} · ${isoUtc(identity.scheduledAt)}",
                    TextLanguage.NEUTRAL,
                    TextStyle.BODY,
                    BODY_WIDTH_MM,
                    endY(),
                ),
            )
            identity.tenancyReference?.let {
                addAll(runs(it, TextLanguage.NEUTRAL, TextStyle.BODY, BODY_WIDTH_MM, endY()))
            }
            addAll(
                bilingualRuns(
                    BilingualText("Adverse items: $adverseCount", "不利项：$adverseCount"),
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
                identity.propertyAddress,
                identity.inspectionType,
                identity.scheduledAt,
                identity.tenancyReference,
                adverseCount,
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

    private fun summaryBlock(item: ReportContentSummaryItem): SizedBlock {
        val textRuns =
            runs("${item.roomId} · ${item.itemId} · ${item.status}", TextLanguage.NEUTRAL, TextStyle.BODY, 180)
        return sized(SummaryItemBlock(item.itemId, item.roomId, item.status, textRuns), 16)
    }

    private fun roomTitleBlock(room: ReportContentRoom): SizedBlock {
        val textRuns = bilingualRuns(room.label, TextStyle.TITLE, 180)
        return sized(RoomTitleBlock(room.id, room.label, textRuns), 12)
    }

    /**
     * One row of the item table. When the item has visible photos the text column narrows to
     * [ITEM_TEXT_WIDTH_MM] and the pictures occupy a fixed [INLINE_THUMB_MM] column on the right, so the
     * renderer draws a table with a picture column rather than text followed by loose full-width images.
     */
    private fun itemBlock(item: ReportContentItem): SizedBlock {
        val textWidth = if (item.photos.isEmpty()) BODY_WIDTH_MM else ITEM_TEXT_WIDTH_MM
        val textRuns = buildList {
            addAll(bilingualRuns(item.label, TextStyle.BODY, textWidth))
            addAll(
                runs(
                    listOfNotNull(item.status, item.wearOrDamage).joinToString(" · "),
                    TextLanguage.NEUTRAL,
                    TextStyle.BODY,
                    textWidth,
                    endY(),
                ),
            )
            item.note?.takeIf { it.isNotBlank() }?.let {
                addAll(runs(it, TextLanguage.ORIGINAL, TextStyle.BODY, textWidth, endY()))
            }
        }
        var thumbY = 0
        val thumbnails = item.photos.map { photo ->
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
            item.status,
            item.note,
            item.wearOrDamage,
            textRuns,
            thumbnails,
        )
        return SizedBlock(maxOf(18, textRuns.endY() + 2, thumbnailHeight), block, 18)
    }

    /** Room-level photography: a full-width picture that stands on its own between item rows. */
    private fun imageBlock(photo: ReportContentPhoto, purpose: ImagePurpose): SizedBlock {
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
        photo: ReportContentPhoto,
        purpose: ImagePurpose,
        x: Int,
        y: Int,
        widthMm: Int,
        imageHeightMm: Int,
    ): ImageSlotBlock {
        val capturedAt = photo.capturedAt
        val caption = "${photo.reference} · ${photo.source} · ${isoUtc(capturedAt)}"
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
            source = photo.source,
            capturedAt = capturedAt,
            textRuns = textRuns,
            xMm = x,
            yMm = y,
            widthMm = widthMm,
            imageHeightMm = imageHeightMm,
            heightMm = height,
        )
    }

    /**
     * The imported source, under its own heading. Every value is a hash or a version string the projection
     * carried in, and the native digest is deliberately not among them: the two attest different artifacts,
     * and a reader who met them in one paragraph would have no way to tell which claim was which.
     */
    private fun provenanceBlock(provenance: LegacyImportProvenance): SizedBlock {
        val lines = buildList {
            add("Source SHA-256 / 源文件 SHA-256: ${provenance.sourceSha256}")
            add("Normalized manifest / 归一化清单: ${provenance.normalizedManifestSha256}")
            add("Mapping receipt / 映射回执: ${provenance.mappingReceiptSha256}")
            add("Extractor / 提取器: ${provenance.extractorVersion}")
            provenance.sourceReportDate?.let { add("Source report date / 源报告日期: $it") }
        }
        val textRuns = buildList {
            lines.forEach { addAll(runs(it, TextLanguage.NEUTRAL, TextStyle.CAPTION, 180, endY())) }
        }
        return sized(
            ProvenanceBlock(
                provenance.sourceSha256,
                provenance.normalizedManifestSha256,
                provenance.mappingReceiptSha256,
                provenance.extractorVersion,
                provenance.sourceReportDate,
                textRuns,
            ),
            20,
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

    private fun disclaimerBlock(text: BilingualText): SizedBlock {
        val textRuns = bilingualRuns(text, TextStyle.CAPTION, 180)
        return sized(DisclaimerBlock(text, textRuns), 24)
    }

    private fun tenantAgreementBlock(label: BilingualText): SizedBlock {
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

    private fun splitBlock(
        block: SizedBlock,
        firstMaxHeightMm: Int = BODY_HEIGHT_MM,
        continuationMaxHeightMm: Int = BODY_HEIGHT_MM,
    ): List<SizedBlock> {
        require(firstMaxHeightMm > 0) { "no page space remains for ${block.content.describe()}" }
        require(continuationMaxHeightMm > 0) { "no continuation space remains for ${block.content.describe()}" }
        if (block.heightMm <= firstMaxHeightMm) return listOf(block)
        val content = block.content
        if (content is ItemRowBlock) {
            return splitItemRow(block, content, firstMaxHeightMm, continuationMaxHeightMm)
        }
        require(content !is ImageSlotBlock) {
            // Splitting would emit two slots carrying the same photoId: the same photograph printed twice,
            // each with part of its caption.
            "${content.describe()} is indivisible and measures ${block.heightMm}mm, over the " +
                "${firstMaxHeightMm}mm available"
        }
        val text = content as? TextBearingBlock
            ?: throw IllegalArgumentException("${content.describe()} is indivisible and exceeds the page body")
        val (paired, flowing) = text.textRuns.partition {
            it.language == TextLanguage.EN || it.language == TextLanguage.ZH
        }
        require(paired.endY() + 2 <= firstMaxHeightMm) {
            "${content.describe()} has a bilingual pair measuring ${paired.endY() + 2}mm, " +
                "over the ${firstMaxHeightMm}mm available; an en/zh pair is never split across pages"
        }
        require(flowing.isNotEmpty()) {
            "${content.describe()} measures ${block.heightMm}mm and carries no free text to flow onto a second page"
        }
        val chunks = mutableListOf<List<TextRun>>()
        var current = paired.toMutableList()
        var currentHeight = current.endY()
        var currentMaxHeight = firstMaxHeightMm
        for (run in flowing) {
            if (current.isNotEmpty() && currentHeight + run.heightMm + 2 > currentMaxHeight) {
                chunks += rebase(current)
                current = mutableListOf()
                currentHeight = 0
                currentMaxHeight = continuationMaxHeightMm
            }
            require(run.heightMm + 2 <= currentMaxHeight) {
                "${content.describe()} has a flowing line measuring ${run.heightMm}mm; with 2mm padding it " +
                    "cannot fit in the ${currentMaxHeight}mm page body"
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
    private fun splitItemRow(
        block: SizedBlock,
        row: ItemRowBlock,
        firstMaxHeightMm: Int,
        continuationMaxHeightMm: Int,
    ): List<SizedBlock> {
        val (label, flowing) = row.textRuns.partition {
            it.language == TextLanguage.EN || it.language == TextLanguage.ZH
        }
        require(label.endY() + 2 <= firstMaxHeightMm) {
            "item ${row.itemId} has a bilingual label measuring ${label.endY() + 2}mm, " +
                "over the ${firstMaxHeightMm}mm available"
        }
        val pendingText = ArrayDeque(flowing)
        val pendingThumbnails = ArrayDeque(row.thumbnails)
        val chunks = mutableListOf<SizedBlock>()
        while (chunks.isEmpty() || pendingText.isNotEmpty() || pendingThumbnails.isNotEmpty()) {
            val maxHeightMm = if (chunks.isEmpty()) firstMaxHeightMm else continuationMaxHeightMm
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
        is ProvenanceBlock -> "import provenance $extractorVersion"
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
        is ProvenanceBlock -> copy(textRuns = runs)
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
        const val PROVENANCE_KEY = "provenance"
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
