package evola.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/** Body both session-completion routes now accept (01_PRODUCT_SPEC.md §1.10: "both 1.8 and 1.9
 * write to the same aggregation"). `local_date` is the CLIENT's own calendar date - the server has
 * no reliable way to know the user's timezone, and computing this by server UTC date instead would
 * silently corrupt streaks across the day boundary (a session at 23:58 vs 00:02 local time). */
@Serializable
data class SessionCompleteRequest(@SerialName("local_date") val localDate: String? = null)

/** Parses the client-sent local date; falls back to the server's own UTC date only when it's
 * missing or malformed - an honest safety net, not the primary path (the real client always sends
 * a real value). */
internal fun resolveLocalDate(localDate: String?): LocalDate =
    localDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now(ZoneOffset.UTC)

/** Idempotent: completing multiple sessions on the same local day is a no-op after the first -
 * `daily_activity` is one row per user per day (01_PRODUCT_SPEC.md §1.10), not an event log. */
internal fun recordDailyActivity(userId: UUID, localDate: LocalDate) {
    val existing = DailyActivityTable.selectAll()
        .where { (DailyActivityTable.userId eq userId) and (DailyActivityTable.activityDate eq localDate) }
        .singleOrNull()
    if (existing == null) {
        DailyActivityTable.insert {
            it[id] = UUID.randomUUID()
            it[this.userId] = userId
            it[activityDate] = localDate
            it[completed] = true
        }
    } else if (existing[DailyActivityTable.completed] != true) {
        DailyActivityTable.update({
            (DailyActivityTable.userId eq userId) and (DailyActivityTable.activityDate eq localDate)
        }) {
            it[completed] = true
        }
    }
}

/** Consecutive-calendar-days streak (01_PRODUCT_SPEC.md §1.10): if today has activity, count
 * backward from today; if today doesn't but yesterday does, the streak isn't broken yet - it only
 * "resets to 0 the FOLLOWING day" per spec, so count backward from yesterday instead; if neither
 * has activity, the streak is 0. Pure and DB-free so it's directly unit-testable. */
internal fun computeStreak(activityDates: Set<LocalDate>, today: LocalDate): Int {
    var cursor = if (today in activityDates) today else today.minusDays(1)
    if (cursor !in activityDates) return 0
    var streak = 0
    while (cursor in activityDates) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}
