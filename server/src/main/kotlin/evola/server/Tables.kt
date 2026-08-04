package evola.server

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

internal object ExtractionCacheTable : Table("extraction_cache") {
    val id = uuid("id")
    val contentHash = text("content_hash").uniqueIndex()
    val segments = text("segments")
    val detectedLanguage = varchar("detected_language", 10).nullable()
    val unsupportedContent = bool("unsupported_content")
    val modelVersion = text("model_version")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object MaterialsTable : Table("materials") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val goalId = uuid("goal_id")
    val filename = text("filename")
    val contentHash = text("content_hash")
    val status = text("status")
    val fileRef = text("file_ref")
    val mimeType = varchar("mime_type", 100)
    val sizeBytes = long("size_bytes")
    val pageCount = integer("page_count").nullable()
    /** "auto" (default, LLM/heuristic segmentation via the shared extraction_jobs/extraction_cache
     * pipeline) or "entire" (one synthetic lesson spanning the whole document, materialized
     * synchronously at upload time - see [MaterialService]). "manual" is visually offered by the
     * AI Wizard but not yet backed - never actually stored here. */
    val organizationMode = varchar("organization_mode", 20)
    val aiInstructions = text("ai_instructions").nullable()
    val resourceType = varchar("resource_type", 30).nullable()
    /** Only populated for "entire" mode materials - their single lesson's full text, stored here
     * rather than the shared (content-hash-keyed) extraction_jobs table since that table is a
     * cross-user/cross-material cache and an "entire" upload's per-material lesson-splitting
     * choice isn't a property of the content itself. */
    val contentText = text("content_text").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ExtractionJobsTable : Table("extraction_jobs") {
    val id = uuid("id")
    val contentHash = text("content_hash").uniqueIndex()
    val status = text("status")
    val error = text("error").nullable()
    val contentText = text("content_text")
    /** JSON array of [start,end] chunk ranges that exhausted retries - reprocess() re-attempts only these. */
    val failedRanges = text("failed_ranges").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ModelCallLogTable : Table("model_call_log") {
    val id = uuid("id")
    val taskType = text("task_type")
    val modelTier = text("model_tier")
    val inputTokens = integer("input_tokens")
    val outputTokens = integer("output_tokens")
    val costEstimate = double("cost_estimate")
    val cacheHit = bool("cache_hit")
    val materialId = uuid("material_id").nullable()
    val userId = uuid("user_id").nullable()
    val extractionJobId = uuid("extraction_job_id").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object UsersTable : Table("users") {
    val id = uuid("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val fullName = text("full_name")
    val emailVerified = bool("email_verified")
    val onboardingCompleted = bool("onboarding_completed")
    val failedLoginCount = integer("failed_login_count")
    val lockedUntil = timestamp("locked_until").nullable()
    val lastFailedLoginAt = timestamp("last_failed_login_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object PasswordResetTokensTable : Table("password_reset_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val tokenHash = text("token_hash")
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object RefreshTokensTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val tokenHash = text("token_hash").uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object GoalsTable : Table("goals") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val goalText = varchar("goal_text", 200)
    val title = varchar("title", 60).nullable()
    val isActive = bool("is_active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LessonsTable : Table("lessons") {
    val id = uuid("id")
    val materialId = uuid("material_id")
    val goalId = uuid("goal_id")
    val number = integer("number")
    val title = varchar("title", 150)
    val status = text("status")
    val sourceTextRef = text("source_text_ref").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object VocabularyItemsTable : Table("vocabulary_items") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id")
    val term = varchar("term", 100)
    val meaning = varchar("meaning", 200)
    val gender = varchar("gender", 20).nullable()
    val exampleSentence = text("example_sentence").nullable()
    /** Populated from V10 on - existing rows extracted before it stay null, which simply excludes
     * them from the "fill in the blank" drill (falls back to typed-recall/multiple-choice). */
    val partOfSpeech = varchar("part_of_speech", 30).nullable()
    val grammaticalCase = varchar("grammatical_case", 20).nullable()
    val exampleSentenceTranslation = text("example_sentence_translation").nullable()
    /** Populated from V12 on - the design handoff's Arabic-native-language vocabulary session;
     * existing rows extracted before it stay null (falls back to the English [meaning] in the UI). */
    val meaningAr = text("meaning_ar").nullable()
    val ipaPronunciation = varchar("ipa_pronunciation", 100).nullable()
    /** JSON array of strings (2-4 related German words). */
    val relatedWords = text("related_words").nullable()
    val difficultyRating = varchar("difficulty_rating", 20).nullable()
    val frequencyRating = varchar("frequency_rating", 20).nullable()
    val memoryTip = text("memory_tip").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object VocabularyProgressTable : Table("vocabulary_progress") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val vocabularyItemId = uuid("vocabulary_item_id")
    val masteryState = text("mastery_state")
    val correctStreak = integer("correct_streak")
    val intervalIndex = short("interval_index")
    val nextReviewAt = timestamp("next_review_at")
    val lastReviewedAt = timestamp("last_reviewed_at").nullable()
    /** Populated from V13 on (pack/stage session redesign) - the Discover card's bookmark and
     * "mark as difficult" icon buttons. */
    val isBookmarked = bool("is_bookmarked")
    val markedDifficult = bool("marked_difficult")
    override val primaryKey = PrimaryKey(id)
}

internal object VocabularyExtractionJobsTable : Table("vocabulary_extraction_jobs") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id").uniqueIndex()
    val status = text("status")
    val error = text("error").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** Pack/stage vocabulary session architecture (design handoff Phase 7). One row per pack of ~5
 * words a user works through for a lesson - replaces the old flat-session model entirely (its
 * tables were dropped in V14 once this architecture was proven in production). */
internal object VocabularyPacksTable : Table("vocabulary_packs") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val lessonId = uuid("lesson_id")
    val packNumber = integer("pack_number")
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()
    val itemsCount = integer("items_count")
    val accuracy = decimal("accuracy", 5, 2).nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object VocabularyPackWordsTable : Table("vocabulary_pack_words") {
    val id = uuid("id")
    val packId = uuid("pack_id")
    val vocabularyItemId = uuid("vocabulary_item_id")
    val position = integer("position")
    /** JSON array of meaning choices for stage 1 (Recognition), computed once at pack creation. */
    val recognitionChoices = text("recognition_choices").nullable()
    override val primaryKey = PrimaryKey(id)
}

/** stage_index is 0-6, matching the design's 7 stages (Discover..Free Production) 0-indexed. One
 * row per (pack_word, stage) - no retries modeled, since the design's own "Check" -> "Continue"
 * footer never lets a stage be re-submitted. */
internal object VocabularyStageAnswersTable : Table("vocabulary_stage_answers") {
    val id = uuid("id")
    val packWordId = uuid("pack_word_id")
    val stageIndex = short("stage_index")
    val userResponse = text("user_response").nullable()
    val correct = bool("correct").nullable()
    val answeredAt = timestamp("answered_at")
    override val primaryKey = PrimaryKey(id)
}

internal object GrammarTopicsTable : Table("grammar_topics") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id")
    val name = varchar("name", 100)
    val explanation = text("explanation")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object GrammarExercisesTable : Table("grammar_exercises") {
    val id = uuid("id")
    val topicId = uuid("topic_id")
    val type = text("type")
    val prompt = text("prompt")
    val answerKey = text("answer_key")
    val distractors = text("distractors").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object GrammarProgressTable : Table("grammar_progress") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val topicId = uuid("topic_id")
    val masteryState = text("mastery_state")
    val correctStreak = integer("correct_streak")
    val intervalIndex = short("interval_index")
    val nextReviewAt = timestamp("next_review_at")
    val lastReviewedAt = timestamp("last_reviewed_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object GrammarSessionsTable : Table("grammar_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val topicId = uuid("topic_id")
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()
    val accuracy = decimal("accuracy", 5, 2).nullable()
    override val primaryKey = PrimaryKey(id)
}

/** M7 work queue for grammar extraction (mirrors [VocabularyExtractionJobsTable] exactly) -
 * auto-queued in parallel with vocabulary extraction per lesson. */
internal object GrammarExtractionJobsTable : Table("grammar_extraction_jobs") {
    val id = uuid("id")
    val lessonId = uuid("lesson_id").uniqueIndex()
    val status = text("status")
    val error = text("error").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

/** One row per answered exercise within a grammar_session - NOT a snapshot of session membership
 * (a topic's exercise set is immutable after generation, so "this session's exercises" is always
 * derived as grammar_exercises WHERE topic_id = session.topic_id). The mastery snapshot taken
 * immediately after this answer makes a retried/duplicate POST trivially idempotent. */
internal object GrammarSessionAnswersTable : Table("grammar_session_answers") {
    val id = uuid("id")
    val sessionId = uuid("session_id")
    val exerciseId = uuid("exercise_id")
    val response = text("response").nullable()
    val correct = bool("correct")
    val masteryStateAfter = text("mastery_state_after")
    val nextReviewAtAfter = timestamp("next_review_at_after")
    val answeredAt = timestamp("answered_at")
    override val primaryKey = PrimaryKey(id)
}

internal object DailyActivityTable : Table("daily_activity") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val activityDate = date("activity_date")
    val completed = bool("completed")
    override val primaryKey = PrimaryKey(id)
}
