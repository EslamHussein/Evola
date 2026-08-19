package evola.shared.local

import evola.shared.ai.GrammarExtractor
import evola.shared.ai.ImageTranscriber
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
import evola.shared.materials.ImageInput
import evola.shared.materials.Lesson
import evola.shared.materials.Material
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import evola.shared.materials.MaterialsRepository
import evola.shared.materials.UploadResult
import evola.shared.segmentation.PageSegmenter
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

    private fun finishUpload(
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
        if (initialInputTokens > 0 || initialOutputTokens > 0) {
            db.materialsQueries.addTokenUsage(initialInputTokens, initialOutputTokens, materialId)
        }

        launchProcessMaterial(materialId)
        return UploadResult.Success(materialId, MaterialStatus.PROCESSING)
    }

    override suspend fun list(): ApiResult<List<Material>> =
        ApiResult.Success(
            db.materialsQueries.selectByUser(LOCAL_USER).executeAsList().map { row ->
                row.toMaterial(
                    lessonsTotal = db.lessonsQueries.countByMaterial(row.id).executeAsOne().toInt(),
                    lessonsReady = db.lessonsQueries.countReadyByMaterial(row.id).executeAsOne().toInt(),
                )
            },
        )

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
        val material = row.toMaterial(lessonsTotal = lessons.size, lessonsReady = lessons.count { it.status == "ready" })
        return ApiResult.Success(MaterialDetail(material = material, lessons = lessons))
    }

    override suspend fun reprocess(materialId: String): ApiResult<Unit> {
        val row = db.materialsQueries.selectById(materialId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Material not found"))
        if (row.status != "FAILED") return ApiResult.Failure(DataError.Http(409, "Not failed"))
        db.materialsQueries.updateStatus("PROCESSING", materialId)
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
            runCatching { processMaterial(materialId) }
                .onFailure {
                    EvolaLog.d("extract", "material=$materialId processMaterial crashed: $it")
                    db.lessonsQueries.sweepStuckExtracting(materialId)
                    db.materialsQueries.updateStatus("FAILED", materialId)
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
        val documentDerivedLessonIds = db.lessonsQueries.documentDerivedLessonIdsForMaterial(materialId).executeAsList()
        documentDerivedLessonIds.forEach { lessonId ->
            db.vocabularyQueries.originatingItemsForLesson(lessonId).executeAsList().forEach { itemId ->
                val survivingLessonId = db.vocabularyQueries.survivingCurriculumLinkForItem(itemId).executeAsOneOrNull()
                if (survivingLessonId != null) {
                    db.vocabularyQueries.reassignItemOrigin(survivingLessonId, itemId)
                    db.vocabularyQueries.unlinkItemFromLesson(survivingLessonId, itemId)
                }
            }
        }
        db.materialsQueries.deleteById(materialId)
        return ApiResult.Success(Unit)
    }

    override suspend fun deleteLesson(lessonId: String): ApiResult<Unit> {
        db.lessonsQueries.deleteById(lessonId)
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
        val material = db.materialsQueries.selectById(materialId).executeAsOneOrNull() ?: return
        val text = material.content_text ?: run {
            EvolaLog.d("extract", "material=$materialId FAILED: content_text is null")
            db.materialsQueries.updateStatus("FAILED", materialId)
            return
        }
        val goalRow = db.goalsQueries.selectById(material.goal_id).executeAsOneOrNull()
        val goalText = goalRow?.goal_text ?: ""
        val nativeLanguage = goalRow?.native_language?.let { NativeLanguage.fromCode(it) } ?: NativeLanguage.ENGLISH
        val onUsage: (Int, Int) -> Unit = { input, output -> db.materialsQueries.addTokenUsage(input.toLong(), output.toLong(), materialId) }

        if (db.lessonsQueries.countByMaterial(materialId).executeAsOne() == 0L) {
            EvolaLog.d("extract", "processMaterial start: mode=${material.organization_mode} textChars=${text.length} goalChars=${goalText.length}")
            val segments: List<RawSegment> = when (material.organization_mode) {
                "entire" -> listOf(RawSegment(title = material.filename, startOffset = 0, endOffset = text.length, hasRealHeading = true))
                // No AI call at all - pure text splitting, so it's both free and immune to the
                // segmentation-prompt truncation/parse-failure modes "auto" can hit.
                "pages" -> PageSegmenter.segment(text)
                else -> when (val r = segmentation.segment(text, onUsage)) {
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
            val now = nowMillis()
            segments.forEachIndexed { index, segment ->
                db.lessonsQueries.insert(
                    newId(), materialId, material.goal_id, (index + 1).toLong(), segment.title.take(150),
                    "pending", "${segment.startOffset}:${segment.endOffset}", now,
                )
            }
        }

        val existingTerms = db.vocabularyQueries.allUserVocab(LOCAL_USER).executeAsList()
            .associateTo(mutableMapOf()) { it.term.lowercase() to it.id }

        val toProcess = db.lessonsQueries.selectByMaterial(materialId).executeAsList().filter { it.status != "ready" }
        toProcess.forEach { lesson ->
            db.lessonsQueries.updateStatus("extracting", lesson.id)
            val lessonText = sourceTextRefSlice(text, lesson.source_text_ref)
            val succeeded = extractVocabulary(lesson.id, goalText, lessonText, material.ai_instructions, nativeLanguage, existingTerms, onUsage)
            // Vocabulary-only scope: grammar extraction is disabled for now (saves the grammar
            // generation + answer-key-validation model calls). Re-enable extractGrammar(...) to
            // bring Grammar back.
            db.lessonsQueries.updateStatus(if (succeeded) "ready" else "failed", lesson.id)
        }

        val allReady = db.lessonsQueries.selectByMaterial(materialId).executeAsList().all { it.status == "ready" }
        if (allReady) {
            EvolaLog.d("extract", "material=$materialId READY")
            db.materialsQueries.updateStatus("READY", materialId)
        } else {
            EvolaLog.d("extract", "material=$materialId FAILED (partial - some lessons still need extraction)")
            db.materialsQueries.updateStatus("FAILED", materialId)
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
                db.vocabularyQueries.linkItemToLesson(lessonId, existingItemId)
                return@forEach
            }
            val itemId = newId()
            existingTerms[key] = itemId
            db.vocabularyQueries.insertItem(
                itemId, lessonId, item.term, item.meaning, item.gender, item.exampleSentence,
                item.partOfSpeech, item.plural, null, item.exampleSentenceTranslation, item.nativeMeaning,
                item.ipaPronunciation, encodeStringList(item.relatedWords), item.difficultyRating,
                item.frequencyRating, item.memoryTip, item.grammarNote, now,
            )
            db.vocabularyQueries.insertProgress(newId(), LOCAL_USER, itemId, "unseen", 0L, 0L, 0L, now, null, 0L, 0L)
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

    private fun evola.shared.db.Materials.toMaterial(lessonsTotal: Int = 0, lessonsReady: Int = 0) = Material(
        id = id,
        userId = user_id,
        goalId = goal_id,
        filename = filename,
        contentHash = content_hash,
        status = runCatching { MaterialStatus.valueOf(status) }.getOrDefault(MaterialStatus.PROCESSING),
        mimeType = mime_type,
        sizeBytes = size_bytes,
        pageCount = page_count?.toInt(),
        inputTokens = input_tokens,
        outputTokens = output_tokens,
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
