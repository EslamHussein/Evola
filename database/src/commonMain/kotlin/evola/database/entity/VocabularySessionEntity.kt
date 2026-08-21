package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per study session for a lesson. [lessonId] is null for a category-practice session
 * (Home's Needs practice/Learning/Mastered cards): those pull words across every lesson in the
 * goal, so there's no single lesson to attribute to. [localDate] is the caller's own local
 * calendar date (kotlinx-datetime) - stored on the session itself so Home's per-day activity chart
 * groups by the learner's own day boundary, not UTC's. Null until completed. */
@Entity(
    tableName = "vocabulary_sessions",
    foreignKeys = [
        ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("lesson_id")],
)
data class VocabularySessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String?,
    @ColumnInfo(name = "session_number") val sessionNumber: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "local_date") val localDate: String?,
    @ColumnInfo(name = "new_words_count") val newWordsCount: Long,
    @ColumnInfo(name = "review_words_count") val reviewWordsCount: Long,
    @ColumnInfo(name = "correct_count") val correctCount: Long,
    @ColumnInfo(name = "incorrect_count") val incorrectCount: Long,
)
