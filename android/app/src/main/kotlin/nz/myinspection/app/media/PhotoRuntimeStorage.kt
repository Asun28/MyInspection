package nz.myinspection.app.media

import android.content.Context
import java.io.File

/** Single runtime composition point for the private media root and SQLDelight database name. */
data class PhotoRuntimeStorage(
    val mediaRoot: File,
    val databaseName: String,
) {
    companion object {
        const val DATABASE_NAME = "myinspection.db"

        fun from(context: Context): PhotoRuntimeStorage =
            PhotoRuntimeStorage(mediaRoot = File(context.filesDir, "media"), databaseName = DATABASE_NAME)
    }
}
