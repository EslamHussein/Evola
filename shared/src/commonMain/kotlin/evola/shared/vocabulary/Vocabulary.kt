package evola.shared.vocabulary

import evola.shared.core.ApiResult

/** A lesson's own vocabulary item plus this user's current SRS status (Lingvist-style flat-queue
 * engine). [status] is one of [VocabularySrs.STATUSES]. */
data class VocabularyItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val partOfSpeech: String? = null,
    val plural: String? = null,
    val status: String,
    val nativeMeaning: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val isBookmarked: Boolean = false,
    val markedDifficult: Boolean = false,
)

/** The five card types the session queue can hand back. A new word walks its own difficulty ladder
 * ([Intro] -> [Recognition] -> [WordBank] -> [Hint] -> [Blind]); a review-due word skips straight to
 * [Blind]. No answer/term field is ever exposed before grading - the reveal (correct term/choice +
 * completed sentence) only comes back from [VocabularyRepository.submitChoice]/[submitTyped]'s
 * result. */
sealed interface VocabularyCard {
    val itemId: String

    /** Shown once, the first time a word is presented. No recall required - "Got it" proceeds
     * straight to this same word's [Recognition] card. */
    data class Intro(
        override val itemId: String,
        val term: String,
        val gender: String?,
        val partOfSpeech: String?,
        val plural: String?,
        val ipaPronunciation: String?,
        val meaning: String,
        val exampleSentence: String?,
        val exampleSentenceTranslation: String?,
        val grammarNote: String?,
        val relatedWords: List<String>,
        val difficultyRating: String?,
        val frequencyRating: String?,
        val memoryTip: String?,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
        /** null until the learner taps "AI explain"; populated (and persisted) by
         * [VocabularyRepository.explainItem] on first request. */
        val aiExplanation: String? = null,
    ) : VocabularyCard

    /** Ladder step 1 (graded): the term is shown, the learner picks its correct translation from
     * [choices] (4 options, persisted so a resumed session doesn't reshuffle). Submitted via
     * [VocabularyRepository.submitChoice]. */
    data class Recognition(
        override val itemId: String,
        val term: String,
        val choices: List<String>,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
    ) : VocabularyCard

    /** Ladder step 2 (graded): a blanked sentence, the learner taps the correct German term from
     * [choices] instead of typing. Submitted via [VocabularyRepository.submitChoice]. */
    data class WordBank(
        override val itemId: String,
        val sentenceWithBlank: String,
        val sentenceTranslation: String?,
        val grammarNote: String?,
        val choices: List<String>,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
    ) : VocabularyCard

    /** Ladder step 3 (graded): the learner's native-language meaning is the prompt (no German
     * sentence context), typed recall of the German term, input starts pre-filled with
     * [hintPrefix] (the first half of the term). Submitted via [VocabularyRepository.submitTyped]. */
    data class Hint(
        override val itemId: String,
        val meaning: String,
        val grammarNote: String?,
        val hintPrefix: String,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
    ) : VocabularyCard

    /** Ladder step 4 (graded, final) for new words, and the *only* step for due-for-review words:
     * the native-language meaning is the prompt, typed from scratch, no scaffolding. Submitted via
     * [VocabularyRepository.submitTyped]. */
    data class Blind(
        override val itemId: String,
        val meaning: String,
        val grammarNote: String?,
        val isBookmarked: Boolean,
        val markedDifficult: Boolean,
    ) : VocabularyCard
}

/** Current position in the session queue. Exiting mid-card is always safe - the queue durably
 * tracks position, so [VocabularyRepository.startOrResumeSession] always resumes exactly where the
 * user left off. [origin] is "new" (walking its own difficulty ladder), "due_review" (skipped
 * straight to Blind), or "repeat" (re-queued after a wrong answer) - drives whether the UI shows a
 * per-word ladder step label or a plain "Review" pill. */
data class VocabularySessionState(
    val sessionId: String,
    val sessionNumber: Int,
    val cardsCompleted: Int,
    val cardsRemaining: Int,
    val card: VocabularyCard,
    val origin: String,
    /** 1-based position of the current word among the session's distinct words (not ladder
     * cards - a new word spans several cards but counts as one step here), and the session's
     * total distinct word count. Drives a "word 3 of 5" readout. */
    val wordIndex: Int,
    val totalWords: Int,
)

/** [correct] is null for Intro (never graded - "Got it" always proceeds). [correctAnswer] is the
 * correct translation (Recognition), German term (WordBank/Hint/Blind), populated regardless of
 * right/wrong so the UI can always reveal it. [completedSentence] is only populated for the
 * sentence-based steps (WordBank/Hint/Blind). [next] is null once the queue is exhausted - the
 * caller should then call [VocabularyRepository.complete]. */
data class VocabularyAnswerResult(
    val correct: Boolean?,
    val correctAnswer: String? = null,
    val completedSentence: String? = null,
    val next: VocabularySessionState?,
)

data class VocabularySessionSummary(
    val sessionNumber: Int,
    val wordsLearned: Int,
    val accuracy: Double,
    val timeSeconds: Long,
    val newWordsCount: Int,
    val reviewWordsCount: Int,
)

interface VocabularyRepository {
    suspend fun startOrResumeSession(lessonId: String): ApiResult<VocabularySessionState>
    suspend fun listVocabulary(lessonId: String): ApiResult<List<VocabularyItem>>
    suspend fun submitIntro(sessionId: String, itemId: String): ApiResult<VocabularyAnswerResult>

    /** For [VocabularyCard.Recognition]/[VocabularyCard.WordBank] - the tapped option. */
    suspend fun submitChoice(sessionId: String, itemId: String, selectedChoice: String): ApiResult<VocabularyAnswerResult>

    /** For [VocabularyCard.Hint]/[VocabularyCard.Blind] - the typed response. */
    suspend fun submitTyped(sessionId: String, itemId: String, response: String): ApiResult<VocabularyAnswerResult>
    suspend fun complete(sessionId: String, localDate: String): ApiResult<VocabularySessionSummary>
    suspend fun updateFlags(itemId: String, isBookmarked: Boolean? = null, markedDifficult: Boolean? = null): ApiResult<VocabularyItem>

    /** Edits the extracted term/meaning directly - lets the learner fix an AI extraction mistake. */
    suspend fun updateItem(itemId: String, term: String, meaning: String, nativeMeaning: String?): ApiResult<VocabularyItem>

    /** The Intro card's "AI explain" toggle: a short plain-language note on the word, generated
     * once on first request and persisted so re-viewing the card never re-calls the model. */
    suspend fun explainItem(itemId: String): ApiResult<String>
}
