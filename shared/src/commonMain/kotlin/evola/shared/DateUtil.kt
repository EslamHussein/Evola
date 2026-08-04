package evola.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** The device's own local calendar date as an ISO string (e.g. "2026-08-04"). Sent to the server
 * on session completion and the progress fetch so streak/"today completed" are computed against the
 * user's real day, not server UTC (01_PRODUCT_SPEC.md §1.10: a session at 23:58 vs 00:02 local time
 * must land on different days). The client is the only party that knows its timezone. */
fun todayLocalDate(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
