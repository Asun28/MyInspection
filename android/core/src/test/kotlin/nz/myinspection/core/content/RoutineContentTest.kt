package nz.myinspection.core.content

import nz.myinspection.core.template.LoadedTemplate
import nz.myinspection.core.template.TemplateLoader
import nz.myinspection.core.template.TemplateValidationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * `data/templates/routine-v1.json` 内容完备性测试（T2-ROUTINE-CONTENT DoD）。
 *
 * 语法/域校验（未知字段、评级域、拍照规则域、必填非空、stableId 唯一）已由
 * [TemplateLoader.validate]（T1-TEMPLATE-ENGINE）逐条覆盖——本类**不重复**那些用例，只测
 * 内容卡自己的契约：项数区间、每房间恰一张 ROOM_PANORAMA、按调研清单的房间/条目覆盖。
 * 引擎抛出的校验错误一律在 [loadRoutine] 里转成可读的 `fail`，帮内容作者一次看全问题。
 *
 * 文件由 `android/core/build.gradle.kts` 的 test resources srcDir 注册（`data/templates/`），
 * 走 classpath 读取，不随 Gradle 工作目录漂移。
 */
class RoutineContentTest {

    private fun loadRoutine(): LoadedTemplate {
        val stream = javaClass.getResourceAsStream("/routine-v1.json")
            ?: fail("data/templates/routine-v1.json not found on test classpath (test resources srcDir wiring broken?)")
        return try {
            TemplateLoader.load(stream)
        } catch (e: TemplateValidationException) {
            fail("routine-v1.json failed engine validation:\n${e.errors.joinToString("\n")}")
        }
    }

    @Test
    fun `routine-v1 json passes full engine validation`() {
        // loadRoutine() 本身即断言：validate() 非空清单会在这里 fail，带着全部错误文案。
        loadRoutine()
    }

    @Test
    fun `item count is between 80 and 120`() {
        val count = loadRoutine().template.items.size
        assertTrue(count in 80..120, "item count is $count, expected 80..120")
    }

    @Test
    fun `every stable id is unique`() {
        val ids = loadRoutine().template.items.map { it.stableId }
        val duplicated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet(), duplicated, "duplicate stableId(s): $duplicated")
    }

    @Test
    fun `every item has non-blank English and Chinese text`() {
        val blanks = loadRoutine().template.items.filter { it.textEn.isBlank() || it.textZh.isBlank() }.map { it.stableId }
        assertTrue(blanks.isEmpty(), "items missing bilingual text: $blanks")
    }

    @Test
    fun `every room in the researched skeleton has exactly one ROOM_PANORAMA item`() {
        val expectedRooms = setOf("LOUNGE", "KITCHEN-DINING", "BATHROOM", "LAUNDRY", "BEDROOM", "GENERAL", "EXTERIOR")
        val items = loadRoutine().template.items
        val actualRooms = items.map { it.room }.toSet()
        assertEquals(expectedRooms, actualRooms, "room set drifted from the researched NZ official-form skeleton")

        for (room in expectedRooms) {
            val panoramaCount = items.count { it.room == room && it.photoRule == "ROOM_PANORAMA" }
            assertEquals(1, panoramaCount, "$room should have exactly one ROOM_PANORAMA item, found $panoramaCount")
        }
    }

    @Test
    fun `coverage matches the researched NZ official form skeleton, no gaps`() {
        val items = loadRoutine().template.items
        val byId = items.associateBy { it.stableId }

        // 每个必需 stableId 不仅要存在，还必须落在指定房间——只判 membership 的话，一条项目被
        // 错挪进另一个房间（如 KIT-SINK-01 挪去 BATHROOM）不会被发现，id 仍在整份 ids 集合里。
        val requiredIdToRoom = buildMap {
            for ((prefix, room) in listOf(
                "LNG" to "LOUNGE", "KIT" to "KITCHEN-DINING", "BTH" to "BATHROOM",
                "LDY" to "LAUNDRY", "BED" to "BEDROOM",
            )) {
                // 官方表 Wall-Doors / Lights-Power points / Floors-Coverings / Windows / Blinds-Curtains
                // 五组，本卡按各组内命名的两个子部件分开落项——见 stableId 命名。
                for (obj in listOf("WALL", "DOOR", "LIGHT", "POWER", "FLOOR", "COVER", "WIN", "BLIND")) {
                    put("$prefix-$obj-01", room)
                }
            }
            put("LNG-HEATER-01", "LOUNGE")
            for (obj in listOf("CUPBD", "SINK", "OVEN", "FRIDGE", "VENT")) put("KIT-$obj-01", "KITCHEN-DINING")
            for (obj in listOf("MIRROR", "BATH", "SHOWER", "BASIN", "TOILET", "VENT")) put("BTH-$obj-01", "BATHROOM")
            for (obj in listOf("WASHER", "TUB")) put("LDY-$obj-01", "LAUNDRY")
            // GENERAL：官方表 8 项 + 水表读数
            for (obj in listOf("BIN", "LOCK", "GARAGE", "GROUNDS", "KEYS", "INSUL", "GUTTER", "MOIST", "METER")) {
                put("GEN-$obj-01", "GENERAL")
            }
            // Exterior 围护细分（调研补充，非官方表原生条目）
            for (obj in listOf("CLAD", "ROOF", "FOUND", "FENCE", "PATH", "SEAL")) put("EXT-$obj-01", "EXTERIOR")
        }
        for ((id, expectedRoom) in requiredIdToRoom) {
            val item = byId[id] ?: fail("missing required item $id (expected room $expectedRoom)")
            assertEquals(expectedRoom, item.room, "$id should belong to room $expectedRoom, found ${item.room}")
        }

        // 7 点烟雾报警器声明（官方表照抄）：从模板**实际派生**出的 GEN-SMOKE-* 条目集合须恰好等于
        // 这 7 点，不多不少——只断言"这些 id 存在"不够，多余/被替换的声明点不会被上面按 id 点名的
        // 断言发现（它们只查"要求的 id 是否在场"，反向的"在场的 id 是否都是要求的"漏判）。
        val actualSmokeIds = items.filter { it.room == "GENERAL" && it.stableId.startsWith("GEN-SMOKE-") }
            .map { it.stableId }.toSet()
        val expectedSmokeIds = setOf(
            "GEN-SMOKE-POS-01", "GEN-SMOKE-TYPE-01", "GEN-SMOKE-POWER-01", "GEN-SMOKE-TEST-01",
            "GEN-SMOKE-EXPIRY-01", "GEN-SMOKE-OBSTRUCT-01", "GEN-SMOKE-COUNT-01",
        )
        assertEquals(expectedSmokeIds, actualSmokeIds, "official form's smoke-alarm declaration must be exactly these 7 points, no more, no fewer")

        // Healthy Homes 日常复核点：与官方表天然重合的四项（地板下/天花绝缘、厨房与浴室抽风、防潮布），
        // 文案须点名 Healthy Homes 以便未来 T6-HHC 按同 stableId 承接。
        for (id in listOf("GEN-INSUL-01", "GEN-MOIST-01", "KIT-VENT-01", "BTH-VENT-01")) {
            assertTrue(byId.getValue(id).textEn.contains("Healthy Homes"), "$id should read as a Healthy Homes checkpoint")
        }
    }
}
