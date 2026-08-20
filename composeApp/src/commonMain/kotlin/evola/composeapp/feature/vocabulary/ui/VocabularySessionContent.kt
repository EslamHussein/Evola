@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.feature.vocabulary.ui

import evola.composeapp.feature.vocabulary.vm.VocabularySessionUiState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_done
import evola.composeapp.generated.resources.lessons_action_retry
import evola.composeapp.generated.resources.lessons_nav_close
import evola.composeapp.generated.resources.lessons_study_empty
import evola.composeapp.generated.resources.lessons_undo_last_answer
import evola.composeapp.generated.resources.lessons_vocab_status_learning
import evola.composeapp.generated.resources.lessons_vocab_status_new
import evola.composeapp.generated.resources.lessons_vocab_status_review
import evola.composeapp.generated.resources.lessons_vocab_title
import evola.composeapp.generated.resources.lessons_word_mastered
import evola.composeapp.generated.resources.lessons_word_progress
import evola.composeapp.core.common.ChaseLoadingIndicator
import evola.composeapp.core.utils.SpeechService
import evola.composeapp.core.utils.rememberSpeechService
import evola.composeapp.core.designsystem.CenteredMessage
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.widget.rememberWidgetRefresher
import evola.shared.feature.profile.domain.AppSettings
import evola.shared.feature.vocabulary.domain.VocabularyCard
import evola.shared.feature.vocabulary.domain.VocabularySessionState
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** A due review's swipe labels differ from a still-learning word's - both are stored as
 * [VocabularyCard.Practice], distinguished by [evola.shared.feature.vocabulary.domain.VocabularySessionState.origin]. */
@Composable
internal fun statusPillLabel(origin: String, card: VocabularyCard): String = when {
    card is VocabularyCard.New -> stringResource(Res.string.lessons_vocab_status_new)
    origin == "due_review" -> stringResource(Res.string.lessons_vocab_status_review)
    else -> stringResource(Res.string.lessons_vocab_status_learning)
}

@Composable
internal fun VocabularySessionContent(
    state: VocabularySessionUiState,
    settings: AppSettings,
    speechService: SpeechService,
    onDone: () -> Unit,
    onUndo: (sessionId: String) -> Unit,
    onRetry: () -> Unit,
    onStartNextSession: () -> Unit,
    onMarkSwipeTutorialSeen: () -> Unit,
    onAlreadyKnown: (sessionId: String, itemId: String) -> Unit,
    onStartLearning: (sessionId: String, itemId: String) -> Unit,
    onToggleBookmark: (itemId: String, newValue: Boolean) -> Unit,
    onToggleDifficult: (itemId: String, newValue: Boolean) -> Unit,
    onExplain: (itemId: String) -> Unit,
    onSelfGrade: (sessionId: String, itemId: String, correct: Boolean) -> Unit,
    onKeepShowing: (sessionId: String, itemId: String) -> Unit,
    onSelectChoice: (sessionId: String, itemId: String, choice: String) -> Unit,
    onCheckTyped: (sessionId: String, itemId: String, response: String) -> Unit,
    onContinue: (sessionId: String, next: VocabularySessionState?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val justMasteredItemId = (state as? VocabularySessionUiState.InProgress)
        ?.takeIf { it.answered?.justMastered == true }?.session?.card?.itemId
    val wordMasteredMsg = stringResource(Res.string.lessons_word_mastered)
    LaunchedEffect(justMasteredItemId) {
        if (justMasteredItemId != null) snackbarHostState.showSnackbar(wordMasteredMsg)
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(Res.string.lessons_vocab_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.lessons_nav_close))
                        }
                    },
                    actions = {
                        (state as? VocabularySessionUiState.InProgress)?.let { inProgress ->
                            if (inProgress.canUndo) {
                                IconButton(onClick = { onUndo(inProgress.session.sessionId) }) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(Res.string.lessons_undo_last_answer), tint = EvolaColors.Accent)
                                }
                            }
                            Text(
                                statusPillLabel(inProgress.session.origin, inProgress.session.card),
                                style = MaterialTheme.typography.labelMedium,
                                color = EvolaColors.Text2,
                                modifier = Modifier.padding(end = EvolaSpacing.md),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                (state as? VocabularySessionUiState.InProgress)?.let { inProgress ->
                    val session = inProgress.session
                    val progressLabel = stringResource(Res.string.lessons_word_progress, session.wordIndex, session.totalWords)
                    Text(
                        progressLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = EvolaColors.Text3,
                        modifier = Modifier.padding(horizontal = EvolaSpacing.lg),
                    )
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    SegmentedProgressDashes(
                        total = session.totalWords.coerceAtLeast(1),
                        filled = session.wordIndex - 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.xs)
                            .semantics { contentDescription = progressLabel },
                    )
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is VocabularySessionUiState.Loading -> CenteredMessage { ChaseLoadingIndicator() }

                is VocabularySessionUiState.Error -> CenteredMessage {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(EvolaSpacing.lg))
                    Button(onClick = onRetry) { Text(stringResource(Res.string.lessons_action_retry)) }
                }

                is VocabularySessionUiState.Empty -> CenteredMessage {
                    Text(
                        stringResource(Res.string.lessons_study_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(EvolaSpacing.lg))
                    Button(onClick = onDone) { Text(stringResource(Res.string.lessons_action_done)) }
                }

                is VocabularySessionUiState.InProgress -> CardBody(
                    state = current,
                    settings = settings,
                    speechService = speechService,
                    onAlreadyKnown = onAlreadyKnown,
                    onStartLearning = onStartLearning,
                    onToggleBookmark = onToggleBookmark,
                    onToggleDifficult = onToggleDifficult,
                    onExplain = onExplain,
                    onSelfGrade = onSelfGrade,
                    onKeepShowing = onKeepShowing,
                    onSelectChoice = onSelectChoice,
                    onCheckTyped = onCheckTyped,
                    onContinue = onContinue,
                )

                is VocabularySessionUiState.Summary -> {
                    val refreshWidget = rememberWidgetRefresher()
                    LaunchedEffect(current.summary) { refreshWidget() }
                    SessionSummaryScreen(
                        summary = current.summary,
                        onContinueToNextSession = onStartNextSession,
                        onDone = onDone,
                    )
                }
            }

            if (state is VocabularySessionUiState.InProgress && !settings.hasSeenSwipeTutorial) {
                SwipeTutorialOverlay(invertSwipe = settings.invertSwipe, onDismiss = onMarkSwipeTutorialSeen)
            }
        }
    }
}

@Composable
private fun PreviewVocabularySessionContent(state: VocabularySessionUiState, settings: AppSettings = AppSettings(hasSeenSwipeTutorial = true)) {
    EvolaTheme {
        VocabularySessionContent(
            state = state,
            settings = settings,
            speechService = rememberSpeechService(),
            onDone = fakeVocabularySessionActions.onDone,
            onUndo = fakeVocabularySessionActions.onUndo,
            onRetry = fakeVocabularySessionActions.onRetry,
            onStartNextSession = fakeVocabularySessionActions.onStartNextSession,
            onMarkSwipeTutorialSeen = fakeVocabularySessionActions.onMarkSwipeTutorialSeen,
            onAlreadyKnown = fakeVocabularySessionActions.onAlreadyKnown,
            onStartLearning = fakeVocabularySessionActions.onStartLearning,
            onToggleBookmark = fakeVocabularySessionActions.onToggleBookmark,
            onToggleDifficult = fakeVocabularySessionActions.onToggleDifficult,
            onExplain = fakeVocabularySessionActions.onExplain,
            onSelfGrade = fakeVocabularySessionActions.onSelfGrade,
            onKeepShowing = fakeVocabularySessionActions.onKeepShowing,
            onSelectChoice = fakeVocabularySessionActions.onSelectChoice,
            onCheckTyped = fakeVocabularySessionActions.onCheckTyped,
            onContinue = fakeVocabularySessionActions.onContinue,
        )
    }
}

@Preview
@Composable
private fun VocabularySessionContentLoadingPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.Loading)
}

@Preview
@Composable
private fun VocabularySessionContentNewCardPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.InProgress(session = fakeNewCardSession))
}

@Preview
@Composable
private fun VocabularySessionContentPracticeCardPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.InProgress(session = fakePracticeCardSession))
}

@Preview
@Composable
private fun VocabularySessionContentEmptyPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.Empty)
}

@Preview
@Composable
private fun VocabularySessionContentErrorPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.Error("Couldn't load your session"))
}

@Preview
@Composable
private fun VocabularySessionContentSummaryPreview() {
    PreviewVocabularySessionContent(state = VocabularySessionUiState.Summary(fakeSessionSummary))
}
