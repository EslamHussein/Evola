@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.RootTopBarTitle
import evola.shared.goals.DayActivity
import evola.shared.goals.Goal
import evola.shared.goals.GoalProgress
import evola.shared.goals.Lesson
import evola.shared.goals.NudgeWord
import evola.shared.goals.VocabularyBreakdown
import evola.shared.vocabulary.SessionMode
import evola.shared.vocabulary.WordCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

/** Home tab / Progress Dashboard (01_PRODUCT_SPEC.md §1.10). Three honest states: the encouraging
 * empty state when the goal has no lessons yet (never a broken 0% chart), a real readiness dial +
 * streak + "continue" CTA once there's something to study, and an all-complete celebration when
 * every lesson is done. */
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
    val state by viewModel.subscribe()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val newlyUnlockedBadges = (state as? HomeState.Loaded)?.progress?.newlyUnlockedBadges.orEmpty()
    LaunchedEffect(newlyUnlockedBadges) {
        newlyUnlockedBadges.forEach { badge -> snackbarHostState.showSnackbar("Achievement unlocked: ${badge.title}") }
    }

    Scaffold(
        topBar = { TopAppBar(title = { RootTopBarTitle("Home") }) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
                Text("Your goal", style = MaterialTheme.typography.labelLarge, color = EvolaColors.Accent)
                Spacer(Modifier.height(4.dp))
                Text(goal.goalText, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(EvolaSpacing.xl))

                when (val current = state) {
                    is HomeState.Loading -> CenteredBox { ChaseLoadingIndicator() }

                    is HomeState.Error -> CenteredBox {
                        Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(EvolaSpacing.md))
                        Button(onClick = { viewModel.intent(HomeIntent.Refresh) }) { Text("Retry") }
                    }

                    is HomeState.Loaded ->
                        if (!current.hasLessons) {
                            EmptyState(onGoToMaterials)
                        } else {
                            DashboardBody(
                                progress = current.progress,
                                currentLesson = current.currentLesson,
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
            Text("No lessons yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                "Upload a book or study material and Evola will generate lessons for you.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(EvolaSpacing.md))
            Button(onClick = onGoToMaterials, modifier = Modifier.fillMaxWidth()) {
                Text("Upload a material")
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
                    "Continue Lesson ${currentLesson.number}: ${currentLesson.title}",
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
                Text("You've completed every lesson!", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** Reword's Home "Spaced repetition" section - three rows with real counts, each a plain multi-word
 * session across the whole goal (see [evola.shared.vocabulary.SessionMode]) rather than tied to any
 * one lesson. Mirrors Reword's structure - own wording, own icon language. A row with nothing
 * available (0 due, or 0 new against today's daily-goal remainder) is still shown, just non-tappable,
 * same "always visible, disabled when empty" convention [MasteryCard] already uses. */
@Composable
private fun SessionModesSection(progress: GoalProgress, onStartModeSession: (SessionMode) -> Unit) {
    val newRemaining = (progress.dailyGoal - progress.todayNewWordsLearned).coerceAtLeast(0)
    Text("Study", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            ModeRow(
                Icons.Filled.Add, EvolaColors.Rust, "Learn new words", "Learned today: ${progress.todayNewWordsLearned} of ${progress.dailyGoal}",
                onClick = { onStartModeSession(SessionMode.NEW_ONLY) }.takeIf { newRemaining > 0 },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            ModeRow(
                Icons.Filled.History, EvolaColors.Amber, "Review words", "Words to review: ${progress.wordsToReviewCount}",
                onClick = { onStartModeSession(SessionMode.REVIEW_ONLY) }.takeIf { progress.wordsToReviewCount > 0 },
            )
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            ModeRow(
                Icons.Filled.Lightbulb, EvolaColors.Accent, "Mixed mode", "Both new words and words for review will appear",
                onClick = { onStartModeSession(SessionMode.MIXED) }.takeIf { newRemaining > 0 || progress.wordsToReviewCount > 0 },
            )
        }
    }
}

/** Reword's row icon treatment - a small outlined circle around the icon, tinted per-row (not just
 * a plain bare icon), confirmed against a live screenshot. [badgeColor] is the row's own identity
 * color, dimmed to [EvolaColors.Text3] on both the ring and the icon when the row is disabled -
 * same disabled convention [ModeRow]'s text already used. */
@Composable
private fun ModeRow(icon: ImageVector, badgeColor: Color, title: String, subtitle: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(EvolaSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (onClick != null) badgeColor else EvolaColors.Text3
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, tint, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (onClick != null) EvolaColors.Text else EvolaColors.Text3)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        }
    }
}

/** Reword's "Extra modes (do not affect stats)" section - Browse flashcards / Hands-free, both
 * already-existing features surfaced here as their own Home rows instead of buried under the
 * "Continue Lesson" CTA, matching Reword's placement. Scoped to [currentLesson] same as the CTA
 * below it, since neither feature has a goal-wide entry point today. */
@Composable
private fun ExtraModesSection(onBrowseFlashcards: () -> Unit, onStartHandsFree: () -> Unit) {
    Text("Extra modes", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(2.dp))
    Text("Doesn't affect your streak or stats", style = MaterialTheme.typography.labelSmall, color = EvolaColors.Text3)
    Spacer(Modifier.height(EvolaSpacing.md))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBrowseFlashcards).padding(EvolaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text("Browse flashcards", style = MaterialTheme.typography.titleSmall)
            }
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onStartHandsFree).padding(EvolaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text("Hands-free mode", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** Colors shared across the mini ring, the word-breakdown tiles, and the nudge chip, so every
 * "mastered/learning/not started" signal on the dashboard reads as one system. */
private val MasteredColor: Color @Composable get() = EvolaColors.Accent
private val LearningColor: Color @Composable get() = EvolaColors.Ink2
private val NotStartedColor: Color @Composable get() = EvolaColors.Text3

/** Evola-original addition, no Reword equivalent - kept (per this dashboard's own earlier design
 * note: "a stronger design than Reword's own streak-only view") but no longer paired with a
 * duplicate streak tile now that [StatsSection] owns all streak content on its own, matching
 * Reword's structure. */
@Composable
private fun TopTilesRow(percent: Int, vocabulary: VocabularyBreakdown) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReadinessRing(percent = percent, vocabulary = vocabulary)
            Spacer(Modifier.width(EvolaSpacing.md))
            Text("Exam readiness", style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text2)
        }
    }
}

/** Reword's "Stats" section - a day-of-week strip (already built as [WeeklyStreakStrip]), two big
 * Current/Best streak tiles, and a Share row, all in one card, matching the real app's structure
 * (confirmed against a live screenshot). [evola.composeapp.share.rememberShareText] is the same
 * platform share sheet Profile's own "Share progress" row uses. */
@Composable
private fun StatsSection(progress: GoalProgress) {
    val shareText = evola.composeapp.share.rememberShareText()
    Text("Stats", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
            if (progress.weeklyActivity.isNotEmpty()) {
                WeeklyStreakStrip(progress.weeklyActivity)
                Spacer(Modifier.height(EvolaSpacing.lg))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
                StreakTile("Current streak", progress.streakDays, modifier = Modifier.weight(1f))
                StreakTile("Best streak", progress.bestStreakDays, modifier = Modifier.weight(1f))
            }
            if (progress.streakFreezesAvailable > 0) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AcUnit, contentDescription = null, tint = EvolaColors.Text3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(EvolaSpacing.xs))
                    Text(
                        "${progress.streakFreezesAvailable} streak freeze${if (progress.streakFreezesAvailable == 1) "" else "s"} available",
                        style = MaterialTheme.typography.labelSmall,
                        color = EvolaColors.Text3,
                    )
                }
            }
            Spacer(Modifier.height(EvolaSpacing.md))
            Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        shareText(
                            if (progress.streakDays > 0) {
                                "I'm on a ${progress.streakDays}-day streak learning German on Evola!"
                            } else {
                                "I'm learning German on Evola!"
                            },
                        )
                    }
                    .padding(top = EvolaSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = EvolaColors.Text2)
                Spacer(Modifier.width(EvolaSpacing.md))
                Text("Share", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun StreakTile(label: String, days: Int, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.medium) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text2)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$days", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(4.dp))
                Text(if (days == 1) "day" else "days", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
            }
        }
    }
}

/** Evola-original addition, no Reword equivalent (Reword's own Stats card has no stacked bar chart)
 * - the "Learned today X/Y" readout moved into [SessionModesSection]'s "Learn new words" row instead
 * of appearing twice. */
@Composable
private fun ActivityChartCard(progress: GoalProgress) {
    if (progress.weeklyActivity.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg)) {
            Text("This week's activity", style = MaterialTheme.typography.titleSmall, color = EvolaColors.Text2)
            Spacer(Modifier.height(EvolaSpacing.md))
            ActivityChart(progress.weeklyActivity)
        }
    }
}

/** Compact readiness dial (80dp): a 3-segment ring - mastered, then learning, remainder unfilled -
 * so the ring itself shows what the % is made of, with the number centered inside it. */
@Composable
private fun ReadinessRing(percent: Int, vocabulary: VocabularyBreakdown, modifier: Modifier = Modifier) {
    val clamped = percent.coerceIn(0, 100)
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    val masteredColor = MasteredColor
    val learningColor = LearningColor
    val notStartedColor = NotStartedColor
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            fun arc(color: Color, startAngle: Float, sweepAngle: Float) {
                if (sweepAngle <= 0f) return
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            arc(notStartedColor, -90f, 360f)
            if (total > 0) {
                val masteredSweep = vocabulary.mastered / total.toFloat() * 360f
                val learningSweep = vocabulary.inProgress / total.toFloat() * 360f
                arc(masteredColor, -90f, masteredSweep)
                arc(learningColor, -90f + masteredSweep, learningSweep)
            }
        }
        Text("$clamped%", style = MaterialTheme.typography.titleLarge)
    }
}

/** "Word breakdown" header (with the goal's total word count as a pill) plus three red/yellow/
 * green cards - a re-cut of the same words by how they're actually going, not just SRS status:
 * red = the most recent answer was wrong (needs attention now), green = mastered, yellow = touched
 * at least once but still building up. "Not started" (unseen) words don't count as "learning" -
 * they're part of the total pill but don't get their own card, matching [ReadinessRing]'s ring
 * (which also treats unseen separately). A word can't land in both red and green - any wrong answer
 * demotes it out of "mastered" immediately (see VocabularySrs.onIncorrect) - so the three cards
 * always sum to less than or equal to the total, the gap being untouched words. */
@Composable
private fun WordBreakdownSection(vocabulary: VocabularyBreakdown, onStartCategorySession: (WordCategory) -> Unit) {
    val total = vocabulary.notStarted + vocabulary.inProgress + vocabulary.mastered
    val struggling = vocabulary.struggling
    val mastered = vocabulary.mastered
    val learning = (vocabulary.inProgress - struggling).coerceAtLeast(0)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Word breakdown", style = MaterialTheme.typography.titleMedium)
        Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.extraLarge) {
            Text(
                "$total words total",
                style = MaterialTheme.typography.labelMedium,
                color = EvolaColors.Text2,
                modifier = Modifier.padding(horizontal = EvolaSpacing.md, vertical = EvolaSpacing.xs),
            )
        }
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        MasteryCard(
            Icons.Filled.TrackChanges, "Needs practice", "Review soon", struggling, total,
            EvolaColors.Rust, EvolaColors.RustSoft,
            onClick = { onStartCategorySession(WordCategory.STRUGGLING) }.takeIf { struggling > 0 },
        )
        MasteryCard(
            Icons.AutoMirrored.Filled.MenuBook, "Learning", "Keep it up", learning, total,
            EvolaColors.Amber, EvolaColors.AmberSoft,
            onClick = { onStartCategorySession(WordCategory.LEARNING) }.takeIf { learning > 0 },
        )
        MasteryCard(
            Icons.Filled.EmojiEvents, "Mastered", "Well done!", mastered, total,
            EvolaColors.Teal, EvolaColors.TealSoft,
            onClick = { onStartCategorySession(WordCategory.MASTERED) }.takeIf { mastered > 0 },
        )
    }
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(
        "Progress updates as you learn",
        style = MaterialTheme.typography.labelMedium,
        color = EvolaColors.Text3,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

/** One card of the red/yellow/green breakdown: an icon avatar, title/subtitle, count + share of
 * the total, and a thin progress track. [total] of 0 (no vocabulary yet) renders an empty track
 * rather than dividing by zero. [onClick] is null (and the card non-interactive) when [count] is
 * 0 - nothing to practice in that category yet. */
@Composable
private fun MasteryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    count: Int,
    total: Int,
    color: Color,
    softColor: Color,
    onClick: (() -> Unit)?,
) {
    val fraction = if (total > 0) count / total.toFloat() else 0f
    val percent = if (total > 0) (count * 100f / total).roundToInt() else 0
    Card(
        onClick = onClick ?: {},
        modifier = Modifier.fillMaxWidth(),
        enabled = onClick != null,
        colors = CardDefaults.cardColors(containerColor = EvolaColors.SurfaceAlt),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(softColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color)
            }
            Spacer(Modifier.width(EvolaSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = color)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                Spacer(Modifier.height(EvolaSpacing.sm))
                Box(
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(EvolaColors.Surface),
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(color),
                    )
                }
            }
            Spacer(Modifier.width(EvolaSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.xs)) {
                Text("$count", style = MaterialTheme.typography.titleMedium, color = color)
                Text("|", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Border)
                Text("$percent%", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
            }
        }
    }
}

/** Tappable nudge toward the single word closest to mastered - a concrete, low-effort next step
 * rather than the abstract percentage alone. Routes into the current lesson's vocabulary session,
 * same destination as the main CTA, since the session engine doesn't target a single word. */
@Composable
private fun NudgeCard(nudge: NudgeWord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(EvolaSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(EvolaColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.TrackChanges, contentDescription = null, tint = EvolaColors.Ink2)
            }
            Spacer(Modifier.height(EvolaSpacing.sm))
            val reviewWord = if (nudge.reviewsRemaining == 1) "review" else "reviews"
            Text(
                "${nudge.reviewsRemaining} $reviewWord from mastering \"${nudge.term}\"",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(EvolaSpacing.xs))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EvolaColors.Accent)
        }
    }
}

/** Seven circles, oldest day first, today last - filled + a day-of-week initial when that day had
 * any completed session, outlined otherwise. Same "which days did I show up" signal as Reword's own
 * weekly strip, built from [evola.shared.local.LocalGoalsRepository]'s per-day [DayActivity] list
 * rather than a separate calendar widget. */
@Composable
private fun WeeklyStreakStrip(days: List<DayActivity>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { index, day ->
            val date = runCatching { LocalDate.parse(day.date) }.getOrNull()
            val label = date?.dayOfWeek?.let(::dayInitial) ?: "?"
            // Oldest-first, today last (see this list's own construction) - Reword highlights the
            // selected day with a filled circle and a small triangle pointer underneath; today is
            // this strip's equivalent "selected" day, marked the same way regardless of hadActivity
            // so today is always visually findable, not just days with a completed session.
            val isToday = index == days.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                        .background(if (day.hadActivity || isToday) EvolaColors.Accent else EvolaColors.Surface)
                        .border(1.dp, if (day.hadActivity || isToday) Color.Transparent else EvolaColors.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.hadActivity || isToday) Color.White else EvolaColors.Text3,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(if (isToday) EvolaColors.Accent else Color.Transparent))
            }
        }
    }
}

private fun dayInitial(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "M"
    DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"
    DayOfWeek.THURSDAY -> "T"
    DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "S"
    else -> "?"
}

/** Stacked new-words/reviewed bar chart, one bar per [days] entry - the mastered/learning color
 * pairing from [MasteredColor]/[LearningColor] reused here (new words = mastered-color's accent,
 * reviews = the softer ink) so the chart reads as part of the same visual system as the ring above
 * it instead of introducing a third color pairing. */
@Composable
private fun ActivityChart(days: List<DayActivity>) {
    // A week with literally zero activity used to render as thin flat hairlines with no other
    // signal on the card - easy to misread as broken rather than "nothing yet". A caption instead
    // says so directly, matching how the rest of this dashboard never shows an ambiguous empty chart.
    if (days.all { it.newWordsLearned == 0 && it.wordsReviewed == 0 }) {
        Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            Text("No activity yet this week", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        }
        return
    }
    val maxCount = days.maxOf { it.newWordsLearned + it.wordsReviewed }.coerceAtLeast(1)
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEach { day ->
            val total = day.newWordsLearned + day.wordsReviewed
            val barFraction = (total / maxCount.toFloat()).coerceIn(0f, 1f)
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                if (total == 0) {
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(EvolaColors.Border))
                } else {
                    // A single bar, its own height proportional to the day's total - internally
                    // split top(reviewed)/bottom(new) by weight, so the two segments' heights stay
                    // proportional to each other regardless of the outer bar's overall height.
                    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(barFraction)) {
                        if (day.wordsReviewed > 0) {
                            Box(modifier = Modifier.fillMaxWidth().weight(day.wordsReviewed.toFloat()).background(EvolaColors.Ink2))
                        }
                        if (day.newWordsLearned > 0) {
                            Box(modifier = Modifier.fillMaxWidth().weight(day.newWordsLearned.toFloat()).background(EvolaColors.Accent))
                        }
                    }
                }
            }
        }
    }
}
