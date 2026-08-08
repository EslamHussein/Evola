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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import evola.composeapp.loading.ChaseLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import evola.composeapp.BackHandler
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.shared.vocabulary.VocabularyAnswerResult
import evola.shared.vocabulary.VocabularyCard

/** Ladder step labels, in order, matching evola.shared.local's card_type sequence for a new word. */
private val LADDER_LABELS = listOf("Discover", "Recognition", "Word Bank", "Hint", "Blind")

private fun ladderStepIndex(card: VocabularyCard): Int = when (card) {
    is VocabularyCard.Intro -> 0
    is VocabularyCard.Recognition -> 1
    is VocabularyCard.WordBank -> 2
    is VocabularyCard.Hint -> 3
    is VocabularyCard.Blind -> 4
}

/** Vocabulary session screen: a persisted, priority-ordered SRS queue. New words walk their own
 * 5-step difficulty ladder (Intro -> Recognition -> WordBank -> Hint -> Blind); due-for-review words
 * skip straight to Blind. Exiting mid-card is always safe - the repository durably tracks queue
 * position, so the close button and system back gesture are wired to [onDone] in every state,
 * matching the resumable-session guarantee this app's other sessions already have. */
@Composable
fun VocabularySessionScreen(viewModel: VocabularySessionViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onDone)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Vocabulary") },
                    navigationIcon = {
                        IconButton(onClick = onDone) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        (state as? VocabularySessionUiState.InProgress)?.let { inProgress ->
                            val tag = when (inProgress.session.origin) {
                                "new" -> "New word"
                                "repeat" -> "Repeat"
                                else -> "Review"
                            }
                            Text(tag, style = MaterialTheme.typography.labelMedium, color = EvolaColors.Text2, modifier = Modifier.padding(end = EvolaSpacing.md))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
                (state as? VocabularySessionUiState.InProgress)?.let { inProgress ->
                    val session = inProgress.session
                    if (session.origin == "new") {
                        val step = ladderStepIndex(session.card)
                        Text(
                            "Step ${step + 1} of ${LADDER_LABELS.size} · ${LADDER_LABELS[step]}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EvolaColors.Text3,
                            modifier = Modifier.padding(horizontal = EvolaSpacing.lg),
                        )
                    }
                    val total = (session.cardsCompleted + session.cardsRemaining).coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { session.cardsCompleted / total.toFloat() },
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
                    Button(onClick = viewModel::retry) { Text("Retry") }
                }

                is VocabularySessionUiState.Empty -> CenteredMessage {
                    Text(
                        "Nothing to study right now. Check back later or move on to the next lesson.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onDone) { Text("Done") }
                }

                is VocabularySessionUiState.InProgress -> CardBody(current, viewModel)

                is VocabularySessionUiState.Summary -> SessionSummaryScreen(
                    summary = current.summary,
                    onContinueToNextSession = viewModel::startNextSession,
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
private fun CardBody(state: VocabularySessionUiState.InProgress, viewModel: VocabularySessionViewModel) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier.fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusManager.clearFocus() }
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(EvolaSpacing.lg),
    ) {
        when (val card = state.session.card) {
            is VocabularyCard.Intro -> IntroCard(
                card = card,
                explainLoading = state.explainLoading,
                onGotIt = viewModel::submitIntro,
                onToggleBookmark = viewModel::toggleBookmark,
                onToggleDifficult = viewModel::toggleDifficult,
                onExplain = viewModel::explainWord,
            )
            is VocabularyCard.Recognition -> RecognitionCard(
                card = card,
                answered = state.answered,
                onSelect = viewModel::submitChoice,
                onContinue = viewModel::continueToNext,
            )
            is VocabularyCard.WordBank -> WordBankCard(
                card = card,
                answered = state.answered,
                onSelect = viewModel::submitChoice,
                onContinue = viewModel::continueToNext,
            )
            is VocabularyCard.Hint -> HintCard(
                card = card,
                answered = state.answered,
                onCheck = viewModel::submitTyped,
                onContinue = viewModel::continueToNext,
            )
            is VocabularyCard.Blind -> BlindCard(
                card = card,
                answered = state.answered,
                onCheck = viewModel::submitTyped,
                onContinue = viewModel::continueToNext,
            )
        }
    }
}

private fun genderBadgeLabel(gender: String?): String? = when (gender?.lowercase()) {
    "der" -> "m"
    "die" -> "f"
    "das" -> "n"
    else -> null
}

/** der = blue, die = red, das = green - a common learner mnemonic for the article. */
private fun articleColor(gender: String?): androidx.compose.ui.graphics.Color? = when (gender?.lowercase()) {
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

@Composable
private fun IntroCard(
    card: VocabularyCard.Intro,
    explainLoading: Boolean,
    onGotIt: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDifficult: () -> Unit,
    onExplain: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(EvolaSpacing.xs))
        Text(
            "New word — learn it, then you'll practice it right away.",
            style = MaterialTheme.typography.labelMedium,
            color = EvolaColors.Text2,
        )
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
                    append(card.term)
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                if (card.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = "Bookmark",
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
        // Audio playback isn't implemented yet - visual-only, per the locked out-of-scope decision.
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play pronunciation (not yet available)", tint = EvolaColors.Text3)
        card.ipaPronunciation?.let {
            Spacer(Modifier.width(EvolaSpacing.sm))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
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
            Text("AI explain")
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
        Text(if (card.markedDifficult) "Marked as difficult" else "Mark as difficult")
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    Button(onClick = onGotIt, modifier = Modifier.fillMaxWidth()) { Text("Got it — practice now") }
}

/** Ladder step 1: term shown, pick the correct translation from 4 options - matches the retired
 * pack model's own RecognitionStage reveal-on-tap treatment. */
@Composable
private fun RecognitionCard(
    card: VocabularyCard.Recognition,
    answered: VocabularyAnswerResult?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var selected by remember(card.itemId, answered) { mutableStateOf<String?>(null) }

    Text("What does this word mean?", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text3)
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(card.term, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(EvolaSpacing.lg))

    Column(verticalArrangement = Arrangement.spacedBy(EvolaSpacing.sm)) {
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
                shape = MaterialTheme.shapes.small,
                color = bg,
                border = BorderStroke(1.dp, border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                RtlText(choice, modifier = Modifier.padding(EvolaSpacing.md))
            }
        }
    }

    AdvanceButton(answered, onContinue)
}

/** Ladder step 2: blanked sentence, tap the correct German term from a small word bank instead of
 * typing. */
@Composable
private fun WordBankCard(
    card: VocabularyCard.WordBank,
    answered: VocabularyAnswerResult?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var selected by remember(card.itemId, answered) { mutableStateOf<String?>(null) }

    Text("Tap the missing word", style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text3)
    Spacer(Modifier.height(EvolaSpacing.sm))
    Text(blankedSentence(card.sentenceWithBlank), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(EvolaSpacing.sm))
    card.sentenceTranslation?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
    }
    card.grammarNote?.let {
        Spacer(Modifier.height(EvolaSpacing.xs))
        Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

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

/** Ladder step 3: typed recall, input pre-filled with the first half of the term, rendered inline
 * within the sentence (not a separate boxed field) to match the design's "keep typing where the
 * blank is" feel. */
@Composable
private fun HintCard(
    card: VocabularyCard.Hint,
    answered: VocabularyAnswerResult?,
    onCheck: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var typedAnswer by remember(card.itemId, answered) { mutableStateOf(card.hintPrefix) }
    val focusManager = LocalFocusManager.current
    val (prefix, suffix) = splitOnBlank(card.sentenceWithBlank)

    if (answered == null) {
        Text("It's started for you — finish the word", style = MaterialTheme.typography.bodySmall, color = EvolaColors.Text3)
        Spacer(Modifier.height(EvolaSpacing.sm))
        InlineFillSentence(
            prefix = prefix,
            suffix = suffix,
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            onDone = { focusManager.clearFocus() },
        )
    } else {
        RevealedInlineSentence(prefix = prefix, word = answered.correctAnswer ?: typedAnswer, suffix = suffix, correct = answered.correct == true)
    }
    Spacer(Modifier.height(EvolaSpacing.sm))
    card.sentenceTranslation?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        Spacer(Modifier.height(EvolaSpacing.sm))
    }
    card.grammarNote?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text("Check")
        }
    } else {
        FeedbackNote(answered)
        AdvanceButton(answered, onContinue)
    }
}

/** Ladder step 4 (final) for new words, and the only step for due-for-review words: typed from
 * scratch, no scaffolding, rendered inline within the sentence like [HintCard]. */
@Composable
private fun BlindCard(
    card: VocabularyCard.Blind,
    answered: VocabularyAnswerResult?,
    onCheck: (String) -> Unit,
    onContinue: () -> Unit,
) {
    var typedAnswer by remember(card.itemId, answered) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val (prefix, suffix) = splitOnBlank(card.sentenceWithBlank)

    if (answered == null) {
        InlineFillSentence(
            prefix = prefix,
            suffix = suffix,
            value = typedAnswer,
            onValueChange = { typedAnswer = it },
            onDone = { focusManager.clearFocus() },
        )
    } else {
        RevealedInlineSentence(prefix = prefix, word = answered.correctAnswer ?: typedAnswer, suffix = suffix, correct = answered.correct == true)
    }
    Spacer(Modifier.height(EvolaSpacing.sm))
    card.sentenceTranslation?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
        Spacer(Modifier.height(EvolaSpacing.sm))
    }
    card.grammarNote?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = EvolaColors.Accent)
    }
    Spacer(Modifier.height(EvolaSpacing.lg))

    if (answered == null) {
        Spacer(Modifier.height(EvolaSpacing.md))
        Button(onClick = { onCheck(typedAnswer) }, modifier = Modifier.fillMaxWidth(), enabled = typedAnswer.isNotBlank()) {
            Text("Check")
        }
    } else {
        FeedbackNote(answered)
        AdvanceButton(answered, onContinue)
    }
}

@Composable
private fun AdvanceButton(answered: VocabularyAnswerResult?, onContinue: () -> Unit) {
    if (answered == null) return
    Spacer(Modifier.height(EvolaSpacing.lg))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(if (answered.next == null) "Finish session" else "Continue")
    }
}

@Composable
private fun FeedbackNote(answered: VocabularyAnswerResult) {
    val correct = answered.correct ?: return
    val (bg, text) = if (correct) {
        EvolaColors.GoldSoft to "Correct!"
    } else {
        EvolaColors.RustSoft to "Not quite — the answer was \"${answered.correctAnswer}\". This step repeats later, so you'll get another shot."
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

private fun splitOnBlank(sentence: String): Pair<String, String> {
    val idx = sentence.indexOf("___")
    return if (idx < 0) sentence to "" else sentence.substring(0, idx) to sentence.substring(idx + 3)
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
    FlowRow(verticalArrangement = Arrangement.Center) {
        Text(prefix, style = style)
        Box(
            modifier = Modifier.widthIn(min = 56.dp).drawBehind {
                drawLine(
                    color = EvolaColors.Accent,
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
