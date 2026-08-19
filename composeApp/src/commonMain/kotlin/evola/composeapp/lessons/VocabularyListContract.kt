package evola.composeapp.lessons

import evola.shared.vocabulary.VocabularyItem
import kotlin.random.Random
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface VocabularyListContent {
    data object Loading : VocabularyListContent
    data class Loaded(val items: List<VocabularyItem>) : VocabularyListContent
    data class Error(val message: String) : VocabularyListContent
}

/** See [evola.composeapp.main.GoalUpdateEvent] - same state-based one-shot-event pattern
 * (`subscribeConsume`/`MVIAction` isn't visible from `commonMain` in FlowMVI 3.1.0). One sealed
 * event type covers all five outcomes since they're all "something just happened, tell the screen
 * once" signals off the same list. */
sealed interface VocabularyListEvent {
    val id: Long

    data class ItemUpdated(val item: VocabularyItem, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class ItemUpdateFailed(override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class MarkedAlreadyKnown(val item: VocabularyItem?, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class CopiedToPersonalList(val success: Boolean, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class WordAdded(val success: Boolean, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class WordsImported(val count: Int?, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class ItemDeleted(val success: Boolean, override val id: Long = Random.nextLong()) : VocabularyListEvent
    data class ProgressReset(val success: Boolean, override val id: Long = Random.nextLong()) : VocabularyListEvent
}

data class VocabularyListState(
    val content: VocabularyListContent = VocabularyListContent.Loading,
    val event: VocabularyListEvent? = null,
) : MVIState

sealed interface VocabularyListIntent : MVIIntent {
    data class UpdateItem(val itemId: String, val term: String, val meaning: String, val nativeMeaning: String?) : VocabularyListIntent

    /** Word-detail sheet's standalone "Mark as already known" - fast-tracks straight into the
     * review schedule, same as a New card's swipe-left, without needing an active session. */
    data class MarkAlreadyKnown(val itemId: String) : VocabularyListIntent

    /** Word-detail sheet's "Copy to Eigene Vokabeln" - duplicates the word into the goal's single
     * personal list (auto-created on first use). The copy is a new, independent item; this
     * screen's own list is unaffected unless it's already viewing that personal lesson. */
    data class CopyToPersonalList(val itemId: String) : VocabularyListIntent

    /** Adds a word straight into this lesson (no AI extraction) - the Reword-style "add your own
     * word" feature, landing in whichever lesson is currently open since Evola's content is
     * lesson-scoped rather than pre-loaded decks. */
    data class AddWord(val term: String, val meaning: String, val nativeMeaning: String?) : VocabularyListIntent

    /** Reword's "Import words" - bulk-adds every row from a parsed CSV file into this lesson, then
     * re-fetches the full list once (rather than appending client-side) since a large import makes
     * a single re-fetch cheaper than folding hundreds of items into state one at a time. */
    data class ImportWords(val rows: List<Triple<String, String, String?>>) : VocabularyListIntent

    /** Reword's word-detail "Remove" - permanently deletes the word. Confirmed by the screen before
     * dispatch (this intent itself performs no confirmation). */
    data class DeleteItem(val itemId: String) : VocabularyListIntent

    /** Reword's per-category "Reset progress" - confirmed by the screen before dispatch. */
    data object ResetProgress : VocabularyListIntent
}
