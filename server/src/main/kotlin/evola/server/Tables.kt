package evola.server

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

internal object ExtractionCacheTable : Table("extraction_cache") {
    val id = uuid("id")
    val contentHash = text("content_hash").uniqueIndex()
    val vocabulary = text("vocabulary")
    val grammar = text("grammar")
    val exercises = text("exercises")
    val confidence = float("confidence")
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
    val extractionCacheId = uuid("extraction_cache_id").nullable()
    val status = text("status")
    val fileRef = text("file_ref")
    val mimeType = varchar("mime_type", 100)
    val sizeBytes = long("size_bytes")
    val pageCount = integer("page_count").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object ExtractionJobsTable : Table("extraction_jobs") {
    val id = uuid("id")
    val contentHash = text("content_hash").uniqueIndex()
    val status = text("status")
    val error = text("error").nullable()
    val contentText = text("content_text")
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
    override val primaryKey = PrimaryKey(id)
}

internal object VocabularySessionsTable : Table("vocabulary_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val lessonId = uuid("lesson_id")
    val startedAt = timestamp("started_at")
    val completedAt = timestamp("completed_at").nullable()
    val itemsCount = integer("items_count")
    val accuracy = decimal("accuracy", 5, 2).nullable()
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

internal object DailyActivityTable : Table("daily_activity") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val activityDate = date("activity_date")
    val completed = bool("completed")
    override val primaryKey = PrimaryKey(id)
}
