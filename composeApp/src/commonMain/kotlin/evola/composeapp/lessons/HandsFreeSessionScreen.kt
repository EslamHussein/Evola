@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.respawn.flowmvi.compose.dsl.subscribe
import evola.composeapp.BackHandler
import evola.composeapp.loading.ChaseLoadingIndicator
import evola.composeapp.rtl.RtlText
import evola.composeapp.speech.SpeechService
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_action_already_know
import evola.composeapp.generated.resources.lessons_action_got_it
import evola.composeapp.generated.resources.lessons_action_keep_showing
import evola.composeapp.generated.resources.lessons_action_memorized
import evola.composeapp.generated.resources.lessons_action_missed_it
import evola.composeapp.generated.resources.lessons_action_start_learning
import evola.composeapp.generated.resources.lessons_handsfree_empty
import evola.composeapp.generated.resources.lessons_handsfree_title
import evola.composeapp.generated.resources.lessons_nav_close
import org.jetbrains.compose.resources.stringResource
import evola.shared.vocabulary.VocabularyCard

/**
 * Reword-style "hands-free" practice: narrates each card via TTS (so the learner doesn't need to
 * look) and replaces the swipe gesture + typed/multiple-choice options with two oversized tap
 * targets - "hands-free" means eyes-off, not touch-off, since grading still needs a deliberate
 * action. Reuses [VocabularySessionViewModel]'s existing queue/SRS engine completely unchanged;
 * this is a different renderer of the same session, not a different session type. Only the plain
 * self-graded path is offered here (no typed/multiple-choice) - narrating a prompt then asking for
 * a typed answer defeats the point of not looking at the screen.
 *
 * A [VocabularyCard.Practice] card's term is never sent to the client before grading (see
 * [evola.shared.vocabulary.VocabularyRepository]'s own contract), so only the native-language
 * meaning can be narrated as the prompt - recall is still on the learner, exactly like the swipe
 * screen's plain self-report path, just spoken instead of read.
 */
@Composable
fun HandsFreeSessionScreen(viewModel: VocabularySessionViewModel, speechService: SpeechService, onDone: () -> Unit) {
    val state by viewModel.subscribe()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    BackHandler(onBack = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_handsfree_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.lessons_nav_close)) }
                },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is VocabularySessionUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChaseLoadingIndicator() }

                is VocabularySessionUiState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is VocabularySessionUiState.Empty -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.lessons_handsfree_empty), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }

                is VocabularySessionUiState.Summary -> SessionSummaryScreen(
                    summary = current.summary,
                    onContinueToNextSession = { viewModel.intent(VocabularySessionIntent.StartNextSession) },
                    onDone = onDone,
                )

                is VocabularySessionUiState.InProgress -> HandsFreeCard(
                    card = current.session.card,
                    dueReview = current.session.origin == "due_review",
                    speechService = speechService,
                    ttsRate = settings.ttsRate,
                    ttsVoiceName = settings.ttsVoiceName,
                    onAlreadyKnown = {
                        viewModel.intent(VocabularySessionIntent.SubmitAlreadyKnown(current.session.sessionId, current.session.card.itemId))
                    },
                    onStartLearning = {
                        viewModel.intent(VocabularySessionIntent.SubmitStartLearning(current.session.sessionId, current.session.card.itemId))
                    },
                    onSelfGrade = { correct ->
                        viewModel.intent(VocabularySessionIntent.SubmitSelfGrade(current.session.sessionId, current.session.card.itemId, correct))
                    },
                    onKeepShowing = {
                        viewModel.intent(VocabularySessionIntent.SubmitKeepShowing(current.session.sessionId, current.session.card.itemId))
                    },
                )
            }
        }
    }
}

@Composable
private fun HandsFreeCard(
    card: VocabularyCard,
    dueReview: Boolean,
    speechService: SpeechService,
    ttsRate: Float,
    ttsVoiceName: String?,
    onAlreadyKnown: () -> Unit,
    onStartLearning: () -> Unit,
    onSelfGrade: (Boolean) -> Unit,
    onKeepShowing: () -> Unit,
) {
    val spokenText = when (card) {
        is VocabularyCard.New -> "${card.term}. ${card.meaning}"
        is VocabularyCard.Practice -> card.meaning
    }
    // Speaks once per card, not on every recomposition - keyed on the card's identity + type, since
    // the same word can reappear as a different card type later in the queue.
    LaunchedEffect(card.itemId, card::class) {
        speechService.speak(spokenText, rate = ttsRate, voiceName = ttsVoiceName)
    }

    val alreadyKnowLabel = stringResource(Res.string.lessons_action_already_know)
    val startLearningLabel = stringResource(Res.string.lessons_action_start_learning)
    val gotItLabel = stringResource(Res.string.lessons_action_got_it)
    val missedItLabel = stringResource(Res.string.lessons_action_missed_it)
    val memorizedLabel = stringResource(Res.string.lessons_action_memorized)
    val keepShowingLabel = stringResource(Res.string.lessons_action_keep_showing)
    val (leftLabel, rightLabel, onLeft, onRight) = when (card) {
        is VocabularyCard.New -> HandsFreeActions(alreadyKnowLabel, startLearningLabel, onAlreadyKnown, onStartLearning)
        is VocabularyCard.Practice -> if (dueReview) {
            HandsFreeActions(gotItLabel, missedItLabel, { onSelfGrade(true) }, { onSelfGrade(false) })
        } else {
            HandsFreeActions(memorizedLabel, keepShowingLabel, { onSelfGrade(true) }, onKeepShowing)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = EvolaColors.Accent, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(EvolaSpacing.lg))
                RtlText(
                    if (card is VocabularyCard.New) card.term else (card as VocabularyCard.Practice).meaning,
                    style = MaterialTheme.typography.headlineLarge.copy(textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (card is VocabularyCard.New) {
                    Spacer(Modifier.height(EvolaSpacing.sm))
                    Text(card.meaning, style = MaterialTheme.typography.titleMedium, color = EvolaColors.Text2, textAlign = TextAlign.Center)
                }
            }
        }
        Button(
            onClick = onLeft,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EvolaColors.Rust),
        ) { Text(leftLabel, style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(EvolaSpacing.md))
        Button(
            onClick = onRight,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EvolaColors.Gold),
        ) { Text(rightLabel, style = MaterialTheme.typography.titleMedium) }
    }
}

private data class HandsFreeActions(val leftLabel: String, val rightLabel: String, val onLeft: () -> Unit, val onRight: () -> Unit)
