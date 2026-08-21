package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import evola.database.entity.GrammarExerciseEntity
import evola.database.entity.GrammarExtractionJobEntity
import evola.database.entity.GrammarProgressEntity
import evola.database.entity.GrammarSessionAnswerEntity
import evola.database.entity.GrammarSessionEntity
import evola.database.entity.GrammarTopicEntity

@Dao
interface GrammarDao {
    @Query("SELECT * FROM grammar_topics WHERE lesson_id = :lessonId ORDER BY created_at ASC")
    suspend fun topicsByLesson(lessonId: String): List<GrammarTopicEntity>

    @Query("SELECT * FROM grammar_topics WHERE id = :id")
    suspend fun topicById(id: String): GrammarTopicEntity?

    @Insert
    suspend fun insertTopic(topic: GrammarTopicEntity)

    @Query("SELECT * FROM grammar_exercises WHERE topic_id = :topicId ORDER BY created_at ASC")
    suspend fun exercisesByTopic(topicId: String): List<GrammarExerciseEntity>

    @Insert
    suspend fun insertExercise(exercise: GrammarExerciseEntity)

    @Query("SELECT * FROM grammar_progress WHERE user_id = :userId AND topic_id = :topicId")
    suspend fun progressForTopic(userId: String, topicId: String): GrammarProgressEntity?

    @Insert
    suspend fun insertTopicProgress(progress: GrammarProgressEntity)

    @Query(
        "UPDATE grammar_progress SET mastery_state = :masteryState, correct_streak = :correctStreak, interval_index = :intervalIndex, next_review_at = :nextReviewAt, last_reviewed_at = :lastReviewedAt WHERE user_id = :userId AND topic_id = :topicId",
    )
    suspend fun updateTopicProgress(
        masteryState: String,
        correctStreak: Long,
        intervalIndex: Long,
        nextReviewAt: Long,
        lastReviewedAt: Long?,
        userId: String,
        topicId: String,
    )

    @Insert
    suspend fun insertSession(session: GrammarSessionEntity)

    @Query("SELECT * FROM grammar_sessions WHERE user_id = :userId AND topic_id = :topicId AND completed_at IS NULL LIMIT 1")
    suspend fun incompleteSessionForTopic(userId: String, topicId: String): GrammarSessionEntity?

    @Query("SELECT * FROM grammar_sessions WHERE id = :id")
    suspend fun sessionById(id: String): GrammarSessionEntity?

    @Query("UPDATE grammar_sessions SET completed_at = :completedAt, accuracy = :accuracy WHERE id = :id")
    suspend fun completeSession(completedAt: Long, accuracy: Double?, id: String)

    @Query("SELECT * FROM grammar_session_answers WHERE session_id = :sessionId")
    suspend fun answersForSession(sessionId: String): List<GrammarSessionAnswerEntity>

    @Query("SELECT * FROM grammar_session_answers WHERE session_id = :sessionId AND exercise_id = :exerciseId")
    suspend fun answerForExercise(sessionId: String, exerciseId: String): GrammarSessionAnswerEntity?

    @Insert
    suspend fun insertSessionAnswer(answer: GrammarSessionAnswerEntity)

    @Query("SELECT * FROM grammar_extraction_jobs WHERE lesson_id = :lessonId")
    suspend fun jobByLesson(lessonId: String): GrammarExtractionJobEntity?

    @Insert
    suspend fun insertJob(job: GrammarExtractionJobEntity)

    @Query("UPDATE grammar_extraction_jobs SET status = :status, error = :error, updated_at = :updatedAt WHERE lesson_id = :lessonId")
    suspend fun updateJobStatus(status: String, error: String?, updatedAt: Long, lessonId: String)
}
