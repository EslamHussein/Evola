package evola.composeapp.core.database

import androidx.compose.runtime.Composable
import app.cash.sqldelight.db.SqlDriver

/** Creates the platform SQLite driver for the on-device database — Android's [android-driver] and
 * iOS's [native-driver]. Lives in `:composeApp` (which alone has the Android `Context`), mirroring
 * [platformHttpEngine]; `:shared` only defines the schema/queries, never instantiates a driver. */
expect class DatabaseDriverFactory {
    fun create(): SqlDriver
}

/** Composable provider so `App.kt` obtains the factory (with the Android `Context` bound) once and
 * hands it to [AppModule], mirroring [rememberSecureStore]/[rememberFileTextExtractor]. */
@Composable
expect fun rememberDatabaseDriverFactory(): DatabaseDriverFactory
