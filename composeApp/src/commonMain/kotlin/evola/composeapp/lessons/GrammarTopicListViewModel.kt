package evola.composeapp.lessons

import androidx.lifecycle.ViewModel
import evola.composeapp.core.toUserMessage
import evola.shared.core.fold
import evola.shared.grammar.GrammarRepository
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.viewmodel.orbitContainer

class GrammarTopicListViewModel(
    private val lessonId: String,
    private val repository: GrammarRepository,
) : ViewModel(), OrbitContainerHost<GrammarTopicListState, GrammarTopicListState, Nothing> {

    override val container = orbitContainer<GrammarTopicListState, Nothing>(GrammarTopicListState.Loading, onCreate = {
        val newState = repository.listTopics(lessonId).fold(
            onSuccess = { GrammarTopicListState.Loaded(it) },
            onFailure = { GrammarTopicListState.Error(it.toUserMessage()) },
        )
        reduce { newState }
    })
}
