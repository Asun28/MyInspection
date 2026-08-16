package nz.myinspection.core.capture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 纯函数测试（无 DB）：把 [RoomSnapshot] 手搭起来，直接钉住 [computeRoomProgress] /
 * [computeMissingPhotos] / [computeMissingNotes] / [AdverseStatuses] 的逻辑边界。
 * DB 装配/守卫那侧另有 InspectionRepositoryTest。
 */
class CompletenessTest {

    private fun itemDef(stableId: String, photoRule: String? = null) =
        ItemDef(stableId = stableId, photoRule = photoRule, allowedStatuses = CaptureTestFixtures.RENTAL_STATUSES)

    private fun room(
        roomInstanceId: String = "room-1",
        roomKey: String = "KITCHEN",
        items: List<ItemDef>,
        recorded: Map<String, RecordedItem> = emptyMap(),
        roomPhotoCount: Int = 0,
        itemPhotoCounts: Map<String, Int> = emptyMap(),
    ) = RoomSnapshot(
        roomInstanceId = roomInstanceId, roomKey = roomKey, displayLabel = roomKey,
        items = items, recordedItems = recorded, roomPhotoCount = roomPhotoCount, itemPhotoCounts = itemPhotoCounts,
    )

    // ---- AdverseStatuses ----

    @Test
    fun `rental types share the same adverse predicate`() {
        for (type in listOf("ROUTINE", "INGOING", "EXIT")) {
            assertTrue(AdverseStatuses.isAdverse(type, "FAIR"), "$type/FAIR should be adverse")
            assertTrue(AdverseStatuses.isAdverse(type, "POOR"), "$type/POOR should be adverse")
            assertFalse(AdverseStatuses.isAdverse(type, "GOOD"), "$type/GOOD should not be adverse")
            assertFalse(AdverseStatuses.isAdverse(type, "NOT_APPLICABLE"), "$type/NOT_APPLICABLE should not be adverse")
        }
    }

    @Test
    fun `annual has its own adverse predicate excluding NO_ISSUE and NOT_APPLICABLE`() {
        assertTrue(AdverseStatuses.isAdverse("ANNUAL", "MONITOR"))
        assertTrue(AdverseStatuses.isAdverse("ANNUAL", "MAINTENANCE_ITEM"))
        assertTrue(AdverseStatuses.isAdverse("ANNUAL", "SIGNIFICANT_DEFECT"))
        assertFalse(AdverseStatuses.isAdverse("ANNUAL", "NO_ISSUE"))
        assertFalse(AdverseStatuses.isAdverse("ANNUAL", "NOT_APPLICABLE"))
    }

    @Test
    fun `an unknown type is never adverse`() {
        assertFalse(AdverseStatuses.isAdverse("BOGUS", "FAIR"))
        assertFalse(AdverseStatuses.isAdverse("BOGUS", "MONITOR"))
    }

    // ---- computeRoomProgress ----

    @Test
    fun `a room with no panorama requirement is photo-satisfied without any room photo`() {
        val r = room(items = listOf(itemDef("BED-WALL-01")), recorded = mapOf("BED-WALL-01" to RecordedItem("GOOD", null)))
        val progress = computeRoomProgress("ROUTINE", r)
        assertEquals(1, progress.totalItems)
        assertEquals(1, progress.completedItems)
        assertEquals(false, progress.requiresRoomPanorama)
        assertTrue(progress.hasRoomPanorama)
        assertTrue(progress.isComplete)
    }

    @Test
    fun `a room requiring a panorama is incomplete with zero room photos`() {
        val r = room(
            items = listOf(itemDef("KIT-ROOM-01", photoRule = "ROOM_PANORAMA")),
            recorded = mapOf("KIT-ROOM-01" to RecordedItem("GOOD", null)),
            roomPhotoCount = 0,
        )
        val progress = computeRoomProgress("ROUTINE", r)
        assertTrue(progress.requiresRoomPanorama)
        assertEquals(false, progress.hasRoomPanorama)
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun `one room photo satisfies the panorama requirement`() {
        val r = room(
            items = listOf(itemDef("KIT-ROOM-01", photoRule = "ROOM_PANORAMA")),
            recorded = mapOf("KIT-ROOM-01" to RecordedItem("GOOD", null)),
            roomPhotoCount = 1,
        )
        assertTrue(computeRoomProgress("ROUTINE", r).hasRoomPanorama)
    }

    @Test
    fun `completed items only counts stable ids that have a recorded status`() {
        val r = room(
            items = listOf(itemDef("A"), itemDef("B"), itemDef("C")),
            recorded = mapOf("A" to RecordedItem("GOOD", null)),
        )
        val progress = computeRoomProgress("ROUTINE", r)
        assertEquals(3, progress.totalItems)
        assertEquals(1, progress.completedItems)
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun `an adverse-status item without a note does not count as completed`() {
        // 卡片正文「status 为不利发现时 note 非空才算该项完成」——这条规则必须同时体现在房间/整体进度上，
        // 不能只体现在 computeMissingNotes 那一份独立清单里（否则两处对"完成"的判断会互相矛盾）。
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("FAIR", null)))
        val progress = computeRoomProgress("ROUTINE", r)
        assertEquals(0, progress.completedItems)
        assertEquals(false, progress.isComplete)
    }

    @Test
    fun `an adverse-status item with a note counts as completed`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("FAIR", "chip")))
        assertEquals(1, computeRoomProgress("ROUTINE", r).completedItems)
    }

    @Test
    fun `a non-adverse status counts as completed without any note`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("GOOD", null)))
        assertEquals(1, computeRoomProgress("ROUTINE", r).completedItems)
    }

    @Test
    fun `the note-required rule uses the annual adverse set for annual inspections`() {
        val r = room(items = listOf(itemDef("ROOF-01")), recorded = mapOf("ROOF-01" to RecordedItem("MONITOR", null)))
        assertEquals(0, computeRoomProgress("ANNUAL", r).completedItems, "MONITOR is annual-adverse and needs a note")

        val withNote = room(items = listOf(itemDef("ROOF-01")), recorded = mapOf("ROOF-01" to RecordedItem("MONITOR", "watch")))
        assertEquals(1, computeRoomProgress("ANNUAL", withNote).completedItems)

        val nonAdverse = room(items = listOf(itemDef("ROOF-01")), recorded = mapOf("ROOF-01" to RecordedItem("NO_ISSUE", null)))
        assertEquals(1, computeRoomProgress("ANNUAL", nonAdverse).completedItems, "NO_ISSUE is not annual-adverse")
    }

    // ---- computeMissingPhotos ----

    @Test
    fun `missing photos flags a room lacking its required panorama`() {
        val r = room(items = listOf(itemDef("KIT-ROOM-01", photoRule = "ROOM_PANORAMA")), roomPhotoCount = 0)
        val result = computeMissingPhotos("ROUTINE", listOf(r))
        assertEquals(listOf(MissingRoomPhoto("room-1", "KITCHEN")), result.missingRoomPanoramas)
    }

    @Test
    fun `missing photos does not flag a room with no panorama requirement`() {
        val r = room(items = listOf(itemDef("BED-WALL-01")), roomPhotoCount = 0)
        assertTrue(computeMissingPhotos("ROUTINE", listOf(r)).missingRoomPanoramas.isEmpty())
    }

    @Test
    fun `missing photos flags an adverse-only item at an adverse status without a photo`() {
        val r = room(
            items = listOf(itemDef("KIT-BENCH-01", photoRule = "ADVERSE_ONLY")),
            recorded = mapOf("KIT-BENCH-01" to RecordedItem("FAIR", "cracked tile")),
        )
        val result = computeMissingPhotos("ROUTINE", listOf(r))
        assertEquals(listOf(MissingItemPhoto("room-1", "KIT-BENCH-01")), result.missingItemPhotos)
    }

    @Test
    fun `missing photos does not flag an adverse-only item at a non-adverse status`() {
        val r = room(
            items = listOf(itemDef("KIT-BENCH-01", photoRule = "ADVERSE_ONLY")),
            recorded = mapOf("KIT-BENCH-01" to RecordedItem("GOOD", null)),
        )
        assertTrue(computeMissingPhotos("ROUTINE", listOf(r)).missingItemPhotos.isEmpty())
    }

    @Test
    fun `missing photos does not flag NOT_APPLICABLE even for an adverse-only item`() {
        val r = room(
            items = listOf(itemDef("KIT-BENCH-01", photoRule = "ADVERSE_ONLY")),
            recorded = mapOf("KIT-BENCH-01" to RecordedItem("NOT_APPLICABLE", null)),
        )
        assertTrue(computeMissingPhotos("ROUTINE", listOf(r)).missingItemPhotos.isEmpty())
    }

    @Test
    fun `an existing item photo clears the adverse-only requirement`() {
        val r = room(
            items = listOf(itemDef("KIT-BENCH-01", photoRule = "ADVERSE_ONLY")),
            recorded = mapOf("KIT-BENCH-01" to RecordedItem("POOR", "note")),
            itemPhotoCounts = mapOf("KIT-BENCH-01" to 1),
        )
        assertTrue(computeMissingPhotos("ROUTINE", listOf(r)).missingItemPhotos.isEmpty())
    }

    @Test
    fun `missing photos ignores items that have not been visited yet`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01", photoRule = "ADVERSE_ONLY")), recorded = emptyMap())
        assertTrue(computeMissingPhotos("ROUTINE", listOf(r)).missingItemPhotos.isEmpty())
    }

    @Test
    fun `annual adverse-only items use the annual adverse set`() {
        val r = room(
            items = listOf(itemDef("ROOF-01", photoRule = "ADVERSE_ONLY")),
            recorded = mapOf("ROOF-01" to RecordedItem("MONITOR", "watch this")),
        )
        assertEquals(listOf(MissingItemPhoto("room-1", "ROOF-01")), computeMissingPhotos("ANNUAL", listOf(r)).missingItemPhotos)
    }

    // ---- computeMissingNotes ----

    @Test
    fun `missing notes flags an adverse status with a blank note`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("POOR", "  ")))
        assertEquals(listOf(MissingNote("room-1", "KIT-BENCH-01")), computeMissingNotes("ROUTINE", listOf(r)))
    }

    @Test
    fun `missing notes flags an adverse status with a null note`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("FAIR", null)))
        assertEquals(listOf(MissingNote("room-1", "KIT-BENCH-01")), computeMissingNotes("ROUTINE", listOf(r)))
    }

    @Test
    fun `a non-blank note clears the adverse-status requirement`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("POOR", "cracked")))
        assertTrue(computeMissingNotes("ROUTINE", listOf(r)).isEmpty())
    }

    @Test
    fun `missing notes does not require a note for a non-adverse status`() {
        val r = room(items = listOf(itemDef("KIT-BENCH-01")), recorded = mapOf("KIT-BENCH-01" to RecordedItem("GOOD", null)))
        assertTrue(computeMissingNotes("ROUTINE", listOf(r)).isEmpty())
    }

    @Test
    fun `missing notes applies regardless of photo rule`() {
        // photoRule=null（无拍照要求）但状态不利，仍要备注——两条规则各自独立生效。
        val r = room(items = listOf(itemDef("BED-WALL-01", photoRule = null)), recorded = mapOf("BED-WALL-01" to RecordedItem("FAIR", null)))
        assertEquals(listOf(MissingNote("room-1", "BED-WALL-01")), computeMissingNotes("ROUTINE", listOf(r)))
    }
}
