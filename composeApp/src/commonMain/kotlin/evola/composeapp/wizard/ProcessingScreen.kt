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
import evola.composeapp.BackHandler
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.wizard_processing_analyzing
import evola.composeapp.generated.resources.wizard_processing_continue_background
import evola.composeapp.generated.resources.wizard_processing_error_title
import evola.composeapp.generated.resources.wizard_processing_extracting
import evola.composeapp.generated.resources.wizard_processing_lesson_of_total
import evola.composeapp.generated.resources.wizard_processing_splitting
import evola.composeapp.generated.resources.wizard_processing_splitting_description
import evola.composeapp.loading.ChaseLoadingIndicator
import evola.composeapp.theme.EvolaSpacing
import evola.shared.materials.MaterialDetail
import org.jetbrains.compose.resources.stringResource

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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val current = state) {
                ProcessingState.Loading -> {
                    ChaseLoadingIndicator()
                    Spacer(Modifier.height(EvolaSpacing.md))
                    Text(stringResource(Res.string.wizard_processing_analyzing), style = MaterialTheme.typography.titleMedium)
                }

                is ProcessingState.InProgress -> {
                    InProgressContent(current.detail)
                }

                is ProcessingState.Done -> {
                    ChaseLoadingIndicator()
                }

                is ProcessingState.Error -> {
                    Text(stringResource(Res.string.wizard_processing_error_title), style = MaterialTheme.typography.titleMedium)
                    Text(current.message, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (state is ProcessingState.Loading || state is ProcessingState.InProgress) {
                Spacer(Modifier.height(EvolaSpacing.lg))
                TextButton(onClick = { onDone(materialId) }) { Text(stringResource(Res.string.wizard_processing_continue_background)) }
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
