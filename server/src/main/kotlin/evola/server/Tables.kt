package evola.server

import org.jetbrains.exposed.sql.Table
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
    val filename = text("filename")
    val contentHash = text("content_hash")
    val extractionCacheId = uuid("extraction_cache_id").nullable()
    val status = text("status")
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
