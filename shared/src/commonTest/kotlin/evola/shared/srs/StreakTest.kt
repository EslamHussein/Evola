package evola.shared.srs

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals

class StreakTest {

    private val today = LocalDate(2026, 8, 5)
    private fun daysAgo(n: Int) = today.minus(n, DateTimeUnit.DAY)

    @Test
    fun `empty set is zero`() = assertEquals(0, computeStreak(emptySet(), today))

    @Test
    fun `consecutive run ending today counts fully`() {
        assertEquals(3, computeStreak(setOf(today, daysAgo(1), daysAgo(2)), today))
    }

    @Test
    fun `today missing but yesterday present keeps the streak alive`() {
        assertEquals(2, computeStreak(setOf(daysAgo(1), daysAgo(2)), today))
    }

    @Test
    fun `a gap breaks it`() {
        assertEquals(1, computeStreak(setOf(today, daysAgo(2), daysAgo(3)), today))
    }
}
