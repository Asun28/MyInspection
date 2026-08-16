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
 * 语法/域校验（未知字段、评级域、拍照规则域、必填非空、stableId 唯一）由
 * [TemplateLoader.validate]（T1-TEMPLATE-ENGINE）逐条覆盖，经 [loadRoutine] 转成可读 `fail`——
 * 本类只加内容卡自己的契约，engine 已判定的一律不重复断言。加的契约：
 * - 模板身份（type/version 锁定）与 stableId 命名约定（房间-对象-两位序号）；
 * - 项数区间、房间清单与每房间恰一张 ROOM_PANORAMA；
 * - 按调研清单的房间/条目覆盖——骨架必需项的存在与房间归属、冰箱/洗衣机检查对象本身（而非
 *   其摆放空间）、7 点烟雾报警器声明、Healthy Homes 四项日常复核点。
 *
 * 这些覆盖断言一律用**逐字文案**（`assertEquals`），不用子串/关键词判定：内容作者随手改一个词、
 * 或把判断句换成中性标签时漏掉了原有事实，逐字断言会立刻报出旧文案与新文案的差异；子串判定
 * 容易被"关键词还在、意思已经变了"的改动骗过（如 `contains("condition")` 挡不住把检查对象从
 * "电器本身"悄悄换成"电器接口"，只要新文案里仍带 "condition" 一词）。
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
        // loadRoutine() 本身即断言：validate() 非空清单会在这里 fail，带着全部错误文案
        // （含 stableId 唯一性、双语非空——engine 已判定，本类不另开重复用例）。
        loadRoutine()
    }

    @Test
    fun `item count is between 80 and 120`() {
        val count = loadRoutine().template.items.size
        assertTrue(count in 80..120, "item count is $count, expected 80..120")
    }

    @Test
    fun `template identity is pinned to ROUTINE v1`() {
        // 类型/版本本身没有被任何内容断言覆盖：把 type 改成 INGOING/EXIT 或 version 改成 2，
        // 引擎校验与其余内容断言照样全绿——这两个字段只有这里在盯。
        val template = loadRoutine().template
        assertEquals("ROUTINE", template.type)
        assertEquals(1, template.version)
    }

    @Test
    fun `every stable id follows the room-object-two-digit-sequence convention`() {
        // 卡片命名约定：房间缩写-对象-两位序号，恰三段（两个连字符），不靠肉眼逐条数。
        val idPattern = Regex("^[A-Z]{2,4}-[A-Z0-9]+-\\d{2}$")
        val bad = loadRoutine().template.items.map { it.stableId }.filterNot { idPattern.matches(it) }
        assertTrue(bad.isEmpty(), "stableIds must follow ROOM-OBJECT-NN (exactly two hyphens): $bad")
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
    fun `every required stableId from the researched skeleton exists and belongs to its expected room`() {
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
            // GENERAL：官方表 8 项 + 水表读数（烟雾报警器 7 项另有专门测试覆盖 id/房间/文案三者）
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
    }

    @Test
    fun `refrigerator and washing machine items check the appliance's own condition`() {
        // 官方表 KITCHEN/DINING 的 Refrigerator、LAUNDRY 的 Washing machine 是"检查该电器本身
        // 状况/运行"，不是"检查摆放它的空间/接口"。逐字断言：子串判 contains("condition") 挡不住
        // 文案被换成"Refrigerator connection condition"这类关键词命中、检查对象却仍是空间/接口的改动。
        val byId = loadRoutine().template.items.associateBy { it.stableId }
        assertEquals("Refrigerator condition and operation", byId.getValue("KIT-FRIDGE-01").textEn)
        assertEquals("Washing machine condition and operation", byId.getValue("LDY-WASHER-01").textEn)
    }

    @Test
    fun `the smoke-alarm declaration group is exactly these 7 authored points, verbatim`() {
        val items = loadRoutine().template.items
        val byId = items.associateBy { it.stableId }

        // 7 点烟雾报警器声明：内容对齐 Residential Tenancies (Smoke Alarms and Insulation)
        // Regulations 2016 规定的 7 项事实要求（卧室/其他睡眠空间 3 米范围内 / 每层含无卧室楼层 /
        // 房车-独立睡屋 / 制造商到期日 / 2016-07-01 起长效光电或硬连线且达标 / 按说明安装 /
        // 租期开始时含电池确认正常）。文案系本卡独立撰写、不逐字复制 tenancy.govt.nz 官方表
        // MB_TEN0004 的措辞——该站版权声明明示商业性复用需书面授权，受保护的是官方选择的具体
        // 表达而非法规本身的事实要求，故换一套独立措辞完整保留全部事实点以规避复制风险。
        val expectedSmokeText = mapOf(
            "GEN-SMOKEBED-01" to "Smoke alarm coverage inside each bedroom or other sleeping space, or within 3m of its door",
            "GEN-SMOKELVL-01" to "Smoke alarm coverage on each storey / level of the property, including levels with no bedrooms",
            "GEN-SMOKECRV-01" to "Smoke alarm coverage in any on-site caravan, sleep-out or similar structure",
            "GEN-SMOKEXPY-01" to "Smoke alarm expiry or recommended replacement date (per manufacturer)",
            "GEN-SMOKEBAT-01" to "Smoke alarm type and battery life for alarms installed since 1 July 2016 (long-life photoelectric, minimum 8-year battery, or hardwired; meets the current regulatory product standard)",
            "GEN-SMOKEINS-01" to "Smoke alarm installation method (landlord / agent, per manufacturer instructions)",
            "GEN-SMOKEWRK-01" to "Smoke alarm operating condition, including battery, at tenancy start",
        )
        // 逐字文案断言：只判「这些 id 存在」不够——文案被替换成别的（哪怕格式相似）不会被单纯的
        // membership 断言发现；只判「含某关键词」也不够——事实被简化掉、关键词还在照样通过。
        val actualSmokeIds = items.filter { it.room == "GENERAL" && it.stableId.startsWith("GEN-SMOKE") }
            .map { it.stableId }.toSet()
        assertEquals(expectedSmokeText.keys, actualSmokeIds, "the smoke-alarm declaration group must be exactly these 7 points, no more, no fewer")
        for ((id, expectedText) in expectedSmokeText) {
            assertEquals(expectedText, byId.getValue(id).textEn, "$id textEn drifted from its authored content")
        }
    }

    @Test
    fun `Healthy Homes checkpoints describe their actual content, verbatim`() {
        // Healthy Homes 日常复核点：与官方表天然重合的四项（地板下/天花绝缘、防潮布、厨房与浴室
        // 抽风运行）。逐字断言：子串判 contains("operation") 挡不住文案被换成"Oven operation
        // (Healthy Homes)"这类关键词命中、检查主体却错误的改动；文案须点名 Healthy Homes 以便
        // 未来 T6-HHC 按同 stableId 承接。
        val byId = loadRoutine().template.items.associateBy { it.stableId }
        assertEquals("Ceiling & underfloor insulation condition (Healthy Homes)", byId.getValue("GEN-INSUL-01").textEn)
        assertEquals("Ground moisture barrier condition (Healthy Homes)", byId.getValue("GEN-MOIST-01").textEn)
        assertEquals("Ventilation / range hood extraction fan operation (Healthy Homes)", byId.getValue("KIT-VENT-01").textEn)
        assertEquals("Ventilation / extractor fan operation (Healthy Homes)", byId.getValue("BTH-VENT-01").textEn)
    }
}
