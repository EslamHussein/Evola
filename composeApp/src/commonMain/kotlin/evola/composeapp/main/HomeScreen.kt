@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.EvolaTheme
import evola.composeapp.theme.components.RootTopBarTitle
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.Lesson
import evola.shared.goals.VocabularyBreakdown
import evola.shared.language.NativeLanguage
import evola.shared.vocabulary.SessionMode
import evola.shared.vocabulary.WordCategory
import kotlin.math.roundToInt
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.main_home_achievement_unlocked
import evola.composeapp.generated.resources.main_home_all_complete
import evola.composeapp.generated.resources.main_home_continue_lesson
import evola.composeapp.generated.resources.main_home_empty_body
import evola.composeapp.generated.resources.main_home_empty_cta
import evola.composeapp.generated.resources.main_home_empty_title
import evola.composeapp.generated.resources.main_home_retry
import evola.composeapp.generated.resources.main_home_title
import evola.composeapp.generated.resources.main_home_your_goal_label
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10). Three honest states: the encouraging
 * empty state when the goal has no lessons yet (never a broken 0% chart), a real readiness dial +
 * streak + "continue" CTA once there's something to study, and an all-complete celebration when
 * every lesson is done.
 *
 * The dashboard's own sections live in sibling files in this package - [HomeModesSection.kt] (spaced
 * repetition + extra modes), [HomeStatsSection.kt] (streak/activity strip), [HomeReadinessSection.kt]
 * (readiness ring + activity chart), [HomeWordBreakdownSection.kt] (mastery cards + nudge) - this
 * file is just the screen shell that composes them, kept separate so no single file mixes every
 * dashboard concern at once. */
@Composable
fun HomeScreen(
    goal: Goal,
    viewModel: HomeViewModel,
    onGoToMaterials: () -> Unit,
    onContinueLesson: (Lesson) -> Unit,
    onStartCategorySession: (WordCategory) -> Unit,
    onStartModeSession: (SessionMode) -> Unit,
    onStartHandsFree: (Lesson) -> Unit,
    onBrowseFlashcards: (Lesson) -> Unit,
) {
    val state by viewModel.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val newlyUnlockedBadges = (state as? HomeState.Loaded)?.progress?.newlyUnlockedBadges.orEmpty()
    val achievementMessages = newlyUnlockedBadges.map { badge -> stringResource(Res.string.main_home_achievement_unlocked, badge.title) }
    LaunchedEffect(newlyUnlockedBadges) {
        achievementMessages.forEach { message -> snackbarHostState.showSnackbar(message) }
    }

    HomeContent(
        goal = goal,
        state = state,
        snackbarHostState = snackbarHostState,
        onRetry = { viewModel.refresh() },
        onGoToMaterials = onGoToMaterials,
        onContinueLesson = onContinueLesson,
        onStartCategorySession = onStartCategorySession,
        onStartModeSession = onStartModeSession,
        onStartHandsFree = onStartHandsFree,
        onBrowseFlashcards = onBrowseFlashcards,
    )
}

@Composable
private fun HomeContent(
    goal: Goal,
    state: HomeState,
    snackbarHostState: SnackbarHostState,
    onRetry: () -> Unit,
    onGoToMaterials: () -> Unit,
    onContinueLesson: (Lesson) -> Unit,
    onStartCategorySession: (WordCategory) -> Unit,
    onStartModeSession: (SessionMode) -> Unit,
    onStartHandsFree: (Lesson) -> Unit,
    onBrowseFlashcards: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { RootTopBarTitle(stringResource(Res.string.main_home_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text(stringResource(Res.string.main_home_your_goal_label), style = MaterialTheme.typography.labelLarge, color = EvolaColors.Accent)
                Spacer(Modifier.height(EvolaSpacing.xs))
                Text(goal.goalText, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (state) {
                    is HomeState.Loading -> CenteredBox { ChaseLoadingIndicator() }

                    is HomeState.Error -> CenteredBox {
                        Text(state.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(EvolaSpacing.md))
                        Button(onClick = onRetry) { Text(stringResource(Res.string.main_home_retry)) }
                    }

                    is HomeState.Loaded ->
                        if (!state.hasLessons) {
                            EmptyState(onGoToMaterials)
                        } else {
                            DashboardBody(
                                progress = state.progress,
                                currentLesson = state.currentLesson,
                                onContinueLesson = onContinueLesson,
                                onStartCategorySession = onStartCategorySession,
                                onStartModeSession = onStartModeSession,
                                onStartHandsFree = onStartHandsFree,
                                onBrowseFlashcards = onBrowseFlashcards,
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun EmptyState(onGoToMaterials: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Text(stringResource(Res.string.main_home_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.main_home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(EvolaSpacing.md))
            Button(onClick = onGoToMaterials, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.main_home_empty_cta))
            }
        }
    }
}

@Composable
private fun DashboardBody(
    progress: GoalProgress,
    currentLesson: Lesson?,
    onContinueLesson: (Lesson) -> Unit,
    onStartCategorySession: (WordCategory) -> Unit,
    onStartModeSession: (SessionMode) -> Unit,
    onStartHandsFree: (Lesson) -> Unit,
    onBrowseFlashcards: (Lesson) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scrollable: the dashboard grew past a single screen once the weekly activity card was added
    // (5+ words, both breakdown cards, a nudge, and the CTAs no longer reliably fit shorter devices),
    // so the old "pin the CTA to the bottom via a weight(1f) spacer" trick - which requires a
    // bounded-height, non-scrolling Column - had to go; the CTAs are now just the last items in the
    // scrollable content instead of pinned.
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        // Section order mirrors Reword's own Home tab: Study -> Extra modes -> one combined Stats
        // card (day strip + streak tiles + Share), confirmed against a live screenshot of the real
        // app. The readiness ring/activity chart/word breakdown below have no Reword equivalent -
        // Evola-original additions, kept but placed after the matched section instead of interleaved
        // into it.
        SessionModesSection(progress, onStartModeSession)
        Spacer(Modifier.height(EvolaSpacing.lg))
        if (currentLesson != null) {
            ExtraModesSection(
                onBrowseFlashcards = { onBrowseFlashcards(currentLesson) },
                onStartHandsFree = { onStartHandsFree(currentLesson) },
            )
            Spacer(Modifier.height(EvolaSpacing.lg))
        }
        StatsSection(progress)
        Spacer(Modifier.height(EvolaSpacing.lg))
        TopTilesRow(percent = (progress.overallPct * 100).roundToInt(), vocabulary = progress.vocabulary)
        Spacer(Modifier.height(EvolaSpacing.lg))
        ActivityChartCard(progress)
        Spacer(Modifier.height(EvolaSpacing.lg))
        WordBreakdownSection(progress.vocabulary, onStartCategorySession)

        progress.nudgeWord?.let { nudge ->
            Spacer(Modifier.height(EvolaSpacing.lg))
            NudgeCard(nudge, onClick = { currentLesson?.let(onContinueLesson) })
        }

        Spacer(Modifier.height(EvolaSpacing.xxl))

        if (currentLesson != null) {
            OutlinedButton(
                onClick = { onContinueLesson(currentLesson) },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, EvolaColors.Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = EvolaColors.Accent),
            ) {
                Text(
                    stringResource(Res.string.main_home_continue_lesson, currentLesson.number, currentLesson.title),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(EvolaSpacing.sm))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = EvolaColors.Accent)
                Text(stringResource(Res.string.main_home_all_complete), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val fakeGoal = Goal(id = "g1", goalText = "Learn German for my trip to Berlin", title = "Berlin Trip", nativeLanguage = NativeLanguage.ENGLISH, isActive = true, createdAt = "2026-01-01")

private val fakeLesson = Lesson(id = "l1", number = 3, title = "Restaurants", status = "ready", vocabProgress = 0.6f, grammarProgress = 0.4f, grammarCount = 2)

private val fakeGoalProgress = GoalProgress(
    overallPct = 0.42f, currentLessonId = "l1", streakDays = 5, todayCompleted = false,
    vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
)

@Preview
@Composable
private fun HomeLoadingPreview() {
    EvolaTheme {
        HomeContent(
            goal = fakeGoal, state = HomeState.Loading, snackbarHostState = remember { SnackbarHostState() }, onRetry = {},
            onGoToMaterials = {}, onContinueLesson = {}, onStartCategorySession = {}, onStartModeSession = {}, onStartHandsFree = {}, onBrowseFlashcards = {},
        )
    }
}

@Preview
@Composable
private fun HomeLoadedPreview() {
    EvolaTheme {
        HomeContent(
            goal = fakeGoal,
            state = HomeState.Loaded(progress = fakeGoalProgress, currentLesson = fakeLesson, hasLessons = true),
            snackbarHostState = remember { SnackbarHostState() }, onRetry = {},
            onGoToMaterials = {}, onContinueLesson = {}, onStartCategorySession = {}, onStartModeSession = {}, onStartHandsFree = {}, onBrowseFlashcards = {},
        )
    }
}

@Preview
@Composable
private fun HomeEmptyPreview() {
    EvolaTheme {
        HomeContent(
            goal = fakeGoal,
            state = HomeState.Loaded(progress = fakeGoalProgress, currentLesson = null, hasLessons = false),
            snackbarHostState = remember { SnackbarHostState() }, onRetry = {},
            onGoToMaterials = {}, onContinueLesson = {}, onStartCategorySession = {}, onStartModeSession = {}, onStartHandsFree = {}, onBrowseFlashcards = {},
        )
    }
}

@Preview
@Composable
private fun HomeErrorPreview() {
    EvolaTheme {
        HomeContent(
            goal = fakeGoal, state = HomeState.Error("Something went wrong."), snackbarHostState = remember { SnackbarHostState() }, onRetry = {},
            onGoToMaterials = {}, onContinueLesson = {}, onStartCategorySession = {}, onStartModeSession = {}, onStartHandsFree = {}, onBrowseFlashcards = {},
        )
    }
}
