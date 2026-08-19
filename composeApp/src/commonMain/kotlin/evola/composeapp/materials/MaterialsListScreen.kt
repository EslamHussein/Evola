@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.materials

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.materials_list_add_material_desc
import evola.composeapp.generated.resources.materials_list_delete_desc
import evola.composeapp.generated.resources.materials_list_empty
import evola.composeapp.generated.resources.materials_list_lessons_count
import evola.composeapp.generated.resources.materials_list_loading
import evola.composeapp.generated.resources.materials_list_one_lesson
import evola.composeapp.generated.resources.materials_list_processing
import evola.composeapp.generated.resources.materials_list_processing_with_count
import evola.composeapp.generated.resources.materials_list_retry
import evola.composeapp.generated.resources.materials_list_title
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.RootTopBarTitle
import evola.shared.materials.Material
import evola.shared.materials.MaterialStatus
import org.jetbrains.compose.resources.stringResource

@Composable
fun MaterialsListScreen(
    viewModel: MaterialsListViewModel,
    onAddMaterial: () -> Unit,
    onOpenMaterial: (String) -> Unit,
) {
    val state by viewModel.subscribe()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { TopAppBar(title = { RootTopBarTitle(stringResource(Res.string.materials_list_title)) }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMaterial) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.materials_list_add_material_desc))
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is MaterialsListState.Loading -> LoadingBody()
                is MaterialsListState.Error -> ErrorBody(current.message, onRetry = { viewModel.intent(MaterialsListIntent.Refresh) })
                is MaterialsListState.Loaded -> MaterialsListBody(
                    materials = current.materials,
                    onOpenMaterial = onOpenMaterial,
                    onDeleteMaterial = { materialId -> viewModel.intent(MaterialsListIntent.Delete(materialId)) },
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
            Text(stringResource(Res.string.materials_list_loading))
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
        Button(onClick = onRetry) { Text(stringResource(Res.string.materials_list_retry)) }
    }
}

@Composable
private fun MaterialsListBody(
    materials: List<Material>,
    onOpenMaterial: (String) -> Unit,
    onDeleteMaterial: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (materials.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.materials_list_empty), style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(materials, key = { it.id }) { material ->
                    MaterialRow(
                        material = material,
                        onClick = { onOpenMaterial(material.id) },
                        onDelete = { onDeleteMaterial(material.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onClick: () -> Unit, onDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { it != SwipeToDismissBoxValue.StartToEnd })
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteSwipeBackground(onDelete) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(material.filename, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(materialStatusLabel(material), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun materialStatusLabel(material: Material): String = when (material.status) {
    MaterialStatus.PROCESSING ->
        if (material.lessonsTotal > 0) {
            stringResource(Res.string.materials_list_processing_with_count, material.lessonsReady, material.lessonsTotal)
        } else {
            stringResource(Res.string.materials_list_processing)
        }
    MaterialStatus.READY ->
        if (material.lessonsTotal == 1) {
            stringResource(Res.string.materials_list_one_lesson)
        } else {
            stringResource(Res.string.materials_list_lessons_count, material.lessonsTotal)
        }
    else -> material.status.name
}

/** Swiping only reveals this - it doesn't delete by itself (`confirmValueChange` above blocks the
 * dismiss-and-remove animation on drag alone); the row is only actually removed once this button
 * is tapped, matching the two-step "swipe reveals, tap confirms" pattern (same shape as the lesson
 * row's delete swipe in MaterialDetailScreen.kt). */
@Composable
private fun DeleteSwipeBackground(onDelete: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(EvolaColors.Rust),
        contentAlignment = Alignment.CenterEnd,
    ) {
        IconButton(onClick = onDelete, modifier = Modifier.padding(horizontal = EvolaSpacing.lg)) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.materials_list_delete_desc), tint = Color.White)
        }
    }
}
