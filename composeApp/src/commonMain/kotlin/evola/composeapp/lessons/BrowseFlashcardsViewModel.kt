package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.vocabulary.VocabularyRepository
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Reword's "Extra modes (do not affect stats)" - a plain flip-through of every word in the lesson,
 * regardless of SRS status, with no grading and no repository writes at all: [next]/[previous] only
 * move a local index. Reuses [VocabularyRepository.listVocabulary] as-is - there's no dedicated
 * "browse" endpoint because there's nothing for one to do beyond what the list call already returns. */
class BrowseFlashcardsViewModel(
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : ViewModel(), OrbitContainerHost<BrowseFlashcardsState, BrowseFlashcardsState, Nothing> {

    override val container = orbitContainer<BrowseFlashcardsState, Nothing>(BrowseFlashcardsState.Loading, onCreate = { load() })

    private suspend fun Syntax<BrowseFlashcardsState, Nothing>.load() {
        val newState = repository.listVocabulary(lessonId).fold(
            onSuccess = { items -> if (items.isEmpty()) BrowseFlashcardsState.Empty else BrowseFlashcardsState.Browsing(items, 0) },
            onFailure = { BrowseFlashcardsState.Error(it.toUserMessage()) },
        )
        reduce { newState }
    }

    fun next() = intent {
        val current = state
        if (current is BrowseFlashcardsState.Browsing && current.index < current.items.lastIndex) {
            reduce { current.copy(index = current.index + 1) }
        }
    }

    fun previous() = intent {
        val current = state
        if (current is BrowseFlashcardsState.Browsing && current.index > 0) {
            reduce { current.copy(index = current.index - 1) }
        }
    }
}
