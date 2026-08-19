package evola.composeapp.main

import evola.shared.core.fold
import evola.shared.goals.GoalsRepository
import evola.shared.goals.UpdateGoalResult
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.CancellationException
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.reduce

/** Goal editing from Profile per 01_PRODUCT_SPEC.md §1.4 - editable later without repeating onboarding. */
class ProfileContainer(
    private val goalsRepository: GoalsRepository,
    private val vocabularyRepository: VocabularyRepository,
) : Container<ProfileState, ProfileIntent, Nothing> {

    override val store = store(initial = ProfileState()) {
        configure { name = "ProfileStore" }
        reduce { intent ->
            when (intent) {
                is ProfileIntent.UpdateGoal -> {
                    val trimmedText = intent.goalText.trim()
                    if (trimmedText.length < 3) {
                        updateState { copy(errorMessage = "Tell us a bit more about your goal (at least 3 characters).") }
                        return@reduce
                    }

                    updateState { copy(isSubmitting = true, errorMessage = null) }
                    try {
                        when (
                            val result = goalsRepository.updateGoal(
                                intent.goalId,
                                trimmedText,
                                intent.title?.trim()?.ifBlank { null },
                                intent.nativeLanguage,
                            )
                        ) {
                            is UpdateGoalResult.Success -> updateState { copy(goalUpdated = GoalUpdateEvent(result.goal)) }
                            UpdateGoalResult.NotFound -> updateState { copy(errorMessage = "This goal could not be found.") }
                            is UpdateGoalResult.ValidationError -> updateState { copy(errorMessage = result.message) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        updateState { copy(errorMessage = e.message ?: "Something went wrong. Please try again.") }
                    } finally {
                        updateState { copy(isSubmitting = false) }
                    }
                }

                ProfileIntent.ResetAllProgress -> {
                    vocabularyRepository.resetAllProgress().fold(
                        onSuccess = { updateState { copy(progressReset = ProgressResetEvent(true)) } },
                        onFailure = { updateState { copy(progressReset = ProgressResetEvent(false)) } },
                    )
                }
            }
        }
    }
}
