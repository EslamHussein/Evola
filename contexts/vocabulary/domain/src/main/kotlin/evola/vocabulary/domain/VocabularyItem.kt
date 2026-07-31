package evola.vocabulary.domain

import evola.core.kernel.CefrLevel
import evola.core.kernel.LearnerId
import evola.core.kernel.VocabularyItemId

data class VocabularyItem(
    val id: VocabularyItemId,
    val germanWord: String,
    val englishTranslation: String,
    val cefrLevel: CefrLevel,
    val partOfSpeech: String?,
    val article: String? = null,
    val pluralForm: String? = null,
    val exampleSentence: String? = null,
    val topic: String? = null,
    val synonyms: List<String> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    /** null = shared, Flyway-seeded word; non-null = private, learner-authored from an uploaded resource. */
    val ownerLearnerId: LearnerId? = null,
)
