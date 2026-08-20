package evola.composeapp.feature.learning.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import evola.composeapp.feature.learning.vm.GrammarExerciseSessionViewModel
import org.orbitmvi.orbit.compose.collectAsState

/** Grammar's exercise session (01_PRODUCT_SPEC.md §1.9): a flat multiple-choice/fill-in-blank
 * list, unlike Vocabulary's pack/7-stage model. Exiting mid-drill is always safe - the session
 * resumes exactly where it left off (server-tracked via `grammar_session_answers`), so the back
 * arrow is wired to [onDone] in every state. No BackHandler for the system back gesture - this
 * screen is only ever reached via MaterialsRoute.GrammarSession, whose NavDisplay already handles
 * it (predictive-back animation included) by popping the back stack, exactly what [onDone] does. */
@Composable
fun GrammarExerciseSessionScreen(viewModel: GrammarExerciseSessionViewModel, onDone: () -> Unit) {
    val state by viewModel.collectAsState()
    GrammarExerciseSessionContent(
        state = state,
        onDone = onDone,
        onRetry = { viewModel.retry() },
        onSubmit = { exerciseId, response, correct -> viewModel.submitAnswer(exerciseId, response, correct) },
    )
}
