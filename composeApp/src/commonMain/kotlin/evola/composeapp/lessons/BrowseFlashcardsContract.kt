package evola.composeapp.lessons

import evola.shared.vocabulary.VocabularyItem

sealed interface BrowseFlashcardsState {
    data object Loading : BrowseFlashcardsState
    data class Error(val message: String) : BrowseFlashcardsState
    data object Empty : BrowseFlashcardsState
    data class Browsing(val items: List<VocabularyItem>, val index: Int) : BrowseFlashcardsState
}
