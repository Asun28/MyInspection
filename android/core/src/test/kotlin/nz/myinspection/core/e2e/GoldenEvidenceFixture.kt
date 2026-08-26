package nz.myinspection.core.e2e

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class GoldenEvidenceFixture(
    val fixtureVersion: Int,
    val template: TemplateFixture,
    val property: PropertyFixture,
    val tenancy: TenancyFixture,
    val inspection: InspectionFixture,
    val photos: List<PhotoEvidenceFixture>,
    val expectedDataHash: String,
    val report: ReportExpectationFixture,
)

@Serializable
internal data class TemplateFixture(
    val resource: String,
    val type: String,
    val version: Int,
    val expectedId: String,
    val expectedContentHash: String,
    val panoramaRooms: List<String>,
)

@Serializable
internal data class PropertyFixture(
    val id: String,
    val address: String,
    val kind: String,
    val isBoardingHouse: Boolean,
)

@Serializable
internal data class TenancyFixture(
    val id: String,
    val startMs: Long,
    val endMs: Long?,
)

@Serializable
internal data class InspectionFixture(
    val expectedId: String,
    val type: String,
    val scheduledAt: Long,
    val finalizedAt: Long,
    val defaultStatus: String,
    val itemOverrides: List<ItemAnswerFixture>,
)

@Serializable
internal data class ItemAnswerFixture(
    val stableId: String,
    val status: String,
    val note: String?,
)

@Serializable
internal data class PhotoTargetFixture(
    val kind: String,
    val key: String,
)

@Serializable
internal data class PhotoEvidenceFixture(
    val key: String,
    val target: PhotoTargetFixture,
    val bytesHex: String,
    val expectedContentHash: String,
    val source: String,
    val exifTimeMs: Long,
    val privacy: Boolean,
    val reportReference: String,
)

@Serializable
internal data class ReportExpectationFixture(
    val remediationStableId: String,
    val landlordIncludePrivacyPhotos: Boolean,
    val tenantIncludePrivacyPhotos: Boolean,
    val landlordExpectedSentinels: List<String>,
    val tenantExpectedSentinels: List<String>,
    val tenantForbiddenSentinels: List<String>,
)

internal object GoldenEvidenceFixtureLoader {
    private val json = Json { ignoreUnknownKeys = false }

    fun load(): GoldenEvidenceFixture {
        val stream = checkNotNull(GoldenEvidenceFixtureLoader::class.java.getResourceAsStream(RESOURCE)) {
            "missing Golden Evidence fixture: $RESOURCE"
        }
        return stream.use { json.decodeFromString(GoldenEvidenceFixture.serializer(), it.readBytes().toString(Charsets.UTF_8)) }
    }

    private const val RESOURCE = "/e2e/golden-inspection-v1.json"
}
