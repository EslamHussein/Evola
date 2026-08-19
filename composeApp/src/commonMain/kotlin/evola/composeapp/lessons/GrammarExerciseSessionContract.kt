package evola.composeapp.lessons

import evola.shared.grammar.GrammarExercise
import pro.respawn.flowmvi.api.MVIIntent
import pro.respawn.flowmvi.api.MVIState

sealed interface GrammarExerciseSessionState : MVIState {
    data object Loading : GrammarExerciseSessionState
    data class InProgress(val currentExercise: GrammarExercise, val answeredCount: Int) : GrammarExerciseSessionState
    data class Summary(val exercisesCompleted: Int, val accuracy: Double) : GrammarExerciseSessionState

    /** This topic ended up with 0 valid exercises (spec: a topic can survive validation with
     * fewer than 3, or even 0, valid exercises - the explanation is still shown on the topic list,
     * this is not an error). */
    data object Empty : GrammarExerciseSessionState
    data class Error(val message: String) : GrammarExerciseSessionState
}

sealed interface GrammarExerciseSessionIntent : MVIIntent {
    data object Retry : GrammarExerciseSessionIntent
    data class SubmitAnswer(val exerciseId: String, val response: String, val correct: Boolean) : GrammarExerciseSessionIntent
}
