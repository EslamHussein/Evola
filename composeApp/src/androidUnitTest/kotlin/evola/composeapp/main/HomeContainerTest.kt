package evola.composeapp.main

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.goals.CreateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.local.LocalAchievementsRepository
import evola.shared.local.LocalGoalsRepository
import evola.shared.local.LocalSettingsRepository
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Proves out the FlowMVI Container test pattern this app uses everywhere else: a real
 * [LocalGoalsRepository] backed by an in-memory SQLite [EvolaDatabase] (same convention as
 * :shared's own repository tests), driven through [HomeContainer.store] via the official
 * `pro.respawn.flowmvi:test` DSL - never a mocked repository. */
class HomeContainerTest {

    private fun goalsRepository(): LocalGoalsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        return LocalGoalsRepository(db, LocalSettingsRepository(db), LocalAchievementsRepository(db))
    }

    @Test
    fun `refresh loads progress for a goal with no lessons yet`() = runTest {
        val repository = goalsRepository()
        val created = repository.createGoal("Learn German", null, NativeLanguage.ENGLISH) as CreateGoalResult.Success

        HomeContainer(created.goal.id, repository).store.subscribeAndTest {
            HomeIntent.Refresh resultsIn { assertIs<HomeState.Loaded>(states.value) }
        }
    }

    @Test
    fun `an unknown goal id still resolves to a zeroed Loaded state, not a crash`() = runTest {
        // listLessons/getProgress treat a nonexistent goalId as "zero lessons", not a failure -
        // this exercises that HomeContainer doesn't assume the goal is guaranteed to exist.
        HomeContainer("does-not-exist", goalsRepository()).store.subscribeAndTest {
            HomeIntent.Refresh resultsIn {
                val loaded = assertIs<HomeState.Loaded>(states.value)
                assertEquals(false, loaded.hasLessons)
            }
        }
    }
}
