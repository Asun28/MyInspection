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
        val ids = items.map { it.stableId }.toSet()

        // 每房间重复条目组（官方表 Wall-Doors / Lights-Power points / Floors-Coverings / Windows /
        // Blinds-Curtains 五组，本卡按各组内命名的两个子部件分开落项——见 stableId 命名）。
        for (roomPrefix in listOf("LNG", "KIT", "BTH", "LDY", "BED")) {
            for (obj in listOf("WALL", "DOOR", "LIGHT", "POWER", "FLOOR", "COVER", "WIN", "BLIND")) {
                assertTrue("$roomPrefix-$obj-01" in ids, "missing repeated-group item $roomPrefix-$obj-01")
            }
        }

        // 房间专属条目
        assertTrue("LNG-HEATER-01" in ids, "missing lounge heater item")
        for (obj in listOf("CUPBD", "SINK", "OVEN", "FRIDGE", "VENT")) {
            assertTrue("KIT-$obj-01" in ids, "missing kitchen-specific item KIT-$obj-01")
        }
        for (obj in listOf("MIRROR", "BATH", "SHOWER", "BASIN", "TOILET", "VENT")) {
            assertTrue("BTH-$obj-01" in ids, "missing bathroom-specific item BTH-$obj-01")
        }
        for (obj in listOf("WASHER", "TUB")) {
            assertTrue("LDY-$obj-01" in ids, "missing laundry-specific item LDY-$obj-01")
        }

        // GENERAL：官方表 8 项 + 7 点烟雾报警器声明（照抄官方表）+ 水表读数
        for (obj in listOf("BIN", "LOCK", "GARAGE", "GROUNDS", "KEYS", "INSUL", "GUTTER", "MOIST", "METER")) {
            assertTrue("GEN-$obj-01" in ids, "missing general item GEN-$obj-01")
        }
        val smokeObjs = listOf("SMOKE-POS", "SMOKE-TYPE", "SMOKE-POWER", "SMOKE-TEST", "SMOKE-EXPIRY", "SMOKE-OBSTRUCT", "SMOKE-COUNT")
        for (obj in smokeObjs) {
            assertTrue("GEN-$obj-01" in ids, "missing smoke-alarm declaration item GEN-$obj-01")
        }
        assertEquals(7, smokeObjs.size, "official form's smoke-alarm declaration has exactly 7 points")

        // Exterior 围护细分（调研补充，非官方表原生条目）
        for (obj in listOf("CLAD", "ROOF", "FOUND", "FENCE", "PATH", "SEAL")) {
            assertTrue("EXT-$obj-01" in ids, "missing exterior item EXT-$obj-01")
        }

        // Healthy Homes 日常复核点：与官方表天然重合的三项（地板下/天花绝缘、抽风扇、防潮布），
        // 文案须点名 Healthy Homes 以便未来 T6-HHC 按同 stableId 承接（非本卡断言其存在，只断言可辨识）。
        val byId = items.associateBy { it.stableId }
        assertTrue(byId.getValue("GEN-INSUL-01").textEn.contains("Healthy Homes"), "GEN-INSUL-01 should read as a Healthy Homes checkpoint")
        assertTrue(byId.getValue("GEN-MOIST-01").textEn.isNotBlank(), "GEN-MOIST-01 (ground moisture barrier) missing")
        assertTrue(byId.getValue("KIT-VENT-01").textEn.contains("Healthy Homes"), "KIT-VENT-01 should read as a Healthy Homes checkpoint")
        assertTrue(byId.getValue("BTH-VENT-01").textEn.contains("Healthy Homes"), "BTH-VENT-01 should read as a Healthy Homes checkpoint")
    }
}
