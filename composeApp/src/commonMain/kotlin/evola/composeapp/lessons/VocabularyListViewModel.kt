package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import evola.composeapp.core.common.toUserMessage
import evola.shared.core.analytics.EvolaLog
import evola.shared.core.common.fold
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyRepository
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Patches [updated] into the loaded list in place, no-op if the current content isn't [VocabularyListContent.Loaded]. */
private fun VocabularyListState.replacingItem(updated: VocabularyItem): VocabularyListState {
    val current = content
    return if (current is VocabularyListContent.Loaded) {
        copy(content = current.copy(items = current.items.map { if (it.itemId == updated.itemId) updated else it }))
    } else {
        this
    }
}

class VocabularyListViewModel(
    private val lessonId: String,
    private val goalId: String,
    private val repository: VocabularyRepository,
) : ViewModel(), OrbitContainerHost<VocabularyListState, VocabularyListState, VocabularyListSideEffect> {

    override val container =
        orbitContainer<VocabularyListState, VocabularyListSideEffect>(VocabularyListState(), onCreate = { loadInitial() })

    private suspend fun Syntax<VocabularyListState, VocabularyListSideEffect>.loadInitial() {
        val content = repository.listVocabulary(lessonId).fold(
            onSuccess = { VocabularyListContent.Loaded(it) },
            onFailure = { VocabularyListContent.Error(it.toUserMessage()) },
        )
        reduce { state.copy(content = content) }
    }

    fun updateItem(itemId: String, term: String, meaning: String, nativeMeaning: String?) = intent {
        repository.updateItem(itemId, term, meaning, nativeMeaning).fold(
            onSuccess = { updated ->
                reduce { state.replacingItem(updated) }
                postSideEffect(VocabularyListSideEffect.ItemUpdated(updated))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.ItemUpdateFailed) },
        )
    }

    /** Word-detail sheet's standalone "Mark as already known" - fast-tracks straight into the
     * review schedule, same as a New card's swipe-left, without needing an active session. */
    fun markAlreadyKnown(itemId: String) = intent {
        repository.markAlreadyKnown(itemId).fold(
            onSuccess = { updated ->
                reduce { state.replacingItem(updated) }
                postSideEffect(VocabularyListSideEffect.MarkedAlreadyKnown(updated))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.MarkedAlreadyKnown(null)) },
        )
    }

    /** Word-detail sheet's "Copy to Eigene Vokabeln" - duplicates the word into the goal's single
     * personal list (auto-created on first use). The copy is a new, independent item; this
     * screen's own list is unaffected unless it's already viewing that personal lesson. */
    fun copyToPersonalList(itemId: String) = intent {
        repository.copyToPersonalList(goalId, itemId).fold(
            onSuccess = { postSideEffect(VocabularyListSideEffect.CopiedToPersonalList(true)) },
            onFailure = { postSideEffect(VocabularyListSideEffect.CopiedToPersonalList(false)) },
        )
    }

    /** Adds a word straight into this lesson (no AI extraction) - the Reword-style "add your own
     * word" feature, landing in whichever lesson is currently open since Evola's content is
     * lesson-scoped rather than pre-loaded decks. */
    fun addWord(term: String, meaning: String, nativeMeaning: String?) = intent {
        repository.addCustomWord(lessonId, term, meaning, nativeMeaning).fold(
            onSuccess = { added ->
                reduce {
                    val current = state.content
                    if (current is VocabularyListContent.Loaded) {
                        state.copy(content = current.copy(items = current.items + added))
                    } else {
                        state
                    }
                }
                postSideEffect(VocabularyListSideEffect.WordAdded(true))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.WordAdded(false)) },
        )
    }

    /** Reword's word-detail "Remove" - permanently deletes the word. Confirmed by the screen before
     * dispatch (this method itself performs no confirmation). */
    fun deleteItem(itemId: String) = intent {
        repository.deleteItem(itemId).fold(
            onSuccess = {
                reduce {
                    val current = state.content
                    if (current is VocabularyListContent.Loaded) {
                        state.copy(content = current.copy(items = current.items.filterNot { it.itemId == itemId }))
                    } else {
                        state
                    }
                }
                postSideEffect(VocabularyListSideEffect.ItemDeleted(true))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.ItemDeleted(false)) },
        )
    }

    /** Reword's per-category "Reset progress" - confirmed by the screen before dispatch. */
    fun resetProgress() = intent {
        repository.resetLessonProgress(lessonId).fold(
            onSuccess = {
                repository.listVocabulary(lessonId).fold(
                    onSuccess = { items -> reduce { state.copy(content = VocabularyListContent.Loaded(items)) } },
                    // The reset itself succeeded server-side - only the follow-up re-fetch failed,
                    // so the visible list is now stale until the next successful load. Logged since
                    // that mismatch is otherwise silent.
                    onFailure = { EvolaLog.d("vocab-list", "post-reset re-fetch failed for lesson=$lessonId: $it") },
                )
                postSideEffect(VocabularyListSideEffect.ProgressReset(true))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.ProgressReset(false)) },
        )
    }

    /** Reword's "Import words" - bulk-adds every row from a parsed CSV file into this lesson, then
     * re-fetches the full list once (rather than appending client-side) since a large import makes
     * a single re-fetch cheaper than folding hundreds of items into state one at a time. */
    fun importWords(rows: List<Triple<String, String, String?>>) = intent {
        repository.importWords(lessonId, rows).fold(
            onSuccess = { count ->
                repository.listVocabulary(lessonId).fold(
                    onSuccess = { items -> reduce { state.copy(content = VocabularyListContent.Loaded(items)) } },
                    onFailure = { EvolaLog.d("vocab-list", "post-import re-fetch failed for lesson=$lessonId: $it") },
                )
                postSideEffect(VocabularyListSideEffect.WordsImported(count))
            },
            onFailure = { postSideEffect(VocabularyListSideEffect.WordsImported(null)) },
        )
    }
}
