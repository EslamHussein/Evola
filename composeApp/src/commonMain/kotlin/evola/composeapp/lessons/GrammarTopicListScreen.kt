@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.grammar.GrammarTopic

/** A lesson's grammar topics (01_PRODUCT_SPEC.md §1.9) - honest empty state when 0 topics were
 * extracted (a valid, non-error outcome), not an error message. */
@Composable
fun GrammarTopicListScreen(viewModel: GrammarTopicListViewModel, onOpenTopic: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.subscribe()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Grammar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is GrammarTopicListState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ChaseLoadingIndicator()
                }

                is GrammarTopicListState.Error -> Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is GrammarTopicListState.Loaded -> {
                    if (current.topics.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No grammar topics for this lesson yet.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
                        ) {
                            items(current.topics) { topic -> TopicRow(topic, onClick = { onOpenTopic(topic.topicId) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicRow(topic: GrammarTopic, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(topic.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    topic.masteryState.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Spacer(Modifier.height(EvolaSpacing.xs))
            Text(topic.explanation, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        }
    }
}
