package evola.database.dao

import androidx.room.ColumnInfo

/** item + this user's progress, one row per lesson item (mirrors Vocabulary.sq's
 * itemsWithProgressByLesson/itemWithProgress column set exactly). */
data class VocabularyItemWithProgress(
    val id: String,
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
    @ColumnInfo(name = "p_status") val status: String,
    @ColumnInfo(name = "p_is_bookmarked") val isBookmarked: Long,
    @ColumnInfo(name = "p_marked_difficult") val markedDifficult: Long,
)

data class ItemStatus(
    @ColumnInfo(name = "item_id") val itemId: String,
    val status: String,
)

data class WordStatus(
    @ColumnInfo(name = "item_id") val itemId: String,
    val status: String,
    @ColumnInfo(name = "incorrect_streak") val incorrectStreak: Long,
)

data class InProgressWord(
    val term: String,
    val status: String,
)

data class DailyVocabCount(
    @ColumnInfo(name = "local_date") val localDate: String?,
    @ColumnInfo(name = "new_words") val newWords: Long?,
    @ColumnInfo(name = "review_words") val reviewWords: Long?,
)

data class VocabExport(
    val id: String,
    val term: String,
    val meaning: String,
    @ColumnInfo(name = "native_meaning") val nativeMeaning: String?,
    @ColumnInfo(name = "example_sentence") val exampleSentence: String?,
    @ColumnInfo(name = "example_sentence_translation") val exampleSentenceTranslation: String?,
)
