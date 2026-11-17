package nz.myinspection.app.privacy.policy

import kotlin.test.Test
import kotlin.test.assertEquals

class SensitiveSurfacePolicyTest {
    @Test
    fun `backup password, restore preflight, tenant contact and full sensitive photo use a secure window`() {
        val listed = listOf(
            AppSurface.BACKUP_PASSWORD,
            AppSurface.RESTORE_PREFLIGHT,
            AppSurface.TENANT_CONTACT,
            AppSurface.FULL_SENSITIVE_PHOTO,
        )
        listed.forEach { surface ->
            assertEquals(SurfaceProtection.SECURE_WINDOW, SensitiveSurfacePolicy.protectionFor(surface), surface.name)
        }
    }

    @Test
    fun `capture, inspection list and report action carry no secure window`() {
        listOf(AppSurface.CAPTURE, AppSurface.INSPECTION_LIST, AppSurface.REPORT_ACTION).forEach { surface ->
            assertEquals(SurfaceProtection.ORDINARY, SensitiveSurfacePolicy.protectionFor(surface), surface.name)
        }
    }

    @Test
    fun `only the four listed surfaces are secure, so no surface applies a global secure window`() {
        val secure = AppSurface.entries
            .filter { SensitiveSurfacePolicy.protectionFor(it) == SurfaceProtection.SECURE_WINDOW }
            .toSet()

        assertEquals(
            setOf(
                AppSurface.BACKUP_PASSWORD,
                AppSurface.RESTORE_PREFLIGHT,
                AppSurface.TENANT_CONTACT,
                AppSurface.FULL_SENSITIVE_PHOTO,
            ),
            secure,
        )
    }
}
