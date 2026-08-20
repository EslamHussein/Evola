package evola.composeapp.feature.learning.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import evola.composeapp.feature.learning.vm.GrammarTopicListViewModel
import org.orbitmvi.orbit.compose.collectAsState

/** A lesson's grammar topics (01_PRODUCT_SPEC.md §1.9) - honest empty state when 0 topics were
 * extracted (a valid, non-error outcome), not an error message. */
@Composable
fun GrammarTopicListScreen(viewModel: GrammarTopicListViewModel, onOpenTopic: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.collectAsState()
    GrammarTopicListContent(state = state, onOpenTopic = onOpenTopic, onBack = onBack)
}
