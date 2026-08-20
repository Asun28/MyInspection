package nz.myinspection.core.media

import java.awt.Color
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoQualityProfileTest {
    @Test
    fun `four profiles pin the published long-edge and JPEG-quality contract`() {
        assertEquals(
            listOf(
                PhotoQualityProfile.LOW,
                PhotoQualityProfile.MEDIUM,
                PhotoQualityProfile.HIGH,
                PhotoQualityProfile.EXTRA_HIGH,
            ),
            PhotoQualityProfile.entries.toList(),
            "the user-facing profile contract must expose exactly these four entries in this order",
        )
        assertEquals(PhotoQualityProfile.MEDIUM, PhotoQualityProfile.DEFAULT)
        assertEquals("low", PhotoQualityProfile.LOW.storedValue)
        assertEquals(1280, PhotoQualityProfile.LOW.maximumLongEdgePx)
        assertEquals(75, PhotoQualityProfile.LOW.jpegQuality)
        assertEquals("medium", PhotoQualityProfile.MEDIUM.storedValue)
        assertEquals(1920, PhotoQualityProfile.MEDIUM.maximumLongEdgePx)
        assertEquals(82, PhotoQualityProfile.MEDIUM.jpegQuality)
        assertEquals("high", PhotoQualityProfile.HIGH.storedValue)
        assertEquals(2560, PhotoQualityProfile.HIGH.maximumLongEdgePx)
        assertEquals(88, PhotoQualityProfile.HIGH.jpegQuality)
        assertEquals("extra_high", PhotoQualityProfile.EXTRA_HIGH.storedValue)
        assertEquals(4096, PhotoQualityProfile.EXTRA_HIGH.maximumLongEdgePx)
        assertEquals(92, PhotoQualityProfile.EXTRA_HIGH.jpegQuality)

        assertEquals(PhotoQualityProfile.MEDIUM, PhotoQualityProfile.fromStoredValue(null))
        assertEquals(PhotoQualityProfile.MEDIUM, PhotoQualityProfile.fromStoredValue("unknown-future-value"))
        assertEquals(PhotoQualityProfile.EXTRA_HIGH, PhotoQualityProfile.fromStoredValue("extra_high"))
    }

    @Test
    fun `profiles cap post-orientation dimensions proportionally without enlarging smaller images`() {
        assertEquals(PhotoDimensions(1280, 480), PhotoQualityProfile.LOW.scaledDimensions(4096, 1536))
        assertEquals(PhotoDimensions(1920, 720), PhotoQualityProfile.MEDIUM.scaledDimensions(4096, 1536))
        assertEquals(PhotoDimensions(2560, 960), PhotoQualityProfile.HIGH.scaledDimensions(4096, 1536))
        assertEquals(PhotoDimensions(4096, 1536), PhotoQualityProfile.EXTRA_HIGH.scaledDimensions(4096, 1536))

        val postOrientationPortrait = PhotoQualityProfile.MEDIUM.scaledDimensions(3000, 4000)
        assertEquals(PhotoDimensions(1440, 1920), postOrientationPortrait)

        for (profile in PhotoQualityProfile.entries) {
            assertEquals(
                PhotoDimensions(800, 600),
                profile.scaledDimensions(800, 600),
                "$profile must never enlarge a smaller source bitmap",
            )
        }
    }

    /**
     * This is parameter-contract evidence only: the deterministic fixtures use the same dimensions and quality
     * factors through JDK ImageIO, not Android's Bitmap.compress. Android byte equality is neither expected nor
     * asserted; the Android adapter is covered by the compile/wiring guard in [PhotoStreamingWiringTest].
     */
    @Test
    fun `fixed panorama plate low-light and high-entropy fixtures have capped dimensions and monotonic aggregate JPEG bytes`() {
        val fixtures = listOf(
            FixtureFactory("room panorama", ::panoramaFixture),
            FixtureFactory("plate text") { plateFixture().image },
            FixtureFactory("low light", ::lowLightFixture),
            FixtureFactory("high entropy", ::highEntropyFixture),
        )
        val totalBytesByProfile = PhotoQualityProfile.entries.associateWith { 0L }.toMutableMap()

        for (fixture in fixtures) {
            val source = fixture.create()
            try {
                for (profile in PhotoQualityProfile.entries) {
                    val target = profile.scaledDimensions(source.width, source.height)
                    val scaled = scale(source, target)
                    try {
                        val encoded = encodeJpeg(scaled, profile.jpegQuality)
                        val decoded = checkNotNull(ImageIO.read(ByteArrayInputStream(encoded))) { "${fixture.name} did not decode after ImageIO JPEG encoding" }
                        try {
                            assertEquals(target.width, decoded.width, "${fixture.name}: $profile width must follow the core cap")
                            assertEquals(target.height, decoded.height, "${fixture.name}: $profile height must follow the core cap")
                            assertTrue(decoded.width <= source.width, "${fixture.name}: $profile must not upscale width")
                            assertTrue(decoded.height <= source.height, "${fixture.name}: $profile must not upscale height")
                        } finally {
                            decoded.flush()
                        }
                        totalBytesByProfile[profile] = totalBytesByProfile.getValue(profile) + encoded.size
                    } finally {
                        if (scaled !== source) scaled.flush()
                    }
                }
            } finally {
                source.flush()
            }
        }

        assertTrue(totalBytesByProfile.getValue(PhotoQualityProfile.LOW) < totalBytesByProfile.getValue(PhotoQualityProfile.MEDIUM))
        assertTrue(totalBytesByProfile.getValue(PhotoQualityProfile.MEDIUM) < totalBytesByProfile.getValue(PhotoQualityProfile.HIGH))
        assertTrue(totalBytesByProfile.getValue(PhotoQualityProfile.HIGH) < totalBytesByProfile.getValue(PhotoQualityProfile.EXTRA_HIGH))
    }

    @Test
    fun `high and extra high retain an objective dark plate-glyph contrast after parameter-contract encoding`() {
        val fixture = plateFixture()

        for (profile in listOf(PhotoQualityProfile.HIGH, PhotoQualityProfile.EXTRA_HIGH)) {
            val target = profile.scaledDimensions(fixture.image.width, fixture.image.height)
            val decoded = checkNotNull(
                ImageIO.read(ByteArrayInputStream(encodeJpeg(scale(fixture.image, target), profile.jpegQuality))),
            ) { "$profile plate fixture did not decode" }
            val dark = luminance(decoded, scalePoint(fixture.darkGlyphPoint, fixture.image, decoded))
            val light = luminance(decoded, scalePoint(fixture.lightPlatePoint, fixture.image, decoded))

            assertTrue(
                light - dark >= 100.0,
                "$profile must retain at least 100 luma levels between a plate glyph and its background; observed ${light - dark}",
            )
        }
    }

    private data class FixtureFactory(val name: String, val create: () -> BufferedImage)

    private data class PlateFixture(
        val image: BufferedImage,
        val darkGlyphPoint: Point,
        val lightPlatePoint: Point,
    )

    private fun panoramaFixture(): BufferedImage = BufferedImage(4096, 1536, BufferedImage.TYPE_INT_RGB).also { image ->
        image.draw { graphics ->
            graphics.color = Color(132, 177, 219)
            graphics.fillRect(0, 0, image.width, 860)
            graphics.color = Color(203, 190, 162)
            graphics.fillRect(0, 860, image.width, image.height - 860)
            graphics.color = Color(157, 132, 101)
            graphics.fillRect(300, 340, 780, 690)
            graphics.color = Color(97, 75, 55)
            graphics.fillRect(360, 400, 660, 630)
            graphics.color = Color(236, 237, 225)
            graphics.fillRect(1300, 220, 960, 700)
            graphics.color = Color(50, 72, 90)
            graphics.fillRect(1380, 300, 800, 520)
            graphics.color = Color(182, 151, 111)
            graphics.fillRect(2600, 620, 1120, 420)
            graphics.color = Color(90, 73, 58)
            repeat(12) { index -> graphics.fillRect(2640 + index * 86, 660, 42, 280) }
        }
    }

    private fun plateFixture(): PlateFixture {
        val image = BufferedImage(4096, 1024, BufferedImage.TYPE_INT_RGB)
        val plateLeft = 700
        val plateTop = 220
        val glyphLeft = 1500
        val glyphTop = 360
        val cell = 32
        image.draw { graphics ->
            graphics.color = Color(66, 74, 82)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(225, 213, 164)
            graphics.fillRoundRect(plateLeft, plateTop, 2700, 580, 36, 36)
            graphics.color = Color(35, 38, 42)
            graphics.fillRect(plateLeft + 55, plateTop + 55, 2590, 470)
            graphics.color = Color(245, 239, 205)
            graphics.fillRect(plateLeft + 90, plateTop + 90, 2520, 400)
            graphics.color = Color(18, 18, 18)
            drawGlyph(graphics, glyphLeft, glyphTop, cell, listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"))
            drawGlyph(graphics, glyphLeft + 8 * cell, glyphTop, cell, listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"))
            drawGlyph(graphics, glyphLeft + 16 * cell, glyphTop, cell, listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"))
        }
        return PlateFixture(
            image = image,
            darkGlyphPoint = Point(glyphLeft + cell + cell / 2, glyphTop + cell / 2),
            lightPlatePoint = Point(plateLeft + 300, plateTop + 300),
        )
    }

    private fun lowLightFixture(): BufferedImage = BufferedImage(3072, 1728, BufferedImage.TYPE_INT_RGB).also { image ->
        image.draw { graphics ->
            graphics.color = Color(15, 18, 24)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(24, 28, 37)
            graphics.fillRect(220, 260, 1120, 970)
            graphics.color = Color(34, 31, 28)
            graphics.fillRect(1700, 460, 890, 750)
            graphics.color = Color(118, 98, 70)
            graphics.fillOval(2140, 240, 280, 280)
            graphics.color = Color(74, 103, 125)
            repeat(10) { index -> graphics.fillRect(340 + index * 242, 1320, 105, 105) }
        }
    }

    private fun highEntropyFixture(): BufferedImage {
        val image = BufferedImage(2048, 1536, BufferedImage.TYPE_INT_RGB)
        var state = 0x13579BDF
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                state = state * 1_664_525 + 1_013_904_223
                val red = state ushr 24 and 0xff
                state = state * 1_664_525 + 1_013_904_223
                val green = state ushr 24 and 0xff
                state = state * 1_664_525 + 1_013_904_223
                val blue = state ushr 24 and 0xff
                image.setRGB(x, y, red shl 16 or (green shl 8) or blue)
            }
        }
        return image
    }

    private fun drawGlyph(graphics: Graphics2D, left: Int, top: Int, cell: Int, rows: List<String>) {
        rows.forEachIndexed { row, pattern ->
            pattern.forEachIndexed { column, bit ->
                if (bit == '1') graphics.fillRect(left + column * cell, top + row * cell, cell, cell)
            }
        }
    }

    private fun scale(source: BufferedImage, target: PhotoDimensions): BufferedImage {
        if (source.width == target.width && source.height == target.height) return source
        return BufferedImage(target.width, target.height, BufferedImage.TYPE_INT_RGB).also { scaled ->
            scaled.draw { graphics ->
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                graphics.drawImage(source, 0, 0, target.width, target.height, null)
            }
        }
    }

    private fun encodeJpeg(image: BufferedImage, quality: Int): ByteArray {
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        check(writers.hasNext()) { "JDK ImageIO has no JPEG writer" }
        val writer = writers.next()
        try {
            ByteArrayOutputStream().use { bytes ->
                ImageIO.createImageOutputStream(bytes).use { output ->
                    writer.output = output
                    writer.defaultWriteParam.apply {
                        compressionMode = ImageWriteParam.MODE_EXPLICIT
                        compressionQuality = quality / 100f
                    }.also { parameters ->
                        writer.write(null, IIOImage(image, null, null), parameters)
                    }
                }
                return bytes.toByteArray()
            }
        } finally {
            writer.dispose()
        }
    }

    private fun scalePoint(point: Point, source: BufferedImage, target: BufferedImage): Point = Point(
        (point.x.toLong() * target.width / source.width).toInt().coerceIn(0, target.width - 1),
        (point.y.toLong() * target.height / source.height).toInt().coerceIn(0, target.height - 1),
    )

    private fun luminance(image: BufferedImage, point: Point): Double {
        val rgb = image.getRGB(point.x, point.y)
        val red = rgb ushr 16 and 0xff
        val green = rgb ushr 8 and 0xff
        val blue = rgb and 0xff
        return red * 0.2126 + green * 0.7152 + blue * 0.0722
    }

    private inline fun BufferedImage.draw(block: (Graphics2D) -> Unit) {
        val graphics = createGraphics()
        try {
            block(graphics)
        } finally {
            graphics.dispose()
        }
    }
}
