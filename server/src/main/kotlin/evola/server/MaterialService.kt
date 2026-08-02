package evola.server

import evola.shared.materials.ExtractionCache
import evola.shared.materials.Exercise
import evola.shared.materials.GrammarRule
import evola.shared.materials.Material
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.VocabItem
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

internal val MATERIALS_JSON = Json { ignoreUnknownKeys = true }

private const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
private const val MIN_EXTRACTABLE_LENGTH = 20

sealed interface UploadOutcome {
    data class Created(val materialId: String, val status: String) : UploadOutcome
    data object GoalNotFound : UploadOutcome
    data object UnsupportedFileType : UploadOutcome
    data object FileTooLarge : UploadOutcome
    data object PasswordProtected : UploadOutcome
    data object CorruptedFile : UploadOutcome
    data object NoExtractableText : UploadOutcome
    data class DuplicateFile(val existingMaterialId: String) : UploadOutcome
}

@Serializable
data class MaterialUploadResponse(@SerialName("material_id") val materialId: String, val status: String)

@Serializable
data class MaterialStatusResponse(val status: String, @SerialName("page_count") val pageCount: Int? = null)

@Serializable
data class DuplicateFileResponse(
    val error: ErrorBody = ErrorBody("DUPLICATE_FILE", "You've already uploaded this file."),
    @SerialName("existing_material_id") val existingMaterialId: String,
)

/**
 * Material Upload rebuild (01_PRODUCT_SPEC.md §1.5): real PDF/DOCX files, MIME-sniffed from
 * content (never trusted from the client's declared type or file extension), 25MB cap, per-user
 * duplicate detection by content hash, stored on local disk. Text extraction happens here so
 * [ExtractionWorker] keeps working on plain text exactly as before - only how that text gets
 * produced changed.
 */
class MaterialService(
    private val database: Database,
    private val uploadsDir: String = "uploads",
    private val onJobQueued: () -> Unit = {},
) {

    suspend fun uploadMaterial(userId: String, goalId: String, fileName: String, bytes: ByteArray): UploadOutcome {
        if (bytes.size > MAX_FILE_SIZE_BYTES) return UploadOutcome.FileTooLarge

        val mimeType = FileTextExtractor.detectMimeType(bytes) ?: return UploadOutcome.UnsupportedFileType

        val userUuid = UUID.fromString(userId)
        val goalUuid = UUID.fromString(goalId)
        val goalOwned = newSuspendedTransaction(Dispatchers.IO, database) {
            GoalsTable.selectAll().where { (GoalsTable.id eq goalUuid) and (GoalsTable.userId eq userUuid) }.any()
        }
        if (!goalOwned) return UploadOutcome.GoalNotFound

        val parsed = FileTextExtractor.extractText(bytes, mimeType)
        val (text, pageCount) = when (parsed) {
            is FileParseOutcome.Success -> parsed.text to parsed.pageCount
            FileParseOutcome.PasswordProtected -> return UploadOutcome.PasswordProtected
            FileParseOutcome.Corrupted -> return UploadOutcome.CorruptedFile
        }
        val normalizedText = normalize(text)
        if (normalizedText.length < MIN_EXTRACTABLE_LENGTH) return UploadOutcome.NoExtractableText

        val contentHash = sha256(normalizedText)
        var queuedNewJob = false

        val outcome = newSuspendedTransaction(Dispatchers.IO, database) {
            val existingForUser = MaterialsTable
                .selectAll().where { (MaterialsTable.userId eq userUuid) and (MaterialsTable.contentHash eq contentHash) }
                .singleOrNull()
            if (existingForUser != null) {
                return@newSuspendedTransaction UploadOutcome.DuplicateFile(existingForUser[MaterialsTable.id].toString())
            }

            val materialId = UUID.randomUUID()
            val now = Instant.now()
            val fileRef = saveToDisk(materialId, mimeType, bytes)

            val cachedId = ExtractionCacheTable
                .selectAll().where { ExtractionCacheTable.contentHash eq contentHash }
                .singleOrNull()?.get(ExtractionCacheTable.id)
            val status = if (cachedId != null) "ANALYZED" else "UPLOADED"

            MaterialsTable.insert {
                it[id] = materialId
                it[this.userId] = userUuid
                it[this.goalId] = goalUuid
                it[filename] = fileName
                it[this.contentHash] = contentHash
                it[extractionCacheId] = cachedId
                it[this.status] = status
                it[this.fileRef] = fileRef
                it[this.mimeType] = mimeType
                it[sizeBytes] = bytes.size.toLong()
                it[this.pageCount] = pageCount
                it[createdAt] = now
            }

            if (cachedId == null) {
                val alreadyQueued = ExtractionJobsTable.selectAll().where { ExtractionJobsTable.contentHash eq contentHash }.any()
                if (!alreadyQueued) {
                    ExtractionJobsTable.insert {
                        it[id] = UUID.randomUUID()
                        it[this.contentHash] = contentHash
                        it[this.status] = "QUEUED"
                        it[contentText] = text
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                    queuedNewJob = true
                }
            }

            UploadOutcome.Created(materialId.toString(), status)
        }

        if (queuedNewJob) onJobQueued()
        return outcome
    }

    /** Re-queues a failed material's extraction job. Returns false if the material isn't the
     * caller's, doesn't exist, or isn't currently in a failed state. */
    suspend fun reprocess(userId: String, materialId: String): Boolean {
        val requeued = newSuspendedTransaction(Dispatchers.IO, database) {
            val materialUuid = UUID.fromString(materialId)
            val material = MaterialsTable
                .selectAll().where { (MaterialsTable.id eq materialUuid) and (MaterialsTable.userId eq UUID.fromString(userId)) }
                .singleOrNull() ?: return@newSuspendedTransaction false
            if (material[MaterialsTable.status] != "FAILED") return@newSuspendedTransaction false

            val now = Instant.now()
            MaterialsTable.update({ MaterialsTable.id eq materialUuid }) { it[status] = "UPLOADED" }
            val updatedJobs = ExtractionJobsTable.update({
                (ExtractionJobsTable.contentHash eq material[MaterialsTable.contentHash]) and (ExtractionJobsTable.status eq "FAILED")
            }) {
                it[status] = "QUEUED"
                it[error] = null
                it[updatedAt] = now
            }
            updatedJobs > 0
        }
        if (requeued) onJobQueued()
        return requeued
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

    private fun saveToDisk(materialId: UUID, mimeType: String, bytes: ByteArray): String {
        val extension = if (mimeType == MIME_PDF) "pdf" else "docx"
        val dir = File(uploadsDir).apply { mkdirs() }
        val file = File(dir, "$materialId.$extension")
        file.writeBytes(bytes)
        return file.path
    }

    private fun ResultRow.toMaterial() = Material(
        id = this[MaterialsTable.id].toString(),
        userId = this[MaterialsTable.userId].toString(),
        goalId = this[MaterialsTable.goalId].toString(),
        filename = this[MaterialsTable.filename],
        contentHash = this[MaterialsTable.contentHash],
        extractionCacheId = this[MaterialsTable.extractionCacheId]?.toString(),
        status = MaterialStatus.valueOf(this[MaterialsTable.status]),
        mimeType = this[MaterialsTable.mimeType],
        sizeBytes = this[MaterialsTable.sizeBytes],
        pageCount = this[MaterialsTable.pageCount],
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
