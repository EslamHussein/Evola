package evola.composeapp.main

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.local.AppTheme
import evola.shared.local.LocalSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import pro.respawn.flowmvi.test.subscribeAndTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SettingsContainer] is a thin reactive wrapper: every setter intent just upserts through
 * [LocalSettingsRepository], and [SettingsState.settings] is populated by `whileSubscribed`
 * collecting the repository's own Flow rather than by the reducer updating state directly (see the
 * Container's own doc comment). That collector runs as an independent coroutine from the one that
 * processes the intent, so these tests assert via `states.first { predicate }` (wait for the
 * eventual matching state) rather than reading `states.value` immediately after `resultsIn` -
 * asserting immediately would race the repository's Flow re-emission. Each assertion also confirms
 * the write actually landed in the repository ([LocalSettingsRepository.current]), not just in
 * whatever state the Container happened to publish. */
class SettingsContainerTest {

    private fun repository(): LocalSettingsRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EvolaDatabase.Schema.create(driver)
        return LocalSettingsRepository(EvolaDatabase(driver))
    }

    @Test
    fun `state reflects settings already persisted before the store starts`() = runTest {
        val repository = repository()
        repository.setDailyNewWordGoal(25)
        repository.setAppTheme(AppTheme.DARK)

        SettingsContainer(repository).store.subscribeAndTest {
            val loaded = states.first { it.settings.dailyNewWordGoal == 25 }
            assertEquals(AppTheme.DARK, loaded.settings.appTheme)
        }
    }

    @Test
    fun `SetDailyNewWordGoal updates state and persists to the repository`() = runTest {
        val repository = repository()
        SettingsContainer(repository).store.subscribeAndTest {
            SettingsIntent.SetDailyNewWordGoal(12) resultsIn {
                val loaded = states.first { it.settings.dailyNewWordGoal == 12 }
                assertEquals(12, loaded.settings.dailyNewWordGoal)
            }
        }
        assertEquals(12, repository.current().dailyNewWordGoal)
    }

    @Test
    fun `boolean and enum setters each propagate to state and the repository`() = runTest {
        val repository = repository()
        SettingsContainer(repository).store.subscribeAndTest {
            SettingsIntent.SetKeyboardExerciseEnabled(false) resultsIn {
                states.first { !it.settings.keyboardExerciseEnabled }
            }
            SettingsIntent.SetInvertSwipe(true) resultsIn {
                states.first { it.settings.invertSwipe }
            }
            SettingsIntent.SetTtsEnabled(true) resultsIn {
                states.first { it.settings.ttsEnabled }
            }
            SettingsIntent.SetTtsVoiceName("Anna") resultsIn {
                states.first { it.settings.ttsVoiceName == "Anna" }
            }
            SettingsIntent.SetReducedMotion(true) resultsIn {
                states.first { it.settings.reducedMotion }
            }
            SettingsIntent.SetAppTheme(AppTheme.LIGHT) resultsIn {
                states.first { it.settings.appTheme == AppTheme.LIGHT }
            }
        }

        val settings = repository.current()
        assertTrue(!settings.keyboardExerciseEnabled)
        assertTrue(settings.invertSwipe)
        assertTrue(settings.ttsEnabled)
        assertEquals("Anna", settings.ttsVoiceName)
        assertTrue(settings.reducedMotion)
        assertEquals(AppTheme.LIGHT, settings.appTheme)
    }

    @Test
    fun `SetReminderHour clamps an out-of-range value the same way the repository does`() = runTest {
        val repository = repository()
        SettingsContainer(repository).store.subscribeAndTest {
            SettingsIntent.SetReminderHour(99) resultsIn {
                val loaded = states.first { it.settings.reminderHour == 23 }
                assertEquals(23, loaded.settings.reminderHour)
            }
        }
        assertEquals(23, repository.current().reminderHour)
    }
}
