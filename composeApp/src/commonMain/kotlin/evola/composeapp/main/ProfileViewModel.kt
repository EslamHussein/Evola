package evola.composeapp.main

import androidx.lifecycle.ViewModel
import evola.shared.achievements.AchievementsRepository
import evola.shared.core.fold
import evola.shared.core.getOrNull
import evola.shared.goals.GoalsRepository
import evola.shared.goals.UpdateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.todayLocalDate
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.CancellationException
import org.orbitmvi.orbit.OrbitContainerHost
import org.orbitmvi.orbit.syntax.Syntax
import org.orbitmvi.orbit.viewmodel.orbitContainer

/** Goal editing from Profile per 01_PRODUCT_SPEC.md §1.4 - editable later without repeating
 * onboarding. Also owns the achievement badges and progress-summary reads Profile needs (badges
 * for the Achievements section, progress for the "Share progress" text) - both used to be fetched
 * directly from the composable via `koinInject`, which bypassed this ViewModel entirely; loading
 * them here instead means they get the same loading/error handling as everything else in state. */
class ProfileViewModel(
    private val goalId: String,
    private val goalsRepository: GoalsRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val achievementsRepository: AchievementsRepository,
) : ViewModel(), OrbitContainerHost<ProfileState, ProfileState, ProfileSideEffect> {

    override val container = orbitContainer<ProfileState, ProfileSideEffect>(ProfileState(), onCreate = { loadInitial() })

    private suspend fun Syntax<ProfileState, ProfileSideEffect>.loadInitial() {
        val badges = achievementsRepository.unlockedBadgeIds().getOrNull().orEmpty()
        val progress = goalsRepository.getProgress(goalId, todayLocalDate()).getOrNull()
        reduce { state.copy(unlockedBadgeIds = badges, progress = progress) }
    }

    // Length validation lives solely in GoalsRepository.updateGoal (returns
    // UpdateGoalResult.ValidationError) - a duplicate check here previously disagreed with the
    // repository's real 3-200 bound and message.
    fun updateGoal(goalId: String, goalText: String, title: String?, nativeLanguage: NativeLanguage) = intent {
        val trimmedText = goalText.trim()
        reduce { state.copy(isSubmitting = true, errorMessage = null) }
        try {
            when (
                val result = goalsRepository.updateGoal(goalId, trimmedText, title?.trim()?.ifBlank { null }, nativeLanguage)
            ) {
                is UpdateGoalResult.Success -> postSideEffect(ProfileSideEffect.GoalUpdated(result.goal))
                UpdateGoalResult.NotFound -> reduce { state.copy(errorMessage = "This goal could not be found.") }
                is UpdateGoalResult.ValidationError -> reduce { state.copy(errorMessage = result.message) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reduce { state.copy(errorMessage = e.message ?: "Something went wrong. Please try again.") }
        } finally {
            reduce { state.copy(isSubmitting = false) }
        }
    }

    /** Reword's Menu "Reset all progress" - confirmed by the screen before dispatch. */
    fun resetAllProgress() = intent {
        vocabularyRepository.resetAllProgress().fold(
            onSuccess = {
                val progress = goalsRepository.getProgress(goalId, todayLocalDate()).getOrNull()
                reduce { state.copy(progress = progress) }
                postSideEffect(ProfileSideEffect.ProgressReset(true))
            },
            onFailure = { postSideEffect(ProfileSideEffect.ProgressReset(false)) },
        )
    }
}
