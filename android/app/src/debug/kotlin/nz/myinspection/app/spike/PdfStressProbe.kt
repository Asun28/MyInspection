package nz.myinspection.app.spike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Debug
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val pdfProbeRunning = AtomicBoolean(false)

/** Debug-only synthetic probe. Call serially on a worker thread; replaces only its own files.
 * PSS is the whole process's maximum at named checkpoints, not a continuous or absolute peak.
 * Elapsed time includes placeholder encoding, PDF writing and memory sampling.
 */
internal fun runPdfStress(context: Context): String {
    check(Looper.myLooper() != Looper.getMainLooper()) { "PDF probe requires a worker thread" }
    if (!pdfProbeRunning.compareAndSet(false, true)) return "PDF 探针正在运行；请等待上一轮结束后重试。"
    return try {
        val startedAt = System.currentTimeMillis()
        val started = SystemClock.elapsedRealtime()
        val directory = File(checkNotNull(context.getExternalFilesDir(null)), "spike")
        check(directory.isDirectory || directory.mkdirs())
        val pdf = File(directory, "platform-stress.pdf")
        val pendingPdf = File(directory, "platform-stress.pdf.tmp")
        val receipt = File(directory, "receipt.json")
        val pendingReceipt = File(directory, "receipt.json.tmp")
        for (file in listOf(receipt, pendingReceipt, pdf, pendingPdf)) {
            check(!file.exists() || file.delete())
        }
        var peakPssKiB = 0
        var samples = 0
        fun samplePss(): Int {
            val memory = Debug.MemoryInfo()
            Debug.getMemoryInfo(memory)
            val pss = memory.totalPss
            check(pss > 0) { "PSS unavailable" }
            peakPssKiB = maxOf(peakPssKiB, pss)
            samples++
            return pss
        }
        val baselinePssKiB = samplePss()
        val fontAsset = "fonts/DroidSansFallback.ttf"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.createFromAsset(context.assets, fontAsset)
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var images = 0
        var recycledImages = 0
        var pages = 0
        val document = PdfDocument()
        try {
            repeat(80) { index ->
                val source = Bitmap.createBitmap(2048, 1536, Bitmap.Config.ARGB_8888)
                val encoded = try {
                    samplePss()
                    val canvas = Canvas(source)
                    canvas.drawColor(Color.rgb(160 + index, 200, 240 - index))
                    val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f }
                    canvas.drawRect(100f, 100f, 1948f, 1436f, marker)
                    marker.color = Color.WHITE
                    canvas.drawText("Placeholder ${index + 1} / 80", 220f, 800f, marker)
                    ByteArrayOutputStream().use { output ->
                        check(source.compress(Bitmap.CompressFormat.JPEG, 85, output))
                        output.toByteArray()
                    }
                } finally {
                    source.recycle()
                }
                val bitmap = checkNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size, options))
                try {
                    check(bitmap.width == 512 && bitmap.height == 384)
                    samplePss()
                    val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 1).create())
                    try {
                        page.canvas.drawText("Image ${index + 1} / 80", 42f, 64f, text)
                        page.canvas.drawBitmap(bitmap, null, Rect(42, 100, 554, 484), null)
                        images++
                    } finally {
                        document.finishPage(page)
                    }
                } finally {
                    bitmap.recycle()
                    if (bitmap.isRecycled) recycledImages++
                }
                samplePss()
            }
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 81).create())
            try {
                page.canvas.drawText("平台 PDF 压力测试 / Platform PDF stress", 42f, 80f, text)
                page.canvas.drawText("巡检 Inspection · 房间 Room · 正常 Good", 42f, 120f, text)
                page.canvas.drawText("中文：墙面、门窗、厨房、浴室。", 42f, 160f, text)
                page.canvas.drawText("English: walls, doors, kitchen, bathroom.", 42f, 200f, text)
            } finally {
                document.finishPage(page)
            }
            pages = document.pages.size
            check(pages == 81 && images == 80 && recycledImages == 80)
            pendingPdf.outputStream().use { document.writeTo(it) }
            samplePss()
        } finally {
            document.close()
        }
        samplePss()
        check(pendingPdf.length() > 0 && pendingPdf.renameTo(pdf))
        val elapsedMs = SystemClock.elapsedRealtime() - started
        val result = JSONObject().put("status", "complete").put("startedAtEpochMs", startedAt)
            .put("deviceModel", Build.MODEL).put("buildFingerprint", Build.FINGERPRINT)
            .put("sdkInt", Build.VERSION.SDK_INT).put("pages", pages).put("images", images)
            .put("recycledImages", recycledImages).put("sourceWidth", 2048).put("sourceHeight", 1536)
            .put("decodedWidth", 512).put("decodedHeight", 384).put("inSampleSize", options.inSampleSize)
            .put("fontAsset", fontAsset).put("pdfBytes", pdf.length()).put("elapsedMs", elapsedMs)
            .put("baselinePssKiB", baselinePssKiB).put("sampledPeakPssKiB", peakPssKiB)
            .put("pssSamples", samples).put("memoryScope", "whole process; checkpoint maximum, not absolute peak")
            .put("checkpoints", "baseline; each source allocation, decode, recycle; PDF write; document close")
        pendingReceipt.writeText(result.toString(2), Charsets.UTF_8)
        check(pendingReceipt.renameTo(receipt))
        "PDF 完成：$pages 页 / $images 图，${elapsedMs}ms；采样 PSS 最大 ${peakPssKiB} KiB"
    } catch (failure: Exception) {
        "PDF 失败：${failure.javaClass.simpleName}；本次未生成成功记录"
    } finally {
        pdfProbeRunning.set(false)
    }
}
