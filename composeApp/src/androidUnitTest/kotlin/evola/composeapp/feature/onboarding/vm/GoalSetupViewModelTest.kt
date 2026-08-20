package evola.composeapp.feature.onboarding.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.language.NativeLanguage
import evola.shared.local.LocalAchievementsRepository
import evola.shared.feature.onboarding.data.LocalGoalsRepository
import evola.shared.local.LocalSettingsRepository
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Same real-repository-over-in-memory-SQLite convention as [evola.composeapp.main.HomeViewModelTest],
 * driven through the real [GoalSetupViewModel] via `org.orbit-mvi:orbit-test`. */
class GoalSetupViewModelTest {

    private fun goalsRepository(): LocalGoalsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        return LocalGoalsRepository(db, LocalSettingsRepository(db), LocalAchievementsRepository(db))
    }

    @Test
    fun `a valid goal creates successfully and reports the created goal`() = runTest {
        val viewModel = GoalSetupViewModel(goalsRepository())
        viewModel.testWithInternalState(this, GoalSetupState()) {
            containerHost.createGoal("Pass the German B1 exam", "B1 exam", NativeLanguage.ENGLISH)
            assertTrue(awaitInternalState().isSubmitting)
            val effect = assertIs<GoalSetupSideEffect.GoalCreated>(awaitSideEffect())
            assertEquals("Pass the German B1 exam", effect.goal.goalText)
            assertEquals("B1 exam", effect.goal.title)
            val finalState = awaitInternalState()
            assertFalse(finalState.isSubmitting)
            assertNull(finalState.errorMessage)
        }
    }

    @Test
    fun `text under 3 chars surfaces the repository's own validation message, not a hardcoded one`() = runTest {
        val viewModel = GoalSetupViewModel(goalsRepository())
        viewModel.testWithInternalState(this, GoalSetupState()) {
            containerHost.createGoal("Hi", null, NativeLanguage.ENGLISH)
            assertTrue(awaitInternalState().isSubmitting)
            val errorState = awaitInternalState()
            assertEquals("Goal text must be 3-200 characters.", errorState.errorMessage)
            val finalState = awaitInternalState()
            assertFalse(finalState.isSubmitting)
        }
    }

    @Test
    fun `creating a second goal while one is active still succeeds - local repo replaces the active goal`() = runTest {
        // LocalGoalsRepository.createGoal() unconditionally deactivates any existing goal before
        // inserting the new one (see LocalGoalsRepository.kt) - it never actually returns
        // CreateGoalResult.ActiveGoalExists, so GoalSetupViewModel's "already has an active goal"
        // fallback branch is unreachable through the real repository. This test documents that: a
        // second createGoal succeeds outright and becomes the new active goal, rather than erroring.
        val viewModel = GoalSetupViewModel(goalsRepository())
        viewModel.testWithInternalState(this, GoalSetupState()) {
            containerHost.createGoal("Learn German", null, NativeLanguage.ENGLISH)
            assertTrue(awaitInternalState().isSubmitting)
            val firstEffect = assertIs<GoalSetupSideEffect.GoalCreated>(awaitSideEffect())
            assertEquals("Learn German", firstEffect.goal.goalText)
            assertFalse(awaitInternalState().isSubmitting)

            containerHost.createGoal("Learn Spanish", null, NativeLanguage.ENGLISH)
            assertTrue(awaitInternalState().isSubmitting)
            val secondEffect = assertIs<GoalSetupSideEffect.GoalCreated>(awaitSideEffect())
            assertEquals("Learn Spanish", secondEffect.goal.goalText)
            val finalState = awaitInternalState()
            assertFalse(finalState.isSubmitting)
            assertNull(finalState.errorMessage)
        }
    }
}
