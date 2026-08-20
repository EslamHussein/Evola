package evola.composeapp.main

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.core.network.AnthropicClient
import evola.shared.db.EvolaDatabase
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.local.LocalAchievementsRepository
import evola.shared.feature.onboarding.data.LocalGoalsRepository
import evola.shared.local.LocalSettingsRepository
import evola.shared.feature.vocabulary.data.LocalVocabularyRepository
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ProfileViewModel] now owns the achievement-badge and progress-summary reads that used to be
 * fetched straight from the composable (see the ViewModel's own doc comment) - these tests cover
 * both that `onCreate` load and the two intents, driven through the real [ProfileViewModel] via
 * the `org.orbit-mvi:orbit-test` DSL (same pattern as [HomeViewModelTest]), backed by real
 * Local*Repository implementations sharing one in-memory SQLite [EvolaDatabase] - never mocks. */
class ProfileViewModelTest {

    private class Repos(
        val db: EvolaDatabase,
        val goals: LocalGoalsRepository,
        val vocabulary: LocalVocabularyRepository,
        val achievements: LocalAchievementsRepository,
    )

    private fun repos(): Repos {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        val settings = LocalSettingsRepository(db)
        val achievements = LocalAchievementsRepository(db)
        val goals = LocalGoalsRepository(db, settings, achievements)
        // resetAllProgress/the vocabulary reads ProfileViewModel touches never call the AI client -
        // a MockEngine that errors on any request makes that an enforced assumption, not a guess.
        val client = AnthropicClient(MockEngine { error("AI must not be called by this test") }) { "test-key" }
        val vocabulary = LocalVocabularyRepository(db, client, settings)
        return Repos(db, goals, vocabulary, achievements)
    }

    private suspend fun createGoal(repos: Repos, text: String = "Learn German for travel"): String {
        val result = repos.goals.createGoal(text, null, NativeLanguage.ENGLISH) as CreateGoalResult.Success
        return result.goal.id
    }

    @Test
    fun `a freshly created goal loads zeroed progress and no unlocked badges`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)
        val viewModel = ProfileViewModel(goalId, repos.goals, repos.vocabulary, repos.achievements)

        viewModel.testWithInternalState(this, ProfileState()) {
            runOnCreate()
            val state = awaitInternalState()
            assertEquals(emptySet(), state.unlockedBadgeIds)
            val progress = assertNotNull(state.progress)
            assertEquals(0f, progress.overallPct)
            assertEquals(0, progress.streakDays)
            assertFalse(state.isSubmitting)
        }
    }

    @Test
    fun `UpdateGoal with valid text updates the goal and posts a GoalUpdated side effect`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)
        val viewModel = ProfileViewModel(goalId, repos.goals, repos.vocabulary, repos.achievements)

        viewModel.testWithInternalState(this, ProfileState()) {
            containerHost.updateGoal(goalId, "Learn Spanish for my trip", "My Trip", NativeLanguage.GERMAN)
            assertTrue(awaitInternalState().isSubmitting)
            val effect = assertIs<ProfileSideEffect.GoalUpdated>(awaitSideEffect())
            assertEquals("Learn Spanish for my trip", effect.goal.goalText)
            assertEquals("My Trip", effect.goal.title)
            assertEquals(NativeLanguage.GERMAN, effect.goal.nativeLanguage)
            val finalState = awaitInternalState()
            assertFalse(finalState.isSubmitting)
            assertNull(finalState.errorMessage)
        }
    }

    @Test
    fun `UpdateGoal with too-short text surfaces a validation error instead of updating`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)
        val viewModel = ProfileViewModel(goalId, repos.goals, repos.vocabulary, repos.achievements)

        viewModel.testWithInternalState(this, ProfileState()) {
            containerHost.updateGoal(goalId, "ab", null, NativeLanguage.ENGLISH)
            assertTrue(awaitInternalState().isSubmitting)
            val errorState = awaitInternalState()
            assertEquals("Goal text must be 3-200 characters.", errorState.errorMessage)
            val finalState = awaitInternalState()
            assertFalse(finalState.isSubmitting)
        }
    }

    @Test
    fun `ResetAllProgress clears vocabulary progress and refreshes the progress summary`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)
        val viewModel = ProfileViewModel(goalId, repos.goals, repos.vocabulary, repos.achievements)

        viewModel.testWithInternalState(this, ProfileState()) {
            containerHost.resetAllProgress()
            val state = awaitInternalState()
            assertNotNull(state.progress)
            val effect = assertIs<ProfileSideEffect.ProgressReset>(awaitSideEffect())
            assertTrue(effect.success)
        }
    }
}
