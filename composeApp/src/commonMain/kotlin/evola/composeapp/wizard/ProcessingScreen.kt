package evola.composeapp.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.core.navigation.BackHandler
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.wizard_processing_analyzing
import evola.composeapp.generated.resources.wizard_processing_continue_background
import evola.composeapp.generated.resources.wizard_processing_error_title
import evola.composeapp.generated.resources.wizard_processing_extracting
import evola.composeapp.generated.resources.wizard_processing_lesson_of_total
import evola.composeapp.generated.resources.wizard_processing_splitting
import evola.composeapp.generated.resources.wizard_processing_splitting_description
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.materials.Lesson
import evola.shared.materials.Material
import evola.shared.materials.MaterialDetail
import evola.shared.materials.MaterialStatus
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    materialId: String,
    onDone: (materialId: String) -> Unit,
) {
    val state by viewModel.collectAsState()

    LaunchedEffect(state) {
        val done = state as? ProcessingState.Done
        if (done != null) onDone(done.materialId)
    }

    // Extraction already runs on a process-lifetime scope, not tied to this screen - it keeps
    // going whether or not the user is looking at it, so there's no reason to block them here.
    // Both the back gesture and the button below just jump straight to the same Detail screen
    // Done already lands on, which shows the same live progress this screen does.
    BackHandler(onBack = { onDone(materialId) })

    ProcessingContent(state = state, onContinueInBackground = { onDone(materialId) })
}

@Composable
private fun ProcessingContent(state: ProcessingState, onContinueInBackground: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                ProcessingState.Loading -> {
                    ChaseLoadingIndicator()
                    Spacer(Modifier.height(EvolaSpacing.md))
                    Text(stringResource(Res.string.wizard_processing_analyzing), style = MaterialTheme.typography.titleMedium)
                }

                is ProcessingState.InProgress -> {
                    InProgressContent(state.detail)
                }

                is ProcessingState.Done -> {
                    ChaseLoadingIndicator()
                }

                is ProcessingState.Error -> {
                    Text(stringResource(Res.string.wizard_processing_error_title), style = MaterialTheme.typography.titleMedium)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state is ProcessingState.Loading || state is ProcessingState.InProgress) {
                Spacer(Modifier.height(EvolaSpacing.lg))
                TextButton(onClick = onContinueInBackground) { Text(stringResource(Res.string.wizard_processing_continue_background)) }
            }
        }
    }
}

@Composable
private fun InProgressContent(detail: MaterialDetail) {
    val lessons = detail.lessons
    if (lessons.isEmpty()) {
        // Segmentation phase: no lesson rows exist yet.
        ChaseLoadingIndicator()
        Spacer(Modifier.height(EvolaSpacing.md))
        Text(stringResource(Res.string.wizard_processing_splitting), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.wizard_processing_splitting_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val readyCount = lessons.count { it.status == "ready" }
    val total = lessons.size
    val current = lessons.firstOrNull { it.status == "extracting" }

    ChaseLoadingIndicator()
    Spacer(Modifier.height(EvolaSpacing.md))
    Text(
        stringResource(Res.string.wizard_processing_lesson_of_total, current?.number ?: (readyCount + 1).coerceAtMost(total), total),
        style = MaterialTheme.typography.titleMedium,
    )
    if (current != null) {
        Text(
            stringResource(Res.string.wizard_processing_extracting),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    LinearProgressIndicator(
        progress = { readyCount / total.toFloat() },
        modifier = Modifier.fillMaxWidth(),
    )
}

private val fakeMaterial = Material(
    id = "m1", userId = "u1", goalId = "g1", filename = "grammar-book.pdf", contentHash = "h1",
    status = MaterialStatus.PROCESSING, mimeType = "application/pdf", sizeBytes = 204_800L,
)

private val fakeInProgressDetail = MaterialDetail(
    material = fakeMaterial,
    lessons = listOf(
        Lesson(id = "l1", materialId = "m1", goalId = "g1", number = 1, title = "Lesson 1", status = "ready"),
        Lesson(id = "l2", materialId = "m1", goalId = "g1", number = 2, title = "Lesson 2", status = "extracting"),
        Lesson(id = "l3", materialId = "m1", goalId = "g1", number = 3, title = "Lesson 3", status = "pending"),
    ),
)

@Preview
@Composable
private fun ProcessingLoadingPreview() {
    EvolaTheme { ProcessingContent(state = ProcessingState.Loading, onContinueInBackground = {}) }
}

@Preview
@Composable
private fun ProcessingSplittingPreview() {
    EvolaTheme { ProcessingContent(state = ProcessingState.InProgress(fakeInProgressDetail.copy(lessons = emptyList())), onContinueInBackground = {}) }
}

@Preview
@Composable
private fun ProcessingInProgressPreview() {
    EvolaTheme { ProcessingContent(state = ProcessingState.InProgress(fakeInProgressDetail), onContinueInBackground = {}) }
}

@Preview
@Composable
private fun ProcessingErrorPreview() {
    EvolaTheme { ProcessingContent(state = ProcessingState.Error("Upload failed. Please try again."), onContinueInBackground = {}) }
}
