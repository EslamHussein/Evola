package evola.shared.vocabulary

/** A lesson's own vocabulary item plus this user's current mastery state (01_PRODUCT_SPEC.md §1.8). */
data class VocabularyItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val masteryState: String,
)

/** One question occurrence within a session. `drillType` is one of "typed_recall",
 * "multiple_choice_term_to_meaning", or "multiple_choice_meaning_to_term" - the latter two both
 * count as "multiple choice" per spec, split so the client knows which field to show as the
 * prompt vs. the answer choices. */
data class VocabularySessionItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val drillType: String,
    val choices: List<String> = emptyList(),
) {
    val isMultipleChoice: Boolean get() = drillType.startsWith("multiple_choice")
    val isTermToMeaning: Boolean get() = drillType == "multiple_choice_term_to_meaning"
}

data class VocabularySession(
    val sessionId: String,
    val items: List<VocabularySessionItem>,
    val hasLessonVocabulary: Boolean,
)

data class VocabularyAnswerResult(
    val masteryState: String,
    val nextReviewAt: String,
    val resurfaced: Boolean,
)

data class VocabularySessionSummary(
    val itemsCount: Int,
    val accuracy: Double,
)

interface VocabularyRepository {
    suspend fun startOrResumeSession(accessToken: String, lessonId: String): VocabularySession?
    suspend fun listVocabulary(accessToken: String, lessonId: String): List<VocabularyItem>
    suspend fun answer(accessToken: String, sessionId: String, itemId: String, response: String, correct: Boolean): VocabularyAnswerResult?
    suspend fun complete(accessToken: String, sessionId: String): VocabularySessionSummary?
}
