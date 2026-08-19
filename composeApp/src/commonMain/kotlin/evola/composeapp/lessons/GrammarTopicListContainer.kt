package evola.composeapp.lessons

import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.grammar.GrammarRepository
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

class GrammarTopicListContainer(
    private val lessonId: String,
    private val repository: GrammarRepository,
) : Container<GrammarTopicListState, GrammarTopicListIntent, Nothing> {

    override val store = store(initial = GrammarTopicListState.Loading) {
        configure { name = "GrammarTopicListStore" }
        init {
            val newState = repository.listTopics(lessonId).fold(
                onSuccess = { GrammarTopicListState.Loaded(it) },
                onFailure = { GrammarTopicListState.Error(it.toUserMessage()) },
            )
            updateState { newState }
        }
        reduce { }
    }
}
