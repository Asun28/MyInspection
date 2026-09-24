package nz.myinspection.core.report.importing.docx.image

import kotlin.test.*
import nz.myinspection.core.report.importing.docx.image.DocxImageFixture as F

class DocxImageQualifierTest {
    private val qualifier = DocxImageQualifier()

    private fun review(bytes: ByteArray): DocxImageQualification {
        val attempt = runCatching { qualifier.qualify(bytes) }
        assertTrue(attempt.isSuccess, "Unproven payload must remain reviewable")
        return attempt.getOrThrow().also { assertEquals(DocxImageDisposition.REVIEW_REQUIRED, it.disposition) }
    }
    private fun encoded(payload: ByteArray, header: ByteArray = F.header()) =
        F.pngChunks("IHDR" to header, "IDAT" to payload, "IEND" to byteArrayOf())

    private fun validatedCandidate(bytes: ByteArray) {
        assertEquals(DocxImageDisposition.VALIDATED_SMALL_CANDIDATE, qualifier.qualify(bytes).disposition)
    }

    @Test fun completeTinyRgbAndRgbaContentPatternsYieldValidatedCandidatesAtBothDimensionEdges() {
        for (channels in listOf(3, 4)) for (size in listOf(1, 24)) {
            val source = F.throughReader(F.png(size, size, channels))
            val result = qualifier.qualify(source)
            assertEquals(DocxImageDisposition.VALIDATED_SMALL_CANDIDATE, result.disposition)
            assertEquals(DocxImageDimensions(size, size), result.dimensions)
        }
    }

    @Test fun independentLiteralRgbVectorYieldsValidatedCandidate() {
        // Independently encoded with Python struct/zlib: one RGB pixel (3, 7, 11), filter 0.
        val source = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGNgZucGAAAmABYTihH6AAAAAElFTkSuQmCC")
        assertEquals(69, source.size)
        validatedCandidate(F.throughReader(source))
        assertEquals(DocxImageDimensions(1, 1), qualifier.qualify(source).dimensions)
    }

    @Test fun headerCandidatesDoNotAuthorizeExclusionWithoutPayloads() {
        val png = F.throughReader(F.png().copyOf(33))
        val jpeg = F.throughReader(F.jpegHeader(F.jpeg()), "jpg")
        for (source in listOf(png, jpeg)) {
            assertEquals(DocxImageDimensions(1, 1), review(source).dimensions)
        }
    }

    @Test fun completeJpegAndSubstantivePngRemainReviewable() {
        assertEquals(DocxImageDimensions(1, 1), review(F.throughReader(F.jpeg(), "jpg")).dimensions)
        for ((width, height) in listOf(25 to 1, 1 to 25, 25 to 25)) {
            assertEquals(DocxImageDimensions(width, height), review(F.png(width, height)).dimensions)
        }
    }

    @Test fun repeatedQualificationIsDeterministicAndDoesNotRetainOrMutateInput() {
        val source = F.png(24, 24, 4)
        val original = source.copyOf()
        val first = qualifier.qualify(source)
        assertEquals(first, qualifier.qualify(source))
        assertContentEquals(original, source)
        source.fill(0)
        assertEquals(DocxImageDimensions(24, 24), first.dimensions)
        assertEquals(DocxImageDisposition.VALIDATED_SMALL_CANDIDATE, first.disposition)
        assertEquals(first, qualifier.qualify(original))
    }

    @Test fun everyFilterSelectorIsValidForBothSupportedColorTypes() {
        for (channels in listOf(3, 4)) for (filter in 0..4) {
            validatedCandidate(F.png(24, 24, channels, IntArray(24) { filter }))
        }
    }

    @Test fun idatBoundariesMaySplitEveryByteOfZlibIncludingHeaderAndAdler() {
        val payload = F.zlib(F.scanlines(1, 1, 4))
        val parts = listOf("IHDR" to F.header(color = 6), "IDAT" to byteArrayOf()) +
            payload.map { "IDAT" to byteArrayOf(it) } +
            listOf("IDAT" to byteArrayOf(), "IEND" to byteArrayOf())
        validatedCandidate(F.pngChunks(*parts.toTypedArray()))
    }

    @Test fun unsupportedProfilesAndAncillaryChunksRemainReviewable() {
        val payload = F.zlib(F.scanlines(1, 1, 3))
        val headers = listOf(F.header(color = 0), F.header(color = 3), F.header(color = 4),
            F.header(depth = 16), F.header().apply { this[12] = 1 })
        for (header in headers) assertEquals(DocxImageDimensions(1, 1), review(encoded(payload, header)).dimensions)
        // An unsupported header cannot borrow the accepted RGBA scanline shape.
        review(encoded(F.zlib(F.scanlines(1, 1, 4)), F.header(color = 0)))
        for (type in listOf("tEXt", "PLTE", "acTL", "ABCD")) {
            review(F.pngChunks("IHDR" to F.header(), type to byteArrayOf(0),
                "IDAT" to payload, "IEND" to byteArrayOf()))
        }
    }

    @Test fun unknownCriticalAndAncillaryChunksHaveValidFramingButRequireReview() {
        val control = qualifier.qualify(F.png())
        assertEquals(DocxImageDisposition.VALIDATED_SMALL_CANDIDATE, control.disposition)
        assertEquals(DocxImageDimensions(1, 1), control.dimensions)
        for (type in listOf("ABCD", "abCD")) {
            val source = F.pngChunks("IHDR" to F.header(), type to byteArrayOf(0),
                "IDAT" to F.zlib(F.scanlines(1, 1, 3)), "IEND" to byteArrayOf())
            var offset = 8
            val types = mutableListOf<String>()
            while (offset < source.size) {
                val length = java.nio.ByteBuffer.wrap(source, offset, 4).int
                assertTrue(length >= 0 && offset + 12 + length <= source.size)
                types += String(source, offset + 4, 4, Charsets.US_ASCII)
                val crc = java.util.zip.CRC32().apply { update(source, offset + 4, length + 4) }.value
                val stored = java.nio.ByteBuffer.wrap(source, offset + 8 + length, 4).int.toLong() and 0xffffffffL
                assertEquals(crc, stored)
                if (types.last() == type) assertEquals(1, length)
                offset += length + 12
            }
            assertEquals(source.size, offset)
            assertEquals(listOf("IHDR", type, "IDAT", "IEND"), types)
            assertEquals(control.dimensions, review(source).dimensions)
        }
    }

    @Test fun invalidPngHeadersNeverMintDimensionCandidates() {
        val payload = F.zlib(F.scanlines(1, 1, 3))
        for ((offset, value) in listOf(8 to 4, 9 to 1, 10 to 1, 11 to 1, 12 to 2)) {
            val header = F.header().apply { this[offset] = value.toByte() }
            assertNull(review(encoded(payload, header)).dimensions)
        }
        for (header in listOf(F.header(width = 0), F.header(height = 0), F.header(width = -1))) {
            assertNull(review(encoded(payload, header)).dimensions)
        }
        for (source in listOf(F.png().apply { this[0] = 0 }, F.png().copyOf(32),
            F.png().apply { this[11] = 12 }, F.png().apply { this[11] = 14 },
            F.pngChunks("JHDR" to F.header(), "IDAT" to payload, "IEND" to byteArrayOf()),
            F.png().apply { this[29] = (this[29].toInt() xor 1).toByte() })) {
            assertNull(review(source).dimensions)
        }
    }

    @Test fun malformedChunkOrderingAndEndMarkersRemainReviewable() {
        val h = "IHDR" to F.header()
        val d = "IDAT" to F.zlib(F.scanlines(1, 1, 3))
        val e = "IEND" to byteArrayOf()
        val wrong = listOf(listOf(d, h, e), listOf(h, h, d, e), listOf(h, e, d),
            listOf(h, d, e, e), listOf(h, d), listOf(h, e),
            listOf(h, d, "tEXt" to byteArrayOf(), d, e), listOf(h, d, "IEND" to byteArrayOf(0)))
        for (chunks in wrong) review(F.pngChunks(*chunks.toTypedArray()))
        review(F.png() + byteArrayOf(0))
        review(F.signature + F.integer(4294967295L) + "IHDR".toByteArray())
        review(F.signature + F.chunk("IHDR", F.header()) + F.integer(4294967295L) + "IDAT".toByteArray())
        for (length in listOf(1000L, 2147483647L)) {
            // A complete 12-byte framing reaches the length guard before CRC access.
            review(F.signature + F.chunk("IHDR", F.header()) + F.integer(length) + "IDAT".toByteArray() + ByteArray(4))
        }
    }

    @Test fun eachChunkCrcMustMatchEvenWhenPayloadIsOtherwiseComplete() {
        val original = F.png()
        val idatEnd = original.size - 12
        for (offset in listOf(29, idatEnd - 1, original.lastIndex)) {
            review(original.copyOf().apply { this[offset] = (this[offset].toInt() xor 1).toByte() })
        }
    }

    @Test fun truncationCannotBeHiddenByRepairedChunkCrcOrAnAppendedIend() {
        val payload = F.zlib(F.scanlines(24, 24, 4))
        for (length in listOf(0, 1, 2, payload.size / 2, payload.size - 4, payload.size - 1)) {
            val damaged = encoded(payload.copyOf(length), F.header(24, 24, 6))
            review(F.throughReader(damaged))
        }
        val complete = F.png()
        review(F.throughReader(complete.copyOf(complete.size - 15) + F.chunk("IEND", byteArrayOf())))
    }

    @Test fun validPngCrcDoesNotAttestDeflateOrAdlerIntegrity() {
        val raw = F.scanlines(1, 1, 3)
        val stored = F.storedZlib(raw, 0)
        for (offset in listOf(0, 1, 2, 5, stored.lastIndex)) {
            review(encoded(stored.copyOf().apply { this[offset] = (this[offset].toInt() xor 255).toByte() }))
        }
        review(encoded(F.zlib(raw, "original dictionary".toByteArray())))
    }

    @Test fun exactlyOneZlibStreamMustConsumeAllIdatBytes() {
        val payload = F.zlib(F.scanlines(1, 1, 3))
        review(encoded(payload + byteArrayOf(0)))
        review(encoded(payload + payload))
        review(encoded(byteArrayOf(0x78, 0x01)))
    }

    @Test fun inflatedOutputMustHaveExactlyEveryExpectedScanlineAndValidFinalFilter() {
        val raw = F.scanlines(24, 24, 4)
        for (bytes in listOf(raw.copyOf(raw.size - 1), raw + byteArrayOf(0), ByteArray(100000))) {
            review(encoded(F.zlib(bytes), F.header(24, 24, 6)))
        }
        for (row in listOf(0, 23)) {
            val invalid = raw.copyOf().apply { this[row * 97] = 5 }
            review(encoded(F.zlib(invalid), F.header(24, 24, 6)))
        }
    }

    @Test fun chunkCountLimitIncludesIhdrAndIendAndAllowsEmptyIdat() {
        val payload = F.zlib(F.scanlines(1, 1, 3))
        for (count in listOf(64, 65)) {
            val parts = listOf("IHDR" to F.header()) +
                List(count - 2) { "IDAT" to if (it == 0) payload else byteArrayOf() } +
                listOf("IEND" to byteArrayOf())
            val source = F.pngChunks(*parts.toTypedArray())
            if (count == 64) validatedCandidate(source) else review(source)
        }
    }

    @Test fun encodedByteLimitUsesCompleteValidDeflateBlocksAtLimitAndLimitPlusOne() {
        for (size in listOf(65536, 65537)) {
            val height = if (size == 65536) 2 else 1
            val raw = F.scanlines(1, height, 3)
            // PNG framing 57, zlib header/trailer 6, each stored block 5 bytes.
            val blocks = (size - 63 - raw.size) / 5
            val source = encoded(F.storedZlib(raw, blocks - 1), F.header(1, height))
            assertEquals(size, source.size)
            val independentlyDecoded = javax.imageio.ImageIO.read(source.inputStream())
            assertNotNull(independentlyDecoded, "Both budget-edge fixtures must be valid PNGs")
            assertEquals(height, independentlyDecoded.height)
            if (size == 65536) validatedCandidate(source) else review(source)
        }
    }

    @Test fun provenPixelLimitRejectsBeforeQualificationCapsWithoutLeakingInput() {
        val boundary = F.signature + F.chunk("IHDR", F.header(8000, 5000))
        assertEquals(DocxImageDimensions(8000, 5000), review(boundary).dimensions)
        for (source in listOf(F.signature + F.chunk("IHDR", F.header(8001, 5000)),
            F.signature + F.chunk("IHDR", F.header(40000001, 1)) + ByteArray(65536))) {
            val error = assertFailsWith<DocxImagePixelLimitException> { qualifier.qualify(source) }
            assertEquals("DOCX_IMAGE_PIXELS", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun jpegPayloadDamageAndForgedEndMarkersAlwaysRemainReviewable() {
        val original = F.jpeg()
        val header = F.jpegHeader(original)
        for (source in listOf(header, original.copyOf(original.size - 2), original.copyOf(original.size / 2),
            header + byteArrayOf(255.toByte(), 217.toByte()),
            original.copyOf().apply { this[lastIndex - 3] = (this[lastIndex - 3].toInt() xor 127).toByte() })) {
            review(F.throughReader(source, "jpg"))
        }
    }

    @Test fun jpegHeaderCannotBorrowPngOffsetsAndACompletePngPayload() {
        val header = F.jpegFrame(1, 1).copyOf(33).apply { this[24] = 8; this[25] = 2 }
        val source = header + F.chunk("IDAT", F.zlib(F.scanlines(1, 1, 3))) + F.chunk("IEND", byteArrayOf())
        assertEquals(DocxImageDimensions(1, 1), review(F.throughReader(source, "jpg")).dimensions)
    }

    @Test fun jpegHeaderDimensionsRespectTheSameClosedPixelLimit() {
        val boundary = F.throughReader(F.jpegFrame(8000, 5000), "jpg")
        assertEquals(DocxImageDimensions(8000, 5000), review(boundary).dimensions)
        val above = F.throughReader(F.jpegFrame(8001, 5000), "jpg")
        val error = assertFailsWith<DocxImagePixelLimitException> { qualifier.qualify(above) }
        assertEquals("DOCX_IMAGE_PIXELS", error.message)
        assertNull(error.cause)
    }
}

/* Fresh R4: 35 assertion-killed mutations; control 22/22.
 * Qualifier SHA256 c7094ebd340c829a0a6d675c8a02b96c0d27740f047ad342854603f20bad3a83.
 * Byte-cap test physically removed with its guard: full core survived; unique test restored.
 * Raw XML and hashes: .review/r4-final/ and .review/image-full-core-pruning/.
 */
