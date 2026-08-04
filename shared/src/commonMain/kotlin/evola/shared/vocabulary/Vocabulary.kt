package evola.shared.vocabulary

/** A lesson's own vocabulary item plus this user's current mastery state (01_PRODUCT_SPEC.md §1.8).
 * The meaning_ar/ipa/related-words/difficulty/frequency/memory-tip fields are only populated for
 * items extracted from V12 on - null/empty for pre-existing rows. is_bookmarked/marked_difficult
 * are populated from V13 on (pack/stage session redesign). */
data class VocabularyItem(
    val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val masteryState: String,
    val meaningAr: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val isBookmarked: Boolean = false,
    val markedDifficult: Boolean = false,
)

/** One word's full Discover-card payload plus whichever stage-specific fields the current stage
 * needs (design handoff Phase 7/8 pack session). */
data class PackWord(
    val itemId: String,
    val term: String,
    val meaning: String,
    val gender: String? = null,
    val exampleSentence: String? = null,
    val masteryState: String,
    val meaningAr: String? = null,
    val ipaPronunciation: String? = null,
    val relatedWords: List<String> = emptyList(),
    val difficultyRating: String? = null,
    val frequencyRating: String? = null,
    val memoryTip: String? = null,
    val isBookmarked: Boolean = false,
    val markedDifficult: Boolean = false,
    val recognitionChoices: List<String> = emptyList(),
    val partialMask: String? = null,
    val sentenceWithBlank: String? = null,
    val sentenceTranslationPrompt: String? = null,
)

/** The current position within a ~5-word pack: which word (0-based), which of the 7 fixed stages
 * (Discover..Free Production, also 0-based), and that word's full payload. [readyToComplete] is
 * true once every word in the pack has finished all 7 stages - the client should show "Finish
 * pack" and call [VocabularyRepository.complete] rather than render another stage. */
data class VocabularyPack(
    val packId: String,
    val packNumber: Int,
    val wordIndex: Int,
    val wordsCount: Int,
    val stageIndex: Int,
    val word: PackWord,
    val readyToComplete: Boolean = false,
)

/** [correct] is null for Stage 0 (Discover) and Stage 1 (Recognition) - never graded, since the
 * design always reveals the correct answer regardless of input. [feedback] is only populated for
 * Stage 6 (Free Production)'s AI grading. */
data class VocabularyStageAnswerResult(
    val correct: Boolean?,
    val feedback: String? = null,
    val next: VocabularyPack? = null,
)

data class VocabularyPackSummary(
    val wordsLearned: Int,
    val accuracy: Double,
    val timeSeconds: Long,
)

interface VocabularyRepository {
    suspend fun startOrResumeSession(accessToken: String, lessonId: String): VocabularyPack?
    suspend fun listVocabulary(accessToken: String, lessonId: String): List<VocabularyItem>
    suspend fun answer(accessToken: String, packId: String, itemId: String, stageIndex: Int, response: String): VocabularyStageAnswerResult?
    suspend fun complete(accessToken: String, packId: String, localDate: String): VocabularyPackSummary?
    suspend fun updateFlags(accessToken: String, itemId: String, isBookmarked: Boolean? = null, markedDifficult: Boolean? = null): VocabularyItem?
}
