package evola.tutoring.domain

import evola.core.kernel.DialogueTurnId
import evola.core.kernel.LearnerId
import evola.core.kernel.TutoringSessionId
import evola.core.kernel.VocabularyItemId
import java.time.Instant

enum class LearningMode { VOCABULARY, GRAMMAR, SPEAKING, EXAM_PREPARATION, DAILY_CHALLENGE }

enum class SessionStatus { IN_PROGRESS, COMPLETED, ABANDONED }

/**
 * Note: a `sourceResourceId` field (linking a session to a Milestone-2 uploaded resource) is
 * deliberately omitted for now — the Resource Library bounded context doesn't exist in this
 * codebase yet. Adding it back is additive once that module is built.
 */
data class TutoringSession(
    val id: TutoringSessionId,
    val learnerId: LearnerId,
    val mode: LearningMode,
    val focusVocabularyItemId: VocabularyItemId?,
    val focusGrammarTopic: String?,
    val status: SessionStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
) {
    fun complete(now: Instant = Instant.now()): TutoringSession = copy(status = SessionStatus.COMPLETED, completedAt = now)

    companion object {
        fun startVocabularyDrill(
            id: TutoringSessionId,
            learnerId: LearnerId,
            vocabularyItemId: VocabularyItemId,
            now: Instant = Instant.now(),
        ) = TutoringSession(
            id = id, learnerId = learnerId, mode = LearningMode.VOCABULARY,
            focusVocabularyItemId = vocabularyItemId, focusGrammarTopic = null,
            status = SessionStatus.IN_PROGRESS, startedAt = now, completedAt = null,
        )

        fun startGrammarDrill(
            id: TutoringSessionId,
            learnerId: LearnerId,
            grammarTopic: String,
            now: Instant = Instant.now(),
        ) = TutoringSession(
            id = id, learnerId = learnerId, mode = LearningMode.GRAMMAR,
            focusVocabularyItemId = null, focusGrammarTopic = grammarTopic,
            status = SessionStatus.IN_PROGRESS, startedAt = now, completedAt = null,
        )

        fun startSpeakingScenario(
            id: TutoringSessionId,
            learnerId: LearnerId,
            scenarioTitle: String,
            now: Instant = Instant.now(),
        ) = TutoringSession(
            id = id, learnerId = learnerId, mode = LearningMode.SPEAKING,
            focusVocabularyItemId = null, focusGrammarTopic = scenarioTitle,
            status = SessionStatus.IN_PROGRESS, startedAt = now, completedAt = null,
        )
    }
}

enum class TurnRole { PROMPT, LEARNER_ANSWER, FEEDBACK, FOLLOW_UP_PROMPT }

data class DialogueTurn(
    val id: DialogueTurnId,
    val sessionId: TutoringSessionId,
    val turnIndex: Int,
    val role: TurnRole,
    val exerciseKind: ExerciseKind?,
    val content: String,
    val correctAnswer: String?,
    val explanation: String?,
    val wasCorrect: Boolean?,
    val createdAt: Instant,
)
