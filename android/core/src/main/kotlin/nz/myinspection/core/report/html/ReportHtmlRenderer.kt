package nz.myinspection.core.report.html

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.content.LegacyImportProvenance
import nz.myinspection.core.report.content.ReportContent
import nz.myinspection.core.report.content.ReportContentItem
import nz.myinspection.core.report.content.ReportContentPhoto
import nz.myinspection.core.report.content.ReportContentRoom

/**
 * Serializes the shared semantic report into one self-contained HTML file.
 *
 * The renderer decides nothing about the report. Audience and privacy were settled before [ReportContent]
 * existed, so there is no filter here to get wrong and no removed byte to reintroduce; what is left is
 * serialization, escaping and a hard ceiling on embedded evidence.
 *
 * Two deliberate differences from the paginated PDF of the same content, both permitted by ADR-0007 as
 * "pagination, visual layout and encoding": there is no photo appendix, because the PDF's reason for
 * printing every picture twice is that paper cannot be zoomed, and doing it here would carry every
 * photograph twice inside one file that has to open on a phone — each appears once, still numbered, so no
 * reference is lost; and page geometry is absent, because the browser paginates and the print rules are
 * `T3-REPORT-HTML-PRESENTATION`.
 */
class ReportHtmlRenderer(
    private val images: ReportImageSource,
    private val bounds: HtmlImageBounds = HtmlImageBounds(),
) {
    fun render(content: ReportContent): String = Document(content).build()

    private inner class Document(private val content: ReportContent) {
        private val out = StringBuilder()
        private val roomLabels = content.rooms.associate { it.id to it.label }

        /** Spent in [Long] so a report with thousands of photographs cannot wrap the running total. */
        private var spentImageBytes = 0L

        fun build(): String {
            val identity = content.identity
            line("<!DOCTYPE html>")
            line("<html lang=\"en\">")
            line("<head>")
            line("<meta charset=\"utf-8\">")
            line("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            val css = ReportHtmlStylesheet.css
            line("<meta http-equiv=\"Content-Security-Policy\" content=\"$POLICY_HEAD" + "'sha256-${styleHash(css)}'; $POLICY_TAIL\">")
            line("<title>${text("${identity.inspectionType} · ${identity.propertyAddress}")}</title>")
            // One line, so the element's content is exactly the stylesheet text the hash covers.
            line("<style>$css</style>")
            line("</head>")
            line("<body class=\"${HtmlClass.REPORT.cssName}\">")
            header()
            line("<main>")
            glossary()
            summary()
            content.rooms.forEach(::room)
            content.importProvenance?.let(::provenance)
            closing()
            line("</main>")
            integrity()
            line("</body>")
            line("</html>")
            return out.toString()
        }

        private fun header() {
            val identity = content.identity
            line("<header class=\"${HtmlClass.IDENTITY.cssName}\">")
            line("<h1>${original(identity.propertyAddress)}</h1>")
            line("<dl class=\"${HtmlClass.IDENTITY_FIELD.cssName}\">")
            field("Inspection type", "巡检类型", identity.inspectionType)
            field("Scheduled", "计划时间", isoUtc(identity.scheduledAt))
            field("Finalized", "定稿时间", isoUtc(identity.finalizedAt))
            identity.tenancyReference?.let { field("Tenancy reference", "租约编号", it) }
            field("Template", "模板", "${identity.templateId} v${identity.templateVersion}")
            field("Audience", "受众", content.audience.name)
            line("</dl>")
            line("</header>")
        }

        private fun glossary() {
            line("<section class=\"${HtmlClass.SECTION.cssName}\">")
            line("<h2>${bilingual("Status glossary", "评级词表")}</h2>")
            line("<dl>")
            content.statusDefinitions.forEach { definition ->
                line("<div class=\"${HtmlClass.GLOSSARY_ENTRY.cssName}\">")
                line("<dt>${text(definition.status)} ${bilingual(definition.label)}</dt>")
                line("<dd>${bilingual(definition.description)}</dd>")
                line("</div>")
            }
            line("</dl>")
            line("</section>")
        }

        private fun summary() {
            val summary = content.summary
            line("<section class=\"${HtmlClass.SECTION.cssName}\">")
            line("<h2>${bilingual("Summary", "摘要")}</h2>")
            line("<p>${bilingual("Adverse items", "不利项")}: ${summary.adverseItems.size}</p>")
            summary.pendingRemediationCount?.let {
                line("<p>${bilingual("Pending remediation", "待处理")}: $it</p>")
            }
            line("<table class=\"${HtmlClass.SUMMARY_COUNTS.cssName}\">")
            line("<caption>${bilingual("Status counts by room", "各房间评级统计")}</caption>")
            headerRow("Room" to "房间", "Status" to "评级", "Count" to "数量")
            line("<tbody>")
            summary.roomStatusCounts.forEach { count ->
                val label = roomLabels[count.roomId]
                line(
                    "<tr><th scope=\"row\">${label?.let(::bilingual) ?: text(count.roomId)}</th>" +
                        "<td>${text(count.status)}</td><td>${count.count}</td></tr>",
                )
            }
            line("</tbody>")
            line("</table>")
            if (summary.adverseItems.isNotEmpty()) {
                line("<ul class=\"${HtmlClass.SUMMARY_ADVERSE.cssName}\">")
                summary.adverseItems.forEach { item ->
                    val room = roomLabels[item.roomId]?.let(::bilingual) ?: text(item.roomId)
                    val note = item.note?.let { " — ${original(it)}" }.orEmpty()
                    line("<li>$room: ${bilingual(item.label)} (${text(item.status)})$note</li>")
                }
                line("</ul>")
            }
            line("</section>")
        }

        private fun room(room: ReportContentRoom) {
            line("<section class=\"${HtmlClass.SECTION.cssName} ${HtmlClass.ROOM.cssName}\">")
            line("<h2>${bilingual(room.label)}</h2>")
            gallery(room.photos) { photo ->
                "Room photograph ${photo.reference} of ${room.label.en}"
            }
            if (room.items.isNotEmpty()) {
                line("<table class=\"${HtmlClass.ITEM_TABLE.cssName}\">")
                line("<caption>${bilingual("Inspection items", "检查项")}</caption>")
                headerRow("Item" to "检查项", "Status" to "评级", "Notes" to "备注", "Evidence" to "证据")
                line("<tbody>")
                room.items.forEach { item -> itemRow(room, item) }
                line("</tbody>")
                line("</table>")
            }
            line("</section>")
        }

        private fun itemRow(room: ReportContentRoom, item: ReportContentItem) {
            line("<tr class=\"${HtmlClass.ITEM_ROW.cssName}\">")
            line("<th scope=\"row\">${bilingual(item.label)}</th>")
            // The status word itself is in the cell, never a colour alone: a reader in forced-colours mode,
            // in greyscale print, or with a colour vision deficiency reads exactly what everyone else reads.
            line("<td class=\"${HtmlClass.ITEM_STATUS.cssName}\">${text(item.status)}</td>")
            line("<td>")
            item.note?.let { line("<p class=\"${HtmlClass.ITEM_NOTE.cssName}\">${original(it)}</p>") }
            item.wearOrDamage?.let {
                line(
                    "<p class=\"${HtmlClass.ITEM_WEAR.cssName}\">" +
                        "${bilingual("Wear or damage", "损耗或损坏")}: ${original(it)}</p>",
                )
            }
            line("</td>")
            line("<td>")
            gallery(item.photos) { photo ->
                "Evidence photograph ${photo.reference} for ${item.label.en} in ${room.label.en}"
            }
            line("</td>")
            line("</tr>")
        }

        private fun gallery(photos: List<ReportContentPhoto>, alt: (ReportContentPhoto) -> String) {
            if (photos.isEmpty()) return
            line("<div class=\"${HtmlClass.EVIDENCE_GALLERY.cssName}\">")
            photos.forEach { photo -> figure(photo, alt(photo)) }
            line("</div>")
        }

        /**
         * A figure is emitted whether or not its picture could be embedded: dropping it would silently
         * renumber evidence the reader may be holding the PDF of. An unreadable file costs the picture and
         * nothing else — number, source and capture time still appear, and so does a statement that the
         * picture is missing rather than absent from the inspection.
         */
        private fun figure(photo: ReportContentPhoto, alt: String) {
            line("<figure class=\"${HtmlClass.EVIDENCE_FIGURE.cssName}\">")
            val image = embed(photo)
            if (image == null) {
                line(
                    "<p class=\"${HtmlClass.EVIDENCE_MISSING.cssName}\">" +
                        "${bilingual("Photograph not embedded", "照片未内嵌")}</p>",
                )
            } else {
                val encoded = Base64.getEncoder().encodeToString(image.bytes)
                line("<img src=\"data:${attribute(image.mediaType)};base64,$encoded\" alt=\"${attribute(alt)}\">")
            }
            line(
                "<figcaption class=\"${HtmlClass.EVIDENCE_CAPTION.cssName}\">" +
                    "${text("${photo.reference} · ${photo.source} · ${isoUtc(photo.capturedAt)}")}</figcaption>",
            )
            line("</figure>")
        }

        private fun embed(photo: ReportContentPhoto): EmbeddedImage? {
            // The port is offered what this document can still accept, never the per-image ceiling on
            // its own: offering more would have it materialise bytes that are then refused, which is the
            // very allocation the ceiling exists to prevent. Once nothing is left the port is not called.
            val ceiling = minOf(bounds.maxImageBytes.toLong(), bounds.maxTotalImageBytes - spentImageBytes)
            if (ceiling <= 0L) return null
            // A port that hands back bytes the document may not carry is refusing that one picture,
            // not failing the report: the figure below still appears, numbered and captioned. Only this
            // one type is caught, so a genuine defect in the port still surfaces instead of quietly
            // turning into a report with photographs missing.
            val image = try {
                images.read(photo, ceiling.toInt())
            } catch (rejected: RejectedEvidenceException) {
                null
            } ?: return null
            // The backstop for a port that overshoots the limit it was handed anyway.
            if (image.bytes.size > ceiling) return null
            spentImageBytes += image.bytes.size
            return image
        }

        /**
         * The source claim, under its own heading and its own wording. It attests the document the report
         * was imported from and says so; the native data hash in the footer attests the finalized native
         * evidence and says that. Neither is ever printed as the other.
         */
        private fun provenance(provenance: LegacyImportProvenance) {
            line("<section class=\"${HtmlClass.SECTION.cssName} ${HtmlClass.PROVENANCE.cssName}\">")
            line("<h2>${bilingual("Imported source", "导入来源")}</h2>")
            line(
                "<p>${bilingual(
                    "These hashes attest the source document this report was imported from, not the native evidence.",
                    "以下哈希证明本报告所导入的源文件，不证明原生证据。",
                )}</p>",
            )
            line("<dl>")
            field("Source hash", "源文件哈希", provenance.sourceSha256)
            field("Normalized manifest hash", "归一化清单哈希", provenance.normalizedManifestSha256)
            field("Mapping receipt hash", "映射回执哈希", provenance.mappingReceiptSha256)
            field("Extractor version", "提取器版本", provenance.extractorVersion)
            provenance.sourceReportDate?.let { field("Source report date", "源报告日期", it) }
            line("</dl>")
            line("</section>")
        }

        private fun closing() {
            line("<section class=\"${HtmlClass.SECTION.cssName}\">")
            line("<h2>${bilingual("Closing", "报告结尾")}</h2>")
            if (content.remediations.isNotEmpty()) {
                line("<h3>${bilingual("Remediation", "整改建议")}</h3>")
                content.remediations.forEach { remediation ->
                    line("<div class=\"${HtmlClass.REMEDIATION.cssName}\">")
                    line(
                        "<p class=\"${HtmlClass.REMEDIATION_URGENCY.cssName}\">" +
                            "${bilingual("Urgency", "紧急度")}: ${text(remediation.urgency.name)}</p>",
                    )
                    line("<p>${bilingual(remediation.text)}</p>")
                    line("</div>")
                }
            }
            if (content.supplements.isNotEmpty()) {
                line("<h3>${bilingual("Supplements", "补充说明")}</h3>")
                content.supplements.forEach { supplement ->
                    line(
                        "<p class=\"${HtmlClass.SUPPLEMENT.cssName}\">" +
                            "${text(supplement.reference)}: ${original(supplement.text)}</p>",
                    )
                }
            }
            line("<h3>${bilingual("Disclaimer", "免责声明")}</h3>")
            line("<p class=\"${HtmlClass.DISCLAIMER.cssName}\">${bilingual(content.disclaimer)}</p>")
            content.tenantAgreement?.let {
                line("<h3>${bilingual("Tenant agreement", "租客确认")}</h3>")
                line("<p class=\"${HtmlClass.TENANT_AGREEMENT.cssName}\">${bilingual(it)}</p>")
            }
            line("</section>")
        }

        private fun integrity() {
            line("<footer class=\"${HtmlClass.INTEGRITY.cssName}\">")
            line("<dl>")
            integrityField("Native data hash", "原生数据哈希", content.nativeIntegrity.dataHash)
            integrityField("Semantic fingerprint", "语义指纹", content.semanticFingerprint)
            integrityField("Content contract version", "内容合同版本", content.contractVersion.toString())
            integrityField("Photo scope", "照片范围", content.privatePhotoScope.name)
            integrityField("Origin", "来源", content.origin.name)
            line("</dl>")
            line("</footer>")
        }

        /** `scope="col"` on every heading cell, so a screen reader can name the column of any cell read. */
        private fun headerRow(vararg columns: Pair<String, String>) {
            line(
                columns.joinToString("", "<thead><tr>", "</tr></thead>") { (en, zh) ->
                    "<th scope=\"col\">${bilingual(en, zh)}</th>"
                },
            )
        }

        private fun field(en: String, zh: String, value: String) {
            line("<dt>${bilingual(en, zh)}</dt>")
            line("<dd>${text(value)}</dd>")
        }

        private fun integrityField(en: String, zh: String, value: String) {
            line("<div class=\"${HtmlClass.INTEGRITY_LABEL.cssName}\">")
            field(en, zh, value)
            line("</div>")
        }

        private fun line(value: String) {
            out.append(value).append('\n')
        }
    }

    internal companion object {
        /** The spelling the PDF footer and cover use, so one inspection reads the same in both formats. */
        private val ISO_UTC: DateTimeFormatter =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        fun isoUtc(epochMillis: Long): String = ISO_UTC.format(Instant.ofEpochMilli(epochMillis))

        /**
         * `docs/SECURITY.md` requires the report to deny network, navigation and active content *by
         * policy*, not merely by the renderer emitting none. Everything is denied, then two things are
         * re-permitted: `data:` images, and the one stylesheet **by hash** rather than `'unsafe-inline'`,
         * so the policy admits that exact text and no other inline style. `frame-ancestors` is absent on
         * purpose: it is ignored in a meta policy, and a directive that does nothing where it is written
         * reads as protection while giving none.
         */
        const val POLICY_HEAD = "default-src 'none'; img-src data:; style-src "
        const val POLICY_TAIL = "base-uri 'none'; form-action 'none'"

        fun styleHash(css: String): String = Base64.getEncoder()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(css.toByteArray(Charsets.UTF_8)))

        fun text(value: String) = HtmlEscaping.text(value)

        fun attribute(value: String) = HtmlEscaping.attribute(value)

        fun bilingual(value: BilingualText) = bilingual(value.en, value.zh)

        /** Template text is bilingual by construction, so each half can be tagged with the language it is. */
        fun bilingual(en: String, zh: String) =
            "<span class=\"${HtmlClass.TEXT_EN.cssName}\" lang=\"en\">${text(en)}</span> " +
                "<span class=\"${HtmlClass.TEXT_ZH.cssName}\" lang=\"zh\">${text(zh)}</span>"

        /**
         * Free text keeps the language it was dictated in, and this renderer does not know what that is -
         * so it says exactly that, with `lang=""`, which is HTML's "undetermined".
         *
         * Leaving the attribute off is not the same statement and was the earlier bug here: an absent
         * `lang` **inherits** `<html lang="en">`, so a Chinese dictated note was announced by a screen
         * reader in an English voice - the precise outcome this comment used to claim it avoided. Empty
         * means undetermined and stops that inheritance; a guessed value would be worse still.
         */
        fun original(value: String) =
            "<span class=\"${HtmlClass.TEXT_ORIGINAL.cssName}\" lang=\"\">${text(value)}</span>"
    }
}
