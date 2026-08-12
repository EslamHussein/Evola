package evola.composeapp.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.io.RollingFileLogWriter
import co.touchlab.kermit.io.RollingFileLogWriterConfig
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSHomeDirectory

actual class LogFileWriterFactory {
    actual fun create(): LogWriter {
        val logDir = Path(NSHomeDirectory(), "Documents/logs")
        SystemFileSystem.createDirectories(logDir)
        return RollingFileLogWriter(config = RollingFileLogWriterConfig(logFileName = "evola.log", logFilePath = logDir))
    }
}

@Composable
actual fun rememberLogFileWriterFactory(): LogFileWriterFactory = remember { LogFileWriterFactory() }
