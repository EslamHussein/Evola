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
 * "multiple_choice_term_to_meaning", "multiple_choice_meaning_to_term", or "fill_blank" - the
 * multiple-choice pair both count as "multiple choice" per spec, split so the client knows which
 * field to show as the prompt vs. the answer choices. "fill_blank" (a word typed into its own
 * example sentence with a blank in place of the term) is only assembled server-side for items
 * whose extraction populated the sentence/part-of-speech/translation fields it needs - items
 * extracted before that shipped simply never get this drill type. */
data class VocabularySessionItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val drillType: String,
    val choices: List<String> = emptyList(),
    val sentenceWithBlank: String? = null,
    val partOfSpeech: String? = null,
    val grammaticalCase: String? = null,
    val sentenceTranslation: String? = null,
) {
    val isMultipleChoice: Boolean get() = drillType.startsWith("multiple_choice")
    val isTermToMeaning: Boolean get() = drillType == "multiple_choice_term_to_meaning"
    val isFillBlank: Boolean get() = drillType == "fill_blank"
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
