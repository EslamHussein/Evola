package evola.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import evola.database.entity.LessonVocabularyItemEntity
import evola.database.entity.VocabularyItemEntity
import evola.database.entity.VocabularyProgressEntity
import evola.database.entity.VocabularySessionEntity
import evola.database.entity.VocabularySessionQueueEntity

@Dao
interface VocabularyDao {

    // items -------------------------------------------------------------

    @Query("SELECT vi.* FROM vocabulary_items vi JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id WHERE lv.lesson_id = :lessonId ORDER BY vi.created_at ASC")
    suspend fun itemsByLesson(lessonId: String): List<VocabularyItemEntity>

    @Insert
    suspend fun insertItem(item: VocabularyItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkItemToLesson(link: LessonVocabularyItemEntity)

    @Query("DELETE FROM lesson_vocabulary_items WHERE lesson_id = :lessonId AND vocabulary_item_id = :vocabularyItemId")
    suspend fun unlinkItemFromLesson(lessonId: String, vocabularyItemId: String)

    @Query("SELECT * FROM vocabulary_items WHERE id = :id")
    suspend fun itemById(id: String): VocabularyItemEntity?

    @Query("UPDATE vocabulary_items SET term = :term, meaning = :meaning, native_meaning = :nativeMeaning WHERE id = :id")
    suspend fun updateItemContent(term: String, meaning: String, nativeMeaning: String?, id: String)

    @Query("UPDATE vocabulary_items SET ai_note = :aiNote WHERE id = :id")
    suspend fun updateAiNote(aiNote: String?, id: String)

    @Query("DELETE FROM vocabulary_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    // progress ------------------------------------------------------------

    @Query("SELECT * FROM vocabulary_progress WHERE user_id = :userId AND vocabulary_item_id = :vocabularyItemId")
    suspend fun progressForItem(userId: String, vocabularyItemId: String): VocabularyProgressEntity?

    @Insert
    suspend fun insertProgress(progress: VocabularyProgressEntity)

    @Query(
        "UPDATE vocabulary_progress SET status = :status, correct_streak = :correctStreak, incorrect_streak = :incorrectStreak, interval_index = :intervalIndex, next_review_at = :nextReviewAt, last_seen_at = :lastSeenAt WHERE user_id = :userId AND vocabulary_item_id = :vocabularyItemId",
    )
    suspend fun updateProgress(
        status: String,
        correctStreak: Long,
        incorrectStreak: Long,
        intervalIndex: Long,
        nextReviewAt: Long,
        lastSeenAt: Long?,
        userId: String,
        vocabularyItemId: String,
    )

    /** Reword's "Reset progress" - back to unseen, SRS state cleared. Bookmarks/difficulty flags
     * are left untouched. */
    @Query(
        """
        UPDATE vocabulary_progress
        SET status = 'unseen', correct_streak = 0, incorrect_streak = 0, interval_index = 0, next_review_at = 0, last_seen_at = NULL
        WHERE user_id = :userId AND vocabulary_item_id IN (SELECT id FROM vocabulary_items WHERE lesson_id = :lessonId)
        """,
    )
    suspend fun resetLessonProgress(userId: String, lessonId: String)

    @Query(
        """
        UPDATE vocabulary_progress
        SET status = 'unseen', correct_streak = 0, incorrect_streak = 0, interval_index = 0, next_review_at = 0, last_seen_at = NULL
        WHERE user_id = :userId
        """,
    )
    suspend fun resetAllProgress(userId: String)

    @Query("SELECT COUNT(*) FROM vocabulary_progress WHERE user_id = :userId AND status = 'mastered'")
    suspend fun masteredCountForUser(userId: String): Long

    @Query("UPDATE vocabulary_progress SET is_bookmarked = :bookmarked WHERE user_id = :userId AND vocabulary_item_id = :vocabularyItemId")
    suspend fun setBookmarked(bookmarked: Long, userId: String, vocabularyItemId: String)

    @Query("UPDATE vocabulary_progress SET marked_difficult = :markedDifficult WHERE user_id = :userId AND vocabulary_item_id = :vocabularyItemId")
    suspend fun setMarkedDifficult(markedDifficult: Long, userId: String, vocabularyItemId: String)

    @Query(
        """
        SELECT vi.*, vp.status AS p_status, vp.is_bookmarked AS p_is_bookmarked, vp.marked_difficult AS p_marked_difficult
        FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE lv.lesson_id = :lessonId AND vp.user_id = :userId
        ORDER BY vi.created_at ASC
        """,
    )
    suspend fun itemsWithProgressByLesson(lessonId: String, userId: String): List<VocabularyItemWithProgress>

    @Query(
        """
        SELECT vi.*, vp.status AS p_status, vp.is_bookmarked AS p_is_bookmarked, vp.marked_difficult AS p_marked_difficult
        FROM vocabulary_items vi
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE vi.id = :id AND vp.user_id = :userId
        """,
    )
    suspend fun itemWithProgress(id: String, userId: String): VocabularyItemWithProgress?

    @Query(
        """
        SELECT vi.id AS item_id, COALESCE(vp.status, 'unseen') AS status
        FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        LEFT JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE lv.lesson_id = :lessonId
        """,
    )
    suspend fun statusByLesson(userId: String, lessonId: String): List<ItemStatus>

    @Query(
        """
        SELECT DISTINCT vi.id AS item_id, COALESCE(vp.status, 'unseen') AS status, COALESCE(vp.incorrect_streak, 0) AS incorrect_streak
        FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN curriculum_lessons l ON l.id = lv.lesson_id
        LEFT JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE l.goal_id = :goalId
        """,
    )
    suspend fun wordStatusesByGoal(userId: String, goalId: String): List<WordStatus>

    @Query(
        """
        SELECT DISTINCT vi.term AS term, vp.status AS status
        FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN curriculum_lessons l ON l.id = lv.lesson_id
        JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE l.goal_id = :goalId AND vp.status IN ('introduced', 'learning', 'review')
        """,
    )
    suspend fun inProgressWordsByGoal(userId: String, goalId: String): List<InProgressWord>

    @Query(
        """
        SELECT DISTINCT vi.id FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN curriculum_lessons l ON l.id = lv.lesson_id
        LEFT JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE l.goal_id = :goalId AND (vp.id IS NULL OR vp.status = 'unseen')
        ORDER BY vi.created_at ASC LIMIT :limit
        """,
    )
    suspend fun newItemsForGoal(userId: String, goalId: String, limit: Long): List<String>

    @Query(
        """
        SELECT DISTINCT vi.id FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN curriculum_lessons l ON l.id = lv.lesson_id
        JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE l.goal_id = :goalId AND vp.status NOT IN ('unseen', 'introduced') AND vp.next_review_at <= :now
        ORDER BY vp.next_review_at ASC LIMIT :limit
        """,
    )
    suspend fun dueItemsForGoal(userId: String, goalId: String, now: Long, limit: Long): List<String>

    // Option B cascade support ----------------------------------------------

    @Query("SELECT id FROM vocabulary_items WHERE lesson_id = :lessonId")
    suspend fun originatingItemsForLesson(lessonId: String): List<String>

    @Query(
        """
        SELECT lvi.lesson_id FROM lesson_vocabulary_items lvi
        JOIN curriculum_lessons l ON l.id = lvi.lesson_id
        WHERE lvi.vocabulary_item_id = :vocabularyItemId
        LIMIT 1
        """,
    )
    suspend fun survivingCurriculumLinkForItem(vocabularyItemId: String): String?

    @Query("UPDATE vocabulary_items SET lesson_id = :lessonId WHERE id = :id")
    suspend fun reassignItemOrigin(lessonId: String, id: String)

    // Session queue assembly draws --------------------------------------------

    @Query(
        """
        SELECT vi.id FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        LEFT JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE lv.lesson_id = :lessonId AND (vp.id IS NULL OR vp.status = 'unseen')
        ORDER BY vi.created_at ASC LIMIT :limit
        """,
    )
    suspend fun newItemsForLesson(userId: String, lessonId: String, limit: Long): List<String>

    @Query(
        """
        SELECT vi.id FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE lv.lesson_id = :lessonId AND vp.user_id = :userId AND vp.status NOT IN ('unseen', 'introduced') AND vp.next_review_at <= :now
        ORDER BY vp.next_review_at ASC LIMIT :limit
        """,
    )
    suspend fun dueItemsInLesson(lessonId: String, userId: String, now: Long, limit: Long): List<String>

    @Query(
        """
        SELECT vi.id FROM vocabulary_items vi
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE vi.id NOT IN (SELECT vocabulary_item_id FROM lesson_vocabulary WHERE lesson_id = :lessonId)
        AND vp.user_id = :userId AND vp.status NOT IN ('unseen', 'introduced') AND vp.next_review_at <= :now
        ORDER BY vp.next_review_at ASC LIMIT :limit
        """,
    )
    suspend fun dueItemsElsewhere(lessonId: String, userId: String, now: Long, limit: Long): List<String>

    @Query(
        """
        SELECT vi.id FROM vocabulary_items vi
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE vp.user_id = :userId AND vp.status = 'mastered'
        LIMIT :limit
        """,
    )
    suspend fun masteredItems(userId: String, limit: Long): List<String>

    @Query("SELECT COUNT(*) FROM vocabulary_progress WHERE user_id = :userId AND status NOT IN ('unseen', 'introduced') AND next_review_at <= :now")
    suspend fun dueCountForUser(userId: String, now: Long): Long

    @Query(
        """
        SELECT COUNT(DISTINCT vi.id) FROM vocabulary_items vi
        JOIN lesson_vocabulary lv ON lv.vocabulary_item_id = vi.id
        JOIN curriculum_lessons l ON l.id = lv.lesson_id
        JOIN vocabulary_progress vp ON vp.vocabulary_item_id = vi.id AND vp.user_id = :userId
        WHERE l.goal_id = :goalId AND vp.status NOT IN ('unseen', 'introduced') AND vp.next_review_at <= :now
        """,
    )
    suspend fun dueCountForGoal(userId: String, goalId: String, now: Long): Long

    @Query(
        """
        SELECT vi.id, vi.term, vi.meaning, vi.native_meaning, vi.example_sentence, vi.example_sentence_translation
        FROM vocabulary_items vi
        JOIN vocabulary_progress vp ON vi.id = vp.vocabulary_item_id
        WHERE vp.user_id = :userId
        """,
    )
    suspend fun allUserVocab(userId: String): List<VocabExport>

    // Sessions -----------------------------------------------------------------

    @Query("SELECT * FROM vocabulary_sessions WHERE user_id = :userId AND lesson_id = :lessonId AND completed_at IS NULL LIMIT 1")
    suspend fun incompleteSessionForLesson(userId: String, lessonId: String): VocabularySessionEntity?

    @Query("SELECT * FROM vocabulary_sessions WHERE id = :id AND user_id = :userId")
    suspend fun sessionById(id: String, userId: String): VocabularySessionEntity?

    @Query("SELECT session_number FROM vocabulary_sessions WHERE user_id = :userId AND lesson_id = :lessonId ORDER BY session_number DESC LIMIT 1")
    suspend fun maxSessionNumber(userId: String, lessonId: String): Long?

    @Insert
    suspend fun insertSession(session: VocabularySessionEntity)

    @Query("UPDATE vocabulary_sessions SET correct_count = correct_count + :correct, incorrect_count = incorrect_count + :incorrect WHERE id = :id")
    suspend fun incrementSessionCounters(correct: Long, incorrect: Long, id: String)

    @Query("UPDATE vocabulary_sessions SET completed_at = :completedAt, local_date = :localDate WHERE id = :id")
    suspend fun completeSession(completedAt: Long, localDate: String, id: String)

    /** Per-day new/review counts across every completed session, for Home's stacked activity
     * chart. Grouped by the caller's own stored local_date, never derived from completed_at. */
    @Query(
        """
        SELECT local_date, SUM(new_words_count) AS new_words, SUM(review_words_count) AS review_words
        FROM vocabulary_sessions
        WHERE user_id = :userId AND completed_at IS NOT NULL AND local_date >= :since
        GROUP BY local_date
        """,
    )
    suspend fun dailyCounts(userId: String, since: String): List<DailyVocabCount>

    // Queue ---------------------------------------------------------------------

    @Query("SELECT * FROM vocabulary_session_queue WHERE session_id = :sessionId ORDER BY position ASC")
    suspend fun queueForSession(sessionId: String): List<VocabularySessionQueueEntity>

    @Query("SELECT * FROM vocabulary_session_queue WHERE session_id = :sessionId AND answered_at IS NULL ORDER BY position ASC LIMIT 1")
    suspend fun nextQueueItem(sessionId: String): VocabularySessionQueueEntity?

    @Query("SELECT MAX(position) FROM vocabulary_session_queue WHERE session_id = :sessionId")
    suspend fun maxQueuePosition(sessionId: String): Long?

    @Insert
    suspend fun insertQueueItem(item: VocabularySessionQueueEntity)

    @Query("UPDATE vocabulary_session_queue SET answered_at = :answeredAt, correct = :correct, user_response = :userResponse WHERE id = :id")
    suspend fun answerQueueItem(answeredAt: Long, correct: Long?, userResponse: String?, id: String)

    /** Reword's per-card undo: reverts a graded queue row back to unanswered so it becomes the
     * current card again (nextQueueItem picks the lowest-position unanswered row). */
    @Query("UPDATE vocabulary_session_queue SET answered_at = NULL, correct = NULL, user_response = NULL WHERE id = :id")
    suspend fun unanswerQueueItem(id: String)

    @Query("DELETE FROM vocabulary_session_queue WHERE id = :id")
    suspend fun deleteQueueItem(id: String)

    @Query("SELECT COUNT(DISTINCT vocabulary_item_id) FROM vocabulary_session_queue WHERE session_id = :sessionId")
    suspend fun wordsPracticedInSession(sessionId: String): Long

    // Backup.sq -------------------------------------------------------------

    @Query(
        """
        SELECT vocabulary_items.* FROM vocabulary_items
        JOIN lessons ON lessons.id = vocabulary_items.lesson_id
        JOIN materials ON materials.id = lessons.material_id
        WHERE materials.user_id = :userId
        """,
    )
    suspend fun selectAllItemsForUser(userId: String): List<VocabularyItemEntity>

    @Query(
        """
        DELETE FROM vocabulary_items WHERE lesson_id IN (
            SELECT lessons.id FROM lessons JOIN materials ON materials.id = lessons.material_id WHERE materials.user_id = :userId
        )
        """,
    )
    suspend fun deleteAllItemsForUser(userId: String)

    @Query("SELECT * FROM vocabulary_progress WHERE user_id = :userId")
    suspend fun selectAllProgressForUser(userId: String): List<VocabularyProgressEntity>

    @Query("DELETE FROM vocabulary_progress WHERE user_id = :userId")
    suspend fun deleteAllProgressForUser(userId: String)
}
