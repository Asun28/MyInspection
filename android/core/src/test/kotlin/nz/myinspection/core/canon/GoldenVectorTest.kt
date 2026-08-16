package nz.myinspection.core.canon

import nz.myinspection.core.model.AudioSnapshot
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.InspectionSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.model.PropertySnapshot
import nz.myinspection.core.model.SupplementSnapshot
import nz.myinspection.core.model.TemplateSnapshot
import nz.myinspection.core.model.TenancySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 黄金向量：固定输入 -> 固定 canonical 串 -> 固定 SHA-256。期望值不是由被测实现算出——由独立实现
 * （Python json.dumps sort_keys + NFC 归一 + hashlib sha256）预先计算后写死于此，交叉复核可用任意
 * 第三方实现复算。任何一侧改动（投影键名/排序/转义/NFC/哈希输入）都会当场变红。
 * data_hash 进 PDF 页脚自证报告未被事后修改（CLAUDE.md 关键不变量）——这组向量就是那份自证的地基。
 */
class GoldenVectorTest {

    // ---- 向量 1：满配快照（中文备注、双照片、音频、tenancy end=null） ----

    private fun vector1() = InspectionSnapshot(
        id = "insp-0001",
        type = "ROUTINE",
        tenancyId = "ten-0001",
        scheduledAt = 1_755_302_400_000L,
        finalizedAt = 1_755_309_600_000L,
        previousInspectionId = "insp-0000",
        baselineInspectionId = "insp-base",
        property = PropertySnapshot(id = "prop-0001", address = "12 Aroha Ave, Auckland", kind = "RENTAL", isBoardingHouse = false),
        tenancy = TenancySnapshot(id = "ten-0001", startMs = 1_704_067_200_000L, endMs = null),
        template = TemplateSnapshot(id = "tpl-routine-v3", type = "ROUTINE", version = 3, contentHash = "template-hash-1"),
        items = listOf(
            InspectionItemSnapshot(stableId = "kitchen.wall.paint", status = "GOOD", note = null, wearOrDamage = null),
            InspectionItemSnapshot(stableId = "lounge.carpet", status = "POOR", note = "墙面有刮痕，需重新粉刷", wearOrDamage = "DAMAGE"),
        ),
        photos = listOf(
            PhotoSnapshot(contentHash = "ph-hash-1", source = "camera", exifTimeMs = 1_755_303_000_000L, isRoomLevel = false),
            PhotoSnapshot(contentHash = "ph-hash-2", source = "imported", exifTimeMs = null, isRoomLevel = true),
        ),
        audios = listOf(AudioSnapshot(contentHash = "au-hash-1")),
    )

    private val v1Canonical = "{\"audios\":[{\"content_hash\":\"au-hash-1\"}],\"baseline_inspection_id\":\"insp-base\",\"finalized_at\":1755309600000,\"id\":\"insp-0001\",\"items\":[{\"note\":null,\"stable_id\":\"kitchen.wall.paint\",\"status\":\"GOOD\",\"wear_or_damage\":null},{\"note\":\"墙面有刮痕，需重新粉刷\",\"stable_id\":\"lounge.carpet\",\"status\":\"POOR\",\"wear_or_damage\":\"DAMAGE\"}],\"photos\":[{\"content_hash\":\"ph-hash-1\",\"exif_time_ms\":1755303000000,\"is_room_level\":false,\"source\":\"camera\"},{\"content_hash\":\"ph-hash-2\",\"exif_time_ms\":null,\"is_room_level\":true,\"source\":\"imported\"}],\"previous_inspection_id\":\"insp-0000\",\"property\":{\"address\":\"12 Aroha Ave, Auckland\",\"id\":\"prop-0001\",\"is_boarding_house\":false,\"kind\":\"RENTAL\"},\"scheduled_at\":1755302400000,\"template\":{\"content_hash\":\"template-hash-1\",\"id\":\"tpl-routine-v3\",\"type\":\"ROUTINE\",\"version\":3},\"tenancy\":{\"end\":null,\"id\":\"ten-0001\",\"start\":1704067200000},\"tenancy_id\":\"ten-0001\",\"type\":\"ROUTINE\"}"

    private val v1Hash = "ea9cd02e76bf79ac320df5795e51433b3200eb28900ab8837479a0c15eaf452d"

    @Test
    fun `golden vector 1 full snapshot pins canonical string and hash`() {
        val canon = canonicalJson(vector1())
        assertEquals(v1Canonical, canon)
        assertEquals(v1Hash, sha256Hex(canon))
    }

    // ---- 向量 2：NFC 归一（组合字符 e-acute 两种编码产同一 canonical 串与哈希） ----

    private fun vector2(address: String, note: String) = InspectionSnapshot(
        id = "insp-0002",
        type = "EXIT",
        tenancyId = "ten-0002",
        scheduledAt = 1_760_000_000_000L,
        finalizedAt = null,
        previousInspectionId = null,
        baselineInspectionId = "insp-0001",
        property = PropertySnapshot(id = "prop-0001", address = address, kind = "RENTAL", isBoardingHouse = true),
        tenancy = TenancySnapshot(id = "ten-0002", startMs = 1_750_000_000_000L, endMs = 1_760_000_000_000L),
        template = TemplateSnapshot(id = "tpl-exit-v1", type = "EXIT", version = 1, contentHash = "template-hash-2"),
        items = listOf(InspectionItemSnapshot(stableId = "kitchen.bench", status = "GOOD", note = note, wearOrDamage = "WEAR")),
        photos = emptyList(),
        audios = emptyList(),
    )

    private val v2Canonical = "{\"audios\":[],\"baseline_inspection_id\":\"insp-0001\",\"finalized_at\":null,\"id\":\"insp-0002\",\"items\":[{\"note\":\"caf\u00e9\",\"stable_id\":\"kitchen.bench\",\"status\":\"GOOD\",\"wear_or_damage\":\"WEAR\"}],\"photos\":[],\"previous_inspection_id\":null,\"property\":{\"address\":\"7 Caf\u00e9 Lane\",\"id\":\"prop-0001\",\"is_boarding_house\":true,\"kind\":\"RENTAL\"},\"scheduled_at\":1760000000000,\"template\":{\"content_hash\":\"template-hash-2\",\"id\":\"tpl-exit-v1\",\"type\":\"EXIT\",\"version\":1},\"tenancy\":{\"end\":1760000000000,\"id\":\"ten-0002\",\"start\":1750000000000},\"tenancy_id\":\"ten-0002\",\"type\":\"EXIT\"}"

    private val v2Hash = "35a4e01779df5d7d1dbb426c937ce37f417eb7ed6c903466d0dae82ca14e0540"

    @Test
    fun `golden vector 2 composed and decomposed unicode produce one canonical string and one hash`() {
        val composed = vector2(address = "7 Caf\u00e9 Lane", note = "caf\u00e9")
        val decomposed = vector2(address = "7 Cafe\u0301 Lane", note = "cafe\u0301")
        assertEquals(v2Canonical, canonicalJson(composed))
        assertEquals(v2Canonical, canonicalJson(decomposed))
        assertEquals(v2Hash, sha256Hex(canonicalJson(decomposed)))
    }

    // ---- 向量 3：最小快照（全部可空字段为 null、全部列表为空）钉死 null 显式序列化策略 ----

    private fun vector3() = InspectionSnapshot(
        id = "insp-0003",
        type = "INGOING",
        tenancyId = null,
        scheduledAt = 1_730_000_000_000L,
        finalizedAt = null,
        previousInspectionId = null,
        baselineInspectionId = null,
        property = PropertySnapshot(id = "prop-0002", address = "3/45 Queen St", kind = "RENTAL", isBoardingHouse = false),
        tenancy = null,
        template = TemplateSnapshot(id = "tpl-ingoing-v2", type = "INGOING", version = 2, contentHash = "template-hash-3"),
        items = emptyList(),
        photos = emptyList(),
        audios = emptyList(),
    )

    private val v3Canonical = "{\"audios\":[],\"baseline_inspection_id\":null,\"finalized_at\":null,\"id\":\"insp-0003\",\"items\":[],\"photos\":[],\"previous_inspection_id\":null,\"property\":{\"address\":\"3/45 Queen St\",\"id\":\"prop-0002\",\"is_boarding_house\":false,\"kind\":\"RENTAL\"},\"scheduled_at\":1730000000000,\"template\":{\"content_hash\":\"template-hash-3\",\"id\":\"tpl-ingoing-v2\",\"type\":\"INGOING\",\"version\":2},\"tenancy\":null,\"tenancy_id\":null,\"type\":\"INGOING\"}"

    private val v3Hash = "cdd7680aa92482a8cea9c3c685a19e5bdb30e0b01e0dff7be4df9ed9304e15c8"

    @Test
    fun `golden vector 3 minimal snapshot pins explicit null serialization`() {
        val canon = canonicalJson(vector3())
        assertEquals(v3Canonical, canon)
        assertEquals(v3Hash, sha256Hex(canon))
    }

    // ---- 排除域：这些键永远不得出现在 canonical 投影里 ----

    @Test
    fun `excluded domain keys never appear in the canonical projection`() {
        // 投影若哪天从更宽的源取数并把排除域漏进 JSON，键名会当场现形。断言锚定到键形态（"key":）——
        // 值里的引号在 canonical 串中是转义形态（反斜杠+引号），伪装不出未转义的键形态；
        // 下面用「值里嵌入键文本」的变体证明该锚定无误报（L165 断言面=契约）。
        val canon = canonicalJson(vector1())
        val tricky = canonicalJson(
            vector1().copy(
                items = listOf(InspectionItemSnapshot(stableId = "s", status = "GOOD", note = "\"updated_at\": 1", wearOrDamage = null)),
            ),
        )
        for (key in listOf("updated_at", "deleted_at", "rel_path", "tenant_name", "contact", "remediation", "supplement")) {
            assertFalse(canon.contains("\"" + key + "\":"), "排除域键 " + key + " 不得进入哈希域（ADR-0003）")
            assertFalse(tricky.contains("\"" + key + "\":"), "值里嵌入的键文本不得被判成泄漏的键：" + key)
        }
    }

    @Test
    fun `snapshot types carry no excluded-domain field`() {
        // dod_assert「排除域字段变化不改哈希」的构造性保证：排除域字段在输入类型上根本不存在，
        // 故其变化不可能流入哈希。这里把该事实在 canon 自己的（合并后冻结的）测试里钉成机检——
        // 未来若有人往任何快照类型加回 relPath/updatedAt 之类字段，本测试当场红，冻结的向量随之复审。
        val excluded = setOf("updatedAt", "deletedAt", "relPath", "tenantName", "tenantContact", "contact")
        val types = listOf(
            InspectionSnapshot::class.java,
            PropertySnapshot::class.java,
            TenancySnapshot::class.java,
            TemplateSnapshot::class.java,
            InspectionItemSnapshot::class.java,
            PhotoSnapshot::class.java,
            AudioSnapshot::class.java,
            SupplementSnapshot::class.java,
        )
        for (type in types) {
            val leaked = type.declaredFields.map { it.name }.filter { it in excluded }
            assertTrue(leaked.isEmpty(), type.simpleName + " 携带排除域字段：" + leaked)
        }
    }

    @Test
    fun `supplement chain rejects a prev that is not 64 lowercase hex`() {
        // 空串/截断/大写 prev 在这里 fail-fast——否则链静默锚定在错误锚点，复验时才炸。
        val s = SupplementSnapshot(createdAt = 1L, text = "x")
        for (prev in listOf("", "abc", v1Hash.uppercase(), v1Hash + "00")) {
            assertFailsWith<IllegalArgumentException>("prev=" + prev) { supplementChainHash(prev, s) }
        }
    }

    // ---- Supplement 哈希链：chain(n) = SHA-256(canonical(supplement_n) + prev)，prev(1) = data_hash ----

    @Test
    fun `supplement chain golden vectors`() {
        val s1 = SupplementSnapshot(createdAt = 1_755_400_000_000L, text = "补充：租客已同意维修安排")
        val chain1 = supplementChainHash(v1Hash, s1)
        assertEquals("59e9fd2d95952aa43886c7d46694ad69208c759963213b9be77647eb50d6f169", chain1)
        val s2 = SupplementSnapshot(createdAt = 1_755_500_000_000L, text = "Follow-up: repair completed")
        assertEquals("15c44bd63ab3109e5ddf66e2f0fe3f1a2bf8e827073cb91c62dc5fa4cc718786", supplementChainHash(chain1, s2))
    }

    @Test
    fun `supplement text NFC normalizes before chaining`() {
        val composed = SupplementSnapshot(createdAt = 1L, text = "caf\u00e9")
        val decomposed = SupplementSnapshot(createdAt = 1L, text = "cafe\u0301")
        assertEquals(supplementChainHash(v1Hash, composed), supplementChainHash(v1Hash, decomposed))
    }

    // ---- sha256Hex 自身对 FIPS 180-4 公开向量 ----

    @Test
    fun `sha256Hex matches published FIPS vectors`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256Hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }
}
