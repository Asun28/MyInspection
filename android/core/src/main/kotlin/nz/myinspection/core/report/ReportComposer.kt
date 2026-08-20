package nz.myinspection.core.report

import nz.myinspection.core.canon.canonicalJson
import nz.myinspection.core.canon.sha256Hex

/** Pure Kotlin layout engine. Renderers consume [DocumentPlan] without making pagination decisions. */
class ReportComposer(private val textMeasurer: TextMeasurer) {
    fun compose(
        report: ReportSnapshot,
        audience: Audience,
        options: ReportOptions = ReportOptions(),
    ): DocumentPlan {
        validateProjection(report)
        val dataHash = sha256Hex(canonicalJson(report.canonical))
        val photos = report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }
            .filter { options.includePrivacyPhotos || !it.privacy }
        val adverseStatuses = report.statusDefinitions.filter { it.adverse }.mapTo(mutableSetOf()) { it.status }
        val adverseItems = report.rooms.flatMap { room ->
            room.items.filter { it.snapshot.status in adverseStatuses }.map { room.id to it }
        }
        val pages = mutableListOf<MutableList<PlacedBlock>>()

        pages.add(pageOf(
            SizedBlock(
                100,
                CoverBlock(
                    address = report.canonical.property.address,
                    inspectionType = report.canonical.type,
                    scheduledAt = report.canonical.scheduledAt,
                    tenancyReference = report.tenancyReference,
                    adverseItemCount = adverseItems.size,
                    remediationCount = if (audience == Audience.LANDLORD) report.remediations.size else 0,
                ),
            ),
        ))

        pages.add(pageOf(
            SizedBlock(10, SectionTitleBlock("status-glossary", BilingualText("Status glossary", "评级词表"))),
            *report.statusDefinitions.map {
                SizedBlock(20, StatusDefinitionBlock(it.status, it.label, it.description, it.adverse))
            }.toTypedArray(),
        ))

        pages.add(pageOf(
            SizedBlock(10, SectionTitleBlock("summary", BilingualText("Summary", "摘要"))),
            *adverseItems.map { (roomId, item) ->
                SizedBlock(16, SummaryItemBlock(item.id, roomId, item.snapshot.status))
            }.toTypedArray(),
        ))

        if (report.rooms.isNotEmpty()) pages.add(mutableListOf())
        val paginator = Paginator(pages)
        for (room in report.rooms) {
            if (room.items.isEmpty()) {
                paginator.addGroup(listOf(SizedBlock(12, RoomTitleBlock(room.id, room.label))))
                room.photos.filter { it in photos }.forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
                continue
            }
            val firstItem = room.items.first()
            val roomPhotos = room.photos.filter { it in photos }
            val opening = buildList {
                add(SizedBlock(12, RoomTitleBlock(room.id, room.label)))
                roomPhotos.firstOrNull()?.let { add(imageBlock(it, ImagePurpose.INLINE)) }
                add(itemBlock(firstItem))
            }
            paginator.addGroup(opening)
            roomPhotos.drop(1).forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
            firstItem.photos.filter { it in photos }.forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
            for (item in room.items.drop(1)) {
                paginator.add(itemBlock(item))
                item.photos.filter { it in photos }.forEach { paginator.add(imageBlock(it, ImagePurpose.INLINE)) }
            }
        }

        if (photos.isNotEmpty()) {
            val appendix = photos.map { imageBlock(it, ImagePurpose.APPENDIX) }
            appendix.chunked(2).forEach { pair ->
                pages.add(pageOf(
                    SizedBlock(10, SectionTitleBlock("photo-appendix", BilingualText("Photo appendix", "照片附录"))),
                    *pair.toTypedArray(),
                ))
            }
        }

        val closing = mutableListOf<SizedBlock>()
        closing += SizedBlock(10, SectionTitleBlock("closing", BilingualText("Closing", "报告结尾")))
        if (audience == Audience.LANDLORD) {
            closing += report.remediations.map {
                SizedBlock(20, RemediationBlock(it.itemId, it.urgency, it.text))
            }
        }
        closing += report.supplements.map {
            val measured = measure(it.text, TextStyle.BODY, 180)
            SizedBlock(maxOf(14, measured), SupplementBlock(it.reference, it.text))
        }
        closing += SizedBlock(
            24,
            DisclaimerBlock(
                BilingualText(
                    "This report records observed condition and is not professional diagnosis.",
                    "本报告仅记录观察到的状况，不构成专业诊断。",
                ),
            ),
        )
        if (audience == Audience.TENANT) {
            closing += SizedBlock(24, TenantAgreementBlock(BilingualText("Tenant agreement / signature", "租客同意 / 签名")))
        }
        paginator.addSection(closing)

        val totalPages = pages.size
        val completedPages = pages.mapIndexed { index, body ->
            val footer = PlacedBlock(
                xMm = PAGE_MARGIN_MM,
                yMm = BODY_BOTTOM_MM,
                widthMm = A4_WIDTH_MM - 2 * PAGE_MARGIN_MM,
                heightMm = 10,
                content = FooterBlock(dataHash, dataHash.take(12), index + 1, totalPages),
            )
            PagePlan(index + 1, body + footer)
        }
        return DocumentPlan(audience, dataHash, completedPages)
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
        require(multiset(items.map { it.snapshot }) == multiset(report.canonical.items)) {
            "report items do not match canonical snapshot"
        }
        require(multiset(photos.map { it.snapshot }) == multiset(report.canonical.photos)) {
            "report photos do not match canonical snapshot"
        }
        val itemIds = items.mapTo(mutableSetOf()) { it.id }
        require(report.remediations.all { it.itemId in itemIds }) { "remediation references unknown item" }
        require(report.statusDefinitions.map { it.status }.toSet().size == report.statusDefinitions.size) {
            "duplicate status definition"
        }
        val definedStatuses = report.statusDefinitions.mapTo(mutableSetOf()) { it.status }
        require(items.all { it.snapshot.status in definedStatuses }) { "item status is missing from report glossary" }
    }

    private fun <T> multiset(values: List<T>): Map<T, Int> = values.groupingBy { it }.eachCount()

    private fun itemBlock(item: ReportItem): SizedBlock {
        val noteHeight = item.snapshot.note?.let { measure(it, TextStyle.BODY, 180) } ?: 0
        return SizedBlock(
            maxOf(18, noteHeight + 10),
            ItemRowBlock(
                item.id,
                item.label,
                item.snapshot.status,
                item.snapshot.note,
                item.snapshot.wearOrDamage,
            ),
        )
    }

    private fun imageBlock(photo: ReportPhoto, purpose: ImagePurpose): SizedBlock = SizedBlock(
        if (purpose == ImagePurpose.INLINE) 48 else 120,
        ImageSlotBlock(
            photo.id,
            purpose,
            photo.reference,
            photo.snapshot.source,
            photo.snapshot.exifTimeMs ?: photo.capturedAt,
        ),
    )

    private fun measure(text: String, style: TextStyle, widthMm: Int): Int {
        val height = textMeasurer.heightMm(text, style, widthMm)
        require(height > 0) { "TextMeasurer must return a positive height" }
        return height
    }

    private fun pageOf(vararg blocks: SizedBlock): MutableList<PlacedBlock> {
        require(blocks.sumOf { it.heightMm } <= BODY_BOTTOM_MM - PAGE_MARGIN_MM) { "section exceeds one page" }
        var y = PAGE_MARGIN_MM
        return blocks.mapTo(mutableListOf()) { block ->
            PlacedBlock(PAGE_MARGIN_MM, y, A4_WIDTH_MM - 2 * PAGE_MARGIN_MM, block.heightMm, block.content)
                .also { y += block.heightMm }
        }
    }

    private data class SizedBlock(val heightMm: Int, val content: DocumentBlock)

    private inner class Paginator(private val pages: MutableList<MutableList<PlacedBlock>>) {
        fun add(block: SizedBlock) = addGroup(listOf(block))

        fun addGroup(blocks: List<SizedBlock>) {
            val totalHeight = blocks.sumOf { it.heightMm }
            require(totalHeight <= BODY_BOTTOM_MM - PAGE_MARGIN_MM) { "indivisible block group exceeds one page" }
            var page = pages.lastOrNull()
            var y = page?.bodyEnd() ?: PAGE_MARGIN_MM
            if (page == null || y + totalHeight > BODY_BOTTOM_MM) {
                page = mutableListOf()
                pages.add(page)
                y = PAGE_MARGIN_MM
            }
            for (block in blocks) {
                page += PlacedBlock(PAGE_MARGIN_MM, y, A4_WIDTH_MM - 2 * PAGE_MARGIN_MM, block.heightMm, block.content)
                y += block.heightMm
            }
        }

        fun addSection(blocks: List<SizedBlock>) {
            if (blocks.sumOf { it.heightMm } <= BODY_BOTTOM_MM - PAGE_MARGIN_MM) {
                pages.add(pageOf(*blocks.toTypedArray()))
                return
            }
            pages.add(mutableListOf())
            blocks.forEach(::add)
        }

        private fun List<PlacedBlock>.bodyEnd(): Int = lastOrNull()?.let { it.yMm + it.heightMm } ?: PAGE_MARGIN_MM
    }
}
