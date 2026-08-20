package evola.shared

import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The device's own local calendar date as an ISO string (e.g. "2026-08-04"). Used on session
 * completion and progress reads so streak/"today completed" are computed against the user's real
 * day (01_PRODUCT_SPEC.md §1.10: a session at 23:58 vs 00:02 local time must land on different
 * days), using the device's own timezone. */
fun todayLocalDate(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
