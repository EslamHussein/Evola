package evola.composeapp.core.database

import androidx.compose.runtime.Composable
import evola.database.DatabaseFactory

/** Composable provider so `App.kt` obtains the Room [DatabaseFactory] (with the Android `Context`
 * bound) once and hands it to [evola.composeapp.core.di.evolaModule] - mirrors
 * [rememberDatabaseDriverFactory]'s SQLDelight-driver equivalent it's replacing. */
@Composable
expect fun rememberRoomDatabaseFactory(): DatabaseFactory
