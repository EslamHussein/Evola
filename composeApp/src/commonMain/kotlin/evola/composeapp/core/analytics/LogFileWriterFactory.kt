package evola.composeapp.core.analytics

import androidx.compose.runtime.Composable
import co.touchlab.kermit.LogWriter

/** Creates the platform file [LogWriter] — Android's app-private files dir and iOS's Documents
 * directory. Lives in `:composeApp` (which alone has the Android `Context`), mirroring
 * [evola.database.DatabaseFactory]; `:shared`'s [evola.shared.core.analytics.EvolaLog] only knows about [LogWriter],
 * never how to construct one. */
expect class LogFileWriterFactory {
    fun create(): LogWriter
}

@Composable
expect fun rememberLogFileWriterFactory(): LogFileWriterFactory
