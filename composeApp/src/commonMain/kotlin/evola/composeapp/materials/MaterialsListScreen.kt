@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.RootTopBarTitle
import evola.shared.goals.Lesson
import evola.shared.materials.Material

@Composable
fun MaterialsListScreen(
    viewModel: MaterialsListViewModel,
    onAddMaterial: () -> Unit,
    onOpenMaterial: (String) -> Unit,
    onContinueLesson: (Lesson) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { RootTopBarTitle("Materials") }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMaterial) {
                Icon(Icons.Filled.Add, contentDescription = "Add material")
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is MaterialsListState.Loading -> LoadingBody()
                is MaterialsListState.Error -> ErrorBody(current.message, onRetry = viewModel::refresh)
                is MaterialsListState.Loaded -> MaterialsListBody(
                    materials = current.materials,
                    currentLesson = current.currentLesson,
                    onOpenMaterial = onOpenMaterial,
                    onContinueLesson = onContinueLesson,
                )
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ChaseLoadingIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading...")
        }
    }
}

@Composable
private fun ErrorBody(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun MaterialsListBody(
    materials: List<Material>,
    currentLesson: Lesson?,
    onOpenMaterial: (String) -> Unit,
    onContinueLesson: (Lesson) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (materials.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No materials yet. Add one to get started.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                currentLesson?.let { lesson ->
                    item {
                        ContinueLessonCard(lesson = lesson, onClick = { onContinueLesson(lesson) })
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(materials) { material ->
                    MaterialRow(material = material, onClick = { onOpenMaterial(material.id) })
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Same "what's next" job the old Study tab's flat list used to own, now surfaced as a single card
 * above the book list - Materials is the one lesson browser now, so this is the one place that
 * job needs to live. */
@Composable
private fun ContinueLessonCard(lesson: Lesson, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Text("Continue", style = MaterialTheme.typography.labelMedium, color = EvolaColors.Accent)
            Spacer(Modifier.height(4.dp))
            Text("${lesson.number}. ${lesson.title}", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(material.filename, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(material.status.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
