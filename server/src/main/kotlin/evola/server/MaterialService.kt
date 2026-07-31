package evola.server

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database
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

/**
 * Content-hash dedup (spec §5.1): hash the normalized text, check the shared extraction_cache
 * first. Hit -> link instantly, no job enqueued. Miss -> enqueue a stub queued job; the real
 * extraction pipeline (async worker + model router) is Milestone 2, not this vertical slice.
 */
class MaterialService(private val database: Database) {

    suspend fun uploadMaterial(request: UploadMaterialRequest): UploadMaterialResponse =
        newSuspendedTransaction(Dispatchers.IO, database) {
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
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                }
            }

            UploadMaterialResponse(materialId = materialId.toString(), status = status, cacheHit = cachedId != null)
        }

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
