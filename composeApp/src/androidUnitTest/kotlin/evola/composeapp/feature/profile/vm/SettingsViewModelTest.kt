package evola.composeapp.feature.profile.vm

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import evola.shared.db.EvolaDatabase
import evola.shared.feature.profile.domain.AppTheme
import evola.shared.feature.profile.data.LocalSettingsRepository
import kotlinx.coroutines.test.runTest
import org.orbitmvi.orbit.test.testWithInternalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [SettingsViewModel] is a thin reactive wrapper: every setter intent just upserts through
 * [LocalSettingsRepository], and [SettingsState.settings] is populated by `onCreate` collecting
 * the repository's own Flow rather than by a reducer inside the setter itself (see the
 * ViewModel's own doc comment). That collector never completes on its own, so every test ends
 * with `cancelAndIgnoreRemainingItems()` (see the plan's gotcha notes on infinite `onCreate`
 * loops). Each assertion also confirms the write actually landed in the repository
 * ([LocalSettingsRepository.current]), not just in whatever state the ViewModel happened to publish. */
class SettingsViewModelTest {

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

        val viewModel = SettingsViewModel(repository)
        viewModel.testWithInternalState(this, SettingsState()) {
            runOnCreate()
            val loaded = awaitInternalState()
            assertEquals(25, loaded.settings.dailyNewWordGoal)
            assertEquals(AppTheme.DARK, loaded.settings.appTheme)
            cancelAndIgnoreRemainingItems()
        }
    }

    @Test
    fun `SetDailyNewWordGoal updates state and persists to the repository`() = runTest {
        val repository = repository()
        val viewModel = SettingsViewModel(repository)
        viewModel.testWithInternalState(this, SettingsState()) {
            runOnCreate()
            containerHost.setDailyNewWordGoal(12)
            val loaded = awaitInternalState()
            assertEquals(12, loaded.settings.dailyNewWordGoal)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(12, repository.current().dailyNewWordGoal)
    }

    @Test
    fun `boolean and enum setters each propagate to state and the repository`() = runTest {
        val repository = repository()
        val viewModel = SettingsViewModel(repository)
        viewModel.testWithInternalState(this, SettingsState()) {
            runOnCreate()

            containerHost.setKeyboardExerciseEnabled(false)
            assertTrue(!awaitInternalState().settings.keyboardExerciseEnabled)

            containerHost.setInvertSwipe(true)
            assertTrue(awaitInternalState().settings.invertSwipe)

            containerHost.setTtsEnabled(true)
            assertTrue(awaitInternalState().settings.ttsEnabled)

            containerHost.setTtsVoiceName("Anna")
            assertEquals("Anna", awaitInternalState().settings.ttsVoiceName)

            containerHost.setReducedMotion(true)
            assertTrue(awaitInternalState().settings.reducedMotion)

            containerHost.setAppTheme(AppTheme.LIGHT)
            assertEquals(AppTheme.LIGHT, awaitInternalState().settings.appTheme)

            cancelAndIgnoreRemainingItems()
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
        val viewModel = SettingsViewModel(repository)
        viewModel.testWithInternalState(this, SettingsState()) {
            runOnCreate()
            containerHost.setReminderHour(99)
            val loaded = awaitInternalState()
            assertEquals(23, loaded.settings.reminderHour)
            cancelAndIgnoreRemainingItems()
        }
        assertEquals(23, repository.current().reminderHour)
    }
}
