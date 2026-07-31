package evola.tutoring.application

import evola.core.kernel.LearnerId
import evola.core.kernel.LearningSessionRunId
import evola.core.kernel.TutoringSessionId
import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.MatchPair
import evola.tutoring.domain.DailySessionPlan
import evola.tutoring.domain.DialogueTurn
import evola.tutoring.domain.LearningMode
import evola.tutoring.domain.LearningSessionRun
import evola.tutoring.domain.TutoringSession
import java.time.LocalDate

interface TutoringSessionRepository {
    suspend fun findById(id: TutoringSessionId): TutoringSession?
    suspend fun save(session: TutoringSession)
}

interface DialogueTurnRepository {
    suspend fun findBySession(sessionId: TutoringSessionId): List<DialogueTurn>
    suspend fun append(turn: DialogueTurn)
    suspend fun mostFrequentWrongGrammarTopic(learnerId: LearnerId): String?
}

interface TutoringWordContentCache {
    suspend fun find(vocabularyItemId: VocabularyItemId, kind: String, difficultyTier: String?): CachedPracticeContent?
    suspend fun store(vocabularyItemId: VocabularyItemId, kind: String, difficultyTier: String?, content: CachedPracticeContent)
}

interface TutoringGrammarContentCache {
    suspend fun find(grammarTopic: String, difficultyTier: String): CachedPracticeContent?
    suspend fun store(grammarTopic: String, difficultyTier: String, content: CachedPracticeContent)
}

data class CachedPracticeContent(
    val promptText: String,
    val correctAnswer: String,
    val hint: String?,
    val explanation: String?,
    val options: List<String>? = null,
    val matchPairs: List<MatchPair>? = null,
    val modelUsed: String,
)

interface LearnerTutoringProfileRepository {
    suspend fun getActiveMode(learnerId: LearnerId): LearningMode?
    suspend fun setActiveMode(learnerId: LearnerId, mode: LearningMode)
}

interface DailySessionPlanRepository {
    suspend fun findByLearnerAndDate(learnerId: LearnerId, date: LocalDate): DailySessionPlan?
    suspend fun save(plan: DailySessionPlan)
}

interface LearningSessionRunRepository {
    suspend fun findById(id: LearningSessionRunId): LearningSessionRun?
    suspend fun save(run: LearningSessionRun)
}
