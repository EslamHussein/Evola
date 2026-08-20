package evola.composeapp.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.onboarding_category_add_and_continue
import evola.composeapp.generated.resources.onboarding_category_all_selected
import evola.composeapp.generated.resources.onboarding_category_collapse
import evola.composeapp.generated.resources.onboarding_category_continue
import evola.composeapp.generated.resources.onboarding_category_description
import evola.composeapp.generated.resources.onboarding_category_expand
import evola.composeapp.generated.resources.onboarding_category_lessons_selected
import evola.composeapp.generated.resources.onboarding_category_prompt
import evola.composeapp.generated.resources.onboarding_category_skip
import evola.composeapp.generated.resources.onboarding_category_some_selected
import evola.composeapp.generated.resources.onboarding_category_subtitle_words
import evola.composeapp.generated.resources.onboarding_category_words_count
import evola.composeapp.generated.resources.onboarding_level_lesson_title
import evola.composeapp.core.designsystem.EvolaColors
import evola.composeapp.core.designsystem.EvolaSpacing
import evola.composeapp.core.designsystem.EvolaTheme
import evola.shared.feature.vocabulary.domain.StarterLesson
import evola.shared.feature.vocabulary.domain.StarterLevel
import evola.shared.feature.vocabulary.domain.StarterWord
import evola.shared.feature.vocabulary.domain.VocabularyRepository
import evola.shared.feature.vocabulary.domain.decodeStarterLevels
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

/** Reword's onboarding level/lesson picker - see [evola.shared.feature.vocabulary.domain.StarterLevel]'s own doc
 * comment. Levels are loaded once, lazily, from bundled JSON assets (the real "Das Leben" A1/A2
 * glossaries) rather than hardcoded Kotlin - large enough now (hundreds of words) that holding them
 * as literal data would be unwieldy, and this also means updating the bundled content later never
 * needs a code change. A1 and A2 aren't mutually exclusive, and neither are a level's own lessons -
 * this is a plain multi-select across every [StarterLesson] in every level. A level with only one
 * lesson renders as a single flat checkbox; a level with several renders as a collapsible tree -
 * a tri-state square (empty/minus/check) that selects or clears every one of its lessons at once,
 * plus a chevron that expands to the individually checkable lessons below it. Tapping the square
 * toggles selection; tapping the rest of the header row toggles expansion - two separate gestures,
 * so opening a level to look inside never silently selects everything in it. A level auto-expands
 * the first time one of its lessons gets selected, so the user can see what they picked. Entirely
 * optional: "Skip" and "Continue" both proceed, the only difference is whether any lessons get
 * created first. Each checked lesson becomes one ordinary lesson via
 * [VocabularyRepository.createStarterLesson], called with that lesson's own title/words directly -
 * the repository has no copy of this data to look up by id. */
@Composable
fun CategoryPickerScreen(goalId: String, onContinue: () -> Unit) {
    val vocabularyRepository = koinInject<VocabularyRepository>()
    val coroutineScope = rememberCoroutineScope()
    var levels by remember { mutableStateOf<List<StarterLevel>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var expandedLevels by remember { mutableStateOf(setOf<String>()) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val jsonTexts = listOf("files/starter_a1.json", "files/starter_a2.json").mapNotNull { path ->
            runCatching { Res.readBytes(path).decodeToString() }.getOrNull()
        }
        levels = decodeStarterLevels(jsonTexts)
    }

    CategoryPickerContent(
        levels = levels,
        selected = selected,
        onSelectedChange = { selected = it },
        expandedLevels = expandedLevels,
        onExpandedLevelsChange = { expandedLevels = it },
        isSubmitting = isSubmitting,
        onContinue = { currentLevels, levelLessonTitles ->
            isSubmitting = true
            coroutineScope.launch {
                currentLevels.forEach { level ->
                    level.lessons.forEach { lesson ->
                        if (lesson.id in selected) {
                            // "A2" alone when the level has just one lesson; "A2 · Kapitel 1: ..."
                            // once a level splits into several, so the created lesson's own
                            // title still says which level it's from.
                            val title = if (level.lessons.size == 1) level.title else levelLessonTitles.getValue(lesson.id)
                            vocabularyRepository.createStarterLesson(goalId, title, lesson.words)
                        }
                    }
                }
                isSubmitting = false
                onContinue()
            }
        },
        onSkip = onContinue,
    )
}

@Composable
private fun CategoryPickerContent(
    levels: List<StarterLevel>?,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
    expandedLevels: Set<String>,
    onExpandedLevelsChange: (Set<String>) -> Unit,
    isSubmitting: Boolean,
    onContinue: (levels: List<StarterLevel>, levelLessonTitles: Map<String, String>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        if (levels == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }
        // Pre-resolved here (composable context) since it's needed inside the non-composable
        // onClick/launch below - stringResource can't be called from there directly.
        val levelLessonTitles = buildMap {
            levels.forEach { level ->
                if (level.lessons.size > 1) {
                    level.lessons.forEach { lesson ->
                        put(lesson.id, stringResource(Res.string.onboarding_level_lesson_title, level.title, lesson.title))
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EvolaSpacing.xl),
        ) {
            Text(stringResource(Res.string.onboarding_category_prompt), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(EvolaSpacing.sm))
            Text(
                stringResource(Res.string.onboarding_category_description),
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(EvolaSpacing.xl))
            levels.forEach { level ->
                LevelCard(
                    level = level,
                    selected = selected,
                    expanded = level.id in expandedLevels,
                    onToggleExpanded = {
                        onExpandedLevelsChange(if (level.id in expandedLevels) expandedLevels - level.id else expandedLevels + level.id)
                    },
                    onToggleLesson = { lessonId ->
                        val nowSelected = lessonId !in selected
                        onSelectedChange(if (nowSelected) selected + lessonId else selected - lessonId)
                        if (nowSelected) onExpandedLevelsChange(expandedLevels + level.id)
                    },
                    onToggleLevel = { allSelected ->
                        val ids = level.lessons.map { it.id }.toSet()
                        onSelectedChange(if (allSelected) selected - ids else selected + ids)
                        if (!allSelected) onExpandedLevelsChange(expandedLevels + level.id)
                    },
                )
                Spacer(Modifier.height(EvolaSpacing.sm))
            }
            Spacer(Modifier.height(EvolaSpacing.lg))
            Button(
                onClick = { onContinue(levels, levelLessonTitles) },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (selected.isEmpty()) {
                        stringResource(Res.string.onboarding_category_continue)
                    } else {
                        stringResource(Res.string.onboarding_category_add_and_continue, selected.size)
                    },
                )
            }
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                TextButton(onClick = onSkip, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.onboarding_category_skip))
                }
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: StarterLevel,
    selected: Set<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleLesson: (String) -> Unit,
    onToggleLevel: (allSelected: Boolean) -> Unit,
) {
    val selectedCount = level.lessons.count { it.id in selected }
    val allSelected = selectedCount == level.lessons.size
    val isMultiLesson = level.lessons.size > 1

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.lg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = isMultiLesson) { onToggleExpanded() },
            ) {
                if (isMultiLesson) {
                    TriStateSquare(selectedCount = selectedCount, total = level.lessons.size, onClick = { onToggleLevel(allSelected) })
                } else {
                    Checkbox(checked = selectedCount > 0, onCheckedChange = { onToggleLevel(allSelected) })
                }
                Spacer(Modifier.width(EvolaSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(level.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (isMultiLesson) {
                            stringResource(Res.string.onboarding_category_lessons_selected, level.subtitle, selectedCount, level.lessons.size)
                        } else {
                            stringResource(Res.string.onboarding_category_subtitle_words, level.subtitle, level.lessons.first().words.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedCount > 0) EvolaColors.Accent else EvolaColors.Text3,
                    )
                }
                if (isMultiLesson) {
                    Icon(
                        Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) {
                            stringResource(Res.string.onboarding_category_collapse)
                        } else {
                            stringResource(Res.string.onboarding_category_expand)
                        },
                        tint = EvolaColors.Text3,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (isMultiLesson && expanded) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                Spacer(Modifier.height(EvolaSpacing.sm))
                level.lessons.forEach { lesson ->
                    val checked = lesson.id in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (checked) EvolaColors.AccentSoft else Color.Transparent)
                            .clickable { onToggleLesson(lesson.id) }
                            .padding(horizontal = EvolaSpacing.sm, vertical = EvolaSpacing.sm),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleLesson(lesson.id) })
                        Spacer(Modifier.width(EvolaSpacing.sm))
                        Column {
                            Text(lesson.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(Res.string.onboarding_category_words_count, lesson.words.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = EvolaColors.Text3,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tri-state select-all indicator for a level's header - a filled square (like the reference
 * design this replaced a plain [androidx.compose.material3.TriStateCheckbox] with) rather than a
 * checkbox, so it reads as a tree control distinct from the individually checkable lesson rows
 * below it. */
@Composable
private fun TriStateSquare(selectedCount: Int, total: Int, onClick: () -> Unit) {
    val isOn = selectedCount == total
    val isIndeterminate = selectedCount in 1 until total
    val isFilled = isOn || isIndeterminate
    Box(
        modifier = Modifier.size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFilled) {
                    Modifier.background(EvolaColors.Accent)
                } else {
                    Modifier.border(1.5.dp, EvolaColors.Text3, RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isOn -> Icon(
                Icons.Filled.Check,
                contentDescription = stringResource(Res.string.onboarding_category_all_selected),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            isIndeterminate -> Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(Res.string.onboarding_category_some_selected),
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun fakeStarterWords(count: Int) = List(count) { i -> StarterWord(term = "Wort $i", meaning = "word $i") }

private val fakeStarterLevels = listOf(
    StarterLevel(
        id = "a1", title = "A1", subtitle = "Beginner",
        lessons = listOf(StarterLesson(id = "a1-1", title = "A1", words = fakeStarterWords(60))),
    ),
    StarterLevel(
        id = "a2", title = "A2", subtitle = "Elementary",
        lessons = listOf(
            StarterLesson(id = "a2-1", title = "Kapitel 1: Alltag", words = fakeStarterWords(30)),
            StarterLesson(id = "a2-2", title = "Kapitel 2: Reisen", words = fakeStarterWords(25)),
        ),
    ),
)

@Preview
@Composable
private fun CategoryPickerContentLoadingPreview() {
    EvolaTheme {
        CategoryPickerContent(
            levels = null,
            selected = emptySet(),
            onSelectedChange = {},
            expandedLevels = emptySet(),
            onExpandedLevelsChange = {},
            isSubmitting = false,
            onContinue = { _, _ -> },
            onSkip = {},
        )
    }
}

@Preview
@Composable
private fun CategoryPickerContentLoadedPreview() {
    EvolaTheme {
        CategoryPickerContent(
            levels = fakeStarterLevels,
            selected = setOf("a2-1"),
            onSelectedChange = {},
            expandedLevels = setOf("a2"),
            onExpandedLevelsChange = {},
            isSubmitting = false,
            onContinue = { _, _ -> },
            onSkip = {},
        )
    }
}
