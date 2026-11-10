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
    // Behavioural RED baseline stub: every surface secure.
    @Suppress("UNUSED_PARAMETER")
    fun protectionFor(surface: AppSurface): SurfaceProtection = SurfaceProtection.SECURE_WINDOW
}
