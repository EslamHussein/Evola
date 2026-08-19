package evola.shared.grammar

import evola.shared.core.ApiResult
import evola.shared.vocabulary.isTolerantMatch

/** A lesson's own grammar topic plus this user's current mastery state (01_PRODUCT_SPEC.md §1.9). */
data class GrammarTopic(
    val topicId: String,
    val name: String,
    val explanation: String,
    val masteryState: String,
)

/** One exercise within a topic's session. The client self-grades and reports `correct` itself
 * (same trust model Vocabulary used pre-redesign), so [answerKey] and [choices] (multiple_choice
 * only, already shuffled server-side) are both included here. */
data class GrammarExercise(
    val exerciseId: String,
    val type: String,
    val prompt: String,
    val answerKey: String,
    val choices: List<String> = emptyList(),
    val answered: Boolean = false,
) {
    val isMultipleChoice: Boolean get() = type == "multiple_choice"
    val isFillInBlank: Boolean get() = type == "fill_in_blank"

    /** Grades [response] against [answerKey] - exact match for multiple choice (the choice text
     * itself is the answer), tolerant match for fill-in-blank (typos/case/whitespace forgiven),
     * matching [isTolerantMatch]'s use for Vocabulary's own self-graded answers. The single place
     * both drill types should grade through, so multiple choice and fill-in-blank can't drift into
     * inconsistent grading rules. */
    fun grade(response: String): Boolean = if (isMultipleChoice) response == answerKey else isTolerantMatch(answerKey, response)
}

data class GrammarSession(
    val sessionId: String,
    val topicName: String,
    val exercises: List<GrammarExercise>,
)

data class GrammarAnswerResult(
    val masteryState: String,
    val nextReviewAt: String,
)

data class GrammarSessionSummary(
    val exercisesCompleted: Int,
    val accuracy: Double,
)

interface GrammarRepository {
    suspend fun listTopics(lessonId: String): ApiResult<List<GrammarTopic>>
    suspend fun startOrResumeSession(topicId: String): ApiResult<GrammarSession>
    suspend fun answer(sessionId: String, exerciseId: String, response: String, correct: Boolean): ApiResult<GrammarAnswerResult>
    suspend fun complete(sessionId: String, localDate: String): ApiResult<GrammarSessionSummary>
}
