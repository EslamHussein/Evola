package evola.shared.local

import evola.shared.ai.GrammarExtractor
import evola.shared.ai.SegmentationExtractor
import evola.shared.ai.VocabularyExtractor
import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.core.EvolaLog
import evola.shared.files.FileTextExtractor
import evola.shared.files.detectMimeType
import evola.shared.files.MIME_TEXT_PLAIN
import evola.shared.db.EvolaDatabase
import evola.shared.language.NativeLanguage
import evola.shared.materials.Lesson
import evola.shared.materials.Material
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import evola.shared.segmentation.RawSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
private const val MIN_EXTRACTABLE_LENGTH = 20
private const val MAX_PASTED_TEXT_LENGTH = 200_000

/**
 * On-device materials pipeline (serverless) — ports the retired server `MaterialService` +
 * segmentation/vocabulary/grammar extraction workers into one coroutine. Upload inserts the
 * material (PROCESSING), stores its text, and launches extraction on [scope]; the UI's existing
 * "poll until READY" screens observe the status flip exactly as they polled the server. Following
 * this project's convention, the extraction path has no unit test (the server's batch workers had
 * none either) — it's verified with real Anthropic calls on-device.
 */
class LocalMaterialsRepository(
    private val db: EvolaDatabase,
    private val fileTextExtractor: FileTextExtractor,
    private val segmentation: SegmentationExtractor,
    private val vocabExtractor: VocabularyExtractor,
    private val grammarExtractor: GrammarExtractor,
    private val scope: CoroutineScope,
) : MaterialsRepository {

    override suspend fun upload(
        goalId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        if (bytes.size > MAX_FILE_SIZE_BYTES) return UploadResult.FileTooLarge
        val sniffed = detectMimeType(bytes) ?: return UploadResult.UnsupportedFileType
        val text = fileTextExtractor.extractText(bytes, sniffed) ?: return UploadResult.NoExtractableText
        return finishUpload(goalId, fileName, sniffed, bytes.size.toLong(), text, organizationMode, aiInstructions, resourceType)
    }

    override suspend fun uploadText(
        goalId: String,
        fileName: String,
        text: String,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        if (text.length > MAX_PASTED_TEXT_LENGTH) return UploadResult.FileTooLarge
        return finishUpload(goalId, fileName, MIME_TEXT_PLAIN, text.encodeToByteArray().size.toLong(), text, organizationMode, aiInstructions, resourceType)
    }

    private fun finishUpload(
        goalId: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        rawText: String,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        if (db.goalsQueries.selectById(goalId).executeAsOneOrNull() == null) return UploadResult.GoalNotFound

        val normalized = normalize(rawText)
        if (normalized.length < MIN_EXTRACTABLE_LENGTH) return UploadResult.NoExtractableText

        val contentHash = contentHash(normalized)
        db.materialsQueries.selectByContentHash(LOCAL_USER, contentHash).executeAsOneOrNull()?.let {
            return UploadResult.DuplicateFile(it.id)
        }

        val materialId = newId()
        db.materialsQueries.insert(
            materialId, LOCAL_USER, goalId, fileName, contentHash, "PROCESSING", mimeType, sizeBytes,
            null, organizationMode, aiInstructions, resourceType, rawText, nowMillis(),
        )

        scope.launch { runCatching { processMaterial(materialId) } }
        return UploadResult.Success(materialId, MaterialStatus.PROCESSING)
    }

    override suspend fun list(): ApiResult<List<Material>> =
        ApiResult.Success(db.materialsQueries.selectByUser(LOCAL_USER).executeAsList().map { it.toMaterial() })

    override suspend fun get(materialId: String): ApiResult<MaterialDetail> {
        val row = db.materialsQueries.selectById(materialId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))
        val lessons = db.lessonsQueries.selectByMaterial(materialId).executeAsList().map { l ->
            Lesson(
                id = l.id,
                materialId = l.material_id,
                goalId = l.goal_id,
                number = l.number.toInt(),
                title = l.title,
                status = l.status,
                vocabCount = db.vocabItemCount(l.id),
                vocabProgress = db.lessonVocabProgress(l.id),
                grammarCount = db.grammarTopicCount(l.id),
                grammarProgress = db.lessonGrammarProgress(l.id),
            )
        }
        return ApiResult.Success(MaterialDetail(material = row.toMaterial(), lessons = lessons))
    }

    override suspend fun reprocess(materialId: String): ApiResult<Unit> {
        val row = db.materialsQueries.selectById(materialId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))
        if (row.status != "FAILED") return ApiResult.Failure(DataError.Http(409, "Not failed"))
        db.materialsQueries.updateStatus("PROCESSING", materialId)
        scope.launch { runCatching { processMaterial(materialId) } }
        return ApiResult.Success(Unit)
    }

    /** The whole on-device extraction coroutine: segment (or one "entire" lesson), then per lesson
     * extract vocabulary + grammar into the local DB and flip the lesson to "ready"; finally flip
     * the material to READY / UNSUPPORTED_CONTENT / FAILED. */
    suspend fun processMaterial(materialId: String) {
        val material = db.materialsQueries.selectById(materialId).executeAsOneOrNull() ?: return
        val text = material.content_text ?: run {
            EvolaLog.d("extract", "material=$materialId FAILED: content_text is null")
            db.materialsQueries.updateStatus("FAILED", materialId)
            return
        }
        val goalRow = db.goalsQueries.selectById(material.goal_id).executeAsOneOrNull()
        val goalText = goalRow?.goal_text ?: ""
        val nativeLanguage = goalRow?.native_language?.let { NativeLanguage.fromCode(it) } ?: NativeLanguage.ENGLISH
        EvolaLog.d("extract", "processMaterial start: mode=${material.organization_mode} textChars=${text.length} goalChars=${goalText.length}")

        val segments: List<RawSegment> = if (material.organization_mode == "entire") {
            listOf(RawSegment(title = material.filename, startOffset = 0, endOffset = text.length, hasRealHeading = true))
        } else {
            when (val r = segmentation.segment(text)) {
                is ApiResult.Success -> {
                    if (r.data.unsupported) {
                        EvolaLog.d("extract", "material=$materialId UNSUPPORTED_CONTENT (segmentation)")
                        db.materialsQueries.updateStatus("UNSUPPORTED_CONTENT", materialId)
                        return
                    }
                    r.data.segments
                }
                is ApiResult.Failure -> {
                    EvolaLog.d("extract", "material=$materialId FAILED: segmentation error=${r.error}")
                    db.materialsQueries.updateStatus("FAILED", materialId)
                    return
                }
            }
        }
        EvolaLog.d("extract", "material=$materialId segmented into ${segments.size} lesson(s)")

        val existingTerms = db.vocabularyQueries.allUserVocab(LOCAL_USER).executeAsList()
            .map { it.term.lowercase() }.toMutableSet()

        segments.forEachIndexed { index, segment ->
            val lessonId = newId()
            val now = nowMillis()
            val lessonText = text.substring(segment.startOffset.coerceIn(0, text.length), segment.endOffset.coerceIn(0, text.length))
            db.lessonsQueries.insert(
                lessonId, materialId, material.goal_id, (index + 1).toLong(), segment.title.take(150),
                "pending", "${segment.startOffset}:${segment.endOffset}", now,
            )
            extractVocabulary(lessonId, goalText, lessonText, material.ai_instructions, nativeLanguage, existingTerms)
            // Vocabulary-only scope: grammar extraction is disabled for now (saves the grammar
            // generation + answer-key-validation model calls). Re-enable extractGrammar(...) to
            // bring Grammar back.
            db.lessonsQueries.updateStatus("ready", lessonId)
        }

        EvolaLog.d("extract", "material=$materialId READY")
        db.materialsQueries.updateStatus("READY", materialId)
    }

    private suspend fun extractVocabulary(
        lessonId: String,
        goalText: String,
        lessonText: String,
        aiInstructions: String?,
        nativeLanguage: NativeLanguage,
        existingTerms: MutableSet<String>,
    ) {
        val items = when (val r = vocabExtractor.extract(goalText, lessonText, aiInstructions, nativeLanguage)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> {
                EvolaLog.d("extract", "vocab extraction failed for lesson=$lessonId error=${r.error}")
                return
            }
        }
        EvolaLog.d("extract", "vocab extracted ${items.size} item(s) for lesson=$lessonId")
        val now = nowMillis()
        items.forEach { item ->
            val key = item.term.lowercase()
            if (key in existingTerms) return@forEach
            existingTerms.add(key)
            val itemId = newId()
            db.vocabularyQueries.insertItem(
                itemId, lessonId, item.term, item.meaning, item.gender, item.exampleSentence,
                item.partOfSpeech, item.plural, item.grammaticalCase, item.exampleSentenceTranslation, item.nativeMeaning,
                item.ipaPronunciation, encodeStringList(item.relatedWords), item.difficultyRating,
                item.frequencyRating, item.memoryTip, item.grammarNote, now,
            )
            db.vocabularyQueries.insertProgress(newId(), LOCAL_USER, itemId, "unseen", 0L, 0L, 0L, now, null, 0L, 0L)
        }
    }

    private suspend fun extractGrammar(lessonId: String, goalText: String, lessonText: String, aiInstructions: String?) {
        val topics = when (val r = grammarExtractor.extract(goalText, lessonText, aiInstructions)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> emptyList()
        }
        val now = nowMillis()
        topics.forEach { topic ->
            val topicId = newId()
            db.grammarQueries.insertTopic(topicId, lessonId, topic.name, topic.explanation, now)
            db.grammarQueries.insertTopicProgress(newId(), LOCAL_USER, topicId, "new", 0L, 0L, now, null)
            topic.exercises.forEach { ex ->
                db.grammarQueries.insertExercise(
                    newId(), topicId, ex.type, ex.prompt, ex.answerKey,
                    ex.distractors.takeIf { it.isNotEmpty() }?.let { encodeStringList(it) }, now,
                )
            }
        }
        // A DONE grammar job row is what the Lesson Details hub reads to show the Grammar section as
        // populated (vs "still preparing") — mirrors the server's grammar_extraction_jobs row.
        db.grammarQueries.insertJob(newId(), lessonId, "DONE", now, now)
    }

    private fun evola.shared.db.Materials.toMaterial() = Material(
        id = id,
        userId = user_id,
        goalId = goal_id,
        filename = filename,
        contentHash = content_hash,
        status = runCatching { MaterialStatus.valueOf(status) }.getOrDefault(MaterialStatus.PROCESSING),
        mimeType = mime_type,
        sizeBytes = size_bytes,
        pageCount = page_count?.toInt(),
    )
}

/** Collapse whitespace, trim — the retired server's `normalize`, so content hashing/length checks
 * behave identically. */
private fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

/** FNV-1a 64-bit hex — a stable, dependency-free content hash for on-device duplicate detection
 * (single-user, so no cryptographic guarantee is needed, unlike the server's cross-tenant SHA-256). */
private fun contentHash(text: String): String {
    var hash = -0x340d631b7bdddcdbL // 14695981039346656037 (FNV offset basis)
    for (ch in text) {
        hash = hash xor ch.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
