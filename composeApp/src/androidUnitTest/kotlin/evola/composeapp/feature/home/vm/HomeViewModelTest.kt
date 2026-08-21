package evola.composeapp.feature.home.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.composeapp.core.database.testAppDatabase
import evola.shared.db.EvolaDatabase
import evola.shared.feature.onboarding.domain.CreateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.feature.profile.data.LocalAchievementsRepository
import evola.shared.feature.onboarding.data.LocalGoalsRepository
import evola.shared.feature.profile.data.LocalSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.testWithInternalState
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Proves out the Orbit MVI ViewModel test pattern this app uses everywhere else: a real
 * [LocalGoalsRepository] backed by an in-memory SQLite [EvolaDatabase] (same convention as
 * :shared's own repository tests), driven through [HomeViewModel] via the official
 * `org.orbit-mvi:orbit-test` DSL - never a mocked repository. Robolectric only because
 * [LocalAchievementsRepository]'s Room database needs a real `Context` on Android; still runs on
 * the host JVM. */
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private fun goalsRepository(): LocalGoalsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        val roomDb = testAppDatabase()
        return LocalGoalsRepository(db, LocalSettingsRepository(roomDb), LocalAchievementsRepository(roomDb))
    }

    @Test
    fun `refresh loads progress for a goal with no lessons yet`() = runTest {
        val repository = goalsRepository()
        val created = repository.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success

        val viewModel = HomeViewModel(created.goal.id, repository)
        viewModel.testWithInternalState(this, HomeState.Loading) {
            containerHost.refresh()
            assertIs<HomeState.Loaded>(awaitInternalState())
        }
    }

    @Test
    fun `an unknown goal id still resolves to a zeroed Loaded state, not a crash`() = runTest {
        // listLessons/getProgress treat a nonexistent goalId as "zero lessons", not a failure -
        // this exercises that HomeViewModel doesn't assume the goal is guaranteed to exist.
        val viewModel = HomeViewModel("does-not-exist", goalsRepository())
        viewModel.testWithInternalState(this, HomeState.Loading) {
            containerHost.refresh()
            val loaded = assertIs<HomeState.Loaded>(awaitInternalState())
            assertEquals(false, loaded.hasLessons)
        }
    }
}
