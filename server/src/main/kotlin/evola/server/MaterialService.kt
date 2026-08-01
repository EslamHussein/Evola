package evola.server

import evola.shared.materials.ExtractionCache
import evola.shared.materials.Exercise
import evola.shared.materials.GrammarRule
import evola.shared.materials.Material
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.VocabItem
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Serializable
data class UploadMaterialRequest(val userId: String, val filename: String, val contentText: String)

@Serializable
data class UploadMaterialResponse(val materialId: String, val status: String, val cacheHit: Boolean)

internal val MATERIALS_JSON = Json { ignoreUnknownKeys = true }

/**
 * Content-hash dedup (spec §5.1): hash the normalized text, check the shared extraction_cache
 * first. Hit -> link instantly, no job enqueued. Miss -> enqueue a real queued job (picked up by
 * [ExtractionWorker]), storing the raw text so the worker has something to extract from.
 */
class MaterialService(
    private val database: Database,
    private val onJobQueued: () -> Unit = {},
) {

    suspend fun uploadMaterial(request: UploadMaterialRequest): UploadMaterialResponse {
        var queuedNewJob = false

        val response = newSuspendedTransaction(Dispatchers.IO, database) {
            val contentHash = sha256(normalize(request.contentText))
            val materialId = UUID.randomUUID()
            val now = Instant.now()

            val cachedId = ExtractionCacheTable
                .selectAll().where { ExtractionCacheTable.contentHash eq contentHash }
                .singleOrNull()
                ?.get(ExtractionCacheTable.id)

            val status = if (cachedId != null) "ANALYZED" else "UPLOADED"

            MaterialsTable.insert {
                it[id] = materialId
                it[userId] = UUID.fromString(request.userId)
                it[filename] = request.filename
                it[this.contentHash] = contentHash
                it[extractionCacheId] = cachedId
                it[this.status] = status
                it[createdAt] = now
            }

            if (cachedId == null) {
                val alreadyQueued = ExtractionJobsTable
                    .selectAll().where { ExtractionJobsTable.contentHash eq contentHash }
                    .any()
                if (!alreadyQueued) {
                    ExtractionJobsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[this.contentHash] = contentHash
                        it[this.status] = "QUEUED"
                        it[contentText] = request.contentText
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    queuedNewJob = true
                }
            }

            UploadMaterialResponse(materialId = materialId.toString(), status = status, cacheHit = cachedId != null)
        }

        if (queuedNewJob) onJobQueued()
        return response
    }

    suspend fun listMaterials(userId: String): List<Material> =
        newSuspendedTransaction(Dispatchers.IO, database) {
            MaterialsTable
                .selectAll().where { MaterialsTable.userId eq UUID.fromString(userId) }
                .map { it.toMaterial() }
        }

    suspend fun getMaterial(materialId: String): MaterialDetail? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            val row = MaterialsTable
                .selectAll().where { MaterialsTable.id eq UUID.fromString(materialId) }
                .singleOrNull() ?: return@newSuspendedTransaction null

            val material = row.toMaterial()
            val extraction = material.extractionCacheId?.let { cacheId ->
                ExtractionCacheTable
                    .selectAll().where { ExtractionCacheTable.id eq UUID.fromString(cacheId) }
                    .singleOrNull()
                    ?.toExtractionCache()
            }

            MaterialDetail(material = material, extraction = extraction)
        }

    private fun ResultRow.toMaterial() = Material(
        id = this[MaterialsTable.id].toString(),
        userId = this[MaterialsTable.userId].toString(),
        filename = this[MaterialsTable.filename],
        contentHash = this[MaterialsTable.contentHash],
        extractionCacheId = this[MaterialsTable.extractionCacheId]?.toString(),
        status = MaterialStatus.valueOf(this[MaterialsTable.status]),
    )

    private fun ResultRow.toExtractionCache() = ExtractionCache(
        id = this[ExtractionCacheTable.id].toString(),
        contentHash = this[ExtractionCacheTable.contentHash],
        vocabulary = MATERIALS_JSON.decodeFromString(ListSerializer(VocabItem.serializer()), this[ExtractionCacheTable.vocabulary]),
        grammar = MATERIALS_JSON.decodeFromString(ListSerializer(GrammarRule.serializer()), this[ExtractionCacheTable.grammar]),
        exercises = MATERIALS_JSON.decodeFromString(ListSerializer(Exercise.serializer()), this[ExtractionCacheTable.exercises]),
        confidence = this[ExtractionCacheTable.confidence],
        modelVersion = this[ExtractionCacheTable.modelVersion],
    )

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
