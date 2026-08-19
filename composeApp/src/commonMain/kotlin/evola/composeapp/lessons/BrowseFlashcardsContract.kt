package evola.composeapp.lessons

import evola.shared.vocabulary.VocabularyItem
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface BrowseFlashcardsState : MVIState {
    data object Loading : BrowseFlashcardsState
    data class Error(val message: String) : BrowseFlashcardsState
    data object Empty : BrowseFlashcardsState
    data class Browsing(val items: List<VocabularyItem>, val index: Int) : BrowseFlashcardsState
}

sealed interface BrowseFlashcardsIntent : MVIIntent {
    data object Next : BrowseFlashcardsIntent
    data object Previous : BrowseFlashcardsIntent
}
