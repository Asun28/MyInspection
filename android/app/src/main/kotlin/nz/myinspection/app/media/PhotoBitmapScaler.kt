package nz.myinspection.app.media

import android.graphics.Bitmap
import nz.myinspection.core.media.PhotoQualityProfile

/** Android bitmap adapter for the pure-core photo profile cap. It never allocates when no downscale is needed. */
object PhotoBitmapScaler {
    fun scaleDown(source: Bitmap, profile: PhotoQualityProfile): Bitmap {
        val target = profile.scaledDimensions(source.width, source.height)
        if (target.width == source.width && target.height == source.height) return source
        return Bitmap.createScaledBitmap(source, target.width, target.height, true)
    }
}
