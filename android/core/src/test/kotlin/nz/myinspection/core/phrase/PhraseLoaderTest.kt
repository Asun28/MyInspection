package nz.myinspection.core.phrase

import nz.myinspection.core.phrase.PhraseTestFixtures.library
import nz.myinspection.core.phrase.PhraseTestFixtures.phrase
import nz.myinspection.core.phrase.PhraseTestFixtures.smallLibrary
import kotlinx.serialization.SerializationException
import java.nio.charset.CharacterCodingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * 加载 + 校验的契约测试（同 `TemplateLoaderTest` 的理由）。
 *
 * 校验错误一律做**整串等值**断言，不用 `contains`：错误文案本身就是内容卡 DoD 的判据面——
 * 只断言"包含某关键词"的话，把域名写错、把两条错误合并成一条，测试都照样绿（L165）。
 */
class PhraseLoaderTest {

    private fun load(json: String): LoadedPhraseLibrary = PhraseLoader.load(json.byteInputStream())

    private fun errorsOf(json: String): List<String> =
        assertFailsWith<PhraseValidationException> { load(json) }.errors

    @Test
    fun `load maps every phrase field`() {
        val loaded = load(smallLibrary())

        assertEquals(1, loaded.library.version)
        assertEquals(
            listOf("Fair wear and tear", "No issues noted", "Stain observed on the surface"),
            loaded.library.phrases.map { it.en },
        )

        val first = loaded.library.phrases[0]
        assertEquals("正常损耗", first.zh)
        assertEquals("wear", first.category)
        assertEquals(0, first.sort)
        assertEquals(listOf("FAIR"), first.appliesToStatuses)
        assertEquals("FWT", first.shortcut)

        val second = loaded.library.phrases[1]
        assertNull(second.appliesToStatuses, "appliesToStatuses 可空：null = 不限评级")
        assertNull(second.shortcut, "shortcut 可空：null = 无快捷键")
    }

    @Test
    fun `malformed UTF-8 bytes are rejected rather than silently replaced`() {
        val json = smallLibrary()
        val corrupted = json.toByteArray()
        corrupted[corrupted.indexOfFirst { byte -> byte.toInt() and 0x80 != 0 }] = 0xFF.toByte()

        assertFailsWith<CharacterCodingException> { PhraseLoader.load(corrupted.inputStream()) }
    }

    @Test
    fun `an unknown field is rejected rather than silently ignored`() {
        // 大小写拼错的键（Zh）：若被忽略，zh 取空默认值，只会看到一句"zh is blank"，
        // 看不出真正病因是键名拼错。
        val typo = """{"en":"Fair wear and tear","Zh":"正常损耗","category":"wear","sort":0}"""

        assertFailsWith<SerializationException> { load(library(phrases = listOf(typo))) }
    }

    @Test
    fun `the loaded library's collections cannot be mutated through a cast`() {
        val loaded = load(smallLibrary())

        assertFailsWith<UnsupportedOperationException> { (loaded.library.phrases as MutableList).removeAt(0) }
        assertFailsWith<UnsupportedOperationException> {
            (loaded.library.phrases[0].appliesToStatuses as MutableList).clear()
        }
        assertFailsWith<UnsupportedOperationException> { (PhraseDomains.CATEGORIES as MutableSet).clear() }
    }

    @Test
    fun `a blank required field is rejected and the error names the phrase by position`() {
        assertEquals(listOf("phrase[0]: en is blank"), errorsOf(library(phrases = listOf(phrase(en = "")))))
        assertEquals(listOf("phrase[0]: zh is blank"), errorsOf(library(phrases = listOf(phrase(zh = "")))))
    }

    @Test
    fun `an unknown category is rejected and the error names the phrase and the value`() {
        assertEquals(
            listOf("phrase[0]: unknown category wearx"),
            errorsOf(library(phrases = listOf(phrase(category = "wearx")))),
        )
    }

    @Test
    fun `duplicate en text is rejected and the error names both phrases`() {
        val json = library(
            phrases = listOf(
                phrase(en = "Fair wear and tear", shortcut = "\"FWT\""),
                phrase(en = "Fair wear and tear", zh = "别的中文", category = "condition-general", sort = 1, shortcut = null),
            ),
        )

        assertEquals(listOf("phrase[1]: duplicate en text (same as phrase[0])"), errorsOf(json))
    }

    @Test
    fun `duplicate shortcut is rejected even when the wording differs`() {
        val json = library(
            phrases = listOf(
                phrase(en = "Fair wear and tear", shortcut = "\"FWT\""),
                phrase(en = "Minor wear consistent with age of property", sort = 1, shortcut = "\"FWT\""),
            ),
        )

        assertEquals(listOf("phrase[1]: duplicate shortcut FWT (same as phrase[0])"), errorsOf(json))
    }

    @Test
    fun `a blank shortcut string is rejected in favour of omitting the field`() {
        assertEquals(
            listOf("phrase[0]: shortcut is blank (omit the field instead of an empty string)"),
            errorsOf(library(phrases = listOf(phrase(shortcut = "\"\"")))),
        )
    }

    @Test
    fun `an empty appliesToStatuses list is rejected in favour of omitting the field`() {
        assertEquals(
            listOf("phrase[0]: appliesToStatuses is empty (omit the field to apply to every status)"),
            errorsOf(library(phrases = listOf(phrase(appliesToStatuses = "[]")))),
        )
    }

    @Test
    fun `a status outside the recognized rating domain is rejected and the error names it`() {
        assertEquals(
            listOf("phrase[0]: status EXCELLENT is not a recognized rating value"),
            errorsOf(library(phrases = listOf(phrase(appliesToStatuses = """["EXCELLENT"]""")))),
        )
    }

    @Test
    fun `appliesToStatuses accepts values from either the rental or the annual domain`() {
        val loaded = load(
            library(
                phrases = listOf(
                    phrase(appliesToStatuses = """["FAIR","MONITOR"]"""),
                ),
            ),
        )
        assertEquals(listOf("FAIR", "MONITOR"), loaded.library.phrases.single().appliesToStatuses)
    }

    @Test
    fun `library-level defects are rejected`() {
        assertEquals(listOf("phrase-library: version must be >= 1"), errorsOf(library(version = 0)))
        assertEquals(listOf("phrase-library: phrases is empty"), errorsOf(library(phrases = emptyList())))
    }

    @Test
    fun `every defect is reported in one pass, in array order`() {
        val json = library(
            phrases = listOf(
                phrase(en = "", shortcut = null, appliesToStatuses = null),
                phrase(category = "wearx", sort = 1, shortcut = null, appliesToStatuses = null),
            ),
        )

        assertEquals(
            listOf(
                "phrase[0]: en is blank",
                "phrase[1]: unknown category wearx",
            ),
            errorsOf(json),
        )
    }
}
