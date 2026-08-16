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

        // 官方表 KITCHEN/DINING 的 Refrigerator、LAUNDRY 的 Washing machine 是"检查该电器本身
        // 状况/运行"，不是"检查摆放它的空间/接口"——stableId 存在且落对房间不够，文案若被
        // 悄悄换成只查空间/接口，等于漏检了供应电器的实际状况，membership+room 两道断言都测不出。
        assertTrue(byId.getValue("KIT-FRIDGE-01").textEn.contains("condition"), "KIT-FRIDGE-01 must check the refrigerator's own condition, not just its space/connection")
        assertTrue(byId.getValue("LDY-WASHER-01").textEn.contains("condition"), "LDY-WASHER-01 must check the washing machine's own condition, not just its space/connection")

        // 7 点烟雾报警器声明：内容对齐 Residential Tenancies (Smoke Alarms and Insulation) Regulations
        // 2016 规定的 7 项事实要求（卧室 3 米范围 / 每层至少一个 / 房车-独立睡屋 / 制造商到期日 /
        // 2016-07-01 起 8 年电池或硬连线 / 按说明安装 / 租期开始时确认正常），但**文案系本卡独立撰写**，
        // 不逐字复制 tenancy.govt.nz 官方表 MB_TEN0004 的措辞——该站版权声明明示「商业性复用需书面授权」
        // （https://www.tenancy.govt.nz/copyright，R3 round 3 拦住的许可风险）；受保护的是官方选择的具体
        // 表达，不是法规本身的事实要求，故换一套独立措辞完整保留全部事实点即规避复制风险。
        // 断言**一对一 stableId → 本卡撰写文案**的精确文案，不是只判存在——只判「这些 id 存在」不够：
        // 文案被替换成别的（哪怕格式相似）不会被单纯的 membership 断言发现。
        val expectedSmokeText = mapOf(
            "GEN-SMOKE-BEDROOM-01" to "Working smoke alarm present in every bedroom or other sleeping space, or within 3m of its door",
            "GEN-SMOKE-STOREY-01" to "Working smoke alarm present on every storey / level of the property, including levels with no bedrooms",
            "GEN-SMOKE-CARAVAN-01" to "Any on-site caravan, sleep-out or similar structure has its own working smoke alarm",
            "GEN-SMOKE-EXPIRY-01" to "No smoke alarm has exceeded its manufacturer expiry / replacement date",
            "GEN-SMOKE-BATTERY-01" to "Smoke alarms installed since 1 July 2016 are long-life photoelectric (minimum 8-year battery life) or hardwired, meeting the current regulatory product standard",
            "GEN-SMOKE-INSTALL-01" to "Smoke alarms installed by the landlord / agent according to manufacturer instructions",
            "GEN-SMOKE-WORKING-01" to "All smoke alarms confirmed working, including battery condition, at tenancy start",
        )
        val actualSmokeIds = items.filter { it.room == "GENERAL" && it.stableId.startsWith("GEN-SMOKE-") }
            .map { it.stableId }.toSet()
        assertEquals(expectedSmokeText.keys, actualSmokeIds, "the smoke-alarm declaration group must be exactly these 7 points, no more, no fewer")
        for ((id, expectedText) in expectedSmokeText) {
            assertEquals(expectedText, byId.getValue(id).textEn, "$id textEn drifted from its authored content")
        }

        // Healthy Homes 日常复核点：与官方表天然重合的四项（地板下/天花绝缘、厨房与浴室抽风、防潮布），
        // 文案须点名 Healthy Homes 以便未来 T6-HHC 按同 stableId 承接。
        for (id in listOf("GEN-INSUL-01", "GEN-MOIST-01", "KIT-VENT-01", "BTH-VENT-01")) {
            assertTrue(byId.getValue(id).textEn.contains("Healthy Homes"), "$id should read as a Healthy Homes checkpoint")
        }
    }
}
