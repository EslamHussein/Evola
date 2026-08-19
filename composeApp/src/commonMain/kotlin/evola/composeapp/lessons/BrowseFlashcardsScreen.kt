@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package evola.composeapp.lessons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.orbitmvi.orbit.compose.collectAsState
import evola.composeapp.BackHandler
import evola.composeapp.loading.ChaseLoadingIndicator
import evola.composeapp.rtl.RtlText
import evola.composeapp.theme.EvolaColors
import evola.composeapp.theme.EvolaSpacing
import evola.composeapp.generated.resources.Res
import evola.composeapp.generated.resources.lessons_browse_ipa
import evola.composeapp.generated.resources.lessons_browse_next_word
import evola.composeapp.generated.resources.lessons_browse_previous_word
import evola.composeapp.generated.resources.lessons_browse_tap_to_reveal
import evola.composeapp.generated.resources.lessons_browse_title
import evola.composeapp.generated.resources.lessons_empty_vocabulary
import evola.composeapp.generated.resources.lessons_nav_close
import evola.composeapp.generated.resources.lessons_word_progress
import org.jetbrains.compose.resources.stringResource
import evola.shared.vocabulary.VocabularyItem

/** Reword's "Extra modes (do not affect stats)" - flip through every word in the lesson, tap the
 * card to reveal the meaning, no grading. A fresh card always opens meaning-hidden ([revealed]
 * resets via the `remember(item.itemId)` key below), matching Reword's own "guess before you
 * reveal" framing even though nothing here is graded. */
@Composable
fun BrowseFlashcardsScreen(viewModel: BrowseFlashcardsViewModel, onDone: () -> Unit) {
    val state by viewModel.collectAsState()
    BackHandler(onBack = onDone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.lessons_browse_title)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.lessons_nav_close)) } },
            )
        },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                is BrowseFlashcardsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ChaseLoadingIndicator() }
                is BrowseFlashcardsState.Error -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(current.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                is BrowseFlashcardsState.Empty -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.lessons_empty_vocabulary), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                is BrowseFlashcardsState.Browsing -> BrowsingBody(
                    current,
                    onNext = { viewModel.next() },
                    onPrevious = { viewModel.previous() },
                )
            }
        }
    }
}

@Composable
private fun BrowsingBody(state: BrowseFlashcardsState.Browsing, onNext: () -> Unit, onPrevious: () -> Unit) {
    val item = state.items[state.index]
    Column(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.lg)) {
        Text(
            stringResource(Res.string.lessons_word_progress, state.index + 1, state.items.size),
            style = MaterialTheme.typography.labelSmall,
            color = EvolaColors.Text3,
        )
        Spacer(Modifier.height(EvolaSpacing.xs))
        LinearProgressIndicator(
            progress = { (state.index + 1) / state.items.size.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(EvolaSpacing.xxl))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            FlipCard(item)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onPrevious, enabled = state.index > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(Res.string.lessons_browse_previous_word))
            }
            IconButton(onClick = onNext, enabled = state.index < state.items.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(Res.string.lessons_browse_next_word))
            }
        }
    }
}

@Composable
private fun FlipCard(item: VocabularyItem) {
    var revealed by remember(item.itemId) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().height(320.dp).clickable { revealed = !revealed },
        shape = RoundedCornerShape(24.dp),
        color = EvolaColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, EvolaColors.Border),
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(EvolaSpacing.xl), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = revealed,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "flashcard-reveal",
            ) { showMeaning ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!showMeaning) {
                        Text(
                            item.gender?.let { "$it ${item.term}" } ?: item.term,
                            style = MaterialTheme.typography.headlineLarge,
                            textAlign = TextAlign.Center,
                        )
                        item.ipaPronunciation?.let {
                            Spacer(Modifier.height(EvolaSpacing.sm))
                            Text(stringResource(Res.string.lessons_browse_ipa, it), style = MaterialTheme.typography.bodyMedium, color = EvolaColors.Text2)
                        }
                        Spacer(Modifier.height(EvolaSpacing.xl))
                        Text(stringResource(Res.string.lessons_browse_tap_to_reveal), style = MaterialTheme.typography.labelMedium, color = EvolaColors.Text3)
                    } else {
                        RtlText(item.nativeMeaning ?: item.meaning, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.fillMaxWidth())
                        item.exampleSentence?.let {
                            Spacer(Modifier.height(EvolaSpacing.lg))
                            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
