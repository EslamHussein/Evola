package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.dsl.updateState
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/** Reword's "Extra modes (do not affect stats)" - a plain flip-through of every word in the lesson,
 * regardless of SRS status, with no grading and no repository writes at all: [BrowseFlashcardsIntent.Next]/
 * [BrowseFlashcardsIntent.Previous] only move a local index. Reuses [VocabularyRepository.listVocabulary]
 * as-is - there's no dedicated "browse" endpoint because there's nothing for one to do beyond what the
 * list call already returns. */
class BrowseFlashcardsContainer(
    private val lessonId: String,
    private val repository: VocabularyRepository,
) : Container<BrowseFlashcardsState, BrowseFlashcardsIntent, Nothing> {

    override val store = store(initial = BrowseFlashcardsState.Loading) {
        configure { name = "BrowseFlashcardsStore" }
        init {
            val newState = repository.listVocabulary(lessonId).fold(
                onSuccess = { items -> if (items.isEmpty()) BrowseFlashcardsState.Empty else BrowseFlashcardsState.Browsing(items, 0) },
                onFailure = { BrowseFlashcardsState.Error(it.toUserMessage()) },
            )
            updateState { newState }
        }
        reduce { intent ->
            when (intent) {
                BrowseFlashcardsIntent.Next -> updateState<BrowseFlashcardsState.Browsing, _> {
                    if (index < items.lastIndex) copy(index = index + 1) else this
                }
                BrowseFlashcardsIntent.Previous -> updateState<BrowseFlashcardsState.Browsing, _> {
                    if (index > 0) copy(index = index - 1) else this
                }
            }
        }
    }
}
