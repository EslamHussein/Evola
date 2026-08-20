package evola.composeapp.core.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.io.RollingFileLogWriter
import co.touchlab.kermit.io.RollingFileLogWriterConfig
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

actual class LogFileWriterFactory(private val context: Context) {
    actual fun create(): LogWriter {
        val logDir = Path(context.applicationContext.filesDir.absolutePath, "logs")
        SystemFileSystem.createDirectories(logDir)
        return RollingFileLogWriter(config = RollingFileLogWriterConfig(logFileName = "evola.log", logFilePath = logDir))
    }
}

@Composable
actual fun rememberLogFileWriterFactory(): LogFileWriterFactory {
    val context = LocalContext.current
    return remember { LogFileWriterFactory(context) }
}
