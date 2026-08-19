package evola.shared.srs

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Consecutive-calendar-days streak (01_PRODUCT_SPEC.md §1.10), ported from the server's
 * `DailyActivity.computeStreak` to `kotlinx.datetime` for `commonMain`. If today has activity, count
 * back from today; if today doesn't but yesterday does, the streak isn't broken yet — it only
 * "resets the FOLLOWING day" — so count back from yesterday; otherwise 0. Pure and testable.
 */
fun computeStreak(activityDates: Set<LocalDate>, today: LocalDate): Int {
    var cursor = if (today in activityDates) today else today.minus(1, DateTimeUnit.DAY)
    if (cursor !in activityDates) return 0
    var streak = 0
    while (cursor in activityDates) {
        streak++
        cursor = cursor.minus(1, DateTimeUnit.DAY)
    }
    return streak
}

/** Longest run of consecutive calendar days anywhere in [activityDates] (all-time, not just up to
 * [computeStreak]'s "current" run) - Reword's "Best streak" stat. Pure and testable, same shape as
 * [computeStreak]: walks the sorted dates once, resetting the running count whenever a gap appears. */
fun computeBestStreak(activityDates: Set<LocalDate>): Int {
    if (activityDates.isEmpty()) return 0
    val sorted = activityDates.sorted()
    var best = 1
    var running = 1
    for (i in 1 until sorted.size) {
        running = if (sorted[i] == sorted[i - 1].plus(1, DateTimeUnit.DAY)) running + 1 else 1
        if (running > best) best = running
    }
    return best
}
