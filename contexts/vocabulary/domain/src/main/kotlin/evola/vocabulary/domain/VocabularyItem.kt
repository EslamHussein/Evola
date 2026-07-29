package evola.vocabulary.domain

import evola.core.kernel.CefrLevel
import evola.core.kernel.VocabularyItemId

data class VocabularyItem(
    val id: VocabularyItemId,
    val germanWord: String,
    val englishTranslation: String,
    val cefrLevel: CefrLevel,
    val partOfSpeech: String?,
)
