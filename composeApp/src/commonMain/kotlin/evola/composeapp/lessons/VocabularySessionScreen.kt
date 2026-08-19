@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.lessons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.BackHandler
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_already_know
import evola.composeapp.generated.resources.lessons_action_done
import evola.composeapp.generated.resources.lessons_action_got_it
import evola.composeapp.generated.resources.lessons_action_keep_showing
import evola.composeapp.generated.resources.lessons_action_memorized
import evola.composeapp.generated.resources.lessons_action_missed_it
import evola.composeapp.generated.resources.lessons_action_play_pronunciation
import evola.composeapp.generated.resources.lessons_action_retry
import evola.composeapp.generated.resources.lessons_action_start_learning
import evola.composeapp.generated.resources.lessons_ai_explain
import evola.composeapp.generated.resources.lessons_check
import evola.composeapp.generated.resources.lessons_content_desc_bookmark
import evola.composeapp.generated.resources.lessons_content_desc_more
import evola.composeapp.generated.resources.lessons_continue
import evola.composeapp.generated.resources.lessons_exercise_choose_options
import evola.composeapp.generated.resources.lessons_exercise_reveal_answer
import evola.composeapp.generated.resources.lessons_exercise_type_answer
import evola.composeapp.generated.resources.lessons_feedback_correct
import evola.composeapp.generated.resources.lessons_feedback_incorrect
import evola.composeapp.generated.resources.lessons_finish_session
import evola.composeapp.generated.resources.lessons_marked_difficult
import evola.composeapp.generated.resources.lessons_mark_difficult
import evola.composeapp.generated.resources.lessons_menu_bookmark
import evola.composeapp.generated.resources.lessons_menu_remove_bookmark
import evola.composeapp.generated.resources.lessons_menu_unmark_difficult
import evola.composeapp.generated.resources.lessons_nav_close
import evola.composeapp.generated.resources.lessons_new_word_label
import evola.composeapp.generated.resources.lessons_study_empty
import evola.composeapp.generated.resources.lessons_tutorial_know_it
import evola.composeapp.generated.resources.lessons_tutorial_missed_it
import evola.composeapp.generated.resources.lessons_tutorial_start_learning
import evola.composeapp.generated.resources.lessons_tutorial_swipe_hint
import evola.composeapp.generated.resources.lessons_tutorial_tap_hint
import evola.composeapp.generated.resources.lessons_undo_last_answer
import evola.composeapp.generated.resources.lessons_vocab_status_learning
import evola.composeapp.generated.resources.lessons_vocab_status_new
import evola.composeapp.generated.resources.lessons_vocab_status_review
import evola.composeapp.generated.resources.lessons_vocab_title
import evola.composeapp.generated.resources.lessons_whats_the_word_for
import evola.composeapp.generated.resources.lessons_word_mastered
import evola.composeapp.generated.resources.lessons_word_progress
import org.jetbrains.compose.resources.stringResource

/** A due review's swipe labels differ from a still-learning word's - both are stored as
 * [VocabularyCard.Practice], distinguished by [evola.shared.vocabulary.VocabularySessionState.origin]. */
@Composable
private fun statusPillLabel(origin: String, card: VocabularyCard): String = when {
    card is VocabularyCard.New -> stringResource(Res.string.lessons_vocab_status_new)
    origin == "due_review" -> stringResource(Res.string.lessons_vocab_status_review)
    else -> stringResource(Res.string.lessons_vocab_status_learning)
}

/** Vocabulary session screen: a persisted, priority-ordered SRS queue rendered as swipeable cards,
 * Reword-style. A brand-new word is swiped left ("I already know this") or right ("start learning
 * it"); a still-learning or due-review word is swiped left ("got it") or right ("missed it"/"keep
 * showing"), or checked via a typed or multiple-choice input instead. Exiting mid-card is always
 * safe - the repository durably tracks queue position, so the close button and system back gesture
 * are wired to [onDone] in every state, matching the resumable-session guarantee this app's other
 * sessions already have. */
@Composable
fun VocabularySessionScreen(viewModel: VocabularySessionViewModel, onDone: () -> Unit) {
    val state by viewModel.subscribe()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val speechService = evola.composeapp.speech.rememberSpeechService()
    BackHandler(onBack = onDone)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val justMasteredItemId = (state as? VocabularySessionUiState.InProgress)
        ?.takeIf { it.answered?.justMastered == true }?.session?.card?.itemId
    val wordMasteredMsg = stringResource(Res.string.lessons_word_mastered)
    LaunchedEffect(justMasteredItemId) {
        if (justMasteredItemId != null) snackbarHostState.showSnackbar(wordMasteredMsg)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
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
                                IconButton(onClick = { viewModel.intent(VocabularySessionIntent.UndoLastGrade(inProgress.session.sessionId)) }) {
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
                    Text(
                        stringResource(Res.string.lessons_word_progress, session.wordIndex, session.totalWords),
                        style = MaterialTheme.typography.labelSmall,
                        color = EvolaColors.Text3,
                        modifier = Modifier.padding(horizontal = EvolaSpacing.lg),
                    )
                    Spacer(Modifier.height(EvolaSpacing.xs))
                    SegmentedProgressDashes(
                        total = session.totalWords.coerceAtLeast(1),
                        filled = session.wordIndex - 1,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.xs),
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
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.intent(VocabularySessionIntent.Retry) }) { Text(stringResource(Res.string.lessons_action_retry)) }
                }

                is VocabularySessionUiState.Empty -> CenteredMessage {
                    Text(
                        stringResource(Res.string.lessons_study_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text(stringResource(Res.string.lessons_action_done)) }
                }

                is VocabularySessionUiState.InProgress -> CardBody(current, viewModel, settings, speechService)

                is VocabularySessionUiState.Summary -> {
                    val refreshWidget = evola.composeapp.widget.rememberWidgetRefresher()
                    LaunchedEffect(current.summary) { refreshWidget() }
                    SessionSummaryScreen(
                        summary = current.summary,
                        onContinueToNextSession = { viewModel.intent(VocabularySessionIntent.StartNextSession) },
                        onDone = onDone,
                    )
                }
            }

            if (state is VocabularySessionUiState.InProgress && !settings.hasSeenSwipeTutorial) {
                SwipeTutorialOverlay(invertSwipe = settings.invertSwipe, onDismiss = viewModel::markSwipeTutorialSeen)
            }
        }
    }
}

/** Shown once, ever, the first time a learner reaches an in-progress card - Reword's own onboarding
 * has an equivalent swipe walkthrough; this app's content is lesson-scoped rather than pre-loaded
 * decks so there's no separate category-picker step to hang it off, but the gesture itself is
 * identical and just as non-obvious on first use. Dismissed by tapping anywhere, which flips
 * [evola.shared.local.LocalSettingsRepository.setHasSeenSwipeTutorial] so it never shows again. */
@Composable
private fun SwipeTutorialOverlay(invertSwipe: Boolean, onDismiss: () -> Unit) {
    val missedItNotYetLabel = stringResource(Res.string.lessons_tutorial_missed_it)
    val iKnowItLabel = stringResource(Res.string.lessons_tutorial_know_it)
    val startLearningShortLabel = stringResource(Res.string.lessons_tutorial_start_learning)
    val knowItLabel = if (invertSwipe) missedItNotYetLabel else iKnowItLabel
    val learnItLabel = if (invertSwipe) iKnowItLabel else startLearningShortLabel
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xCC1C1E27))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(EvolaSpacing.xl),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = EvolaColors.Rust,
                modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = 90f },
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(knowItLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Spacer(Modifier.height(EvolaSpacing.xl))
            Text(
                stringResource(Res.string.lessons_tutorial_swipe_hint),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            Text(
                stringResource(Res.string.lessons_tutorial_tap_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = EvolaColors.Gold,
                modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = 270f },
            )
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(learnItLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

/** Reword's segmented progress strip - one dash per distinct word in the session (matching the
 * already-shown "Word X of Y" text), filled up to [filled]. Reads at a glance far better than a
 * single continuous bar once there are only a handful of words. */
@Composable
private fun SegmentedProgressDashes(total: Int, filled: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) EvolaColors.Accent else EvolaColors.AccentSoft),
            )
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun CardBody(
    state: VocabularySessionUiState.InProgress,
    viewModel: VocabularySessionViewModel,
    settings: evola.shared.local.AppSettings,
    speechService: evola.composeapp.speech.SpeechService,
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
    // empty space below short content, confirmed by a live screenshot). Centered vertically via
    // Arrangement.Center on the scrollable Column: when content fits, it centers in the available
    // space same as Reword's own bounded card; when content overflows (e.g. the typed-check keyboard
    // pushing things up), the Column simply scrolls and Center has no effect, which is the correct
    // fallback either way.
    Column(
        modifier = Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() }
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(EvolaSpacing.lg),
        verticalArrangement = Arrangement.Center,
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
                        onAlreadyKnown = { viewModel.intent(VocabularySessionIntent.SubmitAlreadyKnown(state.session.sessionId, card.itemId)) },
                        onStartLearning = { viewModel.intent(VocabularySessionIntent.SubmitStartLearning(state.session.sessionId, card.itemId)) },
                        onToggleBookmark = { viewModel.intent(VocabularySessionIntent.ToggleBookmark(card.itemId, !card.isBookmarked)) },
                        onToggleDifficult = { viewModel.intent(VocabularySessionIntent.ToggleDifficult(card.itemId, !card.markedDifficult)) },
                        onExplain = { viewModel.intent(VocabularySessionIntent.ExplainWord(card.itemId)) },
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
                            viewModel.intent(VocabularySessionIntent.SubmitSelfGrade(state.session.sessionId, card.itemId, correct))
                        },
                        onKeepShowing = { viewModel.intent(VocabularySessionIntent.SubmitKeepShowing(state.session.sessionId, card.itemId)) },
                        onSelectChoice = { choice ->
                            viewModel.intent(VocabularySessionIntent.SubmitChoice(state.session.sessionId, card.itemId, choice))
                        },
                        onCheckTyped = { response ->
                            viewModel.intent(VocabularySessionIntent.SubmitTyped(state.session.sessionId, card.itemId, response))
                        },
                        onContinue = { viewModel.intent(VocabularySessionIntent.ContinueToNext(state.session.sessionId, state.answered?.next)) },
                        onToggleBookmark = { viewModel.intent(VocabularySessionIntent.ToggleBookmark(card.itemId, !card.isBookmarked)) },
                        onToggleDifficult = { viewModel.intent(VocabularySessionIntent.ToggleDifficult(card.itemId, !card.markedDifficult)) },
                    )
                }
            }
        }
    }
}

private fun genderBadgeLabel(gender: String?): String? = when (gender?.lowercase()) {
    "der" -> "m"
    "die" -> "f"
    "das" -> "n"
    else -> null
}

/** Some already-extracted rows have the article baked into [term] itself (a pre-existing extraction
 * slip - see [evola.shared.ai.VocabularyExtractor]); guards this render site so it doesn't show up
 * twice ("Der Der Hund") when we prepend [gender] below. */
private fun termWithoutDuplicateArticle(term: String, gender: String?): String {
    if (gender.isNullOrBlank()) return term
    val prefix = "$gender "
    return if (term.startsWith(prefix, ignoreCase = true)) term.substring(prefix.length) else term
}

/** der = blue, die = red, das = green - a common learner mnemonic for the article. */
@Composable
private fun articleColor(gender: String?): Color? = when (gender?.lowercase()) {
    "der" -> EvolaColors.GenderMasculine
    "die" -> EvolaColors.GenderFeminine
    "das" -> EvolaColors.GenderNeuter
    else -> null
}

/** Circle (masculine) / diamond (feminine) / square (neuter) - shape carries the gender, never
 * color alone, per the design's explicit accessibility note. */
@Composable
private fun GenderBadge(gender: String?) {
    val label = genderBadgeLabel(gender) ?: return
    val shape = when (label) {
        "f" -> RoundedCornerShape(2.dp)
        "n" -> RoundedCornerShape(6.dp)
        else -> CircleShape
    }
    Box(
        modifier = Modifier.size(32.dp).clip(shape).background(EvolaColors.GoldSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = EvolaColors.Gold)
    }
}

/** Wraps [content] in a two-direction swipe gesture that fires [onSwipeLeft]/[onSwipeRight] on
 * commit and always snaps back to settled (this screen advances by replacing the card's state, not
 * by animating the row away) - same "swipe reveals/triggers, doesn't remove itself" convention as
 * [evola.composeapp.materials.MaterialsListScreen]'s delete swipe. [leftLabel]/[rightLabel] are also
 * plain tappable text, matching Reword's persistent bottom affordance and giving a non-gesture path
 * to the same actions. */
@Composable
private fun SwipeGradeCard(
    leftLabel: String,
    rightLabel: String,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    invertSwipe: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Settings > Invert swipe direction: swaps which physical side triggers which action, without
    // the caller needing to know or care - the label under a given thumb position always still
    // matches the action it triggers.
    val effectiveLeftLabel = if (invertSwipe) rightLabel else leftLabel
    val effectiveRightLabel = if (invertSwipe) leftLabel else rightLabel
    val effectiveOnSwipeLeft = if (invertSwipe) onSwipeRight else onSwipeLeft
    val effectiveOnSwipeRight = if (invertSwipe) onSwipeLeft else onSwipeRight

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> effectiveOnSwipeRight()
                SwipeToDismissBoxValue.EndToStart -> effectiveOnSwipeLeft()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val (label, color) = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> effectiveRightLabel to EvolaColors.Gold
                SwipeToDismissBoxValue.EndToStart -> effectiveLeftLabel to EvolaColors.Rust
                SwipeToDismissBoxValue.Settled -> "" to Color.Transparent
            }
            Box(modifier = Modifier.fillMaxSize().background(color), contentAlignment = Alignment.Center) {
                if (label.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(horizontal = EvolaSpacing.lg))
                }
            }
        },
    ) {
        Surface { content() }
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            effectiveLeftLabel,
            style = MaterialTheme.typography.labelLarge,
            color = EvolaColors.Rust,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f).clickable(onClick = effectiveOnSwipeLeft),
        )
        Text(
            effectiveRightLabel,
            style = MaterialTheme.typography.labelLarge,
            color = EvolaColors.Gold,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).clickable(onClick = effectiveOnSwipeRight),
        )
    }
}

@Composable
private fun NewCard(
    card: VocabularyCard.New,
    explainLoading: Boolean,
    invertSwipe: Boolean,
    showTranscription: Boolean,
    onAlreadyKnown: () -> Unit,
    onStartLearning: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
    onExplain: () -> Unit,
    onPlayPronunciation: () -> Unit,
) {
    SwipeGradeCard(
        leftLabel = stringResource(Res.string.lessons_action_already_know),
        rightLabel = stringResource(Res.string.lessons_action_start_learning),
        onSwipeLeft = onAlreadyKnown,
        onSwipeRight = onStartLearning,
        invertSwipe = invertSwipe,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(EvolaSpacing.xs))
                Text(stringResource(Res.string.lessons_new_word_label), style = MaterialTheme.typography.labelMedium, color = EvolaColors.Text2)
            }
            Spacer(Modifier.height(EvolaSpacing.md))

            Row(verticalAlignment = Alignment.Top) {
                GenderBadge(card.gender)
                Spacer(Modifier.width(EvolaSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            card.gender?.let { gender ->
                                articleColor(gender)?.let { color ->
                                    withStyle(SpanStyle(color = color)) { append("$gender ") }
                                } ?: append("$gender ")
                            }
                            append(termWithoutDuplicateArticle(card.term, card.gender))
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = stringResource(Res.string.lessons_content_desc_bookmark),
                        tint = if (card.isBookmarked) EvolaColors.Gold else EvolaColors.Text3,
                    )
                }
            }

            val posLine = listOfNotNull(card.partOfSpeech, card.plural?.let { "Pl. $it" }).joinToString(" · ")
            if (posLine.isNotEmpty()) {
                Text(posLine, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            }
            Spacer(Modifier.height(EvolaSpacing.sm))

            RtlText(card.meaning, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.md))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPronunciation, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(Res.string.lessons_action_play_pronunciation), tint = EvolaColors.Accent)
                }
                if (showTranscription) {
                    card.ipaPronunciation?.let {
                        Spacer(Modifier.width(EvolaSpacing.sm))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                    }
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))

            card.exampleSentence?.let { sentence ->
                Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(EvolaSpacing.md)) {
                        Text(sentence, style = MaterialTheme.typography.bodyMedium)
                        card.exampleSentenceTranslation?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
                        }
                    }
                }
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            card.grammarNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            if (card.relatedWords.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                    card.relatedWords.forEach { related ->
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
                            Text(related, modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                card.difficultyRating?.let {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.GoldSoft) {
                        Row(modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Speed, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                card.frequencyRating?.let {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.Surface, border = BorderStroke(1.dp, EvolaColors.Border)) {
                        Text(it, modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))

            card.memoryTip?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                Spacer(Modifier.height(EvolaSpacing.md))
            }

            val aiExplanation = card.aiExplanation
            if (aiExplanation == null) {
                OutlinedButton(onClick = onExplain, enabled = !explainLoading, modifier = Modifier.fillMaxWidth()) {
                    if (explainLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EvolaColors.Accent)
                        Spacer(Modifier.width(EvolaSpacing.sm))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(stringResource(Res.string.lessons_ai_explain))
                }
            } else {
                Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(EvolaSpacing.md)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(EvolaSpacing.sm))
                        Text(aiExplanation, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))

            OutlinedButton(onClick = onToggleDifficult, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (card.markedDifficult) EvolaColors.Rust else EvolaColors.Text3,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(if (card.markedDifficult) stringResource(Res.string.lessons_marked_difficult) else stringResource(Res.string.lessons_mark_difficult))
            }
        }
    }
}

/** Which non-swipe check, if any, is currently active for a [VocabularyCard.Practice] card. Resets
 * every time the card's [VocabularyCard.itemId] changes (a fresh card always starts back on the
 * plain swipe). */
private enum class ExerciseMode { TYPED, CHOICE }

@Composable
private fun PracticeCard(
    card: VocabularyCard.Practice,
    dueReview: Boolean,
    answered: VocabularyAnswerResult?,
    invertSwipe: Boolean,
    keyboardExerciseEnabled: Boolean,
    multipleChoiceExerciseEnabled: Boolean,
    onSelfGrade: (Boolean) -> Unit,
    onKeepShowing: () -> Unit,
    onSelectChoice: (String) -> Unit,
    onCheckTyped: (String) -> Unit,
    onContinue: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
) {
    var mode by remember(card.itemId) { mutableStateOf<ExerciseMode?>(null) }
    var overflowExpanded by remember(card.itemId) { mutableStateOf(false) }
    val leftLabel = if (dueReview) stringResource(Res.string.lessons_action_got_it) else stringResource(Res.string.lessons_action_memorized)
    val rightLabel = if (dueReview) stringResource(Res.string.lessons_action_missed_it) else stringResource(Res.string.lessons_action_keep_showing)

    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.lessons_whats_the_word_for), style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            Spacer(Modifier.height(EvolaSpacing.xs))
            RtlText(card.meaning, style = MaterialTheme.typography.headlineMedium)
        }
        Box {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.lessons_content_desc_more), tint = EvolaColors.Text3)
            }
            androidx.compose.material3.DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (card.isBookmarked) stringResource(Res.string.lessons_menu_remove_bookmark) else stringResource(Res.string.lessons_menu_bookmark)) },
                    onClick = { overflowExpanded = false; onToggleBookmark() },
                    leadingIcon = { Icon(if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = null) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (card.markedDifficult) stringResource(Res.string.lessons_menu_unmark_difficult) else stringResource(Res.string.lessons_mark_difficult)) },
                    onClick = { overflowExpanded = false; onToggleDifficult() },
                    leadingIcon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                )
            }
        }
    }
    Spacer(Modifier.height(EvolaSpacing.sm))
    card.grammarNote?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
        Spacer(Modifier.height(EvolaSpacing.sm))
    }
    Spacer(Modifier.height(EvolaSpacing.md))

    when (mode) {
        null -> {
            if (answered == null) {
                SwipeGradeCard(
                    leftLabel = leftLabel,
                    rightLabel = rightLabel,
                    onSwipeLeft = { onSelfGrade(true) },
                    onSwipeRight = { if (dueReview) onSelfGrade(false) else onKeepShowing() },
                    invertSwipe = invertSwipe,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.md)) {
                        if (keyboardExerciseEnabled) {
                            ExerciseIconButton(Icons.Filled.Keyboard, stringResource(Res.string.lessons_exercise_type_answer)) { mode = ExerciseMode.TYPED }
                        }
                        // Reword's "peek" icon - revealing the answer always grades this attempt as
                        // a miss (not knowing it well enough to answer is exactly what asking to see
                        // it first means), reusing the same graded self-grade path a swipe-right on a
                        // due review already uses, rather than a separate ungraded reveal.
                        ExerciseIconButton(Icons.Filled.Visibility, stringResource(Res.string.lessons_exercise_reveal_answer)) { onSelfGrade(false) }
                        if (multipleChoiceExerciseEnabled) {
                            ExerciseIconButton(Icons.Filled.GridView, stringResource(Res.string.lessons_exercise_choose_options)) { mode = ExerciseMode.CHOICE }
                        }
                    }
                }
            } else {
                RevealedInlineSentence(prefix = "", word = answered.correctAnswer ?: "", suffix = "", correct = answered.correct == true)
                Spacer(Modifier.height(EvolaSpacing.sm))
                FeedbackNote(answered)
                AdvanceButton(answered, onContinue)
            }
        }
        ExerciseMode.TYPED -> TypedCheck(card, answered, onCheckTyped, onContinue)
        ExerciseMode.CHOICE -> ChoiceCheck(card, answered, onSelectChoice, onContinue)
    }
}

@Composable
private fun ExerciseIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = EvolaColors.Surface,
        border = BorderStroke(1.dp, EvolaColors.Border),
    ) {
        Icon(icon, contentDescription = description, tint = EvolaColors.Text2, modifier = Modifier.padding(EvolaSpacing.md).size(20.dp))
    }
}

/** Typed-recall check for a [VocabularyCard.Practice] card, reachable via the keyboard icon instead
 * of the plain swipe. Grades on "Check"; correctness feeds the same SRS transition a graded swipe
 * would. */
@Composable
private fun TypedCheck(
    card: VocabularyCard.Practice,
    answered: VocabularyAnswerResult?,
    onCheck: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var typedAnswer by remember(card.itemId, answered) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    if (answered == null) {
        InlineFillSentence(
            prefix = "",
            suffix = "",
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            onDone = { focusManager.clearFocus() },
        )
    } else {
        RevealedInlineSentence(prefix = "", word = answered.correctAnswer ?: typedAnswer, suffix = "", correct = answered.correct == true)
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text(stringResource(Res.string.lessons_check))
        }
    } else {
        FeedbackNote(answered)
        AdvanceButton(answered, onContinue)
    }
}

/** Multiple-choice check for a [VocabularyCard.Practice] card, reachable via the grid icon instead
 * of the plain swipe. Grades on tap; correctness feeds the same SRS transition a graded swipe
 * would. */
@Composable
private fun ChoiceCheck(
    card: VocabularyCard.Practice,
    answered: VocabularyAnswerResult?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var selected by remember(card.itemId, answered) { mutableStateOf<String?>(null) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm), verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        card.choices.forEach { choice ->
            val revealed = answered != null
            val isCorrectChoice = revealed && choice == answered.correctAnswer
            val isSelectedWrong = revealed && choice == selected && choice != answered.correctAnswer
            val (bg, border) = when {
                isCorrectChoice -> EvolaColors.GoldSoft to EvolaColors.Gold
                isSelectedWrong -> EvolaColors.RustSoft to EvolaColors.Rust
                else -> EvolaColors.Surface to EvolaColors.Border
            }
            Surface(
                onClick = {
                    if (!revealed) {
                        selected = choice
                        onSelect(choice)
                    }
                },
                enabled = !revealed,
                shape = MaterialTheme.shapes.extraLarge,
                color = bg,
                border = BorderStroke(1.dp, border),
            ) {
                Text(choice, modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.sm))
            }
        }
    }

    if (answered != null) {
        Spacer(Modifier.height(EvolaSpacing.md))
        FeedbackNote(answered)
    }

    AdvanceButton(answered, onContinue)
}

@Composable
private fun AdvanceButton(answered: VocabularyAnswerResult?, onContinue: () -> Unit) {
    if (answered == null) return
    Spacer(Modifier.height(EvolaSpacing.lg))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(if (answered.next == null) stringResource(Res.string.lessons_finish_session) else stringResource(Res.string.lessons_continue))
    }
}

@Composable
private fun FeedbackNote(answered: VocabularyAnswerResult) {
    val correct = answered.correct ?: return
    val (bg, text) = if (correct) {
        EvolaColors.GoldSoft to stringResource(Res.string.lessons_feedback_correct)
    } else {
        EvolaColors.RustSoft to stringResource(Res.string.lessons_feedback_incorrect, answered.correctAnswer ?: "")
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            answered.completedSentence?.let {
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            }
        }
    }
}

/** Typed recall rendered inline within the sentence itself - the blank IS the text field, styled
 * to match the surrounding headline text, rather than a separate boxed Material field below. */
@Composable
private fun InlineFillSentence(
    prefix: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val style = MaterialTheme.typography.headlineSmall
    val underlineColor = EvolaColors.Accent
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text(prefix, style = style)
        Box(
            modifier = Modifier.widthIn(min = 56.dp).drawBehind {
                drawLine(
                    color = underlineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = style.copy(color = EvolaColors.Accent, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(EvolaColors.Accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.widthIn(min = 56.dp).padding(bottom = 2.dp),
            )
        }
        Text(suffix, style = style)
    }
}

/** Answer revealed inline (correct term highlighted within the sentence) once graded, matching
 * [InlineFillSentence]'s layout so the sentence doesn't visually jump between typing and reveal. */
@Composable
private fun RevealedInlineSentence(prefix: String, word: String, suffix: String, correct: Boolean) {
    val color = if (correct) EvolaColors.Gold else EvolaColors.Rust
    Text(
        buildAnnotatedString {
            append(prefix)
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(word) }
            append(suffix)
        },
        style = MaterialTheme.typography.headlineSmall,
    )
}
