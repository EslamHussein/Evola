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
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

internal object UsersTable : Table("users") {
    val id = uuid("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
