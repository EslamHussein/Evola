package evola.shared.feature.materials.data

import evola.database.AppDatabase
import evola.database.entity.GrammarExerciseEntity
import evola.database.entity.GrammarExtractionJobEntity
import evola.database.entity.GrammarProgressEntity
import evola.database.entity.GrammarTopicEntity
import evola.database.entity.LessonEntity
import evola.database.entity.LessonVocabularyItemEntity
import evola.database.entity.MaterialEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.shared.feature.materials.domain.GrammarExtractor
import evola.shared.feature.materials.domain.ImageTranscriber
import evola.shared.feature.materials.domain.SegmentationExtractor
import evola.shared.feature.materials.domain.VocabularyExtractor
import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.encodeStringList
import evola.shared.core.common.grammarTopicCountRoom
import evola.shared.core.common.lessonGrammarProgressRoom
import evola.shared.core.common.lessonVocabProgressRoom
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.core.common.vocabItemCountRoom
import evola.shared.core.common.FileTextExtractor
import evola.shared.core.common.detectMimeType
import evola.shared.core.common.MIME_TEXT_PLAIN
import evola.shared.language.NativeLanguage
import evola.shared.feature.materials.domain.ImageInput
import evola.shared.feature.materials.domain.Lesson
import evola.shared.feature.materials.domain.Material
import evola.shared.feature.materials.domain.MaterialDetail
import evola.shared.feature.materials.domain.MaterialStatus
import evola.shared.feature.materials.domain.MaterialsRepository
import evola.shared.feature.materials.domain.MIN_EXTRACTABLE_TEXT_LENGTH
import evola.shared.feature.materials.domain.UploadResult
import evola.shared.feature.materials.domain.PageSegmenter
import evola.shared.feature.materials.domain.RawSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024
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
    private val db: AppDatabase,
    private val fileTextExtractor: FileTextExtractor,
    private val segmentation: SegmentationExtractor,
    private val vocabExtractor: VocabularyExtractor,
    private val grammarExtractor: GrammarExtractor,
    private val imageTranscriber: ImageTranscriber,
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

    override suspend fun uploadImages(
        goalId: String,
        images: List<ImageInput>,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
    ): UploadResult {
        if (images.isEmpty()) return UploadResult.NoExtractableText
        if (images.sumOf { it.bytes.size } > MAX_FILE_SIZE_BYTES) return UploadResult.FileTooLarge

        var transcribeInputTokens = 0L
        var transcribeOutputTokens = 0L
        val transcribed = images.mapNotNull { image ->
            when (val result = imageTranscriber.transcribe(image.bytes, image.mimeType) { i, o -> transcribeInputTokens += i; transcribeOutputTokens += o }) {
                is ApiResult.Success -> result.data.trim().takeIf { it.isNotEmpty() }
                is ApiResult.Failure -> {
                    EvolaLog.d("materials", "image transcription failed for ${image.fileName}: ${result.error}")
                    null
                }
            }
        }
        val combinedText = transcribed.joinToString("\n\n")
        if (combinedText.isEmpty()) return UploadResult.NoExtractableText

        val fileName = if (images.size == 1) images.first().fileName else "${images.size} photos"
        return finishUpload(
            goalId, fileName, MIME_TEXT_PLAIN, combinedText.encodeToByteArray().size.toLong(),
            combinedText, organizationMode, aiInstructions, resourceType,
            initialInputTokens = transcribeInputTokens, initialOutputTokens = transcribeOutputTokens,
        )
    }

    private suspend fun finishUpload(
        goalId: String,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        rawText: String,
        organizationMode: String,
        aiInstructions: String?,
        resourceType: String?,
        initialInputTokens: Long = 0,
        initialOutputTokens: Long = 0,
    ): UploadResult {
        if (db.goalDao().selectById(goalId) == null) return UploadResult.GoalNotFound

        val normalized = normalize(rawText)
        if (normalized.length < MIN_EXTRACTABLE_TEXT_LENGTH) return UploadResult.NoExtractableText

        val contentHash = contentHash(normalized)
        db.materialDao().selectByContentHash(LOCAL_USER, contentHash).firstOrNull()?.let {
            return UploadResult.DuplicateFile(it.id)
        }

        val materialId = newId()
        db.materialDao().insert(
            MaterialEntity(
                materialId, LOCAL_USER, goalId, fileName, contentHash, "PROCESSING", mimeType, sizeBytes,
                null, organizationMode, aiInstructions, resourceType, rawText, 0L, 0L, nowMillis(),
            ),
        )
        if (initialInputTokens > 0 || initialOutputTokens > 0) {
            db.materialDao().addTokenUsage(initialInputTokens, initialOutputTokens, materialId)
        }

        launchProcessMaterial(materialId)
        return UploadResult.Success(materialId, MaterialStatus.PROCESSING)
    }

    override suspend fun list(): ApiResult<List<Material>> =
        ApiResult.Success(
            db.materialDao().selectByUser(LOCAL_USER).map { row ->
                row.toMaterial(
                    lessonsTotal = db.lessonDao().countByMaterial(row.id).toInt(),
                    lessonsReady = db.lessonDao().countReadyByMaterial(row.id).toInt(),
                )
            },
        )

    override suspend fun get(materialId: String): ApiResult<MaterialDetail> {
        val row = db.materialDao().selectById(materialId)
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))
        val lessons = db.lessonDao().selectByMaterial(materialId).map { l ->
            Lesson(
                id = l.id,
                materialId = l.materialId,
                goalId = l.goalId,
                number = l.number.toInt(),
                title = l.title,
                status = l.status,
                vocabCount = db.vocabItemCountRoom(l.id),
                vocabProgress = db.lessonVocabProgressRoom(l.id),
                grammarCount = db.grammarTopicCountRoom(l.id),
                grammarProgress = db.lessonGrammarProgressRoom(l.id),
            )
        }
        val material = row.toMaterial(lessonsTotal = lessons.size, lessonsReady = lessons.count { it.status == "ready" })
        return ApiResult.Success(MaterialDetail(material = material, lessons = lessons))
    }

    override suspend fun reprocess(materialId: String): ApiResult<Unit> {
        val row = db.materialDao().selectById(materialId)
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))
        if (row.status != "FAILED") return ApiResult.Failure(DataError.Http(409, "Not failed"))
        db.materialDao().updateStatus("PROCESSING", materialId)
        launchProcessMaterial(materialId)
        return ApiResult.Success(Unit)
    }

    /** [processMaterial] is resumable (see its own doc comment), so a retry after a partial
     * failure only reprocesses what didn't finish - no special "delete and redo" needed here. A
     * crash mid-run (not a clean [ApiResult.Failure] from an AI call, an actual exception) is the
     * one case [processMaterial] itself can't clean up after: it's thrown out of whatever DB/lesson
     * loop it was in, potentially leaving a lesson stuck at `"extracting"` forever - sweep that back
     * to `"failed"` (so a future retry picks it up) and flip the material to FAILED so the UI
     * surfaces it instead of spinning indefinitely.
     * Known limitation: if the app process itself is killed while PROCESSING (not FAILED), nothing
     * resumes it automatically - `reprocess()`'s own guard only fires from FAILED. */
    private fun launchProcessMaterial(materialId: String) {
        scope.launch {
            val result = runCatching { processMaterial(materialId) }
            if (result.isFailure) {
                EvolaLog.d("extract", "material=$materialId processMaterial crashed: ${result.exceptionOrNull()}")
                db.lessonDao().sweepStuckExtracting(materialId)
                db.materialDao().updateStatus("FAILED", materialId)
            }
        }
    }

    /** Curriculum-origin materials delete exactly as before (unaffected by this). For a material
     * with document-derived (draft/reviewed) lessons: before the cascade runs, re-parent any word
     * whose origin is one of those lessons but which ALSO still has a surviving curriculum link -
     * otherwise the plain FK cascade below would delete a word the user has since incorporated
     * into their real curriculum, along with every lesson it's linked to. A word with no surviving
     * curriculum link is untouched here and cascades away normally. */
    override suspend fun deleteMaterial(materialId: String): ApiResult<Unit> {
        val documentDerivedLessonIds = db.lessonDao().documentDerivedLessonIdsForMaterial(materialId)
        documentDerivedLessonIds.forEach { lessonId ->
            db.vocabularyDao().originatingItemsForLesson(lessonId).forEach { itemId ->
                val survivingLessonId = db.vocabularyDao().survivingCurriculumLinkForItem(itemId)
                if (survivingLessonId != null) {
                    db.vocabularyDao().reassignItemOrigin(survivingLessonId, itemId)
                    db.vocabularyDao().unlinkItemFromLesson(survivingLessonId, itemId)
                }
            }
        }
        db.materialDao().deleteById(materialId)
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteLesson(lessonId: String): ApiResult<Unit> {
        db.lessonDao().deleteById(lessonId)
        return ApiResult.Success(Unit)
    }

    /** The whole on-device extraction coroutine: segment (or one "entire"/"pages" lesson set), then
     * per lesson extract vocabulary into the local DB; finally flip the material to READY (every
     * lesson "ready") / FAILED (partial - retry only reprocesses what's left) / UNSUPPORTED_CONTENT.
     *
     * Resumable by construction, so [reprocess] can just call this again after a failure without
     * any special-cased "delete and redo": segmentation only runs if this material has NO lesson
     * rows yet (line below) - a retry with existing rows skips straight to the per-lesson loop,
     * and that loop only (re-)processes lessons not already `"ready"`. This is what makes a retry
     * cheap in AI tokens instead of repeating already-successful work. */
    suspend fun processMaterial(materialId: String) {
        val material = db.materialDao().selectById(materialId) ?: return
        val text = material.contentText ?: run {
            EvolaLog.d("extract", "material=$materialId FAILED: content_text is null")
            db.materialDao().updateStatus("FAILED", materialId)
            return
        }
        val goalRow = db.goalDao().selectById(material.goalId)
        val goalText = goalRow?.goalText ?: ""
        val nativeLanguage = goalRow?.nativeLanguage?.let { NativeLanguage.fromCode(it) } ?: NativeLanguage.ENGLISH
        // onUsage's signature is a plain, non-suspend callback shared across every AI-calling
        // extractor (SegmentationExtractor/VocabularyExtractor/ImageTranscriber all the way down
        // to AnthropicClient.complete) - out of scope to make suspend here. addTokenUsage's own
        // UPDATE is a commutative increment, so a launched write racing another (or the final
        // status flip) can't lose data, only transiently lag the displayed token count by a beat.
        val onUsage: (Int, Int) -> Unit = { input, output -> scope.launch { db.materialDao().addTokenUsage(input.toLong(), output.toLong(), materialId) } }

        if (db.lessonDao().countByMaterial(materialId) == 0L) {
            EvolaLog.d("extract", "processMaterial start: mode=${material.organizationMode} textChars=${text.length} goalChars=${goalText.length}")
            val segments: List<RawSegment> = when (material.organizationMode) {
                "entire" -> listOf(RawSegment(title = material.filename, startOffset = 0, endOffset = text.length, hasRealHeading = true))
                // No AI call at all - pure text splitting, so it's both free and immune to the
                // segmentation-prompt truncation/parse-failure modes "auto" can hit.
                "pages" -> PageSegmenter.segment(text)
                else -> when (val r = segmentation.segment(text, onUsage)) {
                    is ApiResult.Success -> {
                        if (r.data.unsupported) {
                            EvolaLog.d("extract", "material=$materialId UNSUPPORTED_CONTENT (segmentation)")
                            db.materialDao().updateStatus("UNSUPPORTED_CONTENT", materialId)
                            return
                        }
                        r.data.segments
                    }
                    is ApiResult.Failure -> {
                        EvolaLog.d("extract", "material=$materialId FAILED: segmentation error=${r.error}")
                        db.materialDao().updateStatus("FAILED", materialId)
                        return
                    }
                }
            }
            EvolaLog.d("extract", "material=$materialId segmented into ${segments.size} lesson(s)")
            val now = nowMillis()
            segments.forEachIndexed { index, segment ->
                db.lessonDao().insert(
                    LessonEntity(
                        newId(), materialId, material.goalId, (index + 1).toLong(), segment.title.take(150),
                        "pending", "curriculum", null, "${segment.startOffset}:${segment.endOffset}", now,
                    ),
                )
            }
        }

        val existingTerms = db.vocabularyDao().allUserVocab(LOCAL_USER)
            .associateTo(mutableMapOf()) { it.term.lowercase() to it.id }

        val toProcess = db.lessonDao().selectByMaterial(materialId).filter { it.status != "ready" }
        toProcess.forEach { lesson ->
            db.lessonDao().updateStatus("extracting", lesson.id)
            val lessonText = sourceTextRefSlice(text, lesson.sourceTextRef)
            val succeeded = extractVocabulary(lesson.id, goalText, lessonText, material.aiInstructions, nativeLanguage, existingTerms, onUsage)
            // Vocabulary-only scope: grammar extraction is disabled for now (saves the grammar
            // generation + answer-key-validation model calls). Re-enable extractGrammar(...) to
            // bring Grammar back.
            db.lessonDao().updateStatus(if (succeeded) "ready" else "failed", lesson.id)
        }

        val allReady = db.lessonDao().selectByMaterial(materialId).all { it.status == "ready" }
        if (allReady) {
            EvolaLog.d("extract", "material=$materialId READY")
            db.materialDao().updateStatus("READY", materialId)
        } else {
            EvolaLog.d("extract", "material=$materialId FAILED (partial - some lessons still need extraction)")
            db.materialDao().updateStatus("FAILED", materialId)
        }
    }

    /** Recovers a lesson's slice of the material's full text from its stored `"start:end"` character
     * offsets - lets a resumed [processMaterial] run re-derive `lessonText` for a not-yet-`"ready"`
     * lesson without needing the original in-memory segment list (which only existed on the first,
     * possibly-different, run). */
    private fun sourceTextRefSlice(text: String, sourceTextRef: String?): String {
        val parts = sourceTextRef?.split(":")
        val start = parts?.getOrNull(0)?.toIntOrNull()?.coerceIn(0, text.length) ?: 0
        val end = parts?.getOrNull(1)?.toIntOrNull()?.coerceIn(start, text.length) ?: text.length
        return text.substring(start, end)
    }

    /** Returns whether extraction succeeded - the caller flips the lesson to `"ready"`/`"failed"`
     * accordingly rather than this function touching lesson status itself. */
    private suspend fun extractVocabulary(
        lessonId: String,
        goalText: String,
        lessonText: String,
        aiInstructions: String?,
        nativeLanguage: NativeLanguage,
        existingTerms: MutableMap<String, String>,
        onUsage: (Int, Int) -> Unit,
    ): Boolean {
        val items = when (val r = vocabExtractor.extract(goalText, lessonText, aiInstructions, nativeLanguage, onUsage)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> {
                EvolaLog.d("extract", "vocab extraction failed for lesson=$lessonId error=${r.error}")
                return false
            }
        }
        EvolaLog.d("extract", "vocab extracted ${items.size} item(s) for lesson=$lessonId")
        val now = nowMillis()
        items.forEach { item ->
            val key = item.term.lowercase()
            val existingItemId = existingTerms[key]
            if (existingItemId != null) {
                // Already taught (in this lesson or a previous one) - link rather than duplicate,
                // so this lesson's vocabulary list still shows the word (see lesson_vocabulary in
                // Vocabulary.sq) without a second row/progress-tracking split for the same word.
                db.vocabularyDao().linkItemToLesson(LessonVocabularyItemEntity(lessonId, existingItemId, null))
                return@forEach
            }
            val itemId = newId()
            existingTerms[key] = itemId
            db.vocabularyDao().insertItem(
                VocabularyItemEntity(
                    itemId, lessonId, item.term, item.meaning, item.gender, item.exampleSentence,
                    item.partOfSpeech, item.plural, null, item.exampleSentenceTranslation, item.nativeMeaning,
                    item.ipaPronunciation, encodeStringList(item.relatedWords), item.difficultyRating,
                    item.frequencyRating, item.memoryTip, item.grammarNote, null, now,
                ),
            )
            db.vocabularyDao().insertProgress(VocabularyProgressEntity(newId(), LOCAL_USER, itemId, "unseen", 0L, 0L, 0L, now, null, 0L, 0L))
        }
        return true
    }

    private suspend fun extractGrammar(lessonId: String, goalText: String, lessonText: String, aiInstructions: String?) {
        val topics = when (val r = grammarExtractor.extract(goalText, lessonText, aiInstructions)) {
            is ApiResult.Success -> r.data
            is ApiResult.Failure -> emptyList()
        }
        val now = nowMillis()
        topics.forEach { topic ->
            val topicId = newId()
            db.grammarDao().insertTopic(GrammarTopicEntity(topicId, lessonId, topic.name, topic.explanation, now))
            db.grammarDao().insertTopicProgress(GrammarProgressEntity(newId(), LOCAL_USER, topicId, "new", 0L, 0L, now, null))
            topic.exercises.forEach { ex ->
                db.grammarDao().insertExercise(
                    GrammarExerciseEntity(
                        newId(), topicId, ex.type, ex.prompt, ex.answerKey,
                        ex.distractors.takeIf { it.isNotEmpty() }?.let { encodeStringList(it) }, now,
                    ),
                )
            }
        }
        // A DONE grammar job row is what the Lesson Details hub reads to show the Grammar section as
        // populated (vs "still preparing") — mirrors the server's grammar_extraction_jobs row.
        // error is always null on insert, matching the original .sq's hardcoded VALUES(..., NULL, ...).
        db.grammarDao().insertJob(GrammarExtractionJobEntity(newId(), lessonId, "DONE", null, now, now))
    }

    private fun MaterialEntity.toMaterial(lessonsTotal: Int = 0, lessonsReady: Int = 0) = Material(
        id = id,
        userId = userId,
        goalId = goalId,
        filename = filename,
        contentHash = contentHash,
        status = runCatching { MaterialStatus.valueOf(status) }.getOrDefault(MaterialStatus.PROCESSING),
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        pageCount = pageCount?.toInt(),
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        lessonsTotal = lessonsTotal,
        lessonsReady = lessonsReady,
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
