package nz.myinspection.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Production break under test: without the report model/composer there is no deterministic layout tree,
 * no typed audience boundary, and no canonical data-hash footer for a renderer to consume.
 *
 * A19 mutation receipts (2026-08-24; every production file was restored before the next run):
 *
 * | Acceptance | Minimal mutation | Actual discriminating failure |
 * | --- | --- | --- |
 * | A1 | omit the forced summary page | `expected 6 pages: cover + glossary + summary + room + appendix + closing` |
 * | A2 | replace accumulated thumbnail pitch with fixed `+56` | `Expected <[0, 48, 100, 156]>, actual <[0, 56, 112, 168]>` |
 * | A3 | reduce appendix density from two to one | `3 photos must span two appendix pages` |
 * | A4 | indent the room panorama by 1 mm | `the room panorama is indented inside its own placed block` |
 * | A5 | retain all thumbnails in every split row chunk | `each item photo must be drawn exactly once` |
 * | A6 | store `imageHeightMm + 1` while retaining the measured slot height | `Expected <40>, actual <41>` |
 * | A7 | drop one UTF-16 unit before appending the marker | `caption elision left an unpaired UTF-16 surrogate` |
 * | A8 | shorten the footer hash slice from 12 to 11 | `Expected <ea9cd02e76bf · 1/6>, actual <ea9cd02e76b · 1/6>` |
 * | A9 | place a photo-only heading and photo sequentially | `the heading did not move to a fresh page with its photo` |
 * | A10 | chunk the 80-photo appendix one-up instead of two-up | `Expected <64>, actual <104>` |
 * | A11 | copy the EN/ZH pair into continuation chunks | `later chunk 2 repeats EN/ZH` |
 * | A12 | expose wearOrDamage to the tenant row | `Expected value to be true` at the typed tenant assertion |
 * | A13 | include privacy photos by default | `a private photo reached the LANDLORD default report` |
 * | A14 | delete the duplicate-photo-id guard | `projection accepted: duplicate photo id` |
 * | A15 | render scheduledAt as epoch milliseconds | `cover draws a raw epoch value` |
 * | A16 | insert one `ReportComposer` + `.` reference into a test source | `a test names the composer's companion instead of writing the value out` |
 * | A17 | delete the single `add(disclaimerBlock())` call | `final page must contain exactly one disclaimer` |
 * | A18 | reverse the adverse-item traversal | `summary order differs from room/item traversal` |
 */
class ReportComposerGoldenTest {
    private val composer = ReportComposer(ReportTestFixtures.measurer)

    @Test
    fun `fixed inspection produces the golden six-page layout tree`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)

        assertEquals("ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d", plan.dataHash)
        // Six pages are forced by six independently paged regions: cover, glossary, summary, room detail,
        // one two-up appendix page, and closing. None is inferred from the result being asserted.
        assertEquals(6, plan.pages.size, "expected 6 pages: cover + glossary + summary + room + appendix + closing")
        assertEquals(
            listOf(
                listOf("cover@15:100", "footer@272:10"),
                listOf(
                    "section:status-glossary@15:10", "status:GOOD@25:20", "status:FAIR@45:20",
                    "status:POOR@65:20", "status:NOT_APPLICABLE@85:20", "footer@272:10",
                ),
                listOf("section:summary@15:10", "summary:item-poor@25:16", "footer@272:10"),
                // The item photo is a thumbnail inside item-poor, which is why that row is 54 mm tall and
                // no separate inline image slot follows it.
                listOf(
                    "room:room-kitchen@15:12", "image:photo-room:inline@27:50",
                    "item:item-good@77:18", "item:item-poor@95:54",
                    "footer@272:10",
                ),
                listOf(
                    "section:photo-appendix@15:10", "image:photo-room:appendix@25:114",
                    "image:photo-item:appendix@139:114", "footer@272:10",
                ),
                listOf(
                    "section:closing@15:10", "remediation:item-poor:HIGH@25:20",
                    "supplement:S1@45:14", "disclaimer@59:26", "footer@272:10",
                ),
            ),
            plan.pages.map { page -> page.blocks.map(::signature) },
        )
        assertEquals((1..6).toList(), plan.pages.map { it.number })
        val cover = plan.contents().single { it is CoverBlock } as CoverBlock
        assertEquals(1, cover.adverseItemCount)
        assertEquals(1, cover.pendingItemCount)
        assertEquals(
            listOf(RoomStatusCount("room-kitchen", "GOOD", 1), RoomStatusCount("room-kitchen", "POOR", 1)),
            cover.roomStatusCounts,
        )
    }

    /**
     * The footer proves the report was not edited after finalize, so what matters is the text that reaches
     * paper: the drawn run is the short hash, and the full digest stays in the block for verification.
     */
    @Test
    fun `the footer that gets drawn carries the short hash, not the full digest`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        assertEquals("ea9cd02e76bf · 1/6", plan.pages.first().footerText())
        assertEquals("ea9cd02e76bf · 6/6", plan.pages.last().footerText())

        plan.pages.forEach { page ->
            val footer = page.blocks.single { it.content is FooterBlock }.content as FooterBlock
            assertEquals(
                "ea9cd02e76bf · ${page.number}/6",
                footer.textRuns.single().text,
                "the drawn footer text must be the short hash",
            )
            assertTrue(
                !footer.textRuns.single().text.contains(
                    "ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d",
                ),
                "the full 64-character digest must not be drawn",
            )
            assertEquals(
                "ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d",
                footer.dataHash,
                "the full digest stays available",
            )
            assertEquals("ea9cd02e76bf", footer.shortHash)
            assertEquals(page.number, footer.pageNumber)
            assertEquals(6, footer.totalPages)
            val run = footer.textRuns.single()
            assertTrue(run.yMm >= 0 && run.yMm + run.heightMm <= 10, "footer run exceeds its 10mm strip")
        }
    }

    @Test
    fun `a footer that measures to multiple lines is refused instead of overflowing its fixed strip`() {
        val wrappingFooter = TextMeasurer { text, style, widthMm ->
            if (text.startsWith("ea9cd02e76bf ·")) {
                MeasuredText(listOf("ea9cd02e76bf", text.substringAfter("ea9cd02e76bf ")), 4)
            } else {
                ReportTestFixtures.measurer.measure(text, style, widthMm)
            }
        }

        assertEquals(
            "footer text must measure as one line within the 10mm strip",
            kotlin.test.assertFailsWith<IllegalArgumentException> {
                ReportComposer(wrappingFooter).compose(ReportTestFixtures.report(), Audience.LANDLORD)
            }.message,
        )
    }

    /** 封面即答案: the totals and the instant have to reach paper, not merely sit in the block's fields. */
    @Test
    fun `the cover draws labelled totals and ISO-8601 times rather than epoch milliseconds`() {
        val report = ReportTestFixtures.report()
        val plan = composer.compose(report, Audience.LANDLORD)
        val cover = plan.contents().filterIsInstance<CoverBlock>().single()
        val drawn = cover.textRuns.joinToString("|") { it.text }

        assertTrue(drawn.contains("Adverse items: 1|不利项：1"), "cover does not draw the adverse total: $drawn")
        assertTrue(drawn.contains("Pending remediation: 1|待处理：1"), "cover does not draw the pending total: $drawn")
        // scheduledAt 1_755_302_400_000 == 2025-08-16T00:00:00Z, fixed and locale-independent.
        assertTrue(drawn.contains("ROUTINE · 2025-08-16T00:00:00Z"), "cover draws a raw epoch value: $drawn")
        assertTrue(!drawn.contains("1755302400000"), "epoch milliseconds reached the rendered cover")
        assertTrue(drawn.contains("room-kitchen · GOOD · 1"), "the room breakdown is not drawn: $drawn")

        val rawEpochs = buildSet {
            add(report.canonical.scheduledAt.toString())
            report.rooms.flatMap { room -> room.photos + room.items.flatMap { it.photos } }.forEach { photo ->
                add(photo.capturedAt.toString())
                photo.snapshot.exifTimeMs?.let { add(it.toString()) }
            }
        }
        plan.contents().mapNotNull { block ->
            when (block) {
                is CoverBlock -> block
                is ImageSlotBlock -> block
                else -> null
            }
        }.forEach { block ->
            block.textRuns.forEach { run ->
                rawEpochs.forEach { epoch ->
                    assertFalse(
                        run.text.contains(epoch),
                        "raw epoch $epoch reached ${block::class.simpleName}: ${run.text}",
                    )
                }
            }
        }
    }

    @Test
    fun `audience types prevent tenant remediation leakage and retain tenant agreement`() {
        val landlord = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val tenant = composer.compose(tenantFreeTextReport(), Audience.TENANT)

        assertTrue(landlord.contents().any { it is RemediationBlock && it.urgency == Urgency.HIGH })
        assertFalse(landlord.contents().any { it is TenantAgreementBlock })
        assertFalse(tenant.contents().any { it is RemediationBlock })
        assertTrue(tenant.contents().any { it is TenantAgreementBlock })
        assertTrue(tenant.contents().any { it is DisclaimerBlock && it.text == REPORT_DISCLAIMER })
        assertTrue(tenant.contents().filterIsInstance<ItemRowBlock>().all { it.wearOrDamage == null })
        assertTrue(
            tenant.contents().filterIsInstance<ItemRowBlock>().flatMap { it.textRuns }
                .any { it.text.contains("DAMAGE FAIR_WEAR remediation 建议 HIGH") },
            "legitimate tenant-authored vocabulary was removed from the item note",
        )
    }

    /**
     * The audience boundary is not only about block types. A landlord-only total drawn on the tenant cover
     * tells the tenant how many remediation suggestions the landlord is holding, which is the same leak by
     * another route. The disclaimer is exempt because it is one frozen constant, pinned above.
     */
    @Test
    fun `tenant cover and typed blocks exclude landlord-only data without scanning user notes`() {
        val tenant = composer.compose(tenantFreeTextReport(), Audience.TENANT)
        val forbidden = listOf("remediation", "Remediation", "待处理", "建议", "紧急度") + Urgency.entries.map { it.name }

        val cover = tenant.contents().filterIsInstance<CoverBlock>().single()
        assertEquals(null, cover.pendingItemCount, "the tenant cover carries the landlord's pending total")
        assertFalse(tenant.contents().any { it is RemediationBlock }, "a landlord-only block reached the tenant")
        cover.textRuns.forEach { run ->
            forbidden.forEach { word ->
                assertFalse(run.text.contains(word), "tenant cover draws '$word': ${run.text}")
            }
        }
    }

    @Test
    fun `closing tail pins exact disclaimer supplement and audience-specific blocks`() {
        val report = ReportTestFixtures.report().copy(
            supplements = listOf(
                ReportSupplement("S1", "Follow-up inspection requested."),
                ReportSupplement("S2", "Second ordered supplement."),
            ),
        )
        val landlord = composer.compose(report, Audience.LANDLORD)
        val tenant = composer.compose(report, Audience.TENANT)
        val expectedDisclaimerRuns = listOf(
            TextLanguage.EN to "This report records visible conditions at the inspection tim",
            TextLanguage.EN to "e. It is not legal, building, engineering, property, health ",
            TextLanguage.EN to "or safety advice. Requirements and standards may change. Con",
            TextLanguage.EN to "sult appropriately licensed professionals before acting.",
            TextLanguage.ZH to "本报告仅记录检查时可见的状况，不构成法律、建筑、工程、物业、健康或安全建议。要求和标准可能变化，采取行动前请咨询具备相应",
            TextLanguage.ZH to "执照的专业人士。",
        )

        listOf(landlord, tenant).forEach { plan ->
            val tail = plan.pages.last().blocks.map { it.content }
            assertEquals(report.supplements.map { it.reference }, tail.filterIsInstance<SupplementBlock>().map { it.reference })
            assertEquals(1, tail.count { it is DisclaimerBlock }, "final page must contain exactly one disclaimer")
            assertEquals(
                expectedDisclaimerRuns,
                tail.filterIsInstance<DisclaimerBlock>().single().textRuns.map { it.language to it.text },
                "tail contract differs for ${plan.audience}",
            )
            assertEquals(2, tail.count { it is SupplementBlock })
        }
        assertEquals(1, landlord.contents().count { it is RemediationBlock })
        assertEquals(0, landlord.contents().count { it is TenantAgreementBlock })
        assertEquals(0, tenant.contents().count { it is RemediationBlock })
        assertEquals(1, tenant.contents().count { it is TenantAgreementBlock })
        assertEquals(
            listOf(
                TextLanguage.EN to "Tenant agreement / signature",
                TextLanguage.ZH to "租客同意 / 签名",
            ),
            tenant.contents().filterIsInstance<TenantAgreementBlock>().single().textRuns.map { it.language to it.text },
        )
    }

    @Test
    fun `summary rows preserve ordered adverse triples and each backlink resolves to an item row`() {
        val plan = composer.compose(multiAdverseReport(), Audience.LANDLORD)
        val summaries = plan.contents().filterIsInstance<SummaryItemBlock>()
        val rows = plan.contents().filterIsInstance<ItemRowBlock>()

        assertEquals(
            listOf(
                Triple("room-z", "item-z-poor", "POOR"),
                Triple("room-a", "item-a-fair", "FAIR"),
                Triple("room-a", "item-a-poor", "POOR"),
            ),
            summaries.map { Triple(it.roomId, it.itemId, it.status) },
            "summary order differs from room/item traversal",
        )
        assertEquals(
            summaries.map { it.itemId },
            summaries.map { summary -> rows.single { it.itemId == summary.itemId }.itemId },
            "a summary backlink does not resolve to exactly one item row",
        )
    }

    @Test
    fun `bilingual text is one indivisible block rather than separate language rows`() {
        val plan = composer.compose(ReportTestFixtures.report(), Audience.LANDLORD)
        val bilingual = plan.contents().flatMap { it.bilingualText() }

        assertTrue(bilingual.isNotEmpty())
        assertTrue(bilingual.all { it.en.isNotBlank() && it.zh.isNotBlank() })
        plan.pages.flatMap { it.blocks }.forEach { placed ->
            val block = placed.content as? TextBearingBlock ?: return@forEach
            assertTrue(block.textRuns.isNotEmpty(), "${block::class.simpleName} must contain renderer-ready measured runs")
            assertTrue(block.textRuns.all { it.yMm >= 0 && it.yMm + it.heightMm <= placed.heightMm })
        }
    }

    private fun DocumentPlan.contents() = pages.flatMap { page -> page.blocks.map { it.content } }

    private fun PagePlan.footerText(): String =
        (blocks.single { it.content is FooterBlock }.content as FooterBlock).textRuns.joinToString("") { it.text }

    private fun tenantFreeTextReport(): ReportSnapshot {
        val base = ReportTestFixtures.report()
        val room = base.rooms.single()
        val changed = room.items.last().copy(
            snapshot = room.items.last().snapshot.copy(note = "DAMAGE FAIR_WEAR remediation 建议 HIGH"),
        )
        return base.copy(
            canonical = base.canonical.copy(items = listOf(room.items.first().snapshot, changed.snapshot)),
            rooms = listOf(room.copy(items = listOf(room.items.first(), changed))),
        )
    }

    private fun multiAdverseReport(): ReportSnapshot {
        val base = ReportTestFixtures.canonical()
        val zPoor = base.items.last().copy(stableId = "z-poor", status = "POOR")
        val zGood = base.items.first().copy(stableId = "z-good", status = "GOOD")
        val aFair = base.items.last().copy(stableId = "a-fair", status = "FAIR")
        val aPoor = base.items.last().copy(stableId = "a-poor", status = "POOR")
        val rooms = listOf(
            ReportRoom(
                "room-z",
                BilingualText("Rear room", "后室"),
                listOf(
                    ReportItem("item-z-poor", zPoor, BilingualText("Door", "门")),
                    ReportItem("item-z-good", zGood, BilingualText("Wall", "墙")),
                ),
            ),
            ReportRoom(
                "room-a",
                BilingualText("Front room", "前室"),
                listOf(
                    ReportItem("item-a-fair", aFair, BilingualText("Floor", "地板")),
                    ReportItem("item-a-poor", aPoor, BilingualText("Window", "窗")),
                ),
            ),
        )
        return ReportSnapshot(
            canonical = base.copy(items = listOf(zPoor, zGood, aFair, aPoor), photos = emptyList()),
            tenancyReference = null,
            rooms = rooms,
            statusDefinitions = ReportTestFixtures.report().statusDefinitions,
        )
    }

    private fun DocumentBlock.bilingualText(): List<BilingualText> = when (this) {
        is SectionTitleBlock -> listOf(title)
        is StatusDefinitionBlock -> listOf(label, description)
        is RoomTitleBlock -> listOf(label)
        is ItemRowBlock -> listOf(label)
        is RemediationBlock -> listOf(text)
        is DisclaimerBlock -> listOf(text)
        is TenantAgreementBlock -> listOf(label)
        else -> emptyList()
    }

    private fun signature(block: PlacedBlock): String {
        val key = when (val content = block.content) {
            is CoverBlock -> "cover"
            is SectionTitleBlock -> "section:${content.key}"
            is StatusDefinitionBlock -> "status:${content.status}"
            is SummaryItemBlock -> "summary:${content.itemId}"
            is RoomTitleBlock -> "room:${content.roomId}"
            is ItemRowBlock -> "item:${content.itemId}"
            is ImageSlotBlock -> "image:${content.photoId}:${content.purpose.name.lowercase()}"
            is RemediationBlock -> "remediation:${content.itemId}:${content.urgency}"
            is SupplementBlock -> "supplement:${content.reference}"
            is DisclaimerBlock -> "disclaimer"
            is TenantAgreementBlock -> "tenant-agreement"
            is FooterBlock -> "footer"
        }
        return "$key@${block.yMm}:${block.heightMm}"
    }
}
