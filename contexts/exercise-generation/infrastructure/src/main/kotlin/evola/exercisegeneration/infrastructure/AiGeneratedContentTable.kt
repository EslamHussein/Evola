package evola.exercisegeneration.infrastructure

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

internal object AiGeneratedContentTable : Table("ai_generated_content") {
    val id = uuid("id")
    val vocabularyItemId = uuid("vocabulary_item_id")
    val exerciseType = varchar("exercise_type", 50)
    val content = text("content")
    val modelUsed = varchar("model_used", 100)
    val timesServed = integer("times_served")
    val lastServedAt = timestamp("last_served_at").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
