package nz.myinspection.core.e2e

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nz.myinspection.core.capture.InspectionRepository
import nz.myinspection.core.db.ClockMs
import nz.myinspection.core.db.MyInspectionDatabase
import nz.myinspection.core.db.Uuid7Generator
import nz.myinspection.core.db.Uuid7RandomSource
import nz.myinspection.core.finalize.DbCompletenessChecker
import nz.myinspection.core.finalize.FinalizeInspectionUseCase
import nz.myinspection.core.finalize.FinalizeOutcome
import nz.myinspection.core.finalize.InspectionSnapshotAssembler
import nz.myinspection.core.media.NewAssetDiscard
import nz.myinspection.core.media.PhotoAssociationRecorder
import nz.myinspection.core.media.PhotoAssociationResult
import nz.myinspection.core.media.PhotoIngest
import nz.myinspection.core.media.PhotoIngestPlan
import nz.myinspection.core.media.PhotoSource
import nz.myinspection.core.media.PhotoTarget
import nz.myinspection.core.media.StreamDigests
import nz.myinspection.core.model.InspectionItemSnapshot
import nz.myinspection.core.model.PhotoSnapshot
import nz.myinspection.core.report.BilingualText
import nz.myinspection.core.report.DocumentPlan
import nz.myinspection.core.report.MeasuredText
import nz.myinspection.core.report.ReportComposer
import nz.myinspection.core.report.ReportItem
import nz.myinspection.core.report.ReportOptions
import nz.myinspection.core.report.ReportPhoto
import nz.myinspection.core.report.ReportRemediation
import nz.myinspection.core.report.ReportRoom
import nz.myinspection.core.report.ReportSnapshot
import nz.myinspection.core.report.StatusDefinition
import nz.myinspection.core.report.TextMeasurer
import nz.myinspection.core.report.Urgency
import nz.myinspection.core.template.LoadedTemplate
import nz.myinspection.core.template.TemplateItem
import nz.myinspection.core.template.TemplateLoader
import nz.myinspection.core.template.TemplateStore

/**
 * Pure-JVM construction of the golden inspection. Production services own every business transition; only
 * presentation projection and the deliberately independent DB hash calculation live in this test harness.
 */
internal class GoldenEvidenceCoreHarness private constructor(
    val fixture: GoldenEvidenceFixture,
    val dbDataHash: String,
    val landlordPlan: DocumentPlan,
    val independentDataHash: String,
    val persistedPhotoHashes: List<String>,
    val assetContentHashes: List<String>,
    private val driver: JdbcSqliteDriver,
    private val assetRoot: Path,
) : AutoCloseable {
    override fun close() {
        driver.close()
        if (Files.exists(assetRoot)) {
            Files.walk(assetRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    companion object {
        fun execute(): GoldenEvidenceCoreHarness {
            val fixture = GoldenEvidenceFixtureLoader.load()
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            val assetRoot = Files.createTempDirectory("myinspection-golden-evidence-")
            try {
                MyInspectionDatabase.Schema.create(driver)
                val database = MyInspectionDatabase(driver)
                val loadedTemplate = loadRealTemplate(fixture)
                val templateId = TemplateStore(
                    database,
                    deterministicUuid(fixture.inspection.scheduledAt - 1),
                    ClockMs { fixture.inspection.scheduledAt - 1 },
                ).persist(loadedTemplate)
                check(templateId == fixture.template.expectedId)
                check(loadedTemplate.contentHash == fixture.template.expectedContentHash)

                insertPropertyAndTenancy(database, fixture)
                val domainUuid = deterministicUuid(fixture.inspection.scheduledAt)
                val repository = InspectionRepository(
                    database,
                    domainUuid,
                    ClockMs { fixture.inspection.scheduledAt },
                )
                val created = repository.createInspection(
                    type = fixture.inspection.type,
                    propertyId = fixture.property.id,
                    tenancyId = fixture.tenancy.id,
                    templateVersionId = templateId,
                    scheduledAt = fixture.inspection.scheduledAt,
                )
                check(created.inspectionId == fixture.inspection.expectedId)

                val roomByKey = database.roomInstanceQueries.selectByInspection(created.inspectionId)
                    .executeAsList()
                    .associateBy { it.room_key }
                check(roomByKey.keys == fixture.template.panoramaRooms.toSet())
                val overrideByStableId = fixture.inspection.itemOverrides.associateBy { it.stableId }
                loadedTemplate.template.items.forEach { item ->
                    val answer = overrideByStableId[item.stableId]
                    repository.setItemStatus(
                        inspectionId = created.inspectionId,
                        roomInstanceId = checkNotNull(roomByKey[item.room]).id,
                        stableId = item.stableId,
                        status = answer?.status ?: fixture.inspection.defaultStatus,
                        note = answer?.note,
                    )
                }

                val itemRows = database.inspectionItemQueries.selectByInspection(created.inspectionId).executeAsList()
                val itemByStableId = itemRows.associateBy { it.stable_id }
                check(itemByStableId.size == loadedTemplate.template.items.size)
                val assetHashes = ingestPhotos(
                    database = database,
                    fixture = fixture,
                    inspectionId = created.inspectionId,
                    roomByKey = roomByKey.mapValues { it.value.id },
                    itemByStableId = itemByStableId.mapValues { it.value.id },
                    uuid = domainUuid,
                    assetRoot = assetRoot,
                )

                val finalized = FinalizeInspectionUseCase(
                    database,
                    DbCompletenessChecker(database),
                    ClockMs { fixture.inspection.finalizedAt },
                ).finalize(created.inspectionId)
                check(finalized is FinalizeOutcome.Finalized) { "golden inspection did not finalize: $finalized" }
                check(finalized.finalizedAt == fixture.inspection.finalizedAt)

                val inspectionRow = database.inspectionQueries.selectById(created.inspectionId).executeAsOne()
                val dbDataHash = checkNotNull(inspectionRow.data_hash)
                val canonical = InspectionSnapshotAssembler.assemble(database, created.inspectionId, inspectionRow.finalized_at)
                val reportSnapshot = buildReportSnapshot(database, fixture, loadedTemplate, canonical)
                val composer = ReportComposer(DETERMINISTIC_MEASURER)
                val landlordPlan = composer.compose(
                    reportSnapshot,
                    nz.myinspection.core.report.Audience.LANDLORD,
                    ReportOptions(fixture.report.landlordIncludePrivacyPhotos),
                )
                val persistedPhotoHashes = roomByKey.values
                    .flatMap { database.photoQueries.selectByRoomInstance(it.id).executeAsList() }
                    .map { it.content_hash }

                return GoldenEvidenceCoreHarness(
                    fixture = fixture,
                    dbDataHash = dbDataHash,
                    landlordPlan = landlordPlan,
                    independentDataHash = independentHashFromFinalizedDatabase(database, created.inspectionId),
                    persistedPhotoHashes = persistedPhotoHashes,
                    assetContentHashes = assetHashes,
                    driver = driver,
                    assetRoot = assetRoot,
                )
            } catch (failure: Throwable) {
                driver.close()
                deleteTree(assetRoot)
                throw failure
            }
        }

        private fun loadRealTemplate(fixture: GoldenEvidenceFixture): LoadedTemplate {
            val input = checkNotNull(GoldenEvidenceCoreHarness::class.java.getResourceAsStream(fixture.template.resource)) {
                "missing real template resource ${fixture.template.resource}"
            }
            return input.use(TemplateLoader::load)
        }

        private fun insertPropertyAndTenancy(database: MyInspectionDatabase, fixture: GoldenEvidenceFixture) {
            val now = fixture.inspection.scheduledAt
            database.propertyQueries.insert(
                id = fixture.property.id,
                address = fixture.property.address,
                kind = fixture.property.kind,
                is_boarding_house = if (fixture.property.isBoardingHouse) 1 else 0,
                created_at = now,
                updated_at = now,
            )
            database.tenancyQueries.insert(
                id = fixture.tenancy.id,
                property_id = fixture.property.id,
                start_ms = fixture.tenancy.startMs,
                end_ms = fixture.tenancy.endMs,
                tenant_name = "Golden Tenant",
                contact = "tenant@example.invalid",
                baseline_inspection_id = null,
                created_at = now,
                updated_at = now,
            )
        }

        private fun ingestPhotos(
            database: MyInspectionDatabase,
            fixture: GoldenEvidenceFixture,
            inspectionId: String,
            roomByKey: Map<String, String>,
            itemByStableId: Map<String, String>,
            uuid: Uuid7Generator,
            assetRoot: Path,
        ): List<String> {
            val recorder = PhotoAssociationRecorder(database, ClockMs { fixture.inspection.scheduledAt })
            return fixture.photos.map { photo ->
                val roomId = when (photo.target.kind) {
                    "ROOM" -> checkNotNull(roomByKey[photo.target.key])
                    "ITEM" -> {
                        val itemId = checkNotNull(itemByStableId[photo.target.key])
                        database.inspectionItemQueries.selectById(itemId).executeAsOne().room_instance_id
                    }
                    else -> error("unknown fixture photo target ${photo.target.kind}")
                }
                val itemId = photo.target.key.takeIf { photo.target.kind == "ITEM" }?.let(itemByStableId::getValue)
                val pathContext = recorder.resolvePathContext(roomId)
                check(pathContext.inspectionId == inspectionId)
                val photoId = uuid.next()
                val existing = database.photoQueries.selectActiveAssetsByPropertyAndContentHash(
                    fixture.property.id,
                    photo.expectedContentHash,
                ).executeAsList()
                val plan = PhotoIngest.plan(
                    propertyId = pathContext.propertyId,
                    inspectionId = pathContext.inspectionId,
                    photoId = photoId,
                    contentHash = photo.expectedContentHash,
                    existingActiveRelPaths = existing,
                )
                check(plan is PhotoIngestPlan.WriteNewAsset)
                val asset = assetRoot.resolve(plan.relPath)
                Files.createDirectories(checkNotNull(asset.parent))
                val bytes = photo.bytesHex.hexToByteArray()
                val digest = StreamDigests.writeAndClose(Files.newOutputStream(asset)) { it.write(bytes) }
                check(digest.sha256 == photo.expectedContentHash)
                Files.newInputStream(asset).use { StreamDigests.verify(it, digest) }
                val result = recorder.record(
                    plan = plan,
                    photoId = photoId,
                    target = PhotoTarget(roomId, itemId, photo.privacy),
                    source = PhotoSource.valueOf(photo.source),
                    exifTimeMs = photo.exifTimeMs,
                    discard = NewAssetDiscard { relPath -> Files.deleteIfExists(assetRoot.resolve(relPath)) },
                )
                check(result is PhotoAssociationResult.Recorded && !result.reusedExistingAsset)
                digest.sha256
            }
        }

        private fun buildReportSnapshot(
            database: MyInspectionDatabase,
            fixture: GoldenEvidenceFixture,
            loadedTemplate: LoadedTemplate,
            canonical: nz.myinspection.core.model.InspectionSnapshot,
        ): ReportSnapshot {
            val templateItemByStableId = loadedTemplate.template.items.associateBy(TemplateItem::stableId)
            val fixturePhotoByHash = fixture.photos.associateBy(PhotoEvidenceFixture::expectedContentHash)
            val canonicalPhotoByHash = canonical.photos.associateBy(PhotoSnapshot::contentHash)
            val itemSnapshotByStableId = canonical.items.associateBy(InspectionItemSnapshot::stableId)
            val roomRows = database.roomInstanceQueries.selectByInspection(canonical.id).executeAsList().associateBy { it.room_key }
            val itemRows = database.inspectionItemQueries.selectByInspectionInTemplateOrder(canonical.id).executeAsList()
            val itemRowByStableId = itemRows.associateBy { it.stable_id }

            fun reportPhoto(row: nz.myinspection.core.db.Photo): ReportPhoto {
                val expected = fixturePhotoByHash.getValue(row.content_hash)
                return ReportPhoto(
                    id = row.id,
                    snapshot = canonicalPhotoByHash.getValue(row.content_hash),
                    privacy = row.privacy_flag == 1L,
                    reference = expected.reportReference,
                    capturedAt = expected.exifTimeMs,
                )
            }

            val rooms = fixture.template.panoramaRooms.map { roomKey ->
                val room = roomRows.getValue(roomKey)
                val roomPhotos = database.photoQueries.selectByRoomInstance(room.id).executeAsList()
                val items = loadedTemplate.template.items.filter { it.room == roomKey }.map { definition ->
                    val row = itemRowByStableId.getValue(definition.stableId)
                    ReportItem(
                        id = row.id,
                        snapshot = itemSnapshotByStableId.getValue(definition.stableId),
                        label = BilingualText(definition.textEn, definition.textZh),
                        photos = roomPhotos.filter { it.inspection_item_id == row.id }.map(::reportPhoto),
                    )
                }
                ReportRoom(
                    id = room.id,
                    label = BilingualText(roomKey, roomKey),
                    items = items,
                    photos = roomPhotos.filter { it.inspection_item_id == null }.map(::reportPhoto),
                )
            }
            val remediationItemId = itemRowByStableId.getValue(fixture.report.remediationStableId).id
            return ReportSnapshot(
                canonical = canonical,
                tenancyReference = "GOLDEN-TENANCY-001",
                rooms = rooms,
                statusDefinitions = listOf(
                    StatusDefinition("GOOD", BilingualText("Good", "良好"), BilingualText("No issue observed", "未见问题")),
                    StatusDefinition("FAIR", BilingualText("Fair", "一般"), BilingualText("Wear is visible", "可见磨损")),
                    StatusDefinition("POOR", BilingualText("Poor", "较差"), BilingualText("Attention required", "需要处理")),
                    StatusDefinition(
                        "NOT_APPLICABLE",
                        BilingualText("Not applicable", "不适用"),
                        BilingualText("This item does not apply", "本项不适用"),
                    ),
                ),
                remediations = listOf(
                    ReportRemediation(
                        remediationItemId,
                        Urgency.HIGH,
                        BilingualText("LANDLORD_ONLY_GOLDEN_SENTINEL", "LANDLORD_ONLY_GOLDEN_SENTINEL"),
                    ),
                ),
            )
        }

        /** Reads finalized rows directly and does not call production snapshot/canonical/hash helpers. */
        private fun independentHashFromFinalizedDatabase(database: MyInspectionDatabase, inspectionId: String): String {
            val inspection = database.inspectionQueries.selectById(inspectionId).executeAsOne()
            checkNotNull(inspection.finalized_at)
            checkNotNull(inspection.data_hash)
            val property = database.propertyQueries.selectById(inspection.property_id).executeAsOne()
            val tenancy = inspection.tenancy_id?.let { database.tenancyQueries.selectById(it).executeAsOne() }
            val template = database.templateVersionQueries.selectById(inspection.template_version_id).executeAsOne()
            val items = database.inspectionItemQueries.selectByInspectionInTemplateOrder(inspectionId).executeAsList()
            val roomIds = database.roomInstanceQueries.selectByInspection(inspectionId).executeAsList().map { it.id }
            val photos = roomIds.flatMap { database.photoQueries.selectByRoomInstance(it).executeAsList() }.sortedBy { it.id }
            val audios = items.flatMap { database.audioQueries.selectByInspectionItem(it.id).executeAsList() }.sortedBy { it.id }

            val canonical = jsonObject(
                "id" to json(inspection.id),
                "type" to json(inspection.type),
                "tenancy_id" to jsonNullable(inspection.tenancy_id),
                "scheduled_at" to json(inspection.scheduled_at),
                "finalized_at" to jsonNullable(inspection.finalized_at),
                "previous_inspection_id" to jsonNullable(inspection.previous_inspection_id),
                "baseline_inspection_id" to jsonNullable(inspection.baseline_inspection_id),
                "property" to jsonObject(
                    "id" to json(property.id),
                    "address" to json(property.address),
                    "kind" to json(property.kind),
                    "is_boarding_house" to json(property.is_boarding_house == 1L),
                ),
                "tenancy" to (tenancy?.let {
                    jsonObject("id" to json(it.id), "start" to json(it.start_ms), "end" to jsonNullable(it.end_ms))
                } ?: JsonNull),
                "template" to jsonObject(
                    "id" to json(template.id),
                    "type" to json(template.type),
                    "version" to json(template.version),
                    "content_hash" to json(template.content_hash),
                ),
                "items" to JsonArray(items.map {
                    jsonObject(
                        "stable_id" to json(it.stable_id),
                        "status" to json(it.status),
                        "note" to jsonNullable(it.note),
                        "wear_or_damage" to jsonNullable(it.wear_or_damage),
                    )
                }),
                "photos" to JsonArray(photos.map {
                    jsonObject(
                        "content_hash" to json(it.content_hash),
                        "source" to json(it.source),
                        "exif_time_ms" to jsonNullable(it.exif_time_ms),
                        "is_room_level" to json(it.inspection_item_id == null),
                    )
                }),
                "audios" to JsonArray(audios.map { jsonObject("content_hash" to json(it.content_hash)) }),
            ).toString().toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(canonical)
                .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        }

        private fun deterministicUuid(now: Long): Uuid7Generator = Uuid7Generator(
            ClockMs { now },
            FixedRandomSource(listOf(0x0ABCDEF0123L, 0x11223344L)),
        )

        private fun jsonObject(vararg values: Pair<String, JsonElement>): JsonObject =
            JsonObject(values.toMap().toSortedMap())

        private fun json(value: String): JsonPrimitive = JsonPrimitive(value)
        private fun json(value: Long): JsonPrimitive = JsonPrimitive(value)
        private fun json(value: Boolean): JsonPrimitive = JsonPrimitive(value)
        private fun jsonNullable(value: String?): JsonElement = value?.let(::json) ?: JsonNull
        private fun jsonNullable(value: Long?): JsonElement = value?.let(::json) ?: JsonNull

        private fun String.hexToByteArray(): ByteArray {
            require(length % 2 == 0)
            return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }

        private fun deleteTree(root: Path) {
            if (!Files.exists(root)) return
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }

        private val DETERMINISTIC_MEASURER = TextMeasurer { text, _, widthMm ->
            MeasuredText(text.chunked((widthMm / 3).coerceAtLeast(1)).ifEmpty { listOf(" ") }, 4)
        }
    }
}

private class FixedRandomSource(private val values: List<Long>) : Uuid7RandomSource {
    private var index = 0
    override fun nextLong(): Long = values.getOrElse(index++) { 0L }
}
