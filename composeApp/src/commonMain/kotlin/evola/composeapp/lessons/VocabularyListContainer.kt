package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.vocabulary.VocabularyItem
import evola.shared.vocabulary.VocabularyRepository
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/** Patches [updated] into the loaded list in place, no-op if the current content isn't [VocabularyListContent.Loaded]. */
private fun VocabularyListState.replacingItem(updated: VocabularyItem): VocabularyListState {
    val current = content
    return if (current is VocabularyListContent.Loaded) {
        copy(content = current.copy(items = current.items.map { if (it.itemId == updated.itemId) updated else it }))
    } else {
        this
    }
}

class VocabularyListContainer(
    private val lessonId: String,
    private val goalId: String,
    private val repository: VocabularyRepository,
) : Container<VocabularyListState, VocabularyListIntent, Nothing> {

    override val store = store(initial = VocabularyListState()) {
        configure { name = "VocabularyListStore" }
        init {
            val content = repository.listVocabulary(lessonId).fold(
                onSuccess = { VocabularyListContent.Loaded(it) },
                onFailure = { VocabularyListContent.Error(it.toUserMessage()) },
            )
            updateState { copy(content = content) }
        }
        reduce { intent ->
            when (intent) {
                is VocabularyListIntent.UpdateItem -> {
                    repository.updateItem(intent.itemId, intent.term, intent.meaning, intent.nativeMeaning).fold(
                        onSuccess = { updated ->
                            updateState { replacingItem(updated) }
                            updateState { copy(event = VocabularyListEvent.ItemUpdated(updated)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.ItemUpdateFailed()) } },
                    )
                }

                is VocabularyListIntent.MarkAlreadyKnown -> {
                    repository.markAlreadyKnown(intent.itemId).fold(
                        onSuccess = { updated ->
                            updateState { replacingItem(updated) }
                            updateState { copy(event = VocabularyListEvent.MarkedAlreadyKnown(updated)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.MarkedAlreadyKnown(null)) } },
                    )
                }

                is VocabularyListIntent.CopyToPersonalList -> {
                    repository.copyToPersonalList(goalId, intent.itemId).fold(
                        onSuccess = { updateState { copy(event = VocabularyListEvent.CopiedToPersonalList(true)) } },
                        onFailure = { updateState { copy(event = VocabularyListEvent.CopiedToPersonalList(false)) } },
                    )
                }

                is VocabularyListIntent.AddWord -> {
                    repository.addCustomWord(lessonId, intent.term, intent.meaning, intent.nativeMeaning).fold(
                        onSuccess = { added ->
                            updateState {
                                val current = content
                                if (current is VocabularyListContent.Loaded) {
                                    copy(content = current.copy(items = current.items + added))
                                } else this
                            }
                            updateState { copy(event = VocabularyListEvent.WordAdded(true)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.WordAdded(false)) } },
                    )
                }

                is VocabularyListIntent.DeleteItem -> {
                    repository.deleteItem(intent.itemId).fold(
                        onSuccess = {
                            updateState {
                                val current = content
                                if (current is VocabularyListContent.Loaded) {
                                    copy(content = current.copy(items = current.items.filterNot { it.itemId == intent.itemId }))
                                } else {
                                    this
                                }
                            }
                            updateState { copy(event = VocabularyListEvent.ItemDeleted(true)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.ItemDeleted(false)) } },
                    )
                }

                VocabularyListIntent.ResetProgress -> {
                    repository.resetLessonProgress(lessonId).fold(
                        onSuccess = {
                            repository.listVocabulary(lessonId).fold(
                                onSuccess = { items -> updateState { copy(content = VocabularyListContent.Loaded(items)) } },
                                onFailure = {},
                            )
                            updateState { copy(event = VocabularyListEvent.ProgressReset(true)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.ProgressReset(false)) } },
                    )
                }

                is VocabularyListIntent.ImportWords -> {
                    repository.importWords(lessonId, intent.rows).fold(
                        onSuccess = { count ->
                            repository.listVocabulary(lessonId).fold(
                                onSuccess = { items -> updateState { copy(content = VocabularyListContent.Loaded(items)) } },
                                onFailure = {},
                            )
                            updateState { copy(event = VocabularyListEvent.WordsImported(count)) }
                        },
                        onFailure = { updateState { copy(event = VocabularyListEvent.WordsImported(null)) } },
                    )
                }
            }
        }
    }
}
