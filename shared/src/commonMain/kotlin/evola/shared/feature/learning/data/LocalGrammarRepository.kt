package evola.shared.feature.learning.data

import evola.database.AppDatabase
import evola.database.entity.GrammarSessionAnswerEntity
import evola.database.entity.GrammarSessionEntity
import evola.shared.core.common.ApiResult
import evola.shared.core.common.DataError
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.LOCAL_USER
import evola.shared.core.common.decodeStringList
import evola.shared.core.common.newId
import evola.shared.core.common.nowMillis
import evola.shared.feature.learning.domain.GrammarAnswerResult
import evola.shared.feature.learning.domain.GrammarExercise
import evola.shared.feature.learning.domain.GrammarRepository
import evola.shared.feature.learning.domain.GrammarSession
import evola.shared.feature.learning.domain.GrammarSessionSummary
import evola.shared.feature.learning.domain.GrammarTopic
import evola.shared.core.common.srs.MasterySrs
import kotlin.time.Instant
import kotlin.random.Random

private const val MILLIS_PER_DAY = 86_400_000L

/** On-device Grammar Learning — ports the retired server `GrammarService` (per-topic flat session,
 * client self-grades, two-consecutive-correct mastery rule). Ownership checks collapse to "does a
 * grammar_progress row exist for (LOCAL_USER, topic)" exactly as the server did. */
class LocalGrammarRepository(private val db: AppDatabase) : GrammarRepository {

    override suspend fun listTopics(lessonId: String): ApiResult<List<GrammarTopic>> {
        db.lessonDao().selectById(lessonId)
            ?: return fail(404, "Lesson not found", "lessonId=$lessonId")
        val topics = db.grammarDao().topicsByLesson(lessonId).mapNotNull { topic ->
            val progress = db.grammarDao().progressForTopic(LOCAL_USER, topic.id)
                ?: return@mapNotNull null
            GrammarTopic(topic.id, topic.name, topic.explanation, progress.masteryState)
        }
        return ApiResult.Success(topics)
    }

    override suspend fun startOrResumeSession(topicId: String): ApiResult<GrammarSession> {
        db.grammarDao().progressForTopic(LOCAL_USER, topicId)
            ?: return fail(404, "Topic not found", "topicId=$topicId")
        val topic = db.grammarDao().topicById(topicId)
            ?: return fail(404, "Topic not found", "topicId=$topicId")

        val existing = db.grammarDao().incompleteSessionForTopic(LOCAL_USER, topicId)
        val sessionId = existing?.id ?: newId().also {
            db.grammarDao().insertSession(GrammarSessionEntity(it, LOCAL_USER, topicId, nowMillis(), null, null))
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
        val session = db.grammarDao().sessionById(sessionId)
            ?.takeIf { it.userId == LOCAL_USER }
            ?: return fail(404, "Session not found", "sessionId=$sessionId")
        val topicId = session.topicId

        // Idempotency: a retried answer returns the stored snapshot without re-applying MasterySrs.
        db.grammarDao().answerForExercise(sessionId, exerciseId)?.let { prior ->
            return ApiResult.Success(GrammarAnswerResult(prior.masteryStateAfter, isoOf(prior.nextReviewAtAfter)))
        }

        val progress = db.grammarDao().progressForTopic(LOCAL_USER, topicId)
            ?: return fail(404, "Topic progress missing", "sessionId=$sessionId topicId=$topicId")

        val currentState = MasterySrs.State(progress.masteryState, progress.intervalIndex.toInt(), progress.correctStreak.toInt())
        val nextState = when {
            !correct -> MasterySrs.onIncorrect(currentState)
            currentState.correctStreak % 2 == 0 -> MasterySrs.onPartialCorrect(currentState)
            else -> MasterySrs.onCorrect(currentState)
        }
        val now = nowMillis()
        val nextReviewAt = now + MasterySrs.intervalDaysFor(nextState.intervalIndex) * MILLIS_PER_DAY

        db.grammarDao().updateTopicProgress(
            nextState.masteryState, nextState.correctStreak.toLong(), nextState.intervalIndex.toLong(),
            nextReviewAt, now, LOCAL_USER, topicId,
        )
        db.grammarDao().insertSessionAnswer(
            GrammarSessionAnswerEntity(
                newId(), sessionId, exerciseId, response, if (correct) 1L else 0L,
                nextState.masteryState, nextReviewAt, now,
            ),
        )

        return ApiResult.Success(GrammarAnswerResult(nextState.masteryState, isoOf(nextReviewAt)))
    }

    override suspend fun complete(sessionId: String, localDate: String): ApiResult<GrammarSessionSummary> {
        db.grammarDao().sessionById(sessionId)
            ?.takeIf { it.userId == LOCAL_USER }
            ?: return fail(404, "Session not found", "sessionId=$sessionId")

        val answers = db.grammarDao().answersForSession(sessionId)
        val completed = answers.size
        val correctCount = answers.count { it.correct == 1L }
        val accuracy = if (completed > 0) (correctCount.toDouble() / completed) * 100.0 else 0.0

        db.grammarDao().completeSession(nowMillis(), accuracy, sessionId)
        db.activityDao().upsert(evola.database.entity.DailyActivityEntity(newId(), LOCAL_USER, localDate, 1L))

        return ApiResult.Success(GrammarSessionSummary(completed, accuracy))
    }

    /** This topic's exercise set is immutable; "this session's exercises" is derived from
     * grammar_exercises, left-joined against this session's answers to mark already-graded ones.
     * Multiple-choice choices shuffle deterministically (seeded by session+exercise) so a resumed
     * session shows identical order. */
    private suspend fun buildExercises(sessionId: String, topicId: String): List<GrammarExercise> {
        val answeredIds = db.grammarDao().answersForSession(sessionId).map { it.exerciseId }.toSet()
        return db.grammarDao().exercisesByTopic(topicId).map { ex ->
            val distractors = decodeStringList(ex.distractors)
            val choices = if (ex.type == "multiple_choice" && distractors.isNotEmpty()) {
                (distractors + ex.answerKey).shuffled(Random(sessionId.hashCode() + ex.id.hashCode()))
            } else {
                emptyList()
            }
            GrammarExercise(
                exerciseId = ex.id,
                type = ex.type,
                prompt = ex.prompt,
                answerKey = ex.answerKey,
                choices = choices,
                answered = ex.id in answeredIds,
            )
        }
    }

    private fun isoOf(millis: Long): String = Instant.fromEpochMilliseconds(millis).toString()

    private fun fail(code: Int, message: String, context: String): ApiResult.Failure {
        EvolaLog.d("grammar", "$message ($context)")
        return ApiResult.Failure(DataError.Http(code, message))
    }
}
