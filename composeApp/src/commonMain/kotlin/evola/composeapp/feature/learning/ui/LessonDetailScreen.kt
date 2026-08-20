package evola.composeapp.feature.learning.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import evola.composeapp.feature.learning.vm.LessonDetailViewModel
import org.orbitmvi.orbit.compose.collectAsState

@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onBack: () -> Unit,
    onOpenSection: (key: String) -> Unit,
    onViewVocabularyList: () -> Unit,
) {
    val state by viewModel.collectAsState()
    LessonDetailContent(state = state, onBack = onBack, onOpenSection = onOpenSection, onViewVocabularyList = onViewVocabularyList)
}
