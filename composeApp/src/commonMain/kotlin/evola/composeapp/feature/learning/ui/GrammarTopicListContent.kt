@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.learning.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.feature.learning.vm.GrammarTopicListState
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_grammar_topics_empty
import evola.composeapp.generated.resources.lessons_grammar_topics_title
import evola.composeapp.generated.resources.lessons_nav_back
import evola.shared.feature.learning.domain.GrammarTopic
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun GrammarTopicListContent(
    state: GrammarTopicListState,
    onOpenTopic: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_grammar_topics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.lessons_nav_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is GrammarTopicListState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChaseLoadingIndicator()
                }

                is GrammarTopicListState.Error -> Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
                    Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is GrammarTopicListState.Loaded -> {
                    if (state.topics.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.lessons_grammar_topics_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
                        ) {
                            items(state.topics) { topic -> TopicRow(topic, onClick = { onOpenTopic(topic.topicId) }) }
                        }
                    }
                }
            }
        }
    }
}

private val fakeGrammarTopics = listOf(
    GrammarTopic(topicId = "t1", name = "Akkusativ", explanation = "The accusative case marks the direct object.", masteryState = "learning"),
    GrammarTopic(topicId = "t2", name = "Dativ", explanation = "The dative case marks the indirect object.", masteryState = "new"),
)

@Preview
@Composable
private fun GrammarTopicListLoadingPreview() {
    EvolaTheme { GrammarTopicListContent(state = GrammarTopicListState.Loading, onOpenTopic = {}, onBack = {}) }
}

@Preview
@Composable
private fun GrammarTopicListLoadedPreview() {
    EvolaTheme { GrammarTopicListContent(state = GrammarTopicListState.Loaded(fakeGrammarTopics), onOpenTopic = {}, onBack = {}) }
}

@Preview
@Composable
private fun GrammarTopicListEmptyPreview() {
    EvolaTheme { GrammarTopicListContent(state = GrammarTopicListState.Loaded(emptyList()), onOpenTopic = {}, onBack = {}) }
}

@Preview
@Composable
private fun GrammarTopicListErrorPreview() {
    EvolaTheme { GrammarTopicListContent(state = GrammarTopicListState.Error("Something went wrong."), onOpenTopic = {}, onBack = {}) }
}
