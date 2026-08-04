package evola.server

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals

/** [computeStreak] and [resolveLocalDate] are pure and DB-free, so they're tested directly here -
 * the streak rule (01_PRODUCT_SPEC.md §1.10) has enough edge cases around the day boundary to be
 * worth exercising in isolation rather than only through a seeded-database service test. */
class DailyActivityTest {

    private val today = LocalDate.of(2026, 8, 4)

    @Test
    fun `an empty activity set is a zero streak`() {
        assertEquals(0, computeStreak(emptySet(), today))
    }

    @Test
    fun `activity today alone is a one-day streak`() {
        assertEquals(1, computeStreak(setOf(today), today))
    }

    @Test
    fun `consecutive days ending today count fully`() {
        val dates = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(4, computeStreak(dates, today))
    }

    @Test
    fun `a gap breaks the streak - only the run ending today counts`() {
        // 3 days ago is present but 2 days ago is missing, so the run stops at yesterday.
        val dates = setOf(today, today.minusDays(1), today.minusDays(3), today.minusDays(4))
        assertEquals(2, computeStreak(dates, today))
    }

    @Test
    fun `today missing but yesterday present keeps the streak alive - it resets the FOLLOWING day`() {
        // Per spec: "Missed day -> resets to 0 the following day." Mid-day today, having not yet
        // studied, the user's streak is still whatever it was through yesterday - not 0.
        val dates = setOf(today.minusDays(1), today.minusDays(2), today.minusDays(3))
        assertEquals(3, computeStreak(dates, today))
    }

    @Test
    fun `neither today nor yesterday present is a broken streak`() {
        val dates = setOf(today.minusDays(2), today.minusDays(3), today.minusDays(4))
        assertEquals(0, computeStreak(dates, today))
    }

    @Test
    fun `future-dated activity does not inflate the streak`() {
        // A client with a badly-skewed clock shouldn't be able to report tomorrow into the count.
        val dates = setOf(today.plusDays(1), today, today.minusDays(1))
        assertEquals(2, computeStreak(dates, today))
    }

    @Test
    fun `resolveLocalDate parses a client-sent ISO date`() {
        assertEquals(LocalDate.of(2026, 8, 4), resolveLocalDate("2026-08-04"))
    }

    @Test
    fun `resolveLocalDate falls back to the server date for a null or malformed value`() {
        val serverToday = LocalDate.now(java.time.ZoneOffset.UTC)
        assertEquals(serverToday, resolveLocalDate(null))
        assertEquals(serverToday, resolveLocalDate("not-a-date"))
    }
}
