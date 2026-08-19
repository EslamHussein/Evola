package evola.composeapp.main

import evola.shared.achievements.AchievementsRepository
import evola.shared.core.fold
import evola.shared.core.getOrNull
import evola.shared.goals.GoalsRepository
import evola.shared.goals.UpdateGoalResult
import evola.shared.todayLocalDate
import evola.shared.vocabulary.VocabularyRepository
import kotlinx.coroutines.CancellationException
import pro.respawn.flowmvi.api.Container
import pro.respawn.flowmvi.dsl.store
import pro.respawn.flowmvi.plugins.init
import pro.respawn.flowmvi.plugins.reduce

/** Goal editing from Profile per 01_PRODUCT_SPEC.md §1.4 - editable later without repeating
 * onboarding. Also owns the achievement badges and progress-summary reads Profile needs (badges
 * for the Achievements section, progress for the "Share progress" text) - both used to be fetched
 * directly from the composable via `koinInject`, which bypassed this Container entirely; loading
 * them here instead means they get the same loading/error handling as everything else in state. */
class ProfileContainer(
    private val goalId: String,
    private val goalsRepository: GoalsRepository,
    private val vocabularyRepository: VocabularyRepository,
    private val achievementsRepository: AchievementsRepository,
) : Container<ProfileState, ProfileIntent, Nothing> {

    override val store = store(initial = ProfileState()) {
        configure { name = "ProfileStore" }
        init {
            val badges = achievementsRepository.unlockedBadgeIds().getOrNull().orEmpty()
            val progress = goalsRepository.getProgress(goalId, todayLocalDate()).getOrNull()
            updateState { copy(unlockedBadgeIds = badges, progress = progress) }
        }
        reduce { intent ->
            when (intent) {
                is ProfileIntent.UpdateGoal -> {
                    // Length validation lives solely in GoalsRepository.updateGoal (returns
                    // UpdateGoalResult.ValidationError) - a duplicate check here previously
                    // disagreed with the repository's real 3-200 bound and message.
                    val trimmedText = intent.goalText.trim()
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
                        onSuccess = {
                            val progress = goalsRepository.getProgress(goalId, todayLocalDate()).getOrNull()
                            updateState { copy(progressReset = ProgressResetEvent(true), progress = progress) }
                        },
                        onFailure = { updateState { copy(progressReset = ProgressResetEvent(false)) } },
                    )
                }
            }
        }
    }
}
