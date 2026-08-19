package evola.composeapp.onboarding

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.language.NativeLanguage
import evola.shared.local.LocalAchievementsRepository
import evola.shared.local.LocalGoalsRepository
import evola.shared.local.LocalSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Same real-repository-over-in-memory-SQLite convention as [evola.composeapp.main.HomeContainerTest].
 * Unlike [evola.composeapp.main.HomeContainer] (whose `init {}` plugin already lands the store on a
 * terminal state before any intent is even sent), [GoalSetupContainer] only changes state in
 * response to [GoalSetupIntent.CreateGoal] itself, and the reducer's own suspend work (the real
 * repository call) can still be in flight when `resultsIn { }`'s lambda starts running - so each
 * assertion waits on `states.first { }` for a state that actually carries the outcome (a non-null
 * `goalCreated`/`errorMessage`) rather than reading `states.value` immediately. */
class GoalSetupContainerTest {

    private fun goalsRepository(): LocalGoalsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        val db = EvolaDatabase(driver)
        return LocalGoalsRepository(db, LocalSettingsRepository(db), LocalAchievementsRepository(db))
    }

    @Test
    fun `a valid goal creates successfully and reports the created goal`() = runTest {
        GoalSetupContainer(goalsRepository()).store.subscribeAndTest {
            GoalSetupIntent.CreateGoal("Pass the German B1 exam", "B1 exam", NativeLanguage.ENGLISH) resultsIn {
                val final = states.first { it.goalCreated != null || it.errorMessage != null }
                assertEquals("Pass the German B1 exam", final.goalCreated?.goal?.goalText)
                assertEquals("B1 exam", final.goalCreated?.goal?.title)
                assertNull(final.errorMessage)
            }
        }
    }

    @Test
    fun `text under 3 chars surfaces the repository's own validation message, not a hardcoded one`() = runTest {
        GoalSetupContainer(goalsRepository()).store.subscribeAndTest {
            GoalSetupIntent.CreateGoal("Hi", null, NativeLanguage.ENGLISH) resultsIn {
                val final = states.first { it.goalCreated != null || it.errorMessage != null }
                assertEquals("Goal text must be 3-200 characters.", final.errorMessage)
                assertNull(final.goalCreated)
            }
        }
    }

    @Test
    fun `creating a second goal while one is active still succeeds - local repo replaces the active goal`() = runTest {
        // LocalGoalsRepository.createGoal() unconditionally deactivates any existing goal before
        // inserting the new one (see LocalGoalsRepository.kt) - it never actually returns
        // CreateGoalResult.ActiveGoalExists, so GoalSetupContainer's "already has an active goal"
        // fallback branch is unreachable through the real repository. This test documents that: a
        // second CreateGoal succeeds outright and becomes the new active goal, rather than erroring.
        val repository = goalsRepository()
        GoalSetupContainer(repository).store.subscribeAndTest {
            GoalSetupIntent.CreateGoal("Learn German", null, NativeLanguage.ENGLISH) resultsIn {
                // `!it.isSubmitting` alone would also match the initial default state (isSubmitting
                // is false before any intent runs too), resolving before creation even starts - wait
                // for the actual outcome field instead, same as every other test in this file.
                val final = states.first { it.goalCreated?.goal?.goalText == "Learn German" }
                assertEquals("Learn German", final.goalCreated?.goal?.goalText)
            }
            GoalSetupIntent.CreateGoal("Learn Spanish", null, NativeLanguage.ENGLISH) resultsIn {
                val final = states.first { !it.isSubmitting && it.goalCreated?.goal?.goalText == "Learn Spanish" }
                assertEquals("Learn Spanish", final.goalCreated?.goal?.goalText)
                assertNull(final.errorMessage)
            }
        }
    }
}
