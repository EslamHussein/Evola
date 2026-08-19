package evola.composeapp.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import evola.composeapp.generated.resources.Res
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.StarterLesson
import evola.shared.vocabulary.StarterLevel
import evola.shared.vocabulary.VocabularyRepository
import evola.shared.vocabulary.decodeStarterLevels
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Reword's onboarding level/lesson picker - see [evola.shared.vocabulary.StarterLevel]'s own doc
 * comment. Levels are loaded once, lazily, from bundled JSON assets (the real "Das Leben" A1/A2
 * glossaries) rather than hardcoded Kotlin - large enough now (hundreds of words) that holding them
 * as literal data would be unwieldy, and this also means updating the bundled content later never
 * needs a code change. A1 and A2 aren't mutually exclusive, and neither are a level's own lessons -
 * this is a plain multi-select across every [StarterLesson] in every level. A level with only one
 * lesson renders as a single flat checkbox; a level with several renders as a [TriStateCheckbox]
 * header - checked/empty/indeterminate reflecting whether all, none, or some of its own lessons are
 * selected, and tapping it toggles all of them at once - plus the individually checkable lessons
 * below it. Entirely optional: "Skip" and "Continue" both proceed, the only difference is whether
 * any lessons get created first. Each checked lesson becomes one ordinary lesson via
 * [VocabularyRepository.createStarterLesson], called with that lesson's own title/words directly -
 * the repository has no copy of this data to look up by id. */
@Composable
fun CategoryPickerScreen(goalId: String, onContinue: () -> Unit) {
    val vocabularyRepository = koinInject<VocabularyRepository>()
    val coroutineScope = rememberCoroutineScope()
    var levels by remember { mutableStateOf<List<StarterLevel>?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val jsonTexts = listOf("files/starter_a1.json", "files/starter_a2.json").mapNotNull { path ->
            runCatching { Res.readBytes(path).decodeToString() }.getOrNull()
        }
        levels = decodeStarterLevels(jsonTexts)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val currentLevels = levels
        if (currentLevels == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Surface
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Text("Want a running start?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick a level to add as a starter lesson - or skip and build your own from your own materials.",
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Text2,
            )
            Spacer(Modifier.height(24.dp))
            currentLevels.forEach { level ->
                LevelCard(
                    level = level,
                    selected = selected,
                    onToggleLesson = { lessonId -> selected = if (lessonId in selected) selected - lessonId else selected + lessonId },
                    onToggleLevel = { allSelected ->
                        val ids = level.lessons.map { it.id }.toSet()
                        selected = if (allSelected) selected - ids else selected + ids
                    },
                )
                Spacer(Modifier.height(EvolaSpacing.sm))
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    isSubmitting = true
                    coroutineScope.launch {
                        currentLevels.forEach { level ->
                            level.lessons.forEach { lesson ->
                                if (lesson.id in selected) {
                                    // "A2" alone when the level has just one lesson; "A2 · Kapitel 1: ..."
                                    // once a level splits into several, so the created lesson's own
                                    // title still says which level it's from.
                                    val title = if (level.lessons.size == 1) level.title else "${level.title} · ${lesson.title}"
                                    vocabularyRepository.createStarterLesson(goalId, title, lesson.words)
                                }
                            }
                        }
                        isSubmitting = false
                        onContinue()
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (selected.isEmpty()) "Continue" else "Add ${selected.size} and continue")
            }
            if (selected.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onContinue, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun LevelCard(
    level: StarterLevel,
    selected: Set<String>,
    onToggleLesson: (String) -> Unit,
    onToggleLevel: (allSelected: Boolean) -> Unit,
) {
    val selectedCount = level.lessons.count { it.id in selected }
    val allSelected = selectedCount == level.lessons.size
    val toggleableState = when {
        selectedCount == 0 -> ToggleableState.Off
        allSelected -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(EvolaSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggleLevel(allSelected) },
            ) {
                if (level.lessons.size == 1) {
                    Checkbox(checked = selectedCount > 0, onCheckedChange = { onToggleLevel(allSelected) })
                } else {
                    TriStateCheckbox(state = toggleableState, onClick = { onToggleLevel(allSelected) })
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(level.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (level.lessons.size == 1) {
                            "${level.subtitle} · ${level.lessons.first().words.size} words"
                        } else {
                            "${level.subtitle} - $selectedCount of ${level.lessons.size} lessons selected"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedCount > 0) EvolaColors.Accent else EvolaColors.Text3,
                    )
                }
            }
            if (level.lessons.size > 1) {
                Spacer(Modifier.height(EvolaSpacing.sm))
                Surface(color = EvolaColors.Border, modifier = Modifier.fillMaxWidth().height(1.dp)) {}
                Spacer(Modifier.height(EvolaSpacing.sm))
                level.lessons.forEach { lesson ->
                    val checked = lesson.id in selected
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleLesson(lesson.id) }.padding(vertical = 4.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggleLesson(lesson.id) })
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(lesson.title, style = MaterialTheme.typography.bodyMedium)
                            Text("${lesson.words.size} words", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
                        }
                    }
                }
            }
        }
    }
}
