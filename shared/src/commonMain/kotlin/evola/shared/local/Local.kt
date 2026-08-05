package evola.shared.local

import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Single fixed local "user" — the on-device app is single-user, but the ported schema/services keep
 * a user_id column so the port stayed near-mechanical. */
const val LOCAL_USER = "local"

@OptIn(ExperimentalUuidApi::class)
internal fun newId(): String = Uuid.random().toString()

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
