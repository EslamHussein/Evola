package evola.learningresources.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

internal object LearningResourcesTable : Table("learning_resources") {
    val id = uuid("id")
    val learnerId = uuid("learner_id")
    val title = text("title")
    val sourceType = varchar("source_type", 20)
    val storagePath = text("storage_path").nullable()
    val extractedText = text("extracted_text")
    val status = varchar("status", 20)
    val language = text("language").nullable()
    val cefrLevel = varchar("cefr_level", 20).nullable()
    val topics = text("topics").nullable()
    val overviewSummary = text("overview_summary").nullable()
    val overviewModelUsed = text("overview_model_used").nullable()
    val analysisTruncated = bool("analysis_truncated")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

internal object LearningResourceGeneratedContentTable : Table("learning_resource_generated_content") {
    val id = uuid("id")
    val resourceId = uuid("resource_id")
    val goal = varchar("goal", 30)
    val content = text("content")
    val modelUsed = text("model_used")
    val timesServed = integer("times_served")
    val lastServedAt = timestamp("last_served_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
