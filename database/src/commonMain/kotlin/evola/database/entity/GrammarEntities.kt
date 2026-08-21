package evola.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grammar_topics",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lesson_id")],
)
data class GrammarTopicEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    val name: String,
    val explanation: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "grammar_exercises",
    foreignKeys = [ForeignKey(entity = GrammarTopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("topic_id")],
)
data class GrammarExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    val type: String,
    val prompt: String,
    @ColumnInfo(name = "answer_key") val answerKey: String,
    val distractors: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "grammar_progress",
    foreignKeys = [ForeignKey(entity = GrammarTopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("topic_id")],
)
data class GrammarProgressEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "mastery_state") val masteryState: String,
    @ColumnInfo(name = "correct_streak") val correctStreak: Long,
    @ColumnInfo(name = "interval_index") val intervalIndex: Long,
    @ColumnInfo(name = "next_review_at") val nextReviewAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long?,
)

@Entity(
    tableName = "grammar_sessions",
    foreignKeys = [ForeignKey(entity = GrammarTopicEntity::class, parentColumns = ["id"], childColumns = ["topic_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("topic_id")],
)
data class GrammarSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    val accuracy: Double?,
)

@Entity(
    tableName = "grammar_session_answers",
    foreignKeys = [
        ForeignKey(entity = GrammarSessionEntity::class, parentColumns = ["id"], childColumns = ["session_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = GrammarExerciseEntity::class, parentColumns = ["id"], childColumns = ["exercise_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class GrammarSessionAnswerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    val response: String?,
    val correct: Long,
    @ColumnInfo(name = "mastery_state_after") val masteryStateAfter: String,
    @ColumnInfo(name = "next_review_at_after") val nextReviewAtAfter: Long,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
)

@Entity(
    tableName = "grammar_extraction_jobs",
    foreignKeys = [ForeignKey(entity = LessonEntity::class, parentColumns = ["id"], childColumns = ["lesson_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("lesson_id", unique = true)],
)
data class GrammarExtractionJobEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "lesson_id") val lessonId: String,
    val status: String,
    val error: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
