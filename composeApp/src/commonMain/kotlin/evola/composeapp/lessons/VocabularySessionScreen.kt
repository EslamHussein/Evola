package evola.composeapp.lessons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.core.navigation.BackHandler
import evola.composeapp.speech.rememberSpeechService

/** Vocabulary session screen: a persisted, priority-ordered SRS queue rendered as swipeable cards,
 * Reword-style. A brand-new word is swiped left ("I already know this") or right ("start learning
 * it"); a still-learning or due-review word is swiped left ("got it") or right ("missed it"/"keep
 * showing"), or checked via a typed or multiple-choice input instead. Exiting mid-card is always
 * safe - the repository durably tracks queue position, so the close button and system back gesture
 * are wired to [onDone] in every state, matching the resumable-session guarantee this app's other
 * sessions already have.
 *
 * No @Preview here: this composable is wired directly to a real [VocabularySessionViewModel] and
 * isn't meaningfully previewable in isolation - see [VocabularySessionContent] for the previewable
 * state-driven UI this delegates to. */
@Composable
fun VocabularySessionScreen(viewModel: VocabularySessionViewModel, onDone: () -> Unit) {
    val state by viewModel.collectAsState()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val speechService = rememberSpeechService()
    BackHandler(onBack = onDone)

    VocabularySessionContent(
        state = state,
        settings = settings,
        speechService = speechService,
        onDone = onDone,
        onUndo = viewModel::undoLastGrade,
        onRetry = viewModel::retry,
        onStartNextSession = viewModel::startNextSession,
        onMarkSwipeTutorialSeen = viewModel::markSwipeTutorialSeen,
        onAlreadyKnown = viewModel::submitAlreadyKnown,
        onStartLearning = viewModel::submitStartLearning,
        onToggleBookmark = viewModel::toggleBookmark,
        onToggleDifficult = viewModel::toggleDifficult,
        onExplain = viewModel::explainWord,
        onSelfGrade = viewModel::submitSelfGrade,
        onKeepShowing = viewModel::submitKeepShowing,
        onSelectChoice = viewModel::submitChoice,
        onCheckTyped = viewModel::submitTyped,
        onContinue = viewModel::continueToNext,
    )
}
