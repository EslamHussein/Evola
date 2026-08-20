package evola.composeapp.feature.vocabulary.vm

import evola.shared.feature.vocabulary.domain.VocabularyItem

sealed interface VocabularyListContent {
    data object Loading : VocabularyListContent
    data class Loaded(val items: List<VocabularyItem>) : VocabularyListContent
    data class Error(val message: String) : VocabularyListContent
}

data class VocabularyListState(
    val content: VocabularyListContent = VocabularyListContent.Loading,
)

/** One sealed side-effect type covers all eight outcomes since they're all "something just
 * happened, tell the screen once" signals off the same list. */
sealed interface VocabularyListSideEffect {
    data class ItemUpdated(val item: VocabularyItem) : VocabularyListSideEffect
    data object ItemUpdateFailed : VocabularyListSideEffect
    data class MarkedAlreadyKnown(val item: VocabularyItem?) : VocabularyListSideEffect
    data class CopiedToPersonalList(val success: Boolean) : VocabularyListSideEffect
    data class WordAdded(val success: Boolean) : VocabularyListSideEffect
    data class WordsImported(val count: Int?) : VocabularyListSideEffect
    data class ItemDeleted(val success: Boolean) : VocabularyListSideEffect
    data class ProgressReset(val success: Boolean) : VocabularyListSideEffect
}
