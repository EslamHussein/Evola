package evola.composeapp.feature.vocabulary.ui

import evola.composeapp.feature.vocabulary.vm.VocabularySessionUiState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import evola.composeapp.speech.SpeechService
import evola.composeapp.speech.rememberSpeechService
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.local.AppSettings
import evola.shared.feature.vocabulary.domain.VocabularyCard
import evola.shared.feature.vocabulary.domain.VocabularySessionState
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Centers the (single) child like [Arrangement.Center], except the gap ABOVE it is capped at
 * [maxTopGapFraction] of the column's height - any extra leftover space falls below instead. A
 * short card (little/no optional content) would otherwise split its large leftover space evenly
 * into two big dead zones above and below; capping the top half biases it toward the top third,
 * which reads as "a card near the top of the screen" instead of "a card floating in a void". */
private fun cardBiasedArrangement(maxTopGapFraction: Float = 0.18f): Arrangement.Vertical =
    object : Arrangement.Vertical {
        override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
            val contentHeight = sizes.sum()
            val extraSpace = (totalSize - contentHeight).coerceAtLeast(0)
            val topGap = (extraSpace / 2).coerceAtMost((totalSize * maxTopGapFraction).toInt())
            var y = topGap
            for (i in sizes.indices) {
                outPositions[i] = y
                y += sizes[i]
            }
        }
    }

@Composable
internal fun CardBody(
    state: VocabularySessionUiState.InProgress,
    settings: AppSettings,
    speechService: SpeechService,
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
) {
    val focusManager = LocalFocusManager.current
    val card = state.session.card
    // Reword's "Automatically pronounce" - speaks the term as soon as a New card appears, on top
    // of (not instead of) the always-available manual tap-to-hear button. New cards only: a
    // Practice card's term is never sent to the client before grading (see this repo's ROADMAP.md),
    // so auto-speaking it would leak the answer the recall exercise is testing for.
    LaunchedEffect(card.itemId, settings.autoPronounce, settings.ttsEnabled) {
        if (settings.ttsEnabled && settings.autoPronounce && card is VocabularyCard.New) {
            speechService.speak(card.term, rate = settings.ttsRate, voiceName = settings.ttsVoiceName)
        }
    }
    // Wrapped in a bordered Card (previously the card content sat directly on the page background
    // with no visible boundary, and top-pinned in a plain Column - left most of the screen as dead
    // empty space below short content, confirmed by a live screenshot). Vertically placed via
    // [cardBiasedArrangement], not a plain Arrangement.Center: a short card (e.g. a New word with
    // no example sentence/related words/memory tip - all optional) left equally huge dead gaps
    // above AND below when centered, confirmed by a live screenshot. Capping the top gap biases
    // short cards toward the top third instead, while a tall card that fills the column still
    // behaves the same as before (no gap to cap). When content overflows (e.g. the typed-check
    // keyboard pushing things up), the Column simply scrolls and the arrangement has no effect,
    // which is the correct fallback either way.
    Column(
        modifier = Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() }
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(EvolaSpacing.lg),
        verticalArrangement = cardBiasedArrangement(),
    ) {
        // Reword's "Reduce motion" setting exists (Settings > Appearance) but is not wired to any
        // transition here - an AnimatedContent wrapping this when(card) was tried and reverted: it
        // collided with SwipeToDismissBox's own internal drag/dismiss state lifecycle inside
        // PracticeCard, producing overlapping/collapsed layouts on a real device (confirmed via a
        // live screenshot showing the card body missing and swipe labels overlapping). A working
        // session screen matters more than this animation; revisit with a safer approach (e.g.
        // keying SwipeToDismissBox's own remembered state on card.itemId explicitly, or animating
        // only the New/Practice card content rather than the whole subtree) rather than retrying the
        // same wrapping.
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, EvolaColors.Border),
        ) {
            Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
                when (card) {
                    is VocabularyCard.New -> NewCard(
                        card = card,
                        explainLoading = state.explainLoading,
                        invertSwipe = settings.invertSwipe,
                        showTranscription = settings.showTranscription,
                        onAlreadyKnown = { onAlreadyKnown(state.session.sessionId, card.itemId) },
                        onStartLearning = { onStartLearning(state.session.sessionId, card.itemId) },
                        onToggleBookmark = { onToggleBookmark(card.itemId, !card.isBookmarked) },
                        onToggleDifficult = { onToggleDifficult(card.itemId, !card.markedDifficult) },
                        onExplain = { onExplain(card.itemId) },
                        // A manual tap-to-hear is always available regardless of the "Speak words aloud"
                        // setting - that setting governs auto-play (hands-free mode's narration), not this
                        // explicit user action.
                        onPlayPronunciation = { speechService.speak(card.term, rate = settings.ttsRate, voiceName = settings.ttsVoiceName) },
                    )
                    is VocabularyCard.Practice -> PracticeCard(
                        card = card,
                        dueReview = state.session.origin == "due_review",
                        answered = state.answered,
                        invertSwipe = settings.invertSwipe,
                        keyboardExerciseEnabled = settings.keyboardExerciseEnabled,
                        multipleChoiceExerciseEnabled = settings.multipleChoiceExerciseEnabled,
                        onSelfGrade = { correct ->
                            onSelfGrade(state.session.sessionId, card.itemId, correct)
                        },
                        onKeepShowing = { onKeepShowing(state.session.sessionId, card.itemId) },
                        onSelectChoice = { choice ->
                            onSelectChoice(state.session.sessionId, card.itemId, choice)
                        },
                        onCheckTyped = { response ->
                            onCheckTyped(state.session.sessionId, card.itemId, response)
                        },
                        onContinue = { onContinue(state.session.sessionId, state.answered?.next) },
                        onToggleBookmark = { onToggleBookmark(card.itemId, !card.isBookmarked) },
                        onToggleDifficult = { onToggleDifficult(card.itemId, !card.markedDifficult) },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun CardBodyPreview() {
    EvolaTheme {
        CardBody(
            state = VocabularySessionUiState.InProgress(session = fakeNewCardSession),
            settings = AppSettings(hasSeenSwipeTutorial = true),
            speechService = rememberSpeechService(),
            onAlreadyKnown = { _, _ -> },
            onStartLearning = { _, _ -> },
            onToggleBookmark = { _, _ -> },
            onToggleDifficult = { _, _ -> },
            onExplain = {},
            onSelfGrade = { _, _, _ -> },
            onKeepShowing = { _, _ -> },
            onSelectChoice = { _, _, _ -> },
            onCheckTyped = { _, _, _ -> },
            onContinue = { _, _ -> },
        )
    }
}
