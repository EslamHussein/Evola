package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A word already taught in one lesson can reappear in another without duplicating the row -
 * [VocabularyItemEntity.lessonId] stays the ORIGIN lesson; this table records every OTHER lesson
 * that reuses it. See [LessonVocabularyView], which every lesson-scoped vocabulary query joins
 * through instead of filtering [VocabularyItemEntity.lessonId] directly. */
@Entity(
    tableName = "lesson_vocabulary_items",
    primaryKeys = ["lesson_id", "vocabulary_item_id"],
    foreignKeys = [
        ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VocabularyItemEntity::class, parentColumns = ["id"], childColumns = ["vocabulary_item_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vocabulary_item_id")],
)
data class LessonVocabularyItemEntity(
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "vocabulary_item_id") val vocabularyItemId: String,
    @ColumnInfo(name = "context_snippet") val contextSnippet: String?,
)

/** Mirrors Vocabulary.sq's `lesson_vocabulary` view - union of every item's origin lesson and
 * every extra (lesson, item) link, so lesson-scoped vocabulary queries never need to distinguish
 * origin from linked appearances. */
@DatabaseView(
    viewName = "lesson_vocabulary",
    value = """
        SELECT lesson_id, id AS vocabulary_item_id FROM vocabulary_items
        UNION
        SELECT lesson_id, vocabulary_item_id FROM lesson_vocabulary_items
    """,
)
data class LessonVocabularyView(
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    @ColumnInfo(name = "vocabulary_item_id") val vocabularyItemId: String,
)
