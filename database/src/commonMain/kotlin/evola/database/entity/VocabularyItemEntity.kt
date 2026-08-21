package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary_items",
    foreignKeys = [
        ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("lesson_id")],
)
data class VocabularyItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    val term: String,
    val meaning: String,
    val gender: String?,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String?,
    @ColumnInfo(name = "part_of_speech") val partOfSpeech: String?,
    val plural: String?,
    @ColumnInfo(name = "grammatical_case") val grammaticalCase: String?,
    @ColumnInfo(name = "example_sentence_translation") val exampleSentenceTranslation: String?,
    @ColumnInfo(name = "native_meaning") val nativeMeaning: String?,
    @ColumnInfo(name = "ipa_pronunciation") val ipaPronunciation: String?,
    @ColumnInfo(name = "related_words") val relatedWords: String?,
    @ColumnInfo(name = "difficulty_rating") val difficultyRating: String?,
    @ColumnInfo(name = "frequency_rating") val frequencyRating: String?,
    @ColumnInfo(name = "memory_tip") val memoryTip: String?,
    @ColumnInfo(name = "grammar_note") val grammarNote: String?,
    @ColumnInfo(name = "ai_note") val aiNote: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
