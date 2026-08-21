@file:OptIn(org.koin.core.annotation.KoinExperimentalAPI::class)

package evola.composeapp.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import evola.composeapp.core.designsystem.EvolaTheme
import evola.composeapp.core.navigation.MaterialsNavContext
import evola.composeapp.core.navigation.ProfileNavContext
import evola.composeapp.feature.home.vm.HomeViewModel
import evola.shared.core.common.ApiResult
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.feature.onboarding.domain.Goal
import evola.shared.feature.onboarding.domain.GoalProgress
import evola.shared.feature.onboarding.domain.GoalsRepository
import evola.shared.feature.onboarding.domain.Lesson
import evola.shared.feature.onboarding.domain.UpdateGoalResult
import evola.shared.feature.onboarding.domain.VocabularyBreakdown
import evola.shared.feature.profile.domain.AppTheme
import evola.shared.language.NativeLanguage
import org.koin.compose.KoinApplication
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinConfiguration
import org.koin.dsl.module

private val fakeMainScreenGoal = Goal(
    id = "g1", goalText = "Learn German for my trip to Berlin", title = "Berlin Trip",
    nativeLanguage = NativeLanguage.ENGLISH, isActive = true, createdAt = "2026-01-01",
)

private val fakeMainScreenLesson = Lesson(
    id = "l1", number = 1, title = "Greetings", status = "ready",
    vocabProgress = 0.6f, grammarProgress = 0.3f, grammarCount = 2,
)

/** In-memory [GoalsRepository] so [MainScreenPreview] can start a real (tiny) Koin instance
 * instead of hitting Room/network - the only dependency [HomeViewModel] actually needs. */
private object FakeGoalsRepository : GoalsRepository {
    override suspend fun createGoal(goalText: String, title: String?, nativeLanguage: NativeLanguage): CreateGoalResult =
        CreateGoalResult.Success(fakeMainScreenGoal)
    override suspend fun updateGoal(goalId: String, goalText: String?, title: String?, nativeLanguage: NativeLanguage?): UpdateGoalResult =
        UpdateGoalResult.Success(fakeMainScreenGoal)
    override suspend fun getActiveGoal(): ApiResult<Goal?> = ApiResult.Success(fakeMainScreenGoal)
    override suspend fun listLessons(goalId: String): ApiResult<List<Lesson>> = ApiResult.Success(listOf(fakeMainScreenLesson))
    override suspend fun getProgress(goalId: String, localDate: String): ApiResult<GoalProgress> = ApiResult.Success(
        GoalProgress(
            overallPct = 0.42f, currentLessonId = fakeMainScreenLesson.id, streakDays = 5, todayCompleted = false,
            vocabulary = VocabularyBreakdown(notStarted = 12, inProgress = 8, mastered = 20, struggling = 3),
        ),
    )
}

/** [MainScreen] itself is Koin-wired end to end (koinInject/koinViewModel), so previewing it for
 * real means standing up a real (if tiny) Koin instance rather than the module graph the running
 * app builds in [evola.composeapp.App] (which needs a real Room database, secure store, and file
 * extractor) - [MaterialsNavContext]/[ProfileNavContext] are plain no-arg singletons, and
 * [HomeViewModel] (the only other thing [MainScreen] resolves before any tab switch happens) needs
 * nothing but [GoalsRepository], faked here with [FakeGoalsRepository]. Only Home ever renders:
 * `selectedTab` always starts at [MainTab.HOME] and there's no way to seed it otherwise. */
@Composable
private fun MainScreenPreview(appTheme: AppTheme) {
    KoinApplication(
        configuration = koinConfiguration {
            modules(
                module {
                    single { MaterialsNavContext() }
                    single { ProfileNavContext() }
                    single<GoalsRepository> { FakeGoalsRepository }
                    viewModel { (goalId: String) -> HomeViewModel(goalId, get()) }
                },
            )
        },
    ) {
        EvolaTheme(appTheme = appTheme) {
            MainScreen(initialGoal = fakeMainScreenGoal)
        }
    }
}

@Preview
@Composable
private fun MainScreenLightPreview() {
    MainScreenPreview(appTheme = AppTheme.LIGHT)
}

@Preview
@Composable
private fun MainScreenDarkPreview() {
    MainScreenPreview(appTheme = AppTheme.DARK)
}
