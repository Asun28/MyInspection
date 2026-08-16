package nz.myinspection.app.skeleton

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.format.DateTimeFormatter

/**
 * T1-SKELETON-E2E · 最笨的一页 PDF。**用完即弃**。
 *
 * 刻意不做（见卡片 non_goals）：不分页、不排版引擎、无双版本（房东/房客）、无 CJK 字体嵌入、
 * 无页脚数据哈希、无免责声明。真实渲染器是 T3-REPORT-COMPOSER + T3-PDF-RENDERER 的产出。
 *
 * DoD 要求真机走查时**看得见**检查项文字与其状态——这条不打折。只有 CJK **字形**（中文标签在缺字体的
 * 设备上可能出方框）不归本卡：字体嵌入是 T3-PDF-RENDERER 的活（见其卡的 DroidSansFallback 断言）。
 * 两者别混：「状态文字必须画出来」是本卡的义务，「中文字形一定渲染得出」不是。
 */
object SkeletonPdf {

    // A4 @ 72dpi，PdfDocument 的坐标单位就是 point。
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** 把一次巡检画成一页 PDF 写进 [out]。[out] 由调用方（SAF）提供并负责关闭。 */
    fun write(inspection: SkeletonInspection, out: OutputStream) {
        val doc = PdfDocument()
        try {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            draw(page.canvas, inspection)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    private fun draw(canvas: Canvas, inspection: SkeletonInspection) {
        val title = Paint().apply { color = Color.BLACK; textSize = 22f; isFakeBoldText = true }
        val body = Paint().apply { color = Color.BLACK; textSize = 13f }
        val muted = Paint().apply { color = Color.DKGRAY; textSize = 11f }

        var y = MARGIN + title.textSize

        canvas.drawText("Inspection Report (skeleton)", MARGIN, y, title)
        y += 28f

        canvas.drawText(inspection.address, MARGIN, y, body)
        y += 18f
        canvas.drawText(inspection.startedAt.format(TIMESTAMP), MARGIN, y, muted)
        y += 28f

        val item = inspection.item
        if (item == null) {
            canvas.drawText("(no items recorded)", MARGIN, y, muted)
            return
        }

        canvas.drawText("${item.description} — ${item.condition.label}", MARGIN, y, body)
        y += 20f

        val photo = item.photo ?: run {
            canvas.drawText("(no photo)", MARGIN, y, muted)
            return
        }

        // 等比缩放进可用宽度，高度封顶，避免竖图撑出页面。
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        val maxHeight = PAGE_HEIGHT - y - MARGIN
        val scale = minOf(maxWidth / photo.width, maxHeight / photo.height)
        val destination = Rect(
            MARGIN.toInt(),
            y.toInt(),
            (MARGIN + photo.width * scale).toInt(),
            (y + photo.height * scale).toInt(),
        )
        canvas.drawBitmap(photo, null, destination, null)
    }
}
