package nz.myinspection.app.privacy.policy

enum class AppSurface {
    BACKUP_PASSWORD,
    RESTORE_PREFLIGHT,
    TENANT_CONTACT,
    FULL_SENSITIVE_PHOTO,
    CAPTURE,
    INSPECTION_LIST,
    REPORT_ACTION,
}

enum class SurfaceProtection {
    SECURE_WINDOW,
    ORDINARY,
}

/** ADR-0006 section 5: secure-window and recents protection only on the listed sensitive surfaces, never globally. */
object SensitiveSurfacePolicy {
    private val secureSurfaces = setOf(
        AppSurface.BACKUP_PASSWORD,
        AppSurface.RESTORE_PREFLIGHT,
        AppSurface.TENANT_CONTACT,
        AppSurface.FULL_SENSITIVE_PHOTO,
    )

    fun protectionFor(surface: AppSurface): SurfaceProtection =
        if (surface in secureSurfaces) SurfaceProtection.SECURE_WINDOW else SurfaceProtection.ORDINARY
}
