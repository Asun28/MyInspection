package nz.myinspection.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SupplementSnapshotTest {
    @Test
    fun `both fields participate in equality`() {
        val base = SupplementSnapshot(createdAt = 1_700_000_000_000L, text = "landlord to fix gate latch")
        assertEquals(base, base.copy())
        assertNotEquals(base, base.copy(createdAt = base.createdAt + 1))
        assertNotEquals(base, base.copy(text = "different text"))
    }
}
