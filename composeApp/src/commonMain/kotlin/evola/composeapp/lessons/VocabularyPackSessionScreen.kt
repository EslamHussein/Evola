@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package evola.composeapp.lessons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.BackHandler
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.theme.components.SegmentedProgressBar
import evola.shared.vocabulary.PackWord
import evola.shared.vocabulary.VocabularyStageAnswerResult

private val STAGE_NAMES = listOf(
    "Discover", "Recognition", "Reverse Recall", "Partial Recall", "Sentence Completion", "Translation", "Free Production",
)
private const val STAGE_COUNT = 7

/** 7-stage-per-word pack session (design handoff Phase 7/8), replacing the flat drill-queue
 * `VocabularySessionScreen`. Exiting mid-word is always safe - the server durably tracks
 * pack/word/stage position, so the close button and system back gesture are wired to [onDone] in
 * every state, matching the resumable-session guarantee the old model had. */
@Composable
fun VocabularyPackSessionScreen(viewModel: VocabularyPackSessionViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onDone)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        val inProgress = state as? VocabularyPackSessionState.InProgress
                        Text(
                            if (inProgress != null) {
                                "Word ${inProgress.pack.wordIndex + 1} of ${inProgress.pack.wordsCount} · ${STAGE_NAMES[inProgress.pack.stageIndex]}"
                            } else {
                                "Vocabulary"
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                )
                (state as? VocabularyPackSessionState.InProgress)?.let { inProgress ->
                    SegmentedProgressBar(
                        segmentCount = STAGE_COUNT,
                        filledCount = inProgress.pack.stageIndex + 1,
                        modifier = Modifier.padding(horizontal = EvolaSpacing.lg, vertical = EvolaSpacing.xs),
                    )
                }
            }
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is VocabularyPackSessionState.Loading -> CenteredMessage { CircularProgressIndicator() }

                is VocabularyPackSessionState.Error -> CenteredMessage {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::retry) { Text("Retry") }
                }

                is VocabularyPackSessionState.Empty -> CenteredMessage {
                    Text(
                        "Nothing to study right now. Check back later or move on to the next lesson.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text("Done") }
                }

                is VocabularyPackSessionState.InProgress -> StageBody(current, viewModel)

                is VocabularyPackSessionState.Summary -> PackSummaryScreen(
                    summary = current.summary,
                    packNumber = current.packNumber,
                    onContinueToNextPack = viewModel::startNextPack,
                    onDone = onDone,
                )
            }
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
private fun StageBody(state: VocabularyPackSessionState.InProgress, viewModel: VocabularyPackSessionViewModel) {
    val word = state.pack.word
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EvolaSpacing.lg)) {
        when (state.pack.stageIndex) {
            0 -> DiscoverStage(
                word = word,
                onContinue = viewModel::continueFromDiscover,
                onToggleBookmark = viewModel::toggleBookmark,
                onToggleDifficult = viewModel::toggleDifficult,
            )
            1 -> RecognitionStage(word, state.selectedChoice, state.answered, onSelect = viewModel::selectRecognitionChoice, viewModel)
            2 -> TypedStage(
                prompt = { RtlText(word.meaningAr ?: word.meaning, style = MaterialTheme.typography.headlineMedium) },
                label = "der ...",
                expectedForFeedback = word.term,
                answered = state.answered,
                onCheck = viewModel::checkAnswer,
                viewModel = viewModel,
            )
            3 -> TypedStage(
                prompt = {
                    Text(
                        word.partialMask ?: word.term,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                label = "Type the full word",
                expectedForFeedback = word.term,
                answered = state.answered,
                onCheck = viewModel::checkAnswer,
                viewModel = viewModel,
            )
            4 -> TypedStage(
                prompt = {
                    Text(
                        blankedSentence(word.sentenceWithBlank ?: word.term),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                label = "Type the missing word",
                expectedForFeedback = word.term,
                answered = state.answered,
                onCheck = viewModel::checkAnswer,
                viewModel = viewModel,
                multiline = false,
            )
            5 -> TypedStage(
                prompt = {
                    Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            word.sentenceTranslationPrompt ?: word.exampleSentence.orEmpty(),
                            modifier = Modifier.padding(EvolaSpacing.md),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                },
                label = "Type the German translation",
                expectedForFeedback = word.exampleSentence,
                answered = state.answered,
                onCheck = viewModel::checkAnswer,
                viewModel = viewModel,
                multiline = true,
            )
            6 -> FreeProductionStage(word, state.answered, onCheck = viewModel::checkAnswer, viewModel)
        }
    }
}

@Composable
private fun AdvanceButton(answered: VocabularyStageAnswerResult?, viewModel: VocabularyPackSessionViewModel) {
    if (answered == null) return
    val finishing = answered.next?.readyToComplete == true
    Spacer(Modifier.height(EvolaSpacing.lg))
    Button(
        onClick = { if (finishing) viewModel.finishPack() else viewModel.continueToNext() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (finishing) "Finish pack" else "Continue")
    }
}

@Composable
private fun FeedbackBanner(correct: Boolean?, expected: String?) {
    if (correct == null) return
    val (bg, text) = if (correct) {
        EvolaColors.GoldSoft to "Correct!"
    } else {
        EvolaColors.RustSoft to if (expected != null) "Not quite - the answer was \"$expected\"." else "Not quite."
    }
    Spacer(Modifier.height(EvolaSpacing.md))
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(EvolaSpacing.md), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun genderBadgeLabel(gender: String?): String? = when (gender?.lowercase()) {
    "der" -> "m"
    "die" -> "f"
    "das" -> "n"
    else -> null
}

/** Circle (masculine) / diamond (feminine) / square (neuter) - shape carries the gender, never
 * color alone, per the design's explicit accessibility note. */
@Composable
private fun GenderBadge(gender: String?) {
    val label = genderBadgeLabel(gender) ?: return
    val shape = when (label) {
        "f" -> RoundedCornerShape(2.dp) // diamond approximated via a rotated square background below
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

@Composable
private fun DiscoverStage(
    word: PackWord,
    onContinue: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
) {
    var showExplanation by remember(word.itemId) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.Top) {
        GenderBadge(word.gender)
        Spacer(Modifier.width(EvolaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(word.term, style = MaterialTheme.typography.headlineMedium)
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (word.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (word.isBookmarked) EvolaColors.Gold else EvolaColors.Text3,
            )
        }
    }
    Spacer(Modifier.height(EvolaSpacing.sm))

    RtlText(word.meaningAr ?: word.meaning, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(EvolaSpacing.md))

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Audio playback isn't implemented yet - visual-only, per the locked out-of-scope
        // decision (no real TTS in this pass).
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play pronunciation (not yet available)", tint = EvolaColors.Text3)
        word.ipaPronunciation?.let {
            Spacer(Modifier.width(EvolaSpacing.sm))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        }
    }
    Spacer(Modifier.height(EvolaSpacing.md))

    word.exampleSentence?.let { sentence ->
        Surface(color = EvolaColors.SurfaceAlt, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(sentence, modifier = Modifier.padding(EvolaSpacing.md), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(EvolaSpacing.md))
    }

    if (word.relatedWords.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
            word.relatedWords.forEach { related ->
                Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.SurfaceAlt) {
                    Text(related, modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(EvolaSpacing.md))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        word.difficultyRating?.let {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.GoldSoft) {
                Row(modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        word.frequencyRating?.let {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = EvolaColors.Surface, border = BorderStroke(1.dp, EvolaColors.Border)) {
                Row(modifier = Modifier.padding(horizontal = EvolaSpacing.sm, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = EvolaColors.Text2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Spacer(Modifier.height(EvolaSpacing.md))

    // Reuses the single extracted memory_tip field for both the always-relevant hint and the
    // toggleable "AI explanation" - a deliberate simplification (see the plan's Phase 8 notes)
    // rather than a second AI-generated field/call for one toggle.
    word.memoryTip?.let { tip ->
        TextButton(onClick = { showExplanation = !showExplanation }) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = EvolaColors.Gold, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (showExplanation) "Hide AI explanation" else "Show AI explanation")
        }
        if (showExplanation) {
            Text(tip, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2, modifier = Modifier.padding(horizontal = EvolaSpacing.md))
        }
        Spacer(Modifier.height(EvolaSpacing.md))
    }

    OutlinedButton(onClick = onToggleDifficult, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = if (word.markedDifficult) EvolaColors.Rust else EvolaColors.Text3,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(if (word.markedDifficult) "Marked as difficult" else "Mark as difficult")
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun RecognitionStage(
    word: PackWord,
    selectedChoice: String?,
    answered: VocabularyStageAnswerResult?,
    onSelect: (String) -> Unit,
    viewModel: VocabularyPackSessionViewModel,
) {
    Text(word.term, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(EvolaSpacing.lg))

    val correctAnswer = word.meaningAr ?: word.meaning
    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
        word.recognitionChoices.forEach { choice ->
            val isCorrectChoice = choice == correctAnswer
            val isSelected = choice == selectedChoice
            val revealed = selectedChoice != null
            val (bg, border) = when {
                revealed && isCorrectChoice -> EvolaColors.GoldSoft to EvolaColors.Gold
                revealed && isSelected -> EvolaColors.SurfaceAlt to EvolaColors.Border
                else -> EvolaColors.Surface to EvolaColors.Border
            }
            Surface(
                onClick = { if (selectedChoice == null) onSelect(choice) },
                enabled = selectedChoice == null,
                shape = MaterialTheme.shapes.small,
                color = bg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RtlText(choice, modifier = Modifier.padding(EvolaSpacing.md))
            }
        }
    }

    AdvanceButton(answered, viewModel)
}

@Composable
private fun TypedStage(
    prompt: @Composable () -> Unit,
    label: String,
    expectedForFeedback: String?,
    answered: VocabularyStageAnswerResult?,
    onCheck: (String) -> Unit,
    viewModel: VocabularyPackSessionViewModel,
    multiline: Boolean = false,
) {
    var typedAnswer by remember(answered) { mutableStateOf("") }

    prompt()
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        OutlinedTextField(
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            label = { Text(label) },
            singleLine = !multiline,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(EvolaSpacing.md))
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text("Check")
        }
    } else {
        FeedbackBanner(answered.correct, expectedForFeedback)
    }

    AdvanceButton(answered, viewModel)
}

@Composable
private fun FreeProductionStage(
    word: PackWord,
    answered: VocabularyStageAnswerResult?,
    onCheck: (String) -> Unit,
    viewModel: VocabularyPackSessionViewModel,
) {
    var typedAnswer by remember(answered) { mutableStateOf("") }

    Text("Write an original sentence using \"${word.term}\".", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text("AI checks grammar and usage.", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        OutlinedTextField(
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            label = { Text("Your sentence") },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(EvolaSpacing.md))
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text("Check")
        }
    } else {
        Surface(color = EvolaColors.TealSoft, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(
                answered.feedback ?: "Thanks for your answer!",
                modifier = Modifier.padding(EvolaSpacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = EvolaColors.Teal,
            )
        }
    }

    AdvanceButton(answered, viewModel)
}

private fun blankedSentence(sentence: String): AnnotatedString = buildAnnotatedString {
    val idx = sentence.indexOf("___")
    if (idx < 0) {
        append(sentence)
        return@buildAnnotatedString
    }
    append(sentence.substring(0, idx))
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append("_____") }
    append(sentence.substring(idx + 3))
}
