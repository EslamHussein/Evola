package evola.shared.local

import evola.shared.core.ApiResult
import evola.shared.core.DataError
import evola.shared.db.EvolaDatabase
import evola.shared.grammar.GrammarAnswerResult
import evola.shared.grammar.GrammarExercise
import evola.shared.grammar.GrammarRepository
import evola.shared.grammar.GrammarSession
import evola.shared.grammar.GrammarSessionSummary
import evola.shared.grammar.GrammarTopic
import evola.shared.srs.MasterySrs
import kotlinx.datetime.Instant
import kotlin.random.Random

private const val MILLIS_PER_DAY = 86_400_000L

/** On-device Grammar Learning — ports the retired server `GrammarService` (per-topic flat session,
 * client self-grades, two-consecutive-correct mastery rule). Ownership checks collapse to "does a
 * grammar_progress row exist for (LOCAL_USER, topic)" exactly as the server did. */
class LocalGrammarRepository(private val db: EvolaDatabase) : GrammarRepository {

    override suspend fun listTopics(lessonId: String): ApiResult<List<GrammarTopic>> {
        db.lessonsQueries.selectById(lessonId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Lesson not found"))
        val topics = db.grammarQueries.topicsByLesson(lessonId).executeAsList().mapNotNull { topic ->
            val progress = db.grammarQueries.progressForTopic(LOCAL_USER, topic.id).executeAsOneOrNull()
                ?: return@mapNotNull null
            GrammarTopic(topic.id, topic.name, topic.explanation, progress.mastery_state)
        }
        return ApiResult.Success(topics)
    }

    override suspend fun startOrResumeSession(topicId: String): ApiResult<GrammarSession> {
        db.grammarQueries.progressForTopic(LOCAL_USER, topicId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Topic not found"))
        val topic = db.grammarQueries.topicById(topicId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Topic not found"))

        val existing = db.grammarQueries.incompleteSessionForTopic(LOCAL_USER, topicId).executeAsOneOrNull()
        val sessionId = existing?.id ?: newId().also {
            db.grammarQueries.insertSession(it, LOCAL_USER, topicId, nowMillis())
        }

        return ApiResult.Success(
            GrammarSession(
                sessionId = sessionId,
                topicName = topic.name,
                exercises = buildExercises(sessionId, topicId),
            ),
        )
    }

    override suspend fun answer(sessionId: String, exerciseId: String, response: String, correct: Boolean): ApiResult<GrammarAnswerResult> {
        val session = db.grammarQueries.sessionById(sessionId).executeAsOneOrNull()
            ?.takeIf { it.user_id == LOCAL_USER }
            ?: return ApiResult.Failure(DataError.Http(404, "Session not found"))
        val topicId = session.topic_id

        // Idempotency: a retried answer returns the stored snapshot without re-applying MasterySrs.
        db.grammarQueries.answerForExercise(sessionId, exerciseId).executeAsOneOrNull()?.let { prior ->
            return ApiResult.Success(GrammarAnswerResult(prior.mastery_state_after, isoOf(prior.next_review_at_after)))
        }

        val progress = db.grammarQueries.progressForTopic(LOCAL_USER, topicId).executeAsOneOrNull()
            ?: return ApiResult.Failure(DataError.Http(404, "Topic progress missing"))

        val currentState = MasterySrs.State(progress.mastery_state, progress.interval_index.toInt(), progress.correct_streak.toInt())
        val nextState = when {
            !correct -> MasterySrs.onIncorrect(currentState)
            currentState.correctStreak % 2 == 0 -> MasterySrs.onPartialCorrect(currentState)
            else -> MasterySrs.onCorrect(currentState)
        }
        val now = nowMillis()
        val nextReviewAt = now + MasterySrs.intervalDaysFor(nextState.intervalIndex) * MILLIS_PER_DAY

        db.grammarQueries.updateTopicProgress(
            nextState.masteryState, nextState.correctStreak.toLong(), nextState.intervalIndex.toLong(),
            nextReviewAt, now, LOCAL_USER, topicId,
        )
        db.grammarQueries.insertSessionAnswer(
            newId(), sessionId, exerciseId, response, if (correct) 1L else 0L,
            nextState.masteryState, nextReviewAt, now,
        )

        return ApiResult.Success(GrammarAnswerResult(nextState.masteryState, isoOf(nextReviewAt)))
    }

    override suspend fun complete(sessionId: String, localDate: String): ApiResult<GrammarSessionSummary> {
        db.grammarQueries.sessionById(sessionId).executeAsOneOrNull()
            ?.takeIf { it.user_id == LOCAL_USER }
            ?: return ApiResult.Failure(DataError.Http(404, "Session not found"))

        val answers = db.grammarQueries.answersForSession(sessionId).executeAsList()
        val completed = answers.size
        val correctCount = answers.count { it.correct == 1L }
        val accuracy = if (completed > 0) (correctCount.toDouble() / completed) * 100.0 else 0.0

        db.grammarQueries.completeSession(nowMillis(), accuracy, sessionId)
        db.activityQueries.upsert(newId(), LOCAL_USER, localDate)

        return ApiResult.Success(GrammarSessionSummary(completed, accuracy))
    }

    /** This topic's exercise set is immutable; "this session's exercises" is derived from
     * grammar_exercises, left-joined against this session's answers to mark already-graded ones.
     * Multiple-choice choices shuffle deterministically (seeded by session+exercise) so a resumed
     * session shows identical order. */
    private fun buildExercises(sessionId: String, topicId: String): List<GrammarExercise> {
        val answeredIds = db.grammarQueries.answersForSession(sessionId).executeAsList().map { it.exercise_id }.toSet()
        return db.grammarQueries.exercisesByTopic(topicId).executeAsList().map { ex ->
            val distractors = decodeStringList(ex.distractors)
            val choices = if (ex.type == "multiple_choice" && distractors.isNotEmpty()) {
                (distractors + ex.answer_key).shuffled(Random(sessionId.hashCode() + ex.id.hashCode()))
            } else {
                emptyList()
            }
            GrammarExercise(
                exerciseId = ex.id,
                type = ex.type,
                prompt = ex.prompt,
                answerKey = ex.answer_key,
                choices = choices,
                answered = ex.id in answeredIds,
            )
        }
    }

    private fun isoOf(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()
}
