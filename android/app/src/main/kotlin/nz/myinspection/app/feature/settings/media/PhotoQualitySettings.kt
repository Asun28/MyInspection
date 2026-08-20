package nz.myinspection.app.feature.settings.media

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.myinspection.core.media.PhotoQualityProfile
import nz.myinspection.core.media.PhotoQualityProfileSource

/** Persistent app-owned selection for future camera/import operations. */
class PhotoQualityPreferenceStore(
    private val sharedPreferences: SharedPreferences,
) : PhotoQualityProfileSource {
    override fun snapshotForNewPhoto(): PhotoQualityProfile =
        PhotoQualityProfile.fromStoredValue(sharedPreferences.getString(PHOTO_QUALITY_PROFILE_KEY, null))

    fun updateForFuturePhotos(profile: PhotoQualityProfile) {
        sharedPreferences.edit().putString(PHOTO_QUALITY_PROFILE_KEY, profile.storedValue).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "media_settings"
        private const val PHOTO_QUALITY_PROFILE_KEY = "photo_quality_profile"

        fun from(context: Context): PhotoQualityPreferenceStore =
            PhotoQualityPreferenceStore(
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
            )
    }
}

/**
 * Settings-page section for the new-photo profile. Navigation injects the store; existing assets are never opened or
 * rewritten here, and each already-started ingest has retained its own snapshot.
 */
@Composable
fun PhotoQualitySettingsSection(
    store: PhotoQualityPreferenceStore,
    modifier: Modifier = Modifier,
) {
    var selectedProfile by remember { mutableStateOf(store.snapshotForNewPhoto()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("New photo quality", style = MaterialTheme.typography.titleLarge)
        Text(
            "Applies only to photos saved after this change. Existing photos and their hashes are unchanged.",
            style = MaterialTheme.typography.bodyMedium,
        )
        for (profile in PhotoQualityProfile.entries) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = profile == selectedProfile,
                    onClick = {
                        store.updateForFuturePhotos(profile)
                        selectedProfile = profile
                    },
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(profile.label(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Up to ${profile.maximumLongEdgePx}px · JPEG quality ${profile.jpegQuality}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun PhotoQualityProfile.label(): String = when (this) {
    PhotoQualityProfile.LOW -> "Low"
    PhotoQualityProfile.MEDIUM -> "Medium"
    PhotoQualityProfile.HIGH -> "High"
    PhotoQualityProfile.EXTRA_HIGH -> "Extra High"
}
