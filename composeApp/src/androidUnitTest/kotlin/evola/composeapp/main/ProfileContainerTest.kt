package evola.composeapp.main

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.ai.AnthropicClient
import evola.shared.db.EvolaDatabase
import evola.shared.goals.CreateGoalResult
import evola.shared.language.NativeLanguage
import evola.shared.local.LocalAchievementsRepository
import evola.shared.local.LocalGoalsRepository
import evola.shared.local.LocalSettingsRepository
import evola.shared.local.LocalVocabularyRepository
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import pro.respawn.flowmvi.test.wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** [ProfileContainer] now owns the achievement-badge and progress-summary reads that used to be
 * fetched straight from the composable (see the Container's own doc comment) - these tests cover
 * both that `init` load and the two intents, driven through the real [ProfileContainer.store] via
 * the `pro.respawn.flowmvi:test` DSL (same pattern as [HomeContainerTest]), backed by real
 * Local*Repository implementations sharing one in-memory SQLite [EvolaDatabase] - never mocks. */
class ProfileContainerTest {

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
        // resetAllProgress/the vocabulary reads ProfileContainer touches never call the AI client -
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

        ProfileContainer(goalId, repos.goals, repos.vocabulary, repos.achievements).store.subscribeAndTest {
            val state = states.value
            assertEquals(emptySet(), state.unlockedBadgeIds)
            val progress = assertNotNull(state.progress)
            assertEquals(0f, progress.overallPct)
            assertEquals(0, progress.streakDays)
            assertFalse(state.isSubmitting)
            assertNull(state.errorMessage)
        }
    }

    @Test
    fun `UpdateGoal with valid text updates the goal and publishes a GoalUpdateEvent`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)

        ProfileContainer(goalId, repos.goals, repos.vocabulary, repos.achievements).store.subscribeAndTest {
            ProfileIntent.UpdateGoal(goalId, "Learn Spanish for my trip", "My Trip", NativeLanguage.GERMAN) resultsIn {
                wait()
                val event = assertNotNull(states.value.goalUpdated)
                assertEquals("Learn Spanish for my trip", event.goal.goalText)
                assertEquals("My Trip", event.goal.title)
                assertEquals(NativeLanguage.GERMAN, event.goal.nativeLanguage)
                assertNull(states.value.errorMessage)
                assertFalse(states.value.isSubmitting)
            }
        }
    }

    @Test
    fun `UpdateGoal with too-short text surfaces a validation error instead of updating`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)

        ProfileContainer(goalId, repos.goals, repos.vocabulary, repos.achievements).store.subscribeAndTest {
            ProfileIntent.UpdateGoal(goalId, "ab", null, NativeLanguage.ENGLISH) resultsIn {
                wait()
                assertEquals("Goal text must be 3-200 characters.", states.value.errorMessage)
                assertNull(states.value.goalUpdated)
                assertFalse(states.value.isSubmitting)
            }
        }
    }

    @Test
    fun `ResetAllProgress clears vocabulary progress and refreshes the progress summary`() = runTest {
        val repos = repos()
        val goalId = createGoal(repos)

        ProfileContainer(goalId, repos.goals, repos.vocabulary, repos.achievements).store.subscribeAndTest {
            ProfileIntent.ResetAllProgress resultsIn {
                wait()
                val event = assertNotNull(states.value.progressReset)
                assertTrue(event.success)
                assertNotNull(states.value.progress)
            }
        }
    }
}
