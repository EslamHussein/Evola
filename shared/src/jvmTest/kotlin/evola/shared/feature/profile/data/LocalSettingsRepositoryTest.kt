package evola.shared.feature.profile.data

import evola.database.DatabaseFactory
import evola.database.create
import evola.shared.feature.profile.domain.AppSettings
import evola.shared.feature.profile.domain.DEFAULT_DAILY_NEW_WORD_GOAL
import evola.shared.feature.profile.domain.isWithinNotificationFrequencyLimit
import evola.shared.feature.profile.domain.isWithinSilentHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSettingsRepositoryTest {

    private fun repo(): LocalSettingsRepository = LocalSettingsRepository(DatabaseFactory().create())

    @Test
    fun `defaults match pre-Settings behavior when nothing has been set`() = runTest {
        val settings = repo().settings.first()
        assertEquals(DEFAULT_DAILY_NEW_WORD_GOAL, settings.dailyNewWordGoal)
        assertTrue(settings.keyboardExerciseEnabled)
        assertTrue(settings.multipleChoiceExerciseEnabled)
        assertFalse(settings.invertSwipe)
        assertFalse(settings.ttsEnabled)
        assertFalse(settings.notificationsEnabled)
    }

    @Test
    fun `setters persist and current reflects them immediately`() = runTest {
        val repo = repo()
        repo.setDailyNewWordGoal(15)
        repo.setInvertSwipe(true)
        repo.setKeyboardExerciseEnabled(false)
        repo.setNotificationsEnabled(true)
        repo.setReminderHour(7)

        val settings = repo.current()
        assertEquals(15, settings.dailyNewWordGoal)
        assertTrue(settings.invertSwipe)
        assertFalse(settings.keyboardExerciseEnabled)
        assertTrue(settings.multipleChoiceExerciseEnabled) // untouched setting stays default
        assertTrue(settings.notificationsEnabled)
        assertEquals(7, settings.reminderHour)
    }

    @Test
    fun `settings flow reacts to a later write`() = runTest {
        val repo = repo()
        assertEquals(DEFAULT_DAILY_NEW_WORD_GOAL, repo.settings.first().dailyNewWordGoal)
        repo.setDailyNewWordGoal(20)
        assertEquals(20, repo.settings.first().dailyNewWordGoal)
    }

    @Test
    fun `daily goal is floored at 1 and reminder hour is clamped to a valid range`() = runTest {
        val repo = repo()
        repo.setDailyNewWordGoal(-5)
        repo.setReminderHour(99)
        val settings = repo.current()
        assertEquals(1, settings.dailyNewWordGoal)
        assertEquals(23, settings.reminderHour)
    }

    @Test
    fun `silent hours window wraps past midnight`() {
        val settings = AppSettings(silentHoursStart = 22, silentHoursEnd = 8)
        assertTrue(settings.isWithinSilentHours(23))
        assertTrue(settings.isWithinSilentHours(0))
        assertTrue(settings.isWithinSilentHours(7))
        assertFalse(settings.isWithinSilentHours(8))
        assertFalse(settings.isWithinSilentHours(21))
        assertFalse(settings.isWithinSilentHours(12))
    }

    @Test
    fun `silent hours window works when it does not wrap`() {
        val settings = AppSettings(silentHoursStart = 1, silentHoursEnd = 5)
        assertTrue(settings.isWithinSilentHours(2))
        assertFalse(settings.isWithinSilentHours(5))
        assertFalse(settings.isWithinSilentHours(23))
    }

    @Test
    fun `notification frequency limit blocks a too-recent repost and allows an old one`() {
        val settings = AppSettings(notificationFrequencyLimitHours = 2, lastNotificationPostedAtMillis = 0L)
        assertTrue(settings.isWithinNotificationFrequencyLimit(nowMillis = 3_600_000L)) // 1h later - still limited
        assertFalse(settings.isWithinNotificationFrequencyLimit(nowMillis = 7_200_001L)) // just over 2h later - clear
    }

    @Test
    fun `notification frequency limit never blocks when nothing has posted yet`() {
        val settings = AppSettings(lastNotificationPostedAtMillis = null)
        assertFalse(settings.isWithinNotificationFrequencyLimit(nowMillis = 0L))
    }
}
