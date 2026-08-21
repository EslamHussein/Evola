@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.materials.ui

import evola.composeapp.feature.materials.vm.MaterialsListState
import evola.composeapp.feature.materials.vm.MaterialsListViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import evola.composeapp.core.common.ChaseLoadingIndicator
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
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.materials_list_add_material_desc
import evola.composeapp.generated.resources.materials_list_delete_desc
import evola.composeapp.generated.resources.materials_list_empty
import evola.composeapp.generated.resources.materials_list_lessons_count
import evola.composeapp.generated.resources.materials_list_loading
import evola.composeapp.generated.resources.materials_list_one_lesson
import evola.composeapp.generated.resources.materials_list_processing
import evola.composeapp.generated.resources.materials_list_processing_with_count
import evola.composeapp.generated.resources.materials_list_title
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.components.EvolaCard
import evola.composeapp.core.designsystem.components.EvolaEmptyState
import evola.composeapp.core.designsystem.components.EvolaErrorState
import evola.composeapp.core.designsystem.components.RootTopBarTitle
import evola.composeapp.core.designsystem.components.SwipeToRevealDelete
import evola.shared.feature.materials.domain.Material
import evola.shared.feature.materials.domain.MaterialStatus
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MaterialsListScreen(
    viewModel: MaterialsListViewModel,
    onAddMaterial: () -> Unit,
    onOpenMaterial: (String) -> Unit,
) {
    val state by viewModel.collectAsState()
    MaterialsListContent(
        state = state,
        onAddMaterial = onAddMaterial,
        onOpenMaterial = onOpenMaterial,
        onRetry = { viewModel.refresh() },
        onDeleteMaterial = { materialId -> viewModel.delete(materialId) },
    )
}

@Composable
private fun MaterialsListContent(
    state: MaterialsListState,
    onAddMaterial: () -> Unit,
    onOpenMaterial: (String) -> Unit,
    onRetry: () -> Unit,
    onDeleteMaterial: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        // See the matching comment on HomeContent's Scaffold - MainScreen's own bottom tab bar
        // already accounts for the bottom inset, so this defaults to safeDrawing would double it.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { RootTopBarTitle(stringResource(Res.string.materials_list_title)) }, scrollBehavior = scrollBehavior) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMaterial) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.materials_list_add_material_desc))
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is MaterialsListState.Loading -> LoadingBody()
                is MaterialsListState.Error -> EvolaErrorState(message = state.message, onRetry = onRetry)
                is MaterialsListState.Loaded -> MaterialsListBody(
                    materials = state.materials,
                    onOpenMaterial = onOpenMaterial,
                    onDeleteMaterial = onDeleteMaterial,
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
            Spacer(Modifier.height(EvolaSpacing.lg))
            Text(stringResource(Res.string.materials_list_loading))
        }
    }
}

@Composable
private fun MaterialsListBody(
    materials: List<Material>,
    onOpenMaterial: (String) -> Unit,
    onDeleteMaterial: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        if (materials.isEmpty()) {
            EvolaEmptyState(message = stringResource(Res.string.materials_list_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = EvolaSpacing.xs),
            ) {
                items(materials, key = { it.id }) { material ->
                    MaterialRow(
                        material = material,
                        onClick = { onOpenMaterial(material.id) },
                        onDelete = { onDeleteMaterial(material.id) },
                    )
                    Spacer(Modifier.height(EvolaSpacing.sm))
                }
            }
        }
    }
}

@Composable
private fun MaterialRow(material: Material, onClick: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealDelete(
        onDelete = onDelete,
        deleteContentDescription = stringResource(Res.string.materials_list_delete_desc),
    ) {
        EvolaCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            Text(material.filename, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.xs))
            Text(materialStatusLabel(material), style = MaterialTheme.typography.bodyMedium)
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

private val fakeMaterialsListItems = listOf(
    Material(
        id = "m1", userId = "u1", goalId = "g1", filename = "grammar-book.pdf", contentHash = "h1",
        status = MaterialStatus.READY, mimeType = "application/pdf", sizeBytes = 204_800L, lessonsReady = 3, lessonsTotal = 3,
    ),
    Material(
        id = "m2", userId = "u1", goalId = "g1", filename = "reading-practice.pdf", contentHash = "h2",
        status = MaterialStatus.PROCESSING, mimeType = "application/pdf", sizeBytes = 102_400L, lessonsReady = 1, lessonsTotal = 4,
    ),
)

@Preview
@Composable
private fun MaterialsListLoadingPreview() {
    EvolaTheme { MaterialsListContent(state = MaterialsListState.Loading, onAddMaterial = {}, onOpenMaterial = {}, onRetry = {}, onDeleteMaterial = {}) }
}

@Preview
@Composable
private fun MaterialsListLoadedPreview() {
    EvolaTheme {
        MaterialsListContent(
            state = MaterialsListState.Loaded(fakeMaterialsListItems),
            onAddMaterial = {}, onOpenMaterial = {}, onRetry = {}, onDeleteMaterial = {},
        )
    }
}

@Preview
@Composable
private fun MaterialsListEmptyPreview() {
    EvolaTheme { MaterialsListContent(state = MaterialsListState.Loaded(emptyList()), onAddMaterial = {}, onOpenMaterial = {}, onRetry = {}, onDeleteMaterial = {}) }
}

@Preview
@Composable
private fun MaterialsListErrorPreview() {
    EvolaTheme {
        MaterialsListContent(state = MaterialsListState.Error("Something went wrong."), onAddMaterial = {}, onOpenMaterial = {}, onRetry = {}, onDeleteMaterial = {})
    }
}
