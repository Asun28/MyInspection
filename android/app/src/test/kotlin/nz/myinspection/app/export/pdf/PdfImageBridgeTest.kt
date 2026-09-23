package nz.myinspection.app.export.pdf

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import nz.myinspection.core.report.ImagePurpose
import nz.myinspection.core.report.pdf.PdfImageOp
import nz.myinspection.core.report.pdf.PdfImagePlacement
import nz.myinspection.core.report.pdf.PdfSourcePixels

class PdfImageBridgeTest {
    private val photo = "018f4a5e-1267-7d3a-8b18-0425d7f0b4aa"
    private val other = "018f4a5e-1267-7d3a-8b18-0425d7f0b4bb"
    private val oneOp = listOf("open:$photo", "bounds", "close", "open:$photo", "decode:2", "close", "draw", "recycle")

    @Test
    fun `landscape picture fills the frame width and is centred vertically`() {
        val port = FakePort(PdfSourcePixels(2000, 800), 1000 to 400)
        PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 400))

        assertEquals(oneOp, port.events)
        val (_, sourceRect, destination) = port.draws.single()
        assertEquals(PdfPixelRect(0, 0, 1000, 400), sourceRect)
        assertRect(PdfPointRect(10f, 25f, 110f, 65f), destination)
        assertEquals(0, port.live)
    }

    @Test
    fun `portrait picture fills the frame height and is centred horizontally`() {
        val port = FakePort(PdfSourcePixels(1000, 2000), 1000 to 2000)
        PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500))

        assertEquals("decode:1", port.events[4])
        assertRect(PdfPointRect(47.5f, 20f, 72.5f, 70f), port.draws.single().third)
    }

    @Test
    fun `square picture in a tall offset frame keeps its aspect ratio`() {
        val port = FakePort(PdfSourcePixels(600, 600), 300 to 300)
        PdfImageBridge(port).draw(op(30, 40, 80, 100, 300, 300))

        assertEquals("decode:2", port.events[4])
        assertEquals(PdfPixelRect(0, 0, 300, 300), port.draws.single().second)
        assertRect(PdfPointRect(30f, 50f, 110f, 130f), port.draws.single().third)
    }

    @Test
    fun `long picture samples at eight and is drawn whole, narrow and centred`() {
        val port = FakePort(PdfSourcePixels(4200, 18400), 525 to 2300)
        PdfImageBridge(port).draw(op(30, 40, 80, 100, 500, 2000))

        assertEquals("decode:8", port.events[4])
        assertEquals(PdfPixelRect(0, 0, 525, 2300), port.draws.single().second)
        assertRect(PdfPointRect(58.586957f, 40f, 81.413043f, 140f), port.draws.single().third)
    }

    @Test
    fun `fitted rectangle follows the decoder's odd dimensions, not the source bounds`() {
        val port = FakePort(PdfSourcePixels(4000, 2000), 999 to 501)
        PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500))

        assertEquals("decode:4", port.events[4])
        assertEquals(PdfPixelRect(0, 0, 999, 501), port.draws.single().second)
        assertRect(PdfPointRect(10.149701f, 20f, 109.850299f, 70f), port.draws.single().third)
    }

    @Test
    fun `a source one pixel short of four times the target samples at two`() {
        val port = FakePort(PdfSourcePixels(3999, 2000), 2000 to 1000)
        PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500))

        assertEquals("decode:2", port.events[4])
    }

    @Test
    fun `consecutive operations hold at most one decoded picture and finish each before the next`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val bridge = PdfImageBridge(port)
        bridge.draw(op(10, 20, 100, 50, 1000, 500))
        bridge.draw(op(10, 80, 100, 50, 1000, 500, other))

        assertEquals(oneOp + oneOp.map { it.replace(photo, other) }, port.events)
        assertEquals(1, port.maxLive)
        assertEquals(0, port.live)
        assertRect(PdfPointRect(10f, 80f, 110f, 130f), port.draws[1].third)
    }

    @Test
    fun `an empty frame is refused before any source is opened`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        assertFailsWith<IllegalArgumentException> { PdfImageBridge(port).draw(op(10, 20, 0, 50, 1000, 500)) }
        assertFailsWith<IllegalArgumentException> { PdfImageBridge(port).draw(op(10, 20, 100, 0, 1000, 500)) }
        assertEquals(emptyList(), port.events)
    }

    @Test
    fun `an open failure propagates untouched with nothing else attempted`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("open")
        port.onOpen = { throw failure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf("open:$photo"), port.events)
    }

    @Test
    fun `undecodable bounds close the stream and stop before decoding`() {
        val port = FakePort(null, 1000 to 500)
        assertFailsWith<PdfImageBridgeException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertEquals(listOf("open:$photo", "bounds", "close"), port.events)
    }

    @Test
    fun `a bounds failure still closes its stream and propagates`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("bounds")
        port.onBounds = { throw failure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf("open:$photo", "bounds", "close"), port.events)
    }

    @Test
    fun `a null decode closes the stream and never draws or fabricates a picture`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), null)
        assertFailsWith<PdfImageBridgeException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertEquals(listOf("open:$photo", "bounds", "close", "open:$photo", "decode:2", "close"), port.events)
        assertEquals(emptyList(), port.draws)
    }

    @Test
    fun `a decode failure closes its stream and propagates`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("decode")
        port.onDecode = { throw failure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf("open:$photo", "bounds", "close", "open:$photo", "decode:2", "close"), port.events)
    }

    @Test
    fun `a decoded picture without pixels on either axis is recycled and refused, not drawn`() {
        listOf(0 to 500, 500 to 0).forEach { decoded ->
            val port = FakePort(PdfSourcePixels(2000, 1000), decoded)
            assertFailsWith<PdfImageBridgeException>("decoded $decoded") { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
            assertEquals(listOf("open:$photo", "bounds", "close", "open:$photo", "decode:2", "close", "recycle"), port.events, "decoded $decoded")
            assertEquals(0, port.live)
        }
    }

    @Test
    fun `a draw failure still recycles and propagates`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IllegalStateException("draw")
        port.onDraw = { throw failure }

        val thrown = assertFailsWith<IllegalStateException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(oneOp, port.events)
        assertEquals(0, port.live)
    }

    @Test
    fun `a recycle failure after a successful draw propagates`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IllegalStateException("recycle")
        port.onRecycle = { throw failure }

        val thrown = assertFailsWith<IllegalStateException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(oneOp, port.events)
    }

    @Test
    fun `a stream close failure after a successful decode recycles the picture and propagates`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("close after decode")
        port.onClose = { nth -> if (nth == 2) throw failure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf("open:$photo", "bounds", "close", "open:$photo", "decode:2", "close", "recycle"), port.events)
        assertEquals(0, port.live)
        assertEquals(emptyList(), port.draws)
    }

    @Test
    fun `a stream close failure after bounds propagates before any decode`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("close after bounds")
        port.onClose = { nth -> if (nth == 1) throw failure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf("open:$photo", "bounds", "close"), port.events)
    }

    @Test
    fun `a close failure under an earlier failure is attached to it, not thrown in its place`() {
        val port = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val failure = IOException("decode")
        val closeFailure = IOException("close")
        port.onDecode = { throw failure }
        port.onClose = { nth -> if (nth == 2) throw closeFailure }

        val thrown = assertFailsWith<IOException> { PdfImageBridge(port).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(failure, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
    }

    @Test
    fun `a recycle failure during cleanup is attached to the failure already in flight`() {
        val recycleFailure = IllegalStateException("recycle")
        val underDraw = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val drawFailure = IllegalStateException("draw")
        underDraw.onDraw = { throw drawFailure }
        underDraw.onRecycle = { throw recycleFailure }
        val thrownForDraw = assertFailsWith<IllegalStateException> { PdfImageBridge(underDraw).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(drawFailure, thrownForDraw)
        assertEquals(listOf(recycleFailure), thrownForDraw.suppressed.toList())

        val underClose = FakePort(PdfSourcePixels(2000, 1000), 1000 to 500)
        val closeFailure = IOException("close after decode")
        underClose.onClose = { nth -> if (nth == 2) throw closeFailure }
        underClose.onRecycle = { throw recycleFailure }
        val thrownForClose = assertFailsWith<IOException> { PdfImageBridge(underClose).draw(op(10, 20, 100, 50, 1000, 500)) }
        assertSame(closeFailure, thrownForClose)
        assertEquals(listOf(recycleFailure), thrownForClose.suppressed.toList())
        assertEquals(emptyList(), underClose.draws)
    }

    private fun op(x: Int, y: Int, w: Int, h: Int, targetW: Int, targetH: Int, id: String = photo) =
        PdfImageOp(PdfImagePlacement(id, ImagePurpose.INLINE, x, y, w, h), targetW, targetH)

    private fun assertRect(expected: PdfPointRect, actual: PdfPointRect) {
        assertEquals(expected.left, actual.left, 0.001f, "left of $actual")
        assertEquals(expected.top, actual.top, 0.001f, "top of $actual")
        assertEquals(expected.right, actual.right, 0.001f, "right of $actual")
        assertEquals(expected.bottom, actual.bottom, 0.001f, "bottom of $actual")
    }

    /** Records every call in order and counts live pictures; it decides nothing the bridge is meant to decide. */
    private class FakePort(
        private val bounds: PdfSourcePixels?,
        private val decoded: Pair<Int, Int>?,
    ) : PdfImagePort<FakePort.Source, FakePort.Image> {
        class Source(val photoId: String)
        inner class Image(override val width: Int, override val height: Int) : PdfDecodedImage {
            override fun recycle() {
                live--
                events += "recycle"
                onRecycle()
            }
        }

        val events = mutableListOf<String>()
        val draws = mutableListOf<Triple<Image, PdfPixelRect, PdfPointRect>>()
        var live = 0
        var maxLive = 0
        private var closes = 0
        var onOpen: (String) -> Unit = {}
        var onBounds: () -> Unit = {}
        var onDecode: () -> Unit = {}
        var onDraw: () -> Unit = {}
        var onRecycle: () -> Unit = {}
        var onClose: (Int) -> Unit = {}

        override fun openSource(photoId: String): Source {
            events += "open:$photoId"
            onOpen(photoId)
            return Source(photoId)
        }

        override fun closeSource(source: Source) {
            events += "close"
            onClose(++closes)
        }

        override fun readBounds(source: Source): PdfSourcePixels? {
            events += "bounds"
            onBounds()
            return bounds
        }

        override fun decode(source: Source, sampleSize: Int): Image? {
            events += "decode:$sampleSize"
            onDecode()
            val (width, height) = decoded ?: return null
            live++
            maxLive = maxOf(maxLive, live)
            return Image(width, height)
        }

        override fun draw(image: Image, sourceRect: PdfPixelRect, destination: PdfPointRect) {
            events += "draw"
            onDraw()
            assertTrue(live == 1, "drawing requires exactly the one live picture, saw $live")
            draws += Triple(image, sourceRect, destination)
        }
    }
}

/*
 * R4 (T3-PDF-IMAGE-OWNERSHIP): 23/23 single-point compiling mutants of PdfImageBridge.kt killed. Kill evidence per
 * mutant is the TestNG report for PdfImageBridgeTest being produced (so the mutant compiled; 20 tests ran) with the
 * named case failing by java.lang.AssertionError. Production PdfImageBridge.kt SHA-256 at batch time and after each
 * restore:
 *   2033B709C1A59DF7956269F8AAB23E6F252E1C5D8373E3E8C21FF0D3A8968149
 * Command per mutant: cmd /c android\gradlew.bat -p android --offline --no-daemon --no-build-cache -q
 *   :app:testDebugUnitTest --tests nz.myinspection.app.export.pdf.PdfImageBridgeTest
 * M01 sample size not forwarded (decode at 1) -> landscape
 * M02 target width/height swapped into sampling -> landscape
 * M03 source width/height swapped into sampling -> landscape
 * M04 fit from source bounds instead of decoded dimensions -> odd decoder dimensions
 * M05 source rect from source bounds instead of decoded dimensions -> odd decoder dimensions
 * M06 source rect crops the decoded image -> landscape
 * M07 recycle omitted on success -> landscape
 * M08 recycle before draw -> landscape
 * M09 recycle skipped when draw fails -> draw failure
 * M10 decoded picture not released when its stream close fails -> close failure after decode
 * M11 no close attempt on the failure path -> bounds failure
 * M12 close failure replaces the earlier failure -> close under earlier failure
 * M13 success-path close dropped -> landscape
 * M14 close failure after success swallowed -> close failure after decode
 * M15 release failure replaces the close failure -> cleanup failure attached
 * M16 undecodable bounds fabricated as 1x1 -> undecodable bounds
 * M17 null decode raises a foreign exception type -> null decode
 * M18 zero-pixel decoded picture accepted -> no pixels
 * M19 zero-height decoded picture accepted -> no pixels
 * M20 zero-width decoded picture accepted -> no pixels
 * M21 empty frame checked only after the sources are opened -> empty frame
 * M22 recycle failure swallowed -> recycle failure
 * M23 recycle failure replaces the in-flight failure -> cleanup failure attached
 */
